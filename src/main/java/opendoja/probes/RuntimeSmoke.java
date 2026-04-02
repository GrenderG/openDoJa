package opendoja.probes;

import com.nttdocomo.ui.Canvas;
import com.nttdocomo.ui.Display;
import com.nttdocomo.ui.Frame;
import com.nttdocomo.ui.Graphics;
import com.nttdocomo.ui.IApplication;
import com.nttdocomo.ui.Image;
import com.nttdocomo.ui.MediaData;
import com.nttdocomo.ui.MediaManager;
import com.nttdocomo.util.Timer;

import javax.microedition.io.Connector;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class RuntimeSmoke {
    private RuntimeSmoke() {
    }

    public static void main(String[] args) throws Exception {
        DemoLog.enableInfoLogging();
        System.setProperty("java.awt.headless", "true");
        Path scratchpadRoot = Files.createTempDirectory("runtime-smoke-scratchpad");
        IApplication app = opendoja.host.DesktopLauncher.launch(
                opendoja.host.LaunchConfig.builder(SmokeApp.class)
                        .scratchpadRoot(scratchpadRoot)
                        .build());
        if (!(Display.getCurrent() instanceof SmokeCanvas)) {
            throw new IllegalStateException("Display current frame was not set");
        }

        Image image = Image.createImage(8, 8);
        Graphics g = image.getGraphics();
        g.setColor(Graphics.getColorOfName(Graphics.RED));
        g.fillRect(0, 0, 8, 8);
        g.dispose();
        if (image.getWidth() != 8 || image.getHeight() != 8) {
            throw new IllegalStateException("Image dimensions mismatch");
        }

        try (OutputStream out = Connector.openOutputStream("scratchpad:///0:pos=0")) {
            out.write("ok".getBytes(StandardCharsets.UTF_8));
        }
        byte[] scratch = new byte[2];
        try (InputStream in = Connector.openInputStream("scratchpad:///0:pos=0")) {
            if (in.read(scratch) != 2) {
                throw new IllegalStateException("Scratchpad read length mismatch");
            }
        }
        if (!"ok".equals(new String(scratch, StandardCharsets.UTF_8))) {
            throw new IllegalStateException("Scratchpad content mismatch");
        }

        MediaData data = MediaManager.getData("resource:///opendoja/demo/test.txt");
        if (data == null) {
            throw new IllegalStateException("MediaManager resource load failed");
        }

        CountDownLatch latch = new CountDownLatch(1);
        Timer timer = new Timer();
        timer.setTime(10);
        timer.setListener(t -> latch.countDown());
        timer.start();
        if (!latch.await(2, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timer did not fire");
        }
        timer.dispose();
        app.terminate();
        DemoLog.info(RuntimeSmoke.class, "Runtime smoke OK");
    }

    public static final class SmokeApp extends IApplication {
        @Override
        public void start() {
            Display.setCurrent((Frame) new SmokeCanvas());
        }
    }

    static final class SmokeCanvas extends Canvas {
        @Override
        public void paint(Graphics g) {
            g.lock();
            g.clearRect(0, 0, Display.getWidth(), Display.getHeight());
            g.setColor(Graphics.getColorOfName(Graphics.WHITE));
            g.drawString("smoke", 0, 0);
            g.unlock(true);
        }
    }
}
