package com.familymdm.otgsetup;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Base64;

import com.cgutman.adblib.AdbBase64;
import com.cgutman.adblib.AdbConnection;
import com.cgutman.adblib.AdbCrypto;
import com.cgutman.adblib.AdbStream;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Makes the Family MDM app the manager of another phone, over a USB cable, from this phone. The same steps as the
 * Windows program (FamilyMdmSetup.exe): look at the phone, install the app, switch off the account services of the
 * accounts that exist for a moment (Android refuses to hand over management while accounts exist), make the app the
 * device owner, and switch the account services back on. What was switched off is written down BEFORE it is touched and put
 * back in every case; "Put things back" does it again by hand.
 *
 * It talks to the other phone with the ADB protocol (the library in com.cgutman.adblib) over this phone's USB host port.
 */
final class OtgSetup {
    interface Log {
        void line(String s);
    }

    static final String PACKAGE = "com.familymdm.agent";
    static final String ADMIN = PACKAGE + "/.AdminReceiver";
    private static final String APK_URL = "https://github.com/abegumbo-ui/MDM/releases/download/latest/mdm-agent-enroll.apk";
    private static final String VERSION_URL = "https://github.com/abegumbo-ui/MDM/releases/download/latest/version.json";

    private final Context ctx;
    private final Log log;
    private AdbConnection adb;
    private UsbTransport usb;

    OtgSetup(Context ctx, Log log) {
        this.ctx = ctx.getApplicationContext();
        this.log = log;
    }

    private SharedPreferences prefs() {
        return ctx.getSharedPreferences("otgsetup", Context.MODE_PRIVATE);
    }

    // ---------------- the connection ----------------

    private boolean connect(UsbManager manager, UsbDevice device) throws Exception {
        usb = UsbTransport.open(manager, device);
        File priv = new File(ctx.getFilesDir(), "adbkey"), pub = new File(ctx.getFilesDir(), "adbkey.pub");
        AdbBase64 b64 = data -> Base64.encodeToString(data, Base64.NO_WRAP);
        AdbCrypto crypto;
        if (priv.exists() && pub.exists()) {
            crypto = AdbCrypto.loadAdbKeyPair(b64, priv, pub);
        } else {
            log.line("Making this phone's own key for talking to the other phone (once)...");
            crypto = AdbCrypto.generateAdbKeyPair(b64);
            crypto.saveAdbKeyPair(priv, pub);
        }
        adb = AdbConnection.create(usb.input(), usb.output(), usb, crypto);
        log.line("Look at the OTHER phone's screen: tap Allow on 'Allow USB debugging?' (tick 'Always allow from this computer').");
        adb.connect();
        return true;
    }

    /** The cable was pulled or the person pressed Cancel: ends whatever is waiting on the other phone. */
    void abort() {
        disconnect();
    }

    private void disconnect() {
        try {
            if (adb != null) adb.close();
        } catch (Exception ignored) {
        }
        if (usb != null) usb.close();
        adb = null;
        usb = null;
    }

    /** Runs a shell command on the other phone and returns what it printed. */
    private String shell(String cmd) throws Exception {
        AdbStream s = adb.open("shell:" + cmd);
        return readAll(s);
    }

    private static String readAll(AdbStream s) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            while (!s.isClosed()) {
                byte[] d = s.read();
                if (d == null) break;
                out.write(d);
            }
        } catch (IOException closed) {
            // the other phone closed the stream: that is how a command ends
        }
        return out.toString("UTF-8");
    }

    // ---------------- what was switched off ----------------

    private String key(UsbDevice d) {
        return d.getVendorId() + ":" + d.getProductId();
    }

    private List<String> readState(String key) {
        List<String> out = new ArrayList<>();
        String s = prefs().getString("off:" + key, "");
        if (!s.isEmpty()) for (String p : s.split(",")) if (p.matches("[A-Za-z0-9_.]+")) out.add(p);
        return out;
    }

    private void writeState(String key, List<String> pkgs) {
        SharedPreferences.Editor e = prefs().edit();
        if (pkgs.isEmpty()) e.remove("off:" + key);
        else e.putString("off:" + key, String.join(",", pkgs));
        e.commit();
    }

    /** Switches back on what was switched off on that phone. True when nothing is left off. */
    private boolean putBack(String key) throws Exception {
        List<String> pkgs = readState(key);
        if (pkgs.isEmpty()) return true;
        log.line("Putting the other phone's account services back on (" + pkgs.size() + ")...");
        for (String p : pkgs) {
            shell("pm enable --user 0 " + p);
            shell("pm enable " + p);
        }
        List<String> stillOff = Parse.packages(shell("pm list packages -d --user 0"));
        List<String> left = new ArrayList<>();
        for (String p : pkgs) if (stillOff.contains(p)) left.add(p);
        writeState(key, left);
        if (left.isEmpty()) {
            log.line("OK  Everything is back on.");
            return true;
        }
        log.line("PROBLEM: still switched off: " + String.join(", ", left) + ". Plug the phone in and press 'Put things back' again.");
        return false;
    }

    /** "Put things back" by hand. */
    boolean restore(UsbManager manager, UsbDevice device) {
        try {
            connect(manager, device);
            if (readState(key(device)).isEmpty()) {
                log.line("OK  Nothing is switched off on that phone.");
                return true;
            }
            prefs().edit().remove("retry:" + key(device)).commit();
            return putBack(key(device));
        } catch (Exception e) {
            log.line("PROBLEM: " + e.getMessage());
            return false;
        } finally {
            disconnect();
        }
    }

    // ---------------- the app file ----------------

    private File getApk() throws Exception {
        log.line("Downloading the newest Family MDM app...");
        File apk = new File(ctx.getCacheDir(), "mdm-agent-enroll.apk");
        download(APK_URL, apk);
        String want = null;
        try {
            File vf = new File(ctx.getCacheDir(), "version.json");
            download(VERSION_URL, vf);
            ByteArrayOutputStream text = new ByteArrayOutputStream();
            try (InputStream in = new FileInputStream(vf)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) text.write(buf, 0, n);
            }
            Matcher m = Pattern.compile("\"enrollSha256\"\\s*:\\s*\"([^\"]+)\"").matcher(text.toString("UTF-8"));
            if (m.find()) want = m.group(1);
        } catch (IOException ignored) {
        }
        if (want != null) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new FileInputStream(apk)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            }
            if (!Parse.checksumMatches(md.digest(), want)) {
                apk.delete();
                throw new IOException("The downloaded app does not match its published checksum. Nothing was installed.");
            }
            log.line("OK  The app matches its published checksum.");
        }
        return apk;
    }

    private static void download(String url, File to) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        c.setInstanceFollowRedirects(true);
        if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode() + " for " + url);
        try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(to)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } finally {
            c.disconnect();
        }
    }

    /** Installs the app on the other phone by streaming the file (Android 7 and newer). */
    private String install(File apk) throws Exception {
        long size = apk.length();
        AdbStream s = adb.open("exec:cmd package install -S " + size + " -r");
        int chunk = Math.max(1024, Math.min(16384, adb.getMaxData()));
        byte[] buf = new byte[chunk];
        try (InputStream in = new FileInputStream(apk)) {
            int n;
            long sent = 0;
            while ((n = in.read(buf)) > 0) {
                s.write(n == buf.length ? buf : java.util.Arrays.copyOf(buf, n));
                sent += n;
            }
        }
        return readAll(s);
    }

    // ---------------- the whole thing ----------------

    boolean run(UsbManager manager, UsbDevice device, String code, String server, boolean dryRun) {
        String key = key(device);
        log.line("---- Family MDM phone setup " + (dryRun ? "(only checking, nothing will be changed)" : "") + " ----");
        boolean connected = false;
        boolean keepOff = false;
        try {
            connect(manager, device);
            connected = true;
            String model = shell("getprop ro.product.model").trim(), release = shell("getprop ro.build.version.release").trim();
            int sdk = 0;
            try {
                sdk = Integer.parseInt(shell("getprop ro.build.version.sdk").trim());
            } catch (NumberFormatException ignored) {
            }
            log.line("OK  Phone found: " + model + ", Android " + release);

            boolean retry = prefs().getBoolean("retry:" + key, false);
            if (!dryRun && !retry && !readState(key).isEmpty()) putBack(key); // from an earlier run

            String owner = Parse.deviceOwner(shell("dumpsys device_policy"));
            boolean ownerIsUs = owner.equals(PACKAGE);
            if (!owner.isEmpty() && !ownerIsUs) {
                log.line("PROBLEM: that phone is already managed by '" + owner + "'. Only one app can manage a phone. Remove that first (or factory reset the phone). Nothing was changed.");
                return false;
            }
            if (ownerIsUs) log.line("OK  Family MDM already manages that phone.");
            if (!ownerIsUs) {
                int users = Parse.userCount(shell("pm list users"));
                if (users > 1) {
                    log.line("PROBLEM: that phone has " + users + " users (a guest, a second user or a work profile). Android only allows management with a single user. Remove the extra users, then run this again. Nothing was changed.");
                    return false;
                }
                log.line("OK  That phone has a single user.");
            }

            String dump = shell("dumpsys account");
            int accounts = Parse.accountCount(dump);
            List<String> toSwitchOff = new ArrayList<>();
            if (!ownerIsUs && accounts != 0) {
                Map<String, String> map = Parse.authenticators(dump);
                List<String> wanted = new ArrayList<>();
                for (String type : Parse.accountTypes(dump)) {
                    String pkg = map.get(type);
                    if (pkg != null && !wanted.contains(pkg)) wanted.add(pkg);
                }
                if (wanted.isEmpty()) for (String pkg : map.values()) if (!wanted.contains(pkg)) wanted.add(pkg);
                List<String> alreadyOff = Parse.packages(shell("pm list packages -d --user 0"));
                for (String pkg : wanted) if (!pkg.equals(PACKAGE) && !alreadyOff.contains(pkg)) toSwitchOff.add(pkg);
            }
            if (retry) toSwitchOff = readState(key); // still off from the first try: keep them off and finish
            log.line(accounts == 0 ? "OK  That phone has no accounts." : "That phone has " + (accounts < 0 ? "some" : String.valueOf(accounts)) + " account(s). For a moment these services are switched off and then back on: "
                    + (toSwitchOff.isEmpty() ? "(none found)" : String.join(", ", toSwitchOff)));

            if (dryRun) {
                log.line("Only checking: the app would be installed" + (ownerIsUs ? " (already there)" : "") + ", " + (ownerIsUs ? "" : "made the manager, ") + "and started.");
                log.line("Nothing was changed on that phone.");
                return true;
            }

            if (!retry) {
                File apk = getApk();
                log.line("Installing the app on that phone (" + (apk.length() / 1024 / 1024) + " MB, over the cable)...");
                String res = install(apk);
                if (!res.toLowerCase().contains("success")) {
                    log.line("PROBLEM: that phone refused the app: " + res.trim());
                    if (res.contains("USER_RESTRICTED")) log.line("On Samsung: switch off Settings > Security and privacy > Auto Blocker; on Xiaomi switch on 'Install via USB' in Developer options.");
                    if (res.contains("UPDATE_INCOMPATIBLE")) log.line("A copy of the app signed differently is already on that phone. Uninstall it there first.");
                    return false;
                }
                log.line("OK  App installed.");
            }

            if (!ownerIsUs) {
                if (!retry && !toSwitchOff.isEmpty()) {
                    writeState(key, toSwitchOff); // written BEFORE anything is touched
                    log.line("Switching off the account services for a moment. Do not unplug the phone.");
                    for (String p : toSwitchOff) shell("pm disable-user --user 0 " + p);
                }
                log.line("Making Family MDM the manager of that phone...");
                String r = shell("dpm set-device-owner " + ADMIN);
                if (!r.toLowerCase().contains("success")) {
                    log.line("The phone said: " + r.trim());
                    if (sdk >= 34 && !retry && !toSwitchOff.isEmpty()) {
                        // Android 14 and newer sometimes need a restart before they notice the accounts are gone.
                        keepOff = true;
                        prefs().edit().putBoolean("retry:" + key, true).commit();
                        log.line("NEXT: restart the OTHER phone now (hold its power button > Restart), unlock it, plug it in again and press the button once more. The account services stay off until then, and are switched back on at the end.");
                        return false;
                    }
                    prefs().edit().remove("retry:" + key).commit();
                    log.line("PROBLEM: the phone would not make Family MDM its manager. The account services are being put back. If the message mentions accounts, remove them in that phone's Settings > Accounts and run this again.");
                    return false;
                }
                log.line("OK  Family MDM is now the manager of that phone.");
                prefs().edit().remove("retry:" + key).commit();
                putBack(key);
            }

            StringBuilder extras = new StringBuilder();
            if (code != null && code.matches("[A-Za-z0-9-]{4,40}")) extras.append(" --es code ").append(code);
            if (server != null && server.matches("https?://[A-Za-z0-9.\\-:/_]+")) extras.append(" --es server ").append(server);
            shell("am start -n " + PACKAGE + "/.MainActivity" + extras);
            log.line("OK  The app was started on that phone.");
            log.line("DONE. The other phone is set up. If you typed a code it joins the dashboard on its own; otherwise open the app there and enter the code.");
            return true;
        } catch (Exception e) {
            log.line("PROBLEM: " + (e.getMessage() == null ? e.toString() : e.getMessage()));
            return false;
        } finally {
            if (connected && !dryRun && !keepOff) {
                try {
                    putBack(key);
                } catch (Exception ignored) {
                }
            }
            disconnect();
        }
    }
}
