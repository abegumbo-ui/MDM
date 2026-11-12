package com.familymdm.agent.sitepolicy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Message alerts (dashboard: "Alerts about messages"): finds worrying words in chat text shown on
 * the screen of a messaging app. The word list comes from the dashboard (its built-in list plus
 * the parent's own words). Whole words only, so "class" never matches "ass"; a phrase matches as
 * a run of whole words. Pure Java, checked by MessageRulesCheck.
 */
public final class MessageRules {
    private MessageRules() {}

    /** Apps whose screens are read for message alerts (chats only; never browsers or anything else). */
    public static final Set<String> MESSAGING_APPS = Set.of(
            "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.thunderdog.challegram",
            "com.instagram.android", "com.facebook.orca", "com.facebook.mlite", "com.snapchat.android",
            "org.thoughtcrime.securesms", "com.discord", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
            "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms",
            "com.motorola.messaging", "com.verizon.messaging.vzmsgs", "com.textra", "com.kik.android",
            "com.viber.voip", "jp.naver.line.android", "com.google.android.apps.dynamite", "com.skype.raider");

    public static boolean watched(String pkg) {
        return pkg != null && MESSAGING_APPS.contains(pkg);
    }

    /** Lowercase, and everything that isn't a letter or digit becomes one space, padded at both ends. */
    static String normalize(String s) {
        StringBuilder b = new StringBuilder(s.length() + 2).append(' ');
        boolean space = true;
        for (char ch : s.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(ch)) {
                b.append(ch);
                space = false;
            } else if (!space) {
                b.append(' ');
                space = true;
            }
        }
        if (!space) b.append(' ');
        return b.toString();
    }

    /** The first listed word or phrase found in the text, or null. */
    public static String match(String text, Collection<String> words) {
        if (text == null || text.isEmpty() || words == null || words.isEmpty()) return null;
        String t = normalize(text);
        for (String w : words) {
            if (w == null) continue;
            String n = normalize(w);
            if (n.trim().isEmpty()) continue;
            if (t.contains(n)) return w.trim();
        }
        return null;
    }

    /** Text cut to at most max characters around the match, for the alert. */
    public static String snippet(String text, String word, int max) {
        String s = text.replaceAll("\\s+", " ").trim();
        if (s.length() <= max) return s;
        int at = word == null ? -1 : s.toLowerCase(Locale.ROOT).indexOf(word.toLowerCase(Locale.ROOT));
        int start = Math.max(0, Math.min(s.length() - max, at < 0 ? 0 : at - max / 3));
        return (start > 0 ? "…" : "") + s.substring(start, Math.min(s.length(), start + max)).trim() + (start + max < s.length() ? "…" : "");
    }

    /** Splits a comma/new-line list into words. */
    public static List<String> parse(String list) {
        List<String> out = new ArrayList<>();
        if (list == null) return out;
        for (String p : list.split("[,\\n]")) {
            String w = p.trim();
            if (!w.isEmpty()) out.add(w);
        }
        return out;
    }
}
