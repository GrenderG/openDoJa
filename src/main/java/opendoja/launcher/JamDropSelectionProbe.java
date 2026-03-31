package opendoja.launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class JamDropSelectionProbe {
    private JamDropSelectionProbe() {
    }

    public static void main(String[] args) throws Exception {
        verifyAcceptsSingleJam();
        verifyRejectsMultipleFiles();
        verifyRejectsNonJam();

        System.out.println("Jam drop selection probe OK");
    }

    private static void verifyAcceptsSingleJam() throws Exception {
        Path root = Files.createTempDirectory("jam-drop-ok");
        Path jam = Files.writeString(root.resolve("game.jam"), "");
        Path resolved = JamLaunchService.droppedJamPath(List.of(jam));
        check(resolved.equals(jam.toAbsolutePath().normalize()), "single dropped JAM should be accepted");
    }

    private static void verifyRejectsMultipleFiles() throws Exception {
        Path root = Files.createTempDirectory("jam-drop-many");
        Path first = Files.writeString(root.resolve("first.jam"), "");
        Path second = Files.writeString(root.resolve("second.jam"), "");
        expectFailure(List.of(first, second), "Drop exactly one .jam file.");
    }

    private static void verifyRejectsNonJam() throws Exception {
        Path root = Files.createTempDirectory("jam-drop-non-jam");
        Path txt = Files.writeString(root.resolve("notes.txt"), "");
        expectFailure(List.of(txt), "Dropped file is not a .jam");
    }

    private static void expectFailure(List<Path> droppedPaths, String expectedMessageFragment) throws Exception {
        try {
            JamLaunchService.droppedJamPath(droppedPaths);
            throw new IllegalStateException("Expected dropped-file validation to fail");
        } catch (IOException e) {
            if (!e.getMessage().contains(expectedMessageFragment)) {
                throw new IllegalStateException("Unexpected error message: " + e.getMessage(), e);
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
