package opendoja.probes;

import com.nttdocomo.ui.IApplication;
import opendoja.host.JamLauncher;
import opendoja.host.LaunchConfig;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class JamSourceUrlProbe {
    private JamSourceUrlProbe() {
    }

    public static void main(String[] args) throws Exception {
        verifyRelativeJarPackageUsesContainingDirectory();
        verifyAbsoluteJarPackageUsesContainingDirectory();
        verifyAbsoluteDownloadScriptUsesContainingDirectory();
        verifyAbsoluteDirectoryPackageStaysDirectory();

        System.out.println("Jam source URL probe OK");
    }

    private static void verifyRelativeJarPackageUsesContainingDirectory() throws Exception {
        Path root = Files.createTempDirectory("jam-source-relative");
        Files.createDirectories(root.resolve("bin"));
        Path jam = writeJam(root.resolve("Relative.jam"), "PackageURL=bin/Game.jar\n");

        LaunchConfig config = JamLauncher.buildLaunchConfig(jam, false);
        check(root.resolve("bin").toUri().toString().equals(config.sourceUrl()),
                "relative jar PackageURL should normalize to its containing directory");
    }

    private static void verifyAbsoluteJarPackageUsesContainingDirectory() throws Exception {
        Path jam = writeStandaloneJam("AbsoluteJar.jam",
                "PackageURL=http://example.test/games/Game.jar\n");

        LaunchConfig config = JamLauncher.buildLaunchConfig(jam, false);
        check("http://example.test/games/".equals(config.sourceUrl()),
                "absolute jar PackageURL should normalize to its containing directory");
    }

    private static void verifyAbsoluteDownloadScriptUsesContainingDirectory() throws Exception {
        Path jam = writeStandaloneJam("AbsoluteScript.jam",
                "PackageURL=http://sdg.gs.keitaiarchive.org/jar.php?uid=LQNBFCWVHBQF\n");

        LaunchConfig config = JamLauncher.buildLaunchConfig(jam, false);
        check("http://sdg.gs.keitaiarchive.org/".equals(config.sourceUrl()),
                "download-script PackageURL should normalize to the parent directory for getSourceURL()");
    }

    private static void verifyAbsoluteDirectoryPackageStaysDirectory() throws Exception {
        Path jam = writeStandaloneJam("AbsoluteDirectory.jam",
                "PackageURL=http://example.test/games/\n");

        LaunchConfig config = JamLauncher.buildLaunchConfig(jam, false);
        check("http://example.test/games/".equals(config.sourceUrl()),
                "directory PackageURL should stay unchanged");
    }

    private static Path writeStandaloneJam(String fileName, String extraProperties) throws Exception {
        Path root = Files.createTempDirectory("jam-source-standalone");
        return writeJam(root.resolve(fileName), extraProperties);
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
