package opendoja.host.storage;

import com.nttdocomo.device.StorageDevice;
import com.nttdocomo.fs.DoJaAccessToken;
import com.nttdocomo.fs.DoJaStorageService;
import com.nttdocomo.fs.FileNotAccessibleException;
import com.nttdocomo.fs.FileSystemFullException;
import com.nttdocomo.fs.MediaNotFoundException;
import opendoja.host.DoJaEncoding;
import opendoja.host.OpenDoJaPaths;
import opendoja.host.DoJaRuntime;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Internal host-side implementation for the DoJa storage APIs.
 */
public final class DoJaStorageHost {
    public static final String EXTERNAL_DEVICE_NAME = "/ext0";
    public static final String PRINT_NAME = "SD Memory Card";
    private static final String SD_BIND_DIRECTORY_NAME = "SD_BIND";

    private static final String[] ALL_CAPABILITIES = {
            StorageDevice.CAPABILITY_SD,
            StorageDevice.CAPABILITY_FAT32,
            StorageDevice.CAPABILITY_FAT_LONG_NAME,
            StorageDevice.CAPABILITY_SD_BINDING
    };
    private static final String[] HARDWARE_CAPABILITIES = {
            StorageDevice.CAPABILITY_SD
    };
    private static final String[] FILESYSTEM_CAPABILITIES = {
            StorageDevice.CAPABILITY_FAT32,
            StorageDevice.CAPABILITY_FAT_LONG_NAME
    };
    private static final String[] ENCRYPTION_CAPABILITIES = {
            StorageDevice.CAPABILITY_SD_BINDING
    };

    private static final Object OPEN_LOCK = new Object();
    private static final Map<Path, OpenState> OPEN_FILES = new HashMap<>();

    private DoJaStorageHost() {
    }

    public static void ensureStoragePermission() {
        DoJaRuntime runtime = DoJaRuntime.current();
        if (runtime == null) {
            throw new SecurityException("UseStorage requires an active DoJa runtime");
        }
        String useStorage = runtime.parameters().get("UseStorage");
        if (useStorage == null || useStorage.isBlank()) {
            throw new SecurityException("ADF UseStorage permission is required");
        }
    }

    public static Path deviceRoot() {
        return OpenDoJaPaths.hostDataRoot().resolve("storage").resolve("ext0");
    }

    public static Path bindingRoot() {
        return deviceRoot().resolve(SD_BIND_DIRECTORY_NAME);
    }

    public static String[] getCapabilities(String category) {
        if (category == null) {
            return ALL_CAPABILITIES.clone();
        }
        return switch (category) {
            case StorageDevice.CATEGORY_HARDWARE -> HARDWARE_CAPABILITIES.clone();
            case StorageDevice.CATEGORY_FILESYSTEM -> FILESYSTEM_CAPABILITIES.clone();
            case StorageDevice.CATEGORY_ENCRYPTION -> ENCRYPTION_CAPABILITIES.clone();
            default -> null;
        };
    }

    public static String mediaId() {
        String base = stableId(deviceRoot().toAbsolutePath().normalize().toString());
        return "odjext0" + base.substring(0, java.lang.Math.min(24, base.length()));
    }

    public static Path resolveNamespaceRoot(DoJaAccessToken token) {
        ensureStoragePermission();
        DoJaRuntime runtime = DoJaRuntime.current();
        if (runtime == null) {
            throw new SecurityException("Storage access requires an active DoJa runtime");
        }
        String applicationClassName = runtime.application() == null
                ? trim(runtime.parameters().get("AppClass"))
                : runtime.application().getClass().getName();
        return resolveNamespaceRoot(runtime.parameters(), runtime.sourceUrl(), applicationClassName,
                token.getAccess(), token.getShare());
    }

    static Path resolveNamespaceRoot(Map<String, String> parameters,
                                     String sourceUrl,
                                     String applicationClassName,
                                     int access,
                                     int share) {
        String scopeName = switch (share) {
            case DoJaStorageService.SHARE_APPLICATION -> scopedDirectoryName(
                    applicationScopeDisplayName(parameters, sourceUrl, applicationClassName),
                    "app-" + stableId(applicationScopeKey(parameters, sourceUrl, applicationClassName)));
            case DoJaStorageService.SHARE_CONTENTS_PROVIDER -> scopedDirectoryName(
                    contentsProviderDisplayName(parameters),
                    "cp-" + stableId(contentsProviderScopeKey(parameters)));
            default -> throw new IllegalArgumentException("Unsupported share mode: " + share);
        };
        Path scopeRoot = bindingRoot().resolve(scopeName);
        if (share == DoJaStorageService.SHARE_APPLICATION) {
            // Official DoJa documents per-access isolation behind SD-Binding
            // bind IDs, but real extracted game packages are commonly
            // distributed for idkDoJa-style manual installs under one visible
            // application folder. Keep every app-shared token rooted at the
            // same SD_BIND/<app> directory so those media dumps remain
            // consumable while contents-provider shares stay isolated.
            return scopeRoot;
        }
        return scopeRoot.resolve(".odj-bind")
                .resolve("access-" + access)
                .resolve("share-" + share);
    }

    public static void ensureNamespaceExists(Path namespaceRoot) throws IOException {
        try {
            Files.createDirectories(namespaceRoot);
        } catch (FileSystemException exception) {
            throw translateCreateFailure(exception);
        }
    }

    public static long getUsableSpace(Path namespaceRoot) throws IOException {
        Path probe = namespaceRoot;
        if (!Files.exists(probe)) {
            Path parent = probe.getParent();
            probe = parent == null ? deviceRoot() : parent;
        }
        Files.createDirectories(probe);
        FileStore store = Files.getFileStore(probe);
        return store.getUsableSpace();
    }

    public static void validateFileName(String fileName) throws FileNotAccessibleException {
        if (fileName == null) {
            throw new NullPointerException("fileName");
        }
        if (fileName.isEmpty()
                || ".".equals(fileName)
                || "..".equals(fileName)
                || fileName.indexOf('/') >= 0
                || fileName.indexOf('\\') >= 0
                || fileName.indexOf('\0') >= 0) {
            throw new FileNotAccessibleException(FileNotAccessibleException.ILLEGAL_NAME,
                    "Illegal file name: " + fileName);
        }
    }

    public static boolean isFileAttributeSupported(Class<?> clazz) {
        return com.nttdocomo.fs.EncryptionAttribute.class.isAssignableFrom(clazz);
    }

    public static Path resolveExistingFile(Path directory, String fileName) throws IOException {
        Path exact = directory.resolve(fileName);
        if (Files.exists(exact)) {
            return exact;
        }
        if (!Files.isDirectory(directory)) {
            return null;
        }
        Path match = null;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path candidate : stream) {
                String candidateName = candidate.getFileName().toString();
                if (!candidateName.equalsIgnoreCase(fileName)) {
                    continue;
                }
                if (candidateName.equals(fileName)) {
                    return candidate;
                }
                // Real SD-card dumps often keep the original handset-era case,
                // while games request lower-case names. Pick one deterministic
                // case-insensitive match so lookups and duplicate checks behave
                // consistently across platforms.
                if (match == null
                        || candidateName.compareToIgnoreCase(match.getFileName().toString()) < 0
                        || candidateName.compareTo(match.getFileName().toString()) < 0) {
                    match = candidate;
                }
            }
        }
        return match;
    }

    public static Runnable acquireOpen(Path path, int mode) throws FileNotAccessibleException {
        synchronized (OPEN_LOCK) {
            Path normalized = normalize(path);
            OpenState state = OPEN_FILES.computeIfAbsent(normalized, ignored -> new OpenState());
            boolean writeMode = mode != com.nttdocomo.fs.File.MODE_READ_ONLY;
            if (writeMode) {
                if (state.readers > 0 || state.writer) {
                    throw new FileNotAccessibleException(FileNotAccessibleException.IN_USE,
                            "File is already open: " + normalized);
                }
                state.writer = true;
            } else {
                if (state.writer) {
                    throw new FileNotAccessibleException(FileNotAccessibleException.IN_USE,
                            "File is already open for writing: " + normalized);
                }
                state.readers++;
            }
            return new OpenHandle(normalized, writeMode);
        }
    }

    public static boolean isFileOpen(Path path) {
        synchronized (OPEN_LOCK) {
            OpenState state = OPEN_FILES.get(normalize(path));
            return state != null && (state.writer || state.readers > 0);
        }
    }

    public static IOException translateCreateFailure(IOException exception) {
        if (isNoSpace(exception)) {
            return new FileSystemFullException(exception.getMessage());
        }
        if (exception instanceof AccessDeniedException) {
            return new FileNotAccessibleException(FileNotAccessibleException.ACCESS_DENIED, exception.getMessage());
        }
        if (exception instanceof FileAlreadyExistsException) {
            return new FileNotAccessibleException(FileNotAccessibleException.ALREADY_EXISTS, exception.getMessage());
        }
        if (exception instanceof NoSuchFileException) {
            return new MediaNotFoundException(exception.getMessage());
        }
        if (exception instanceof FileSystemException) {
            return new FileNotAccessibleException(FileNotAccessibleException.ACCESS_DENIED, exception.getMessage());
        }
        return exception;
    }

    public static IOException translateExistingPathFailure(IOException exception) {
        if (exception instanceof NoSuchFileException) {
            return new FileNotAccessibleException(FileNotAccessibleException.NOT_FOUND, exception.getMessage());
        }
        if (exception instanceof AccessDeniedException) {
            return new FileNotAccessibleException(FileNotAccessibleException.ACCESS_DENIED, exception.getMessage());
        }
        if (exception instanceof FileSystemException) {
            return new FileNotAccessibleException(FileNotAccessibleException.ACCESS_DENIED, exception.getMessage());
        }
        return exception;
    }

    public static IOException translateDeleteFailure(IOException exception) {
        if (exception instanceof NoSuchFileException) {
            return new FileNotAccessibleException(FileNotAccessibleException.NOT_FOUND, exception.getMessage());
        }
        if (exception instanceof AccessDeniedException || exception instanceof FileSystemException) {
            return new FileNotAccessibleException(FileNotAccessibleException.ACCESS_DENIED, exception.getMessage());
        }
        return exception;
    }

    public static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    public static long nowMillis() {
        return Instant.now().toEpochMilli();
    }

    private static String applicationScopeKey(Map<String, String> parameters,
                                              String sourceUrl,
                                              String applicationClassName) {
        if (sourceUrl != null && !sourceUrl.isBlank()) {
            return sourceUrl.trim();
        }
        String appClass = trim(applicationClassName);
        if (appClass != null) {
            return appClass;
        }
        String appName = trim(parameters.get("AppName"));
        if (appName != null) {
            return appName;
        }
        return "runtime-missing";
    }

    private static String applicationScopeDisplayName(Map<String, String> parameters,
                                                      String sourceUrl,
                                                      String applicationClassName) {
        String appName = trim(parameters.get("AppName"));
        if (appName != null) {
            return appName;
        }
        String appClass = trim(parameters.get("AppClass"));
        if (appClass == null) {
            appClass = trim(applicationClassName);
        }
        if (appClass != null) {
            int separator = java.lang.Math.max(appClass.lastIndexOf('.'), appClass.lastIndexOf('$'));
            return separator >= 0 ? appClass.substring(separator + 1) : appClass;
        }
        String trimmedSource = trim(sourceUrl);
        if (trimmedSource == null) {
            return null;
        }
        int slash = trimmedSource.lastIndexOf('/');
        String tail = slash >= 0 ? trimmedSource.substring(slash + 1) : trimmedSource;
        int query = tail.indexOf('?');
        if (query >= 0) {
            tail = tail.substring(0, query);
        }
        int dot = tail.lastIndexOf('.');
        return dot > 0 ? tail.substring(0, dot) : tail;
    }

    private static String contentsProviderScopeKey(Map<String, String> parameters) {
        String cpName = trim(parameters.get("CPName"));
        if (cpName == null) {
            throw new SecurityException("CPName is required for contents-provider sharing");
        }
        String trustedApiId = trim(parameters.get("TrustedAPID"));
        String cpBindingAuth = trim(parameters.get("CPBindingAuth"));
        if (trustedApiId == null && cpBindingAuth == null) {
            throw new SecurityException("TrustedAPID or CPBindingAuth is required for contents-provider sharing");
        }
        String binding = trustedApiId != null ? trustedApiId : cpBindingAuth;
        return cpName + ":" + binding;
    }

    private static String contentsProviderDisplayName(Map<String, String> parameters) {
        return trim(parameters.get("CPName"));
    }

    private static String stableId(String raw) {
        String normalized = raw == null ? "" : raw.trim();
        byte[] bytes = normalized.getBytes(DoJaEncoding.DEFAULT_CHARSET);
        return UUID.nameUUIDFromBytes(bytes).toString().replace("-", "").toLowerCase(Locale.ROOT);
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String scopedDirectoryName(String preferredName, String fallbackName) {
        String sanitized = sanitizeDirectoryName(preferredName);
        return sanitized == null ? fallbackName : sanitized;
    }

    private static String sanitizeDirectoryName(String value) {
        String trimmed = trim(value);
        if (trimmed == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder(trimmed.length());
        boolean wroteReplacement = false;
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            // Keep any Unicode letters/digits so real AppName values such as
            // Japanese titles still map to a recognizable SD_BIND folder, and
            // only collapse characters that are awkward across common desktop
            // filesystems.
            boolean allowed = Character.isLetterOrDigit(ch) || ch == '_' || ch == '-' || ch == '.';
            if (allowed) {
                builder.append(ch);
                wroteReplacement = false;
                continue;
            }
            if (!wroteReplacement) {
                builder.append('_');
                wroteReplacement = true;
            }
        }
        String sanitized = builder.toString();
        return sanitized.isEmpty() ? null : sanitized;
    }

    private static boolean isNoSpace(IOException exception) {
        if (!(exception instanceof FileSystemException fileSystemException)) {
            return false;
        }
        String reason = fileSystemException.getReason();
        String message = reason == null ? exception.getMessage() : reason;
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("no space")
                || normalized.contains("not enough space")
                || normalized.contains("disk full");
    }

    private static final class OpenState {
        private int readers;
        private boolean writer;
    }

    private static final class OpenHandle implements Runnable {
        private final Path path;
        private final boolean writer;
        private boolean released;

        private OpenHandle(Path path, boolean writer) {
            this.path = path;
            this.writer = writer;
        }

        @Override
        public void run() {
            synchronized (OPEN_LOCK) {
                if (released) {
                    return;
                }
                released = true;
                OpenState state = OPEN_FILES.get(path);
                if (state == null) {
                    return;
                }
                if (writer) {
                    state.writer = false;
                } else if (state.readers > 0) {
                    state.readers--;
                }
                if (!state.writer && state.readers == 0) {
                    OPEN_FILES.remove(path);
                }
            }
        }
    }
}
