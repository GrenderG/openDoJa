package opendoja.probes;

import com.nttdocomo.opt.ui.j3d.Graphics3D;
import com.nttdocomo.opt.ui.j3d.Texture;
import com.nttdocomo.ui.Graphics;
import com.nttdocomo.ui.Image;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Verifies that opt.ui.j3d command lists decode into the same textured quad render
 * for both the documented packed v1 header and the older literal `1` header variant.
 */
public final class OptCommandListProbe {
    private OptCommandListProbe() {
    }

    public static void main(String[] args) throws Exception {
        DemoLog.enableInfoLogging();
        int[] documented = render(Graphics3D.COMMAND_LIST_VERSION_1);
        int[] legacy = render(1);

        if (!Arrays.equals(documented, legacy)) {
            throw new IllegalStateException("Legacy and packed command-list headers produced different output");
        }
        verifyCommandScaleOverridesPreviousScreenView();
        verifySmallFixedPointScaleAndUnsetTransformTable();

        Set<Integer> unique = new HashSet<>();
        int opaquePixels = 0;
        for (int pixel : documented) {
            if ((pixel & 0x00FFFFFF) == 0) {
                continue;
            }
            opaquePixels++;
            unique.add(pixel);
        }
        if (opaquePixels == 0 || unique.size() < 3) {
            throw new IllegalStateException("Expected textured command-list output, got opaquePixels="
                    + opaquePixels + " uniqueColors=" + unique.size());
        }
        DemoLog.info(OptCommandListProbe.class, "Opt command-list probe OK opaquePixels="
                + opaquePixels + " uniqueColors=" + unique.size());
    }

    private static void verifyCommandScaleOverridesPreviousScreenView() throws Exception {
        Image image = Image.createImage(64, 64);
        Graphics graphics = image.getGraphics();
        graphics.setColor(Graphics.getColorOfRGB(0, 0, 0));
        graphics.fillRect(0, 0, 64, 64);
        graphics.setPrimitiveTextureArray(new Texture(makeTexture(), true));
        graphics.setScreenView(1, 1);
        graphics.executeCommandList(buildScaledCommandList());

        int[] pixels = graphics.getRGBPixels(0, 0, 64, 64, null, 0);
        int visiblePixels = 0;
        for (int pixel : pixels) {
            if ((pixel & 0x00FFFFFF) != 0) {
                visiblePixels++;
            }
        }
        if (visiblePixels == 0) {
            throw new IllegalStateException("COMMAND_SCREEN_SCALE did not override the previous screen-view projection");
        }
    }

    private static void verifySmallFixedPointScaleAndUnsetTransformTable() throws Exception {
        Image image = Image.createImage(64, 64);
        Graphics graphics = image.getGraphics();
        graphics.setPrimitiveTextureArray(new Texture(makeTexture(), false));
        graphics.executeCommandList(new int[]{
                1,
                Graphics3D.COMMAND_SCREEN_CENTER, 0, 0,
                Graphics3D.COMMAND_SCREEN_SCALE, 1, 1,
                Graphics3D.COMMAND_VIEW_TRANS | 2,
                Graphics3D.COMMAND_TEXTURE,
                Graphics3D.COMMAND_RENDER_QUADS | Graphics3D.TEXTURE_COORD_PER_VERTEX | (1 << 16),
                0, 0, 0, 64 << 12, 0, 0, 64 << 12, 64 << 12, 0, 0, 64 << 12, 0,
                0, 0, 255, 0, 255, 255, 0, 255,
                Graphics3D.COMMAND_FLUSH, Graphics3D.COMMAND_END
        });
        int[] pixels = graphics.getRGBPixels(0, 0, 64, 64, null, 0);
        checkRgb(pixels[16 * 64 + 16], 0xFF0000);
        checkRgb(pixels[16 * 64 + 48], 0x00FF00);
        checkRgb(pixels[48 * 64 + 16], 0x0000FF);
        checkRgb(pixels[48 * 64 + 48], 0xFFFFFF);
    }

    private static void checkRgb(int pixel, int expected) {
        if ((pixel & 0xFFFFFF) != expected) {
            throw new IllegalStateException("Small fixed-point scale produced "
                    + Integer.toHexString(pixel) + " instead of " + Integer.toHexString(expected));
        }
    }

    private static int[] render(int version) throws Exception {
        Image image = Image.createImage(64, 64);
        Graphics graphics = image.getGraphics();
        graphics.setColor(Graphics.getColorOfRGB(0, 0, 0));
        graphics.fillRect(0, 0, 64, 64);
        graphics.setPrimitiveTextureArray(new Texture(makeTexture(), true));
        graphics.enableLight(true);
        graphics.executeCommandList(buildCommandList(version));
        return graphics.getRGBPixels(0, 0, 64, 64, null, 0);
    }

    private static int[] buildCommandList(int version) {
        return new int[]{
                version,
                Graphics3D.COMMAND_SCREEN_CENTER, 32, 32,
                Graphics3D.COMMAND_PERSPECTIVE1, 20, 8191, 603,
                Graphics3D.COMMAND_TEXTURE,
                Graphics3D.COMMAND_RENDER_QUADS
                        | Graphics3D.NORMAL_PER_VERTEX
                        | Graphics3D.TEXTURE_COORD_PER_VERTEX
                        | Graphics3D.ATTR_LIGHT
                        | (1 << 16),
                -16, 16, 256,
                16, 16, 256,
                16, -16, 256,
                -16, -16, 256,
                0, 0, 4096,
                0, 0, 4096,
                0, 0, 4096,
                0, 0, 4096,
                0, 0,
                255, 0,
                255, 255,
                0, 255,
                Graphics3D.COMMAND_FLUSH,
                Graphics3D.COMMAND_END
        };
    }

    private static int[] buildScaledCommandList() {
        return new int[]{
                Graphics3D.COMMAND_LIST_VERSION_1,
                Graphics3D.COMMAND_SCREEN_CENTER, 32, 32,
                Graphics3D.COMMAND_SCREEN_SCALE, 4096, 4096,
                Graphics3D.COMMAND_TEXTURE,
                Graphics3D.COMMAND_RENDER_QUADS
                        | Graphics3D.TEXTURE_COORD_PER_VERTEX
                        | (1 << 16),
                -16, -16, 0,
                16, -16, 0,
                16, 16, 0,
                -16, 16, 0,
                0, 0,
                255, 0,
                255, 255,
                0, 255,
                Graphics3D.COMMAND_FLUSH,
                Graphics3D.COMMAND_END
        };
    }

    private static byte[] makeTexture() throws Exception {
        java.awt.image.BufferedImage texture = new java.awt.image.BufferedImage(256, 256, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < texture.getHeight(); y++) {
            for (int x = 0; x < texture.getWidth(); x++) {
                int color;
                if (x < 128 && y < 128) {
                    color = 0xFFFF0000;
                } else if (x >= 128 && y < 128) {
                    color = 0xFF00FF00;
                } else if (x < 128) {
                    color = 0xFF0000FF;
                } else {
                    color = 0xFFFFFFFF;
                }
                texture.setRGB(x, y, color);
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(texture, "png", output);
        return output.toByteArray();
    }
}
