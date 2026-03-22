package opendoja.host;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class LaunchCompatibility {
    private static final String APPLIED_PROPERTY = "opendoja.launchCompatApplied";
    private static final String DEFAULT_ENCODING_PROPERTY = "opendoja.defaultEncoding";
    private static final String KEEP_EXPLICIT_GC_PROPERTY = "opendoja.keepExplicitGc";
    // Probe these in order and use the first charset the host JVM exposes. The goal is CP-932
    // semantics, but some JVMs resolve the raw "CP932" alias to x-IBM942C instead of the
    // Windows/MS932 mapping the game data expects, so that alias is intentionally not listed here.
    private static final String[] DEFAULT_ENCODINGS = {"MS932", "windows-31j", "Shift_JIS"};

    private LaunchCompatibility() {
    }

    static void reexecJamLauncherIfNeeded(Path jamPath) throws IOException, InterruptedException {
        if (Boolean.getBoolean(APPLIED_PROPERTY)) {
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
            if (arg.startsWith("-D" + APPLIED_PROPERTY + "=")
                    || arg.startsWith("-Dfile.encoding=")
                    || arg.equals("-XX:+DisableExplicitGC")
                    || arg.equals("-XX:-DisableExplicitGC")) {
                continue;
            }
            command.add(arg);
        }
        command.add("-D" + APPLIED_PROPERTY + "=true");
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
        String override = System.getProperty(DEFAULT_ENCODING_PROPERTY);
        if (override != null) {
            String value = override.trim();
            return value.isEmpty() ? null : value;
        }
        for (String candidate : DEFAULT_ENCODINGS) {
            try {
                return Charset.forName(candidate).name();
            } catch (RuntimeException ignored) {
                // Prefer CP-932 semantics first. On this JVM the "CP932" alias resolves to
                // x-IBM942C, which differs from the Windows/MS932 mapping used by the game data,
                // so we probe the known-compatible names explicitly before falling back to plain
                // Shift_JIS.
            }
        }
        return null;
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
        if (Boolean.getBoolean(KEEP_EXPLICIT_GC_PROPERTY)) {
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
