using System.Runtime.InteropServices;
using System.Security.Principal;
using System.Text.Json;
using LockGuard.Common;

namespace LockGuard.Service;

/// <summary>
/// Screen time on the computer (see ScreenTimeRules). Counts the time the person at the screen is
/// signed in and the screen is unlocked, for standard (non-administrator) accounts only. When the
/// time is up or bedtime starts, a message says so and, a minute later, the computer goes back to the
/// Windows sign-in screen. Nothing is closed: the child's programs stay open for tomorrow.
/// Administrator accounts are never counted or signed out. Anything it cannot work out (no one at
/// the screen, the account type unknown, an error) means: do nothing.
/// </summary>
public static class ScreenTimeGuard
{
    private static string File => Path.Combine(Paths.RootDir, "screentime.json");
    private sealed class Usage { public string Day { get; set; } = ""; public long Seconds { get; set; } public string Warned { get; set; } = ""; }

    private static DateTime _lastTick = DateTime.MinValue, _lastSave = DateTime.MinValue, _graceUntil = DateTime.MinValue, _lastSignOut = DateTime.MinValue;
    private static bool _wasActive;
    private static Usage? _usage;
    public static string? LastError;

    /// <summary>For the dashboard: today's minutes and what applies now.</summary>
    public static object Report()
    {
        var u = Load();
        return new { day = u.Day, minutes = (int)(u.Seconds / 60), state = _state.ToString() };
    }
    private static ScreenTimeRules.State _state = ScreenTimeRules.State.Allowed;

    /// <summary>Called every couple of seconds by the service.</summary>
    public static void Tick(ScreenTimeRules? rules, bool unlocked, Action<string> note)
    {
        var now = DateTime.Now;
        var elapsed = _lastTick == DateTime.MinValue ? 0 : Math.Min(10, (now - _lastTick).TotalSeconds);
        _lastTick = now;
        try
        {
            var u = Load();
            var today = now.ToString("yyyy-MM-dd");
            if (u.Day != today) { u.Day = today; u.Seconds = 0; u.Warned = ""; }

            var session = ActiveLimitedSession();
            if (session < 0) { _wasActive = false; _state = ScreenTimeRules.State.Allowed; Save(u, false); return; }
            u.Seconds += (long)Math.Round(elapsed);

            if (rules is null || !rules.Any || unlocked) { _state = ScreenTimeRules.State.Allowed; _wasActive = true; Save(u, false); return; }
            var state = rules.Decide(now, u.Seconds);
            _state = state;
            if (state == ScreenTimeRules.State.WarnSoon)
            {
                var key = today + (rules.InBedtime((now.Hour * 60 + now.Minute + 5) % 1440) ? ":bed" : ":limit");
                if (u.Warned != key)
                {
                    u.Warned = key;
                    Message(session, "Screen time", key.EndsWith(":bed")
                        ? "Bedtime starts in 5 minutes. Save your work."
                        : "5 minutes of screen time left today. Save your work.");
                }
            }
            else if (state == ScreenTimeRules.State.Over || state == ScreenTimeRules.State.Bedtime)
            {
                if (now - _lastSignOut < TimeSpan.FromSeconds(10)) { /* just sent to the sign-in screen */ }
                else if (!_wasActive || _graceUntil == DateTime.MinValue)
                {
                    // Just signed in (or the time just ran out): say so, then a minute to save work.
                    _graceUntil = now.AddSeconds(_wasActive ? 60 : 15);
                    Message(session, "Screen time", state == ScreenTimeRules.State.Bedtime
                        ? "It is bedtime. The computer goes back to the sign-in screen in a moment. Nothing is closed."
                        : "Screen time is up for today. The computer goes back to the sign-in screen in a moment. Nothing is closed.");
                }
                else if (now >= _graceUntil && now - _lastSignOut > TimeSpan.FromSeconds(10))
                {
                    _lastSignOut = now;
                    if (WTSDisconnectSession(IntPtr.Zero, session, false))
                        note(state == ScreenTimeRules.State.Bedtime ? "Bedtime: the computer went back to the sign-in screen." : "Screen time was up: the computer went back to the sign-in screen.");
                    _graceUntil = DateTime.MinValue;
                }
            }
            else _graceUntil = DateTime.MinValue;
            _wasActive = true;
            Save(u, false);
            LastError = null;
        }
        catch (Exception e)
        {
            LastError = e.Message;
        }
    }

    /// <summary>The console session when someone is signed in, it is unlocked, and the account is a standard one; else -1.</summary>
    private static int ActiveLimitedSession()
    {
        var id = WTSGetActiveConsoleSessionId();
        if (id == 0xFFFFFFFF || id == 0) return -1;
        var session = (int)id;
        if (!WTSQuerySessionInformation(IntPtr.Zero, session, 25 /* WTSSessionInfoEx */, out var buf, out _)) return -1;
        try
        {
            // WTSINFOEXW: DWORD Level, then (8-byte aligned) SessionId, SessionState, SessionFlags.
            var state = Marshal.ReadInt32(buf, 12);
            var flags = Marshal.ReadInt32(buf, 16);
            if (state != 0 /* WTSActive */ || flags != 1 /* WTS_SESSIONSTATE_UNLOCK */) return -1;
        }
        finally { WTSFreeMemory(buf); }
        return IsAdministrator(session) == false ? session : -1;
    }

    /// <summary>true / false, or null when it cannot be told (then nothing is limited).</summary>
    private static bool? IsAdministrator(int session)
    {
        if (!WTSQueryUserToken(session, out var token)) return null;
        try
        {
            // An administrator with UAC has a "limited" or "full" token; anything else is checked by group.
            if (GetTokenInformation(token, 18 /* TokenElevationType */, out var type, 4, out _) && (type == 2 || type == 3)) return true;
            using var identity = new WindowsIdentity(token);
            return new WindowsPrincipal(identity).IsInRole(WindowsBuiltInRole.Administrator);
        }
        catch { return null; }
        finally { CloseHandle(token); }
    }

    private static void Message(int session, string title, string text)
    {
        // Does not wait for OK; disappears after 60 seconds.
        WTSSendMessage(IntPtr.Zero, session, title, title.Length * 2, text, text.Length * 2, 0x40 /* MB_ICONINFORMATION */, 60, out _, false);
    }

    private static Usage Load()
    {
        if (_usage != null) return _usage;
        try { if (System.IO.File.Exists(File)) _usage = JsonSerializer.Deserialize<Usage>(System.IO.File.ReadAllText(File)); } catch { }
        return _usage ??= new Usage();
    }

    private static void Save(Usage u, bool force)
    {
        _usage = u;
        if (!force && DateTime.UtcNow - _lastSave < TimeSpan.FromSeconds(30)) return;
        _lastSave = DateTime.UtcNow;
        try { Directory.CreateDirectory(Paths.RootDir); System.IO.File.WriteAllText(File, JsonSerializer.Serialize(u)); } catch { }
    }

    [DllImport("kernel32.dll")] private static extern uint WTSGetActiveConsoleSessionId();
    [DllImport("wtsapi32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern bool WTSQuerySessionInformation(IntPtr server, int sessionId, int infoClass, out IntPtr buffer, out int bytes);
    [DllImport("wtsapi32.dll")] private static extern void WTSFreeMemory(IntPtr memory);
    [DllImport("wtsapi32.dll", SetLastError = true)] private static extern bool WTSDisconnectSession(IntPtr server, int sessionId, bool wait);
    [DllImport("wtsapi32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern bool WTSSendMessage(IntPtr server, int sessionId, string title, int titleLength, string message, int messageLength, int style, int timeout, out int response, bool wait);
    [DllImport("wtsapi32.dll", SetLastError = true)] private static extern bool WTSQueryUserToken(int sessionId, out IntPtr token);
    [DllImport("advapi32.dll", SetLastError = true)]
    private static extern bool GetTokenInformation(IntPtr token, int infoClass, out int info, int length, out int returned);
    [DllImport("kernel32.dll")] private static extern bool CloseHandle(IntPtr handle);
}
