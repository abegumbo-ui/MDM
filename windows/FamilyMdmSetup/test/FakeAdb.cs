// A pretend "adb" for testing FamilyMdmSetup.exe without a phone. It behaves like a phone that has a Google and a Samsung account:
// "dpm set-device-owner" only succeeds when the two account services were switched off first (like Android), and everything it
// is asked is written to fake-adb.log next to it. Settings come from environment variables:
//   FAKE_SDK (default 35), FAKE_OWNER (a package that is already the device owner), FAKE_FAIL=1 (set-device-owner always fails),
//   FAKE_USERS (number of users, default 1), FAKE_STATE (the file that remembers which packages are switched off).
using System;
using System.Collections.Generic;
using System.IO;

public static class FakeAdb
{
    public static int Main(string[] args)
    {
        string dir = AppDomain.CurrentDomain.BaseDirectory;
        string state = Environment.GetEnvironmentVariable("FAKE_STATE") ?? Path.Combine(dir, "fake-state.txt");
        File.AppendAllText(Path.Combine(dir, "fake-adb.log"), string.Join(" ", args) + "\r\n");
        var list = new List<string>(args);
        if (list.Count >= 2 && list[0] == "-s") list.RemoveRange(0, 2);
        if (list.Count == 0) return 0;
        string cmd = list[0];
        string rest = string.Join(" ", list.GetRange(1, list.Count - 1).ToArray());
        var off = new List<string>();
        if (File.Exists(state)) foreach (string l in File.ReadAllLines(state)) if (l.Trim().Length > 0) off.Add(l.Trim());
        string sdk = Environment.GetEnvironmentVariable("FAKE_SDK") ?? "35";
        string owner = Environment.GetEnvironmentVariable("FAKE_OWNER") ?? "";
        int users; if (!int.TryParse(Environment.GetEnvironmentVariable("FAKE_USERS"), out users)) users = 1;

        if (cmd == "start-server" || cmd == "wait-for-device" || cmd == "reboot") return 0;
        if (cmd == "devices") { Console.WriteLine("List of devices attached\nFAKE\tdevice\n"); return 0; }
        if (cmd == "install") { Console.WriteLine("Performing Streamed Install\nSuccess"); return 0; }
        if (cmd != "shell") return 0;

        if (rest == "getprop ro.product.model") Console.WriteLine("FakePhone");
        else if (rest == "getprop ro.build.version.release") Console.WriteLine("15");
        else if (rest == "getprop ro.build.version.sdk") Console.WriteLine(sdk);
        else if (rest == "getprop sys.boot_completed") Console.WriteLine("1");
        else if (rest == "dumpsys device_policy")
        {
            Console.WriteLine("Current Device Policy Manager state:");
            if (owner.Length > 0) Console.WriteLine("  Device Owner:\n    admin=ComponentInfo{" + owner + "/" + owner + ".AdminReceiver}\n    package=" + owner);
        }
        else if (rest == "pm list users")
        {
            Console.WriteLine("Users:");
            for (int i = 0; i < users; i++) Console.WriteLine("\tUserInfo{" + i + ":User" + i + ":13} running");
        }
        else if (rest == "dumpsys account")
        {
            Console.WriteLine("Accounts: 2\n  Account {name=someone@example.com, type=com.google}\n  Account {name=x, type=com.osp.app.signin}\n");
            Console.WriteLine("RegisteredServicesCache: 3 services");
            Console.WriteLine("  ServiceInfo: AuthenticatorDescription {type=com.google}, ComponentInfo{com.google.android.gms/com.google.android.gms.auth.GoogleAccountAuthenticatorService}, uid 10228");
            Console.WriteLine("  ServiceInfo: AuthenticatorDescription {type=com.osp.app.signin}, ComponentInfo{com.osp.app.signin/com.samsung.android.OspAuthenticationService}, uid 10101");
            Console.WriteLine("  ServiceInfo: AuthenticatorDescription {type=com.microsoft.skydrive}, ComponentInfo{com.microsoft.skydrive/com.microsoft.authorization.AuthenticationService}, uid 10097");
        }
        else if (rest.StartsWith("pm list packages -d")) foreach (string p in off) Console.WriteLine("package:" + p);
        else if (rest.StartsWith("pm disable-user"))
        {
            string pkg = list[list.Count - 1];
            if (!off.Contains(pkg)) off.Add(pkg);
            File.WriteAllLines(state, off.ToArray());
            Console.WriteLine("Package " + pkg + " new state: disabled-user");
        }
        else if (rest.StartsWith("pm enable"))
        {
            string pkg = list[list.Count - 1];
            off.Remove(pkg);
            File.WriteAllLines(state, off.ToArray());
            Console.WriteLine("Package " + pkg + " new state: enabled");
        }
        else if (rest.StartsWith("dpm set-device-owner"))
        {
            bool services = off.Contains("com.google.android.gms") && off.Contains("com.osp.app.signin");
            if (Environment.GetEnvironmentVariable("FAKE_FAIL") == "1") Console.WriteLine("java.lang.IllegalStateException: Not allowed to set the device owner because there are already some accounts on the device");
            else if (!services) Console.WriteLine("java.lang.IllegalStateException: Not allowed to set the device owner because there are already some accounts on the device");
            else Console.WriteLine("Success: Device owner set to package ComponentInfo{com.familymdm.agent/com.familymdm.agent.AdminReceiver}");
        }
        else if (rest.StartsWith("am start")) Console.WriteLine("Starting: Intent { cmp=com.familymdm.agent/.MainActivity }");
        return 0;
    }
}
