package opendoja.launcher;

import opendoja.host.JamLauncher;
import opendoja.host.OpenDoJaLaunchArgs;
import opendoja.host.OpenDoJaLog;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.nio.file.Path;

public final class OpenDoJaLauncher {
    static final String APP_NAME = "OpenDoJaLauncher";
    static final String VERSION = "0.1.0";
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
        if (args.length == 0) {
            configureLookAndFeel();
            SwingUtilities.invokeLater(() -> new OpenDoJaLauncherFrame(
                    new JamLaunchService(),
                    new LauncherToolsController()).setVisible(true));
            return;
        }
        if (args.length == 1 && looksLikeJamPath(args[0])) {
            GameLaunchSelection selection = new JamGameJarResolver().resolve(Path.of(args[0]));
            int exitCode = new LauncherProcessSupport().runInForeground(selection);
            System.exit(exitCode);
        }
        if (args.length == 2 && RUN_JAM_FLAG.equals(args[0])) {
            GameLaunchSelection selection = new JamGameJarResolver().resolve(Path.of(args[1]));
            int exitCode = new LauncherProcessSupport().runInForeground(selection);
            System.exit(exitCode);
        }
        if (args.length == 2 && RUN_JAM_INTERNAL_FLAG.equals(args[0])) {
            JamLauncher.main(new String[]{Path.of(args[1]).toString()});
            return;
        }
        if (args.length == 2 && SPAWN_JAM_FLAG.equals(args[0])) {
            GameLaunchSelection selection = new JamGameJarResolver().resolve(Path.of(args[1]));
            Process process = new LauncherProcessSupport().startInBackground(selection);
            OpenDoJaLog.configureIfUnset(OpenDoJaLog.Level.INFO);
            OpenDoJaLog.info(OpenDoJaLauncher.class,
                    "Spawned " + selection.jamPath() + " as pid " + process.pid());
            return;
        }
        if (args.length == 1 && ("--help".equals(args[0]) || "-h".equals(args[0]))) {
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
        return APP_NAME + " [<path-to-jam> | " + RUN_JAM_FLAG + " <path-to-jam> | "
                + SPAWN_JAM_FLAG + " <path-to-jam>]";
    }

    private static String helpText() {
        return usageLine()
                + "\n\nPass custom runtime properties before -jar, for example:"
                + "\n  java -D" + OpenDoJaLaunchArgs.HOST_SCALE + "=2 -jar target/opendoja-0.1.0-SNAPSHOT.jar <game.jam>"
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
