package com.acrodea.xf3.math;

public final class xfMath {
    private xfMath() {
    }

    public static float abs(float value) {
        return Math.abs(value);
    }

    public static void matrixIdentity(xfMatrix4 matrix) {
        if (matrix != null) {
            matrix.setIdentity();
        }
    }

    public static void matrixTranslation(xfMatrix4 matrix, xfVector3 translation) {
        if (matrix == null) {
            return;
        }
        matrix.setIdentity();
        if (translation != null) {
            matrix.m[3][0] = translation.x;
            matrix.m[3][1] = translation.y;
            matrix.m[3][2] = translation.z;
        }
    }

    public static void matrixLookAt(xfMatrix4 matrix, xfVector3 eye, xfVector3 center, xfVector3 up) {
        if (matrix == null) {
            return;
        }
        xfVector3 actualEye = eye == null ? new xfVector3() : eye;
        xfVector3 actualCenter = center == null ? new xfVector3() : center;
        xfVector3 actualUp = up == null ? new xfVector3(0f, 1f, 0f) : up;

        xfVector3 forward = normalize(subtract(actualCenter, actualEye));
        xfVector3 right = normalize(cross(forward, actualUp));
        if (lengthSquared(right) == 0f) {
            right = new xfVector3(-1f, 0f, 0f);
        }
        xfVector3 correctedUp = normalize(cross(right, forward));

        matrix.setIdentity();
        matrix.m[0][0] = right.x;
        matrix.m[0][1] = right.y;
        matrix.m[0][2] = right.z;
        matrix.m[0][3] = 0f;

        matrix.m[1][0] = correctedUp.x;
        matrix.m[1][1] = correctedUp.y;
        matrix.m[1][2] = correctedUp.z;
        matrix.m[1][3] = 0f;

        matrix.m[2][0] = forward.x;
        matrix.m[2][1] = forward.y;
        matrix.m[2][2] = forward.z;
        matrix.m[2][3] = 0f;

        matrix.m[3][0] = actualEye.x;
        matrix.m[3][1] = actualEye.y;
        matrix.m[3][2] = actualEye.z;
        matrix.m[3][3] = 1f;
    }

    public static void matrixInverseFast(xfMatrix4 matrix) {
        if (matrix == null) {
            return;
        }
        double[][] augmented = new double[4][8];
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 4; column++) {
                augmented[row][column] = matrix.m[row][column];
            }
            augmented[row][row + 4] = 1d;
        }
        for (int pivot = 0; pivot < 4; pivot++) {
            int swap = pivot;
            for (int row = pivot + 1; row < 4; row++) {
                if (Math.abs(augmented[row][pivot]) > Math.abs(augmented[swap][pivot])) {
                    swap = row;
                }
            }
            if (Math.abs(augmented[swap][pivot]) < 1e-8d) {
                matrix.setIdentity();
                return;
            }
            if (swap != pivot) {
                double[] tmp = augmented[pivot];
                augmented[pivot] = augmented[swap];
                augmented[swap] = tmp;
            }
            double scale = augmented[pivot][pivot];
            for (int column = 0; column < 8; column++) {
                augmented[pivot][column] /= scale;
            }
            for (int row = 0; row < 4; row++) {
                if (row == pivot) {
                    continue;
                }
                double factor = augmented[row][pivot];
                for (int column = 0; column < 8; column++) {
                    augmented[row][column] -= factor * augmented[pivot][column];
                }
            }
        }
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 4; column++) {
                matrix.m[row][column] = (float) augmented[row][column + 4];
            }
        }
    }

    private static xfVector3 subtract(xfVector3 left, xfVector3 right) {
        return new xfVector3(left.x - right.x, left.y - right.y, left.z - right.z);
    }

    private static xfVector3 normalize(xfVector3 value) {
        float length = (float) Math.sqrt(lengthSquared(value));
        if (length == 0f) {
            return new xfVector3();
        }
        return new xfVector3(value.x / length, value.y / length, value.z / length);
    }

    private static float lengthSquared(xfVector3 value) {
        return value.x * value.x + value.y * value.y + value.z * value.z;
    }

    private static float dot(xfVector3 left, xfVector3 right) {
        return left.x * right.x + left.y * right.y + left.z * right.z;
    }

    private static xfVector3 cross(xfVector3 left, xfVector3 right) {
        return new xfVector3(
                left.y * right.z - left.z * right.y,
                left.z * right.x - left.x * right.z,
                left.x * right.y - left.y * right.x);
    }
}
