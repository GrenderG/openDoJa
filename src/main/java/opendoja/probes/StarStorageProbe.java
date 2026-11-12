package opendoja.probes;

import com.docomostar.StarApplication;
import com.docomostar.device.StorageDevice;
import com.docomostar.fs.*;
import com.docomostar.io.FileDataInput;
import com.docomostar.io.FileEntity;
import opendoja.host.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Exercises real SD-Binding I/O and verifies that STAR preserves host access rules. */
public final class StarStorageProbe {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        String name = "star-storage-probe-" + UUID.randomUUID();
        launch(name, true);
        File file = null;
        try {
            StarAccessToken token = StarStorageService.getAccessToken(StarAccessToken.ACCESS_PLATFORM,
                    StarStorageService.SHARE_APPLICATION);
            check(token == StarStorageService.getAccessToken(2, 8), "Stable token identity");
            check(token.getAccess() == 2 && token.getShare() == 8, "Token values");
            StorageDevice device = StorageDevice.getInstance("/ext0");
            check(device == StorageDevice.getInstance("/ext0"), "Stable device identity");
            Folder folder = device.getFolder(token);
            check(folder.getStorageDevice() == device && folder.getAccessToken() == token, "Folder identity");
            expectIllegalArgument(() -> device.getFolder(new AccessToken() { }));
            expectIllegalArgument(() -> StarStorageService.getAccessToken(8, 8));
            expectIllegalArgument(() -> StarStorageService.getAccessToken(2, 0));
            expectFileStatus(FileNotAccessibleException.ILLEGAL_NAME, () -> folder.getFile("../escape"));
            expectFileStatus(FileNotAccessibleException.NOT_FOUND, () -> folder.getFile("missing.bin"));

            file = folder.createFile("Assets.BIN", new FileAttribute[]{new EncryptionAttribute()});
            check(Files.isRegularFile(OpenDoJaPaths.hostDataRoot()
                    .resolve("storage/ext0/SD_BIND/SVC0000" + name + ".jam/Assets.BIN")),
                    "STAR should use the JAM's SVC0000 storage folder");
            File created = file;
            try (FileEntity entity = file.open(File.MODE_WRITE_ONLY)) {
                entity.setBufferSize(256);
                check(entity.getBufferSize() == 256, "Buffer size");
                try (var output = entity.openDataOutput()) {
                    output.writeInt(0x12345678);
                    output.writeUTF("STAR");
                    output.write(new byte[]{4, 5, 6});
                    output.flush();
                    check(output.getSize() == 13, "Output length");
                }
            }
            check(folder.getFiles().length == 1, "Folder listing");
            // The existing SD host resolves names case-insensitively, as on FAT media.
            try (FileEntity entity = folder.getFile("assets.bin").open(File.MODE_READ_ONLY)) {
                FileDataInput input = entity.openDataInput();
                check(input.getSize() == 13 && input.getPosition() == 0, "Input size and position");
                check(input.readInt() == 0x12345678, "Read integer");
                input.setPositionRelative(-4);
                check(input.readInt() == 0x12345678 && "STAR".equals(input.readUTF()), "Random access and UTF");
                byte[] tail = new byte[3]; input.readFully(tail);
                check(java.util.Arrays.equals(tail, new byte[]{4, 5, 6}), "Read complete payload");
                expectFileStatus(FileNotAccessibleException.IN_USE, () -> created.open(File.MODE_WRITE_ONLY));
                expectFileStatus(FileNotAccessibleException.IN_USE, created::delete);
                entity.close();
                try { input.getSize(); throw new AssertionError("Owner close must invalidate input"); }
                catch (IOException expected) { }
            }
            DoJaRuntime.current().shutdown();
            launch(name + "-other", true);
            Folder other = StorageDevice.getInstance("/ext0").getFolder(token);
            expectFileStatus(FileNotAccessibleException.NOT_FOUND, () -> other.getFile("Assets.BIN"));
            DoJaRuntime.current().shutdown();
            launch(name, false);
            expectSecurity(() -> StarStorageService.getAccessToken(2, 8));
            expectSecurity(() -> StorageDevice.getInstance("/ext0"));
        } finally {
            if (DoJaRuntime.current() != null) DoJaRuntime.current().shutdown();
            // Delete only the fixture created by this probe, using the same public API.
            if (file != null) { launch(name, true); try { file.delete(); } finally { DoJaRuntime.current().shutdown(); } }
        }
        System.out.println("star-storage-probe-ok");
    }

    private static void launch(String name, boolean storage) {
        var builder = LaunchConfig.builder(ProbeApp.class).parameter("AppType", "FullApp")
                .jamPath(Path.of(name + ".jam"))
                .parameter("AppName", name).sourceUrl("https://star-storage-probe.invalid/");
        if (storage) builder.parameter("UseStorage", "ext0");
        DesktopLauncher.launch(builder.build());
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void expectFileStatus(int status, Action action) throws Exception {
        try { action.run(); throw new AssertionError("Expected file status " + status); }
        catch (FileNotAccessibleException expected) { check(expected.getStatus() == status, "STAR file status"); }
    }
    private static void expectIllegalArgument(Action action) throws Exception {
        try { action.run(); throw new AssertionError("Expected invalid argument"); }
        catch (IllegalArgumentException expected) { }
    }
    private static void expectSecurity(Action action) throws Exception {
        try { action.run(); throw new AssertionError("Expected storage permission denial"); }
        catch (SecurityException expected) { }
    }
    @FunctionalInterface private interface Action { void run() throws Exception; }
    public static final class ProbeApp extends StarApplication { @Override public void started(int reason) { } }
}
