package opendoja.demo;

import com.nttdocomo.ui.Canvas;
import com.nttdocomo.ui.Display;
import com.nttdocomo.ui.Frame;
import opendoja.host.DoJaRuntime;
import opendoja.host.JamLauncher;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

public final class JamInputProbe {
    private JamInputProbe() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 4 || args.length > 5) {
            throw new IllegalArgumentException("Usage: JamInputProbe <jam-path> <before-ms> <key> <after-ms> [output-png]");
        }
        Path jamPath = Path.of(args[0]);
        long beforeMillis = Long.parseLong(args[1]);
        int key = parseKey(args[2]);
        long afterMillis = Long.parseLong(args[3]);
        Path output = args.length == 5 ? Path.of(args[4]) : null;

        Thread launchThread = new Thread(() -> {
            try {
                JamLauncher.launch(jamPath);
            } catch (Throwable throwable) {
                throwable.printStackTrace(System.err);
            }
        }, "jam-input-probe-launch");
        launchThread.setDaemon(true);
        launchThread.start();

        Throwable failure = null;
        try {
            waitForRuntime();
            Thread.sleep(java.lang.Math.max(0L, beforeMillis));
            DoJaRuntime runtime = requireRuntime();
            runtime.dispatchSyntheticKey(key, Display.KEY_PRESSED_EVENT);
            Thread.sleep(200L);
            runtime.dispatchSyntheticKey(key, Display.KEY_RELEASED_EVENT);
            Thread.sleep(java.lang.Math.max(0L, afterMillis));
            requireRuntime();
            if (output != null) {
                writeCurrentCanvas(output);
            }
        } catch (Throwable throwable) {
            failure = throwable;
            throwable.printStackTrace(System.err);
        } finally {
            DoJaRuntime runtime = DoJaRuntime.current();
            if (runtime != null) {
                runtime.shutdown();
            }
            System.exit(failure == null ? 0 : 1);
        }
    }

    private static int parseKey(String keyName) {
        return switch (keyName.toUpperCase()) {
            case "SELECT", "ENTER" -> Display.KEY_SELECT;
            case "LEFT" -> Display.KEY_LEFT;
            case "RIGHT" -> Display.KEY_RIGHT;
            case "UP" -> Display.KEY_UP;
            case "DOWN" -> Display.KEY_DOWN;
            case "SOFT1" -> Display.KEY_SOFT1;
            case "SOFT2" -> Display.KEY_SOFT2;
            default -> throw new IllegalArgumentException("Unsupported key: " + keyName);
        };
    }

    private static void waitForRuntime() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000L;
        while (DoJaRuntime.current() == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(20L);
        }
        if (DoJaRuntime.current() == null) {
            throw new IllegalStateException("DoJa runtime did not initialize");
        }
    }

    private static DoJaRuntime requireRuntime() {
        DoJaRuntime runtime = DoJaRuntime.current();
        if (runtime == null) {
            throw new IllegalStateException("DoJa runtime exited before probe completed");
        }
        return runtime;
    }

    private static void writeCurrentCanvas(Path output) throws Exception {
        BufferedImage image = captureCurrentCanvas();
        if (image == null) {
            throw new IllegalStateException("No current canvas image available");
        }
        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }
        ImageIO.write(image, "png", output.toFile());
    }

    private static BufferedImage captureCurrentCanvas() throws Exception {
        DoJaRuntime runtime = requireRuntime();
        Frame frame = runtime.getCurrentFrame();
        if (!(frame instanceof Canvas canvas)) {
            return null;
        }
        Method surfaceMethod = Canvas.class.getDeclaredMethod("surface");
        surfaceMethod.setAccessible(true);
        Object surface = surfaceMethod.invoke(canvas);
        if (surface == null) {
            return null;
        }
        Method imageMethod = surface.getClass().getDeclaredMethod("image");
        imageMethod.setAccessible(true);
        BufferedImage image = (BufferedImage) imageMethod.invoke(surface);
        BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        copy.getGraphics().drawImage(image, 0, 0, null);
        return copy;
    }
}
