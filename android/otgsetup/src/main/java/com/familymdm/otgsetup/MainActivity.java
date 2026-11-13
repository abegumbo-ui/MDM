package com.familymdm.otgsetup;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Family MDM Phone Setup: makes ANOTHER phone managed by the Family MDM app, over a USB cable and an OTG adapter,
 * with this phone playing the part of the computer. The steps are in OtgSetup; this is the screen.
 */
public class MainActivity extends Activity {
    private static final String ACTION_PERMISSION = "com.familymdm.otgsetup.USB_PERMISSION";

    private UsbManager usb;
    private TextView logView;
    private EditText code, server;
    private Button go, check, back;
    private ScrollView scroll;
    private volatile OtgSetup running;
    private String pendingMode; // "setup", "check" or "restore", while the USB permission question is open

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        usb = (UsbManager) getSystemService(Context.USB_SERVICE);
        setContentView(buildScreen());
        IntentFilter f = new IntentFilter(ACTION_PERMISSION);
        f.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(receiver, f);
        if (!getPackageManager().hasSystemFeature("android.hardware.usb.host")) {
            log("This phone cannot act as a USB host, so it cannot set up another phone. Use the Windows program instead.");
            setButtons(false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(receiver);
        OtgSetup r = running;
        if (r != null) r.abort();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private View buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        TextView title = new TextView(this);
        title.setText("Set up another phone with a cable");
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);
        TextView help = new TextView(this);
        help.setText("1. On the OTHER phone: Settings > About phone > tap Build number 7 times, then Settings > Developer options > USB debugging on.\n"
                + "2. Join the two phones with a USB cable and an OTG adapter on THIS phone. This phone plays the computer.\n"
                + "3. On the dashboard: Codes > Enrollment code > Generate new code. Type it below.\n"
                + "4. Press the button, then tap Allow on the other phone when it asks.\n"
                + "This phone needs the internet (to download the app).");
        help.setPadding(0, dp(8), 0, dp(8));
        root.addView(help);
        code = field("Code from the dashboard");
        server = field("Dashboard address (leave empty if the app already has it)");
        server.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        root.addView(code);
        root.addView(server);
        go = button("Set up the other phone", v -> start("setup"));
        check = button("Only check it (change nothing)", v -> start("check"));
        back = button("Put things back", v -> start("restore"));
        root.addView(go);
        root.addView(check);
        root.addView(back);
        logView = new TextView(this);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextSize(13);
        logView.setPadding(0, dp(12), 0, 0);
        scroll = new ScrollView(this);
        scroll.addView(logView);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private EditText field(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        return e;
    }

    private Button button(String text, View.OnClickListener l) {
        Button x = new Button(this);
        x.setText(text);
        x.setAllCaps(false);
        x.setOnClickListener(l);
        return x;
    }

    private void setButtons(boolean on) {
        go.setEnabled(on);
        check.setEnabled(on);
        back.setEnabled(on);
    }

    private void log(String line) {
        runOnUiThread(() -> {
            logView.append(line + "\n");
            scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        });
    }

    // ---------------- finding the other phone ----------------

    private UsbDevice otherPhone() {
        UsbDevice found = null;
        int count = 0;
        for (UsbDevice d : usb.getDeviceList().values()) {
            if (UsbTransport.hasAdb(d)) {
                found = d;
                count++;
            }
        }
        if (count == 0) {
            log("No phone with USB debugging found on the cable. Check the OTG adapter and cable, and that USB debugging is on in the other phone's Developer options.");
            return null;
        }
        if (count > 1) {
            log("More than one phone is plugged in. Unplug the others so the wrong one is never changed.");
            return null;
        }
        return found;
    }

    private void start(String mode) {
        if (running != null) return;
        UsbDevice d = otherPhone();
        if (d == null) return;
        if (!usb.hasPermission(d)) {
            pendingMode = mode;
            int flags = Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0;
            Intent i = new Intent(ACTION_PERMISSION).setPackage(getPackageName());
            usb.requestPermission(d, PendingIntent.getBroadcast(this, 0, i, flags));
            log("Allow this app to use the USB connection when Android asks.");
            return;
        }
        run(mode, d);
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (ACTION_PERMISSION.equals(i.getAction())) {
                UsbDevice d = i.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (d != null && i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) && pendingMode != null) {
                    String mode = pendingMode;
                    pendingMode = null;
                    run(mode, d);
                } else {
                    pendingMode = null;
                    log("The USB connection was not allowed, so nothing was done.");
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(i.getAction())) {
                OtgSetup r = running;
                if (r != null) {
                    log("The cable was unplugged.");
                    r.abort();
                }
            }
        }
    };

    private void run(final String mode, final UsbDevice d) {
        final String c = code.getText().toString().trim(), s = server.getText().toString().trim();
        setButtons(false);
        final OtgSetup setup = new OtgSetup(this, this::log);
        running = setup;
        new Thread(() -> {
            try {
                if ("restore".equals(mode)) setup.restore(usb, d);
                else setup.run(usb, d, c, s, "check".equals(mode));
            } finally {
                running = null;
                runOnUiThread(() -> setButtons(true));
            }
        }, "mdm-otg-setup").start();
    }
}
