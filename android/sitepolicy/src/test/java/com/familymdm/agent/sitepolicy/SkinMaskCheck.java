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

        // Women only / men only: a 400x400 picture all skin, a man's face at the left, a woman's at the right.
        float[][] out = new float[4 + NudeDetect.LABELS.length][2];
        int maleFace = java.util.Arrays.asList(NudeDetect.LABELS).indexOf("FACE_MALE"), femaleFace = java.util.Arrays.asList(NudeDetect.LABELS).indexOf("FACE_FEMALE");
        // anchors in 320-pixel units (the picture is 400 wide: k = 1.25)
        out[0][0] = 40; out[1][0] = 40; out[2][0] = 30; out[3][0] = 30; out[4 + maleFace][0] = 0.9f;
        out[0][1] = 260; out[1][1] = 40; out[2][1] = 30; out[3][1] = 30; out[4 + femaleFace][1] = 0.9f;
        java.util.List<NudeDetect.Det> dets = NudeDetect.decode(out, 400, 400);
        check(dets.size() == 2, "two faces read from the detector's answer (" + dets + ")");
        NudeDetect.People people = new NudeDetect.People(dets);
        int W = 400;
        int[] img = new int[W * W];
        java.util.Arrays.fill(img, 0xFFE0AC8C);
        SkinMask.cover(img, W, W, people, "women");
        check(img[150 * W + 50] == 0xFFE0AC8C, "women only: the man's body is left alone");
        check(img[150 * W + 330] == SkinMask.GREY, "women only: the woman's body is covered");
        check(img[390 * W + 200] == SkinMask.GREY, "women only: skin nobody can be tied to is covered (in doubt)");
        java.util.Arrays.fill(img, 0xFFE0AC8C);
        SkinMask.cover(img, W, W, people, "men");
        check(img[150 * W + 50] == SkinMask.GREY && img[150 * W + 330] == 0xFFE0AC8C, "men only: the other way round");
        java.util.Arrays.fill(img, 0xFFE0AC8C);
        SkinMask.cover(img, W, W, people, "all");
        check(img[150 * W + 50] == SkinMask.GREY && img[150 * W + 330] == SkinMask.GREY, "everyone: both covered");

        // Detector boxes: a bare body part is covered even where the colour rule sees nothing (very dark or grey skin).
        int breast = java.util.Arrays.asList(NudeDetect.LABELS).indexOf("FEMALE_BREAST_EXPOSED"), covered = java.util.Arrays.asList(NudeDetect.LABELS).indexOf("FEMALE_BREAST_COVERED");
        float[][] o2 = new float[4 + NudeDetect.LABELS.length][2];
        o2[0][0] = 160; o2[1][0] = 160; o2[2][0] = 40; o2[3][0] = 40; o2[4 + breast][0] = 0.8f;   // centre of a 320 picture
        o2[0][1] = 40; o2[1][1] = 280; o2[2][1] = 40; o2[3][1] = 40; o2[4 + covered][1] = 0.9f;   // clothed: left alone
        java.util.List<NudeDetect.Det> d2 = NudeDetect.decode(o2, 320, 320);
        int[] grey = new int[320 * 320];
        java.util.Arrays.fill(grey, 0xFF404040);
        float share2 = SkinMask.coverDetected(grey, 320, 320, d2, null, "all");
        check(grey[160 * 320 + 160] == SkinMask.GREY, "detected bare part is covered even though its colour is not skin");
        check(grey[280 * 320 + 40] == 0xFF404040, "a covered (clothed) part is left alone");
        check(share2 > 0.02f && share2 < 0.2f, "covered share is the box, not the picture (" + share2 + ")");
        check(SkinMask.coverDetected(grey, 320, 320, null, null, "all") == 0, "no detections: nothing covered");
        java.util.List<NudeDetect.Det> bare = NudeDetect.decode(o2, 320, 320);
        check(SkinMask.isPrivateExposed(bare.get(0)) == "FEMALE_BREAST_EXPOSED".equals(bare.get(0).label), "private-part rule matches the label");
    }
}
