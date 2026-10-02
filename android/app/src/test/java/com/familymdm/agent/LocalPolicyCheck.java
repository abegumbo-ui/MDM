package com.familymdm.agent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Plain checks for LocalPolicy (no test framework needed). Run on a computer:
 *   javac -cp json.jar -d out android/app/src/main/java/com/familymdm/agent/LocalPolicy.java android/app/src/test/java/com/familymdm/agent/LocalPolicyCheck.java
 *   java -cp json.jar:out com.familymdm.agent.LocalPolicyCheck
 */
public class LocalPolicyCheck {
    static int failures = 0;

    static void check(boolean ok, String what) {
        if (!ok) {
            failures++;
            System.out.println("FAIL: " + what);
        } else {
            System.out.println("ok:   " + what);
        }
    }

    static JSONArray arr(String... items) {
        return new JSONArray(Arrays.asList(items));
    }

    static Set<String> set(JSONArray a) throws Exception {
        Set<String> s = new HashSet<>();
        for (int i = 0; i < a.length(); i++) s.add(a.getString(i));
        return s;
    }

    public static void main(String[] args) throws Exception {
        // hides unlisted apps only when asked, never protected ones
        JSONObject cfg = new JSONObject("{\"blockUnlisted\":true,\"apps\":{\"com.google.android.apps.maps\":{\"mode\":\"allow\"}}}");
        JSONObject p = LocalPolicy.build(cfg, arr("com.google.android.apps.maps", "com.android.chrome", "com.android.systemui", "com.motorola.launcher3"), null);
        check(set(p.getJSONArray("hide")).equals(new HashSet<>(Arrays.asList("com.android.chrome"))), "hide-unlisted hides only the unprotected unlisted app");
        check(set(p.getJSONArray("show")).contains("com.google.android.apps.maps"), "allowed app is shown");
        check(set(p.getJSONArray("show")).contains("com.android.systemui") && set(p.getJSONArray("show")).contains("com.motorola.launcher3"), "protected apps are never hidden");
        check(set(p.getJSONArray("allowed")).equals(new HashSet<>(Arrays.asList("com.google.android.apps.maps"))), "allowed list has only allow/force apps");

        // off by default
        JSONObject q = LocalPolicy.build(new JSONObject(), arr("x.y"), null);
        check(q.getJSONArray("hide").length() == 0 && set(q.getJSONArray("show")).contains("x.y"), "nothing is hidden by default");

        // explicit block applies even to apps not reported, and beats protection
        JSONObject r = LocalPolicy.build(new JSONObject("{\"apps\":{\"com.example.game\":{\"mode\":\"block\"},\"com.android.settings\":{\"mode\":\"block\"}}}"), arr("com.android.settings"), null);
        check(set(r.getJSONArray("hide")).equals(new HashSet<>(Arrays.asList("com.example.game", "com.android.settings"))), "explicit blocks apply (even unreported / protected)");

        // approval mode holds apps that are not in the baseline
        JSONObject a = LocalPolicy.build(new JSONObject("{\"approveNew\":true}"), arr("old.app", "new.app"), new HashSet<>(Arrays.asList("old.app")));
        check(set(a.getJSONArray("hide")).equals(new HashSet<>(Arrays.asList("new.app"))) && set(a.getJSONArray("pending")).equals(new HashSet<>(Arrays.asList("new.app"))), "approval mode holds new apps");
        check(set(a.getJSONArray("known")).contains("old.app"), "baseline is sent along");
        JSONObject b = LocalPolicy.build(new JSONObject("{\"approveNew\":true,\"apps\":{\"new.app\":{\"mode\":\"allow\"}}}"), arr("old.app", "new.app"), new HashSet<>(Arrays.asList("old.app")));
        check(b.getJSONArray("hide").length() == 0 && b.getJSONArray("pending").length() == 0, "approved app is shown");
        JSONObject c0 = LocalPolicy.build(new JSONObject("{\"approveNew\":true}"), arr("new.app"), null);
        check(c0.getJSONArray("hide").length() == 0, "without a baseline nothing is held");

        // restrictions: defaults on, debugging off until the end
        JSONObject d = LocalPolicy.build(new JSONObject(), arr(), null);
        Set<String> rs = set(d.getJSONArray("restrictions"));
        check(rs.contains("no_factory_reset") && rs.contains("no_safe_boot") && !rs.contains("no_debugging_features") && !rs.contains("no_install_apps"), "restriction defaults");
        JSONObject e = LocalPolicy.build(new JSONObject("{\"restrictions\":{\"debuggingDisabled\":true,\"safeBootDisabled\":false}}"), arr(), null);
        Set<String> rs2 = set(e.getJSONArray("restrictions"));
        check(rs2.contains("no_debugging_features") && !rs2.contains("no_safe_boot"), "restriction toggles");

        // schedules: validated, not kept for blocked apps
        JSONObject s = LocalPolicy.build(new JSONObject("{\"apps\":{\"a.b\":{\"mode\":\"allow\",\"schedule\":{\"days\":[1,1,3,9],\"from\":\"08:00\",\"to\":\"17:30\"}},\"c.d\":{\"mode\":\"block\",\"schedule\":{\"days\":[1],\"from\":\"10:00\",\"to\":\"12:00\"}},\"e.f\":{\"mode\":\"allow\",\"schedule\":{\"days\":[1],\"from\":\"9:00\",\"to\":\"12:00\"}}}}"), arr(), null);
        JSONObject sched = s.getJSONObject("schedules");
        check(sched.length() == 1 && sched.has("a.b"), "only valid schedules on non-blocked apps survive");
        check(sched.getJSONObject("a.b").getJSONArray("days").toString().equals("[1,3]"), "schedule days are de-duplicated and range-checked");
        check(LocalPolicy.normalizeSchedule(new JSONObject("{\"days\":[1],\"from\":\"09:00\",\"to\":\"09:00\"}")) == null, "empty window is rejected");

        // Factory Reset Protection account IDs
        JSONArray ids = new JSONArray(Arrays.asList("people/118273645564738291027", "118273645564738291027", "me@gmail.com", "123", " 118273645564738291028 "));
        check(LocalPolicy.normalizeAccounts(ids).equals(Arrays.asList("118273645564738291027", "118273645564738291028")), "account IDs are cleaned (people/ prefix, email and short numbers dropped, de-duplicated)");
        JSONArray many = new JSONArray();
        for (int i = 0; i < 9; i++) many.put("11827364556473829102" + i);
        check(LocalPolicy.normalizeAccounts(many).size() == 3, "at most 3 account IDs");
        JSONObject f = LocalPolicy.build(new JSONObject("{\"frpAccounts\":[\"118273645564738291027\"],\"homeScreen\":true}"), arr(), null);
        check(f.getJSONArray("frpAccounts").getString(0).equals("118273645564738291027") && f.getBoolean("homeScreen"), "FRP accounts and home screen reach the policy");

        // garbage in the settings is ignored
        JSONObject g = LocalPolicy.normalize(new JSONObject("{\"apps\":{\"a b\":{\"mode\":\"allow\"},\"ok.app\":{\"mode\":\"evil\"},\"fine.app\":{\"mode\":\"force\"}}}"));
        check(g.getJSONObject("apps").length() == 1 && g.getJSONObject("apps").has("fine.app"), "bad package names and modes are dropped");
        check(LocalPolicy.isProtected("com.familymdm.agent") && LocalPolicy.isProtected("com.familymdm.browser") && LocalPolicy.isProtected("com.android.documentsui") && !LocalPolicy.isProtected("com.android.chrome"), "protected list matches the dashboard's");

        // sites: kept keyed through normalize() (like apps), flattened to an array by build()
        JSONObject withSite = new JSONObject();
        JSONObject oneSite = new JSONObject();
        oneSite.put("exact:https://news.example/a", new JSONObject("{\"type\":\"exact\",\"url\":\"https://news.example/a\"}"));
        oneSite.put("bad", new JSONObject("{\"type\":\"nope\",\"url\":\"https://x\"}"));
        withSite.put("sites", oneSite);
        JSONObject normalized = LocalPolicy.normalize(withSite);
        check(normalized.getJSONObject("sites").length() == 1 && normalized.getJSONObject("sites").has("exact:https://news.example/a"), "sites are kept keyed, invalid entries dropped");
        JSONObject normalizedAgain = LocalPolicy.normalize(normalized);
        check(normalizedAgain.getJSONObject("sites").length() == 1, "a saved site survives being normalized a second time");
        JSONObject sitePolicy = LocalPolicy.build(normalized, arr(), null);
        check(sitePolicy.getJSONArray("sites").length() == 1 && sitePolicy.getJSONArray("sites").getJSONObject(0).getString("host").equals("news.example"), "build() flattens sites into the array SitePolicy reads");

        System.out.println(failures == 0 ? "ALL PASSED" : failures + " FAILED");
        if (failures > 0) System.exit(1);
    }
}
