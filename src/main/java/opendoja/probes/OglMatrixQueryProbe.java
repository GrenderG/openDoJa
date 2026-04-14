package opendoja.probes;

import com.nttdocomo.ui.Graphics;
import com.nttdocomo.ui.Image;
import com.nttdocomo.ui.ogl.GraphicsOGL;

/**
 * Verifies that matrix queries reflect the current GLES matrix state instead of
 * falling through the default no-op interface methods.
 */
public final class OglMatrixQueryProbe {
    private OglMatrixQueryProbe() {
    }

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");

        GraphicsOGL ogl = (GraphicsOGL) Image.createImage(8, 8).getGraphics();
        float[] projection = new float[16];
        float[] modelView = new float[16];

        ogl.beginDrawing();
        try {
            ogl.glMatrixMode(GraphicsOGL.GL_PROJECTION);
            ogl.glLoadIdentity();
            ogl.glFrustumf(-0.5f, 0.5f, -0.25f, 0.25f, 1f, 9f);
            ogl.glGetFloatv(GraphicsOGL.GL_PROJECTION_MATRIX, projection);

            check(close(projection[0], 2f), "projection[0] should reflect frustum scale");
            check(close(projection[5], 4f), "projection[5] should reflect frustum scale");
            check(close(projection[10], -1.25f), "projection[10] should reflect depth term");
            check(close(projection[11], -1f), "projection[11] should reflect perspective term");
            check(close(projection[14], -2.25f), "projection[14] should reflect depth translation");

            ogl.glMatrixMode(GraphicsOGL.GL_MODELVIEW);
            ogl.glLoadIdentity();
            ogl.glTranslatef(1f, 2f, 3f);
            ogl.glGetFloatv(GraphicsOGL.GL_MODELVIEW_MATRIX, modelView);
            check(close(modelView[12], 1f), "model-view translation x should be queryable");
            check(close(modelView[13], 2f), "model-view translation y should be queryable");
            check(close(modelView[14], 3f), "model-view translation z should be queryable");

            check(ogl.glGetError() == GraphicsOGL.GL_NO_ERROR, "matrix query probe should not set a GL error");
        } finally {
            ogl.endDrawing();
        }

        System.out.println("OGL matrix query probe OK");
    }

    private static boolean close(float actual, float expected) {
        return Math.abs(actual - expected) < 0.0001f;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
