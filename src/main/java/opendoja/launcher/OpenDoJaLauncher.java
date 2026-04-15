package opendoja.launcher;

import opendoja.host.JamLauncher;
import opendoja.host.OpenDoJaLaunchArgs;
import opendoja.host.OpenDoJaLog;

import javax.swing.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class OpenDoJaLauncher {
    static final String APP_NAME = "openDoJa Launcher";
    static final String VERSION = "0.2.1";
    static final String REPOSITORY_URL = "https://github.com/GrenderG/openDoJa";
    static final String LATEST_RELEASE_URL = REPOSITORY_URL + "/releases/latest";
    static final String GITHUB_LATEST_RELEASE_API_URL = "https://api.github.com/repos/GrenderG/openDoJa/releases/latest";
    private static final String PHONE_MODEL_FLAG = "--phone-model";
    private static final String SHOW_OPEN_GLES_FPS_FLAG = "--show-gles-fps";
    private static final String RUN_JAM_FLAG = "--run-jam";
    private static final String RUN_JAM_INTERNAL_FLAG = "--run-jam-internal";
    private static final String SPAWN_JAM_FLAG = "--spawn-jam";

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
            if (PHONE_MODEL_FLAG.equals(args[i])) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Usage: " + usageLine());
                }
                OpenDoJaLaunchArgs.set(OpenDoJaLaunchArgs.MICROEDITION_PLATFORM_OVERRIDE, args[++i]);
                continue;
            }
            if (SHOW_OPEN_GLES_FPS_FLAG.equals(args[i])) {
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
        if (effectiveArgs.size() == 2 && RUN_JAM_FLAG.equals(effectiveArgs.get(0))) {
            GameLaunchSelection selection = new JamGameJarResolver().resolve(Path.of(effectiveArgs.get(1)));
            int exitCode = new LauncherProcessSupport().runInForeground(selection);
            System.exit(exitCode);
        }
        if (effectiveArgs.size() == 2 && RUN_JAM_INTERNAL_FLAG.equals(effectiveArgs.get(0))) {
            JamLauncher.main(new String[]{Path.of(effectiveArgs.get(1)).toString()});
            return;
        }
        if (effectiveArgs.size() == 2 && SPAWN_JAM_FLAG.equals(effectiveArgs.get(0))) {
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
        return RUN_JAM_INTERNAL_FLAG;
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
        return APP_NAME + " [" + PHONE_MODEL_FLAG + " <model>] [" + SHOW_OPEN_GLES_FPS_FLAG + "] [<path-to-jam> | " + RUN_JAM_FLAG + " <path-to-jam> | "
                + SPAWN_JAM_FLAG + " <path-to-jam>]";
    }

    private static String helpText() {
        return usageLine()
                + "\n\nPass custom runtime properties before -jar, for example:"
                + "\n  java -D" + OpenDoJaLaunchArgs.HOST_SCALE + "=fullscreen -jar target/opendoja-{version}.jar <game.jam>"
                + "\n  java -jar target/opendoja-{version}.jar " + SHOW_OPEN_GLES_FPS_FLAG + " <game.jam>"
                + "\n  java -jar target/opendoja-{version}.jar " + PHONE_MODEL_FLAG + " P900i <game.jam>"
                + "\n\n" + OpenDoJaLaunchArgs.formatProperties();
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
