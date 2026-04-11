package opendoja.host;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

final class LaunchCompatibility {
    private LaunchCompatibility() {
    }

    static void reexecJamLauncherIfNeeded(Path jamPath) throws IOException, InterruptedException {
        if (OpenDoJaLaunchArgs.getBoolean(OpenDoJaLaunchArgs.LAUNCH_COMPAT_APPLIED)) {
            return;
        }
        boolean interpretLegacyBusyWaits = shouldUseInterpreterForLegacyJam(jamPath);
        boolean disableExplicitGc = shouldDisableExplicitGc();
        boolean limitHotSpotTier = shouldLimitHotSpotTier();
        boolean disableOnStackReplacement = shouldDisableOnStackReplacement();
        if (!interpretLegacyBusyWaits
                && !disableExplicitGc && !limitHotSpotTier
                && !disableOnStackReplacement) {
            return;
        }

        Process process = new ProcessBuilder(buildCompatibilityCommand(
                        interpretLegacyBusyWaits,
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

    private static List<String> buildCompatibilityCommand(boolean interpretLegacyBusyWaits,
            boolean disableExplicitGc, boolean limitHotSpotTier,
            boolean disableOnStackReplacement, String mainClass, String[] args) {
        List<String> command = new ArrayList<>();
        command.add(Path.of(OpenDoJaLaunchArgs.get("java.home"), "bin", "java").toString());
        for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (arg.startsWith("-D" + OpenDoJaLaunchArgs.LAUNCH_COMPAT_APPLIED + "=")
                    || arg.equals("-Xint")
                    || arg.startsWith("-XX:TieredStopAtLevel=")
                    || arg.equals("-XX:+UseOnStackReplacement")
                    || arg.equals("-XX:-UseOnStackReplacement")
                    || arg.equals("-XX:+DisableExplicitGC")
                    || arg.equals("-XX:-DisableExplicitGC")) {
                continue;
            }
            command.add(arg);
        }
        appendCurrentOpenDoJaProperties(command);
        command.add("-D" + OpenDoJaLaunchArgs.LAUNCH_COMPAT_APPLIED + "=true");
        if (disableExplicitGc) {
            // Games issue System.gc() liberally around UI/resource transitions as a lightweight
            // handset-era memory hint. On desktop HotSpot that becomes a blocking full GC, which
            // stalls the single game thread and drags audio down with it.
            command.add("-XX:+DisableExplicitGC");
        }
        if (interpretLegacyBusyWaits) {
            // Early DoJa Apps exchange frame/timer ownership through unsynchronized
            // empty polling loops. Running those apps in the interpreter keeps each flag load
            // observable enough to preserve the handset-era handoff behavior.
            command.add("-Xint");
        } else if (limitHotSpotTier) {
            // The official emulator runs on JBlend rather than HotSpot C2. Stopping at tier 1
            // keeps legacy empty polling loops observable without per-title deoptimization.
            command.add("-XX:TieredStopAtLevel=1");
        }
        if (!interpretLegacyBusyWaits && disableOnStackReplacement) {
            // HotSpot OSR can still compile empty scene polling loops into a stale-value spin even
            // when tiering is capped. Disabling OSR keeps those loops on the normal entry path so
            // cross-thread scene handoffs used by legacy titles like DDR remain observable.
            command.add("-XX:-UseOnStackReplacement");
        }
        command.add("-cp");
        command.add(OpenDoJaLaunchArgs.get("java.class.path"));
        command.add(mainClass);
        for (String arg : args) {
            command.add(arg);
        }
        return command;
    }

    private static boolean shouldUseInterpreterForLegacyJam(Path jamPath) {
        if (explicitCompilationModeArgument() != null) {
            return false;
        }
        try {
            Properties properties = JamLauncher.loadJamProperties(jamPath);
            Map<String, String> parameters = new HashMap<>();
            for (String name : properties.stringPropertyNames()) {
                parameters.put(name, properties.getProperty(name));
            }
            DoJaProfile profile = DoJaProfile.fromParametersOrDocumentedDeviceIdentity(parameters);
            return profile.isKnown() && profile.isBefore(3, 0);
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
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
        appendCurrentOpenDoJaProperties(command);
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

    private static void appendCurrentOpenDoJaProperties(List<String> command) {
        for (String name : System.getProperties().stringPropertyNames()) {
            if (!name.startsWith("opendoja.")) {
                continue;
            }
            if (name.equals(OpenDoJaLaunchArgs.LAUNCH_COMPAT_APPLIED)
                    || name.equals(OpenDoJaLaunchArgs.VERIFY_FALLBACK_APPLIED)) {
                continue;
            }
            String value = System.getProperty(name);
            if (value != null) {
                command.add("-D" + name + "=" + value);
            }
        }
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

    private static String explicitVerificationArgument() {
        for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (arg.startsWith("-Xverify:") || arg.equals("-noverify")) {
                return arg;
            }
        }
        return null;
    }

    private static String explicitCompilationModeArgument() {
        for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (arg.equals("-Xint")
                    || arg.startsWith("-XX:TieredStopAtLevel=")
                    || arg.equals("-XX:+TieredCompilation")
                    || arg.equals("-XX:-TieredCompilation")
                    || arg.equals("-XX:+UseOnStackReplacement")
                    || arg.equals("-XX:-UseOnStackReplacement")) {
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
