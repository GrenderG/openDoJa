package opendoja.launcher;

import opendoja.host.DoJaEncoding;
import opendoja.host.OpenDoJaLaunchArgs;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;

final class LauncherProcessSupport {
    static final String ENABLE_NATIVE_ACCESS_ARGUMENT = "--enable-native-access=ALL-UNNAMED";
    private static final String JAVA2D_UI_SCALE_ENABLED = "sun.java2d.uiScale.enabled";

    Process startInBackground(GameLaunchSelection selection) throws IOException {
        return startInBackground(selection, null);
    }

    Process startInBackground(GameLaunchSelection selection, LauncherSettings settings) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder(buildLaunchCommand(selection, settings));
        if (selection.jamPath().getParent() != null) {
            processBuilder.directory(selection.jamPath().getParent().toFile());
        }
        processBuilder.inheritIO();
        return processBuilder.start();
    }

    int runInForeground(GameLaunchSelection selection) throws IOException, InterruptedException {
        Process process = startInBackground(selection);
        return process.waitFor();
    }

    List<String> buildLaunchCommand(GameLaunchSelection selection) throws IOException {
        return buildLaunchCommand(selection, null);
    }

    List<String> buildLaunchCommand(GameLaunchSelection selection, LauncherSettings settings) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(Path.of(OpenDoJaLaunchArgs.get("java.home"), "bin", "java").toString());
        command.add(ENABLE_NATIVE_ACCESS_ARGUMENT);
        if (settings != null && settings.disableBytecodeVerification()) {
            command.add("-Xverify:none");
        }
        Set<String> overriddenProperties = appendForwardedProperties(command, System.getProperties(), settings);
        appendLauncherSettings(command, settings, overriddenProperties);
        appendFileEncoding(command, settings);
        command.add("-cp");
        command.add(resolveLauncherArtifact() + File.pathSeparator + selection.gameJarPath());
        command.add(OpenDoJaLauncher.class.getName());
        command.add(OpenDoJaLauncher.internalRunJamFlag());
        command.add(selection.jamPath().toString());
        return command;
    }

    private Set<String> appendForwardedProperties(List<String> command, Properties properties, LauncherSettings settings) {
        Set<String> forwarded = new HashSet<>();
        boolean disableOsDpiScaling = settings != null && settings.disableOsDpiScaling();
        for (String name : properties.stringPropertyNames()) {
            if (disableOsDpiScaling
                    && (name.equals("sun.java2d.uiScale") || name.equals(JAVA2D_UI_SCALE_ENABLED))) {
                continue;
            }
            if (name.startsWith("opendoja.")
                    || name.equals("java.awt.headless")
                    || name.equals("sun.java2d.uiScale")
                    || name.equals(JAVA2D_UI_SCALE_ENABLED)) {
                command.add("-D" + name + "=" + properties.getProperty(name));
                forwarded.add(name);
            }
        }
        return forwarded;
    }

    private void appendLauncherSettings(List<String> command, LauncherSettings settings, Set<String> overriddenProperties) {
        if (settings == null) {
            return;
        }
        appendProperty(command, overriddenProperties, OpenDoJaLaunchArgs.HOST_SCALE, Integer.toString(settings.hostScale()));
        appendProperty(command, overriddenProperties, OpenDoJaLaunchArgs.MLD_SYNTH, settings.synthId());
        appendProperty(command, overriddenProperties, OpenDoJaLaunchArgs.TERMINAL_ID, settings.terminalId());
        appendProperty(command, overriddenProperties, OpenDoJaLaunchArgs.USER_ID, settings.userId());
        appendProperty(command, overriddenProperties, OpenDoJaLaunchArgs.FONT_TYPE, settings.fontType());
        appendProperty(command, overriddenProperties, OpenDoJaLaunchArgs.HTTP_OVERRIDE_DOMAIN, settings.httpOverrideDomain());
        appendProperty(command, overriddenProperties, OpenDoJaLaunchArgs.MICROEDITION_PLATFORM_OVERRIDE,
                settings.microeditionPlatformOverride());
        appendProperty(command, overriddenProperties, OpenDoJaLaunchArgs.OPEN_GLES_RENDERER,
                settings.openGlesRendererMode().id());
        appendProperty(command, overriddenProperties, OpenDoJaLaunchArgs.SHOW_OPEN_GLES_FPS,
                Boolean.toString(settings.showOpenGlesFps()));
        appendProperty(command, overriddenProperties, OpenDoJaLaunchArgs.OPEN_GLES_SUPERSAMPLE_SCALE,
                Integer.toString(settings.openGlesSupersampleScale()));
        if (settings.disableOsDpiScaling()) {
            // Oracle's Java 2D troubleshooting docs recommend uiScale.enabled=false to disable
            // high-DPI scaling, while noting dpiaware=false no longer affects JDK 9+ on Windows.
            appendProperty(command, overriddenProperties, JAVA2D_UI_SCALE_ENABLED, "false");
        }
    }

    private void appendProperty(List<String> command, Set<String> overriddenProperties, String name, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (overriddenProperties != null && overriddenProperties.contains(name)) {
            return;
        }
        if (overriddenProperties != null) {
            overriddenProperties.add(name);
        }
        command.add("-D" + name + "=" + value);
    }

    private void appendFileEncoding(List<String> command, LauncherSettings settings) {
        String fileEncoding = settings == null ? null : settings.fileEncodingOverride();
        if (fileEncoding == null || fileEncoding.isBlank()) {
            fileEncoding = DoJaEncoding.explicitFileEncodingLaunchArgument();
        }
        if (fileEncoding == null) {
            fileEncoding = DoJaEncoding.defaultCharsetName();
        }
        if (fileEncoding == null || fileEncoding.isBlank()) {
            return;
        }
        command.add("-Dfile.encoding=" + fileEncoding);
    }

    private Path resolveLauncherArtifact() throws IOException {
        try {
            URL location = OpenDoJaLauncher.class.getProtectionDomain().getCodeSource().getLocation();
            if (location != null) {
                return Path.of(location.toURI()).toAbsolutePath().normalize();
            }
        } catch (URISyntaxException e) {
            throw new IOException("Could not resolve launcher location", e);
        }
        throw new IOException("Could not resolve launcher location");
    }
}
