using System.Text.Json;
using System.Text.Json.Serialization;

namespace LockGuard.Common;

/// <summary>
/// The web filter's settings, as sent by the dashboard (see src/index.js winFilter) and kept on
/// disk so the filter keeps working with no internet. "On" false (or no file) means no filtering.
/// </summary>
public sealed class FilterPolicy
{
    [JsonPropertyName("on")] public bool On { get; set; }
    /// <summary>"moderate", "strict" or "approved" (approved sites only).</summary>
    [JsonPropertyName("level")] public string Level { get; set; } = "moderate";
    /// <summary>Where the category lists are downloaded from (manifest.json and the .mdb files).</summary>
    [JsonPropertyName("base")] public string Base { get; set; } = "";
    [JsonPropertyName("allow")] public List<string> Allow { get; set; } = new();
    [JsonPropertyName("block")] public List<string> Block { get; set; } = new();
    /// <summary>Never blocked: the dashboard, the list downloads, Windows' "am I online" check.</summary>
    [JsonPropertyName("essential")] public List<string> Essential { get; set; } = new();
    [JsonPropertyName("words")] public List<string> Words { get; set; } = new();
    [JsonPropertyName("safeSearch")] public bool SafeSearch { get; set; }
    /// <summary>"off", "moderate" or "strict".</summary>
    [JsonPropertyName("youtube")] public string Youtube { get; set; } = "off";
    [JsonPropertyName("pauseUntil")] public long PauseUntil { get; set; }
    /// <summary>"Turn the internet off for a while": until then only the dashboard and essentials look up (ms).</summary>
    [JsonPropertyName("internetOffUntil")] public long InternetOffUntil { get; set; }
    [JsonPropertyName("recordSites")] public bool RecordSites { get; set; }
    [JsonPropertyName("blockImages")] public bool BlockImages { get; set; }
    [JsonPropertyName("imagesAllow")] public List<string> ImagesAllow { get; set; } = new();
    [JsonPropertyName("noIncognito")] public bool NoIncognito { get; set; }
    [JsonPropertyName("blockExtensions")] public bool BlockExtensions { get; set; }
    [JsonPropertyName("blockOtherBrowsers")] public bool BlockOtherBrowsers { get; set; }
    [JsonPropertyName("blockVpnApps")] public bool BlockVpnApps { get; set; }
    /// <summary>Program file names (e.g. "steam.exe") closed whenever they start.</summary>
    [JsonPropertyName("blockedPrograms")] public List<string> BlockedPrograms { get; set; } = new();
    /// <summary>Full paths of programs that open but may not use the internet (see WfpBlocker.SetAppBlocks).</summary>
    [JsonPropertyName("noInternetPrograms")] public List<string> NoInternetPrograms { get; set; } = new();
    /// <summary>The browsers' start and new-tab page (with "approved sites only": buttons to the allowed sites).</summary>
    [JsonPropertyName("startPage")] public string StartPage { get; set; } = "";

    public bool Paused(long nowMs) => PauseUntil > nowMs;

    public static string FilePath => Path.Combine(Paths.RootDir, "filter.json");

    public static FilterPolicy Load()
    {
        try
        {
            if (File.Exists(FilePath)) return JsonSerializer.Deserialize<FilterPolicy>(File.ReadAllText(FilePath)) ?? new FilterPolicy();
        }
        catch { }
        return new FilterPolicy();
    }

    public void Save()
    {
        Directory.CreateDirectory(Paths.RootDir);
        File.WriteAllText(FilePath, JsonSerializer.Serialize(this));
    }

    public string Json() => JsonSerializer.Serialize(this);
}
