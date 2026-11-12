using System.Collections.Generic;
using System.Linq;
using System.Text.Json;

namespace LockGuard.Common;

/// <summary>
/// Which category lists a filter level needs, from the blocklists manifest (the same one the phones
/// use), plus the ad list when "Block ads" is on.
/// </summary>
public static class ListChoice
{
    public static List<string> Names(string manifestJson, string level, bool ads)
    {
        using var doc = JsonDocument.Parse(manifestJson);
        var root = doc.RootElement;
        var names = root.GetProperty("tiers").GetProperty(level).EnumerateArray().Select(x => x.GetString()!).ToList();
        if (ads && root.GetProperty("categories").TryGetProperty("ads", out _) && !names.Contains("ads")) names.Add("ads");
        return names;
    }
}
