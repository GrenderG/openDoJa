package opendoja.probes;

import com.nttdocomo.ui.Graphics;
import com.nttdocomo.ui.Image;
import com.nttdocomo.ui.ogl.DirectBufferFactory;
import com.nttdocomo.ui.ogl.FloatBuffer;
import com.nttdocomo.ui.ogl.GraphicsOGL;

/**
 * Verifies that disabling GL_TEXTURE_COORD_ARRAY stops the renderer from
 * dereferencing a stale texcoord pointer on later draws.
 */
public final class OglDisabledTexCoordArrayProbe {
    private OglDisabledTexCoordArrayProbe() {
    }

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");

        Graphics graphics = (Graphics) Image.createImage(32, 32).getGraphics();
        GraphicsOGL ogl = (GraphicsOGL) graphics;
        DirectBufferFactory buffers = DirectBufferFactory.getFactory();
        FloatBuffer vertices = buffers.allocateFloatBuffer(new float[]{
                -0.8f, -0.8f, 0f,
                0.8f, -0.8f, 0f,
                0.0f, 0.8f, 0f
        });
        // One vertex only: valid pointer object, invalid for the real draw if dereferenced after disable.
        FloatBuffer staleTexCoords = buffers.allocateFloatBuffer(new float[]{0f, 0f});

        ogl.beginDrawing();
        try {
            ogl.glEnableClientState(GraphicsOGL.GL_VERTEX_ARRAY);
            ogl.glVertexPointer(3, GraphicsOGL.GL_FLOAT, 0, vertices);
            ogl.glEnableClientState(GraphicsOGL.GL_TEXTURE_COORD_ARRAY);
            ogl.glTexCoordPointer(2, GraphicsOGL.GL_FLOAT, 0, staleTexCoords);
            ogl.glDisableClientState(GraphicsOGL.GL_TEXTURE_COORD_ARRAY);
            ogl.glColor4ub((short) 255, (short) 255, (short) 255, (short) 255);
            ogl.glDrawArrays(GraphicsOGL.GL_TRIANGLES, 0, 3);
            check(ogl.glGetError() == GraphicsOGL.GL_NO_ERROR,
                    "disabled texcoord array should not be dereferenced on draw");
            check(graphics.getPixel(16, 16) != 0, "triangle should still render");
        } finally {
            ogl.endDrawing();
        }

        System.out.println("OGL disabled texcoord-array probe OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
