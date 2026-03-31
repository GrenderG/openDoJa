package opendoja.probes;

import opendoja.g3d.MascotFigure;
import opendoja.g3d.MbacModel;
import opendoja.g3d.Software3DContext;
import opendoja.g3d.SoftwareTexture;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

/**
 * Verifies the split exposed by live opt Figure titles: the default identity basis still uses the
 * native subtract-Y projection, while transformed view bases must not be flipped a second time.
 */
public final class OptFigureOrientationProbe {
    private OptFigureOrientationProbe() {
    }

    public static void main(String[] args) throws Exception {
        DemoLog.enableInfoLogging();

        assertOrientation("identity-basis", identity(), 48, 96, 0xFF0000FF, 0xFFFF0000);
        assertOrientation("transformed-basis", ridgeLikePositiveM11(), 64, 80, 0xFFFF0000, 0xFF0000FF);
    }

    private static void assertOrientation(String label, float[] transform, int topSampleY, int bottomSampleY,
                                          int expectedTop, int expectedBottom) throws Exception {
        MascotFigure figure = new MascotFigure(makeQuadModel());
        figure.setTexture(makeTexture());

        BufferedImage image = new BufferedImage(192, 144, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            Software3DContext context = new Software3DContext();
            context.setOptScreenCenter(96, 72);
            context.setOptPerspective(20, 8191, 603);
            context.setOptViewTransform(transform);
            context.renderOptFigure(graphics, image, 0, 0, image.getWidth(), image.getHeight(), figure);
        } finally {
            graphics.dispose();
        }

        int topColor = image.getRGB(96, topSampleY);
        int bottomColor = image.getRGB(96, bottomSampleY);
        if (topColor != expectedTop || bottomColor != expectedBottom) {
            throw new IllegalStateException(String.format(
                    "Unexpected opt figure orientation %s top=%08x bottom=%08x",
                    label,
                    topColor,
                    bottomColor
            ));
        }
        DemoLog.info(OptFigureOrientationProbe.class, String.format(
                "%s top=%08x bottom=%08x",
                label,
                topColor,
                bottomColor
        ));
    }

    private static MbacModel makeQuadModel() {
        int[] vertices = {
                -64, -48, 256,
                64, -48, 256,
                64, 48, 256,
                -64, 48, 256
        };
        MbacModel.Polygon polygon = new MbacModel.Polygon(
                new int[]{0, 1, 2, 3},
                new float[]{
                        0f, 0f,
                        255f, 0f,
                        255f, 255f,
                        0f, 255f
                },
                0xFFFFFFFF,
                0,
                0,
                0,
                true,
                false
        );
        return new MbacModel(vertices, vertices.clone(), new MbacModel.Polygon[]{polygon}, 1, new MbacModel.Bone[0]);
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

    private static float[] ridgeLikePositiveM11() {
        float sin = 0.70710677f;
        float cos = 0.70710677f;
        return new float[]{
                1f, 0f, 0f, 0f,
                0f, cos, -sin, 181f,
                0f, sin, cos, 256f,
                0f, 0f, 0f, 1f
        };
    }
}
