package opendoja.host;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class LaunchCompatibility {
    private LaunchCompatibility() {
    }

    static void reexecJamLauncherIfNeeded(Path jamPath) throws IOException, InterruptedException {
        if (Boolean.getBoolean(OpenDoJaLaunchArgs.LAUNCH_COMPAT_APPLIED)) {
            return;
        }
        String targetEncoding = targetDefaultEncoding();
        boolean needsEncodingCompat = targetEncoding != null && !defaultCharsetMatches(targetEncoding);
        boolean disableExplicitGc = shouldDisableExplicitGc();
        if (!needsEncodingCompat && !disableExplicitGc) {
            return;
        }

        Process process = new ProcessBuilder(buildJavaCommand(targetEncoding, disableExplicitGc, JamLauncher.class.getName(),
                        new String[]{jamPath.toString()}))
                .inheritIO()
                .start();
        int exit = process.waitFor();
        System.exit(exit);
    }

    private static List<String> buildJavaCommand(String targetEncoding, boolean disableExplicitGc, String mainClass, String[] args) {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (arg.startsWith("-D" + OpenDoJaLaunchArgs.LAUNCH_COMPAT_APPLIED + "=")
                    || arg.startsWith("-Dfile.encoding=")
                    || arg.equals("-XX:+DisableExplicitGC")
                    || arg.equals("-XX:-DisableExplicitGC")) {
                continue;
            }
            command.add(arg);
        }
        command.add("-D" + OpenDoJaLaunchArgs.LAUNCH_COMPAT_APPLIED + "=true");
        if (disableExplicitGc) {
            // Games issue System.gc() liberally around UI/resource transitions as a lightweight
            // handset-era memory hint. On desktop HotSpot that becomes a blocking full GC, which
            // stalls the single game thread and drags audio down with it.
            command.add("-XX:+DisableExplicitGC");
        }
        // Many DoJa-era games decode resource tables through String(byte[], off, len), which
        // follows the VM default charset. Modern Java defaults to UTF-8, but the handset-era
        // blobs here are Shift-JIS/Windows-31J encoded.
        if (targetEncoding != null) {
            command.add("-Dfile.encoding=" + targetEncoding);
        }
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(mainClass);
        for (String arg : args) {
            command.add(arg);
        }
        return command;
    }

    private static String targetDefaultEncoding() {
        if (explicitFileEncodingArgument() != null) {
            return null;
        }
        String override = System.getProperty(OpenDoJaLaunchArgs.DEFAULT_ENCODING);
        if (override != null) {
            String value = override.trim();
            return value.isEmpty() ? null : value;
        }
        return DoJaEncoding.defaultCharsetName();
    }

    private static String explicitFileEncodingArgument() {
        for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (arg.startsWith("-Dfile.encoding=")) {
                return arg.substring("-Dfile.encoding=".length());
            }
        }
        return null;
    }

    private static boolean shouldDisableExplicitGc() {
        if (Boolean.getBoolean(OpenDoJaLaunchArgs.KEEP_EXPLICIT_GC)) {
            return false;
        }
        return explicitGcArgument() == null;
    }

    private static String explicitGcArgument() {
        for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (arg.equals("-XX:+DisableExplicitGC") || arg.equals("-XX:-DisableExplicitGC")) {
                return arg;
            }
        }
        return null;
    }

    private static boolean defaultCharsetMatches(String targetEncoding) {
        try {
            return Charset.defaultCharset().name().equalsIgnoreCase(Charset.forName(targetEncoding).name());
        } catch (RuntimeException ignored) {
            return true;
        }
    }
}
