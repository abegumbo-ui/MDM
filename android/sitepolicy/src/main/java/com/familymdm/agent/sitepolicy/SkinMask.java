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

    /** Boxes below this detector score are ignored by coverDetected. */
    public static final float DETECT_MIN = 0.3f;
    /** The detector's body parts that are bare and private: one of these at this score greys the whole picture. */
    public static final float PRIVATE_MIN = 0.5f;

    public static boolean isPrivateExposed(NudeDetect.Det d) {
        if (d.score < PRIVATE_MIN) return false;
        return d.label.equals("FEMALE_GENITALIA_EXPOSED") || d.label.equals("MALE_GENITALIA_EXPOSED")
                || d.label.equals("FEMALE_BREAST_EXPOSED") || d.label.equals("BUTTOCKS_EXPOSED") || d.label.equals("ANUS_EXPOSED");
    }

    /**
     * Paints a grey box (a little larger than the detector's) over every bare body part it found.
     * This works on what the picture shows, not on colour, so dark, dim and black-and-white skin is
     * covered too. who/people as in cover(). Returns how much of the picture was covered (0..1).
     */
    public static float coverDetected(int[] px, int w, int h, java.util.List<NudeDetect.Det> dets, NudeDetect.People people, String who) {
        if (dets == null || w <= 0 || h <= 0) return 0;
        boolean everyone = people == null || !("women".equals(who) || "men".equals(who));
        long covered = 0;
        for (NudeDetect.Det d : dets) {
            if (!d.label.endsWith("_EXPOSED") || d.score < DETECT_MIN) continue;
            if (!everyone && !people.cover(d.x + d.w / 2f, d.y + d.h / 2f, who)) continue;
            int padX = Math.round(d.w * 0.15f) + CELL / 2, padY = Math.round(d.h * 0.15f) + CELL / 2;
            int x1 = Math.max(0, Math.round(d.x) - padX), y1 = Math.max(0, Math.round(d.y) - padY);
            int x2 = Math.min(w, Math.round(d.x + d.w) + padX), y2 = Math.min(h, Math.round(d.y + d.h) + padY);
            for (int y = y1; y < y2; y++) for (int x = x1; x < x2; x++) px[y * w + x] = GREY;
            if (x2 > x1 && y2 > y1) covered += (long) (x2 - x1) * (y2 - y1);
        }
        return Math.min(1f, covered / (float) ((long) w * h));
    }

    /** Covers skin blocks in place; returns how much of the picture was covered (0..1). */
    public static float cover(int[] px, int w, int h) {
        return cover(px, w, h, null, "all");
    }

    /**
     * Same, but only skin of the chosen people ("women" or "men"; "all" = everyone), as told by
     * the man/woman detector (NudeDetect.People, in this picture's pixels). When in doubt: covered.
     */
    public static float cover(int[] px, int w, int h, NudeDetect.People people, String who) {
        boolean everyone = people == null || !("women".equals(who) || "men".equals(who));
        int covered = 0, cells = 0;
        for (int cy = 0; cy < h; cy += CELL) {
            for (int cx = 0; cx < w; cx += CELL) {
                int ex = Math.min(w, cx + CELL), ey = Math.min(h, cy + CELL), skin = 0, n = 0;
                for (int y = cy; y < ey; y++) for (int x = cx; x < ex; x++) { n++; if (isSkin(px[y * w + x])) skin++; }
                cells++;
                if (n > 0 && skin >= n * SHARE && (everyone || people.cover(cx + (ex - cx) / 2f, cy + (ey - cy) / 2f, who))) {
                    covered++;
                    for (int y = cy; y < ey; y++) for (int x = cx; x < ex; x++) px[y * w + x] = GREY;
                }
            }
        }
        return cells == 0 ? 0 : (float) covered / cells;
    }
}
