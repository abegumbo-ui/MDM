using System.Runtime.Versioning;
using System.Text.Json;
using LockGuard.Common;
using Microsoft.Win32;

namespace LockGuard.Service;

/// <summary>
/// Locks browser settings through Windows' official browser policies (the registry keys under
/// HKLM\SOFTWARE\Policies that Chrome, Edge, Brave and Firefox read; checkable in each browser at
/// chrome://policy, edge://policy, brave://policy, about:policies). Every value written here is
/// remembered in ProgramData\LockGuard\policies-owned.json, and only those are ever changed or
/// removed -- anything an administrator set by hand is left alone.
/// </summary>
[SupportedOSPlatform("windows")]
public static class BrowserPolicies
{
    private static readonly string[] Chromium = { @"SOFTWARE\Policies\Google\Chrome", @"SOFTWARE\Policies\BraveSoftware\Brave", @"SOFTWARE\Policies\Chromium" };
    private const string Edge = @"SOFTWARE\Policies\Microsoft\Edge";
    private const string Firefox = @"SOFTWARE\Policies\Mozilla\Firefox";
    private static string OwnedFile => Path.Combine(Paths.RootDir, "policies-owned.json");

    /// <summary>One value we set: key path, value name ("" + list = a numbered list subkey).</summary>
    private sealed record Item(string Key, string Name, object Value);

    private static List<Item> Desired(FilterPolicy p)
    {
        var o = new List<Item>();
        if (!p.On) return o;
        var lists = new Dictionary<string, List<string>>();
        void Add(string key, string name, object v) => o.Add(new Item(key, name, v));
        foreach (var k in Chromium.Append(Edge))
        {
            bool edge = k == Edge;
            Add(k, "DnsOverHttpsMode", "off"); // the browser's own lookups would skip the filter
            if (p.SafeSearch) Add(k, "ForceGoogleSafeSearch", 1);
            if (edge && p.SafeSearch) Add(k, "ForceBingSafeSearch", 2);
            if (p.Youtube == "moderate") Add(k, "ForceYouTubeRestrict", 1);
            if (p.Youtube == "strict") Add(k, "ForceYouTubeRestrict", 2);
            if (p.NoIncognito) { Add(k, edge ? "InPrivateModeAvailability" : "IncognitoModeAvailability", 1); Add(k, "BrowserGuestModeEnabled", 0); }
            if (p.BlockImages) Add(k, "DefaultImagesSetting", 2);
            if (p.BlockImages && p.ImagesAllow.Count > 0) Add(k + @"\ImagesAllowedForUrls", "", p.ImagesAllow);
            if (p.BlockExtensions) Add(k + @"\ExtensionInstallBlocklist", "", new List<string> { "*" });
            var block = new List<string>();
            var allow = new List<string>();
            if (p.Level == "approved")
            {
                block.Add("*");
                allow.AddRange(p.Allow);
                allow.AddRange(p.Essential);
                allow.Add(edge ? "edge://*" : "chrome://*");
            }
            else block.AddRange(p.Block);
            if (block.Count > 0) Add(k + @"\URLBlocklist", "", block);
            if (allow.Count > 0) Add(k + @"\URLAllowlist", "", allow);
        }
        Add(Firefox + @"\DNSOverHTTPS", "Enabled", 0);
        Add(Firefox + @"\DNSOverHTTPS", "Locked", 1);
        if (p.NoIncognito) Add(Firefox, "DisablePrivateBrowsing", 1);
        if (p.Level == "approved")
        {
            Add(Firefox + @"\WebsiteFilter\Block", "", new List<string> { "<all_urls>" });
            Add(Firefox + @"\WebsiteFilter\Exceptions", "", p.Allow.Concat(p.Essential).SelectMany(d => new[] { "https://" + d + "/*", "https://*." + d + "/*" }).ToList());
        }
        return o;
    }

    private static List<string[]> LoadOwned()
    {
        try { if (File.Exists(OwnedFile)) return JsonSerializer.Deserialize<List<string[]>>(File.ReadAllText(OwnedFile)) ?? new(); } catch { }
        return new();
    }

    public static string? LastError { get; private set; }

    /// <summary>Writes what the policy asks for and removes anything this app set before that is no longer wanted.</summary>
    public static void Apply(FilterPolicy p)
    {
        try
        {
            var want = Desired(p);
            var owned = LoadOwned();
            var wantKeys = want.Select(i => new[] { i.Key, i.Name }).ToList();
            foreach (var old in owned)
            {
                if (wantKeys.Any(w => w[0] == old[0] && w[1] == old[1])) continue;
                if (old[1] == "") Registry.LocalMachine.DeleteSubKeyTree(old[0], false);
                else using (var k = Registry.LocalMachine.OpenSubKey(old[0], true)) k?.DeleteValue(old[1], false);
            }
            foreach (var i in want)
            {
                if (i.Value is List<string> list)
                {
                    Registry.LocalMachine.DeleteSubKeyTree(i.Key, false); // rewritten whole: no leftover entries
                    using var k = Registry.LocalMachine.CreateSubKey(i.Key, true);
                    for (int n = 0; n < list.Count; n++) k.SetValue((n + 1).ToString(), list[n], RegistryValueKind.String);
                }
                else
                {
                    using var k = Registry.LocalMachine.CreateSubKey(i.Key, true);
                    if (i.Value is int d) k.SetValue(i.Name, d, RegistryValueKind.DWord);
                    else k.SetValue(i.Name, i.Value.ToString()!, RegistryValueKind.String);
                }
            }
            Directory.CreateDirectory(Paths.RootDir);
            File.WriteAllText(OwnedFile, JsonSerializer.Serialize(wantKeys));
            LastError = null;
        }
        catch (Exception e)
        {
            LastError = "Could not set browser policies: " + e.Message;
        }
    }

    public static void Remove() => Apply(new FilterPolicy { On = false });
}
