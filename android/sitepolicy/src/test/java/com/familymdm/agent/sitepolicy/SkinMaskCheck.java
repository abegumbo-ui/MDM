package com.familymdm.agent.sitepolicy;

/** Run by android.yml: skin colours are covered, other colours are left alone. */
public class SkinMaskCheck {
    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
        System.out.println("ok: " + what);
    }

    public static void main(String[] a) {
        int[] skinTones = {0xFFE0AC8C, 0xFFC68642, 0xFFF1C27D, 0xFF8D5524, 0xFFFFDBAC};
        for (int s : skinTones) check(SkinMask.isSkin(s), "skin tone " + Integer.toHexString(s) + " is skin");
        int[] others = {0xFF0000FF, 0xFF00FF00, 0xFFFFFFFF, 0xFF000000, 0xFF808080, 0xFF3366CC, 0x00E0AC8C};
        for (int o : others) check(!SkinMask.isSkin(o), "colour " + Integer.toHexString(o) + " is not skin");

        // A 32x32 picture: left half skin, right half blue.
        int w = 32, h = 32;
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) px[y * w + x] = x < 16 ? 0xFFE0AC8C : 0xFF3366CC;
        float share = SkinMask.cover(px, w, h);
        check(Math.abs(share - 0.5f) < 0.01f, "half the picture covered (" + share + ")");
        check(px[5 * w + 3] == SkinMask.GREY, "skin side is grey");
        check(px[5 * w + 30] == 0xFF3366CC, "blue side untouched");
    }
}
