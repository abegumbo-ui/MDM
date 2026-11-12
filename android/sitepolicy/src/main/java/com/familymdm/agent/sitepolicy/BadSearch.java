package com.familymdm.agent.sitepolicy;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * "Oh come on, you're better than that." A search for explicit things on the Strict level (or with
 * one of the parent's blocked words, on any level) gets this page instead of results. Used by the
 * family Browser, by Chrome with Inspection, and by the MDM app for searches typed into any browser.
 */
public final class BadSearch {
    private BadSearch() {}

    public static final String TITLE = "Oh come on, you're better than that.";
    public static final String LINE = "That search isn't allowed here. Try something else.";

    /** Explicit words and phrases; matched as whole words, so "Essex" or "Sussex" are fine. */
    private static final Set<String> WORDS = new HashSet<>(Arrays.asList(
            "porn", "porno", "pornography", "xxx", "nsfw", "nude", "nudes", "naked", "nudity", "sex", "sexy", "sexual",
            "hentai", "rule34", "onlyfans", "fansly", "boobs", "boob", "tits", "titties", "nipple", "nipples", "pussy",
            "vagina", "penis", "dick", "cock", "blowjob", "handjob", "milf", "camgirl", "camgirls", "stripper", "strip club",
            "erotic", "erotica", "fetish", "bdsm", "orgasm", "masturbate", "masturbation", "horny", "lingerie", "bikini",
            "playboy", "hooker", "escort", "escorts", "sexting", "nudist", "topless", "bottomless", "thong", "bra",
            "hot girls", "hot women", "sexy girls", "girls in bikini", "adult video", "adult videos", "18+"));

    /** The words typed in a search address (Google, Bing, DuckDuckGo, Yahoo, YouTube), or null. */
    public static String query(String url) {
        if (url == null) return null;
        try {
            URI u = new URI(url);
            String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
            String path = u.getPath() == null ? "" : u.getPath();
            String key = null;
            if (host.matches("(www\\.)?google\\.[a-z.]+") && (path.equals("/search") || path.equals("/webhp"))) key = "q";
            else if (host.endsWith("bing.com") && path.startsWith("/search")) key = "q";
            else if (host.endsWith("duckduckgo.com")) key = "q";
            else if (host.endsWith("search.yahoo.com")) key = "p";
            else if (SitePolicy.isYoutubeHost(host) && path.equals("/results")) key = "search_query";
            if (key == null || u.getRawQuery() == null) return null;
            for (String part : u.getRawQuery().split("&")) {
                int eq = part.indexOf('=');
                if (eq > 0 && part.substring(0, eq).equals(key))
                    return java.net.URLDecoder.decode(part.substring(eq + 1), "UTF-8");
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** Lower case, everything that is not a letter or digit (or +) turned into single spaces. */
    static String normalize(String s) {
        return (" " + s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{Nd}+]+", " ").trim() + " ");
    }

    /**
     * Should this search get the "you're better than that" page? strict: the built-in list applies
     * (the Strict level); blockedWords: the parent's own blocked words (any level).
     */
    public static boolean isBad(String searchText, boolean strict, List<String> blockedWords) {
        if (searchText == null || searchText.trim().isEmpty()) return false;
        String q = normalize(searchText);
        if (strict) for (String w : WORDS) if (q.contains(" " + w + " ")) return true;
        if (blockedWords != null) for (String w : blockedWords) {
            if (w == null || w.trim().isEmpty()) continue;
            if (q.contains(" " + normalize(w).trim() + " ")) return true;
        }
        return false;
    }

    /** The page itself: the family's look, with the message. */
    public static String page(String extraHtml) {
        return BlockedPage.html(TITLE, new String[]{LINE}, extraHtml);
    }
}
