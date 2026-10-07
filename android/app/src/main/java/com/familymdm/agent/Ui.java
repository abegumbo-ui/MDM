package com.familymdm.agent;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Material 3 look (colors, shapes, type) built by hand, so the agent needs no extra libraries. */
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

    /** The administrator's logo, if one has been downloaded; otherwise null. */
    static ImageView logoView(Context c) {
        try {
            java.io.File f = Agent.logoFile(c);
            if (!f.exists()) return null;
            Bitmap bmp = BitmapFactory.decodeFile(f.getPath());
            if (bmp == null) return null;
            ImageView iv = new ImageView(c);
            iv.setImageBitmap(bmp);
            iv.setAdjustViewBounds(true);
            iv.setMaxHeight(dp(c, 96));
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            return iv;
        } catch (Exception e) {
            return null;
        }
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
        t.setTextSize(28);
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

    static TextView chip(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(12);
        t.setTextColor(color(c, R.color.m3_on_surface_variant));
        t.setPadding(dp(c, 10), dp(c, 4), dp(c, 10), dp(c, 4));
        t.setBackground(shape(color(c, R.color.m3_surface_container_high), dp(c, 8), 0, 0));
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
        e.setTextColor(color(c, R.color.m3_on_surface));
        e.setHintTextColor(color(c, R.color.m3_on_surface_variant));
        e.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        e.setBackground(shape(color(c, R.color.m3_surface), dp(c, 8), color(c, R.color.m3_outline), dp(c, 1)));
        return e;
    }

    /** Same look as field(), but narrows a dropdown of suggestions as you type instead of a plain box. */
    static android.widget.AutoCompleteTextView autoCompleteField(Context c, String hint) {
        android.widget.AutoCompleteTextView e = new android.widget.AutoCompleteTextView(c);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setTextColor(color(c, R.color.m3_on_surface));
        e.setHintTextColor(color(c, R.color.m3_on_surface_variant));
        e.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        e.setBackground(shape(color(c, R.color.m3_surface), dp(c, 8), color(c, R.color.m3_outline), dp(c, 1)));
        e.setThreshold(1);
        return e;
    }
}
