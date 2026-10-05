namespace LockGuard.Setup;

/// <summary>
/// Finds installed programs the easy way -- by reading Start Menu shortcuts and following them to
/// the real .exe -- so "Add a program" can show a pick list instead of making someone browse to a
/// file path themselves. Misses anything with no Start Menu entry (portable apps, some games);
/// the picker falls back to a normal file browser for those.
/// </summary>
internal static class InstalledPrograms
{
    public static List<(string Name, string Path)> Find()
    {
        var byPath = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        var shellType = Type.GetTypeFromProgID("WScript.Shell");
        if (shellType is null) return new List<(string, string)>();
        dynamic shell = Activator.CreateInstance(shellType)!;

        foreach (var dir in StartMenuDirs())
        {
            if (!Directory.Exists(dir)) continue;
            IEnumerable<string> shortcuts;
            try
            {
                shortcuts = Directory.EnumerateFiles(dir, "*.lnk", SearchOption.AllDirectories);
            }
            catch
            {
                continue; // a folder we can't read shouldn't stop the rest
            }

            foreach (var lnk in shortcuts)
            {
                try
                {
                    dynamic shortcut = shell.CreateShortcut(lnk);
                    string target = (string)(shortcut.TargetPath ?? "");
                    if (string.IsNullOrWhiteSpace(target) || !target.EndsWith(".exe", StringComparison.OrdinalIgnoreCase)) continue;
                    if (!File.Exists(target)) continue;
                    byPath[target] = Path.GetFileNameWithoutExtension(lnk);
                }
                catch
                {
                    // an unreadable or broken shortcut just gets skipped
                }
            }
        }

        return byPath
            .Select(kv => (Name: kv.Value, Path: kv.Key))
            .OrderBy(p => p.Name, StringComparer.OrdinalIgnoreCase)
            .ToList();
    }

    private static IEnumerable<string> StartMenuDirs()
    {
        yield return Environment.GetFolderPath(Environment.SpecialFolder.CommonStartMenu); // every user
        yield return Environment.GetFolderPath(Environment.SpecialFolder.StartMenu); // this user only
    }
}
