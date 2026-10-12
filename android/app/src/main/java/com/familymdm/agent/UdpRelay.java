package com.familymdm.agent;

import android.net.VpnService;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Plain NAT for UDP that isn't a DNS query (DnsFilterVpnService keeps handling port 53 itself) --
 * mainly QUIC/HTTP3 on :443. Relayed transparently, unlogged: QUIC's own "ClientHello" equivalent
 * is encrypted (its keys derived from a public salt, not actually secret, but decoding it is a
 * meaningfully bigger parser than TlsSni and was left out of this build), so there's no hostname
 * here to decide on or to show in the Traffic Log -- only TCP connections on :443 are. Needed
 * regardless, once the VPN captures every destination: without it, QUIC traffic would simply stop
 * working rather than fall back to TCP the way it does on a network that blocks it outright.
 */
final class UdpRelay {
    private static final String TAG = "MdmAgent";
    private static final long IDLE_TIMEOUT_MS = 90_000;

    interface Writer {
        void write(byte[] packet);
    }

    private final VpnService service;
    private final Writer writer;
    private final ConcurrentHashMap<Integer, Entry> entries = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private Thread sweepThread;

    UdpRelay(VpnService service, Writer writer) {
        this.service = service;
        this.writer = writer;
        sweepThread = new Thread(this::sweepLoop, "mdm-udp-relay-sweep");
        sweepThread.setDaemon(true);
        sweepThread.start();
    }

    void shutdown() {
        running.set(false);
        if (sweepThread != null) sweepThread.interrupt();
        for (Entry e : new ArrayList<>(entries.values())) close(e);
        entries.clear();
    }

    void handle(byte[] clientIp, byte[] destIp, int clientPort, int destPort, byte[] payload) {
        try {
            Entry e = entries.get(clientPort);
            if (e == null) {
                DatagramSocket s = new DatagramSocket();
                service.protect(s);
                e = new Entry(s, clientIp, clientPort);
                Entry raced = entries.putIfAbsent(clientPort, e);
                if (raced != null) {
                    s.close();
                    e = raced;
                } else {
                    startReader(e);
                }
            }
            e.lastActivity = System.currentTimeMillis();
            e.socket.send(new DatagramPacket(payload, payload.length, InetAddress.getByAddress(destIp), destPort));
        } catch (Exception e) {
            // Dropped, same as any lost packet on a flaky network -- the app's own UDP protocol
            // (QUIC included) already has to tolerate that.
        }
    }

    private void startReader(Entry e) {
        new Thread(() -> {
            byte[] buf = new byte[2048];
            while (running.get() && !e.closed) {
                try {
                    DatagramPacket resp = new DatagramPacket(buf, buf.length);
                    e.socket.receive(resp);
                    byte[] answer = new byte[resp.getLength()];
                    System.arraycopy(buf, 0, answer, 0, resp.getLength());
                    byte[] fromIp = resp.getAddress().getAddress();
                    writer.write(NetUtil.buildUdpPacket(fromIp, e.clientIp, resp.getPort(), e.clientPort, answer));
                    e.lastActivity = System.currentTimeMillis();
                } catch (IOException ex) {
                    return; // socket closed (idle sweep or shutdown) -- this reader's job is done
                } catch (Exception ex) {
                    Log.w(TAG, "UDP relay reply failed: " + ex);
                }
            }
        }, "mdm-udp-relay").start();
    }

    private void sweepLoop() {
        while (running.get()) {
            try {
                Thread.sleep(30_000);
            } catch (InterruptedException e) {
                return;
            }
            long now = System.currentTimeMillis();
            for (Entry e : new ArrayList<>(entries.values())) {
                if (now - e.lastActivity > IDLE_TIMEOUT_MS) {
                    entries.remove(e.clientPort, e);
                    close(e);
                }
            }
        }
    }

    private void close(Entry e) {
        e.closed = true;
        try {
            e.socket.close();
        } catch (Exception ignored) {
        }
    }

    private static final class Entry {
        final DatagramSocket socket;
        final byte[] clientIp;
        final int clientPort;
        volatile boolean closed;
        volatile long lastActivity = System.currentTimeMillis();

        Entry(DatagramSocket socket, byte[] clientIp, int clientPort) {
            this.socket = socket;
            this.clientIp = clientIp;
            this.clientPort = clientPort;
        }
    }
}
