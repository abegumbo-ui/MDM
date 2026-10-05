using System.Text.Json;
using System.Text.Json.Serialization;

namespace LockGuard.Common;

/// <summary>
/// What's allowed through: a list of program paths (the email client, etc.) that keep working
/// with the internet while everything else on the computer is cut off from it. Apps themselves
/// are never blocked from opening -- only their internet access is -- so Word, the Microsoft
/// Store, everything still opens normally, it just can't reach the internet if it isn't listed
/// here. Phase 2 (not built yet) adds specific allowed websites for a browser; for now a browser
/// added to this list gets full internet access through it, same as any other allowed program.
/// </summary>
public sealed class LockConfig
{
    [JsonPropertyName("enabled")] public bool Enabled { get; set; } = true;
    [JsonPropertyName("allowedPrograms")] public List<string> AllowedPrograms { get; set; } = new();

    public static LockConfig Load()
    {
        if (!File.Exists(Paths.ConfigFile)) return new LockConfig();
        try
        {
            return JsonSerializer.Deserialize<LockConfig>(File.ReadAllText(Paths.ConfigFile)) ?? new LockConfig();
        }
        catch
        {
            return new LockConfig();
        }
    }

    public void Save()
    {
        Directory.CreateDirectory(Paths.RootDir);
        File.WriteAllText(Paths.ConfigFile, JsonSerializer.Serialize(this, new JsonSerializerOptions { WriteIndented = true }));
    }
}
