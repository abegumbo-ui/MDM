package com.familymdm.agent.sitepolicy;

/**
 * Turns a picture into the photo checker's input, the same way the original model (Yahoo open_nsfw,
 * via opennsfw2's "YAHOO" preprocessing) expects it: the picture scaled to 256x256, the middle
 * 224x224 cut out, colours in blue-green-red order, minus the average colour the model was trained
 * with. Plain Java so the build machines can check it against the original (tools/nsfw/PrepCheck.java).
 */
public final class NudityPrep {
    private NudityPrep() {}

    public static final int SCALED = 256, SIZE = 224;
    private static final float MEAN_B = 104f, MEAN_G = 117f, MEAN_R = 123f;

    /** argb: SCALED*SCALED pixels (0xAARRGGBB), row by row. Returns SIZE*SIZE*3 floats (B, G, R per pixel). */
    public static float[] toInput(int[] argb) {
        if (argb.length != SCALED * SCALED) throw new IllegalArgumentException("need " + SCALED + "x" + SCALED);
        int off = (SCALED - SIZE) / 2;
        float[] out = new float[SIZE * SIZE * 3];
        int i = 0;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int p = argb[(y + off) * SCALED + (x + off)];
                out[i++] = (p & 0xFF) - MEAN_B;
                out[i++] = ((p >> 8) & 0xFF) - MEAN_G;
                out[i++] = ((p >> 16) & 0xFF) - MEAN_R;
            }
        }
        return out;
    }

    /** At or above this the picture is counted as "may show nudity". */
    public static final float THRESHOLD = 0.8f;
}
