using System.Text.RegularExpressions;

namespace LockGuard.Common;

/// <summary>
/// Blocked words, the same rules as the phones (android/sitepolicy WordFilter.java): a word of 4+
/// letters also counts inside a longer word, a shorter one only as a whole word, a phrase when its
/// words appear in that order. Hebrew letters count as letters.
/// </summary>
public static class WordFilter
{
    private static readonly Regex Split = new("[^a-z0-9֐-׿]+", RegexOptions.Compiled);

    public static List<string> Tokens(string? text)
    {
        var o = new List<string>();
        if (text == null) return o;
        var t = text;
        try { t = Uri.UnescapeDataString(text.Replace("+", " ")); } catch { }
        foreach (var s in Split.Split(t.ToLowerInvariant())) if (s.Length > 0) o.Add(s);
        return o;
    }

    public static string? Clean(string? word)
    {
        var t = Tokens(word);
        if (t.Count == 0) return null;
        var w = string.Join(" ", t);
        return w.Length > 60 ? null : w;
    }

    public static string? Match(string? text, IReadOnlyList<string>? words)
    {
        if (words == null || words.Count == 0 || text == null) return null;
        var toks = Tokens(text);
        if (toks.Count == 0) return null;
        var joined = " " + string.Join(" ", toks) + " ";
        foreach (var w in words)
        {
            if (string.IsNullOrEmpty(w)) continue;
            if (w.Contains(' '))
            {
                if (joined.Contains(" " + w + " ")) return w;
                continue;
            }
            foreach (var t in toks)
                if (w.Length >= 4 ? t.Contains(w) : t == w) return w;
        }
        return null;
    }
}
