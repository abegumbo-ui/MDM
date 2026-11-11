package com.familymdm.agent;

import android.content.Context;

import com.familymdm.agent.sitepolicy.WordFilter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** The administrator's blocked words, from the stored policy (see WordFilter for how they match). */
final class BlockedWords {
    private BlockedWords() {}

    private static String policyRef;
    private static List<String> words = new ArrayList<>();

    static synchronized List<String> list(Context c) {
        String policy = Agent.prefs(c).getString("policy", null);
        if (policy != policyRef) { // the same String object until the policy changes, so this is cheap
            policyRef = policy;
            List<String> out = new ArrayList<>();
            try {
                JSONArray a = policy == null ? null : new JSONObject(policy).optJSONArray("blockedWords");
                for (int i = 0; a != null && i < a.length(); i++) {
                    String w = WordFilter.clean(a.optString(i));
                    if (w != null) out.add(w);
                }
            } catch (Exception ignored) {
            }
            words = out;
        }
        return words;
    }

    /** For the site filter: is there a blocked word in this site's name? */
    static boolean blocksHost(Context c, String host) {
        List<String> w = list(c);
        return !w.isEmpty() && host != null && WordFilter.match(host, w) != null;
    }
}
