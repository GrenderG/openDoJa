package opendoja.probes;

import com.nttdocomo.ui.Graphics;
import com.nttdocomo.ui.Image;
import com.nttdocomo.ui.ogl.DirectBufferFactory;
import com.nttdocomo.ui.ogl.FloatBuffer;
import com.nttdocomo.ui.ogl.GraphicsOGL;
import com.nttdocomo.ui.ogl.ByteBuffer;

/**
 * Verifies that OES_matrix_palette weighted positions use the loaded palette
 * matrices and per-vertex weights instead of falling back to the plain model-view path.
 */
public final class OglMatrixPaletteProbe {
    private OglMatrixPaletteProbe() {
    }

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");

        Graphics graphics = (Graphics) Image.createImage(64, 32).getGraphics();
        GraphicsOGL ogl = (GraphicsOGL) graphics;
        DirectBufferFactory buffers = DirectBufferFactory.getFactory();
        FloatBuffer vertices = buffers.allocateFloatBuffer(new float[]{
                -0.4f, -0.4f, 0f,
                0.0f, 0.4f, 0f,
                0.4f, -0.4f, 0f
        });
        FloatBuffer weights = buffers.allocateFloatBuffer(new float[]{
                0.5f, 0.5f,
                0.5f, 0.5f,
                0.5f, 0.5f
        });
        ByteBuffer matrixIndices = buffers.allocateByteBuffer(new byte[]{
                0, 1,
                0, 1,
                0, 1
        });

        ogl.beginDrawing();
        try {
            ogl.glClearColor(0f, 0f, 0f, 0f);
            ogl.glClear(GraphicsOGL.GL_COLOR_BUFFER_BIT | GraphicsOGL.GL_DEPTH_BUFFER_BIT);
            ogl.glColor4ub((short) 255, (short) 255, (short) 255, (short) 255);
            ogl.glEnableClientState(GraphicsOGL.GL_VERTEX_ARRAY);
            ogl.glVertexPointer(3, GraphicsOGL.GL_FLOAT, 0, vertices);
            ogl.glEnable(GraphicsOGL.GL_MATRIX_PALETTE_OES);
            ogl.glEnableClientState(GraphicsOGL.GL_MATRIX_INDEX_ARRAY_OES);
            ogl.glEnableClientState(GraphicsOGL.GL_WEIGHT_ARRAY_OES);
            ogl.glMatrixIndexPointerOES(2, GraphicsOGL.GL_UNSIGNED_BYTE, 0, matrixIndices);
            ogl.glWeightPointerOES(2, GraphicsOGL.GL_FLOAT, 0, weights);

            ogl.glMatrixMode(GraphicsOGL.GL_PROJECTION);
            ogl.glLoadIdentity();
            ogl.glMatrixMode(GraphicsOGL.GL_MODELVIEW);
            ogl.glLoadIdentity();

            ogl.glCurrentPaletteMatrixOES(0);
            ogl.glLoadPaletteFromModelViewMatrixOES();

            ogl.glLoadIdentity();
            ogl.glTranslatef(1f, 0f, 0f);
            ogl.glCurrentPaletteMatrixOES(1);
            ogl.glLoadPaletteFromModelViewMatrixOES();

            ogl.glMatrixMode(GraphicsOGL.GL_MODELVIEW);
            ogl.glLoadIdentity();
            ogl.glDrawArrays(GraphicsOGL.GL_TRIANGLES, 0, 3);

            int[] bounds = opaqueBounds(graphics);
            check(graphics.getPixel(48, 16) != 0,
                    "weighted palette triangle should draw at translated center, bounds=" + describe(bounds));
            check(graphics.getPixel(24, 16) == 0,
                    "untranslated center should stay clear, bounds=" + describe(bounds));
            check(ogl.glGetError() == GraphicsOGL.GL_NO_ERROR, "matrix-palette probe should not set a GL error");
        } finally {
            ogl.endDrawing();
        }

        System.out.println("OGL matrix-palette probe OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static int[] opaqueBounds(Graphics graphics) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 64; x++) {
                if ((graphics.getPixel(x, y) >>> 24) == 0) {
                    continue;
                }
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }
        return minX == Integer.MAX_VALUE ? null : new int[]{minX, minY, maxX, maxY};
    }

    private static String describe(int[] bounds) {
        return bounds == null ? "<none>" : bounds[0] + "," + bounds[1] + "->" + bounds[2] + "," + bounds[3];
    }
}
