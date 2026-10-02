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

import java.util.ArrayList;
import java.util.List;

/**
 * A regular tabbed browser. On its own (no MDM installed, or the agent hasn't pushed a site list
 * yet) it opens anything, like any browser. Once the separate agent app pushes a site list through
 * Android's managed-configuration channel, it only opens pages on that list, and a blocked page
 * offers to ask the administrator or use the master code — see isManaged()/currentSites() below for
 * where that switch happens.
 */
public class BrowserActivity extends Activity {
    private static final String AGENT_PACKAGE = "com.familymdm.agent";
    private static final String PROVIDER_URI = "content://com.familymdm.agent.provider";
    private static final String SITE_REQUEST_ACTION = "com.familymdm.agent.action.SITE_REQUEST";
    private static final String HOME_URL = "about:home";

    private final List<Tab> tabs = new ArrayList<>();
    private int currentIndex = -1;

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

    /** One browser tab: its own WebView, plus a blocked-page overlay built lazily when it's needed. */
    private static final class Tab {
        final FrameLayout root;
        final WebView webView;
        LinearLayout blockedBox;
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
        if (url != null) {
            int i = newTab(url);
            showTab(i);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(restrictionsChanged, new IntentFilter(Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED));
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
        addressBar.setHint("Search or type a web address");
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

        bar.addView(iconButton("+", v -> { int i = newTab(HOME_URL); showTab(i); }));
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
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("New tab");
        if (tabs.size() > 1) menu.getMenu().add("Close this tab");
        if (t != null && canAddToHomeScreen(t)) menu.getMenu().add("Add to Home Screen");
        if (isManaged()) menu.getMenu().add("Allowed sites");
        menu.setOnMenuItemClickListener(item -> {
            String s = item.getTitle().toString();
            if (s.equals("New tab")) {
                showTab(newTab(HOME_URL));
            } else if (s.equals("Close this tab")) {
                closeTab(currentIndex);
            } else if (s.equals("Add to Home Screen")) {
                addToHomeScreen();
            } else if (s.equals("Allowed sites")) {
                showAllowedSitesDialog();
            }
            return true;
        });
        menu.show();
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

    private void go(String typed) {
        String s = typed.trim();
        if (s.isEmpty()) return;
        String url = looksLikeUrl(s) ? (s.contains("://") ? s : "https://" + s)
                : "https://www.google.com/search?q=" + Uri.encode(s);
        load(current(), url);
    }

    private boolean looksLikeUrl(String s) {
        return s.contains(".") && !s.contains(" ");
    }

    private void setupWebView(final Tab t) {
        t.webView.getSettings().setJavaScriptEnabled(true);
        t.webView.getSettings().setDomStorageEnabled(true);
        t.webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
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
        if (!allowedOrBlock(t, url)) return;
        showWeb(t);
        t.webView.loadUrl(url);
    }

    /** True if `url` may load; otherwise shows the blocked screen in this tab and returns false. */
    private boolean allowedOrBlock(Tab t, String url) {
        if (!isManaged()) return true;
        SitePolicy.Site match = SitePolicy.matching(url, currentSites());
        if (match == null) {
            showBlocked(t, url);
            return false;
        }
        t.webView.getSettings().setBlockNetworkImage(match.blockImages);
        t.webView.getSettings().setLoadsImagesAutomatically(!match.blockImages);
        return true;
    }

    private void showWeb(Tab t) {
        t.webView.setVisibility(View.VISIBLE);
        if (t.homeView != null) t.homeView.setVisibility(View.GONE);
        if (t.blockedBox != null) t.blockedBox.setVisibility(View.GONE);
        if (t == current()) syncToolbar();
    }

    // ---------- managed mode: the allowlist pushed by the separate agent app, if any ----------

    /**
     * True once the agent has pushed a site list at least once (even an empty one). Until then
     * (no MDM installed, or installed but not yet configured) this is a plain, unrestricted browser.
     */
    private boolean isManaged() {
        try {
            RestrictionsManager rm = (RestrictionsManager) getSystemService(Context.RESTRICTIONS_SERVICE);
            Bundle b = rm == null ? null : rm.getApplicationRestrictions();
            return b != null && b.containsKey("sites");
        } catch (Exception e) {
            return false;
        }
    }

    private List<SitePolicy.Site> currentSites() {
        try {
            RestrictionsManager rm = (RestrictionsManager) getSystemService(Context.RESTRICTIONS_SERVICE);
            Bundle b = rm == null ? null : rm.getApplicationRestrictions();
            String json = b == null ? null : b.getString("sites");
            if (json == null) return new ArrayList<>();
            return SitePolicy.parse(new JSONArray(json));
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void showBlocked(final Tab t, final String url) {
        t.webView.setVisibility(View.GONE);
        if (t.homeView != null) t.homeView.setVisibility(View.GONE);
        if (t.blockedBox == null) {
            t.blockedBox = new LinearLayout(this);
            t.blockedBox.setOrientation(LinearLayout.VERTICAL);
            int pad = dp(20);
            t.blockedBox.setPadding(pad, pad, pad, pad);
            t.root.addView(t.blockedBox, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        }
        t.blockedBox.setVisibility(View.VISIBLE);
        t.blockedBox.removeAllViews();
        t.blockedBox.addView(title("This page isn't allowed"));
        TextView urlText = body(url);
        urlText.setPadding(0, dp(8), 0, dp(16));
        t.blockedBox.addView(urlText);

        add(t.blockedBox, button("Request access to this page", v -> {
            Intent i = new Intent(SITE_REQUEST_ACTION).setPackage(AGENT_PACKAGE).putExtra("url", url);
            sendBroadcast(i, "com.familymdm.agent.permission.BROWSER");
            toast("Sent. Ask the administrator to approve it.");
        }));
        add(t.blockedBox, button("Allow this page (master code)", v -> promptMasterThenApprove(t, url, "exact")));
        add(t.blockedBox, button("Allow the whole site (master code)", v -> promptMasterThenApprove(t, url, "domain")));
        add(t.blockedBox, button("Go to the start page", v -> load(t, HOME_URL)));
        if (t == current()) syncToolbar();
    }

    private void promptMasterThenApprove(final Tab t, final String url, final String type) {
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

    private void showAllowedSitesDialog() {
        List<SitePolicy.Site> sites = currentSites();
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

    // ---------- the new-tab / home page ----------

    private void renderHome(Tab t) {
        t.url = HOME_URL;
        t.title = "New tab";
        t.webView.setVisibility(View.GONE);
        if (t.blockedBox != null) t.blockedBox.setVisibility(View.GONE);
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
        if (isManaged()) {
            List<SitePolicy.Site> sites = currentSites();
            TextView sub = body(sites.isEmpty() ? "No sites are allowed yet." : "Allowed sites:");
            sub.setPadding(0, dp(12), 0, dp(12));
            box.addView(sub);
            for (final SitePolicy.Site s : sites) add(box, button(s.label, v -> load(current(), s.url)), 8);
        } else {
            TextView sub = body("Type an address above, or search.");
            sub.setPadding(0, dp(12), 0, dp(12));
            box.addView(sub);
        }
        box.setVisibility(View.VISIBLE);
        if (t == current()) syncToolbar();
    }

    // ---------- "Add to Home Screen" ----------

    private boolean canAddToHomeScreen(Tab t) {
        if (HOME_URL.equals(t.url) || t.webView.getUrl() == null) return false;
        if (!isManaged()) return true;
        SitePolicy.Site match = SitePolicy.matching(t.webView.getUrl(), currentSites());
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
