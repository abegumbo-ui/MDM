package com.familymdm.agent.sitepolicy;

import java.util.Collection;

/**
 * Who may call the phone, and whom the phone may call. Pure Java, checked on a computer (see
 * CallRulesCheck).
 *
 * Modes: "all" (anyone), "contacts" (the phone's contacts plus the dashboard's list), "list"
 * (only the dashboard's list).
 */
public final class CallRules {
    private CallRules() {}

    /** Just the digits of a phone number. */
    public static String digits(String number) {
        if (number == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < number.length(); i++) {
            char ch = number.charAt(i);
            if (ch >= '0' && ch <= '9') sb.append(ch);
        }
        return sb.toString();
    }

    /**
     * The same number written two ways? "+972 52-123-4567" and "052 123 4567" are; so are
     * "+1 212 555 1234" and "(212) 555-1234". Compares the last 9 digits (fewer if a number is
     * shorter); numbers under 7 digits (short codes) must match exactly.
     */
    public static boolean same(String a, String b) {
        String da = digits(a), db = digits(b);
        if (da.isEmpty() || db.isEmpty()) return false;
        int n = Math.min(9, Math.min(da.length(), db.length()));
        if (n < 7) return da.equals(db);
        return da.substring(da.length() - n).equals(db.substring(db.length() - n));
    }

    public static boolean listed(String number, Collection<String> list) {
        if (list == null) return false;
        for (String x : list) if (same(number, x)) return true;
        return false;
    }

    /** Is this call allowed? A call with no number (hidden) is only allowed when the mode is "all". */
    public static boolean allowed(String number, String mode, Collection<String> list, boolean inContacts) {
        if (mode == null || "all".equals(mode)) return true;
        if (digits(number).isEmpty()) return false;
        if (listed(number, list)) return true;
        return "contacts".equals(mode) && inContacts;
    }
}
