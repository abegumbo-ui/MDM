package com.familymdm.agent;

import android.content.Context;
import android.graphics.Bitmap;

import com.familymdm.agent.sitepolicy.NudityPrep;

import org.tensorflow.lite.Interpreter;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * The on-phone photo checker: how likely a picture is to show nudity, from 0 to 1. Runs only on the
 * phone; the picture is never sent anywhere. The model (assets/nsfw.tflite) is built from the open
 * opennsfw2 / Yahoo open_nsfw model by the build machines (.github/workflows/nsfw-model.yml); a build
 * without it simply has no photo checker (score -1). Like every such checker it is sometimes wrong
 * both ways.
 */
final class NudityCheck {
    private NudityCheck() {}

    private static Interpreter interp;
    private static boolean missing;

    private static void load(Context c) {
        if (interp != null || missing) return;
        try (InputStream in = c.getAssets().open("nsfw.tflite")) {
            ByteArrayOutputStream all = new ByteArrayOutputStream();
            byte[] buf = new byte[65536];
            for (int n; (n = in.read(buf)) > 0; ) all.write(buf, 0, n);
            byte[] b = all.toByteArray();
            ByteBuffer model = ByteBuffer.allocateDirect(b.length).order(ByteOrder.nativeOrder());
            model.put(b).rewind();
            Interpreter.Options o = new Interpreter.Options();
            o.setNumThreads(2);
            interp = new Interpreter(model, o);
        } catch (Throwable e) {
            missing = true;
        }
    }

    static synchronized boolean available(Context c) {
        load(c);
        return interp != null;
    }

    /** 0..1 (likely nudity at NudityPrep.THRESHOLD and above), or -1 when there is no checker in this build. */
    static synchronized float score(Context c, Bitmap picture) {
        load(c);
        if (interp == null || picture == null) return -1;
        try {
            Bitmap soft = picture.getConfig() == Bitmap.Config.HARDWARE ? picture.copy(Bitmap.Config.ARGB_8888, false) : picture;
            Bitmap s = Bitmap.createScaledBitmap(soft, NudityPrep.SCALED, NudityPrep.SCALED, true);
            int[] px = new int[NudityPrep.SCALED * NudityPrep.SCALED];
            s.getPixels(px, 0, NudityPrep.SCALED, 0, 0, NudityPrep.SCALED, NudityPrep.SCALED);
            float[] in = NudityPrep.toInput(px);
            ByteBuffer input = ByteBuffer.allocateDirect(in.length * 4).order(ByteOrder.nativeOrder());
            input.asFloatBuffer().put(in);
            float[][] out = new float[1][2];
            interp.run(input, out);
            return out[0][1];
        } catch (Throwable e) {
            return -1;
        }
    }
}
