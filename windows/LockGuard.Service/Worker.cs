using System.Diagnostics;
using LockGuard.Common;

namespace LockGuard.Service;

/// <summary>
/// The service's whole lifecycle: bootstrap the default code on first run, keep the firewall
/// matching config.json, and reapply it on a timer so someone deleting a rule by hand (or a
/// Windows update resetting firewall state) gets corrected within a minute instead of silently
/// leaving a hole open.
/// </summary>
public sealed class Worker : BackgroundService
{
    private static readonly TimeSpan ReapplyInterval = TimeSpan.FromSeconds(30);

    private readonly ILogger<Worker> _logger;
    private readonly CodeStore _codeStore = new();

    public Worker(ILogger<Worker> logger)
    {
        _logger = logger;
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
            }
        }
        catch (OperationCanceledException)
        {
            // normal shutdown
        }

        await pipeTask;
    }

    private void ApplyNow()
    {
        try
        {
            var config = LockConfig.Load();
            if (config.Enabled)
                FirewallManager.ApplyLockdown(config.AllowedPrograms);
            else
                FirewallManager.RemoveLockdown();
        }
        catch (Exception e)
        {
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

        Environment.Exit(0);
    }
}
