package com.familymdm.agent;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.familymdm.agent.sitepolicy.BadSearch;

/** "Oh come on, you're better than that." Shown by the MDM app over any browser after a bad search. */
public class BetterThanThatActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WebView v = new WebView(this);
        v.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                finish();
                return true;
            }
        });
        v.loadDataWithBaseURL("about:blank", BadSearch.page("<a class=b href='mdmguard://close'>OK</a>"), "text/html", "utf-8", null);
        setContentView(v);
    }
}
