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

    private static final String[] ALL_CAPABILITIES = {
            StorageDevice.CAPABILITY_SD,
            StorageDevice.CAPABILITY_FAT32,
            StorageDevice.CAPABILITY_FAT_LONG_NAME
    };
    private static final String[] HARDWARE_CAPABILITIES = {
            StorageDevice.CAPABILITY_SD
    };
    private static final String[] FILESYSTEM_CAPABILITIES = {
            StorageDevice.CAPABILITY_FAT32,
            StorageDevice.CAPABILITY_FAT_LONG_NAME
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

    public static String[] getCapabilities(String category) {
        if (category == null) {
            return ALL_CAPABILITIES.clone();
        }
        return switch (category) {
            case StorageDevice.CATEGORY_HARDWARE -> HARDWARE_CAPABILITIES.clone();
            case StorageDevice.CATEGORY_FILESYSTEM -> FILESYSTEM_CAPABILITIES.clone();
            case StorageDevice.CATEGORY_ENCRYPTION -> null;
            default -> null;
        };
    }

    public static String mediaId() {
        String base = stableId(deviceRoot().toAbsolutePath().normalize().toString());
        return "odjext0" + base.substring(0, java.lang.Math.min(24, base.length()));
    }

    public static Path resolveNamespaceRoot(DoJaAccessToken token) {
        ensureStoragePermission();
        String scopeName = switch (token.getShare()) {
            case DoJaStorageService.SHARE_APPLICATION -> "app-" + stableId(applicationScopeKey());
            case DoJaStorageService.SHARE_CONTENTS_PROVIDER -> "cp-" + stableId(contentsProviderScopeKey());
            default -> throw new IllegalArgumentException("Unsupported share mode: " + token.getShare());
        };
        return deviceRoot().resolve(scopeName).resolve("access-" + token.getAccess());
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

    private static String applicationScopeKey() {
        DoJaRuntime runtime = DoJaRuntime.current();
        if (runtime == null) {
            return "runtime-missing";
        }
        String sourceUrl = runtime.sourceUrl();
        if (sourceUrl != null && !sourceUrl.isBlank()) {
            return sourceUrl.trim();
        }
        return runtime.application().getClass().getName();
    }

    private static String contentsProviderScopeKey() {
        DoJaRuntime runtime = DoJaRuntime.current();
        if (runtime == null) {
            throw new SecurityException("Contents-provider sharing requires an active DoJa runtime");
        }
        String cpName = trim(runtime.parameters().get("CPName"));
        if (cpName == null) {
            throw new SecurityException("CPName is required for contents-provider sharing");
        }
        String trustedApiId = trim(runtime.parameters().get("TrustedAPID"));
        String cpBindingAuth = trim(runtime.parameters().get("CPBindingAuth"));
        if (trustedApiId == null && cpBindingAuth == null) {
            throw new SecurityException("TrustedAPID or CPBindingAuth is required for contents-provider sharing");
        }
        String binding = trustedApiId != null ? trustedApiId : cpBindingAuth;
        return cpName + ":" + binding;
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
