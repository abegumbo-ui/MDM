package com.familymdm.agent.sitepolicy;

import java.util.HashSet;
import java.util.Set;

public final class DashboardMatchCheck {
    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError("FAILED: " + what);
        System.out.println("ok:   " + what);
    }

    public static void main(String[] args) {
        check("mdmpi.tail67992b.ts.net".equals(DashboardMatch.hostOf("https://mdmpi.tail67992b.ts.net")), "hostOf: a plain https address");
        check("mdm.abegumbo.workers.dev".equals(DashboardMatch.hostOf("https://mdm.abegumbo.workers.dev/")), "hostOf: a trailing slash");
        check(DashboardMatch.hostOf(null) == null, "hostOf: null in, null out");
        check(DashboardMatch.hostOf("") == null, "hostOf: empty in, null out");
        check(DashboardMatch.hostOf("not a url") == null, "hostOf: garbage in, null out (never throws)");

        Set<String> hosts = new HashSet<>();
        hosts.add("mdmpi.tail67992b.ts.net");
        hosts.add("mdm.abegumbo.workers.dev");

        check(DashboardMatch.matches(hosts, "mdmpi.tail67992b.ts.net"), "matches: the dashboard's own host");
        check(DashboardMatch.matches(hosts, "MDMPI.TAIL67992B.TS.NET"), "matches: case does not matter");
        check(DashboardMatch.matches(hosts, "mdmpi.tail67992b.ts.net."), "matches: one trailing dot is stripped");
        check(DashboardMatch.matches(hosts, "mdm.abegumbo.workers.dev"), "matches: the backup's host too");
        check(!DashboardMatch.matches(hosts, "evil.mdmpi.tail67992b.ts.net"), "matches: a look-alike SUBdomain is not the dashboard");
        check(!DashboardMatch.matches(hosts, "mdmpi.tail67992b.ts.net.attacker.example"), "matches: a look-alike SUPERdomain is not the dashboard either");
        check(!DashboardMatch.matches(hosts, "notmdmpi.tail67992b.ts.net"), "matches: a different host entirely");
        check(!DashboardMatch.matches(hosts, null), "matches: a null name is never the dashboard");
        check(!DashboardMatch.matches(new HashSet<String>(), "mdmpi.tail67992b.ts.net"), "matches: no configured hosts means nothing matches");
        check(!DashboardMatch.matches(null, "mdmpi.tail67992b.ts.net"), "matches: a null host set is handled, not a crash");

        System.out.println("ALL PASSED");
    }
}
