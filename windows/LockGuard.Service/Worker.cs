using System.Diagnostics;
using System.Reflection;
using LockGuard.Common;

namespace LockGuard.Service;

/// <summary>
/// The service's whole lifecycle: bootstrap the default code on first run, keep the firewall
/// matching config.json, reapply it on a timer so someone deleting a rule by hand (or a
/// Windows update resetting firewall state) gets corrected within a minute instead of silently
/// leaving a hole open, and -- if this build has a dashboard URL baked in -- check in with it on
/// the same timer so it can be controlled remotely, the same way the Android agent is.
/// </summary>
public sealed class Worker : BackgroundService
{
    private static readonly TimeSpan ReapplyInterval = TimeSpan.FromSeconds(30);

    private readonly ILogger<Worker> _logger;
    private readonly CodeStore _codeStore = new();
    private readonly DashboardClient? _dashboard = CreateDashboardClient();

    public Worker(ILogger<Worker> logger)
    {
        _logger = logger;
    }

    /// <summary>
    /// windows.yml writes the dashboard's own https:// address into DashboardUrl.txt and embeds
    /// it as a resource right before this project builds (mirroring how DASHBOARD_URL is baked
    /// into the Android agent at build time). No URL embedded -- e.g. a local, non-CI build --
    /// means dashboard integration is simply off; everything else still works purely locally.
    /// </summary>
    private static DashboardClient? CreateDashboardClient()
    {
        try
        {
            using var stream = Assembly.GetExecutingAssembly().GetManifestResourceStream("DashboardUrl.txt");
            if (stream is null) return null;
            using var reader = new StreamReader(stream);
            var url = reader.ReadToEnd().Trim();
            return string.IsNullOrEmpty(url) ? null : new DashboardClient(url);
        }
        catch
        {
            return null;
        }
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        Directory.CreateDirectory(Paths.RootDir);
        _codeStore.InitializeWithDefault();

        var pipeServer = new PipeServer(onConfigChanged: ApplyNow, onUninstallRequested: BeginUninstall);
        var pipeTask = pipeServer.RunAsync(stoppingToken);

        ApplyNow();

        try
        {
            while (!stoppingToken.IsCancellationRequested)
            {
                await Task.Delay(ReapplyInterval, stoppingToken);
                ApplyNow();
                await SyncDashboardAsync();
            }
        }
        catch (OperationCanceledException)
        {
            // normal shutdown
        }

        await pipeTask;
    }

    /// <summary>
    /// The dashboard is treated as authoritative, same as the Android agent's policy from the
    /// server: whatever it says to apply replaces the local config, overriding any change made
    /// directly through Setup. If it can't be reached (no internet, dashboard down), this is a
    /// no-op and the local config keeps working exactly as it would with no dashboard at all.
    /// </summary>
    private async Task SyncDashboardAsync()
    {
        if (_dashboard is null) return;
        try
        {
            var config = LockConfig.Load();
            var result = await _dashboard.SyncAsync(config.Enabled, config.AllowedPrograms, Environment.MachineName);
            if (result is null) return;

            if (result.Enabled != config.Enabled || !result.AllowedPrograms.SequenceEqual(config.AllowedPrograms))
            {
                config.Enabled = result.Enabled;
                config.AllowedPrograms = result.AllowedPrograms;
                config.Save();
                ApplyNow();
            }

            if (result.Commands.Any(c => c.Type == "uninstall")) BeginUninstall();
        }
        catch (Exception e)
        {
            _logger.LogWarning(e, "Could not check in with the dashboard.");
        }
    }

    private void ApplyNow()
    {
        try
        {
            var config = LockConfig.Load();
            var ok = config.Enabled
                ? FirewallManager.ApplyLockdown(config.AllowedPrograms)
                : FirewallManager.RemoveLockdown();
            new FirewallStatus { Ok = ok, Error = ok ? null : FirewallManager.LastError }.Save();
            if (!ok) _logger.LogError("Could not apply the firewall lockdown: {Error}", FirewallManager.LastError);
        }
        catch (Exception e)
        {
            new FirewallStatus { Ok = false, Error = e.Message }.Save();
            _logger.LogError(e, "Could not apply the firewall lockdown.");
        }
    }

    /// <summary>
    /// Removing the lockdown and the service itself has to happen from OUTSIDE this process --
    /// a Windows Service can't delete its own registration or binary file while it's the one
    /// running. This hands off to a detached PowerShell script that waits for this process to
    /// actually stop, then finishes the job, and exits this process right after to trigger that.
    /// </summary>
    private void BeginUninstall()
    {
        try
        {
            FirewallManager.RemoveLockdown();
        }
        catch (Exception e)
        {
            _logger.LogError(e, "Could not remove the firewall lockdown before uninstalling.");
        }

        var scriptPath = Path.Combine(Path.GetTempPath(), "lockguard-uninstall.ps1");
        File.WriteAllText(scriptPath, $@"
Start-Sleep -Seconds 2
Stop-Service -Name LockGuard -Force -ErrorAction SilentlyContinue
sc.exe delete LockGuard | Out-Null
Remove-Item -Recurse -Force '{Paths.RootDir}' -ErrorAction SilentlyContinue
Remove-Item -Force '{scriptPath}' -ErrorAction SilentlyContinue
");
        Process.Start(new ProcessStartInfo
        {
            FileName = "powershell.exe",
            Arguments = $"-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File \"{scriptPath}\"",
            UseShellExecute = false,
            CreateNoWindow = true,
        });

        // This method is called from inside PipeServer.Handle(), which still needs to write its
        // "Removing..." response back to Setup before the pipe goes away -- exiting immediately
        // here (as this used to) kills the process mid-handler, so Setup always saw a timeout
        // instead of that confirmation, even though the uninstall itself had already kicked off.
        // A short delay on a separate thread lets that response actually reach the pipe first.
        new Thread(() =>
        {
            Thread.Sleep(500);
            Environment.Exit(0);
        }) { IsBackground = true }.Start();
    }
}
