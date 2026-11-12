using System.Drawing.Imaging;
using LockGuard.Common;

namespace LockGuard.Setup;

/// <summary>
/// "LockGuard.Setup.exe /screenshot ticket": started by the LockGuard service when the parent asks
/// for a picture of the screen on the dashboard. Takes one picture of every screen, gives it to the
/// service, then tells the child for a few seconds that it was sent.
/// </summary>
internal static class Screenshot
{
    public static void Run(string ticket)
    {
        string? b64 = null;
        try
        {
            var area = SystemInformation.VirtualScreen;
            using var full = new Bitmap(area.Width, area.Height);
            using (var g = Graphics.FromImage(full)) g.CopyFromScreen(area.Left, area.Top, 0, 0, area.Size);
            var scale = Math.Min(1.0, 1600.0 / Math.Max(1, area.Width));
            using var small = new Bitmap(full, new Size(Math.Max(1, (int)(area.Width * scale)), Math.Max(1, (int)(area.Height * scale))));
            var jpeg = ImageCodecInfo.GetImageEncoders().First(e => e.FormatID == ImageFormat.Jpeg.Guid);
            using var ps = new EncoderParameters(1);
            ps.Param[0] = new EncoderParameter(System.Drawing.Imaging.Encoder.Quality, 60L);
            using var ms = new MemoryStream();
            small.Save(ms, jpeg, ps);
            b64 = Convert.ToBase64String(ms.ToArray());
        }
        catch
        {
            // The screen could not be read (for example the lock screen is showing): nothing is sent.
        }
        if (b64 is null) return;
        var sent = PipeClient.SendAsync(new PipeRequest { Action = PipeActions.Screenshot, Code = ticket, Arg = b64 }, 15000).GetAwaiter().GetResult();
        if (!sent.Ok) return;
        using var note = new NotifyIcon { Icon = SystemIcons.Information, Visible = true, Text = "LockGuard" };
        note.ShowBalloonTip(6000, "LockGuard", "A picture of this screen was sent to your parent.", ToolTipIcon.Info);
        var until = DateTime.UtcNow.AddSeconds(7);
        while (DateTime.UtcNow < until) { Application.DoEvents(); Thread.Sleep(100); }
        note.Visible = false;
    }
}
