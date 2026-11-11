using System.Text.RegularExpressions;

namespace LockGuard.Common;

public enum Verdict { Allow, Block, Safe }

/// <summary>
/// What the filter does with one website name. Pure, so it is tested on a computer (LockGuard.Tests).
/// Order: no filter / paused → allow. Allowed sites and essentials → allow (SafeSearch still applies).
/// "Approved sites only" → block everything else. SafeSearch / YouTube → the search engine's safe
/// address. Then your blocked sites, the category lists, and blocked words.
/// </summary>
public static class HostRules
{
    private static readonly Regex Google = new(@"^www\.google\.(com|[a-z]{2,3}|com?\.[a-z]{2})$", RegexOptions.Compiled);

    /// <summary>Same as the phones' SafeSearchDns.target.</summary>
    public static string? SafeTarget(string host, bool safeSearch, string? youtube)
    {
        var h = host.ToLowerInvariant();
        if (safeSearch)
        {
            if (Google.IsMatch(h)) return "forcesafesearch.google.com";
            if (h == "www.bing.com") return "strict.bing.com";
            if (h == "duckduckgo.com" || h == "www.duckduckgo.com") return "safe.duckduckgo.com";
        }
        if (youtube == "strict" || youtube == "moderate")
        {
            if (h is "www.youtube.com" or "m.youtube.com" or "youtubei.googleapis.com" or "youtube.googleapis.com" or "www.youtube-nocookie.com")
                return youtube == "strict" ? "restrict.youtube.com" : "restrictmoderate.youtube.com";
        }
        return null;
    }

    /// <summary>Fixed addresses if the safe name itself cannot be looked up (same as the phones).</summary>
    public static List<byte[]> SafeFallback(string target) => target switch
    {
        "forcesafesearch.google.com" or "restrict.youtube.com" => new() { new byte[] { 216, 239, 38, 120 } },
        "restrictmoderate.youtube.com" => new() { new byte[] { 216, 239, 38, 119 } },
        "strict.bing.com" => new() { new byte[] { 204, 79, 197, 220 } },
        _ => new(),
    };

    /// <summary>The host is the domain, or under it ("a.b.example.com" matches "example.com").</summary>
    public static bool Under(string host, IEnumerable<string> domains)
    {
        foreach (var raw in domains)
        {
            var d = raw.Trim().ToLowerInvariant().TrimEnd('.');
            if (d.StartsWith("www.")) d = d[4..];
            if (d.Length == 0) continue;
            if (host == d || host.EndsWith("." + d)) return true;
        }
        return false;
    }

    public static (Verdict verdict, string? target) Decide(string? hostIn, FilterPolicy p, Func<string, bool> categoryBlocks, long nowMs)
    {
        if (hostIn == null || !p.On || p.Paused(nowMs)) return (Verdict.Allow, null);
        var host = hostIn.ToLowerInvariant().TrimEnd('.');
        bool allowed = Under(host, p.Allow) || Under(host, p.Essential);
        var safe = SafeTarget(host, p.SafeSearch, p.Youtube);
        if (p.Level == "approved" && !allowed)
        {
            // The search engines' own "safe" names stay reachable so an allowed Google keeps working.
            if (Under(host, new[] { "forcesafesearch.google.com", "strict.bing.com", "safe.duckduckgo.com", "restrict.youtube.com", "restrictmoderate.youtube.com" })) return (Verdict.Allow, null);
            return (Verdict.Block, null);
        }
        if (safe != null) return (Verdict.Safe, safe);
        if (allowed) return (Verdict.Allow, null);
        if (Under(host, p.Block)) return (Verdict.Block, null);
        if ((p.Level == "moderate" || p.Level == "strict") && categoryBlocks(host)) return (Verdict.Block, null);
        if (WordFilter.Match(host, p.Words) != null) return (Verdict.Block, null);
        return (Verdict.Allow, null);
    }
}
