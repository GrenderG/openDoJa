package opendoja.probes;

import com.nttdocomo.ui.IApplication;
import com.nttdocomo.ui.Image;
import opendoja.audio.mld.MLDSynth;
import opendoja.host.DoJaEncoding;
import opendoja.host.HostControlAction;
import opendoja.host.HostInputBinding;
import opendoja.host.HostKeybindConfiguration;
import opendoja.host.HostKeybindProfile;
import opendoja.host.HostScale;
import opendoja.host.LaunchConfig;
import opendoja.host.OpenDoJaIdentity;
import opendoja.host.OpenDoJaLaunchArgs;
import opendoja.host.OpenGlesRendererMode;
import opendoja.host.input.ControllerBindingDescriptor;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public final class LauncherProcessSupportProbe {
    private static final String OUTPUT_PARAMETER = "EncodingProbeOutput";
    private static final String GAME_LAUNCH_SELECTION_CLASS_NAME = "opendoja.launcher.GameLaunchSelection";
    private static final String LAUNCHER_PROCESS_SUPPORT_CLASS_NAME = "opendoja.launcher.LauncherProcessSupport";
    private static final String LAUNCHER_SETTINGS_CLASS_NAME = "opendoja.launcher.LauncherSettings";

    private LauncherProcessSupportProbe() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 1) {
            throw new IllegalArgumentException("Usage: LauncherProcessSupportProbe [expected-file-encoding]");
        }
        System.setProperty("java.awt.headless", "true");
        String expectedEncoding = args.length == 0 ? DoJaEncoding.defaultCharsetName() : args[0];
        verifyBuildLaunchCommandAddsExpectedFileEncoding(expectedEncoding);
        verifySpawnedJamSeesExpectedFileEncoding(expectedEncoding);
        verifyLauncherSettingsOverrideEncoding();
        verifyLauncherSettingsForwardFullscreenHostScale();
        verifyLauncherSettingsForwardStandbyLaunchType();
        verifyLauncherSettingsForwardActiveKeybindProfile();
        verifySpawnedHardwareLaunchAvoidsNativeAccessWarning();
        System.out.println("Launcher process support probe OK");
    }

    private static void verifyBuildLaunchCommandAddsExpectedFileEncoding(String expectedEncoding) throws Exception {
        Object selection = newGameLaunchSelection(Path.of("probe.jam"), Path.of("probe.jar"));
        List<String> command = buildLaunchCommand(selection);
        String nativeAccessArgument = enableNativeAccessArgument();
        check(command.contains(nativeAccessArgument),
                "launch command should contain " + nativeAccessArgument + " but was " + command);
        String expectedArgument = "-Dfile.encoding=" + expectedEncoding;
        check(command.contains(expectedArgument),
                "launch command should contain " + expectedArgument + " but was " + command);
        check(command.stream().filter(arg -> arg.startsWith("-Dfile.encoding=")).count() == 1,
                "launch command should contain exactly one file.encoding argument: " + command);
    }

    private static void verifySpawnedJamSeesExpectedFileEncoding(String expectedEncoding) throws Exception {
        verifySpawnedJamSeesExpectedFileEncoding(null, expectedEncoding);
    }

    private static void verifySpawnedJamSeesExpectedFileEncoding(Object settings, String expectedEncoding) throws Exception {
        Properties properties = readSpawnedProbeProperties(settings);
        String actualFileEncoding = properties.getProperty("file.encoding");
        String actualDefaultCharset = properties.getProperty("defaultCharset");
        String canonicalExpected = Charset.forName(expectedEncoding).name();
        check(expectedEncoding.equals(actualFileEncoding),
                "spawned JAM file.encoding should be " + expectedEncoding + " but was " + actualFileEncoding);
        check(canonicalExpected.equals(actualDefaultCharset),
                "spawned JAM default charset should be " + canonicalExpected + " but was " + actualDefaultCharset);
    }

    private static void verifyLauncherSettingsOverrideEncoding() throws Exception {
        String overrideEncoding = StandardCharsets.UTF_16LE.name();
        Object settings = newLauncherSettings(
                HostScale.DEFAULT_ID,
                MLDSynth.DEFAULT.id,
                OpenDoJaIdentity.defaultTerminalId(),
                OpenDoJaIdentity.defaultUserId(),
                LaunchConfig.FontType.BITMAP.id,
                "",
                overrideEncoding,
                "",
                OpenGlesRendererMode.SOFTWARE,
                false,
                false,
                false);
        Object selection = newGameLaunchSelection(Path.of("probe.jam"), Path.of("probe.jar"));
        List<String> command = buildLaunchCommand(selection, settings);
        String nativeAccessArgument = enableNativeAccessArgument();
        check(command.contains(nativeAccessArgument),
                "launch command should contain " + nativeAccessArgument + " with launcher override but was " + command);
        String expectedArgument = "-Dfile.encoding=" + overrideEncoding;
        check(command.contains(expectedArgument),
                "launch command should contain explicit launcher override " + expectedArgument + " but was " + command);
        check(command.stream().filter(arg -> arg.startsWith("-Dfile.encoding=")).count() == 1,
                "launch command should contain exactly one file.encoding argument with launcher override: " + command);
        verifySpawnedJamSeesExpectedFileEncoding(settings, overrideEncoding);
    }

    private static void verifyLauncherSettingsForwardFullscreenHostScale() throws Exception {
        Object settings = newLauncherSettings(
                HostScale.FULLSCREEN_ID,
                MLDSynth.DEFAULT.id,
                OpenDoJaIdentity.defaultTerminalId(),
                OpenDoJaIdentity.defaultUserId(),
                LaunchConfig.FontType.BITMAP.id,
                "",
                "",
                "",
                OpenGlesRendererMode.SOFTWARE,
                false,
                false,
                false);
        Object selection = newGameLaunchSelection(Path.of("probe.jam"), Path.of("probe.jar"));
        List<String> command = buildLaunchCommand(selection, settings);
        String expectedArgument = "-D" + OpenDoJaLaunchArgs.HOST_SCALE + "=" + HostScale.FULLSCREEN_ID;
        check(command.contains(expectedArgument),
                "launch command should forward fullscreen host scale as " + expectedArgument + " but was " + command);
    }

    private static void verifyLauncherSettingsForwardActiveKeybindProfile() throws Exception {
        HostKeybindProfile alternateProfile = HostKeybindProfile.defaults()
                .withBinding(HostControlAction.SELECT, 0,
                        HostInputBinding.controller("", ControllerBindingDescriptor.button("A")))
                .withoutBinding(HostControlAction.SELECT, 1);
        HostKeybindConfiguration keybindConfiguration = new HostKeybindConfiguration(
                List.of(HostKeybindProfile.defaults(), alternateProfile),
                List.of(HostKeybindConfiguration.DEFAULT_PROFILE_NAME, "Arcade"),
                1);
        Object settings = invokeNoArgStatic(launcherSettingsClass(), "defaults");
        settings = invoke(settings, "withKeybindConfiguration", new Class<?>[]{HostKeybindConfiguration.class}, keybindConfiguration);
        Object selection = newGameLaunchSelection(Path.of("probe.jam"), Path.of("probe.jar"));
        List<String> command = buildLaunchCommand(selection, settings);
        String expectedArgument = "-D" + OpenDoJaLaunchArgs.INPUT_BINDINGS + "=" + alternateProfile.serialize();
        check(command.contains(expectedArgument),
                "launch command should forward the active keybind profile as " + expectedArgument + " but was " + command);

        Properties properties = readSpawnedProbeProperties(settings);
        check(alternateProfile.serialize().equals(properties.getProperty("inputBindings")),
                "spawned JAM should see the active keybind profile");
    }

    private static void verifyLauncherSettingsForwardStandbyLaunchType() throws Exception {
        Object settings = invokeNoArgStatic(launcherSettingsClass(), "defaults");
        settings = invoke(settings, "withLaunchType", new Class<?>[]{String.class}, LaunchConfig.LaunchTypeOption.STANDBY.id);
        Object selection = newGameLaunchSelection(Path.of("probe.jam"), Path.of("probe.jar"));
        List<String> command = buildLaunchCommand(selection, settings);
        String expectedPropertyArgument = "-D" + OpenDoJaLaunchArgs.LAUNCH_TYPE + "=" + LaunchConfig.LaunchTypeOption.STANDBY.id;
        check(command.contains(expectedPropertyArgument),
                "launch command should forward standby launch type as " + expectedPropertyArgument + " but was " + command);

        Properties properties = readSpawnedProbeProperties(settings);
        check(Integer.toString(IApplication.LAUNCHED_AS_CONCIERGE).equals(properties.getProperty("launchType")),
                "spawned JAM should launch with standby/concierge launch type");
    }

    private static void verifySpawnedHardwareLaunchAvoidsNativeAccessWarning() throws Exception {
        Path root = Files.createTempDirectory("launcher-native-access");
        Object selection = newGameLaunchSelection(
                writeNativeAccessJam(root.resolve("NativeAccessProbe.jam")),
                currentArtifactPath());

        ProcessBuilder processBuilder = new ProcessBuilder(buildLaunchCommand(selection, defaultHardwareSettings()));
        processBuilder.redirectErrorStream(true);
        processBuilder.directory(root.toFile());
        Process process = processBuilder.start();
        if (!process.waitFor(20, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("spawned native-access probe timed out");
        }
        String output;
        try (var input = process.getInputStream()) {
            output = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        check(process.exitValue() == 0,
                "spawned native-access probe should exit cleanly but output was:\n" + output);
        check(output.contains("Native access probe app OK"),
                "spawned native-access probe should reach the hardware initialization path but output was:\n" + output);
        check(!output.contains("A restricted method in java.lang.System has been called"),
                "spawned native-access probe should not emit the native-access warning but output was:\n" + output);
    }

    private static Path writeJam(Path jam, Path output) throws Exception {
        Files.writeString(jam,
                "AppClass=" + ProbeApp.class.getName() + '\n'
                        + "AppName=LauncherProcessSupportProbe\n"
                        + OUTPUT_PARAMETER + "=" + output.toUri() + '\n',
                StandardCharsets.ISO_8859_1);
        return jam;
    }

    private static Path writeNativeAccessJam(Path jam) throws Exception {
        Files.writeString(jam,
                "AppClass=" + NativeAccessProbeApp.class.getName() + '\n'
                        + "AppName=LauncherNativeAccessProbe\n",
                StandardCharsets.ISO_8859_1);
        return jam;
    }

    private static Properties readSpawnedProbeProperties(Object settings) throws Exception {
        Path root = Files.createTempDirectory("launcher-process-support");
        Path output = root.resolve("encoding.properties");
        Object selection = newGameLaunchSelection(
                writeJam(root.resolve("EncodingProbe.jam"), output),
                currentArtifactPath());

        Process process = startInBackground(selection, settings);
        if (!process.waitFor(20, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("spawned JAM probe timed out");
        }
        check(process.exitValue() == 0, "spawned JAM probe should exit cleanly");
        check(Files.exists(output), "spawned JAM probe should write " + output);

        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(output, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private static Object defaultHardwareSettings() throws Exception {
        return newLauncherSettings(
                HostScale.DEFAULT_ID,
                MLDSynth.DEFAULT.id,
                OpenDoJaIdentity.defaultTerminalId(),
                OpenDoJaIdentity.defaultUserId(),
                LaunchConfig.FontType.BITMAP.id,
                "",
                "",
                "",
                OpenGlesRendererMode.HARDWARE,
                false,
                false,
                false);
    }

    private static Path currentArtifactPath() throws Exception {
        return Path.of(LauncherProcessSupportProbe.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .toAbsolutePath()
                .normalize();
    }

    private static Object newGameLaunchSelection(Path jamPath, Path gameJarPath) throws Exception {
        Constructor<?> constructor = gameLaunchSelectionClass().getDeclaredConstructor(Path.class, Path.class);
        constructor.setAccessible(true);
        return constructor.newInstance(jamPath, gameJarPath);
    }

    private static Object newLauncherSettings(String hostScale,
                                              String synthId,
                                              String terminalId,
                                              String userId,
                                              String fontType,
                                              String httpOverrideDomain,
                                              String fileEncodingOverride,
                                              String microeditionPlatformOverride,
                                              OpenGlesRendererMode openGlesRendererMode,
                                              boolean showOpenGlesFps,
                                              boolean disableBytecodeVerification,
                                              boolean disableOsDpiScaling) throws Exception {
        Constructor<?> constructor = launcherSettingsClass().getDeclaredConstructor(
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                OpenGlesRendererMode.class,
                boolean.class,
                boolean.class,
                boolean.class);
        constructor.setAccessible(true);
        return constructor.newInstance(hostScale, synthId, terminalId, userId, fontType, httpOverrideDomain,
                fileEncodingOverride, microeditionPlatformOverride, openGlesRendererMode, showOpenGlesFps,
                disableBytecodeVerification, disableOsDpiScaling);
    }

    @SuppressWarnings("unchecked")
    private static List<String> buildLaunchCommand(Object selection) throws Exception {
        return (List<String>) invoke(newLauncherProcessSupport(), "buildLaunchCommand",
                new Class<?>[]{gameLaunchSelectionClass()}, selection);
    }

    @SuppressWarnings("unchecked")
    private static List<String> buildLaunchCommand(Object selection, Object settings) throws Exception {
        return (List<String>) invoke(newLauncherProcessSupport(), "buildLaunchCommand",
                new Class<?>[]{gameLaunchSelectionClass(), launcherSettingsClass()}, selection, settings);
    }

    private static Process startInBackground(Object selection, Object settings) throws Exception {
        return (Process) invoke(newLauncherProcessSupport(), "startInBackground",
                new Class<?>[]{gameLaunchSelectionClass(), launcherSettingsClass()}, selection, settings);
    }

    private static String enableNativeAccessArgument() throws Exception {
        Field field = launcherProcessSupportClass().getDeclaredField("ENABLE_NATIVE_ACCESS_ARGUMENT");
        field.setAccessible(true);
        return (String) field.get(null);
    }

    private static Object newLauncherProcessSupport() throws Exception {
        Constructor<?> constructor = launcherProcessSupportClass().getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static Object invokeNoArgStatic(Class<?> type, String methodName) throws Exception {
        Method method = type.getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method.invoke(null);
    }

    private static Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static Class<?> gameLaunchSelectionClass() throws ClassNotFoundException {
        return Class.forName(GAME_LAUNCH_SELECTION_CLASS_NAME);
    }

    private static Class<?> launcherProcessSupportClass() throws ClassNotFoundException {
        return Class.forName(LAUNCHER_PROCESS_SUPPORT_CLASS_NAME);
    }

    private static Class<?> launcherSettingsClass() throws ClassNotFoundException {
        return Class.forName(LAUNCHER_SETTINGS_CLASS_NAME);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message + " (default charset=" + DoJaEncoding.defaultCharsetName() + ")");
        }
    }

    public static final class ProbeApp extends IApplication {
        @Override
        public void start() {
            String outputUri = getParameter(OUTPUT_PARAMETER);
            if (outputUri == null || outputUri.isBlank()) {
                throw new IllegalStateException("Missing " + OUTPUT_PARAMETER + " JAM parameter");
            }
            Properties properties = new Properties();
            properties.setProperty("file.encoding", System.getProperty("file.encoding", "<unset>"));
            properties.setProperty("defaultCharset", Charset.defaultCharset().name());
            properties.setProperty("inputBindings", System.getProperty(OpenDoJaLaunchArgs.INPUT_BINDINGS, "<unset>"));
            properties.setProperty("launchType", Integer.toString(getLaunchType()));
            try (var writer = Files.newBufferedWriter(Path.of(URI.create(outputUri)), StandardCharsets.UTF_8)) {
                properties.store(writer, null);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to record child encoding state", e);
            }
            terminate();
        }
    }

    public static final class NativeAccessProbeApp extends IApplication {
        @Override
        public void start() {
            Image.createImage(4, 4).getGraphics();
            System.out.println("Native access probe app OK");
            terminate();
        }
    }
}
