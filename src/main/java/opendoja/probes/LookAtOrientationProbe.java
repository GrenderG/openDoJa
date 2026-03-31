package opendoja.probes;

import com.nttdocomo.opt.ui.j3d.AffineTrans;
import com.nttdocomo.opt.ui.j3d.Vector3D;
import com.nttdocomo.ui.util3d.Transform;

/**
 * Verifies the documented DoJa lookAt contract shared by the fixed-point and float transform
 * helpers: the second vector is the target point, so the forward axis is `(look - position)`.
 */
public final class LookAtOrientationProbe {
    private LookAtOrientationProbe() {
    }

    public static void main(String[] args) {
        DemoLog.enableInfoLogging();

        Vector3D position = new Vector3D(4096, 0, 0);
        Vector3D look = new Vector3D(4096, 0, 4096);
        Vector3D up = new Vector3D(0, 4096, 0);

        AffineTrans affine = new AffineTrans();
        affine.lookAt(position, look, up);
        assertFixed("AffineTrans", affine.m00, affine.m11, affine.m22, affine.m03, affine.m13, affine.m23);

        Transform transform = new Transform();
        transform.lookAt(
                new com.nttdocomo.ui.util3d.Vector3D(1f, 0f, 0f),
                new com.nttdocomo.ui.util3d.Vector3D(1f, 0f, 1f),
                new com.nttdocomo.ui.util3d.Vector3D(0f, 1f, 0f)
        );
        assertFloat("Transform", transform);
    }

    private static void assertFixed(String label, int m00, int m11, int m22, int m03, int m13, int m23) {
        if (m00 != -4096 || m11 != -4096 || m22 != 4096 || m03 != 4096 || m13 != 0 || m23 != 0) {
            throw new IllegalStateException(String.format(
                    "%s lookAt mismatch m00=%d m11=%d m22=%d t=(%d,%d,%d)",
                    label,
                    m00,
                    m11,
                    m22,
                    m03,
                    m13,
                    m23
            ));
        }
        DemoLog.info(LookAtOrientationProbe.class, label + " OK");
    }

    private static void assertFloat(String label, Transform transform) {
        float[] matrix = new float[16];
        transform.get(matrix);
        if (matrix[0] != -1f || matrix[5] != -1f || matrix[10] != 1f
                || matrix[3] != 1f || matrix[7] != 0f || matrix[11] != 0f) {
            throw new IllegalStateException(String.format(
                    "%s lookAt mismatch m00=%f m11=%f m22=%f t=(%f,%f,%f)",
                    label,
                    matrix[0],
                    matrix[5],
                    matrix[10],
                    matrix[3],
                    matrix[7],
                    matrix[11]
            ));
        }
        DemoLog.info(LookAtOrientationProbe.class, label + " OK");
    }
}
