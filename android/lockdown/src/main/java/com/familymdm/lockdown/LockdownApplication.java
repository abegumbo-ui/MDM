package com.familymdm.lockdown;

import android.app.Application;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Installs a crash recorder before anything else in this process runs -- the very first thing
 * Android does when starting any component here (MainActivity, or the VPN service getting
 * auto-started by always-on without MainActivity ever opening first). Without a computer or
 * logcat access, there was no way to see what a crash's actual stack trace said; this writes it
 * to the same SharedPreferences file every screen here already uses, so MainActivity can show it
 * on screen, selectable to copy, the next time this app opens -- see MainActivity's own
 * buildCrashBannerIfAny().
 */
public class LockdownApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                StringWriter sw = new StringWriter();
                throwable.printStackTrace(new PrintWriter(sw));
                String text = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())
                        + " on thread \"" + thread.getName() + "\"\n" + sw;
                // commit(), not apply() -- the process is about to die, possibly within
                // milliseconds; apply()'s async write could easily lose the race.
                getSharedPreferences("lockdown", MODE_PRIVATE).edit().putString("lastCrash", text).commit();
            } catch (Throwable ignored) {
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            } else {
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(10);
            }
        });
    }
}
