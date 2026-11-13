// Family MDM phone setup (Windows).
//
// Makes a phone managed by the Family MDM app in one go: installs the app over a USB cable, switches the phone's
// account services off for a moment (Android refuses to hand over management while accounts exist), makes the app the
// device owner, and switches the account services back on. What it switched off is written to a file BEFORE it is
// touched, and put back in every case (success, failure, window closed): "Put things back" does it again by hand.
//
// Plain C# 5 so the compiler that ships with Windows can build it (see build.ps1). Needs nothing installed.
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.IO.Compression;
using System.Net;
using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Windows.Forms;

namespace FamilyMdmSetup
{
    /// <summary>Reading what the phone prints. Kept apart from everything else so it can be tested without a phone.</summary>
    public static class Parse
    {
        /// <summary>The account types that have at least one account ("Account {name=..., type=com.google}").</summary>
        public static List<string> AccountTypes(string dumpsysAccount)
        {
            var types = new List<string>();
            if (dumpsysAccount == null) return types;
            int stop = dumpsysAccount.IndexOf("RegisteredServicesCache", StringComparison.Ordinal);
            string head = stop > 0 ? dumpsysAccount.Substring(0, stop) : dumpsysAccount;
            foreach (Match m in Regex.Matches(head, @"Account \{name=[^\r\n]*?, type=([^}\r\n]+)\}"))
                if (!types.Contains(m.Groups[1].Value.Trim())) types.Add(m.Groups[1].Value.Trim());
            return types;
        }

        /// <summary>How many accounts the phone says it has ("Accounts: 3"); -1 when it does not say.</summary>
        public static int AccountCount(string dumpsysAccount)
        {
            if (dumpsysAccount == null) return -1;
            Match m = Regex.Match(dumpsysAccount, @"^\s*Accounts:\s*(\d+)", RegexOptions.Multiline);
            return m.Success ? int.Parse(m.Groups[1].Value) : -1;
        }

        /// <summary>account type -> the package whose service handles it, from the "RegisteredServicesCache" part.</summary>
        public static Dictionary<string, string> Authenticators(string dumpsysAccount)
        {
            var map = new Dictionary<string, string>();
            if (dumpsysAccount == null) return map;
            foreach (Match m in Regex.Matches(dumpsysAccount, @"AuthenticatorDescription \{type=([^}]+)\}, ComponentInfo\{([^/}]+)/"))
                map[m.Groups[1].Value.Trim()] = m.Groups[2].Value.Trim();
            return map;
        }

        /// <summary>The package that is the device owner, or "" when the phone has none.</summary>
        public static string DeviceOwner(string dumpsysDevicePolicy)
        {
            if (dumpsysDevicePolicy == null) return "";
            Match m = Regex.Match(dumpsysDevicePolicy, @"Device Owner:\s*\r?\n\s*admin=ComponentInfo\{([^/}]+)/");
            return m.Success ? m.Groups[1].Value.Trim() : "";
        }

        /// <summary>How many users the phone has (the owner, a guest, a work profile...).</summary>
        public static int UserCount(string pmListUsers)
        {
            if (pmListUsers == null) return 0;
            return Regex.Matches(pmListUsers, @"UserInfo\{").Count;
        }

        /// <summary>"package:com.x" lines to package names.</summary>
        public static List<string> Packages(string pmList)
        {
            var list = new List<string>();
            if (pmList == null) return list;
            foreach (Match m in Regex.Matches(pmList, @"^\s*package:([A-Za-z0-9_.]+)", RegexOptions.Multiline)) list.Add(m.Groups[1].Value);
            return list;
        }

        /// <summary>The (serial, state) pairs of "adb devices".</summary>
        public static List<string[]> Devices(string adbDevices)
        {
            var list = new List<string[]>();
            if (adbDevices == null) return list;
            foreach (string raw in adbDevices.Split('\n'))
            {
                string line = raw.Trim();
                if (line.Length == 0 || line.StartsWith("List of devices") || line.StartsWith("*")) continue;
                string[] parts = Regex.Split(line, @"\s+");
                if (parts.Length >= 2) list.Add(new[] { parts[0], parts[1] });
            }
            return list;
        }

        /// <summary>True when `checksum` (hex, base64 or base64url) is the SHA-256 of the data.</summary>
        public static bool ChecksumMatches(byte[] sha256, string checksum)
        {
            if (sha256 == null || string.IsNullOrEmpty(checksum)) return false;
            string want = checksum.Trim();
            string hex = BitConverter.ToString(sha256).Replace("-", "");
            if (string.Equals(hex, want, StringComparison.OrdinalIgnoreCase)) return true;
            string b64 = Convert.ToBase64String(sha256);
            string norm = want.Replace('-', '+').Replace('_', '/').TrimEnd('=');
            return string.Equals(b64.TrimEnd('='), norm, StringComparison.Ordinal);
        }
    }

    public class Options
    {
        public string Server = "";
        public string Code = "";
        public string ApkPath = "";
        public string Serial = "";
        public bool DryRun;
    }

    /// <summary>The setup itself.</summary>
    public class Setup
    {
        public const string Package = "com.familymdm.agent";
        public const string Admin = "com.familymdm.agent/.AdminReceiver";
        const string ApkUrl = "https://github.com/abegumbo-ui/MDM/releases/download/latest/mdm-agent-enroll.apk";
        const string VersionUrl = "https://github.com/abegumbo-ui/MDM/releases/download/latest/version.json";
        const string ToolsUrl = "https://dl.google.com/android/repository/platform-tools-latest-windows.zip";

        public static readonly string Home = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "FamilyMDMSetup");
        public static readonly string LogFile = Path.Combine(Home, "setup.log");
        static readonly string StateFile = Path.Combine(Home, "switched-off.txt");
        static readonly object LogLock = new object();

        readonly Action<string> ui;
        string adb;
        public volatile bool Cancel;

        public Setup(Action<string> ui)
        {
            this.ui = ui;
            Directory.CreateDirectory(Home);
        }

        public void Log(string s)
        {
            string line = DateTime.Now.ToString("HH:mm:ss") + "  " + s;
            lock (LogLock) { try { File.AppendAllText(LogFile, line + "\r\n"); } catch (IOException) { } }
            if (ui != null) ui(s);
        }

        // ---------------- running adb ----------------

        string Run(string file, string args, int timeoutMs, out int exit)
        {
            var psi = new ProcessStartInfo(file, args);
            psi.UseShellExecute = false;
            psi.CreateNoWindow = true;
            psi.RedirectStandardOutput = true;
            psi.RedirectStandardError = true;
            psi.StandardOutputEncoding = Encoding.UTF8;
            var sb = new StringBuilder();
            using (var p = new Process())
            {
                p.StartInfo = psi;
                p.OutputDataReceived += (s, e) => { if (e.Data != null) lock (sb) sb.AppendLine(e.Data); };
                p.ErrorDataReceived += (s, e) => { if (e.Data != null) lock (sb) sb.AppendLine(e.Data); };
                p.Start();
                p.BeginOutputReadLine();
                p.BeginErrorReadLine();
                if (!p.WaitForExit(timeoutMs))
                {
                    try { p.Kill(); } catch (InvalidOperationException) { }
                    exit = -1;
                    lock (sb) return sb.ToString() + "(timed out)";
                }
                p.WaitForExit();
                exit = p.ExitCode;
            }
            lock (sb) return sb.ToString();
        }

        string Adb(string serial, string args, int timeoutMs)
        {
            int exit;
            return Adb(serial, args, timeoutMs, out exit);
        }

        string Adb(string serial, string args, int timeoutMs, out int exit)
        {
            string full = (string.IsNullOrEmpty(serial) ? "" : "-s " + serial + " ") + args;
            return Run(adb, full, timeoutMs, out exit);
        }

        // ---------------- step 1: adb ----------------

        string FindAdb()
        {
            string here = AppDomain.CurrentDomain.BaseDirectory;
            string[] candidates =
            {
                Path.Combine(here, "platform-tools", "adb.exe"),
                Path.Combine(here, "adb.exe"),
                Path.Combine(Home, "platform-tools", "adb.exe"),
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Android", "Sdk", "platform-tools", "adb.exe"),
            };
            foreach (string c in candidates) if (File.Exists(c)) return c;
            foreach (string dir in (Environment.GetEnvironmentVariable("PATH") ?? "").Split(';'))
            {
                try { string c = Path.Combine(dir.Trim().Trim('"'), "adb.exe"); if (dir.Trim().Length > 0 && File.Exists(c)) return c; } catch (ArgumentException) { }
            }
            return null;
        }

        void Download(string url, string file)
        {
            ServicePointManager.SecurityProtocol = (SecurityProtocolType)3072; // TLS 1.2
            using (var wc = new WebClient()) wc.DownloadFile(url, file);
        }

        bool EnsureAdb()
        {
            adb = FindAdb();
            if (adb == null)
            {
                Log("Google's small adb tool is not on this computer. Downloading it from Google (about 10 MB)...");
                string zip = Path.Combine(Home, "platform-tools.zip");
                Download(ToolsUrl, zip);
                string target = Path.Combine(Home, "platform-tools");
                if (Directory.Exists(target)) Directory.Delete(target, true);
                ZipFile.ExtractToDirectory(zip, Home);
                File.Delete(zip);
                adb = FindAdb();
            }
            if (adb == null || !File.Exists(adb)) { Log("PROBLEM: could not get adb. Put the folder 'platform-tools' next to this program and try again."); return false; }
            int exit;
            Run(adb, "start-server", 20000, out exit);
            Log("OK  adb is ready (" + adb + ")");
            return true;
        }

        // ---------------- step 2: the phone ----------------

        string WaitForPhone(string want)
        {
            bool toldAllow = false;
            for (int i = 0; i < 60 && !Cancel; i++)
            {
                List<string[]> devs = Parse.Devices(Adb(null, "devices", 15000));
                var ready = new List<string>();
                foreach (string[] d in devs)
                {
                    if (!string.IsNullOrEmpty(want) && d[0] != want) continue;
                    if (d[1] == "device") ready.Add(d[0]);
                    else if (d[1] == "unauthorized" && !toldAllow)
                    {
                        toldAllow = true;
                        Log("Look at the phone's screen and tap Allow on 'Allow USB debugging?' (tick 'Always allow from this computer').");
                    }
                }
                if (ready.Count == 1) return ready[0];
                if (ready.Count > 1)
                {
                    Log("PROBLEM: more than one phone is plugged in. Unplug the others and try again, so the wrong phone is never changed.");
                    return null;
                }
                if (i == 0) Log("Waiting for the phone... Plug it in with its USB cable, and switch on Developer options > USB debugging.");
                Thread.Sleep(3000);
            }
            Log("PROBLEM: no phone showed up. Check the cable (it must carry data, not only charge), USB debugging, and the Allow question on the phone.");
            return null;
        }

        string Prop(string serial, string name) { return Adb(serial, "shell getprop " + name, 15000).Trim(); }

        // ---------------- the "switched off" list (written first, read back to put things right) ----------------

        List<string> ReadState(string serial)
        {
            var list = new List<string>();
            if (!File.Exists(StateFile)) return list;
            foreach (string line in File.ReadAllLines(StateFile))
            {
                string[] p = line.Split('\t');
                if (p.Length == 2 && p[0] == serial && Regex.IsMatch(p[1], @"^[A-Za-z0-9_.]+$")) list.Add(p[1]);
            }
            return list;
        }

        void WriteState(string serial, List<string> pkgs)
        {
            var keep = new List<string>();
            if (File.Exists(StateFile)) foreach (string line in File.ReadAllLines(StateFile)) if (!line.StartsWith(serial + "\t")) keep.Add(line);
            foreach (string p in pkgs) keep.Add(serial + "\t" + p);
            if (keep.Count == 0) { if (File.Exists(StateFile)) File.Delete(StateFile); }
            else File.WriteAllLines(StateFile, keep.ToArray());
        }

        /// <summary>Switches back on everything this program switched off on that phone. True when nothing is left switched off.</summary>
        public bool PutBack(string serial)
        {
            List<string> pkgs = ReadState(serial);
            if (pkgs.Count == 0) return true;
            Log("Putting the phone's account services back on (" + pkgs.Count + ")...");
            var left = new List<string>();
            foreach (string pkg in pkgs)
            {
                Adb(serial, "shell pm enable --user 0 " + pkg, 20000);
                Adb(serial, "shell pm enable " + pkg, 20000);
            }
            List<string> stillOff = Parse.Packages(Adb(serial, "shell pm list packages -d --user 0", 20000));
            foreach (string pkg in pkgs) if (stillOff.Contains(pkg)) left.Add(pkg);
            WriteState(serial, left);
            if (left.Count == 0) { Log("OK  Everything is back on."); return true; }
            Log("PROBLEM: these are still switched off: " + string.Join(", ", left.ToArray()) + ". Run 'Put things back' again with the phone plugged in.");
            return false;
        }

        /// <summary>"Put things back" by hand: finds the plugged-in phone and switches on whatever an earlier run left off.</summary>
        public bool RestoreFlow()
        {
            try
            {
                if (!EnsureAdb()) return false;
                string serial = WaitForPhone("");
                if (serial == null) return false;
                if (ReadState(serial).Count == 0) { Log("OK  Nothing is switched off on this phone."); return true; }
                return PutBack(serial);
            }
            catch (Exception e)
            {
                Log("PROBLEM: " + e.Message);
                return false;
            }
        }

        // ---------------- step 4: the app ----------------

        string GetApk(Options o)
        {
            if (!string.IsNullOrEmpty(o.ApkPath) && File.Exists(o.ApkPath)) return o.ApkPath;
            string beside = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "mdm-agent-enroll.apk");
            if (File.Exists(beside)) { Log("Using the app file beside this program."); return beside; }
            Log("Downloading the newest Family MDM app...");
            string file = Path.Combine(Home, "mdm-agent-enroll.apk");
            Download(ApkUrl, file);
            string want = null;
            try
            {
                string vf = Path.Combine(Home, "version.json");
                Download(VersionUrl, vf);
                Match m = Regex.Match(File.ReadAllText(vf), "\"enrollSha256\"\\s*:\\s*\"([^\"]+)\"");
                if (m.Success) want = m.Groups[1].Value;
            }
            catch (WebException) { }
            if (want != null)
            {
                byte[] sha;
                using (var s = File.OpenRead(file)) sha = SHA256.Create().ComputeHash(s);
                if (!Parse.ChecksumMatches(sha, want)) { Log("PROBLEM: the downloaded app does not match its published checksum. Nothing was installed."); File.Delete(file); return null; }
                Log("OK  The app matches its published checksum.");
            }
            return file;
        }

        // ---------------- the whole thing ----------------

        public bool Run(Options o)
        {
            Log("---- Family MDM phone setup " + (o.DryRun ? "(only checking, nothing will be changed)" : "") + " ----");
            string serial = null;
            try
            {
                if (!EnsureAdb()) return false;
                serial = WaitForPhone(o.Serial);
                if (serial == null) return false;
                string model = Prop(serial, "ro.product.model"), release = Prop(serial, "ro.build.version.release");
                int sdk; int.TryParse(Prop(serial, "ro.build.version.sdk"), out sdk);
                Log("OK  Phone found: " + model + ", Android " + release);

                // Anything left switched off by an earlier run goes back first.
                if (ReadState(serial).Count > 0 && !o.DryRun) PutBack(serial);

                string owner = Parse.DeviceOwner(Adb(serial, "shell dumpsys device_policy", 30000));
                bool ownerIsUs = owner == Package;
                if (owner.Length > 0 && !ownerIsUs)
                {
                    Log("PROBLEM: this phone is already managed by '" + owner + "'. Only one app can manage a phone. Remove that first (or factory reset the phone), then run this again. Nothing was changed.");
                    return false;
                }
                if (ownerIsUs) Log("OK  Family MDM is already the manager of this phone.");

                if (!ownerIsUs)
                {
                    int users = Parse.UserCount(Adb(serial, "shell pm list users", 20000));
                    if (users > 1)
                    {
                        Log("PROBLEM: the phone has " + users + " users (a guest, a second user or a work profile). Android only allows management on a phone with a single user. Remove the extra users in Settings > Accounts and backup > Users (or Settings > General management), then run this again. Nothing was changed.");
                        return false;
                    }
                    Log("OK  The phone has a single user.");
                }

                string accountDump = Adb(serial, "shell dumpsys account", 30000);
                int accounts = Parse.AccountCount(accountDump);
                List<string> toSwitchOff = ownerIsUs ? new List<string>() : ServicesToSwitchOff(serial, accountDump, accounts);
                Log(accounts == 0 ? "OK  The phone has no accounts." : "The phone has " + (accounts < 0 ? "some" : accounts.ToString()) + " account(s). For a moment these services will be switched off and then back on: " + (toSwitchOff.Count == 0 ? "(none found)" : string.Join(", ", toSwitchOff.ToArray())));

                if (o.DryRun)
                {
                    Log("Only checking: the app would be installed" + (ownerIsUs ? " (already there)" : "") + ", " + (ownerIsUs ? "" : "made the manager, ") + "and started" + (string.IsNullOrEmpty(o.Code) ? "." : " with the code."));
                    Log("Nothing was changed on the phone.");
                    return true;
                }

                string apk = GetApk(o);
                if (apk == null) return false;
                Log("Installing the app on the phone...");
                int exit;
                string inst = Adb(serial, "install -r \"" + apk + "\"", 180000, out exit);
                if (inst.IndexOf("Success", StringComparison.OrdinalIgnoreCase) < 0)
                {
                    Log("PROBLEM: the phone refused the app: " + inst.Trim());
                    if (inst.IndexOf("USER_RESTRICTED", StringComparison.OrdinalIgnoreCase) >= 0)
                        Log("On Samsung: switch off Settings > Security and privacy > Auto Blocker, and on Xiaomi switch on 'Install via USB' in Developer options, then run this again.");
                    if (inst.IndexOf("UPDATE_INCOMPATIBLE", StringComparison.OrdinalIgnoreCase) >= 0)
                        Log("A copy of the app signed differently is already on the phone. Uninstall it from the phone first.");
                    return false;
                }
                Log("OK  App installed.");

                if (!ownerIsUs && !MakeOwner(serial, sdk, toSwitchOff)) return false;

                StartApp(serial, o);
                Log("DONE. The phone is set up. If you typed a code it joins the dashboard on its own; otherwise open the app on the phone and enter the code.");
                return true;
            }
            catch (Exception e)
            {
                Log("PROBLEM: " + e.Message);
                return false;
            }
            finally
            {
                if (serial != null && !o.DryRun) PutBack(serial);
            }
        }

        List<string> ServicesToSwitchOff(string serial, string dump, int accounts)
        {
            var result = new List<string>();
            if (accounts == 0) return result;
            Dictionary<string, string> map = Parse.Authenticators(dump);
            var wanted = new List<string>();
            foreach (string type in Parse.AccountTypes(dump)) { string pkg; if (map.TryGetValue(type, out pkg) && !wanted.Contains(pkg)) wanted.Add(pkg); }
            if (wanted.Count == 0) foreach (string pkg in map.Values) if (!wanted.Contains(pkg)) wanted.Add(pkg); // could not tell which: all of them
            List<string> alreadyOff = Parse.Packages(Adb(serial, "shell pm list packages -d --user 0", 20000));
            foreach (string pkg in wanted) if (pkg != Package && !alreadyOff.Contains(pkg)) result.Add(pkg);
            return result;
        }

        bool MakeOwner(string serial, int sdk, List<string> toSwitchOff)
        {
            if (toSwitchOff.Count > 0)
            {
                WriteState(serial, toSwitchOff); // written BEFORE anything is touched
                Log("Switching off the account services for a moment. Do not unplug the phone.");
                foreach (string pkg in toSwitchOff) Adb(serial, "shell pm disable-user --user 0 " + pkg, 20000);
            }
            for (int attempt = 1; attempt <= 3 && !Cancel; attempt++)
            {
                Log("Making Family MDM the manager of the phone" + (attempt > 1 ? " (try " + attempt + ")" : "") + "...");
                string r = Adb(serial, "shell dpm set-device-owner " + Admin, 60000);
                if (r.IndexOf("Success", StringComparison.OrdinalIgnoreCase) >= 0)
                {
                    Log("OK  Family MDM is now the manager of this phone.");
                    PutBack(serial); // straight away: the account services come back on before anything else happens
                    return true;
                }
                Log("The phone said: " + r.Trim());
                if (attempt == 3 || sdk < 34) break;
                // Android 14 and newer sometimes need a restart before they notice the accounts are gone.
                Log("Restarting the phone and trying again (Android 14 and newer need this). Unlock it with its PIN when it asks.");
                Thread.Sleep(15000);
                Adb(serial, "reboot", 20000);
                Thread.Sleep(15000);
                Adb(serial, "wait-for-device", 240000);
                for (int i = 0; i < 40 && Prop(serial, "sys.boot_completed") != "1"; i++) Thread.Sleep(5000);
                Adb(serial, "shell input keyevent KEYCODE_WAKEUP", 10000);
                Adb(serial, "shell wm dismiss-keyguard", 10000);
                Thread.Sleep(25000);
            }
            Log("PROBLEM: the phone would not make Family MDM its manager. Nothing else was changed, and the account services are being put back. If the message above mentions accounts, remove them in Settings > Accounts and run this again; if it mentions 'already provisioned' or 'users', follow what it says.");
            return false;
        }

        void StartApp(string serial, Options o)
        {
            string extras = "";
            if (Regex.IsMatch(o.Code ?? "", @"^[A-Za-z0-9-]{4,40}$")) extras += " --es code " + o.Code;
            if (Regex.IsMatch(o.Server ?? "", @"^https?://[A-Za-z0-9.\-:/_]+$")) extras += " --es server " + o.Server;
            Adb(serial, "shell am start -n " + Package + "/.MainActivity" + extras, 20000);
            Log("OK  The app was started on the phone.");
        }
    }

    // ---------------- the window ----------------

    public class MainForm : Form
    {
        readonly TextBox server = new TextBox(), code = new TextBox(), log = new TextBox();
        readonly CheckBox dry = new CheckBox();
        readonly Button go = new Button(), back = new Button(), folder = new Button();
        Setup setup;
        Thread worker;

        public MainForm()
        {
            Text = "Family MDM phone setup";
            Width = 640;
            Height = 640;
            MinimumSize = new Size(560, 520);
            Font = new Font("Segoe UI", 10f);
            var intro = new Label();
            intro.Text = "Plug the phone into this computer with its USB cable and switch on Developer options > USB debugging.\r\n" +
                "Then type the code from the dashboard (Codes > Enrollment code) and press the button.";
            intro.SetBounds(16, 12, 592, 56);
            var l1 = new Label(); l1.Text = "Code from the dashboard"; l1.SetBounds(16, 76, 250, 22);
            code.SetBounds(16, 100, 250, 28);
            var l2 = new Label(); l2.Text = "Dashboard address (leave empty if the app already has it)"; l2.SetBounds(280, 76, 330, 22);
            server.SetBounds(280, 100, 328, 28);
            dry.Text = "Only check the phone (change nothing)"; dry.SetBounds(16, 138, 380, 26);
            go.Text = "Set up the phone"; go.SetBounds(16, 172, 200, 40); go.Click += (s, e) => Start(false);
            back.Text = "Put things back"; back.SetBounds(226, 172, 160, 40); back.Click += (s, e) => Start(true);
            folder.Text = "Open the log"; folder.SetBounds(396, 172, 140, 40);
            folder.Click += (s, e) => { try { Process.Start("notepad.exe", Setup.LogFile); } catch (Exception) { } };
            log.Multiline = true; log.ReadOnly = true; log.ScrollBars = ScrollBars.Vertical; log.SetBounds(16, 226, 592, 366);
            log.Font = new Font("Consolas", 9.5f);
            log.Anchor = AnchorStyles.Top | AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right;
            Controls.AddRange(new Control[] { intro, l1, code, l2, server, dry, go, back, folder, log });
            setup = new Setup(Append);
            FormClosing += (s, e) =>
            {
                if (worker != null && worker.IsAlive)
                {
                    if (MessageBox.Show("The setup is still running. Closing now could leave the phone's account services switched off (they are put back the next time you run this).\r\n\r\nClose anyway?", "Family MDM phone setup", MessageBoxButtons.YesNo, MessageBoxIcon.Warning) == DialogResult.No) e.Cancel = true;
                    else setup.Cancel = true;
                }
            };
        }

        void Append(string text)
        {
            if (InvokeRequired) { try { BeginInvoke(new Action<string>(Append), text); } catch (InvalidOperationException) { } return; }
            log.AppendText(text + "\r\n");
        }

        void Start(bool restoreOnly)
        {
            if (worker != null && worker.IsAlive) return;
            var o = new Options();
            o.Code = code.Text.Trim();
            o.Server = server.Text.Trim();
            o.DryRun = dry.Checked;
            go.Enabled = false; back.Enabled = false;
            worker = new Thread(() =>
            {
                try
                {
                    if (restoreOnly) RestoreOnly(); else setup.Run(o);
                }
                finally { try { BeginInvoke(new Action(() => { go.Enabled = true; back.Enabled = true; })); } catch (InvalidOperationException) { } }
            });
            worker.IsBackground = false;
            worker.Start();
        }

        void RestoreOnly()
        {
            setup.Log("---- Putting things back ----");
            setup.RestoreFlow();
        }
    }

    public static class Program
    {
        [STAThread]
        public static int Main(string[] args)
        {
            var o = new Options();
            bool cli = false, restore = false, selftest = false;
            for (int i = 0; i < args.Length; i++)
            {
                string a = args[i];
                string next = i + 1 < args.Length ? args[i + 1] : "";
                if (a == "--cli") cli = true;
                else if (a == "--dry-run") o.DryRun = true;
                else if (a == "--restore") { cli = true; restore = true; }
                else if (a == "--selftest") { cli = true; selftest = true; }
                else if (a == "--code") { o.Code = next; i++; }
                else if (a == "--server") { o.Server = next; i++; }
                else if (a == "--apk") { o.ApkPath = next; i++; }
                else if (a == "--serial") { o.Serial = next; i++; }
            }
            if (selftest) return SelfTest.Run();
            if (cli)
            {
                var setup = new Setup(null);
                if (restore) return setup.RestoreFlow() ? 0 : 1;
                return setup.Run(o) ? 0 : 1;
            }
            Application.EnableVisualStyles();
            Application.Run(new MainForm());
            return 0;
        }
    }

    /// <summary>`FamilyMdmSetup.exe --selftest`: checks the parsing against output copied from a real phone. Writes the verdict to the log.</summary>
    public static class SelfTest
    {
        public static int Run()
        {
            var s = new Setup(null);
            int bad = 0;
            Action<string, bool> check = (name, ok) => { s.Log((ok ? "PASS  " : "FAIL  ") + name); if (!ok) bad++; };
            string dump = "Accounts: 2\n  Account {name=someone@example.com, type=com.google}\n  Account {name=x, type=com.osp.app.signin}\n\nRegisteredServicesCache: 3 services\n" +
                "  ServiceInfo: AuthenticatorDescription {type=com.google}, ComponentInfo{com.google.android.gms/com.google.android.gms.auth.account.authenticator.GoogleAccountAuthenticatorService}, uid 10228, lastUpdateTime 1\n" +
                "  ServiceInfo: AuthenticatorDescription {type=com.osp.app.signin}, ComponentInfo{com.osp.app.signin/com.samsung.android.samsungaccount.authentication.service.OspAuthenticationService}, uid 10101\n" +
                "  ServiceInfo: AuthenticatorDescription {type=com.microsoft.skydrive}, ComponentInfo{com.microsoft.skydrive/com.microsoft.authorization.AuthenticationService}, uid 10097\n";
            check("account count", Parse.AccountCount(dump) == 2);
            check("account types", string.Join(",", Parse.AccountTypes(dump).ToArray()) == "com.google,com.osp.app.signin");
            Dictionary<string, string> map = Parse.Authenticators(dump);
            check("authenticator packages", map.Count == 3 && map["com.google"] == "com.google.android.gms" && map["com.osp.app.signin"] == "com.osp.app.signin");
            check("no accounts", Parse.AccountCount("Accounts: 0\nRegisteredServicesCache: 7 services\n") == 0 && Parse.AccountTypes("Accounts: 0\n").Count == 0);
            check("device owner", Parse.DeviceOwner("  Device Owner:\r\n    admin=ComponentInfo{com.familymdm.agent/com.familymdm.agent.AdminReceiver}\r\n    package=com.familymdm.agent\r\n") == "com.familymdm.agent");
            check("no device owner", Parse.DeviceOwner("Current Device Policy Manager state:\n  Immutable state:\n") == "");
            check("one user", Parse.UserCount("Users:\n\tUserInfo{0:null:4c13} running\n") == 1);
            check("two users", Parse.UserCount("Users:\n\tUserInfo{0:Owner:13} running\n\tUserInfo{10:Guest:4} running\n") == 2);
            check("package list", string.Join(",", Parse.Packages("package:com.a.b\r\npackage:com.c\r\n").ToArray()) == "com.a.b,com.c");
            List<string[]> devs = Parse.Devices("List of devices attached\nR5GL40JLNTY\tdevice\nABC123\tunauthorized\n\n");
            check("adb devices", devs.Count == 2 && devs[0][0] == "R5GL40JLNTY" && devs[1][1] == "unauthorized");
            byte[] sha = SHA256.Create().ComputeHash(Encoding.ASCII.GetBytes("abc"));
            string hex = BitConverter.ToString(sha).Replace("-", "").ToLowerInvariant();
            string b64url = Convert.ToBase64String(sha).Replace('+', '-').Replace('/', '_').TrimEnd('=');
            check("checksum hex / base64url / wrong", Parse.ChecksumMatches(sha, hex) && Parse.ChecksumMatches(sha, b64url) && !Parse.ChecksumMatches(sha, "00" + hex.Substring(2)));
            s.Log(bad == 0 ? "SELFTEST OK" : "SELFTEST FAILED (" + bad + ")");
            return bad == 0 ? 0 : 1;
        }
    }
}
