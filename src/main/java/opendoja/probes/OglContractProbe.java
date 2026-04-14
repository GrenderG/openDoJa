package opendoja.probes;

import com.nttdocomo.ui.Graphics;
import com.nttdocomo.ui.Image;
import com.nttdocomo.ui.ogl.GraphicsOGL;

public final class OglContractProbe {
    private OglContractProbe() {
    }

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");

        GraphicsOGL graphics = (GraphicsOGL) Image.createImage(4, 4).getGraphics();
        int[] value = new int[1];
        graphics.glGetIntegerv(GraphicsOGL.GL_MAX_TEXTURE_UNITS, value);
        check(value[0] == 1, "GL_MAX_TEXTURE_UNITS should report the supported software texture unit count");
        check(graphics.glGetError() == GraphicsOGL.GL_NO_ERROR, "GL_MAX_TEXTURE_UNITS should not set an OGL error");

        graphics.glGetIntegerv(GraphicsOGL.GL_MAX_LIGHTS, value);
        check(value[0] == 8, "GL_MAX_LIGHTS should report the GLES light count");
        check(graphics.glGetError() == GraphicsOGL.GL_NO_ERROR, "GL_MAX_LIGHTS should not set an OGL error");

        graphics.glGetIntegerv(GraphicsOGL.GL_MAX_PALETTE_MATRICES_OES, value);
        check(value[0] == 32, "GL_MAX_PALETTE_MATRICES_OES should report the software palette capacity");
        check(graphics.glGetError() == GraphicsOGL.GL_NO_ERROR,
                "GL_MAX_PALETTE_MATRICES_OES should not set an OGL error");

        graphics.glGetIntegerv(GraphicsOGL.GL_MAX_VERTEX_UNITS_OES, value);
        check(value[0] == 8, "GL_MAX_VERTEX_UNITS_OES should report the supported weighted vertex unit count");
        check(graphics.glGetError() == GraphicsOGL.GL_NO_ERROR,
                "GL_MAX_VERTEX_UNITS_OES should not set an OGL error");

        assertThrows("null params", NullPointerException.class,
                () -> graphics.glGetIntegerv(GraphicsOGL.GL_MAX_TEXTURE_UNITS, null));
        assertThrows("empty params", IllegalArgumentException.class,
                () -> graphics.glGetIntegerv(GraphicsOGL.GL_MAX_TEXTURE_UNITS, new int[0]));

        graphics.glGetIntegerv(-1, value);
        check(graphics.glGetError() == GraphicsOGL.GL_INVALID_ENUM, "invalid pname should set GL_INVALID_ENUM");
        check(graphics.glGetError() == GraphicsOGL.GL_NO_ERROR, "glGetError should reset after read");

        System.out.println("OGL contract probe OK");
    }

    private static void assertThrows(String label, Class<? extends Throwable> expected, ThrowingRunnable action) {
        try {
            action.run();
        } catch (Throwable throwable) {
            if (expected.isInstance(throwable)) {
                return;
            }
            throw new IllegalStateException(label + " threw " + throwable.getClass().getName()
                    + " instead of " + expected.getName(), throwable);
        }
        throw new IllegalStateException(label + " did not throw " + expected.getName());
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private interface ThrowingRunnable {
        void run() throws Throwable;
    }
}
