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
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The device's DNS blocklist AND its TLS-SNI-aware traffic log, both in one VPN -- not two
 * separate services, because both need the same thing: a look at traffic before it leaves the
 * phone. Captures every IPv4 route (0.0.0.0/0), which is a real change from this class's first
 * version (DNS-only, one fake address, nothing else could ever reach it) -- now a bug here CAN
 * affect any other app's connectivity, the tradeoff that came with being able to see a TLS
 * handshake's SNI hostname at all (it's sent for any HTTPS connection to any destination, not
 * just ones this already resolved through its own DNS sinkhole). See TcpRelay and UdpRelay for
 * the actual relaying; this class is the TUN read loop and the DNS sinkhole itself.
 *
 * What's NOT covered: IPv6 (no IPv6 address was added to the tun, so IPv6 traffic bypasses this
 * filter entirely -- not logged, not blockable), ICMP (ping stops working while this runs), and
 * QUIC/HTTP3's own SNI-equivalent (UdpRelay relays it, but doesn't decode it -- see its own doc).
 *
 * A query/connection whose hostname matches DnsBlocklist is stopped before it ever reaches its
 * destination -- an NXDOMAIN for DNS, a connection that's never even opened for TLS. Every other
 * query or connection is relayed byte-for-byte to where it was actually headed. TrafficLog.add()
 * records every DNS query and every TLS SNI hostname this sees, blocked or not, for the
 * Administrator panel's own Traffic Log (Blocking > Traffic Log) to show.
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
    private TcpRelay tcpRelay;
    private UdpRelay udpRelay;
    private UidResolver uidResolver;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(2, notification());
        if (running.compareAndSet(false, true)) {
            try {
                establish();
                uidResolver = new UidResolver(this);
                tcpRelay = new TcpRelay(this, this, this::writePacket);
                udpRelay = new UdpRelay(this, this::writePacket);
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
        if (tcpRelay != null) tcpRelay.shutdown();
        if (udpRelay != null) udpRelay.shutdown();
        closeTun();
        super.onDestroy();
    }

    private void establish() throws IOException {
        Builder builder = new Builder()
                .setSession("Family MDM DNS Filter")
                .setMtu(MTU)
                .addAddress(TUN_ADDRESS, 30)
                .addDnsServer(FAKE_DNS)
                .addRoute("0.0.0.0", 0)
                .setBlocking(true);
        // Critical: without this, this app's own network traffic -- including the DNS lookup it
        // needs to reach the dashboard at all -- gets captured by its own filter too. Any hiccup
        // in the relay logic above then breaks this app's own connectivity to the server, which
        // looks exactly like "the dashboard stopped hearing from the phone" (it did: this app
        // could no longer reach it) rather than like a DNS filter bug.
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
                // one unanswered query or one dropped connection, same as a hiccup on any flaky
                // network, not an outage for anything else.
                Log.w(TAG, "DNS filter packet handling failed: " + e);
            }
        }
    }

    private void writePacket(byte[] packet) {
        synchronized (writeLock) {
            try (FileOutputStream out = new FileOutputStream(tun.getFileDescriptor())) {
                out.write(packet);
            } catch (IOException e) {
                Log.w(TAG, "DNS filter write failed: " + e);
            }
        }
    }

    private void handlePacket(byte[] buf, int len) {
        if (len < 20) return; // shorter than any valid IPv4 header
        int version = (buf[0] >> 4) & 0xF;
        if (version != 4) return; // IPv6 not handled -- see class doc
        int ihl = (buf[0] & 0xF) * 4;
        if (ihl < 20 || len < ihl) return;
        int protocol = buf[9] & 0xFF;

        byte[] srcIp = {buf[12], buf[13], buf[14], buf[15]};
        byte[] dstIp = {buf[16], buf[17], buf[18], buf[19]};

        if (protocol == NetUtil.IPPROTO_TCP) {
            tcpRelay.handle(buf, ihl, len, srcIp, dstIp);
            return;
        }
        if (protocol != NetUtil.IPPROTO_UDP) return; // ICMP and anything else: not handled, see class doc
        if (len < ihl + 8) return;

        int udpOffset = ihl;
        int srcPort = ((buf[udpOffset] & 0xFF) << 8) | (buf[udpOffset + 1] & 0xFF);
        int dstPort = ((buf[udpOffset + 2] & 0xFF) << 8) | (buf[udpOffset + 3] & 0xFF);
        int udpLength = ((buf[udpOffset + 4] & 0xFF) << 8) | (buf[udpOffset + 5] & 0xFF);

        if (dstPort != 53) {
            // Not a DNS query -- plain NAT relay (QUIC on :443 is the common case).
            int payloadLen = Math.min(udpLength, len - udpOffset) - 8;
            if (payloadLen <= 0) return;
            byte[] payload = new byte[payloadLen];
            System.arraycopy(buf, udpOffset + 8, payload, 0, payloadLen);
            udpRelay.handle(srcIp, dstIp, srcPort, dstPort, payload);
            return;
        }

        int dnsOffset = udpOffset + 8;
        int dnsLength = udpLength - 8;
        if (dnsLength <= 12 || dnsOffset + dnsLength > len) return; // shorter than a DNS header plus a question

        String qname = parseQuestionName(buf, dnsOffset, dnsOffset + dnsLength);
        boolean blocked = qname != null && DnsBlocklist.blocks(this, qname);
        logDnsQuery(qname, srcIp, srcPort, dstIp, blocked);

        if (blocked) {
            byte[] response = buildNxdomain(buf, dnsOffset, dnsLength);
            // Reply's source is what the query was addressed to; its destination is what the
            // query came from (the querying app).
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

    private void logDnsQuery(String qname, byte[] clientIp, int clientPort, byte[] destIp, boolean blocked) {
        if (qname == null) return;
        try {
            int uid = uidResolver.uidFor(NetUtil.IPPROTO_UDP,
                    new InetSocketAddress(InetAddress.getByAddress(clientIp), clientPort),
                    new InetSocketAddress(InetAddress.getByAddress(destIp), 53));
            String label = uidResolver.labelFor(uid);
            TrafficLog.add(this, TrafficLog.KIND_DNS, qname, uid, label, blocked);
        } catch (Exception e) {
            TrafficLog.add(this, TrafficLog.KIND_DNS, qname, -1, "Unknown app", blocked);
        }
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
        writePacket(NetUtil.buildUdpPacket(replySrcIp, replyDstIp, replySrcPort, replyDstPort, dnsMessage));
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
