package com.familymdm.agent.sitepolicy;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * Plain checks for SafeSearchDns. Run on a computer:
 *   javac -d out android/sitepolicy/src/main/java/com/familymdm/agent/sitepolicy/SafeSearchDns.java android/sitepolicy/src/test/java/com/familymdm/agent/sitepolicy/SafeSearchDnsCheck.java
 *   java -cp out com.familymdm.agent.sitepolicy.SafeSearchDnsCheck
 */
public class SafeSearchDnsCheck {
    static int failures = 0;

    static void check(boolean ok, String what) {
        if (!ok) {
            failures++;
            System.out.println("FAIL: " + what);
        } else {
            System.out.println("ok:   " + what);
        }
    }

    public static void main(String[] args) throws Exception {
        // ---- which lookups are redirected ----
        check("forcesafesearch.google.com".equals(SafeSearchDns.target("www.google.com", true, "off")), "Google");
        check("forcesafesearch.google.com".equals(SafeSearchDns.target("WWW.Google.co.il", true, "off")), "Google's Israeli site, any case");
        check("forcesafesearch.google.com".equals(SafeSearchDns.target("www.google.com.au", true, "off")) && "forcesafesearch.google.com".equals(SafeSearchDns.target("www.google.de", true, "off")), "other country sites");
        check("strict.bing.com".equals(SafeSearchDns.target("www.bing.com", true, "off")), "Bing");
        check("safe.duckduckgo.com".equals(SafeSearchDns.target("duckduckgo.com", true, "off")), "DuckDuckGo");
        for (String leave : new String[] {"google.com", "mail.google.com", "maps.google.com", "www.googleapis.com", "accounts.google.com", "www.google.com.evil.example", "notwww.google.com", "bing.com", "example.com", "safe.duckduckgo.com"}) {
            check(SafeSearchDns.target(leave, true, "strict") == null, "left alone: " + leave);
        }
        check(SafeSearchDns.target("www.google.com", false, "off") == null && SafeSearchDns.target("www.bing.com", false, "strict") == null, "search engines untouched when SafeSearch is off");
        for (String yt : new String[] {"www.youtube.com", "m.youtube.com", "youtubei.googleapis.com", "youtube.googleapis.com", "www.youtube-nocookie.com"}) {
            check("restrict.youtube.com".equals(SafeSearchDns.target(yt, false, "strict")), "YouTube strict: " + yt);
            check("restrictmoderate.youtube.com".equals(SafeSearchDns.target(yt, false, "moderate")), "YouTube moderate: " + yt);
            check(SafeSearchDns.target(yt, true, "off") == null, "YouTube untouched when its setting is off: " + yt);
        }
        // Google's own list of hosts that must NOT be redirected, or YouTube breaks.
        for (String leave : new String[] {"youtube.com", "s.ytimg.com", "youtu.be", "googleapis.com", "i.ytimg.com", "googlevideo.com"}) {
            check(SafeSearchDns.target(leave, true, "strict") == null, "never redirected: " + leave);
        }
        check(SafeSearchDns.target(null, true, "strict") == null, "no name, no redirect");

        // ---- DNS messages ----
        byte[] q = SafeSearchDns.buildQuery(0xBEEF, "www.google.com");
        check(SafeSearchDns.questionType(q) == SafeSearchDns.TYPE_A, "a built query asks for an address");
        List<byte[]> ips = new ArrayList<>();
        ips.add(new byte[] {(byte) 216, (byte) 239, 38, 120});
        ips.add(new byte[] {1, 2, 3, 4});
        byte[] a = SafeSearchDns.answer(q, ips, 300);
        check(a != null && a[0] == (byte) 0xBE && a[1] == (byte) 0xEF, "the answer carries the query's id");
        check((a[2] & 0x80) != 0 && (a[3] & 0x0f) == 0, "it is a response with no error");
        List<byte[]> back = SafeSearchDns.parseA(a);
        check(back.size() == 2 && InetAddress.getByAddress(back.get(0)).getHostAddress().equals("216.239.38.120") && back.get(1)[3] == 4, "the addresses can be read back out of it");
        boolean sameQuestion = true;
        for (int i = 12; i < q.length; i++) if (a[i] != q[i]) sameQuestion = false;
        check(sameQuestion, "the question is echoed back exactly");
        byte[] none = SafeSearchDns.answer(q, new ArrayList<byte[]>(), 300);
        check(none != null && SafeSearchDns.parseA(none).isEmpty() && (none[3] & 0x0f) == 0, "an empty answer means 'no such record', not an error");

        // A real-shaped reply with a CNAME first and name compression (what a resolver actually sends).
        byte[] head = SafeSearchDns.buildQuery(7, "forcesafesearch.google.com");
        java.io.ByteArrayOutputStream r = new java.io.ByteArrayOutputStream();
        r.write(head, 0, head.length);
        r.write(new byte[] {(byte) 0xc0, 0x0c, 0, 5, 0, 1, 0, 0, 0, 60, 0, 4, 1, 'x', (byte) 0xc0, 0x0c}, 0, 16); // a CNAME record
        r.write(new byte[] {(byte) 0xc0, 0x0c, 0, 1, 0, 1, 0, 0, 0, 60, 0, 4, (byte) 216, (byte) 239, 38, 120}, 0, 16); // an A record
        byte[] reply = r.toByteArray();
        reply[2] = (byte) 0x81; reply[3] = (byte) 0x80; reply[7] = 2;
        List<byte[]> parsed = SafeSearchDns.parseA(reply);
        check(parsed.size() == 1 && (parsed.get(0)[0] & 0xff) == 216, "an address is found after a CNAME record");
        reply[3] = (byte) 0x83;
        check(SafeSearchDns.parseA(reply).isEmpty(), "a failed lookup gives no addresses");

        // Lifetimes are cut short, nothing else is touched.
        byte[] longLived = reply.clone();
        longLived[3] = (byte) 0x80;
        int ttlAt1 = head.length + 6, ttlAt2 = head.length + 16 + 6; // the TTL of the CNAME record and of the A record
        longLived[ttlAt1 + 3] = (byte) 0x10; longLived[ttlAt1 + 2] = (byte) 0x0e; // 3600 + 0x1000 seconds, a long time
        longLived[ttlAt2 + 3] = 20; // already short: must stay 20
        byte[] capped = SafeSearchDns.capTtl(longLived, 60);
        long t1 = ((capped[ttlAt1] & 0xffL) << 24) | ((capped[ttlAt1 + 1] & 0xffL) << 16) | ((capped[ttlAt1 + 2] & 0xffL) << 8) | (capped[ttlAt1 + 3] & 0xffL);
        long t2 = ((capped[ttlAt2] & 0xffL) << 24) | ((capped[ttlAt2 + 1] & 0xffL) << 16) | ((capped[ttlAt2 + 2] & 0xffL) << 8) | (capped[ttlAt2 + 3] & 0xffL);
        check(t1 == 60 && t2 == 20, "a long lifetime is cut to 60 seconds, a short one is left alone");
        boolean restSame = true;
        for (int i = 0; i < longLived.length; i++) if ((i < ttlAt1 || i > ttlAt1 + 3) && capped[i] != longLived[i]) restSame = false;
        check(restSame && SafeSearchDns.parseA(capped).size() == 1, "nothing but the lifetime changed; the address still reads out");
        byte[] cut = java.util.Arrays.copyOf(longLived, longLived.length - 5);
        check(java.util.Arrays.equals(SafeSearchDns.capTtl(cut, 60), cut) && SafeSearchDns.capTtl(null, 60) == null && SafeSearchDns.capTtl(new byte[] {1, 2}, 60).length == 2, "a damaged or tiny message is returned as it was");
        check(SafeSearchDns.questionType(new byte[] {1, 2, 3}) == -1 && SafeSearchDns.answer(new byte[] {1, 2, 3}, ips, 1) == null && SafeSearchDns.parseA(new byte[] {1, 2}).isEmpty(), "garbage is refused, not crashed on");
        byte[] aaaa = q.clone();
        aaaa[aaaa.length - 3] = (byte) SafeSearchDns.TYPE_AAAA;
        check(SafeSearchDns.questionType(aaaa) == SafeSearchDns.TYPE_AAAA, "an IPv6 question is recognised");
        check(SafeSearchDns.fallback("forcesafesearch.google.com").size() == 1 && SafeSearchDns.fallback("safe.duckduckgo.com").isEmpty(), "published fallback addresses only where the provider publishes one");

        System.out.println(failures == 0 ? "ALL OK" : failures + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
