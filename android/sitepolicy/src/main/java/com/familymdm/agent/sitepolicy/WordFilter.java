package com.familymdm.agent.sitepolicy;

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Blocked words: a site address, a search, or a page address that contains one of the
 * administrator's words is blocked. Pure Java so it can be checked on a computer (WordFilterCheck).
 *
 * To avoid blocking innocent words that merely contain a short word ("Sussex", "Essex"), a word of
 * 1-3 letters only matches a whole word on its own, while a word of 4 or more letters also matches
 * inside longer words ("casino" blocks "onlinecasinos"). A phrase with spaces matches those words
 * in that order. Hebrew letters count as letters.
 */
public final class WordFilter {
    private WordFilter() {}

    private static final String SPLIT = "[^a-z0-9\\u0590-\\u05ff]+";

    /** The words of an address or search: decoded, lower-case, split on everything that is not a letter or digit. */
    static List<String> tokens(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        String t = text;
        try {
            t = URLDecoder.decode(text.replace("+", " "), "UTF-8");
        } catch (Exception ignored) {
            // a stray % in an address: use it as it is
        }
        for (String s : t.toLowerCase(Locale.ROOT).split(SPLIT)) if (!s.isEmpty()) out.add(s);
        return out;
    }

    /** Lower-case, single-spaced, without leading/trailing punctuation; null if it is not a usable word. */
    public static String clean(String word) {
        if (word == null) return null;
        List<String> t = tokens(word);
        if (t.isEmpty()) return null;
        String w = String.join(" ", t);
        return w.length() > 60 ? null : w;
    }

    /** The first blocked word found in `text`, or null. `words` should already be clean(). */
    public static String match(String text, List<String> words) {
        if (words == null || words.isEmpty() || text == null) return null;
        List<String> toks = tokens(text);
        if (toks.isEmpty()) return null;
        String joined = " " + String.join(" ", toks) + " ";
        for (String w : words) {
            if (w == null || w.isEmpty()) continue;
            if (w.indexOf(' ') >= 0) {
                if (joined.contains(" " + w + " ")) return w;
                continue;
            }
            for (String t : toks) {
                if (w.length() >= 4 ? t.contains(w) : t.equals(w)) return w;
            }
        }
        return null;
    }
}
