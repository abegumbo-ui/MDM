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
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.familymdm.agent.sitepolicy.SitePolicy;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
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
 *  - OFFLINE: no agent, no dashboard connection; set up on its own with a local master code. Sites
 *    live only in this app's storage, added with that master code.
 *  - UNCONFIGURED: none of the above yet. Deny-by-default: nothing opens until one of the other three
 *    modes is set up. This is the fresh-install state.
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

    private LinearLayout tabStrip;
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
        root.setBackgroundColor(Color.WHITE);

        root.addView(buildTabStrip());
        root.addView(buildToolbar());

        contentHost = new FrameLayout(this);
        root.addView(contentHost, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        newTab(startUrl(getIntent()));
        showTab(0);
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
        if (mode() == Mode.ONLINE) maybeSyncOnline(false);
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

    private HorizontalScrollView buildTabStrip() {
        tabStrip = new LinearLayout(this);
        tabStrip.setOrientation(LinearLayout.HORIZONTAL);
        tabStrip.setPadding(dp(4), dp(6), dp(4), dp(0));
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.addView(tabStrip);
        scroll.setBackgroundColor(Color.parseColor("#ECECEC"));
        return scroll;
    }

    private LinearLayout buildToolbar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Color.parseColor("#ECECEC"));
        int pad = dp(6);
        bar.setPadding(pad, pad, pad, pad);

        backBtn = iconButton("‹", v -> { if (current() != null && current().webView.canGoBack()) current().webView.goBack(); });
        fwdBtn = iconButton("›", v -> { if (current() != null && current().webView.canGoForward()) current().webView.goForward(); });
        reloadBtn = iconButton("⟳", v -> { if (current() != null) current().webView.reload(); });
        bar.addView(backBtn);
        bar.addView(fwdBtn);
        bar.addView(reloadBtn);

        addressBar = new EditText(this);
        addressBar.setSingleLine(true);
        addressBar.setHint("Type a full web address, e.g. example.com");
        addressBar.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        addressBar.setImeOptions(EditorInfo.IME_ACTION_GO);
        addressBar.setBackgroundColor(Color.WHITE);
        addressBar.setPadding(dp(12), dp(8), dp(12), dp(8));
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

        bar.addView(iconButton("+", v -> showTab(newTab(HOME_URL))));
        bar.addView(iconButton("⋮", this::showMenu));
        return bar;
    }

    private ImageButton iconButton(String glyph, View.OnClickListener l) {
        ImageButton b = new ImageButton(this);
        b.setImageDrawable(textDrawable(glyph));
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setOnClickListener(l);
        b.setPadding(dp(10), dp(10), dp(10), dp(10));
        return b;
    }

    /** No icon assets in this app on purpose: draw the toolbar glyphs as text instead. */
    private android.graphics.drawable.Drawable textDrawable(String glyph) {
        android.graphics.Paint paint = new android.graphics.Paint();
        paint.setAntiAlias(true);
        paint.setTextSize(dp(20));
        paint.setColor(Color.DKGRAY);
        paint.setTextAlign(android.graphics.Paint.Align.CENTER);
        android.graphics.Rect b = new android.graphics.Rect();
        paint.getTextBounds(glyph, 0, glyph.length(), b);
        int size = dp(28);
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(bmp);
        c.drawText(glyph, size / 2f, size / 2f - b.exactCenterY(), paint);
        return new android.graphics.drawable.BitmapDrawable(getResources(), bmp);
    }

    private void showMenu(View anchor) {
        Tab t = current();
        Mode mode = mode();
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("New tab");
        if (tabs.size() > 1) menu.getMenu().add("Close this tab");
        if (t != null && canAddToHomeScreen(t)) menu.getMenu().add("Add to Home Screen");
        if (mode != Mode.UNCONFIGURED) menu.getMenu().add("Allowed sites");
        if (mode == Mode.OFFLINE) menu.getMenu().add("Manage sites (master code)");
        if (mode == Mode.UNCONFIGURED) menu.getMenu().add("Set up Browser");
        if (mode == Mode.ONLINE || mode == Mode.OFFLINE) menu.getMenu().add("Disconnect this setup");
        if (mode == Mode.AGENT && browseWindowActive()) {
            RestrictionsManager rm = (RestrictionsManager) getSystemService(Context.RESTRICTIONS_SERVICE);
            Bundle b = rm == null ? null : rm.getApplicationRestrictions();
            long until = b == null ? 0 : b.getLong("browseUntil", 0);
            menu.getMenu().add("Free browsing until " + android.text.format.DateFormat.getTimeFormat(this).format(new java.util.Date(until)));
        }
        menu.setOnMenuItemClickListener(item -> {
            String s = item.getTitle().toString();
            if (s.equals("New tab")) showTab(newTab(HOME_URL));
            else if (s.equals("Close this tab")) closeTab(currentIndex);
            else if (s.equals("Add to Home Screen")) addToHomeScreen();
            else if (s.equals("Allowed sites")) showAllowedSitesDialog();
            else if (s.equals("Manage sites (master code)")) promptMasterThenManageSites();
            else if (s.equals("Set up Browser")) promptWhitelistMode(current(), null);
            else if (s.equals("Disconnect this setup")) disconnectSetup();
            return true;
        });
        menu.show();
    }

    private void disconnectSetup() {
        new AlertDialog.Builder(this)
                .setTitle("Disconnect this setup?")
                .setMessage("Browser goes back to allowing nothing until it's connected to a dashboard again or set up on its own.")
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
        tabStrip.removeAllViews();
        for (int i = 0; i < tabs.size(); i++) {
            final int idx = i;
            Tab t = tabs.get(i);
            LinearLayout chip = new LinearLayout(this);
            chip.setOrientation(LinearLayout.HORIZONTAL);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setPadding(dp(12), dp(8), dp(8), dp(8));
            chip.setBackgroundColor(i == currentIndex ? Color.WHITE : Color.parseColor("#ECECEC"));
            TextView label = new TextView(this);
            String title = t.title == null || t.title.isEmpty() ? "New tab" : t.title;
            label.setText(title.length() > 16 ? title.substring(0, 16) + "…" : title);
            label.setTextColor(Color.DKGRAY);
            chip.addView(label);
            TextView close = new TextView(this);
            close.setText("  ×");
            close.setTextColor(Color.GRAY);
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
            public void onPageFinished(WebView view, String url) {
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
        t.webView.loadUrl(url);
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
        SitePolicy.Site match = SitePolicy.matching(url, currentSites(mode));
        if (match == null) {
            if (mode == Mode.AGENT && browseWindowActive()) {
                reportNewHostOnce(url);
                t.webView.getSettings().setBlockNetworkImage(false);
                t.webView.getSettings().setLoadsImagesAutomatically(true);
                return true;
            }
            showBlocked(t, url, mode);
            return false;
        }
        t.webView.getSettings().setBlockNetworkImage(match.blockImages);
        t.webView.getSettings().setLoadsImagesAutomatically(!match.blockImages);
        return true;
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
        if (BrowserState.isStandalone(this)) return Mode.OFFLINE;
        return Mode.UNCONFIGURED;
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
        new AlertDialog.Builder(this)
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
            t.overlay.setPadding(pad, pad, pad, pad);
            t.root.addView(t.overlay, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        }
    }

    private void promptAgentMasterThenApprove(final Tab t, final String url, final String type) {
        final EditText input = field("Master code");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new AlertDialog.Builder(this)
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
        new AlertDialog.Builder(this)
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
        new AlertDialog.Builder(this)
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
        new AlertDialog.Builder(this)
                .setTitle("Local sites")
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    if (which == labels.size() - 1) promptAddLocalSite();
                    else {
                        new AlertDialog.Builder(this)
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
        new AlertDialog.Builder(this)
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
        new AlertDialog.Builder(this)
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
        TextView sub = body("Nothing opens until you connect this Browser to a dashboard, or set it up on its own.");
        sub.setPadding(0, dp(8), 0, dp(16));
        t.overlay.addView(sub);
        renderUnconfigured(t.overlay, t, pendingUrl);
        if (t == current()) syncToolbar();
    }

    private void renderUnconfigured(LinearLayout box, Tab t, String pendingUrl) {
        add(box, button("Connect (whitelist mode)", v -> promptWhitelistMode(t, pendingUrl)));
        TextView advanced = body("Advanced setup options");
        advanced.setTextColor(Color.parseColor("#6750A4"));
        advanced.setOnClickListener(v -> showAdvancedSetupDialog(t, pendingUrl));
        add(box, advanced, 16);
    }

    /** The primary path: no code, nothing typed — this device just connects itself. */
    private void promptWhitelistMode(final Tab t, final String pendingUrl) {
        new AlertDialog.Builder(this)
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
        new AlertDialog.Builder(this)
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
        new AlertDialog.Builder(this)
                .setTitle("Advanced setup")
                .setItems(new String[]{"Connect to a specific dashboard (needs a code)", "Set up fully offline (master code)"}, (d, which) -> {
                    if (which == 0) showSetupDialog(t, pendingUrl);
                    else showOfflineSetupDialog(t, pendingUrl);
                })
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
        new AlertDialog.Builder(this)
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
        new AlertDialog.Builder(this)
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
        b.setOnClickListener(l);
        return b;
    }

    private TextView title(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(22);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        return t;
    }

    private TextView body(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(15);
        t.setTextColor(Color.GRAY);
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
