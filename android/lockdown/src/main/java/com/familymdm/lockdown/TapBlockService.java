package com.familymdm.lockdown;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

/**
 * Tap to block, for Lockdown: closes the screens picked with "✓ Block this screen" the moment they
 * open, and covers or closes the parts of apps picked with "Pick a part" (see PartBlocker). Has to
 * be switched on once in the phone's Settings → Accessibility; nothing else uses it.
 */
public class TapBlockService extends AccessibilityService {
    static volatile TapBlockService instance;
    private long lastBack;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onDestroy() {
        instance = null;
        PartBlocker.stopPicking(this);
        PartBlocker.clearCovers(this);
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        CharSequence pkgCs = event.getPackageName();
        if (pkgCs == null) return;
        String pkg = pkgCs.toString();
        int type = event.getEventType();
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.getClassName() != null) {
            String cls = event.getClassName().toString();
            PartBlocker.noteScreen(pkg, cls);
            if (!PartBlocker.picking && LockdownPrefs.getSet(this, "tapBlockedScreens").contains(pkg + "/" + cls)) {
                long now = System.currentTimeMillis();
                if (now - lastBack > 600) {
                    lastBack = now;
                    performGlobalAction(GLOBAL_ACTION_BACK);
                }
                return;
            }
        }
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED || type == AccessibilityEvent.TYPE_VIEW_SCROLLED)
            PartBlocker.onScreen(this, pkg);
    }

    @Override
    public void onInterrupt() {
    }

    static boolean on(android.content.Context c) {
        String enabled = android.provider.Settings.Secure.getString(c.getContentResolver(), android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabled != null && enabled.contains(c.getPackageName() + "/");
    }
}
