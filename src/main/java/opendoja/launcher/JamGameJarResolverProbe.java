package opendoja.launcher;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class JamGameJarResolverProbe {
    private JamGameJarResolverProbe() {
    }

    public static void main(String[] args) throws Exception {
        verifyPackageUrlLookupIsCaseInsensitive();

        System.out.println("Jam game jar resolver probe OK");
    }

    private static void verifyPackageUrlLookupIsCaseInsensitive() throws Exception {
        Path root = Files.createTempDirectory("jam-jar-resolver");
        Path jam = root.resolve("Select.jam");
        Path expectedJar = root.resolve("Wanted.jar");
        Files.write(expectedJar, new byte[0]);
        Files.write(root.resolve("Other.jar"), new byte[0]);
        Files.writeString(jam, "packageurl=Wanted.jar\n", StandardCharsets.ISO_8859_1);

        GameLaunchSelection selection = new JamGameJarResolver().resolve(jam);
        check(expectedJar.toAbsolutePath().normalize().equals(selection.gameJarPath()),
                "lowercase PackageURL should select the referenced jar");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
