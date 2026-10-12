package com.familymdm.agent;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Pulls the SNI hostname out of a buffered TLS ClientHello -- the hostname a HTTPS connection is
 * asking for, sent in the clear before encryption starts (TLS never encrypts its own handshake
 * negotiation), so this needs no certificate and no decryption, just reading the wire format.
 * Only ever looks at the client's first TLS record; a ClientHello that spans more than one
 * (unusual) reads as NOT_TLS rather than risk misreading past the buffer.
 */
final class TlsSni {
    enum Status {
        NEED_MORE,  // not enough bytes yet -- keep buffering
        NOT_TLS,    // definitely not a (single-record) TLS ClientHello -- stop buffering now
        FOUND,      // a plain hostname was parsed out
        NOT_FOUND,  // a well-formed ClientHello with no SNI extension (rare, but valid) -- stop
    }

    static final class Result {
        final Status status;
        final String hostname;
        Result(Status status, String hostname) {
            this.status = status;
            this.hostname = hostname;
        }
    }

    private TlsSni() {}

    static Result parse(byte[] buf, int len) {
        if (len < 5) return new Result(Status.NEED_MORE, null);
        if ((buf[0] & 0xFF) != 0x16) return new Result(Status.NOT_TLS, null); // not a TLS handshake record
        int recordLen = ((buf[3] & 0xFF) << 8) | (buf[4] & 0xFF);
        if (recordLen > 16384) return new Result(Status.NOT_TLS, null); // bigger than any valid TLS record -- not real TLS
        int recordEnd = 5 + recordLen;
        if (len < recordEnd) return new Result(Status.NEED_MORE, null); // valid so far, just not all here yet

        int p = 5;
        if (p >= recordEnd || (buf[p] & 0xFF) != 0x01) return new Result(Status.NOT_TLS, null); // not a ClientHello
        p += 4; // handshake type (1) + length (3) -- trust the record's own length instead
        p += 2; // client_version
        p += 32; // random
        if (p >= recordEnd) return new Result(Status.NOT_TLS, null);
        int sessionIdLen = buf[p] & 0xFF;
        p += 1 + sessionIdLen;
        if (p + 2 > recordEnd) return new Result(Status.NOT_TLS, null);
        int cipherSuitesLen = ((buf[p] & 0xFF) << 8) | (buf[p + 1] & 0xFF);
        p += 2 + cipherSuitesLen;
        if (p + 1 > recordEnd) return new Result(Status.NOT_TLS, null);
        int compressionLen = buf[p] & 0xFF;
        p += 1 + compressionLen;
        if (p + 2 > recordEnd) return new Result(Status.NOT_FOUND, null); // no room left for extensions at all
        int extensionsLen = ((buf[p] & 0xFF) << 8) | (buf[p + 1] & 0xFF);
        p += 2;
        int extensionsEnd = Math.min(p + extensionsLen, recordEnd);

        while (p + 4 <= extensionsEnd) {
            int extType = ((buf[p] & 0xFF) << 8) | (buf[p + 1] & 0xFF);
            int extLen = ((buf[p + 2] & 0xFF) << 8) | (buf[p + 3] & 0xFF);
            int extStart = p + 4;
            if (extStart + extLen > extensionsEnd) break;
            if (extType == 0x0000) { // server_name (RFC 6066 section 3)
                String host = parseServerNameExtension(buf, extStart, extStart + extLen);
                return host != null ? new Result(Status.FOUND, host) : new Result(Status.NOT_FOUND, null);
            }
            p = extStart + extLen;
        }
        return new Result(Status.NOT_FOUND, null);
    }

    private static String parseServerNameExtension(byte[] buf, int start, int end) {
        if (start + 2 > end) return null;
        int listLen = ((buf[start] & 0xFF) << 8) | (buf[start + 1] & 0xFF);
        int p = start + 2;
        int listEnd = Math.min(p + listLen, end);
        while (p + 3 <= listEnd) {
            int type = buf[p] & 0xFF;
            int nameLen = ((buf[p + 1] & 0xFF) << 8) | (buf[p + 2] & 0xFF);
            p += 3;
            if (p + nameLen > listEnd) return null;
            if (type == 0) { // host_name
                return new String(buf, p, nameLen, StandardCharsets.US_ASCII).toLowerCase(Locale.ROOT);
            }
            p += nameLen;
        }
        return null;
    }
}
