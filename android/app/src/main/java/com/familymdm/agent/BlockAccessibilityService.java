package com.familymdm.agent;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

/**
 * Real-time version of AppBlocklist's bounce: reacts to the same stored "pkg/ClassName" list, but
 * through Android's own accessibility event stream instead of polling UsageStatsManager every
 * 800ms, and backs out with a genuine simulated back-press (GLOBAL_ACTION_BACK) instead of
 * starting a new activity. That matters for two reasons AppBlocklist alone can't fix: a real
 * back-press isn't subject to the background-activity-launch restriction that silently dropped
 * the earlier "bounce to Settings' own homepage" attempt, and it pops the blocked screen off its
 * own task's back stack instead of leaving it frozen underneath -- no stale task left sitting in
 * recents.
 *
 * Has to be turned on by hand under Settings > Accessibility -- Android never lets a device owner
 * silently grant this one, even with full device-owner access. There's no real Android API to
 * freeze that whole Settings screen afterward (an earlier version of this tried
 * UserManager.DISALLOW_CONFIG_ACCESSIBILITY, which doesn't exist -- caught by CI, not a device);
 * the practical equivalent is just adding Settings' own Accessibility screen to this same
 * blocklist, with Pause used whenever it needs to be opened on purpose.
 */
public class BlockAccessibilityService extends AccessibilityService {
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = event.getPackageName();
        CharSequence cls = event.getClassName();
        if (pkg == null || cls == null) return;
        String component = pkg + "/" + cls;
        AppBlocklist.recordSeen(this, component);
        if (AppBlocklist.list(this).contains(component) && !AppBlocklist.isPaused(this, component)) {
            performGlobalAction(GLOBAL_ACTION_BACK);
            Agent.addEvent(this, "restriction", "Blocked app/screen opened (" + component + ") -- backed out instantly");
        }
    }

    @Override
    public void onInterrupt() {
    }
}
