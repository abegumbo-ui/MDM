using System.Diagnostics;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Runtime.Versioning;
using System.Text.Json;
using LockGuard.Common;
using Microsoft.Win32;

namespace LockGuard.Service;

/// <summary>
/// Points every network connection (Wi-Fi, cable...) at the filter (127.0.0.1 / ::1), and puts
/// each one back exactly as it was when the filter is turned off: automatic (from the router) or
/// the addresses that were typed in. What was there before is saved the first time, in
/// ProgramData\LockGuard\dns-original.json. Checked again every 30 seconds, so a new Wi-Fi adapter
/// or a changed setting is pointed back at the filter.
/// </summary>
[SupportedOSPlatform("windows")]
public static class NetworkDns
{
    private static string OriginalFile => Path.Combine(Paths.RootDir, "dns-original.json");

    private sealed class Original
    {
        public string Name { get; set; } = "";
        public string Id { get; set; } = "";
        /// <summary>Empty = automatic (DHCP).</summary>
        public List<string> V4 { get; set; } = new();
        /// <summary>Empty = automatic.</summary>
        public List<string> V6 { get; set; } = new();
    }

    private static Dictionary<string, Original> LoadOriginals()
    {
        try { if (File.Exists(OriginalFile)) return JsonSerializer.Deserialize<Dictionary<string, Original>>(File.ReadAllText(OriginalFile)) ?? new(); } catch { }
        return new();
    }

    private static void SaveOriginals(Dictionary<string, Original> o) => File.WriteAllText(OriginalFile, JsonSerializer.Serialize(o));

    private static IEnumerable<NetworkInterface> Adapters() => NetworkInterface.GetAllNetworkInterfaces()
        .Where(n => n.NetworkInterfaceType != NetworkInterfaceType.Loopback && n.NetworkInterfaceType != NetworkInterfaceType.Tunnel
                    && n.OperationalStatus == OperationalStatus.Up && n.Supports(NetworkInterfaceComponent.IPv4));

    /// <summary>Addresses typed in by hand for this adapter, from the registry; empty = automatic.</summary>
    private static List<string> StaticV4(string id) => Typed(@"SYSTEM\CurrentControlSet\Services\Tcpip\Parameters\Interfaces\" + id);
    private static List<string> StaticV6(string id) => Typed(@"SYSTEM\CurrentControlSet\Services\Tcpip6\Parameters\Interfaces\" + id);

    private static List<string> Typed(string path)
    {
        using var k = Registry.LocalMachine.OpenSubKey(path);
        var s = k?.GetValue("NameServer") as string ?? "";
        return s.Split(new[] { ',', ' ' }, StringSplitOptions.RemoveEmptyEntries).ToList();
    }

    private static void Netsh(string args)
    {
        using var p = Process.Start(new ProcessStartInfo("netsh.exe", args) { CreateNoWindow = true, UseShellExecute = false, RedirectStandardOutput = true, RedirectStandardError = true });
        p?.WaitForExit(15000);
    }

    /// <summary>Points every connected adapter at the filter. Returns true when something changed.</summary>
    public static bool Apply()
    {
        var originals = LoadOriginals();
        bool changed = false;
        foreach (var n in Adapters())
        {
            var dns = n.GetIPProperties().DnsAddresses;
            bool v4ok = dns.Any(a => a.AddressFamily == AddressFamily.InterNetwork && a.ToString() == "127.0.0.1")
                        && dns.All(a => a.AddressFamily != AddressFamily.InterNetwork || a.ToString() == "127.0.0.1");
            bool v6ok = !n.Supports(NetworkInterfaceComponent.IPv6) || dns.Any(a => a.AddressFamily == AddressFamily.InterNetworkV6 && a.ToString() == "::1");
            if (v4ok && v6ok) continue;
            if (!originals.ContainsKey(n.Id))
            {
                var st = StaticV4(n.Id).Where(a => a != "127.0.0.1").ToList();
                originals[n.Id] = new Original { Name = n.Name, Id = n.Id, V4 = st, V6 = StaticV6(n.Id).Where(a => a != "::1").ToList() };
                SaveOriginals(originals);
            }
            Netsh($"interface ipv4 set dnsservers name=\"{n.Name}\" source=static address=127.0.0.1 register=none validate=no");
            if (n.Supports(NetworkInterfaceComponent.IPv6)) Netsh($"interface ipv6 set dnsservers name=\"{n.Name}\" source=static address=::1 register=none validate=no");
            changed = true;
        }
        if (changed) Netsh("interface ip delete destinationcache"); // harmless; plus a lookup-cache flush below
        if (changed) FlushCache();
        return changed;
    }

    /// <summary>Puts every adapter back the way it was before the filter.</summary>
    public static void Restore()
    {
        var originals = LoadOriginals();
        foreach (var o in originals.Values)
        {
            var name = NetworkInterface.GetAllNetworkInterfaces().FirstOrDefault(n => n.Id == o.Id)?.Name ?? o.Name;
            if (o.V4.Count == 0) Netsh($"interface ipv4 set dnsservers name=\"{name}\" source=dhcp");
            else
            {
                Netsh($"interface ipv4 set dnsservers name=\"{name}\" source=static address={o.V4[0]} register=primary validate=no");
                for (int i = 1; i < o.V4.Count; i++) Netsh($"interface ipv4 add dnsservers name=\"{name}\" address={o.V4[i]} index={i + 1} validate=no");
            }
            if (o.V6.Count == 0) Netsh($"interface ipv6 set dnsservers name=\"{name}\" source=dhcp");
            else
            {
                Netsh($"interface ipv6 set dnsservers name=\"{name}\" source=static address={o.V6[0]} register=primary validate=no");
                for (int i = 1; i < o.V6.Count; i++) Netsh($"interface ipv6 add dnsservers name=\"{name}\" address={o.V6[i]} index={i + 1} validate=no");
            }
        }
        try { File.Delete(OriginalFile); } catch { }
        FlushCache();
    }

    public static void FlushCache()
    {
        try
        {
            using var p = Process.Start(new ProcessStartInfo("ipconfig.exe", "/flushdns") { CreateNoWindow = true, UseShellExecute = false, RedirectStandardOutput = true });
            p?.WaitForExit(10000);
        }
        catch { }
    }
}
