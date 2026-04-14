package opendoja.probes;

import com.nttdocomo.ui.Graphics;
import com.nttdocomo.ui.Image;
import com.nttdocomo.ui.ogl.DirectBufferFactory;
import com.nttdocomo.ui.ogl.FloatBuffer;
import com.nttdocomo.ui.ogl.GraphicsOGL;
import com.nttdocomo.ui.ogl.ShortBuffer;

import java.util.Arrays;

/**
 * Verifies indexed GLES clipping and triangle-strip assembly under face culling.
 */
public final class OglClippedPrimitiveProbe {
    private static final int IMAGE_SIZE = 96;

    private OglClippedPrimitiveProbe() {
    }

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");

        int[] clippedTriangle = render(
                new float[]{
                        -1f, -1f, -0.5f,
                        1f, -1f, -2f,
                        0f, 1f, -2f
                },
                new short[]{0, 1, 2},
                GraphicsOGL.GL_TRIANGLES);
        int[] explicitClippedQuad = render(
                new float[]{
                        -1f / 3f, -1f, -1f,
                        1f, -1f, -2f,
                        0f, 1f, -2f,
                        -2f / 3f, -1f / 3f, -1f
                },
                new short[]{0, 1, 2, 0, 2, 3},
                GraphicsOGL.GL_TRIANGLES);
        verifyEqual(clippedTriangle, explicitClippedQuad,
                "near-plane clipped indexed triangle should match explicit clipped quad under culling");

        int[] stripQuad = render(
                new float[]{
                        -1f, 1f, -2f,
                        -1f, -1f, -2f,
                        1f, 1f, -2f,
                        1f, -1f, -2f
                },
                new short[]{0, 1, 2, 3},
                GraphicsOGL.GL_TRIANGLE_STRIP);
        int[] explicitStripQuad = render(
                new float[]{
                        -1f, 1f, -2f,
                        -1f, -1f, -2f,
                        1f, 1f, -2f,
                        1f, -1f, -2f
                },
                new short[]{0, 1, 2, 2, 1, 3},
                GraphicsOGL.GL_TRIANGLES);
        verifyEqual(stripQuad, explicitStripQuad,
                "indexed triangle strip should match explicit culled triangles");

        System.out.println("OGL clipped primitive probe OK");
    }

    private static int[] render(float[] vertices, short[] indices, int mode) {
        Graphics graphics = (Graphics) Image.createImage(IMAGE_SIZE, IMAGE_SIZE).getGraphics();
        GraphicsOGL ogl = (GraphicsOGL) graphics;
        DirectBufferFactory buffers = DirectBufferFactory.getFactory();
        FloatBuffer vertexBuffer = buffers.allocateFloatBuffer(vertices);
        ShortBuffer indexBuffer = buffers.allocateShortBuffer(indices);

        ogl.beginDrawing();
        try {
            ogl.glClearColor(0f, 0f, 0f, 0f);
            ogl.glClear(GraphicsOGL.GL_COLOR_BUFFER_BIT | GraphicsOGL.GL_DEPTH_BUFFER_BIT);
            ogl.glEnableClientState(GraphicsOGL.GL_VERTEX_ARRAY);
            ogl.glVertexPointer(3, GraphicsOGL.GL_FLOAT, 0, vertexBuffer);
            ogl.glColor4ub((short) 255, (short) 255, (short) 255, (short) 255);
            ogl.glEnable(GraphicsOGL.GL_CULL_FACE);
            ogl.glCullFace(GraphicsOGL.GL_BACK);
            ogl.glMatrixMode(GraphicsOGL.GL_PROJECTION);
            ogl.glLoadIdentity();
            ogl.glFrustumf(-1f, 1f, -1f, 1f, 1f, 10f);
            ogl.glMatrixMode(GraphicsOGL.GL_MODELVIEW);
            ogl.glLoadIdentity();
            ogl.glDrawElements(mode, indices.length, GraphicsOGL.GL_UNSIGNED_SHORT, indexBuffer);
            if (ogl.glGetError() != GraphicsOGL.GL_NO_ERROR) {
                throw new IllegalStateException("unexpected GL error");
            }
            return pixels(graphics);
        } finally {
            ogl.endDrawing();
        }
    }

    private static int[] pixels(Graphics graphics) {
        int[] pixels = new int[IMAGE_SIZE * IMAGE_SIZE];
        int offset = 0;
        for (int y = 0; y < IMAGE_SIZE; y++) {
            for (int x = 0; x < IMAGE_SIZE; x++) {
                pixels[offset++] = graphics.getPixel(x, y);
            }
        }
        return pixels;
    }

    private static void verifyEqual(int[] actual, int[] expected, String message) {
        if (!Arrays.equals(actual, expected)) {
            throw new IllegalStateException(message
                    + " actualOpaque=" + opaqueCount(actual)
                    + " expectedOpaque=" + opaqueCount(expected));
        }
        if (opaqueCount(actual) == 0) {
            throw new IllegalStateException(message + " rendered no pixels");
        }
    }

    private static int opaqueCount(int[] pixels) {
        int opaque = 0;
        for (int pixel : pixels) {
            if ((pixel >>> 24) != 0) {
                opaque++;
            }
        }
        return opaque;
    }
}
