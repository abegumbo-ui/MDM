import com.familymdm.agent.sitepolicy.NudeDetect;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

/**
 * Build-machine check for NudeDetect (run by tools/nudenet/convert.py): reads the phone model's raw
 * answer for a picture, decodes it the phone's way, and compares with NudeNet's own result
 * (same things found, boxes in the same places). Also prepares the picture the phone's way and
 * writes it out, for the preparation check. Usage: java DetectCheck picture.jpg
 */
public class DetectCheck {
    public static void main(String[] a) throws Exception {
        String name = a[0];
        String meta = new String(Files.readAllBytes(new File(name + ".json").toPath()), "UTF-8");
        int w = intOf(meta, "\"w\": (\\d+)"), h = intOf(meta, "\"h\": (\\d+)"), anchors = intOf(meta, "\"anchors\": (\\d+)");
        ByteBuffer bb = ByteBuffer.wrap(Files.readAllBytes(new File(name + ".out.f32").toPath())).order(ByteOrder.LITTLE_ENDIAN);
        float[][] out = new float[4 + NudeDetect.LABELS.length][anchors];
        for (int r = 0; r < out.length; r++) for (int c = 0; c < anchors; c++) out[r][c] = bb.getFloat();
        List<NudeDetect.Det> mine = NudeDetect.decode(out, w, h);
        System.out.println(name + " phone: " + mine);

        // NudeNet's own result: {"class": "...", "score": ..., "box": [x, y, w, h]}
        Matcher m = Pattern.compile("\"class\": \"(\\w+)\", \"score\": ([0-9.e-]+), \"box\": \\[(-?\\d+), (-?\\d+), (-?\\d+), (-?\\d+)\\]").matcher(meta);
        List<float[]> ref = new ArrayList<>();
        List<String> refLabels = new ArrayList<>();
        while (m.find()) {
            refLabels.add(m.group(1));
            ref.add(new float[]{Float.parseFloat(m.group(3)), Float.parseFloat(m.group(4)), Float.parseFloat(m.group(5)), Float.parseFloat(m.group(6))});
        }
        int matched = 0;
        for (int i = 0; i < ref.size(); i++) {
            float[] r = ref.get(i);
            for (NudeDetect.Det d : mine) {
                if (!d.label.equals(refLabels.get(i))) continue;
                if (iou(r, new float[]{d.x, d.y, d.w, d.h}) > 0.6f) { matched++; break; }
            }
        }
        System.out.println(name + ": " + matched + " of " + ref.size() + " found the same way (phone found " + mine.size() + ")");
        // The phone model is stored in half precision, so a borderline find may come and go.
        if (ref.size() > 0 && matched < Math.max(1, (int) Math.ceil(ref.size() * 0.75))) System.exit(1);

        // The phone's picture preparation: pad to a square at the right/bottom, scale to 320.
        BufferedImage src = ImageIO.read(new File(name));
        int side = Math.max(src.getWidth(), src.getHeight());
        BufferedImage sq = new BufferedImage(NudeDetect.SIZE, NudeDetect.SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sq.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(java.awt.Color.BLACK);
        g.fillRect(0, 0, NudeDetect.SIZE, NudeDetect.SIZE);
        float k = NudeDetect.SIZE / (float) side;
        g.drawImage(src, 0, 0, Math.round(src.getWidth() * k), Math.round(src.getHeight() * k), null);
        g.dispose();
        float[] in = NudeDetect.input(sq.getRGB(0, 0, NudeDetect.SIZE, NudeDetect.SIZE, null, 0, NudeDetect.SIZE));
        ByteBuffer ob = ByteBuffer.allocate(in.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float f : in) ob.putFloat(f);
        Files.write(new File(name + ".prep.f32").toPath(), ob.array());
    }

    private static int intOf(String s, String re) {
        Matcher m = Pattern.compile(re).matcher(s);
        if (!m.find()) throw new IllegalStateException("missing " + re);
        return Integer.parseInt(m.group(1));
    }

    private static float iou(float[] a, float[] b) {
        float x1 = Math.max(a[0], b[0]), y1 = Math.max(a[1], b[1]);
        float x2 = Math.min(a[0] + a[2], b[0] + b[2]), y2 = Math.min(a[1] + a[3], b[1] + b[3]);
        float inter = Math.max(0, x2 - x1) * Math.max(0, y2 - y1);
        float uni = a[2] * a[3] + b[2] * b[3] - inter;
        return uni <= 0 ? 0 : inter / uni;
    }
}
