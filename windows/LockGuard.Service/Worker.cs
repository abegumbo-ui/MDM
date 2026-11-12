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
    /// <summary>The unlock file is in Downloads: every restriction is off until it is gone.</summary>
    private volatile bool _unlocked;
    private bool _internetOff;
    private static string ScreenRulesFile => Path.Combine(Paths.RootDir, "screentime-rules.json");
    /// <summary>The dashboard's screen time, kept on disk so it works with no internet.</summary>
    private volatile ScreenTimeRules? _screenRules = LoadScreenRules();
    private static ScreenTimeRules? LoadScreenRules()
    {
        try { return File.Exists(ScreenRulesFile) ? System.Text.Json.JsonSerializer.Deserialize<ScreenTimeRules>(File.ReadAllText(ScreenRulesFile)) : null; } catch { return null; }
    }

    /// <summary>What actually applies: the dashboard's filter, or (unlock file present) nothing blocked.</summary>
    private FilterPolicy Effective()
    {
        var f = _filter;
        if (!_unlocked && f.InternetOffUntil > DateTimeOffset.UtcNow.ToUnixTimeMilliseconds())
        {
            // Internet off for a while: nothing but the essentials (the dashboard) is looked up.
            var off = System.Text.Json.JsonSerializer.Deserialize<FilterPolicy>(f.Json()) ?? new FilterPolicy();
            off.Level = "approved";
            off.Allow = new List<string>();
            off.PauseUntil = 0;
            return off;
        }
        if (!_unlocked) return f;
        var open = System.Text.Json.JsonSerializer.Deserialize<FilterPolicy>(f.Json()) ?? new FilterPolicy();
        open.PauseUntil = long.MaxValue / 2;
        open.BlockedPrograms = new List<string>();
        open.NoInternetPrograms = new List<string>();
        open.BlockOtherBrowsers = false;
        open.BlockVpnApps = false;
        return open;
    }
    private bool _filterApplied;
    private DateTime _lastReport = DateTime.UtcNow;
    private DateTime _lastHarden = DateTime.MinValue;
    private DateTime _lastPrograms = DateTime.MinValue;
    private DateTime _lastUnlockCheck = DateTime.MinValue;
    /// <summary>When the dashboard last answered (ms), for the LockGuard window.</summary>
    public static long LastCheckIn;
    private string _appBlocksApplied = "";
    private static string ProtectFile => Path.Combine(Paths.RootDir, "protect.txt");
    /// <summary>On unless the dashboard switched it off.</summary>
    private bool _protect = !File.Exists(ProtectFile) || File.ReadAllText(ProtectFile).Trim() != "off";
    private CancellationToken _stopping;

    public Worker(ILogger<Worker> logger)
    {
        _logger = logger;
        _proxy = new DnsProxy(() => Effective(), _lists, _siteLog);
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
        Hardening.Apply(_protect);
        _lastHarden = DateTime.UtcNow;

        try
        {
            while (!stoppingToken.IsCancellationRequested)
            {
                await SyncDashboardAsync();
                await Task.Delay(ReapplyInterval, stoppingToken);
                ApplyNow();
                await ApplyFilterAsync();
                if (DateTime.UtcNow - _lastHarden > TimeSpan.FromMinutes(10)) { _lastHarden = DateTime.UtcNow; Hardening.Apply(_protect); }
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
                if (DateTime.UtcNow - _lastUnlockCheck > TimeSpan.FromSeconds(5))
                {
                    _lastUnlockCheck = DateTime.UtcNow;
                    // The internet-off time starting or ending: forget earlier answers so it applies at once.
                    var offNow = _filter.InternetOffUntil > DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
                    if (offNow != _internetOff)
                    {
                        _internetOff = offNow;
                        Note(offNow ? "The internet was turned off for a while." : "The internet is back on.");
                        _proxy.ClearCache();
                        NetworkDns.FlushCache();
                    }
                    var now = DashboardExtras.UnlockFilePresent();
                    if (now != _unlocked)
                    {
                        _unlocked = now;
                        Note(now ? "The unlock file was put in Downloads: every restriction is off." : "The unlock file was removed: restrictions are back on.");
                        _proxy.ClearCache();
                        NetworkDns.FlushCache();
                        ApplyNow();
                        await ApplyFilterAsync();
                    }
                }
                foreach (var n in AppBlocker.Sweep(Effective())) Note(n);
                ScreenTimeGuard.Tick(_screenRules, _unlocked, Note);
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
                ApplyAppBlocks(Effective().NoInternetPrograms);
                BrowserPolicies.Apply(p);
                _filterApplied = true;
                await _lists.EnsureAsync(p.Base, p.Level, p.Ads);
            }
            else if (_filterApplied || File.Exists(Path.Combine(Paths.RootDir, "dns-original.json")))
            {
                NetworkDns.Restore();
                FirewallManager.SetFilterRules(false);
                ApplyAppBlocks(new List<string>());
                BrowserPolicies.Remove();
                _filterApplied = false;
            }
        }
        catch (Exception e)
        {
            _logger.LogError(e, "Could not apply the web filter.");
        }
    }

    /// <summary>"No internet" programs: changed only when the list changes.</summary>
    private void ApplyAppBlocks(List<string> paths)
    {
        var key = string.Join("|", paths.OrderBy(x => x, StringComparer.OrdinalIgnoreCase));
        if (key == _appBlocksApplied) return;
        if (WfpBlocker.SetAppBlocks(paths)) _appBlocksApplied = key;
    }

    private object Info() => new
    {
        hostname = Environment.MachineName,
        tz = (int)TimeZoneInfo.Local.GetUtcOffset(DateTime.Now).TotalMinutes,
        version = Assembly.GetExecutingAssembly().GetName().Version?.ToString(),
        build = Updater.Build,
        protectedFromAdmins = Hardening.On,
        unlocked = _unlocked,
        protectError = Hardening.LastError,
        accounts = Accounts.Report(),
        screenTime = ScreenTimeGuard.Report(),
        screenTimeError = ScreenTimeGuard.LastError,
        filter = new
        {
            on = _filter.On,
            running = _proxy.Running,
            listsReady = _lists.Ready(_filter.Level) || _filter.Level == "approved",
            error = _proxy.LastError ?? _lists.LastError ?? BrowserPolicies.LastError ?? (_filter.On ? WfpBlocker.LastError : null),
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
            // The installed programs go up every 30 minutes (and on the first check-in), for the dashboard's list.
            List<object>? programs = null;
            if (DateTime.UtcNow - _lastPrograms > TimeSpan.FromMinutes(30))
            {
                try { programs = ProgramList.Find().Select(p => (object)new { name = p.Name, path = p.Path }).ToList(); } catch { }
            }
            var asked = SiteRequests.Take();
            var result = await _dashboard.SyncAsync(config.Enabled, config.AllowedPrograms, Info(), log, events, programs, asked);
            if (result is null) SiteRequests.Restore(asked);
            if (programs != null && result != null) _lastPrograms = DateTime.UtcNow;
            if (result is null) return;
            if (report) _lastReport = DateTime.UtcNow;
            LastCheckIn = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();

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

            if (result.Protect is bool protect && protect != _protect)
            {
                _protect = protect;
                File.WriteAllText(ProtectFile, protect ? "on" : "off");
                Hardening.Apply(protect);
                _lastHarden = DateTime.UtcNow;
            }
            DashboardExtras.Save(result.RemovalCode, result.Unlock);
            var rulesJson = result.ScreenTime is null ? "" : System.Text.Json.JsonSerializer.Serialize(result.ScreenTime);
            var haveJson = _screenRules is null ? "" : System.Text.Json.JsonSerializer.Serialize(_screenRules);
            if (rulesJson != haveJson)
            {
                _screenRules = result.ScreenTime;
                try { if (result.ScreenTime is null) File.Delete(ScreenRulesFile); else File.WriteAllText(ScreenRulesFile, rulesJson); } catch { }
            }
            if (result.Commands.Any(c => c.Type == "uninstall")) { BeginUninstall(); return; }
            if (result.Commands.Any(c => c.Type == "screenshot"))
            {
                var why = ScreenshotTaker.Start();
                if (why != null) Note("Screenshot not taken: " + why);
                else
                {
                    var dash = _dashboard;
                    _ = Task.Run(async () =>
                    {
                        var pic = await ScreenshotTaker.WaitAsync(_stopping);
                        if (pic is null) Note("Screenshot not taken: the screen could not be read (maybe it is locked).");
                        else if (!await dash.SendScreenshotAsync(pic)) Note("Screenshot taken but it could not be sent.");
                    });
                }
            }
            await Updater.MaybeUpdateAsync(result.Update, Note);
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
            var ok = config.Enabled && !_unlocked
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
            Hardening.Apply(false); // normal rights again, so everything below (and the script) can remove it
            FirewallManager.RemoveLockdown();
            FirewallManager.SetFilterRules(false);
            WfpBlocker.SetAppBlocks(new List<string>());
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
Remove-Item -Path 'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\LockGuard' -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force '{Paths.RootDir}' -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force (Join-Path $env:ProgramFiles 'LockGuard') -ErrorAction SilentlyContinue
Get-ChildItem ([Environment]::GetFolderPath('CommonDesktopDirectory')) -Filter 'LockGuard Setup*.lnk' -ErrorAction SilentlyContinue | Remove-Item -Force -ErrorAction SilentlyContinue
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
