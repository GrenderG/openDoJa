package opendoja.probes;

import com.nttdocomo.ui.Graphics;
import com.nttdocomo.ui.Image;
import com.nttdocomo.ui.ogl.DirectBufferFactory;
import com.nttdocomo.ui.ogl.FloatBuffer;
import com.nttdocomo.ui.ogl.GraphicsOGL;

/**
 * Verifies GLES 1.1 per-component blend factors used by fixed-function draw paths.
 */
public final class OglBlendFactorProbe {
    private OglBlendFactorProbe() {
    }

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");

        Graphics graphics = (Graphics) Image.createImage(32, 32).getGraphics();
        GraphicsOGL ogl = (GraphicsOGL) graphics;
        DirectBufferFactory buffers = DirectBufferFactory.getFactory();
        FloatBuffer triangle = buffers.allocateFloatBuffer(new float[]{
                -0.8f, -0.8f, 0f,
                0.8f, -0.8f, 0f,
                0f, 0.8f, 0f
        });

        ogl.beginDrawing();
        try {
            ogl.glViewport(0, 0, 32, 32);
            ogl.glEnableClientState(GraphicsOGL.GL_VERTEX_ARRAY);
            ogl.glVertexPointer(3, GraphicsOGL.GL_FLOAT, 0, triangle);

            ogl.glClearColor(1f, 1f, 1f, 1f);
            ogl.glClear(GraphicsOGL.GL_COLOR_BUFFER_BIT | GraphicsOGL.GL_DEPTH_BUFFER_BIT);
            ogl.glEnable(GraphicsOGL.GL_BLEND);
            ogl.glColor4f(1f, 0f, 0f, 1f);
            ogl.glBlendFunc(GraphicsOGL.GL_ZERO, GraphicsOGL.GL_SRC_COLOR);
            check(ogl.glGetError() == GraphicsOGL.GL_NO_ERROR, "GL_ZERO/GL_SRC_COLOR should be accepted");
            ogl.glDrawArrays(GraphicsOGL.GL_TRIANGLES, 0, 3);
            int redResult = graphics.getPixel(16, 16);
            check(component(redResult, 16) > 200 && component(redResult, 8) < 32 && component(redResult, 0) < 32,
                    String.format("expected multiply-style red result, got %08x", redResult));

            ogl.glClearColor(0f, 0f, 1f, 1f);
            ogl.glClear(GraphicsOGL.GL_COLOR_BUFFER_BIT | GraphicsOGL.GL_DEPTH_BUFFER_BIT);
            ogl.glColor4f(1f, 1f, 1f, 1f);
            ogl.glBlendFunc(GraphicsOGL.GL_ONE_MINUS_DST_COLOR, GraphicsOGL.GL_ZERO);
            check(ogl.glGetError() == GraphicsOGL.GL_NO_ERROR, "GL_ONE_MINUS_DST_COLOR/GL_ZERO should be accepted");
            ogl.glDrawArrays(GraphicsOGL.GL_TRIANGLES, 0, 3);
            int yellowResult = graphics.getPixel(16, 16);
            check(component(yellowResult, 16) > 200 && component(yellowResult, 8) > 200 && component(yellowResult, 0) < 32,
                    String.format("expected inverse-destination-color yellow result, got %08x", yellowResult));

            ogl.glBlendFunc(GraphicsOGL.GL_ONE, GraphicsOGL.GL_DST_COLOR);
            check(ogl.glGetError() == GraphicsOGL.GL_INVALID_ENUM, "GL_DST_COLOR must be rejected as a destination factor");
        } finally {
            ogl.endDrawing();
        }

        System.out.println("OGL blend-factor probe OK");
    }

    private static int component(int color, int shift) {
        return (color >>> shift) & 0xFF;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
