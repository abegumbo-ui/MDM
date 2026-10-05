using System.Diagnostics;
using System.IO.Compression;
using System.Reflection;
using System.Windows.Forms;

namespace LockGuard.Installer;

// The single-exe installer: double-click, approve the UAC prompt (the app manifest asks for
// elevation directly -- no manual relaunch needed), and this does everything install.ps1 does,
// then opens Setup to finish configuration. See windows/README.md for the manual, zip-based
// path this replaces for most people.
internal static class Program
{
    private static readonly string InstallDir = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "LockGuard");
    private static readonly string ServiceDir = Path.Combine(InstallDir, "Service");
    private static readonly string SetupDir = Path.Combine(InstallDir, "Setup");
    private static readonly string DataDir = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.CommonApplicationData), "LockGuard");

    [STAThread]
    private static int Main()
    {
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
