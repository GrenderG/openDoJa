package opendoja.host;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;

public final class ScratchpadSeed {
    private static final int HEADER_BYTES = 64;
    private static final int HEADER_ENTRIES = 16;

    private ScratchpadSeed() {
    }

    public static void seedIfNeeded(Path scratchpadRoot, Path seedFile) throws IOException {
        seedIfNeeded(scratchpadRoot, seedFile, new int[0]);
    }

    public static void seedIfNeeded(Path scratchpadRoot, Path seedFile, int[] configuredSizes) throws IOException {
        if (seedFile == null || !Files.exists(seedFile) || hasExistingScratchpadData(scratchpadRoot)) {
            return;
        }
        Files.createDirectories(scratchpadRoot);
        byte[] seed = Files.readAllBytes(seedFile);
        if (seed.length == 0) {
            return;
        }
        if (seedAsMultiScratchpad(scratchpadRoot, seed, configuredSizes)) {
            return;
        }
        Files.write(scratchpadRoot.resolve("sp-0.bin"), seed);
    }

    private static boolean hasExistingScratchpadData(Path scratchpadRoot) throws IOException {
        if (!Files.isDirectory(scratchpadRoot)) {
            return false;
        }
        try (Stream<Path> entries = Files.list(scratchpadRoot)) {
            return entries.anyMatch(path -> {
                String name = path.getFileName().toString();
                return name.startsWith("sp-") && name.endsWith(".bin");
            });
        }
    }

    private static boolean seedAsMultiScratchpad(Path scratchpadRoot, byte[] seed, int[] configuredSizes) throws IOException {
        if (seed.length < HEADER_BYTES) {
            return false;
        }
        ByteBuffer buffer = ByteBuffer.wrap(seed, 0, HEADER_BYTES);
        int[] sizes = new int[HEADER_ENTRIES];
        long total = HEADER_BYTES;
        for (int i = 0; i < HEADER_ENTRIES; i++) {
            int raw = buffer.getInt();
            int normalized = normalizeHeaderSize(raw, seed.length, configuredSizes, i);
            if (normalized < 0) {
                return false;
            }
            sizes[i] = normalized;
            total += normalized;
        }
        if (total != seed.length) {
            return false;
        }
        int offset = HEADER_BYTES;
        for (int i = 0; i < sizes.length; i++) {
            int size = sizes[i];
            if (size > 0) {
                Files.write(scratchpadRoot.resolve("sp-" + i + ".bin"),
                        Arrays.copyOfRange(seed, offset, offset + size));
            }
            offset += size;
        }
        return true;
    }

    private static int normalizeHeaderSize(int raw, int seedLength, int[] configuredSizes, int index) {
        int chosen = raw;
        int reversed = Integer.reverseBytes(raw);
        int configured = index < configuredSizes.length ? configuredSizes[index] : Integer.MIN_VALUE;
        if (configured != Integer.MIN_VALUE) {
            if (reversed == configured && chosen != configured) {
                chosen = reversed;
            }
        } else if (chosen > seedLength || chosen < -1) {
            chosen = reversed;
        }
        if (chosen > seedLength || chosen < -1) {
            return -1;
        }
        return chosen == -1 ? 0 : chosen;
    }
}
