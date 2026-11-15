package com.familymdm.browser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PullToRefreshTest {
    private static final float D = 3f; // a phone's density

    @Test
    public void aLongPullDownFromTheTop_refreshes() {
        assertTrue(PullToRefresh.shouldRefresh(500, 300, 510, 300 + 600, 0, true, D));
    }

    @Test
    public void aShortPull_doesNot() {
        assertFalse(PullToRefresh.shouldRefresh(500, 300, 500, 300 + 200, 0, true, D));
    }

    @Test
    public void aGestureThatDidNotStartAtTheTop_doesNot() {
        assertFalse("it was scrolled down when the finger went down", PullToRefresh.shouldRefresh(500, 300, 500, 1100, 0, false, D));
        assertFalse("it is not at the top at the end", PullToRefresh.shouldRefresh(500, 300, 500, 1100, 40, true, D));
    }

    @Test
    public void aMostlySidewaysSwipe_doesNot() {
        assertFalse(PullToRefresh.shouldRefresh(100, 300, 800, 300 + 500, 0, true, D));
    }

    @Test
    public void aPullUp_doesNot() {
        assertFalse(PullToRefresh.shouldRefresh(500, 900, 500, 200, 0, true, D));
    }

    @Test
    public void theDistanceFollowsTheScreenDensity() {
        assertFalse("300 px is not far on a dense screen", PullToRefresh.shouldRefresh(500, 300, 500, 600, 0, true, 3f));
        assertTrue("300 px is far on a low-density screen", PullToRefresh.shouldRefresh(500, 300, 500, 600, 0, true, 1f));
    }
}
