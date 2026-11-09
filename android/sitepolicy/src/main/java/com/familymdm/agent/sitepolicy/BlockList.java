package com.familymdm.agent.sitepolicy;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;

/**
 * A category blocklist (adult sites, gambling, ...) in a compact form that a small phone can use
 * without loading a long list of names into memory.
 *
 * The file is built by tools/build-blocklists.mjs: every blocked domain is reduced to a 64-bit
 * fingerprint (the first 8 bytes of its SHA-256), the fingerprints are sorted, and the file is
 * "MDB1", the count as a 4-byte number, then the fingerprints. Checking a site is a binary search
 * on the memory-mapped file, so 500,000 names cost about 4 MB of storage and almost no memory.
 *
 * A domain on the list blocks all of its subdomains too (blocking "example.com" blocks
 * "www.example.com").
 *
 * Pure Java, so it can be checked on a computer. See BlockListCheck.
 */
public final class BlockList {
    private static final int HEADER = 8;
    private final ByteBuffer buf;
    private final int count;

    private BlockList(ByteBuffer buf, int count) {
        this.buf = buf;
        this.count = count;
    }

    /** Opens a downloaded list file; throws if it is not a complete, well-formed list. */
    public static BlockList open(File f) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(f, "r"); FileChannel ch = raf.getChannel()) {
            long size = ch.size();
            if (size < HEADER || size > Integer.MAX_VALUE) throw new IOException("bad list size");
            ByteBuffer b = ch.map(FileChannel.MapMode.READ_ONLY, 0, size); // stays valid after the channel closes
            return wrap(b);
        }
    }

    public static BlockList fromBytes(byte[] bytes) throws IOException {
        return wrap(ByteBuffer.wrap(bytes));
    }

    private static BlockList wrap(ByteBuffer b) throws IOException {
        if (b.capacity() < HEADER || b.get(0) != 'M' || b.get(1) != 'D' || b.get(2) != 'B' || b.get(3) != '1') {
            throw new IOException("not a blocklist file");
        }
        int n = b.getInt(4);
        if (n < 0 || (long) HEADER + 8L * n != b.capacity()) throw new IOException("blocklist is incomplete");
        return new BlockList(b, n);
    }

    public int size() {
        return count;
    }

    /** The 64-bit fingerprint of a (lower-case) domain, exactly as the builder computes it. */
    public static long hash(String domain) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(domain.getBytes(StandardCharsets.UTF_8));
            long h = 0;
            for (int i = 0; i < 8; i++) h = (h << 8) | (d[i] & 0xff);
            return h;
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private boolean containsHash(long h) {
        int lo = 0, hi = count - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            long v = buf.getLong(HEADER + 8 * mid);
            int c = Long.compareUnsigned(v, h);
            if (c == 0) return true;
            if (c < 0) lo = mid + 1;
            else hi = mid - 1;
        }
        return false;
    }

    /** True if this host, or any parent domain of it, is on the list. */
    public boolean blocks(String host) {
        if (host == null) return false;
        String h = host.toLowerCase(Locale.ROOT);
        if (h.endsWith(".")) h = h.substring(0, h.length() - 1);
        if (h.startsWith("www.")) h = h.substring(4);
        while (true) {
            if (containsHash(hash(h))) return true;
            int dot = h.indexOf('.');
            if (dot < 0) return false;
            h = h.substring(dot + 1);
            if (h.indexOf('.') < 0) return false; // never test a bare "com" or "uk"
        }
    }

    /** True if any of the lists blocks this host. */
    public static boolean anyBlocks(List<BlockList> lists, String host) {
        if (lists == null || host == null) return false;
        for (BlockList l : lists) if (l.blocks(host)) return true;
        return false;
    }
}
