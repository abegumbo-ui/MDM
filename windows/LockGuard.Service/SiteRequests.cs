namespace LockGuard.Service;

/// <summary>Websites asked for in the LockGuard window, waiting for the next check-in (at most 20).</summary>
public static class SiteRequests
{
    private static readonly List<object> Pending = new();

    public static void Add(string url)
    {
        lock (Pending)
        {
            if (Pending.Count >= 20) Pending.RemoveAt(0);
            Pending.Add(new { url, at = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() });
        }
    }

    /// <summary>Takes everything waiting (put back with Restore if the check-in fails).</summary>
    public static List<object> Take()
    {
        lock (Pending) { var all = new List<object>(Pending); Pending.Clear(); return all; }
    }

    public static void Restore(List<object> items)
    {
        lock (Pending) { Pending.InsertRange(0, items); while (Pending.Count > 20) Pending.RemoveAt(0); }
    }
}
