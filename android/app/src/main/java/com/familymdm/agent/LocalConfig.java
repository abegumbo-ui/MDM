package com.familymdm.agent;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/** Offline mode: where the phone keeps its own settings and how it turns them into action. */
final class LocalConfig {
    private LocalConfig() {}

    static JSONObject load(Context c) {
        try {
            return LocalPolicy.normalize(new JSONObject(Agent.prefs(c).getString("localConfig", "{}")));
        } catch (Exception e) {
            try {
                return LocalPolicy.normalize(null);
            } catch (Exception impossible) {
                return new JSONObject();
            }
        }
    }

    static void save(Context c, JSONObject cfg) {
        try {
            Agent.prefs(c).edit().putString("localConfig", LocalPolicy.normalize(cfg).toString()).apply();
        } catch (Exception ignored) {
        }
    }

    /** Package names of the apps the phone currently reports. */
    static JSONArray reportedNames(Context c) throws Exception {
        JSONArray packages = PolicyApplier.collectPackages(c);
        JSONArray names = new JSONArray();
        for (int i = 0; i < packages.length(); i++) names.put(packages.getJSONObject(i).getString("p"));
        return names;
    }

    /**
     * Works out the policy from the saved settings and applies it right now (any thread, but not the UI thread:
     * it talks to Android a lot). The policy is also stored so the rest of the agent sees it.
     */
    static synchronized JSONObject refresh(Context c) throws Exception {
        JSONArray names = reportedNames(c);
        JSONObject cfg = load(c);

        // Apps that were already on the phone before "hold new apps" was switched on count as approved.
        JSONArray knownArr = cfg.optJSONArray("known");
        Set<String> known = null;
        if (knownArr != null) {
            known = new LinkedHashSet<>();
            for (int i = 0; i < knownArr.length(); i++) known.add(knownArr.getString(i));
        }
        Set<String> current = new HashSet<>();
        for (int i = 0; i < names.length(); i++) current.add(names.getString(i));
        if (known == null) {
            known = new LinkedHashSet<>(current);
            cfg.put("known", new JSONArray(known));
            save(c, cfg);
        } else if (!cfg.optBoolean("approveNew", false) && !known.containsAll(current)) {
            known.addAll(current);
            cfg.put("known", new JSONArray(known));
            save(c, cfg);
        }

        JSONObject policy = LocalPolicy.build(cfg, names, known);
        Agent.prefs(c).edit().putString("policy", policy.toString()).apply();
        PolicyApplier.apply(c, policy);
        return policy;
    }
}
