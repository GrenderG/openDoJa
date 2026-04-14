package opendoja.probes;

import com.nttdocomo.ui.Graphics;
import com.nttdocomo.ui.Image;
import com.nttdocomo.ui.ogl.DirectBufferFactory;
import com.nttdocomo.ui.ogl.FloatBuffer;
import com.nttdocomo.ui.ogl.GraphicsOGL;

/**
 * Verifies that the software renderer applies GLES 1.1 fixed-function lighting,
 * material parameters, and color-material tracking instead of falling back to the current color.
 */
public final class OglLightingMaterialProbe {
    private OglLightingMaterialProbe() {
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

        ogl.beginDrawing();
        try {
            ogl.glEnableClientState(GraphicsOGL.GL_VERTEX_ARRAY);
            ogl.glVertexPointer(3, GraphicsOGL.GL_FLOAT, 0, vertices);
            ogl.glNormal3f(0f, 0f, 1f);
            ogl.glEnable(GraphicsOGL.GL_LIGHTING);
            ogl.glEnable(GraphicsOGL.GL_LIGHT0);

            // Material parameters must drive lighting when color material is disabled.
            ogl.glClearColor(0f, 0f, 0f, 0f);
            ogl.glClear(GraphicsOGL.GL_COLOR_BUFFER_BIT | GraphicsOGL.GL_DEPTH_BUFFER_BIT);
            ogl.glColor4ub((short) 0, (short) 0, (short) 255, (short) 255);
            ogl.glMaterialfv(GraphicsOGL.GL_FRONT_AND_BACK, GraphicsOGL.GL_AMBIENT, new float[]{0f, 0f, 0f, 1f});
            ogl.glMaterialfv(GraphicsOGL.GL_FRONT_AND_BACK, GraphicsOGL.GL_DIFFUSE, new float[]{0f, 1f, 0f, 1f});
            ogl.glMaterialfv(GraphicsOGL.GL_FRONT_AND_BACK, GraphicsOGL.GL_SPECULAR, new float[]{0f, 0f, 0f, 1f});
            ogl.glMaterialfv(GraphicsOGL.GL_FRONT_AND_BACK, GraphicsOGL.GL_EMISSION, new float[]{0f, 0f, 0f, 1f});
            ogl.glDrawArrays(GraphicsOGL.GL_TRIANGLES, 0, 3);
            assertDominant(graphics.getPixel(16, 16), 1, "material-lit fragment should be green");

            // With color material enabled, ambient+diffuse must track the current color.
            ogl.glClear(GraphicsOGL.GL_COLOR_BUFFER_BIT | GraphicsOGL.GL_DEPTH_BUFFER_BIT);
            ogl.glMaterialfv(GraphicsOGL.GL_FRONT_AND_BACK, GraphicsOGL.GL_AMBIENT_AND_DIFFUSE, new float[]{0f, 0f, 1f, 1f});
            ogl.glEnable(GraphicsOGL.GL_COLOR_MATERIAL);
            ogl.glColor4ub((short) 255, (short) 0, (short) 0, (short) 255);
            ogl.glDrawArrays(GraphicsOGL.GL_TRIANGLES, 0, 3);
            assertDominant(graphics.getPixel(16, 16), 0, "color-material tracked fragment should be red");

            check(ogl.glGetError() == GraphicsOGL.GL_NO_ERROR, "lighting/material probe should not set a GL error");
        } finally {
            ogl.endDrawing();
        }

        System.out.println("OGL lighting/material probe OK");
    }

    private static void assertDominant(int color, int channel, String message) {
        int red = (color >>> 16) & 0xFF;
        int green = (color >>> 8) & 0xFF;
        int blue = color & 0xFF;
        boolean ok = switch (channel) {
            case 0 -> red > green && red > blue;
            case 1 -> green > red && green > blue;
            case 2 -> blue > red && blue > green;
            default -> false;
        };
        check(ok, String.format("%s: got %08x", message, color));
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
