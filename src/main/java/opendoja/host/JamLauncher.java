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
        return launch(jamPath, false, LaunchConfig.LaunchTypeOption.resolveConfigured());
    }

    public static IApplication launch(Path jamPath, boolean exitOnShutdown) throws IOException, ClassNotFoundException {
        return launch(jamPath, exitOnShutdown, LaunchConfig.LaunchTypeOption.resolveConfigured());
    }

    public static IApplication launch(Path jamPath, LaunchConfig.LaunchTypeOption launchType)
            throws IOException, ClassNotFoundException {
        return launch(jamPath, false, launchType);
    }

    public static IApplication launch(Path jamPath, boolean exitOnShutdown, LaunchConfig.LaunchTypeOption launchType)
            throws IOException, ClassNotFoundException {
        return DesktopLauncher.launch(buildLaunchConfig(jamPath, exitOnShutdown, launchType));
    }

    public static LaunchConfig buildLaunchConfig(Path jamPath, boolean exitOnShutdown) throws IOException, ClassNotFoundException {
        return buildLaunchConfig(jamPath, exitOnShutdown, LaunchConfig.LaunchTypeOption.resolveConfigured());
    }

    public static LaunchConfig buildLaunchConfig(Path jamPath, boolean exitOnShutdown, LaunchConfig.LaunchTypeOption launchType)
            throws IOException, ClassNotFoundException {
        Properties properties = JamMetadataResolver.loadJamProperties(jamPath);
        return buildLaunchConfig(jamPath, properties, exitOnShutdown, launchType);
    }

    private static LaunchConfig buildLaunchConfig(Path jamPath, Properties properties, boolean exitOnShutdown,
                                                  LaunchConfig.LaunchTypeOption launchType)
            throws IOException, ClassNotFoundException {
        return buildLaunchConfig(jamPath, properties, exitOnShutdown, launchType, null);
    }

    public static LaunchConfig buildLinkedLaunchConfig(Path jamPath, boolean exitOnShutdown,
                                                       Map<String, String> linkedParameters)
            throws IOException, ClassNotFoundException {
        return buildLaunchConfig(jamPath, JamMetadataResolver.loadJamProperties(jamPath), exitOnShutdown,
                LaunchConfig.LaunchTypeOption.NORMAL, java.util.Objects.requireNonNull(linkedParameters));
    }

    private static LaunchConfig buildLaunchConfig(Path jamPath, Properties properties, boolean exitOnShutdown,
                                                  LaunchConfig.LaunchTypeOption launchType,
                                                  Map<String, String> linkedParameters)
            throws IOException, ClassNotFoundException {
        String appClassName = properties.getProperty("AppClass");
        if (appClassName == null || appClassName.isBlank()) {
            throw new IllegalArgumentException("JAM/ADF missing AppClass: " + jamPath);
        }
        Path gameJarPath = locateGameJarIfPresent(jamPath, properties);
        // Install the bridge before any app code runs so bootstrap-owned Class.getResource*
        // lookups can still fall back to the selected JAM jar.
        JamNamedModuleResourceBridge.install(gameJarPath);
        // Legacy titles ask Java for "SJIS"/"Shift_JIS", but DoJa's mapping is not identical to
        // the desktop JDK binding. Install the OpenDoJa-owned SJIS_i provider before app code
        // runs, and disable the built-in Shift_JIS binding so those names resolve to it.
        DoJaSjisCompatibility.install();
        Class<?> rawClass = loadApplicationClass(appClassName.trim(), gameJarPath);
        if (!IApplication.class.isAssignableFrom(rawClass)) {
            throw new IllegalArgumentException("AppClass does not extend IApplication: " + appClassName);
        }
        @SuppressWarnings("unchecked")
        Class<? extends IApplication> applicationClass = (Class<? extends IApplication>) rawClass;
        int[] scratchpadSizes = parseScratchpadSizes(properties.getProperty("SPsize"));
        LaunchConfig.Builder builder = LaunchConfig.builder(applicationClass)
                .title(properties.getProperty("AppName", applicationClass.getSimpleName()))
                .jamPath(jamPath)
                .sourceUrl(resolvePackageUrl(jamPath, properties.getProperty("PackageURL")))
                .scratchpadSizes(scratchpadSizes)
                .launchType(linkedParameters == null
                        ? (launchType == null ? LaunchConfig.LaunchTypeOption.NORMAL : launchType).launchType
                        : IApplication.LAUNCHED_FROM_IAPPLI)
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
        if (linkedParameters != null) {
            linkedParameters.forEach(builder::parameter);
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
        LaunchConfig.LaunchTypeOption launchType = LaunchConfig.LaunchTypeOption.resolveConfigured();
        List<String> effectiveArgs = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if (OpenDoJaCliFlags.PHONE_MODEL.equals(args[i])) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Usage: " + usageLine());
                }
                OpenDoJaLaunchArgs.set(OpenDoJaLaunchArgs.MICROEDITION_PLATFORM_OVERRIDE, args[++i]);
                continue;
            }
            if (OpenDoJaCliFlags.LAUNCH_TYPE.equals(args[i])) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Usage: " + usageLine());
                }
                launchType = requireLaunchType(args[++i]);
                OpenDoJaLaunchArgs.set(OpenDoJaLaunchArgs.LAUNCH_TYPE, launchType.id);
                continue;
            }
            if (OpenDoJaCliFlags.SCREEN_ROTATION.equals(args[i])) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Usage: " + usageLine());
                }
                OpenDoJaLaunchArgs.set(OpenDoJaLaunchArgs.DISPLAY_ROTATION, requireDisplayRotation(args[++i]));
                continue;
            }
            effectiveArgs.add(args[i]);
        }
        if (effectiveArgs.size() != 1) {
            throw new IllegalArgumentException("Usage: " + usageLine());
        }
        Path jamPath = Path.of(effectiveArgs.get(0));
        LaunchCompatibility.reexecJamLauncherWithRequiredAddOpensIfNeeded(jamPath);
        LaunchCompatibility.reexecJamLauncherIfNeeded(jamPath);
        String requestFile = OpenDoJaLaunchArgs.get(OpenDoJaLaunchArgs.LINKED_LAUNCH_REQUEST);
        Path requestPath = requestFile.isBlank() ? null : Path.of(requestFile);
        LinkedAppLaunchRequest linkedRequest = requestPath == null ? null
                : LinkedAppLaunchRequest.read(requestPath, jamPath);
        if (requestPath != null) {
            // Keep it available to the one-time VerifyError re-exec until startup succeeds.
            requestPath.toFile().deleteOnExit();
        }
        try {
            if (linkedRequest == null) {
                launch(jamPath, true, launchType);
            } else {
                DesktopLauncher.launch(buildLinkedLaunchConfig(jamPath, true, linkedRequest.parameters()));
            }
        } catch (VerifyError error) {
            // A few handset-era jars contain bytecode that modern HotSpot rejects up front even
            // though the same title otherwise runs once verification is disabled. Retry once from
            // the top launch boundary so the default path stays strict for all normal titles.
            if (!LaunchCompatibility.reexecJamLauncherOnVerifyError(jamPath)) {
                throw error;
            }
            return;
        }
        if (requestPath != null) {
            Files.deleteIfExists(requestPath);
            System.clearProperty(OpenDoJaLaunchArgs.LINKED_LAUNCH_REQUEST);
        }
        DoJaRuntime runtime = DoJaRuntime.current();
        if (runtime != null) {
            runtime.awaitShutdown();
        }
        System.exit(0);
    }

    private static String usageLine() {
        return "JamLauncher [" + OpenDoJaCliFlags.PHONE_MODEL + " <model>] ["
                + OpenDoJaCliFlags.LAUNCH_TYPE + " <normal|standby>] ["
                + OpenDoJaCliFlags.SCREEN_ROTATION + " <" + OpenDoJaLaunchArgs.displayRotationChoices() + ">] <path-to-jam>";
    }

    private static LaunchConfig.LaunchTypeOption requireLaunchType(String value) {
        LaunchConfig.LaunchTypeOption launchType = LaunchConfig.LaunchTypeOption.fromId(value);
        if (launchType == null) {
            throw new IllegalArgumentException("Unknown launch type: " + value + ". Expected normal or standby.");
        }
        return launchType;
    }

    private static String requireDisplayRotation(String value) {
        String normalized = OpenDoJaLaunchArgs.normalizeDisplayRotation(value);
        if (!normalized.equals(value == null ? null : value.trim().toLowerCase(java.util.Locale.ROOT))) {
            throw new IllegalArgumentException(
                    "Unknown screen rotation: " + value + ". Expected "
                            + OpenDoJaLaunchArgs.displayRotationChoicesText() + ".");
        }
        return normalized;
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

    private static Class<?> loadApplicationClass(String className, Path gameJarPath) throws ClassNotFoundException {
        if (gameJarPath != null) {
            ClassLoader parentLoader = Thread.currentThread().getContextClassLoader();
            if (parentLoader == null) {
                parentLoader = JamLauncher.class.getClassLoader();
            }
            ClassLoader gameLoader = JamGameClassLoaderFactory.create(gameJarPath, parentLoader);
            return Class.forName(className, false, gameLoader);
        }
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        if (contextLoader != null) {
            return Class.forName(className, false, contextLoader);
        }
        return Class.forName(className, false, JamLauncher.class.getClassLoader());
    }

    private static Path locateGameJarIfPresent(Path jamPath, Properties properties) {
        try {
            return JamGameJarLocator.locate(jamPath, properties);
        } catch (IOException ignored) {
            return null;
        }
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
