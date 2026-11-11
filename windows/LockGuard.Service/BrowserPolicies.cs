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
            if (p.StartPage.Length > 0)
            {
                Add(k, "HomepageLocation", p.StartPage);
                Add(k, "HomepageIsNewTabPage", 0);
                Add(k, "ShowHomeButton", 1);
                Add(k, "NewTabPageLocation", p.StartPage);
                Add(k, "RestoreOnStartup", 4);
                Add(k + @"\RestoreOnStartupURLs", "", new List<string> { p.StartPage });
            }
            if (allow.Count > 0) Add(k + @"\URLAllowlist", "", allow);
        }
        Add(Firefox + @"\DNSOverHTTPS", "Enabled", 0);
        Add(Firefox + @"\DNSOverHTTPS", "Locked", 1);
        if (p.NoIncognito) Add(Firefox, "DisablePrivateBrowsing", 1);
        if (p.StartPage.Length > 0)
        {
            Add(Firefox + @"\Homepage", "URL", p.StartPage);
            Add(Firefox + @"\Homepage", "Locked", 1);
            Add(Firefox + @"\Homepage", "StartPage", "homepage");
        }
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

    /// <summary>What a value (or numbered list) was before LockGuard first set it, so it can be put back.</summary>
    private sealed class Was
    {
        public bool Existed { get; set; }
        public string? Value { get; set; }
        public bool Dword { get; set; }
        public List<string>? List { get; set; }
    }

    private static string BackupFile => Path.Combine(Paths.RootDir, "policies-backup.json");

    private static Dictionary<string, Was> LoadBackup()
    {
        try { if (File.Exists(BackupFile)) return JsonSerializer.Deserialize<Dictionary<string, Was>>(File.ReadAllText(BackupFile)) ?? new(); } catch { }
        return new();
    }

    private static Was Capture(string key, string name)
    {
        using var k = Registry.LocalMachine.OpenSubKey(key);
        if (k == null) return new Was();
        if (name == "")
            return new Was { Existed = true, List = k.GetValueNames().OrderBy(n => int.TryParse(n, out var x) ? x : int.MaxValue).Select(n => k.GetValue(n)?.ToString() ?? "").ToList() };
        var v = k.GetValue(name);
        if (v == null) return new Was();
        return new Was { Existed = true, Value = v.ToString(), Dword = k.GetValueKind(name) == RegistryValueKind.DWord };
    }

    private static void Restore(string key, string name, Was was)
    {
        if (!was.Existed) return;
        using var k = Registry.LocalMachine.CreateSubKey(key, true);
        if (name == "") { for (int n = 0; n < (was.List?.Count ?? 0); n++) k.SetValue((n + 1).ToString(), was.List![n], RegistryValueKind.String); }
        else if (was.Dword && int.TryParse(was.Value, out var d)) k.SetValue(name, d, RegistryValueKind.DWord);
        else k.SetValue(name, was.Value ?? "", RegistryValueKind.String);
    }

    public static string? LastError { get; private set; }

    /// <summary>Writes what the policy asks for and removes anything this app set before that is no longer wanted.</summary>
    public static void Apply(FilterPolicy p)
    {
        try
        {
            var want = Desired(p);
            var owned = LoadOwned();
            var backup = LoadBackup();
            var wantKeys = want.Select(i => new[] { i.Key, i.Name }).ToList();
            foreach (var old in owned)
            {
                if (wantKeys.Any(w => w[0] == old[0] && w[1] == old[1])) continue;
                if (old[1] == "") Registry.LocalMachine.DeleteSubKeyTree(old[0], false);
                else using (var k = Registry.LocalMachine.OpenSubKey(old[0], true)) k?.DeleteValue(old[1], false);
                // Put back whatever was there before LockGuard first set it.
                var id = old[0] + "|" + old[1];
                if (backup.TryGetValue(id, out var was)) { Restore(old[0], old[1], was); backup.Remove(id); }
            }
            foreach (var i in want)
            {
                var id = i.Key + "|" + i.Name;
                if (!backup.ContainsKey(id) && !owned.Any(o => o[0] == i.Key && o[1] == i.Name)) backup[id] = Capture(i.Key, i.Name);
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
            File.WriteAllText(BackupFile, JsonSerializer.Serialize(backup.Where(kv => kv.Value.Existed).ToDictionary(kv => kv.Key, kv => kv.Value)));
            LastError = null;
        }
        catch (Exception e)
        {
            LastError = "Could not set browser policies: " + e.Message;
        }
    }

    public static void Remove() => Apply(new FilterPolicy { On = false });
}
