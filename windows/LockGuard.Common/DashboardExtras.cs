using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace LockGuard.Common;

/// <summary>
/// Two things the dashboard sets for this computer, kept on disk so they also work offline:
/// - a removal code (only its SHA-256 fingerprint, valid until a time) that LockGuard accepts like its own code;
/// - the unlock file: while a file with this name, holding this text, is in a user's Downloads
///   folder, every restriction is off.
/// </summary>
public static class DashboardExtras
{
    public sealed class RemovalCode
    {
        [JsonPropertyName("hash")] public string Hash { get; set; } = "";
        [JsonPropertyName("until")] public long Until { get; set; }
    }

    public sealed class UnlockFile
    {
        [JsonPropertyName("name")] public string Name { get; set; } = "";
        [JsonPropertyName("text")] public string Text { get; set; } = "";
    }

    private static string RemovalPath => Path.Combine(Paths.RootDir, "removal-code.json");
    private static string UnlockPath => Path.Combine(Paths.RootDir, "unlock-file.json");

    public static void Save(RemovalCode? code, UnlockFile? unlock)
    {
        try
        {
            Directory.CreateDirectory(Paths.RootDir);
            if (code != null) File.WriteAllText(RemovalPath, JsonSerializer.Serialize(code)); else File.Delete(RemovalPath);
            if (unlock != null) File.WriteAllText(UnlockPath, JsonSerializer.Serialize(unlock)); else File.Delete(UnlockPath);
        }
        catch { }
    }

    /// <summary>Is this the dashboard's removal code, and still valid?</summary>
    public static bool VerifyRemovalCode(string? code)
    {
        try
        {
            if (string.IsNullOrWhiteSpace(code) || !File.Exists(RemovalPath)) return false;
            var r = JsonSerializer.Deserialize<RemovalCode>(File.ReadAllText(RemovalPath));
            if (r == null || r.Until < DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()) return false;
            var hash = Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(code.Trim()))).ToLowerInvariant();
            return CryptographicOperations.FixedTimeEquals(Encoding.ASCII.GetBytes(hash), Encoding.ASCII.GetBytes(r.Hash.ToLowerInvariant()));
        }
        catch { return false; }
    }

    /// <summary>Is the unlock file in some user's Downloads folder right now, with the right text?</summary>
    public static bool UnlockFilePresent()
    {
        try
        {
            if (!File.Exists(UnlockPath)) return false;
            var u = JsonSerializer.Deserialize<UnlockFile>(File.ReadAllText(UnlockPath));
            if (u == null || u.Name.Length < 3 || u.Text.Length == 0 || u.Name.IndexOfAny(Path.GetInvalidFileNameChars()) >= 0) return false;
            var users = Path.Combine(Path.GetPathRoot(Environment.SystemDirectory) ?? "C:\\", "Users");
            foreach (var home in Directory.EnumerateDirectories(users))
            {
                var f = Path.Combine(home, "Downloads", u.Name);
                try
                {
                    if (File.Exists(f) && new FileInfo(f).Length < 10000 && File.ReadAllText(f).Trim() == u.Text.Trim()) return true;
                }
                catch { }
            }
        }
        catch { }
        return false;
    }
}
