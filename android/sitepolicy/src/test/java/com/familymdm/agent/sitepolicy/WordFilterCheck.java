package com.familymdm.agent.sitepolicy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Plain checks for WordFilter. Run on a computer:
 *   javac -d out android/sitepolicy/src/main/java/com/familymdm/agent/sitepolicy/WordFilter.java android/sitepolicy/src/test/java/com/familymdm/agent/sitepolicy/WordFilterCheck.java
 *   java -cp out com.familymdm.agent.sitepolicy.WordFilterCheck
 */
public class WordFilterCheck {
    static int failures = 0;

    static void check(boolean ok, String what) {
        if (!ok) {
            failures++;
            System.out.println("FAIL: " + what);
        } else {
            System.out.println("ok:   " + what);
        }
    }

    public static void main(String[] args) {
        List<String> words = new ArrayList<>();
        for (String w : new String[] {"sex", "Casino", "  lottery tickets ", "גוי", "x", "!!!"}) {
            String c = WordFilter.clean(w);
            if (c != null) words.add(c);
        }
        check(words.equals(Arrays.asList("sex", "casino", "lottery tickets", "גוי", "x")), "words are cleaned: lower-case, trimmed, junk dropped");

        check("sex".equals(WordFilter.match("https://www.google.com/search?q=sex+videos", words)), "a short word as its own word in a search");
        check(WordFilter.match("https://www.visitsussex.org/essex-and-middlesex", words) == null, "a short word inside a longer word is NOT matched (Sussex, Essex)");
        check("casino".equals(WordFilter.match("https://best-onlinecasinos.example/", words)), "a 4+ letter word inside a longer word is matched");
        check("casino".equals(WordFilter.match("https://example.com/q=CASINO", words)), "any case");
        check("lottery tickets".equals(WordFilter.match("https://shop.example/search?q=cheap%20lottery%20tickets%21", words)), "a phrase, URL-encoded");
        check(WordFilter.match("https://shop.example/search?q=tickets+lottery", words) == null, "a phrase only in that order");
        check(WordFilter.match("https://shop.example/lotterytickets", words) == null, "a phrase needs its words apart");
        check("גוי".equals(WordFilter.match("https://example.com/search?q=%D7%92%D7%95%D7%99", words)), "Hebrew, URL-encoded");
        check("x".equals(WordFilter.match("https://x.com/home", words)), "a one-letter word as a whole site label");
        check(WordFilter.match("https://example.com/max", words) == null, "a one-letter word inside a word is not matched");
        check(WordFilter.match("https://example.com/100%", words) == null, "a stray % does not crash");
        check(WordFilter.match("", words) == null && WordFilter.match(null, words) == null && WordFilter.match("sex", null) == null && WordFilter.match("sex", new ArrayList<String>()) == null, "nothing to check, nothing matched");
        check("casino".equals(WordFilter.match("casinoroyale.example", words)), "a bare site name (as the site filter sees it)");

        System.out.println(failures == 0 ? "ALL OK" : failures + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
