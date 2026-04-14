package opendoja.probes;

import com.nttdocomo.ui.ogl.math.Matrix4f;
import com.nttdocomo.ui.ogl.math.Point4f;

/**
 * Verifies the DoJa OGL math contract that Matrix4f.rotate() takes degrees.
 */
public final class Matrix4fRotationProbe {
    private static final float EPSILON = 0.001f;

    private Matrix4fRotationProbe() {
    }

    public static void main(String[] args) {
        Matrix4f zero = new Matrix4f();
        for (int i = 0; i < 16; i++) {
            assertApprox("ctor[" + i + "]", 0f, zero.m[i]);
        }

        Matrix4f matrix = new Matrix4f();
        matrix.setIdentity();
        matrix.rotate(90f, 0f, 0f, 1f);

        Point4f point = new Point4f(1f, 0f, 0f, 1f);
        matrix.transform(point);
        assertApprox("x", 0f, point.x);
        assertApprox("y", 1f, point.y);
        assertApprox("z", 0f, point.z);
        assertApprox("w", 1f, point.w);

        Matrix4f singular = new Matrix4f();
        expectIllegalArgument(() -> singular.invert());

        System.out.println("Matrix4f rotation probe OK");
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
        } catch (Throwable throwable) {
            throw new IllegalStateException("expected IllegalArgumentException but got " + throwable.getClass().getName(), throwable);
        }
        throw new IllegalStateException("expected IllegalArgumentException");
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
    }
}
