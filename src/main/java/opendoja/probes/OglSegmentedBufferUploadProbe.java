package opendoja.probes;

import com.nttdocomo.ui.Graphics;
import com.nttdocomo.ui.Image;
import com.nttdocomo.ui.ogl.DirectBufferFactory;
import com.nttdocomo.ui.ogl.FloatBuffer;
import com.nttdocomo.ui.ogl.GraphicsOGL;
import com.nttdocomo.ui.ogl.ShortBuffer;

/**
 * Verifies that VBO uploads honor the active DirectBuffer segment rather than the full backing array.
 */
public final class OglSegmentedBufferUploadProbe {
    private OglSegmentedBufferUploadProbe() {
    }

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");

        Graphics graphics = (Graphics) Image.createImage(64, 64).getGraphics();
        GraphicsOGL ogl = (GraphicsOGL) graphics;
        DirectBufferFactory buffers = DirectBufferFactory.getFactory();

        FloatBuffer vertices = buffers.allocateFloatBuffer(new float[]{
                3f, 3f, 0f,
                4f, 3f, 0f,
                3f, 4f, 0f,
                -0.4f, -0.4f, 0f,
                0f, 0.4f, 0f,
                0.4f, -0.4f, 0f
        });
        vertices.setSegment(9, 9);

        ShortBuffer indices = buffers.allocateShortBuffer(new short[]{
                7, 7, 7,
                0, 1, 2
        });
        indices.setSegment(3, 3);

        int[] vbo = new int[1];
        int[] ebo = new int[1];

        ogl.beginDrawing();
        try {
            ogl.glClearColor(0f, 0f, 0f, 0f);
            ogl.glClear(GraphicsOGL.GL_COLOR_BUFFER_BIT | GraphicsOGL.GL_DEPTH_BUFFER_BIT);
            ogl.glColor4ub((short) 255, (short) 255, (short) 255, (short) 255);
            ogl.glEnableClientState(GraphicsOGL.GL_VERTEX_ARRAY);
            ogl.glGenBuffers(vbo);
            ogl.glGenBuffers(ebo);
            ogl.glBindBuffer(GraphicsOGL.GL_ARRAY_BUFFER, vbo[0]);
            ogl.glBufferData(GraphicsOGL.GL_ARRAY_BUFFER, vertices, GraphicsOGL.GL_STATIC_DRAW);
            ogl.glBindBuffer(GraphicsOGL.GL_ELEMENT_ARRAY_BUFFER, ebo[0]);
            ogl.glBufferData(GraphicsOGL.GL_ELEMENT_ARRAY_BUFFER, indices, GraphicsOGL.GL_STATIC_DRAW);
            ogl.glVertexPointer(3, GraphicsOGL.GL_FLOAT, 0, 0);
            ogl.glDrawElements(GraphicsOGL.GL_TRIANGLES, 3, GraphicsOGL.GL_UNSIGNED_SHORT, 0);

            if (ogl.glGetError() != GraphicsOGL.GL_NO_ERROR) {
                throw new IllegalStateException("unexpected GL error");
            }
            if (graphics.getPixel(32, 30) == 0) {
                throw new IllegalStateException("segmented VBO upload did not render the centered triangle");
            }
            if (graphics.getPixel(8, 8) != 0) {
                throw new IllegalStateException("out-of-segment prefix data leaked into VBO upload");
            }
        } finally {
            ogl.endDrawing();
        }

        System.out.println("OGL segmented buffer upload probe OK");
    }
}
