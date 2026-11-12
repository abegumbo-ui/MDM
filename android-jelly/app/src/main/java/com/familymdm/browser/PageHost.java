package com.familymdm.browser;

/** The Browser's page view, told which address one of our pages stands for (shown in the address bar). */
public interface PageHost {
    void showingPageFor(String url);
}
