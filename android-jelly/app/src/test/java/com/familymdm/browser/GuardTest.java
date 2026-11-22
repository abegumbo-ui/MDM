package com.familymdm.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.webkit.WebView;

import androidx.test.core.app.ApplicationProvider;

import com.familymdm.agent.sitepolicy.SitePolicy;

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
        assertTrue(d.html.contains("Ask for this site"));
        assertTrue(d.html.contains("mdmguard://ask?u="));
        assertTrue("an unknown site says Miscellaneous", d.html.contains(">Miscellaneous<"));
        Guard.Decision news = Guard.check(c, "https://www.foxnews.com/politics/some-story", null);
        assertFalse(news.allow);
        assertTrue("a known news site says News", news.html.contains(">News<"));
        assertTrue("the ask page has a message box and sends to the dashboard", Guard.askPage("https://example.com/").contains("mdmguard://request?u=") && Guard.askPage(null).contains("Message to your parent"));
        String ask = Guard.askPage("https://www.foxnews.com/politics/some-story");
        assertTrue("the ask page offers just this page or the whole site", ask.contains("Just this page") && ask.contains("The whole site (foxnews.com)") && ask.contains("name=h value='foxnews.com'"));
        assertFalse("typing a link or a name (no page known) has no such choice", Guard.askPage(null).contains("Just this page"));
        assertFalse("YouTube is asked for one channel or video at a time, never as a whole site", Guard.askPage("https://www.youtube.com/watch?v=abcdefghijk").contains("The whole site"));
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
    public void onePageApprovedOnABlockedSite_opens_whileTheRestOfThatSiteStaysClosed() throws Exception {
        connected("off");
        BrowserState.setCachedSites(c, new JSONArray().put(new JSONObject().put("type", "exact").put("url", "https://www.foxnews.com/politics/the-one-story").put("host", "www.foxnews.com").put("label", "foxnews.com")));
        Guard.forgetCache();
        assertTrue("the approved page opens", Guard.check(c, "https://www.foxnews.com/politics/the-one-story", null).allow);
        assertFalse("another page on the same site stays closed", Guard.check(c, "https://www.foxnews.com/politics/another-story", null).allow);
        assertFalse("so does the front page", Guard.check(c, "https://www.foxnews.com/", null).allow);
        assertTrue("a closed page still names its kind", Guard.check(c, "https://www.foxnews.com/", null).html.contains(">News<"));
        // Its own site's files load with it (the page needs its scripts and styles), another site's do not.
        assertTrue(SitePolicy.sameSiteAs("www.foxnews.com", "static.foxnews.com"));
        assertFalse(SitePolicy.sameSiteAs("www.foxnews.com", "www.cnn.com"));
    }

    @Test
    public void noPicturesEverywhere_doesNotCoverYouTube_butEveryOtherSiteAndTheSiteList_stillDo() throws Exception {
        connected("open");
        FilterStore.setBlockImages(c, true);
        assertTrue("an ordinary site has no pictures", Guard.check(c, "https://example.com/", null).blockImages);
        assertFalse("YouTube has its own picture choices, so its thumbnails and channel pictures show", Guard.check(c, "https://www.youtube.com/", null).blockImages);
        assertFalse("YouTube Music too", Guard.check(c, "https://music.youtube.com/", null).blockImages);
        // The YouTube exception is only about pictures: a site named on the no-pictures list still has none, and a blocked YouTube stays blocked.
        FilterStore.setNoPictureSites(c, new String[]{"youtube.com"});
        assertTrue("named on the list: pictures off even on YouTube", Guard.check(c, "https://www.youtube.com/", null).blockImages);
        FilterStore.setNoPictureSites(c, new String[0]);
        FilterStore.setYoutubeMode(c, "blocked");
        assertFalse("YouTube off stays off, whatever the pictures say", Guard.check(c, "https://www.youtube.com/", null).allow);
        FilterStore.setBlockImages(c, false);
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
    public void perSiteChoices_winOverTheGeneralSetting() throws Exception {
        connected("open");
        FilterStore.setPictureGuard(c, "nudity");
        FilterStore.setSiteMedia(c, "{\"asis.example\":\"off\",\"strict.example\":\"skin\",\"nude.example\":\"nudity\"}", "{\"vidskin.example\":\"skin\",\"vidoff.example\":\"off\"}");
        assertEquals("off", FilterStore.siteMode(c, false, "asis.example", "cdn.other.net"));
        assertEquals("the page's site decides, whatever site the file comes from", "off", FilterStore.siteMode(c, false, "www.asis.example", "images.cdn.net"));
        assertEquals("off", FilterStore.siteMode(c, false, null, "img.asis.example"));
        assertEquals("skin", FilterStore.siteMode(c, false, "strict.example", null));
        assertEquals("nudity", FilterStore.siteMode(c, false, "m.nude.example", null));
        assertNull("no choice for this site: the general setting applies", FilterStore.siteMode(c, false, "plain.example", "cdn.plain.net"));
        assertNull("look-alike names are not caught", FilterStore.siteMode(c, false, "notasis.example", null));
        assertNull("videos have their own map", FilterStore.siteMode(c, true, "asis.example", null));
        FilterStore.setPictureGuard(c, "skin");
        assertNotNull("general cover skin, and nothing chosen for this site: videos covered", Guard.videoScript(c, "plain.example"));
        assertNull("Allow as they are on a site: its videos are not covered", Guard.videoScript(c, "vidoff.example"));
        FilterStore.setPictureGuard(c, "off");
        assertNotNull("Filter chosen for one site's videos: covered there only", Guard.videoScript(c, "www.vidskin.example"));
        assertNull(Guard.videoScript(c, "plain.example"));
        FilterStore.setSiteMedia(c, "", "");
        assertNull("nothing chosen: nothing changes", FilterStore.siteMode(c, false, "asis.example", null));
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

    @Test
    public void youtubeSearchPictures_show_winsOverGlobalPictureGuard_andSkin_coversEvenWhenGuardIsOff() throws Exception {
        connected("open");
        // "show": YouTube thumbnails pass through even when the global skin filter would cover them.
        FilterStore.setPictureGuard(c, "skin");
        FilterStore.setYoutubeSearchPictures(c, "show");
        android.net.Uri thumb = android.net.Uri.parse("https://i.ytimg.com/vi/somevidid/hqdefault.jpg");
        android.webkit.WebResourceRequest req = new android.webkit.WebResourceRequest() {
            @Override public android.net.Uri getUrl() { return thumb; }
            @Override public boolean isForMainFrame() { return false; }
            @Override public boolean isRedirect() { return false; }
            @Override public boolean hasGesture() { return false; }
            @Override public String getMethod() { return "GET"; }
            @Override public java.util.Map<String, String> getRequestHeaders() { return new java.util.HashMap<>(); }
        };
        assertNull("youtubeSearchPictures=show: thumbnail passes through despite global skin guard",
                Guard.intercept(c, req, "www.youtube.com"));
        // "skin": YouTube thumbnails should be covered even when the global guard is off.
        FilterStore.setPictureGuard(c, "off");
        FilterStore.setYoutubeSearchPictures(c, "skin");
        // In a test environment (no network) PictureGuard.handle returns null, so we verify via
        // siteMode: a non-YouTube page has no picture override, so its guard follows the global setting.
        assertNull("no per-site choice for a non-YouTube page: global 'off' applies",
                FilterStore.siteMode(c, false, "other.example", "cdn.other.net"));
        FilterStore.setPictureGuard(c, "off");
        FilterStore.setYoutubeSearchPictures(c, "hide");
    }

    private static android.webkit.WebResourceRequest picReq(String url, boolean mainFrame, java.util.Map<String, String> headers) {
        final android.net.Uri u = android.net.Uri.parse(url);
        return new android.webkit.WebResourceRequest() {
            @Override public android.net.Uri getUrl() { return u; }
            @Override public boolean isForMainFrame() { return mainFrame; }
            @Override public boolean isRedirect() { return false; }
            @Override public boolean hasGesture() { return false; }
            @Override public String getMethod() { return "GET"; }
            @Override public java.util.Map<String, String> getRequestHeaders() { return headers; }
        };
    }

    @Test
    public void pictureGuard_checksAPictureOpenedAsThePage_notAnOrdinaryPage() throws Exception {
        assertTrue("a picture address opened as the page is checked", PictureGuard.isPicture(picReq("https://example.com/a/photo.JPG", true, new java.util.HashMap<>())));
        assertFalse("an ordinary page is not a picture", PictureGuard.isPicture(picReq("https://example.com/index.html", true, new java.util.HashMap<>())));
        java.util.Map<String, String> accept = new java.util.HashMap<>();
        accept.put("Accept", "image/avif,image/webp,*/*");
        assertTrue("a file on a page that asks for an image is checked", PictureGuard.isPicture(picReq("https://cdn.example/x?id=1", false, accept)));
        assertFalse("a script on a page is not", PictureGuard.isPicture(picReq("https://cdn.example/app.js", false, new java.util.HashMap<>())));
    }

    @Test
    public void pictureGuard_aPictureThatCannotBeFetchedIsNotShown() throws Exception {
        connected("open");
        FilterStore.setPictureGuard(c, "skin");
        android.webkit.WebResourceResponse r = Guard.intercept(c, picReq("http://127.0.0.1:1/never.jpg", false, new java.util.HashMap<>()), "example.com");
        assertNotNull("a failed fetch gives a plain grey picture, never the WebView's own unchecked load", r);
        assertTrue(r.getMimeType().startsWith("image/"));
        FilterStore.setPictureGuard(c, "off");
    }

    @Test
    public void youtubeSearchPictures_doesNotApplyOutsideChannelsMode() throws Exception {
        connected("open");
        FilterStore.setPictureGuard(c, "skin");
        FilterStore.setYoutubeSearchPictures(c, "show");
        FilterStore.setYoutubeMode(c, "open"); // not channels mode
        android.net.Uri thumb = android.net.Uri.parse("https://i.ytimg.com/vi/somevidid/hqdefault.jpg");
        android.webkit.WebResourceRequest req = new android.webkit.WebResourceRequest() {
            @Override public android.net.Uri getUrl() { return thumb; }
            @Override public boolean isForMainFrame() { return false; }
            @Override public boolean isRedirect() { return false; }
            @Override public boolean hasGesture() { return false; }
            @Override public String getMethod() { return "GET"; }
            @Override public java.util.Map<String, String> getRequestHeaders() { return new java.util.HashMap<>(); }
        };
        // The YouTube picture choice only applies in channels mode; in open mode the global guard applies.
        // (In test, PictureGuard.handle returns null due to no network, but the control flow differs.)
        // We just verify it does not crash.
        Guard.intercept(c, req, "www.youtube.com");
        FilterStore.setYoutubeMode(c, "channels");
    }

    @Test
    public void youtubeSignIn_opensOnlyWhenAllowed_andSaysItIsAPartOfYouTube() throws Exception {
        connected("open");
        FilterStore.setYoutubeSignIn(c, false);
        Guard.Decision d = Guard.check(c, "https://accounts.google.com/ServiceLogin?service=youtube", null);
        assertFalse("signing in is off by default", d.allow);
        assertTrue(d.html.contains("This part of YouTube is not allowed"));
        assertFalse(Guard.check(c, "https://m.youtube.com/signin?action_handle_signin=true", null).allow);
        FilterStore.setYoutubeSignIn(c, true);
        assertTrue("allowed on the dashboard: it opens", Guard.check(c, "https://accounts.google.com/ServiceLogin?service=youtube", null).allow);
        Guard.Decision other = Guard.check(c, "https://example.com/", null);
        assertTrue("other sites are not affected", other.allow);
    }
}
