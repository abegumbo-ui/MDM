using System.Diagnostics;
using System.Reflection;
using LockGuard.Common;

namespace LockGuard.Service;

/// <summary>
/// The service's whole lifecycle:
/// - "internet only for these programs" (the original lockdown): keep the firewall matching config.json;
/// - the web filter: run the lookup filter (DnsProxy), point the network at it (NetworkDns), close
///   other lookup routes (firewall), lock browser settings (BrowserPolicies) and close blocked
///   programs (AppBlocker);
/// - check in with the dashboard, which is authoritative for both.
/// Everything is re-applied every 30 seconds, so a setting changed by hand (or by Windows) is put
/// back within a minute instead of silently leaving a hole open.
/// </summary>
public sealed class Worker : BackgroundService
{
    private static readonly TimeSpan ReapplyInterval = TimeSpan.FromSeconds(30);
    private static readonly TimeSpan ReportInterval = TimeSpan.FromMinutes(5);

    private readonly ILogger<Worker> _logger;
    private readonly CodeStore _codeStore = new();
    private readonly DashboardClient? _dashboard = CreateDashboardClient();
    private readonly CategoryLists _lists = new();
    private readonly SiteLog _siteLog = new();
    private readonly List<object> _events = new();
    private readonly DnsProxy _proxy;
    private volatile FilterPolicy _filter = FilterPolicy.Load();
    private bool _filterApplied;
    private DateTime _lastReport = DateTime.UtcNow;
    private CancellationToken _stopping;

    public Worker(ILogger<Worker> logger)
    {
        _logger = logger;
        _proxy = new DnsProxy(() => _filter, _lists, _siteLog);
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

    private void Note(string text)
    {
        lock (_events) _events.Add(new { k = "security", m = text, at = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() });
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        _stopping = stoppingToken;
        Directory.CreateDirectory(Paths.RootDir);
        _codeStore.InitializeWithDefault();

        var pipeServer = new PipeServer(onConfigChanged: ApplyNow, onUninstallRequested: BeginUninstall);
        var pipeTask = pipeServer.RunAsync(stoppingToken);

        // The filter starts first: after a restart the network may already point at it.
        if (_filter.On) _proxy.Start(stoppingToken);
        ApplyNow();
        await ApplyFilterAsync();
        _ = BlockerLoop(stoppingToken);

        try
        {
            while (!stoppingToken.IsCancellationRequested)
            {
                await SyncDashboardAsync();
                await Task.Delay(ReapplyInterval, stoppingToken);
                ApplyNow();
                await ApplyFilterAsync();
            }
        }
        catch (OperationCanceledException)
        {
            // normal shutdown
        }

        await pipeTask;
    }

    private async Task BlockerLoop(CancellationToken ct)
    {
        while (!ct.IsCancellationRequested)
        {
            try
            {
                foreach (var n in AppBlocker.Sweep(_filter)) Note(n);
                await Task.Delay(2000, ct);
            }
            catch (OperationCanceledException) { return; }
            catch (Exception e) { _logger.LogWarning(e, "Program check failed."); }
        }
    }

    /// <summary>Turns the web filter on or off to match the current settings.</summary>
    private async Task ApplyFilterAsync()
    {
        var p = _filter;
        try
        {
            if (p.On)
            {
                _proxy.Start(_stopping);
                if (!_proxy.Running) return; // could not start; leave the network alone (see status)
                if (NetworkDns.Apply() && _filterApplied) Note("The computer's network settings were changed; the filter put them back.");
                FirewallManager.SetFilterRules(true);
                BrowserPolicies.Apply(p);
                _filterApplied = true;
                await _lists.EnsureAsync(p.Base, p.Level);
            }
            else if (_filterApplied || File.Exists(Path.Combine(Paths.RootDir, "dns-original.json")))
            {
                NetworkDns.Restore();
                FirewallManager.SetFilterRules(false);
                BrowserPolicies.Remove();
                _filterApplied = false;
            }
        }
        catch (Exception e)
        {
            _logger.LogError(e, "Could not apply the web filter.");
        }
    }

    private object Info() => new
    {
        hostname = Environment.MachineName,
        tz = (int)TimeZoneInfo.Local.GetUtcOffset(DateTime.Now).TotalMinutes,
        version = Assembly.GetExecutingAssembly().GetName().Version?.ToString(),
        filter = new
        {
            on = _filter.On,
            running = _proxy.Running,
            listsReady = _lists.Ready(_filter.Level) || _filter.Level == "approved",
            error = _proxy.LastError ?? _lists.LastError ?? BrowserPolicies.LastError,
        },
    };

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
            List<object>? log = null, events = null;
            bool report = DateTime.UtcNow - _lastReport > ReportInterval; // sites go up every 5 minutes, not every check-in
            if (report)
            {
                log = _siteLog.Drain();
                lock (_events) { events = new List<object>(_events); _events.Clear(); }
            }
            var result = await _dashboard.SyncAsync(config.Enabled, config.AllowedPrograms, Info(), log, events);
            if (result is null) return;
            if (report) _lastReport = DateTime.UtcNow;

            if (result.Enabled != config.Enabled || !result.AllowedPrograms.SequenceEqual(config.AllowedPrograms))
            {
                config.Enabled = result.Enabled;
                config.AllowedPrograms = result.AllowedPrograms;
                config.Save();
                ApplyNow();
            }

            var next = result.Filter ?? new FilterPolicy { On = false };
            if (next.Json() != _filter.Json())
            {
                next.Save();
                _filter = next;
                _proxy.ClearCache();
                NetworkDns.FlushCache();
                await ApplyFilterAsync();
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
    /// running. Everything this app changed (firewall, network lookups, browser policies) is put
    /// back first, then a detached PowerShell script finishes the job.
    /// </summary>
    private void BeginUninstall()
    {
        try
        {
            FirewallManager.RemoveLockdown();
            FirewallManager.SetFilterRules(false);
            NetworkDns.Restore();
            BrowserPolicies.Remove();
        }
        catch (Exception e)
        {
            _logger.LogError(e, "Could not undo everything before uninstalling.");
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
        // "Removing..." response back to Setup before the pipe goes away -- a short delay on a
        // separate thread lets that response actually reach the pipe first.
        new Thread(() =>
        {
            Thread.Sleep(500);
            Environment.Exit(0);
        }) { IsBackground = true }.Start();
    }
}
