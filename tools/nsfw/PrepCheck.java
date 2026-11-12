import com.familymdm.agent.sitepolicy.NudityPrep;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import javax.imageio.ImageIO;

/**
 * Build-machine check (nsfw-model.yml): prepares a picture with the phone's own NudityPrep and writes
 * the floats out, so the Python side can compare them with the original model's preparation.
 * Usage: java PrepCheck in.png out.f32
 */
public class PrepCheck {
    public static void main(String[] a) throws Exception {
        BufferedImage src = ImageIO.read(new File(a[0]));
        BufferedImage s = new BufferedImage(NudityPrep.SCALED, NudityPrep.SCALED, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = s.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, NudityPrep.SCALED, NudityPrep.SCALED, null);
        g.dispose();
        int[] px = s.getRGB(0, 0, NudityPrep.SCALED, NudityPrep.SCALED, null, 0, NudityPrep.SCALED);
        float[] in = NudityPrep.toInput(px);
        try (DataOutputStream o = new DataOutputStream(new FileOutputStream(a[1]))) {
            for (float f : in) o.writeInt(Integer.reverseBytes(Float.floatToIntBits(f))); // little-endian for numpy
        }
    }
}
