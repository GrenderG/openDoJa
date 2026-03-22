package opendoja.demo;

import opendoja.g3d.Software3DContext;
import opendoja.g3d.SoftwareTexture;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.HashSet;
import java.util.Set;

public final class Textured3DProbe {
    private Textured3DProbe() {
    }

    public static void main(String[] args) throws Exception {
        DemoLog.enableInfoLogging();
        BufferedImage target = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = target.createGraphics();
        try {
            Software3DContext context = new Software3DContext();
            context.setUiParallelView(32, 32);
            context.renderUiPrimitive(
                    graphics,
                    target,
                    0,
                    0,
                    32,
                    32,
                    4,
                    0,
                    1,
                    new int[]{
                            -16, 16, 0,
                            16, 16, 0,
                            16, -16, 0,
                            -16, -16, 0
                    },
                    null,
                    new int[]{
                            0, 2,
                            2, 2,
                            2, 0,
                            0, 0
                    },
                    makeTexture(),
                    Software3DContext.identity(),
                    0,
                    1f
            );
        } finally {
            graphics.dispose();
        }

        Set<Integer> unique = new HashSet<>();
        for (int y = 0; y < target.getHeight(); y++) {
            for (int x = 0; x < target.getWidth(); x++) {
                int pixel = target.getRGB(x, y);
                if ((pixel >>> 24) != 0) {
                    unique.add(pixel);
                }
            }
        }
        DemoLog.info(Textured3DProbe.class, () -> "uniqueOpaqueColors=" + unique.size());
        if (unique.size() < 4) {
            throw new IllegalStateException("Expected textured raster output, got " + unique.size() + " colors");
        }
    }

    private static SoftwareTexture makeTexture() throws Exception {
        BufferedImage texture = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        texture.setRGB(0, 0, 0xFFFF0000);
        texture.setRGB(1, 0, 0xFF00FF00);
        texture.setRGB(0, 1, 0xFF0000FF);
        texture.setRGB(1, 1, 0xFFFFFFFF);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(texture, "png", out);
        return new SoftwareTexture(out.toByteArray(), true);
    }
}
