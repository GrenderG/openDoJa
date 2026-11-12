package opendoja.probes;

import com.docomostar.ui.Graphics;
import com.docomostar.ui.Image;
import com.docomostar.ui.ogl.GraphicsOGL;
import com.nttdocomo.ui.ogl.DirectBufferFactory;
import java.util.Arrays;

/** Replays TOBAL's RGB readback -> colour-key comparison -> tinted RGBA HUD texture. */
public final class StarRgbReadbackProbe {
    private static final int KEY = 0x0000ff;
    private static final int BACKGROUND = 0xff285078;

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");
        Image source = Image.createImage(2, 1);
        Graphics sourceGraphics = source.getGraphics();
        sourceGraphics.setRGBPixels(0,0,2,1,new int[]{KEY,0xffffff},0);
        int[] rgb = sourceGraphics.getRGBPixels(0,0,2,1,null,0);
        check(rgb[0] == KEY && rgb[1] == 0xffffff, "Exact RGB colour-key comparisons must not include host alpha");
        check(sourceGraphics.getRGBPixel(0,0) == KEY, "Single-pixel RGB readback has the same representation");
        check(sourceGraphics.getPixel(0,0) == 0xff0000ff, "Native colour readback retains the host pixel value");
        check(sourceGraphics.getPixels(0,0,2,1,null,0)[0] == 0xff0000ff, "Native bulk colour readback is unchanged");
        int[] destination = {0x76543210,0,0,0x12345678};
        check(sourceGraphics.getRGBPixels(0,0,2,1,destination,1) == destination, "RGB reads retain the supplied destination");
        check(Arrays.equals(destination,new int[]{0x76543210,KEY,0xffffff,0x12345678}), "Only the requested destination region is written");
        int[] clipped = sourceGraphics.getRGBPixels(-1,0,3,1,null,2);
        check(Arrays.equals(clipped,new int[]{0,0,0,KEY,0xffffff}), "Offset allocation and off-surface reads retain the backend contract");
        try { sourceGraphics.getRGBPixels(0,0,2,1,new int[1],0); throw new AssertionError("Short destination accepted"); }
        catch (ArrayIndexOutOfBoundsException expected) { }

        byte[] rgba = new byte[rgb.length*4];
        for(int i=0;i<rgb.length;i++) {
            int color=rgb[i];
            rgba[4*i]=(byte)(color >>> 16); rgba[4*i+1]=(byte)(color >>> 8); rgba[4*i+2]=(byte)color;
            rgba[4*i+3]=color == KEY ? 0 : (byte)255;
        }
        Image target=Image.createImage(32,16); Graphics graphics=target.getGraphics();
        GraphicsOGL gl=graphics;
        var buffers=DirectBufferFactory.getFactory();
        gl.beginDrawing();
        try {
            gl.glViewport(0,0,32,16);
            gl.glMatrixMode(GraphicsOGL.GL_PROJECTION); gl.glLoadIdentity();
            gl.glMatrixMode(GraphicsOGL.GL_MODELVIEW); gl.glLoadIdentity();
            gl.glDisable(GraphicsOGL.GL_DEPTH_TEST); gl.glDisable(GraphicsOGL.GL_LIGHTING);
            gl.glDisable(GraphicsOGL.GL_ALPHA_TEST); gl.glDisable(GraphicsOGL.GL_CULL_FACE);
            gl.glEnable(GraphicsOGL.GL_BLEND); gl.glBlendFunc(GraphicsOGL.GL_SRC_ALPHA,GraphicsOGL.GL_ONE_MINUS_SRC_ALPHA);
            gl.glEnableClientState(GraphicsOGL.GL_VERTEX_ARRAY);
            gl.glVertexPointer(2,GraphicsOGL.GL_FLOAT,0,buffers.allocateFloatBuffer(new float[]{-1,1,-1,-1,1,1,1,-1}));
            gl.glEnableClientState(GraphicsOGL.GL_TEXTURE_COORD_ARRAY);
            gl.glTexCoordPointer(2,GraphicsOGL.GL_FLOAT,0,buffers.allocateFloatBuffer(new float[]{0,0,0,1,1,0,1,1}));
            int[] name=new int[1]; gl.glGenTextures(name); gl.glBindTexture(GraphicsOGL.GL_TEXTURE_2D,name[0]);
            gl.glTexImage2D(GraphicsOGL.GL_TEXTURE_2D,0,GraphicsOGL.GL_RGBA,2,1,0,GraphicsOGL.GL_RGBA,GraphicsOGL.GL_UNSIGNED_BYTE,buffers.allocateByteBuffer(rgba));
            gl.glTexParameterf(GraphicsOGL.GL_TEXTURE_2D,GraphicsOGL.GL_TEXTURE_MIN_FILTER,GraphicsOGL.GL_NEAREST);
            gl.glTexParameterf(GraphicsOGL.GL_TEXTURE_2D,GraphicsOGL.GL_TEXTURE_MAG_FILTER,GraphicsOGL.GL_NEAREST);
            gl.glEnable(GraphicsOGL.GL_TEXTURE_2D); gl.glColor4f(1,0.25f,0.25f,1);
            gl.glClearColor(40/255f,80/255f,120/255f,1); gl.glClear(GraphicsOGL.GL_COLOR_BUFFER_BIT);
            gl.glDrawArrays(GraphicsOGL.GL_TRIANGLE_STRIP,0,4);
            if("hardware".equals(System.getProperty("opendoja.openGlesRenderer"))) checkHardware(graphics);
            gl.glFlush();
            check(graphics.getPixel(4,8) == BACKGROUND, "The colour-keyed HUD region must preserve the stage behind it");
            check(graphics.getPixel(28,8) == 0xffff4040, "The opaque HUD texel must retain the red health-gauge tint");
            check(gl.glGetError() == GraphicsOGL.GL_NO_ERROR,"Readback-based texture construction leaves no GL errors");
            gl.glDeleteTextures(name);
        } finally { gl.endDrawing(); }
        com.nttdocomo.ui.Graphics nativeGraphics=com.nttdocomo.ui.Image.createImage(1,1).getGraphics();
        nativeGraphics.setRGBPixel(0,0,KEY);
        check(nativeGraphics.getRGBPixel(0,0) == 0xff0000ff,"Existing DoJa pixel readback remains unchanged");
        nativeGraphics.dispose(); source.dispose(); target.dispose();
        System.out.println("star-rgb-readback-probe-ok");
    }
    private static void checkHardware(Graphics graphics) {
        try {
            var renderer=com.nttdocomo.ui.Graphics.class.getDeclaredField("oglRenderer"); renderer.setAccessible(true);
            Object host=renderer.get(graphics.hostGraphicsOGL());
            var backend=host.getClass().getSuperclass().getDeclaredField("hardware"); backend.setAccessible(true);
            Object hardware=backend.get(host);
            var pending=hardware.getClass().getDeclaredField("readbackPending"); pending.setAccessible(true);
            check(pending.getBoolean(hardware),"HUD rendering must use the GPU without software fallback");
        } catch(ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static void check(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
}
