package com.familymdm.agent.inspect;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Per site, pictures and videos each have their own "Filter" choice: a site with Filter chosen for its pictures gets them checked (skin covered)
 * even when no filter is on everywhere, and the same for its videos, each on its own.
 */
public final class SiteFilterCheck {
    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError("FAILED: " + what);
        System.out.println("ok:   " + what);
    }

    static InspectProxy.Request req(String host, String path, String referer, String dest) {
        InspectProxy.Headers h = new InspectProxy.Headers();
        if (referer != null) h.set("Referer", referer);
        if (dest != null) h.set("Sec-Fetch-Dest", dest);
        return new InspectProxy.Request("GET", host, 443, true, path, h);
    }

    static Set<String> set(String... a) {
        return new HashSet<>(Arrays.asList(a));
    }

    public static void main(String[] args) {
        byte[] jpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        byte[] siteChecked = "SITE-CHECKED".getBytes(StandardCharsets.UTF_8);
        byte[] generalChecked = "GENERAL-CHECKED".getBytes(StandardCharsets.UTF_8);
        String script = "/*VIDEO-SKIN*/";

        YoutubeInspector y = new YoutubeInspector(false, new HashSet<String>());
        y.setSiteFilters(set("filterpics.example"), set("filtervids.example"), p -> siteChecked, script);

        // Pictures: only on (or from) the site with Filter chosen for its pictures.
        InspectProxy.Request onSite = req("filterpics.example", "/a.jpg", null, "image");
        check(y.rewrites(onSite, "image/jpeg"), "a picture on a site with Filter chosen is checked");
        check(Arrays.equals(y.rewrite(onSite, jpeg), siteChecked), "...and what the phone shows is the checked picture");
        InspectProxy.Request fromSite = req("cdn.example.net", "/a.jpg", "https://www.filterpics.example/page", "image");
        check(y.rewrites(fromSite, "image/jpeg") && Arrays.equals(y.rewrite(fromSite, jpeg), siteChecked), "a picture that site's page loads from its CDN is checked too");
        InspectProxy.Request elsewhere = req("plain.example", "/a.jpg", null, "image");
        check(!y.rewrites(elsewhere, "image/jpeg"), "a picture on another site is left alone (no general filter on)");
        check(Arrays.equals(y.rewrite(elsewhere, jpeg), jpeg), "...and passes unchanged");
        InspectProxy.Request vidSitePic = req("filtervids.example", "/a.jpg", null, "image");
        check(!y.rewrites(vidSitePic, "image/jpeg"), "Filter chosen for VIDEOS does not filter that site's pictures");

        // Videos: the skin script goes into the pages of the site with Filter chosen for its videos, and only those.
        InspectProxy.Request vidDoc = req("filtervids.example", "/watch", null, "document");
        check(y.rewrites(vidDoc, "text/html") && y.wantsIdentity(vidDoc), "a page on a site with Filter chosen for videos is rewritten (and asked uncompressed)");
        String page = new String(y.rewrite(vidDoc, "<html><head></head><body>hi</body></html>".getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
        check(page.contains(script) && page.indexOf(script) < page.indexOf("</head>"), "...and the video skin script is added to its head");
        InspectProxy.Request picDoc = req("filterpics.example", "/", null, "document");
        check(!y.rewrites(picDoc, "text/html"), "Filter chosen for PICTURES does not add the video script to that site's pages");
        InspectProxy.Request plainDoc = req("plain.example", "/", null, "document");
        check(!y.rewrites(plainDoc, "text/html"), "a page on another site is not touched");

        // Filtered pictures AND videos on one site, each chosen on its own.
        YoutubeInspector both = new YoutubeInspector(false, new HashSet<String>());
        both.setSiteFilters(set("both.example"), set("both.example"), p -> siteChecked, script);
        check(both.rewrites(req("both.example", "/a.jpg", null, "image"), "image/jpeg") && both.rewrites(req("both.example", "/", null, "document"), "text/html"), "one site can have both pictures and videos filtered");

        // The general filter (Filter everywhere) still works, and a site with its own Filter choice uses the site's checker.
        YoutubeInspector general = new YoutubeInspector(false, new HashSet<String>());
        general.setPictureFilter(p -> generalChecked);
        general.setSiteFilters(set("filterpics.example"), set(), p -> siteChecked, script);
        check(Arrays.equals(general.rewrite(elsewhere, jpeg), generalChecked), "with the general filter on, other sites get the general checker");
        check(Arrays.equals(general.rewrite(onSite, jpeg), siteChecked), "a site with its own Filter choice gets the site's checker");
        check(general.pageScriptFor(plainDoc) == null, "no video script anywhere when none was chosen");

        // "Filter nudity only": its own checker and its own video script, and it wins over the general filter on its site.
        byte[] nudityChecked = "NUDITY-CHECKED".getBytes(StandardCharsets.UTF_8);
        String nudityScript = "/*VIDEO-NUDITY*/";
        YoutubeInspector nude = new YoutubeInspector(false, new HashSet<String>());
        nude.setPictureFilter(p -> generalChecked);
        nude.setPageScript("/*GENERAL-SKIN*/");
        nude.setSiteFilters(set("skin.example"), set("skin.example"), p -> siteChecked, script);
        nude.setSiteNudityFilters(set("nude.example"), set("nude.example"), p -> nudityChecked, nudityScript);
        check(Arrays.equals(nude.rewrite(req("nude.example", "/a.jpg", null, "image"), jpeg), nudityChecked), "a site with Filter nudity only gets the nudity checker for its pictures");
        check(Arrays.equals(nude.rewrite(req("cdn.example.net", "/a.jpg", "https://nude.example/p", "image"), jpeg), nudityChecked), "...also for pictures its page loads from elsewhere");
        check(Arrays.equals(nude.rewrite(req("skin.example", "/a.jpg", null, "image"), jpeg), siteChecked), "a site with Filter nudity and skin keeps its own checker");
        check(Arrays.equals(nude.rewrite(req("plain.example", "/a.jpg", null, "image"), jpeg), generalChecked), "any other site gets the general checker");
        check(nude.pageScriptFor(req("nude.example", "/", null, "document")).equals(nudityScript), "the nudity video script goes into that site's pages, instead of the general skin script");
        check(nude.pageScriptFor(req("skin.example", "/", null, "document")).equals(script), "a nudity and skin site gets the skin script");
        check(nude.pageScriptFor(req("plain.example", "/", null, "document")).equals("/*GENERAL-SKIN*/"), "other sites keep the general script");
        String nudePage = new String(nude.rewrite(req("nude.example", "/", null, "document"), "<html><head></head><body>x</body></html>".getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
        check(nudePage.contains(nudityScript) && !nudePage.contains("GENERAL-SKIN"), "the page of a nudity-only site carries only the nudity script");
        // The pictures and videos are chosen separately: nudity only for the videos leaves the site's pictures to the general filter.
        YoutubeInspector vidOnly = new YoutubeInspector(false, new HashSet<String>());
        vidOnly.setSiteNudityFilters(set(), set("v.example"), null, nudityScript);
        check(!vidOnly.rewrites(req("v.example", "/a.jpg", null, "image"), "image/jpeg") && vidOnly.rewrites(req("v.example", "/", null, "document"), "text/html"), "nudity only on videos does not touch that site's pictures");
        check(YoutubeInspector.videoNudityScript() != null && YoutubeInspector.videoNudityScript().contains("ask.mdm.invalid/nudity"), "the nudity video script is packed with the proxy");

        // Nothing chosen: nothing changes.
        YoutubeInspector none = new YoutubeInspector(false, new HashSet<String>());
        none.setSiteFilters(set(), set(), null, null);
        check(!none.rewrites(onSite, "image/jpeg") && !none.rewrites(vidDoc, "text/html"), "no site has Filter chosen: nothing is checked");
        System.out.println("SiteFilterCheck OK");
    }
}
