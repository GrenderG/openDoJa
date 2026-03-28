package opendoja.audio.mld.fuetrek;

import java.io.IOException;
import java.io.InputStream;

/**
 * Native note-shape tables used by the FueTrek ordinary note-on path.
 *
 * <p>The resource is a direct slice from {@code MFiSynth_ft.dll} covering the
 * note remap bytes {@code 0x10014450..0x100144cf} and the paired scaler tables
 * rooted at {@code 0x100144d0} / {@code 0x100145ce}.
 */
final class FueTrekNoteShapeTables {
    private static final String RESOURCE_NAME = "fuetrek-note-shape.bin";
    private static final int NOTE_MAP_OFFSET = 0x0c18;
    private static final int NOTE_MAP_COUNT = 0x80;
    private static final int SCALE_FORWARD_OFFSET = NOTE_MAP_OFFSET + NOTE_MAP_COUNT;
    private static final int REVERSE_SCALE_BASE = 0x0d96;
    private static final byte[] DATA = loadBytes();

    private FueTrekNoteShapeTables() {
    }

    static int noteShapeIndex(int noteByte) {
        int clampedNote = Math.max(0, Math.min(NOTE_MAP_COUNT - 1, noteByte));
        return DATA[NOTE_MAP_OFFSET + clampedNote] & 0xff;
    }

    static int scaleForward(int word, int shapeIndex) {
        int forwardScale = u16(SCALE_FORWARD_OFFSET + (shapeIndex << 1));
        return (word * forwardScale) >>> 15;
    }

    static int scaleReverse(int word, int shapeIndex) {
        int reverseScale = u16(REVERSE_SCALE_BASE - (shapeIndex << 1));
        return (word * reverseScale) >>> 15;
    }

    private static int u16(int offset) {
        return (DATA[offset] & 0xff) | ((DATA[offset + 1] & 0xff) << 8);
    }

    private static byte[] loadBytes() {
        try (InputStream input = FueTrekNoteShapeTables.class.getResourceAsStream(RESOURCE_NAME)) {
            if (input == null) {
                throw new IllegalStateException("Missing FueTrek resource " + RESOURCE_NAME);
            }
            byte[] data = input.readAllBytes();
            if (data.length != 0x0d98) {
                throw new IllegalStateException("Unexpected FueTrek note-shape size: " + data.length);
            }
            return data;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load FueTrek resource " + RESOURCE_NAME, exception);
        }
    }
}
