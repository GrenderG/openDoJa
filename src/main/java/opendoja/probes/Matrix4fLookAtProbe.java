package opendoja.probes;

import com.nttdocomo.ui.ogl.math.Matrix4f;
import com.nttdocomo.ui.ogl.math.Point3f;
import com.nttdocomo.ui.ogl.math.Vector3f;

/**
 * Verifies the DoJa OGL math contract for Matrix4f.lookAt().
 */
public final class Matrix4fLookAtProbe {
    private static final float EPSILON = 0.001f;

    private Matrix4fLookAtProbe() {
    }

    public static void main(String[] args) {
        Matrix4f matrix = new Matrix4f();
        matrix.lookAt(new Point3f(1f, 2f, 3f), new Point3f(4f, 6f, 3f), new Vector3f(0f, 0f, 1f));

        assertApprox("m[0]", 0.8f, matrix.m[0]);
        assertApprox("m[1]", 0f, matrix.m[1]);
        assertApprox("m[2]", -0.6f, matrix.m[2]);
        assertApprox("m[4]", -0.6f, matrix.m[4]);
        assertApprox("m[5]", 0f, matrix.m[5]);
        assertApprox("m[6]", -0.8f, matrix.m[6]);
        assertApprox("m[8]", 0f, matrix.m[8]);
        assertApprox("m[9]", 1f, matrix.m[9]);
        assertApprox("m[10]", 0f, matrix.m[10]);
        assertApprox("m[12]", 0.4f, matrix.m[12]);
        assertApprox("m[13]", -3f, matrix.m[13]);
        assertApprox("m[14]", 2.2f, matrix.m[14]);
        assertApprox("m[15]", 1f, matrix.m[15]);

        expectIllegalArgument(() -> new Matrix4f().lookAt(
                new Point3f(1f, 1f, 1f), new Point3f(1f, 1f, 1f), new Vector3f(0f, 1f, 0f)));
        expectIllegalArgument(() -> new Matrix4f().lookAt(
                new Point3f(0f, 0f, 0f), new Point3f(0f, 0f, -1f), new Vector3f(0f, 0f, 0f)));
        expectIllegalArgument(() -> new Matrix4f().lookAt(
                new Point3f(0f, 0f, 0f), new Point3f(0f, 0f, -1f), new Vector3f(0f, 0f, 1f)));

        System.out.println("Matrix4f lookAt probe OK");
    }

    private static void assertApprox(String label, float expected, float actual) {
        if (Math.abs(expected - actual) > EPSILON) {
            throw new IllegalStateException(label + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void expectIllegalArgument(ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new IllegalStateException("expected IllegalArgumentException");
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
    }
}
