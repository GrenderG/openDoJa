package opendoja.probes;

import com.nttdocomo.ui.Graphics;
import com.nttdocomo.ui.Image;
import com.nttdocomo.ui.ogl.ByteBuffer;
import com.nttdocomo.ui.ogl.DirectBufferFactory;
import com.nttdocomo.ui.ogl.GraphicsOGL;

/**
 * Verifies that byte-backed VBO uploads are decoded with the little-endian layout used by real
 * DoJa OpenGL titles when they serialize float vertex data and unsigned-short index data into
 * ByteBuffer instances.
 */
public final class OglByteBufferVboProbe {
    private OglByteBufferVboProbe() {
    }

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");

        Graphics graphics = (Graphics) Image.createImage(32, 32).getGraphics();
        GraphicsOGL ogl = (GraphicsOGL) graphics;
        DirectBufferFactory buffers = DirectBufferFactory.getFactory();

        ByteBuffer vertexBytes = buffers.allocateByteBuffer(4 + (3 * 3 * 4));
        putFloatLittleEndian(vertexBytes, 4, -0.6f);
        putFloatLittleEndian(vertexBytes, 8, -0.6f);
        putFloatLittleEndian(vertexBytes, 12, 0f);
        putFloatLittleEndian(vertexBytes, 16, 0.6f);
        putFloatLittleEndian(vertexBytes, 20, -0.6f);
        putFloatLittleEndian(vertexBytes, 24, 0f);
        putFloatLittleEndian(vertexBytes, 28, 0f);
        putFloatLittleEndian(vertexBytes, 32, 0.6f);
        putFloatLittleEndian(vertexBytes, 36, 0f);

        ByteBuffer indexBytes = buffers.allocateByteBuffer(2 + (3 * 2));
        putShortLittleEndian(indexBytes, 2, 0);
        putShortLittleEndian(indexBytes, 4, 1);
        putShortLittleEndian(indexBytes, 6, 2);

        int[] vboIds = new int[2];
        ogl.beginDrawing();
        try {
            ogl.glClearColor(0f, 0f, 0f, 0f);
            ogl.glClear(GraphicsOGL.GL_COLOR_BUFFER_BIT | GraphicsOGL.GL_DEPTH_BUFFER_BIT);
            ogl.glGenBuffers(vboIds);
            ogl.glBindBuffer(GraphicsOGL.GL_ARRAY_BUFFER, vboIds[0]);
            ogl.glBufferData(GraphicsOGL.GL_ARRAY_BUFFER, vertexBytes, GraphicsOGL.GL_STATIC_DRAW);
            ogl.glBindBuffer(GraphicsOGL.GL_ELEMENT_ARRAY_BUFFER, vboIds[1]);
            ogl.glBufferData(GraphicsOGL.GL_ELEMENT_ARRAY_BUFFER, indexBytes, GraphicsOGL.GL_STATIC_DRAW);
            ogl.glEnableClientState(GraphicsOGL.GL_VERTEX_ARRAY);
            ogl.glColor4f(1f, 1f, 1f, 1f);
            ogl.glVertexPointer(3, GraphicsOGL.GL_FLOAT, 12, 4);
            ogl.glDrawElements(GraphicsOGL.GL_TRIANGLES, 3, GraphicsOGL.GL_UNSIGNED_SHORT, 2);
            check(ogl.glGetError() == GraphicsOGL.GL_NO_ERROR, "byte-backed VBO draw should not set a GL error");
            int center = graphics.getPixel(16, 16);
            check((center >>> 24) != 0, String.format("expected triangle coverage at center pixel, got %08x", center));
        } finally {
            ogl.glBindBuffer(GraphicsOGL.GL_ARRAY_BUFFER, 0);
            ogl.glBindBuffer(GraphicsOGL.GL_ELEMENT_ARRAY_BUFFER, 0);
            if (vboIds[0] != 0 || vboIds[1] != 0) {
                ogl.glDeleteBuffers(vboIds);
            }
            ogl.endDrawing();
        }

        System.out.println("OGL byte-buffer VBO probe OK");
    }

    private static void putShortLittleEndian(ByteBuffer buffer, int offset, int value) {
        byte[] raw = new byte[]{
                (byte) value,
                (byte) (value >>> 8)
        };
        buffer.put(offset, raw);
    }

    private static void putFloatLittleEndian(ByteBuffer buffer, int offset, float value) {
        int bits = Float.floatToIntBits(value);
        byte[] raw = new byte[]{
                (byte) bits,
                (byte) (bits >>> 8),
                (byte) (bits >>> 16),
                (byte) (bits >>> 24)
        };
        buffer.put(offset, raw);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
