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
            String title = event.getText() != null && !event.getText().isEmpty() && event.getText().get(0) != null ? event.getText().get(0).toString() : "";
            PartBlocker.noteScreen(pkg, cls, title);
            if (!enforcing()) return;
            if (!PartBlocker.picking && (LockdownPrefs.getSet(this, "tapBlockedScreens").contains(pkg + "/" + cls)
                    || (!title.isEmpty() && LockdownPrefs.getSet(this, "tapBlockedTitles").contains(pkg + "|" + title)))) {
                android.widget.Toast.makeText(getApplicationContext(), "This screen is not allowed on this phone.", android.widget.Toast.LENGTH_SHORT).show();
                long now = System.currentTimeMillis();
                if (now - lastBack > 600) {
                    lastBack = now;
                    performGlobalAction(GLOBAL_ACTION_BACK);
                }
                return;
            }
        }
        if (!enforcing()) {
            PartBlocker.clearCovers(this);
            return;
        }
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED || type == AccessibilityEvent.TYPE_VIEW_SCROLLED)
            PartBlocker.onScreen(this, pkg);
    }

    @Override
    public void onInterrupt() {
    }

    /** Like the rest of Lockdown: only while the Lockdown switch is on and no emergency unlock is in place. */
    private boolean enforcing() {
        return LockdownPrefs.prefs(this).getBoolean("lockdownOn", false) && !EmergencyUnlock.isActive(this);
    }

    static boolean on(android.content.Context c) {
        String enabled = android.provider.Settings.Secure.getString(c.getContentResolver(), android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabled != null && enabled.contains(c.getPackageName() + "/");
    }
}
