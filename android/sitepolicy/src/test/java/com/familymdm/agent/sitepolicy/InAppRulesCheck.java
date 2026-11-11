package com.familymdm.agent.sitepolicy;

import java.util.Collections;
import java.util.List;

/**
 * Plain checks for InAppRules. Run on a computer:
 *   javac -d out android/sitepolicy/src/main/java/com/familymdm/agent/sitepolicy/InAppRules.java android/sitepolicy/src/test/java/com/familymdm/agent/sitepolicy/InAppRulesCheck.java
 *   java -cp out com.familymdm.agent.sitepolicy.InAppRulesCheck
 */
public class InAppRulesCheck {
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
        String tab = "org.chromium.chrome.browser.customtabs.CustomTabActivity";
        List<String> none = Collections.emptyList();
        check(!InAppRules.blocks("off", "com.android.chrome", tab, false, none), "off: nothing is closed");
        check(InAppRules.blocks("tabs", "com.android.chrome", tab, false, none), "tabs: Chrome's page on top of an app is closed");
        check(InAppRules.blocks("tabs", "com.sec.android.app.sbrowser", "com.sec.android.app.sbrowser.customtab.CustomTabActivity", false, none), "tabs: Samsung Internet's too");
        check(!InAppRules.blocks("tabs", "com.android.chrome", "org.chromium.chrome.browser.ChromeTabbedActivity", false, none), "tabs: Chrome itself still opens");
        check(!InAppRules.blocks("tabs", "com.instagram.android", "com.instagram.SomeActivity", true, none), "tabs: a web page inside Instagram is left for 'all'");
        check(InAppRules.blocks("all", "com.instagram.android", "com.instagram.SomeActivity", true, none), "all: a web page inside Instagram is closed");
        check(!InAppRules.blocks("all", "com.instagram.android", "com.instagram.SomeActivity", false, none), "all: Instagram without a web page is fine");
        check(!InAppRules.blocks("all", "com.bank.app", "x", true, Collections.singletonList("com.bank.app")), "all: an exception is left alone");
        check(!InAppRules.blocks("all", "com.android.settings", "x", true, none), "all: Android's own screens are left alone");
        check(!InAppRules.blocks("all", "com.familymdm.browser", "x", true, none), "all: our own browser is left alone");
        check(InAppRules.blocks("all", "com.android.chrome", tab, false, Collections.singletonList("com.android.chrome")), "all: custom tabs are closed even if Chrome is an exception");
        check(!InAppRules.blocks("all", null, null, true, none), "nothing known: nothing closed");

        check(InAppRules.whatsAppBlocks("com.whatsapp", "com.whatsapp.status.playback.StatusPlaybackActivity", true, false), "WhatsApp: watching a status is closed");
        check(!InAppRules.whatsAppBlocks("com.whatsapp", "com.whatsapp.status.playback.StatusPlaybackActivity", false, true), "WhatsApp: status allowed when only channels are off");
        check(InAppRules.whatsAppBlocks("com.whatsapp.w4b", "com.whatsapp.newsletter.NewsletterActivity", false, true), "WhatsApp Business: channels closed");
        check(!InAppRules.whatsAppBlocks("com.whatsapp", "com.whatsapp.Conversation", true, true), "WhatsApp: a chat stays open");
        check(!InAppRules.whatsAppBlocks("com.whatsapp", "com.whatsapp.HomeActivity", true, true), "WhatsApp: the main screen stays open");
        check(!InAppRules.whatsAppBlocks("org.telegram.messenger", "x.StatusPlaybackActivity", true, true), "other apps are not touched");

        System.out.println(failures == 0 ? "ALL OK" : failures + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
