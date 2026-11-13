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

        // Videos on web pages: blocked everywhere except the sites marked "Allow videos", and always on the sites marked "Block videos" (never YouTube).
        y.setVideoRules(true, new HashSet<>(Arrays.asList("allowed.example")), new HashSet<>(Arrays.asList("never.example")));
        java.util.function.BiFunction<String, String, InspectProxy.Request> media = (referer, dest) -> {
            InspectProxy.Headers h = new InspectProxy.Headers();
            h.set("Sec-Fetch-Dest", dest);
            if (referer != null) h.set("Referer", referer);
            return new InspectProxy.Request("GET", "cdn.example.net", 443, true, "/files/clip", h);
        };
        check(y.onRequest(media.apply("https://other.example/page", "video")) != null, "videos blocked everywhere: a video on another site is refused");
        check(y.onRequest(media.apply("https://www.allowed.example/page", "video")) == null, "...except on a site marked Allow videos");
        check(y.onRequest(media.apply("https://other.example/page", "image")) == null, "...pictures are not touched");
        check(y.onRequest(media.apply("https://other.example/page", "document")) == null, "...nor pages");
        InspectProxy.Request hls = new InspectProxy.Request("GET", "cdn.example.net", 443, true, "/live/stream.m3u8?x=1", new InspectProxy.Headers());
        check(y.onRequest(hls) != null, "a stream file (m3u8) is refused too");
        y.setVideoRules(false, new HashSet<String>(), new HashSet<>(Arrays.asList("never.example")));
        check(y.onRequest(media.apply("https://never.example/watch", "video")) != null, "Block videos on one site, with the switch off");
        check(y.onRequest(media.apply("https://other.example/watch", "video")) == null, "...other sites keep their videos");
        check(y.videosBlockedOn("never.example") && y.videosBlockedOn("www.never.example") && !y.videosBlockedOn("other.example") && !y.videosBlockedOn("m.youtube.com"), "which sites have their videos blocked");
        InspectProxy.Headers docHeaders = new InspectProxy.Headers();
        docHeaders.set("Sec-Fetch-Dest", "document");
        InspectProxy.Request docOnBlocked = new InspectProxy.Request("GET", "never.example", 443, true, "/", docHeaders);
        InspectProxy.Request docOnOther = new InspectProxy.Request("GET", "other.example", 443, true, "/", docHeaders);
        check(y.rewrites(docOnBlocked, "text/html") && !y.rewrites(docOnOther, "text/html"), "only pages of a Block videos site are rewritten");
        check(new String(y.rewrite(docOnBlocked, page), java.nio.charset.StandardCharsets.UTF_8).contains("video{display:none"), "its video elements are hidden and stopped");
        y.setVideoRules(false, null, null);

        System.out.println(failures == 0 ? "ALL PASSED" : failures + " FAILED");
        if (failures > 0) System.exit(1);
    }
}
