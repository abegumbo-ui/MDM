using System.Diagnostics;
using System.IO.Compression;
using System.Reflection;
using System.Security.Principal;
using System.Windows.Forms;

namespace LockGuard.Installer;

// The single-exe installer: double-click, approve the UAC prompt, and this does everything
// install.ps1 does, then opens Setup to finish configuration. See windows/README.md for the
// manual, zip-based path this replaces for most people.
internal static class Program
{
    private static readonly string InstallDir = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "LockGuard");
    private static readonly string ServiceDir = Path.Combine(InstallDir, "Service");
    private static readonly string SetupDir = Path.Combine(InstallDir, "Setup");
    private static readonly string DataDir = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.CommonApplicationData), "LockGuard");
    /// <summary>The guard (guard.ps1) and the install log live apart from LockGuard, so a failed update cannot take them along.</summary>
    private static readonly string GuardDir = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "LockGuard Guard");

    [STAThread]
    private static int Main(string[] args)
    {
        // "/quiet": the service updating itself (it runs as the system, so already elevated).
        // No windows, no messages, and Setup is not opened.
        bool quiet = args.Any(a => a.Equals("/quiet", StringComparison.OrdinalIgnoreCase));
        // LockGuard (or what is left of it) is locked against administrators: only the system may
        // change it, so an administrator's install is handed to the system.
        if (quiet && IsElevated() && !IsSystem() && (ServiceExists() || Directory.Exists(InstallDir)))
            return HandToSystem() ? 0 : 1;
        if (quiet && IsElevated())
        {
            try { Install(); return 0; }
            catch (Exception e) { Log("Failed: " + e); return 1; }
            finally { RunAndWait("schtasks.exe", "/delete /tn LockGuardUpdate /f", allowFailure: true); }
        }
        // The app manifest already asks Windows for requireAdministrator, which should elevate
        // this before Main() ever runs -- but single-file published apps have been seen not to
        // honor that reliably. This is the same fallback a plain "right-click, Run as
        // Administrator" does, just automatic: if we're not elevated yet, relaunch ourselves
        // with the "runas" verb (which always shows the UAC prompt, regardless of manifest
        // quirks) and let the manifest-triggered run, if it happened, just see this as a no-op.
        if (!IsElevated())
        {
            try
            {
                Process.Start(new ProcessStartInfo
                {
                    FileName = Environment.ProcessPath ?? Application.ExecutablePath,
                    UseShellExecute = true,
                    Verb = "runas",
                });
            }
            catch (System.ComponentModel.Win32Exception)
            {
                // The user clicked "No" on the UAC prompt -- nothing to install without it.
                MessageBox.Show("LockGuard needs administrator access to install. Run it again and click \"Yes\" when Windows asks.",
                    "LockGuard", MessageBoxButtons.OK, MessageBoxIcon.Warning);
            }
            return 0;
        }

        // Already installed: LockGuard protects itself from administrators, so the update is handed
        // to the system (a one-off scheduled task running this same file with /quiet).
        if (ServiceExists() || Directory.Exists(InstallDir))
        {
            bool ok = HandToSystem();
            MessageBox.Show(ok ? "LockGuard is installed and running." : "LockGuard could not be installed. The details are in C:\\Program Files\\LockGuard Guard\\install.log.",
                "LockGuard", MessageBoxButtons.OK, ok ? MessageBoxIcon.Information : MessageBoxIcon.Error);
            return ok ? 0 : 1;
        }

        try
        {
            Install();
            MessageBox.Show(
                "LockGuard is installed and running. Setup will now open so you can choose a code and pick allowed programs.",
                "LockGuard", MessageBoxButtons.OK, MessageBoxIcon.Information);
            Process.Start(Path.Combine(SetupDir, "LockGuard.Setup.exe"));
            return 0;
        }
        catch (Exception ex)
        {
            MessageBox.Show($"Install failed: {ex.Message}", "LockGuard",
                MessageBoxButtons.OK, MessageBoxIcon.Error);
            return 1;
        }
    }

    private static bool IsSystem()
    {
        using var identity = WindowsIdentity.GetCurrent();
        return identity.User?.Value == "S-1-5-18";
    }

    /// <summary>
    /// Runs this same file with /quiet as the system (a one-off task), waits for it to finish, and
    /// says whether LockGuard is running afterwards.
    /// </summary>
    private static bool HandToSystem()
    {
        try
        {
            var me = Environment.ProcessPath ?? Application.ExecutablePath;
            RunAndWait("schtasks.exe", $"/create /tn LockGuardUpdate /tr \"\\\"{me}\\\" /quiet\" /sc once /st 00:00 /ru SYSTEM /rl HIGHEST /f");
            RunAndWait("schtasks.exe", "/run /tn LockGuardUpdate");
            Thread.Sleep(3000);
            // Wait for the system's copy of this installer to finish (up to 5 minutes).
            var mine = Environment.ProcessId;
            var name = Path.GetFileNameWithoutExtension(me);
            for (var i = 0; i < 150 && Process.GetProcessesByName(name).Any(p => p.Id != mine); i++) Thread.Sleep(2000);
            RunAndWait("schtasks.exe", "/delete /tn LockGuardUpdate /f", allowFailure: true);
            if (!ServiceExists()) return false;
            using var sc = new System.ServiceProcess.ServiceController("LockGuard");
            return sc.Status == System.ServiceProcess.ServiceControllerStatus.Running;
        }
        catch (Exception e)
        {
            Log("Handing the install to the system failed: " + e.Message);
            return false;
        }
    }

    private static bool IsElevated()
    {
        using var identity = WindowsIdentity.GetCurrent();
        return new WindowsPrincipal(identity).IsInRole(WindowsBuiltInRole.Administrator);
    }

    /// <summary>
    /// Installs, or updates in place. Safe by design: the new version is unpacked next to the old
    /// one first (nothing is touched if that fails); the old one is only stopped once the new one is
    /// ready, and it is put back if the new one does not start. LockGuard is never deleted from
    /// Windows during an update. The guard (see guard.ps1) is installed first, so even if this
    /// process dies halfway the computer gets LockGuard, or its internet, back within 5 minutes.
    /// </summary>
    private static void Install()
    {
        Log("Install or update started (" + (Environment.ProcessPath ?? "?") + ").");
        string newService = ServiceDir + ".new", newSetup = SetupDir + ".new";
        string oldService = ServiceDir + ".old", oldSetup = SetupDir + ".old";
        foreach (var d in new[] { newService, newSetup, oldService, oldSetup }) DeleteDir(d);
        ExtractEmbeddedZip("Service.zip", newService);
        ExtractEmbeddedZip("Setup.zip", newSetup);
        if (!File.Exists(Path.Combine(newService, "LockGuard.Service.exe"))) throw new InvalidOperationException("The new version is incomplete; nothing was changed.");
        Log("New version unpacked.");

        InstallGuard();

        bool existed = ServiceExists();
        bool swapped = false;
        try
        {
            if (existed) StopAndWait();
            if (Directory.Exists(ServiceDir)) Directory.Move(ServiceDir, oldService);
            if (Directory.Exists(SetupDir)) Directory.Move(SetupDir, oldSetup);
            Directory.Move(newService, ServiceDir);
            Directory.Move(newSetup, SetupDir);
            swapped = true;

            var servicePath = Path.Combine(ServiceDir, "LockGuard.Service.exe");
            if (!existed)
            {
                RunAndWait("sc.exe", $"create LockGuard binPath= \"{servicePath}\" start= auto DisplayName= \"LockGuard\"");
                RunAndWait("sc.exe", "description LockGuard \"Blocks internet access for everything except the programs you allow. Managed through LockGuard Setup.\"");
            }
            RunAndWait("sc.exe", "failure LockGuard reset= 0 actions= restart/5000/restart/5000/restart/5000", allowFailure: true);
            if (!StartAndWait(TimeSpan.FromSeconds(90))) throw new InvalidOperationException("The new version did not start.");
            Log("New version running.");
        }
        catch (Exception e)
        {
            Log("Update failed (" + e.Message + "). Putting the previous version back.");
            try
            {
                if (ServiceExists()) StopAndWait();
                if (Directory.Exists(oldService))
                {
                    DeleteDir(ServiceDir);
                    Directory.Move(oldService, ServiceDir);
                }
                if (Directory.Exists(oldSetup))
                {
                    DeleteDir(SetupDir);
                    Directory.Move(oldSetup, SetupDir);
                }
                if (ServiceExists()) Log(StartAndWait(TimeSpan.FromSeconds(90)) ? "Previous version running again." : "Previous version did not start; the guard takes over.");
            }
            catch (Exception e2)
            {
                Log("Could not put the previous version back (" + e2.Message + "); the guard takes over.");
            }
            if (!swapped) { DeleteDir(newService); DeleteDir(newSetup); }
            throw;
        }
        DeleteDir(oldService);
        DeleteDir(oldSetup);

        // First install only: on an update the running service keeps its own (stricter) folder rights.
        if (!existed)
        {
            for (var i = 0; i < 10 && !Directory.Exists(DataDir); i++)
                Thread.Sleep(1000);
            if (Directory.Exists(DataDir))
            {
                RunAndWait("icacls.exe", $"\"{DataDir}\" /inheritance:r", allowFailure: true);
                RunAndWait("icacls.exe", $"\"{DataDir}\" /grant \"SYSTEM:(OI)(CI)F\"", allowFailure: true);
                RunAndWait("icacls.exe", $"\"{DataDir}\" /grant \"*S-1-5-32-544:(OI)(CI)F\"", allowFailure: true);
            }
        }

        try { CreateDesktopShortcut(); } catch (Exception e) { Log("Shortcut: " + e.Message); }
        RegisterUninstall();
        Log("Done.");
    }

    /// <summary>guard.ps1 as two system tasks: every 5 minutes, and 2 minutes after startup.</summary>
    private static void InstallGuard()
    {
        Directory.CreateDirectory(GuardDir);
        var script = Path.Combine(GuardDir, "guard.ps1");
        using (var stream = Assembly.GetExecutingAssembly().GetManifestResourceStream("guard.ps1")
            ?? throw new InvalidOperationException("Missing embedded resource: guard.ps1"))
        using (var file = File.Create(script))
            stream.CopyTo(file);
        var run = $"powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File \\\"{script}\\\"";
        RunAndWait("schtasks.exe", $"/create /tn \"LockGuard Guard\" /tr \"{run}\" /sc minute /mo 5 /ru SYSTEM /rl HIGHEST /f");
        RunAndWait("schtasks.exe", $"/create /tn \"LockGuard Guard at startup\" /tr \"{run}\" /sc onstart /delay 0002:00 /ru SYSTEM /rl HIGHEST /f");
        Log("Guard installed.");
    }

    private static bool ServiceExists() => System.ServiceProcess.ServiceController.GetServices().Any(x => x.ServiceName == "LockGuard");

    /// <summary>Stops the service and waits until its program has really exited (its files are free).</summary>
    private static void StopAndWait()
    {
        using (var sc = new System.ServiceProcess.ServiceController("LockGuard"))
        {
            if (sc.Status != System.ServiceProcess.ServiceControllerStatus.Stopped)
            {
                try { sc.Stop(); } catch (InvalidOperationException) { }
                try { sc.WaitForStatus(System.ServiceProcess.ServiceControllerStatus.Stopped, TimeSpan.FromSeconds(60)); } catch (System.ServiceProcess.TimeoutException) { }
            }
        }
        for (var i = 0; i < 30; i++)
        {
            var left = Process.GetProcessesByName("LockGuard.Service");
            if (left.Length == 0) return;
            if (i == 20) foreach (var p in left) { try { p.Kill(); } catch { } }
            Thread.Sleep(1000);
        }
        throw new InvalidOperationException("LockGuard would not stop.");
    }

    private static bool StartAndWait(TimeSpan timeout)
    {
        using var sc = new System.ServiceProcess.ServiceController("LockGuard");
        try { if (sc.Status != System.ServiceProcess.ServiceControllerStatus.Running) sc.Start(); } catch (InvalidOperationException) { }
        try { sc.WaitForStatus(System.ServiceProcess.ServiceControllerStatus.Running, timeout); } catch (System.ServiceProcess.TimeoutException) { return false; }
        Thread.Sleep(5000); // still running a few seconds later, not crashing on start
        sc.Refresh();
        return sc.Status == System.ServiceProcess.ServiceControllerStatus.Running;
    }

    private static void DeleteDir(string d)
    {
        try { if (Directory.Exists(d)) Directory.Delete(d, true); } catch (Exception e) { Log("Could not delete " + d + ": " + e.Message); }
    }

    private static void Log(string line)
    {
        try
        {
            Directory.CreateDirectory(GuardDir);
            File.AppendAllText(Path.Combine(GuardDir, "install.log"), DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss") + "  " + line + Environment.NewLine);
        }
        catch { }
    }

    /// <summary>
    /// LockGuard in Windows' own "Installed apps" list. Its Uninstall button opens LockGuard
    /// Setup, which only removes LockGuard after the code is entered.
    /// </summary>
    private static void RegisterUninstall()
    {
        using var k = Microsoft.Win32.Registry.LocalMachine.CreateSubKey(@"SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\LockGuard", true);
        var setup = Path.Combine(SetupDir, "LockGuard.Setup.exe");
        k.SetValue("DisplayName", "LockGuard");
        k.SetValue("Publisher", "Family MDM");
        k.SetValue("DisplayIcon", setup);
        k.SetValue("UninstallString", "\"" + setup + "\" /uninstall");
        k.SetValue("NoModify", 1, Microsoft.Win32.RegistryValueKind.DWord);
        k.SetValue("NoRepair", 1, Microsoft.Win32.RegistryValueKind.DWord);
        k.SetValue("Comments", "Removing LockGuard needs a removal code from the dashboard (or the LockGuard code).");
    }

    private static void ExtractEmbeddedZip(string resourceName, string targetDir)
    {
        using var stream = Assembly.GetExecutingAssembly().GetManifestResourceStream(resourceName)
            ?? throw new InvalidOperationException($"Missing embedded resource: {resourceName}");
        using var archive = new ZipArchive(stream, ZipArchiveMode.Read);
        archive.ExtractToDirectory(targetDir, overwriteFiles: true);
    }

    private static void RunAndWait(string fileName, string arguments, bool allowFailure = false)
    {
        using var process = Process.Start(new ProcessStartInfo
        {
            FileName = fileName,
            Arguments = arguments,
            UseShellExecute = false,
            CreateNoWindow = true,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
        }) ?? throw new InvalidOperationException($"Could not start {fileName}");
        process.WaitForExit();
        if (!allowFailure && process.ExitCode != 0)
        {
            var error = process.StandardError.ReadToEnd();
            throw new InvalidOperationException($"{fileName} {arguments} failed ({process.ExitCode}): {error}");
        }
    }

    private static void CreateDesktopShortcut()
    {
        var publicDesktop = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.CommonDesktopDirectory));
        var shellType = Type.GetTypeFromProgID("WScript.Shell")
            ?? throw new InvalidOperationException("WScript.Shell is unavailable");
        dynamic shell = Activator.CreateInstance(shellType)!;
        dynamic shortcut = shell.CreateShortcut(Path.Combine(publicDesktop, "LockGuard Setup.lnk"));
        shortcut.TargetPath = Path.Combine(SetupDir, "LockGuard.Setup.exe");
        shortcut.Save();
    }
}
