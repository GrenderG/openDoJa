package opendoja.probes;

import com.nttdocomo.ui.IApplication;
import opendoja.host.ApplicationProfile;
import opendoja.host.DoJaProfile;
import opendoja.host.JamLauncher;

import java.nio.file.Files;
import java.util.Map;

/** Checks JAM family selection independently of DoJa version inference. */
public final class StarProfileProbe {
    public static void main(String[] args) throws Exception {
        var root = Files.createTempDirectory("star-profile");
        String common = "AppClass=" + ProbeApp.class.getName() + "\nAppSize=890014\nDrawArea=240x240\n";
        for (String field : new String[]{"AppType = FullApp", "  aPpTyPe = FullApp", "AppType="}) {
            var jam = root.resolve("star.jam");
            Files.writeString(jam, common + field + "\nProfileVer=Star-1.0\nTargetDevice=N905i\n");
            var config = JamLauncher.buildLaunchConfig(jam, false);
            check(config.applicationProfile() == ApplicationProfile.STAR, "AppType selects STAR: " + field);
            check(!DoJaProfile.fromParametersOrDocumentedDeviceIdentity(config.parameters()).isKnown(),
                    "STAR must not inherit a DoJa version from device hints");
        }
        var starWithoutVersion = root.resolve("star-without-version.jam");
        Files.writeString(starWithoutVersion, common + "AppType=FullApp\n");
        var starConfig = JamLauncher.buildLaunchConfig(starWithoutVersion, false);
        check(!starConfig.parameters().containsKey("ProfileVer"), "STAR must skip DoJa size inference");
        var doja = root.resolve("doja.jam");
        Files.writeString(doja, common);
        var config = JamLauncher.buildLaunchConfig(doja, false);
        check(config.applicationProfile() == ApplicationProfile.DOJA, "No AppType selects DoJa");
        check(DoJaProfile.fromParameters(config.parameters()).isAtLeast(5, 0), "Keep DoJa size inference");
        check(!DoJaProfile.parse("Star-1.0").isKnown(), "DoJa parser must reject STAR versions");
        check(ApplicationProfile.fromParameters(Map.of("apptype", "")) == ApplicationProfile.STAR,
                "Family selection depends on field presence");
        System.out.println("star-profile-probe-ok");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static final class ProbeApp extends IApplication {
        @Override public void start() { }
    }
}
