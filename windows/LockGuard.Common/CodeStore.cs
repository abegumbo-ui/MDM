using System.Security.Cryptography;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace LockGuard.Common;

/// <summary>
/// The removal/settings code. Never stored in the clear -- only a salted PBKDF2 hash, the same
/// approach the Android side of this project uses for its master code. Ships with one bootstrap
/// code (DefaultCode) that works exactly once, to get a non-technical person through first setup;
/// the moment a real code is chosen, the bootstrap code is gone for good -- "IsDefault" flips to
/// false and nothing will ever match DefaultCode again, on this install or compared against it.
/// </summary>
public sealed class CodeStore
{
    /// <summary>
    /// The ONLY code that is ever the same across every install. It exists purely so a first-time
    /// setup has something to type before any real code has been chosen -- it is never a permanent
    /// backdoor. Setup.exe refuses to finish without replacing it, and once replaced this constant
    /// is checked against nothing: see Verify() below.
    /// </summary>
    public const string DefaultCode = "223711223";

    private const int Iterations = 100_000; // this runs rarely (setup, uninstall attempts), not in a hot loop
    private const int HashLength = 32;

    private sealed class StoredCode
    {
        [JsonPropertyName("salt")] public string Salt { get; set; } = "";
        [JsonPropertyName("hash")] public string Hash { get; set; } = "";
        [JsonPropertyName("isDefault")] public bool IsDefault { get; set; } = true;
    }

    private readonly string _file;

    public CodeStore(string? file = null)
    {
        _file = file ?? Path.Combine(Paths.RootDir, "code.json");
    }

    /// <summary>True until the bootstrap code has been replaced with a real one.</summary>
    public bool IsDefault()
    {
        var stored = Load();
        return stored is null || stored.IsDefault;
    }

    /// <summary>
    /// Call once, at first install, before anyone has set a real code. Lets Verify(DefaultCode)
    /// succeed until SetCode() is called for the first time.
    /// </summary>
    public void InitializeWithDefault()
    {
        if (File.Exists(_file)) return; // never overwrite an already-initialized install
        var (salt, hash) = Derive(DefaultCode);
        Save(new StoredCode { Salt = salt, Hash = hash, IsDefault = true });
    }

    /// <summary>Replaces the current code. Always clears IsDefault, even if called again later.</summary>
    public void SetCode(string newCode)
    {
        if (string.IsNullOrWhiteSpace(newCode) || newCode.Length < 6)
            throw new ArgumentException("Use a code of at least 6 characters.");
        var (salt, hash) = Derive(newCode);
        Save(new StoredCode { Salt = salt, Hash = hash, IsDefault = false });
    }

    /// <summary>
    /// True if `code` is currently valid. The bootstrap code only ever matches while IsDefault is
    /// still true -- once a real code has been set, typing the old default does nothing, same as
    /// every other wrong guess.
    /// </summary>
    public bool Verify(string code)
    {
        var stored = Load();
        if (stored is null) return false;
        var candidate = DeriveWithSalt(code, stored.Salt);
        return FixedTimeEquals(candidate, stored.Hash);
    }

    private StoredCode? Load()
    {
        if (!File.Exists(_file)) return null;
        try
        {
            return JsonSerializer.Deserialize<StoredCode>(File.ReadAllText(_file));
        }
        catch
        {
            return null;
        }
    }

    private void Save(StoredCode code)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(_file)!);
        File.WriteAllText(_file, JsonSerializer.Serialize(code));
    }

    private static (string salt, string hash) Derive(string code)
    {
        Span<byte> saltBytes = stackalloc byte[16];
        RandomNumberGenerator.Fill(saltBytes);
        var saltHex = Convert.ToHexString(saltBytes);
        return (saltHex, DeriveWithSalt(code, saltHex));
    }

    private static string DeriveWithSalt(string code, string saltHex)
    {
        var salt = Convert.FromHexString(saltHex);
        using var pbkdf2 = new Rfc2898DeriveBytes(code, salt, Iterations, HashAlgorithmName.SHA256);
        return Convert.ToHexString(pbkdf2.GetBytes(HashLength));
    }

    private static bool FixedTimeEquals(string a, string b)
    {
        if (a.Length != b.Length) return false;
        return CryptographicOperations.FixedTimeEquals(
            System.Text.Encoding.ASCII.GetBytes(a), System.Text.Encoding.ASCII.GetBytes(b));
    }
}
