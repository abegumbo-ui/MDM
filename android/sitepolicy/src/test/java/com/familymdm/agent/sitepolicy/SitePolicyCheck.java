package com.familymdm.agent.sitepolicy;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * Plain checks for SitePolicy (no test framework needed). Run on a computer:
 *   javac -cp json.jar -d out android/sitepolicy/src/main/java/com/familymdm/agent/sitepolicy/SitePolicy.java android/sitepolicy/src/test/java/com/familymdm/agent/sitepolicy/SitePolicyCheck.java
 *   java -cp json.jar:out com.familymdm.agent.sitepolicy.SitePolicyCheck
 */
public class SitePolicyCheck {
    static int failures = 0;

    static void check(boolean ok, String what) {
        if (!ok) {
            failures++;
            System.out.println("FAIL: " + what);
        } else {
            System.out.println("ok:   " + what);
        }
    }

    static JSONArray sites(String... jsonObjects) throws Exception {
        JSONArray a = new JSONArray();
        for (String s : jsonObjects) a.put(new JSONObject(s));
        return a;
    }

    public static void main(String[] args) throws Exception {
        List<SitePolicy.Site> domain = SitePolicy.parse(sites("{\"type\":\"domain\",\"url\":\"https://nytimes.com\",\"host\":\"nytimes.com\"}"));
        check(SitePolicy.allowed("https://nytimes.com/section/world", domain), "domain covers subpages");
        check(SitePolicy.allowed("https://www.nytimes.com/", domain), "domain covers www");
        check(SitePolicy.allowed("https://m.nytimes.com/x", domain), "domain covers subdomains");
        check(!SitePolicy.allowed("https://notnytimes.com/", domain), "domain does not cover a different domain");
        check(!SitePolicy.allowed("https://evilnytimes.com/", domain), "domain does not cover a look-alike domain");

        List<SitePolicy.Site> exact = SitePolicy.parse(sites("{\"type\":\"exact\",\"url\":\"https://example.com/safe-page\",\"host\":\"example.com\"}"));
        check(SitePolicy.allowed("https://example.com/safe-page", exact), "exact matches the page");
        check(SitePolicy.allowed("https://example.com/safe-page/", exact), "exact ignores a trailing slash");
        check(SitePolicy.allowed("https://example.com/safe-page?x=1#y", exact), "exact ignores query/fragment");
        check(!SitePolicy.allowed("https://example.com/other-page", exact), "exact does not cover a different page");
        check(!SitePolicy.allowed("https://example.com/", exact), "exact does not cover the site root");

        SitePolicy.Site m = SitePolicy.matching("https://nytimes.com/x", domain);
        check(m != null && m.host.equals("nytimes.com"), "matching() returns the entry that matched");
        check(SitePolicy.matching("https://nope.com/", domain) == null, "matching() returns null when nothing matches");

        JSONArray mixed = sites("{\"type\":\"bogus\",\"url\":\"https://x.com\"}");
        mixed.put("not an object"); // a malformed entry should just be skipped, not throw
        check(SitePolicy.parse(mixed).isEmpty(), "unknown type / non-object entries are dropped");

        check(SitePolicy.isBlockedAdult("https://pornhub.com/video"), "a known adult site is blocked");
        check(SitePolicy.isBlockedAdult("https://www.pornhub.com/"), "...even with a www. prefix");
        check(SitePolicy.isBlockedAdult("https://sub.pornhub.com/"), "...and any subdomain");
        check(!SitePolicy.isBlockedAdult("https://notpornhub.com/"), "a look-alike domain is not swept in");
        check(!SitePolicy.isBlockedAdult("https://nytimes.com/"), "an ordinary site is not blocked");

        System.out.println(failures == 0 ? "ALL PASSED" : failures + " FAILED");
        if (failures > 0) System.exit(1);
    }
}
