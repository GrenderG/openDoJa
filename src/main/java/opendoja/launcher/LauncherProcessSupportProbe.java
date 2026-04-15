package opendoja.launcher;

import com.nttdocomo.ui.IApplication;
import com.nttdocomo.ui.Image;
import opendoja.audio.mld.MLDSynth;
import opendoja.host.HostControlAction;
import opendoja.host.HostInputBinding;
import opendoja.host.HostKeybindConfiguration;
import opendoja.host.HostKeybindProfile;
import opendoja.host.DoJaEncoding;
import opendoja.host.HostScale;
import opendoja.host.LaunchConfig;
import opendoja.host.OpenDoJaIdentity;
import opendoja.host.OpenDoJaLaunchArgs;
import opendoja.host.OpenGlesRendererMode;

import java.awt.event.KeyEvent;
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
        verifyLauncherSettingsForwardActiveKeybindProfile();
        verifySpawnedHardwareLaunchAvoidsNativeAccessWarning();
        System.out.println("Launcher process support probe OK");
    }

    private static void verifyBuildLaunchCommandAddsExpectedFileEncoding(String expectedEncoding) throws Exception {
        GameLaunchSelection selection = new GameLaunchSelection(
                java.nio.file.Path.of("probe.jam"),
                java.nio.file.Path.of("probe.jar"));
        List<String> command = new LauncherProcessSupport().buildLaunchCommand(selection);
        check(command.contains(LauncherProcessSupport.ENABLE_NATIVE_ACCESS_ARGUMENT),
                "launch command should contain " + LauncherProcessSupport.ENABLE_NATIVE_ACCESS_ARGUMENT + " but was " + command);
        String expectedArgument = "-Dfile.encoding=" + expectedEncoding;
        check(command.contains(expectedArgument),
                "launch command should contain " + expectedArgument + " but was " + command);
        check(command.stream().filter(arg -> arg.startsWith("-Dfile.encoding=")).count() == 1,
                "launch command should contain exactly one file.encoding argument: " + command);
    }

    private static void verifySpawnedJamSeesExpectedFileEncoding(String expectedEncoding) throws Exception {
        verifySpawnedJamSeesExpectedFileEncoding(null, expectedEncoding);
    }

    private static void verifySpawnedJamSeesExpectedFileEncoding(LauncherSettings settings, String expectedEncoding) throws Exception {
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
        LauncherSettings settings = new LauncherSettings(
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
        GameLaunchSelection selection = new GameLaunchSelection(
                java.nio.file.Path.of("probe.jam"),
                java.nio.file.Path.of("probe.jar"));
        List<String> command = new LauncherProcessSupport().buildLaunchCommand(selection, settings);
        check(command.contains(LauncherProcessSupport.ENABLE_NATIVE_ACCESS_ARGUMENT),
                "launch command should contain " + LauncherProcessSupport.ENABLE_NATIVE_ACCESS_ARGUMENT + " with launcher override but was " + command);
        String expectedArgument = "-Dfile.encoding=" + overrideEncoding;
        check(command.contains(expectedArgument),
                "launch command should contain explicit launcher override " + expectedArgument + " but was " + command);
        check(command.stream().filter(arg -> arg.startsWith("-Dfile.encoding=")).count() == 1,
                "launch command should contain exactly one file.encoding argument with launcher override: " + command);
        verifySpawnedJamSeesExpectedFileEncoding(settings, overrideEncoding);
    }

    private static void verifyLauncherSettingsForwardFullscreenHostScale() throws Exception {
        LauncherSettings settings = new LauncherSettings(
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
        GameLaunchSelection selection = new GameLaunchSelection(
                java.nio.file.Path.of("probe.jam"),
                java.nio.file.Path.of("probe.jar"));
        List<String> command = new LauncherProcessSupport().buildLaunchCommand(selection, settings);
        String expectedArgument = "-D" + OpenDoJaLaunchArgs.HOST_SCALE + "=" + HostScale.FULLSCREEN_ID;
        check(command.contains(expectedArgument),
                "launch command should forward fullscreen host scale as " + expectedArgument + " but was " + command);
    }

    private static void verifyLauncherSettingsForwardActiveKeybindProfile() throws Exception {
        HostKeybindProfile alternateProfile = HostKeybindProfile.defaults()
                .withBinding(HostControlAction.SELECT, 0, HostInputBinding.keyboard(KeyEvent.VK_Z))
                .withoutBinding(HostControlAction.SELECT, 1);
        HostKeybindConfiguration keybindConfiguration = new HostKeybindConfiguration(
                List.of(HostKeybindProfile.defaults(), alternateProfile),
                List.of(HostKeybindConfiguration.DEFAULT_PROFILE_NAME, "Arcade"),
                1);
        LauncherSettings settings = LauncherSettings.defaults().withKeybindConfiguration(keybindConfiguration);
        GameLaunchSelection selection = new GameLaunchSelection(
                java.nio.file.Path.of("probe.jam"),
                java.nio.file.Path.of("probe.jar"));
        List<String> command = new LauncherProcessSupport().buildLaunchCommand(selection, settings);
        String expectedArgument = "-D" + OpenDoJaLaunchArgs.INPUT_BINDINGS + "=" + alternateProfile.serialize();
        check(command.contains(expectedArgument),
                "launch command should forward the active keybind profile as " + expectedArgument + " but was " + command);

        Properties properties = readSpawnedProbeProperties(settings);
        check(alternateProfile.serialize().equals(properties.getProperty("inputBindings")),
                "spawned JAM should see the active keybind profile");
    }

    private static void verifySpawnedHardwareLaunchAvoidsNativeAccessWarning() throws Exception {
        Path root = Files.createTempDirectory("launcher-native-access");
        GameLaunchSelection selection = new GameLaunchSelection(
                writeNativeAccessJam(root.resolve("NativeAccessProbe.jam")),
                currentArtifactPath());

        ProcessBuilder processBuilder = new ProcessBuilder(
                new LauncherProcessSupport().buildLaunchCommand(selection, defaultHardwareSettings()));
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

    private static Properties readSpawnedProbeProperties(LauncherSettings settings) throws Exception {
        Path root = Files.createTempDirectory("launcher-process-support");
        Path output = root.resolve("encoding.properties");
        GameLaunchSelection selection = new GameLaunchSelection(
                writeJam(root.resolve("EncodingProbe.jam"), output),
                currentArtifactPath());

        Process process = new LauncherProcessSupport().startInBackground(selection, settings);
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

    private static LauncherSettings defaultHardwareSettings() {
        return new LauncherSettings(
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
