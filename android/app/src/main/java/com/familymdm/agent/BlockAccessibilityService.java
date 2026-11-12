package com.familymdm.agent;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import com.familymdm.agent.sitepolicy.InAppRules;

import java.util.ArrayDeque;

/**
 * Two jobs, both switched on from the dashboard, both needing this service turned on once in the
 * phone's Settings → Accessibility (Android allows no app to turn it on for itself):
 * 1. Blocked app screens (AppBlocklist): backs out of them the instant they open.
 * 2. Web pages inside apps (InAppRules): closes the browser page an app opens on top of itself
 *    and, in the stricter mode, any app screen that contains a web page.
 * 3. Message alerts and screenshots on request (see Monitor), only when switched on from the
 *    dashboard; the phone tells the child when either is used. Otherwise it looks only at what
 *    kind of screen is open (class names), never at what is written on it.
 */
public class BlockAccessibilityService extends AccessibilityService {
    /** The running service, for Monitor.screenshot(); null while switched off. */
    static volatile BlockAccessibilityService instance;
    private String lastPkg;
    private long lastSearchLook;

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
    private long lastWindowAt, lastCheckAt, lastNoteAt;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        CharSequence pkgCs = event.getPackageName();
        CharSequence clsCs = event.getClassName();
        if (pkgCs == null) return;
        String pkg = pkgCs.toString();
        long now = System.currentTimeMillis();
        // Tap to block: parts of apps (covered or closed), on every screen change and scroll.
        int type = event.getEventType();
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED || type == AccessibilityEvent.TYPE_VIEW_SCROLLED) PartBlocker.onScreen(this, pkg);

        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (clsCs == null) return;
            String component = pkg + "/" + clsCs;
            AppBlocklist.recordSeen(this, component);
            PartBlocker.noteScreen(pkg, clsCs.toString());
            if (AppBlocklist.list(this).contains(component) && !AppBlocklist.isPaused(this, component)) {
                performGlobalAction(GLOBAL_ACTION_BACK);
                Agent.addEvent(this, "restriction", "Blocked app/screen opened (" + component + ") -- backed out instantly");
                return;
            }
            if (InAppRules.whatsAppBlocks(pkg, clsCs.toString(), Protection.waStatus(this), Protection.waChannels(this))) {
                performGlobalAction(GLOBAL_ACTION_BACK);
                if (now - lastNoteAt > 60000) {
                    lastNoteAt = now;
                    Agent.addEvent(this, "restriction", "Closed WhatsApp " + (clsCs.toString().toLowerCase().contains("newsletter") ? "Channels" : "Status"));
                    new Handler(Looper.getMainLooper()).post(() ->
                            Toast.makeText(getApplicationContext(), "This part of WhatsApp is turned off on this phone.", Toast.LENGTH_SHORT).show());
                }
                return;
            }
            lastPkg = pkg;
            lastWindowAt = now;
            checkInApp(pkg, clsCs.toString(), now);
            if (Monitor.on(this)) Monitor.scan(this, pkg, getRootInActiveWindow());
            Monitor.trackSearch(this, pkg, getRootInActiveWindow(), true);
        } else if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (now - lastSearchLook > 1000) {
                lastSearchLook = now;
                Monitor.trackSearch(this, pkg, getRootInActiveWindow(), false);
            }
            if (Monitor.on(this)) {
                Monitor.scan(this, pkg, getRootInActiveWindow());
                Monitor.scanSearch(this, pkg, getRootInActiveWindow());
            }
            // A web page often appears a moment after the screen opens: look again for a few
            // seconds after each new screen, at most every half second.
            if (pkg.equals(lastPkg) && now - lastWindowAt < 4000 && now - lastCheckAt > 500) checkInApp(pkg, null, now);
        }
    }

    private void checkInApp(String pkg, String cls, long now) {
        String mode = Protection.inAppMode(this);
        if ("off".equals(mode)) return;
        lastCheckAt = now;
        boolean webView = false;
        if ("all".equals(mode) && !InAppRules.NEVER.contains(pkg) && !Protection.inAppExceptions(this).contains(pkg)) {
            webView = hasWebView(getRootInActiveWindow());
        }
        if (!InAppRules.blocks(mode, pkg, cls, webView, Protection.inAppExceptions(this))) return;
        performGlobalAction(GLOBAL_ACTION_BACK);
        lastWindowAt = 0; // handled; don't look again for this screen
        if (now - lastNoteAt > 60000) { // at most one log line and message a minute
            lastNoteAt = now;
            Agent.addEvent(this, "restriction", "Closed a web page inside an app (" + pkg + ")");
            new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(getApplicationContext(), "Web pages inside apps are turned off on this phone.", Toast.LENGTH_SHORT).show());
        }
    }

    /** Does this screen contain a web page? Looks at up to 400 parts of it, by kind only. */
    private static boolean hasWebView(AccessibilityNodeInfo root) {
        if (root == null) return false;
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        int seen = 0;
        while (!q.isEmpty() && seen++ < 400) {
            AccessibilityNodeInfo n = q.poll();
            CharSequence c = n.getClassName();
            if (c != null && "android.webkit.WebView".contentEquals(c)) return true;
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo ch = n.getChild(i);
                if (ch != null) q.add(ch);
            }
        }
        return false;
    }

    @Override
    public void onInterrupt() {
    }
}
