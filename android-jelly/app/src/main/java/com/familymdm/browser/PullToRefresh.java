package com.familymdm.browser;

/**
 * Pull a page down while it is at its very top to reload it, like every other browser (our Browser had no way to refresh by hand).
 * The decision is kept here, away from the touch handling, so it can be checked on a computer.
 */
public final class PullToRefresh {
    private PullToRefresh() {
    }

    /** How far (in dp) the finger must travel down for a pull to count. */
    public static final float PULL_DP = 140f;

    /**
     * Should this gesture reload the page? It must have started with the page at its top (and still be there), go down at least PULL_DP,
     * and be mostly vertical (a sideways swipe, or a scroll that happened to start at the top, is not a refresh).
     */
    public static boolean shouldRefresh(float startX, float startY, float endX, float endY, int scrollYAtEnd, boolean startedAtTop, float density) {
        if (!startedAtTop || scrollYAtEnd != 0) return false;
        float down = endY - startY;
        float side = Math.abs(endX - startX);
        return down > PULL_DP * density && side < down / 2f;
    }
}
