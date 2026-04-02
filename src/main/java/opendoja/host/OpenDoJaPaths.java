package opendoja.host;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

public final class OpenDoJaPaths {
    private OpenDoJaPaths() {
    }

    public static Path hostDataRoot() {
        return artifactDirectory().resolve(".opendoja").normalize();
    }

    public static Path artifactDirectory() {
        try {
            URL location = OpenDoJaPaths.class.getProtectionDomain().getCodeSource().getLocation();
            if (location != null) {
                Path path = Path.of(location.toURI()).toAbsolutePath().normalize();
                if (Files.isRegularFile(path)) {
                    Path parent = path.getParent();
                    return parent == null ? path : parent;
                }
                if (Files.isDirectory(path)) {
                    return path;
                }
            }
        } catch (URISyntaxException | IllegalArgumentException | SecurityException ignored) {
        }
        return Path.of("").toAbsolutePath().normalize();
    }
}
