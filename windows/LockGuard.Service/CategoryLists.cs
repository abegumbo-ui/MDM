using System.Security.Cryptography;
using System.Text.Json;
using LockGuard.Common;

namespace LockGuard.Service;

/// <summary>
/// The category lists (adult, gambling, ...), downloaded from the dashboard's "blocklists" release
/// exactly like the phones do: manifest.json first, then each list the level needs, kept only when
/// its SHA-256 matches the manifest. Saved under ProgramData so they load instantly after a restart.
/// Until a level's lists are loaded the filter blocks everything except essentials (fail closed),
/// the same as the phones.
/// </summary>
public sealed class CategoryLists
{
    private readonly HttpClient _http = new() { Timeout = TimeSpan.FromMinutes(5) };
    private readonly object _lock = new();
    private List<BlockList> _loaded = new();
    private string _loadedKey = "";
    private bool _loadedAds;
    private DateTime _checkedAt = DateTime.MinValue;
    public string? LastError { get; private set; }

    private static string Dir => Path.Combine(Paths.RootDir, "lists");

    /// <summary>True once the lists for this level are loaded.</summary>
    public bool Ready(string level) => _loadedKey.StartsWith(level + "|");

    public bool Blocks(string host)
    {
        List<BlockList> l;
        lock (_lock) l = _loaded;
        foreach (var b in l) if (b.Blocks(host)) return true;
        return false;
    }

    /// <summary>Loads (and when due, refreshes) the lists for this level. Safe to call often.</summary>
    /// ads: also the ad list ("Block ads"). Switching it keeps the current lists working until the new set is in.
    public async Task EnsureAsync(string baseUrl, string level, bool ads = false)
    {
        if (string.IsNullOrEmpty(baseUrl) || (level != "moderate" && level != "strict")) return;
        bool due = !Ready(level) || _loadedAds != ads || DateTime.UtcNow - _checkedAt > TimeSpan.FromHours(6);
        if (!due) return;
        _checkedAt = DateTime.UtcNow;
        Directory.CreateDirectory(Dir);
        var manifestPath = Path.Combine(Dir, "manifest.json");
        string manifestText;
        try
        {
            manifestText = await _http.GetStringAsync(baseUrl.TrimEnd('/') + "/manifest.json");
            File.WriteAllText(manifestPath, manifestText);
        }
        catch (Exception e)
        {
            if (!File.Exists(manifestPath)) { LastError = "Could not download the list index: " + e.Message; return; }
            manifestText = File.ReadAllText(manifestPath); // offline: the copy from last time
        }
        try
        {
            using var doc = JsonDocument.Parse(manifestText);
            var cats = doc.RootElement.GetProperty("categories");
            var names = ListChoice.Names(manifestText, level, ads);
            var lists = new List<BlockList>();
            foreach (var name in names)
            {
                if (!cats.TryGetProperty(name, out var c)) continue;
                var file = c.GetProperty("file").GetString()!;
                var sha = c.GetProperty("sha256").GetString()!.ToLowerInvariant();
                var path = Path.Combine(Dir, Path.GetFileName(file));
                byte[]? bytes = File.Exists(path) ? File.ReadAllBytes(path) : null;
                if (bytes == null || Convert.ToHexString(SHA256.HashData(bytes)).ToLowerInvariant() != sha)
                {
                    bytes = await _http.GetByteArrayAsync(baseUrl.TrimEnd('/') + "/" + file);
                    if (Convert.ToHexString(SHA256.HashData(bytes)).ToLowerInvariant() != sha) throw new InvalidDataException(name + " list did not match its checksum");
                    File.WriteAllBytes(path, bytes);
                }
                lists.Add(BlockList.FromBytes(bytes));
            }
            lock (_lock)
            {
                _loaded = lists;
                _loadedKey = level + "|" + baseUrl;
                _loadedAds = ads;
            }
            LastError = null;
        }
        catch (Exception e)
        {
            LastError = "Could not load the category lists: " + e.Message;
        }
    }
}
