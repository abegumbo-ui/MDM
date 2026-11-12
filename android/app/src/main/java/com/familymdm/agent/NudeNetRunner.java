package com.familymdm.agent;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import com.familymdm.agent.sitepolicy.NudeDetect;

import org.tensorflow.lite.Interpreter;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * Runs the man/woman detector (NudeNet 320n, AGPL-3.0; assets/nudenet320.tflite) on a picture, on
 * the phone. Used for "cover skin of: women only / men only". Null when this build has no detector.
 */
final class NudeNetRunner {
    private NudeNetRunner() {}

    private static volatile ByteBuffer model;
    private static volatile boolean missing;
    private static final ThreadLocal<Interpreter> interp = new ThreadLocal<>();

    private static synchronized ByteBuffer model(Context c) {
        if (model == null && !missing) {
            try (InputStream in = c.getAssets().open("nudenet320.tflite")) {
                ByteArrayOutputStream all = new ByteArrayOutputStream();
                byte[] b = new byte[65536];
                for (int n; (n = in.read(b)) > 0; ) all.write(b, 0, n);
                ByteBuffer m = ByteBuffer.allocateDirect(all.size()).order(ByteOrder.nativeOrder());
                m.put(all.toByteArray()).rewind();
                model = m;
            } catch (Throwable e) {
                missing = true;
            }
        }
        return model;
    }

    /** Who is in the picture (in the picture's own pixels), or null if it cannot be told. */
    static NudeDetect.People people(Context c, Bitmap picture) {
        try {
            Interpreter it = interp.get();
            if (it == null) {
                ByteBuffer m = model(c);
                if (m == null) return null;
                Interpreter.Options o = new Interpreter.Options();
                o.setNumThreads(1);
                it = new Interpreter(m.duplicate().order(ByteOrder.nativeOrder()), o);
                interp.set(it);
            }
            int w = picture.getWidth(), h = picture.getHeight(), side = Math.max(w, h);
            // Padded to a square with black at the right/bottom, then scaled to 320 (as NudeNet does).
            Bitmap sq = Bitmap.createBitmap(NudeDetect.SIZE, NudeDetect.SIZE, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(sq);
            cv.drawColor(Color.BLACK);
            float k = NudeDetect.SIZE / (float) side;
            cv.drawBitmap(Bitmap.createScaledBitmap(picture, Math.max(1, Math.round(w * k)), Math.max(1, Math.round(h * k)), true), 0, 0, new Paint(Paint.FILTER_BITMAP_FLAG));
            int[] px = new int[NudeDetect.SIZE * NudeDetect.SIZE];
            sq.getPixels(px, 0, NudeDetect.SIZE, 0, 0, NudeDetect.SIZE, NudeDetect.SIZE);
            float[] in = NudeDetect.input(px);
            ByteBuffer input = ByteBuffer.allocateDirect(in.length * 4).order(ByteOrder.nativeOrder());
            input.asFloatBuffer().put(in);
            int[] shape = it.getOutputTensor(0).shape(); // [1, 22, 2100] (or [1, 2100, 22])
            float[][][] out = new float[shape[0]][shape[1]][shape[2]];
            it.run(input, out);
            float[][] rows = out[0];
            if (rows.length != 4 + NudeDetect.LABELS.length) { // turn [2100][22] into [22][2100]
                float[][] t = new float[rows[0].length][rows.length];
                for (int i = 0; i < rows.length; i++) for (int j = 0; j < rows[0].length; j++) t[j][i] = rows[i][j];
                rows = t;
            }
            List<NudeDetect.Det> dets = NudeDetect.decode(rows, w, h);
            return new NudeDetect.People(dets);
        } catch (Throwable e) {
            return null;
        }
    }
}
