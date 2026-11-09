package com.familymdm.agent.sitepolicy;

/**
 * Pieces of the "restricted YouTube" setting that both browsers (the separate Browser app and the
 * browser built into the Agent) share, so there is one copy of them.
 *
 * Restricted YouTube means: YouTube opens and search works, but there is no recommendation feed
 * (the home page is replaced by a plain search box), no Shorts, no trending or other feeds, and the
 * recommendations and comments around a video are hidden. See SitePolicy.youtubeKind for which
 * pages are let through.
 */
public final class YoutubeRestricted {
    private YoutubeRestricted() {}

    /** Asks YouTube itself to filter more strictly. Best effort: sent on the first request of a page. */
    public static final String RESTRICT_HEADER = "YouTube-Restrict";
    public static final String RESTRICT_VALUE = "Strict";

    /** Shown instead of YouTube's home page: nothing but a search box. */
    public static String searchPageHtml() {
        return "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>"
                + "<body style='font-family:sans-serif;padding:24px'><h2>YouTube</h2>"
                + "<form action='https://www.youtube.com/results' method='get'>"
                + "<input name='search_query' type='search' placeholder='Search YouTube' autofocus "
                + "style='width:100%;font-size:18px;padding:14px;box-sizing:border-box;border:1px solid #999;border-radius:10px'>"
                + "<button type='submit' style='margin-top:14px;font-size:18px;padding:12px 24px;border-radius:10px'>Search</button>"
                + "</form></body></html>";
    }

    /**
     * Hides what YouTube would otherwise push at the viewer: suggestions beside and after a video,
     * comments, Shorts. Best effort: it relies on YouTube's page structure, which YouTube changes
     * from time to time. The pages that matter most (home, feeds, Shorts) are also blocked outright
     * by SitePolicy.youtubeKind, so this is cleanup, not the only protection.
     */
    public static final String HIDE_SCRIPT = "(function(){if(window.__mdmYt)return;window.__mdmYt=true;"
            + "var css=['#related','ytd-watch-next-secondary-results-renderer','ytd-comments','#comments',"
            + "'ytm-watch-next-secondary-results-renderer','ytm-comments-entry-point-header-renderer','ytm-comment-section-renderer',"
            + "'.ytp-endscreen-content','.ytp-ce-element','.ytp-autonav-endscreen-countdown-container',"
            + "'ytd-reel-shelf-renderer','ytm-reel-shelf-renderer','ytm-shorts-lockup-view-model','a[href^=\"/shorts\"]',"
            + "'ytd-browse[page-subtype=\"home\"] ytd-rich-grid-renderer'].join(',')+'{display:none !important}';"
            + "var st=document.createElement('style');st.textContent=css;(document.head||document.documentElement).appendChild(st);})();";
}
