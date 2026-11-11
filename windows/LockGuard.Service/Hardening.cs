using System.Diagnostics;
using System.Runtime.Versioning;
using System.Security.AccessControl;
using System.Security.Principal;
using LockGuard.Common;
using Microsoft.Win32;

namespace LockGuard.Service;

/// <summary>
/// Makes LockGuard resist an ordinary administrator, the way commercial filters do: only the
/// system itself (this service, its updater, its uninstaller -- all running as SYSTEM) may stop,
/// reconfigure or delete the service, change its registry entry, or change its files. An
/// administrator can still look, and Setup still works (it talks to the service through its pipe,
/// with the code). Removal: the code in Setup, or "Uninstall" on the dashboard.
///
/// Not unbreakable, and not meant to be: an administrator who knows how can take ownership back
/// (Windows always allows that), boot into Safe Mode, or reset Windows. It stops the everyday
/// "I'm an administrator, I'll just uninstall it". Re-applied every 10 minutes.
/// Turned off by switching it off on the dashboard (then everything goes back to normal rights).
/// </summary>
[SupportedOSPlatform("windows")]
public static class Hardening
{
    private static readonly SecurityIdentifier System = new(WellKnownSidType.LocalSystemSid, null);
    private static readonly SecurityIdentifier Admins = new(WellKnownSidType.BuiltinAdministratorsSid, null);
    private static readonly SecurityIdentifier Users = new(WellKnownSidType.BuiltinUsersSid, null);

    // Service rights in SDDL: CC query config, DC change config, LC query status, SW enumerate
    // dependents, RP start, WP stop, DT pause, LO interrogate, CR user control, SD delete, RC read
    // control, WD write DAC, WO write owner.
    private const string Locked = "D:(A;;CCDCLCSWRPWPDTLOCRSDRCWDWO;;;SY)(A;;CCLCSWRPLOCRRC;;;BA)(A;;CCLCSWLOCRRC;;;IU)(A;;CCLCSWLOCRRC;;;SU)";
    private const string Normal = "D:(A;;CCLCSWRPWPDTLOCRRC;;;SY)(A;;CCDCLCSWRPWPDTLOCRSDRCWDWO;;;BA)(A;;CCLCSWLOCRRC;;;IU)(A;;CCLCSWLOCRRC;;;SU)";

    public static string? LastError { get; private set; }
    public static bool On { get; private set; }

    private static string ProgramDir => Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "LockGuard");

    public static void Apply(bool on)
    {
        var errors = new List<string>();
        void Try(string what, Action a) { try { a(); } catch (Exception e) { errors.Add(what + ": " + e.Message); } }

        Try("service", () => Sc("sdset LockGuard " + (on ? Locked : Normal)));
        Try("registry", () =>
        {
            using var k = Registry.LocalMachine.OpenSubKey(@"SYSTEM\CurrentControlSet\Services\LockGuard", RegistryKeyPermissionCheck.ReadWriteSubTree, RegistryRights.ChangePermissions | RegistryRights.ReadKey);
            if (k == null) return;
            var sec = new RegistrySecurity();
            sec.SetAccessRuleProtection(true, false);
            sec.AddAccessRule(new RegistryAccessRule(System, RegistryRights.FullControl, InheritanceFlags.ContainerInherit, PropagationFlags.None, AccessControlType.Allow));
            sec.AddAccessRule(new RegistryAccessRule(Admins, on ? RegistryRights.ReadKey : RegistryRights.FullControl, InheritanceFlags.ContainerInherit, PropagationFlags.None, AccessControlType.Allow));
            sec.AddAccessRule(new RegistryAccessRule(Users, RegistryRights.ReadKey, InheritanceFlags.ContainerInherit, PropagationFlags.None, AccessControlType.Allow));
            k.SetAccessControl(sec);
        });
        Try("program files", () => LockFolder(ProgramDir, on, usersRead: true));
        Try("data", () => LockFolder(Paths.RootDir, on, usersRead: false));
        On = on && errors.Count == 0;
        LastError = errors.Count == 0 ? null : "Protection not fully applied: " + string.Join("; ", errors);
    }

    private static void LockFolder(string dir, bool on, bool usersRead)
    {
        if (!Directory.Exists(dir)) return;
        var info = new DirectoryInfo(dir);
        var sec = new DirectorySecurity();
        sec.SetAccessRuleProtection(true, false);
        var inherit = InheritanceFlags.ContainerInherit | InheritanceFlags.ObjectInherit;
        sec.AddAccessRule(new FileSystemAccessRule(System, FileSystemRights.FullControl, inherit, PropagationFlags.None, AccessControlType.Allow));
        if (on) { if (usersRead) sec.AddAccessRule(new FileSystemAccessRule(Admins, FileSystemRights.ReadAndExecute, inherit, PropagationFlags.None, AccessControlType.Allow)); }
        else sec.AddAccessRule(new FileSystemAccessRule(Admins, FileSystemRights.FullControl, inherit, PropagationFlags.None, AccessControlType.Allow));
        if (usersRead) sec.AddAccessRule(new FileSystemAccessRule(Users, FileSystemRights.ReadAndExecute, inherit, PropagationFlags.None, AccessControlType.Allow));
        sec.SetOwner(System);
        info.SetAccessControl(sec);
    }

    private static void Sc(string args)
    {
        using var p = Process.Start(new ProcessStartInfo("sc.exe", args) { CreateNoWindow = true, UseShellExecute = false, RedirectStandardOutput = true, RedirectStandardError = true })!;
        var output = p.StandardOutput.ReadToEnd();
        p.WaitForExit(15000);
        if (p.ExitCode != 0) throw new InvalidOperationException(output.Trim());
    }
}
