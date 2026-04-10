package opendoja.host.storage;

import com.nttdocomo.device.StorageDevice;
import com.nttdocomo.fs.DoJaAccessToken;
import com.nttdocomo.fs.DoJaStorageService;
import com.nttdocomo.fs.EncryptionAttribute;
import com.nttdocomo.fs.FileAttribute;
import com.nttdocomo.fs.FileNotAccessibleException;
import com.nttdocomo.fs.Folder;
import com.nttdocomo.fs.sd.SDBindingEncryptionAttribute;
import com.nttdocomo.io.FileEntity;
import com.nttdocomo.ui.IApplication;
import opendoja.host.DoJaRuntime;
import opendoja.host.JamLauncher;
import opendoja.host.OpenDoJaLog;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public final class DoJaStorageHostProbe {
    private static final int BRIDGE_ACCESS_MASK =
            DoJaAccessToken.ACCESS_UIM | DoJaAccessToken.ACCESS_PLATFORM;
    private static final Path GUNDAM_PACKAGE_PATH = Path.of(
            "resources/sample_games/[ENGLISH PATCH] SD Gundam G Generations Mobile/sdcarddata/PACKAGE.LST");
    private static final Path ROCKMAN_CARD_MARKER_PATH = Path.of(
            "resources/sample_games/[ENGLISH PATCH-Localized] Rockman DASH, Great Adventure on 5 Islands/sdcarddata/RDDATA0.BIN");
    private static final Path ROCKMAN_AREA_PAYLOAD_PATH = Path.of(
            "resources/sample_games/[ENGLISH PATCH-Localized] Rockman DASH, Great Adventure on 5 Islands/sdcarddata/RDDATA00.BIN");

    private DoJaStorageHostProbe() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        OpenDoJaLog.configure(OpenDoJaLog.Level.WARN);

        verifyBundledGundamMapping();
        verifyBundledRockmanMapping();
        verifyRuntimeStorageContract();
        verifyOptionalGundamStorageIoContract();
        verifyOptionalRockmanStorageIoContract();

        System.out.println("DoJa storage host probe OK");
    }

    private static void verifyBundledGundamMapping() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("AppName", "GGeneMobile");
        parameters.put("AppClass", "GGeneMobile");
        parameters.put("CPName", "NBGI");
        parameters.put("TrustedAPID", "00000000000");

        Path actual = DoJaStorageHost.resolveNamespaceRoot(
                parameters,
                "http://sdg.gs.keitaiarchive.org/jar.php?uid=LQNBFCWVHBQF",
                "GGeneMobile",
                BRIDGE_ACCESS_MASK,
                DoJaStorageService.SHARE_APPLICATION);
        check(actual.endsWith(Path.of("SD_BIND", "GGeneMobile")),
                "sample Gundam app should resolve to an SD_BIND/GGeneMobile bridge folder");
    }

    private static void verifyRuntimeStorageContract() throws Exception {
        String appName = "SdBindingProbe-" + Long.toUnsignedString(System.nanoTime(), 36);
        Path jamRoot = Files.createTempDirectory("sd-binding-storage-probe");
        Path jam = jamRoot.resolve("Probe.jam");
        Files.writeString(jam, buildJam(appName), StandardCharsets.UTF_8);

        IApplication application = JamLauncher.launch(jam, false);
        if (application == null) {
            throw new IllegalStateException("Jam launch returned null application");
        }

        try {
            StorageDevice device = StorageDevice.getInstance(DoJaStorageHost.EXTERNAL_DEVICE_NAME);
            check(hasCapability(device.getCapability(null), StorageDevice.CAPABILITY_SD_BINDING),
                    "all-capabilities query should expose SD-Binding");
            check(hasCapability(device.getCapability(StorageDevice.CATEGORY_ENCRYPTION), StorageDevice.CAPABILITY_SD_BINDING),
                    "encryption capabilities should expose SD-Binding");

            DoJaAccessToken token = DoJaStorageService.getAccessToken(
                    BRIDGE_ACCESS_MASK,
                    DoJaStorageService.SHARE_APPLICATION);
            Folder folder = device.getFolder(token);
            Path namespaceRoot = DoJaStorageHost.resolveNamespaceRoot(token);

            check("/".equals(folder.getPath()), "storage folder should keep logical root path");
            check(namespaceRoot.endsWith(Path.of("SD_BIND", appName)),
                    "bridge token should map to an SD_BIND scope folder");
            check(folder.isFileAttributeSupported(EncryptionAttribute.class),
                    "EncryptionAttribute should be supported for SD storage");
            check(folder.isFileAttributeSupported(SDBindingEncryptionAttribute.class),
                    "SDBindingEncryptionAttribute should be supported for SD storage");

            Files.createDirectories(namespaceRoot);
            Files.copy(GUNDAM_PACKAGE_PATH, namespaceRoot.resolve("PACKAGE.LST"), StandardCopyOption.REPLACE_EXISTING);

            com.nttdocomo.fs.File packageFile = folder.getFile("package.lst");
            check("/PACKAGE.LST".equals(packageFile.getPath()),
                    "case-insensitive lookup should preserve the on-disk file name");
            check(Arrays.equals(Files.readAllBytes(GUNDAM_PACKAGE_PATH), readAllBytes(packageFile)),
                    "case-insensitive reads should return the copied payload");

            EncryptionAttribute attribute = new EncryptionAttribute();
            attribute.setEncryption(true);
            com.nttdocomo.fs.File created = folder.createFile("settings.bin", new FileAttribute[]{attribute});
            FileEntity entity = created.open(com.nttdocomo.fs.File.MODE_WRITE_ONLY);
            try (OutputStream out = entity.openOutputStream()) {
                out.write("ok".getBytes(StandardCharsets.UTF_8));
            } finally {
                entity.close();
            }

            check(Arrays.equals("ok".getBytes(StandardCharsets.UTF_8), readAllBytes(folder.getFile("SETTINGS.BIN"))),
                    "case-insensitive lookup should resolve files created through the API");

            try {
                folder.createFile("SETTINGS.BIN");
                throw new IllegalStateException("case-insensitive duplicate file creation should fail");
            } catch (FileNotAccessibleException expected) {
                check(expected.getStatus() == FileNotAccessibleException.ALREADY_EXISTS,
                        "case-insensitive duplicate should report ALREADY_EXISTS");
            }
        } finally {
            DoJaRuntime runtime = DoJaRuntime.current();
            if (runtime != null) {
                runtime.shutdown();
            }
        }
    }

    private static void verifyBundledRockmanMapping() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("AppName", "RockmanDashProbe");
        parameters.put("AppClass", "RockmanDash_F");

        Path actual = DoJaStorageHost.resolveNamespaceRoot(
                parameters,
                "file:/probe/RockmanDASH.jar",
                "RockmanDash_F",
                DoJaAccessToken.ACCESS_SERIES,
                DoJaStorageService.SHARE_APPLICATION);
        check(actual.endsWith(Path.of("SD_BIND", "RockmanDashProbe")),
                "application-shared series-only token should still resolve to a visible SD_BIND app folder");
    }

    private static void verifyOptionalGundamStorageIoContract() throws Exception {
        Class<?> storageIoClass;
        try {
            storageIoClass = Class.forName("nexframe.StorageIO");
        } catch (ClassNotFoundException ignored) {
            return;
        }

        Path jamRoot = Files.createTempDirectory("sd-binding-gundam-probe");
        Path jam = jamRoot.resolve("GundamProbe.jam");
        Files.writeString(jam, buildJam("GGeneMobile"), StandardCharsets.UTF_8);

        IApplication application = JamLauncher.launch(jam, false);
        if (application == null) {
            throw new IllegalStateException("Jam launch returned null application for Gundam probe");
        }

        try {
            DoJaAccessToken token = DoJaStorageService.getAccessToken(
                    BRIDGE_ACCESS_MASK,
                    DoJaStorageService.SHARE_APPLICATION);
            Path namespaceRoot = DoJaStorageHost.resolveNamespaceRoot(token);
            Files.createDirectories(namespaceRoot);

            Files.copy(GUNDAM_PACKAGE_PATH, namespaceRoot.resolve("PACKAGE.LST"), StandardCopyOption.REPLACE_EXISTING);

            Object storageIo = storageIoClass.getMethod("getInstance").invoke(null);
            check(storageIo != null, "sample nexframe.StorageIO should initialize successfully");

            byte[] packageBytes = (byte[]) storageIoClass.getMethod("getFile", String.class)
                    .invoke(storageIo, "package.lst");
            check(Arrays.equals(Files.readAllBytes(GUNDAM_PACKAGE_PATH), packageBytes),
                    "sample nexframe.StorageIO should read copied PACKAGE.LST bytes case-insensitively");

            String probeFileName = "probe_" + Long.toUnsignedString(System.nanoTime(), 36) + ".bin";
            boolean wrote = (Boolean) storageIoClass.getMethod("writeFile", String.class, byte[].class)
                    .invoke(storageIo, probeFileName, "ok".getBytes(StandardCharsets.UTF_8));
            check(wrote, "sample nexframe.StorageIO should be able to create encrypted files");

            byte[] probeBytes = (byte[]) storageIoClass.getMethod("getFile", String.class)
                    .invoke(storageIo, probeFileName.toUpperCase());
            check(Arrays.equals("ok".getBytes(StandardCharsets.UTF_8), probeBytes),
                    "sample nexframe.StorageIO should resolve files regardless of case");
        } finally {
            DoJaRuntime runtime = DoJaRuntime.current();
            if (runtime != null) {
                runtime.shutdown();
            }
        }
    }

    private static void verifyOptionalRockmanStorageIoContract() throws Exception {
        Class<?> storageClass;
        try {
            storageClass = Class.forName("at");
        } catch (ClassNotFoundException ignored) {
            return;
        }

        Path jamRoot = Files.createTempDirectory("sd-binding-rockman-probe");
        Path jam = jamRoot.resolve("RockmanProbe.jam");
        Files.writeString(jam, buildJam("RockmanDashProbe"), StandardCharsets.UTF_8);

        IApplication application = JamLauncher.launch(jam, false);
        if (application == null) {
            throw new IllegalStateException("Jam launch returned null application for Rockman probe");
        }

        try {
            DoJaAccessToken token = DoJaStorageService.getAccessToken(
                    DoJaAccessToken.ACCESS_SERIES,
                    DoJaStorageService.SHARE_APPLICATION);
            Path namespaceRoot = DoJaStorageHost.resolveNamespaceRoot(token);
            Files.createDirectories(namespaceRoot);

            Files.copy(ROCKMAN_CARD_MARKER_PATH, namespaceRoot.resolve("RDDATA0.BIN"),
                    StandardCopyOption.REPLACE_EXISTING);
            Files.copy(ROCKMAN_AREA_PAYLOAD_PATH, namespaceRoot.resolve("RDDATA00.BIN"),
                    StandardCopyOption.REPLACE_EXISTING);

            Object storage = storageClass.getConstructor().newInstance();
            storageClass.getMethod("a").invoke(storage);
            check((Boolean) storageClass.getMethod("h").invoke(storage),
                    "Rockman storage wrapper should see the SD card as connected");
            check((Boolean) storageClass.getMethod("i").invoke(storage),
                    "Rockman storage wrapper should see the SD card as writable");
            check((Boolean) storageClass.getMethod("j").invoke(storage),
                    "Rockman storage wrapper should successfully open its folder");

            verifyRockmanRead(storageClass, storage, "rddata0.bin", ROCKMAN_CARD_MARKER_PATH, 2);
            verifyRockmanRead(storageClass, storage, "rddata00.bin", ROCKMAN_AREA_PAYLOAD_PATH, 16);

            boolean created = (Boolean) storageClass.getMethod("b", String.class, int.class)
                    .invoke(storage, "rddata9.bin", com.nttdocomo.fs.File.MODE_WRITE_ONLY);
            check(created, "Rockman storage wrapper should create encrypted SD files");
            storageClass.getMethod("d").invoke(storage);
            byte[] payload = "ok".getBytes(StandardCharsets.UTF_8);
            storageClass.getMethod("a", byte[].class, int.class, int.class)
                    .invoke(storage, payload, 0, payload.length);
            storageClass.getMethod("e").invoke(storage);
            storageClass.getMethod("c").invoke(storage);

            check(Arrays.equals(payload, readAllBytes(namespaceRoot.resolve("rddata9.bin"))),
                    "Rockman storage wrapper should write new files into the visible SD_BIND folder");
        } finally {
            DoJaRuntime runtime = DoJaRuntime.current();
            if (runtime != null) {
                runtime.shutdown();
            }
        }
    }

    private static boolean hasCapability(String[] capabilities, String capability) {
        if (capabilities == null) {
            return false;
        }
        for (String candidate : capabilities) {
            if (capability.equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static byte[] readAllBytes(com.nttdocomo.fs.File file) throws Exception {
        FileEntity entity = file.open(com.nttdocomo.fs.File.MODE_READ_ONLY);
        try (InputStream in = entity.openInputStream()) {
            return in.readAllBytes();
        } finally {
            entity.close();
        }
    }

    private static byte[] readAllBytes(Path path) throws Exception {
        return Files.readAllBytes(path);
    }

    private static void verifyRockmanRead(Class<?> storageClass,
                                          Object storage,
                                          String requestedName,
                                          Path expectedPath,
                                          int bytesToRead) throws Exception {
        boolean opened = (Boolean) storageClass.getMethod("a", String.class, int.class)
                .invoke(storage, requestedName, com.nttdocomo.fs.File.MODE_READ_ONLY);
        check(opened, "Rockman storage wrapper should open " + requestedName);
        storageClass.getMethod("f").invoke(storage);
        byte[] actual = new byte[bytesToRead];
        boolean read = (Boolean) storageClass.getMethod("b", byte[].class, int.class, int.class)
                .invoke(storage, actual, 0, actual.length);
        byte[] expected = Arrays.copyOf(Files.readAllBytes(expectedPath), bytesToRead);
        check(read && Arrays.equals(expected, actual),
                "Rockman storage wrapper should read " + expectedPath.getFileName()
                        + " through lower-case " + requestedName);
        storageClass.getMethod("g").invoke(storage);
        storageClass.getMethod("c").invoke(storage);
    }

    private static String buildJam(String appName) {
        return "AppClass=" + ProbeApp.class.getName() + '\n'
                + "AppName=" + appName + '\n'
                + "DrawArea=176x208\n"
                + "UseStorage=ext\n";
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    public static final class ProbeApp extends IApplication {
        @Override
        public void start() {
        }
    }
}
