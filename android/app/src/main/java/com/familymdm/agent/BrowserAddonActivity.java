package com.familymdm.agent;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.familymdm.agent.sitepolicy.SitePolicy;

import org.json.JSONObject;

import java.util.List;

/**
 * The Browser add-on's own icon and screen: a single-tab WebView that only opens pages on this
 * phone's own allowlist -- the exact same policy.sites the dashboard already sends down, enforced
 * the same way the separate standalone Browser app enforces it when it's running under this same
 * agent. Reached only once an administrator turns it on from Add-ons (see
 * AdminActivity.buildAddonsSection() and BrowserAddon.setEnabled()); this is deliberately a smaller
 * single-tab browser, not the full tabbed one the standalone app is -- no tabs, no desktop mode, no
 * shortcut menu yet, just the core "only allowed pages open, everything else can be requested or
 * unlocked with the master code" behavior.
 */
public class BrowserAddonActivity extends Activity {
    private static final String HOME_URL = "about:home";
    private static final Uri BRIDGE = Uri.parse("content://com.familymdm.agent.provider");

    private WebView webView;
    private FrameLayout contentHost;
    private LinearLayout overlay;
    private EditText addressBar;
    private final java.util.Set<String> reportedHostsThisSession = new java.util.HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(buildToolbar());

        contentHost = new FrameLayout(this);
        root.addView(contentHost, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        webView = new WebView(this);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return navigateTo(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (!HOME_URL.equals(url)) addressBar.setText(url);
            }
        });
        contentHost.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        setContentView(root);
        load(HOME_URL);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    // ---------- toolbar ----------

    private LinearLayout buildToolbar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        int pad = Ui.dp(this, 8);
        bar.setPadding(pad, pad, pad, pad);

        LinearLayout nav = new LinearLayout(this);
        nav.addView(Ui.button(this, "<", Ui.OUTLINED, v -> { if (webView.canGoBack()) webView.goBack(); }));
        nav.addView(Ui.button(this, "Reload", Ui.OUTLINED, v -> webView.reload()));
        bar.addView(nav);

        addressBar = Ui.field(this, "Address");
        addressBar.setSingleLine(true);
        addressBar.setImeOptions(EditorInfo.IME_ACTION_GO);
        addressBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                load(addressBar.getText().toString().trim());
                return true;
            }
            return false;
        });
        bar.addView(addressBar, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return bar;
    }

    // ---------- navigation + allowlist enforcement ----------

    private void load(String url) {
        hideOverlay();
        if (HOME_URL.equals(url) || url.isEmpty()) {
            webView.loadData("<html><body style='font-family:sans-serif;padding:24px'>"
                    + "<h2>Browser</h2><p>Type an address above, or open a page you already have a link to.</p>"
                    + "</body></html>", "text/html", "utf-8");
            addressBar.setText("");
            return;
        }
        webView.loadUrl(url.contains("://") ? url : "https://" + url);
    }

    /** Returns true (meaning "I handled it, don't load it") when the page is blocked. */
    private boolean navigateTo(String url) {
        if (HOME_URL.equals(url)) {
            load(HOME_URL);
            return true;
        }
        if (SitePolicy.isBlockedAdult(url)) {
            showAdultBlocked();
            return true;
        }
        List<SitePolicy.Site> sites = currentSites();
        if (SitePolicy.allowed(url, sites)) {
            hideOverlay();
            return false;
        }
        if (browseWindowActive()) {
            reportNewHostOnce(url);
            hideOverlay();
            return false;
        }
        showBlocked(url);
        return true;
    }

    private List<SitePolicy.Site> currentSites() {
        try {
            JSONObject policy = new JSONObject(Agent.prefs(this).getString("policy", "{}"));
            return SitePolicy.parse(policy.optJSONArray("sites"));
        } catch (Exception e) {
            return new java.util.ArrayList<>();
        }
    }

    /** "Browse freely for a while" (a code redeemed on the agent's main screen): true until it expires. */
    private boolean browseWindowActive() {
        return Agent.prefs(this).getLong("browseUntil", 0) > System.currentTimeMillis();
    }

    /** Queues a site visited during a free-browsing window for the administrator's approval, once per host. */
    private void reportNewHostOnce(String url) {
        String host = SitePolicy.hostOf(url);
        if (host == null || !reportedHostsThisSession.add(host)) return;
        Agent.addSiteRequest(this, "https://" + host + "/");
        toast("Free browsing: \"" + host + "\" will need the administrator's approval afterward.");
    }

    // ---------- blocked pages ----------

    private void showAdultBlocked() {
        webView.setVisibility(View.GONE);
        ensureOverlay();
        overlay.removeAllViews();
        overlay.addView(Ui.titleText(this, "Not available"));
        overlay.addView(Ui.body(this, "This kind of site isn't available in this browser.", true));
        overlay.addView(Ui.button(this, "Go to the start page", Ui.TONAL, v -> load(HOME_URL)));
    }

    private void showBlocked(final String url) {
        webView.setVisibility(View.GONE);
        ensureOverlay();
        overlay.removeAllViews();
        overlay.addView(Ui.titleText(this, "This page isn't allowed"));
        overlay.addView(Ui.body(this, url, true));
        overlay.addView(Ui.button(this, "Request access to this page", Ui.TONAL, v -> {
            Agent.addSiteRequest(this, url);
            toast("Sent. Ask the administrator to approve it.");
        }));
        overlay.addView(Ui.button(this, "Allow this page (master code)", Ui.OUTLINED, v -> promptApprove(url, "exact")));
        overlay.addView(Ui.button(this, "Allow the whole site (master code)", Ui.OUTLINED, v -> promptApprove(url, "domain")));
        overlay.addView(Ui.button(this, "Go to the start page", Ui.OUTLINED, v -> load(HOME_URL)));
    }

    private void ensureOverlay() {
        if (overlay == null) {
            overlay = new LinearLayout(this);
            overlay.setOrientation(LinearLayout.VERTICAL);
            int pad = Ui.dp(this, 20);
            overlay.setPadding(pad, pad, pad, pad);
            contentHost.addView(overlay, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        }
        overlay.setVisibility(View.VISIBLE);
    }

    private void hideOverlay() {
        webView.setVisibility(View.VISIBLE);
        if (overlay != null) overlay.setVisibility(View.GONE);
    }

    /** Same door the separate Browser app uses for this (BrowserBridgeProvider), just called
     * in-process since this screen now lives inside the agent that owns it. */
    private void promptApprove(final String url, final String type) {
        final EditText input = Ui.field(this, "Master code");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        android.app.AlertDialog dialog = Ui.alertDialog(this)
                .setTitle("Master code")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Allow", (d, w) -> {
                    Bundle extras = new Bundle();
                    extras.putString("url", url);
                    extras.putString("type", type);
                    extras.putString("code", input.getText().toString());
                    Bundle result = getContentResolver().call(BRIDGE, "approveSite", null, extras);
                    String err = result == null ? "No response" : result.getString("error");
                    if (err != null) {
                        toast(err);
                    } else {
                        toast("Allowed.");
                        load(url);
                    }
                })
                .create();
        dialog.show();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
