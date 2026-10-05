namespace LockGuard.Common;

/// <summary>
/// Where this app's files live. Everything is under ProgramData, not per-user, so it applies to
/// whoever is logged in and survives a user profile being deleted. install.ps1 locks this folder
/// down (SYSTEM + Administrators only) so a standard account can't just edit the files directly
/// to defeat the lock -- the whole point is that changes go through the code-gated pipe instead.
/// </summary>
public static class Paths
{
    public static readonly string RootDir =
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.CommonApplicationData), "LockGuard");

    public static readonly string ConfigFile = Path.Combine(RootDir, "config.json");
    public static readonly string LogFile = Path.Combine(RootDir, "lockguard.log");

    /// <summary>Name of the named pipe the Setup app uses to talk to the running service.</summary>
    public const string PipeName = "LockGuardControlPipe";
}
