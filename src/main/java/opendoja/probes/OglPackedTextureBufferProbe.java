package opendoja.probes;

import com.nttdocomo.ui.Graphics;
import com.nttdocomo.ui.Image;
import com.nttdocomo.ui.ogl.DirectBufferFactory;
import com.nttdocomo.ui.ogl.GraphicsOGL;

/** Packed character textures can be supplied as bytes, as Minna does, or typed shorts. */
public final class OglPackedTextureBufferProbe {
    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");
        for (boolean star : new boolean[]{false, true}) {
            Graphics pixels = Image.createImage(32, 32).getGraphics();
            GraphicsOGL gl = star ? com.docomostar.ui.Graphics.wrap(pixels) : pixels;
            gl.beginDrawing();
            try { verify(gl, pixels); }
            finally { gl.endDrawing(); pixels.dispose(); }
        }
        System.out.println("ogl-packed-texture-buffer-probe-ok");
    }

    private static void verify(GraphicsOGL gl, Graphics pixels) {
        var factory = DirectBufferFactory.getFactory();
        gl.glViewport(0, 0, 32, 32);
        gl.glMatrixMode(GraphicsOGL.GL_PROJECTION); gl.glLoadIdentity();
        gl.glMatrixMode(GraphicsOGL.GL_MODELVIEW); gl.glLoadIdentity();
        gl.glDisable(GraphicsOGL.GL_BLEND); gl.glDisable(GraphicsOGL.GL_DEPTH_TEST);
        gl.glEnableClientState(GraphicsOGL.GL_VERTEX_ARRAY);
        gl.glVertexPointer(3, GraphicsOGL.GL_FLOAT, 0, factory.allocateFloatBuffer(new float[]{
                -1,1,0, -1,-1,0, 1,1,0, 1,-1,0}));
        gl.glEnableClientState(GraphicsOGL.GL_TEXTURE_COORD_ARRAY);
        gl.glTexCoordPointer(2, GraphicsOGL.GL_FLOAT, 0, factory.allocateFloatBuffer(new float[]{0,0,0,1,1,0,1,1}));
        int[] texture = new int[1]; gl.glGenTextures(texture); gl.glBindTexture(GraphicsOGL.GL_TEXTURE_2D, texture[0]);
        gl.glTexParameteri(GraphicsOGL.GL_TEXTURE_2D, GraphicsOGL.GL_TEXTURE_MIN_FILTER, GraphicsOGL.GL_NEAREST);
        gl.glTexParameteri(GraphicsOGL.GL_TEXTURE_2D, GraphicsOGL.GL_TEXTURE_MAG_FILTER, GraphicsOGL.GL_NEAREST);
        gl.glEnable(GraphicsOGL.GL_TEXTURE_2D); gl.glColor4f(1,1,1,1);
        int[] types = {GraphicsOGL.GL_UNSIGNED_SHORT_4_4_4_4, GraphicsOGL.GL_UNSIGNED_SHORT_5_5_5_1, GraphicsOGL.GL_UNSIGNED_SHORT_5_6_5};
        int[][] values = {{0xf00f,0x0f0f,0x00ff,0xffff}, {0xf801,0x07c1,0x003f,0xffff}, {0xf800,0x07e0,0x001f,0xffff}};
        for (int variant = 0; variant < types.length; variant++) {
            int format = variant == 2 ? GraphicsOGL.GL_RGB : GraphicsOGL.GL_RGBA;
            short[] shorts = new short[4]; byte[] bytes = new byte[8];
            for (int i = 0; i < 4; i++) {
                shorts[i] = (short) values[variant][i];
                bytes[2*i] = (byte) shorts[i]; bytes[2*i+1] = (byte) (shorts[i] >>> 8);
            }
            var segmented = factory.allocateByteBuffer(13);
            segmented.put(5, bytes); segmented.setSegment(5, 8);
            gl.glTexImage2D(GraphicsOGL.GL_TEXTURE_2D, 0, format, 2, 2, 0, format, types[variant], segmented);
            check(gl.glGetError() == GraphicsOGL.GL_NO_ERROR, "Packed byte upload accepts format " + types[variant]);
            drawAndCheck(gl, pixels);
            gl.glTexImage2D(GraphicsOGL.GL_TEXTURE_2D, 0, format, 2, 2, 0, format, types[variant], factory.allocateShortBuffer(shorts));
            drawAndCheck(gl, pixels);
        }
        // A partially transparent 4444 texel must retain its alpha when decoded from bytes.
        gl.glDisable(GraphicsOGL.GL_TEXTURE_2D);
        gl.glClearColor(0,0,1,1); gl.glClear(GraphicsOGL.GL_COLOR_BUFFER_BIT);
        gl.glTexImage2D(GraphicsOGL.GL_TEXTURE_2D, 0, GraphicsOGL.GL_RGBA, 1, 1, 0,
                GraphicsOGL.GL_RGBA, GraphicsOGL.GL_UNSIGNED_SHORT_4_4_4_4, factory.allocateByteBuffer(new byte[]{8,(byte)0xf0}));
        gl.glEnable(GraphicsOGL.GL_TEXTURE_2D); gl.glEnable(GraphicsOGL.GL_BLEND);
        gl.glBlendFunc(GraphicsOGL.GL_SRC_ALPHA, GraphicsOGL.GL_ONE_MINUS_SRC_ALPHA);
        draw(gl, pixels);
        int pixel = pixels.getRGBPixel(16,16);
        check(Math.abs(((pixel >>> 16) & 255)-136) <= 1 && Math.abs((pixel & 255)-119) <= 1,
                "Packed RGBA4444 preserves alpha: " + Integer.toHexString(pixel));
        check(gl.glGetError() == GraphicsOGL.GL_NO_ERROR, "Packed texture rendering leaves no GL error");
        gl.glDeleteTextures(texture);
    }

    private static void drawAndCheck(GraphicsOGL gl, Graphics pixels) {
        draw(gl, pixels);
        check(pixels.getRGBPixel(4,4) == 0xffff0000, "Packed red texel");
        check(pixels.getRGBPixel(24,4) == 0xff00ff00, "Packed green texel");
        check(pixels.getRGBPixel(4,24) == 0xff0000ff, "Packed blue texel");
        check(pixels.getRGBPixel(24,24) == 0xffffffff, "Packed white texel");
    }
    private static void draw(GraphicsOGL gl, Graphics pixels) {
        gl.glDrawArrays(GraphicsOGL.GL_TRIANGLE_STRIP, 0, 4);
        if ("hardware".equals(System.getProperty("opendoja.openGlesRenderer"))) {
            try {
                var renderer = Graphics.class.getDeclaredField("oglRenderer"); renderer.setAccessible(true);
                Object host = renderer.get(pixels);
                var backend = host.getClass().getSuperclass().getDeclaredField("hardware"); backend.setAccessible(true);
                Object hardware = backend.get(host);
                var pending = hardware.getClass().getDeclaredField("readbackPending"); pending.setAccessible(true);
                check(pending.getBoolean(hardware), "Packed texture draw requires actual GPU rendering");
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }
        gl.glFlush();
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
