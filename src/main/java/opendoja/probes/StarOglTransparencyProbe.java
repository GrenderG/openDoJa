package opendoja.probes;

import com.docomostar.ui.*;
import com.docomostar.ui.ogl.GraphicsOGL;
import opendoja.host.*;

/** Reproduces canvas initialization followed by an RGBA4444 sprite on an image graphics view. */
public final class StarOglTransparencyProbe {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        DesktopLauncher.launch(LaunchConfig.builder(StarApp.class).parameter("AppType", "FullApp").build());
        try {
            GraphicsOGL canvas = (GraphicsOGL) ((Canvas) Display.getCurrent()).getGraphics();
            canvas.glEnable(GraphicsOGL.GL_BLEND);
            canvas.glBlendFunc(GraphicsOGL.GL_SRC_ALPHA, GraphicsOGL.GL_ONE_MINUS_SRC_ALPHA);
            canvas.glAlphaFunc(GraphicsOGL.GL_NOTEQUAL, 0);
            canvas.glEnable(GraphicsOGL.GL_ALPHA_TEST);
            Graphics image = Image.createImage(32, 32).getGraphics();
            draw(image);
            verifyTransparentSprite(image);
            Graphics copy = image.copy();
            draw(copy);
            verifyTransparentSprite(copy);

            canvas.glBlendFunc(GraphicsOGL.GL_SRC_ALPHA, GraphicsOGL.GL_SRC_ALPHA_SATURATE);
            check(canvas.glGetError() == GraphicsOGL.GL_INVALID_ENUM, "Invalid blend arguments retain GL errors");
            canvas.glDisable(GraphicsOGL.GL_ALPHA_TEST);
            draw(copy);
            verifyTransparentSprite(copy); // Blending also works without the alpha test.

            // Explicit state changes on one STAR view carry over to another view.
            GraphicsOGL state = image;
            state.glDisable(GraphicsOGL.GL_BLEND);
            draw(copy);
            check(copy.getPixel(4, 4) == 0, "Existing views observe changed fragment settings");
            Graphics opaque = Image.createImage(32, 32).getGraphics();
            draw(opaque);
            check(opaque.getPixel(4, 4) == 0, "Explicitly disabled fragment tests remain disabled");
            canvas.glEnable(GraphicsOGL.GL_ALPHA_TEST);
            draw(copy);
            verifyTransparentSprite(copy); // Alpha testing also works without blending.
            canvas.glEnable(GraphicsOGL.GL_BLEND);
        } finally { DoJaRuntime.current().shutdown(); }

        DesktopLauncher.launch(LaunchConfig.builder(StarApp.class).parameter("AppType", "FullApp").build());
        try {
            Graphics fresh = Image.createImage(32, 32).getGraphics();
            draw(fresh);
            check(fresh.getPixel(4, 4) == 0, "Fragment settings must not leak to another application");
        } finally { DoJaRuntime.current().shutdown(); }

        // Normal DoJa contexts keep their existing independent state.
        com.nttdocomo.ui.Graphics doja = com.nttdocomo.ui.Image.createImage(32, 32).getGraphics();
        draw(doja);
        check(doja.getRGBPixel(4, 4) == 0, "DoJa image defaults unchanged");
        System.out.println("star-ogl-transparency-probe-ok");
    }

    private static void draw(com.nttdocomo.ui.ogl.GraphicsOGL gl) {
        var buffers = com.nttdocomo.ui.ogl.DirectBufferFactory.getFactory();
        gl.beginDrawing();
        try {
            gl.glViewport(0, 0, 32, 32);
            gl.glMatrixMode(GraphicsOGL.GL_PROJECTION); gl.glLoadIdentity();
            gl.glMatrixMode(GraphicsOGL.GL_MODELVIEW); gl.glLoadIdentity();
            gl.glDisable(GraphicsOGL.GL_DEPTH_TEST); gl.glDisable(GraphicsOGL.GL_CULL_FACE);
            gl.glClearColor(0, 0, 1, 1); gl.glClear(GraphicsOGL.GL_COLOR_BUFFER_BIT);
            int[] texture = new int[1]; gl.glGenTextures(texture);
            gl.glBindTexture(GraphicsOGL.GL_TEXTURE_2D, texture[0]);
            gl.glTexImage2D(GraphicsOGL.GL_TEXTURE_2D, 0, GraphicsOGL.GL_RGBA, 2, 2, 0,
                    GraphicsOGL.GL_RGBA, GraphicsOGL.GL_UNSIGNED_SHORT_4_4_4_4,
                    buffers.allocateShortBuffer(new short[]{0x0000, 0x000f, 0x0f00, 0x0f0f}));
            gl.glTexParameterf(GraphicsOGL.GL_TEXTURE_2D, GraphicsOGL.GL_TEXTURE_MIN_FILTER, GraphicsOGL.GL_NEAREST);
            gl.glTexParameterf(GraphicsOGL.GL_TEXTURE_2D, GraphicsOGL.GL_TEXTURE_MAG_FILTER, GraphicsOGL.GL_NEAREST);
            gl.glTexParameterf(GraphicsOGL.GL_TEXTURE_2D, GraphicsOGL.GL_TEXTURE_WRAP_S, GraphicsOGL.GL_CLAMP_TO_EDGE);
            gl.glTexParameterf(GraphicsOGL.GL_TEXTURE_2D, GraphicsOGL.GL_TEXTURE_WRAP_T, GraphicsOGL.GL_CLAMP_TO_EDGE);
            gl.glEnable(GraphicsOGL.GL_TEXTURE_2D); gl.glColor4f(1, 1, 1, 1);
            gl.glEnableClientState(GraphicsOGL.GL_VERTEX_ARRAY);
            gl.glEnableClientState(GraphicsOGL.GL_TEXTURE_COORD_ARRAY);
            gl.glVertexPointer(2, GraphicsOGL.GL_FLOAT, 0, buffers.allocateFloatBuffer(new float[]{-1, 1, 1, 1, -1, -1, -1, -1, 1, 1, 1, -1}));
            gl.glTexCoordPointer(2, GraphicsOGL.GL_FLOAT, 0, buffers.allocateFloatBuffer(new float[]{0, 0, 1, 0, 0, 1, 0, 1, 1, 0, 1, 1}));
            gl.glDrawArrays(GraphicsOGL.GL_TRIANGLES, 0, 6); gl.glFlush();
            check(gl.glGetError() == 0, "Valid sprite upload and drawing");
            gl.glDeleteTextures(texture);
        } finally { gl.endDrawing(); }
    }

    private static void verifyTransparentSprite(Graphics graphics) {
        check(graphics.getPixel(4, 4) == 0xff0000ff, "Transparent black texels must preserve the blue background");
        check(graphics.getPixel(4, 24) == 0xff0000ff, "Zero alpha, not RGB color, determines transparency");
        check(graphics.getPixel(24, 4) == 0xff000000, "Opaque black details remain visible");
        check(graphics.getPixel(24, 24) == 0xff00ff00, "Opaque character pixels remain visible");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    public static final class StarApp extends com.docomostar.StarApplication {
        @Override public void started(int reason) { Display.setCurrent(new Canvas() { @Override public void paint(Graphics graphics) { } }); }
    }
}
