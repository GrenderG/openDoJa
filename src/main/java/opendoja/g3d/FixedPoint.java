package opendoja.g3d;

public final class FixedPoint {
    public static final int ONE = 4096;

    private FixedPoint() {
    }

    public static int fromFloat(float value) {
        return Math.round(value * ONE);
    }

    public static float toFloat(int value) {
        return value / (float) ONE;
    }

    public static int mul(int left, int right) {
        return (int) (((long) left * (long) right + 2048L) >> 12);
    }

    public static int mulTrunc(int left, int right) {
        return (int) (((long) left * (long) right) >> 12);
    }

    public static int sqrt(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("Negative sqrt");
        }
        return (int) Math.round(java.lang.Math.sqrt(value));
    }

    public static int sin(int angle) {
        return (int) Math.round(java.lang.Math.sin(angle * java.lang.Math.PI / 2048.0) * ONE);
    }

    public static int cos(int angle) {
        return sin(angle + 1024);
    }

    public static int atan2(int y, int x) {
        return (int) Math.round(java.lang.Math.atan2(y, x) * 2048.0 / java.lang.Math.PI);
    }
}
