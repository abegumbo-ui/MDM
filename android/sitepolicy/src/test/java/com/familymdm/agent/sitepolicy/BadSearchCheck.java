package com.familymdm.agent.sitepolicy;

import java.util.Arrays;
import java.util.Collections;

/** Run by android.yml: "Oh come on, you're better than that" catches the right searches, and only those. */
public class BadSearchCheck {
    private static int failures;

    private static void check(boolean ok, String what) {
        System.out.println((ok ? "ok:   " : "FAIL: ") + what);
        if (!ok) failures++;
    }

    public static void main(String[] a) {
        check("hot girls".equals(BadSearch.query("https://www.google.com/search?q=hot+girls&safe=active")), "reads a Google search");
        check("lego".equals(BadSearch.query("https://m.youtube.com/results?search_query=lego")), "reads a YouTube search");
        check("cats".equals(BadSearch.query("https://www.bing.com/search?q=cats")), "reads a Bing search");
        check(BadSearch.query("https://www.google.com/maps") == null, "not a search: nothing");
        check(BadSearch.isBad("free PORN videos", true, null), "Strict: an explicit search");
        check(BadSearch.isBad("hot girls", true, null), "Strict: a phrase");
        check(!BadSearch.isBad("free porn videos", false, null), "not Strict: the built-in list does not apply");
        check(!BadSearch.isBad("Essex and Sussex history", true, null), "whole words only (Essex, Sussex)");
        check(!BadSearch.isBad("how volcanoes work", true, null), "an ordinary search");
        check(BadSearch.isBad("best casino games", false, Arrays.asList("casino")), "a blocked word, any level");
        check(!BadSearch.isBad("best board games", false, Collections.singletonList("casino")), "no blocked word");
        check(BadSearch.page(null).contains("Oh come on, you&#39;re better than that."), "the page says it");
        System.exit(failures);
    }
}
