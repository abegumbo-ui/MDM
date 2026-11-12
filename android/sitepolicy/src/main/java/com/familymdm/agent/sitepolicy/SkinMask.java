package com.familymdm.agent.sitepolicy;

/**
 * "Cover skin" for pictures in our Browser: every skin-coloured patch of a picture is painted over
 * with grey blocks. It goes by colour only (the well-known RGB + YCbCr skin rules from Kovac et al.
 * and Chai & Ngan), so faces, hands, wood, sand and beige walls are covered too -- it is meant for
 * the strictest setups. Plain Java on raw pixels so it can be checked off the phone (SkinMaskCheck).
 */
public final class SkinMask {
    private SkinMask() {}

    /** Size of a block, in pixels. */
    public static final int CELL = 8;
    /** A block is covered when at least this share of it looks like skin. */
    public static final float SHARE = 0.3f;
    public static final int GREY = 0xFF9E9E9E;

    public static boolean isSkin(int argb) {
        if ((argb >>> 24) < 128) return false; // see-through
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        // RGB rule (daylight)
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        boolean rgb = r > 95 && g > 40 && b > 20 && max - min > 15 && Math.abs(r - g) > 15 && r > g && r > b;
        if (!rgb) return false;
        // YCbCr rule
        double cb = 128 - 0.168736 * r - 0.331264 * g + 0.5 * b;
        double cr = 128 + 0.5 * r - 0.418688 * g - 0.081312 * b;
        return cb >= 77 && cb <= 127 && cr >= 133 && cr <= 173;
    }

    /** Covers skin blocks in place; returns how much of the picture was covered (0..1). */
    public static float cover(int[] px, int w, int h) {
        int covered = 0, cells = 0;
        for (int cy = 0; cy < h; cy += CELL) {
            for (int cx = 0; cx < w; cx += CELL) {
                int ex = Math.min(w, cx + CELL), ey = Math.min(h, cy + CELL), skin = 0, n = 0;
                for (int y = cy; y < ey; y++) for (int x = cx; x < ex; x++) { n++; if (isSkin(px[y * w + x])) skin++; }
                cells++;
                if (n > 0 && skin >= n * SHARE) {
                    covered++;
                    for (int y = cy; y < ey; y++) for (int x = cx; x < ex; x++) px[y * w + x] = GREY;
                }
            }
        }
        return cells == 0 ? 0 : (float) covered / cells;
    }
}
