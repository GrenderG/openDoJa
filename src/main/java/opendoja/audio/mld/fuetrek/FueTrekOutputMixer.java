package opendoja.audio.mld.fuetrek;

/** SDK DSModule's PCM boundary, before DirectSound's spatial renderer. */
public final class FueTrekOutputMixer {
    private static final double EXPORTED_MIX_SCALE = 2097152.0;

    private FueTrekOutputMixer() {}

    private static short quantize(double value) {
        // MSVC061f8 returns an invalid int64 as 8000000000000000; the caller
        // uses its low 32 bits, SAR8, then clips. Java's int cast would saturate.
        long converted = !Double.isFinite(value) || value >= 0x1p63 || value < -0x1p63
                ? Long.MIN_VALUE : (long) value;
        int shifted = ((int) converted) >> 8;
        return (short) Math.max(-32767, Math.min(32767, shifted));
    }

    /**
     * Native04110 routing: lane0 is stereo, lane1 is reserved, lanes2..9
     * are spatial sources. Active spatial sources leave the stereo sum;
     * mode2 folds the complete mix into the first mono source.
     */
    public static short[][] mixLanes(int[][] lanes, int spatialCount, int spatialMode,
                                     float factorA, float factorB) {
        if (lanes.length != 10 || spatialCount < 0 || spatialCount > 8)
            throw new IllegalArgumentException("Expected 10 lanes and 0..8 spatial sources");
        if ((lanes[0].length & 1) != 0)
            throw new IllegalArgumentException("Expected interleaved stereo frames");
        int frames = lanes[0].length / 2;
        for (int[] lane : lanes) {
            if (lane.length != frames * 2) throw new IllegalArgumentException("Mismatched lane length");
        }
        short[][] result = new short[spatialCount + 1][];
        result[0] = new short[frames * 2];
        for (int i = 1; i < result.length; i++) result[i] = new short[frames];
        boolean monoMix = spatialCount > 0 && spatialMode == 2;
        for (int frame = 0; frame < frames; frame++) {
            int index = frame * 2;
            long left = lanes[0][index], right = lanes[0][index + 1];
            for (int i = spatialCount; i < 8; i++) {
                left += lanes[i + 2][index];
                right += lanes[i + 2][index + 1];
            }
            if (!monoMix) {
                result[0][index] = quantize(left);
                // The right accumulator is stored as float32 before conversion.
                result[0][index + 1] = quantize((float) right);
            }
            for (int i = 0; i < spatialCount; i++) {
                long sum;
                if (monoMix) {
                    if (i > 0) continue;
                    sum = left + right;
                    for (int j = 0; j < spatialCount; j++)
                        sum += (long) lanes[j + 2][index] + lanes[j + 2][index + 1];
                } else {
                    sum = (long) lanes[i + 2][index] + lanes[i + 2][index + 1];
                }
                result[i + 1][frame] = quantize(sum * (double) factorB * (double) factorA);
            }
        }
        return result;
    }

    /** Convert the sampler's exported native mix units to little-endian PCM. */
    public static int encodeExported(float[] samples, int sampleOffset, int frames,
                                     byte[] output, int outputOffset) {
        int start = outputOffset;
        for (int i = 0; i < frames * 2; i++) {
            short sample = quantize(samples[sampleOffset + i] * EXPORTED_MIX_SCALE);
            output[outputOffset++] = (byte) sample;
            output[outputOffset++] = (byte) (sample >>> 8);
        }
        return outputOffset - start;
    }
}
