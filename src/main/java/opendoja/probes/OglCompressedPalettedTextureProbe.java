package opendoja.probes;

import com.nttdocomo.ui.Graphics;
import com.nttdocomo.ui.Image;
import com.nttdocomo.ui.ogl.ByteBuffer;
import com.nttdocomo.ui.ogl.DirectBufferFactory;
import com.nttdocomo.ui.ogl.FloatBuffer;
import com.nttdocomo.ui.ogl.GraphicsOGL;

/**
 * Verifies GLES 1.1 OES compressed paletted textures decode with the correct palette entry size,
 * pixel type, and nibble ordering for both PALETTE4 and PALETTE8 uploads.
 */
public final class OglCompressedPalettedTextureProbe {
    private OglCompressedPalettedTextureProbe() {
    }

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");

        Graphics graphics = (Graphics) Image.createImage(32, 16).getGraphics();
        GraphicsOGL ogl = (GraphicsOGL) graphics;
        DirectBufferFactory buffers = DirectBufferFactory.getFactory();
        FloatBuffer vertices = buffers.allocateFloatBuffer(new float[]{
                -1f, -1f, 0f,
                1f, -1f, 0f,
                -1f, 1f, 0f,
                -1f, 1f, 0f,
                1f, -1f, 0f,
                1f, 1f, 0f
        });
        FloatBuffer texCoords = buffers.allocateFloatBuffer(new float[]{
                0f, 1f,
                1f, 1f,
                0f, 0f,
                0f, 0f,
                1f, 1f,
                1f, 0f
        });
        int[] textureId = new int[1];
        int[] formats = {
                GraphicsOGL.GL_PALETTE4_RGB8_OES,
                GraphicsOGL.GL_PALETTE4_RGBA8_OES,
                GraphicsOGL.GL_PALETTE4_R5_G6_B5_OES,
                GraphicsOGL.GL_PALETTE4_RGBA4_OES,
                GraphicsOGL.GL_PALETTE4_RGB5_A1_OES,
                GraphicsOGL.GL_PALETTE8_RGB8_OES,
                GraphicsOGL.GL_PALETTE8_RGBA8_OES,
                GraphicsOGL.GL_PALETTE8_R5_G6_B5_OES,
                GraphicsOGL.GL_PALETTE8_RGBA4_OES,
                GraphicsOGL.GL_PALETTE8_RGB5_A1_OES
        };

        ogl.beginDrawing();
        try {
            ogl.glEnable(GraphicsOGL.GL_TEXTURE_2D);
            ogl.glEnableClientState(GraphicsOGL.GL_VERTEX_ARRAY);
            ogl.glEnableClientState(GraphicsOGL.GL_TEXTURE_COORD_ARRAY);
            ogl.glVertexPointer(3, GraphicsOGL.GL_FLOAT, 0, vertices);
            ogl.glTexCoordPointer(2, GraphicsOGL.GL_FLOAT, 0, texCoords);
            ogl.glColor4f(1f, 1f, 1f, 1f);
            ogl.glGenTextures(1, textureId);
            for (int format : formats) {
                ogl.glClearColor(0f, 0f, 0f, 0f);
                ogl.glClear(GraphicsOGL.GL_COLOR_BUFFER_BIT | GraphicsOGL.GL_DEPTH_BUFFER_BIT);
                ogl.glBindTexture(GraphicsOGL.GL_TEXTURE_2D, textureId[0]);
                ByteBuffer raw = buildCompressedTexture(buffers, format);
                ogl.glCompressedTexImage2D(GraphicsOGL.GL_TEXTURE_2D, 0, format, 2, 1, 0, raw);
                check(ogl.glGetError() == GraphicsOGL.GL_NO_ERROR, "compressed upload failed for format " + format);
                ogl.glDrawArrays(GraphicsOGL.GL_TRIANGLES, 0, 6);
                check(ogl.glGetError() == GraphicsOGL.GL_NO_ERROR, "compressed draw failed for format " + format);
                int left = graphics.getPixel(8, 8);
                int right = graphics.getPixel(24, 8);
                check(isRedDominant(left), String.format("expected left texel to stay red for %d, got %08x", format, left));
                check(isGreenDominant(right), String.format("expected right texel to stay green for %d, got %08x", format, right));
            }
        } finally {
            if (textureId[0] != 0) {
                ogl.glDeleteTextures(1, textureId);
            }
            ogl.endDrawing();
        }

        System.out.println("OGL compressed paletted texture probe OK");
    }

    private static ByteBuffer buildCompressedTexture(DirectBufferFactory buffers, int format) {
        byte[] palette;
        byte[] indices;
        switch (format) {
            case GraphicsOGL.GL_PALETTE4_RGB8_OES -> {
                palette = new byte[16 * 3];
                putRgb8(palette, 0, 255, 0, 0);
                putRgb8(palette, 3, 0, 255, 0);
                indices = new byte[]{0x01};
            }
            case GraphicsOGL.GL_PALETTE4_RGBA8_OES -> {
                palette = new byte[16 * 4];
                putRgba8(palette, 0, 255, 0, 0, 255);
                putRgba8(palette, 4, 0, 255, 0, 255);
                indices = new byte[]{0x01};
            }
            case GraphicsOGL.GL_PALETTE4_R5_G6_B5_OES -> {
                palette = new byte[16 * 2];
                putPacked16(palette, 0, packRgb565(255, 0, 0));
                putPacked16(palette, 2, packRgb565(0, 255, 0));
                indices = new byte[]{0x01};
            }
            case GraphicsOGL.GL_PALETTE4_RGBA4_OES -> {
                palette = new byte[16 * 2];
                putPacked16(palette, 0, packRgba4444(255, 0, 0, 255));
                putPacked16(palette, 2, packRgba4444(0, 255, 0, 255));
                indices = new byte[]{0x01};
            }
            case GraphicsOGL.GL_PALETTE4_RGB5_A1_OES -> {
                palette = new byte[16 * 2];
                putPacked16(palette, 0, packRgb5a1(255, 0, 0, 255));
                putPacked16(palette, 2, packRgb5a1(0, 255, 0, 255));
                indices = new byte[]{0x01};
            }
            case GraphicsOGL.GL_PALETTE8_RGB8_OES -> {
                palette = new byte[256 * 3];
                putRgb8(palette, 0, 255, 0, 0);
                putRgb8(palette, 3, 0, 255, 0);
                indices = new byte[]{0x00, 0x01};
            }
            case GraphicsOGL.GL_PALETTE8_RGBA8_OES -> {
                palette = new byte[256 * 4];
                putRgba8(palette, 0, 255, 0, 0, 255);
                putRgba8(palette, 4, 0, 255, 0, 255);
                indices = new byte[]{0x00, 0x01};
            }
            case GraphicsOGL.GL_PALETTE8_R5_G6_B5_OES -> {
                palette = new byte[256 * 2];
                putPacked16(palette, 0, packRgb565(255, 0, 0));
                putPacked16(palette, 2, packRgb565(0, 255, 0));
                indices = new byte[]{0x00, 0x01};
            }
            case GraphicsOGL.GL_PALETTE8_RGBA4_OES -> {
                palette = new byte[256 * 2];
                putPacked16(palette, 0, packRgba4444(255, 0, 0, 255));
                putPacked16(palette, 2, packRgba4444(0, 255, 0, 255));
                indices = new byte[]{0x00, 0x01};
            }
            case GraphicsOGL.GL_PALETTE8_RGB5_A1_OES -> {
                palette = new byte[256 * 2];
                putPacked16(palette, 0, packRgb5a1(255, 0, 0, 255));
                putPacked16(palette, 2, packRgb5a1(0, 255, 0, 255));
                indices = new byte[]{0x00, 0x01};
            }
            default -> throw new IllegalArgumentException("Unsupported format " + format);
        }
        ByteBuffer buffer = buffers.allocateByteBuffer(palette.length + indices.length);
        buffer.put(0, palette);
        buffer.put(palette.length, indices);
        return buffer;
    }

    private static boolean isRedDominant(int color) {
        int red = (color >>> 16) & 0xFF;
        int green = (color >>> 8) & 0xFF;
        int blue = color & 0xFF;
        return red > green + 32 && red > blue + 32;
    }

    private static boolean isGreenDominant(int color) {
        int red = (color >>> 16) & 0xFF;
        int green = (color >>> 8) & 0xFF;
        int blue = color & 0xFF;
        return green > red + 32 && green > blue + 32;
    }

    private static void putRgb8(byte[] target, int offset, int red, int green, int blue) {
        target[offset] = (byte) red;
        target[offset + 1] = (byte) green;
        target[offset + 2] = (byte) blue;
    }

    private static void putRgba8(byte[] target, int offset, int red, int green, int blue, int alpha) {
        target[offset] = (byte) red;
        target[offset + 1] = (byte) green;
        target[offset + 2] = (byte) blue;
        target[offset + 3] = (byte) alpha;
    }

    private static void putPacked16(byte[] target, int offset, int value) {
        target[offset] = (byte) value;
        target[offset + 1] = (byte) (value >>> 8);
    }

    private static int packRgb565(int red, int green, int blue) {
        return ((red * 31 / 255) << 11) | ((green * 63 / 255) << 5) | (blue * 31 / 255);
    }

    private static int packRgba4444(int red, int green, int blue, int alpha) {
        return ((red * 15 / 255) << 12)
                | ((green * 15 / 255) << 8)
                | ((blue * 15 / 255) << 4)
                | (alpha * 15 / 255);
    }

    private static int packRgb5a1(int red, int green, int blue, int alpha) {
        return ((red * 31 / 255) << 11)
                | ((green * 31 / 255) << 6)
                | ((blue * 31 / 255) << 1)
                | (alpha >= 128 ? 1 : 0);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
