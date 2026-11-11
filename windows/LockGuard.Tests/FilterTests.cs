using LockGuard.Common;
using Xunit;

namespace LockGuard.Tests;

public class FilterTests
{
    private static FilterPolicy Policy(Action<FilterPolicy>? edit = null)
    {
        var p = new FilterPolicy { On = true, Level = "moderate", Essential = new() { "mdm.example.workers.dev", "github.com" } };
        edit?.Invoke(p);
        return p;
    }

    private static readonly BlockList Adult = BlockList.Build(new[] { "badsite.com", "casino.example" });
    private static bool Cat(string h) => Adult.Blocks(h);
    private const long Now = 1_800_000_000_000;

    [Fact]
    public void BlockListMatchesHostAndParents()
    {
        Assert.True(Adult.Blocks("badsite.com"));
        Assert.True(Adult.Blocks("www.badsite.com"));
        Assert.True(Adult.Blocks("img.cdn.badsite.com."));
        Assert.False(Adult.Blocks("goodsite.com"));
        Assert.False(Adult.Blocks("com"));
        Assert.Equal(2, Adult.Count);
    }

    [Fact]
    public void BlockListRejectsBadFiles()
    {
        Assert.Throws<InvalidDataException>(() => BlockList.FromBytes(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8 }));
        Assert.Throws<InvalidDataException>(() => BlockList.FromBytes(new byte[] { (byte)'M', (byte)'D', (byte)'B', (byte)'1', 0, 0, 0, 5 }));
    }

    [Fact]
    public void HashMatchesThePhonesAndTheListBuilder()
    {
        // tools/build-blocklists.mjs: first 8 bytes of SHA-256, big-endian. sha256("example.com") starts a379a6f6eeafb9a5.
        Assert.Equal(0xa379a6f6eeafb9a5UL, BlockList.Hash("example.com"));
    }

    [Fact]
    public void ModerateBlocksCategoriesWordsAndYourList()
    {
        var p = Policy(x => { x.Block.Add("tiktok.com"); x.Words.Add("poker"); });
        Assert.Equal(Verdict.Block, HostRules.Decide("www.badsite.com", p, Cat, Now).verdict);
        Assert.Equal(Verdict.Block, HostRules.Decide("m.tiktok.com", p, Cat, Now).verdict);
        Assert.Equal(Verdict.Block, HostRules.Decide("onlinepoker.net", p, Cat, Now).verdict);
        Assert.Equal(Verdict.Allow, HostRules.Decide("wikipedia.org", p, Cat, Now).verdict);
    }

    [Fact]
    public void AllowedSitesWinAndEssentialsAreNeverBlocked()
    {
        var p = Policy(x => { x.Allow.Add("badsite.com"); x.Block.Add("github.com"); });
        Assert.Equal(Verdict.Allow, HostRules.Decide("badsite.com", p, Cat, Now).verdict);
        Assert.Equal(Verdict.Allow, HostRules.Decide("api.github.com", p, Cat, Now).verdict);
    }

    [Fact]
    public void ApprovedOnlyBlocksEverythingElse()
    {
        var p = Policy(x => { x.Level = "approved"; x.Allow.Add("chabad.org"); x.Allow.Add("google.com"); x.SafeSearch = true; });
        Assert.Equal(Verdict.Allow, HostRules.Decide("www.chabad.org", p, Cat, Now).verdict);
        Assert.Equal(Verdict.Block, HostRules.Decide("wikipedia.org", p, Cat, Now).verdict);
        Assert.Equal(Verdict.Allow, HostRules.Decide("mdm.example.workers.dev", p, Cat, Now).verdict);
        var g = HostRules.Decide("www.google.com", p, Cat, Now);
        Assert.Equal(Verdict.Safe, g.verdict);
        Assert.Equal("forcesafesearch.google.com", g.target);
        Assert.Equal(Verdict.Allow, HostRules.Decide("forcesafesearch.google.com", p, Cat, Now).verdict);
    }

    [Fact]
    public void SafeSearchAndYoutube()
    {
        var p = Policy(x => { x.SafeSearch = true; x.Youtube = "strict"; });
        Assert.Equal(("forcesafesearch.google.com"), HostRules.Decide("www.google.co.il", p, Cat, Now).target);
        Assert.Equal("strict.bing.com", HostRules.Decide("www.bing.com", p, Cat, Now).target);
        Assert.Equal("restrict.youtube.com", HostRules.Decide("m.youtube.com", p, Cat, Now).target);
        Assert.Equal(Verdict.Allow, HostRules.Decide("mail.google.com", p, Cat, Now).verdict);
        p.Youtube = "moderate";
        Assert.Equal("restrictmoderate.youtube.com", HostRules.Decide("www.youtube.com", p, Cat, Now).target);
        Assert.Single(HostRules.SafeFallback("restrict.youtube.com"));
    }

    [Fact]
    public void PausedOrOffAllowsEverything()
    {
        Assert.Equal(Verdict.Allow, HostRules.Decide("badsite.com", Policy(x => x.PauseUntil = Now + 60_000), Cat, Now).verdict);
        Assert.Equal(Verdict.Block, HostRules.Decide("badsite.com", Policy(x => x.PauseUntil = Now - 1), Cat, Now).verdict);
        Assert.Equal(Verdict.Allow, HostRules.Decide("badsite.com", Policy(x => x.On = false), Cat, Now).verdict);
    }

    [Fact]
    public void WordsFollowThePhoneRules()
    {
        var words = new List<string> { "sex", "casino", "lottery tickets" };
        Assert.Null(WordFilter.Match("visit-sussex.co.uk", words));
        Assert.Equal("sex", WordFilter.Match("sex-shop.com", words));
        Assert.Equal("casino", WordFilter.Match("onlinecasinos.net", words));
        Assert.Equal("lottery tickets", WordFilter.Match("buy lottery tickets now", words));
        Assert.Equal("lottery tickets", WordFilter.Clean("  Lottery   TICKETS "));
    }

    [Fact]
    public void DnsQuestionsAndAnswers()
    {
        var q = DnsMessage.BuildQuery(0x1234, "www.Example.com", DnsMessage.TypeA);
        Assert.Equal("www.example.com", DnsMessage.QuestionName(q));
        Assert.Equal(DnsMessage.TypeA, DnsMessage.QuestionType(q));
        var blocked = DnsMessage.Blocked(q)!;
        Assert.Equal(0x12, blocked[0]);
        Assert.Equal(0x34, blocked[1]);
        Assert.Equal(new byte[] { 0, 0, 0, 0 }, DnsMessage.ParseA(blocked).Single());
        var safe = DnsMessage.Answer(q, new[] { new byte[] { 216, 239, 38, 120 } }, 300)!;
        Assert.Equal(new byte[] { 216, 239, 38, 120 }, DnsMessage.ParseA(safe).Single());
        var aaaa = DnsMessage.BuildQuery(7, "www.youtube.com", DnsMessage.TypeAAAA);
        var empty = DnsMessage.Blocked(aaaa)!;
        Assert.Equal(0, (empty[6] << 8) | empty[7]);
        Assert.Equal(2, DnsMessage.ServFail(q)![3] & 0x0f);
        Assert.Null(DnsMessage.QuestionName(new byte[] { 1, 2, 3 }));
        var copy = DnsMessage.WithId(safe, DnsMessage.BuildQuery(0x0102, "www.example.com"));
        Assert.Equal(new byte[] { 1, 2 }, copy.Take(2).ToArray());
    }
}
