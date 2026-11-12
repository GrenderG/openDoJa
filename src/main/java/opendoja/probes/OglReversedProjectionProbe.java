package opendoja.probes;

import com.nttdocomo.ui.Graphics;
import com.nttdocomo.ui.Image;
import com.nttdocomo.ui.ogl.DirectBufferFactory;
import com.nttdocomo.ui.ogl.GraphicsOGL;
import java.util.Arrays;

/** Replays Minna's downward-Y sprite camera rather than an identity-projection quad. */
public final class OglReversedProjectionProbe {
    public static void main(String[] args) {
        System.setProperty("java.awt.headless","true");
        for(boolean star:new boolean[]{false,true}) {
            Graphics pixels=Image.createImage(854,480).getGraphics();
            GraphicsOGL gl=star?com.docomostar.ui.Graphics.wrap(pixels):pixels;
            try { verifyMinnaSpriteCamera(gl,pixels); verifyProjectionContract(gl); }
            finally { pixels.dispose(); }
        }
        System.out.println("ogl-reversed-projection-probe-ok");
    }
    private static void verifyMinnaSpriteCamera(GraphicsOGL gl,Graphics pixels) {
        var buffers=DirectBufferFactory.getFactory();
        gl.beginDrawing();
        try {
            boolean hardware="hardware".equals(System.getProperty("opendoja.openGlesRenderer"));
            gl.glViewport(0,0,854,480);
            gl.glMatrixMode(GraphicsOGL.GL_PROJECTION); gl.glLoadIdentity();
            gl.glFrustumf(-427,427,240,-240,0.5f,40);
            gl.glMultMatrixf(new float[]{-1,0,0,0, 0,-1,0,0, 0,0,1,0, 0,0,-0.5f,1});
            gl.glMatrixMode(GraphicsOGL.GL_MODELVIEW); gl.glLoadIdentity();
            gl.glRotatef(-180,0,0,1); gl.glTranslatef(-427,-240,0);
            gl.glDisable(GraphicsOGL.GL_CULL_FACE); gl.glDisable(GraphicsOGL.GL_DEPTH_TEST);
            gl.glDisable(GraphicsOGL.GL_BLEND); gl.glDisable(GraphicsOGL.GL_ALPHA_TEST);
            gl.glEnableClientState(GraphicsOGL.GL_VERTEX_ARRAY);
            gl.glVertexPointer(3,GraphicsOGL.GL_FLOAT,0,buffers.allocateFloatBuffer(new float[]{
                    123,101,0, 123,67,0, 183,101,0, 183,67,0}));
            gl.glEnableClientState(GraphicsOGL.GL_TEXTURE_COORD_ARRAY);
            gl.glTexCoordPointer(2,GraphicsOGL.GL_FLOAT,0,buffers.allocateFloatBuffer(new float[]{0,1,0,0,1,1,1,0}));
            int[] texture=new int[1]; gl.glGenTextures(texture); gl.glBindTexture(GraphicsOGL.GL_TEXTURE_2D,texture[0]);
            gl.glTexImage2D(GraphicsOGL.GL_TEXTURE_2D,0,GraphicsOGL.GL_RGBA,2,2,0,GraphicsOGL.GL_RGBA,GraphicsOGL.GL_UNSIGNED_BYTE,
                    buffers.allocateByteBuffer(new byte[]{-1,0,0,-1, 0,-1,0,-1, 0,0,-1,-1, -1,-1,-1,-1}));
            gl.glTexParameterf(GraphicsOGL.GL_TEXTURE_2D,GraphicsOGL.GL_TEXTURE_MIN_FILTER,GraphicsOGL.GL_NEAREST);
            gl.glTexParameterf(GraphicsOGL.GL_TEXTURE_2D,GraphicsOGL.GL_TEXTURE_MAG_FILTER,GraphicsOGL.GL_NEAREST);
            gl.glEnable(GraphicsOGL.GL_TEXTURE_2D); gl.glColor4f(1,1,1,1);
            gl.glClearColor(0,0,0,1); gl.glClear(GraphicsOGL.GL_COLOR_BUFFER_BIT);
            gl.glDrawArrays(GraphicsOGL.GL_TRIANGLE_STRIP,0,4);
            if(hardware) {
                check(hardwareFlag(pixels,"available"),"Hardware probe requires a real GL context");
                check(hardwareFlag(pixels,"readbackPending"),"Projection draw must use the GPU without software fallback");
            }
            gl.glFlush();
            pixel(pixels,130,72,0xffff0000,"Sprite upper-left appears at its requested pixel position");
            pixel(pixels,175,72,0xff00ff00,"Sprite upper-right texture coordinates");
            pixel(pixels,130,94,0xff0000ff,"Sprite lower-left texture coordinates");
            pixel(pixels,175,94,0xffffffff,"Sprite lower-right texture coordinates");
            pixel(pixels,130,12,0xff000000,"Projection must not stretch the sprite into vertical bands");
            pixel(pixels,130,300,0xff000000,"Pixels below the sprite remain untouched");
            check(gl.glGetError()==GraphicsOGL.GL_NO_ERROR,"Reversed bounds are valid");
            gl.glDeleteTextures(texture);
        }finally{gl.endDrawing();}
    }
    private static boolean hardwareFlag(Graphics pixels,String name) {
        try {
            var renderer=Graphics.class.getDeclaredField("oglRenderer"); renderer.setAccessible(true);
            Object host=renderer.get(pixels);
            var backend=host.getClass().getSuperclass().getDeclaredField("hardware"); backend.setAccessible(true);
            Object hardware=backend.get(host);
            var field=hardware.getClass().getDeclaredField(name); field.setAccessible(true);
            return field.getBoolean(hardware);
        }catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
    }
    private static void verifyProjectionContract(GraphicsOGL gl) {
        gl.glMatrixMode(GraphicsOGL.GL_PROJECTION); gl.glLoadIdentity();
        gl.glTranslatef(3,4,5); gl.glOrthof(2,-2,3,-3,10,-10);
        float[] actual=new float[16]; gl.glGetFloatv(GraphicsOGL.GL_PROJECTION_MATRIX,actual);
        near(actual[0],-0.5f,"Orthographic horizontal reversal");
        near(actual[5],-1f/3,"Orthographic vertical reversal");
        near(actual[10],0.1f,"Orthographic depth reversal");
        near(actual[12],3,"Orthographic projection post-multiplies the current matrix");
        near(actual[13],4,"Existing translation is retained");
        float[] before=actual.clone();
        gl.glFrustumf(-1,1,1,1,0.5f,40);
        check(gl.glGetError()==GraphicsOGL.GL_INVALID_VALUE,"Zero-height frustum reports an error");
        gl.glGetFloatv(GraphicsOGL.GL_PROJECTION_MATRIX,actual);
        check(Arrays.equals(before,actual),"Invalid frustum preserves current matrix");
        gl.glFrustumf(-1,1,-1,1,0,40);
        check(gl.glGetError()==GraphicsOGL.GL_INVALID_VALUE,"Perspective near plane must be positive");
        gl.glOrthof(-1,1,-1,1,2,2);
        check(gl.glGetError()==GraphicsOGL.GL_INVALID_VALUE,"Orthographic near/far must differ");
        gl.glGetFloatv(GraphicsOGL.GL_PROJECTION_MATRIX,actual);
        check(Arrays.equals(before,actual),"Invalid orthographic projection preserves current matrix");
        gl.glLoadIdentity(); gl.glFrustumf(-1,1,0,0.000001f,1,10);
        gl.glGetFloatv(GraphicsOGL.GL_PROJECTION_MATRIX,actual);
        near(actual[5],2000000,"Small valid ranges are not clamped");
        check(gl.glGetError()==GraphicsOGL.GL_NO_ERROR,"Small perspective range is valid");
    }
    private static void pixel(Graphics g,int x,int y,int expected,String message){int actual=g.getRGBPixel(x,y);check(actual==expected,message+": "+Integer.toHexString(actual));}
    private static void near(float actual,float expected,String message){check(Math.abs(actual-expected)<Math.max(0.00001f,Math.abs(expected)*0.00001f),message+": "+actual);}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
