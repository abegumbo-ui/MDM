package com.familymdm.agent.sitepolicy;

import java.util.List;

/** Plain-Java checks for MessageRules (run in CI, see android.yml). */
public final class MessageRulesCheck {
    private static int failures;

    private static void check(boolean ok, String what) {
        if (!ok) {
            failures++;
            System.out.println("FAIL: " + what);
        }
    }

    public static void main(String[] args) {
        List<String> words = MessageRules.parse("kill yourself, nudes\nsend pics,ass");
        check(words.size() == 4, "parse splits commas and lines");
        check("kill yourself".equals(MessageRules.match("you should KILL   yourself!!", words)), "phrase across spaces and case");
        check("nudes".equals(MessageRules.match("send me nudes pls", words)), "single word");
        check(MessageRules.match("I have class today", words) == null, "no match inside another word");
        check(MessageRules.match("assignment due", words) == null, "no match at word start");
        check("ass".equals(MessageRules.match("what an ass.", words)), "word before punctuation");
        check(MessageRules.match("", words) == null, "empty text");
        check(MessageRules.match("anything", List.of()) == null, "no words");
        check("send pics".equals(MessageRules.match("can u send-pics", words)), "punctuation between phrase words");
        check(MessageRules.watched("com.whatsapp") && !MessageRules.watched("com.android.chrome"), "only messaging apps");
        String longText = "a".repeat(300) + " nudes " + "b".repeat(300);
        String s = MessageRules.snippet(longText, "nudes", 120);
        check(s.contains("nudes") && s.length() <= 124, "snippet keeps the word: " + s.length());
        check("short text".equals(MessageRules.snippet(" short   text ", "x", 120)), "snippet tidies spaces");
        if (failures > 0) {
            System.out.println(failures + " check(s) failed");
            System.exit(1);
        }
        System.out.println("MessageRules: all checks passed");
    }
}
