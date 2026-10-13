package com.familymdm.lockdown;

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
 * records every DNS query and every TLS SNI hostname this sees, blocked or not, for this app's
 * own Traffic Log screen to show -- no dashboard here, nothing leaves the phone.
 *
 * A second, independent layer sits in front of all of that: NetworkBlocklist, keyed by app
 * instead of hostname. A network-blocked app's queries/connections only ever check against its
 * own PerAppDnsAllowlist entry, never DnsBlocklist/DnsAllowlist at all -- see TcpRelay's SNI
 * handling and this class's own DNS handling for exactly where the two layers diverge.
 */
public class DnsFilterVpnService extends VpnService {
    private static final String TAG = "Lockdown";
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
    private Thread establishThread;

    // True only once establish() has actually succeeded and the tunnel is up -- MainActivity's
    // applyDnsFilterLive() checks this (not just "did we ask the service to start") so a failed
    // attempt gets retried on the next tap instead of being silently considered done forever.
    private static final AtomicBoolean active = new AtomicBoolean(false);

    static boolean isActive() {
        return active.get();
    }

    // establish() can still fail the first time this ever runs -- MainActivity's own one-time VPN
    // consent flow (ensureVpnConsentIfNeeded()) and this service's first start can race, so the
    // very first establish() can see the permission not landed yet. Retrying a handful of times a
    // second apart (rather than giving up on the first failure) rides out that race without
    // MainActivity needing to notice and start a whole new service.
    private static final int MAX_ESTABLISH_ATTEMPTS = 6;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // onStartCommand() itself always runs on the main thread -- that's Android's own Service
        // contract, not something any caller's own threading can change. establish() and its
        // retries used to run directly in here, meaning every one of them blocked the UI thread
        // regardless of what thread asked this service to start. Builder.establish() is a real,
        // possibly slow system call (VPN setup, routed through Knox's own policy layer on this
        // device) -- kicking the whole thing off on its own thread immediately is what actually
        // keeps this service's own startup from ever freezing the app.
        startForeground(2, notification());
        if (running.compareAndSet(false, true)) {
            establishThread = new Thread(() -> attemptEstablish(1), "mdm-vpn-establish");
            establishThread.start();
        }
        return START_STICKY;
    }

    /** Runs entirely on establishThread, including every retry -- Thread.sleep() between attempts
     * instead of a main-Looper Handler, so nothing here ever touches the UI thread. */
    private void attemptEstablish(int attempt) {
        try {
            establish();
            active.set(true);
            uidResolver = new UidResolver(this);
            tcpRelay = new TcpRelay(this, this, this::writePacket);
            udpRelay = new UdpRelay(this, this, this::writePacket);
            readerThread = new Thread(this::readLoop, "mdm-dns-filter");
            readerThread.start();
        } catch (Exception e) {
            Log.w(TAG, "DNS filter failed to start (attempt " + attempt + "/" + MAX_ESTABLISH_ATTEMPTS + "): " + e);
            if (attempt < MAX_ESTABLISH_ATTEMPTS && running.get()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                    return;
                }
                if (running.get()) attemptEstablish(attempt + 1);
            } else {
                running.set(false);
                stopSelf();
            }
        }
    }

    /** No longer registered with Android as always-on -- that OS-level supervision (auto-
     * reconnect, the persistent "disconnected" banner) is specifically what's being dropped here,
     * on the theory that it's interacting badly with this device's Knox layer. Self-managing the
     * same guarantee means this can't just stop: a revoke (someone taps "Disconnect" on the
     * notification, or the OS pulls the permission for its own reasons) is treated as "reconnect
     * immediately," the same self-healing attemptEstablish() already does for a failed first
     * connection. Lockdown's kiosk already keeps the real Settings app -- and so the system VPN
     * screen -- unreachable; this is the other path the connection could otherwise be dropped
     * through, so it gets the same treatment. */
    // If something keeps revoking the tunnel right after it connects (this device's Knox layer,
    // or Android's own network validation, not liking it for some reason), reconnecting instantly
    // every single time is itself a risk: onCreate()/onStartCommand()/onRevoke() are all Service
    // callbacks Android dispatches on the UI thread, no way around that, and a tight connect-
    // revoke-reconnect loop would hammer it with those back to back -- not one long block, but
    // possibly enough rapid-fire UI-thread work to trip the same "isn't responding" watchdog
    // anyway. This caps how many revokes get an instant reconnect before backing off instead of
    // spinning.
    private int revokeCount;
    private long revokeWindowStart;
    private static final int MAX_REVOKES_PER_WINDOW = 5;
    private static final long REVOKE_WINDOW_MS = 30_000;

    @Override
    public void onRevoke() {
        active.set(false);
        closeTun();
        if (!running.get()) return;
        long now = System.currentTimeMillis();
        synchronized (this) {
            if (now - revokeWindowStart > REVOKE_WINDOW_MS) {
                revokeWindowStart = now;
                revokeCount = 0;
            }
            revokeCount++;
        }
        if (revokeCount > MAX_REVOKES_PER_WINDOW) {
            Log.w(TAG, "Revoked " + revokeCount + " times in " + (REVOKE_WINDOW_MS / 1000)
                    + "s -- backing off instead of reconnecting instantly");
            running.set(false);
            stopSelf();
            return;
        }
        establishThread = new Thread(() -> attemptEstablish(1), "mdm-vpn-reconnect");
        establishThread.start();
    }

    @Override
    public void onDestroy() {
        running.set(false);
        active.set(false);
        if (establishThread != null) establishThread.interrupt();
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
        // Critical: without this, this app's own network traffic gets captured by its own filter
        // too (this app has no server to reach, but Lockdown's own Settings menu and anything
        // else it does locally shouldn't depend on this filter's relay logic being bug-free).
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
        nm.createNotificationChannel(new NotificationChannel("dnsfilter", "DNS filter", NotificationManager.IMPORTANCE_MIN));
        return new Notification.Builder(this, "dnsfilter")
                .setContentTitle("Keeping you safe")
                .setContentText("Protection is active")
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
        int uid = -1;
        String pkg = null;
        try {
            uid = uidResolver.uidFor(NetUtil.IPPROTO_UDP,
                    new InetSocketAddress(InetAddress.getByAddress(srcIp), srcPort),
                    new InetSocketAddress(InetAddress.getByAddress(dstIp), 53));
            pkg = uidResolver.packageFor(uid);
        } catch (Exception ignored) {
        }
        boolean blocked;
        if (qname == null) {
            blocked = false;
        } else if (NetworkBlocklist.isBlocked(this, pkg)) {
            // A network-blocked app only ever gets what's on its own allow list -- the
            // device-wide DnsAllowlist does NOT apply here, on purpose (see PerAppDnsAllowlist).
            blocked = !PerAppDnsAllowlist.allows(this, pkg, qname);
        } else {
            blocked = DnsBlocklist.blocks(this, qname) && !DnsAllowlist.allows(this, qname);
        }
        if (qname != null) TrafficLog.add(this, TrafficLog.KIND_DNS, qname, uid, uidResolver.labelFor(uid), blocked);

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
