package opendoja.probes;

import com.nttdocomo.ui.Canvas;
import com.nttdocomo.ui.Display;
import com.nttdocomo.ui.Frame;
import com.nttdocomo.ui.Graphics;
import opendoja.host.DoJaRuntime;
import opendoja.host.LaunchConfig;
import opendoja.host.OpenDoJaLaunchArgs;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class ExternalFrameSoftKeyProbe {
    private ExternalFrameSoftKeyProbe() {
    }

    public static void main(String[] args) throws Exception {
        runProbe(176, 208, null, 1, 180, 246, 176, 208,
                new Rectangle(2, 18, 176, 208), new Rectangle(2, 18, 176, 208));
        runProbe(240, 320, null, 1, 244, 358, 240, 320,
                new Rectangle(2, 18, 240, 320), new Rectangle(2, 18, 240, 320));
        runProbe(432, 240, "2", 2, 872, 556, 864, 480,
                new Rectangle(2, 18, 432, 240), new Rectangle(2, 18, 432, 240));
    }

    private static void runProbe(int viewportWidth, int viewportHeight,
                                 String configuredScale, int expectedHostScale,
                                 int expectedEnabledWidth, int expectedEnabledHeight,
                                 int expectedDisabledWidth, int expectedDisabledHeight,
                                 Rectangle expectedScreenArea, Rectangle expectedDrawArea) throws Exception {
        LaunchConfig.Builder builder = LaunchConfig.builder(ProbeApplication.class)
                .viewport(viewportWidth, viewportHeight)
                .title("ExternalFrameSoftKeyProbe");
        LaunchConfig config = builder.build();
        String previousHostScale = OpenDoJaLaunchArgs.get(OpenDoJaLaunchArgs.HOST_SCALE, null);
        if (configuredScale == null) {
            System.clearProperty(OpenDoJaLaunchArgs.HOST_SCALE);
        } else {
            System.setProperty(OpenDoJaLaunchArgs.HOST_SCALE, configuredScale);
        }
        DoJaRuntime.prepareLaunch(config);
        try {
            DoJaRuntime runtime = DoJaRuntime.bootstrap(config);
            try {
                ProbeCanvas canvas = new ProbeCanvas();
                canvas.setSoftLabel(Frame.SOFT_KEY_1, "Left");
                canvas.setSoftLabel(Frame.SOFT_KEY_2, "Right");
                Display.setCurrent(canvas);

                Dimension enabledSize = hostPreferredSize(runtime);
                verifyEnabledLayout(runtime, expectedScreenArea, expectedDrawArea);
                runtime.setExternalFrameEnabled(false);
                Dimension disabledSize = hostPreferredSize(runtime);
                runtime.setExternalFrameEnabled(true);
                Dimension reenabledSize = hostPreferredSize(runtime);

                if (enabledSize.width != expectedEnabledWidth || enabledSize.height != expectedEnabledHeight) {
                    throw new IllegalStateException("Unexpected enabled size for scale " + expectedHostScale + ": " + enabledSize);
                }
                if (disabledSize.width != expectedDisabledWidth || disabledSize.height != expectedDisabledHeight) {
                    throw new IllegalStateException("Unexpected disabled size for scale " + expectedHostScale + ": " + disabledSize);
                }
                if (!enabledSize.equals(reenabledSize)) {
                    throw new IllegalStateException("External frame toggle did not restore the original host viewport");
                }
                if (runtime.hostScale() != expectedHostScale) {
                    throw new IllegalStateException("Unexpected runtime host scale " + runtime.hostScale() + " for requested " + expectedHostScale);
                }

                verifyHostKeyMapping();

                runtime.dispatchHostSoftKey(Frame.SOFT_KEY_1, Display.KEY_PRESSED_EVENT);
                runtime.dispatchHostSoftKey(Frame.SOFT_KEY_1, Display.KEY_RELEASED_EVENT);
                runtime.dispatchHostSoftKey(Frame.SOFT_KEY_2, Display.KEY_PRESSED_EVENT);
                runtime.dispatchHostSoftKey(Frame.SOFT_KEY_2, Display.KEY_RELEASED_EVENT);
                flushEdt();
                // UI callbacks run on the application event executor, independently of Swing.
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
                while (canvas.events().size() < 4 && System.nanoTime() < deadline) Thread.sleep(5);

                List<String> expectedEvents = List.of(
                        "0:21",
                        "1:21",
                        "0:22",
                        "1:22"
                );
                if (!expectedEvents.equals(canvas.events())) {
                    throw new IllegalStateException("Unexpected soft-key dispatch sequence: " + canvas.events());
                }

                System.out.println("configuredHostScale=" + (configuredScale == null ? "<default>" : configuredScale));
                System.out.println("resolvedHostScale=" + expectedHostScale);
                System.out.println("viewport=" + viewportWidth + "x" + viewportHeight);
                System.out.println("enabledSize=" + enabledSize.width + "x" + enabledSize.height);
                System.out.println("disabledSize=" + disabledSize.width + "x" + disabledSize.height);
                System.out.println("hostKeyMap=A:" + Frame.SOFT_KEY_1 + ",S:" + Frame.SOFT_KEY_2 + ",D:-1");
                System.out.println("softKeyEvents=" + canvas.events());
            } finally {
                runtime.shutdown();
            }
        } finally {
            if (previousHostScale == null) {
                System.clearProperty(opendoja.host.OpenDoJaLaunchArgs.HOST_SCALE);
            } else {
                System.setProperty(opendoja.host.OpenDoJaLaunchArgs.HOST_SCALE, previousHostScale);
            }
        }
    }

    private static Dimension hostPreferredSize(DoJaRuntime runtime) throws Exception {
        flushEdt();
        Field hostPanelField = DoJaRuntime.class.getDeclaredField("hostPanel");
        hostPanelField.setAccessible(true);
        Object hostPanel = hostPanelField.get(runtime);
        return ((java.awt.Component) hostPanel).getPreferredSize();
    }

    private static void verifyEnabledLayout(DoJaRuntime runtime, Rectangle expectedScreenArea,
                                            Rectangle expectedDrawArea) throws Exception {
        Field rendererField = DoJaRuntime.class.getDeclaredField("externalFrameRenderer");
        rendererField.setAccessible(true);
        Object renderer = rendererField.get(runtime);
        Method layoutFor = renderer.getClass().getDeclaredMethod("layoutFor", int.class, int.class, int.class);
        layoutFor.setAccessible(true);
        Object layout = layoutFor.invoke(renderer, runtime.displayWidth(), runtime.displayHeight(), runtime.hostScale());
        Method screenAreaMethod = layout.getClass().getDeclaredMethod("screenArea");
        screenAreaMethod.setAccessible(true);
        Method drawAreaMethod = layout.getClass().getDeclaredMethod("drawArea");
        drawAreaMethod.setAccessible(true);
        Rectangle screenArea = (Rectangle) screenAreaMethod.invoke(layout);
        Rectangle drawArea = (Rectangle) drawAreaMethod.invoke(layout);
        if (!expectedScreenArea.equals(screenArea)) {
            throw new IllegalStateException("Unexpected screen area: " + screenArea + " expected " + expectedScreenArea);
        }
        if (!expectedDrawArea.equals(drawArea)) {
            throw new IllegalStateException("Unexpected draw area: " + drawArea + " expected " + expectedDrawArea);
        }
    }

    private static void flushEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
        });
    }

    private static void verifyHostKeyMapping() throws Exception {
        Method mapHostSoftKey = DoJaRuntime.class.getDeclaredMethod("mapHostSoftKey", int.class);
        mapHostSoftKey.setAccessible(true);
        int left = (int) mapHostSoftKey.invoke(null, KeyEvent.VK_A);
        int right = (int) mapHostSoftKey.invoke(null, KeyEvent.VK_S);
        int disabled = (int) mapHostSoftKey.invoke(null, KeyEvent.VK_D);
        if (left != Frame.SOFT_KEY_1 || right != Frame.SOFT_KEY_2 || disabled != -1) {
            throw new IllegalStateException("Unexpected host key mapping A=" + left + " S=" + right + " D=" + disabled);
        }
    }

    public static final class ProbeApplication extends com.nttdocomo.ui.IApplication {
        @Override
        public void start() {
        }
    }

    static final class ProbeCanvas extends Canvas {
        private final List<String> events = new ArrayList<>();

        @Override
        public void paint(Graphics g) {
            g.clearRect(0, 0, Display.getWidth(), Display.getHeight());
        }

        @Override
        public synchronized void processEvent(int type, int param) {
            events.add(type + ":" + param);
        }

        synchronized List<String> events() {
            return List.copyOf(events);
        }
    }
}
