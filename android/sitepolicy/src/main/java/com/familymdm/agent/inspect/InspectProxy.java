package com.familymdm.agent.inspect;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * A small web proxy that runs inside the Agent, on this phone only (127.0.0.1). The phone's browsers
 * are pointed at it, and it trusts nothing it is not told to: connections it is not meant to look
 * inside (see {@link Inspector#bypass}) are passed through untouched, byte for byte.
 *
 * For the rest it answers the browser's secure connection itself, using a certificate for that site
 * made by this phone's own CA (see CertAuthority; the browser trusts that CA because the Agent
 * installed it), reads the request in the clear, lets the {@link Inspector} decide, and makes its
 * own properly verified secure connection to the real site. That is what lets a filter work on
 * what is actually on a page rather than only on the site's name.
 *
 * It speaks plain HTTP/1.1 to the browser (browsers fall back to it when the proxy does not offer
 * HTTP/2), which keeps this small and means the framing of every message is easy to get right.
 * Pure Java, so it is checked on a computer. See ProxyCheck.
 */
public final class InspectProxy implements Closeable {
    /** Decides what to do with each request. All methods may be called from several threads at once. */
    public interface Inspector {
        /** True to tunnel this host untouched (banks, apps that insist on their own certificates). */
        boolean bypass(String host, int port);

        /** Look at (and optionally edit the headers of) a request; return a Response to answer it yourself, or null to forward it. */
        Response onRequest(Request r);

        /** True if this response's body should be passed to {@link #rewrite}. */
        boolean rewrites(Request r, String contentType);

        /** True if the site must send this request's page uncompressed because it will be rewritten. */
        boolean wantsIdentity(Request r);

        byte[] rewrite(Request r, byte[] body);
    }

    /** Opens the connection to the real site. Production code uses {@link #verifyingUpstream()}. */
    public interface Upstream {
        Socket open(String host, int port, boolean tls) throws IOException;
    }

    public static final class Headers {
        private final List<String[]> list = new ArrayList<>();

        public String get(String name) {
            for (String[] h : list) if (h[0].equalsIgnoreCase(name)) return h[1];
            return null;
        }

        public void set(String name, String value) {
            remove(name);
            list.add(new String[] {name, value});
        }

        public void remove(String name) {
            for (int i = list.size() - 1; i >= 0; i--) if (list.get(i)[0].equalsIgnoreCase(name)) list.remove(i);
        }

        void add(String name, String value) {
            list.add(new String[] {name, value});
        }

        List<String[]> all() {
            return list;
        }
    }

    public static final class Request {
        public final String method;
        public final String host;
        public final int port;
        public final boolean tls;
        public final String path; // path and query, e.g. /watch?v=abc
        public final Headers headers;

        Request(String method, String host, int port, boolean tls, String path, Headers headers) {
            this.method = method;
            this.host = host;
            this.port = port;
            this.tls = tls;
            this.path = path;
            this.headers = headers;
        }

        public String url() {
            return (tls ? "https://" : "http://") + host + ((tls && port == 443) || (!tls && port == 80) ? "" : ":" + port) + path;
        }
    }

    public static final class Response {
        public final int status;
        public final String contentType;
        public final byte[] body;

        public Response(int status, String contentType, byte[] body) {
            this.status = status;
            this.contentType = contentType;
            this.body = body;
        }

        public static Response html(int status, String html) {
            return new Response(status, "text/html; charset=utf-8", html.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static final int MAX_HEAD = 32 * 1024;
    private static final int MAX_REQUEST_BODY = 16 * 1024 * 1024;
    private static final int MAX_REWRITE_BODY = 8 * 1024 * 1024;

    private final CertAuthority ca;
    private final Inspector inspector;
    private final Upstream upstream;
    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "mdm-inspect");
        t.setDaemon(true);
        return t;
    });
    private volatile ServerSocket server;
    // Only connections to these ports are looked inside; anything else is passed through untouched.
    private final java.util.Set<Integer> tlsPorts = new java.util.HashSet<>(java.util.Arrays.asList(443));

    /** For checks: also look inside connections to these ports (real browsing only ever uses 443). */
    public InspectProxy alsoInspectPorts(int... ports) {
        for (int p : ports) tlsPorts.add(p);
        return this;
    }

    public InspectProxy(CertAuthority ca, Inspector inspector, Upstream upstream) {
        this.ca = ca;
        this.inspector = inspector;
        this.upstream = upstream;
    }

    private static volatile java.util.function.Function<String, String> hostMap = h -> null;

    /**
     * Sites to connect to instead of the one a browser asked for: SafeSearch (www.google.com becomes
     * forcesafesearch.google.com, Bing and DuckDuckGo likewise) and YouTube Restricted Mode. The phone's DNS filter does
     * this for lookups, but a browser that goes through this proxy never does its own lookup: the proxy does, so it has to
     * do it here too. The certificate is still checked for the name the browser asked for.
     */
    public static void setHostMap(java.util.function.Function<String, String> f) {
        hostMap = f == null ? h -> null : f;
    }

    static String connectTo(String host) {
        String t = hostMap.apply(host);
        return t == null ? host : t;
    }

    /** Connects to real sites with the system's trust store and checks the site's name, the way a browser does. */
    public static Upstream verifyingUpstream() {
        final SSLSocketFactory f = (SSLSocketFactory) SSLSocketFactory.getDefault();
        return (host, port, tls) -> {
            if (!tls) {
                Socket s = new Socket();
                s.connect(new java.net.InetSocketAddress(connectTo(host), port), 15000);
                s.setSoTimeout(60000);
                return s;
            }
            Socket raw = new Socket();
            raw.connect(new java.net.InetSocketAddress(connectTo(host), port), 15000);
            raw.setSoTimeout(60000);
            SSLSocket s = (SSLSocket) f.createSocket(raw, host, port, true);
            SSLParameters p = s.getSSLParameters();
            p.setEndpointIdentificationAlgorithm("HTTPS"); // refuse a certificate that is not for this site
            s.setSSLParameters(p);
            s.startHandshake();
            return s;
        };
    }

    /** Starts listening on 127.0.0.1 on any free port and returns it. */
    public int start() throws IOException {
        return start(0);
    }

    /** Starts listening on 127.0.0.1 on this port (0 = any free port) and returns the port. */
    public int start(int port) throws IOException {
        server = new ServerSocket(port, 64, InetAddress.getByName("127.0.0.1"));
        pool.execute(() -> {
            while (!server.isClosed()) {
                try {
                    final Socket c = server.accept();
                    pool.execute(() -> {
                        try {
                            serve(c);
                        } catch (Exception ignored) {
                            // a dropped or unhappy connection ends only that connection
                        } finally {
                            closeQuietly(c);
                        }
                    });
                } catch (IOException e) {
                    if (server.isClosed()) return;
                }
            }
        });
        return server.getLocalPort();
    }

    @Override
    public void close() {
        try {
            if (server != null) server.close();
        } catch (IOException ignored) {
        }
        pool.shutdownNow();
    }

    // ---------- one browser connection ----------

    private void serve(Socket client) throws Exception {
        client.setSoTimeout(120000);
        InputStream raw = client.getInputStream(); // read one byte at a time until the CONNECT is answered
        String line = readLine(raw);
        if (line == null || line.isEmpty()) return;
        String[] parts = line.split(" ");
        if (parts.length < 3) return;
        Headers headers = readHeaders(raw);
        if (parts[0].equalsIgnoreCase("CONNECT")) {
            String target = parts[1];
            int colon = target.lastIndexOf(':');
            String host = colon > 0 ? target.substring(0, colon) : target;
            int port = colon > 0 ? Integer.parseInt(target.substring(colon + 1)) : 443;
            if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length() - 1);
            OutputStream out = client.getOutputStream();
            if (!tlsPorts.contains(port) || inspector.bypass(host, port)) {
                Socket up;
                try {
                    up = upstream.open(host, port, false);
                } catch (IOException e) {
                    out.write("HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                    return;
                }
                out.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                out.flush();
                tunnel(client, up);
                return;
            }
            out.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();
            SSLContext ctx = ca.serverContextFor(host);
            SSLSocket tls = (SSLSocket) ctx.getSocketFactory().createSocket(client, null, client.getPort(), true);
            tls.setUseClientMode(false);
            tls.startHandshake();
            serveRequests(new BufferedInputStream(tls.getInputStream()), tls.getOutputStream(), host, port, true, null);
            return;
        }
        // A plain-HTTP request sent to the proxy in absolute form: "GET http://site/path HTTP/1.1".
        serveRequests(new BufferedInputStream(raw), client.getOutputStream(), null, 80, false, new Object[] {parts, headers});
    }

    /** Handles requests on one connection until it ends. `first` carries an already-read plain-HTTP request. */
    private void serveRequests(InputStream in, OutputStream out, String tlsHost, int port, boolean tls, Object[] first) throws Exception {
        boolean firstPending = first != null;
        while (true) {
            String[] parts;
            Headers headers;
            if (firstPending) {
                parts = (String[]) first[0];
                headers = (Headers) first[1];
                firstPending = false;
            } else {
                String line = readLine(in);
                if (line == null || line.isEmpty()) return;
                parts = line.split(" ");
                if (parts.length < 3) return;
                headers = readHeaders(in);
            }
            String method = parts[0];
            String target = parts[1];
            String host = tlsHost;
            int p = port;
            String path = target;
            if (!tls) {
                java.net.URI u = new java.net.URI(target);
                host = u.getHost();
                p = u.getPort() > 0 ? u.getPort() : 80;
                path = (u.getRawPath() == null || u.getRawPath().isEmpty() ? "/" : u.getRawPath()) + (u.getRawQuery() != null ? "?" + u.getRawQuery() : "");
            }
            if (host == null) return;
            byte[] body = readRequestBody(in, headers);
            if (body == null) {
                writeSimple(out, 413, "Request too large", true);
                return;
            }
            boolean clientClose = "close".equalsIgnoreCase(headers.get("Connection")) || "HTTP/1.0".equals(parts[2]);
            boolean upgrade = headers.get("Upgrade") != null;
            Request req = new Request(method, host, p, tls, path, headers);

            Response answer = inspector.onRequest(req);
            if (answer != null) {
                writeResponse(out, answer, !clientClose);
                if (clientClose) return;
                continue;
            }
            boolean identity = inspector.wantsIdentity(req);
            if (identity) headers.set("Accept-Encoding", "identity");

            Socket up;
            try {
                up = upstream.open(host, p, tls);
            } catch (IOException e) {
                if (Boolean.getBoolean("mdm.inspect.debug")) e.printStackTrace(); // java -Dmdm.inspect.debug=true, when a check needs explaining
                writeSimple(out, 502, "Could not reach " + host, true);
                return;
            }
            try {
                OutputStream uo = up.getOutputStream();
                sendRequest(uo, req, body, upgrade);
                boolean keep = relayResponse(new BufferedInputStream(up.getInputStream()), out, req, up, in, clientClose);
                if (!keep) return;
            } finally {
                closeQuietly(up);
            }
        }
    }

    private void sendRequest(OutputStream uo, Request req, byte[] body, boolean upgrade) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(req.method).append(' ').append(req.path).append(" HTTP/1.1\r\n");
        boolean hasHost = false;
        for (String[] h : req.headers.all()) {
            String n = h[0].toLowerCase(Locale.ROOT);
            if (n.equals("proxy-connection") || n.equals("connection") || n.equals("keep-alive") || n.equals("transfer-encoding")
                    || n.equals("content-length") || n.equals("proxy-authorization") || n.equals("te")) continue;
            if (n.equals("upgrade") && !upgrade) continue;
            if (n.equals("host")) hasHost = true;
            sb.append(h[0]).append(": ").append(h[1]).append("\r\n");
        }
        if (!hasHost) sb.append("Host: ").append(req.host).append("\r\n");
        sb.append(upgrade ? "Connection: Upgrade\r\n" : "Connection: close\r\n");
        if (body.length > 0 || req.method.equals("POST") || req.method.equals("PUT") || req.method.equals("PATCH")) {
            sb.append("Content-Length: ").append(body.length).append("\r\n");
        }
        sb.append("\r\n");
        uo.write(sb.toString().getBytes(StandardCharsets.ISO_8859_1));
        if (body.length > 0) uo.write(body);
        uo.flush();
    }

    /** Sends the site's answer to the browser. Returns true if this browser connection can carry another request. */
    private boolean relayResponse(InputStream up, OutputStream out, Request req, Socket upSocket, InputStream clientIn, boolean clientClose) throws Exception {
        String statusLine;
        Headers rh;
        int status;
        while (true) {
            statusLine = readLine(up);
            if (statusLine == null) throw new IOException("no response");
            String[] sp = statusLine.split(" ", 3);
            status = sp.length > 1 ? Integer.parseInt(sp[1].trim()) : 502;
            rh = readHeaders(up);
            if (status == 100 || (status >= 102 && status < 200)) {
                out.write(headBytes(statusLine, rh, null));
                out.flush();
                continue; // an interim answer: the real one follows
            }
            break;
        }
        if (status == 101) {
            out.write(headBytes(statusLine, rh, null));
            out.flush();
            tunnelStreams(clientIn, out, up, upSocket.getOutputStream(), upSocket);
            return false;
        }
        boolean noBody = req.method.equals("HEAD") || status == 204 || status == 304;
        boolean chunked = "chunked".equalsIgnoreCase(rh.get("Transfer-Encoding"));
        String cl = rh.get("Content-Length");
        long length = cl == null ? -1 : Long.parseLong(cl.trim());
        String ctype = rh.get("Content-Type");
        String enc = rh.get("Content-Encoding");

        if (!noBody && status == 200 && ctype != null && inspector.rewrites(req, ctype) && (enc == null || enc.equalsIgnoreCase("identity") || enc.equalsIgnoreCase("gzip"))) {
            byte[] body = readBody(up, chunked, length, MAX_REWRITE_BODY);
            if (body != null) {
                if (enc != null && enc.equalsIgnoreCase("gzip")) body = gunzip(body);
                byte[] changed = inspector.rewrite(req, body);
                rh.remove("Content-Encoding");
                rh.remove("Transfer-Encoding");
                rh.remove("Content-Length");
                // The page is being changed, so the site's rules about what a page may run no longer describe it.
                rh.remove("Content-Security-Policy");
                rh.remove("Content-Security-Policy-Report-Only");
                rh.set("Content-Length", String.valueOf(changed.length));
                // A picture replaced by the picture filter is a PNG now, whatever it was before.
                if (changed.length > 8 && (changed[0] & 0xFF) == 0x89 && changed[1] == 'P' && changed[2] == 'N' && changed[3] == 'G'
                        && ctype.toLowerCase(java.util.Locale.ROOT).startsWith("image/")) {
                    rh.set("Content-Type", "image/png");
                    rh.remove("ETag");
                }
                out.write(headBytes(statusLine, rh, clientClose ? "close" : "keep-alive"));
                out.write(changed);
                out.flush();
                return !clientClose;
            }
            // Too large to rewrite safely: fall through is not possible (body already consumed), so refuse.
            writeSimple(out, 502, "Page too large to filter", true);
            return false;
        }

        boolean closeDelimited = !noBody && !chunked && length < 0;
        out.write(headBytes(statusLine, rh, (clientClose || closeDelimited) ? "close" : "keep-alive"));
        if (!noBody) {
            if (chunked) copyChunked(up, out);
            else if (length >= 0) copyExactly(up, out, length);
            else copyUntilEnd(up, out);
        }
        out.flush();
        return !(clientClose || closeDelimited);
    }

    // ---------- HTTP helpers ----------

    private static byte[] headBytes(String statusLine, Headers h, String connection) {
        StringBuilder sb = new StringBuilder(statusLine).append("\r\n");
        for (String[] x : h.all()) {
            String n = x[0].toLowerCase(Locale.ROOT);
            if (connection != null && (n.equals("connection") || n.equals("keep-alive") || n.equals("proxy-connection"))) continue;
            sb.append(x[0]).append(": ").append(x[1]).append("\r\n");
        }
        if (connection != null) sb.append("Connection: ").append(connection).append("\r\n");
        sb.append("\r\n");
        return sb.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    private static void writeResponse(OutputStream out, Response r, boolean keepAlive) throws IOException {
        String head = "HTTP/1.1 " + r.status + " " + reason(r.status) + "\r\nContent-Type: " + r.contentType + "\r\nContent-Length: " + r.body.length
                + "\r\nCache-Control: no-store\r\nConnection: " + (keepAlive ? "keep-alive" : "close") + "\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.ISO_8859_1));
        out.write(r.body);
        out.flush();
    }

    private static void writeSimple(OutputStream out, int status, String message, boolean close) throws IOException {
        writeResponse(out, Response.html(status, "<html><body style='font-family:sans-serif;padding:24px'><h3>" + message + "</h3></body></html>"), !close);
    }

    private static String reason(int s) {
        switch (s) {
            case 200: return "OK";
            case 403: return "Forbidden";
            case 413: return "Payload Too Large";
            case 502: return "Bad Gateway";
            default: return "Status";
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') b.write(c);
            if (b.size() > MAX_HEAD) throw new IOException("header line too long");
        }
        if (c == -1 && b.size() == 0) return null;
        return new String(b.toByteArray(), StandardCharsets.ISO_8859_1);
    }

    private static Headers readHeaders(InputStream in) throws IOException {
        Headers h = new Headers();
        int total = 0;
        while (true) {
            String line = readLine(in);
            if (line == null) throw new IOException("connection closed in headers");
            if (line.isEmpty()) return h;
            total += line.length();
            if (total > MAX_HEAD) throw new IOException("headers too large");
            int colon = line.indexOf(':');
            if (colon > 0) h.add(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
        }
    }

    /** The request's body as plain bytes, or null if it is bigger than we accept. */
    private static byte[] readRequestBody(InputStream in, Headers h) throws IOException {
        if ("chunked".equalsIgnoreCase(h.get("Transfer-Encoding"))) return readBody(in, true, -1, MAX_REQUEST_BODY);
        String cl = h.get("Content-Length");
        if (cl == null) return new byte[0];
        long n = Long.parseLong(cl.trim());
        if (n > MAX_REQUEST_BODY) return null;
        return readBody(in, false, n, MAX_REQUEST_BODY);
    }

    /** Reads a whole body (chunked, fixed length, or until the connection ends), or null if it exceeds `max`. */
    private static byte[] readBody(InputStream in, boolean chunked, long length, int max) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        if (chunked) {
            while (true) {
                String sizeLine = readLine(in);
                if (sizeLine == null) throw new IOException("closed mid-body");
                int semi = sizeLine.indexOf(';');
                long size = Long.parseLong((semi >= 0 ? sizeLine.substring(0, semi) : sizeLine).trim(), 16);
                if (size == 0) {
                    while (true) { // trailers, up to the blank line
                        String t = readLine(in);
                        if (t == null || t.isEmpty()) break;
                    }
                    return b.toByteArray();
                }
                if (b.size() + size > max) return null;
                long left = size;
                while (left > 0) {
                    int n = in.read(buf, 0, (int) Math.min(buf.length, left));
                    if (n < 0) throw new IOException("closed mid-chunk");
                    b.write(buf, 0, n);
                    left -= n;
                }
                readLine(in); // the CRLF after the chunk
            }
        }
        if (length >= 0) {
            if (length > max) return null;
            long left = length;
            while (left > 0) {
                int n = in.read(buf, 0, (int) Math.min(buf.length, left));
                if (n < 0) throw new IOException("closed mid-body");
                b.write(buf, 0, n);
                left -= n;
            }
            return b.toByteArray();
        }
        int n;
        while ((n = in.read(buf)) >= 0) {
            b.write(buf, 0, n);
            if (b.size() > max) return null;
        }
        return b.toByteArray();
    }

    private static void copyExactly(InputStream in, OutputStream out, long length) throws IOException {
        byte[] buf = new byte[16384];
        long left = length;
        while (left > 0) {
            int n = in.read(buf, 0, (int) Math.min(buf.length, left));
            if (n < 0) throw new IOException("closed mid-body");
            out.write(buf, 0, n);
            left -= n;
        }
    }

    private static void copyUntilEnd(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
    }

    /** Copies a chunked body exactly as it came (sizes, data, trailers), without storing it. */
    private static void copyChunked(InputStream in, OutputStream out) throws IOException {
        while (true) {
            String sizeLine = readLine(in);
            if (sizeLine == null) throw new IOException("closed mid-body");
            out.write((sizeLine + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
            int semi = sizeLine.indexOf(';');
            long size = Long.parseLong((semi >= 0 ? sizeLine.substring(0, semi) : sizeLine).trim(), 16);
            if (size == 0) {
                while (true) {
                    String t = readLine(in);
                    if (t == null) return;
                    out.write((t + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
                    if (t.isEmpty()) return;
                }
            }
            copyExactly(in, out, size);
            readLine(in);
            out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
        }
    }

    private static byte[] gunzip(byte[] data) throws IOException {
        try (GZIPInputStream g = new GZIPInputStream(new java.io.ByteArrayInputStream(data))) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = g.read(buf)) >= 0) b.write(buf, 0, n);
            return b.toByteArray();
        }
    }

    // ---------- blind tunnels ----------

    private void tunnel(Socket a, Socket b) throws IOException {
        tunnelStreams(a.getInputStream(), a.getOutputStream(), b.getInputStream(), b.getOutputStream(), b);
    }

    /** Copies both ways until either side ends. */
    private void tunnelStreams(final InputStream aIn, final OutputStream aOut, final InputStream bIn, final OutputStream bOut, final Socket b) throws IOException {
        Thread t = new Thread(() -> {
            try {
                byte[] buf = new byte[16384];
                int n;
                while ((n = aIn.read(buf)) >= 0) {
                    bOut.write(buf, 0, n);
                    bOut.flush();
                }
            } catch (IOException ignored) {
            } finally {
                closeQuietly(b);
            }
        }, "mdm-inspect-tunnel");
        t.setDaemon(true);
        t.start();
        try {
            byte[] buf = new byte[16384];
            int n;
            while ((n = bIn.read(buf)) >= 0) {
                aOut.write(buf, 0, n);
                aOut.flush();
            }
        } catch (IOException ignored) {
        }
    }

    private static void closeQuietly(Closeable c) {
        try {
            if (c != null) c.close();
        } catch (IOException ignored) {
        }
    }
}
