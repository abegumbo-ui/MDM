package com.familymdm.agent;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;

import org.json.JSONException;
import org.json.JSONObject;

/** Battery and connection details reported to the dashboard. No location is ever collected. */
final class Telemetry {
    private Telemetry() {}

    static JSONObject battery(Context c) throws JSONException {
        Intent i = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (i == null) return null;
        int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        JSONObject o = new JSONObject();
        o.put("pct", level < 0 ? -1 : level * 100 / Math.max(scale, 1));
        o.put("charging", status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL);
        return o;
    }

    /** transport: wifi / mobile / other / none. The network name needs Android's Location setting to be on. */
    static JSONObject wifi(Context c) throws JSONException {
        JSONObject o = new JSONObject();
        String transport = "none";
        ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network n = cm.getActiveNetwork();
        if (n != null) {
            NetworkCapabilities nc = cm.getNetworkCapabilities(n);
            if (nc != null) {
                if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) transport = "wifi";
                else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) transport = "mobile";
                else transport = "other";
            }
        }
        o.put("transport", transport);
        if ("wifi".equals(transport)) {
            WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo wi = wm == null ? null : wm.getConnectionInfo();
            if (wi != null) {
                String ssid = wi.getSSID();
                if (ssid != null && ssid.length() >= 2 && ssid.startsWith("\"") && ssid.endsWith("\"")) {
                    ssid = ssid.substring(1, ssid.length() - 1);
                }
                if (ssid != null && !"<unknown ssid>".equals(ssid) && !ssid.isEmpty()) o.put("ssid", ssid);
                int rssi = wi.getRssi();
                if (rssi > -127) o.put("rssi", rssi);
            }
        }
        return o;
    }
}
