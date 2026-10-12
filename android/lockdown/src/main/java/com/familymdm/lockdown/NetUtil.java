package com.familymdm.lockdown;

/**
 * Raw IPv4/TCP/UDP packet building and checksums shared by DnsFilterVpnService's DNS sinkhole,
 * its general UDP NAT, and TcpRelay -- the same three pieces of math (header layout, the 16-bit
 * ones'-complement checksum, TCP's extra pseudo-header) would otherwise be duplicated three times.
 */
final class NetUtil {
    static final int IPPROTO_TCP = 6;
    static final int IPPROTO_UDP = 17;

    static final int TCP_FIN = 0x01;
    static final int TCP_SYN = 0x02;
    static final int TCP_RST = 0x04;
    static final int TCP_PSH = 0x08;
    static final int TCP_ACK = 0x10;

    private NetUtil() {}

    /** Standard 16-bit ones'-complement checksum over [offset, offset+len). */
    static int checksum(byte[] buf, int offset, int len) {
        long sum = 0;
        for (int i = offset; i < offset + len; i += 2) {
            int word = (buf[i] & 0xFF) << 8;
            if (i + 1 < offset + len) word |= (buf[i + 1] & 0xFF);
            sum += word;
        }
        while ((sum >> 16) != 0) sum = (sum & 0xFFFF) + (sum >> 16);
        return (int) (~sum & 0xFFFF);
    }

    /** TCP's checksum covers a 12-byte pseudo-header (src/dst IP, protocol, segment length) ahead
     * of the real segment too -- unlike UDP's, this one is mandatory; a receiving OS stack can
     * reject a segment whose checksum doesn't account for it. */
    private static int tcpChecksum(byte[] srcIp, byte[] dstIp, byte[] full, int tcpOffset, int tcpLen) {
        byte[] pseudo = new byte[12 + tcpLen + (tcpLen % 2)];
        System.arraycopy(srcIp, 0, pseudo, 0, 4);
        System.arraycopy(dstIp, 0, pseudo, 4, 4);
        pseudo[9] = IPPROTO_TCP;
        pseudo[10] = (byte) (tcpLen >> 8);
        pseudo[11] = (byte) tcpLen;
        System.arraycopy(full, tcpOffset, pseudo, 12, tcpLen);
        return checksum(pseudo, 0, pseudo.length);
    }

    /** A 20-byte IPv4 header (no options), checksum already filled in, followed by `payloadLen`
     * zeroed bytes for the caller to fill in starting at offset 20. */
    private static byte[] ipv4Header(byte[] srcIp, byte[] dstIp, int protocol, int payloadLen) {
        int totalLen = 20 + payloadLen;
        byte[] p = new byte[totalLen];
        p[0] = 0x45; // version 4, IHL 5
        p[6] = 0x40; // flags: don't fragment
        p[8] = 64; // TTL
        p[9] = (byte) protocol;
        System.arraycopy(srcIp, 0, p, 12, 4);
        System.arraycopy(dstIp, 0, p, 16, 4);
        p[2] = (byte) (totalLen >> 8);
        p[3] = (byte) totalLen;
        int ipChecksum = checksum(p, 0, 20);
        p[10] = (byte) (ipChecksum >> 8);
        p[11] = (byte) ipChecksum;
        return p;
    }

    static byte[] buildUdpPacket(byte[] srcIp, byte[] dstIp, int srcPort, int dstPort, byte[] payload) {
        int udpLen = 8 + payload.length;
        byte[] p = ipv4Header(srcIp, dstIp, IPPROTO_UDP, udpLen);
        int u = 20;
        p[u] = (byte) (srcPort >> 8);
        p[u + 1] = (byte) srcPort;
        p[u + 2] = (byte) (dstPort >> 8);
        p[u + 3] = (byte) dstPort;
        p[u + 4] = (byte) (udpLen >> 8);
        p[u + 5] = (byte) udpLen;
        // p[u+6], p[u+7] (checksum) left at 0 -- explicitly valid for UDP over IPv4, RFC 768.
        System.arraycopy(payload, 0, p, u + 8, payload.length);
        return p;
    }

    /** seq/ack are tracked as longs (unsigned 32-bit arithmetic doesn't fit a Java int) but only
     * their low 32 bits are ever written into the wire format. */
    static byte[] buildTcpPacket(byte[] srcIp, byte[] dstIp, int srcPort, int dstPort,
                                  long seq, long ack, int flags, int window, byte[] payload) {
        int payloadLen = payload == null ? 0 : payload.length;
        int tcpLen = 20 + payloadLen;
        byte[] p = ipv4Header(srcIp, dstIp, IPPROTO_TCP, tcpLen);
        int t = 20;
        p[t] = (byte) (srcPort >> 8);
        p[t + 1] = (byte) srcPort;
        p[t + 2] = (byte) (dstPort >> 8);
        p[t + 3] = (byte) dstPort;
        p[t + 4] = (byte) (seq >> 24);
        p[t + 5] = (byte) (seq >> 16);
        p[t + 6] = (byte) (seq >> 8);
        p[t + 7] = (byte) seq;
        p[t + 8] = (byte) (ack >> 24);
        p[t + 9] = (byte) (ack >> 16);
        p[t + 10] = (byte) (ack >> 8);
        p[t + 11] = (byte) ack;
        p[t + 12] = 0x50; // data offset: 5 words (20 bytes), no options
        p[t + 13] = (byte) flags;
        p[t + 14] = (byte) (window >> 8);
        p[t + 15] = (byte) window;
        if (payloadLen > 0) System.arraycopy(payload, 0, p, t + 20, payloadLen);
        int cksum = tcpChecksum(srcIp, dstIp, p, t, tcpLen);
        p[t + 16] = (byte) (cksum >> 8);
        p[t + 17] = (byte) cksum;
        return p;
    }
}
