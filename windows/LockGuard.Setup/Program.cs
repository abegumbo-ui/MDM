namespace LockGuard.Setup;

internal static class Program
{
    [STAThread]
    private static void Main(string[] args)
    {
        // "/screenshot ticket": started by the service for the dashboard's "See the screen" (see Screenshot.cs).
        if (args.Length >= 2 && args[0].Equals("/screenshot", StringComparison.OrdinalIgnoreCase))
        {
            Application.SetHighDpiMode(HighDpiMode.PerMonitorV2);
            Screenshot.Run(args[1]);
            return;
        }
        Application.SetHighDpiMode(HighDpiMode.SystemAware);
        Application.EnableVisualStyles();
        Application.SetCompatibleTextRenderingDefault(false);
        // "/uninstall": opened from Windows' Installed apps → Uninstall; goes straight to the removal code.
        bool uninstall = args.Any(a => a.Equals("/uninstall", StringComparison.OrdinalIgnoreCase));
        SetupForm.Preview = args.Any(a => a.Equals("/preview", StringComparison.OrdinalIgnoreCase));
        Application.Run(new SetupForm(uninstall));
    }
}
