package opendoja.audio.mld.fuetrek;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Compact FueTrek ROM image extracted from the native synth tables and PCM payloads.
 */
final class FueTrekRom {
    private static final String ROM_RESOURCE = "fuetrek-rom.bin";
    private static final String CONTROL_RESOURCE = "fuetrek-control.bin";
    private static final int MAGIC = 0x4d525446; // FTRM
    private static final int CONTROL_MAGIC = 0x54435446; // FTCT
    private static final int VERSION = 1;
    private static final int CONTROL_VERSION = 1;
    private static final int GROUP_ENTRY_COUNT = 128;
    private static final int COMPACT_ZONE_PREFIX = 4;
    private static final int COMPACT_ZONE_SUFFIX = 0x38;
    private static final int EXPANDED_ZONE_SIZE = 0x44;
    private static final int NULL_INDEX = 0xffff;

    private static volatile FueTrekRom instance;

    final Sample[] samples;
    final Group[] groupsById;
    final int[] pitchRatioTable;
    final short[] interpolationTable;
    final int[] panLawTable;
    final byte[] drumPanTable;
    final int rootKeyTableStart;
    final byte[] rootKeyTable;
    final int[][][] mixProfileTables;

    private FueTrekRom(
            Sample[] samples,
            Group[] groupsById,
            int[] pitchRatioTable,
            short[] interpolationTable,
            int[] panLawTable,
            byte[] drumPanTable,
            int rootKeyTableStart,
            byte[] rootKeyTable,
            int[][][] mixProfileTables) {
        this.samples = samples;
        this.groupsById = groupsById;
        this.pitchRatioTable = pitchRatioTable;
        this.interpolationTable = interpolationTable;
        this.panLawTable = panLawTable;
        this.drumPanTable = drumPanTable;
        this.rootKeyTableStart = rootKeyTableStart;
        this.rootKeyTable = rootKeyTable;
        this.mixProfileTables = mixProfileTables;
    }

    static FueTrekRom load() {
        FueTrekRom current = instance;
        if (current != null) {
            return current;
        }
        synchronized (FueTrekRom.class) {
            current = instance;
            if (current != null) {
                return current;
            }
            ControlData control = parseControl(readResourceBytes(CONTROL_RESOURCE));
            instance = current = parse(readResourceBytes(ROM_RESOURCE), control);
            return current;
        }
    }

    Group group(int id) {
        return groupsById[id & 0xff];
    }

    int mixProfileScale(int mode, int groupId, int subId, int index) {
        if (mode < 1 || mode > mixProfileTables.length || subId != 0) {
            return 0x2000;
        }
        int record;
        switch (groupId & 0xff) {
            case 0x79:
                record = 0;
                break;
            case 0x78:
                record = 1;
                break;
            case 0x7d:
                record = 2;
                break;
            case 0x14:
                record = 3;
                break;
            default:
                return 0x2000;
        }
        int clampedIndex = Math.max(0, Math.min(127, index));
        return mixProfileTables[mode - 1][record][clampedIndex];
    }

    int nativeRootKeyWord(int encodedKey) {
        int index = encodedKey - rootKeyTableStart;
        if (index < 0 || index >= rootKeyTable.length) {
            return 0;
        }
        return (rootKeyTable[index] & 0xff) + 0x400;
    }

    private static byte[] readResourceBytes(String resourceName) {
        try (InputStream input = FueTrekRom.class.getResourceAsStream(resourceName)) {
            if (input == null) {
                throw new IllegalStateException("Missing FueTrek resource " + resourceName);
            }
            return input.readAllBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load FueTrek resource " + resourceName, exception);
        }
    }

    private static FueTrekRom parse(byte[] data, ControlData control) {
        Reader reader = new Reader(data);
        int magic = reader.u32();
        if (magic != MAGIC) {
            throw new IllegalStateException("Invalid FueTrek ROM magic: 0x" + Integer.toHexString(magic));
        }
        int version = reader.u16();
        if (version != VERSION) {
            throw new IllegalStateException("Unsupported FueTrek ROM version: " + version);
        }

        int sampleCount = reader.u16();
        int objectCount = reader.u16();
        int groupCount = reader.u16();
        int pitchRatioCount = reader.u16();
        int interpolationCount = reader.u16();
        int panLawCount = reader.u16();
        int drumPanCount = reader.u16();

        int[] pitchRatioTable = new int[pitchRatioCount];
        for (int i = 0; i < pitchRatioCount; i++) {
            pitchRatioTable[i] = reader.u32();
        }

        short[] interpolationTable = new short[interpolationCount];
        for (int i = 0; i < interpolationCount; i++) {
            interpolationTable[i] = (short) reader.u16();
        }

        int[] panLawTable = new int[panLawCount];
        for (int i = 0; i < panLawCount; i++) {
            panLawTable[i] = reader.u16();
        }

        byte[] drumPanTable = reader.bytes(drumPanCount);

        Sample[] samples = new Sample[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            int tune1024 = reader.u16();
            int rootKey = reader.u8();
            int controlByte = reader.u8();
            int loopStart = reader.u32();
            int loopEnd = reader.u32();
            byte[] pcm = reader.bytes(reader.u32());
            samples[i] = new Sample(pcm, loopStart, loopEnd, tune1024, rootKey, controlByte);
        }

        ObjectHeader[] objects = new ObjectHeader[objectCount];
        for (int i = 0; i < objectCount; i++) {
            int lowKey = reader.u8();
            int highKey = reader.u8();
            int zoneCount = reader.u8();
            Zone[] zones = new Zone[zoneCount];
            for (int z = 0; z < zoneCount; z++) {
                int sampleAIndex = reader.u16();
                int sampleBIndex = reader.u16();
                byte[] raw = expandZone(reader.bytes(COMPACT_ZONE_PREFIX), reader.bytes(COMPACT_ZONE_SUFFIX));
                zones[z] = new Zone(
                        raw,
                        raw[0],
                        sample(sampleAIndex, samples),
                        sample(sampleBIndex, samples));
            }
            objects[i] = new ObjectHeader(lowKey, highKey, zones);
        }

        Group[] groupsById = new Group[256];
        for (int g = 0; g < groupCount; g++) {
            int id = reader.u8();
            ObjectHeader[] entries = new ObjectHeader[GROUP_ENTRY_COUNT];
            for (int i = 0; i < GROUP_ENTRY_COUNT; i++) {
                int objectIndex = reader.u16();
                if (objectIndex != NULL_INDEX && objectIndex < objects.length) {
                    entries[i] = objects[objectIndex];
                }
            }
            // 0x10008670 turns entry 4 into the writable 16-slot preset bank
            // 0x11 and replaces its entries. Its on-disk drum entries are not
            // available for ordinary ROM selection after initialization.
            if (g != 4) {
                groupsById[id] = new Group(id, entries);
            }
        }

        reader.expectFullyConsumed();
        return new FueTrekRom(
                samples,
                groupsById,
                pitchRatioTable,
                interpolationTable,
                panLawTable,
                drumPanTable,
                control.rootKeyTableStart,
                control.rootKeyTable,
                control.mixProfileTables);
    }

    private static ControlData parseControl(byte[] data) {
        Reader reader = new Reader(data);
        int magic = reader.u32();
        if (magic != CONTROL_MAGIC) {
            throw new IllegalStateException("Invalid FueTrek control magic: 0x" + Integer.toHexString(magic));
        }
        int version = reader.u16();
        if (version != CONTROL_VERSION) {
            throw new IllegalStateException("Unsupported FueTrek control version: " + version);
        }
        int rootKeyTableStart = reader.u8();
        int rootKeyTableCount = reader.u8();
        int modeCount = reader.u8();
        reader.u8(); // reserved
        int blockSize = reader.u16();
        if (blockSize != 0x408) {
            throw new IllegalStateException("Unexpected FueTrek control block size: " + blockSize);
        }

        byte[] rootKeyTable = reader.bytes(rootKeyTableCount);
        int[][][] mixProfileTables = new int[modeCount][4][128];
        for (int mode = 0; mode < modeCount; mode++) {
            for (int record = 0; record < 4; record++) {
                int groupId = reader.u8();
                int subId = reader.u8();
                int expectedGroupId = record == 0 ? 0x79 : record == 1 ? 0x78 : record == 2 ? 0x7d : 0x14;
                if (groupId != expectedGroupId || subId != 0) {
                    throw new IllegalStateException(
                            "Unexpected FueTrek control record header: group=0x"
                                    + Integer.toHexString(groupId)
                                    + " sub=0x"
                                    + Integer.toHexString(subId));
                }
                for (int i = 0; i < 128; i++) {
                    mixProfileTables[mode][record][i] = reader.u16();
                }
            }
        }

        reader.expectFullyConsumed();
        return new ControlData(rootKeyTableStart, rootKeyTable, mixProfileTables);
    }

    private static Sample sample(int index, Sample[] samples) {
        if (index == NULL_INDEX || index < 0 || index >= samples.length) {
            return null;
        }
        return samples[index];
    }

    private static byte[] expandZone(byte[] prefix, byte[] suffix) {
        byte[] raw = new byte[EXPANDED_ZONE_SIZE];
        System.arraycopy(prefix, 0, raw, 0, prefix.length);
        System.arraycopy(suffix, 0, raw, 0x0c, suffix.length);
        return raw;
    }

    static final class Sample {
        final byte[] pcm8;
        final int loopStart;
        final int loopEnd;
        final int tune1024;
        final int rootKey;
        final int controlByte;

        Sample(
                byte[] pcm8,
                int loopStart,
                int loopEnd,
                int tune1024,
                int rootKey,
                int controlByte) {
            this(pcm8, loopStart, loopEnd, tune1024, rootKey, controlByte, true);
        }

        private Sample(byte[] pcm8, int loopStart, int loopEnd, int tune1024,
                       int rootKey, int controlByte, boolean copy) {
            this.pcm8 = copy ? Arrays.copyOf(pcm8, pcm8.length) : pcm8;
            this.loopStart = loopStart;
            this.loopEnd = loopEnd;
            this.tune1024 = tune1024;
            this.rootKey = rootKey;
            this.controlByte = controlByte;
        }

        Sample withPitch(int rootKey, int tune1024) {
            return new Sample(pcm8, loopStart, loopEnd, (short) tune1024,
                    rootKey & 0xff, controlByte, false);
        }

        Sample withWaveform(Sample wave) {
            return new Sample(wave.pcm8, wave.loopStart, wave.loopEnd, tune1024,
                    rootKey, controlByte, false);
        }

        int sampleCount() {
            return pcm8.length;
        }
    }

    static final class Zone {
        final byte[] raw;
        final int keyHigh;
        final Sample sampleA;
        final Sample sampleB;

        Zone(byte[] raw, int keyHigh, Sample sampleA, Sample sampleB) {
            this.raw = raw;
            this.keyHigh = keyHigh;
            this.sampleA = sampleA;
            this.sampleB = sampleB;
        }

        int s8(int offset) {
            return raw[offset];
        }

        int u8(int offset) {
            return raw[offset] & 0xff;
        }

        int s16(int offset) {
            return (short) u16(offset);
        }

        int s32(int offset) {
            return u8(offset)
                    | (u8(offset + 1) << 8)
                    | (u8(offset + 2) << 16)
                    | (raw[offset + 3] << 24);
        }

        int u16(int offset) {
            return u8(offset) | (u8(offset + 1) << 8);
        }
    }

    static final class ObjectHeader {
        final int lowKey;
        final int highKey;
        final Zone[] zones;

        ObjectHeader(int lowKey, int highKey, Zone[] zones) {
            this.lowKey = lowKey;
            this.highKey = highKey;
            this.zones = zones;
        }
    }

    static final class Group {
        final int id;
        final ObjectHeader[] entries;

        Group(int id, ObjectHeader[] entries) {
            this.id = id;
            this.entries = entries;
        }

        ObjectHeader entry(int index) {
            if (index < 0 || index >= entries.length) {
                return null;
            }
            return entries[index];
        }
    }

    private static final class ControlData {
        final int rootKeyTableStart;
        final byte[] rootKeyTable;
        final int[][][] mixProfileTables;

        ControlData(int rootKeyTableStart, byte[] rootKeyTable, int[][][] mixProfileTables) {
            this.rootKeyTableStart = rootKeyTableStart;
            this.rootKeyTable = rootKeyTable;
            this.mixProfileTables = mixProfileTables;
        }
    }

    private static final class Reader {
        private final byte[] data;
        private int offset;

        Reader(byte[] data) {
            this.data = data;
        }

        int u8() {
            return data[offset++] & 0xff;
        }

        int u16() {
            int value = (data[offset] & 0xff) | ((data[offset + 1] & 0xff) << 8);
            offset += 2;
            return value;
        }

        int u32() {
            int value = u16() | (u16() << 16);
            return value;
        }

        byte[] bytes(int length) {
            byte[] slice = Arrays.copyOfRange(data, offset, offset + length);
            offset += length;
            return slice;
        }

        void expectFullyConsumed() {
            if (offset != data.length) {
                throw new IllegalStateException(
                        "FueTrek ROM parser stopped at " + offset + " of " + data.length + " bytes");
            }
        }
    }
}
