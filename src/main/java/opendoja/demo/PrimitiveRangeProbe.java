package opendoja.demo;

import com.nttdocomo.opt.ui.j3d.Graphics3D;
import com.nttdocomo.opt.ui.j3d.PrimitiveArray;
import opendoja.g3d.Software3DContext;
import opendoja.g3d.SoftwareTexture;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Verifies that the three-int DoJa primitive overload renders a primitive subrange
 * and that color-key flags come from the attr word rather than PrimitiveArray param.
 */
public final class PrimitiveRangeProbe {
    private PrimitiveRangeProbe() {
    }

    public static void main(String[] args) throws Exception {
        DemoLog.enableInfoLogging();
        Path outputDir = args.length == 0 ? Path.of("/tmp/opendoja-captures") : Path.of(args[0]);
        Files.createDirectories(outputDir);

        PrimitiveArray primitives = new PrimitiveArray(Graphics3D.PRIMITIVE_QUADS, Graphics3D.TEXTURE_COORD_PER_VERTEX, 2);
        int[] vertices = primitives.getVertexArray();
        int[] uvs = primitives.getTextureCoordArray();

        // Left quad.
        writeQuad(vertices, 0, -40, 32, 0, -8);
        writeQuadUvs(uvs, 0);
        // Right quad.
        writeQuad(vertices, 12, 8, 80, 0, -8);
        writeQuadUvs(uvs, 8);

        Software3DContext context = new Software3DContext();
        context.setOptScreenCenter(48, 32);
        context.setOptScreenScale(4096, 4096);
        context.setPrimitiveTextures(new SoftwareTexture[]{makeTexture()});
        context.setPrimitiveTexture(0);

        renderSlice(outputDir.resolve("primitive-range-left.png"), context, primitives, 0, 1, 0);
        renderSlice(outputDir.resolve("primitive-range-right.png"), context, primitives, 1, 1, Graphics3D.ATTR_COLOR_KEY);
    }

    private static void renderSlice(Path output, Software3DContext context, PrimitiveArray primitives,
                                    int start, int count, int attr) throws Exception {
        BufferedImage image = new BufferedImage(96, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            context.setUiParallelView(96, 64);
            context.renderOptPrimitivesRange(graphics, image, 0, 0, image.getWidth(), image.getHeight(), primitives, start, count, attr);
        } finally {
            graphics.dispose();
        }
        ImageIO.write(image, "png", output.toFile());
        DemoLog.info(PrimitiveRangeProbe.class, () -> output.toAbsolutePath().toString());
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

    private static void writeQuadUvs(int[] uvs, int offset) {
        uvs[offset] = 0;
        uvs[offset + 1] = 0;
        uvs[offset + 2] = 1;
        uvs[offset + 3] = 0;
        uvs[offset + 4] = 1;
        uvs[offset + 5] = 1;
        uvs[offset + 6] = 0;
        uvs[offset + 7] = 1;
    }

    private static SoftwareTexture makeTexture() throws Exception {
        BufferedImage texture = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        texture.setRGB(0, 0, 0xFF000000);
        texture.setRGB(1, 0, 0xFFFF0000);
        texture.setRGB(0, 1, 0xFF00FF00);
        texture.setRGB(1, 1, 0xFF0000FF);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(texture, "png", output);
        return new SoftwareTexture(output.toByteArray(), true);
    }
}
