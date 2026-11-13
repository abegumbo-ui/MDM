package com.familymdm.browser;

/** The Browser's page view, told which address one of our pages stands for (shown in the address bar). */
public interface PageHost {
    void showingPageFor(String url);

    /** The last address that opened (not a blocked page): where "back" returns to. Null when none yet. */
    String lastAllowedPage();

    /** True while a page that did not open is showing. */
    boolean showingBlockedPage();
}
