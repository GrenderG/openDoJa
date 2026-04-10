package opendoja.probes;

import com.nttdocomo.ui.graphics3d.Primitive;
import opendoja.g3d.Software3DContext;
import opendoja.g3d.SoftwareTexture;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.HashSet;
import java.util.Set;

public final class TexturedVertexModulationProbe {
    private TexturedVertexModulationProbe() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
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
                    Primitive.PRIMITIVE_TRIANGLES,
                    Primitive.TEXTURE_COORD_PER_VERTEX | Primitive.COLOR_PER_VERTEX_INTERNAL,
                    1,
                    new int[]{
                            -14, 14, 0,
                            14, 14, 0,
                            0, -14, 0
                    },
                    new int[]{
                            0xFFFF0000,
                            0xFF00FF00,
                            0x400000FF
                    },
                    new int[]{
                            0, 0,
                            0, 0,
                            0, 0
                    },
                    makeWhiteTexture(),
                    Software3DContext.identity(),
                    0,
                    1f,
                    false,
                    0f,
                    0f
            );
        } finally {
            graphics.dispose();
        }

        Set<Integer> uniqueOpaque = new HashSet<>();
        int translucentPixels = 0;
        for (int y = 0; y < target.getHeight(); y++) {
            for (int x = 0; x < target.getWidth(); x++) {
                int pixel = target.getRGB(x, y);
                int alpha = (pixel >>> 24) & 0xFF;
                if (alpha == 0) {
                    continue;
                }
                uniqueOpaque.add(pixel);
                if (alpha != 0xFF) {
                    translucentPixels++;
                }
            }
        }
        if (uniqueOpaque.size() < 20) {
            throw new IllegalStateException("Expected interpolated textured vertex modulation, got " + uniqueOpaque.size() + " colors");
        }
        if (translucentPixels == 0) {
            throw new IllegalStateException("Expected translucent output from vertex alpha modulation");
        }

        int center = target.getRGB(16, 16);
        assertChannel("center alpha", (center >>> 24) & 0xFF, 163, 3);
        assertChannel("center red", (center >>> 16) & 0xFF, 61, 3);
        assertChannel("center green", (center >>> 8) & 0xFF, 71, 3);
        assertChannel("center blue", center & 0xFF, 123, 3);

        int uniqueColorCount = uniqueOpaque.size();
        int translucentPixelCount = translucentPixels;
        DemoLog.info(TexturedVertexModulationProbe.class, () -> "uniqueNonTransparentColors=" + uniqueColorCount
                + " translucentPixels=" + translucentPixelCount);
    }

    private static SoftwareTexture makeWhiteTexture() throws Exception {
        BufferedImage texture = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        texture.setRGB(0, 0, 0xFFFFFFFF);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(texture, "png", out);
        return new SoftwareTexture(out.toByteArray(), true);
    }

    private static void assertChannel(String label, int actual, int expected, int tolerance) {
        if (Math.abs(actual - expected) > tolerance) {
            throw new IllegalStateException(label + " mismatch: expected " + expected + ", got " + actual);
        }
    }
}
