package com.familymdm.agent;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A minimal, self-contained Bluetooth screen -- toggle, paired devices, pair a new one -- built
 * entirely with BluetoothAdapter rather than opening the real Settings app. Lock-task (Home
 * screen) mode has no way to allow just one screen of another app (see SettingsWatchdog's class
 * doc for the same wall, hit from the other direction); staying inside this app's own screens
 * sidesteps that entirely, since this app is the one lock-task already allows.
 *
 * BLUETOOTH_CONNECT/BLUETOOTH_SCAN (API 31+) are ordinary runtime permissions -- unlike Usage
 * access, which is a special app-op permission with no such guarantee, setPermissionGrantState()
 * is Android's documented, reliable way for a device owner to grant these without a prompt.
 */
public class BluetoothActivity extends Activity {
    private LinearLayout root;
    private LinearLayout foundBox;
    private android.widget.Button scanButton;
    private BluetoothAdapter adapter;
    private boolean receiverRegistered;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) addFound(device);
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                if (scanButton != null) {
                    scanButton.setText("Find new devices");
                    scanButton.setEnabled(true);
                }
            } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                build();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        grantBluetoothPermissions();
        BluetoothManager bm = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        adapter = bm == null ? null : bm.getAdapter();
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        root.setPadding(pad, pad, pad, pad);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);

        IntentFilter f = new IntentFilter();
        f.addAction(BluetoothDevice.ACTION_FOUND);
        f.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(receiver, f);
        receiverRegistered = true;
        build();
    }

    @Override
    protected void onDestroy() {
        if (receiverRegistered) {
            try {
                unregisterReceiver(receiver);
            } catch (Exception ignored) {
            }
        }
        try {
            if (adapter != null && hasScanPermission()) adapter.cancelDiscovery();
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    /** No Settings prompt needed on a device-owner phone -- a plain runtime permission, unlike Usage access. */
    private void grantBluetoothPermissions() {
        try {
            android.app.admin.DevicePolicyManager dpm = Agent.dpm(this);
            android.content.ComponentName admin = Agent.admin(this);
            if (dpm == null || admin == null || !dpm.isDeviceOwnerApp(getPackageName())) return;
            dpm.setPermissionGrantState(admin, getPackageName(), android.Manifest.permission.BLUETOOTH_CONNECT,
                    android.app.admin.DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
            dpm.setPermissionGrantState(admin, getPackageName(), android.Manifest.permission.BLUETOOTH_SCAN,
                    android.app.admin.DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
        } catch (Exception ignored) {
        }
    }

    private boolean hasConnectPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        return checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasScanPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        return checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
    }

    private void build() {
        root.removeAllViews();
        root.addView(Ui.headline(this, "Bluetooth"));
        if (adapter == null) {
            root.addView(Ui.body(this, "This phone has no Bluetooth hardware.", true));
            return;
        }
        if (!hasConnectPermission() || !hasScanPermission()) {
            root.addView(Ui.body(this, "Bluetooth permission isn't granted yet. Leave this screen and come back "
                    + "in a moment, or ask the administrator to grant \"Nearby devices\" for MDM Agent by hand.", true));
            return;
        }
        boolean on;
        try {
            on = adapter.isEnabled();
        } catch (SecurityException e) {
            on = false;
        }
        LinearLayout toggleCard = Ui.card(this, root);
        toggleCard.addView(Ui.titleText(this, on ? "Bluetooth is on" : "Bluetooth is off"));
        final boolean wasOn = on;
        Ui.add(toggleCard, Ui.button(this, on ? "Turn off" : "Turn on", Ui.TONAL, v -> {
            try {
                if (wasOn) adapter.disable();
                else adapter.enable();
            } catch (Exception e) {
                toast("Could not change Bluetooth: " + e.getMessage());
            }
        }), 8);

        if (!on) return;

        LinearLayout paired = Ui.card(this, root);
        paired.addView(Ui.titleText(this, "Paired devices"));
        Set<BluetoothDevice> bonded;
        try {
            bonded = adapter.getBondedDevices();
        } catch (SecurityException e) {
            bonded = new LinkedHashSet<>();
        }
        if (bonded.isEmpty()) paired.addView(Ui.body(this, "None yet.", true));
        for (BluetoothDevice d : bonded) paired.addView(Ui.body(this, deviceName(d), false));

        LinearLayout scan = Ui.card(this, root);
        scan.addView(Ui.titleText(this, "Find a new device"));
        foundBox = new LinearLayout(this);
        foundBox.setOrientation(LinearLayout.VERTICAL);
        Ui.add(scan, foundBox, 8);
        scanButton = Ui.button(this, "Find new devices", Ui.TONAL, v -> startScan());
        Ui.add(scan, scanButton, 8);
    }

    private String deviceName(BluetoothDevice d) {
        try {
            String n = d.getName();
            return n == null || n.isEmpty() ? d.getAddress() : n;
        } catch (SecurityException e) {
            return d.getAddress();
        }
    }

    private void startScan() {
        if (foundBox != null) foundBox.removeAllViews();
        try {
            if (adapter.isDiscovering()) adapter.cancelDiscovery();
            adapter.startDiscovery();
            scanButton.setText("Searching...");
            scanButton.setEnabled(false);
        } catch (SecurityException e) {
            toast("Missing Bluetooth permission.");
        }
    }

    private void addFound(BluetoothDevice device) {
        if (foundBox == null) return;
        android.widget.Button b = Ui.button(this, deviceName(device), Ui.OUTLINED, v -> pair(device));
        Ui.add(foundBox, b, 6);
    }

    private void pair(BluetoothDevice device) {
        try {
            adapter.cancelDiscovery();
            device.createBond();
            toast("Pairing with " + deviceName(device) + "...");
        } catch (SecurityException e) {
            toast("Missing Bluetooth permission.");
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
