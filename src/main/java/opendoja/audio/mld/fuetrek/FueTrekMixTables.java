package opendoja.audio.mld.fuetrek;

import java.io.IOException;
import java.io.InputStream;

/**
 * Exact upstream gain and stereo tables recovered from {@code MFiSynth_ft.dll}.
 */
final class FueTrekMixTables {
    private static final String RESOURCE_NAME = "fuetrek-mix-tables.bin";
    private static final int MAGIC = 0x584d5446; // FTMX
    private static final int VERSION = 1;
    private static final Tables TABLES = loadTables();
    private static final int[] GAIN_CURVE = TABLES.gainCurve;
    private static final int[] STEREO_CURVE = TABLES.stereoCurve;

    private FueTrekMixTables() {
    }

    static int amplitudeToGainByte(float amplitude) {
        if (amplitude <= 0.0f) {
            return 0;
        }
        int target = Math.min(0x7fff, Math.round(amplitude * 32767.0f));
        int bestIndex = 0;
        int bestDelta = Integer.MAX_VALUE;
        for (int i = 0; i < GAIN_CURVE.length; i++) {
            int delta = Math.abs(GAIN_CURVE[i] - target);
            if (delta < bestDelta) {
                bestDelta = delta;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    static int gainWord(int value) {
        return GAIN_CURVE[clamp(value, 0, 0x7f)];
    }

    static int stereoWord(int signedPan) {
        return STEREO_CURVE[clamp(signedPan + 64, 0, STEREO_CURVE.length - 1)];
    }

    static int noteVelocityByte(float velocity) {
        if (velocity <= 0.0f) {
            return 0;
        }
        // SynthModule consumes the full MIDI 7-bit domain. The MFi player
        // performs its separate 6-bit expansion before calling this layer.
        return clamp(Math.round(velocity * 127.0f), 0, 0x7f);
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private static Tables loadTables() {
        byte[] data = readResourceBytes(RESOURCE_NAME);
        Reader reader = new Reader(data);
        int magic = reader.u32();
        if (magic != MAGIC) {
            throw new IllegalStateException("Invalid FueTrek mix table magic: 0x" + Integer.toHexString(magic));
        }
        int version = reader.u16();
        if (version != VERSION) {
            throw new IllegalStateException("Unsupported FueTrek mix table version: " + version);
        }
        int gainCount = reader.u16();
        int stereoCount = reader.u16();
        reader.u16(); // reserved
        int[] gainCurve = new int[gainCount];
        int[] stereoCurve = new int[stereoCount];
        for (int i = 0; i < gainCurve.length; i++) {
            gainCurve[i] = reader.u16();
        }
        for (int i = 0; i < stereoCurve.length; i++) {
            stereoCurve[i] = reader.u16();
        }
        reader.expectFullyConsumed();
        if (gainCount != 128 || stereoCount != 129) {
            throw new IllegalStateException(
                    "Unexpected FueTrek mix table sizes: gain=" + gainCount + " stereo=" + stereoCount);
        }
        return new Tables(gainCurve, stereoCurve);
    }

    private static byte[] readResourceBytes(String resourceName) {
        try (InputStream input = FueTrekMixTables.class.getResourceAsStream(resourceName)) {
            if (input == null) {
                throw new IllegalStateException("Missing FueTrek resource " + resourceName);
            }
            return input.readAllBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load FueTrek resource " + resourceName, exception);
        }
    }

    private static final class Tables {
        private final int[] gainCurve;
        private final int[] stereoCurve;

        private Tables(int[] gainCurve, int[] stereoCurve) {
            this.gainCurve = gainCurve;
            this.stereoCurve = stereoCurve;
        }
    }

    private static final class Reader {
        private final byte[] data;
        private int offset;

        private Reader(byte[] data) {
            this.data = data;
        }

        private int u16() {
            int value = (data[offset] & 0xff) | ((data[offset + 1] & 0xff) << 8);
            offset += 2;
            return value;
        }

        private int u32() {
            int value = u16();
            return value | (u16() << 16);
        }

        private void expectFullyConsumed() {
            if (offset != data.length) {
                throw new IllegalStateException(
                        "FueTrek mix table resource has trailing data: " + (data.length - offset));
            }
        }
    }
}
