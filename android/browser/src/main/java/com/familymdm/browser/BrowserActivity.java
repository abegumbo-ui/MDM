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
import android.view.View;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.familymdm.agent.sitepolicy.SitePolicy;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/**
 * This app, by itself, is the family's only browser once the dashboard's "Make this the only
 * browser" switch is on: it only opens a page that is on the administrator's allowlist. It is a
 * separate app from the agent (which stays the device owner); the two talk over three narrow,
 * signature-protected channels — see AGENT_PACKAGE below.
 */
public class BrowserActivity extends Activity {
    private static final String AGENT_PACKAGE = "com.familymdm.agent";
    private static final String PROVIDER_URI = "content://com.familymdm.agent.provider";
    private static final String SITE_REQUEST_ACTION = "com.familymdm.agent.action.SITE_REQUEST";

    private WebView webView;
    private LinearLayout blockedBox;
    private TextView blockedText;
    private LinearLayout startPage;
    private ScrollView startPageScroll;
    private Button addToHomeButton;
    private SitePolicy.Site currentMatch;
    private final BroadcastReceiver restrictionsChanged = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (startPageScroll.getVisibility() == View.VISIBLE) showStartPage();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        int barPad = dp(8);
        bar.setPadding(barPad, barPad, barPad, barPad);
        Button back = button("‹", v -> { if (webView.canGoBack()) webView.goBack(); });
        Button fwd = button("›", v -> { if (webView.canGoForward()) webView.goForward(); });
        Button reload = button("⟳", v -> { if (currentMatch != null) webView.reload(); });
        Button go = button("Go to…", v -> promptUrl());
        addToHomeButton = button("Add to Home Screen", v -> addToHomeScreen());
        addToHomeButton.setVisibility(View.GONE);
        LinearLayout.LayoutParams wrap = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        wrap.rightMargin = dp(6);
        bar.addView(back, wrap);
        bar.addView(fwd, wrap);
        bar.addView(reload, wrap);
        bar.addView(go, wrap);
        bar.addView(addToHomeButton, wrap);
        root.addView(bar);

        webView = new WebView(this);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !attempt(request.getUrl().toString());
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel(); // never ignore a broken certificate, even on an allowed site
            }
        });
        root.addView(webView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        int pad = dp(20);
        blockedBox = new LinearLayout(this);
        blockedBox.setOrientation(LinearLayout.VERTICAL);
        blockedBox.setPadding(pad, pad, pad, pad);
        blockedBox.setVisibility(View.GONE);
        root.addView(blockedBox, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        startPage = new LinearLayout(this);
        startPage.setOrientation(LinearLayout.VERTICAL);
        startPage.setPadding(pad, pad, pad, pad);
        startPageScroll = new ScrollView(this);
        startPageScroll.addView(startPage);
        root.addView(startPageScroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(restrictionsChanged, new IntentFilter(Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED));
        if (startPageScroll.getVisibility() == View.VISIBLE) showStartPage();
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
        if (webView.getVisibility() == View.VISIBLE && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    private void handleIntent(Intent intent) {
        String url = intent == null ? null : intent.getStringExtra("url");
        if (url == null && intent != null && intent.getData() != null) url = intent.getData().toString();
        if (url != null) attempt(url);
        else showStartPage();
    }

    /** The allowlist the agent most recently pushed down (Android's managed-configuration channel). */
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

    /** Checks `url` against the allowlist and either loads it or shows the blocked screen. Returns whether it loaded. */
    private boolean attempt(String url) {
        SitePolicy.Site match = SitePolicy.matching(url, currentSites());
        if (match == null) {
            showBlocked(url);
            return false;
        }
        currentMatch = match;
        webView.getSettings().setBlockNetworkImage(match.blockImages);
        webView.getSettings().setLoadsImagesAutomatically(!match.blockImages);
        addToHomeButton.setVisibility(match.installable ? View.VISIBLE : View.GONE);
        startPageScroll.setVisibility(View.GONE);
        blockedBox.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        webView.loadUrl(url);
        return true;
    }

    private void showBlocked(final String url) {
        currentMatch = null;
        startPageScroll.setVisibility(View.GONE);
        webView.setVisibility(View.GONE);
        addToHomeButton.setVisibility(View.GONE);
        blockedBox.setVisibility(View.VISIBLE);
        blockedBox.removeAllViews();
        blockedBox.addView(title("This page isn't allowed"));
        TextView urlText = body(url);
        urlText.setPadding(0, dp(8), 0, dp(16));
        blockedBox.addView(urlText);

        add(blockedBox, button("Request access to this page", v -> {
            Intent i = new Intent(SITE_REQUEST_ACTION).setPackage(AGENT_PACKAGE).putExtra("url", url);
            sendBroadcast(i, "com.familymdm.agent.permission.BROWSER");
            toast("Sent. Ask the administrator to approve it.");
        }));
        add(blockedBox, button("Allow this page (master code)", v -> promptMasterThenApprove(url, "exact")));
        add(blockedBox, button("Allow the whole site (master code)", v -> promptMasterThenApprove(url, "domain")));
        add(blockedBox, button("Go to the start page", v -> showStartPage()));
    }

    private void showStartPage() {
        webView.setVisibility(View.GONE);
        blockedBox.setVisibility(View.GONE);
        addToHomeButton.setVisibility(View.GONE);
        startPageScroll.setVisibility(View.VISIBLE);
        startPage.removeAllViews();
        startPage.addView(title("Sites"));
        List<SitePolicy.Site> sites = currentSites();
        if (sites.isEmpty()) add(startPage, body("No sites are allowed yet."));
        for (final SitePolicy.Site s : sites) add(startPage, button(s.label, v -> attempt(s.url)));
        add(startPage, button("Go to a web address…", v -> promptUrl()));
    }

    private void promptUrl() {
        final EditText input = field("Web address");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        new AlertDialog.Builder(this)
                .setTitle("Go to…")
                .setView(pad(input))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Go", (d, w) -> {
                    String u = input.getText().toString().trim();
                    if (!u.isEmpty()) attempt(u.contains("://") ? u : "https://" + u);
                })
                .show();
    }

    private void promptMasterThenApprove(final String url, final String type) {
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
                            else attempt(url);
                        });
                    }).start();
                })
                .show();
    }

    private void addToHomeScreen() {
        if (currentMatch == null) return;
        ShortcutManager sm = getSystemService(ShortcutManager.class);
        if (sm == null || !sm.isRequestPinShortcutSupported()) {
            toast("This phone can't add home screen shortcuts.");
            return;
        }
        final String url = webView.getUrl() != null ? webView.getUrl() : currentMatch.url;
        final String label = webView.getTitle() != null && !webView.getTitle().isEmpty() ? webView.getTitle() : currentMatch.label;
        Intent launch = new Intent(this, BrowserActivity.class).setAction(Intent.ACTION_VIEW).putExtra("url", url)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        ShortcutInfo.Builder builder = new ShortcutInfo.Builder(this, "site:" + url)
                .setShortLabel(label.length() > 20 ? label.substring(0, 20) : label)
                .setLongLabel(label)
                .setIntent(launch);
        Bitmap favicon = webView.getFavicon();
        builder.setIcon(favicon != null ? Icon.createWithBitmap(favicon) : Icon.createWithResource(this, android.R.drawable.ic_menu_compass));
        sm.requestPinShortcut(builder.build(), null);
    }

    // ---------- tiny view helpers (no shared UI library with the agent app) ----------

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private Button button(String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    private TextView title(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(22);
        return t;
    }

    private TextView body(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(15);
        t.setTextColor(Color.GRAY);
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
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        parent.addView(child, lp);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
