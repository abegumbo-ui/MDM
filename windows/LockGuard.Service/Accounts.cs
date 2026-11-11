using System.Diagnostics;

namespace LockGuard.Service;

/// <summary>
/// The computer's Windows accounts and which are administrators, for the dashboard's warning: an
/// administrator account can remove any filter, so the child's account should be a standard one.
/// Read with PowerShell's own account commands (by the Administrators group's fixed ID, so it works
/// in any Windows language), at most once an hour.
/// </summary>
public static class Accounts
{
    private static DateTime _at = DateTime.MinValue;
    private static object _cached = new { users = Array.Empty<string>(), admins = Array.Empty<string>() };

    public static object Report()
    {
        if (DateTime.UtcNow - _at < TimeSpan.FromHours(1)) return _cached;
        _at = DateTime.UtcNow;
        try
        {
            var users = Run("Get-LocalUser | Where-Object Enabled | ForEach-Object Name");
            var admins = Run("Get-LocalGroupMember -SID S-1-5-32-544 | Where-Object ObjectClass -eq 'User' | ForEach-Object { $_.Name.Split('\\')[-1] }");
            _cached = new { users, admins };
        }
        catch { }
        return _cached;
    }

    private static string[] Run(string script)
    {
        using var p = Process.Start(new ProcessStartInfo("powershell.exe", "-NoProfile -NonInteractive -Command \"" + script.Replace("\"", "\\\"") + "\"")
        {
            CreateNoWindow = true, UseShellExecute = false, RedirectStandardOutput = true, RedirectStandardError = true,
        })!;
        var output = p.StandardOutput.ReadToEnd();
        p.WaitForExit(20000);
        return output.Split('\n', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries).Where(s => s.Length > 0).ToArray();
    }
}
