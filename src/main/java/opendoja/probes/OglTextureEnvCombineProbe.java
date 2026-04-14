package opendoja.probes;

import com.nttdocomo.ui.Graphics;
import com.nttdocomo.ui.Image;
import com.nttdocomo.ui.ogl.DirectBufferFactory;
import com.nttdocomo.ui.ogl.FloatBuffer;
import com.nttdocomo.ui.ogl.GraphicsOGL;

/**
 * Verifies that the software renderer applies GLES 1.1 texture environment combine state instead
 * of falling back to the old "MODULATE-or-white" shortcut.
 */
public final class OglTextureEnvCombineProbe {
    private OglTextureEnvCombineProbe() {
    }

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");

        Graphics graphics = (Graphics) Image.createImage(32, 32).getGraphics();
        GraphicsOGL ogl = (GraphicsOGL) graphics;
        DirectBufferFactory buffers = DirectBufferFactory.getFactory();
        FloatBuffer vertices = buffers.allocateFloatBuffer(new float[]{
                -0.7f, -0.7f, 0f,
                0.7f, -0.7f, 0f,
                0.0f, 0.7f, 0f
        });
        FloatBuffer texCoords = buffers.allocateFloatBuffer(new float[]{
                0f, 1f,
                1f, 1f,
                0.5f, 0f
        });

        int[] textureId = new int[1];
        ogl.beginDrawing();
        try {
            ogl.glClearColor(0f, 0f, 0f, 0f);
            ogl.glClear(GraphicsOGL.GL_COLOR_BUFFER_BIT | GraphicsOGL.GL_DEPTH_BUFFER_BIT);
            ogl.glGenTextures(1, textureId);
            ogl.glBindTexture(GraphicsOGL.GL_TEXTURE_2D, textureId[0]);
            ogl.glTexImage2D(GraphicsOGL.GL_TEXTURE_2D, 0, GraphicsOGL.GL_RGBA, 2, 2, 0,
                    GraphicsOGL.GL_RGBA, GraphicsOGL.GL_UNSIGNED_BYTE,
                    buffers.allocateByteBuffer(new byte[]{
                            (byte) 255, 0, 0, (byte) 255,
                            0, (byte) 255, 0, (byte) 255,
                            0, 0, (byte) 255, (byte) 255,
                            (byte) 255, (byte) 255, (byte) 255, (byte) 255
                    }));
            ogl.glEnable(GraphicsOGL.GL_TEXTURE_2D);
            ogl.glEnableClientState(GraphicsOGL.GL_VERTEX_ARRAY);
            ogl.glEnableClientState(GraphicsOGL.GL_TEXTURE_COORD_ARRAY);
            ogl.glVertexPointer(3, GraphicsOGL.GL_FLOAT, 0, vertices);
            ogl.glTexCoordPointer(2, GraphicsOGL.GL_FLOAT, 0, texCoords);
            ogl.glColor4ub((short) 255, (short) 0, (short) 0, (short) 255);
            ogl.glTexEnvi(GraphicsOGL.GL_TEXTURE_ENV, GraphicsOGL.GL_TEXTURE_ENV_MODE, GraphicsOGL.GL_COMBINE);
            ogl.glTexEnvi(GraphicsOGL.GL_TEXTURE_ENV, GraphicsOGL.GL_COMBINE_RGB, GraphicsOGL.GL_MODULATE);
            ogl.glTexEnvi(GraphicsOGL.GL_TEXTURE_ENV, GraphicsOGL.GL_SRC0_RGB, GraphicsOGL.GL_TEXTURE);
            ogl.glTexEnvi(GraphicsOGL.GL_TEXTURE_ENV, GraphicsOGL.GL_SRC1_RGB, GraphicsOGL.GL_PREVIOUS);
            ogl.glTexEnvi(GraphicsOGL.GL_TEXTURE_ENV, GraphicsOGL.GL_COMBINE_ALPHA, GraphicsOGL.GL_REPLACE);
            ogl.glTexEnvi(GraphicsOGL.GL_TEXTURE_ENV, GraphicsOGL.GL_SRC0_ALPHA, GraphicsOGL.GL_TEXTURE);
            ogl.glDrawArrays(GraphicsOGL.GL_TRIANGLES, 0, 3);
            check(ogl.glGetError() == GraphicsOGL.GL_NO_ERROR, "texture-env combine draw should not set a GL error");
            int center = graphics.getPixel(16, 16);
            int red = (center >>> 16) & 0xFF;
            int green = (center >>> 8) & 0xFF;
            int blue = center & 0xFF;
            check(red > green && red > blue, String.format("expected red-modulated textured fragment, got %08x", center));
        } finally {
            if (textureId[0] != 0) {
                ogl.glDeleteTextures(1, textureId);
            }
            ogl.endDrawing();
        }

        System.out.println("OGL texture-env combine probe OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
