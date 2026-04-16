package opendoja.probes;

import com.nttdocomo.ui.IApplication;
import opendoja.host.DoJaProfile;
import opendoja.host.JamLauncher;
import opendoja.host.LaunchConfig;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class JamProfileResolutionInferenceProbe {
    private JamProfileResolutionInferenceProbe() {
    }

    public static void main(String[] args) throws Exception {
        verifyUniqueLegacyResolutionInfersDoJa20();
        verifyPackageUrlIdentityBeatsResolutionInference();
        verifyAmbiguousLegacyResolutionPrefersNewestBeforeFolderFallback();
        verifyFolderHintLegacyTargetFallsBackToDocumentedDrawAreaProfile();
        verifyDoJa30SizedResolutionIsIgnored();

        System.out.println("Jam profile resolution inference probe OK");
    }

    private static void verifyUniqueLegacyResolutionInfersDoJa20() throws Exception {
        Path root = Files.createTempDirectory("jam-profile-resolution-20");
        Path jam = writeJam(root.resolve("Unique20.jam"), "132x144");

        LaunchConfig config = JamLauncher.buildLaunchConfig(jam, false);
        check("DoJa-2.0".equals(config.parameters().get("ProfileVer")),
                "132x144 should infer DoJa-2.0 from the archived NTT DoCoMo handset table");
        check("DoJa-2.0".equals(DoJaProfile.fromParametersOrDocumentedDeviceIdentity(config.parameters()).toString()),
                "132x144 should resolve as DoJa-2.0");
    }

    private static void verifyAmbiguousLegacyResolutionPrefersNewestBeforeFolderFallback() throws Exception {
        Path root = Files.createTempDirectory("jam-profile-resolution-overlap");
        Path bin = Files.createDirectories(root.resolve("N503iS_Version").resolve("bin"));
        Path jam = writeJam(bin.resolve("Overlap.jam"), "176x182");

        LaunchConfig config = JamLauncher.buildLaunchConfig(jam, false);
        check("N503iS".equals(config.parameters().get("TargetDevice")),
                "folder-name fallback should still infer TargetDevice");
        check("DoJa-2.2".equals(config.parameters().get("ProfileVer")),
                "176x182 should prefer the newest matching legacy profile");
        check("DoJa-2.2".equals(DoJaProfile.fromParametersOrDocumentedDeviceIdentity(config.parameters()).toString()),
                "resolution-based ProfileVer should win before folder-name profile inference");
    }

    private static void verifyPackageUrlIdentityBeatsResolutionInference() throws Exception {
        Path root = Files.createTempDirectory("jam-profile-resolution-package");
        Path jam = writeJam(root.resolve("PackageWins.jam"),
                "176x182",
                "PackageURL=http://example.test/N503iS/game.jar\n");

        LaunchConfig config = JamLauncher.buildLaunchConfig(jam, false);
        check(config.parameters().get("ProfileVer") == null,
                "resolution inference should not inject ProfileVer when PackageURL already exposes device identity");
        check("DoJa-1.0".equals(DoJaProfile.fromParametersOrDocumentedDeviceIdentity(config.parameters()).toString()),
                "PackageURL device identity should resolve the profile before folder-name fallback");
    }

    private static void verifyFolderHintLegacyTargetFallsBackToDocumentedDrawAreaProfile() throws Exception {
        Path root = Files.createTempDirectory("jam-profile-resolution-folder");
        Path jam = writeJam(Files.createDirectories(root.resolve("N2051 Version")).resolve("FolderHint.jam"),
                "");

        LaunchConfig config = JamLauncher.buildLaunchConfig(jam, false);
        check("N2051".equals(config.parameters().get("TargetDevice")),
                "folder-name fallback should infer the legacy handset identity");
        check("DoJa-2.2".equals(config.parameters().get("ProfileVer")),
                "legacy handset hints without explicit ProfileVer should fall back through the documented draw area");
        check("DoJa-2.2".equals(DoJaProfile.fromParametersOrDocumentedDeviceIdentity(config.parameters()).toString()),
                "documented draw-area fallback should make the effective runtime profile concrete");
    }

    private static void verifyDoJa30SizedResolutionIsIgnored() throws Exception {
        Path root = Files.createTempDirectory("jam-profile-resolution-modern");
        Path bin = Files.createDirectories(root.resolve("N505i_Version").resolve("bin"));
        Path jam = writeJam(bin.resolve("Modern.jam"), "240x240");

        LaunchConfig config = JamLauncher.buildLaunchConfig(jam, false);
        check(config.parameters().get("ProfileVer") == null,
                "240x240 should not infer ProfileVer from resolution because 3.0+ rows are ignored");
        check("N505i".equals(config.parameters().get("TargetDevice")),
                "folder-name TargetDevice inference should remain available after resolution inference");
        check("DoJa-3.0".equals(DoJaProfile.fromParametersOrDocumentedDeviceIdentity(config.parameters()).toString()),
                "240x240 should still resolve through the existing device-identity fallback");
    }

    private static Path writeJam(Path jam, String drawArea) throws Exception {
        return writeJam(jam, drawArea, "");
    }

    private static Path writeJam(Path jam, String drawArea, String extraProperties) throws Exception {
        Files.writeString(jam,
                "AppClass=" + ProbeApp.class.getName() + '\n'
                        + "AppName=Probe\n"
                        + "DrawArea=" + drawArea + '\n'
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
