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
        // "Ask for more time" on the Browser's Time is up page: a "More screen time" request, like the one on the lock screen.
        if ("time".equals(intent.getStringExtra("kind"))) {
            Agent.addAppRequest(context, "time", null, null);
            if (Agent.enrolled(context)) AgentService.requestSync();
            return;
        }
        String url = intent.getStringExtra("url");
        if (url == null || url.isEmpty()) return;
        Agent.addSiteRequest(context, url, intent.getStringExtra("note"));
        if (Agent.enrolled(context)) AgentService.requestSync();
    }
}
