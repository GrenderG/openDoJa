package opendoja.demo;

import com.nttdocomo.ui.Canvas;
import com.nttdocomo.ui.Frame;
import opendoja.host.DoJaRuntime;
import opendoja.host.JamLauncher;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CaptureJamFrame {
    private CaptureJamFrame() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("Usage: CaptureJamFrame <jam-path> <delay-ms> <output-png>");
        }
        Path jamPath = Path.of(args[0]);
        long delayMillis = Long.parseLong(args[1]);
        Path output = Path.of(args[2]);
        System.err.println("launching " + jamPath);
        Thread launchThread = new Thread(() -> {
            try {
                JamLauncher.launch(jamPath);
            } catch (Throwable throwable) {
                throwable.printStackTrace(System.err);
            }
        }, "capture-launch");
        launchThread.setDaemon(true);
        launchThread.start();
        Throwable failure = null;
        try {
            waitForRuntime();
            System.err.println("runtime ready");
            Thread.sleep(Math.max(0L, delayMillis));
            System.err.println("capturing");
            BufferedImage image = waitForCurrentCanvasImage();
            if (image == null) {
                throw new IllegalStateException("No current canvas image available");
            }
            if (output.getParent() != null) {
                Files.createDirectories(output.getParent());
            }
            ImageIO.write(image, "png", output.toFile());
            System.out.println(output.toAbsolutePath());
            System.err.println("written");
        } catch (Throwable throwable) {
            failure = throwable;
            throwable.printStackTrace(System.err);
        } finally {
            DoJaRuntime runtime = DoJaRuntime.current();
            if (runtime != null) {
                runtime.shutdown();
            }
            System.err.println("shutdown");
            System.exit(failure == null ? 0 : 1);
        }
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

    private static BufferedImage captureCurrentCanvas() throws ReflectiveOperationException, IOException {
        DoJaRuntime runtime = DoJaRuntime.current();
        if (runtime == null) {
            return null;
        }
        Frame frame = runtime.getCurrentFrame();
        if (!(frame instanceof Canvas canvas)) {
            System.err.println("captureCurrentCanvas: current frame is " + (frame == null ? "null" : frame.getClass().getName()));
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

    private static BufferedImage waitForCurrentCanvasImage() throws ReflectiveOperationException, IOException, InterruptedException {
        long deadline = System.currentTimeMillis() + 5000L;
        while (System.currentTimeMillis() < deadline) {
            BufferedImage image = captureCurrentCanvas();
            if (image != null) {
                return image;
            }
            Thread.sleep(50L);
        }
        return captureCurrentCanvas();
    }
}
