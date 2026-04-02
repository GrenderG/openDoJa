package opendoja.launcher;

import opendoja.host.OpenDoJaLaunchArgs;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

final class FolderOpenSupport {
    private FolderOpenSupport() {
    }

    static void openDirectory(Path directory) throws IOException {
        Path normalized = Files.createDirectories(directory.toAbsolutePath().normalize());
        if (tryDesktopOpen(normalized)) {
            return;
        }
        List<String> fallbackCommand = fallbackCommand(normalized, OpenDoJaLaunchArgs.get("os.name", ""));
        if (fallbackCommand == null) {
            throw new IOException("Could not open folder on this platform: " + normalized);
        }
        try {
            new ProcessBuilder(fallbackCommand).start();
        } catch (IOException exception) {
            throw new IOException("Could not open folder: " + normalized, exception);
        }
    }

    static List<String> fallbackCommand(Path directory, String osName) {
        String normalizedOs = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        String path = directory.toAbsolutePath().normalize().toString();
        if (normalizedOs.contains("win")) {
            return List.of("explorer", path);
        }
        if (normalizedOs.contains("mac")) {
            return List.of("open", path);
        }
        if (normalizedOs.contains("nux")
                || normalizedOs.contains("nix")
                || normalizedOs.contains("aix")
                || normalizedOs.contains("linux")) {
            return List.of("xdg-open", path);
        }
        return null;
    }

    private static boolean tryDesktopOpen(Path directory) {
        if (!Desktop.isDesktopSupported()) {
            return false;
        }
        try {
            Desktop desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.OPEN)) {
                return false;
            }
            desktop.open(directory.toFile());
            return true;
        } catch (RuntimeException | IOException exception) {
            return false;
        }
    }
}
