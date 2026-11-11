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

    [STAThread]
    private static int Main(string[] args)
    {
        // "/quiet": the service updating itself (it runs as the system, so already elevated).
        // No windows, no messages, and Setup is not opened.
        bool quiet = args.Any(a => a.Equals("/quiet", StringComparison.OrdinalIgnoreCase));
        if (quiet && IsElevated())
        {
            try { Install(); return 0; }
            catch { return 1; }
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
        if (System.ServiceProcess.ServiceController.GetServices().Any(s => s.ServiceName == "LockGuard"))
        {
            try
            {
                var me = Environment.ProcessPath ?? Application.ExecutablePath;
                RunAndWait("schtasks.exe", $"/create /tn LockGuardUpdate /tr \"\\\"{me}\\\" /quiet\" /sc once /st 00:00 /ru SYSTEM /rl HIGHEST /f");
                RunAndWait("schtasks.exe", "/run /tn LockGuardUpdate");
                Thread.Sleep(5000);
                for (var i = 0; i < 60; i++)
                {
                    using var sc = new System.ServiceProcess.ServiceController("LockGuard");
                    if (sc.Status == System.ServiceProcess.ServiceControllerStatus.Running) break;
                    Thread.Sleep(2000);
                }
                RunAndWait("schtasks.exe", "/delete /tn LockGuardUpdate /f", allowFailure: true);
                MessageBox.Show("LockGuard is updated and running.", "LockGuard", MessageBoxButtons.OK, MessageBoxIcon.Information);
                return 0;
            }
            catch (Exception ex)
            {
                MessageBox.Show($"Update failed: {ex.Message}", "LockGuard", MessageBoxButtons.OK, MessageBoxIcon.Error);
                return 1;
            }
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

    private static bool IsElevated()
    {
        using var identity = WindowsIdentity.GetCurrent();
        return new WindowsPrincipal(identity).IsInRole(WindowsBuiltInRole.Administrator);
    }

    private static void Install()
    {
        RunAndWait("sc.exe", "stop LockGuard", allowFailure: true);
        RunAndWait("sc.exe", "delete LockGuard", allowFailure: true);

        Directory.CreateDirectory(ServiceDir);
        Directory.CreateDirectory(SetupDir);
        ExtractEmbeddedZip("Service.zip", ServiceDir);
        ExtractEmbeddedZip("Setup.zip", SetupDir);

        var servicePath = Path.Combine(ServiceDir, "LockGuard.Service.exe");
        RunAndWait("sc.exe",
            $"create LockGuard binPath= \"{servicePath}\" start= auto DisplayName= \"LockGuard\"");
        RunAndWait("sc.exe",
            "description LockGuard \"Blocks internet access for everything except the programs you allow. Managed through LockGuard Setup.\"");
        RunAndWait("sc.exe",
            "failure LockGuard reset= 0 actions= restart/5000/restart/5000/restart/5000");
        RunAndWait("sc.exe", "start LockGuard");

        // The service creates the data folder itself on first start; wait for it so the
        // lockdown below has something to lock.
        for (var i = 0; i < 10 && !Directory.Exists(DataDir); i++)
            Thread.Sleep(1000);
        if (Directory.Exists(DataDir))
        {
            RunAndWait("icacls.exe", $"\"{DataDir}\" /inheritance:r");
            RunAndWait("icacls.exe", $"\"{DataDir}\" /grant \"SYSTEM:(OI)(CI)F\"");
            RunAndWait("icacls.exe", $"\"{DataDir}\" /grant \"*S-1-5-32-544:(OI)(CI)F\"");
        }

        CreateDesktopShortcut();
        RegisterUninstall();
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
        k.SetValue("UninstallString", "\"" + setup + "\"");
        k.SetValue("NoModify", 1, Microsoft.Win32.RegistryValueKind.DWord);
        k.SetValue("NoRepair", 1, Microsoft.Win32.RegistryValueKind.DWord);
        k.SetValue("Comments", "Removing LockGuard needs its code: Setup asks for it.");
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
