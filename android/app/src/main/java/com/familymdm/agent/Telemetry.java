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
import java.io.BufferedReader;
import java.io.InputStreamReader;

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

    private static String prop(String key) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"getprop", key});
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line = r.readLine();
                return line == null ? "" : line.trim();
            }
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * "locked", "unlocked" or "unknown". A locked bootloader is what makes Factory Reset Protection hold:
     * with an unlocked one, the protection can be erased from a computer.
     */
    static String bootloader(Context c) {
        android.content.SharedPreferences p = Agent.prefs(c);
        long now = System.currentTimeMillis();
        if (now - p.getLong("bootloaderAt", 0) < 60 * 60 * 1000 && p.getString("bootloader", null) != null) {
            return p.getString("bootloader", "unknown");
        }
        String flash = prop("ro.boot.flash.locked");
        String verified = prop("ro.boot.verifiedbootstate");
        String state = "unknown";
        if ("0".equals(flash) || "orange".equals(verified)) state = "unlocked";
        else if ("1".equals(flash) || "green".equals(verified) || "yellow".equals(verified)) state = "locked";
        p.edit().putString("bootloader", state).putLong("bootloaderAt", now).apply();
        return state;
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
        // ACCESS_WIFI_STATE isn't declared in the "enroll" build flavor (see android/app/build.gradle),
        // so this throws SecurityException there until the self-update to "full" lands.
        if ("wifi".equals(transport)) {
            try {
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
            } catch (SecurityException ignored) {
            }
        }
        // getNetworkOperatorName() is public info (the carrier whose tower the radio is on), no
        // permission needed -- unlike reading a SIM phone number or IMEI.
        if ("mobile".equals(transport)) {
            try {
                android.telephony.TelephonyManager tm =
                        (android.telephony.TelephonyManager) c.getSystemService(Context.TELEPHONY_SERVICE);
                String carrier = tm == null ? null : tm.getNetworkOperatorName();
                if (carrier == null || carrier.isEmpty()) carrier = tm == null ? null : tm.getSimOperatorName();
                if (carrier != null && !carrier.isEmpty()) o.put("carrier", carrier);
            } catch (Exception ignored) {
            }
        }
        return o;
    }
}
