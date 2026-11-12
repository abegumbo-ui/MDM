package com.familymdm.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.RestrictionsManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.familymdm.agent.sitepolicy.SitePolicy;
import com.familymdm.agent.sitepolicy.YoutubeRestricted;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * A regular tabbed browser with four modes, decided fresh each time a page is checked (mode()):
 *
 *  - AGENT: the separate agent app (device owner) has pushed a site list through Android's
 *    managed-configuration channel. Takes priority over everything else, since it's backed by
 *    device-owner control.
 *  - ONLINE: no agent, but this exact Browser install was connected directly to a dashboard with
 *    its own one-time code (see BrowserState/Api). Sites come from the dashboard's global allowlist,
 *    cached locally and refreshed opportunistically.
 *  - OFFLINE: no longer offered -- the Browser is run only from the dashboard (or the agent). An old
 *    "set up on its own" install counts as UNCONFIGURED.
 *  - UNCONFIGURED: not connected yet. Deny-by-default: nothing opens until it is connected to the
 *    dashboard. This is the fresh-install state.
 */
public class BrowserActivity extends Activity {
    private static final String AGENT_PACKAGE = "com.familymdm.agent";
    private static final String PROVIDER_URI = "content://com.familymdm.agent.provider";
    private static final String SITE_REQUEST_ACTION = "com.familymdm.agent.action.SITE_REQUEST";
    private static final String HOME_URL = "about:home";
    private static final long ONLINE_SYNC_MIN_INTERVAL_MS = 45_000;

    private enum Mode { AGENT, ONLINE, OFFLINE, UNCONFIGURED }

    private final List<Tab> tabs = new ArrayList<>();
    private int currentIndex = -1;
    private volatile boolean syncing;
    // True while the mode is one the dashboard/agent controls (AGENT or ONLINE) and a filter level is
    // set. Kept as a plain flag so checking every image and script a page loads stays cheap.
    private volatile boolean filterModeOk;
    private volatile List<SitePolicy.Site> interceptSites = new ArrayList<>();
    private volatile long interceptSitesAt;
    // A session-only toggle from the shortcut menu (not persisted, like Chrome's own "Desktop site"
    // switch) -- applies to every tab, current and future, until turned off again.
    private boolean desktopMode;
    private static final String DESKTOP_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private LinearLayout tabStrip;
    private View tabStripScroll, classicBar, chromeBar;
    private EditText chromeAddress;
    private TextView tabCount;
    // "Chrome look": white bar, rounded address box with the site's name, tab count, no tab strip.
    private static final int C_BAR = 0xFFFFFFFF, C_FIELD = 0xFFF1F3F4, C_INK = 0xFF202124, C_MUTED = 0xFF5F6368;

    private boolean chromeLook() {
        return getSharedPreferences("look", MODE_PRIVATE).getBoolean("chrome", false);
    }

    private void applyLook() {
        boolean c = chromeLook();
        tabStripScroll.setVisibility(c ? View.GONE : View.VISIBLE);
        classicBar.setVisibility(c ? View.GONE : View.VISIBLE);
        chromeBar.setVisibility(c ? View.VISIBLE : View.GONE);
        getWindow().setStatusBarColor(c ? C_BAR : BAR_DARK);
        View decor = getWindow().getDecorView();
        int flags = decor.getSystemUiVisibility();
        decor.setSystemUiVisibility(c ? flags | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR : flags & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        syncToolbar();
    }

    private LinearLayout buildChromeToolbar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(C_BAR);
        bar.setPadding(dp(4), dp(6), dp(4), dp(6));
        bar.setElevation(dp(2));
        bar.addView(iconButton("⌂", C_INK, v -> { if (current() != null) load(current(), HOME_URL); }));
        chromeAddress = new EditText(this);
        chromeAddress.setSingleLine(true);
        chromeAddress.setHint("Type web address");
        chromeAddress.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        chromeAddress.setImeOptions(EditorInfo.IME_ACTION_GO);
        chromeAddress.setSelectAllOnFocus(true);
        chromeAddress.setBackground(rounded(C_FIELD, 24));
        chromeAddress.setTextColor(C_INK);
        chromeAddress.setHintTextColor(C_MUTED);
        chromeAddress.setTextSize(15);
        chromeAddress.setPadding(dp(16), dp(10), dp(16), dp(10));
        chromeAddress.setOnFocusChangeListener((v, has) -> {
            Tab ct = current();
            if (has && ct != null) { chromeAddress.setText(HOME_URL.equals(ct.url) || ct.url == null ? "" : ct.url); chromeAddress.selectAll(); }
            else syncToolbar();
        });
        chromeAddress.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                go(chromeAddress.getText().toString());
                chromeAddress.clearFocus();
                ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(chromeAddress.getWindowToken(), 0);
                return true;
            }
            return false;
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = dp(2);
        lp.rightMargin = dp(6);
        bar.addView(chromeAddress, lp);
        // The tab count in a rounded square, like Chrome's tab switcher button.
        tabCount = new TextView(this);
        tabCount.setGravity(Gravity.CENTER);
        tabCount.setTextColor(C_INK);
        tabCount.setTextSize(12);
        tabCount.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        android.graphics.drawable.GradientDrawable sq = new android.graphics.drawable.GradientDrawable();
        sq.setStroke(dp(2), C_INK);
        sq.setCornerRadius(dp(4));
        tabCount.setBackground(sq);
        tabCount.setOnClickListener(v -> showTabSwitcher());
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(dp(22), dp(22));
        tl.leftMargin = dp(8);
        tl.rightMargin = dp(8);
        bar.addView(tabCount, tl);
        bar.addView(iconButton("⋮", C_INK, this::showMenu));
        return bar;
    }

    private void showTabSwitcher() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        AlertDialog dialog = alertDialog().setTitle(tabs.size() == 1 ? "1 tab" : tabs.size() + " tabs").setView(box).create();
        for (int i = 0; i < tabs.size(); i++) {
            final int idx = i;
            Tab t = tabs.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(20), dp(12), dp(12), dp(12));
            TextView label = new TextView(this);
            String title = t.title == null || t.title.isEmpty() ? "New tab" : t.title;
            label.setText((i == currentIndex ? "● " : "") + title);
            label.setSingleLine(true);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            label.setTextSize(16);
            label.setTextColor(getColor(R.color.m3_on_surface));
            row.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            TextView close = new TextView(this);
            close.setText("✕");
            close.setTextSize(16);
            close.setPadding(dp(12), 0, dp(4), 0);
            close.setTextColor(getColor(R.color.m3_on_surface));
            close.setOnClickListener(v -> { dialog.dismiss(); closeTab(idx); });
            row.addView(close);
            row.setOnClickListener(v -> { dialog.dismiss(); showTab(idx); });
            box.addView(row);
        }
        menuRow(box, "  ＋  New tab", v -> { dialog.dismiss(); showTab(newTab(HOME_URL)); });
        dialog.show();
    }

    /** What the Chrome-look address box shows when not being typed in: the site's name, like Chrome. */
    private static String shortAddress(String url) {
        try {
            String host = Uri.parse(url).getHost();
            if (host == null) return url;
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception e) {
            return url;
        }
    }
    private FrameLayout contentHost;
    private EditText addressBar;
    private ImageButton backBtn, fwdBtn, reloadBtn;

    private final BroadcastReceiver restrictionsChanged = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Tab t = current();
            if (t != null && HOME_URL.equals(t.url)) renderHome(t);
        }
    };

    /** One browser tab: its own WebView, plus a blocked/overlay screen built lazily when it's needed. */
    private static final class Tab {
        final FrameLayout root;
        final WebView webView;
        LinearLayout overlay;
        View homeView;
        String url = HOME_URL;
        String title = "New tab";

        Tab(Context c) {
            root = new FrameLayout(c);
            webView = new WebView(c);
            root.addView(webView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE); // the page area: web pages are drawn on white
        getWindow().setStatusBarColor(BAR_DARK);
        if (getActionBar() != null) getActionBar().hide(); // the tab strip and toolbar are the whole top bar

        root.addView(tabStripScroll = buildTabStrip());
        root.addView(classicBar = buildToolbar());
        root.addView(chromeBar = buildChromeToolbar());

        contentHost = new FrameLayout(this);
        root.addView(contentHost, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        newTab(startUrl(getIntent()));
        showTab(0);
        applyLook();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String url = startUrl(intent);
        if (url != null) showTab(newTab(url));
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(restrictionsChanged, new IntentFilter(Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED));
        applyFilterPolicy();
        if (mode() == Mode.ONLINE) maybeSyncOnline(false);
    }

    /**
     * The category filter (Moderate / Strict) only ever applies where a dashboard or the agent is in
     * charge (AGENT and ONLINE). OFFLINE and UNCONFIGURED stay on "approved sites only".
     */
    private void applyFilterPolicy() {
        try {
            Mode m = mode();
            if (m == Mode.AGENT) {
                RestrictionsManager rm = (RestrictionsManager) getSystemService(Context.RESTRICTIONS_SERVICE);
                Bundle b = rm == null ? null : rm.getApplicationRestrictions();
                FilterStore.setPolicy(this, b == null ? "off" : b.getString("filterTier", "off"), b == null ? "" : b.getString("filterBase", ""));
                FilterStore.setYoutubeMode(this, b == null ? "channels" : b.getString("youtubeMode", "channels"));
                FilterStore.setBlockImages(this, b != null && b.getBoolean("blockImages", false));
                FilterStore.setBlockedWords(this, b == null ? null : b.getStringArray("blockedWords"));
                FilterStore.setBlockImageSearch(this, b != null && b.getBoolean("blockImageSearch", false));
                FilterStore.setPictureGuard(this, b == null ? "off" : b.getString("pictureGuard", "off"));
            } else if (m == Mode.ONLINE) {
                FilterStore.refreshAsync(this, false);
            } else {
                FilterStore.setPolicy(this, "off", "");
                FilterStore.setYoutubeMode(this, "channels");
                FilterStore.setBlockImages(this, false);
                FilterStore.setBlockedWords(this, null);
                FilterStore.setBlockImageSearch(this, false);
                FilterStore.setPictureGuard(this, "off");
            }
            filterModeOk = (m == Mode.AGENT || m == Mode.ONLINE) && !"off".equals(FilterStore.tier(this));
        } catch (Exception e) {
            filterModeOk = false;
        }
    }

    @Override
    protected void onPause() {
        try {
            unregisterReceiver(restrictionsChanged);
        } catch (Exception ignored) {
        }
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        Tab t = current();
        if (t != null && t.webView.getVisibility() == View.VISIBLE && t.webView.canGoBack()) {
            t.webView.goBack();
        } else if (tabs.size() > 1) {
            closeTab(currentIndex);
        } else {
            super.onBackPressed();
        }
    }

    private String startUrl(Intent intent) {
        String url = intent == null ? null : intent.getStringExtra("url");
        if (url == null && intent != null && intent.getData() != null) url = intent.getData().toString();
        return url == null ? HOME_URL : url;
    }

    // ---------- the two toolbars ----------

    // The same dark look as the Agent and the dashboard (see res/values/colors.xml).
    private static final int BAR_DARK = 0xFF0B0D12, BAR = 0xFF12151C, FIELD = 0xFF1D222C, ON_BAR = 0xFFE4E7EC,
            ON_BAR_MUTED = 0xFF98A2B3, INK = 0xFF101828, INK_MUTED = 0xFF475467, ACCENT = 0xFF4F46E5;

    private android.graphics.drawable.GradientDrawable rounded(int fill, int radiusDp) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private HorizontalScrollView buildTabStrip() {
        tabStrip = new LinearLayout(this);
        tabStrip.setOrientation(LinearLayout.HORIZONTAL);
        tabStrip.setPadding(dp(4), dp(6), dp(4), dp(0));
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.addView(tabStrip);
        scroll.setBackgroundColor(BAR_DARK);
        return scroll;
    }

    private LinearLayout buildToolbar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(BAR);
        int pad = dp(6);
        bar.setPadding(pad, pad, pad, pad);

        backBtn = iconButton("‹", ON_BAR, v -> { if (current() != null && current().webView.canGoBack()) current().webView.goBack(); });
        fwdBtn = iconButton("›", ON_BAR, v -> { if (current() != null && current().webView.canGoForward()) current().webView.goForward(); });
        reloadBtn = iconButton("⟳", ON_BAR, v -> { if (current() != null) current().webView.reload(); });
        bar.addView(backBtn);
        bar.addView(fwdBtn);
        bar.addView(reloadBtn);

        addressBar = new EditText(this);
        addressBar.setSingleLine(true);
        addressBar.setHint("Type a full web address, e.g. example.com");
        addressBar.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        addressBar.setImeOptions(EditorInfo.IME_ACTION_GO);
        addressBar.setBackground(rounded(FIELD, 20));
        addressBar.setTextColor(ON_BAR);
        addressBar.setHintTextColor(ON_BAR_MUTED);
        addressBar.setTextSize(15);
        addressBar.setPadding(dp(14), dp(9), dp(14), dp(9));
        addressBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                go(addressBar.getText().toString());
                return true;
            }
            return false;
        });
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        barLp.leftMargin = dp(4);
        barLp.rightMargin = dp(4);
        bar.addView(addressBar, barLp);

        bar.addView(iconButton("+", ON_BAR, v -> showTab(newTab(HOME_URL))));
        bar.addView(iconButton("⋮", ON_BAR, this::showMenu));
        return bar;
    }

    private ImageButton iconButton(String glyph, int color, View.OnClickListener l) {
        ImageButton b = new ImageButton(this);
        b.setImageDrawable(textDrawable(glyph, color));
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setOnClickListener(l);
        b.setPadding(dp(10), dp(10), dp(10), dp(10));
        return b;
    }

    /** No icon assets in this app on purpose: draw the toolbar glyphs as text instead. */
    private android.graphics.drawable.Drawable textDrawable(String glyph, int color) {
        android.graphics.Paint paint = new android.graphics.Paint();
        paint.setAntiAlias(true);
        paint.setTextSize(dp(20));
        paint.setColor(color);
        paint.setTextAlign(android.graphics.Paint.Align.CENTER);
        android.graphics.Rect b = new android.graphics.Rect();
        paint.getTextBounds(glyph, 0, glyph.length(), b);
        int size = dp(28);
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(bmp);
        c.drawText(glyph, size / 2f, size / 2f - b.exactCenterY(), paint);
        return new android.graphics.drawable.BitmapDrawable(getResources(), bmp);
    }

    /** A proper full list, themed to match the rest of this project, instead of a bare native
     * PopupMenu (unthemed on this app's end -- no res/values existed here at all before this). */
    private void showMenu(View anchor) {
        final Tab t = current();
        final Mode mode = mode();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        AlertDialog dialog = alertDialog().setView(box).create();

        if (mode == Mode.AGENT && browseWindowActive()) {
            RestrictionsManager rm = (RestrictionsManager) getSystemService(Context.RESTRICTIONS_SERVICE);
            Bundle b = rm == null ? null : rm.getApplicationRestrictions();
            long until = b == null ? 0 : b.getLong("browseUntil", 0);
            TextView notice = body("Free browsing until " + android.text.format.DateFormat.getTimeFormat(this).format(new java.util.Date(until)));
            notice.setTextColor(getColor(R.color.m3_on_surface_variant)); // this one sits on the dark menu, not a white page
            notice.setPadding(0, dp(4), 0, dp(12));
            box.addView(notice);
        }

        if (chromeLook() && t != null) {
            // Chrome keeps back, forward and reload at the top of its menu.
            LinearLayout nav = new LinearLayout(this);
            nav.setOrientation(LinearLayout.HORIZONTAL);
            int ink = getColor(R.color.m3_on_surface);
            for (Object[] it : new Object[][]{{"‹", (View.OnClickListener) v -> { dialog.dismiss(); if (t.webView.canGoBack()) t.webView.goBack(); }},
                    {"›", (View.OnClickListener) v -> { dialog.dismiss(); if (t.webView.canGoForward()) t.webView.goForward(); }},
                    {"⟳", (View.OnClickListener) v -> { dialog.dismiss(); t.webView.reload(); }}}) {
                nav.addView(iconButton((String) it[0], ink, (View.OnClickListener) it[1]), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            }
            box.addView(nav);
        }
        menuRow(box, "New tab", v -> { dialog.dismiss(); showTab(newTab(HOME_URL)); });
        if (tabs.size() > 1) menuRow(box, "Close this tab", v -> { dialog.dismiss(); closeTab(currentIndex); });
        if (t != null && canAddToHomeScreen(t)) menuRow(box, "Add to Home Screen", v -> { dialog.dismiss(); addToHomeScreen(); });
        menuCheckRow(box, "Desktop Mode", desktopMode, (btn, on) -> setDesktopMode(on));
        menuCheckRow(box, "Chrome look", chromeLook(), (btn, on) -> {
            getSharedPreferences("look", MODE_PRIVATE).edit().putBoolean("chrome", on).apply();
            dialog.dismiss();
            applyLook();
        });
        menuRow(box, "Clear Cache", v -> { dialog.dismiss(); clearBrowserCache(); });
        if (mode != Mode.UNCONFIGURED) menuRow(box, "Allowed sites", v -> { dialog.dismiss(); showAllowedSitesDialog(); });
        if (mode == Mode.ONLINE) menuRow(box, "Sync now", v -> { dialog.dismiss(); maybeSyncOnline(true); toast("Syncing..."); });
        if (mode == Mode.OFFLINE) menuRow(box, "Manage sites (master code)", v -> { dialog.dismiss(); promptMasterThenManageSites(); });
        if (mode == Mode.UNCONFIGURED) menuRow(box, "Set up Browser", v -> { dialog.dismiss(); promptWhitelistMode(current(), null); });
        if (mode == Mode.ONLINE || mode == Mode.OFFLINE) menuRow(box, "Disconnect this setup", v -> { dialog.dismiss(); disconnectSetup(); });
        menuRow(box, "Exit", v -> { dialog.dismiss(); finish(); });

        dialog.show();
    }

    private void menuRow(LinearLayout box, String label, View.OnClickListener onClick) {
        TextView row = new TextView(this);
        row.setText(label);
        row.setTextSize(16);
        row.setTextColor(getColor(R.color.m3_on_surface));
        row.setPadding(dp(4), dp(14), dp(4), dp(14));
        row.setOnClickListener(onClick);
        box.addView(row);
    }

    private void menuCheckRow(LinearLayout box, String label, boolean checked, android.widget.CompoundButton.OnCheckedChangeListener onChange) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(10), dp(4), dp(10));
        TextView label2 = new TextView(this);
        label2.setText(label);
        label2.setTextSize(16);
        label2.setTextColor(getColor(R.color.m3_on_surface));
        row.addView(label2, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        android.widget.CheckBox box2 = new android.widget.CheckBox(this);
        box2.setButtonTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.m3_primary)));
        box2.setChecked(checked);
        box2.setOnCheckedChangeListener(onChange);
        row.addView(box2);
        row.setOnClickListener(v -> box2.setChecked(!box2.isChecked())); // whole row tappable, not just the box
        box.addView(row);
    }

    /** Every dialog in this app goes through this instead of a bare AlertDialog.Builder -- see the
     * comment on AppAlertDialogTheme (themes.xml) for why that has to be passed explicitly. */
    private AlertDialog.Builder alertDialog() {
        return new AlertDialog.Builder(this, R.style.AppAlertDialogTheme);
    }

    private void setDesktopMode(boolean on) {
        desktopMode = on;
        for (Tab t : tabs) t.webView.getSettings().setUserAgentString(on ? DESKTOP_UA : null);
        if (current() != null && current().webView.getUrl() != null) current().webView.reload();
    }

    private void clearBrowserCache() {
        for (Tab t : tabs) t.webView.clearCache(true);
        toast("Cache cleared.");
    }

    private void disconnectSetup() {
        alertDialog()
                .setTitle("Disconnect this setup?")
                .setMessage("Browser goes back to allowing nothing until it's connected to the dashboard again.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Disconnect", (d, w) -> {
                    BrowserState.prefs(this).edit().clear().apply();
                    if (current() != null) renderHome(current());
                    toast("Disconnected.");
                })
                .show();
    }

    // ---------- tabs ----------

    private Tab current() {
        return currentIndex >= 0 && currentIndex < tabs.size() ? tabs.get(currentIndex) : null;
    }

    /** Creates a tab and loads it right away; returns its index. */
    private int newTab(String url) {
        Tab t = new Tab(this);
        setupWebView(t);
        tabs.add(t);
        int index = tabs.size() - 1;
        contentHost.addView(t.root, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        t.root.setVisibility(View.GONE);
        load(t, url);
        rebuildTabStrip();
        return index;
    }

    private void closeTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        boolean wasCurrent = index == currentIndex;
        Tab t = tabs.remove(index);
        contentHost.removeView(t.root);
        t.webView.destroy();
        if (tabs.isEmpty()) {
            finish();
            return;
        }
        if (wasCurrent) {
            currentIndex = -1; // the tab current() would point to is already destroyed
            showTab(Math.min(index, tabs.size() - 1));
        } else {
            if (index < currentIndex) currentIndex--;
            rebuildTabStrip();
        }
    }

    private void showTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        if (current() != null) current().root.setVisibility(View.GONE);
        currentIndex = index;
        current().root.setVisibility(View.VISIBLE);
        rebuildTabStrip();
        syncToolbar();
    }

    private void rebuildTabStrip() {
        if (tabCount != null) tabCount.setText(String.valueOf(tabs.size()));
        tabStrip.removeAllViews();
        for (int i = 0; i < tabs.size(); i++) {
            final int idx = i;
            Tab t = tabs.get(i);
            LinearLayout chip = new LinearLayout(this);
            chip.setOrientation(LinearLayout.HORIZONTAL);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setPadding(dp(12), dp(8), dp(8), dp(8));
            if (i == currentIndex) chip.setBackground(rounded(BAR, 10));
            else chip.setBackgroundColor(Color.TRANSPARENT);
            TextView label = new TextView(this);
            String title = t.title == null || t.title.isEmpty() ? "New tab" : t.title;
            label.setText(title.length() > 16 ? title.substring(0, 16) + "…" : title);
            label.setTextColor(i == currentIndex ? ON_BAR : ON_BAR_MUTED);
            chip.addView(label);
            TextView close = new TextView(this);
            close.setText("  ×");
            close.setTextColor(ON_BAR_MUTED);
            close.setOnClickListener(v -> closeTab(idx));
            chip.addView(close);
            chip.setOnClickListener(v -> showTab(idx));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(2);
            tabStrip.addView(chip, lp);
        }
    }

    private void syncToolbar() {
        Tab t = current();
        if (t == null) return;
        addressBar.setText(HOME_URL.equals(t.url) ? "" : t.url);
        backBtn.setEnabled(t.webView.canGoBack());
        fwdBtn.setEnabled(t.webView.canGoForward());
        if (chromeAddress != null && !chromeAddress.hasFocus()) chromeAddress.setText(HOME_URL.equals(t.url) || t.url == null ? "" : shortAddress(t.url));
        if (tabCount != null) tabCount.setText(String.valueOf(tabs.size()));
    }

    // ---------- navigation ----------

    /** Only a full address is accepted here — no search box behind it, so there's nothing to search for. */
    private void go(String typed) {
        String s = typed.trim();
        if (s.isEmpty()) return;
        if (looksLikeUrl(s)) {
            load(current(), s.contains("://") ? s : "https://" + s);
            return;
        }
        // Free browsing (a redeemed code) is temporary full access by design, like a timed Play
        // Store window for app installs — so a search works here too, and the sites visited still
        // need the administrator's approval afterward, same as a newly installed app would.
        if (mode() == Mode.AGENT && browseWindowActive()) {
            load(current(), withSafeSearch("https://www.google.com/search?q=" + Uri.encode(s)));
            return;
        }
        toast("Type a full web address, like example.com — there's no search here.");
    }

    private boolean looksLikeUrl(String s) {
        return s.contains(".") && !s.contains(" ");
    }

    private static final java.util.regex.Pattern GOOGLE_HOST = java.util.regex.Pattern.compile("^(www\\.)?google\\.[a-z.]+$");

    /**
     * Forces Google's SafeSearch on, on every Google page this browser ever loads — not just our
     * own typed searches, but any link clicked inside Google's own results too, and any Google
     * page an administrator allowed directly. Overwrites an existing "safe" parameter, so a typed
     * "&amp;safe=off" address can't turn it back off either. Not a device-wide DNS-level lock, just
     * this browser's own pipeline — but every navigation goes through this pipeline.
     */
    private String withSafeSearch(String url) {
        try {
            Uri u = Uri.parse(url);
            String host = u.getHost();
            if (host == null || !GOOGLE_HOST.matcher(host.toLowerCase(java.util.Locale.ROOT)).matches()) return url;
            if ("active".equals(u.getQueryParameter("safe"))) return url;
            Uri.Builder b = u.buildUpon().clearQuery();
            for (String name : u.getQueryParameterNames()) {
                if (name.equals("safe")) continue;
                for (String v : u.getQueryParameters(name)) b.appendQueryParameter(name, v);
            }
            b.appendQueryParameter("safe", "active");
            return b.build().toString();
        } catch (Exception e) {
            return url;
        }
    }

    private void setupWebView(final Tab t) {
        t.webView.getSettings().setJavaScriptEnabled(true);
        t.webView.getSettings().setDomStorageEnabled(true);
        if (desktopMode) t.webView.getSettings().setUserAgentString(DESKTOP_UA);
        t.webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                String safe = withSafeSearch(url);
                if (!safe.equals(url)) {
                    // A link clicked inside Google itself (not our own address bar): re-run it
                    // through load() so SafeSearch is forced the same way a typed search is.
                    load(t, safe);
                    return true;
                }
                return !allowedOrBlock(t, url);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                try {
                    // "Pictures in our Browser": every picture is checked on the phone first (PictureGuard).
                    String guard = FilterStore.pictureGuard(BrowserActivity.this);
                    if (!"off".equals(guard) && !FilterStore.blockImages(BrowserActivity.this) && PictureGuard.isPicture(request)) {
                        String gh = SitePolicy.hostOf(request.getUrl().toString());
                        boolean filterBlocks = filterModeOk && FilterStore.active(BrowserActivity.this) && gh != null && FilterStore.blocks(BrowserActivity.this, gh);
                        if (!filterBlocks) return PictureGuard.handle(BrowserActivity.this, request, guard);
                    }
                } catch (Exception e) {
                    // fall through to the normal filter
                }
                try {
                    if (!filterModeOk || !FilterStore.active(BrowserActivity.this)) return null;
                    String url = request.getUrl().toString();
                    String host = SitePolicy.hostOf(url);
                    if (host == null || !FilterStore.blocks(BrowserActivity.this, host)) return null;
                    // Something in a blocked category: still allowed if it is on the approved list, or during a free-browsing window.
                    Mode m = mode();
                    if (SitePolicy.matching(url, interceptSitesNow(m)) != null) return null;
                    if (m == Mode.AGENT && browseWindowActive()) return null;
                    if (request.isForMainFrame()) runOnUiThread(() -> allowedOrBlock(t, url));
                    return new WebResourceResponse("text/plain", "utf-8", 403, "Blocked", new HashMap<String, String>(), new ByteArrayInputStream(new byte[0]));
                } catch (Exception e) {
                    return null; // never break a page because the filter hit a problem
                }
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                super.doUpdateVisitedHistory(view, url, isReload);
                if (url == null || t.webView != view || !SitePolicy.isYoutubeHost(SitePolicy.hostOf(url))) return;
                // YouTube switches video or page in place (no page load), so this is the only signal.
                if (!allowedOrBlock(t, url)) {
                    view.stopLoading();
                    view.loadUrl("about:blank"); // otherwise the video keeps playing sound under the blocked screen
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if ("about:blank".equals(url)) return; // the blank page used to stop a blocked video; keep the real address shown
                if (url != null && filterModeOk && FilterStore.youtubeRestricted(BrowserActivity.this) && isYoutubeAddress(url)) {
                    view.evaluateJavascript(YoutubeRestricted.HIDE_SCRIPT, null);
                }
                t.url = url;
                if (t == current()) syncToolbar();
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel(); // never ignore a broken certificate, even on an allowed site
            }
        });
        t.webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onReceivedTitle(WebView view, String title) {
                t.title = title;
                rebuildTabStrip();
            }
        });
    }

    /** Loads `url` into the tab's WebView (or the home page) after the allowlist check, if any. */
    private void load(Tab t, String url) {
        if (HOME_URL.equals(url)) {
            renderHome(t);
            return;
        }
        url = withSafeSearch(url);
        if (!allowedOrBlock(t, url)) return;
        showWeb(t);
        Mode m = mode();
        if ((m == Mode.AGENT || m == Mode.ONLINE) && FilterStore.youtubeRestricted(this) && isYoutubeAddress(url)) {
            // Ask YouTube itself to filter strictly too (best effort, on this first request of the page).
            java.util.Map<String, String> headers = new java.util.HashMap<>();
            headers.put(YoutubeRestricted.RESTRICT_HEADER, YoutubeRestricted.RESTRICT_VALUE);
            t.webView.loadUrl(url, headers);
        } else {
            t.webView.loadUrl(url);
        }
    }

    /** True if `url` may load; otherwise shows the right screen for the current mode and returns false. */
    private boolean allowedOrBlock(Tab t, String url) {
        if (SitePolicy.isBlockedAdult(url)) {
            showAdultBlocked(t);
            return false;
        }
        Mode mode = mode();
        if (mode == Mode.UNCONFIGURED) {
            showSetupRequired(t, url);
            return false;
        }
        if ((mode == Mode.AGENT || mode == Mode.ONLINE) && com.familymdm.agent.sitepolicy.WordFilter.match(url, FilterStore.blockedWords(this)) != null) {
            showNotAvailable(t, "This page or search has a blocked word in it.");
            return false;
        }
        if ((mode == Mode.AGENT || mode == Mode.ONLINE) && FilterStore.blockImageSearch(this) && com.familymdm.agent.sitepolicy.ProtectionRules.isImageSearch(url)) {
            showNotAvailable(t, "Image search is turned off.");
            return false;
        }
        if ((mode == Mode.AGENT || mode == Mode.ONLINE) && FilterStore.youtubeRestricted(this) && isYoutubeAddress(url)) {
            int kind = SitePolicy.youtubeKind(url);
            if (kind == SitePolicy.YT_HOME) {
                showYoutubeSearch(t);
                return false;
            }
            if (SitePolicy.youtubeRestrictedAllows(url)) {
                t.webView.getSettings().setBlockNetworkImage(FilterStore.blockImages(this));
                t.webView.getSettings().setLoadsImagesAutomatically(!FilterStore.blockImages(this));
                return true;
            }
            showNotAvailable(t, "This part of YouTube isn't available.");
            return false;
        }
        List<SitePolicy.Site> sitesNow = currentSites(mode);
        SitePolicy.Site match = SitePolicy.matching(url, sitesNow);
        if (match == null) {
            // Allowed YouTube channels: their pages and their videos open, the rest of YouTube does not.
            int yt = youtubeCheck(t, url, sitesNow);
            if (yt == YT_PENDING) return false;
            if (yt == YT_ALLOW) {
                t.webView.getSettings().setBlockNetworkImage(FilterStore.blockImages(this));
                t.webView.getSettings().setLoadsImagesAutomatically(!FilterStore.blockImages(this));
                return true;
            }
            if (mode == Mode.AGENT && browseWindowActive()) {
                reportNewHostOnce(url);
                t.webView.getSettings().setBlockNetworkImage(FilterStore.blockImages(this));
                t.webView.getSettings().setLoadsImagesAutomatically(!FilterStore.blockImages(this));
                return true;
            }
            // Moderate / Strict filter: everything is open except the blocked categories. Only once the
            // lists are downloaded and verified; until then this stays "approved sites only".
            if ((mode == Mode.AGENT || mode == Mode.ONLINE) && FilterStore.active(this)) {
                String host = SitePolicy.hostOf(url);
                if (host != null && !FilterStore.blocks(this, host)) {
                    t.webView.getSettings().setBlockNetworkImage(FilterStore.blockImages(this));
                    t.webView.getSettings().setLoadsImagesAutomatically(!FilterStore.blockImages(this));
                    return true;
                }
            }
            showBlocked(t, url, mode);
            return false;
        }
        t.webView.getSettings().setBlockNetworkImage(match.blockImages || FilterStore.blockImages(this));
        t.webView.getSettings().setLoadsImagesAutomatically(!(match.blockImages || FilterStore.blockImages(this)));
        return true;
    }

    private static boolean isYoutubeAddress(String url) {
        String host = SitePolicy.hostOf(url);
        return SitePolicy.isYoutubeHost(host) || "consent.youtube.com".equals(host) || "accounts.youtube.com".equals(host);
    }

    /** Restricted YouTube's home page: just a search box, never the recommendation feed. */
    private void showYoutubeSearch(final Tab t) {
        showWeb(t);
        t.webView.loadDataWithBaseURL(null, YoutubeRestricted.searchPageHtml(), "text/html", "utf-8", null);
    }

    private void showNotAvailable(final Tab t, String message) {
        t.webView.setVisibility(View.GONE);
        if (t.homeView != null) t.homeView.setVisibility(View.GONE);
        ensureOverlay(t);
        t.overlay.setVisibility(View.VISIBLE);
        t.overlay.removeAllViews();
        t.overlay.addView(title("Not available"));
        TextView sub = body(message);
        sub.setPadding(0, dp(8), 0, dp(16));
        t.overlay.addView(sub);
        add(t.overlay, button("Go to the start page", v -> load(t, HOME_URL)));
        if (t == current()) syncToolbar();
    }

    private static final int YT_NO = 0, YT_ALLOW = 1, YT_PENDING = 2;

    /**
     * YouTube channel entries: a channel's own pages are allowed, and a video is allowed when YouTube
     * says it belongs to an allowed channel. Everything else on YouTube (home, search, shorts feed,
     * other channels' videos) is not decided here and falls through to the normal "not allowed".
     */
    private int youtubeCheck(final Tab t, final String url, List<SitePolicy.Site> sites) {
        String host = SitePolicy.hostOf(url);
        if (!SitePolicy.isYoutubeHost(host) || !SitePolicy.hasYoutubeChannels(sites)) return YT_NO;
        String handle = SitePolicy.youtubeChannelHandle(url);
        if (handle != null) return SitePolicy.youtubeChannelAllowed(handle, sites) ? YT_ALLOW : YT_NO;
        String video = SitePolicy.youtubeVideoId(url);
        if (video == null) return YT_NO;
        String known = YoutubeLookup.cached(this, video);
        if (known == null) {
            YoutubeLookup.start(this, video, () -> runOnUiThread(() -> {
                if (!isFinishing()) load(t, url); // now the channel is known, so this decides for real
            }));
            showChecking(t);
            return YT_PENDING;
        }
        return SitePolicy.youtubeChannelAllowed(known, sites) ? YT_ALLOW : YT_NO;
    }

    private void showChecking(final Tab t) {
        t.webView.setVisibility(View.GONE);
        if (t.homeView != null) t.homeView.setVisibility(View.GONE);
        ensureOverlay(t);
        t.overlay.setVisibility(View.VISIBLE);
        t.overlay.removeAllViews();
        t.overlay.addView(title("Checking this video…"));
        TextView sub = body("Making sure it is from an allowed channel.");
        sub.setPadding(0, dp(8), 0, dp(16));
        t.overlay.addView(sub);
        if (t == current()) syncToolbar();
    }

    /** "Browse freely for a while" (a code redeemed on the agent's main screen): true until it expires. */
    private boolean browseWindowActive() {
        try {
            RestrictionsManager rm = (RestrictionsManager) getSystemService(Context.RESTRICTIONS_SERVICE);
            Bundle b = rm == null ? null : rm.getApplicationRestrictions();
            return b != null && b.getLong("browseUntil", 0) > System.currentTimeMillis();
        } catch (Exception e) {
            return false;
        }
    }

    private final java.util.Set<String> reportedHostsThisSession = new java.util.HashSet<>();

    /** Queues a site visited during a free-browsing window for the administrator's approval, once per host. */
    private void reportNewHostOnce(String url) {
        String host = SitePolicy.hostOf(url);
        if (host == null || !reportedHostsThisSession.add(host)) return;
        Intent i = new Intent(SITE_REQUEST_ACTION).setPackage(AGENT_PACKAGE).putExtra("url", "https://" + host + "/");
        try {
            sendBroadcast(i, "com.familymdm.agent.permission.BROWSER");
            toast("Free browsing: \"" + host + "\" will need the administrator's approval afterward.");
        } catch (Exception ignored) {
        }
    }

    private void showWeb(Tab t) {
        t.webView.setVisibility(View.VISIBLE);
        if (t.homeView != null) t.homeView.setVisibility(View.GONE);
        if (t.overlay != null) t.overlay.setVisibility(View.GONE);
        if (t == current()) syncToolbar();
    }

    // ---------- which mode is active, and where its site list comes from ----------

    private Mode mode() {
        if (isAgentManaged()) return Mode.AGENT;
        if (BrowserState.isOnline(this)) return Mode.ONLINE;
        return Mode.UNCONFIGURED; // "set up on its own" (OFFLINE) is gone: only the dashboard or the agent runs it
    }

    /** True once the agent has pushed a site list at least once (even an empty one). */
    private boolean isAgentManaged() {
        try {
            RestrictionsManager rm = (RestrictionsManager) getSystemService(Context.RESTRICTIONS_SERVICE);
            Bundle b = rm == null ? null : rm.getApplicationRestrictions();
            return b != null && b.containsKey("sites");
        } catch (Exception e) {
            return false;
        }
    }

    private List<SitePolicy.Site> interceptSitesNow(Mode mode) {
        long now = System.currentTimeMillis();
        if (now - interceptSitesAt > 3000) {
            interceptSites = currentSites(mode);
            interceptSitesAt = now;
        }
        return interceptSites;
    }

    private List<SitePolicy.Site> currentSites(Mode mode) {
        try {
            switch (mode) {
                case AGENT:
                    RestrictionsManager rm = (RestrictionsManager) getSystemService(Context.RESTRICTIONS_SERVICE);
                    Bundle b = rm == null ? null : rm.getApplicationRestrictions();
                    String json = b == null ? null : b.getString("sites");
                    return json == null ? new ArrayList<>() : SitePolicy.parse(new JSONArray(json));
                case ONLINE:
                    maybeSyncOnline(false);
                    return SitePolicy.parse(BrowserState.cachedSites(this));
                case OFFLINE:
                    return SitePolicy.parse(BrowserState.localSites(this));
                default:
                    return new ArrayList<>();
            }
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** Talks to the dashboard directly (no agent). Cheap opportunistic refresh, not a background service. */
    private void maybeSyncOnline(boolean force) {
        if (syncing) return;
        if (!force && System.currentTimeMillis() - BrowserState.lastOnlineSync(this) < ONLINE_SYNC_MIN_INTERVAL_MS) return;
        final String server = BrowserState.server(this);
        final String token = BrowserState.token(this);
        if (server == null || token == null) return;
        syncing = true;
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                JSONObject info = new JSONObject();
                info.put("manufacturer", Build.MANUFACTURER);
                info.put("model", Build.MODEL);
                body.put("info", info);
                JSONArray requests = BrowserState.peekSiteRequests(this);
                body.put("siteRequests", requests);
                JSONObject reply = Api.post(server + "/browser/sync", body, token);
                BrowserState.dropSiteRequests(this, requests.length());
                JSONArray sites = reply.optJSONArray("sites");
                BrowserState.setCachedSites(this, sites != null ? sites : new JSONArray());
                JSONObject filter = reply.optJSONObject("filter");
                if (filter != null) FilterStore.setPolicy(this, filter.optString("tier", "off"), filter.optString("base", ""));
                else FilterStore.setPolicy(this, "off", ""); // an older dashboard that does not know about filtering
                FilterStore.setYoutubeMode(this, filter == null ? "channels" : filter.optString("youtube", "channels"));
                FilterStore.setBlockImages(this, filter != null && filter.optBoolean("blockImages", false));
                org.json.JSONArray fw = filter == null ? null : filter.optJSONArray("words");
                String[] wordList = new String[fw == null ? 0 : fw.length()];
                for (int i = 0; i < wordList.length; i++) wordList[i] = fw.optString(i);
                FilterStore.setBlockedWords(this, wordList);
                FilterStore.setBlockImageSearch(this, filter != null && filter.optBoolean("imageSearch", false));
                FilterStore.setPictureGuard(this, filter == null ? "off" : filter.optString("pictureGuard", "off"));
                filterModeOk = !"off".equals(FilterStore.tier(this));
            } catch (Exception ignored) {
                // offline, or the dashboard is unreachable: keep using the last cached list
            } finally {
                syncing = false;
            }
        }).start();
    }

    // ---------- blocked pages: what to offer depends on the mode ----------

    /** A known adult site: always blocked, in every mode, with no master code or request option. */
    private void showAdultBlocked(final Tab t) {
        t.webView.setVisibility(View.GONE);
        if (t.homeView != null) t.homeView.setVisibility(View.GONE);
        ensureOverlay(t);
        t.overlay.setVisibility(View.VISIBLE);
        t.overlay.removeAllViews();
        t.overlay.addView(title("Not available"));
        TextView sub = body("This kind of site isn't available in this browser.");
        sub.setPadding(0, dp(8), 0, dp(16));
        t.overlay.addView(sub);
        add(t.overlay, button("Go to the start page", v -> load(t, HOME_URL)));
        if (t == current()) syncToolbar();
    }

    private void showBlocked(final Tab t, final String url, final Mode mode) {
        if (mode == Mode.ONLINE) {
            showRequestDialog(t, url);
            return;
        }
        t.webView.setVisibility(View.GONE);
        if (t.homeView != null) t.homeView.setVisibility(View.GONE);
        ensureOverlay(t);
        t.overlay.setVisibility(View.VISIBLE);
        t.overlay.removeAllViews();
        t.overlay.addView(title("This page isn't allowed"));
        TextView urlText = body(url);
        urlText.setPadding(0, dp(8), 0, dp(16));
        t.overlay.addView(urlText);

        if (mode == Mode.AGENT) {
            add(t.overlay, button("Request access to this page", v -> {
                Intent i = new Intent(SITE_REQUEST_ACTION).setPackage(AGENT_PACKAGE).putExtra("url", url);
                sendBroadcast(i, "com.familymdm.agent.permission.BROWSER");
                toast("Sent. Ask the administrator to approve it.");
            }));
            add(t.overlay, button("Allow this page (master code)", v -> promptAgentMasterThenApprove(t, url, "exact")));
            add(t.overlay, button("Allow the whole site (master code)", v -> promptAgentMasterThenApprove(t, url, "domain")));
        } else if (mode == Mode.OFFLINE) {
            add(t.overlay, button("Allow this page (master code)", v -> promptLocalMasterThenApprove(t, url, "exact")));
            add(t.overlay, button("Allow the whole site (master code)", v -> promptLocalMasterThenApprove(t, url, "domain")));
        }
        add(t.overlay, button("Go to the start page", v -> load(t, HOME_URL)));
        if (t == current()) syncToolbar();
    }

    /** Whitelist mode (ONLINE): a direct yes/no instead of a page, since there's only one real choice. */
    private void showRequestDialog(final Tab t, final String url) {
        t.webView.setVisibility(View.GONE);
        if (t.homeView != null) t.homeView.setVisibility(View.GONE);
        String host = SitePolicy.hostOf(url);
        alertDialog()
                .setTitle("Not on the allowed list")
                .setMessage((host != null ? "\"" + host + "\"" : "This page") + " isn't allowed yet. Ask the administrator for access?")
                .setNegativeButton("Not now", (d, w) -> load(t, HOME_URL))
                .setOnCancelListener(d -> load(t, HOME_URL))
                .setPositiveButton("Yes, ask", (d, w) -> {
                    BrowserState.addSiteRequest(this, url);
                    maybeSyncOnline(true);
                    toast("Sent. Ask the administrator to approve it on the dashboard.");
                    load(t, HOME_URL);
                })
                .show();
    }

    private void ensureOverlay(Tab t) {
        if (t.overlay == null) {
            t.overlay = new LinearLayout(this);
            t.overlay.setOrientation(LinearLayout.VERTICAL);
            int pad = dp(20);
            t.overlay.setPadding(pad, dp(40), pad, pad);
            t.overlay.setBackgroundColor(Color.WHITE);
            t.root.addView(t.overlay, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        }
    }

    private void promptAgentMasterThenApprove(final Tab t, final String url, final String type) {
        final EditText input = field("Master code");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        alertDialog()
                .setTitle("Master code")
                .setView(pad(input))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Allow", (d, w) -> {
                    final String code = input.getText().toString();
                    new Thread(() -> {
                        Bundle extras = new Bundle();
                        extras.putString("url", url);
                        extras.putString("type", type);
                        extras.putString("code", code);
                        String error;
                        try {
                            ContentResolver cr = getContentResolver();
                            Bundle result = cr.call(Uri.parse(PROVIDER_URI), "approveSite", null, extras);
                            error = result == null ? "Could not reach the agent app. Is it installed?" : result.getString("error");
                        } catch (Exception e) {
                            error = "Could not reach the agent app: " + e.getMessage();
                        }
                        final String err = error;
                        runOnUiThread(() -> {
                            if (err != null) toast(err);
                            else load(t, url);
                        });
                    }).start();
                })
                .show();
    }

    private void promptLocalMasterThenApprove(final Tab t, final String url, final String type) {
        final EditText input = field("Master code");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        alertDialog()
                .setTitle("Master code")
                .setView(pad(input))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Allow", (d, w) -> {
                    final String code = input.getText().toString();
                    new Thread(() -> {
                        String err = BrowserMaster.check(this, code);
                        if (err == null) {
                            String host = SitePolicy.hostOf(url);
                            if (host == null) {
                                err = "That doesn't look like a web address.";
                            } else {
                                try {
                                    String site = type.equals("domain") ? host : url;
                                    BrowserState.addLocalSite(this, type, site, host);
                                } catch (Exception e) {
                                    err = "Could not save it: " + e.getMessage();
                                }
                            }
                        }
                        final String error = err;
                        runOnUiThread(() -> {
                            if (error != null) toast(error);
                            else load(t, url);
                        });
                    }).start();
                })
                .show();
    }

    private void promptMasterThenManageSites() {
        final EditText input = field("Master code");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        alertDialog()
                .setTitle("Master code")
                .setView(pad(input))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("OK", (d, w) -> {
                    String err = BrowserMaster.check(this, input.getText().toString());
                    if (err != null) toast(err);
                    else showManageSitesDialog();
                })
                .show();
    }

    private void showManageSitesDialog() {
        JSONArray sites = BrowserState.localSites(this);
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < sites.length(); i++) {
            JSONObject s = sites.optJSONObject(i);
            if (s != null) labels.add(("domain".equals(s.optString("type")) ? "Whole site: " : "Exact page: ") + s.optString("url"));
        }
        labels.add("＋ Add a site…");
        alertDialog()
                .setTitle("Local sites")
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    if (which == labels.size() - 1) promptAddLocalSite();
                    else {
                        alertDialog()
                                .setMessage("Remove this site from the local allowlist?")
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Remove", (d2, w2) -> {
                                    BrowserState.removeLocalSite(this, which);
                                    toast("Removed.");
                                })
                                .show();
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void promptAddLocalSite() {
        final EditText input = field("Site or link, e.g. khanacademy.org");
        alertDialog()
                .setTitle("Add a site")
                .setView(pad(input))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add whole site", (d, w) -> addLocalSiteFromText(input.getText().toString(), "domain"))
                .setNeutralButton("Add exact page", (d, w) -> addLocalSiteFromText(input.getText().toString(), "exact"))
                .show();
    }

    private void addLocalSiteFromText(String typed, String type) {
        String url = typed.trim();
        if (url.isEmpty()) return;
        if (!url.contains("://")) url = "https://" + url;
        String host = SitePolicy.hostOf(url);
        if (host == null) {
            toast("That doesn't look like a web address.");
            return;
        }
        if (SitePolicy.isBlockedAdult(url)) {
            toast("That site can't be allowed.");
            return;
        }
        try {
            BrowserState.addLocalSite(this, type, type.equals("domain") ? host : url, host);
            toast("Added.");
        } catch (Exception e) {
            toast("Could not save it: " + e.getMessage());
        }
    }

    private void showAllowedSitesDialog() {
        List<SitePolicy.Site> sites = currentSites(mode());
        String[] labels = new String[sites.size()];
        for (int i = 0; i < sites.size(); i++) labels[i] = sites.get(i).label;
        if (labels.length == 0) {
            toast("No sites are allowed yet.");
            return;
        }
        alertDialog()
                .setTitle("Allowed sites")
                .setItems(labels, (d, which) -> load(current(), sites.get(which).url))
                .setNegativeButton("Close", null)
                .show();
    }

    // ---------- the new-tab / home page, including first-run setup ----------

    private void renderHome(Tab t) {
        t.url = HOME_URL;
        t.title = "New tab";
        t.webView.setVisibility(View.GONE);
        if (t.overlay != null) t.overlay.setVisibility(View.GONE);
        if (t.homeView == null) {
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER_HORIZONTAL);
            int pad = dp(24);
            box.setPadding(pad, dp(60), pad, pad);
            t.homeView = box;
            t.root.addView(box, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        }
        LinearLayout box = (LinearLayout) t.homeView;
        box.removeAllViews();
        box.addView(title("Browser"));
        Mode mode = mode();
        if (mode == Mode.UNCONFIGURED) {
            renderUnconfigured(box, t, null);
        } else {
            List<SitePolicy.Site> sites = currentSites(mode);
            TextView sub = body(sites.isEmpty() ? "No sites are allowed yet." : "Allowed sites:");
            sub.setPadding(0, dp(12), 0, dp(12));
            box.addView(sub);
            for (final SitePolicy.Site s : sites) add(box, button(s.label, v -> load(current(), s.url)), 8);
        }
        box.setVisibility(View.VISIBLE);
        if (t == current()) syncToolbar();
    }

    /** Deny-by-default: nothing opens until this screen's two options set a mode up. */
    private void showSetupRequired(final Tab t, final String pendingUrl) {
        t.webView.setVisibility(View.GONE);
        if (t.homeView != null) t.homeView.setVisibility(View.GONE);
        ensureOverlay(t);
        t.overlay.setVisibility(View.VISIBLE);
        t.overlay.removeAllViews();
        t.overlay.addView(title("Browser isn't set up yet"));
        TextView sub = body("Nothing opens until this Browser is connected to the family dashboard.");
        sub.setPadding(0, dp(8), 0, dp(16));
        t.overlay.addView(sub);
        renderUnconfigured(t.overlay, t, pendingUrl);
        if (t == current()) syncToolbar();
    }

    private void renderUnconfigured(LinearLayout box, Tab t, String pendingUrl) {
        add(box, button("Connect (whitelist mode)", v -> promptWhitelistMode(t, pendingUrl)));
        TextView advanced = body("Advanced setup options");
        advanced.setTextColor(ACCENT);
        advanced.setOnClickListener(v -> showAdvancedSetupDialog(t, pendingUrl));
        add(box, advanced, 16);
    }

    /** The primary path: no code, nothing typed — this device just connects itself. */
    private void promptWhitelistMode(final Tab t, final String pendingUrl) {
        alertDialog()
                .setTitle("Not connected to MDM")
                .setMessage("Use this in whitelist mode? Every new site you visit will be sent to the administrator for approval; nothing opens until it's approved.")
                .setNegativeButton("Not now", null)
                .setPositiveButton("Yes, connect", (d, w) -> autoRegister(t, pendingUrl))
                .show();
    }

    /** Self-registers with the dashboard baked in at build time, or asks once for its address. */
    private void autoRegister(final Tab t, final String pendingUrl) {
        String defaultServer = getString(R.string.default_server);
        if (defaultServer != null && !defaultServer.isEmpty()) {
            registerWithServer(t, defaultServer, pendingUrl);
            return;
        }
        final EditText serverField = field("Dashboard address (https://...)");
        alertDialog()
                .setTitle("Dashboard address")
                .setMessage("This copy of Browser doesn't have a dashboard address built in. Enter yours once — no code needed.")
                .setView(pad(serverField))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Connect", (d, w) -> {
                    String server = serverField.getText().toString().trim().replaceAll("/+$", "");
                    if (!server.isEmpty()) registerWithServer(t, server, pendingUrl);
                })
                .show();
    }

    private void registerWithServer(final Tab t, final String server, final String pendingUrl) {
        new Thread(() -> {
            String error = null;
            try {
                JSONObject info = new JSONObject();
                info.put("manufacturer", Build.MANUFACTURER);
                info.put("model", Build.MODEL);
                JSONObject body = new JSONObject();
                body.put("info", info);
                JSONObject reply = Api.post(server + "/browser/register", body, null);
                BrowserState.setOnline(this, server, reply.getString("token"));
                maybeSyncOnline(true);
            } catch (Exception e) {
                error = e.getMessage();
            }
            final String err = error;
            runOnUiThread(() -> {
                if (err != null) {
                    toast("Could not connect: " + err);
                } else {
                    toast("Connected in whitelist mode.");
                    load(t, pendingUrl != null ? pendingUrl : HOME_URL);
                }
            });
        }).start();
    }

    private void showAdvancedSetupDialog(final Tab t, final String pendingUrl) {
        alertDialog()
                .setTitle("Advanced setup")
                .setItems(new String[]{"Connect to a specific dashboard (needs a code)"}, (d, which) -> showSetupDialog(t, pendingUrl))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showSetupDialog(final Tab t, final String pendingUrl) {
        final EditText serverField = field("Dashboard address (https://...)");
        final EditText codeField = field("Connect code");
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(serverField);
        add(box, codeField);
        alertDialog()
                .setTitle("Connect to a dashboard")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Connect", (d, w) -> {
                    final String server = serverField.getText().toString().trim().replaceAll("/+$", "");
                    final String code = codeField.getText().toString().trim();
                    if (server.isEmpty() || code.isEmpty()) return;
                    new Thread(() -> {
                        String error = null;
                        try {
                            JSONObject info = new JSONObject();
                            info.put("manufacturer", Build.MANUFACTURER);
                            info.put("model", Build.MODEL);
                            JSONObject body = new JSONObject();
                            body.put("code", code);
                            body.put("info", info);
                            JSONObject reply = Api.post(server + "/browser/enroll", body, null);
                            BrowserState.setOnline(this, server, reply.getString("token"));
                            maybeSyncOnline(true);
                        } catch (Exception e) {
                            error = e.getMessage();
                        }
                        final String err = error;
                        runOnUiThread(() -> {
                            if (err != null) {
                                toast("Could not connect: " + err);
                            } else {
                                toast("Connected.");
                                load(t, pendingUrl != null ? pendingUrl : HOME_URL);
                            }
                        });
                    }).start();
                })
                .show();
    }

    private void showOfflineSetupDialog(final Tab t, final String pendingUrl) {
        final EditText one = field("Master code (6 or more characters)");
        final EditText two = field("Repeat it");
        one.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        two.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(one);
        add(box, two);
        alertDialog()
                .setTitle("Set up on its own")
                .setMessage("This code is the only way to add or remove sites. Nothing is allowed until you add sites with it.")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    final String a = one.getText().toString();
                    if (a.length() < 6 || !a.equals(two.getText().toString())) {
                        toast("Use at least 6 characters, typed the same twice.");
                        return;
                    }
                    new Thread(() -> {
                        String error = null;
                        try {
                            BrowserMaster.set(this, a);
                            BrowserState.setStandalone(this);
                        } catch (Exception e) {
                            error = e.getMessage();
                        }
                        final String err = error;
                        runOnUiThread(() -> {
                            if (err != null) toast("Could not set up: " + err);
                            else load(t, pendingUrl != null ? pendingUrl : HOME_URL);
                        });
                    }).start();
                })
                .show();
    }

    // ---------- "Add to Home Screen" ----------

    private boolean canAddToHomeScreen(Tab t) {
        if (HOME_URL.equals(t.url) || t.webView.getUrl() == null) return false;
        Mode mode = mode();
        if (mode == Mode.UNCONFIGURED) return false;
        SitePolicy.Site match = SitePolicy.matching(t.webView.getUrl(), currentSites(mode));
        return match != null && match.installable;
    }

    private void addToHomeScreen() {
        Tab t = current();
        if (t == null || t.webView.getUrl() == null) return;
        ShortcutManager sm = getSystemService(ShortcutManager.class);
        if (sm == null || !sm.isRequestPinShortcutSupported()) {
            toast("This phone can't add home screen shortcuts.");
            return;
        }
        final String url = t.webView.getUrl();
        final String label = t.webView.getTitle() != null && !t.webView.getTitle().isEmpty() ? t.webView.getTitle() : url;
        Intent launch = new Intent(this, BrowserActivity.class).setAction(Intent.ACTION_VIEW).putExtra("url", url)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        ShortcutInfo.Builder builder = new ShortcutInfo.Builder(this, "site:" + url)
                .setShortLabel(label.length() > 20 ? label.substring(0, 20) : label)
                .setLongLabel(label)
                .setIntent(launch);
        Bitmap favicon = t.webView.getFavicon();
        builder.setIcon(favicon != null ? Icon.createWithBitmap(favicon) : Icon.createWithResource(this, android.R.drawable.ic_menu_compass));
        sm.requestPinShortcut(builder.build(), null);
    }

    // ---------- tiny view helpers (no shared UI library with the agent app) ----------

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private android.widget.Button button(String label, View.OnClickListener l) {
        android.widget.Button b = new android.widget.Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        b.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        b.setStateListAnimator(null);
        b.setBackground(rounded(ACCENT, 12));
        b.setPadding(dp(20), dp(12), dp(20), dp(12));
        b.setOnClickListener(l);
        return b;
    }

    private TextView title(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(22);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setTextColor(INK);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        return t;
    }

    private TextView body(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(15);
        t.setTextColor(INK_MUTED);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        return t;
    }

    private EditText field(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        return e;
    }

    private View pad(View v) {
        int p = dp(20);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(p, dp(8), p, 0);
        box.addView(v);
        return box;
    }

    private void add(LinearLayout parent, View child) {
        add(parent, child, 8);
    }

    private void add(LinearLayout parent, View child, int topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topMargin);
        parent.addView(child, lp);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
