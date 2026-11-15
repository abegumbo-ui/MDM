package com.familymdm.agent.sitepolicy;

import java.util.Locale;

/**
 * "What kind of site is this?" for the blocked page: News, Social media, Shopping... from a plain built-in list of well-known sites
 * (no guessing, no online lookup). Anything not on the list is "Miscellaneous". The list is NOT what blocks a site (the filter lists and
 * the approved list do that); it only names the kind so the person reading the page, and the parent, know what was tried.
 *
 * Entries starting with "=" match that exact host only (google.com is Search, but docs.google.com is not).
 */
public final class SiteCategory {
    private SiteCategory() {
    }

    public static final String MISC = "Miscellaneous";
    public static final String ADULT = "Adult content";

    private static final String[][] KINDS = {
            {"News", "foxnews.com", "foxbusiness.com", "cnn.com", "bbc.com", "bbc.co.uk", "nytimes.com", "washingtonpost.com", "wsj.com", "usatoday.com",
                    "nbcnews.com", "abcnews.go.com", "cbsnews.com", "npr.org", "reuters.com", "apnews.com", "msnbc.com", "theguardian.com", "huffpost.com",
                    "bloomberg.com", "forbes.com", "businessinsider.com", "politico.com", "thehill.com", "nypost.com", "dailymail.co.uk", "newsweek.com",
                    "time.com", "latimes.com", "breitbart.com", "vox.com", "axios.com", "cnbc.com", "news.yahoo.com", "jpost.com", "timesofisrael.com",
                    "haaretz.com", "ynetnews.com", "ynet.co.il", "israelhayom.com", "israelnationalnews.com", "newsmax.com", "dailywire.com", "nbcnews.com"},
            {"Social media", "facebook.com", "instagram.com", "tiktok.com", "twitter.com", "x.com", "snapchat.com", "reddit.com", "pinterest.com",
                    "linkedin.com", "tumblr.com", "discord.com", "threads.net", "whatsapp.com", "telegram.org", "t.me", "bsky.app", "mastodon.social", "quora.com"},
            {"Video and streaming", "youtube.com", "youtu.be", "netflix.com", "hulu.com", "disneyplus.com", "twitch.tv", "vimeo.com", "dailymotion.com",
                    "primevideo.com", "max.com", "hbomax.com", "peacocktv.com", "paramountplus.com", "crunchyroll.com", "fox.com", "tubitv.com", "pluto.tv"},
            {"Shopping", "amazon.com", "ebay.com", "walmart.com", "target.com", "aliexpress.com", "temu.com", "shein.com", "etsy.com", "bestbuy.com", "costco.com",
                    "homedepot.com", "lowes.com", "wayfair.com", "newegg.com", "alibaba.com", "zappos.com", "macys.com", "kohls.com", "ikea.com", "groupon.com", "craigslist.org"},
            {"Games", "roblox.com", "minecraft.net", "epicgames.com", "steampowered.com", "steamcommunity.com", "ea.com", "playstation.com", "xbox.com", "nintendo.com",
                    "poki.com", "miniclip.com", "crazygames.com", "coolmathgames.com", "y8.com", "friv.com", "itch.io", "fortnite.com", "blizzard.com", "battle.net", "chess.com", "lichess.org"},
            {"Dating", "tinder.com", "bumble.com", "match.com", "hinge.co", "okcupid.com", "eharmony.com", "pof.com", "jdate.com", "grindr.com"},
            {"Sports", "espn.com", "nfl.com", "nba.com", "mlb.com", "nhl.com", "cbssports.com", "bleacherreport.com", "foxsports.com", "skysports.com", "theathletic.com", "sports.yahoo.com"},
            {"Music", "spotify.com", "soundcloud.com", "pandora.com", "music.apple.com", "deezer.com", "tidal.com", "bandcamp.com", "=music.youtube.com"},
            {"AI chat", "chatgpt.com", "openai.com", "claude.ai", "character.ai", "perplexity.ai", "=gemini.google.com", "copilot.microsoft.com"},
            {"Email", "gmail.com", "outlook.com", "protonmail.com", "proton.me", "aol.com", "=mail.google.com", "=outlook.live.com", "=mail.yahoo.com"},
            {"Search", "bing.com", "duckduckgo.com", "=google.com", "=www.google.com", "=yahoo.com", "=search.yahoo.com", "ecosia.org", "brave.com"},
    };

    private static String clean(String host) {
        if (host == null) return null;
        String h = host.trim().toLowerCase(Locale.ROOT);
        while (h.endsWith(".")) h = h.substring(0, h.length() - 1);
        return h.isEmpty() ? null : h;
    }

    /** The kind of site a host is, or null when it is not on the built-in list. */
    public static String of(String host) {
        String h = clean(host);
        if (h == null) return null;
        // Exact entries first (music.youtube.com is Music, although youtube.com as a whole is Video), then a site with all its sub-domains.
        for (String[] kind : KINDS) {
            for (int i = 1; i < kind.length; i++) {
                if (kind[i].startsWith("=") && h.equals(kind[i].substring(1))) return kind[0];
            }
        }
        for (String[] kind : KINDS) {
            for (int i = 1; i < kind.length; i++) {
                String d = kind[i];
                if (!d.startsWith("=") && (h.equals(d) || h.endsWith("." + d))) return kind[0];
            }
        }
        return null;
    }

    /** What to show on the blocked page for an address: Adult content for a known adult site, else its kind, else Miscellaneous. */
    public static String labelFor(String address) {
        if (address == null) return MISC;
        if (SitePolicy.isBlockedAdult(address)) return ADULT;
        String k = of(SitePolicy.hostOf(address));
        return k != null ? k : MISC;
    }
}
