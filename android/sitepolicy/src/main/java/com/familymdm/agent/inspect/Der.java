package com.familymdm.agent.inspect;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * Just enough DER (the binary format certificates are written in) to build the certificates the
 * inspection proxy needs. Android has no public way to create a certificate and the Agent avoids
 * pulling in a large library for it, so this is written by hand. See CertBuilder.
 */
final class Der {
    private Der() {}

    static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) out.write(p, 0, p.length);
        return out.toByteArray();
    }

    static byte[] tlv(int tag, byte[] content) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        int n = content.length;
        if (n < 128) {
            out.write(n);
        } else if (n < 256) {
            out.write(0x81);
            out.write(n);
        } else if (n < 65536) {
            out.write(0x82);
            out.write(n >> 8);
            out.write(n & 0xff);
        } else {
            out.write(0x83);
            out.write(n >> 16);
            out.write((n >> 8) & 0xff);
            out.write(n & 0xff);
        }
        out.write(content, 0, n);
        return out.toByteArray();
    }

    static byte[] sequence(byte[]... parts) {
        return tlv(0x30, concat(parts));
    }

    static byte[] set(byte[]... parts) {
        return tlv(0x31, concat(parts));
    }

    static byte[] integer(BigInteger v) {
        return tlv(0x02, v.toByteArray()); // two's complement, so a positive number gets a leading 0 when needed
    }

    static byte[] integer(long v) {
        return integer(BigInteger.valueOf(v));
    }

    static byte[] bool(boolean v) {
        return tlv(0x01, new byte[] {(byte) (v ? 0xff : 0x00)});
    }

    static byte[] octetString(byte[] v) {
        return tlv(0x04, v);
    }

    /** A bit string whose bits are whole bytes (used for keys and signatures). */
    static byte[] bitString(byte[] v) {
        return bitString(v, 0);
    }

    static byte[] bitString(byte[] v, int unusedBits) {
        return tlv(0x03, concat(new byte[] {(byte) unusedBits}, v));
    }

    static byte[] utf8(String s) {
        return tlv(0x0c, s.getBytes(StandardCharsets.UTF_8));
    }

    static byte[] ia5(String s) {
        return tlv(0x16, s.getBytes(StandardCharsets.US_ASCII));
    }

    /** UTCTime, which certificates use for dates before 2050. */
    static byte[] utcTime(Date d) {
        SimpleDateFormat f = new SimpleDateFormat("yyMMddHHmmss'Z'");
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return tlv(0x17, f.format(d).getBytes(StandardCharsets.US_ASCII));
    }

    /** An object identifier written as dotted numbers, e.g. "2.5.4.3". */
    static byte[] oid(String dotted) {
        String[] parts = dotted.split("\\.");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(Integer.parseInt(parts[0]) * 40 + Integer.parseInt(parts[1]));
        for (int i = 2; i < parts.length; i++) {
            long v = Long.parseLong(parts[i]);
            byte[] tmp = new byte[10];
            int n = 0;
            tmp[n++] = (byte) (v & 0x7f);
            v >>= 7;
            while (v > 0) {
                tmp[n++] = (byte) ((v & 0x7f) | 0x80);
                v >>= 7;
            }
            for (int k = n - 1; k >= 0; k--) out.write(tmp[k]);
        }
        return tlv(0x06, out.toByteArray());
    }

    /** One X.509 name: a list of (oid, text) pairs, e.g. common name and organisation. */
    static byte[] name(String... oidAndText) {
        byte[][] rdns = new byte[oidAndText.length / 2][];
        for (int i = 0; i < rdns.length; i++) {
            rdns[i] = set(sequence(oid(oidAndText[2 * i]), utf8(oidAndText[2 * i + 1])));
        }
        return sequence(rdns);
    }

    /** One certificate extension; `value` is the already-encoded extension contents. */
    static byte[] extension(String oid, boolean critical, byte[] value) {
        return critical
                ? sequence(oid(oid), bool(true), octetString(value))
                : sequence(oid(oid), octetString(value));
    }
}
