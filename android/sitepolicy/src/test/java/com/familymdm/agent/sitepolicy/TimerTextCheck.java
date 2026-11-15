package com.familymdm.agent.sitepolicy;

public final class TimerTextCheck {
    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError("FAILED: " + what);
        System.out.println("ok:   " + what);
    }

    static final long MIN = 60_000L;

    public static void main(String[] args) {
        check("23 min".equals(TimerText.left(23 * MIN)), "23 minutes");
        check("23 min".equals(TimerText.left(22 * MIN + 1)), "a part of a minute counts as a whole one (never says 0 while time is left)");
        check("1 min".equals(TimerText.left(1)), "one millisecond left is 1 min");
        check("59 min".equals(TimerText.left(59 * MIN)), "59 minutes");
        check("1 h".equals(TimerText.left(60 * MIN)), "an hour");
        check("1 h 5 min".equals(TimerText.left(65 * MIN)), "an hour and five minutes");
        check("2 h".equals(TimerText.left(120 * MIN)), "two hours");
        check("".equals(TimerText.left(0)) && "".equals(TimerText.left(-5000)), "nothing left is empty");

        long now = 1_000_000_000L;
        check(TimerText.describe(now, 0, 0, 0) == null, "no timer: no line");
        check(TimerText.describe(now, now - 1, now - 5, now - 9) == null, "timers that are over: no line");
        check("Internet is off: 40 min left".equals(TimerText.describe(now, now + 40 * MIN, 0, 0)), "internet off, 40 minutes");
        check("Web filtering is paused: 1 h 30 min left".equals(TimerText.describe(now, 0, now + 90 * MIN, 0)), "filtering paused, an hour and a half");
        check("All restrictions are off: 2 h left".equals(TimerText.describe(now, 0, 0, now + 120 * MIN)), "all restrictions off, two hours");
        String two = TimerText.describe(now, now + 90 * MIN, now + 15 * MIN, 0);
        check("Web filtering is paused: 15 min left · Internet is off: 1 h 30 min left".equals(two), "two timers: the one that ends first comes first");
        String three = TimerText.describe(now, now + 30 * MIN, now + 20 * MIN, now + 10 * MIN);
        check(three.startsWith("All restrictions are off: 10 min left · Web filtering is paused: 20 min left · Internet is off: 30 min left"), "all three, soonest first");
        System.out.println("TimerTextCheck OK");
    }
}
