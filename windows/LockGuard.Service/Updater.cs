using System.Diagnostics;
using System.Reflection;
using System.Security.Cryptography;
using LockGuard.Common;

namespace LockGuard.Service;

/// <summary>
/// Keeps LockGuard up to date by itself, like the phones' Agent: when the dashboard says a newer
/// build is published (GitHub release "lockguard-vN"), the service downloads LockGuard-Setup.exe,
/// checks it against the published SHA-256, and runs it with /quiet. It runs as the system, so no
/// "allow changes?" prompt appears. Builds made on a computer (no build number) never update.
/// </summary>
public static class Updater
{
    private static readonly HttpClient Http = new() { Timeout = TimeSpan.FromMinutes(10) };
    private static DateTime _lastTry = DateTime.MinValue;

    /// <summary>This build's number, written by CI into Build.txt (0 for a local build).</summary>
    public static int Build { get; } = ReadBuild();

    private static int ReadBuild()
    {
        try
        {
            using var s = Assembly.GetExecutingAssembly().GetManifestResourceStream("Build.txt");
            if (s == null) return 0;
            return int.TryParse(new StreamReader(s).ReadToEnd().Trim(), out var n) ? n : 0;
        }
        catch { return 0; }
    }

    public static async Task MaybeUpdateAsync(DashboardClient.UpdateInfo? u, Action<string> note)
    {
        if (u == null || Build == 0 || u.Build <= Build || string.IsNullOrEmpty(u.Url)) return;
        if (DateTime.UtcNow - _lastTry < TimeSpan.FromHours(1)) return;
        _lastTry = DateTime.UtcNow;
        if (!Uri.TryCreate(u.Url, UriKind.Absolute, out var uri) || uri.Scheme != "https" ||
            !(uri.Host == "github.com" || uri.Host.EndsWith(".github.com") || uri.Host.EndsWith(".githubusercontent.com"))) return;
        try
        {
            var dir = Path.Combine(Paths.RootDir, "update");
            Directory.CreateDirectory(dir);
            var path = Path.Combine(dir, "LockGuard-Setup.exe");
            var bytes = await Http.GetByteArrayAsync(uri);
            if (!string.IsNullOrEmpty(u.Sha256) && !string.Equals(Convert.ToHexString(SHA256.HashData(bytes)), u.Sha256, StringComparison.OrdinalIgnoreCase))
            {
                note("An update was downloaded but did not match its checksum; not installed.");
                return;
            }
            await File.WriteAllBytesAsync(path, bytes);
            note($"Updating LockGuard from build {Build} to build {u.Build}.");
            Process.Start(new ProcessStartInfo(path, "/quiet") { UseShellExecute = false, CreateNoWindow = true });
        }
        catch (Exception e)
        {
            note("Could not update LockGuard: " + e.Message);
        }
    }
}
