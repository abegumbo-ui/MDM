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
        System.out.println("ConnectivityProbeCheck OK");
    }
}
