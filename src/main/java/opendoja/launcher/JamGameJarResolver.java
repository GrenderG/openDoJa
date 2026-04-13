package opendoja.launcher;

import opendoja.host.JamMetadataResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

final class JamGameJarResolver {
    GameLaunchSelection resolve(Path jamPath) throws IOException {
        Path normalizedJam = jamPath.toAbsolutePath().normalize();
        validateJamPath(normalizedJam);
        Path directory = normalizedJam.getParent();
        if (directory == null) {
            throw new IOException("JAM must be stored in a directory: " + normalizedJam);
        }

        String baseName = stripExtension(normalizedJam.getFileName().toString());
        Path sameBaseJar = directory.resolve(baseName + ".jar");
        if (Files.isRegularFile(sameBaseJar)) {
            return new GameLaunchSelection(normalizedJam, sameBaseJar.toAbsolutePath().normalize());
        }

        Path packageUrlJar = jarFromPackageUrl(normalizedJam, directory);
        if (packageUrlJar != null) {
            return new GameLaunchSelection(normalizedJam, packageUrlJar.toAbsolutePath().normalize());
        }

        List<Path> siblingJars = listSiblingJars(directory);
        if (siblingJars.size() == 1) {
            return new GameLaunchSelection(normalizedJam, siblingJars.get(0).toAbsolutePath().normalize());
        }

        throw new IOException("Could not find a matching .jar next to " + normalizedJam.getFileName());
    }

    private void validateJamPath(Path jamPath) throws IOException {
        if (!jamPath.getFileName().toString().toLowerCase().endsWith(".jam")) {
            throw new IOException("Selected file is not a .jam: " + jamPath.getFileName());
        }
        if (!Files.isRegularFile(jamPath)) {
            throw new IOException("JAM file does not exist: " + jamPath);
        }
    }

    private Path jarFromPackageUrl(Path jamPath, Path directory) throws IOException {
        Properties properties = JamMetadataResolver.loadJamProperties(jamPath);
        String packageUrl = properties.getProperty("PackageURL");
        if (packageUrl == null || packageUrl.isBlank()) {
            return null;
        }
        String trimmed = packageUrl.trim();
        int slash = Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'));
        String fileName = slash >= 0 ? trimmed.substring(slash + 1) : trimmed;
        if (!fileName.toLowerCase().endsWith(".jar")) {
            return null;
        }
        Path candidate = directory.resolve(fileName).normalize();
        return Files.isRegularFile(candidate) ? candidate : null;
    }

    private List<Path> listSiblingJars(Path directory) throws IOException {
        List<Path> siblingJars = new ArrayList<>();
        try (Stream<Path> stream = Files.list(directory)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".jar"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase()))
                    .forEach(siblingJars::add);
        }
        return siblingJars;
    }

    private String stripExtension(String name) {
        int lastDot = name.lastIndexOf('.');
        return lastDot >= 0 ? name.substring(0, lastDot) : name;
    }
}
