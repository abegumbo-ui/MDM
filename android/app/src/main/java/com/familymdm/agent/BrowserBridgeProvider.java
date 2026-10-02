package com.familymdm.agent;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Bundle;

import com.familymdm.agent.sitepolicy.SitePolicy;

import org.json.JSONObject;

/**
 * The only door the separate Browser app has into this agent's data: a single call() method, reachable
 * only by something signed with this same key (android:permission on the <provider> declaration). Used
 * for the master code's "allow this page" / "allow the whole site" actions, which need a yes/no answer
 * right away rather than a fire-and-forget broadcast.
 */
public class BrowserBridgeProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (!"approveSite".equals(method) || extras == null) return errorBundle("Unknown request");
        String url = extras.getString("url");
        String type = extras.getString("type");
        String code = extras.getString("code");
        if (url == null || url.isEmpty() || (!"domain".equals(type) && !"exact".equals(type))) {
            return errorBundle("Bad request");
        }
        if (SitePolicy.isBlockedAdult(url)) return errorBundle("That site can't be allowed.");
        String err = Master.check(getContext(), code == null ? "" : code);
        if (err != null) return errorBundle(err);
        try {
            String site = "domain".equals(type) ? SitePolicy.hostOf(url) : url;
            if (site == null) return errorBundle("Not a valid address");
            JSONObject cfg = LocalConfig.load(getContext());
            JSONObject sites = cfg.optJSONObject("sites");
            if (sites == null) sites = new JSONObject();
            JSONObject entry = new JSONObject();
            entry.put("type", type);
            entry.put("url", site);
            entry.put("label", site);
            entry.put("blockImages", false);
            entry.put("installable", true);
            sites.put(type + ":" + site, entry);
            cfg.put("sites", sites);
            LocalConfig.save(getContext(), cfg);
            Agent.addEvent(getContext(), "local", "Master code on phone (Browser): allowed " + site);
            applyNow();
        } catch (Exception e) {
            return errorBundle("Could not save it: " + e.getMessage());
        }
        return new Bundle();
    }

    /**
     * Offline: recompute the phone's whole policy from its own settings, as usual. Online: the dashboard
     * owns the real policy, so just add this one site to the policy already in effect; the next check-in
     * with the server will reconcile it either way.
     */
    private void applyNow() throws Exception {
        if (Agent.standalone(getContext())) {
            LocalConfig.refresh(getContext());
            return;
        }
        String stored = Agent.prefs(getContext()).getString("policy", null);
        if (stored == null) return;
        JSONObject policy = new JSONObject(stored);
        org.json.JSONArray sites = policy.optJSONArray("sites");
        if (sites == null) sites = new org.json.JSONArray();
        JSONObject cfg = LocalConfig.load(getContext());
        JSONObject cfgSites = cfg.optJSONObject("sites");
        if (cfgSites != null) {
            java.util.Iterator<String> it = cfgSites.keys();
            while (it.hasNext()) {
                JSONObject entry = cfgSites.getJSONObject(it.next());
                boolean present = false;
                for (int i = 0; i < sites.length(); i++) {
                    if (entry.optString("url").equals(sites.getJSONObject(i).optString("url"))) {
                        present = true;
                        break;
                    }
                }
                if (!present) sites.put(entry);
            }
        }
        policy.put("sites", sites);
        Agent.prefs(getContext()).edit().putString("policy", policy.toString()).apply();
        PolicyApplier.apply(getContext(), policy);
    }

    private Bundle errorBundle(String msg) {
        Bundle b = new Bundle();
        b.putString("error", msg);
        return b;
    }

    // Everything else about this provider is unused: it exists only for call().
    @Override
    public android.database.Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
