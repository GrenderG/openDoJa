package opendoja.g3d;

public final class MascotActionTableData {
    static final int[] IDENTITY_AFFINE = {
            FixedPoint.ONE, 0, 0, 0,
            0, FixedPoint.ONE, 0, 0,
            0, 0, FixedPoint.ONE, 0
    };

    private final Action[] actions;
    private final int numBones;

    public MascotActionTableData(Action[] actions, int numBones) {
        this.actions = actions == null ? new Action[0] : actions;
        this.numBones = Math.max(0, numBones);
    }

    public int numActions() {
        return actions.length;
    }

    public int numBones() {
        return numBones;
    }

    public int maxFrame(int action) {
        return actions[action].maxFrame() << 16;
    }

    public int patternForFrame(int action, int frame, int fallbackMask) {
        return actions[action].patternForFrame(frame, fallbackMask);
    }

    public void writeMatricesFixed(int action, int frame, int[] destination) {
        actions[action].writeMatrices(frame, destination);
    }

    public void writeMatrices(int action, int frame, float[] destination) {
        if (destination == null) {
            return;
        }
        int[] matrices = new int[numBones * 12];
        writeMatricesFixed(action, frame, matrices);
        for (int i = 0; i < matrices.length && i < destination.length; i += 12) {
            destination[i] = matrices[i] / 4096f;
            destination[i + 1] = matrices[i + 1] / 4096f;
            destination[i + 2] = matrices[i + 2] / 4096f;
            destination[i + 3] = matrices[i + 3];
            destination[i + 4] = matrices[i + 4] / 4096f;
            destination[i + 5] = matrices[i + 5] / 4096f;
            destination[i + 6] = matrices[i + 6] / 4096f;
            destination[i + 7] = matrices[i + 7];
            destination[i + 8] = matrices[i + 8] / 4096f;
            destination[i + 9] = matrices[i + 9] / 4096f;
            destination[i + 10] = matrices[i + 10] / 4096f;
            destination[i + 11] = matrices[i + 11];
        }
    }

    public static final class Action {
        private final int maxFrame;
        private final BoneAction[] boneActions;
        private final int[] dynamicFrames;
        private final int[] dynamicPatterns;

        public Action(int maxFrame, BoneAction[] boneActions, int[] dynamicFrames, int[] dynamicPatterns) {
            this.maxFrame = maxFrame;
            this.boneActions = boneActions == null ? new BoneAction[0] : boneActions;
            this.dynamicFrames = dynamicFrames == null ? new int[0] : dynamicFrames;
            this.dynamicPatterns = dynamicPatterns == null ? new int[0] : dynamicPatterns;
        }

        public int maxFrame() {
            return maxFrame;
        }

        public int patternForFrame(int frame, int fallbackMask) {
            int selected = fallbackMask;
            int frameKey = Math.max(0, frame) >> 16;
            for (int i = 0; i < dynamicFrames.length; i++) {
                if (dynamicFrames[i] <= frameKey) {
                    selected = dynamicPatterns[i];
                } else {
                    break;
                }
            }
            return selected;
        }

        void writeMatrices(int frame, int[] destination) {
            if (destination == null) {
                return;
            }
            for (int i = 0; i < boneActions.length; i++) {
                BoneAction boneAction = boneActions[i];
                int offset = i * 12;
                if (boneAction == null) {
                    System.arraycopy(IDENTITY_AFFINE, 0, destination, offset, 12);
                    continue;
                }
                boneAction.writeMatrix(frame, destination, offset);
            }
        }
    }

    public static final class BoneAction {
        private final int type;
        private final int[] staticMatrix;
        private final Animation translate;
        private final Animation scale;
        private final Animation rotate;
        private final RollAnimation roll;

        public BoneAction(int type, int[] staticMatrix, Animation translate, Animation scale, Animation rotate, RollAnimation roll) {
            this.type = type;
            this.staticMatrix = staticMatrix;
            this.translate = translate;
            this.scale = scale;
            this.rotate = rotate;
            this.roll = roll;
        }

        void writeMatrix(int frame, int[] destination, int offset) {
            switch (type) {
                case 0 -> System.arraycopy(staticMatrix, 0, destination, offset, 12);
                case 1 -> System.arraycopy(IDENTITY_AFFINE, 0, destination, offset, 12);
                case 2 -> {
                    int[] translateValues = new int[3];
                    int[] scaleValues = new int[3];
                    int[] rotateValues = new int[3];
                    translate.get(frame, translateValues);
                    scale.get(frame, scaleValues);
                    rotate.get(frame, rotateValues);
                    writeTransform(destination, offset,
                            translateValues[0], translateValues[1], translateValues[2],
                            scaleValues[0], scaleValues[1], scaleValues[2],
                            rotateValues[0], rotateValues[1], rotateValues[2],
                            roll.get(frame));
                }
                case 3 -> {
                    int[] translateValues = translate.firstValue();
                    int[] rotateValues = new int[3];
                    rotate.get(frame, rotateValues);
                    writeTransform(destination, offset,
                            translateValues[0], translateValues[1], translateValues[2],
                            FixedPoint.ONE, FixedPoint.ONE, FixedPoint.ONE,
                            rotateValues[0], rotateValues[1], rotateValues[2],
                            roll.firstValue());
                }
                case 4 -> {
                    int[] rotateValues = new int[3];
                    rotate.get(frame, rotateValues);
                    writeTransform(destination, offset,
                            0, 0, 0,
                            FixedPoint.ONE, FixedPoint.ONE, FixedPoint.ONE,
                            rotateValues[0], rotateValues[1], rotateValues[2],
                            roll.get(frame));
                }
                case 5 -> {
                    int[] rotateValues = new int[3];
                    rotate.get(frame, rotateValues);
                    writeTransform(destination, offset,
                            0, 0, 0,
                            FixedPoint.ONE, FixedPoint.ONE, FixedPoint.ONE,
                            rotateValues[0], rotateValues[1], rotateValues[2],
                            0);
                }
                case 6 -> {
                    int[] translateValues = new int[3];
                    int[] rotateValues = new int[3];
                    translate.get(frame, translateValues);
                    rotate.get(frame, rotateValues);
                    writeTransform(destination, offset,
                            translateValues[0], translateValues[1], translateValues[2],
                            FixedPoint.ONE, FixedPoint.ONE, FixedPoint.ONE,
                            rotateValues[0], rotateValues[1], rotateValues[2],
                            roll.get(frame));
                }
                default -> System.arraycopy(IDENTITY_AFFINE, 0, destination, offset, 12);
            }
        }

        private static void writeTransform(int[] matrix, int offset,
                                           int tx, int ty, int tz,
                                           int sx, int sy, int sz,
                                           int rx, int ry, int rz,
                                           int rollAngle) {
            writeRotateRoll(matrix, offset, rx, ry, rz, rollAngle);
            applyScale(matrix, offset, sx, sy, sz);
            matrix[offset + 3] = tx;
            matrix[offset + 7] = ty;
            matrix[offset + 11] = tz;
        }

        private static void applyScale(int[] matrix, int offset, int x, int y, int z) {
            matrix[offset] = FixedPoint.mul(matrix[offset], x);
            matrix[offset + 4] = FixedPoint.mul(matrix[offset + 4], x);
            matrix[offset + 8] = FixedPoint.mul(matrix[offset + 8], x);
            matrix[offset + 1] = FixedPoint.mul(matrix[offset + 1], y);
            matrix[offset + 5] = FixedPoint.mul(matrix[offset + 5], y);
            matrix[offset + 9] = FixedPoint.mul(matrix[offset + 9], y);
            matrix[offset + 2] = FixedPoint.mul(matrix[offset + 2], z);
            matrix[offset + 6] = FixedPoint.mul(matrix[offset + 6], z);
            matrix[offset + 10] = FixedPoint.mul(matrix[offset + 10], z);
        }

        private static void writeRotateRoll(int[] matrix, int offset, int x, int y, int z, int angle) {
            System.arraycopy(IDENTITY_AFFINE, 0, matrix, offset, 12);
            int[] normalized = normalizeRotateVector(x, y, z);
            x = normalized[0];
            y = normalized[1];
            z = normalized[2];
            if (x == 0 && y == 0) {
                if (z < 0) {
                    matrix[offset + 5] = -FixedPoint.ONE;
                    matrix[offset + 10] = -FixedPoint.ONE;
                }
                applyRoll(matrix, offset, angle);
                return;
            }
            int xx = FixedPoint.mulTrunc(x, x);
            int yy = FixedPoint.mulTrunc(y, y);
            int xy = FixedPoint.mulTrunc(x, y);
            int denominator = xx + yy;
            int a = denominator == 0 ? 0 : (int) ((((long) FixedPoint.ONE - z) << 12) / denominator);
            matrix[offset] = FixedPoint.mulTrunc(a, yy) + z;
            matrix[offset + 1] = -FixedPoint.mulTrunc(xy, a);
            matrix[offset + 2] = x;
            matrix[offset + 4] = matrix[offset + 1];
            matrix[offset + 5] = FixedPoint.mulTrunc(a, xx) + z;
            matrix[offset + 6] = y;
            matrix[offset + 8] = -x;
            matrix[offset + 9] = -y;
            matrix[offset + 10] = z;
            applyRoll(matrix, offset, angle);
        }

        private static int[] normalizeRotateVector(int x, int y, int z) {
            long lengthSquared = (long) x * x + (long) y * y + (long) z * z;
            if (lengthSquared == 0L) {
                return new int[]{0, 0, FixedPoint.ONE};
            }
            int length = FixedPoint.sqrt((int) lengthSquared);
            return new int[]{
                    (x << 12) / length,
                    (y << 12) / length,
                    (z << 12) / length
            };
        }

        private static void applyRoll(int[] matrix, int offset, int angle) {
            if (angle == 0) {
                return;
            }
            int sin = FixedPoint.sin(angle);
            int cos = FixedPoint.cos(angle);
            int m00 = matrix[offset];
            int m10 = matrix[offset + 4];
            int m20 = matrix[offset + 8];
            int m01 = matrix[offset + 1];
            int m11 = matrix[offset + 5];
            int m21 = matrix[offset + 9];
            matrix[offset] = FixedPoint.mul(m00, cos) + FixedPoint.mul(m01, sin);
            matrix[offset + 4] = FixedPoint.mul(m10, cos) + FixedPoint.mul(m11, sin);
            matrix[offset + 8] = FixedPoint.mul(m20, cos) + FixedPoint.mul(m21, sin);
            matrix[offset + 1] = FixedPoint.mul(m01, cos) - FixedPoint.mul(m00, sin);
            matrix[offset + 5] = FixedPoint.mul(m11, cos) - FixedPoint.mul(m10, sin);
            matrix[offset + 9] = FixedPoint.mul(m21, cos) - FixedPoint.mul(m20, sin);
        }
    }

    public static final class Animation {
        private final int[] keys;
        private final int[][] values;

        public Animation(int count) {
            this.keys = new int[count];
            this.values = new int[count][3];
        }

        public void set(int index, int keyFrame, int x, int y, int z) {
            keys[index] = keyFrame;
            values[index][0] = x;
            values[index][1] = y;
            values[index][2] = z;
        }

        public void get(int frame, int[] destination) {
            int max = keys.length - 1;
            if (max < 0) {
                destination[0] = 0;
                destination[1] = 0;
                destination[2] = 0;
                return;
            }
            int clampedFrame = java.lang.Math.max(0, frame);
            int frameKey = clampedFrame >> 16;
            if (frameKey >= keys[max]) {
                int[] value = values[max];
                destination[0] = value[0];
                destination[1] = value[1];
                destination[2] = value[2];
                return;
            }
            for (int i = max - 1; i >= 0; i--) {
                int key = keys[i];
                if (key > frameKey) {
                    continue;
                }
                int[] value = values[i];
                long localFrame = (long) clampedFrame - ((long) key << 16);
                if (localFrame <= 0L) {
                    destination[0] = value[0];
                    destination[1] = value[1];
                    destination[2] = value[2];
                    return;
                }
                int nextKey = keys[i + 1];
                if (nextKey <= key) {
                    destination[0] = value[0];
                    destination[1] = value[1];
                    destination[2] = value[2];
                    return;
                }
                int[] next = values[i + 1];
                int fraction = (int) (localFrame / (nextKey - key));
                destination[0] = interpolate(value[0], next[0], fraction);
                destination[1] = interpolate(value[1], next[1], fraction);
                destination[2] = interpolate(value[2], next[2], fraction);
                return;
            }
            int[] value = values[0];
            destination[0] = value[0];
            destination[1] = value[1];
            destination[2] = value[2];
        }

        int[] firstValue() {
            return values.length == 0 ? new int[3] : values[0].clone();
        }
    }

    public static final class RollAnimation {
        private final int[] keys;
        private final int[] values;

        public RollAnimation(int count) {
            this.keys = new int[count];
            this.values = new int[count];
        }

        public void set(int index, int keyFrame, int value) {
            keys[index] = keyFrame;
            values[index] = value;
        }

        public int get(int frame) {
            int max = keys.length - 1;
            if (max < 0) {
                return 0;
            }
            int clampedFrame = java.lang.Math.max(0, frame);
            int frameKey = clampedFrame >> 16;
            if (frameKey >= keys[max]) {
                return values[max];
            }
            for (int i = max - 1; i >= 0; i--) {
                int key = keys[i];
                if (key > frameKey) {
                    continue;
                }
                int value = values[i];
                long localFrame = (long) clampedFrame - ((long) key << 16);
                if (localFrame <= 0L) {
                    return value;
                }
                int nextKey = keys[i + 1];
                if (nextKey <= key) {
                    return value;
                }
                int nextValue = values[i + 1];
                int fraction = (int) (localFrame / (nextKey - key));
                return interpolate(value, nextValue, fraction);
            }
            return values[0];
        }

        int firstValue() {
            return values.length == 0 ? 0 : values[0];
        }
    }

    private static int interpolate(int value, int nextValue, int fraction) {
        int delta = nextValue - value;
        return value + (int) ((((long) delta * fraction) + 0x8000L) >> 16);
    }
}
