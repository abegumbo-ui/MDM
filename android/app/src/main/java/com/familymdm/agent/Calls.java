package com.familymdm.agent;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.CallLog;
import android.provider.ContactsContract;

import com.familymdm.agent.sitepolicy.CallRules;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Calls and texts, from the dashboard's "Calls & texts" group:
 * - who may call the phone (CallGuard, Android's call-screening role) and whom it may call
 *   (CallOutGuard, the call-redirection role). Both roles need one "Allow" tap on the phone
 *   (Android has no way for a device owner to take them silently); CallSetupActivity asks.
 *   Emergency calls are never passed to CallOutGuard by Android, so they always work. Calls from
 *   hidden numbers are not passed to call screening either, so they cannot be blocked here.
 * - a call log and a texts log sent to the dashboard (off unless switched on). The Agent grants
 *   itself READ_CALL_LOG / READ_CONTACTS / READ_SMS as device owner.
 */
final class Calls {
    private Calls() {}

    private static final String CALLS_PENDING = "callsPending", TEXTS_PENDING = "textsPending";
    private static final int MAX_PENDING = 150, MAX_PER_RUN = 100;
    private static final long WEEK = 7L * 24 * 3600 * 1000;

    static void apply(Context c, JSONObject policy) {
        JSONArray nums = policy.optJSONArray("callNumbers");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; nums != null && i < nums.length(); i++) {
            String d = CallRules.digits(nums.optString(i));
            if (!d.isEmpty()) sb.append(sb.length() == 0 ? "" : ",").append(d);
        }
        String in = policy.optString("callsIn", "all"), out = policy.optString("callsOut", "all");
        boolean callLog = policy.optBoolean("callLog", false), textLog = policy.optBoolean("textLog", false);
        Agent.prefs(c).edit().putString("callsIn", in).putString("callsOut", out).putString("callNumbers", sb.toString())
                .putBoolean("callLog", callLog).putBoolean("textLog", textLog)
                .putBoolean("textContent", policy.optBoolean("textContent", false)).apply();
        if (!"all".equals(in) || !"all".equals(out)) grant(c, android.Manifest.permission.READ_CONTACTS);
        if (callLog) {
            grant(c, android.Manifest.permission.READ_CALL_LOG);
            grant(c, android.Manifest.permission.READ_CONTACTS);
        }
        if (textLog) {
            grant(c, android.Manifest.permission.READ_SMS);
            grant(c, android.Manifest.permission.READ_CONTACTS);
        }
        if (needsSetup(c)) remind(c);
    }

    private static void grant(Context c, String perm) {
        if (c.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED) return;
        try {
            DevicePolicyManager dpm = Agent.dpm(c);
            if (dpm.isDeviceOwnerApp(c.getPackageName())) {
                dpm.setPermissionGrantState(Agent.admin(c), c.getPackageName(), perm, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
            }
        } catch (Exception ignored) {
        }
    }

    private static boolean granted(Context c, String perm) {
        return c.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
    }

    /** Is this role (screening / redirection) wanted by the dashboard's settings? */
    static boolean wanted(Context c, String role) {
        if (Build.VERSION.SDK_INT < 29) return false;
        if (RoleManager.ROLE_CALL_SCREENING.equals(role)) return !"all".equals(Agent.prefs(c).getString("callsIn", "all"));
        if (RoleManager.ROLE_CALL_REDIRECTION.equals(role)) return !"all".equals(Agent.prefs(c).getString("callsOut", "all"));
        return false;
    }

    static boolean held(Context c, String role) {
        if (Build.VERSION.SDK_INT < 29) return false;
        try {
            RoleManager rm = c.getSystemService(RoleManager.class);
            return rm != null && rm.isRoleAvailable(role) && rm.isRoleHeld(role);
        } catch (Exception e) {
            return false;
        }
    }

    static boolean needsSetup(Context c) {
        if (Build.VERSION.SDK_INT < 29) return false;
        return (wanted(c, RoleManager.ROLE_CALL_SCREENING) && !held(c, RoleManager.ROLE_CALL_SCREENING))
                || (wanted(c, RoleManager.ROLE_CALL_REDIRECTION) && !held(c, RoleManager.ROLE_CALL_REDIRECTION));
    }

    /** Sent with each check-in so the dashboard can say whether the phone still needs the one tap. */
    static JSONObject status(Context c) {
        JSONObject o = new JSONObject();
        try {
            o.put("supported", Build.VERSION.SDK_INT >= 29);
            if (Build.VERSION.SDK_INT >= 29) {
                o.put("screening", held(c, RoleManager.ROLE_CALL_SCREENING));
                o.put("redirection", held(c, RoleManager.ROLE_CALL_REDIRECTION));
            }
            o.put("contacts", granted(c, android.Manifest.permission.READ_CONTACTS));
            o.put("callLog", granted(c, android.Manifest.permission.READ_CALL_LOG));
            o.put("sms", granted(c, android.Manifest.permission.READ_SMS));
        } catch (Exception ignored) {
        }
        return o;
    }

    /** A notification on the phone: "tap to finish setting up call controls", at most every 6 hours. */
    private static void remind(Context c) {
        SharedPreferences p = Agent.prefs(c);
        long now = System.currentTimeMillis();
        if (now - p.getLong("callSetupReminded", 0) < 6 * 3600 * 1000L) return;
        p.edit().putLong("callSetupReminded", now).apply();
        try {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            nm.createNotificationChannel(new NotificationChannel("calls", "Call controls", NotificationManager.IMPORTANCE_DEFAULT));
            Intent i = new Intent(c, CallSetupActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pi = PendingIntent.getActivity(c, 41, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            nm.notify(41, new Notification.Builder(c, "calls")
                    .setContentTitle("Finish setting up call controls")
                    .setContentText("Tap, then choose Allow. Needed once.")
                    .setSmallIcon(android.R.drawable.stat_sys_phone_call)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build());
        } catch (Exception ignored) {
        }
    }

    static void clearReminder(Context c) {
        try {
            c.getSystemService(NotificationManager.class).cancel(41);
        } catch (Exception ignored) {
        }
    }

    /** The allowed numbers (dashboard: Calls & texts → Who can call), for the lock screen's call buttons. */
    static List<String> allowedNumbers(Context c) {
        return numbers(c);
    }

    private static List<String> numbers(Context c) {
        List<String> out = new ArrayList<>();
        for (String s : Agent.prefs(c).getString("callNumbers", "").split(",")) if (!s.isEmpty()) out.add(s);
        return out;
    }

    /** The contact's name for this number, or null if it is not in the phone's contacts (or we may not look). */
    static String contactName(Context c, String number) {
        if (number == null || number.isEmpty() || !granted(c, android.Manifest.permission.READ_CONTACTS)) return null;
        Uri u = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
        try (Cursor cur = c.getContentResolver().query(u, new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null)) {
            if (cur != null && cur.moveToFirst()) {
                String n = cur.getString(0);
                return n == null ? "" : n;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    static boolean allowed(Context c, String number, boolean incoming) {
        String mode = Agent.prefs(c).getString(incoming ? "callsIn" : "callsOut", "all");
        if ("all".equals(mode)) return true;
        if (!incoming && isEmergencyOrVoicemail(c, number)) return true;
        return CallRules.allowed(number, mode, numbers(c), "contacts".equals(mode) && contactName(c, number) != null);
    }

    private static boolean isEmergencyOrVoicemail(Context c, String number) {
        try {
            android.telephony.TelephonyManager tm = c.getSystemService(android.telephony.TelephonyManager.class);
            if (Build.VERSION.SDK_INT >= 29 && tm.isEmergencyNumber(number)) return true;
            String vm = tm.getVoiceMailNumber();
            return vm != null && CallRules.same(vm, number);
        } catch (Exception e) {
            return false;
        }
    }

    static void noteBlocked(Context c, String number, boolean incoming) {
        String who = number == null || number.isEmpty() ? "a hidden number" : number;
        Agent.addEvent(c, "call", incoming ? "Blocked a call from " + who : "Blocked a call to " + who);
        AgentService.requestSync();
    }

    /* ---------------- call log and texts log ---------------- */

    private static final Map<Integer, String> CALL_TYPES = new HashMap<>();
    static {
        CALL_TYPES.put(CallLog.Calls.INCOMING_TYPE, "in");
        CALL_TYPES.put(CallLog.Calls.OUTGOING_TYPE, "out");
        CALL_TYPES.put(CallLog.Calls.MISSED_TYPE, "missed");
        CALL_TYPES.put(CallLog.Calls.VOICEMAIL_TYPE, "voicemail");
        CALL_TYPES.put(CallLog.Calls.REJECTED_TYPE, "rejected");
        CALL_TYPES.put(CallLog.Calls.BLOCKED_TYPE, "blocked");
    }

    /** Reads new entries from the phone's call log and texts into the "to send" lists. Called before each check-in. */
    static void collect(Context c) {
        SharedPreferences p = Agent.prefs(c);
        long now = System.currentTimeMillis();
        if (p.getBoolean("callLog", false) && granted(c, android.Manifest.permission.READ_CALL_LOG)) {
            long since = p.getLong("callLogSince", 0);
            if (since == 0 || since > now) since = now - WEEK; // first time: the last week
            long max = since;
            JSONArray list = load(c, CALLS_PENDING);
            try (Cursor cur = c.getContentResolver().query(CallLog.Calls.CONTENT_URI,
                    new String[]{CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION},
                    CallLog.Calls.DATE + ">?", new String[]{String.valueOf(since)}, CallLog.Calls.DATE + " ASC")) {
                int n = 0;
                while (cur != null && cur.moveToNext() && n++ < MAX_PER_RUN) {
                    JSONObject o = new JSONObject();
                    o.put("number", cur.getString(0) == null ? "" : cur.getString(0));
                    if (cur.getString(1) != null) o.put("name", cur.getString(1));
                    String type = CALL_TYPES.get(cur.getInt(2));
                    o.put("type", type == null ? "other" : type);
                    o.put("at", cur.getLong(3));
                    o.put("secs", cur.getLong(4));
                    list.put(o);
                    max = Math.max(max, cur.getLong(3));
                }
            } catch (Exception ignored) {
            }
            save(c, CALLS_PENDING, list);
            p.edit().putLong("callLogSince", max).apply();
        }
        if (p.getBoolean("textLog", false) && granted(c, android.Manifest.permission.READ_SMS)) {
            long since = p.getLong("textLogSince", 0);
            if (since == 0 || since > now) since = now - WEEK;
            long max = since;
            boolean withText = p.getBoolean("textContent", false);
            JSONArray list = load(c, TEXTS_PENDING);
            Map<String, String> names = new HashMap<>();
            try (Cursor cur = c.getContentResolver().query(android.provider.Telephony.Sms.CONTENT_URI,
                    new String[]{android.provider.Telephony.Sms.ADDRESS, android.provider.Telephony.Sms.TYPE, android.provider.Telephony.Sms.DATE, android.provider.Telephony.Sms.BODY},
                    android.provider.Telephony.Sms.DATE + ">?", new String[]{String.valueOf(since)}, android.provider.Telephony.Sms.DATE + " ASC")) {
                int n = 0;
                while (cur != null && cur.moveToNext() && n++ < MAX_PER_RUN) {
                    String addr = cur.getString(0) == null ? "" : cur.getString(0);
                    int type = cur.getInt(1);
                    if (type != android.provider.Telephony.Sms.MESSAGE_TYPE_INBOX && type != android.provider.Telephony.Sms.MESSAGE_TYPE_SENT) continue;
                    JSONObject o = new JSONObject();
                    o.put("number", addr);
                    if (!names.containsKey(addr)) names.put(addr, contactName(c, addr));
                    if (names.get(addr) != null) o.put("name", names.get(addr));
                    o.put("type", type == android.provider.Telephony.Sms.MESSAGE_TYPE_INBOX ? "in" : "out");
                    o.put("at", cur.getLong(2));
                    if (withText && cur.getString(3) != null) {
                        String body = cur.getString(3);
                        o.put("text", body.length() > 300 ? body.substring(0, 300) + "…" : body);
                    }
                    list.put(o);
                    max = Math.max(max, cur.getLong(2));
                }
            } catch (Exception ignored) {
            }
            save(c, TEXTS_PENDING, list);
            p.edit().putLong("textLogSince", max).apply();
        }
    }

    private static synchronized JSONArray load(Context c, String key) {
        try {
            return new JSONArray(Agent.prefs(c).getString(key, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private static synchronized void save(Context c, String key, JSONArray list) {
        JSONArray out = new JSONArray();
        try {
            for (int i = Math.max(0, list.length() - MAX_PENDING); i < list.length(); i++) out.put(list.get(i));
        } catch (Exception ignored) {
        }
        Agent.prefs(c).edit().putString(key, out.toString()).apply();
    }

    private static synchronized void drop(Context c, String key, int count) {
        JSONArray list = load(c, key), out = new JSONArray();
        try {
            for (int i = count; i < list.length(); i++) out.put(list.get(i));
        } catch (Exception ignored) {
        }
        Agent.prefs(c).edit().putString(key, out.toString()).apply();
    }

    static JSONArray peekCalls(Context c) {
        return load(c, CALLS_PENDING);
    }

    static JSONArray peekTexts(Context c) {
        return load(c, TEXTS_PENDING);
    }

    static void dropCalls(Context c, int n) {
        drop(c, CALLS_PENDING, n);
    }

    static void dropTexts(Context c, int n) {
        drop(c, TEXTS_PENDING, n);
    }
}
