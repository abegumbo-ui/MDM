package com.familymdm.agent;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.familymdm.agent.inspect.YoutubeInspector;
import com.familymdm.agent.sitepolicy.NudityPrep;
import com.familymdm.agent.sitepolicy.SkinMask;

import java.io.ByteArrayOutputStream;

/**
 * "Pictures in our Browser" for every browser that goes through Inspection (Chrome, Samsung
 * Internet, Edge...): each picture passes through here before the browser gets it, on the phone.
 * "nudity": a picture that may show nudity becomes plain grey. "skin": that, and grey blocks over
 * skin-coloured patches. The same checks as our own Browser (PictureGuard there).
 */
final class ProxyPictureFilter implements YoutubeInspector.PictureFilter {
    private static final int MAX_SIDE = 1024, TINY = 48;
    private final Context c;
    private final boolean skin;
    private final String who;

    ProxyPictureFilter(Context c, String mode) {
        this.c = c;
        this.skin = "skin".equals(mode);
        this.who = "all";
    }

    ProxyPictureFilter(Context c, String mode, String who) {
        this.c = c;
        this.skin = "skin".equals(mode);
        this.who = who == null ? "all" : who;
    }

    @Override
    public byte[] filter(byte[] raw) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(raw, 0, raw.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return grey(1, 1); // cannot be read, so cannot be checked
        if (bounds.outWidth < TINY && bounds.outHeight < TINY) return null; // icons
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / (o.inSampleSize * 2) >= MAX_SIDE) o.inSampleSize *= 2;
        o.inMutable = true;
        Bitmap b = BitmapFactory.decodeByteArray(raw, 0, raw.length, o);
        if (b == null) return grey(bounds.outWidth, bounds.outHeight);
        int w = b.getWidth(), h = b.getHeight();
        if (NudityCheck.score(c, b) >= NudityPrep.THRESHOLD) return grey(w, h);
        if (!skin) return null;
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        com.familymdm.agent.sitepolicy.NudeDetect.People people = "all".equals(who) ? null : NudeNetRunner.people(c, b);
        if (SkinMask.cover(px, w, h, people, who) <= 0) return null;
        b.setPixels(px, 0, w, 0, 0, w, h);
        return png(b);
    }

    private static byte[] grey(int w, int h) {
        Bitmap one = Bitmap.createBitmap(new int[]{SkinMask.GREY}, 1, 1, Bitmap.Config.ARGB_8888);
        return png(Bitmap.createScaledBitmap(one, Math.max(1, Math.min(w, 2000)), Math.max(1, Math.min(h, 2000)), false));
    }

    private static byte[] png(Bitmap b) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.PNG, 100, out);
        return out.toByteArray();
    }
}
