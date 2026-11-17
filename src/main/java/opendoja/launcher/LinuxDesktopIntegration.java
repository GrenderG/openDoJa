package opendoja.launcher;

import opendoja.host.OpenDoJaLog;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

/** Opt-in, per-user application-menu integration for the portable Linux JAR. */
final class LinuxDesktopIntegration {
    private static final String APP_ID = "dev.opendoja.OpenDoJa";
    private static final String MANAGED_KEY = "X-OpenDoJa-Managed=true";

    private LinuxDesktopIntegration() {
    }

    static boolean isAvailable() {
        String osName = System.getProperty("os.name", "");
        if (!"Linux".equalsIgnoreCase(osName)) {
            return false;
        }
        try {
            return isAvailable(osName, LauncherProcessSupport.resolveLauncherArtifact());
        } catch (IOException | SecurityException exception) {
            return false;
        }
    }

    static boolean isAvailable(String osName, Path artifact) {
        return "Linux".equalsIgnoreCase(osName)
                && Files.isRegularFile(artifact)
                && artifact.getFileName().toString().endsWith(".jar");
    }

    static Path install() throws IOException {
        if (!isAvailable()) {
            throw new IOException("Application launcher creation requires a packaged JAR running on Linux.");
        }
        return install(dataHome(), javaExecutable(), LauncherProcessSupport.resolveLauncherArtifact());
    }

    static void refreshIfInstalled() {
        if (!isAvailable()) {
            return;
        }
        try {
            refreshIfInstalled(dataHome(), javaExecutable(), LauncherProcessSupport.resolveLauncherArtifact());
        } catch (IOException | RuntimeException exception) {
            OpenDoJaLog.warn(LinuxDesktopIntegration.class,
                    "Could not update application launcher: " + exception.getMessage());
        }
    }

    static boolean refreshIfInstalled(Path dataHome, Path javaExecutable, Path jar) throws IOException {
        Path desktopFile = desktopFile(dataHome);
        if (!Files.isRegularFile(desktopFile) || !isManaged(Files.readString(desktopFile))) {
            return false;
        }
        install(dataHome, javaExecutable, jar);
        return true;
    }

    static Path install(Path dataHome, Path javaExecutable, Path jar) throws IOException {
        String executable = javaExecutable.toAbsolutePath().normalize().toString();
        if (executable.contains("=")) {
            throw new IOException("The Java executable path cannot contain '=' when creating an application launcher.");
        }
        String entry = "[Desktop Entry]\n"
                + "Type=Application\n"
                + "Name=openDoJa\n"
                + "Comment=Play DoJa i-appli games\n"
                // GIO checks the executable before expanding %% escapes. env keeps the
                // Java path in an argument, where paths containing '%' work too.
                + "Exec=env -- " + execArgument(executable) + " -jar "
                + execArgument(jar.toAbsolutePath().normalize().toString()) + "\n"
                + "Icon=" + APP_ID + "\n"
                + "StartupWMClass=" + OpenDoJaLauncher.class.getName().replace('.', '-') + "\n"
                + "Terminal=false\n"
                + "Categories=Game;Emulator;\n"
                + MANAGED_KEY + "\n";
        try (InputStream icon = LinuxDesktopIntegration.class.getResourceAsStream("/opendoja/images/branding/logo.svg")) {
            if (icon == null) {
                throw new IOException("The application logo is missing from the JAR.");
            }
            writeIfChanged(dataHome.resolve("icons/hicolor/scalable/apps/" + APP_ID + ".svg"), icon.readAllBytes());
        }
        Path desktopFile = desktopFile(dataHome);
        writeIfChanged(desktopFile, entry.getBytes(StandardCharsets.UTF_8));
        return desktopFile;
    }

    private static Path javaExecutable() {
        return Path.of(System.getProperty("java.home"), "bin", "java");
    }

    private static Path dataHome() {
        return dataHome(System.getenv("XDG_DATA_HOME"), System.getProperty("user.home"));
    }

    static Path dataHome(String configured, String userHome) {
        if (configured != null && !configured.isEmpty()) {
            Path path = Path.of(configured);
            if (path.isAbsolute()) {
                return path.normalize();
            }
        }
        return Path.of(userHome, ".local", "share").toAbsolutePath().normalize();
    }

    private static Path desktopFile(Path dataHome) {
        return dataHome.resolve("applications/" + APP_ID + ".desktop");
    }

    private static boolean isManaged(String entry) {
        boolean desktopSection = false;
        for (String rawLine : entry.lines().toList()) {
            String line = rawLine.strip();
            if (line.startsWith("[")) {
                desktopSection = line.equals("[Desktop Entry]");
            } else if (desktopSection && line.equals(MANAGED_KEY)) {
                return true;
            }
        }
        return false;
    }

    private static String execArgument(String value) {
        // Exec quoting is decoded after desktop-entry string escapes: apply both layers.
        String quoted = "\"" + value.replace("\\", "\\\\")
                .replace("\"", "\\\"").replace("`", "\\`").replace("$", "\\$")
                .replace("%", "%%") + "\"";
        return quoted.replace("\\", "\\\\").replace("\n", "\\n")
                .replace("\r", "\\r").replace("\t", "\\t");
    }

    private static void writeIfChanged(Path target, byte[] content) throws IOException {
        if (Files.isRegularFile(target) && Arrays.equals(Files.readAllBytes(target), content)) {
            return;
        }
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".opendoja-", ".tmp");
        try {
            Files.write(temporary, content);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
