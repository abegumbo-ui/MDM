package com.familymdm.agent.sitepolicy;

public final class ConnectivityProbeCheck {
    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError("FAILED: " + what);
        System.out.println("ok:   " + what);
    }

    public static void main(String[] args) {
        check(ConnectivityProbe.isProbeHost("connectivitycheck.gstatic.com"), "the gstatic check host is a probe host");
        check(ConnectivityProbe.isProbeHost("CONNECTIVITYCHECK.GSTATIC.COM."), "case and one trailing dot do not matter");
        check(ConnectivityProbe.isProbeHost("clients3.google.com"), "clients3.google.com (the fallback) is a probe host");
        check(ConnectivityProbe.isProbeHost("connectivitycheck.android.com"), "connectivitycheck.android.com is a probe host");
        check(!ConnectivityProbe.isProbeHost("www.google.com"), "www.google.com is not allowed as a whole host (it serves real pages)");
        check(!ConnectivityProbe.isProbeHost("gstatic.com"), "a different gstatic host is not a probe host");
        check(!ConnectivityProbe.isProbeHost("evil.connectivitycheck.gstatic.com"), "a look-alike subdomain is not a probe host");
        check(!ConnectivityProbe.isProbeHost("connectivitycheck.gstatic.com.attacker.example"), "a look-alike superdomain is not a probe host");
        check(!ConnectivityProbe.isProbeHost(null), "null is not a probe host (never throws)");

        check(ConnectivityProbe.isProbe("www.google.com", "/generate_204"), "https://www.google.com/generate_204 is the check");
        check(ConnectivityProbe.isProbe("www.google.com", "/gen_204"), "http://www.google.com/gen_204 is the check");
        check(ConnectivityProbe.isProbe("play.googleapis.com", "/generate_204"), "play.googleapis.com/generate_204 is the check");
        check(ConnectivityProbe.isProbe("www.google.com", "/generate_204?x=1"), "a query after the path still counts");
        check(!ConnectivityProbe.isProbe("www.google.com", "/search?q=generate_204"), "a search that mentions the word is not the check");
        check(!ConnectivityProbe.isProbe("www.google.com", "/"), "google's front page is not the check");
        check(!ConnectivityProbe.isProbe("www.fox.com", "/generate_204"), "another site with the same path is not the check");
        check(!ConnectivityProbe.isProbe("www.google.com", null), "no path is not the check (never throws)");
        check(ConnectivityProbe.isProbe("connectivitycheck.gstatic.com", "/anything"), "the probe-only host passes whatever the path");
        // The HTTPS fetch of the check is passed through untouched (it cannot accept the inspection certificate), recognised by Android's fixed User-Agent.
        String ua = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3112.32 Safari/537.36";
        check(ConnectivityProbe.isProbeTunnel("www.google.com", ua), "the check's HTTPS tunnel to www.google.com is passed through");
        check(ConnectivityProbe.isProbeTunnel("play.googleapis.com", ua), "...also to play.googleapis.com");
        check(ConnectivityProbe.isProbeTunnel("connectivitycheck.gstatic.com", null), "a probe-only host is always passed through");
        check(!ConnectivityProbe.isProbeTunnel("www.google.com", "Mozilla/5.0 (Linux; Android 15; SM-A166P) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Mobile Safari/537.36"), "Chrome on the phone is still inspected");
        check(!ConnectivityProbe.isProbeTunnel("www.google.com", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36"), "desktop-site mode in Chrome (a newer Chrome) is still inspected");
        check(ConnectivityProbe.isProbeTunnel("www.google.com", "okhttp/2.7.5") && ConnectivityProbe.isProbeTunnel("www.google.com", "Dalvik/2.1.0 (Linux; U; Android 15; SM-A166P Build/AP3A)"), "the identity Android's own HTTP stack puts on a tunnel request is passed through too");
        check(ConnectivityProbe.isProbeTunnel("www.google.com", null) && ConnectivityProbe.isProbeTunnel("play.googleapis.com", ""), "a tunnel request with no User-Agent at all is passed through (only these two hosts)");
        check(!ConnectivityProbe.isProbeTunnel("www.fox.com", null) && !ConnectivityProbe.isProbeTunnel("www.fox.com", "okhttp/4.12.0"), "...never any other host");
        check(!ConnectivityProbe.isProbeTunnel("www.google.com", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/139.0 Mobile Safari/537.36") && !ConnectivityProbe.isProbeTunnel("www.google.com", "Mozilla/5.0 (Linux; Android 15; SAMSUNG SM-A166P) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/28.0 Chrome/130.0 Mobile Safari/537.36"), "no browser is passed through");
        check(!ConnectivityProbe.isProbeTunnel("www.fox.com", ua), "the same User-Agent to another site is still inspected");
        check(!ConnectivityProbe.isProbeTunnel(null, ua), "null host never throws");
        System.out.println("ConnectivityProbeCheck OK");
    }
}
