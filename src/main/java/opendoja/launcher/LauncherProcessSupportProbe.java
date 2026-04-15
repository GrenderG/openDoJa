package opendoja.launcher;

import com.nttdocomo.ui.IApplication;
import opendoja.host.DoJaEncoding;

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
        System.out.println("Launcher process support probe OK");
    }

    private static void verifyBuildLaunchCommandAddsExpectedFileEncoding(String expectedEncoding) throws Exception {
        GameLaunchSelection selection = new GameLaunchSelection(
                java.nio.file.Path.of("probe.jam"),
                java.nio.file.Path.of("probe.jar"));
        List<String> command = new LauncherProcessSupport().buildLaunchCommand(selection);
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
                1,
                opendoja.audio.mld.MLDSynth.DEFAULT.id,
                opendoja.host.OpenDoJaIdentity.defaultTerminalId(),
                opendoja.host.OpenDoJaIdentity.defaultUserId(),
                opendoja.host.LaunchConfig.FontType.BITMAP.id,
                "",
                overrideEncoding,
                "",
                opendoja.host.OpenGlesRendererMode.SOFTWARE,
                false,
                false,
                false);
        GameLaunchSelection selection = new GameLaunchSelection(
                java.nio.file.Path.of("probe.jam"),
                java.nio.file.Path.of("probe.jar"));
        List<String> command = new LauncherProcessSupport().buildLaunchCommand(selection, settings);
        String expectedArgument = "-Dfile.encoding=" + overrideEncoding;
        check(command.contains(expectedArgument),
                "launch command should contain explicit launcher override " + expectedArgument + " but was " + command);
        check(command.stream().filter(arg -> arg.startsWith("-Dfile.encoding=")).count() == 1,
                "launch command should contain exactly one file.encoding argument with launcher override: " + command);
        verifySpawnedJamSeesExpectedFileEncoding(settings, overrideEncoding);
    }

    private static Path writeJam(Path jam, Path output) throws Exception {
        Files.writeString(jam,
                "AppClass=" + ProbeApp.class.getName() + '\n'
                        + "AppName=LauncherProcessSupportProbe\n"
                        + OUTPUT_PARAMETER + "=" + output.toUri() + '\n',
                StandardCharsets.ISO_8859_1);
        return jam;
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
            try (var writer = Files.newBufferedWriter(Path.of(URI.create(outputUri)), StandardCharsets.UTF_8)) {
                properties.store(writer, null);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to record child encoding state", e);
            }
            terminate();
        }
    }
}
