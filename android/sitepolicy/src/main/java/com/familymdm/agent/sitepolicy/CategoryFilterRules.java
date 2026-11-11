package com.familymdm.agent.sitepolicy;

/**
 * The one rule for the phone-wide category filter (Moderate / Strict applied through the Agent's
 * site filter, so it covers Chrome and other browsers, not just our own). Kept as a plain function
 * so it can be checked on a computer. See CategoryFilterRulesCheck.
 *
 *  - A site on the approved list is always allowed.
 *  - The core categories (adult, gambling, drugs, scams, piracy) are blocked for every app: no
 *    ordinary app needs those sites, and a lookup does not always say which app asked.
 *  - Social media (added by Strict) is blocked only for browsers, so a browser filter does not
 *    quietly break WhatsApp or another app the person is allowed to use.
 */
public final class CategoryFilterRules {
    private CategoryFilterRules() {}

    public static boolean blocks(boolean approved, boolean coreHit, boolean socialHit, boolean appIsBrowser) {
        if (approved) return false;
        if (coreHit) return true;
        return socialHit && appIsBrowser;
    }
}
