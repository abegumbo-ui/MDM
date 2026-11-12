using LockGuard.Common;
using Xunit;

namespace LockGuard.Tests;

public class ScreenTimeTests
{
    private static readonly DateTime Noon = new(2026, 11, 12, 12, 0, 0, DateTimeKind.Local);

    [Fact]
    public void NoRulesMeansAllowed()
    {
        var r = new ScreenTimeRules();
        Assert.False(r.Any);
        Assert.Equal(ScreenTimeRules.State.Allowed, r.Decide(Noon, 99999));
    }

    [Fact]
    public void DailyLimitWarnsThenEnds()
    {
        var r = new ScreenTimeRules { Limit = 60 };
        Assert.Equal(ScreenTimeRules.State.Allowed, r.Decide(Noon, 50 * 60));
        Assert.Equal(ScreenTimeRules.State.WarnSoon, r.Decide(Noon, 56 * 60));
        Assert.Equal(ScreenTimeRules.State.Over, r.Decide(Noon, 60 * 60));
    }

    [Fact]
    public void ExtraTimeCountsOnlyOnTheDayItWasGiven()
    {
        var today = new DateTimeOffset(Noon).ToUnixTimeMilliseconds();
        var r = new ScreenTimeRules { Limit = 60, ExtraMinutes = 30, ExtraAt = today };
        Assert.Equal(ScreenTimeRules.State.Allowed, r.Decide(Noon, 70 * 60));
        Assert.Equal(ScreenTimeRules.State.Over, r.Decide(Noon.AddDays(1), 70 * 60));
    }

    [Fact]
    public void BedtimePastMidnight()
    {
        var r = new ScreenTimeRules { BedFrom = "21:30", BedTo = "07:00" };
        Assert.True(r.Any);
        Assert.Equal(ScreenTimeRules.State.Bedtime, r.Decide(Noon.Date.AddHours(23), 0));
        Assert.Equal(ScreenTimeRules.State.Bedtime, r.Decide(Noon.Date.AddHours(6), 0));
        Assert.Equal(ScreenTimeRules.State.Allowed, r.Decide(Noon.Date.AddHours(7), 0));
        Assert.Equal(ScreenTimeRules.State.WarnSoon, r.Decide(Noon.Date.AddHours(21).AddMinutes(27), 0));
        Assert.Equal(ScreenTimeRules.State.Allowed, r.Decide(Noon, 0));
    }

    [Fact]
    public void BadTimesAreIgnored()
    {
        var r = new ScreenTimeRules { BedFrom = "25:00", BedTo = "07:00" };
        Assert.False(r.Any);
        Assert.Equal(ScreenTimeRules.State.Allowed, r.Decide(Noon.Date.AddHours(23), 0));
    }
}
