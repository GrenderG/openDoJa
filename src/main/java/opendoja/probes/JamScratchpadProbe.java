package opendoja.probes;

import com.nttdocomo.ui.IApplication;
import opendoja.host.DoJaRuntime;
import opendoja.host.JamLauncher;
import opendoja.host.LaunchConfig;
import opendoja.host.OpenDoJaLog;

import javax.microedition.io.ConnectionNotFoundException;
import javax.microedition.io.Connector;
import java.io.DataInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class JamScratchpadProbe {
    private JamScratchpadProbe() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        OpenDoJaLog.configure(OpenDoJaLog.Level.WARN);

        verifyScratchpadIsDisabledWithoutSpSize();
        verifyJamFieldsAreCaseInsensitive();
        verifySiblingScratchpadWrite();
        verifyParentSpFallbackWrite();
        verifyMissingScratchpadWarningAndCreation();
        verifyDojaemuHeaderIsPreservedAndMapped();
        verifyDeclaredSizeMismatchWarning();
        verifyMissingResourceStaysMissingAndScratchpadStillReads();

        System.out.println("Jam scratchpad probe OK");
    }

    private static void verifySiblingScratchpadWrite() throws Exception {
        Path root = Files.createTempDirectory("jam-sp-sibling");
        Path jam = root.resolve("Sibling.jam");
        Path scratchpad = root.resolve("Sibling.sp");
        Files.write(scratchpad, new byte[2]);
        writeJam(jam, "SPsize=2\n");

        LaunchConfig config = JamLauncher.buildLaunchConfig(jam, false);
        check(scratchpad.equals(config.scratchpadPackedFile()), "sibling .sp should be preferred");

        launchAndWrite(jam, "ok");
        check("ok".equals(Files.readString(scratchpad, StandardCharsets.UTF_8)), "sibling .sp should receive direct writes");
    }

    private static void verifyJamFieldsAreCaseInsensitive() throws Exception {
        Path root = Files.createTempDirectory("jam-case-insensitive");
        Path jam = root.resolve("MixedCase.jam");
        Path scratchpad = root.resolve("MixedCase.sp");
        Files.write(scratchpad, new byte[2]);
        writeJam(jam, """
                appclass=%s
                appname=Case Insensitive Probe
                spsize=2
                trustedapid=1
                appparam=left right
                """.formatted(ProbeApp.class.getName()));

        LaunchConfig config = JamLauncher.buildLaunchConfig(jam, false);
        check("Case Insensitive Probe".equals(config.title()), "lowercase AppName should set the launch title");
        check(scratchpad.equals(config.scratchpadPackedFile()), "lowercase SPsize should enable scratchpad resolution");
        check(config.iAppliType() == LaunchConfig.IAppliType.I_APPLI_DX,
                "lowercase TrustedAPID should select i-appli DX");
        check("1".equals(config.parameters().get("TrustedAPID")),
                "launch parameters should resolve canonical casing");
        check("1".equals(config.parameters().get("trustedapid")),
                "launch parameters should resolve lowercase casing");
        check(Arrays.equals(new String[]{"left", "right"}, config.args()), "lowercase AppParam should set launch args");

        IApplication app = JamLauncher.launch(jam, false);
        try {
            check("1".equals(app.getParameter("TrustedAPID")),
                    "application parameters should resolve canonical casing");
            check("1".equals(app.getParameter("trustedapid")),
                    "application parameters should resolve lowercase casing");
        } finally {
            DoJaRuntime runtime = DoJaRuntime.current();
            if (runtime != null) {
                runtime.shutdown();
            }
        }

        launchAndWrite(jam, "ci");
        check("ci".equals(Files.readString(scratchpad, StandardCharsets.UTF_8)),
                "case-insensitive SPsize should enable scratchpad writes");
    }

    private static void verifyParentSpFallbackWrite() throws Exception {
        Path root = Files.createTempDirectory("jam-sp-fallback");
        Path bin = Files.createDirectories(root.resolve("bin"));
        Path sp = Files.createDirectories(root.resolve("sp"));
        Path jam = bin.resolve("Fallback.jam");
        Path scratchpad = sp.resolve("Fallback.sp");
        Files.write(scratchpad, new byte[2]);
        writeJam(jam, "SPsize=2\n");

        LaunchConfig config = JamLauncher.buildLaunchConfig(jam, false);
        check(scratchpad.equals(config.scratchpadPackedFile()), "../sp fallback should be selected when sibling is absent");

        launchAndWrite(jam, "fb");
        check("fb".equals(Files.readString(scratchpad, StandardCharsets.UTF_8)), "../sp .sp should receive direct writes");
    }

    private static void verifyMissingScratchpadWarningAndCreation() throws Exception {
        Path root = Files.createTempDirectory("jam-sp-missing");
        Path jam = root.resolve("Missing.jam");
        Path scratchpad = root.resolve("Missing.sp");
        writeJam(jam, "SPsize=4\n");

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        LaunchConfig config;
        try (PrintStream capture = new PrintStream(captured, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            config = JamLauncher.buildLaunchConfig(jam, false);
        } finally {
            System.setOut(originalOut);
        }

        check(scratchpad.equals(config.scratchpadPackedFile()), "missing .sp should target the sibling path");
        check(captured.toString(StandardCharsets.UTF_8).contains("No .sp file found"),
                "missing .sp should log a warning");

        launchAndWrite(jam, "init");
        check(Files.exists(scratchpad), "missing .sp should be creatable at runtime");
        byte[] bytes = Files.readAllBytes(scratchpad);
        check(bytes.length == 4, "SPsize-backed .sp should use raw payload storage");
        check(Arrays.equals("init".getBytes(StandardCharsets.UTF_8), bytes),
                "runtime writes should land at the start of the raw payload");
    }

    private static void verifyScratchpadIsDisabledWithoutSpSize() throws Exception {
        Path root = Files.createTempDirectory("jam-sp-disabled");
        Path jam = root.resolve("Disabled.jam");
        Path scratchpad = root.resolve("Disabled.sp");
        Files.writeString(scratchpad, "legacy", StandardCharsets.UTF_8);
        writeJam(jam, null);

        LaunchConfig config = JamLauncher.buildLaunchConfig(jam, false);
        check(config.scratchpadPackedFile() == null, "scratchpad should stay disabled when SPsize is omitted");
        check(config.scratchpadRoot() == null, "scratchpad root should stay disabled when SPsize is omitted");

        IApplication app = JamLauncher.launch(jam, false);
        try {
            try (InputStream ignored = Connector.openInputStream("scratchpad:///0;pos=0")) {
                throw new IllegalStateException("scratchpad:/// should fail when SPsize is omitted");
            } catch (ConnectionNotFoundException expected) {
                // Expected.
            }
        } finally {
            DoJaRuntime runtime = DoJaRuntime.current();
            if (runtime != null) {
                runtime.shutdown();
            }
        }
        if (app == null) {
            throw new IllegalStateException("Jam launch returned null application");
        }
    }

    private static void verifyDojaemuHeaderIsPreservedAndMapped() throws Exception {
        Path root = Files.createTempDirectory("jam-sp-header");
        Path jam = root.resolve("Header.jam");
        Path scratchpad = root.resolve("Header.sp");
        writeJam(jam, "SPsize=4\n");
        byte[] original = dojaemuScratchpad("DATA");
        Files.write(scratchpad, original);
        launchAndRead(jam, 4);
        launchAndWrite(jam, "PING");

        byte[] bytes = Files.readAllBytes(scratchpad);
        check(bytes.length == 68, "headered dojaemu .sp files should remain headered on disk");
        check(Arrays.equals(Arrays.copyOf(original, 64), Arrays.copyOf(bytes, 64)),
                "headered .sp files should preserve the original 64-byte header");
        check(Arrays.equals("PING".getBytes(StandardCharsets.UTF_8), Arrays.copyOfRange(bytes, 64, 68)),
                "logical scratchpad writes should land in the payload after the header");
    }

    private static void verifyDeclaredSizeMismatchWarning() throws Exception {
        Path root = Files.createTempDirectory("jam-sp-mismatch");
        Path jam = root.resolve("Mismatch.jam");
        Path scratchpad = root.resolve("Mismatch.sp");
        writeJam(jam, "SPsize=4\n");
        Files.write(scratchpad, "abc".getBytes(StandardCharsets.UTF_8));

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try (PrintStream capture = new PrintStream(captured, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            launchAndRead(jam, 3);
        } finally {
            System.setOut(originalOut);
        }

        check(captured.toString(StandardCharsets.UTF_8).contains("declares 4 bytes but file size is 3"),
                "scratchpad size mismatches should log a warning");
    }

    private static void verifyMissingResourceStaysMissingAndScratchpadStillReads() throws Exception {
        Path root = Files.createTempDirectory("jam-sp-resource-fallback");
        Path jam = root.resolve("Fallback.jam");
        Path scratchpad = root.resolve("Fallback.sp");
        writeJam(jam, "SPsize=4\n");
        Files.write(scratchpad, dojaemuScratchpad("DATA"));

        IApplication app = JamLauncher.launch(jam, false);
        try {
            try (DataInputStream ignored = Connector.openDataInputStream("resource:///0")) {
                throw new IllegalStateException("missing resource:/// URL should throw ConnectionNotFoundException");
            } catch (ConnectionNotFoundException expected) {
                // Expected: packaged resource lookup should not bridge into scratchpad.
            }

            try (DataInputStream in = Connector.openDataInputStream("scratchpad:///0;pos=0,length=4")) {
                byte[] bytes = in.readNBytes(4);
                check(Arrays.equals("DATA".getBytes(StandardCharsets.UTF_8), bytes),
                        "scratchpad:/// fallback should still read logical payload bytes");
            }
        } finally {
            DoJaRuntime runtime = DoJaRuntime.current();
            if (runtime != null) {
                runtime.shutdown();
            }
        }
        if (app == null) {
            throw new IllegalStateException("Jam launch returned null application");
        }
    }

    private static void launchAndWrite(Path jam, String value) throws Exception {
        IApplication app = JamLauncher.launch(jam, false);
        try (OutputStream out = Connector.openOutputStream("scratchpad:///0:pos=0")) {
            out.write(value.getBytes(StandardCharsets.UTF_8));
        } finally {
            DoJaRuntime runtime = DoJaRuntime.current();
            if (runtime != null) {
                runtime.shutdown();
            }
        }
        if (app == null) {
            throw new IllegalStateException("Jam launch returned null application");
        }
    }

    private static void launchAndRead(Path jam, int expectedLength) throws Exception {
        IApplication app = JamLauncher.launch(jam, false);
        try (InputStream in = Connector.openInputStream("scratchpad:///0:pos=0")) {
            byte[] bytes = in.readAllBytes();
            check(bytes.length == expectedLength, "unexpected scratchpad payload length");
        } finally {
            DoJaRuntime runtime = DoJaRuntime.current();
            if (runtime != null) {
                runtime.shutdown();
            }
        }
        if (app == null) {
            throw new IllegalStateException("Jam launch returned null application");
        }
    }

    private static void writeJam(Path jam, String extraProperties) throws Exception {
        StringBuilder builder = new StringBuilder();
        builder.append("AppClass=").append(ProbeApp.class.getName()).append('\n');
        builder.append("AppName=Scratchpad Probe\n");
        if (extraProperties != null) {
            builder.append(extraProperties);
        }
        Files.writeString(jam, builder.toString(), StandardCharsets.ISO_8859_1);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static byte[] dojaemuScratchpad(String payload) {
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(64 + bytes.length);
        buffer.putInt(bytes.length);
        for (int i = 1; i < 16; i++) {
            buffer.putInt(-1);
        }
        buffer.put(bytes);
        return buffer.array();
    }

    public static final class ProbeApp extends IApplication {
        @Override
        public void start() {
        }
    }
}
