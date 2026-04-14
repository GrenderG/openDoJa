package opendoja.probes;

import com.nttdocomo.ui.Graphics;
import com.nttdocomo.ui.Image;
import com.nttdocomo.ui.ogl.DirectBufferFactory;
import com.nttdocomo.ui.ogl.FloatBuffer;
import com.nttdocomo.ui.ogl.GraphicsOGL;

/**
 * Verifies that glViewport uses GLES lower-left viewport coordinates instead of
 * projecting directly against the whole surface.
 */
public final class OglViewportProbe {
    private OglViewportProbe() {
    }

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");

        Graphics graphics = (Graphics) Image.createImage(32, 32).getGraphics();
        GraphicsOGL ogl = (GraphicsOGL) graphics;
        DirectBufferFactory buffers = DirectBufferFactory.getFactory();
        FloatBuffer vertices = buffers.allocateFloatBuffer(new float[]{
                -1f, -1f, 0f,
                1f, -1f, 0f,
                0f, 1f, 0f
        });

        ogl.beginDrawing();
        try {
            ogl.glClearColor(0f, 0f, 0f, 0f);
            ogl.glClear(GraphicsOGL.GL_COLOR_BUFFER_BIT | GraphicsOGL.GL_DEPTH_BUFFER_BIT);
            ogl.glEnableClientState(GraphicsOGL.GL_VERTEX_ARRAY);
            ogl.glVertexPointer(3, GraphicsOGL.GL_FLOAT, 0, vertices);
            ogl.glColor4ub((short) 255, (short) 255, (short) 255, (short) 255);
            ogl.glViewport(8, 8, 16, 16);
            ogl.glDrawArrays(GraphicsOGL.GL_TRIANGLES, 0, 3);
            check(graphics.getPixel(16, 16) != 0, "viewport center should be drawn");
            check(graphics.getPixel(4, 4) == 0, "outside viewport should remain clear");
            check(graphics.getPixel(16, 10) != 0, "lower-left GL viewport should map into upper AWT rows");
        } finally {
            ogl.endDrawing();
        }

        System.out.println("OGL viewport probe OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
