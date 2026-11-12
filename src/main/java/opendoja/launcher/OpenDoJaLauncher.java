package opendoja.launcher;

import opendoja.host.JamLauncher;
import opendoja.host.LaunchConfig;
import opendoja.host.OpenDoJaCliFlags;
import opendoja.host.OpenDoJaLaunchArgs;
import opendoja.host.OpenDoJaLog;

import javax.swing.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class OpenDoJaLauncher {
    static final String APP_NAME = "openDoJa Launcher";
    static final String VERSION = "0.2.6";
    static final String REPOSITORY_URL = "https://github.com/GrenderG/openDoJa";
    static final String LATEST_RELEASE_URL = REPOSITORY_URL + "/releases/latest";
    static final String GITHUB_LATEST_RELEASE_API_URL = "https://api.github.com/repos/GrenderG/openDoJa/releases/latest";

    private OpenDoJaLauncher() {
    }

    public static void main(String[] args) {
        try {
            run(args);
        } catch (Exception e) {
            reportFailure(args, e);
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        List<String> effectiveArgs = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if (OpenDoJaCliFlags.PHONE_MODEL.equals(args[i])) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Usage: " + usageLine());
                }
                OpenDoJaLaunchArgs.set(OpenDoJaLaunchArgs.MICROEDITION_PLATFORM_OVERRIDE, args[++i]);
                continue;
            }
            if (OpenDoJaCliFlags.LAUNCH_TYPE.equals(args[i])) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Usage: " + usageLine());
                }
                OpenDoJaLaunchArgs.set(OpenDoJaLaunchArgs.LAUNCH_TYPE, requireLaunchType(args[++i]).id);
                continue;
            }
            if (OpenDoJaCliFlags.SCREEN_ROTATION.equals(args[i])) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Usage: " + usageLine());
                }
                OpenDoJaLaunchArgs.set(OpenDoJaLaunchArgs.DISPLAY_ROTATION, requireDisplayRotation(args[++i]));
                continue;
            }
            if (OpenDoJaCliFlags.SHOW_OPEN_GLES_FPS.equals(args[i])) {
                OpenDoJaLaunchArgs.set(OpenDoJaLaunchArgs.SHOW_OPEN_GLES_FPS, Boolean.TRUE.toString());
                continue;
            }
            effectiveArgs.add(args[i]);
        }
        if (effectiveArgs.isEmpty() && args.length == 0) {
            configureLookAndFeel();
            SwingUtilities.invokeLater(() -> {
                OpenDoJaLauncherFrame frame = new OpenDoJaLauncherFrame(
                        new JamLaunchService(),
                        new LauncherSettingsController());
                frame.setVisible(true);
                frame.handleInitialStartup();
            });
            return;
        }
        if (effectiveArgs.size() == 1 && looksLikeJamPath(effectiveArgs.get(0))) {
            GameLaunchSelection selection = new JamGameJarResolver().resolve(Path.of(effectiveArgs.get(0)));
            int exitCode = new LauncherProcessSupport().runInForeground(selection);
            System.exit(exitCode);
        }
        if (effectiveArgs.size() == 2 && OpenDoJaCliFlags.RUN_JAM.equals(effectiveArgs.get(0))) {
            GameLaunchSelection selection = new JamGameJarResolver().resolve(Path.of(effectiveArgs.get(1)));
            int exitCode = new LauncherProcessSupport().runInForeground(selection);
            System.exit(exitCode);
        }
        if (effectiveArgs.size() == 2 && OpenDoJaCliFlags.RUN_JAM_INTERNAL.equals(effectiveArgs.get(0))) {
            JamLauncher.main(new String[]{Path.of(effectiveArgs.get(1)).toString()});
            return;
        }
        if (effectiveArgs.size() == 2 && OpenDoJaCliFlags.SPAWN_JAM.equals(effectiveArgs.get(0))) {
            GameLaunchSelection selection = new JamGameJarResolver().resolve(Path.of(effectiveArgs.get(1)));
            Process process = new LauncherProcessSupport().startInBackground(selection);
            OpenDoJaLog.configureIfUnset(OpenDoJaLog.Level.INFO);
            OpenDoJaLog.info(OpenDoJaLauncher.class,
                    "Spawned " + selection.jamPath() + " as pid " + process.pid());
            return;
        }
        if (effectiveArgs.size() == 1 && ("--help".equals(effectiveArgs.get(0)) || "-h".equals(effectiveArgs.get(0)))) {
            printUsage();
            return;
        }
        throw new IllegalArgumentException("Usage: " + usageLine());
    }

    static String internalRunJamFlag() {
        return OpenDoJaCliFlags.RUN_JAM_INTERNAL;
    }

    private static void configureLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
    }

    private static void printUsage() {
        OpenDoJaLog.configure(OpenDoJaLog.Level.INFO);
        OpenDoJaLog.info(OpenDoJaLauncher.class, helpText());
    }

    private static String usageLine() {
        return APP_NAME + " [" + OpenDoJaCliFlags.PHONE_MODEL + " <model>] ["
                + OpenDoJaCliFlags.LAUNCH_TYPE + " <normal|standby>] ["
                + OpenDoJaCliFlags.SCREEN_ROTATION + " <" + OpenDoJaLaunchArgs.displayRotationChoices() + ">] ["
                + OpenDoJaCliFlags.SHOW_OPEN_GLES_FPS + "] [<path-to-jam> | " + OpenDoJaCliFlags.RUN_JAM + " <path-to-jam> | "
                + OpenDoJaCliFlags.SPAWN_JAM + " <path-to-jam>]";
    }

    private static String helpText() {
        return usageLine()
                + "\n\nPass custom runtime properties before -jar, for example:"
                + "\n  java -D" + OpenDoJaLaunchArgs.HOST_SCALE + "=fullscreen -jar target/opendoja-{version}.jar <game.jam>"
                + "\n  java -jar target/opendoja-{version}.jar " + OpenDoJaCliFlags.SHOW_OPEN_GLES_FPS + " <game.jam>"
                + "\n  java -jar target/opendoja-{version}.jar " + OpenDoJaCliFlags.PHONE_MODEL + " P900i <game.jam>"
                + "\n  java -jar target/opendoja-{version}.jar " + OpenDoJaCliFlags.LAUNCH_TYPE + " standby <game.jam>"
                + "\n  java -jar target/opendoja-{version}.jar " + OpenDoJaCliFlags.SCREEN_ROTATION + " right <game.jam>"
                + "\n  java -jar target/opendoja-{version}.jar " + OpenDoJaCliFlags.SCREEN_ROTATION + " upside-down <game.jam>"
                + "\n\n" + OpenDoJaLaunchArgs.formatProperties();
    }

    private static LaunchConfig.LaunchTypeOption requireLaunchType(String value) {
        LaunchConfig.LaunchTypeOption launchType = LaunchConfig.LaunchTypeOption.fromId(value);
        if (launchType == null) {
            throw new IllegalArgumentException("Unknown launch type: " + value + ". Expected normal or standby.");
        }
        return launchType;
    }

    private static String requireDisplayRotation(String value) {
        String normalized = OpenDoJaLaunchArgs.normalizeDisplayRotation(value);
        if (!normalized.equals(value == null ? null : value.trim().toLowerCase(java.util.Locale.ROOT))) {
            throw new IllegalArgumentException(
                    "Unknown screen rotation: " + value + ". Expected "
                            + OpenDoJaLaunchArgs.displayRotationChoicesText() + ".");
        }
        return normalized;
    }

    private static boolean looksLikeJamPath(String arg) {
        return arg.toLowerCase().endsWith(".jam");
    }

    private static void reportFailure(String[] args, Exception e) {
        OpenDoJaLog.configure(OpenDoJaLog.Level.ERROR);
        OpenDoJaLog.error(OpenDoJaLauncher.class, e.getMessage());
        if (args.length == 0) {
            JOptionPane.showMessageDialog(
                    null,
                    e.getMessage(),
                    APP_NAME,
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}
