package com.familymdm.agent.sitepolicy;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Plain checks for ScreenTimeRules. Run on a computer:
 *   javac -d out android/sitepolicy/src/main/java/com/familymdm/agent/sitepolicy/ScreenTimeRules.java android/sitepolicy/src/test/java/com/familymdm/agent/sitepolicy/ScreenTimeRulesCheck.java
 *   java -cp out com.familymdm.agent.sitepolicy.ScreenTimeRulesCheck
 */
public class ScreenTimeRulesCheck {
    static int failures = 0;

    static void check(boolean ok, String what) {
        if (!ok) {
            failures++;
            System.out.println("FAIL: " + what);
        } else {
            System.out.println("ok:   " + what);
        }
    }

    static final long MIN = 60_000L;
    static final int FG = ScreenTimeRules.FOREGROUND, BG = ScreenTimeRules.BACKGROUND, OFF = ScreenTimeRules.SCREEN_OFF;

    public static void main(String[] args) {
        // ---- adding up a day ----
        // 0: maps opens. 10: chat opens (maps stops). 25: chat goes to the back. 30: maps again. 40: screen off. 50: chat opens, still open at 60.
        long[] t = {0, 10 * MIN, 25 * MIN, 30 * MIN, 40 * MIN, 50 * MIN};
        String[] p = {"maps", "chat", "chat", "maps", null, "chat"};
        int[] ty = {FG, FG, BG, FG, OFF, FG};
        Map<String, Long> u = ScreenTimeRules.tally(t, p, ty, 60 * MIN, true);
        check(u.get("maps") == 20 * MIN, "maps: 10 minutes, then 10 more until the screen went off");
        check(u.get("chat") == 25 * MIN, "chat: 15 minutes, then 10 still running now");
        check(ScreenTimeRules.tally(t, p, ty, 60 * MIN, false).get("chat") == 15 * MIN, "an app left in front does not keep counting while the screen is off");
        check(ScreenTimeRules.tally(new long[0], new String[0], new int[0], 5, true).isEmpty(), "a day with nothing in it is empty");
        // the same app moving between its own screens is one stretch, not double-counted
        Map<String, Long> same = ScreenTimeRules.tally(new long[] {0, 5 * MIN, 5 * MIN, 9 * MIN}, new String[] {"a", "a", "a", "a"}, new int[] {FG, FG, FG, BG}, 20 * MIN, true);
        check(same.get("a") == 9 * MIN, "moving between an app's own screens counts once");
        // a "went to the back" for an app that is not the one in front changes nothing
        Map<String, Long> stray = ScreenTimeRules.tally(new long[] {0, 3 * MIN, 8 * MIN}, new String[] {"a", "b", "a"}, new int[] {FG, BG, BG}, 20 * MIN, true);
        check(stray.get("a") == 8 * MIN && !stray.containsKey("b"), "a stray event for another app is ignored");

        Set<String> skip = new HashSet<>(Arrays.asList("maps"));
        check(ScreenTimeRules.total(u, skip) == 25 * MIN && ScreenTimeRules.total(u, new HashSet<String>()) == 45 * MIN, "apps marked \"doesn't count\" are left out of the total");

        // ---- bedtime windows (Sunday = 0) ----
        int[] schoolNights = {0, 1, 2, 3, 4};
        int ten = 22 * 60, seven = 7 * 60;
        check(ScreenTimeRules.inWindow(schoolNights, ten, seven, 0, 23 * 60), "Sunday 23:00 is bedtime");
        check(ScreenTimeRules.inWindow(schoolNights, ten, seven, 1, 6 * 60), "Monday 06:00 is still Sunday night's bedtime");
        check(!ScreenTimeRules.inWindow(schoolNights, ten, seven, 1, 7 * 60), "it ends at 07:00 exactly");
        check(!ScreenTimeRules.inWindow(schoolNights, ten, seven, 0, 21 * 60 + 59), "not before 22:00");
        check(!ScreenTimeRules.inWindow(schoolNights, ten, seven, 5, 23 * 60), "Friday night is not a school night");
        check(!ScreenTimeRules.inWindow(schoolNights, ten, seven, 0, 3 * 60), "Sunday 03:00 belongs to Saturday night, which is off");
        check(ScreenTimeRules.inWindow(schoolNights, ten, seven, 5, 3 * 60), "Friday 03:00 belongs to Thursday night, which is on");
        check(ScreenTimeRules.inWindow(new int[] {2}, 13 * 60, 14 * 60, 2, 13 * 60 + 30) && !ScreenTimeRules.inWindow(new int[] {2}, 13 * 60, 14 * 60, 3, 13 * 60 + 30), "a daytime window only on its own day");
        check(!ScreenTimeRules.inWindow(null, ten, seven, 0, 23 * 60) && !ScreenTimeRules.inWindow(schoolNights, ten, ten, 0, 23 * 60) && !ScreenTimeRules.inWindow(new int[0], ten, seven, 0, 23 * 60), "no days, or an empty window, is never bedtime");
        check(ScreenTimeRules.minutesToEnd(seven, 23 * 60) == 8 * 60 && ScreenTimeRules.minutesToEnd(seven, 6 * 60 + 30) == 30, "time left until bedtime ends, across midnight or not");

        // ---- what locks the phone ----
        check(ScreenTimeRules.reason(false, 0, 99 * 60 * MIN, false) == ScreenTimeRules.NONE, "no limit set: never locked for time");
        check(ScreenTimeRules.reason(false, 120, 119 * MIN, false) == ScreenTimeRules.NONE, "under the limit");
        check(ScreenTimeRules.reason(false, 120, 120 * MIN, false) == ScreenTimeRules.LIMIT, "at the limit");
        check(ScreenTimeRules.reason(true, 120, 0, false) == ScreenTimeRules.BEDTIME, "bedtime locks even with time left");
        check(ScreenTimeRules.reason(true, 120, 500 * MIN, true) == ScreenTimeRules.NONE, "extra time from the administrator lifts both");

        // ---- per-app limits ----
        Map<String, Integer> limits = new HashMap<>();
        limits.put("chat", 20);
        limits.put("maps", 60);
        limits.put("unused", 5);
        limits.put("zero", 0);
        check(ScreenTimeRules.overLimit(u, limits, false).equals(new HashSet<>(Arrays.asList("chat"))), "only the app past its own limit is over");
        check(ScreenTimeRules.overLimit(u, limits, true).isEmpty(), "extra time lifts app limits too");

        System.out.println(failures == 0 ? "ALL OK" : failures + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
