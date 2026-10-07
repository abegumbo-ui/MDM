package com.familymdm.agent;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Full-screen timed lock: shows the administrator's message and a countdown. It runs in lock-task
 * mode, so Home, Recents and notifications are unavailable until the time is up or it is unlocked.
 * The dashboard's "Unlock now", or the master code ("Administrator unlock"), ends it early.
 */
public class LockActivity extends Activity {
    static volatile boolean alive;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView countdown;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            long until = Agent.prefs(LockActivity.this).getLong("lockUntil", 0);
            long left = until - System.currentTimeMillis();
            if (until == 0 || left <= 0) {
                finishLock();
                return;
            }
            long s = left / 1000;
            countdown.setText(s >= 3600
                    ? String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
                    : String.format("%d:%02d", s / 60, s % 60));
            try {
                ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
                if (am.getLockTaskModeState() == ActivityManager.LOCK_TASK_MODE_NONE) startLockTask();
            } catch (Exception ignored) {
            }
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        alive = true;
        setShowWhenLocked(true);
        setTurnScreenOn(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        int pad = Ui.dp(this, 32);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Ui.color(this, R.color.m3_surface));

        android.widget.ImageView logo = Ui.logoView(this);
        if (logo != null) Ui.add(root, logo, 0);

        TextView icon = new TextView(this);
        icon.setText("🔒");
        icon.setTextSize(56);
        icon.setGravity(Gravity.CENTER);
        root.addView(icon);

        TextView title = Ui.headline(this, "This phone is locked");
        title.setGravity(Gravity.CENTER);
        title.setOnLongClickListener(v -> {
            promptMaster();
            return true;
        });
        Ui.add(root, title, 12);

        String msg = Agent.prefs(this).getString("lockMsg", "");
        if (!msg.isEmpty()) {
            TextView m = Ui.body(this, msg, false);
            m.setTextSize(20);
            m.setGravity(Gravity.CENTER);
            Ui.add(root, m, 16);
        }

        countdown = Ui.headline(this, "");
        countdown.setTextSize(44);
        countdown.setGravity(Gravity.CENTER);
        Ui.add(root, countdown, 24);
        TextView back = Ui.body(this, "until it unlocks", true);
        back.setGravity(Gravity.CENTER);
        root.addView(back);

        Ui.add(root, Ui.button(this, "Emergency call", Ui.OUTLINED, v -> {
            try {
                startActivity(new Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e) {
                Toast.makeText(this, "No phone app available.", Toast.LENGTH_LONG).show();
            }
        }), 32);
        Ui.add(root, Ui.button(this, "Administrator unlock", Ui.OUTLINED, v -> promptMaster()), 8);
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        alive = true;
        handler.removeCallbacks(tick);
        tick.run();
    }

    @Override
    protected void onDestroy() {
        alive = false;
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // Intentionally blocked while locked.
    }

    private void finishLock() {
        if (Agent.prefs(this).getLong("lockUntil", 0) != 0) Actions.endTimedLock(this);
        try {
            stopLockTask();
        } catch (Exception ignored) {
        }
        alive = false;
        finish();
    }

    private void promptMaster() {
        final EditText input = Ui.field(this, "Master code");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        Ui.alertDialog(this)
                .setTitle("Administrator")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Unlock", (d, w) -> {
                    final String code = input.getText().toString();
                    new Thread(() -> {
                        final String err = Master.check(this, code);
                        runOnUiThread(() -> {
                            if (err != null) {
                                Toast.makeText(this, err, Toast.LENGTH_LONG).show();
                            } else {
                                Agent.addEvent(this, "local", "Master code on phone: unlocked the timed lock");
                                Actions.endTimedLock(this);
                                finishLock();
                            }
                        });
                    }).start();
                })
                .show();
    }
}
