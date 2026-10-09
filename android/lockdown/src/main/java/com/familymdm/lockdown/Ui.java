package com.familymdm.lockdown;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/** Same hand-built dark/red look as the agent app's own Ui.java, trimmed to what this wizard needs. */
final class Ui {
    static final int FILLED = 0;
    static final int TONAL = 1;
    static final int OUTLINED = 2;
    static final int DANGER = 3;

    private Ui() {}

    static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    static int color(Context c, int res) {
        return c.getColor(res);
    }

    private static GradientDrawable shape(int fill, int radiusPx, int strokeColor, int strokePx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radiusPx);
        if (strokePx > 0) g.setStroke(strokePx, strokeColor);
        return g;
    }

    static void add(LinearLayout parent, View child, int topMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(parent.getContext(), topMarginDp);
        parent.addView(child, lp);
    }

    static LinearLayout card(Context c, LinearLayout parent) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(c, 16);
        box.setPadding(pad, pad, pad, pad);
        box.setBackground(shape(color(c, R.color.m3_surface_container), dp(c, 16), color(c, R.color.m3_outline_variant), dp(c, 1)));
        add(parent, box, 12);
        return box;
    }

    static TextView headline(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(26);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(color(c, R.color.m3_on_surface));
        return t;
    }

    static TextView titleText(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(18);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(color(c, R.color.m3_on_surface));
        return t;
    }

    static TextView body(Context c, String text, boolean secondary) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(14);
        t.setTextColor(color(c, secondary ? R.color.m3_on_surface_variant : R.color.m3_on_surface));
        return t;
    }

    static TextView banner(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(22);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(color(c, R.color.m3_on_surface));
        t.setPadding(dp(c, 16), dp(c, 18), dp(c, 16), dp(c, 18));
        t.setBackground(shape(color(c, R.color.m3_surface_container), 0, 0, 0));
        return t;
    }

    static Button button(Context c, String text, int style, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setStateListAnimator(null);
        b.setElevation(0);
        b.setMinHeight(dp(c, 44));
        b.setMinimumHeight(dp(c, 44));
        b.setPadding(dp(c, 20), 0, dp(c, 20), 0);
        int fill = Color.TRANSPARENT;
        int stroke = 0;
        int strokeColor = 0;
        int textColor;
        switch (style) {
            case FILLED:
                fill = color(c, R.color.m3_primary);
                textColor = color(c, R.color.m3_on_primary);
                break;
            case TONAL:
                fill = color(c, R.color.m3_secondary_container);
                textColor = color(c, R.color.m3_on_secondary_container);
                break;
            case DANGER:
                stroke = dp(c, 1);
                strokeColor = color(c, R.color.m3_error);
                textColor = color(c, R.color.m3_error);
                break;
            default:
                stroke = dp(c, 1);
                strokeColor = color(c, R.color.m3_outline);
                textColor = color(c, R.color.m3_primary);
        }
        Drawable bg = new RippleDrawable(ColorStateList.valueOf(0x22000000),
                shape(fill, dp(c, 100), strokeColor, stroke), null);
        b.setBackground(bg);
        b.setTextColor(textColor);
        b.setOnClickListener(l);
        return b;
    }

    static EditText field(Context c, String hint) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setTextSize(15);
        e.setTextColor(color(c, R.color.m3_on_surface));
        e.setHintTextColor(color(c, R.color.m3_on_surface_variant));
        e.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        e.setBackground(shape(color(c, R.color.m3_surface), dp(c, 8), color(c, R.color.m3_outline), dp(c, 1)));
        return e;
    }

    /** A multi-line text box for pasting a block of text into, rather than a single search/account
     * field -- monospace so pasted package names stay easy to scan. */
    static EditText multilineField(Context c, String hint) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setMinLines(6);
        e.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        e.setTextSize(13);
        e.setTypeface(Typeface.MONOSPACE);
        e.setTextColor(color(c, R.color.m3_on_surface));
        e.setHintTextColor(color(c, R.color.m3_on_surface_variant));
        e.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        e.setBackground(shape(color(c, R.color.m3_surface), dp(c, 8), color(c, R.color.m3_outline), dp(c, 1)));
        return e;
    }

    /** A Switch with its thumb/track colors pinned explicitly -- this app's theme forces a very
     * dark background without declaring itself a proper dark theme to the platform, so a plain
     * Switch inherits ambient device-default colors that can render as barely visible (dark-on-dark)
     * against it. Explicit tinting here, matching the rest of this app's own palette, instead of
     * trusting that resolution. */
    static Switch tintedSwitch(Context c) {
        Switch sw = new Switch(c);
        int on = color(c, R.color.m3_primary);
        int onThumb = color(c, R.color.m3_on_primary);
        int off = color(c, R.color.m3_outline);
        int offThumb = color(c, R.color.m3_on_surface_variant);
        int[][] states = {{android.R.attr.state_checked}, {}};
        sw.setTrackTintList(new ColorStateList(states, new int[]{on, off}));
        sw.setThumbTintList(new ColorStateList(states, new int[]{onThumb, offThumb}));
        return sw;
    }

    /** A full-width row with a checkbox, a bold label, and an optional description underneath. */
    static LinearLayout checkRow(Context c, String label, String desc, boolean checked, android.widget.CompoundButton.OnCheckedChangeListener l) {
        return checkRow(c, null, label, desc, checked, l);
    }

    /** Same as above, with an app icon between the checkbox and the text -- pass null for a row
     * with no icon (a restriction switch, a Settings category, anything that isn't an app). */
    static LinearLayout checkRow(Context c, Drawable icon, String label, String desc, boolean checked, android.widget.CompoundButton.OnCheckedChangeListener l) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        int pad = dp(c, 10);
        row.setPadding(pad, pad, pad, pad);
        row.setBackground(shape(color(c, R.color.m3_surface_container), dp(c, 12), 0, 0));

        CheckBox box = new CheckBox(c);
        box.setChecked(checked);
        box.setOnCheckedChangeListener(l);
        row.addView(box);

        if (icon != null) {
            android.widget.ImageView iv = new android.widget.ImageView(c);
            iv.setImageDrawable(icon);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(c, 32), dp(c, 32));
            ilp.leftMargin = dp(c, 4);
            row.addView(iv, ilp);
        }

        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView t = body(c, label, false);
        t.setTextSize(15);
        text.addView(t);
        if (desc != null && !desc.isEmpty()) {
            TextView d = body(c, desc, true);
            d.setTextSize(12);
            text.addView(d);
        }
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textLp.leftMargin = dp(c, 8);
        row.addView(text, textLp);

        row.setOnClickListener(v -> box.setChecked(!box.isChecked()));
        return row;
    }

    static android.app.AlertDialog.Builder alertDialog(Context c) {
        return new android.app.AlertDialog.Builder(c, R.style.AppAlertDialogTheme);
    }
}
