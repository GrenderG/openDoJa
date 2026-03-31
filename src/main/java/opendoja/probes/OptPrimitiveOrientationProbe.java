package opendoja.probes;

import com.nttdocomo.opt.ui.j3d.Graphics3D;
import com.nttdocomo.opt.ui.j3d.PrimitiveArray;
import opendoja.g3d.Software3DContext;
import opendoja.g3d.SoftwareTexture;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

/**
 * Verifies that opt PrimitiveArray batches keep the expected texture orientation for the default
 * basis, the translation-only identity-basis path used by titles like Ridge Racers, and a quad
 * whose submitted geometry itself is vertically mirrored.
 */
public final class OptPrimitiveOrientationProbe {
    private OptPrimitiveOrientationProbe() {
    }

    public static void main(String[] args) throws Exception {
        DemoLog.enableInfoLogging();

        assertUpright("identity", identity(), false);
        assertUpright("identity-basis-translation", translatedIdentity(580f), false);
        assertOrientation("mirrored-geometry", identity(), true, 0xFF0000FF, 0xFFFF0000);
    }

    private static void assertUpright(String label, float[] transform, boolean mirroredGeometry) throws Exception {
        assertOrientation(label, transform, mirroredGeometry, 0xFFFF0000, 0xFF0000FF);
    }

    private static void assertOrientation(String label, float[] transform, boolean mirroredGeometry,
                                          int expectedTop, int expectedBottom) throws Exception {
        PrimitiveArray primitives = new PrimitiveArray(Graphics3D.PRIMITIVE_QUADS, Graphics3D.TEXTURE_COORD_PER_VERTEX, 1);
        int[] vertices = primitives.getVertexArray();
        int[] uvs = primitives.getTextureCoordArray();
        writeQuad(vertices, -64, 64, -48, 48, mirroredGeometry ? 0 : 512, mirroredGeometry);
        writeFullUvs(uvs);

        Software3DContext context = new Software3DContext();
        context.setOptScreenCenter(96, 72);
        context.setOptScreenScale(4096, 4096);
        context.setOptViewTransform(transform);
        context.setPrimitiveTextures(new SoftwareTexture[]{makeTexture()});
        context.setPrimitiveTexture(0);

        BufferedImage image = new BufferedImage(192, 144, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            context.renderOptPrimitivesRange(graphics, image, 0, 0, image.getWidth(), image.getHeight(), primitives, 0, 1, 0);
        } finally {
            graphics.dispose();
        }

        int topColor = image.getRGB(96, 48);
        int bottomColor = image.getRGB(96, 96);
        if (topColor != expectedTop || bottomColor != expectedBottom) {
            throw new IllegalStateException(String.format(
                    "Unexpected opt primitive orientation %s top=%08x bottom=%08x",
                    label,
                    topColor,
                    bottomColor
            ));
        }
        DemoLog.info(OptPrimitiveOrientationProbe.class, String.format(
                "%s top=%08x bottom=%08x",
                label,
                topColor,
                bottomColor
        ));
    }

    private static void assertUpright(String label, boolean mirroredGeometry) throws Exception {
        assertUpright(label, identity(), mirroredGeometry);
    }

    private static void writeQuad(int[] vertices, int left, int right, int top, int bottom, int z, boolean mirroredGeometry) {
        int effectiveTop = mirroredGeometry ? bottom : top;
        int effectiveBottom = mirroredGeometry ? top : bottom;
        vertices[0] = left;
        vertices[1] = effectiveTop;
        vertices[2] = z;
        vertices[3] = right;
        vertices[4] = effectiveTop;
        vertices[5] = z;
        vertices[6] = right;
        vertices[7] = effectiveBottom;
        vertices[8] = z;
        vertices[9] = left;
        vertices[10] = effectiveBottom;
        vertices[11] = z;
    }

    private static void writeFullUvs(int[] uvs) {
        uvs[0] = 0;
        uvs[1] = 0;
        uvs[2] = 255;
        uvs[3] = 0;
        uvs[4] = 255;
        uvs[5] = 255;
        uvs[6] = 0;
        uvs[7] = 255;
    }

    private static SoftwareTexture makeTexture() throws Exception {
        BufferedImage texture = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < texture.getHeight(); y++) {
            int color = y < 128 ? 0xFFFF0000 : 0xFF0000FF;
            for (int x = 0; x < texture.getWidth(); x++) {
                texture.setRGB(x, y, color);
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(texture, "png", output);
        return new SoftwareTexture(output.toByteArray(), true);
    }

    private static float[] identity() {
        return new float[]{
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f
        };
    }

    private static float[] translatedIdentity(float z) {
        return new float[]{
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, z,
                0f, 0f, 0f, 1f
        };
    }

}
