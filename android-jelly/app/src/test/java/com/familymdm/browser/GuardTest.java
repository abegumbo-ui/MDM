package com.familymdm.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.webkit.WebView;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Every rule the family dashboard can set for the new Browser, checked without a phone
 * (Robolectric). Run by browser-jelly.yml on every build.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class GuardTest {
    private Context c;

    @Before
    public void setUp() throws Exception {
        c = ApplicationProvider.getApplicationContext();
        BrowserState.prefs(c).edit().clear().commit();
        Guard.forgetCache();
    }

    /** Connected to a dashboard, with these allowed sites, and every switch at its default. */
    private void connected(String tier, String... allowedHosts) throws Exception {
        BrowserState.setOnline(c, "https://dashboard.invalid", "id.secret");
        JSONArray sites = new JSONArray();
        for (String h : allowedHosts) sites.put(new JSONObject().put("type", "domain").put("url", "https://" + h).put("host", h).put("label", h));
        BrowserState.setCachedSites(c, sites);
        FilterStore.setPolicy(c, tier, "");
        FilterStore.setSwitches(c, true, true, true);
        FilterStore.setBlockedWords(c, null);
        FilterStore.setBlockImageSearch(c, false);
        FilterStore.setYoutubeMode(c, "channels");
        Guard.forgetCache();
    }

    @Test
    public void notConnected_nothingOpens_andTheConnectPageAsksForACode() {
        assertEquals(Guard.Mode.UNCONFIGURED, Guard.mode(c));
        Guard.Decision d = Guard.check(c, "https://example.com/", null);
        assertFalse(d.allow);
        assertTrue(d.html.contains("Connect this Browser"));
        assertTrue(d.html.contains("Add device"));
        assertNull("no searching before connecting", Guard.searchUrl(c, "cats"));
    }

    @Test
    public void approvedSitesOnly_listedSitesOpen_othersOfferAskForAccess() throws Exception {
        connected("off", "khanacademy.org");
        assertTrue(Guard.check(c, "https://www.khanacademy.org/math", null).allow);
        Guard.Decision d = Guard.check(c, "https://example.com/", null);
        assertFalse(d.allow);
        assertTrue(d.html.contains("Ask for access"));
        assertTrue(d.html.contains("mdmguard://ask?u="));
        assertTrue("the ask page has a message box and sends to the dashboard", Guard.askPage("https://example.com/").contains("mdmguard://request?u=") && Guard.askPage(null).contains("Message to your parent"));
        assertNull("no searching on approved-only", Guard.searchUrl(c, "cats"));
    }

    @Test
    public void openLevel_everySiteOpens() throws Exception {
        connected("open");
        assertTrue(Guard.check(c, "https://example.com/", null).allow);
        assertTrue(Guard.check(c, "https://news.example.org/a/b?c=d", null).allow);
    }

    @Test
    public void builtInAdultList_onByDefault_andCanBeSwitchedOff() throws Exception {
        connected("open");
        assertFalse(Guard.check(c, "https://www.pornhub.com/", null).allow);
        FilterStore.setSwitches(c, true, false, true);
        assertTrue(Guard.check(c, "https://www.pornhub.com/", null).allow);
    }

    @Test
    public void search_isGoogleWithSafeSearch_andEachSwitchWorks() throws Exception {
        connected("open");
        String s = Guard.searchUrl(c, "solar system");
        assertNotNull(s);
        assertTrue(s.startsWith("https://www.google.com/search?"));
        assertTrue(s.contains("safe=active"));
        FilterStore.setSwitches(c, false, true, true); // SafeSearch off
        assertFalse(Guard.searchUrl(c, "solar system").contains("safe=active"));
        FilterStore.setSwitches(c, true, true, false); // searching off
        assertNull(Guard.searchUrl(c, "solar system"));
    }

    @Test
    public void safeSearch_isForcedOnGooglePages_evenIfTheAddressTurnsItOff() throws Exception {
        connected("open");
        assertTrue(Guard.safeUrl(c, "https://www.google.com/search?q=x&safe=off").contains("safe=active"));
        assertFalse(Guard.safeUrl(c, "https://www.google.com/search?q=x&safe=off").contains("safe=off"));
        assertEquals("other sites are untouched", "https://example.com/?safe=off", Guard.safeUrl(c, "https://example.com/?safe=off"));
    }

    @Test
    public void blockedWords_blockPagesAndSearches() throws Exception {
        connected("open");
        FilterStore.setBlockedWords(c, new String[]{"casino"});
        assertFalse(Guard.check(c, "https://www.google.com/search?q=best+casino", null).allow);
        assertTrue(Guard.check(c, "https://www.google.com/search?q=museum", null).allow);
    }

    @Test
    public void imageSearch_canBeTurnedOff() throws Exception {
        connected("open");
        String images = "https://www.google.com/search?q=cats&tbm=isch";
        assertTrue(Guard.check(c, images, null).allow);
        FilterStore.setBlockImageSearch(c, true);
        assertFalse(Guard.check(c, images, null).allow);
    }

    @Test
    public void restrictedYoutube_homeBecomesSearch_videosOpen_shortsDoNot() throws Exception {
        connected("off");
        FilterStore.setYoutubeMode(c, "restricted");
        assertTrue("YouTube's own home page opens (its feed is emptied by the script)", Guard.check(c, "https://m.youtube.com/", null).allow);
        assertTrue(Guard.afterLoadScript(c, "https://m.youtube.com/").contains("mdm-yt-home"));
        assertTrue(Guard.check(c, "https://www.youtube.com/watch?v=dQw4w9WgXcQ", null).allow);
        assertFalse(Guard.check(c, "https://www.youtube.com/shorts/abcdefghijk", null).allow);
        assertNotNull("feeds are hidden after loading", Guard.afterLoadScript(c, "https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
    }

    @Test
    public void appDownloads_areRefused_otherFilesAreFine() {
        assertFalse(Guard.downloadAllowed("https://x.test/game.apk", null));
        assertFalse(Guard.downloadAllowed("https://x.test/file", "application/vnd.android.package-archive"));
        assertTrue(Guard.downloadAllowed("https://x.test/homework.pdf", "application/pdf"));
    }

    @Test
    public void askForAccess_isQueuedForTheDashboard() throws Exception {
        connected("off");
        WebView v = new WebView(c);
        assertTrue(Guard.handleAction(c, v, "mdmguard://request?u=" + android.net.Uri.encode("https://example.com/page")));
        JSONArray queued = BrowserState.peekSiteRequests(c);
        assertEquals(1, queued.length());
        assertEquals("https://example.com/page", queued.getJSONObject(0).getString("url"));
        assertFalse("ordinary addresses are not our buttons", Guard.handleAction(c, v, "https://example.com/"));
    }

    @Test
    public void homePage_showsTheStartPage_whenItWouldNotOpen() throws Exception {
        connected("off", "khanacademy.org");
        assertTrue(Guard.useStartPage(c, "https://www.google.com/", "https://www.google.com/"));
        assertTrue(Guard.startPage(c).contains("khanacademy.org"));
        connected("open");
        assertFalse(Guard.useStartPage(c, "https://www.google.com/", "https://www.google.com/"));
    }

    @Test
    public void ourOwnPages_neverContainRawAddresses() throws Exception {
        connected("off");
        Guard.Decision d = Guard.check(c, "https://example.com/<script>alert(1)</script>", null);
        assertFalse(d.html.contains("<script>alert"));
    }

    @Test
    public void noPictureSites_turnOffPicturesOnThatSiteOnly() throws Exception {
        connected("open");
        FilterStore.setNoPictureSites(c, new String[]{"amazon.com"});
        Guard.Decision amazon = Guard.check(c, "https://www.amazon.com/s?k=shoes", null);
        assertTrue("the site still opens", amazon.allow);
        assertTrue("without pictures", amazon.blockImages);
        assertFalse("other sites keep their pictures", Guard.check(c, "https://example.com/", null).blockImages);
        assertFalse("look-alike names are not caught", Guard.check(c, "https://notamazon.com/", null).blockImages);
    }

    @Test
    public void videoCovering_onlyWithCoverSkin() throws Exception {
        connected("open");
        FilterStore.setPictureGuard(c, "nudity");
        assertNull(Guard.videoScript(c));
        FilterStore.setPictureGuard(c, "skin");
        String js = Guard.videoScript(c);
        assertNotNull(js);
        assertTrue(js.contains("requestVideoFrameCallback") && js.contains("texImage2D"));
    }

    @Test
    public void youtube_onlyWhatIAllow_holdsEvenOnTheOpenLevel_andOffBlocksIt() throws Exception {
        connected("open");
        FilterStore.setYoutubeMode(c, "open");
        assertTrue(Guard.check(c, "https://m.youtube.com/", null).allow);
        FilterStore.setYoutubeMode(c, "channels");
        assertTrue("its home page opens (it lists what is allowed)", Guard.check(c, "https://m.youtube.com/", null).allow);
        assertTrue("searching opens, to find videos to ask for (they show covered, with Ask for access)", Guard.check(c, "https://m.youtube.com/results?search_query=games", null).allow);
        Guard.Decision d = Guard.check(c, "https://m.youtube.com/watch?v=abcdefghijk", null);
        assertFalse("a video that is not allowed stays closed, even though the web is open", d.allow);
        assertTrue(d.html.contains("Ask for access"));
        FilterStore.setYoutubeSearch(c, false);
        assertFalse("search switched off on the dashboard: blocked", Guard.check(c, "https://m.youtube.com/results?search_query=games", null).allow);
        FilterStore.setYoutubeSearch(c, true);
        assertTrue("other sites still open", Guard.check(c, "https://example.com/", null).allow);
        FilterStore.setYoutubeMode(c, "blocked");
        assertFalse(Guard.check(c, "https://www.youtube.com/watch?v=dQw4w9WgXcQ", null).allow);
    }

    @Test
    public void youtube_allowedChannelsAndVideos_showOnTheHomePage_andAVideoOpensWithAnyExtras() throws Exception {
        connected("off");
        JSONArray sites = new JSONArray();
        sites.put(new JSONObject().put("type", "youtube").put("kind", "channel").put("id", "@nasa").put("url", "https://www.youtube.com/@nasa")
                .put("label", "NASA").put("thumb", "https://yt3.ggpht.com/abc=s176"));
        sites.put(new JSONObject().put("type", "exact").put("url", "https://www.youtube.com/watch?v=dQw4w9WgXcQ").put("label", "A song").put("thumb", "https://i.ytimg.com/vi/dQw4w9WgXcQ/mqdefault.jpg"));
        BrowserState.setCachedSites(c, sites);
        Guard.forgetCache();
        FilterStore.setYoutubeMode(c, "channels");
        assertTrue("the home page opens", Guard.check(c, "https://m.youtube.com/", null).allow);
        Guard.Decision vid = Guard.check(c, "https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=42s&si=abc", null);
        assertTrue("an allowed video, with a start time and a share code", vid.allow);
        assertTrue("an allowed video shows as it is (no covering)", vid.trusted && !vid.blockImages);
        assertTrue("an allowed channel's page too", Guard.check(c, "https://m.youtube.com/@nasa", null).trusted);
        assertFalse("other pages are not", Guard.check(c, "https://m.youtube.com/", null).trusted);
        assertTrue("short links too", Guard.check(c, "https://youtu.be/dQw4w9WgXcQ?si=x", null).allow);
        assertFalse("another video is not", Guard.check(c, "https://m.youtube.com/watch?v=aaaaaaaaaaa", null).allow);
        String js = Guard.afterLoadScript(c, "https://m.youtube.com/");
        assertNotNull(js);
        assertTrue("the home page lists them", js.contains("\"@nasa\"") && js.contains("NASA") && js.contains("dQw4w9WgXcQ") && js.contains("A song"));
        assertTrue(js.contains("mdm-yt-home"));
        assertFalse("no data placeholder left", js.contains("__MDM_DATA__"));
    }

    @Test
    public void blockAds_youtubeAdsAndAdServers_andTheOffMessage() throws Exception {
        connected("open");
        assertNull("off by default", Guard.adBlockScript(c));
        FilterStore.setBlockAds(c, true);
        String js = Guard.adBlockScript(c);
        assertTrue(js != null && js.contains("__mdmNoAds") && js.contains("adPlacements"));
        assertTrue(com.familymdm.agent.sitepolicy.YoutubeScripts.isAdRequest("https://googleads.g.doubleclick.net/pagead/id"));
        assertTrue(com.familymdm.agent.sitepolicy.YoutubeScripts.isAdRequest("https://www.youtube.com/api/stats/ads?ver=2"));
        assertTrue(com.familymdm.agent.sitepolicy.YoutubeScripts.isAdRequest("https://m.youtube.com/pagead/viewthroughconversion/1"));
        assertFalse("the video itself is not an ad", com.familymdm.agent.sitepolicy.YoutubeScripts.isAdRequest("https://rr1---sn-abc.googlevideo.com/videoplayback?id=1"));
        assertFalse(com.familymdm.agent.sitepolicy.YoutubeScripts.isAdRequest("https://m.youtube.com/watch?v=dQw4w9WgXcQ"));
        FilterStore.setYoutubeMode(c, "blocked");
        FilterStore.setYoutubeOffMessage(c, "Homework first, then YouTube.");
        Guard.Decision d = Guard.check(c, "https://m.youtube.com/", null);
        assertFalse(d.allow);
        assertTrue("the parent's own message", d.html.contains("Homework first, then YouTube."));
        assertTrue(d.html.contains("Ask to open YouTube"));
    }

    @Test
    public void badSearch_onStrict_saysYoureBetterThanThat() throws Exception {
        connected("strict");
        Guard.Decision d = Guard.check(c, "https://www.google.com/search?q=hot+girls", null);
        assertFalse(d.allow);
        assertTrue(d.html.contains("better than that"));
        connected("moderate");
        assertFalse("not Strict: the built-in list does not apply (the filter decides)", Guard.check(c, "https://www.google.com/search?q=hot+girls", null).html != null
                && Guard.check(c, "https://www.google.com/search?q=hot+girls", null).html.contains("better than that"));
        FilterStore.setBlockedWords(c, new String[]{"casino"});
        assertTrue("a blocked word, any level", Guard.check(c, "https://www.google.com/search?q=casino+games", null).html.contains("better than that"));
    }

    @Test
    public void adsCovered_onSitesThatRefuseAdBlocking_andTheMenuSwitchesBothWays() throws Exception {
        connected("open");
        assertNull("ads not blocked: nothing to do", Guard.adsMenuLabel(c, "news.example.com"));
        FilterStore.setBlockAds(c, true);
        assertEquals("Show ads on this page (covered)", Guard.adsMenuLabel(c, "news.example.com"));
        String watcher = Guard.adsPageScript(c, "news.example.com");
        assertTrue("other sites get the watcher", watcher != null && watcher.contains("MdmAds"));
        assertFalse("only the page's own site can be switched", Guard.noticedAdBlockWall(c, "evil.example.org", "news.example.com"));
        assertTrue(Guard.noticedAdBlockWall(c, "news.example.com", "news.example.com"));
        assertTrue(FilterStore.adsCovered(c, "news.example.com"));
        assertTrue("covered: the black-out script", Guard.adsPageScript(c, "news.example.com").contains("brightness(0)"));
        assertEquals("Block ads on this page", Guard.adsMenuLabel(c, "news.example.com"));
        Guard.toggleAdsHere(c, "news.example.com");
        assertFalse("switched back from the menu", FilterStore.adsCovered(c, "news.example.com"));
        FilterStore.setCoveredFromDashboard(c, new String[]{"shop.example"});
        assertTrue("from the dashboard, with its parts", FilterStore.adsCovered(c, "www.shop.example"));
        assertFalse(FilterStore.adsCovered(c, "notshop.example"));
    }

    @Test
    public void youtube_videosThatAreNotAllowed_areCovered_onlyInTheLimitedModes() throws Exception {
        String c1 = Guard.coverScript(c, "channels");
        assertTrue("the cover script, told what is allowed", c1 != null && c1.contains("mdm-cov") && c1.contains("\"cover\":\"channels\""));
        String c2 = Guard.coverScript(c, "restricted");
        assertTrue(c2 != null && c2.contains("\"cover\":\"restricted\""));
    }

    private JSONObject site(String host, long until) throws Exception {
        JSONObject s = new JSONObject().put("type", "domain").put("url", "https://" + host).put("host", host).put("label", host);
        if (until > 0) s.put("until", until);
        return s;
    }

    @Test
    public void allowList_endEarly_blocksRightAway_isListed_andTheDashboardIsTold() throws Exception {
        connected("off");
        long until = System.currentTimeMillis() + 3_600_000L;
        JSONArray sites = new JSONArray().put(site("games.example", until)).put(site("always.example", 0));
        BrowserState.setCachedSites(c, sites);
        Guard.forgetCache();
        assertTrue("allowed for an hour", Guard.check(c, "https://games.example/play", null).allow);
        AllowList.Rows r = AllowList.rows(c, Guard.allSites(c));
        assertEquals(1, r.live.size());
        assertEquals(1, r.always.size());
        assertTrue(AllowList.status(c, r.live.get(0)).contains("left"));
        AllowList.endEarly(c, sites.getJSONObject(0));
        Guard.forgetCache();
        assertFalse("ended early: blocked right away", Guard.check(c, "https://games.example/play", null).allow);
        assertTrue("the rest is untouched", Guard.check(c, "https://always.example/", null).allow);
        r = AllowList.rows(c, Guard.allSites(c));
        assertEquals("listed under ended, to ask again", 1, r.done.size());
        assertEquals("ended", r.done.get(0).state);
        assertEquals("the dashboard is told", 1, AllowList.endedForServer(c).length());
        // Allowed again on the dashboard (a new end time): it opens again.
        sites.getJSONObject(0).put("until", until + 60_000);
        BrowserState.setCachedSites(c, sites);
        Guard.forgetCache();
        assertTrue(Guard.check(c, "https://games.example/play", null).allow);
    }

    @Test
    public void allowList_timeIsUp_blocked_andListedForADay() throws Exception {
        connected("off");
        long soon = System.currentTimeMillis() + 3_600_000L;
        BrowserState.setCachedSites(c, new JSONArray().put(site("games.example", soon)));
        Guard.forgetCache();
        AllowList.rows(c, Guard.allSites(c)); // seen while it was running
        long past = System.currentTimeMillis() - 60_000L;
        BrowserState.setCachedSites(c, new JSONArray().put(site("games.example", past)));
        Guard.forgetCache();
        assertFalse("time is up: blocked", Guard.check(c, "https://games.example/", null).allow);
        AllowList.Rows r = AllowList.rows(c, Guard.allSites(c));
        assertEquals(1, r.done.size());
        assertEquals("over", r.done.get(0).state);
        assertEquals("2 h 5 min left", AllowList.left((2 * 60 + 5) * 60_000L));
        assertEquals("1 day 3 h left", AllowList.left((27 * 60) * 60_000L));
    }
}
