package opendoja.launcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class JamDropResolutionProbe {
    private JamDropResolutionProbe() {
    }

    public static void main(String[] args) throws Exception {
        verifyDirectJamFileDrop();
        verifyRootFolderJamIsPreferred();
        verifyBinFolderJamFallback();
        verifyFolderWithoutJamIsIgnored();
        verifyNonJamFileStillFails();

        System.out.println("Jam drop resolution probe OK");
    }

    private static void verifyDirectJamFileDrop() throws Exception {
        Path root = Files.createTempDirectory("jam-drop-file");
        Path jam = writeJam(root.resolve("Direct.jam"));

        Path resolved = JamLaunchService.droppedJamPath(List.of(jam));
        check(jam.toAbsolutePath().normalize().equals(resolved), "direct .jam drop should resolve to the file itself");
    }

    private static void verifyRootFolderJamIsPreferred() throws Exception {
        Path root = Files.createTempDirectory("jam-drop-root");
        Path expected = writeJam(root.resolve("Root.jam"));
        Path bin = Files.createDirectories(root.resolve("bin"));
        writeJam(bin.resolve("Bin.jam"));

        Path resolved = JamLaunchService.droppedJamPath(List.of(root));
        check(expected.toAbsolutePath().normalize().equals(resolved),
                "root folder .jam should win over bin/.jam");
    }

    private static void verifyBinFolderJamFallback() throws Exception {
        Path root = Files.createTempDirectory("jam-drop-bin");
        Path bin = Files.createDirectories(root.resolve("bin"));
        Path expected = writeJam(bin.resolve("Game.jam"));

        Path resolved = JamLaunchService.droppedJamPath(List.of(root));
        check(expected.toAbsolutePath().normalize().equals(resolved),
                "bin/.jam should be used when the root folder has no .jam");
    }

    private static void verifyFolderWithoutJamIsIgnored() throws Exception {
        Path root = Files.createTempDirectory("jam-drop-empty");
        Files.createDirectories(root.resolve("bin"));
        Files.writeString(root.resolve("readme.txt"), "no jam", StandardCharsets.UTF_8);

        Path resolved = JamLaunchService.droppedJamPath(List.of(root));
        check(resolved == null, "folder drops without a matching .jam should be ignored");
    }

    private static void verifyNonJamFileStillFails() throws Exception {
        Path root = Files.createTempDirectory("jam-drop-invalid");
        Path file = root.resolve("not-a-jam.txt");
        Files.writeString(file, "invalid", StandardCharsets.UTF_8);

        try {
            JamLaunchService.droppedJamPath(List.of(file));
            throw new IllegalStateException("non-.jam file drop should fail");
        } catch (IOException expected) {
            check(expected.getMessage().contains("not a .jam"), "non-.jam file should keep the existing error");
        }
    }

    private static Path writeJam(Path jam) throws Exception {
        Files.writeString(jam, "AppName=Probe\n", StandardCharsets.ISO_8859_1);
        return jam;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
