using System.Runtime.Versioning;

namespace LockGuard.Common;

/// <summary>
/// The computer's installed programs, for the dashboard's program list: every Start menu shortcut
/// (all users, and each user's own) followed to its .exe. Portable programs with no shortcut are
/// not found; those can still be closed by file name.
/// </summary>
[SupportedOSPlatform("windows")]
public static class ProgramList
{
    public sealed record Program(string Name, string Path);

    public static List<Program> Find()
    {
        var byPath = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        var shellType = Type.GetTypeFromProgID("WScript.Shell");
        if (shellType is null) return new List<Program>();
        dynamic shell = Activator.CreateInstance(shellType)!;
        foreach (var dir in Dirs())
        {
            if (!Directory.Exists(dir)) continue;
            IEnumerable<string> links;
            try { links = Directory.EnumerateFiles(dir, "*.lnk", SearchOption.AllDirectories).Take(3000).ToList(); }
            catch { continue; }
            foreach (var lnk in links)
            {
                try
                {
                    dynamic sc = shell.CreateShortcut(lnk);
                    string target = (string)(sc.TargetPath ?? "");
                    if (!target.EndsWith(".exe", StringComparison.OrdinalIgnoreCase) || !File.Exists(target)) continue;
                    var name = System.IO.Path.GetFileNameWithoutExtension(lnk);
                    if (name.Contains("uninstall", StringComparison.OrdinalIgnoreCase)) continue;
                    byPath.TryAdd(target, name);
                }
                catch { }
            }
        }
        return byPath.Select(kv => new Program(kv.Value, kv.Key)).OrderBy(p => p.Name, StringComparer.OrdinalIgnoreCase).Take(500).ToList();
    }

    private static IEnumerable<string> Dirs()
    {
        yield return Environment.GetFolderPath(Environment.SpecialFolder.CommonStartMenu);
        yield return Environment.GetFolderPath(Environment.SpecialFolder.CommonDesktopDirectory);
        var users = System.IO.Path.Combine(System.IO.Path.GetPathRoot(Environment.SystemDirectory) ?? "C:\\", "Users");
        IEnumerable<string> homes;
        try { homes = Directory.EnumerateDirectories(users).ToList(); } catch { yield break; }
        foreach (var home in homes)
        {
            yield return System.IO.Path.Combine(home, @"AppData\Roaming\Microsoft\Windows\Start Menu\Programs");
            yield return System.IO.Path.Combine(home, "Desktop");
        }
    }
}
