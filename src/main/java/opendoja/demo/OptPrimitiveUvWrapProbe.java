package opendoja.demo;

import com.nttdocomo.opt.ui.j3d.Graphics3D;
import com.nttdocomo.opt.ui.j3d.PrimitiveArray;
import opendoja.g3d.Software3DContext;
import opendoja.g3d.SoftwareTexture;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

/**
 * Verifies that opt.ui.j3d primitive texture coordinates follow the original
 * unsigned-byte domain instead of clamping signed int values at zero.
 */
public final class OptPrimitiveUvWrapProbe {
    private OptPrimitiveUvWrapProbe() {
    }

    public static void main(String[] args) throws Exception {
        DemoLog.enableInfoLogging();
        PrimitiveArray primitives = new PrimitiveArray(Graphics3D.PRIMITIVE_QUADS, Graphics3D.TEXTURE_COORD_PER_VERTEX, 2);
        writeQuad(primitives.getVertexArray(), 0, -60, -10, -20, 20);
        writeQuad(primitives.getVertexArray(), 12, 10, 60, -20, 20);

        int[] uvs = primitives.getTextureCoordArray();
        // -1 must wrap to 255 on the opt primitive path.
        writeConstantColumnUvs(uvs, 0, -1);
        // 256 must wrap to 0 on the opt primitive path.
        writeConstantColumnUvs(uvs, 8, 256);

        Software3DContext context = new Software3DContext();
        context.setOptScreenCenter(80, 48);
        context.setOptScreenScale(4096, 4096);
        context.setPrimitiveTextures(new SoftwareTexture[]{makeTexture()});
        context.setPrimitiveTexture(0);

        BufferedImage image = new BufferedImage(160, 96, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            context.renderOptPrimitivesRange(graphics, image, 0, 0, image.getWidth(), image.getHeight(), primitives, 0, 2, 0);
        } finally {
            graphics.dispose();
        }

        int wrapped255 = image.getRGB(35, 48);
        int wrapped0 = image.getRGB(125, 48);
        if (wrapped255 != 0xFF0000FF || wrapped0 != 0xFF00FF00) {
            throw new IllegalStateException(String.format(
                    "Unexpected wrapped colors left=%08x right=%08x",
                    wrapped255,
                    wrapped0
            ));
        }
        DemoLog.info(OptPrimitiveUvWrapProbe.class, String.format(
                "opt-primitive-uv-wrap left=%08x right=%08x",
                wrapped255,
                wrapped0));
    }

    private static void writeQuad(int[] vertices, int offset, int left, int right, int top, int bottom) {
        vertices[offset] = left;
        vertices[offset + 1] = top;
        vertices[offset + 2] = 256;
        vertices[offset + 3] = right;
        vertices[offset + 4] = top;
        vertices[offset + 5] = 256;
        vertices[offset + 6] = right;
        vertices[offset + 7] = bottom;
        vertices[offset + 8] = 256;
        vertices[offset + 9] = left;
        vertices[offset + 10] = bottom;
        vertices[offset + 11] = 256;
    }

    private static void writeConstantColumnUvs(int[] uvs, int offset, int u) {
        uvs[offset] = u;
        uvs[offset + 1] = 0;
        uvs[offset + 2] = u;
        uvs[offset + 3] = 0;
        uvs[offset + 4] = u;
        uvs[offset + 5] = 255;
        uvs[offset + 6] = u;
        uvs[offset + 7] = 255;
    }

    private static SoftwareTexture makeTexture() throws Exception {
        BufferedImage texture = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < texture.getHeight(); y++) {
            for (int x = 0; x < texture.getWidth(); x++) {
                texture.setRGB(x, y, 0xFFFF0000);
            }
        }
        for (int y = 0; y < texture.getHeight(); y++) {
            texture.setRGB(0, y, 0xFF00FF00);
            texture.setRGB(255, y, 0xFF0000FF);
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(texture, "png", output);
        return new SoftwareTexture(output.toByteArray(), true);
    }
}
