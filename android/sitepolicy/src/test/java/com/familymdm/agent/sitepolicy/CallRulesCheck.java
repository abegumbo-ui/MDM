package com.familymdm.agent.sitepolicy;

import java.util.Arrays;
import java.util.List;

/**
 * Plain checks for CallRules. Run on a computer:
 *   javac -d out android/sitepolicy/src/main/java/com/familymdm/agent/sitepolicy/CallRules.java android/sitepolicy/src/test/java/com/familymdm/agent/sitepolicy/CallRulesCheck.java
 *   java -cp out com.familymdm.agent.sitepolicy.CallRulesCheck
 */
public class CallRulesCheck {
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
        check(CallRules.same("+972 52-123-4567", "052 123 4567"), "Israeli number with and without the country code");
        check(CallRules.same("+1 (212) 555-1234", "212.555.1234"), "US number with and without +1");
        check(CallRules.same("555-1234", "+1 212 555 1234"), "a 7-digit local number matches the full one");
        check(!CallRules.same("052 123 4567", "052 123 4568"), "one digit different");
        check(!CallRules.same("611", "+1 212 555 0611"), "short codes must match exactly");
        check(CallRules.same("*611", "611"), "short code with a star");
        check(!CallRules.same("", "123"), "empty never matches");
        check(!CallRules.same(null, null), "nothing never matches");

        List<String> list = Arrays.asList("+972521234567", "212 555 1234");
        check(CallRules.allowed("0505555555", "all", list, false), "anyone: a stranger gets through");
        check(CallRules.allowed(null, "all", list, false), "anyone: a hidden number gets through");
        check(CallRules.allowed("052-123-4567", "list", list, false), "list: a listed number gets through");
        check(!CallRules.allowed("0505555555", "list", list, true), "list: a contact who is not listed is blocked");
        check(CallRules.allowed("0505555555", "contacts", list, true), "contacts: a contact gets through");
        check(CallRules.allowed("2125551234", "contacts", list, false), "contacts: a listed number gets through");
        check(!CallRules.allowed("0505555555", "contacts", list, false), "contacts: a stranger is blocked");
        check(!CallRules.allowed("", "contacts", list, true), "contacts: a hidden number is blocked");
        check(!CallRules.allowed("123", "list", null, false), "list: an empty list blocks everyone");

        System.out.println(failures == 0 ? "ALL OK" : failures + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
