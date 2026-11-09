package com.familymdm.agent;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebView;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
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
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                try {
                    if (!WebFilter.active(BrowserAddonActivity.this)) return null;
                    String url = request.getUrl().toString();
                    String host = SitePolicy.hostOf(url);
                    if (host == null || !WebFilter.blocks(BrowserAddonActivity.this, host)) return null;
                    // In a blocked category: still fine if it is on the approved list or during a free-browsing window.
                    if (SitePolicy.allowed(url, currentSites()) || browseWindowActive()) return null;
                    if (request.isForMainFrame()) runOnUiThread(() -> navigateTo(url));
                    return new WebResourceResponse("text/plain", "utf-8", 403, "Blocked", new java.util.HashMap<String, String>(),
                            new java.io.ByteArrayInputStream(new byte[0]));
                } catch (Exception e) {
                    return null; // never break a page because the filter hit a problem
                }
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                super.doUpdateVisitedHistory(view, url, isReload);
                if (url == null || !SitePolicy.isYoutubeHost(SitePolicy.hostOf(url))) return;
                // YouTube switches video or page in place (no page load), so this is the only signal.
                if (navigateTo(url)) {
                    view.stopLoading();
                    view.loadUrl("about:blank"); // otherwise the video keeps playing sound under the blocked screen
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (!HOME_URL.equals(url) && !"about:blank".equals(url)) addressBar.setText(url);
            }
        });
        contentHost.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        setContentView(root);
        load(HOME_URL);
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            JSONObject policy = new JSONObject(Agent.prefs(this).getString("policy", "{}"));
            WebFilter.setPolicy(this, policy.optString("browserFilter", "off"), policy.optString("browserFilterBase", ""));
        } catch (Exception e) {
            WebFilter.setPolicy(this, "off", "");
        }
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
            webView.loadDataWithBaseURL(null, homeHtml(), "text/html", "utf-8", null);
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
        int yt = youtubeCheck(url, sites);
        if (yt == YT_ALLOW) {
            hideOverlay();
            return false;
        }
        if (yt == YT_PENDING) return true;
        if (browseWindowActive()) {
            reportNewHostOnce(url);
            hideOverlay();
            return false;
        }
        // Moderate / Strict filter: everything opens except blocked categories, but only once the lists
        // are downloaded and verified; until then this stays "approved sites only".
        if (WebFilter.active(this)) {
            String host = SitePolicy.hostOf(url);
            if (host != null && !WebFilter.blocks(this, host)) {
                hideOverlay();
                return false;
            }
        }
        showBlocked(url);
        return true;
    }

    private static final int YT_NO = 0, YT_ALLOW = 1, YT_PENDING = 2;

    /**
     * Allowed YouTube channels: a channel's own pages open, and a video opens when YouTube says it
     * belongs to an allowed channel. Everything else on YouTube is not decided here.
     */
    private int youtubeCheck(final String url, List<SitePolicy.Site> sites) {
        String host = SitePolicy.hostOf(url);
        if (!SitePolicy.isYoutubeHost(host) || !SitePolicy.hasYoutubeChannels(sites)) return YT_NO;
        String handle = SitePolicy.youtubeChannelHandle(url);
        if (handle != null) return SitePolicy.youtubeChannelAllowed(handle, sites) ? YT_ALLOW : YT_NO;
        String video = SitePolicy.youtubeVideoId(url);
        if (video == null) return YT_NO;
        String known = YoutubeLookup.cached(this, video);
        if (known == null) {
            YoutubeLookup.start(this, video, () -> runOnUiThread(() -> {
                if (!isFinishing()) load(url); // now the channel is known, so this decides for real
            }));
            webView.setVisibility(View.GONE);
            ensureOverlay();
            overlay.removeAllViews();
            overlay.addView(Ui.titleText(this, "Checking this video…"));
            overlay.addView(Ui.body(this, "Making sure it is from an allowed channel.", true));
            return YT_PENDING;
        }
        return SitePolicy.youtubeChannelAllowed(known, sites) ? YT_ALLOW : YT_NO;
    }

    /** The start page: every allowed site and channel as a big button, nothing else. */
    private String homeHtml() {
        StringBuilder sb = new StringBuilder("<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>"
                + "<body style='font-family:sans-serif;padding:24px'><h2>Browser</h2>");
        List<SitePolicy.Site> sites = currentSites();
        if (sites.isEmpty()) {
            sb.append("<p>No sites are allowed yet.</p>");
        } else {
            sb.append("<p>Allowed sites:</p>");
            for (SitePolicy.Site s : sites) {
                sb.append("<a href=\"").append(android.text.Html.escapeHtml(s.url)).append("\" style='display:block;padding:16px;margin:10px 0;"
                        + "background:#eceff1;border-radius:12px;color:#000;text-decoration:none;font-size:17px'>")
                        .append(android.text.Html.escapeHtml(s.label)).append("</a>");
            }
        }
        return sb.append("</body></html>").toString();
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
