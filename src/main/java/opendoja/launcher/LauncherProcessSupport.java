package opendoja.launcher;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import opendoja.host.OpenDoJaLaunchArgs;

final class LauncherProcessSupport {
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
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        Set<String> overriddenProperties = appendForwardedProperties(command, System.getProperties());
        appendLauncherSettings(command, settings, overriddenProperties);
        command.add("-cp");
        command.add(resolveLauncherArtifact() + File.pathSeparator + selection.gameJarPath());
        command.add(OpenDoJaLauncher.class.getName());
        command.add(OpenDoJaLauncher.internalRunJamFlag());
        command.add(selection.jamPath().toString());
        return command;
    }

    private Set<String> appendForwardedProperties(List<String> command, Properties properties) {
        Set<String> forwarded = new HashSet<>();
        for (String name : properties.stringPropertyNames()) {
            if (name.startsWith("opendoja.") || name.equals("java.awt.headless") || name.equals("sun.java2d.uiScale")) {
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
