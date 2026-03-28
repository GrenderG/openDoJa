package opendoja.demo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Verifies the packed FueTrek resources against direct byte slices from {@code MFiSynth_ft.dll}.
 */
public final class FueTrekAssetVerifier {
    private static final Path DEFAULT_SYNTH_DLL =
            Path.of("resources/iDKDoJa5.1/bin/soundlib/lib002/Conf/MFiSynth_ft.dll");
    private static final Path DEFAULT_ROM_RESOURCE =
            Path.of("src/main/resources/opendoja/audio/mld/fuetrek/fuetrek-rom.bin");
    private static final Path DEFAULT_CONTROL_RESOURCE =
            Path.of("src/main/resources/opendoja/audio/mld/fuetrek/fuetrek-control.bin");
    private static final Path DEFAULT_MIX_RESOURCE =
            Path.of("src/main/resources/opendoja/audio/mld/fuetrek/fuetrek-mix-tables.bin");

    private static final int SAMPLE_TABLE_VA = 0x1003b7d8;
    private static final int SAMPLE_COUNT = 179;
    private static final int SAMPLE_STRIDE = 0x24;
    private static final int OBJECT_TABLE_VA = 0x1004620c;
    private static final int OBJECT_COUNT = 213;
    private static final int OBJECT_STRIDE = 0x08;
    private static final int GROUP_TABLE_VA = 0x100468b8;
    private static final int GROUP_COUNT = 5;
    private static final int GROUP_STRIDE = 0x204;
    private static final int GROUP_ENTRY_COUNT = 128;
    private static final int ZONE_SIZE = 0x44;
    private static final int ROM_MAGIC = 0x4d525446;
    private static final int CONTROL_MAGIC = 0x54435446;
    private static final int MIX_MAGIC = 0x584d5446;
    private static final int NULL_INDEX = 0xffff;

    private FueTrekAssetVerifier() {
    }

    public static void main(String[] args) throws IOException {
        Path synthDll = args.length > 0 ? Path.of(args[0]) : DEFAULT_SYNTH_DLL;
        Path romResource = args.length > 1 ? Path.of(args[1]) : DEFAULT_ROM_RESOURCE;
        Path controlResource = args.length > 2 ? Path.of(args[2]) : DEFAULT_CONTROL_RESOURCE;
        Path mixResource = args.length > 3 ? Path.of(args[3]) : DEFAULT_MIX_RESOURCE;

        PeFile synth = PeFile.open(synthDll);
        PackedMix mix = PackedMix.parse(Files.readAllBytes(mixResource));
        PackedControl control = PackedControl.parse(Files.readAllBytes(controlResource));
        PackedRom rom = PackedRom.parse(Files.readAllBytes(romResource));

        verifyMix(synth, mix);
        verifyControl(synth, control);
        verifyRom(synth, rom);

        int nonZeroControlBytes = 0;
        for (PackedSample sample : rom.samples) {
            if (sample.controlByte != 0) {
                nonZeroControlBytes++;
            }
        }
        System.out.println("FueTrek asset verification passed.");
        System.out.println("Samples=" + rom.samples.length
                + " Objects=" + rom.objects.length
                + " Groups=" + rom.groups.length
                + " NonZeroSampleControlBytes=" + nonZeroControlBytes
                + " (sample control-byte source remains separately tracked)");
    }

    private static void verifyMix(PeFile synth, PackedMix mix) {
        assertEquals("mix magic", MIX_MAGIC, mix.magic);
        assertByteRangeEquals("gain curve", synth.bytesAtVa(0x10013030, 128 * 2), mix.gainBytes());
        assertByteRangeEquals("stereo curve", synth.bytesAtVa(0x10013130, 129 * 2), mix.stereoBytes());
    }

    private static void verifyControl(PeFile synth, PackedControl control) {
        assertEquals("control magic", CONTROL_MAGIC, control.magic);
        assertByteRangeEquals("root key table", synth.bytesAtVa(0x10011408, control.rootKeyTable.length), control.rootKeyTable);
        assertByteRangeEquals("mix profile block 0", synth.bytesAtVa(0x10013838, 0x408), control.blocks[0]);
        assertByteRangeEquals("mix profile block 1", synth.bytesAtVa(0x10013c40, 0x408), control.blocks[1]);
        assertByteRangeEquals("mix profile block 2", synth.bytesAtVa(0x10014048, 0x408), control.blocks[2]);
    }

    private static void verifyRom(PeFile synth, PackedRom rom) {
        assertEquals("rom magic", ROM_MAGIC, rom.magic);
        assertEquals("sample count", SAMPLE_COUNT, rom.samples.length);
        assertEquals("object count", OBJECT_COUNT, rom.objects.length);
        assertEquals("group count", GROUP_COUNT, rom.groups.length);

        assertByteRangeEquals("pitch ratio table", synth.bytesAtVa(0x1001149c, rom.pitchRatios.length * 4), rom.pitchRatioBytes());
        assertByteRangeEquals("interpolation table", synth.bytesAtVa(0x100472e0, rom.interpolation.length * 2), rom.interpolationBytes());
        assertByteRangeEquals("pan law table", synth.bytesAtVa(0x100144d0, rom.panLaw.length * 2), rom.panLawBytes());
        assertByteRangeEquals("drum pan table", synth.bytesAtVa(0x10014450, rom.drumPan.length), rom.drumPan);

        Map<Integer, Integer> sampleIndexByDescriptorVa = new HashMap<>();
        for (int i = 0; i < rom.samples.length; i++) {
            int descriptorVa = SAMPLE_TABLE_VA + (i * SAMPLE_STRIDE);
            sampleIndexByDescriptorVa.put(descriptorVa, i);

            PackedSample sample = rom.samples[i];
            assertEquals("sample[" + i + "] tune", synth.u16AtVa(descriptorVa + 0x10), sample.tune1024);
            assertEquals("sample[" + i + "] root", synth.u16AtVa(descriptorVa + 0x12) & 0xff, sample.rootKey);
            assertEquals("sample[" + i + "] loopStart", synth.u32AtVa(descriptorVa + 0x08) >>> 12, sample.loopStart);
            assertEquals("sample[" + i + "] loopEnd", synth.u32AtVa(descriptorVa + 0x0c) >>> 12, sample.loopEnd);
            byte[] nativePcm = synth.bytesAtVa(synth.u32AtVa(descriptorVa), synth.u32AtVa(descriptorVa + 0x04));
            assertByteRangeEquals("sample[" + i + "] pcm", nativePcm, sample.pcm8);
        }

        Map<Integer, Integer> objectIndexByHeaderVa = new HashMap<>();
        for (int i = 0; i < rom.objects.length; i++) {
            int headerVa = OBJECT_TABLE_VA + (i * OBJECT_STRIDE);
            objectIndexByHeaderVa.put(headerVa, i);

            PackedObject object = rom.objects[i];
            assertEquals("object[" + i + "] lowKey", synth.u8AtVa(headerVa), object.lowKey);
            assertEquals("object[" + i + "] highKey", synth.u8AtVa(headerVa + 1), object.highKey);
            assertEquals("object[" + i + "] reserved", 0, synth.u8AtVa(headerVa + 2));
            assertEquals("object[" + i + "] zoneCount", synth.u8AtVa(headerVa + 3), object.zones.length);

            int zoneArrayVa = synth.u32AtVa(headerVa + 4);
            for (int z = 0; z < object.zones.length; z++) {
                PackedZone zone = object.zones[z];
                int zoneVa = zoneArrayVa + (z * ZONE_SIZE);
                byte[] nativeZone = synth.bytesAtVa(zoneVa, ZONE_SIZE);
                assertEquals("object[" + i + "] zone[" + z + "] keyHigh", nativeZone[0], zone.raw[0]);
                assertByteRangeEquals(
                        "object[" + i + "] zone[" + z + "] packed tail",
                        Arrays.copyOfRange(nativeZone, 0x0c, nativeZone.length),
                        Arrays.copyOfRange(zone.raw, 0x0c, zone.raw.length));
                int sampleADescriptorVa = u32(nativeZone, 0x04);
                int sampleBDescriptorVa = u32(nativeZone, 0x08);
                assertEquals(
                        "object[" + i + "] zone[" + z + "] sampleA",
                        indexOrNull(sampleIndexByDescriptorVa, sampleADescriptorVa),
                        zone.sampleAIndex);
                assertEquals(
                        "object[" + i + "] zone[" + z + "] sampleB",
                        indexOrNull(sampleIndexByDescriptorVa, sampleBDescriptorVa),
                        zone.sampleBIndex);
            }
        }

        for (int i = 0; i < rom.groups.length; i++) {
            PackedGroup group = rom.groups[i];
            int groupVa = GROUP_TABLE_VA + (i * GROUP_STRIDE);
            assertEquals("group[" + i + "] id", synth.u8AtVa(groupVa), group.id);
            for (int slot = 0; slot < group.objectIndexes.length; slot++) {
                int headerVa = synth.u32AtVa(groupVa + 4 + (slot * 4));
                assertEquals(
                        "group[" + i + "] slot[" + slot + "]",
                        indexOrNull(objectIndexByHeaderVa, headerVa),
                        group.objectIndexes[slot]);
            }
        }
    }

    private static int indexOrNull(Map<Integer, Integer> indexes, int va) {
        if (va == 0) {
            return NULL_INDEX;
        }
        Integer index = indexes.get(va);
        if (index == null) {
            throw new IllegalStateException(String.format("Unknown VA 0x%08x", va));
        }
        return index;
    }

    private static void assertByteRangeEquals(String label, byte[] expected, byte[] actual) {
        if (!Arrays.equals(expected, actual)) {
            throw new IllegalStateException(label + " mismatch");
        }
    }

    private static void assertEquals(String label, int expected, int actual) {
        if (expected != actual) {
            throw new IllegalStateException(label + " mismatch: expected " + expected + " but got " + actual);
        }
    }

    private static int u16(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
    }

    private static int u32(byte[] bytes, int offset) {
        return u16(bytes, offset) | (u16(bytes, offset + 2) << 16);
    }

    private static final class PackedMix {
        final int magic;
        final int[] gainCurve;
        final int[] stereoCurve;

        private PackedMix(int magic, int[] gainCurve, int[] stereoCurve) {
            this.magic = magic;
            this.gainCurve = gainCurve;
            this.stereoCurve = stereoCurve;
        }

        static PackedMix parse(byte[] bytes) {
            Reader reader = new Reader(bytes);
            int magic = reader.u32();
            reader.expect(1, "mix version");
            int gainCount = reader.u16();
            int stereoCount = reader.u16();
            reader.u16();
            int[] gainCurve = new int[gainCount];
            int[] stereoCurve = new int[stereoCount];
            for (int i = 0; i < gainCurve.length; i++) {
                gainCurve[i] = reader.u16();
            }
            for (int i = 0; i < stereoCurve.length; i++) {
                stereoCurve[i] = reader.u16();
            }
            reader.expectFullyConsumed();
            return new PackedMix(magic, gainCurve, stereoCurve);
        }

        byte[] gainBytes() {
            return wordsToBytes(gainCurve);
        }

        byte[] stereoBytes() {
            return wordsToBytes(stereoCurve);
        }
    }

    private static final class PackedControl {
        final int magic;
        final byte[] rootKeyTable;
        final byte[][] blocks;

        private PackedControl(int magic, byte[] rootKeyTable, byte[][] blocks) {
            this.magic = magic;
            this.rootKeyTable = rootKeyTable;
            this.blocks = blocks;
        }

        static PackedControl parse(byte[] bytes) {
            Reader reader = new Reader(bytes);
            int magic = reader.u32();
            reader.expect(1, "control version");
            int rootKeyStart = reader.u8();
            int rootKeyCount = reader.u8();
            int modeCount = reader.u8();
            reader.u8();
            int blockSize = reader.u16();
            if (rootKeyStart != 0x1d || rootKeyCount != 0x64 || modeCount != 3 || blockSize != 0x408) {
                throw new IllegalStateException("Unexpected packed control header");
            }
            byte[] rootKeyTable = reader.bytes(rootKeyCount);
            byte[][] blocks = new byte[modeCount][];
            for (int i = 0; i < modeCount; i++) {
                blocks[i] = reader.bytes(blockSize);
            }
            reader.expectFullyConsumed();
            return new PackedControl(magic, rootKeyTable, blocks);
        }
    }

    private static final class PackedRom {
        final int magic;
        final int[] pitchRatios;
        final int[] interpolation;
        final int[] panLaw;
        final byte[] drumPan;
        final PackedSample[] samples;
        final PackedObject[] objects;
        final PackedGroup[] groups;

        private PackedRom(
                int magic,
                int[] pitchRatios,
                int[] interpolation,
                int[] panLaw,
                byte[] drumPan,
                PackedSample[] samples,
                PackedObject[] objects,
                PackedGroup[] groups) {
            this.magic = magic;
            this.pitchRatios = pitchRatios;
            this.interpolation = interpolation;
            this.panLaw = panLaw;
            this.drumPan = drumPan;
            this.samples = samples;
            this.objects = objects;
            this.groups = groups;
        }

        static PackedRom parse(byte[] bytes) {
            Reader reader = new Reader(bytes);
            int magic = reader.u32();
            reader.expect(1, "rom version");
            int sampleCount = reader.u16();
            int objectCount = reader.u16();
            int groupCount = reader.u16();
            int pitchCount = reader.u16();
            int interpolationCount = reader.u16();
            int panLawCount = reader.u16();
            int drumPanCount = reader.u16();

            int[] pitchRatios = new int[pitchCount];
            for (int i = 0; i < pitchRatios.length; i++) {
                pitchRatios[i] = reader.u32();
            }
            int[] interpolation = new int[interpolationCount];
            for (int i = 0; i < interpolation.length; i++) {
                interpolation[i] = reader.u16();
            }
            int[] panLaw = new int[panLawCount];
            for (int i = 0; i < panLaw.length; i++) {
                panLaw[i] = reader.u16();
            }
            byte[] drumPan = reader.bytes(drumPanCount);

            PackedSample[] samples = new PackedSample[sampleCount];
            for (int i = 0; i < samples.length; i++) {
                int tune1024 = reader.u16();
                int rootKey = reader.u8();
                int controlByte = reader.u8();
                int loopStart = reader.u32();
                int loopEnd = reader.u32();
                byte[] pcm8 = reader.bytes(reader.u32());
                samples[i] = new PackedSample(pcm8, loopStart, loopEnd, tune1024, rootKey, controlByte);
            }

            PackedObject[] objects = new PackedObject[objectCount];
            for (int i = 0; i < objects.length; i++) {
                int lowKey = reader.u8();
                int highKey = reader.u8();
                int zoneCount = reader.u8();
                PackedZone[] zones = new PackedZone[zoneCount];
                for (int z = 0; z < zones.length; z++) {
                    int sampleAIndex = reader.u16();
                    int sampleBIndex = reader.u16();
                    byte[] prefix = reader.bytes(4);
                    byte[] suffix = reader.bytes(0x38);
                    byte[] raw = new byte[ZONE_SIZE];
                    System.arraycopy(prefix, 0, raw, 0, prefix.length);
                    System.arraycopy(suffix, 0, raw, 0x0c, suffix.length);
                    zones[z] = new PackedZone(raw, sampleAIndex, sampleBIndex);
                }
                objects[i] = new PackedObject(lowKey, highKey, zones);
            }

            PackedGroup[] groups = new PackedGroup[groupCount];
            for (int i = 0; i < groups.length; i++) {
                int id = reader.u8();
                int[] objectIndexes = new int[GROUP_ENTRY_COUNT];
                for (int slot = 0; slot < objectIndexes.length; slot++) {
                    objectIndexes[slot] = reader.u16();
                }
                groups[i] = new PackedGroup(id, objectIndexes);
            }

            reader.expectFullyConsumed();
            return new PackedRom(magic, pitchRatios, interpolation, panLaw, drumPan, samples, objects, groups);
        }

        byte[] pitchRatioBytes() {
            byte[] bytes = new byte[pitchRatios.length * 4];
            for (int i = 0; i < pitchRatios.length; i++) {
                write32(bytes, i * 4, pitchRatios[i]);
            }
            return bytes;
        }

        byte[] interpolationBytes() {
            return wordsToBytes(interpolation);
        }

        byte[] panLawBytes() {
            return wordsToBytes(panLaw);
        }
    }

    private static final class PackedSample {
        final byte[] pcm8;
        final int loopStart;
        final int loopEnd;
        final int tune1024;
        final int rootKey;
        final int controlByte;

        private PackedSample(byte[] pcm8, int loopStart, int loopEnd, int tune1024, int rootKey, int controlByte) {
            this.pcm8 = pcm8;
            this.loopStart = loopStart;
            this.loopEnd = loopEnd;
            this.tune1024 = tune1024;
            this.rootKey = rootKey;
            this.controlByte = controlByte;
        }
    }

    private static final class PackedObject {
        final int lowKey;
        final int highKey;
        final PackedZone[] zones;

        private PackedObject(int lowKey, int highKey, PackedZone[] zones) {
            this.lowKey = lowKey;
            this.highKey = highKey;
            this.zones = zones;
        }
    }

    private static final class PackedZone {
        final byte[] raw;
        final int sampleAIndex;
        final int sampleBIndex;

        private PackedZone(byte[] raw, int sampleAIndex, int sampleBIndex) {
            this.raw = raw;
            this.sampleAIndex = sampleAIndex;
            this.sampleBIndex = sampleBIndex;
        }
    }

    private static final class PackedGroup {
        final int id;
        final int[] objectIndexes;

        private PackedGroup(int id, int[] objectIndexes) {
            this.id = id;
            this.objectIndexes = objectIndexes;
        }
    }

    private static final class Reader {
        private final byte[] data;
        private int offset;

        private Reader(byte[] data) {
            this.data = data;
        }

        private int u8() {
            return data[offset++] & 0xff;
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

        private byte[] bytes(int length) {
            byte[] slice = Arrays.copyOfRange(data, offset, offset + length);
            offset += length;
            return slice;
        }

        private void expect(int value, String label) {
            int actual = u16();
            if (actual != value) {
                throw new IllegalStateException(label + " mismatch: " + actual);
            }
        }

        private void expectFullyConsumed() {
            if (offset != data.length) {
                throw new IllegalStateException("Unexpected trailing data: " + (data.length - offset));
            }
        }
    }

    private static final class PeFile {
        private final byte[] data;
        private final int imageBase;
        private final List<Section> sections;

        private PeFile(byte[] data, int imageBase, List<Section> sections) {
            this.data = data;
            this.imageBase = imageBase;
            this.sections = sections;
        }

        static PeFile open(Path path) throws IOException {
            byte[] data = Files.readAllBytes(path);
            if (data.length < 0x40 || data[0] != 'M' || data[1] != 'Z') {
                throw new IllegalStateException("Not a PE file: " + path);
            }
            int peHeader = u32(data, 0x3c);
            if (!"PE\u0000\u0000".equals(new String(data, peHeader, 4, StandardCharsets.ISO_8859_1))) {
                throw new IllegalStateException("Missing PE signature: " + path);
            }
            int sectionCount = u16(data, peHeader + 6);
            int optionalHeaderSize = u16(data, peHeader + 20);
            int imageBase = u32(data, peHeader + 24 + 28);
            int sectionTable = peHeader + 24 + optionalHeaderSize;
            List<Section> sections = new ArrayList<>(sectionCount);
            for (int i = 0; i < sectionCount; i++) {
                int offset = sectionTable + (i * 40);
                int virtualSize = u32(data, offset + 8);
                int virtualAddress = imageBase + u32(data, offset + 12);
                int rawSize = u32(data, offset + 16);
                int rawOffset = u32(data, offset + 20);
                sections.add(new Section(virtualAddress, rawOffset, Math.max(virtualSize, rawSize)));
            }
            return new PeFile(data, imageBase, sections);
        }

        int u8AtVa(int va) {
            return data[fileOffset(va)] & 0xff;
        }

        int u16AtVa(int va) {
            int offset = fileOffset(va);
            return u16(data, offset);
        }

        int u32AtVa(int va) {
            int offset = fileOffset(va);
            return u32(data, offset);
        }

        byte[] bytesAtVa(int va, int length) {
            int offset = fileOffset(va);
            return Arrays.copyOfRange(data, offset, offset + length);
        }

        private int fileOffset(int va) {
            for (Section section : sections) {
                if (va >= section.virtualAddress && va + 1 <= section.virtualAddress + section.size) {
                    return section.rawOffset + (va - section.virtualAddress);
                }
            }
            throw new IllegalStateException(String.format("VA 0x%08x is outside mapped sections", va));
        }
    }

    private static final class Section {
        final int virtualAddress;
        final int rawOffset;
        final int size;

        private Section(int virtualAddress, int rawOffset, int size) {
            this.virtualAddress = virtualAddress;
            this.rawOffset = rawOffset;
            this.size = size;
        }
    }

    private static byte[] wordsToBytes(int[] words) {
        byte[] bytes = new byte[words.length * 2];
        for (int i = 0; i < words.length; i++) {
            bytes[i * 2] = (byte) words[i];
            bytes[(i * 2) + 1] = (byte) (words[i] >>> 8);
        }
        return bytes;
    }

    private static void write32(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) value;
        bytes[offset + 1] = (byte) (value >>> 8);
        bytes[offset + 2] = (byte) (value >>> 16);
        bytes[offset + 3] = (byte) (value >>> 24);
    }
}
