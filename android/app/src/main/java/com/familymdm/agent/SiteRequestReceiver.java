package com.familymdm.agent;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Receives "this page isn't allowed" reports from the separate Browser app. Android only delivers this
 * broadcast to us if the sender holds com.familymdm.agent.permission.BROWSER (signature-level, so only
 * something signed with this same key can send it), so the url is trusted once it arrives here.
 */
public class SiteRequestReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String url = intent.getStringExtra("url");
        if (url == null || url.isEmpty()) return;
        Agent.addSiteRequest(context, url);
        if (Agent.enrolled(context)) AgentService.requestSync();
    }
}
