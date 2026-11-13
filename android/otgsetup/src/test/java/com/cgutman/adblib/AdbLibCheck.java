package com.cgutman.adblib;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A check of the ADB library (as changed for the phone-to-phone setup app) against a pretend "other phone" that speaks the ADB protocol
 * over in-memory pipes: connecting with the key approval step, running commands whose output and close arrive together (the case the
 * original library lost), streaming an install, and a refused open. Run: javac then java com.cgutman.adblib.AdbLibCheck (exit 1 on failure).
 * It cannot test the USB side (UsbTransport), only the protocol on top of it.
 */
public class AdbLibCheck {
    static int failures = 0;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "PASS  " : "FAIL  ") + what);
        if (!ok) failures++;
    }

    // ---------- the pretend phone ----------

    static final byte[] BANNER = "device::ro.product.model=SM-TEST;\0".getBytes();
    static final AtomicInteger installedBytes = new AtomicInteger();

    static void phone(InputStream in, OutputStream out) throws Exception {
        AdbProtocol.AdbMessage m = AdbProtocol.AdbMessage.parseAdbMessage(in);
        if (m.command != AdbProtocol.CMD_CNXN) throw new IOException("expected CNXN");
        // The key is not known yet: ask for a signature, refuse it, ask for the public key, accept it.
        out.write(AdbProtocol.generateMessage(AdbProtocol.CMD_AUTH, AdbProtocol.AUTH_TYPE_TOKEN, 0, new byte[20]));
        m = AdbProtocol.AdbMessage.parseAdbMessage(in);
        if (m.command != AdbProtocol.CMD_AUTH || m.arg0 != AdbProtocol.AUTH_TYPE_SIGNATURE) throw new IOException("expected a signature");
        out.write(AdbProtocol.generateMessage(AdbProtocol.CMD_AUTH, AdbProtocol.AUTH_TYPE_TOKEN, 0, new byte[20]));
        m = AdbProtocol.AdbMessage.parseAdbMessage(in);
        if (m.command != AdbProtocol.CMD_AUTH || m.arg0 != AdbProtocol.AUTH_TYPE_RSA_PUBLIC) throw new IOException("expected the public key");
        out.write(AdbProtocol.generateMessage(AdbProtocol.CMD_CNXN, 0x01000001, 256 * 1024, BANNER));
        int nextId = 100;
        long expecting = -1, got = 0;
        int installRemote = 0, installLocal = 0;
        while (true) {
            m = AdbProtocol.AdbMessage.parseAdbMessage(in);
            switch (m.command) {
                case AdbProtocol.CMD_OPEN: {
                    int local = m.arg0, remote = nextId++;
                    String dest = new String(m.payload, 0, Math.max(0, m.payloadLength - 1));
                    if (dest.startsWith("refuse:")) { // refused: a close with no OKAY
                        out.write(AdbProtocol.generateClose(0, local));
                        break;
                    }
                    out.write(AdbProtocol.generateMessage(AdbProtocol.CMD_OKAY, remote, local, null));
                    if (dest.startsWith("shell:getprop")) {
                        // The output and the close go out together, as fast as can be: the case the original library lost.
                        out.write(AdbProtocol.generateWrite(remote, local, "SM-TEST\n".getBytes()));
                        out.write(AdbProtocol.generateClose(remote, local));
                    } else if (dest.startsWith("shell:big")) {
                        for (int i = 0; i < 20; i++) out.write(AdbProtocol.generateWrite(remote, local, ("line " + i + "\n").getBytes()));
                        out.write(AdbProtocol.generateClose(remote, local));
                    } else if (dest.startsWith("shell:quiet")) {
                        out.write(AdbProtocol.generateClose(remote, local)); // no output at all
                    } else if (dest.startsWith("exec:cmd package install -S ")) {
                        expecting = Long.parseLong(dest.substring("exec:cmd package install -S ".length()).split(" ")[0]);
                        got = 0;
                        installRemote = remote;
                        installLocal = local;
                    }
                    break;
                }
                case AdbProtocol.CMD_WRTE:
                    if (installRemote != 0) {
                        got += m.payloadLength;
                        installedBytes.set((int) got);
                        out.write(AdbProtocol.generateMessage(AdbProtocol.CMD_OKAY, installRemote, installLocal, null));
                        if (got >= expecting) {
                            out.write(AdbProtocol.generateWrite(installRemote, installLocal, "Success\n".getBytes()));
                            out.write(AdbProtocol.generateClose(installRemote, installLocal));
                            installRemote = 0;
                        }
                    }
                    break;
                default:
                    break; // OKAY / CLSE from the host: nothing to do
            }
        }
    }

    /** A plain one-way wire: bytes written at one end come out of the other, in order. */
    static class Wire {
        final java.util.concurrent.LinkedBlockingQueue<byte[]> q = new java.util.concurrent.LinkedBlockingQueue<>();
        volatile boolean closed;
        byte[] cur = new byte[0];
        int pos = 0;
        final OutputStream out = new OutputStream() {
            @Override
            public void write(int b) {
                write(new byte[]{(byte) b}, 0, 1);
            }

            @Override
            public void write(byte[] b, int off, int len) {
                q.add(java.util.Arrays.copyOfRange(b, off, off + len));
            }

            @Override
            public void close() {
                closed = true;
                q.add(new byte[0]);
            }
        };
        final InputStream in = new InputStream() {
            @Override
            public int read() throws IOException {
                byte[] one = new byte[1];
                int n = read(one, 0, 1);
                return n < 0 ? -1 : one[0] & 0xFF;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                try {
                    while (pos >= cur.length) {
                        if (closed && q.isEmpty()) return -1;
                        cur = q.take();
                        pos = 0;
                        if (cur.length == 0 && closed) return -1;
                    }
                } catch (InterruptedException e) {
                    throw new IOException(e);
                }
                int n = Math.min(len, cur.length - pos);
                System.arraycopy(cur, pos, b, off, n);
                pos += n;
                return n;
            }
        };
    }
    // ---------- helpers ----------

    static String readAll(AdbStream s) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            while (true) out.write(s.read());
        } catch (IOException closed) {
            // finished
        }
        return out.toString("UTF-8");
    }

    public static void main(String[] args) {
        try {
            run();
        } catch (Throwable t) {
            t.printStackTrace();
            System.out.println("ADB LIBRARY CHECK FAILED (exception)");
            System.exit(1);
        }
    }

    static void run() throws Exception {
        Wire hostToPhone = new Wire(), phoneToHost = new Wire();
        Thread phone = new Thread(() -> {
            try {
                phone(hostToPhone.in, phoneToHost.out);
            } catch (Exception e) {
                if (!String.valueOf(e.getMessage()).contains("Pipe")) e.printStackTrace(); // (the host closes the pipes at the end)
            }
        }, "pretend-phone");
        phone.setDaemon(true);
        phone.start();

        AdbCrypto crypto = AdbCrypto.generateAdbKeyPair(data -> Base64.getEncoder().encodeToString(data));
        Closeable closer = () -> {
            hostToPhone.out.close();
            phoneToHost.out.close();
        };
        AdbConnection conn = AdbConnection.create(phoneToHost.in, hostToPhone.out, closer, crypto);
        conn.connect();
        check(conn.getMaxData() == 256 * 1024, "connects through the key approval step; the phone's data size is known");

        // Many quick commands: the output and the close arrive together every time.
        int bad = 0;
        for (int i = 0; i < 300; i++) {
            if (i % 50 == 0) System.out.println("  ... command " + i);
            String r = readAll(conn.open("shell:getprop ro.product.model"));
            if (!"SM-TEST\n".equals(r)) bad++;
        }
        check(bad == 0, "300 quick commands each return their output (output and close arriving together)");

        StringBuilder want = new StringBuilder();
        for (int i = 0; i < 20; i++) want.append("line ").append(i).append("\n");
        check(want.toString().equals(readAll(conn.open("shell:big"))), "a command with several pieces of output returns all of them in order");
        check("".equals(readAll(conn.open("shell:quiet"))), "a command with no output ends cleanly with nothing");

        // Streaming an install: 100 KB in 16 KB pieces, then the phone says Success.
        int size = 100 * 1024;
        AdbStream inst = conn.open("exec:cmd package install -S " + size + " -r");
        byte[] chunk = new byte[16384];
        int sent = 0;
        while (sent < size) {
            int n = Math.min(chunk.length, size - sent);
            inst.write(n == chunk.length ? chunk : java.util.Arrays.copyOf(chunk, n));
            sent += n;
        }
        String res = readAll(inst);
        check(res.contains("Success") && installedBytes.get() == size, "a streamed install delivers every byte and gets the phone's answer (" + installedBytes.get() + " bytes)");

        boolean refused = false;
        try {
            conn.open("refuse:please");
        } catch (ConnectException e) {
            refused = true;
        }
        check(refused, "a refused open is reported instead of waiting forever");

        conn.close();
        System.out.println(failures == 0 ? "ADB LIBRARY CHECK OK" : "ADB LIBRARY CHECK FAILED (" + failures + ")");
        System.exit(failures == 0 ? 0 : 1);
    }
}
