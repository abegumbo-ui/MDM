package com.familymdm.agent;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A local, DNS-only VPN -- not a general traffic proxy. Android is told this is the device's DNS
 * server; only packets addressed to that one fake address are routed into this tunnel at all, so
 * no other app traffic -- the actual content of any connection -- ever reaches this code. That's
 * deliberate: the only thing a bug here can break is DNS resolution itself, never anything already
 * connected or any data in flight.
 *
 * A query for a hostname on DnsBlocklist gets an immediate NXDOMAIN, synthesized right here, no
 * network round trip. Every other query is relayed byte-for-byte to a real public resolver and the
 * real answer relayed back, so nothing other than the blocked names is ever affected.
 *
 * IPv4 and UDP only, which covers the overwhelming majority of ordinary app DNS lookups -- not
 * every possible shape DNS traffic could take (see DnsBlocklist's own class doc for what that does
 * and doesn't reach).
 */
public class DnsFilterVpnService extends VpnService {
    private static final String TAG = "MdmAgent";
    private static final String TUN_ADDRESS = "10.111.222.1";
    private static final String FAKE_DNS = "10.111.222.2";
    private static final String UPSTREAM_DNS = "1.1.1.1";
    private static final int MTU = 1500;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ParcelFileDescriptor tun;
    private Thread readerThread;
    private final Object writeLock = new Object();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(2, notification());
        if (running.compareAndSet(false, true)) {
            try {
                establish();
                readerThread = new Thread(this::readLoop, "mdm-dns-filter");
                readerThread.start();
            } catch (Exception e) {
                Log.w(TAG, "DNS filter failed to start: " + e);
                running.set(false);
                stopSelf();
            }
        }
        return START_STICKY;
    }

    @Override
    public void onRevoke() {
        stopSelf();
    }

    @Override
    public void onDestroy() {
        running.set(false);
        if (readerThread != null) readerThread.interrupt();
        closeTun();
        super.onDestroy();
    }

    private void establish() throws IOException {
        Builder builder = new Builder()
                .setSession("Family MDM DNS Filter")
                .setMtu(MTU)
                .addAddress(TUN_ADDRESS, 30)
                .addDnsServer(FAKE_DNS)
                .addRoute(FAKE_DNS, 32)
                .setBlocking(true);
        // Critical: without this, this app's own network traffic -- including the DNS lookup it
        // needs to reach the dashboard at all -- gets captured by its own filter too. Any hiccup
        // in the relay logic above then breaks this app's own connectivity to the server, which
        // looks exactly like "the dashboard stopped hearing from the phone" (it did: this app
        // could no longer reach it) rather than like a DNS filter bug. The whole point of the
        // narrow /32 route above is that only DNS traffic ever enters this tunnel at all; this
        // makes sure that doesn't include this app's own.
        try {
            builder.addDisallowedApplication(getPackageName());
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            throw new IOException("could not exclude this app's own traffic: " + e.getMessage(), e);
        }
        tun = builder.establish();
        if (tun == null) throw new IOException("establish() returned null -- VPN permission not granted");
    }

    private void closeTun() {
        if (tun != null) {
            try {
                tun.close();
            } catch (IOException ignored) {
            }
            tun = null;
        }
    }

    private Notification notification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("dnsfilter", "DNS filter", NotificationManager.IMPORTANCE_LOW));
        return new Notification.Builder(this, "dnsfilter")
                .setContentTitle("MDM DNS filter")
                .setContentText("Filtering blocked hostnames")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .build();
    }

    private void readLoop() {
        FileInputStream in = new FileInputStream(tun.getFileDescriptor());
        byte[] buf = new byte[4096];
        while (running.get()) {
            try {
                int len = in.read(buf);
                if (len <= 0) continue;
                handlePacket(buf, len);
            } catch (IOException e) {
                if (running.get()) Log.w(TAG, "DNS filter read failed: " + e);
            } catch (Exception e) {
                // Never let one bad packet kill the loop -- the worst case for a parse failure is
                // one unanswered query, same as a dropped packet on a flaky network.
                Log.w(TAG, "DNS filter packet handling failed: " + e);
            }
        }
    }

    private void handlePacket(byte[] buf, int len) {
        if (len < 20) return; // shorter than any valid IPv4 header
        int version = (buf[0] >> 4) & 0xF;
        if (version != 4) return; // IPv6 not handled in this version
        int ihl = (buf[0] & 0xF) * 4;
        if (ihl < 20 || len < ihl + 8) return;
        int protocol = buf[9] & 0xFF;
        if (protocol != 17) return; // UDP only -- and per the route above, essentially the only thing that ever arrives here anyway
        int udpOffset = ihl;
        int srcPort = ((buf[udpOffset] & 0xFF) << 8) | (buf[udpOffset + 1] & 0xFF);
        int dstPort = ((buf[udpOffset + 2] & 0xFF) << 8) | (buf[udpOffset + 3] & 0xFF);
        int udpLength = ((buf[udpOffset + 4] & 0xFF) << 8) | (buf[udpOffset + 5] & 0xFF);
        if (dstPort != 53) return;
        int dnsOffset = udpOffset + 8;
        int dnsLength = udpLength - 8;
        if (dnsLength <= 12 || dnsOffset + dnsLength > len) return; // shorter than a DNS header plus a question

        byte[] srcIp = {buf[12], buf[13], buf[14], buf[15]};
        byte[] dstIp = {buf[16], buf[17], buf[18], buf[19]};

        String qname = parseQuestionName(buf, dnsOffset, dnsOffset + dnsLength);
        if (qname != null && DnsBlocklist.blocks(this, qname)) {
            byte[] response = buildNxdomain(buf, dnsOffset, dnsLength);
            // Reply's source is what the query was addressed to (the fake DNS server); its
            // destination is what the query came from (the querying app).
            if (response != null) writeDnsReply(dstIp, srcIp, dstPort, srcPort, response);
            return;
        }

        // Not blocked: relay to a real resolver on a protected socket (bypasses this VPN itself,
        // so the relay's own traffic doesn't loop back into this same tunnel) and relay the real
        // answer back. Off the read loop's own thread so one slow lookup never blocks the next query.
        byte[] query = new byte[dnsLength];
        System.arraycopy(buf, dnsOffset, query, 0, dnsLength);
        final byte[] origSrcIp = srcIp, origDstIp = dstIp;
        new Thread(() -> forward(origSrcIp, origDstIp, srcPort, dstPort, query), "mdm-dns-relay").start();
    }

    private void forward(byte[] origSrcIp, byte[] origDstIp, int origSrcPort, int origDstPort, byte[] query) {
        try (DatagramSocket socket = new DatagramSocket()) {
            protect(socket);
            socket.setSoTimeout(5000);
            InetAddress upstream = InetAddress.getByName(UPSTREAM_DNS);
            socket.send(new DatagramPacket(query, query.length, upstream, 53));
            byte[] respBuf = new byte[4096];
            DatagramPacket resp = new DatagramPacket(respBuf, respBuf.length);
            socket.receive(resp);
            byte[] answer = new byte[resp.getLength()];
            System.arraycopy(respBuf, 0, answer, 0, resp.getLength());
            writeDnsReply(origDstIp, origSrcIp, origDstPort, origSrcPort, answer);
        } catch (Exception e) {
            // An unanswered lookup here behaves exactly like one dropped on any flaky network --
            // the app's own DNS client retries or gives up; nothing else is affected.
        }
    }

    /** Builds a full IPv4+UDP packet and writes it into the tunnel as a reply. */
    private void writeDnsReply(byte[] replySrcIp, byte[] replyDstIp, int replySrcPort, int replyDstPort, byte[] dnsMessage) {
        byte[] packet = buildIpv4UdpPacket(replySrcIp, replyDstIp, replySrcPort, replyDstPort, dnsMessage);
        synchronized (writeLock) {
            try (FileOutputStream out = new FileOutputStream(tun.getFileDescriptor())) {
                out.write(packet);
            } catch (IOException e) {
                Log.w(TAG, "DNS filter write failed: " + e);
            }
        }
    }

    private static byte[] buildIpv4UdpPacket(byte[] srcIp, byte[] dstIp, int srcPort, int dstPort, byte[] payload) {
        int udpLen = 8 + payload.length;
        int totalLen = 20 + udpLen;
        byte[] p = new byte[totalLen];

        p[0] = 0x45; // version 4, IHL 5 (20-byte header, no options)
        p[1] = 0;
        p[2] = (byte) (totalLen >> 8);
        p[3] = (byte) totalLen;
        p[4] = 0;
        p[5] = 0; // identification
        p[6] = 0x40; // flags: don't fragment
        p[7] = 0;
        p[8] = 64; // TTL
        p[9] = 17; // protocol: UDP
        p[10] = 0;
        p[11] = 0; // header checksum, filled below
        System.arraycopy(srcIp, 0, p, 12, 4);
        System.arraycopy(dstIp, 0, p, 16, 4);
        int ipChecksum = checksum(p, 0, 20);
        p[10] = (byte) (ipChecksum >> 8);
        p[11] = (byte) ipChecksum;

        int u = 20;
        p[u] = (byte) (srcPort >> 8);
        p[u + 1] = (byte) srcPort;
        p[u + 2] = (byte) (dstPort >> 8);
        p[u + 3] = (byte) dstPort;
        p[u + 4] = (byte) (udpLen >> 8);
        p[u + 5] = (byte) udpLen;
        p[u + 6] = 0;
        p[u + 7] = 0; // checksum 0 ("not used") is explicitly valid for UDP over IPv4, per RFC 768

        System.arraycopy(payload, 0, p, u + 8, payload.length);
        return p;
    }

    /** Standard 16-bit ones'-complement checksum over [offset, offset+len). */
    private static int checksum(byte[] buf, int offset, int len) {
        long sum = 0;
        for (int i = offset; i < offset + len; i += 2) {
            int word = (buf[i] & 0xFF) << 8;
            if (i + 1 < offset + len) word |= (buf[i + 1] & 0xFF);
            sum += word;
        }
        while ((sum >> 16) != 0) sum = (sum & 0xFFFF) + (sum >> 16);
        return (int) (~sum & 0xFFFF);
    }

    /** The first (and, for ordinary app lookups, only) question's QNAME, dotted and lowercased.
     * Bails out (returns null) rather than guess on anything unexpected, including a compression
     * pointer -- not valid in a question section to begin with, and not worth the risk of
     * misreading past the end of the buffer for traffic this was never meant to handle. */
    private static String parseQuestionName(byte[] buf, int dnsStart, int dnsEnd) {
        int pos = dnsStart + 12; // past the 12-byte DNS header
        if (pos >= dnsEnd) return null;
        StringBuilder sb = new StringBuilder();
        int guard = 0;
        while (pos < dnsEnd && guard++ < 128) {
            int len = buf[pos] & 0xFF;
            if (len == 0) break;
            if ((len & 0xC0) == 0xC0) return null;
            pos++;
            if (pos + len > dnsEnd) return null;
            if (sb.length() > 0) sb.append('.');
            sb.append(new String(buf, pos, len, java.nio.charset.StandardCharsets.US_ASCII));
            pos += len;
        }
        return sb.length() == 0 ? null : sb.toString().toLowerCase(java.util.Locale.ROOT);
    }

    /** A minimal NXDOMAIN reply: the query's own ID, standard response flags, the question echoed
     * back verbatim (most resolvers and clients expect that to match their own query), zero
     * answer/authority/additional records. */
    private static byte[] buildNxdomain(byte[] buf, int dnsStart, int dnsLength) {
        int qStart = dnsStart + 12;
        int pos = qStart;
        int end = dnsStart + dnsLength;
        int guard = 0;
        while (pos < end && guard++ < 128) {
            int len = buf[pos] & 0xFF;
            pos++;
            if (len == 0) break;
            if ((len & 0xC0) == 0xC0) return null;
            pos += len;
        }
        pos += 4; // QTYPE + QCLASS
        if (pos > end) return null;
        int questionLen = pos - qStart;
        byte[] out = new byte[12 + questionLen];
        out[0] = buf[dnsStart];
        out[1] = buf[dnsStart + 1]; // echo the query's own transaction ID
        out[2] = (byte) 0x81; // QR=1 (response), Opcode=0, AA=0, TC=0, RD=1
        out[3] = (byte) 0x83; // RA=1, Z=0, RCODE=3 (NXDOMAIN)
        out[4] = 0;
        out[5] = 1; // QDCOUNT=1
        out[6] = 0;
        out[7] = 0; // ANCOUNT=0
        out[8] = 0;
        out[9] = 0; // NSCOUNT=0
        out[10] = 0;
        out[11] = 0; // ARCOUNT=0
        System.arraycopy(buf, qStart, out, 12, questionLen);
        return out;
    }
}
