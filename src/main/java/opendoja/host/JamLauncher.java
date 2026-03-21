package opendoja.host;

import com.nttdocomo.ui.IApplication;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class JamLauncher {
    private JamLauncher() {
    }

    public static IApplication launch(Path jamPath) throws IOException, ClassNotFoundException {
        return launch(jamPath, false);
    }

    public static IApplication launch(Path jamPath, boolean exitOnShutdown) throws IOException, ClassNotFoundException {
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(jamPath)) {
            properties.load(in);
        }
        String appClassName = properties.getProperty("AppClass");
        if (appClassName == null || appClassName.isBlank()) {
            throw new IllegalArgumentException("JAM/ADF missing AppClass: " + jamPath);
        }
        Class<?> rawClass = Class.forName(appClassName.trim());
        if (!IApplication.class.isAssignableFrom(rawClass)) {
            throw new IllegalArgumentException("AppClass does not extend IApplication: " + appClassName);
        }
        @SuppressWarnings("unchecked")
        Class<? extends IApplication> applicationClass = (Class<? extends IApplication>) rawClass;
        Path scratchpadRoot = defaultScratchpadRoot(jamPath);
        LaunchConfig.Builder builder = LaunchConfig.builder(applicationClass)
                .title(properties.getProperty("AppName", applicationClass.getSimpleName()))
                .sourceUrl(resolvePackageUrl(jamPath, properties.getProperty("PackageURL")))
                .scratchpadRoot(scratchpadRoot)
                .exitOnShutdown(exitOnShutdown);
        String drawArea = properties.getProperty("DrawArea");
        if (drawArea != null) {
            String[] parts = drawArea.toLowerCase().split("x");
            if (parts.length == 2) {
                try {
                    builder.viewport(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        String appParam = properties.getProperty("AppParam");
        if (appParam != null && !appParam.isBlank()) {
            builder.args(appParam.trim().split("\\s+"));
        }
        for (String name : properties.stringPropertyNames()) {
            builder.parameter(name, properties.getProperty(name));
        }
        seedScratchpadIfAvailable(jamPath, scratchpadRoot, parseScratchpadSizes(properties.getProperty("SPsize")));
        return DesktopLauncher.launch(builder.build());
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: JamLauncher <path-to-jam>");
        }
        Path jamPath = Path.of(args[0]);
        LaunchCompatibility.reexecJamLauncherIfNeeded(jamPath);
        launch(jamPath, true);
        DoJaRuntime runtime = DoJaRuntime.current();
        if (runtime != null) {
            runtime.awaitShutdown();
        }
        System.exit(0);
    }

    private static String resolvePackageUrl(Path jamPath, String packageUrl) {
        if (packageUrl == null || packageUrl.isBlank()) {
            return jamPath.toUri().toString();
        }
        String trimmed = packageUrl.trim();
        if (trimmed.contains("://")) {
            return trimmed;
        }
        Path base = jamPath.getParent();
        Path resolved = (base == null ? Path.of(trimmed) : base.resolve(trimmed)).normalize();
        return resolved.toUri().toString();
    }

    private static Path defaultScratchpadRoot(Path jamPath) {
        String baseName = stripExtension(jamPath.getFileName().toString());
        String hash = Integer.toHexString(jamPath.toAbsolutePath().normalize().toString().hashCode());
        return Path.of(".opendoja", sanitize(baseName) + "-" + hash);
    }

    private static void seedScratchpadIfAvailable(Path jamPath, Path scratchpadRoot, int[] scratchpadSizes) throws IOException {
        Path seedFile = locateScratchpadSeed(jamPath);
        if (seedFile == null) {
            return;
        }
        ScratchpadSeed.seedIfNeeded(scratchpadRoot, seedFile, scratchpadSizes);
    }

    private static Path locateScratchpadSeed(Path jamPath) {
        String baseName = stripExtension(jamPath.getFileName().toString());
        Path[] candidates = new Path[]{
                jamPath.resolveSibling(baseName + ".sp"),
                jamPath.getParent() == null ? null : jamPath.getParent().resolve("sp").resolve(baseName + ".sp"),
                jamPath.getParent() == null ? null : jamPath.getParent().resolveSibling("sp").resolve(baseName + ".sp"),
                jamPath.getParent() == null || jamPath.getParent().getParent() == null ? null
                        : jamPath.getParent().getParent().resolve("sp").resolve(baseName + ".sp")
        };
        for (Path candidate : candidates) {
            if (candidate != null && Files.exists(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static int[] parseScratchpadSizes(String raw) {
        if (raw == null || raw.isBlank()) {
            return new int[0];
        }
        String[] parts = raw.split(",");
        int[] result = new int[parts.length];
        int count = 0;
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            result[count++] = Integer.parseInt(trimmed);
        }
        if (count == result.length) {
            return result;
        }
        int[] compact = new int[count];
        System.arraycopy(result, 0, compact, 0, count);
        return compact;
    }

    private static String stripExtension(String name) {
        int lastDot = name.lastIndexOf('.');
        return lastDot < 0 ? name : name.substring(0, lastDot);
    }

    private static String sanitize(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]+", "_");
    }
}
