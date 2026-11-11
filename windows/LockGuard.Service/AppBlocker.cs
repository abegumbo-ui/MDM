using System.Diagnostics;
using LockGuard.Common;

namespace LockGuard.Service;

/// <summary>
/// Closes blocked programs the moment they start (checked every 2 seconds): the ones the dashboard
/// lists by name, plus, when switched on, browsers the filter cannot lock (Tor, Opera...) and VPN
/// or proxy programs that would carry everything around the filter.
/// </summary>
public static class AppBlocker
{
    public static readonly string[] OtherBrowsers =
    {
        "opera.exe", "opera_gx.exe", "vivaldi.exe", "waterfox.exe", "librewolf.exe", "palemoon.exe", "seamonkey.exe",
        "maxthon.exe", "ucbrowser.exe", "avastbrowser.exe", "avgbrowser.exe", "ccleanerbrowser.exe", "slimjet.exe", "iron.exe",
        "epic.exe", "duckduckgo.exe", "arc.exe", "floorp.exe", "zen.exe", "thorium.exe", "tor.exe", "yandex.exe", "qutebrowser.exe",
    };

    public static readonly string[] VpnApps =
    {
        "openvpn.exe", "openvpn-gui.exe", "openvpnconnect.exe", "protonvpn.exe", "protonvpn.client.exe", "protonvpnservice.exe",
        "nordvpn.exe", "nordvpn-service.exe", "expressvpn.exe", "expressvpnd.exe", "windscribe.exe", "windscribeservice.exe",
        "psiphon3.exe", "psiphon.exe", "hotspotshield.exe", "hsscp.exe", "tunnelbear.exe", "surfshark.exe", "surfshark.service.exe",
        "cyberghost.exe", "wireguard.exe", "warp-svc.exe", "cloudflare warp.exe", "ultrasurf.exe", "hide.me.exe", "pia-client.exe",
        "pia-service.exe", "atlasvpn.exe", "urban-vpn.exe", "betternet.exe", "hola.exe", "zenmate.exe", "lantern.exe", "outline.exe",
        "v2rayn.exe", "clash-verge.exe", "clash for windows.exe", "nekoray.exe", "shadowsocks.exe", "privadovpn.exe", "mullvad vpn.exe",
        "mullvad-daemon.exe", "ivpn-client.exe", "vpnclient.exe", "softether.exe", "goose vpn.exe",
    };

    private static readonly Dictionary<string, DateTime> LastNoted = new();

    /// <summary>One pass: closes what should not be running. Returns lines for the dashboard's log.</summary>
    public static List<string> Sweep(FilterPolicy p)
    {
        var notes = new List<string>();
        if (!p.On) return notes;
        var blocked = new HashSet<string>(p.BlockedPrograms.Select(x => x.Trim().ToLowerInvariant()).Where(x => x.Length > 0).Select(x => x.EndsWith(".exe") ? x : x + ".exe"));
        if (p.BlockOtherBrowsers) foreach (var b in OtherBrowsers) blocked.Add(b);
        if (p.BlockVpnApps) foreach (var v in VpnApps) blocked.Add(v);
        if (blocked.Count == 0 && !p.BlockOtherBrowsers) return notes;
        foreach (var proc in Process.GetProcesses())
        {
            try
            {
                var exe = proc.ProcessName.ToLowerInvariant() + ".exe";
                bool hit = blocked.Contains(exe);
                if (!hit && p.BlockOtherBrowsers && exe == "firefox.exe")
                {
                    // Tor Browser is Firefox inside a "Tor Browser" folder.
                    string? path = null;
                    try { path = proc.MainModule?.FileName; } catch { }
                    hit = path != null && path.Contains("tor browser", StringComparison.OrdinalIgnoreCase);
                }
                if (!hit) continue;
                proc.Kill(entireProcessTree: true);
                if (!LastNoted.TryGetValue(exe, out var t) || DateTime.UtcNow - t > TimeSpan.FromMinutes(10))
                {
                    LastNoted[exe] = DateTime.UtcNow;
                    notes.Add("Closed a blocked program: " + exe);
                }
            }
            catch { /* already gone, or protected */ }
            finally { proc.Dispose(); }
        }
        return notes;
    }
}
