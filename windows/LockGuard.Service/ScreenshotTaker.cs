using System.Runtime.InteropServices;
using LockGuard.Common;

namespace LockGuard.Service;

/// <summary>
/// "See the computer's screen", asked for from the dashboard. The service runs out of sight of the
/// screen, so it starts LockGuard Setup in the signed-in person's session with "/screenshot ticket";
/// Setup takes one picture, hands it back over the pipe with that one-time ticket, and then tells the
/// child "A picture of this screen was sent to your parent". A picture without a valid ticket (one
/// the service did not ask for in the last minute) is refused.
/// </summary>
public static class ScreenshotTaker
{
    private static readonly object Gate = new();
    private static string? _ticket;
    private static DateTime _ticketUntil;
    private static string? _picture;

    /// <summary>Starts taking a picture; null when started, else why not.</summary>
    public static string? Start()
    {
        var id = WTSGetActiveConsoleSessionId();
        if (id == 0xFFFFFFFF || id == 0) return "Nobody is signed in on the computer.";
        var setup = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "LockGuard", "Setup", "LockGuard.Setup.exe");
        if (!File.Exists(setup)) return "LockGuard Setup is missing on the computer.";
        var ticket = Convert.ToHexString(System.Security.Cryptography.RandomNumberGenerator.GetBytes(16));
        lock (Gate) { _ticket = ticket; _ticketUntil = DateTime.UtcNow.AddMinutes(1); _picture = null; }
        if (!WTSQueryUserToken((int)id, out var token)) return "Nobody is signed in on the computer.";
        IntPtr env = IntPtr.Zero;
        try
        {
            CreateEnvironmentBlock(out env, token, false);
            var si = new STARTUPINFO { cb = Marshal.SizeOf<STARTUPINFO>(), lpDesktop = "winsta0\\default" };
            var cmd = $"\"{setup}\" /screenshot {ticket}";
            if (!CreateProcessAsUser(token, setup, cmd, IntPtr.Zero, IntPtr.Zero, false, 0x00000400 /* CREATE_UNICODE_ENVIRONMENT */, env, Path.GetDirectoryName(setup)!, ref si, out var pi))
                return "Could not start the picture taker (error " + Marshal.GetLastWin32Error() + ").";
            CloseHandle(pi.hProcess);
            CloseHandle(pi.hThread);
            return null;
        }
        finally
        {
            if (env != IntPtr.Zero) DestroyEnvironmentBlock(env);
            CloseHandle(token);
        }
    }

    /// <summary>From the pipe: the picture, with the ticket it was asked for with.</summary>
    public static bool Accept(string ticket, string? jpegBase64)
    {
        lock (Gate)
        {
            if (_ticket is null || DateTime.UtcNow > _ticketUntil || ticket != _ticket) return false;
            if (string.IsNullOrEmpty(jpegBase64) || jpegBase64.Length > 3_000_000) return false;
            _ticket = null;
            _picture = jpegBase64;
            return true;
        }
    }

    /// <summary>Waits up to a minute for the picture; null if none came.</summary>
    public static async Task<string?> WaitAsync(CancellationToken ct)
    {
        for (var i = 0; i < 60; i++)
        {
            lock (Gate) { if (_picture != null) { var p = _picture; _picture = null; return p; } }
            await Task.Delay(1000, ct);
        }
        return null;
    }

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    private struct STARTUPINFO
    {
        public int cb; public string? lpReserved; public string? lpDesktop; public string? lpTitle;
        public int dwX, dwY, dwXSize, dwYSize, dwXCountChars, dwYCountChars, dwFillAttribute, dwFlags;
        public short wShowWindow, cbReserved2; public IntPtr lpReserved2, hStdInput, hStdOutput, hStdError;
    }
    [StructLayout(LayoutKind.Sequential)]
    private struct PROCESS_INFORMATION { public IntPtr hProcess, hThread; public int dwProcessId, dwThreadId; }

    [DllImport("kernel32.dll")] private static extern uint WTSGetActiveConsoleSessionId();
    [DllImport("wtsapi32.dll", SetLastError = true)] private static extern bool WTSQueryUserToken(int sessionId, out IntPtr token);
    [DllImport("userenv.dll", SetLastError = true)] private static extern bool CreateEnvironmentBlock(out IntPtr env, IntPtr token, bool inherit);
    [DllImport("userenv.dll", SetLastError = true)] private static extern bool DestroyEnvironmentBlock(IntPtr env);
    [DllImport("advapi32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern bool CreateProcessAsUser(IntPtr token, string app, string cmd, IntPtr pa, IntPtr ta, bool inherit, uint flags, IntPtr env, string dir, ref STARTUPINFO si, out PROCESS_INFORMATION pi);
    [DllImport("kernel32.dll")] private static extern bool CloseHandle(IntPtr handle);
}
