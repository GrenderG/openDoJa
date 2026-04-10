package opendoja.host;

import com.nttdocomo.ui.IApplication;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JamLauncher {
    private static final Pattern DEVICE_HINT_PATTERN = Pattern.compile(
            "(?i)(?:^|[^A-Za-z0-9])((?:FOMA\\s+)?[A-Z]?[0-9]{3,4}i?[A-Z]?(?:S|V|C)?)(?:[^A-Za-z0-9]|$)");

    private JamLauncher() {
    }

    public static IApplication launch(Path jamPath) throws IOException, ClassNotFoundException {
        return launch(jamPath, false);
    }

    public static IApplication launch(Path jamPath, boolean exitOnShutdown) throws IOException, ClassNotFoundException {
        return DesktopLauncher.launch(buildLaunchConfig(jamPath, exitOnShutdown));
    }

    public static LaunchConfig buildLaunchConfig(Path jamPath, boolean exitOnShutdown) throws IOException, ClassNotFoundException {
        Properties properties = loadJamProperties(jamPath);
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
        applyOptionalDrawArea(builder, properties.getProperty("DrawArea"), properties.getProperty("TargetDevice"));
        String appParam = properties.getProperty("AppParam");
        if (appParam != null && !appParam.isBlank()) {
            builder.args(appParam.trim().split("\\s+"));
        }
        for (String name : properties.stringPropertyNames()) {
            builder.parameter(name, properties.getProperty(name));
        }
        String inferredTargetDevice = inferTargetDevice(jamPath, properties);
        String inferredProfileVersion = inferProfileVersion(properties);
        if (inferredProfileVersion != null) {
            builder.parameter("ProfileVer", inferredProfileVersion);
        }
        if (inferredTargetDevice != null && !properties.containsKey("TargetDevice")) {
            builder.parameter("TargetDevice", inferredTargetDevice);
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

    private static String inferTargetDevice(Path jamPath, Properties properties) {
        String inferred = metadataDeviceIdentity(properties);
        if (inferred != null) {
            return inferred;
        }
        return firstDeviceHint(jamPath.toString());
    }

    private static String inferProfileVersion(Properties properties) {
        String configured = properties.getProperty("ProfileVer");
        if (configured != null && !configured.isBlank()) {
            return null;
        }
        if (metadataDeviceIdentity(properties) != null) {
            return null;
        }
        int[] drawArea = parseDrawArea(properties.getProperty("DrawArea"));
        if (drawArea == null) {
            return null;
        }
        DoJaProfile inferred = DoJaProfile.fromDocumentedLegacyDisplayResolution(drawArea[0], drawArea[1]);
        return inferred.isKnown() ? inferred.toString() : null;
    }

    private static String metadataDeviceIdentity(Properties properties) {
        String configured = properties.getProperty("TargetDevice");
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        String packageUrl = properties.getProperty("PackageURL");
        if (packageUrl != null && !packageUrl.isBlank()) {
            return firstDeviceHint(packageUrl);
        }
        return null;
    }

    private static String firstDeviceHint(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        Matcher matcher = DEVICE_HINT_PATTERN.matcher(candidate);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1).trim();
    }

    private static URI normalizePackageUri(URI packageUri) {
        String path = packageUri.getPath();
        if (!isJarName(lastPathSegment(path))) {
            return packageUri;
        }
        return packageUri.resolve(".");
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

    private static Properties loadJamProperties(Path jamPath) throws IOException {
        byte[] data = Files.readAllBytes(jamPath);
        CharacterCodingException lastCodingFailure = null;
        for (String charsetName : DoJaEncoding.defaultEncodingCandidates()) {
            try {
                return loadJamProperties(data, Charset.forName(charsetName));
            } catch (CharacterCodingException exception) {
                lastCodingFailure = exception;
            } catch (RuntimeException ignored) {
            }
        }
        if (lastCodingFailure != null) {
            throw lastCodingFailure;
        }
        throw new IllegalStateException("No JAM property charsets configured");
    }

    private static Properties loadJamProperties(byte[] data, Charset charset) throws IOException {
        Properties properties = new Properties();
        String text = charset.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(data))
                .toString();
        try (BufferedReader reader = new BufferedReader(new StringReader(text))) {
            properties.load(reader);
        }
        return properties;
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

    private static String stripExtension(String name) {
        int lastDot = name.lastIndexOf('.');
        return lastDot < 0 ? name : name.substring(0, lastDot);
    }

    private record ResolvedScratchpad(Path path, boolean found) {
    }
}
