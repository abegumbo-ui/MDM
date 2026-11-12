using System.Text.Json.Serialization;

namespace LockGuard.Common;

/// <summary>
/// Screen time on the computer, as set on the dashboard: a daily limit for the child's (standard)
/// Windows accounts, a bedtime, and extra minutes given for one day. Administrator accounts are never
/// limited. Everything uses the computer's own clock.
/// </summary>
public sealed class ScreenTimeRules
{
    /// <summary>Minutes a day; 0 = no limit.</summary>
    [JsonPropertyName("limit")] public int Limit { get; set; }
    /// <summary>"HH:MM" or "" for no bedtime.</summary>
    [JsonPropertyName("bedFrom")] public string BedFrom { get; set; } = "";
    [JsonPropertyName("bedTo")] public string BedTo { get; set; } = "";
    /// <summary>Extra minutes, for the day this was given on (ms since 1970; compared by the computer's own date).</summary>
    [JsonPropertyName("extraMinutes")] public int ExtraMinutes { get; set; }
    [JsonPropertyName("extraAt")] public long ExtraAt { get; set; }

    public bool Any => Limit > 0 || (Minute(BedFrom) >= 0 && Minute(BedTo) >= 0);

    public enum State { Allowed, WarnSoon, Over, Bedtime }

    /// <summary>"HH:MM" → minutes after midnight, or -1.</summary>
    public static int Minute(string s)
    {
        if (string.IsNullOrWhiteSpace(s)) return -1;
        var p = s.Trim().Split(':');
        if (p.Length != 2 || !int.TryParse(p[0], out var h) || !int.TryParse(p[1], out var m)) return -1;
        if (h < 0 || h > 23 || m < 0 || m > 59) return -1;
        return h * 60 + m;
    }

    /// <summary>Is this minute of the day inside bedtime (which may run past midnight)?</summary>
    public bool InBedtime(int minuteOfDay)
    {
        int from = Minute(BedFrom), to = Minute(BedTo);
        if (from < 0 || to < 0 || from == to) return false;
        return from < to ? minuteOfDay >= from && minuteOfDay < to : minuteOfDay >= from || minuteOfDay < to;
    }

    /// <summary>Today's limit including extra minutes given today (0 = no limit).</summary>
    public int LimitToday(DateTime localNow)
    {
        if (Limit <= 0) return 0;
        var extra = 0;
        if (ExtraMinutes > 0 && ExtraAt > 0 && DateTimeOffset.FromUnixTimeMilliseconds(ExtraAt).ToLocalTime().Date == localNow.Date) extra = ExtraMinutes;
        return Limit + extra;
    }

    /// <summary>What applies now for a limited account that has used <paramref name="usedSeconds"/> today.</summary>
    public State Decide(DateTime localNow, long usedSeconds)
    {
        var now = localNow.Hour * 60 + localNow.Minute;
        if (InBedtime(now)) return State.Bedtime;
        var limit = LimitToday(localNow);
        if (limit > 0)
        {
            var left = limit * 60L - usedSeconds;
            if (left <= 0) return State.Over;
            if (left <= 5 * 60) return State.WarnSoon;
        }
        // Bedtime starting within 5 minutes also gets a warning.
        int from = Minute(BedFrom), to = Minute(BedTo);
        if (from >= 0 && to >= 0 && from != to)
        {
            var until = (from - now + 1440) % 1440;
            if (until > 0 && until <= 5) return State.WarnSoon;
        }
        return State.Allowed;
    }
}
