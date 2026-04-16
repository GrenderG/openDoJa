package opendoja.probes;

import com.nttdocomo.ui.IApplication;
import opendoja.host.JamLauncher;
import opendoja.host.LaunchConfig;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class JamDrawAreaInferenceProbe {
    private JamDrawAreaInferenceProbe() {
    }

    public static void main(String[] args) throws Exception {
        verifyDocumentedTargetDeviceInfersDrawArea();
        verifyFolderHintTargetDeviceInfersDrawArea();
        verifyExplicitDrawAreaStillWins();
        verifyUndocumentedTargetDeviceKeepsDefaultViewport();

        System.out.println("Jam draw area inference probe OK");
    }

    private static void verifyDocumentedTargetDeviceInfersDrawArea() throws Exception {
        Path root = Files.createTempDirectory("jam-draw-area-target-device");
        Path jam = writeJam(root.resolve("Documented.jam"),
                "TargetDevice=FOMA N2051\n");

        LaunchConfig config = JamLauncher.buildLaunchConfig(jam, false);
        check(config.width() == 176 && config.height() == 198,
                "documented TargetDevice should infer draw area from the archived NTT DoCoMo handset table");
    }

    private static void verifyFolderHintTargetDeviceInfersDrawArea() throws Exception {
        Path root = Files.createTempDirectory("jam-draw-area-folder-target");
        Path jam = writeJam(Files.createDirectories(root.resolve("N2051 Version")).resolve("FolderHint.jam"), "");

        LaunchConfig config = JamLauncher.buildLaunchConfig(jam, false);
        check("N2051".equals(config.parameters().get("TargetDevice")),
                "folder-name fallback should still infer TargetDevice when the JAM omits it");
        check(config.width() == 176 && config.height() == 198,
                "folder-name TargetDevice inference should feed documented draw-area fallback");
    }

    private static void verifyExplicitDrawAreaStillWins() throws Exception {
        Path root = Files.createTempDirectory("jam-draw-area-explicit");
        Path jam = writeJam(root.resolve("ExplicitWins.jam"),
                "TargetDevice=N505i\n"
                        + "DrawArea=132x144\n");

        LaunchConfig config = JamLauncher.buildLaunchConfig(jam, false);
        check(config.width() == 132 && config.height() == 144,
                "explicit DrawArea should win over documented TargetDevice inference");
    }

    private static void verifyUndocumentedTargetDeviceKeepsDefaultViewport() throws Exception {
        Path root = Files.createTempDirectory("jam-draw-area-default");
        Path jam = writeJam(root.resolve("Fallback.jam"),
                "TargetDevice=Unknown9000\n");

        LaunchConfig config = JamLauncher.buildLaunchConfig(jam, false);
        check(config.width() == LaunchConfig.DEFAULT_VIEWPORT_WIDTH
                        && config.height() == LaunchConfig.DEFAULT_VIEWPORT_HEIGHT,
                "undocumented TargetDevice should keep the existing 240x240 default");
    }

    private static Path writeJam(Path jam, String extraProperties) throws Exception {
        Files.writeString(jam,
                "AppClass=" + ProbeApp.class.getName() + '\n'
                        + "AppName=Probe\n"
                        + extraProperties,
                StandardCharsets.ISO_8859_1);
        return jam;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    public static final class ProbeApp extends IApplication {
        @Override
        public void start() {
        }
    }
}
