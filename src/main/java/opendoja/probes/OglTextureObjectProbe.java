package opendoja.probes;

import com.nttdocomo.ui.Graphics;
import com.nttdocomo.ui.Image;
import com.nttdocomo.ui.ogl.DirectBufferFactory;
import com.nttdocomo.ui.ogl.GraphicsOGL;
import java.util.HashSet;

/** TOBAL binds application-assigned texture names, including the default texture zero. */
public final class OglTextureObjectProbe {
    private static final DirectBufferFactory BUFFERS = DirectBufferFactory.getFactory();

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");
        for (boolean star : new boolean[]{false, true}) {
            Graphics pixels = Image.createImage(32, 32).getGraphics();
            GraphicsOGL gl = star ? com.docomostar.ui.Graphics.wrap(pixels) : pixels;
            gl.beginDrawing();
            try { verify(gl, pixels, star); }
            finally { gl.endDrawing(); pixels.dispose(); }
        }
        System.out.println("ogl-texture-object-probe-ok");
    }

    private static void setup(GraphicsOGL gl) {
        gl.glViewport(0, 0, 32, 32);
        gl.glMatrixMode(GraphicsOGL.GL_PROJECTION); gl.glLoadIdentity();
        gl.glMatrixMode(GraphicsOGL.GL_MODELVIEW); gl.glLoadIdentity();
        gl.glDisable(GraphicsOGL.GL_BLEND); gl.glDisable(GraphicsOGL.GL_DEPTH_TEST);
        gl.glDisable(GraphicsOGL.GL_LIGHTING); gl.glDisable(GraphicsOGL.GL_CULL_FACE);
        gl.glEnableClientState(GraphicsOGL.GL_VERTEX_ARRAY);
        gl.glVertexPointer(3, GraphicsOGL.GL_FLOAT, 0, BUFFERS.allocateFloatBuffer(new float[]{
                -1,1,0, -1,-1,0, 1,1,0, 1,-1,0}));
        gl.glEnableClientState(GraphicsOGL.GL_TEXTURE_COORD_ARRAY);
        gl.glTexCoordPointer(2, GraphicsOGL.GL_FLOAT, 0, BUFFERS.allocateFloatBuffer(new float[]{0,0,0,1,1,0,1,1}));
        gl.glEnable(GraphicsOGL.GL_TEXTURE_2D); gl.glColor4f(1,1,1,1);
    }

    private static void verify(GraphicsOGL gl, Graphics pixels, boolean star) {
        setup(gl);
        // The initial binding is already texture zero, even without calling glBindTexture.
        upload(gl, 255, 0, 0);
        check(gl.glGetError() == GraphicsOGL.GL_NO_ERROR, "The default texture accepts image uploads");
        draw(gl, pixels, 0xffff0000);

        int manual = 1900081;
        gl.glBindTexture(GraphicsOGL.GL_TEXTURE_2D, manual); upload(gl, 0, 255, 0);
        check(gl.glGetError() == GraphicsOGL.GL_NO_ERROR, "Binding a new application-assigned name creates the texture");
        draw(gl, pixels, 0xff00ff00);
        gl.glBindTexture(GraphicsOGL.GL_TEXTURE_2D, 0); draw(gl, pixels, 0xffff0000);
        gl.glBindTexture(GraphicsOGL.GL_TEXTURE_2D, manual);
        gl.glDeleteTextures(new int[]{0, manual});
        draw(gl, pixels, 0xffff0000); // Deleting a bound name reverts to the retained default object.

        int[] reserved = new int[16];
        for (int i = 0; i < reserved.length; i++) {
            reserved[i] = i + 1;
            gl.glBindTexture(GraphicsOGL.GL_TEXTURE_2D, reserved[i]);
        }
        int[] generated = new int[20]; gl.glGenTextures(generated);
        var names = new HashSet<Integer>();
        for (int name : generated) check(name > 16 && names.add(name), "Generated names cannot replace application-assigned textures");
        gl.glDeleteTextures(generated); gl.glDeleteTextures(reserved);
        gl.glBindTexture(GraphicsOGL.GL_TEXTURE_2D, 0); draw(gl, pixels, 0xffff0000);

        gl.glActiveTexture(GraphicsOGL.GL_TEXTURE1);
        gl.glBindTexture(GraphicsOGL.GL_TEXTURE_2D, 0); upload(gl, 0, 0, 255);
        gl.glEnable(GraphicsOGL.GL_TEXTURE_2D);
        gl.glTexEnvi(GraphicsOGL.GL_TEXTURE_ENV, GraphicsOGL.GL_TEXTURE_ENV_MODE, GraphicsOGL.GL_REPLACE);
        draw(gl, pixels, 0xff0000ff);
        gl.glDisable(GraphicsOGL.GL_TEXTURE_2D); gl.glActiveTexture(GraphicsOGL.GL_TEXTURE0);
        draw(gl, pixels, 0xff0000ff); // Texture units reference the same default object in this context.

        Graphics otherPixels = Image.createImage(32, 32).getGraphics();
        GraphicsOGL other = star ? com.docomostar.ui.Graphics.wrap(otherPixels) : otherPixels;
        other.beginDrawing();
        try {
            setup(other);
            // An enabled texture without an image must not crash the hardware upload path.
            other.glDrawArrays(GraphicsOGL.GL_TRIANGLE_STRIP, 0, 4); other.glFlush();
            upload(other, 0, 255, 255); draw(other, otherPixels, 0xff00ffff);
        } finally { other.endDrawing(); otherPixels.dispose(); }
        draw(gl, pixels, 0xff0000ff); // Default textures are not shared across graphics contexts.

        // Pending GL errors must remain queryable while subsequent valid geometry still draws.
        gl.glBindTexture(GraphicsOGL.GL_ARRAY_BUFFER, 0);
        gl.glClearColor(0,0,0,1); gl.glClear(GraphicsOGL.GL_COLOR_BUFFER_BIT);
        draw(gl, pixels, 0xff0000ff);
        check(gl.glGetError() == GraphicsOGL.GL_INVALID_ENUM, "Drawing preserves the earlier error for glGetError");
        check(gl.glGetError() == GraphicsOGL.GL_NO_ERROR, "glGetError clears the reported error");
    }

    private static void upload(GraphicsOGL gl, int red, int green, int blue) {
        gl.glTexImage2D(GraphicsOGL.GL_TEXTURE_2D, 0, GraphicsOGL.GL_RGBA, 1, 1, 0,
                GraphicsOGL.GL_RGBA, GraphicsOGL.GL_UNSIGNED_BYTE,
                BUFFERS.allocateByteBuffer(new byte[]{(byte)red,(byte)green,(byte)blue,-1}));
    }
    private static void draw(GraphicsOGL gl, Graphics pixels, int expected) {
        gl.glDrawArrays(GraphicsOGL.GL_TRIANGLE_STRIP, 0, 4);
        if ("hardware".equals(System.getProperty("opendoja.openGlesRenderer"))) {
            try {
                var field = Graphics.class.getDeclaredField("oglRenderer"); field.setAccessible(true);
                Object renderer = field.get(pixels);
                field = renderer.getClass().getSuperclass().getDeclaredField("hardware"); field.setAccessible(true);
                Object hardware = field.get(renderer);
                field = hardware.getClass().getDeclaredField("readbackPending"); field.setAccessible(true);
                check(field.getBoolean(hardware), "Texture-object probe must draw on the GPU without software fallback");
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }
        gl.glFlush();
        int actual = pixels.getRGBPixel(16,16);
        check(actual == expected, "Texture pixel: expected " + Integer.toHexString(expected) + ", got " + Integer.toHexString(actual));
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
