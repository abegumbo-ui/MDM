package com.familymdm.otgsetup;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reading what the other phone prints. The same rules as the Windows program (copied from output of a real phone). */
final class Parse {
    private Parse() {}

    /** Account types that have at least one account ("Account {name=..., type=com.google}"). */
    static List<String> accountTypes(String dump) {
        List<String> types = new ArrayList<>();
        if (dump == null) return types;
        int stop = dump.indexOf("RegisteredServicesCache");
        String head = stop > 0 ? dump.substring(0, stop) : dump;
        Matcher m = Pattern.compile("Account \\{name=[^\\r\\n]*?, type=([^}\\r\\n]+)\\}").matcher(head);
        while (m.find()) if (!types.contains(m.group(1).trim())) types.add(m.group(1).trim());
        return types;
    }

    /** How many accounts the phone says it has ("Accounts: 3"); -1 when it does not say. */
    static int accountCount(String dump) {
        if (dump == null) return -1;
        Matcher m = Pattern.compile("^\\s*Accounts:\\s*(\\d+)", Pattern.MULTILINE).matcher(dump);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    /** account type -> the package whose service handles it. */
    static Map<String, String> authenticators(String dump) {
        Map<String, String> map = new LinkedHashMap<>();
        if (dump == null) return map;
        Matcher m = Pattern.compile("AuthenticatorDescription \\{type=([^}]+)\\}, ComponentInfo\\{([^/}]+)/").matcher(dump);
        while (m.find()) map.put(m.group(1).trim(), m.group(2).trim());
        return map;
    }

    /** The package that is the device owner, or "". */
    static String deviceOwner(String dump) {
        if (dump == null) return "";
        Matcher m = Pattern.compile("Device Owner:\\s*\\r?\\n\\s*admin=ComponentInfo\\{([^/}]+)/").matcher(dump);
        return m.find() ? m.group(1).trim() : "";
    }

    static int userCount(String pmListUsers) {
        if (pmListUsers == null) return 0;
        Matcher m = Pattern.compile("UserInfo\\{").matcher(pmListUsers);
        int n = 0;
        while (m.find()) n++;
        return n;
    }

    /** "package:com.x" lines to package names. */
    static List<String> packages(String pmList) {
        List<String> out = new ArrayList<>();
        if (pmList == null) return out;
        Matcher m = Pattern.compile("^\\s*package:([A-Za-z0-9_.]+)", Pattern.MULTILINE).matcher(pmList);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    /** True when `checksum` (hex, base64 or base64url) is the SHA-256 of the data. */
    static boolean checksumMatches(byte[] sha256, String checksum) {
        if (sha256 == null || checksum == null || checksum.trim().isEmpty()) return false;
        String want = checksum.trim();
        StringBuilder hex = new StringBuilder();
        for (byte b : sha256) hex.append(String.format("%02x", b & 0xFF));
        if (hex.toString().equalsIgnoreCase(want)) return true;
        String b64 = android.util.Base64.encodeToString(sha256, android.util.Base64.NO_WRAP).replace("=", "");
        String norm = want.replace('-', '+').replace('_', '/').replace("=", "");
        return b64.equals(norm);
    }
}
