package com.familymdm.agent.sitepolicy;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * Plain checks for SitePolicy (no test framework needed). Run on a computer:
 *   javac -cp json.jar -d out android/sitepolicy/src/main/java/com/familymdm/agent/sitepolicy/SitePolicy.java android/sitepolicy/src/test/java/com/familymdm/agent/sitepolicy/SitePolicyCheck.java
 *   java -cp json.jar:out com.familymdm.agent.sitepolicy.SitePolicyCheck
 */
public class SitePolicyCheck {
    static int failures = 0;

    static void check(boolean ok, String what) {
        if (!ok) {
            failures++;
            System.out.println("FAIL: " + what);
        } else {
            System.out.println("ok:   " + what);
        }
    }

    static JSONArray sites(String... jsonObjects) throws Exception {
        JSONArray a = new JSONArray();
        for (String s : jsonObjects) a.put(new JSONObject(s));
        return a;
    }

    public static void main(String[] args) throws Exception {
        List<SitePolicy.Site> domain = SitePolicy.parse(sites("{\"type\":\"domain\",\"url\":\"https://nytimes.com\",\"host\":\"nytimes.com\"}"));
        check(SitePolicy.allowed("https://nytimes.com/section/world", domain), "domain covers subpages");
        check(SitePolicy.allowed("https://www.nytimes.com/", domain), "domain covers www");
        check(SitePolicy.allowed("https://m.nytimes.com/x", domain), "domain covers subdomains");
        check(!SitePolicy.allowed("https://notnytimes.com/", domain), "domain does not cover a different domain");
        check(!SitePolicy.allowed("https://evilnytimes.com/", domain), "domain does not cover a look-alike domain");

        List<SitePolicy.Site> exact = SitePolicy.parse(sites("{\"type\":\"exact\",\"url\":\"https://example.com/safe-page\",\"host\":\"example.com\"}"));
        check(SitePolicy.allowed("https://example.com/safe-page", exact), "exact matches the page");
        check(SitePolicy.allowed("https://example.com/safe-page/", exact), "exact ignores a trailing slash");
        check(SitePolicy.allowed("https://example.com/safe-page?x=1#y", exact), "exact ignores query/fragment");
        check(!SitePolicy.allowed("https://example.com/other-page", exact), "exact does not cover a different page");
        check(!SitePolicy.allowed("https://example.com/", exact), "exact does not cover the site root");

        SitePolicy.Site m = SitePolicy.matching("https://nytimes.com/x", domain);
        check(m != null && m.host.equals("nytimes.com"), "matching() returns the entry that matched");
        check(SitePolicy.matching("https://nope.com/", domain) == null, "matching() returns null when nothing matches");

        JSONArray mixed = sites("{\"type\":\"bogus\",\"url\":\"https://x.com\"}");
        mixed.put("not an object"); // a malformed entry should just be skipped, not throw
        check(SitePolicy.parse(mixed).isEmpty(), "unknown type / non-object entries are dropped");

        check(SitePolicy.isBlockedAdult("https://pornhub.com/video"), "a known adult site is blocked");
        check(SitePolicy.isBlockedAdult("https://www.pornhub.com/"), "...even with a www. prefix");
        check(SitePolicy.isBlockedAdult("https://sub.pornhub.com/"), "...and any subdomain");
        check(!SitePolicy.isBlockedAdult("https://notpornhub.com/"), "a look-alike domain is not swept in");
        check(!SitePolicy.isBlockedAdult("https://nytimes.com/"), "an ordinary site is not blocked");

        // ---- YouTube: the same cases test/youtube.test.js checks on the server side ----
        String v = "dQw4w9WgXcQ";
        for (String u : new String[] {
                "https://www.youtube.com/watch?v=" + v, "https://youtube.com/watch?v=" + v + "&t=42s&list=PLabc",
                "https://m.youtube.com/watch?v=" + v, "https://youtu.be/" + v, "https://youtu.be/" + v + "?si=zzz",
                "https://www.youtube.com/shorts/" + v, "https://www.youtube.com/embed/" + v,
                "https://www.youtube-nocookie.com/embed/" + v, "https://www.youtube.com/live/" + v + "?feature=share"}) {
            check(v.equals(SitePolicy.youtubeVideoId(u)), "video id from " + u);
        }
        for (String u : new String[] {"https://www.youtube.com/", "https://www.youtube.com/watch", "https://www.youtube.com/watch?v=short",
                "https://www.youtube.com/results?search_query=x", "https://example.com/watch?v=" + v, "https://www.youtube.com/@someone"}) {
            check(SitePolicy.youtubeVideoId(u) == null, "no video id in " + u);
        }
        check("@aishdotcom".equals(SitePolicy.youtubeChannelHandle("https://www.youtube.com/@AishDotCom")), "channel handle, lower-cased");
        check("@aishdotcom".equals(SitePolicy.youtubeChannelHandle("https://m.youtube.com/@AishDotCom/videos")), "channel handle from a sub-page");
        check(SitePolicy.youtubeChannelHandle("https://www.youtube.com/channel/UCxxxxxxxxxxxxxxxxxxxxxx") == null, "/channel/ links are not accepted");
        check(SitePolicy.youtubeChannelHandle("https://example.com/@handle") == null, "@handle on another site is not a channel");

        List<SitePolicy.Site> yt = SitePolicy.parse(sites(
                "{\"type\":\"exact\",\"url\":\"https://www.youtube.com/watch?v=" + v + "\"}",
                "{\"type\":\"youtube\",\"kind\":\"channel\",\"id\":\"@AishDotCom\",\"url\":\"https://www.youtube.com/@aishdotcom\"}"));
        check(yt.size() == 2, "a video entry and a channel entry both parse");
        check(SitePolicy.allowed("https://youtu.be/" + v, yt), "one allowed video is allowed however it is written");
        check(!SitePolicy.allowed("https://www.youtube.com/watch?v=AAAAAAAAAAA", yt), "a different video is NOT allowed by that entry");
        check(!SitePolicy.allowed("https://www.youtube.com/@aishdotcom", yt), "a channel entry never matches by address (the Browser decides)");
        check(SitePolicy.hasYoutubeChannels(yt), "the channel entry is noticed");
        check(SitePolicy.youtubeChannelAllowed("@AishDotCom", yt) && !SitePolicy.youtubeChannelAllowed("@someoneelse", yt), "only the listed channel is allowed");
        check(!SitePolicy.youtubeChannelAllowed(null, yt), "no channel is never allowed");
        check(SitePolicy.parse(sites("{\"type\":\"youtube\",\"kind\":\"channel\",\"id\":\"nohandle\"}")).isEmpty(), "a channel entry without a real @handle is dropped");
        check(!SitePolicy.hasYoutubeChannels(SitePolicy.parse(sites("{\"type\":\"domain\",\"url\":\"example.com\"}"))), "no channel entries means none noticed");

        System.out.println(failures == 0 ? "ALL PASSED" : failures + " FAILED");
        if (failures > 0) System.exit(1);
    }
}
