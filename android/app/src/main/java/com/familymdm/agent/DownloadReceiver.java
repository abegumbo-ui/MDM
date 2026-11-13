package com.familymdm.agent;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/** The Browser (inside this app) asks for a download here: "com.familymdm.agent.DownloadReceiver" with a "url". */
public class DownloadReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        String why = Downloads.request(c, i.getStringExtra("url"));
        Toast.makeText(c, why == null ? "Downloading…" : why, Toast.LENGTH_LONG).show();
    }
}
