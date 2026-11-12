using LockGuard.Common;

namespace LockGuard.Setup;

/// <summary>
/// The LockGuard window: a coloured band that says plainly whether this computer is protected,
/// status cards (web filter, dashboard, protection, version), and the code to make changes.
/// Opened from Windows' "Installed apps" → Uninstall (with /uninstall) it goes straight to the
/// removal code. Every change is sent to the service over the pipe and checked there; this window
/// holds no authority of its own.
/// </summary>
public sealed class SetupForm : Form
{
    private static readonly Color Brand = Color.FromArgb(79, 70, 229);
    private static readonly Color Good = Color.FromArgb(22, 163, 74);
    private static readonly Color Bad = Color.FromArgb(220, 38, 38);
    private static readonly Color Warn = Color.FromArgb(217, 119, 6);
    private static readonly Color Page = Color.FromArgb(246, 247, 249);
    private static readonly Color Muted = Color.FromArgb(100, 106, 118);
    private static readonly Font Title = new("Segoe UI Semibold", 18f);
    private static readonly Font Big = new("Segoe UI Semibold", 12f);
    private static readonly Font Normal = new("Segoe UI", 10f);
    private static readonly Font Small = new("Segoe UI", 9f);

    private readonly bool _uninstallMode;
    private readonly Panel _header = new() { Dock = DockStyle.Top, Height = 104 };
    private readonly Label _headTitle = new() { AutoSize = true, Font = Title, ForeColor = Color.White, Location = new Point(84, 20), BackColor = Color.Transparent };
    private readonly Label _headSub = new() { AutoSize = true, Font = Normal, ForeColor = Color.White, Location = new Point(86, 60), BackColor = Color.Transparent, MaximumSize = new Size(640, 0) };
    private readonly FlowLayoutPanel _body = new() { Dock = DockStyle.Fill, FlowDirection = FlowDirection.TopDown, WrapContents = false, AutoScroll = true, Padding = new Padding(24, 18, 24, 18) };
    private string _unlockedCode = ""; // held only in memory, for this window

    /// <summary>"/preview": shows the window with sample status and no connection to LockGuard (for screenshots).</summary>
    public static bool Preview;

    public SetupForm(bool uninstallMode = false)
    {
        _uninstallMode = uninstallMode;
        Text = "LockGuard";
        Width = 780;
        Height = 600;
        MinimumSize = new Size(640, 480);
        StartPosition = FormStartPosition.CenterScreen;
        BackColor = Page;
        Font = Normal;

        _header.Paint += (_, e) =>
        {
            // A shield, drawn so there is no image file to ship.
            var g = e.Graphics;
            g.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias;
            var pts = new[] { new Point(36, 22), new Point(60, 30), new Point(60, 52), new Point(36, 80), new Point(12, 52), new Point(12, 30) };
            using var b = new SolidBrush(Color.FromArgb(70, 255, 255, 255));
            g.FillPolygon(b, pts);
            using var p = new Pen(Color.White, 2.5f);
            g.DrawPolygon(p, pts);
        };
        _header.Controls.Add(_headTitle);
        _header.Controls.Add(_headSub);
        Controls.Add(_body);
        Controls.Add(_header);


        Load += async (_, _) => await RefreshAsync();
    }

    private void SetHeader(Color color, string title, string sub)
    {
        _header.BackColor = color;
        _headTitle.Text = title;
        _headSub.Text = sub;
    }

    private void FitCards()
    {
        var w = Math.Max(300, _body.ClientSize.Width - _body.Padding.Horizontal - SystemInformation.VerticalScrollBarWidth);
        // Cards are given their width once, when made (see Card); nothing to do on resize.
    }

    private async Task RefreshAsync()
    {
        var resp = Preview
            ? new PipeResponse { Ok = true, FilterOn = true, FilterLevel = "moderate", ProtectedFromAdmins = true, LastCheckIn = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() - 120000, Build = 47 }
            : await PipeClient.SendAsync(new PipeRequest { Action = PipeActions.GetStatus });
        _body.Controls.Clear();
        if (!resp.Ok)
        {
            SetHeader(Bad, "LockGuard is not running", "This window could not reach LockGuard on this computer.");
            var c = Card("What to do");
            c.Controls.Add(Para("Restart the computer. If this still shows after a restart, LockGuard may have been removed or damaged: install it again from the dashboard.\n\n" + resp.Message, Normal, Muted));
            FitCards();
            return;
        }

        if (resp.IsDefaultCode) ShowFirstRun(resp);
        else if (_unlockedCode == "") ShowStatusAndGate(resp);
        else ShowManage(resp);
        FitCards();
    }

    // ---------- header + status cards (always visible, no code needed to look) ----------

    private void StatusCards(PipeResponse s)
    {
        bool filtering = s.FilterOn && !s.FilterPaused && !s.Unlocked;
        if (s.Unlocked) SetHeader(Warn, "Restrictions are off", "The unlock file is in Downloads. Delete it to turn everything back on.");
        else if (s.Enabled && !s.FirewallOk) SetHeader(Bad, "Not protected", "LockGuard could not apply its settings: " + (s.FirewallError ?? "unknown error"));
        else if (filtering || s.Enabled) SetHeader(Good, "This computer is protected", "LockGuard is filtering the internet on this computer.");
        else if (s.FilterOn && s.FilterPaused) SetHeader(Warn, "Filtering is paused", "It comes back on by itself when the pause ends.");
        else SetHeader(Brand, "LockGuard", "No filter is switched on for this computer yet. Choose one on the dashboard.");

        var row = new FlowLayoutPanel { AutoSize = true, WrapContents = true, Margin = new Padding(0, 0, 0, 12) };
        row.Controls.Add(Tile("Web filter", !s.FilterOn ? "Off" : s.FilterLevel switch { "approved" => "Approved sites only", "strict" => "Strict", _ => "Moderate" }, s.FilterOn ? Good : Muted));
        var ago = s.LastCheckIn == 0 ? "not yet" : Ago(s.LastCheckIn);
        bool fresh = s.LastCheckIn > 0 && DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() - s.LastCheckIn < 15 * 60000;
        row.Controls.Add(Tile("Dashboard", "Last check-in " + ago, fresh ? Good : Warn));
        row.Controls.Add(Tile("Protection", s.ProtectedFromAdmins ? "Administrators cannot remove it" : "Standard", s.ProtectedFromAdmins ? Good : Muted));
        row.Controls.Add(Tile("Version", s.Build > 0 ? "Build " + s.Build : "Local build", Muted));
        _body.Controls.Add(row);
    }

    private static string Ago(long ms)
    {
        var m = (DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() - ms) / 60000;
        return m < 1 ? "just now" : m < 60 ? m + " min ago" : m < 1440 ? (m / 60) + " h ago" : (m / 1440) + " days ago";
    }

    private static Panel Tile(string label, string value, Color accent)
    {
        var p = new Panel { Width = 162, Height = 78, BackColor = Color.White, Margin = new Padding(0, 0, 10, 10) };
        p.Paint += (_, e) =>
        {
            using var bar = new SolidBrush(accent);
            e.Graphics.FillRectangle(bar, 0, 0, 4, p.Height);
            using var border = new Pen(Color.FromArgb(229, 231, 235));
            e.Graphics.DrawRectangle(border, 0, 0, p.Width - 1, p.Height - 1);
        };
        p.Controls.Add(new Label { Text = label, Font = Small, ForeColor = Muted, AutoSize = true, Location = new Point(14, 12) });
        p.Controls.Add(new Label { Text = value, Font = new Font("Segoe UI Semibold", 10f), AutoSize = true, MaximumSize = new Size(142, 0), Location = new Point(14, 34) });
        return p;
    }

    private FlowLayoutPanel Card(string title)
    {
        var c = new FlowLayoutPanel { FlowDirection = FlowDirection.TopDown, WrapContents = false, AutoSize = true, MinimumSize = new Size(700, 0), BackColor = Color.White, Padding = new Padding(18, 14, 18, 16), Margin = new Padding(0, 0, 0, 12) };
        c.Paint += (_, e) => { using var border = new Pen(Color.FromArgb(229, 231, 235)); e.Graphics.DrawRectangle(border, 0, 0, c.Width - 1, c.Height - 1); };
        c.Controls.Add(new Label { Text = title, Font = Big, AutoSize = true, Margin = new Padding(0, 0, 0, 6) });
        _body.Controls.Add(c);
        return c;
    }

    private static Label Para(string text, Font font, Color color) => new() { Text = text, Font = font, ForeColor = color, AutoSize = true, MaximumSize = new Size(660, 0), Margin = new Padding(0, 2, 0, 8) };

    private static TextBox Box(bool password) => new() { Width = 280, UseSystemPasswordChar = password, Font = new Font("Segoe UI", 11f), Margin = new Padding(0, 2, 0, 8) };

    private static Button Btn(string text, bool primary = true, bool danger = false)
    {
        var b = new Button { Text = text, AutoSize = true, Padding = new Padding(14, 6, 14, 6), FlatStyle = FlatStyle.Flat, Font = new Font("Segoe UI Semibold", 10f), Margin = new Padding(0, 4, 10, 4), Cursor = Cursors.Hand };
        b.FlatAppearance.BorderColor = danger ? Bad : Brand;
        b.BackColor = primary ? (danger ? Bad : Brand) : Color.White;
        b.ForeColor = primary ? Color.White : (danger ? Bad : Brand);
        return b;
    }

    private static Label Error() => new() { AutoSize = true, ForeColor = Bad, MaximumSize = new Size(660, 0), Margin = new Padding(0, 4, 0, 0) };

    // ---------- first run: choose a code ----------

    private void ShowFirstRun(PipeResponse s)
    {
        StatusCards(s);
        var c = Card("Choose your LockGuard code");
        c.Controls.Add(Para("You will need it to change anything here on the computer. Write it down somewhere safe: it cannot be recovered. (You can always manage this computer from the dashboard, and make a removal code there.)", Normal, Muted));
        c.Controls.Add(Para("New code (6 or more characters)", Small, Muted));
        var newCode = Box(true);
        c.Controls.Add(newCode);
        c.Controls.Add(Para("Type it again", Small, Muted));
        var confirm = Box(true);
        c.Controls.Add(confirm);
        var save = Btn("Save the code");
        var err = Error();
        c.Controls.Add(save);
        c.Controls.Add(err);
        save.Click += async (_, _) =>
        {
            if (newCode.Text.Length < 6 || newCode.Text != confirm.Text) { err.Text = "Use at least 6 characters, typed the same twice."; return; }
            save.Enabled = false;
            var resp = await PipeClient.SendAsync(new PipeRequest { Action = PipeActions.SetCode, Code = CodeStore.DefaultCode, Arg = newCode.Text });
            save.Enabled = true;
            if (!resp.Ok) { err.Text = resp.Message; return; }
            _unlockedCode = newCode.Text;
            await RefreshAsync();
        };
    }

    // ---------- status, and the code to make changes ----------

    private void ShowStatusAndGate(PipeResponse s)
    {
        StatusCards(s);
        if (!_uninstallMode) AskForSiteCard();
        if (!_uninstallMode) WhatParentSees(s);
        var c = Card(_uninstallMode ? "Remove LockGuard" : "Make changes");
        c.Controls.Add(Para(_uninstallMode
            ? "To remove LockGuard from this computer, enter the removal code. Get it on the dashboard: open this computer → Computer → Make a removal code."
            : "Enter your LockGuard code, or a removal code made on the dashboard.", Normal, Muted));
        var code = Box(true);
        c.Controls.Add(code);
        var go = Btn(_uninstallMode ? "Continue" : "Unlock");
        var err = Error();
        c.Controls.Add(go);
        c.Controls.Add(err);
        AcceptButton = go;
        code.Focus();
        go.Click += async (_, _) =>
        {
            go.Enabled = false;
            var verify = await PipeClient.SendAsync(new PipeRequest { Action = PipeActions.VerifyCode, Code = code.Text });
            go.Enabled = true;
            if (!verify.Ok) { err.Text = "That code is not right. Codes are case-sensitive; a removal code from the dashboard works for 48 hours."; return; }
            _unlockedCode = code.Text;
            if (_uninstallMode) await UninstallAsync();
            else await RefreshAsync();
        };
    }

    /// <summary>Every kind of monitoring that is on, in plain words: nothing is secret from the person at the computer.</summary>
    private void WhatParentSees(PipeResponse s)
    {
        var c = Card("What your parent can see");
        if (s.RecordSites) c.Controls.Add(Para("•  Which websites this computer opens.", Normal, Color.Black));
        c.Controls.Add(Para("•  The list of programs installed on this computer.", Normal, Color.Black));
        c.Controls.Add(Para("•  Websites you ask for here.", Normal, Color.Black));
        c.Controls.Add(Para("Nothing else: not your files, your screen or what you type.", Small, Muted));
    }

    /// <summary>Anyone may ask for a website (no code); the parent allows it on the dashboard.</summary>
    private void AskForSiteCard()
    {
        var c = Card("Ask for a website");
        c.Controls.Add(Para("Need a site that is blocked? Type its address and send it. Your parent sees it on the dashboard and can allow it.", Normal, Muted));
        var box = Box(false);
        box.PlaceholderText = "for example: school-website.org";
        c.Controls.Add(box);
        var send = Btn("Send", primary: false);
        var note = new Label { AutoSize = true, ForeColor = Good, MaximumSize = new Size(660, 0), Margin = new Padding(0, 4, 0, 0) };
        c.Controls.Add(send);
        c.Controls.Add(note);
        send.Click += async (_, _) =>
        {
            send.Enabled = false;
            var r = await PipeClient.SendAsync(new PipeRequest { Action = PipeActions.RequestSite, Arg = box.Text });
            send.Enabled = true;
            note.ForeColor = r.Ok ? Good : Bad;
            note.Text = r.Message;
            if (r.Ok) box.Text = "";
        };
    }

    // ---------- after the code: manage ----------

    private void ShowManage(PipeResponse s)
    {
        StatusCards(s);

        var lockCard = Card("Only chosen programs online");
        lockCard.Controls.Add(Para(s.Enabled
            ? "On: only the programs below can use the internet; everything else on this computer cannot."
            : "Off. Stricter than the web filter: when on, only the programs listed below can use the internet at all.", Normal, Muted));
        var list = new ListBox { Width = 560, Height = 110, Font = Normal, Margin = new Padding(0, 4, 0, 8) };
        list.Items.AddRange(s.AllowedPrograms.ToArray());
        lockCard.Controls.Add(list);
        var row = new FlowLayoutPanel { AutoSize = true, WrapContents = true };
        var add = Btn("Add a program...", primary: false);
        var remove = Btn("Remove selected", primary: false);
        var toggle = Btn(s.Enabled ? "Turn this off" : "Turn this on");
        row.Controls.AddRange(new Control[] { add, remove, toggle });
        lockCard.Controls.Add(row);

        var more = Card("More");
        more.Controls.Add(Para("Most settings (the web filter, blocked programs, schedules) are made on the dashboard and reach this computer within a minute.", Normal, Muted));
        var row2 = new FlowLayoutPanel { AutoSize = true, WrapContents = true };
        var changeCode = Btn("Change the code...", primary: false);
        var uninstall = Btn("Remove LockGuard...", primary: false, danger: true);
        var lockAgain = Btn("Lock this window", primary: false);
        row2.Controls.AddRange(new Control[] { changeCode, lockAgain, uninstall });
        more.Controls.Add(row2);

        add.Click += async (_, _) =>
        {
            using var dlg = new ProgramPickerDialog();
            if (dlg.ShowDialog(this) != DialogResult.OK || string.IsNullOrEmpty(dlg.SelectedPath)) return;
            var resp = await PipeClient.SendAsync(new PipeRequest { Action = PipeActions.AddProgram, Code = _unlockedCode, Arg = dlg.SelectedPath });
            if (!resp.Ok) MessageBox.Show(this, resp.Message, "Could not add it");
            await RefreshAsync();
        };
        remove.Click += async (_, _) =>
        {
            if (list.SelectedItem is not string path) return;
            var resp = await PipeClient.SendAsync(new PipeRequest { Action = PipeActions.RemoveProgram, Code = _unlockedCode, Arg = path });
            if (!resp.Ok) MessageBox.Show(this, resp.Message, "Could not remove it");
            await RefreshAsync();
        };
        toggle.Click += async (_, _) =>
        {
            var resp = await PipeClient.SendAsync(new PipeRequest { Action = PipeActions.SetEnabled, Code = _unlockedCode, Arg = s.Enabled ? "false" : "true" });
            if (!resp.Ok) MessageBox.Show(this, resp.Message, "Could not change it");
            await RefreshAsync();
        };
        changeCode.Click += (_, _) =>
        {
            using var dlg = new ChangeCodeDialog();
            if (dlg.ShowDialog(this) != DialogResult.OK) return;
            _ = ChangeCodeAsync(dlg.NewCode);
        };
        lockAgain.Click += async (_, _) => { _unlockedCode = ""; await RefreshAsync(); };
        uninstall.Click += async (_, _) => await UninstallAsync();
    }

    private async Task UninstallAsync()
    {
        var confirm = MessageBox.Show(this,
            "This removes LockGuard from this computer completely and puts normal internet back. Continue?",
            "Remove LockGuard", MessageBoxButtons.YesNo, MessageBoxIcon.Warning);
        if (confirm != DialogResult.Yes) { await RefreshAsync(); return; }
        var resp = await PipeClient.SendAsync(new PipeRequest { Action = PipeActions.Uninstall, Code = _unlockedCode });
        MessageBox.Show(this, resp.Ok ? "Removing LockGuard. This window will now close." : resp.Message, "LockGuard");
        if (resp.Ok) Close();
    }

    private async Task ChangeCodeAsync(string newCode)
    {
        var resp = await PipeClient.SendAsync(new PipeRequest { Action = PipeActions.SetCode, Code = _unlockedCode, Arg = newCode });
        if (!resp.Ok) { MessageBox.Show(this, resp.Message, "Could not change the code"); return; }
        _unlockedCode = newCode;
        MessageBox.Show(this, "Code changed.", "LockGuard");
    }
}

/// <summary>
/// Pick list for "Add a program": installed programs found from Start Menu shortcuts
/// (InstalledPrograms), searchable, with a manual file-browse fallback.
/// </summary>
internal sealed class ProgramPickerDialog : Form
{
    public string? SelectedPath { get; private set; }

    private readonly List<(string Name, string Path)> _all = InstalledPrograms.Find();

    public ProgramPickerDialog()
    {
        Text = "Add a program";
        Width = 420;
        Height = 420;
        StartPosition = FormStartPosition.CenterParent;
        FormBorderStyle = FormBorderStyle.FixedDialog;
        MaximizeBox = false;
        MinimizeBox = false;
        Font = new Font("Segoe UI", 10f);

        var search = new TextBox { Location = new Point(16, 16), Width = 370, PlaceholderText = "Search programs" };
        var list = new ListBox { Location = new Point(16, 50), Width = 370, Height = 250 };
        var browse = new Button { Text = "Can't find it? Browse for the program...", Location = new Point(16, 308), Width = 370, Height = 30 };
        var buttons = new FlowLayoutPanel { Location = new Point(16, 344), Width = 370, Height = 34, FlowDirection = FlowDirection.RightToLeft };
        var ok = new Button { Text = "Add", AutoSize = true };
        var cancel = new Button { Text = "Cancel", AutoSize = true };
        buttons.Controls.Add(cancel);
        buttons.Controls.Add(ok);
        Controls.Add(search);
        Controls.Add(list);
        Controls.Add(browse);
        Controls.Add(buttons);

        void Repopulate()
        {
            var term = search.Text.Trim();
            list.Items.Clear();
            foreach (var p in _all.Where(p => term.Length == 0 || p.Name.Contains(term, StringComparison.OrdinalIgnoreCase)))
                list.Items.Add(p.Name);
        }
        Repopulate();
        if (_all.Count == 0) list.Items.Add("(No programs found automatically -- use Browse below.)");
        search.TextChanged += (_, _) => Repopulate();

        ok.Click += (_, _) =>
        {
            if (list.SelectedItem is not string name) return;
            var match = _all.FirstOrDefault(p => p.Name == name);
            if (match.Path is null) return;
            SelectedPath = match.Path;
            DialogResult = DialogResult.OK;
            Close();
        };
        list.DoubleClick += (_, _) => ok.PerformClick();
        browse.Click += (_, _) =>
        {
            using var dlg = new OpenFileDialog { Filter = "Programs (*.exe)|*.exe", Title = "Pick the program to allow" };
            if (dlg.ShowDialog(this) != DialogResult.OK) return;
            SelectedPath = dlg.FileName;
            DialogResult = DialogResult.OK;
            Close();
        };
        cancel.Click += (_, _) => { DialogResult = DialogResult.Cancel; Close(); };
    }
}

/// <summary>Small window for "Change the code".</summary>
internal sealed class ChangeCodeDialog : Form
{
    public string NewCode { get; private set; } = "";

    public ChangeCodeDialog()
    {
        Text = "Change the code";
        Width = 340;
        Height = 230;
        StartPosition = FormStartPosition.CenterParent;
        FormBorderStyle = FormBorderStyle.FixedDialog;
        MaximizeBox = false;
        MinimizeBox = false;
        Font = new Font("Segoe UI", 10f);

        var newBox = new TextBox { Location = new Point(16, 16), Width = 280, UseSystemPasswordChar = true, PlaceholderText = "New code (6+ characters)" };
        var confirmBox = new TextBox { Location = new Point(16, 52), Width = 280, UseSystemPasswordChar = true, PlaceholderText = "Type it again" };
        var err = new Label { Location = new Point(16, 88), AutoSize = true, MaximumSize = new Size(280, 0), ForeColor = Color.Firebrick };
        var ok = new Button { Text = "Save", Location = new Point(16, 130), AutoSize = true, DialogResult = DialogResult.None };
        Controls.Add(newBox);
        Controls.Add(confirmBox);
        Controls.Add(err);
        Controls.Add(ok);

        ok.Click += (_, _) =>
        {
            if (newBox.Text.Length < 6 || newBox.Text != confirmBox.Text) { err.Text = "Use at least 6 characters, typed the same twice."; return; }
            NewCode = newBox.Text;
            DialogResult = DialogResult.OK;
            Close();
        };
    }
}
