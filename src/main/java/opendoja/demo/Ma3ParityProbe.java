package opendoja.demo;

import opendoja.audio.mld.ma3.MLD;
import opendoja.audio.mld.ma3.MA3SamplerProvider;
import opendoja.audio.mld.ma3.MLDPlayer;
import opendoja.audio.mld.ma3.MLDPlayerEvent;
import opendoja.audio.mld.ma3.RomData;
import opendoja.audio.mld.ma3.Sampler;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

public final class Ma3ParityProbe {
    private static final int EVENT_BANK_CHANGE = 0xE1;
    private static final int EVENT_PROGRAM_CHANGE = 0xE0;
    private static final int EVENT_X_DRUM_ENABLE = 0xBA;
    private static final int EVENT_TYPE_EXT_B = 1;
    private static final int EVENT_TYPE_EXT_INFO = 2;
    private static final int PRESET_PROGRAMS = 64;
    private static final float[] RENDER_BUFFER = new float[4096 * 2];

    private Ma3ParityProbe() {
    }

    public static void main(String[] args) throws Exception {
        DemoLog.enableInfoLogging();
        if (args.length == 0) {
            throw new IllegalArgumentException(
                    "usage: Ma3ParityProbe <preset-summary|rom-summary|mld-summary> [...]");
        }
        switch (args[0]) {
            case "preset-summary" -> presetSummary();
            case "rom-summary" -> romSummary();
            case "mld-summary" -> mldSummary(Arrays.copyOfRange(args, 1, args.length));
            case "preset-hash" -> presetHash(args);
            case "mld-hash" -> mldHash(args);
            default -> throw new IllegalArgumentException("unknown mode: " + args[0]);
        }
    }

    private static void presetSummary() throws Exception {
        MA3SamplerProvider provider = new MA3SamplerProvider(
                MA3SamplerProvider.FM_MA3_4OP,
                MA3SamplerProvider.FM_MA3_4OP,
                MA3SamplerProvider.WAVE_DRUM_MA3);
        Sampler sampler = provider.instance(MA3SamplerProvider.SAMPLE_RATE);
        for (int bank = 0; bank < 4; bank++) {
            Map<String, List<Integer>> programsByHash = new TreeMap<>();
            for (int program = 0; program < PRESET_PROGRAMS; program++) {
                sampler.reset();
                sampler.bankChange(0, bank);
                sampler.programChange(0, program);
                sampler.keyOn(0, 0, 1.0f);
                Arrays.fill(RENDER_BUFFER, 0.0f);
                sampler.render(RENDER_BUFFER, 0, 2048);
                String hash = pcmHash(RENDER_BUFFER);
                programsByHash.computeIfAbsent(hash, __ -> new ArrayList<>()).add(program);
            }
            int largestCollision = 0;
            List<Integer> representative = List.of();
            for (List<Integer> programs : programsByHash.values()) {
                if (programs.size() > largestCollision) {
                    largestCollision = programs.size();
                    representative = programs;
                }
            }
            DemoLog.info(Ma3ParityProbe.class, "bank=" + bank
                    + " uniqueHashes=" + programsByHash.size()
                    + " largestCollision=" + largestCollision
                    + " collisionPrograms=" + representative);
        }
    }

    private static void romSummary() throws Exception {
        summarizeRom("MA3_INSTRUMENTS_4OP", RomData.MA3_INSTRUMENTS_4OP, false);
        summarizeRom("MA3_INSTRUMENTS_2OP", RomData.MA3_INSTRUMENTS_2OP, false);
        summarizeRom("MA3_DRUMS_4OP", RomData.MA3_DRUMS_4OP, true);
        summarizeRom("MA3_DRUMS_2OP", RomData.MA3_DRUMS_2OP, true);
    }

    private static void summarizeRom(String label, RomData romData, boolean drum) {
        int algorithmCount = romCount(romData);
        int vibratoOperators = 0;
        int tremoloOperators = 0;
        int ignoreKeyOffOperators = 0;
        int sustainingOperators = 0;
        int algorithmsWithVibrato = 0;
        for (int i = 0; i < algorithmCount; i++) {
            byte[] bytes = romData.bytes(i);
            int algorithm = bytes[1] & 0x7;
            int operatorCount = algorithm < 2 ? 2 : 4;
            boolean anyVibrato = false;
            for (int op = 0; op < operatorCount; op++) {
                int offset = 3 + op * 7;
                int flags = bytes[offset] & 0xFF;
                int config = bytes[offset + 1] & 0xFF;
                if ((flags & 0x01) != 0) {
                    vibratoOperators++;
                    anyVibrato = true;
                }
                if ((flags & 0x02) != 0) {
                    tremoloOperators++;
                }
                if ((flags & 0x08) != 0) {
                    sustainingOperators++;
                }
                if ((config & 0x01) != 0) {
                    ignoreKeyOffOperators++;
                }
            }
            if (anyVibrato) {
                algorithmsWithVibrato++;
            }
        }
        DemoLog.info(Ma3ParityProbe.class, label
                + " count=" + algorithmCount
                + " drum=" + drum
                + " algorithmsWithVibrato=" + algorithmsWithVibrato
                + " vibratoOperators=" + vibratoOperators
                + " tremoloOperators=" + tremoloOperators
                + " sustainOperators=" + sustainingOperators
                + " ignoreKeyOffOperators=" + ignoreKeyOffOperators);
    }

    private static void mldSummary(String[] paths) throws Exception {
        if (paths.length == 0) {
            throw new IllegalArgumentException("mld-summary requires one or more file paths");
        }
        Class<?> eventClass = Class.forName("opendoja.audio.mld.ma3.MLDEvent");
        Field typeField = declaredField(eventClass, "type");
        Field bankField = declaredField(eventClass, "bank");
        Field programField = declaredField(eventClass, "program");
        Field enableField = declaredField(eventClass, "enable");
        Field dataField = declaredField(eventClass, "data");
        Field idField = declaredField(eventClass, "id");

        Field tracksField = declaredField(MLD.class, "tracks");

        for (String rawPath : paths) {
            Path path = Path.of(rawPath);
            byte[] bytes = Files.readAllBytes(path);
            MLD mld = new MLD(bytes);
            Object[] tracks = (Object[]) tracksField.get(mld);
            Map<Integer, TreeSet<Integer>> programsByBank = new TreeMap<>();
            TreeSet<Integer> banks = new TreeSet<>();
            int drumEnableOn = 0;
            int fmSysEx = 0;
            int waveDrumSysEx = 0;
            int waveDataSysEx = 0;
            for (Object track : tracks) {
                int currentBank = 0;
                for (Object event : (List<?>) track) {
                    int type = (int) typeField.get(event);
                    if (type == EVENT_TYPE_EXT_B && ((int) idField.get(event)) == EVENT_BANK_CHANGE) {
                        currentBank = (int) bankField.get(event);
                        banks.add(currentBank);
                    } else if (type == EVENT_TYPE_EXT_B && ((int) idField.get(event)) == EVENT_PROGRAM_CHANGE) {
                        int program = (int) programField.get(event);
                        programsByBank.computeIfAbsent(currentBank, __ -> new TreeSet<>()).add(program);
                    } else if (type == EVENT_TYPE_EXT_B && ((int) idField.get(event)) == EVENT_X_DRUM_ENABLE
                            && (boolean) enableField.get(event)) {
                        drumEnableOn++;
                    } else if (type == EVENT_TYPE_EXT_INFO) {
                        byte[] data = (byte[]) dataField.get(event);
                        if (data != null && data.length >= 4 && data[0] == 0x11 && data[1] == 0x01
                                && (data[2] & 0xF0) == (byte) 0xF0) {
                            switch (data[3] & 0xFF) {
                                case 0x04 -> fmSysEx++;
                                case 0x05 -> waveDrumSysEx++;
                                case 0x06 -> waveDataSysEx++;
                                default -> {
                                }
                            }
                        }
                    }
                }
            }
            DemoLog.info(Ma3ParityProbe.class, path.getFileName()
                    + " banks=" + banks
                    + " programs=" + programsByBank
                    + " features=" + describePrograms(programsByBank)
                    + " drumEnableOn=" + drumEnableOn
                    + " fmSysEx=" + fmSysEx
                    + " waveDrumSysEx=" + waveDrumSysEx
                    + " waveDataSysEx=" + waveDataSysEx);
        }
    }

    private static void presetHash(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("usage: Ma3ParityProbe preset-hash <bank> <program>");
        }
        int bank = Integer.parseInt(args[1]);
        int program = Integer.parseInt(args[2]);
        MA3SamplerProvider provider = new MA3SamplerProvider(
                MA3SamplerProvider.FM_MA3_4OP,
                MA3SamplerProvider.FM_MA3_4OP,
                MA3SamplerProvider.WAVE_DRUM_MA3);
        Sampler sampler = provider.instance(MA3SamplerProvider.SAMPLE_RATE);
        sampler.reset();
        sampler.bankChange(0, bank);
        sampler.programChange(0, program);
        sampler.keyOn(0, 0, 1.0f);
        Arrays.fill(RENDER_BUFFER, 0.0f);
        sampler.render(RENDER_BUFFER, 0, 4096);
        DemoLog.info(Ma3ParityProbe.class, "bank=" + bank
                + " program=" + program
                + " hash=" + pcmHash(RENDER_BUFFER)
                + " rms=" + rms(RENDER_BUFFER, 4096));
    }

    private static void mldHash(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) {
            throw new IllegalArgumentException("usage: Ma3ParityProbe mld-hash <path> [chunks]");
        }
        int chunks = args.length >= 3 ? Integer.parseInt(args[2]) : 64;
        byte[] bytes = Files.readAllBytes(Path.of(args[1]));
        MLD mld = new MLD(bytes);
        MLDPlayer player = new MLDPlayer(mld,
                new MA3SamplerProvider(
                        MA3SamplerProvider.FM_MA3_4OP,
                        MA3SamplerProvider.FM_MA3_4OP,
                        MA3SamplerProvider.WAVE_DRUM_MA3),
                MA3SamplerProvider.SAMPLE_RATE);
        player.setPlaybackEventsEnabled(true);
        float[] buffer = new float[512 * 2];
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        int framesRendered = 0;
        double energy = 0.0;
        int loopEvents = 0;
        int endEvents = 0;
        for (int i = 0; i < chunks; i++) {
            Arrays.fill(buffer, 0.0f);
            int frames = player.render(buffer, 0, 512);
            int usable = Math.max(frames, 0);
            framesRendered += usable;
            for (int s = 0; s < usable * 2; s++) {
                int value = Math.round(buffer[s] * 32767.0f);
                digest.update((byte) (value & 0xFF));
                digest.update((byte) ((value >>> 8) & 0xFF));
                energy += buffer[s] * buffer[s];
            }
            for (MLDPlayerEvent event : player.getEvents()) {
                if (event.type == MLDPlayer.EVENT_LOOP) {
                    loopEvents++;
                } else if (event.type == MLDPlayer.EVENT_END) {
                    endEvents++;
                }
            }
            if (frames < 0) {
                break;
            }
        }
        DemoLog.info(Ma3ParityProbe.class, Path.of(args[1]).getFileName()
                + " hash=" + hex(digest.digest(), 8)
                + " frames=" + framesRendered
                + " rms=" + (framesRendered == 0 ? 0.0 : Math.sqrt(energy / (framesRendered * 2.0)))
                + " loops=" + loopEvents
                + " ends=" + endEvents);
    }

    private static Field declaredField(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static int romCount(RomData romData) {
        int count = 0;
        while (true) {
            try {
                romData.bytes(count);
                count++;
            } catch (IndexOutOfBoundsException ignored) {
                return count;
            }
        }
    }

    private static String describePrograms(Map<Integer, TreeSet<Integer>> programsByBank) {
        List<String> descriptions = new ArrayList<>();
        for (Map.Entry<Integer, TreeSet<Integer>> entry : programsByBank.entrySet()) {
            int bank = entry.getKey();
            for (int program : entry.getValue()) {
                int preset = ((bank < 2 ? 0 : (bank & 1) << 6) | (program & 0x3F));
                byte[] bytes = RomData.MA3_INSTRUMENTS_4OP.bytes(preset);
                int algorithm = bytes[1] & 0x7;
                int operatorCount = algorithm < 2 ? 2 : 4;
                int vibratoOperators = 0;
                int tremoloOperators = 0;
                for (int op = 0; op < operatorCount; op++) {
                    int flags = bytes[3 + op * 7] & 0xFF;
                    if ((flags & 0x01) != 0) {
                        vibratoOperators++;
                    }
                    if ((flags & 0x02) != 0) {
                        tremoloOperators++;
                    }
                }
                descriptions.add(String.format("%d:%d[preset=%d alg=%d vib=%d trem=%d]",
                        bank, program, preset, algorithm, vibratoOperators, tremoloOperators));
            }
        }
        return descriptions.toString();
    }

    private static String pcmHash(float[] buffer) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (float sample : buffer) {
            int value = Math.round(sample * 32767.0f);
            digest.update((byte) (value & 0xFF));
            digest.update((byte) ((value >>> 8) & 0xFF));
        }
        byte[] hash = digest.digest();
        return hex(hash, 8);
    }

    private static String hex(byte[] hash, int bytes) {
        StringBuilder builder = new StringBuilder(16);
        for (int i = 0; i < bytes && i < hash.length; i++) {
            builder.append(String.format("%02x", hash[i]));
        }
        return builder.toString();
    }

    private static double rms(float[] buffer, int frames) {
        double energy = 0.0;
        for (int i = 0; i < frames * 2; i++) {
            energy += buffer[i] * buffer[i];
        }
        return Math.sqrt(energy / (frames * 2.0));
    }
}
