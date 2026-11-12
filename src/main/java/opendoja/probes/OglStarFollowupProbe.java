package opendoja.probes;

import com.nttdocomo.ui.Graphics;
import com.nttdocomo.ui.Image;
import com.nttdocomo.ui.ogl.DirectBufferFactory;
import com.nttdocomo.ui.ogl.GraphicsOGL;

/** Pixel-level contracts for GLES operations used by Let's Tap, TOBAL and Minna no Golf 3. */
public final class OglStarFollowupProbe {
    private static final DirectBufferFactory BUFFERS = DirectBufferFactory.getFactory();

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");
        for (boolean star : new boolean[]{false, true}) {
            Graphics pixels = Image.createImage(32, 32).getGraphics();
            GraphicsOGL gl = star ? com.docomostar.ui.Graphics.wrap(pixels) : pixels;
            verify(gl, pixels);
            if ("hardware".equals(System.getProperty("opendoja.openGlesRenderer"))) verifyHardware(pixels);
            pixels.dispose();
        }
        System.out.println("ogl-star-followup-probe-ok");
    }

    private static void verify(GraphicsOGL gl, Graphics pixels) {
        gl.beginDrawing();
        try {
            gl.glViewport(0, 0, 32, 32);
            gl.glMatrixMode(GraphicsOGL.GL_PROJECTION); gl.glLoadIdentity();
            gl.glMatrixMode(GraphicsOGL.GL_MODELVIEW); gl.glLoadIdentity();
            gl.glDisable(GraphicsOGL.GL_BLEND); gl.glDisable(GraphicsOGL.GL_ALPHA_TEST);
            gl.glDisable(GraphicsOGL.GL_CULL_FACE); gl.glDisable(GraphicsOGL.GL_DEPTH_TEST);
            gl.glEnableClientState(GraphicsOGL.GL_VERTEX_ARRAY);
            gl.glVertexPointer(3, GraphicsOGL.GL_FLOAT, 0, BUFFERS.allocateFloatBuffer(new float[]{
                    -1,1,0, 1,1,0, -1,-1,0, -1,-1,0, 1,1,0, 1,-1,0}));
            gl.glEnableClientState(GraphicsOGL.GL_TEXTURE_COORD_ARRAY);
            gl.glTexCoordPointer(2, GraphicsOGL.GL_FLOAT, 0, BUFFERS.allocateFloatBuffer(new float[]{
                    0,0, 1,0, 0,1, 0,1, 1,0, 1,1}));
            gl.glClearColor(0,0,1,1); gl.glClear(GraphicsOGL.GL_COLOR_BUFFER_BIT);
            gl.glScissor(4, 4, 8, 8); gl.glEnable(GraphicsOGL.GL_SCISSOR_TEST);
            gl.glColor4f(1,0,0,1); draw(gl);
            pixel(pixels, 6, 24, 0xffff0000, "Scissor uses bottom-left framebuffer coordinates");
            pixel(pixels, 6, 6, 0xff0000ff, "Scissor clips drawing");
            gl.glScissor(4,4,-1,8);
            check(gl.glGetError() == GraphicsOGL.GL_INVALID_VALUE, "Invalid scissor dimensions report a GL error");
            gl.glClearColor(0,1,0,1); gl.glClear(GraphicsOGL.GL_COLOR_BUFFER_BIT); gl.glFlush();
            pixel(pixels, 6, 24, 0xff00ff00, "Scissor also clips clears");
            pixel(pixels, 6, 6, 0xff0000ff, "Invalid dimensions retain the preceding scissor");
            gl.glDisable(GraphicsOGL.GL_SCISSOR_TEST);

            gl.glClearColor(0,0,1,1); gl.glEnable(GraphicsOGL.GL_DEPTH_TEST);
            gl.glDepthFunc(GraphicsOGL.GL_LESS); gl.glDepthMask(true);
            gl.glClearDepthf(0); gl.glClear(GraphicsOGL.GL_COLOR_BUFFER_BIT | GraphicsOGL.GL_DEPTH_BUFFER_BIT);
            draw(gl); pixel(pixels, 16,16,0xff0000ff,"Near-depth clear rejects more distant fragments");
            gl.glClearDepthf(1); gl.glDepthMask(false); gl.glClear(GraphicsOGL.GL_DEPTH_BUFFER_BIT);
            draw(gl); pixel(pixels,16,16,0xff0000ff,"Depth mask prevents depth clearing");
            gl.glDepthMask(true); gl.glClear(GraphicsOGL.GL_DEPTH_BUFFER_BIT);
            draw(gl); pixel(pixels,16,16,0xffff0000,"Far-depth clear allows nearer fragments");
            gl.glDisable(GraphicsOGL.GL_DEPTH_TEST);

            int[] names = new int[2]; gl.glGenTextures(names);
            gl.glBindTexture(GraphicsOGL.GL_TEXTURE_2D, names[0]);
            gl.glTexImage2D(GraphicsOGL.GL_TEXTURE_2D,0,GraphicsOGL.GL_RGBA,1,1,0,GraphicsOGL.GL_RGBA,GraphicsOGL.GL_UNSIGNED_BYTE,
                    BUFFERS.allocateByteBuffer(new byte[]{(byte)128,0,0,(byte)255}));
            gl.glEnable(GraphicsOGL.GL_TEXTURE_2D); gl.glTexEnvf(GraphicsOGL.GL_TEXTURE_ENV,GraphicsOGL.GL_TEXTURE_ENV_MODE,GraphicsOGL.GL_REPLACE);
            gl.glColor4f(0,0,1,1); draw(gl);
            pixel(pixels,16,16,0xff800000,"Float texture environment selects REPLACE");

            gl.glActiveTexture(GraphicsOGL.GL_TEXTURE1); gl.glBindTexture(GraphicsOGL.GL_TEXTURE_2D,names[1]);
            gl.glTexImage2D(GraphicsOGL.GL_TEXTURE_2D,0,GraphicsOGL.GL_RGBA,1,1,0,GraphicsOGL.GL_RGBA,GraphicsOGL.GL_UNSIGNED_BYTE,
                    BUFFERS.allocateByteBuffer(new byte[]{-1,-1,-1,-1}));
            gl.glEnable(GraphicsOGL.GL_TEXTURE_2D);
            gl.glTexEnvi(GraphicsOGL.GL_TEXTURE_ENV,GraphicsOGL.GL_TEXTURE_ENV_MODE,GraphicsOGL.GL_COMBINE);
            gl.glTexEnvi(GraphicsOGL.GL_TEXTURE_ENV,GraphicsOGL.GL_COMBINE_RGB,GraphicsOGL.GL_ADD);
            gl.glTexEnvi(GraphicsOGL.GL_TEXTURE_ENV,GraphicsOGL.GL_SRC0_RGB,GraphicsOGL.GL_CONSTANT);
            gl.glTexEnvi(GraphicsOGL.GL_TEXTURE_ENV,GraphicsOGL.GL_SRC1_RGB,GraphicsOGL.GL_PREVIOUS);
            gl.glTexEnvi(GraphicsOGL.GL_TEXTURE_ENV,GraphicsOGL.GL_COMBINE_ALPHA,GraphicsOGL.GL_REPLACE);
            gl.glTexEnvi(GraphicsOGL.GL_TEXTURE_ENV,GraphicsOGL.GL_SRC0_ALPHA,GraphicsOGL.GL_PREVIOUS);
            gl.glTexEnvfv(GraphicsOGL.GL_TEXTURE_ENV,GraphicsOGL.GL_TEXTURE_ENV_COLOR,new float[]{0,128f/255f,0,0});
            gl.glActiveTexture(GraphicsOGL.GL_TEXTURE0); draw(gl);
            pixel(pixels,16,16,0xff808000,"Second unit adds to PREVIOUS while the primary unit retains REPLACE");

            gl.glActiveTexture(GraphicsOGL.GL_TEXTURE1);
            gl.glTexImage2D(GraphicsOGL.GL_TEXTURE_2D,0,GraphicsOGL.GL_RGBA,2,1,0,GraphicsOGL.GL_RGBA,GraphicsOGL.GL_UNSIGNED_BYTE,
                    BUFFERS.allocateByteBuffer(new byte[]{-1,0,0,-1,0,-1,0,-1}));
            gl.glTexParameterf(GraphicsOGL.GL_TEXTURE_2D,GraphicsOGL.GL_TEXTURE_WRAP_S,GraphicsOGL.GL_CLAMP_TO_EDGE);
            gl.glTexEnvf(GraphicsOGL.GL_TEXTURE_ENV,GraphicsOGL.GL_TEXTURE_ENV_MODE,GraphicsOGL.GL_REPLACE);
            gl.glClientActiveTexture(GraphicsOGL.GL_TEXTURE1);
            gl.glEnableClientState(GraphicsOGL.GL_TEXTURE_COORD_ARRAY);
            gl.glTexCoordPointer(2,GraphicsOGL.GL_FLOAT,0,BUFFERS.allocateFloatBuffer(new float[12]));
            gl.glClientActiveTexture(GraphicsOGL.GL_TEXTURE0);
            gl.glActiveTexture(GraphicsOGL.GL_TEXTURE0); draw(gl);
            pixel(pixels,16,16,0xffff0000,"Second unit has its own client-coordinate array");
            gl.glActiveTexture(GraphicsOGL.GL_TEXTURE1);
            gl.glMatrixMode(GraphicsOGL.GL_TEXTURE); gl.glLoadIdentity(); gl.glTranslatef(1,0,0);
            gl.glActiveTexture(GraphicsOGL.GL_TEXTURE0); gl.glMatrixMode(GraphicsOGL.GL_MODELVIEW); draw(gl);
            pixel(pixels,16,16,0xff00ff00,"Texture matrices are independent per unit");
            gl.glActiveTexture(GraphicsOGL.GL_TEXTURE1); gl.glDisable(GraphicsOGL.GL_TEXTURE_2D);
            gl.glActiveTexture(GraphicsOGL.GL_TEXTURE0); draw(gl);
            pixel(pixels,16,16,0xff800000,"Disabling second unit leaves the first intact");

            // A copy starts at the framebuffer's lower-left corner, independent of current scissor/viewport.
            gl.glDisable(GraphicsOGL.GL_TEXTURE_2D);
            pixels.setPixel(0,31,0xffff0000); pixels.setPixel(1,31,0xff00ff00);
            pixels.setPixel(0,30,0xff0000ff); pixels.setPixel(1,30,0xffffffff);
            gl.glCopyTexImage2D(GraphicsOGL.GL_TEXTURE_2D,0,GraphicsOGL.GL_RGB,0,0,2,2,0);
            gl.glTexParameterf(GraphicsOGL.GL_TEXTURE_2D,GraphicsOGL.GL_TEXTURE_MIN_FILTER,GraphicsOGL.GL_NEAREST);
            gl.glTexParameterf(GraphicsOGL.GL_TEXTURE_2D,GraphicsOGL.GL_TEXTURE_MAG_FILTER,GraphicsOGL.GL_NEAREST);
            gl.glTexParameterf(GraphicsOGL.GL_TEXTURE_2D,GraphicsOGL.GL_TEXTURE_WRAP_S,GraphicsOGL.GL_CLAMP_TO_EDGE);
            gl.glTexParameterf(GraphicsOGL.GL_TEXTURE_2D,GraphicsOGL.GL_TEXTURE_WRAP_T,GraphicsOGL.GL_CLAMP_TO_EDGE);
            gl.glEnable(GraphicsOGL.GL_TEXTURE_2D); draw(gl);
            pixel(pixels,4,4,0xffff0000,"Framebuffer copy row zero is the lower row");
            pixel(pixels,24,24,0xffffffff,"Framebuffer copy includes the opposite corner");

            byte[] full = palette(4,4,255,0,0); // Entry zero + all zero indices.
            gl.glCompressedTexImage2D(GraphicsOGL.GL_TEXTURE_2D,0,GraphicsOGL.GL_PALETTE8_RGBA8_OES,4,4,0,BUFFERS.allocateByteBuffer(full));
            draw(gl); pixel(pixels,4,4,0xffff0000,"Eight-bit palette upload");
            byte[] patch = palette(2,2,0,255,0);
            var segment = BUFFERS.allocateByteBuffer(patch.length + 7);
            segment.put(7,patch); segment.setSegment(7,patch.length);
            gl.glCompressedTexSubImage2D(GraphicsOGL.GL_TEXTURE_2D,0,0,0,2,2,GraphicsOGL.GL_PALETTE8_RGBA8_OES,segment);
            draw(gl); pixel(pixels,4,4,0xff00ff00,"Paletted subimage decodes the selected segment");
            pixel(pixels,24,24,0xffff0000,"Paletted subimage retains surrounding texels");
            gl.glCompressedTexImage2D(GraphicsOGL.GL_TEXTURE_2D,0,GraphicsOGL.GL_PALETTE8_RGBA8_OES,4,4,0,BUFFERS.allocateByteBuffer(palette(4,4,0,0,255)));
            draw(gl); pixel(pixels,4,4,0xff0000ff,"Repeated palette upload invalidates hardware cache");
            check(gl.glGetError() == GraphicsOGL.GL_NO_ERROR,"Valid follow-up operations leave no GL errors");
            gl.glDeleteTextures(names);
        } finally { gl.endDrawing(); }
    }
    private static byte[] palette(int width,int height,int red,int green,int blue) {
        byte[] bytes = new byte[1024+width*height];
        bytes[0]=(byte)red; bytes[1]=(byte)green; bytes[2]=(byte)blue; bytes[3]=-1;
        return bytes;
    }
    private static void draw(GraphicsOGL gl) {
        gl.glDrawArrays(GraphicsOGL.GL_TRIANGLES,0,6);
        if ("hardware".equals(System.getProperty("opendoja.openGlesRenderer"))) {
            Graphics pixels = gl instanceof com.docomostar.ui.ogl.GraphicsOGL star ? (Graphics) star.hostGraphicsOGL() : (Graphics) gl;
            check(hardwareFlag(pixels,"readbackPending"),"Hardware draw must not silently fall back to software");
        }
        gl.glFlush();
    }
    private static void verifyHardware(Graphics pixels) {
        check(hardwareFlag(pixels,"available"),"Hardware probe must use a real GL context");
    }
    private static boolean hardwareFlag(Graphics pixels,String name) {
        try {
            var renderer=Graphics.class.getDeclaredField("oglRenderer"); renderer.setAccessible(true);
            Object host=renderer.get(pixels);
            var backend=host.getClass().getSuperclass().getDeclaredField("hardware"); backend.setAccessible(true);
            Object hardware=backend.get(host);
            var field=hardware.getClass().getDeclaredField(name); field.setAccessible(true);
            return field.getBoolean(hardware);
        } catch(ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static void pixel(Graphics g,int x,int y,int expected,String message) {
        int actual=g.getRGBPixel(x,y); check(actual==expected,message+": "+Integer.toHexString(actual));
    }
    private static void check(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
