package com.familymdm.agent.inspect;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

/**
 * End-to-end checks for the inspection proxy on a computer: a real encrypted connection from a
 * "browser" through the proxy to a real encrypted "website", all on this machine.
 *   javac -d out android/sitepolicy/src/main/java/com/familymdm/agent/sitepolicy/*.java android/sitepolicy/src/main/java/com/familymdm/agent/inspect/*.java android/sitepolicy/src/test/java/com/familymdm/agent/inspect/ProxyCheck.java
 *   java -cp out com.familymdm.agent.inspect.ProxyCheck
 */
public class ProxyCheck {
    static int failures = 0;

    static void check(boolean ok, String what) {
        if (!ok) {
            failures++;
            System.out.println("FAIL: " + what);
        } else {
            System.out.println("ok:   " + what);
        }
    }

    static CertAuthority newCa(String name) throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        final KeyPair kp = g.generateKeyPair();
        CertAuthority.Signer signer = data -> {
            Signature s = Signature.getInstance("SHA256withECDSA");
            s.initSign(kp.getPrivate());
            s.update(data);
            return s.sign();
        };
        return new CertAuthority(signer, CertAuthority.newCaCertificate(signer, kp.getPublic(), name, System.currentTimeMillis()));
    }

    static SSLContext trusting(X509Certificate... roots) throws Exception {
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null);
        for (int i = 0; i < roots.length; i++) ks.setCertificateEntry("r" + i, roots[i]);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);
        SSLContext c = SSLContext.getInstance("TLS");
        c.init(null, tmf.getTrustManagers(), null);
        return c;
    }

    static SSLContext trustingQuiet(X509Certificate root) {
        try {
            return trusting(root);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ---------- a tiny "website" ----------

    static final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();

    static class Origin {
        final ServerSocket server;
        final CertAuthority ca;

        Origin(CertAuthority ca) throws Exception {
            this.ca = ca;
            SSLServerSocket s = (SSLServerSocket) ca.serverContextFor("origin.test").getServerSocketFactory().createServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
            this.server = s;
            Thread t = new Thread(() -> {
                while (!server.isClosed()) {
                    try {
                        final Socket c = server.accept();
                        new Thread(() -> {
                            try {
                                handle((SSLSocket) c);
                            } catch (Exception ignored) {
                            } finally {
                                try { c.close(); } catch (Exception ignored) { }
                            }
                        }).start();
                    } catch (Exception e) {
                        return;
                    }
                }
            });
            t.setDaemon(true);
            t.start();
        }

        int port() { return server.getLocalPort(); }

        static String line(InputStream in) throws Exception {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            int c;
            while ((c = in.read()) != -1 && c != '\n') if (c != '\r') b.write(c);
            return b.toString("ISO-8859-1");
        }

        void handle(SSLSocket s) throws Exception {
            InputStream in = s.getInputStream();
            OutputStream out = s.getOutputStream();
            String req = line(in);
            String[] p = req.split(" ");
            StringBuilder hdrs = new StringBuilder();
            int len = 0;
            String l;
            while (!(l = line(in)).isEmpty()) {
                hdrs.append(l).append('\n');
                if (l.toLowerCase().startsWith("content-length:")) len = Integer.parseInt(l.substring(15).trim());
            }
            byte[] body = new byte[len];
            int off = 0;
            while (off < len) off += in.read(body, off, len - off);
            String path = p[1];
            hits.computeIfAbsent(path, k -> new AtomicInteger()).incrementAndGet();
            if (path.equals("/hello")) {
                respond(out, "200 OK", "text/plain", "Hello, world".getBytes(), false, null);
            } else if (path.equals("/chunked")) {
                out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n"
                        + "3\r\nabc\r\n5\r\ndefgh\r\n0\r\n\r\n").getBytes());
            } else if (path.equals("/close")) {
                out.write("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nConnection: close\r\n\r\nuntil-close-body".getBytes());
            } else if (path.equals("/echo")) {
                respond(out, "200 OK", "text/plain", (hdrs + "--body--\n" + new String(body, StandardCharsets.UTF_8)).getBytes(), false, null);
            } else if (path.equals("/html")) {
                respond(out, "200 OK", "text/html; charset=utf-8", "<html><head><title>t</title></head><body>page</body></html>".getBytes(), false, "Content-Security-Policy: default-src 'none'\r\n");
            } else if (path.equals("/htmlgz")) {
                respond(out, "200 OK", "text/html", "<html><head></head><body>zipped page</body></html>".getBytes(), true, null);
            } else if (path.equals("/big")) {
                byte[] big = new byte[300_000];
                for (int i = 0; i < big.length; i++) big[i] = (byte) (i * 31);
                respond(out, "200 OK", "application/octet-stream", big, false, null);
            } else {
                respond(out, "404 Not Found", "text/plain", "nope".getBytes(), false, null);
            }
            out.flush();
        }

        static void respond(OutputStream out, String status, String type, byte[] body, boolean gzip, String extra) throws Exception {
            if (gzip) {
                ByteArrayOutputStream z = new ByteArrayOutputStream();
                try (GZIPOutputStream g = new GZIPOutputStream(z)) { g.write(body); }
                body = z.toByteArray();
            }
            out.write(("HTTP/1.1 " + status + "\r\nContent-Type: " + type + "\r\nContent-Length: " + body.length + "\r\n"
                    + (gzip ? "Content-Encoding: gzip\r\n" : "") + (extra == null ? "" : extra) + "Connection: close\r\n\r\n").getBytes());
            out.write(body);
        }
    }

    // ---------- the rules the proxy is given for the test ----------

    static class TestInspector implements InspectProxy.Inspector {
        int bypassPort = -1;

        public boolean bypass(String host, int port) { return port == bypassPort; }

        public InspectProxy.Response onRequest(InspectProxy.Request r) {
            if (r.path.equals("/blocked")) return InspectProxy.Response.html(403, "blocked by rule");
            r.headers.set("X-Added", "yes");
            return null;
        }

        public boolean rewrites(InspectProxy.Request r, String ct) { return r.path.startsWith("/html"); }

        public boolean wantsIdentity(InspectProxy.Request r) { return r.path.startsWith("/html"); }

        public byte[] rewrite(InspectProxy.Request r, byte[] body) {
            return new String(body, StandardCharsets.UTF_8).replace("</body>", "<!--rewritten--></body>").getBytes(StandardCharsets.UTF_8);
        }
    }

    static String get(int proxyPort, SSLContext trust, String host, int port, String path, String method, String postBody, int[] statusOut) throws Exception {
        HttpsURLConnection c = (HttpsURLConnection) new java.net.URL("https://" + host + ":" + port + path)
                .openConnection(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", proxyPort)));
        c.setSSLSocketFactory(trust.getSocketFactory());
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setRequestMethod(method);
        if (postBody != null) {
            c.setDoOutput(true);
            c.getOutputStream().write(postBody.getBytes(StandardCharsets.UTF_8));
        }
        int status = c.getResponseCode();
        if (statusOut != null) statusOut[0] = status;
        InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while (in != null && (n = in.read(buf)) >= 0) b.write(buf, 0, n);
        c.getHeaderFields(); // keep headers available for callers that want them
        lastHeaders = c.getHeaderFields();
        return b.toString("UTF-8");
    }

    static Map<String, java.util.List<String>> lastHeaders;

    public static void main(String[] args) throws Exception {
        CertAuthority originCa = newCa("Origin CA");
        CertAuthority proxyCa = newCa("Proxy CA");
        Origin origin = new Origin(originCa);
        final int op = origin.port();

        // The proxy reaches the "website" with the website's own CA as its only trust, and still checks the name.
        InspectProxy.Upstream upstream = (host, port, tls) -> {
            Socket raw = new Socket("127.0.0.1", op);
            raw.setSoTimeout(15000);
            if (!tls) return raw;
            SSLSocket s = (SSLSocket) trustingQuiet(originCa.caCertificate()).getSocketFactory().createSocket(raw, host, op, true);
            SSLParameters p = s.getSSLParameters();
            p.setEndpointIdentificationAlgorithm("HTTPS");
            s.setSSLParameters(p);
            s.startHandshake();
            return s;
        };
        TestInspector rules = new TestInspector();
        InspectProxy proxy = new InspectProxy(proxyCa, rules, upstream).alsoInspectPorts(op);
        int pp = proxy.start();
        SSLContext browser = trusting(proxyCa.caCertificate()); // a browser that has the phone's CA installed
        int[] st = new int[1];

        check("Hello, world".equals(get(pp, browser, "origin.test", op, "/hello", "GET", null, st)) && st[0] == 200,
                "a page comes through the proxy, and the browser accepts the proxy's certificate");
        check(get(pp, browser, "origin.test", op, "/hello", "GET", null, st).equals("Hello, world"), "a second request on the same connection works");
        check("abcdefgh".equals(get(pp, browser, "origin.test", op, "/chunked", "GET", null, null)), "a chunked answer arrives intact");
        check("until-close-body".equals(get(pp, browser, "origin.test", op, "/close", "GET", null, null)), "an answer that ends when the connection closes arrives intact");

        String echo = get(pp, browser, "origin.test", op, "/echo", "POST", "posted data", null);
        check(echo.contains("X-Added: yes"), "a header added by a rule reaches the website");
        check(echo.endsWith("--body--\nposted data"), "a request body is passed on correctly");
        check(echo.toLowerCase().contains("connection: close") == true, "the proxy talks to the website with its own connection handling");

        int before = hits.getOrDefault("/blocked", new AtomicInteger()).get();
        String blocked = get(pp, browser, "origin.test", op, "/blocked", "GET", null, st);
        check(st[0] == 403 && blocked.contains("blocked by rule"), "a rule can answer a request itself");
        check(hits.getOrDefault("/blocked", new AtomicInteger()).get() == before, "...without the website ever being contacted");

        String html = get(pp, browser, "origin.test", op, "/html", "GET", null, null);
        check(html.contains("<!--rewritten-->") && html.contains("page"), "a page can be rewritten on its way through");
        check(lastHeaders.get("Content-Security-Policy") == null, "the site's content-security rule is dropped for a rewritten page");
        String gz = get(pp, browser, "origin.test", op, "/htmlgz", "GET", null, null);
        check(gz.contains("zipped page") && gz.contains("<!--rewritten-->"), "a compressed page is unpacked, rewritten and sent plain");

        byte[] big = new byte[0];
        HttpsURLConnection bc = (HttpsURLConnection) new java.net.URL("https://origin.test:" + op + "/big")
                .openConnection(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", pp)));
        bc.setSSLSocketFactory(browser.getSocketFactory());
        try (InputStream in = bc.getInputStream()) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) b.write(buf, 0, n);
            big = b.toByteArray();
        }
        boolean same = big.length == 300_000;
        for (int i = 0; same && i < big.length; i++) if (big[i] != (byte) (i * 31)) same = false;
        check(same, "a large download is streamed through without damage");

        // Names are checked on the way out: a certificate for the wrong site is refused.
        String wrong = get(pp, browser, "wrong.test", op, "/hello", "GET", null, st);
        check(st[0] == 502, "a website whose certificate is for another name is refused");

        // A host the rules say not to look inside is tunnelled untouched: the browser sees the website's own certificate.
        Origin second = new Origin(originCa);
        rules.bypassPort = second.port();
        InspectProxy.Upstream both = upstream;
        proxy.close();
        final int sp = second.port();
        InspectProxy proxy2 = new InspectProxy(proxyCa, rules, (host, port, tls) -> {
            Socket raw = new Socket("127.0.0.1", port == sp ? sp : op);
            raw.setSoTimeout(15000);
            if (!tls) return raw;
            SSLSocket s = (SSLSocket) trustingQuiet(originCa.caCertificate()).getSocketFactory().createSocket(raw, host, op, true);
            SSLParameters p = s.getSSLParameters();
            p.setEndpointIdentificationAlgorithm("HTTPS");
            s.setSSLParameters(p);
            s.startHandshake();
            return s;
        }).alsoInspectPorts(op, sp);
        int pp2 = proxy2.start();
        String viaTunnel = get(pp2, trusting(originCa.caCertificate()), "origin.test", sp, "/hello", "GET", null, st); // trusts ONLY the website's CA
        check("Hello, world".equals(viaTunnel), "a bypassed host is passed through untouched (the browser saw the website's real certificate)");
        boolean refused = false;
        try {
            get(pp2, trusting(originCa.caCertificate()), "origin.test", op, "/hello", "GET", null, null); // looked inside: needs the proxy CA
        } catch (Exception e) {
            refused = true;
        }
        check(refused, "a browser that does not have the phone's CA installed rejects inspected connections (nothing is trusted silently)");

        // ---- the YouTube rules, called directly ----
        YoutubeInspector yt = new YoutubeInspector(true, new HashSet<>(Collections.singleton("bank.test")));
        check(yt.bypass("bank.test", 443) && yt.bypass("www.bank.test", 443) && !yt.bypass("notbank.test", 443), "listed hosts are bypassed, look-alikes are not");
        InspectProxy.Headers dh = new InspectProxy.Headers();
        dh.add("Sec-Fetch-Dest", "document");
        InspectProxy.Response home = yt.onRequest(new InspectProxy.Request("GET", "www.youtube.com", 443, true, "/", dh));
        check(home == null, "YouTube's own home page loads (its feed is emptied by the script added to the page)");
        check(dh.get("YouTube-Restrict") != null && dh.get("YouTube-Restrict").equals("Strict"), "YouTube requests carry the strict header");
        InspectProxy.Response shorts = yt.onRequest(new InspectProxy.Request("GET", "www.youtube.com", 443, true, "/shorts/abcdefghijk", dh));
        check(shorts != null && shorts.status == 403, "Shorts are refused");
        check(yt.onRequest(new InspectProxy.Request("GET", "www.youtube.com", 443, true, "/results?search_query=torah", dh)) == null, "search is allowed");
        check(yt.onRequest(new InspectProxy.Request("GET", "www.youtube.com", 443, true, "/watch?v=dQw4w9WgXcQ", dh)) == null, "a video is allowed");
        InspectProxy.Headers xh = new InspectProxy.Headers();
        xh.add("Sec-Fetch-Dest", "empty");
        check(yt.onRequest(new InspectProxy.Request("POST", "www.youtube.com", 443, true, "/youtubei/v1/search", xh)) == null && "Strict".equals(xh.get("YouTube-Restrict")),
                "YouTube's behind-the-scenes requests are not blocked but still carry the header");
        InspectProxy.Headers oh = new InspectProxy.Headers();
        oh.add("Sec-Fetch-Dest", "document");
        check(yt.onRequest(new InspectProxy.Request("GET", "example.com", 443, true, "/", oh)) == null && oh.get("YouTube-Restrict") == null, "other sites are left alone");
        InspectProxy.Request hr = new InspectProxy.Request("GET", "www.youtube.com", 443, true, "/watch?v=dQw4w9WgXcQ", dh);
        String rewritten = new String(yt.rewrite(hr, "<html><HEAD lang='en'><title>x</title></head><body></body></html>".getBytes()));
        check(rewritten.contains("<HEAD lang='en'><style>") && rewritten.contains("#related"), "the hiding rules are put at the top of a YouTube page");
        YoutubeInspector off = new YoutubeInspector(false, Collections.<String>emptySet());
        check(off.onRequest(new InspectProxy.Request("GET", "www.youtube.com", 443, true, "/", dh)) == null && !off.rewrites(hr, "text/html"), "with the setting off, YouTube is untouched");

        // ---- pictures ----
        YoutubeInspector pics = new YoutubeInspector(false, Collections.<String>emptySet(), true, new HashSet<>(Collections.singleton("school.test")));
        InspectProxy.Headers ih = new InspectProxy.Headers();
        ih.add("Sec-Fetch-Dest", "image");
        ih.add("Referer", "https://news.test/story");
        InspectProxy.Response blank = pics.onRequest(new InspectProxy.Request("GET", "cdn.test", 443, true, "/a", ih));
        check(blank != null && blank.status == 200 && blank.contentType.equals("image/gif") && blank.body.length == 43 && blank.body[0] == 'G', "a picture is replaced by a blank one");
        InspectProxy.Headers ph = new InspectProxy.Headers();
        ph.add("Sec-Fetch-Dest", "document");
        check(pics.onRequest(new InspectProxy.Request("GET", "news.test", 443, true, "/story", ph)) == null, "the page itself still loads");
        check(pics.onRequest(new InspectProxy.Request("GET", "cdn.test", 443, true, "/photo.JPG?w=200", new InspectProxy.Headers())) != null, "a picture is recognised by its file name when the browser does not say");
        check(pics.onRequest(new InspectProxy.Request("GET", "cdn.test", 443, true, "/data.json", new InspectProxy.Headers())) == null, "other files are untouched");
        InspectProxy.Headers sh = new InspectProxy.Headers();
        sh.add("Sec-Fetch-Dest", "image");
        sh.add("Referer", "https://www.school.test/lesson");
        check(pics.onRequest(new InspectProxy.Request("GET", "cdn.test", 443, true, "/a", sh)) == null, "pictures show on a page from the 'show pictures' list");
        check(pics.onRequest(new InspectProxy.Request("GET", "img.school.test", 443, true, "/a", ih)) == null, "pictures from a listed site show anywhere");
        check(new YoutubeInspector(false, Collections.<String>emptySet()).onRequest(new InspectProxy.Request("GET", "cdn.test", 443, true, "/a", ih)) == null, "with the setting off, pictures are untouched");
        // approved songs keep their art with "No pictures" on; YouTube Music's own art too unless pictures are off for it
        YoutubeInspector art = new YoutubeInspector(false, Collections.<String>emptySet(), true, Collections.<String>emptySet());
        art.setYoutubeChannels(new HashSet<String>(), new HashSet<>(Collections.singleton("abcdefghijk")), "");
        InspectProxy.Headers ah = new InspectProxy.Headers();
        ah.add("Sec-Fetch-Dest", "image");
        ah.add("Referer", "https://music.youtube.com/");
        check(art.onRequest(new InspectProxy.Request("GET", "i.ytimg.com", 443, true, "/vi/abcdefghijk/mqdefault.jpg", ah)) == null, "the picture of an approved song still shows");
        check(art.onRequest(new InspectProxy.Request("GET", "i.ytimg.com", 443, true, "/vi_webp/abcdefghijk/mqdefault.webp", ah)) == null, "also its webp picture");
        check(art.onRequest(new InspectProxy.Request("GET", "i.ytimg.com", 443, true, "/vi/zzzzzzzzzzz/mqdefault.jpg", ah)) != null, "the picture of a song that is not approved stays blank");
        check(art.onRequest(new InspectProxy.Request("GET", "lh3.googleusercontent.com", 443, true, "/art=w226", ah)) != null, "Music's own art stays blank until pictures are chosen for it");
        art.setMusicPictures(true);
        check(art.onRequest(new InspectProxy.Request("GET", "lh3.googleusercontent.com", 443, true, "/art=w226", ah)) == null, "with pictures chosen for Music, its art loads (the page decides where it shows)");
        InspectProxy.Headers otherPage = new InspectProxy.Headers();
        otherPage.add("Sec-Fetch-Dest", "image");
        otherPage.add("Referer", "https://news.test/story");
        check(art.onRequest(new InspectProxy.Request("GET", "lh3.googleusercontent.com", 443, true, "/art=w226", otherPage)) != null, "other sites still get blank pictures");

        // ---- picture filter (photo checker / cover skin, carried to every browser) ----
        YoutubeInspector pf = new YoutubeInspector(false, Collections.<String>emptySet());
        check(!pf.rewrites(new InspectProxy.Request("GET", "cdn.test", 443, true, "/a.jpg", new InspectProxy.Headers()), "image/jpeg"), "without a picture filter, pictures are not touched");
        final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 0, 'x'};
        pf.setPictureFilter(pic -> PNG);
        InspectProxy.Request pr = new InspectProxy.Request("GET", "cdn.test", 443, true, "/a.jpg", new InspectProxy.Headers());
        check(pf.rewrites(pr, "image/jpeg") && pf.rewrites(pr, "image/webp"), "with a picture filter, pictures go through it");
        check(!pf.rewrites(pr, "image/svg+xml") && !pf.rewrites(pr, "text/html"), "drawings (SVG) and pages are not handed to it");
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F', 0, 1, 1};
        check(java.util.Arrays.equals(pf.rewrite(pr, jpeg), PNG), "a picture is replaced by what the filter returns");
        check(java.util.Arrays.equals(pf.rewrite(pr, "<html>not a picture</html>".getBytes()), "<html>not a picture</html>".getBytes()), "something that is not really a picture is passed unchanged");
        pf.setPictureFilter(pic -> null);
        check(java.util.Arrays.equals(pf.rewrite(pr, jpeg), jpeg), "a picture the filter leaves alone arrives unchanged");
        pf.setPictureFilter(pic -> { throw new RuntimeException("broken"); });
        check(java.util.Arrays.equals(pf.rewrite(pr, jpeg), jpeg), "a filter problem never breaks the page");
        // ---- video skin covering added to pages ----
        YoutubeInspector vs = new YoutubeInspector(false, Collections.<String>emptySet());
        InspectProxy.Request page = new InspectProxy.Request("GET", "news.test", 443, true, "/", new InspectProxy.Headers());
        check(!vs.rewrites(page, "text/html"), "without the setting, pages are not touched");
        String js = YoutubeInspector.videoSkinScript();
        check(js != null && js.contains("texImage2D"), "the video covering script is packed with the proxy");
        vs.setPageScript(js);
        check(vs.rewrites(page, "text/html; charset=utf-8") && !vs.rewrites(page, "application/json"), "with it, web pages (only) are changed");
        String out = new String(vs.rewrite(page, "<html><head><title>x</title></head><body>hi</body></html>".getBytes()));
        check(out.startsWith("<html><head><script>") && out.contains("__mdmVideoSkin") && out.contains("<body>hi</body>"), "the script is put at the top of the page, the page is kept");
        vs.setPageScript(null);
        check(!vs.rewrites(page, "text/html"), "switched off again");

        // Forced SafeSearch: browsers that go through the proxy are connected to the "safe" servers too (the DNS filter cannot do it for them).
        InspectProxy.setHostMap(h -> com.familymdm.agent.sitepolicy.SafeSearchDns.target(h, true, "strict"));
        check("forcesafesearch.google.com".equals(InspectProxy.connectTo("www.google.com")), "www.google.com goes to Google's safe server");
        check("strict.bing.com".equals(InspectProxy.connectTo("www.bing.com")), "Bing goes to its strict server");
        check("restrict.youtube.com".equals(InspectProxy.connectTo("m.youtube.com")), "YouTube goes to Restricted Mode");
        check("example.org".equals(InspectProxy.connectTo("example.org")), "any other site is left alone");
        InspectProxy.setHostMap(null);
        check("www.google.com".equals(InspectProxy.connectTo("www.google.com")), "with SafeSearch off nothing is changed");

        proxy2.close();
        System.out.println(failures == 0 ? "ALL OK" : failures + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
