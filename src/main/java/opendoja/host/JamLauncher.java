package opendoja.host;

import com.nttdocomo.ui.IApplication;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public final class JamLauncher {
    private JamLauncher() {
    }

    public static IApplication launch(Path jamPath) throws IOException, ClassNotFoundException {
        return launch(jamPath, false);
    }

    public static IApplication launch(Path jamPath, boolean exitOnShutdown) throws IOException, ClassNotFoundException {
        return DesktopLauncher.launch(buildLaunchConfig(jamPath, exitOnShutdown));
    }

    public static LaunchConfig buildLaunchConfig(Path jamPath, boolean exitOnShutdown) throws IOException, ClassNotFoundException {
        Properties properties = JamMetadataResolver.loadJamProperties(jamPath);
        return buildLaunchConfig(jamPath, properties, exitOnShutdown);
    }

    private static LaunchConfig buildLaunchConfig(Path jamPath, Properties properties, boolean exitOnShutdown)
            throws IOException, ClassNotFoundException {
        String appClassName = properties.getProperty("AppClass");
        if (appClassName == null || appClassName.isBlank()) {
            throw new IllegalArgumentException("JAM/ADF missing AppClass: " + jamPath);
        }
        Class<?> rawClass = loadApplicationClass(appClassName.trim());
        if (!IApplication.class.isAssignableFrom(rawClass)) {
            throw new IllegalArgumentException("AppClass does not extend IApplication: " + appClassName);
        }
        @SuppressWarnings("unchecked")
        Class<? extends IApplication> applicationClass = (Class<? extends IApplication>) rawClass;
        int[] scratchpadSizes = parseScratchpadSizes(properties.getProperty("SPsize"));
        LaunchConfig.Builder builder = LaunchConfig.builder(applicationClass)
                .title(properties.getProperty("AppName", applicationClass.getSimpleName()))
                .sourceUrl(resolvePackageUrl(jamPath, properties.getProperty("PackageURL")))
                .scratchpadSizes(scratchpadSizes)
                .iAppliType(LaunchConfig.IAppliType.fromJamProperties(properties))
                .exitOnShutdown(exitOnShutdown);
        ResolvedScratchpad scratchpad = null;
        if (scratchpadSizes.length > 0) {
            scratchpad = resolveScratchpad(jamPath);
            builder.scratchpadPackedFile(scratchpad.path());
        } else {
            builder.scratchpadRoot(null)
                    .scratchpadPackedFile(null);
        }
        Map<String, String> effectiveParameters = JamMetadataResolver.resolveEffectiveParameters(jamPath, properties);
        applyOptionalDrawArea(builder, properties.getProperty("DrawArea"), effectiveParameters.get("TargetDevice"));
        String appParam = properties.getProperty("AppParam");
        if (appParam != null && !appParam.isBlank()) {
            builder.args(appParam.trim().split("\\s+"));
        }
        for (Map.Entry<String, String> entry : effectiveParameters.entrySet()) {
            builder.parameter(entry.getKey(), entry.getValue());
        }
        if (scratchpad != null && !scratchpad.found()) {
            ResolvedScratchpad missingScratchpad = scratchpad;
            OpenDoJaLog.warn(JamLauncher.class,
                    () -> "No .sp file found for " + jamPath + " next to the JAM or in ../sp; "
                            + "continuing and using " + missingScratchpad.path() + " if the game creates one at runtime");
        }
        return builder.build();
    }

    public static void main(String[] args) throws Exception {
        List<String> effectiveArgs = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if ("--phone-model".equals(args[i])) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Usage: JamLauncher [--phone-model <model>] <path-to-jam>");
                }
                OpenDoJaLaunchArgs.set(OpenDoJaLaunchArgs.MICROEDITION_PLATFORM_OVERRIDE, args[++i]);
                continue;
            }
            effectiveArgs.add(args[i]);
        }
        if (effectiveArgs.size() != 1) {
            throw new IllegalArgumentException("Usage: JamLauncher [--phone-model <model>] <path-to-jam>");
        }
        Path jamPath = Path.of(effectiveArgs.get(0));
        LaunchCompatibility.reexecJamLauncherIfNeeded(jamPath);
        try {
            launch(jamPath, true);
        } catch (VerifyError error) {
            // A few handset-era jars contain bytecode that modern HotSpot rejects up front even
            // though the same title otherwise runs once verification is disabled. Retry once from
            // the top launch boundary so the default path stays strict for all normal titles.
            if (!LaunchCompatibility.reexecJamLauncherOnVerifyError(jamPath)) {
                throw error;
            }
            return;
        }
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
            return normalizePackageUri(URI.create(trimmed)).toString();
        }
        String relativePath = packagePathPart(trimmed);
        if (relativePath.isEmpty()) {
            return jamPath.toUri().toString();
        }
        Path base = jamPath.getParent();
        Path resolved = (base == null ? Path.of(relativePath) : base.resolve(relativePath)).normalize();
        if (isJarName(lastPathSegment(relativePath))) {
            Path absoluteResolved = resolved.toAbsolutePath().normalize();
            Path parent = absoluteResolved.getParent();
            return toDirectoryUri(parent == null ? absoluteResolved : parent);
        }
        return resolved.toUri().toString();
    }

    private static URI normalizePackageUri(URI packageUri) {
        if (!packageUri.isAbsolute()) {
            return packageUri;
        }
        String path = packageUri.getPath();
        if (path != null && path.endsWith("/")) {
            return stripQueryAndFragment(packageUri);
        }
        // DoJa titles commonly treat getSourceURL() as a base URL and append
        // relative endpoints directly. Normalize any concrete package/download
        // URL such as ".../game.jar" or ".../jar.php?uid=..." to its parent
        // directory so that concatenation continues to work.
        return stripQueryAndFragment(packageUri.resolve("."));
    }

    private static URI stripQueryAndFragment(URI uri) {
        try {
            return new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), null, null);
        } catch (java.net.URISyntaxException exception) {
            throw new IllegalArgumentException("Could not normalize package URI: " + uri, exception);
        }
    }

    private static boolean isJarPath(Path path) {
        return path != null && isJarName(path.toString());
    }

    private static boolean isJarName(String name) {
        return name != null && name.length() >= 4 && name.regionMatches(true, name.length() - 4, ".jar", 0, 4);
    }

    private static String lastPathSegment(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static String packagePathPart(String raw) {
        int cut = raw.length();
        int query = raw.indexOf('?');
        if (query >= 0) {
            cut = Math.min(cut, query);
        }
        int fragment = raw.indexOf('#');
        if (fragment >= 0) {
            cut = Math.min(cut, fragment);
        }
        return raw.substring(0, cut).trim();
    }

    private static String toDirectoryUri(Path directory) {
        String uri = directory.toUri().toString();
        return uri.endsWith("/") ? uri : uri + "/";
    }

    private static Class<?> loadApplicationClass(String className) throws ClassNotFoundException {
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        if (contextLoader != null) {
            return Class.forName(className, false, contextLoader);
        }
        return Class.forName(className, false, JamLauncher.class.getClassLoader());
    }

    private static String stripExtension(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    private static void applyOptionalDrawArea(LaunchConfig.Builder builder, String rawDrawArea, String targetDevice) {
        int[] drawArea = parseDrawArea(rawDrawArea);
        if (drawArea == null) {
            drawArea = DoJaProfile.documentedDrawAreaForTargetDevice(targetDevice);
        }
        if (builder == null || drawArea == null) {
            // DrawArea is optional. When it is absent and the JAM does not expose a documented
            // TargetDevice, leave the launch on the existing 240x240 host default viewport.
            return;
        }
        builder.viewport(drawArea[0], drawArea[1]);
    }

    private static int[] parseDrawArea(String rawDrawArea) {
        if (rawDrawArea == null || rawDrawArea.isBlank()) {
            return null;
        }
        String[] parts = rawDrawArea.trim().split("[xX]");
        if (parts.length != 2) {
            return null;
        }
        try {
            return new int[]{Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())};
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static ResolvedScratchpad resolveScratchpad(Path jamPath) {
        String baseName = stripExtension(jamPath.getFileName().toString());
        Path sibling = jamPath.resolveSibling(baseName + ".sp").normalize();
        if (Files.exists(sibling)) {
            return new ResolvedScratchpad(sibling, true);
        }
        Path fallback = jamPath.getParent() == null ? null
                : jamPath.getParent().resolveSibling("sp").resolve(baseName + ".sp").normalize();
        if (fallback != null && Files.exists(fallback)) {
            return new ResolvedScratchpad(fallback, true);
        }
        return new ResolvedScratchpad(sibling, false);
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

    private record ResolvedScratchpad(Path path, boolean found) {
    }
}
