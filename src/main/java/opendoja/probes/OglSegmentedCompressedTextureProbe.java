package opendoja.probes;

import com.nttdocomo.ui.Graphics;
import com.nttdocomo.ui.Image;
import com.nttdocomo.ui.ogl.*;

/** Eruption uploads compressed texture slices from larger pooled buffers. */
public final class OglSegmentedCompressedTextureProbe {
    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");
        for (boolean star : new boolean[]{false, true}) {
            Graphics pixels = Image.createImage(32, 16).getGraphics();
            GraphicsOGL gl = star ? com.docomostar.ui.Graphics.wrap(pixels) : pixels;
            gl.beginDrawing();
            int[] texture = new int[1];
            try {
                var buffers = DirectBufferFactory.getFactory();
                gl.glGenTextures(texture); gl.glBindTexture(GraphicsOGL.GL_TEXTURE_2D, texture[0]);
                byte[] paletteAndIndices = new byte[49];
                paletteAndIndices[0] = (byte) 255; // palette entry 0 = red
                paletteAndIndices[4] = (byte) 255; // palette entry 1 = green
                paletteAndIndices[48] = 0x01;
                ByteBuffer pool = buffers.allocateByteBuffer(58);
                pool.put(9, paletteAndIndices); pool.setSegment(9, 49);
                gl.glCompressedTexImage2D(GraphicsOGL.GL_TEXTURE_2D, 0, GraphicsOGL.GL_PALETTE4_RGB8_OES,
                        2, 1, 0, pool);
                check(gl.glGetError() == GraphicsOGL.GL_NO_ERROR, "Pooled compressed upload");
                gl.glViewport(0, 0, 32, 16);
                gl.glMatrixMode(GraphicsOGL.GL_PROJECTION); gl.glLoadIdentity();
                gl.glMatrixMode(GraphicsOGL.GL_MODELVIEW); gl.glLoadIdentity();
                gl.glDisable(GraphicsOGL.GL_DEPTH_TEST); gl.glDisable(GraphicsOGL.GL_CULL_FACE);
                gl.glEnable(GraphicsOGL.GL_TEXTURE_2D); gl.glColor4f(1, 1, 1, 1);
                gl.glEnableClientState(GraphicsOGL.GL_VERTEX_ARRAY);
                gl.glEnableClientState(GraphicsOGL.GL_TEXTURE_COORD_ARRAY);
                gl.glVertexPointer(3, GraphicsOGL.GL_FLOAT, 0, buffers.allocateFloatBuffer(new float[]{
                        -1, 1, 0, -1, -1, 0, 1, 1, 0, 1, -1, 0}));
                gl.glTexCoordPointer(2, GraphicsOGL.GL_FLOAT, 0,
                        buffers.allocateFloatBuffer(new float[]{0, 0, 0, 1, 1, 0, 1, 1}));
                gl.glDrawArrays(GraphicsOGL.GL_TRIANGLE_STRIP, 0, 4);
                if ("hardware".equals(System.getProperty("opendoja.openGlesRenderer"))) {
                    var field = Graphics.class.getDeclaredField("oglRenderer"); field.setAccessible(true);
                    Object renderer = field.get(pixels);
                    field = renderer.getClass().getSuperclass().getDeclaredField("hardware"); field.setAccessible(true);
                    Object hardware = field.get(renderer);
                    field = hardware.getClass().getDeclaredField("readbackPending"); field.setAccessible(true);
                    check(field.getBoolean(hardware), "Actual GPU draw required");
                }
                gl.glFlush();
                check((pixels.getRGBPixel(4, 8) & 0xffffff) == 0xff0000, "Segment starts at the red palette");
                check((pixels.getRGBPixel(27, 8) & 0xffffff) == 0x00ff00, "Segment contains green index");
                gl.glCompressedTexImage2D(GraphicsOGL.GL_TEXTURE_2D, 0, GraphicsOGL.GL_PALETTE4_RGB8_OES,
                        2, 1, 0, 50, pool);
                check(gl.glGetError() == GraphicsOGL.GL_INVALID_VALUE, "Oversized explicit upload reports GL error");
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            finally { gl.glDeleteTextures(texture); gl.endDrawing(); pixels.dispose(); }
        }
        System.out.println("ogl-segmented-compressed-texture-probe-ok");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
