using LockGuard.Common;

namespace LockGuard.Setup;

/// <summary>
/// The only UI a person ever sees for this app. Two modes: first-run (no real code chosen yet --
/// walks through picking one, never showing or asking for the bootstrap code at all) and normal
/// (enter the code you already chose to manage what's allowed). Every change is sent to the
/// service over the pipe and re-checked there; this form holds no authority of its own.
/// </summary>
public sealed class SetupForm : Form
{
    private readonly Label _status = new() { AutoSize = true, MaximumSize = new Size(420, 0) };
    private Panel _body = new();
    private string _unlockedCode = ""; // held only in memory, for this session, to avoid re-asking on every click

    public SetupForm()
    {
        Text = "LockGuard Setup";
        Width = 480;
        Height = 420;
        StartPosition = FormStartPosition.CenterScreen;
        FormBorderStyle = FormBorderStyle.FixedDialog;
        MaximizeBox = false;

        _status.Location = new Point(20, 16);
        Controls.Add(_status);

        _body.Location = new Point(20, 60);
        _body.Size = new Size(430, 320);
        Controls.Add(_body);

        Load += async (_, _) => await RefreshAsync();
    }

    private async Task RefreshAsync()
    {
        var resp = await PipeClient.SendAsync(new PipeRequest { Action = PipeActions.GetStatus });
        if (!resp.Ok)
        {
            ShowError(resp.Message);
            return;
        }

        if (resp.IsDefaultCode)
        {
            ShowFirstRun();
        }
        else if (_unlockedCode == "")
        {
            ShowCodeGate();
        }
        else
        {
            ShowManage(resp);
        }
    }

    private void ShowError(string message)
    {
        _status.Text = "Could not reach the LockGuard service.\n\n" + message +
            "\n\nMake sure it's installed and running, then reopen this.";
        _body.Controls.Clear();
    }

    // ---------- first run: choose a code, never see or type the bootstrap one ----------
    private void ShowFirstRun()
    {
        _status.Text = "Set up LockGuard on this computer.\nChoose a code you'll use to make changes later. " +
            "Write it down somewhere safe -- there is no way to recover it if it's lost.";
        _body.Controls.Clear();

        var newCode = LabeledBox(_body, 0, "New code (6+ characters)", password: true);
        var confirm = LabeledBox(_body, 60, "Repeat it", password: true);
        var save = new Button { Text = "Save and continue", Location = new Point(0, 120), Width = 200 };
        var err = new Label { AutoSize = true, Location = new Point(0, 160), MaximumSize = new Size(420, 0), ForeColor = Color.Firebrick };
        _body.Controls.Add(save);
        _body.Controls.Add(err);

        save.Click += async (_, _) =>
        {
            if (newCode.Text.Length < 6 || newCode.Text != confirm.Text)
            {
                err.Text = "Use at least 6 characters, typed the same twice.";
                return;
            }
            save.Enabled = false;
            var resp = await PipeClient.SendAsync(new PipeRequest
            {
                Action = PipeActions.SetCode,
                Code = CodeStore.DefaultCode,
                Arg = newCode.Text,
            });
            save.Enabled = true;
            if (!resp.Ok)
            {
                err.Text = resp.Message;
                return;
            }
            _unlockedCode = newCode.Text;
            await RefreshAsync();
        };
    }

    // ---------- normal run: enter the code already chosen ----------
    private void ShowCodeGate()
    {
        _status.Text = "Enter the LockGuard code to make changes.";
        _body.Controls.Clear();

        var code = LabeledBox(_body, 0, "Code", password: true);
        var unlock = new Button { Text = "Unlock", Location = new Point(0, 60), Width = 150 };
        var err = new Label { AutoSize = true, Location = new Point(0, 100), MaximumSize = new Size(420, 0), ForeColor = Color.Firebrick };
        _body.Controls.Add(unlock);
        _body.Controls.Add(err);
        code.Focus();

        unlock.Click += async (_, _) =>
        {
            unlock.Enabled = false;
            var verify = await PipeClient.SendAsync(new PipeRequest { Action = PipeActions.VerifyCode, Code = code.Text });
            unlock.Enabled = true;
            if (!verify.Ok)
            {
                err.Text = verify.Message;
                return;
            }
            _unlockedCode = code.Text;
            await RefreshAsync();
        };
    }

    // ---------- management screen ----------
    private void ShowManage(PipeResponse status)
    {
        _status.Text = "LockGuard is " + (status.Enabled ? "ON" : "OFF") +
            ". These programs keep working with the internet; everything else on this computer doesn't.";
        _body.Controls.Clear();

        var list = new ListBox { Location = new Point(0, 0), Size = new Size(430, 140) };
        list.Items.AddRange(status.AllowedPrograms.ToArray());
        _body.Controls.Add(list);

        var add = new Button { Text = "Add a program...", Location = new Point(0, 150), Width = 150 };
        var remove = new Button { Text = "Remove selected", Location = new Point(160, 150), Width = 150 };
        _body.Controls.Add(add);
        _body.Controls.Add(remove);

        var toggle = new Button { Text = status.Enabled ? "Turn LockGuard off" : "Turn LockGuard on", Location = new Point(0, 190), Width = 200 };
        var changeCode = new Button { Text = "Change the code...", Location = new Point(0, 230), Width = 200 };
        var uninstall = new Button { Text = "Remove LockGuard entirely...", Location = new Point(0, 270), Width = 200, ForeColor = Color.Firebrick };
        _body.Controls.Add(toggle);
        _body.Controls.Add(changeCode);
        _body.Controls.Add(uninstall);

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
            var resp = await PipeClient.SendAsync(new PipeRequest { Action = PipeActions.SetEnabled, Code = _unlockedCode, Arg = status.Enabled ? "false" : "true" });
            if (!resp.Ok) MessageBox.Show(this, resp.Message, "Could not change it");
            await RefreshAsync();
        };

        changeCode.Click += (_, _) =>
        {
            using var dlg = new ChangeCodeDialog();
            if (dlg.ShowDialog(this) != DialogResult.OK) return;
            _ = ChangeCodeAsync(dlg.NewCode);
        };

        uninstall.Click += async (_, _) =>
        {
            var confirm = MessageBox.Show(this,
                "This removes LockGuard from this computer completely and restores normal internet access. Continue?",
                "Remove LockGuard", MessageBoxButtons.YesNo, MessageBoxIcon.Warning);
            if (confirm != DialogResult.Yes) return;
            var resp = await PipeClient.SendAsync(new PipeRequest { Action = PipeActions.Uninstall, Code = _unlockedCode });
            MessageBox.Show(this, resp.Ok ? "Removing LockGuard. This window will now close." : resp.Message, "LockGuard");
            if (resp.Ok) Close();
        };
    }

    private async Task ChangeCodeAsync(string newCode)
    {
        var resp = await PipeClient.SendAsync(new PipeRequest { Action = PipeActions.SetCode, Code = _unlockedCode, Arg = newCode });
        if (!resp.Ok)
        {
            MessageBox.Show(this, resp.Message, "Could not change the code");
            return;
        }
        _unlockedCode = newCode;
        MessageBox.Show(this, "Code changed.", "LockGuard");
    }

    private static TextBox LabeledBox(Control parent, int y, string label, bool password)
    {
        parent.Controls.Add(new Label { Text = label, AutoSize = true, Location = new Point(0, y) });
        var box = new TextBox { Location = new Point(0, y + 20), Width = 260, UseSystemPasswordChar = password };
        parent.Controls.Add(box);
        return box;
    }
}

/// <summary>
/// Pick list for "Add a program": installed programs found from Start Menu shortcuts
/// (InstalledPrograms), searchable, with a manual file-browse fallback for anything not found
/// there (portable apps, etc.) -- instead of making everyone start with a raw file path.
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

        var search = new TextBox { Location = new Point(16, 16), Width = 370 };
        var list = new ListBox { Location = new Point(16, 46), Width = 370, Height = 260 };
        var browse = new Button { Text = "Can't find it? Browse for the program...", Location = new Point(16, 316), Width = 370 };
        var buttons = new FlowLayoutPanel { Location = new Point(16, 350), Width = 370, Height = 30, FlowDirection = FlowDirection.RightToLeft };
        var ok = new Button { Text = "Add" };
        var cancel = new Button { Text = "Cancel" };
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

        if (_all.Count == 0)
            list.Items.Add("(No programs found automatically -- use Browse below.)");

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

/// <summary>Small modal for "Change the code" so the current screen doesn't need extra fields hanging around.</summary>
internal sealed class ChangeCodeDialog : Form
{
    public string NewCode { get; private set; } = "";

    public ChangeCodeDialog()
    {
        Text = "Change the code";
        Width = 320;
        Height = 180;
        StartPosition = FormStartPosition.CenterParent;
        FormBorderStyle = FormBorderStyle.FixedDialog;
        MaximizeBox = false;
        MinimizeBox = false;

        var newBox = new TextBox { Location = new Point(16, 16), Width = 260, UseSystemPasswordChar = true };
        var confirmBox = new TextBox { Location = new Point(16, 46), Width = 260, UseSystemPasswordChar = true };
        var err = new Label { Location = new Point(16, 76), AutoSize = true, MaximumSize = new Size(260, 0), ForeColor = Color.Firebrick };
        var ok = new Button { Text = "Save", Location = new Point(16, 106), DialogResult = DialogResult.None };
        Controls.Add(newBox);
        Controls.Add(confirmBox);
        Controls.Add(err);
        Controls.Add(ok);

        ok.Click += (_, _) =>
        {
            if (newBox.Text.Length < 6 || newBox.Text != confirmBox.Text)
            {
                err.Text = "Use at least 6 characters, typed the same twice.";
                return;
            }
            NewCode = newBox.Text;
            DialogResult = DialogResult.OK;
            Close();
        };
    }
}
