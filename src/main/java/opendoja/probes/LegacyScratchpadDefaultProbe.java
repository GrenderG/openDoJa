package opendoja.probes;

import com.nttdocomo.ui.Canvas;
import com.nttdocomo.ui.Display;
import com.nttdocomo.ui.Graphics;
import com.nttdocomo.ui.IApplication;
import opendoja.host.DoJaRuntime;

import javax.microedition.io.ConnectionNotFoundException;
import javax.microedition.io.Connector;

public final class LegacyScratchpadDefaultProbe {
    private LegacyScratchpadDefaultProbe() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        IApplication app = opendoja.host.DesktopLauncher.launch(ProbeApp.class);
        try {
            DoJaRuntime runtime = DoJaRuntime.current();
            if (runtime == null) {
                throw new IllegalStateException("Runtime was not initialized");
            }
            if (runtime.scratchpadRoot() != null || runtime.scratchpadPackedFile() != null) {
                throw new IllegalStateException("Direct launch should not expose scratchpad by default");
            }
            try {
                Connector.openInputStream("scratchpad:///0;pos=0").close();
                throw new IllegalStateException("scratchpad:/// should fail when scratchpad is not configured");
            } catch (ConnectionNotFoundException expected) {
                // Expected.
            }
        } finally {
            DoJaRuntime runtime = DoJaRuntime.current();
            if (runtime != null) {
                runtime.shutdown();
                runtime.awaitShutdown();
            }
            if (app == null) {
                throw new IllegalStateException("Launch returned null application");
            }
        }

        System.out.println("Legacy scratchpad default probe OK");
    }

    public static final class ProbeApp extends IApplication {
        @Override
        public void start() {
            Display.setCurrent(new ProbeCanvas());
        }
    }

    static final class ProbeCanvas extends Canvas {
        @Override
        public void paint(Graphics g) {
            g.lock();
            g.clearRect(0, 0, Display.getWidth(), Display.getHeight());
            g.unlock(true);
        }
    }
}
