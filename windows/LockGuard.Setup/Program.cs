namespace LockGuard.Setup;

internal static class Program
{
    [STAThread]
    private static void Main(string[] args)
    {
        Application.SetHighDpiMode(HighDpiMode.SystemAware);
        Application.EnableVisualStyles();
        Application.SetCompatibleTextRenderingDefault(false);
        // "/uninstall": opened from Windows' Installed apps → Uninstall; goes straight to the removal code.
        bool uninstall = args.Any(a => a.Equals("/uninstall", StringComparison.OrdinalIgnoreCase));
        SetupForm.Preview = args.Any(a => a.Equals("/preview", StringComparison.OrdinalIgnoreCase));
        Application.Run(new SetupForm(uninstall));
    }
}
