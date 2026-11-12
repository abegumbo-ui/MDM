package com.familymdm.agent.sitepolicy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * NudeNet (https://github.com/notAI-tech/NudeNet, AGPL-3.0; its 320n model): finds faces, marked
 * female or male, and body parts in a picture. Used for "cover skin of: women only / men only".
 * This is the plain-Java part (getting a picture ready, reading the model's answer) so the build
 * machines can check it against the original (tools/nudenet/DetectCheck.java); the model itself
 * runs on the phone (TensorFlow Lite).
 */
public final class NudeDetect {
    private NudeDetect() {}

    public static final int SIZE = 320;
    public static final String[] LABELS = {
            "FEMALE_GENITALIA_COVERED", "FACE_FEMALE", "BUTTOCKS_EXPOSED", "FEMALE_BREAST_EXPOSED",
            "FEMALE_GENITALIA_EXPOSED", "MALE_BREAST_EXPOSED", "ANUS_EXPOSED", "FEET_EXPOSED",
            "BELLY_COVERED", "FEET_COVERED", "ARMPITS_COVERED", "ARMPITS_EXPOSED", "FACE_MALE",
            "BELLY_EXPOSED", "MALE_GENITALIA_EXPOSED", "ANUS_COVERED", "FEMALE_BREAST_COVERED", "BUTTOCKS_COVERED"};

    public static final class Det {
        public final String label;
        public final float score, x, y, w, h; // in the original picture's pixels

        Det(String label, float score, float x, float y, float w, float h) {
            this.label = label;
            this.score = score;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        @Override
        public String toString() {
            return label + " " + Math.round(score * 100) + "% " + Math.round(x) + "," + Math.round(y) + " " + Math.round(w) + "x" + Math.round(h);
        }
    }

    /**
     * The model's input from a picture already padded to a square (black at the right or bottom,
     * as NudeNet does) and scaled to SIZE x SIZE: argb pixels -> blue, green, red from 0 to 1,
     * pixel by pixel (the phone model takes them in this order).
     */
    public static float[] input(int[] argbSquare) {
        if (argbSquare.length != SIZE * SIZE) throw new IllegalArgumentException("need " + SIZE + "x" + SIZE);
        float[] out = new float[SIZE * SIZE * 3];
        int i = 0;
        // Blue, green, red: NudeNet swaps the colours twice on the way in (cvtColor RGBA2BGR on a
        // BGR picture, then blobFromImage swapRB), so its model was trained on that order.
        for (int p : argbSquare) {
            out[i++] = (p & 0xFF) / 255f;
            out[i++] = ((p >> 8) & 0xFF) / 255f;
            out[i++] = ((p >> 16) & 0xFF) / 255f;
        }
        return out;
    }

    /**
     * Reads the model's answer: `out` is [4 + LABELS.length][anchors] (box centre x, y, width,
     * height in SIZE pixels, then one score per label). `side` is the padded square's side in the
     * original picture (max of its width and height). Same steps as NudeNet's own: score at least
     * 0.2, then overlapping boxes merged (keep 0.25 and up, overlap 0.45).
     */
    public static List<Det> decode(float[][] out, int imageW, int imageH) {
        int anchors = out[0].length, side = Math.max(imageW, imageH);
        // Some phone models give box positions as 0..1 of the picture instead of 0..SIZE: handle both.
        float biggest = 0;
        for (int r = 0; r < 4; r++) for (int a = 0; a < anchors; a++) biggest = Math.max(biggest, Math.abs(out[r][a]));
        float k = (biggest <= 2f ? side : side / (float) SIZE);
        List<Det> all = new ArrayList<>();
        for (int a = 0; a < anchors; a++) {
            int best = -1;
            float score = 0;
            for (int c = 0; c < LABELS.length; c++) {
                float s = out[4 + c][a];
                if (s > score) { score = s; best = c; }
            }
            if (score < 0.2f || best < 0) continue;
            float w = out[2][a] * k, h = out[3][a] * k;
            float x = out[0][a] * k - w / 2, y = out[1][a] * k - h / 2;
            x = Math.max(0, Math.min(x, imageW));
            y = Math.max(0, Math.min(y, imageH));
            w = Math.min(w, imageW - x);
            h = Math.min(h, imageH - y);
            all.add(new Det(LABELS[best], score, x, y, w, h));
        }
        Collections.sort(all, (p, q) -> Float.compare(q.score, p.score));
        List<Det> kept = new ArrayList<>();
        for (Det d : all) {
            if (d.score < 0.25f) continue;
            boolean overlaps = false;
            for (Det k2 : kept) if (iou(d, k2) > 0.45f) { overlaps = true; break; }
            if (!overlaps) kept.add(d);
        }
        return kept;
    }

    private static float iou(Det a, Det b) {
        float x1 = Math.max(a.x, b.x), y1 = Math.max(a.y, b.y);
        float x2 = Math.min(a.x + a.w, b.x + b.w), y2 = Math.min(a.y + a.h, b.y + b.h);
        float inter = Math.max(0, x2 - x1) * Math.max(0, y2 - y1);
        float union = a.w * a.h + b.w * b.h - inter;
        return union <= 0 ? 0 : inter / union;
    }

    // ---------- "cover skin of: women only / men only" ----------

    /** A rectangle in picture pixels. */
    private static final class Area {
        final float x1, y1, x2, y2;

        Area(float x1, float y1, float x2, float y2) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }

        boolean has(float x, float y) {
            return x >= x1 && x <= x2 && y >= y1 && y <= y2;
        }
    }

    /**
     * Who a point of the picture belongs to, as far as can be told: a face's whole body (from the
     * face down: 1.5 face widths to each side, 8 face heights down) and the body parts found.
     */
    public static final class People {
        final List<Area> women = new ArrayList<>(), men = new ArrayList<>();

        public People(List<Det> dets) {
            for (Det d : dets) {
                boolean face = d.label.startsWith("FACE_");
                boolean female = d.label.startsWith("FEMALE_") || d.label.equals("FACE_FEMALE");
                boolean male = d.label.startsWith("MALE_") || d.label.equals("FACE_MALE");
                if (!female && !male) continue; // belly, feet, armpits... say nothing about who
                Area a = face
                        ? new Area(d.x - 1.5f * d.w, d.y - 0.5f * d.h, d.x + 2.5f * d.w, d.y + 8f * d.h)
                        : new Area(d.x - 0.3f * d.w, d.y - 0.3f * d.h, d.x + 1.3f * d.w, d.y + 1.3f * d.h);
                (female ? women : men).add(a);
            }
        }

        /**
         * Should skin at (x, y) be covered? who: "women" or "men" (anything else: everyone).
         * Left alone only when the point clearly belongs to the other kind of person and to no one
         * of the chosen kind; when in doubt, it is covered.
         */
        public boolean cover(float x, float y, String who) {
            boolean w = false, m = false;
            for (Area a : women) if (a.has(x, y)) { w = true; break; }
            for (Area a : men) if (a.has(x, y)) { m = true; break; }
            if ("women".equals(who)) return !(m && !w);
            if ("men".equals(who)) return !(w && !m);
            return true;
        }
    }
}
