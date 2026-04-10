package opendoja.g3d;

/**
 * Host-internal texture-coordinate transform state decoded from scene formats
 * that carry M3G-style Texture2D transforms.
 */
public final class TextureCoordinateTransform {
    public static final TextureCoordinateTransform IDENTITY = new TextureCoordinateTransform(0f, 0f, null);

    private final float translationU;
    private final float translationV;
    private final LinearTranslation linearTranslation;

    public TextureCoordinateTransform(float translationU, float translationV,
                                      LinearTranslation linearTranslation) {
        this.translationU = translationU;
        this.translationV = translationV;
        this.linearTranslation = linearTranslation;
    }

    public float pixelTranslationU(int time, int textureWidth) {
        float normalized = linearTranslation == null ? translationU : linearTranslation.valueU(time);
        return normalized * textureWidth;
    }

    public float pixelTranslationV(int time, int textureHeight) {
        float normalized = linearTranslation == null ? translationV : linearTranslation.valueV(time);
        return normalized * textureHeight;
    }

    public boolean isIdentity() {
        return linearTranslation == null && translationU == 0f && translationV == 0f;
    }

    public static final class LinearTranslation {
        private final int duration;
        private final boolean loop;
        private final int[] times;
        private final float[] uValues;
        private final float[] vValues;

        public LinearTranslation(int duration, boolean loop, int[] times, float[] uValues, float[] vValues) {
            this.duration = duration;
            this.loop = loop;
            this.times = times == null ? new int[0] : times.clone();
            this.uValues = uValues == null ? new float[0] : uValues.clone();
            this.vValues = vValues == null ? new float[0] : vValues.clone();
        }

        float valueU(int time) {
            return valueAt(time, uValues);
        }

        float valueV(int time) {
            return valueAt(time, vValues);
        }

        private float valueAt(int time, float[] values) {
            if (times.length == 0 || values.length == 0) {
                return 0f;
            }
            if (times.length == 1 || values.length == 1) {
                return values[0];
            }
            int effectiveTime = time;
            if (loop && duration > 0) {
                effectiveTime = java.lang.Math.floorMod(time, duration);
            }
            if (effectiveTime <= times[0]) {
                return values[0];
            }
            for (int i = 0; i + 1 < times.length && i + 1 < values.length; i++) {
                int start = times[i];
                int end = times[i + 1];
                if (effectiveTime > end) {
                    continue;
                }
                if (end <= start) {
                    return values[i + 1];
                }
                float ratio = (effectiveTime - start) / (float) (end - start);
                return values[i] + (values[i + 1] - values[i]) * ratio;
            }
            return values[java.lang.Math.min(values.length, times.length) - 1];
        }
    }
}
