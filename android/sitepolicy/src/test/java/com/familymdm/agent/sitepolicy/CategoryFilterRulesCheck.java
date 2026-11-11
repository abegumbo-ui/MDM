package com.familymdm.agent.sitepolicy;

/**
 * Plain checks for CategoryFilterRules. Run on a computer:
 *   javac -d out android/sitepolicy/src/main/java/com/familymdm/agent/sitepolicy/CategoryFilterRules.java android/sitepolicy/src/test/java/com/familymdm/agent/sitepolicy/CategoryFilterRulesCheck.java
 *   java -cp out com.familymdm.agent.sitepolicy.CategoryFilterRulesCheck
 */
public class CategoryFilterRulesCheck {
    static int failures = 0;

    static void check(boolean ok, String what) {
        if (!ok) {
            failures++;
            System.out.println("FAIL: " + what);
        } else {
            System.out.println("ok:   " + what);
        }
    }

    public static void main(String[] args) {
        // (approved, coreHit, socialHit, appIsBrowser)
        check(!CategoryFilterRules.blocks(false, false, false, true), "an ordinary site is not blocked");
        check(CategoryFilterRules.blocks(false, true, false, true), "a core-category site is blocked in a browser");
        check(CategoryFilterRules.blocks(false, true, false, false), "a core-category site is blocked in any other app too");
        check(CategoryFilterRules.blocks(false, false, true, true), "a social site is blocked in a browser");
        check(!CategoryFilterRules.blocks(false, false, true, false), "a social site is NOT blocked for other apps (WhatsApp and friends keep working)");
        check(!CategoryFilterRules.blocks(true, true, true, true), "an approved site is allowed even if it is in a blocked category");
        check(!CategoryFilterRules.blocks(true, false, true, true), "an approved social site is allowed in a browser");
        check(CategoryFilterRules.blocks(false, true, true, false), "core wins even when social also matches");

        System.out.println(failures == 0 ? "ALL OK" : failures + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
