package com.familymdm.agent;

import android.content.Context;
import android.net.VpnService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A minimal, hand-rolled TCP endpoint for DnsFilterVpnService's TUN -- not a real TCP/IP stack
 * (no retransmission, no congestion control, no real flow-control window, no out-of-order
 * handling), just enough of RFC 793's state machine to fake a connection to whatever's on the
 * other end of the tun, relay bytes to a real socket once a connection is let through, and feed
 * the first segment of every :443 connection through TlsSni before deciding that.
 *
 * Every connection this sees is faked a SYN-ACK immediately, before anything about it is known --
 * SNI never appears until the client's first data segment, after the handshake. A :443 connection
 * only actually talks to its real destination once the SNI decision is made (or gives up trying,
 * past SNI_PEEK_CAP bytes); a blocked one never connects to its real destination at all, same as
 * the DNS sinkhole's own "never even talks to it" shape. Every other port connects immediately
 * and relays transparently, unlogged -- this exists to keep the rest of the device's internet
 * working once the VPN captures all of it, not to watch it.
 */
final class TcpRelay {
    private static final int MSS = 1400; // safely under MTU(1500) minus the 40 bytes of IPv4+TCP headers
    private static final int SNI_PEEK_CAP = 16384; // a TLS record can't exceed this anyway
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final long IDLE_TIMEOUT_MS = 120_000;

    interface Writer {
        void write(byte[] packet);
    }

    private final VpnService service;
    private final Context context;
    private final Writer writer;
    private final UidResolver uidResolver;
    private final ConcurrentHashMap<String, Tcb> tcbs = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Random rand = new Random();
    private Thread sweepThread;

    TcpRelay(VpnService service, Context context, Writer writer) {
        this.service = service;
        this.context = context;
        this.writer = writer;
        this.uidResolver = new UidResolver(context);
        sweepThread = new Thread(this::sweepLoop, "mdm-tcp-relay-sweep");
        sweepThread.setDaemon(true);
        sweepThread.start();
    }

    void shutdown() {
        running.set(false);
        if (sweepThread != null) sweepThread.interrupt();
        for (Tcb tcb : new ArrayList<>(tcbs.values())) removeAndClose(tcb);
    }

    /** buf/len is the whole IPv4 packet; ihl is its header length (TCP starts right after it). */
    void handle(byte[] buf, int ihl, int len, byte[] srcIp, byte[] dstIp) {
        int t = ihl;
        if (len < t + 20) return;
        int srcPort = u16(buf, t);
        int dstPort = u16(buf, t + 2);
        long seq = u32(buf, t + 4);
        int dataOffset = ((buf[t + 12] >> 4) & 0xF) * 4;
        int flags = buf[t + 13] & 0xFF;
        if (dataOffset < 20) return;
        int payloadOff = t + dataOffset;
        int payloadLen = Math.max(0, len - payloadOff);

        String key = srcPort + ">" + ipString(dstIp) + ":" + dstPort;
        boolean isSyn = (flags & NetUtil.TCP_SYN) != 0;
        boolean isAck = (flags & NetUtil.TCP_ACK) != 0;
        boolean isFin = (flags & NetUtil.TCP_FIN) != 0;
        boolean isRst = (flags & NetUtil.TCP_RST) != 0;

        if (isRst) {
            Tcb tcb = tcbs.get(key);
            if (tcb != null) removeAndClose(tcb);
            return;
        }

        if (isSyn && !isAck) {
            Tcb existing = tcbs.get(key);
            if (existing != null) removeAndClose(existing);
            Tcb tcb = new Tcb(key, srcIp, srcPort, dstIp, dstPort);
            tcb.ack = (seq + 1) & 0xFFFFFFFFL;
            tcb.seq = rand.nextInt(Integer.MAX_VALUE) & 0xFFFFFFFFL;
            tcbs.put(key, tcb);
            writer.write(NetUtil.buildTcpPacket(dstIp, srcIp, dstPort, srcPort, tcb.seq, tcb.ack,
                    NetUtil.TCP_SYN | NetUtil.TCP_ACK, 65535, null));
            tcb.seq = (tcb.seq + 1) & 0xFFFFFFFFL;
            if (dstPort != 443) startConnect(tcb);
            return;
        }

        Tcb tcb = tcbs.get(key);
        if (tcb == null) return; // unknown/already-closed connection -- nothing to do with it
        tcb.lastActivity = System.currentTimeMillis();

        if (payloadLen > 0) {
            byte[] payload = new byte[payloadLen];
            System.arraycopy(buf, payloadOff, payload, 0, payloadLen);
            tcb.ack = (tcb.ack + payloadLen) & 0xFFFFFFFFL;
            // Ack the data right away so the client's own stack doesn't retransmit it -- there's
            // no real flow control here, only this.
            writer.write(NetUtil.buildTcpPacket(dstIp, srcIp, dstPort, srcPort, tcb.seq, tcb.ack, NetUtil.TCP_ACK, 65535, null));
            onData(tcb, payload);
        }

        if (isFin) {
            tcb.ack = (tcb.ack + 1) & 0xFFFFFFFFL;
            tcb.sawFinFromClient = true;
            writer.write(NetUtil.buildTcpPacket(dstIp, srcIp, dstPort, srcPort, tcb.seq, tcb.ack,
                    NetUtil.TCP_FIN | NetUtil.TCP_ACK, 65535, null));
            tcb.seq = (tcb.seq + 1) & 0xFFFFFFFFL;
            try {
                if (tcb.socket != null) tcb.socket.shutdownOutput();
            } catch (Exception ignored) {
            }
            if (tcb.downlinkDone) removeAndClose(tcb);
        }
    }

    private void onData(Tcb tcb, byte[] payload) {
        synchronized (tcb) {
            if (tcb.removed) return;
            if (tcb.established) {
                try {
                    tcb.socketOut.write(payload);
                    tcb.socketOut.flush();
                } catch (IOException e) {
                    resetAndRemove(tcb);
                }
                return;
            }
            try {
                tcb.pending.write(payload);
            } catch (IOException ignored) {
            }
            if (tcb.destPort == 443 && !tcb.sniChecked) {
                byte[] peek = tcb.pending.toByteArray();
                TlsSni.Result r = TlsSni.parse(peek, peek.length);
                if (r.status == TlsSni.Status.NEED_MORE && peek.length < SNI_PEEK_CAP) {
                    return; // keep buffering, nothing decided yet
                }
                tcb.sniChecked = true;
                String hostname = r.status == TlsSni.Status.FOUND ? r.hostname : null;
                boolean blocked = hostname != null && DnsBlocklist.blocks(context, hostname);
                logAndMaybeBlock(tcb, hostname, blocked);
                if (blocked) return; // logAndMaybeBlock already RST + removed it
            }
            if (!tcb.connectStarted) startConnect(tcb);
        }
    }

    private void logAndMaybeBlock(Tcb tcb, String hostname, boolean blocked) {
        int uid = uidResolver.uidFor(NetUtil.IPPROTO_TCP,
                new InetSocketAddress(ipToInet(tcb.clientIp), tcb.clientPort),
                new InetSocketAddress(ipToInet(tcb.destIp), tcb.destPort));
        String label = uidResolver.labelFor(uid);
        TrafficLog.add(context, TrafficLog.KIND_TLS, hostname == null ? "(no SNI seen)" : hostname, uid, label, blocked);
        if (blocked) {
            writer.write(NetUtil.buildTcpPacket(tcb.destIp, tcb.clientIp, tcb.destPort, tcb.clientPort,
                    tcb.seq, tcb.ack, NetUtil.TCP_RST | NetUtil.TCP_ACK, 0, null));
            removeAndClose(tcb);
        }
    }

    private void startConnect(Tcb tcb) {
        tcb.connectStarted = true;
        new Thread(() -> {
            Socket s = new Socket();
            try {
                service.protect(s);
                s.connect(new InetSocketAddress(ipToInet(tcb.destIp), tcb.destPort), CONNECT_TIMEOUT_MS);
                synchronized (tcb) {
                    if (tcb.removed) {
                        s.close();
                        return;
                    }
                    tcb.socket = s;
                    tcb.socketOut = s.getOutputStream();
                    // The flush has to happen inside this same lock: the instant established=true
                    // is visible, onData() on another thread will write straight to socketOut
                    // itself, and two threads writing the same OutputStream unsynchronized could
                    // interleave their bytes. Holding the lock across this write is what keeps
                    // whatever was buffered while connecting strictly first.
                    byte[] toSend = tcb.pending.toByteArray();
                    tcb.pending.reset();
                    if (toSend.length > 0) {
                        tcb.socketOut.write(toSend);
                        tcb.socketOut.flush();
                    }
                    tcb.established = true;
                }
                downlinkLoop(tcb, s);
            } catch (Exception e) {
                resetAndRemove(tcb);
            }
        }, "mdm-tcp-relay").start();
    }

    /** Reads whatever the real destination sends back and feeds it into the tun as TCP data
     * segments, until it closes (clean EOF -> our own FIN) or errors (-> RST). */
    private void downlinkLoop(Tcb tcb, Socket s) {
        byte[] buf = new byte[MSS];
        try {
            InputStream in = s.getInputStream();
            int n;
            while ((n = in.read(buf)) > 0) {
                byte[] chunk = (n == buf.length) ? buf : Arrays.copyOf(buf, n);
                writer.write(NetUtil.buildTcpPacket(tcb.destIp, tcb.clientIp, tcb.destPort, tcb.clientPort,
                        tcb.seq, tcb.ack, NetUtil.TCP_ACK | NetUtil.TCP_PSH, 65535, chunk));
                tcb.seq = (tcb.seq + n) & 0xFFFFFFFFL;
                tcb.lastActivity = System.currentTimeMillis();
            }
            writer.write(NetUtil.buildTcpPacket(tcb.destIp, tcb.clientIp, tcb.destPort, tcb.clientPort,
                    tcb.seq, tcb.ack, NetUtil.TCP_FIN | NetUtil.TCP_ACK, 65535, null));
            tcb.seq = (tcb.seq + 1) & 0xFFFFFFFFL;
            tcb.downlinkDone = true;
            if (tcb.sawFinFromClient) removeAndClose(tcb);
        } catch (IOException e) {
            resetAndRemove(tcb);
        }
    }

    private void resetAndRemove(Tcb tcb) {
        if (tcb.removed) return;
        writer.write(NetUtil.buildTcpPacket(tcb.destIp, tcb.clientIp, tcb.destPort, tcb.clientPort,
                tcb.seq, tcb.ack, NetUtil.TCP_RST | NetUtil.TCP_ACK, 0, null));
        removeAndClose(tcb);
    }

    private void removeAndClose(Tcb tcb) {
        tcb.removed = true;
        tcbs.remove(tcb.key, tcb);
        try {
            if (tcb.socket != null) tcb.socket.close();
        } catch (Exception ignored) {
        }
    }

    private void sweepLoop() {
        while (running.get()) {
            try {
                Thread.sleep(30_000);
            } catch (InterruptedException e) {
                return;
            }
            long now = System.currentTimeMillis();
            for (Tcb tcb : new ArrayList<>(tcbs.values())) {
                if (now - tcb.lastActivity > IDLE_TIMEOUT_MS) removeAndClose(tcb);
            }
        }
    }

    private static int u16(byte[] b, int o) {
        return ((b[o] & 0xFF) << 8) | (b[o + 1] & 0xFF);
    }

    private static long u32(byte[] b, int o) {
        return ((b[o] & 0xFFL) << 24) | ((b[o + 1] & 0xFFL) << 16) | ((b[o + 2] & 0xFFL) << 8) | (b[o + 3] & 0xFFL);
    }

    private static String ipString(byte[] ip) {
        return (ip[0] & 0xFF) + "." + (ip[1] & 0xFF) + "." + (ip[2] & 0xFF) + "." + (ip[3] & 0xFF);
    }

    private static InetAddress ipToInet(byte[] ip) {
        try {
            return InetAddress.getByAddress(ip); // a raw 4-byte address -- never a real DNS lookup
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final class Tcb {
        final String key;
        final byte[] clientIp;
        final int clientPort;
        final byte[] destIp;
        final int destPort;
        volatile long seq;
        volatile long ack;
        volatile boolean sawFinFromClient;
        volatile boolean downlinkDone;
        volatile boolean established;
        volatile boolean connectStarted;
        volatile boolean sniChecked;
        volatile boolean removed;
        volatile long lastActivity = System.currentTimeMillis();
        Socket socket;
        OutputStream socketOut;
        final java.io.ByteArrayOutputStream pending = new java.io.ByteArrayOutputStream();

        Tcb(String key, byte[] clientIp, int clientPort, byte[] destIp, int destPort) {
            this.key = key;
            this.clientIp = clientIp;
            this.clientPort = clientPort;
            this.destIp = destIp;
            this.destPort = destPort;
        }
    }
}
