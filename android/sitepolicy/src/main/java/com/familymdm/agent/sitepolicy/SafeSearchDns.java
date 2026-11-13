package com.familymdm.agent.sitepolicy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Forces SafeSearch (Google, Bing, DuckDuckGo) and YouTube's Restricted Mode for every browser and
 * app on the phone, the way the search engines themselves tell network filters to do it: when the
 * phone looks up, say, www.google.com, it is given the address of forcesafesearch.google.com
 * instead. The person cannot switch it off in the browser, and it needs no certificate.
 *
 *   Google     www.google.com and country sites  ->  forcesafesearch.google.com
 *   Bing       www.bing.com                      ->  strict.bing.com
 *   DuckDuckGo duckduckgo.com                    ->  safe.duckduckgo.com
 *   YouTube    www / m.youtube.com, youtubei.googleapis.com, youtube.googleapis.com,
 *              www.youtube-nocookie.com          ->  restrict.youtube.com (strict)
 *                                                    or restrictmoderate.youtube.com (moderate)
 *
 * This class is only the rules and the DNS message handling (pure Java, checked on a computer; see
 * SafeSearchDnsCheck). DnsFilterVpnService does the actual lookups.
 */
public final class SafeSearchDns {
    private SafeSearchDns() {}

    public static final int TYPE_A = 1, TYPE_AAAA = 28, TYPE_HTTPS = 65;

    /** The host whose address should be given instead, or null to leave this lookup alone. */
    public static String target(String qname, boolean safeSearch, String youtube) {
        if (qname == null) return null;
        String h = qname.toLowerCase(Locale.ROOT);
        if (safeSearch) {
            if (h.matches("www\\.google\\.(com|[a-z]{2,3}|com?\\.[a-z]{2})")) return "forcesafesearch.google.com";
            if (h.equals("www.bing.com")) return "strict.bing.com";
            if (h.equals("duckduckgo.com") || h.equals("www.duckduckgo.com")) return "safe.duckduckgo.com";
        }
        if ("strict".equals(youtube) || "moderate".equals(youtube)) {
            if (h.equals("www.youtube.com") || h.equals("m.youtube.com") || h.equals("youtubei.googleapis.com")
                    || h.equals("youtube.googleapis.com") || h.equals("www.youtube-nocookie.com")) {
                return "strict".equals(youtube) ? "restrict.youtube.com" : "restrictmoderate.youtube.com";
            }
        }
        return null;
    }

    /** Used only if the safe host cannot be looked up just now. The addresses the providers publish. */
    public static List<byte[]> fallback(String target) {
        List<byte[]> out = new ArrayList<>();
        if ("forcesafesearch.google.com".equals(target) || "restrict.youtube.com".equals(target)) out.add(new byte[] {(byte) 216, (byte) 239, 38, 120});
        else if ("restrictmoderate.youtube.com".equals(target)) out.add(new byte[] {(byte) 216, (byte) 239, 38, 119});
        else if ("strict.bing.com".equals(target)) out.add(new byte[] {(byte) 204, 79, (byte) 197, (byte) 220});
        return out;
    }

    // ---------- DNS messages (a whole message starting at byte 0) ----------

    /** Where the first question ends (just past QTYPE and QCLASS), or -1 if the message is not a simple query. */
    private static int questionEnd(byte[] m) {
        if (m == null || m.length < 17 || (((m[4] & 0xff) << 8) | (m[5] & 0xff)) != 1) return -1;
        int pos = 12, guard = 0;
        while (pos < m.length && guard++ < 128) {
            int len = m[pos] & 0xff;
            pos++;
            if (len == 0) break;
            if ((len & 0xc0) != 0) return -1;
            pos += len;
        }
        pos += 4;
        return pos > m.length ? -1 : pos;
    }

    /** The record type the first question asks for (1 = address), or -1. */
    public static int questionType(byte[] query) {
        int end = questionEnd(query);
        return end < 0 ? -1 : ((query[end - 4] & 0xff) << 8) | (query[end - 3] & 0xff);
    }

    /** A plain "what is the address of host" query. */
    public static byte[] buildQuery(int id, String host) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(id >> 8);
        o.write(id);
        o.write(0x01); // recursion desired
        o.write(0x00);
        o.write(0); o.write(1); // one question
        for (int i = 0; i < 6; i++) o.write(0);
        for (String label : host.split("\\.")) {
            byte[] b = label.getBytes(StandardCharsets.US_ASCII);
            o.write(b.length);
            o.write(b, 0, b.length);
        }
        o.write(0);
        o.write(0); o.write(TYPE_A);
        o.write(0); o.write(1);
        return o.toByteArray();
    }

    private static int skipName(byte[] m, int pos) {
        int guard = 0;
        while (pos < m.length && guard++ < 128) {
            int len = m[pos] & 0xff;
            if (len == 0) return pos + 1;
            if ((len & 0xc0) == 0xc0) return pos + 2; // a pointer ends the name
            pos += 1 + len;
        }
        return -1;
    }

    /**
     * A copy of a DNS reply in which no record may be kept longer than maxTtl seconds. Android keeps every answer it gets for as
     * long as its time-to-live says (often minutes to hours), so after a rule changes (a pause that ends, another filter level, SafeSearch
     * switched) a site that was let through stayed reachable until its old answer expired. With the lifetime cut short the new rule
     * applies within about a minute. Anything that is not a plain reply is returned as it came (never damaged).
     */
    public static byte[] capTtl(byte[] m, int maxTtl) {
        try {
            if (m == null || m.length < 12 || maxTtl < 0) return m;
            int qd = ((m[4] & 0xff) << 8) | (m[5] & 0xff);
            int records = (((m[6] & 0xff) << 8) | (m[7] & 0xff)) + (((m[8] & 0xff) << 8) | (m[9] & 0xff)) + (((m[10] & 0xff) << 8) | (m[11] & 0xff));
            int pos = 12;
            for (int i = 0; i < qd; i++) {
                pos = skipName(m, pos);
                if (pos < 0) return m;
                pos += 4;
                if (pos > m.length) return m;
            }
            byte[] out = m.clone();
            for (int i = 0; i < records; i++) {
                pos = skipName(m, pos);
                if (pos < 0 || pos + 10 > m.length) return m;
                int type = ((m[pos] & 0xff) << 8) | (m[pos + 1] & 0xff);
                long ttl = ((m[pos + 4] & 0xffL) << 24) | ((m[pos + 5] & 0xffL) << 16) | ((m[pos + 6] & 0xffL) << 8) | (m[pos + 7] & 0xffL);
                int len = ((m[pos + 8] & 0xff) << 8) | (m[pos + 9] & 0xff);
                if (type != 41 && ttl > maxTtl) { // type 41 (EDNS) uses these four bytes for flags, not a lifetime
                    out[pos + 4] = (byte) (maxTtl >>> 24);
                    out[pos + 5] = (byte) (maxTtl >>> 16);
                    out[pos + 6] = (byte) (maxTtl >>> 8);
                    out[pos + 7] = (byte) maxTtl;
                }
                pos += 10 + len;
                if (pos > m.length) return m;
            }
            return out;
        } catch (RuntimeException e) {
            return m;
        }
    }

    /** Every IPv4 address in a reply's answer section. */
    public static List<byte[]> parseA(byte[] reply) {
        List<byte[]> out = new ArrayList<>();
        int pos = questionEnd(reply);
        if (pos < 0 || (reply[3] & 0x0f) != 0) return out;
        int answers = ((reply[6] & 0xff) << 8) | (reply[7] & 0xff);
        for (int i = 0; i < answers && pos > 0 && pos < reply.length; i++) {
            pos = skipName(reply, pos);
            if (pos < 0 || pos + 10 > reply.length) break;
            int type = ((reply[pos] & 0xff) << 8) | (reply[pos + 1] & 0xff);
            int len = ((reply[pos + 8] & 0xff) << 8) | (reply[pos + 9] & 0xff);
            pos += 10;
            if (pos + len > reply.length) break;
            if (type == TYPE_A && len == 4) out.add(new byte[] {reply[pos], reply[pos + 1], reply[pos + 2], reply[pos + 3]});
            pos += len;
        }
        return out;
    }

    /** A reply to `query` giving these addresses (none = "this name has no record of that type"). Null if the query is not a simple one. */
    public static byte[] answer(byte[] query, List<byte[]> ips, int ttlSeconds) {
        int qEnd = questionEnd(query);
        if (qEnd < 0) return null;
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(query[0]);
        o.write(query[1]); // the query's own id
        o.write(0x81); // a response, recursion desired
        o.write(0x80); // recursion available, no error
        o.write(0); o.write(1);
        o.write(0); o.write(ips.size());
        for (int i = 0; i < 4; i++) o.write(0);
        o.write(query, 12, qEnd - 12); // the question, echoed back exactly
        for (byte[] ip : ips) {
            o.write(0xc0); o.write(0x0c); // the name: "same as the question"
            o.write(0); o.write(TYPE_A);
            o.write(0); o.write(1);
            o.write(ttlSeconds >>> 24); o.write(ttlSeconds >>> 16); o.write(ttlSeconds >>> 8); o.write(ttlSeconds);
            o.write(0); o.write(4);
            o.write(ip, 0, 4);
        }
        return o.toByteArray();
    }
}
