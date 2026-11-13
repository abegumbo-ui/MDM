package com.familymdm.agent.inspect;

import java.util.Arrays;
import java.util.HashSet;

/** "Only what I allow" in Chrome and the other browsers (YoutubeInspector, through Inspection). */
public class YoutubeChannelsCheck {
    static int failures = 0;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "ok   " : "FAIL ") + what);
        if (!ok) failures++;
    }

    public static void main(String[] args) {
        YoutubeInspector y = new YoutubeInspector(false, new HashSet<String>());
        y.setYoutubeChannels(new HashSet<>(Arrays.asList("@allowedch")), new HashSet<>(Arrays.asList("ALLOWEDVID1")), "<script>x</script>");
        check(!y.channelsRefuses("https://m.youtube.com/"), "the home page opens (its feed is emptied)");
        check(!y.channelsRefuses("https://m.youtube.com/@AllowedCh/videos"), "an allowed channel opens");
        check(y.channelsRefuses("https://m.youtube.com/@someoneelse"), "another channel is refused");
        check(y.channelsRefuses("https://m.youtube.com/results?search_query=cars"), "search is refused");
        check(y.channelsRefuses("https://m.youtube.com/feed/trending"), "feeds are refused");
        check(!y.channelsRefuses("https://m.youtube.com/watch?v=OTHERVIDEO1"), "a video is decided when its page arrives");

        String plainPage = "<script>var ytInitialPlayerResponse = {\"microformat\":{\"playerMicroformatRenderer\":{\"ownerProfileUrl\":\"http://www.youtube.com/@AllowedCh\"}}};</script>";
        String mobilePage = "<script>var ytInitialPlayerResponse = '\\x7b\\x22microformat\\x22:\\x7b\\x22playerMicroformatRenderer\\x22:\\x7b\\x22ownerProfileUrl\\x22:\\x22http:\\/\\/www.youtube.com\\/@WhistlinDiesel\\x22';</script>";
        check("@allowedch".equals(YoutubeInspector.ownerOf(plainPage)), "who made the video, from YouTube's own data");
        check("@whistlindiesel".equals(YoutubeInspector.ownerOf(mobilePage)), "also when the mobile site writes it escaped");
        check(y.channelPageAllowed("https://m.youtube.com/watch?v=OTHERVIDEO1", plainPage), "a video from an allowed channel opens");
        check(!y.channelPageAllowed("https://m.youtube.com/watch?v=OTHERVIDEO2", mobilePage), "a video from another channel is refused");
        check(y.channelPageAllowed("https://m.youtube.com/watch?v=ALLOWEDVID1", mobilePage), "an allowed video opens whoever made it");
        check(!y.channelPageAllowed("https://m.youtube.com/watch?v=OTHERVIDEO3", "<html></html>"), "unknown maker: refused");
        check(y.channelPageAllowed("https://www.youtube.com/channel/UCabc", "\"vanityChannelUrl\":\"http://www.youtube.com/@AllowedCh\""), "an allowed channel's /channel/ page");
        check(!y.channelPageAllowed("https://www.youtube.com/channel/UCxyz", "\"vanityChannelUrl\":\"http://www.youtube.com/@other\""), "another channel's /channel/ page is refused");

        // The YouTube and Music icons add their own scripts: the filter's copy would come first and win, so it adds nothing for them.
        InspectProxy.Headers plain = new InspectProxy.Headers();
        plain.set("User-Agent", "Mozilla/5.0 (Linux; Android 16) Chrome/130 Mobile Safari/537.36");
        InspectProxy.Headers addon = new InspectProxy.Headers();
        addon.set("User-Agent", "Mozilla/5.0 (Linux; Android 16) Chrome/130 Mobile Safari/537.36 MDMAddon/1");
        InspectProxy.Request inChrome = new InspectProxy.Request("GET", "m.youtube.com", 443, true, "/", plain);
        InspectProxy.Request inIcon = new InspectProxy.Request("GET", "music.youtube.com", 443, true, "/", addon);
        byte[] page = "<html><head></head><body>x</body></html>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        check(y.rewrites(inChrome, "text/html"), "Chrome and the other browsers: the page is rewritten");
        check(new String(y.rewrite(inChrome, page), java.nio.charset.StandardCharsets.UTF_8).contains("<script>x</script>"), "Chrome and the other browsers: the filter's scripts are added");
        check(!y.rewrites(inIcon, "text/html"), "the YouTube / Music icon: the page is not rewritten");
        check(!y.wantsIdentity(inIcon), "the YouTube / Music icon: no decoding asked for");
        check(java.util.Arrays.equals(y.rewrite(inIcon, page), page), "the YouTube / Music icon: the page is left as it is");

        System.out.println(failures == 0 ? "ALL PASSED" : failures + " FAILED");
        if (failures > 0) System.exit(1);
    }
}
