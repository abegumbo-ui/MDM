package com.familymdm.agent;

import android.content.Context;

import com.familymdm.agent.sitepolicy.DashboardMatch;

import java.net.InetAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Which address(es) this phone's dashboard lives at right now ("server", and the primary/backup it last learned from a sync reply), so
 * DnsFilterVpnService and TcpRelay can let it through whatever else is blocked. See DashboardMatch for why and for the exact-match rule.
 */
final class DashboardHost {
    private DashboardHost() {
    }

    private static volatile Set<String> ips = Collections.emptySet();
    private static volatile long lastResolve;

    static Set<String> hosts(Context c) {
        Set<String> out = new HashSet<>();
        addHost(out, Agent.prefs(c).getString("server", null));
        addHost(out, Agent.prefs(c).getString("serverPrimary", null));
        addHost(out, Agent.prefs(c).getString("serverBackup", null));
        return out;
    }

    private static void addHost(Set<String> out, String url) {
        String h = DashboardMatch.hostOf(url);
        if (h != null) out.add(h);
    }

    /** True for a DNS question or a TLS SNI name that is the dashboard itself. */
    static boolean isDashboardHost(Context c, String name) {
        return DashboardMatch.matches(hosts(c), name);
    }

    /**
     * Re-resolves the dashboard host(s) into their current IP address(es) every so often, straight from this process -- the agent's own
     * traffic already bypasses its own VPN tunnel (see DnsFilterVpnService's addDisallowedApplication), so no protected socket is needed
     * here. QUIC hides its destination hostname inside encryption, so the UDP/QUIC block in DnsFilterVpnService has to go by IP instead
     * of by name; this keeps that IP set current. Cheap to call often: it does nothing until 90 seconds have passed.
     */
    static void refresh(Context c) {
        long now = System.currentTimeMillis();
        if (now - lastResolve < 90_000) return;
        lastResolve = now;
        Set<String> hs = hosts(c);
        if (hs.isEmpty()) return;
        new Thread(() -> {
            Set<String> out = new HashSet<>();
            for (String h : hs) {
                try {
                    for (InetAddress a : InetAddress.getAllByName(h)) {
                        byte[] addr = a.getAddress();
                        if (addr.length == 4) out.add(dotted(addr));
                    }
                } catch (Exception ignored) {
                    // this phone's network is down, or this name does not resolve right now: the IP set simply keeps its last value
                }
            }
            if (!out.isEmpty()) ips = out;
        }, "mdm-dashboard-resolve").start();
    }

    static boolean isDashboardIp(byte[] ip) {
        return ip != null && ip.length == 4 && ips.contains(dotted(ip));
    }

    private static String dotted(byte[] ip) {
        return (ip[0] & 0xFF) + "." + (ip[1] & 0xFF) + "." + (ip[2] & 0xFF) + "." + (ip[3] & 0xFF);
    }
}
