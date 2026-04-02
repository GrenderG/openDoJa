package opendoja.launcher;

import opendoja.host.storage.DoJaStorageHost;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class SdCardFolderBridgeProbe {
    private SdCardFolderBridgeProbe() {
    }

    public static void main(String[] args) throws Exception {
        verifySdCardFolderPathMatchesExt0AndCreates();
        verifyWindowsFallbackCommand();
        verifyMacFallbackCommand();
        verifyLinuxFallbackCommand();
        verifyUnknownPlatformHasNoFallback();

        System.out.println("SD card folder bridge probe OK");
    }

    private static void verifySdCardFolderPathMatchesExt0AndCreates() throws Exception {
        Path expected = DoJaStorageHost.deviceRoot().toAbsolutePath().normalize();
        Path actual = JamLaunchService.ensureSdCardFolder();

        check(expected.equals(actual), "SD card folder should point at ext0");
        check(Files.isDirectory(actual), "SD card folder should be created on demand");
    }

    private static void verifyWindowsFallbackCommand() {
        Path folder = Path.of("probe-folder").toAbsolutePath().normalize();
        List<String> command = FolderOpenSupport.fallbackCommand(folder, "Windows 11");
        check(command != null && command.size() == 2, "Windows fallback should return a command");
        check("explorer".equals(command.getFirst()), "Windows fallback should use explorer");
        check(folder.toString().equals(command.get(1)), "Windows fallback should target the folder path");
    }

    private static void verifyMacFallbackCommand() {
        Path folder = Path.of("probe-folder").toAbsolutePath().normalize();
        List<String> command = FolderOpenSupport.fallbackCommand(folder, "Mac OS X");
        check(command != null && command.size() == 2, "macOS fallback should return a command");
        check("open".equals(command.getFirst()), "macOS fallback should use open");
        check(folder.toString().equals(command.get(1)), "macOS fallback should target the folder path");
    }

    private static void verifyLinuxFallbackCommand() {
        Path folder = Path.of("probe-folder").toAbsolutePath().normalize();
        List<String> command = FolderOpenSupport.fallbackCommand(folder, "Linux");
        check(command != null && command.size() == 2, "Linux fallback should return a command");
        check("xdg-open".equals(command.getFirst()), "Linux fallback should use xdg-open");
        check(folder.toString().equals(command.get(1)), "Linux fallback should target the folder path");
    }

    private static void verifyUnknownPlatformHasNoFallback() {
        Path folder = Path.of("probe-folder").toAbsolutePath().normalize();
        check(FolderOpenSupport.fallbackCommand(folder, "Plan9") == null,
                "unknown platforms should not pretend to have a fallback opener");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
