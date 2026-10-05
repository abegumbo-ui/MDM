using System.Text.Json;
using System.Text.Json.Serialization;

namespace LockGuard.Common;

/// <summary>
/// Whether the last attempt to apply (or remove) the firewall lockdown actually succeeded, and
/// why not if it didn't. Setup reads this so it can tell the difference between "LockGuard is ON"
/// (config.json says so) and "LockGuard is ON and the firewall actually agrees" -- those used to
/// always be shown as the same thing, which is exactly how a silent COM failure could leave
/// someone's internet wide open while Setup kept insisting everything was locked down.
/// </summary>
public sealed class FirewallStatus
{
    [JsonPropertyName("ok")] public bool Ok { get; set; } = true;
    [JsonPropertyName("error")] public string? Error { get; set; }

    public static FirewallStatus Load()
    {
        if (!File.Exists(Paths.FirewallStatusFile)) return new FirewallStatus();
        try
        {
            return JsonSerializer.Deserialize<FirewallStatus>(File.ReadAllText(Paths.FirewallStatusFile)) ?? new FirewallStatus();
        }
        catch
        {
            return new FirewallStatus();
        }
    }

    public void Save()
    {
        try
        {
            Directory.CreateDirectory(Paths.RootDir);
            File.WriteAllText(Paths.FirewallStatusFile, JsonSerializer.Serialize(this));
        }
        catch
        {
            // Status reporting is best-effort; it should never be the thing that crashes the apply loop.
        }
    }
}
