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
        if (OpenDoJaLaunchArgs.getBoolean(OpenDoJaLaunchArgs.LAUNCH_COMPAT_APPLIED)) {
            return;
        }
        String targetEncoding = targetDefaultEncoding();
        boolean needsEncodingCompat = targetEncoding != null && !defaultCharsetMatches(targetEncoding);
        boolean disableExplicitGc = shouldDisableExplicitGc();
        boolean limitHotSpotTier = shouldLimitHotSpotTier();
        boolean disableOnStackReplacement = shouldDisableOnStackReplacement();
        if (!needsEncodingCompat && !disableExplicitGc && !limitHotSpotTier
                && !disableOnStackReplacement) {
            return;
        }

        Process process = new ProcessBuilder(buildCompatibilityCommand(
                        targetEncoding,
                        disableExplicitGc,
                        limitHotSpotTier,
                        disableOnStackReplacement,
                        JamLauncher.class.getName(),
                        new String[]{jamPath.toString()}))
                .inheritIO()
                .start();
        int exit = process.waitFor();
        System.exit(exit);
    }

    static boolean reexecJamLauncherOnVerifyError(Path jamPath) throws IOException, InterruptedException {
        if (OpenDoJaLaunchArgs.getBoolean(OpenDoJaLaunchArgs.VERIFY_FALLBACK_APPLIED) || explicitVerificationArgument() != null) {
            return false;
        }
        // TODO: https://github.com/GrenderG/openDoJa/issues/9 Find a better/clean way?
        // This fallback is intentionally JVM-wide because bytecode verification is also JVM-wide.
        // Keep it as a one-time startup retry only after the title has actually failed with
        // VerifyError, rather than weakening verification for every launch.
        Process process = new ProcessBuilder(buildVerifyFallbackCommand(JamLauncher.class.getName(),
                        new String[]{jamPath.toString()}))
                .inheritIO()
                .start();
        int exit = process.waitFor();
        System.exit(exit);
        return true;
    }

    private static List<String> buildCompatibilityCommand(String targetEncoding,
            boolean disableExplicitGc, boolean limitHotSpotTier,
            boolean disableOnStackReplacement, String mainClass, String[] args) {
        List<String> command = new ArrayList<>();
        command.add(Path.of(OpenDoJaLaunchArgs.get("java.home"), "bin", "java").toString());
        for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (arg.startsWith("-D" + OpenDoJaLaunchArgs.LAUNCH_COMPAT_APPLIED + "=")
                    || arg.startsWith("-Dfile.encoding=")
                    || arg.startsWith("-XX:TieredStopAtLevel=")
                    || arg.equals("-XX:+UseOnStackReplacement")
                    || arg.equals("-XX:-UseOnStackReplacement")
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
        if (limitHotSpotTier) {
            // The official emulator runs on JBlend rather than HotSpot C2. Stopping at tier 1
            // keeps legacy empty polling loops observable without per-title deoptimization.
            command.add("-XX:TieredStopAtLevel=1");
        }
        if (disableOnStackReplacement) {
            // HotSpot OSR can still compile empty scene polling loops into a stale-value spin even
            // when tiering is capped. Disabling OSR keeps those loops on the normal entry path so
            // cross-thread scene handoffs used by legacy titles like DDR remain observable.
            command.add("-XX:-UseOnStackReplacement");
        }
        // Many DoJa-era games decode resource tables through String(byte[], off, len), which
        // follows the VM default charset. Modern Java defaults to UTF-8, but the handset-era
        // blobs here are Shift-JIS/Windows-31J encoded.
        if (targetEncoding != null) {
            command.add("-Dfile.encoding=" + targetEncoding);
        }
        command.add("-cp");
        command.add(OpenDoJaLaunchArgs.get("java.class.path"));
        command.add(mainClass);
        for (String arg : args) {
            command.add(arg);
        }
        return command;
    }

    private static List<String> buildVerifyFallbackCommand(String mainClass, String[] args) {
        List<String> command = new ArrayList<>();
        command.add(Path.of(OpenDoJaLaunchArgs.get("java.home"), "bin", "java").toString());
        for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (arg.startsWith("-D" + OpenDoJaLaunchArgs.VERIFY_FALLBACK_APPLIED + "=")
                    || arg.startsWith("-Xverify:")
                    || arg.equals("-noverify")) {
                continue;
            }
            command.add(arg);
        }
        command.add("-D" + OpenDoJaLaunchArgs.VERIFY_FALLBACK_APPLIED + "=true");
        command.add("-Xverify:none");
        command.add("-cp");
        command.add(OpenDoJaLaunchArgs.get("java.class.path"));
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
        String override = OpenDoJaLaunchArgs.get(OpenDoJaLaunchArgs.DEFAULT_ENCODING, null);
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
        if (OpenDoJaLaunchArgs.getBoolean(OpenDoJaLaunchArgs.KEEP_EXPLICIT_GC)) {
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

    private static String explicitVerificationArgument() {
        for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (arg.startsWith("-Xverify:") || arg.equals("-noverify")) {
                return arg;
            }
        }
        return null;
    }

    private static boolean shouldLimitHotSpotTier() {
        for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (arg.equals("-Xint")
                    || arg.startsWith("-XX:TieredStopAtLevel=")
                    || arg.equals("-XX:+TieredCompilation")
                    || arg.equals("-XX:-TieredCompilation")) {
                return false;
            }
        }
        return true;
    }

    private static boolean shouldDisableOnStackReplacement() {
        for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (arg.equals("-Xint")
                    || arg.equals("-XX:+UseOnStackReplacement")
                    || arg.equals("-XX:-UseOnStackReplacement")) {
                return false;
            }
        }
        return true;
    }
}
