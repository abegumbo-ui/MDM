package com.familymdm.agent.sitepolicy;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Plain checks for BlockList (no test framework needed). Run on a computer:
 *   javac -d out android/sitepolicy/src/main/java/com/familymdm/agent/sitepolicy/BlockList.java android/sitepolicy/src/test/java/com/familymdm/agent/sitepolicy/BlockListCheck.java
 *   java -cp out com.familymdm.agent.sitepolicy.BlockListCheck
 */
public class BlockListCheck {
    static int failures = 0;

    static void check(boolean ok, String what) {
        if (!ok) {
            failures++;
            System.out.println("FAIL: " + what);
        } else {
            System.out.println("ok:   " + what);
        }
    }

    /** Builds a list file the same way tools/build-blocklists.mjs does. */
    static byte[] build(String... domains) {
        long[] h = new long[domains.length];
        for (int i = 0; i < domains.length; i++) h[i] = BlockList.hash(domains[i]);
        // sort unsigned: flip the sign bit, sort as signed, flip back
        for (int i = 0; i < h.length; i++) h[i] ^= Long.MIN_VALUE;
        Arrays.sort(h);
        ByteBuffer b = ByteBuffer.allocate(8 + 8 * h.length);
        b.put((byte) 'M').put((byte) 'D').put((byte) 'B').put((byte) '1').putInt(h.length);
        for (long v : h) b.putLong(v ^ Long.MIN_VALUE);
        return b.array();
    }

    public static void main(String[] args) throws Exception {
        // Same value the Node side checks in test/blocklist.test.js: sha256("example.com") starts a379a6f6eeafb9a5.
        check(BlockList.hash("example.com") == 0xa379a6f6eeafb9a5L, "fingerprint matches the builder's");

        BlockList l = BlockList.fromBytes(build("bad.example", "gamble.test", "x.y.evil.org", "zzz.net", "aaa.net"));
        check(l.size() == 5, "five entries");
        check(l.blocks("bad.example"), "exact domain is blocked");
        check(l.blocks("www.bad.example"), "www. is ignored");
        check(l.blocks("deep.sub.bad.example"), "subdomains of a listed domain are blocked");
        check(l.blocks("BAD.Example"), "case does not matter");
        check(l.blocks("bad.example."), "trailing dot does not matter");
        check(!l.blocks("good.example"), "a different domain is not blocked");
        check(!l.blocks("notbad.example"), "a domain that merely ends the same way is not blocked");
        check(!l.blocks("example"), "a bare top-level name is never matched");
        check(l.blocks("x.y.evil.org") && !l.blocks("evil.org") && !l.blocks("y.evil.org"), "a listed subdomain does not block its parents");
        check(l.blocks("aaa.net") && l.blocks("zzz.net"), "first and last entries are found (sort order)");
        check(!l.blocks(null) && !l.blocks(""), "null and empty are not blocked");

        List<BlockList> both = new ArrayList<>();
        both.add(l);
        both.add(BlockList.fromBytes(build("other.example")));
        check(BlockList.anyBlocks(both, "other.example") && BlockList.anyBlocks(both, "bad.example") && !BlockList.anyBlocks(both, "fine.example"), "any of several lists");
        check(!BlockList.anyBlocks(new ArrayList<>(), "bad.example"), "no lists blocks nothing");

        BlockList empty = BlockList.fromBytes(build());
        check(!empty.blocks("bad.example"), "an empty list blocks nothing");

        boolean threw = false;
        try { BlockList.fromBytes(new byte[] {1, 2, 3}); } catch (java.io.IOException e) { threw = true; }
        check(threw, "garbage is rejected");
        byte[] cut = build("a.example", "b.example");
        threw = false;
        try { BlockList.fromBytes(Arrays.copyOf(cut, cut.length - 3)); } catch (java.io.IOException e) { threw = true; }
        check(threw, "a half-downloaded file is rejected, not trusted");

        System.out.println(failures == 0 ? "ALL OK" : failures + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
