package com.familymdm.otgsetup;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * The USB side of ADB: finds the ADB interface of the other phone (class 0xFF, subclass 0x42, protocol 1), and gives
 * the ADB library a pair of plain streams on top of its two bulk endpoints.
 *
 * An ADB message is a 24-byte header and then its data. The other phone expects them as two separate USB transfers,
 * so a write that holds both is split after the first 24 bytes. A read asks for the header first and the data next,
 * the same way, because that is how the other phone sends them.
 */
final class UsbTransport implements Closeable {
    private final UsbDeviceConnection conn;
    private final UsbInterface iface;
    private final UsbEndpoint in, out;
    private volatile boolean closed;

    private UsbTransport(UsbDeviceConnection conn, UsbInterface iface, UsbEndpoint in, UsbEndpoint out) {
        this.conn = conn;
        this.iface = iface;
        this.in = in;
        this.out = out;
    }

    /** Is this USB device a phone with ADB switched on? */
    static boolean hasAdb(UsbDevice d) {
        return adbInterface(d) != null;
    }

    private static UsbInterface adbInterface(UsbDevice d) {
        for (int i = 0; i < d.getInterfaceCount(); i++) {
            UsbInterface it = d.getInterface(i);
            if (it.getInterfaceClass() == 0xFF && it.getInterfaceSubclass() == 0x42 && it.getInterfaceProtocol() == 1) return it;
        }
        return null;
    }

    static UsbTransport open(UsbManager manager, UsbDevice device) throws IOException {
        UsbInterface it = adbInterface(device);
        if (it == null) throw new IOException("This USB device does not have ADB (USB debugging) switched on.");
        UsbEndpoint in = null, out = null;
        for (int i = 0; i < it.getEndpointCount(); i++) {
            UsbEndpoint e = it.getEndpoint(i);
            if (e.getType() != UsbConstants.USB_ENDPOINT_XFER_BULK) continue;
            if (e.getDirection() == UsbConstants.USB_DIR_IN) in = e;
            else out = e;
        }
        if (in == null || out == null) throw new IOException("The phone's ADB connection has no data channel.");
        UsbDeviceConnection c = manager.openDevice(device);
        if (c == null) throw new IOException("Could not open the USB connection (was it allowed?).");
        if (!c.claimInterface(it, true)) {
            c.close();
            throw new IOException("Could not take over the phone's ADB connection. Is another app using it?");
        }
        return new UsbTransport(c, it, in, out);
    }

    InputStream input() {
        return new InputStream() {
            @Override
            public int read() throws IOException {
                byte[] one = new byte[1];
                int n = read(one, 0, 1);
                return n < 0 ? -1 : one[0] & 0xFF;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if (len == 0) return 0;
                while (!closed) {
                    // Waits a second at a time so that closing the connection ends the wait. -1 here is a timeout as often as an error;
                    // the unplugging of the cable is told to close() by the screen.
                    int n = conn.bulkTransfer(in, b, off, len, 1000);
                    if (n > 0) return n;
                    if (n == 0) continue;
                }
                throw new IOException("The USB connection was closed.");
            }
        };
    }

    OutputStream output() {
        return new OutputStream() {
            @Override
            public void write(int v) throws IOException {
                write(new byte[]{(byte) v}, 0, 1);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                if (len > 24) { // a message: its header, then its data, as two transfers
                    send(b, off, 24);
                    send(b, off + 24, len - 24);
                } else {
                    send(b, off, len);
                }
            }
        };
    }

    private void send(byte[] b, int off, int len) throws IOException {
        int done = 0;
        while (done < len) {
            if (closed) throw new IOException("The USB connection was closed.");
            int n = conn.bulkTransfer(out, b, off + done, Math.min(16384, len - done), 5000);
            if (n <= 0) throw new IOException("Writing to the phone over the cable failed.");
            done += n;
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try {
            conn.releaseInterface(iface);
        } catch (RuntimeException ignored) {
        }
        conn.close();
    }
}
