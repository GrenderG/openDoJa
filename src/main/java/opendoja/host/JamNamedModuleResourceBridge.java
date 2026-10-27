package opendoja.host;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Bridges DoJa-style absolute resource lookups that accidentally originate from a java.base class.
 *
 * <p>Some titles call {@code "".getClass().getResourceAsStream("/asset")} instead of resolving
 * through their own application class. On Java SE that means {@link java.lang.Class} asks the boot
 * loader for the resource from the named {@code java.base} module, which cannot see the JAM jar.
 * This overlay keeps the normal java.base lookup first and only falls back to the selected game
 * jar when the boot loader reports that java.base does not contain the requested resource.</p>
 */
public final class JamNamedModuleResourceBridge {
    private static final String ADD_OPENS_ARGUMENT = "--add-opens=java.base/jdk.internal.loader=ALL-UNNAMED";
    private static final String TARGET_MODULE = "java.base";
    private static final Object INSTALL_LOCK = new Object();
    private static volatile JarResourceSource resourceSource = JarResourceSource.empty();

    private static volatile boolean installed;

    private JamNamedModuleResourceBridge() {
    }

    public static String requiredAddOpensArgument() {
        return ADD_OPENS_ARGUMENT;
    }

    public static boolean hasRequiredAddOpens() {
        return hasRequiredAddOpens(ManagementFactory.getRuntimeMXBean().getInputArguments());
    }

    public static boolean install(Path gameJarPath) throws IOException {
        if (gameJarPath == null) {
            return false;
        }
        Path normalized = gameJarPath.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized) || !hasRequiredAddOpens()) {
            return false;
        }
        JarResourceSource resourceSource = JarResourceSource.fromJar(normalized);
        if (resourceSource.isEmpty()) {
            return false;
        }
        ensureInstalled();
        JamNamedModuleResourceBridge.resourceSource = resourceSource;
        return true;
    }

    static boolean hasRequiredAddOpens(List<String> arguments) {
        for (int i = 0; i < arguments.size(); i++) {
            String arg = arguments.get(i);
            if (arg.startsWith("--add-opens=")) {
                if (grantsRequiredAddOpens(arg.substring("--add-opens=".length()))) {
                    return true;
                }
                continue;
            }
            if ("--add-opens".equals(arg) && i + 1 < arguments.size()
                    && grantsRequiredAddOpens(arguments.get(i + 1))) {
                return true;
            }
        }
        return false;
    }

    private static boolean grantsRequiredAddOpens(String value) {
        if (value == null || !value.startsWith("java.base/jdk.internal.loader=")) {
            return false;
        }
        String targets = value.substring("java.base/jdk.internal.loader=".length());
        for (String target : targets.split(",")) {
            if ("ALL-UNNAMED".equals(target.trim())) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static void ensureInstalled() {
        if (installed) {
            return;
        }
        synchronized (INSTALL_LOCK) {
            if (installed) {
                return;
            }
            try {
                // Some titles, e.g. games like Midnight Bowling, reach
                // Class.getResourceAsStream("/asset") through String.class, so the JDK
                // resolves the resource from the boot loader's
                // java.base module reader instead of from the JAM app class loader.
                // Replacing only that module reference lets us add a fallback resource source
                // without rewriting any game bytecode or mutating the game jar itself.
                Class<?> classLoaders = Class.forName("jdk.internal.loader.ClassLoaders");
                Method bootLoaderMethod = classLoaders.getDeclaredMethod("bootLoader");
                bootLoaderMethod.setAccessible(true);
                Object bootLoader = bootLoaderMethod.invoke(null);

                Field nameToModuleField = declaredField(bootLoader.getClass(), "nameToModule");
                nameToModuleField.setAccessible(true);
                Map<String, ModuleReference> nameToModule =
                        (Map<String, ModuleReference>) nameToModuleField.get(bootLoader);
                ModuleReference moduleReference = nameToModule.get(TARGET_MODULE);
                if (moduleReference == null) {
                    throw new IllegalStateException("Boot loader does not expose " + TARGET_MODULE);
                }
                if (!(moduleReference instanceof OverlayModuleReference)) {
                    nameToModule.put(TARGET_MODULE, new OverlayModuleReference(moduleReference));
                }
                installed = true;
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Could not install JAM named-module resource bridge", exception);
            }
        }
    }

    private static Field declaredField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static final class OverlayModuleReference extends ModuleReference {
        private final ModuleReference delegate;

        private OverlayModuleReference(ModuleReference delegate) {
            super(delegate.descriptor(), delegate.location().orElse(null));
            this.delegate = delegate;
        }

        @Override
        public ModuleReader open() throws IOException {
            return new OverlayModuleReader(delegate.open());
        }
    }

    private static final class OverlayModuleReader implements ModuleReader {
        private final ModuleReader delegate;

        private OverlayModuleReader(ModuleReader delegate) {
            this.delegate = delegate;
        }

        @Override
        public Optional<URI> find(String name) throws IOException {
            Optional<URI> found = delegate.find(name);
            if (found.isPresent()) {
                return found;
            }
            // ModuleReader already provides default open/read behavior in terms of find().
            // Returning a jar: URI here keeps the overlay small and lets the JDK handle the
            // remaining stream/byte-buffer plumbing.
            return resourceSource.find(name);
        }

        @Override
        public Optional<InputStream> open(String name) throws IOException {
            Optional<InputStream> opened = delegate.open(name);
            if (opened.isPresent()) {
                return opened;
            }
            // OpenDoJa's normal launch-resource API returns memory-backed streams because some
            // titles assume one read(byte[]) fills the requested buffer. The named-module fallback
            // has to preserve that same contract instead of exposing a raw jar URL stream.
            return resourceSource.open(name);
        }

        @Override
        public Stream<String> list() throws IOException {
            return Stream.concat(delegate.list(), resourceSource.list()).distinct();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }

    private static final class JarResourceSource {
        private static final JarResourceSource EMPTY = new JarResourceSource(null, Collections.emptySet());

        private final Path gameJarPath;
        private final Set<String> resourceNames;

        private JarResourceSource(Path gameJarPath, Set<String> resourceNames) {
            this.gameJarPath = gameJarPath;
            this.resourceNames = resourceNames;
        }

        private static JarResourceSource empty() {
            return EMPTY;
        }

        private static JarResourceSource fromJar(Path gameJarPath) throws IOException {
            Set<String> names = new LinkedHashSet<>();
            try (JarFile jarFile = new JarFile(gameJarPath.toFile())) {
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (shouldExpose(entry)) {
                        names.add(entry.getName());
                    }
                }
            }
            if (names.isEmpty()) {
                return EMPTY;
            }
            return new JarResourceSource(gameJarPath, Collections.unmodifiableSet(names));
        }

        private boolean isEmpty() {
            return resourceNames.isEmpty();
        }

        private Optional<URI> find(String name) {
            if (!resourceNames.contains(name)) {
                return Optional.empty();
            }
            String encodedFileUrl = gameJarPath.toUri().toASCIIString().replace("!", "%21");
            return Optional.of(URI.create("jar:" + encodedFileUrl + "!/" + encodeEntryName(name)));
        }

        private Stream<String> list() {
            return resourceNames.stream();
        }

        private Optional<InputStream> open(String name) throws IOException {
            if (!resourceNames.contains(name)) {
                return Optional.empty();
            }
            try (JarFile jarFile = new JarFile(gameJarPath.toFile())) {
                JarEntry entry = jarFile.getJarEntry(name);
                if (entry == null) {
                    return Optional.empty();
                }
                try (InputStream in = jarFile.getInputStream(entry)) {
                    return Optional.of(new ByteArrayInputStream(in.readAllBytes()));
                }
            }
        }

        private static boolean shouldExpose(JarEntry entry) {
            if (entry == null || entry.isDirectory()) {
                return false;
            }
            String name = entry.getName();
            if (name == null || name.isBlank()) {
                return false;
            }
            if (name.endsWith(".class")) {
                return false;
            }
            return !name.regionMatches(true, 0, "META-INF/", 0, "META-INF/".length());
        }

        private static String encodeEntryName(String name) {
            StringBuilder encoded = new StringBuilder();
            String[] parts = name.split("/", -1);
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) {
                    encoded.append('/');
                }
                encoded.append(URLEncoder.encode(parts[i], StandardCharsets.UTF_8).replace("+", "%20"));
            }
            return encoded.toString();
        }
    }
}
