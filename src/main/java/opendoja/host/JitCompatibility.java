package opendoja.host;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class JitCompatibility {
    private static final String APPLIED_PROPERTY = "opendoja.jitCompatApplied";

    private JitCompatibility() {
    }

    static void reexecJamLauncherIfNeeded(Path jamPath) throws IOException, InterruptedException {
        if (!Boolean.parseBoolean(System.getProperty("opendoja.jitCompat", "true"))) {
            return;
        }
        if (Boolean.getBoolean(APPLIED_PROPERTY) || alreadyUsingCompileCommands()) {
            return;
        }

        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(jamPath)) {
            properties.load(in);
        }
        String appClassName = properties.getProperty("AppClass");
        if (appClassName == null || appClassName.isBlank()) {
            return;
        }

        Set<String> patterns = new LinkedHashSet<>();
        String trimmedAppClass = appClassName.trim();
        patterns.add(trimmedAppClass + ".*");
        Path packagePath = resolvePackagePath(jamPath, properties.getProperty("PackageURL"));
        if (packagePath == null) {
            packagePath = resolveAppCodeSource(trimmedAppClass);
        }
        if (packagePath != null && Files.isRegularFile(packagePath) && packagePath.getFileName().toString().endsWith(".jar")) {
            patterns.addAll(classPatternsFromJar(packagePath));
        }
        if (patterns.isEmpty()) {
            return;
        }

        Path commandFile = writeCompileCommandFile(patterns);
        Process process = new ProcessBuilder(buildJavaCommand(commandFile, JamLauncher.class.getName(), new String[]{jamPath.toString()}))
                .inheritIO()
                .start();
        int exit = process.waitFor();
        System.exit(exit);
    }

    private static boolean alreadyUsingCompileCommands() {
        for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (arg.startsWith("-XX:CompileCommandFile=")) {
                return true;
            }
        }
        return false;
    }

    private static List<String> buildJavaCommand(Path commandFile, String mainClass, String[] args) {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (arg.startsWith("-XX:CompileCommandFile=") || arg.startsWith("-D" + APPLIED_PROPERTY + "=")) {
                continue;
            }
            command.add(arg);
        }
        command.add("-D" + APPLIED_PROPERTY + "=true");
        command.add("-XX:CompileCommandFile=" + commandFile);
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(mainClass);
        for (String arg : args) {
            command.add(arg);
        }
        return command;
    }

    private static Path writeCompileCommandFile(Set<String> patterns) throws IOException {
        Path file = Files.createTempFile("opendoja-compile-commands", ".txt");
        List<String> lines = new ArrayList<>(patterns.size());
        for (String pattern : patterns) {
            lines.add("exclude " + pattern);
        }
        Files.write(file, lines, StandardCharsets.UTF_8);
        return file;
    }

    private static Set<String> classPatternsFromJar(Path jarPath) throws IOException {
        Set<String> patterns = new LinkedHashSet<>();
        try (InputStream in = Files.newInputStream(jarPath); ZipInputStream zip = new ZipInputStream(in)) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                if (!entry.getName().endsWith(".class")) {
                    continue;
                }
                String className = entry.getName().substring(0, entry.getName().length() - ".class".length())
                        .replace('/', '.');
                patterns.add(className + ".*");
            }
        }
        return patterns;
    }

    private static Path resolveAppCodeSource(String appClassName) {
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            Class<?> appClass = Class.forName(appClassName, false, loader);
            if (appClass.getProtectionDomain() == null || appClass.getProtectionDomain().getCodeSource() == null) {
                return null;
            }
            URI location = appClass.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path path = Path.of(location);
            return Files.isDirectory(path) ? null : path;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Path resolvePackagePath(Path jamPath, String packageUrl) {
        if (packageUrl == null || packageUrl.isBlank()) {
            return null;
        }
        String trimmed = packageUrl.trim();
        if (trimmed.contains("://")) {
            if (!trimmed.startsWith("file:")) {
                return null;
            }
            return Path.of(java.net.URI.create(trimmed));
        }
        Path base = jamPath.getParent();
        return (base == null ? Path.of(trimmed) : base.resolve(trimmed)).normalize();
    }
}
