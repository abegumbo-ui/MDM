package com.familymdm.agent;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

import com.familymdm.agent.sitepolicy.CallRules;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * Contacts, from the dashboard's Calls & texts → Contacts:
 * - "Show the phone's contacts on the dashboard": the list is sent when it changes.
 * - "Put the allowed numbers into the phone's contacts": the numbers you allow show by name in the
 *   phone app. Only contacts the Agent added itself are ever changed or removed; a number already
 *   in the phone's contacts is left as it is.
 * Permissions are granted by the Agent itself as device owner.
 */
final class ContactSync {
    private ContactSync() {}

    private static final String MANAGED = "managedContacts"; // {digits: {id, name}}
    private static volatile String pendingHash;

    private static void grant(Context c, String perm) {
        if (c.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED) return;
        try {
            if (Agent.dpm(c).isDeviceOwnerApp(c.getPackageName())) {
                Agent.dpm(c).setPermissionGrantState(Agent.admin(c), c.getPackageName(), perm,
                        android.app.admin.DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
            }
        } catch (Exception ignored) {
        }
    }

    static void apply(Context c, JSONObject policy) {
        boolean report = policy.optBoolean("reportContacts", false);
        Agent.prefs(c).edit().putBoolean("reportContacts", report).apply();
        if (report) grant(c, android.Manifest.permission.READ_CONTACTS);
        JSONArray want = policy.optJSONArray("contactsToAdd");
        boolean managing = want != null && want.length() > 0;
        if (managing) {
            grant(c, android.Manifest.permission.READ_CONTACTS);
            grant(c, android.Manifest.permission.WRITE_CONTACTS);
        }
        if (c.checkSelfPermission(android.Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) return;
        try {
            sync(c, managing ? want : new JSONArray());
        } catch (Exception ignored) {
        }
    }

    private static void sync(Context c, JSONArray want) throws Exception {
        ContentResolver cr = c.getContentResolver();
        JSONObject managed = new JSONObject(Agent.prefs(c).getString(MANAGED, "{}"));
        JSONObject wanted = new JSONObject();
        for (int i = 0; i < want.length(); i++) {
            JSONObject w = want.optJSONObject(i);
            if (w == null) continue;
            String digits = CallRules.digits(w.optString("number"));
            if (!digits.isEmpty()) wanted.put(digits, w);
        }
        // Remove ours that are no longer wanted, or whose name changed (re-added below).
        for (Iterator<String> it = managed.keys(); it.hasNext(); ) {
            String d = it.next();
            JSONObject m = managed.getJSONObject(d);
            JSONObject w = wanted.optJSONObject(d);
            if (w == null || !name(w).equals(m.optString("name"))) {
                Uri u = ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, m.getLong("id")).buildUpon()
                        .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build();
                cr.delete(u, null, null);
                it.remove();
            }
        }
        // Add the wanted ones that are not in the phone's contacts yet.
        for (Iterator<String> it = wanted.keys(); it.hasNext(); ) {
            String d = it.next();
            if (managed.has(d)) continue;
            JSONObject w = wanted.getJSONObject(d);
            String number = w.optString("number");
            if (Calls.contactName(c, number) != null) continue; // already a contact: not ours to touch
            ArrayList<ContentProviderOperation> ops = new ArrayList<>();
            ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null).build());
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name(w)).build());
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE).build());
            ContentProviderResult[] r = cr.applyBatch(ContactsContract.AUTHORITY, ops);
            JSONObject m = new JSONObject();
            m.put("id", ContentUris.parseId(r[0].uri));
            m.put("name", name(w));
            managed.put(d, m);
        }
        Agent.prefs(c).edit().putString(MANAGED, managed.toString()).apply();
    }

    private static String name(JSONObject w) {
        String n = w.optString("name", "").trim();
        return n.isEmpty() ? w.optString("number") : n;
    }

    /** The phone's contacts, if switched on and changed since last sent; otherwise null. */
    static JSONArray ifChanged(Context c) {
        if (!Agent.prefs(c).getBoolean("reportContacts", false)
                || c.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null;
        JSONArray out = new JSONArray();
        try (Cursor cur = c.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER},
                null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")) {
            while (cur != null && cur.moveToNext() && out.length() < 1000) {
                JSONObject o = new JSONObject();
                o.put("name", cur.getString(0) == null ? "" : cur.getString(0));
                o.put("number", cur.getString(1) == null ? "" : cur.getString(1));
                out.put(o);
            }
        } catch (Exception e) {
            return null;
        }
        String s = out.toString();
        String hash = s.length() + ":" + s.hashCode();
        if (hash.equals(Agent.prefs(c).getString("contactsSentHash", ""))) return null;
        pendingHash = hash;
        return out;
    }

    /** After a check-in that carried the list. */
    static void sent(Context c) {
        if (pendingHash != null) Agent.prefs(c).edit().putString("contactsSentHash", pendingHash).apply();
        pendingHash = null;
    }
}
