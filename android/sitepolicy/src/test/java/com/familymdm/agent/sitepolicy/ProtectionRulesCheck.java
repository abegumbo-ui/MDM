package com.familymdm.agent.sitepolicy;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Plain checks for ProtectionRules. Run on a computer:
 *   javac -d out android/sitepolicy/src/main/java/com/familymdm/agent/sitepolicy/ProtectionRules.java android/sitepolicy/src/test/java/com/familymdm/agent/sitepolicy/ProtectionRulesCheck.java
 *   java -cp out com.familymdm.agent.sitepolicy.ProtectionRulesCheck
 */
public class ProtectionRulesCheck {
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
        Set<String> chosen = new HashSet<>(Collections.singletonList("com.whatsapp"));
        check(ProtectionRules.internetAllowed("com.instagram.android", false, chosen), "switch off: every app has internet");
        check(ProtectionRules.internetAllowed("com.whatsapp", true, chosen), "a chosen app has internet");
        check(!ProtectionRules.internetAllowed("com.instagram.android", true, chosen), "another app does not");
        check(ProtectionRules.internetAllowed("com.google.android.gms", true, chosen), "Google Play services always (notifications for the chosen apps)");
        check(ProtectionRules.internetAllowed("com.familymdm.agent", true, chosen), "the Agent itself always (to reach the dashboard)");
        check(ProtectionRules.internetAllowed(null, true, chosen), "an app that cannot be identified is not cut off by this rule");
        check(!ProtectionRules.internetAllowed("com.android.chrome", true, null), "nothing chosen: only the essentials");

        for (String yes : new String[] {
                "https://www.google.com/search?q=cats&tbm=isch", "https://www.google.co.il/search?udm=2&q=cats", "https://google.com/search?q=x&udm=2",
                "https://images.google.com/", "https://www.google.com/imgres?imgurl=x", "https://www.bing.com/images/search?q=cats",
                "https://duckduckgo.com/?q=cats&ia=images", "https://duckduckgo.com/?q=cats&iax=images&ia=images"}) {
            check(ProtectionRules.isImageSearch(yes), "image search: " + yes);
        }
        for (String no : new String[] {
                "https://www.google.com/search?q=cats", "https://www.google.com/search?q=udm%3D2", "https://www.google.com/maps",
                "https://www.bing.com/search?q=images", "https://duckduckgo.com/?q=images", "https://example.com/images", "not a url %%", ""}) {
            check(!ProtectionRules.isImageSearch(no), "not image search: " + no);
        }
        check(!ProtectionRules.isImageSearch(null), "nothing is not an image search");

        System.out.println(failures == 0 ? "ALL OK" : failures + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
