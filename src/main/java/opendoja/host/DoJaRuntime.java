package opendoja.host;

import com.nttdocomo.ui.Canvas;
import com.nttdocomo.ui.Display;
import com.nttdocomo.ui.Frame;
import com.nttdocomo.ui.Graphics;
import com.nttdocomo.ui.IApplication;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public final class DoJaRuntime {
    private static final ThreadLocal<LaunchConfig> PREPARED_LAUNCH = new ThreadLocal<>();
    private static final boolean TRACE_EVENTS = Boolean.getBoolean("opendoja.traceEvents");
    private static final long MINIMUM_SELECT_PRESS_NANOS =
            java.lang.Math.max(0L, Long.getLong("opendoja.input.minimumSelectPressMs", 75L)) * 1_000_000L;
    private static volatile DoJaRuntime current;

    private final LaunchConfig config;
    private final IApplication application;
    private final AtomicBoolean shutdown = new AtomicBoolean();
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);
    private final ReentrantLock surfaceLock = new ReentrantLock(true);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "openDoJa-runtime");
        thread.setDaemon(true);
        return thread;
    });
    private final java.util.concurrent.ExecutorService renderExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "openDoJa-render");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean renderQueued = new AtomicBoolean();
    private final Set<AutoCloseable> shutdownResources = ConcurrentHashMap.newKeySet();
    private final HostPanel hostPanel;
    private JFrame frameWindow;
    private Frame currentFrame;
    private volatile Canvas presentedCanvas;
    private volatile BufferedImage presentedFrame;
    private volatile int keypadState;
    private volatile long selectLatchedUntilNanos;

    private DoJaRuntime(LaunchConfig config, IApplication application) {
        this.config = config;
        this.application = application;
        this.hostPanel = new HostPanel(this);
    }

    public static void prepareLaunch(LaunchConfig config) {
        PREPARED_LAUNCH.set(config);
    }

    public static LaunchConfig consumePreparedLaunch() {
        return PREPARED_LAUNCH.get();
    }

    public static LaunchConfig peekPreparedLaunch() {
        return PREPARED_LAUNCH.get();
    }

    public static void clearPreparedLaunch() {
        PREPARED_LAUNCH.remove();
    }

    public static DoJaRuntime bootstrap(LaunchConfig config, IApplication application) {
        DoJaRuntime runtime = new DoJaRuntime(config, application);
        current = runtime;
        runtime.createScratchpadRoot();
        runtime.createWindowIfPossible();
        return runtime;
    }

    public static DoJaRuntime current() {
        return current;
    }

    public LaunchConfig config() {
        return config;
    }

    public ScheduledExecutorService scheduler() {
        return scheduler;
    }

    public ReentrantLock surfaceLock() {
        return surfaceLock;
    }

    public IApplication application() {
        return application;
    }

    public int displayWidth() {
        return config.width();
    }

    public int displayHeight() {
        return config.height();
    }

    public String sourceUrl() {
        return config.sourceUrl();
    }

    public Map<String, String> parameters() {
        return config.parameters();
    }

    public int launchType() {
        return config.launchType();
    }

    public String[] args() {
        return config.args();
    }

    public void startApplication() {
        application.start();
    }

    public void shutdown() {
        if (!shutdown.compareAndSet(false, true)) {
            return;
        }
        closeShutdownResources();
        scheduler.shutdownNow();
        renderExecutor.shutdownNow();
        if (frameWindow != null) {
            SwingUtilities.invokeLater(() -> frameWindow.dispose());
        }
        if (current == this) {
            current = null;
        }
        shutdownLatch.countDown();
        if (config.exitOnShutdown()) {
            System.exit(0);
        }
    }

    public void awaitShutdown() throws InterruptedException {
        shutdownLatch.await();
    }

    public void registerShutdownResource(AutoCloseable resource) {
        if (resource == null || shutdown.get()) {
            if (resource != null) {
                try {
                    resource.close();
                } catch (Exception ignored) {
                }
            }
            return;
        }
        shutdownResources.add(resource);
    }

    public void unregisterShutdownResource(AutoCloseable resource) {
        if (resource != null) {
            shutdownResources.remove(resource);
        }
    }

    public void setCurrentFrame(Frame frame) {
        this.currentFrame = frame;
        if (frame instanceof Canvas canvas) {
            ensureCanvasSurface(canvas);
            if (presentedCanvas != canvas || presentedFrame == null) {
                presentedFrame = snapshotCanvasImage(canvas);
            }
            presentedCanvas = canvas;
            if (usesDirectGraphicsMode(canvas)) {
                repaintWindow();
            } else {
                requestRender(canvas);
            }
        } else {
            presentedCanvas = null;
            presentedFrame = null;
            repaintWindow();
        }
    }

    public Frame getCurrentFrame() {
        return currentFrame;
    }

    public void requestRender(Canvas canvas) {
        if (usesDirectGraphicsMode(canvas)) {
            if (canvas == currentFrame && (presentedCanvas != canvas || presentedFrame == null)) {
                presentedCanvas = canvas;
                presentedFrame = snapshotCanvasImage(canvas);
            }
            repaintWindow();
            return;
        }
        Runnable paintTask = () -> {
            try {
                synchronized (canvas) {
                    ensureCanvasSurface(canvas);
                    Graphics g = runtimeGraphics(canvas);
                    try {
                        // Runtime-driven paints must not happen on the EDT: some games keep a
                        // synchronized paint loop on their own thread and call Graphics.lock()
                        // inside that method. A separate render worker keeps window events flowing
                        // and preserves the same Canvas-monitor -> surface-lock ordering.
                        g.lock();
                        canvas.paint(g);
                    } finally {
                        g.unlock(true);
                        g.dispose();
                    }
                }
            } finally {
                renderQueued.set(false);
            }
        };
        if (shutdown.get()) {
            return;
        }
        if (renderQueued.compareAndSet(false, true)) {
            renderExecutor.execute(paintTask);
        }
    }

    public void repaintWindow() {
        if (frameWindow != null) {
            hostPanel.repaint();
        }
    }

    public Path scratchpadRoot() {
        return config.scratchpadRoot();
    }

    public Path scratchpadFile(int index) {
        return scratchpadRoot().resolve("sp-" + index + ".bin");
    }

    public int keypadState() {
        int state = keypadState;
        if (MINIMUM_SELECT_PRESS_NANOS <= 0L || (state & keyMask(Display.KEY_SELECT)) != 0) {
            return state;
        }
        if (selectLatchedUntilNanos > System.nanoTime()) {
            return state | keyMask(Display.KEY_SELECT);
        }
        return state;
    }

    public void dispatchTimerEvent(Canvas canvas, int param) {
        if (shutdown.get()) {
            return;
        }
        if (TRACE_EVENTS) {
            OpenDoJaLog.debug(DoJaRuntime.class, () -> "timer event canvas=" + canvas.getClass().getName() + " param=" + param);
        }
        canvas.processEvent(Display.TIMER_EXPIRED_EVENT, param);
    }

    public void dispatchSyntheticKey(int dojaKey, int eventType) {
        int mask = keyMask(dojaKey);
        long now = System.nanoTime();
        if (eventType == Display.KEY_PRESSED_EVENT) {
            keypadState |= mask;
            if (dojaKey == Display.KEY_SELECT && MINIMUM_SELECT_PRESS_NANOS > 0L) {
                // Some DoJa games derive edge-triggered confirm input from polled keypad-state snapshots
                // rather than from KEY_PRESSED_EVENT callbacks. If a desktop tap begins and ends between
                // two polls, the sampled state is 0 at both reads and the edge disappears completely.
                // Keep KEY_SELECT visible for a short minimum dwell window so a fast desktop confirm tap
                // survives at least one poll without changing long-hold behavior.
                selectLatchedUntilNanos = now + MINIMUM_SELECT_PRESS_NANOS;
            }
        } else if (eventType == Display.KEY_RELEASED_EVENT) {
            keypadState &= ~mask;
        }
        if (!(currentFrame instanceof Canvas canvas)) {
            return;
        }
        Runnable eventTask = () -> {
            if (TRACE_EVENTS) {
                OpenDoJaLog.debug(DoJaRuntime.class, () -> "key event type=" + eventType + " key=" + dojaKey + " canvas=" + canvas.getClass().getName());
            }
            canvas.processEvent(eventType, dojaKey);
            repaintWindow();
        };
        if (SwingUtilities.isEventDispatchThread()) {
            eventTask.run();
        } else {
            SwingUtilities.invokeLater(eventTask);
        }
    }

    public InputStream openResourceStream(String path) throws IOException {
        return openResourceStream(path, config, application.getClass().getClassLoader());
    }

    public static InputStream openLaunchResourceStream(String path) throws IOException {
        DoJaRuntime runtime = current;
        if (runtime != null) {
            return runtime.openResourceStream(path);
        }
        LaunchConfig prepared = peekPreparedLaunch();
        ClassLoader loader = prepared == null ? Thread.currentThread().getContextClassLoader()
                : prepared.applicationClass().getClassLoader();
        return openResourceStream(path, prepared, loader);
    }

    public void notifySurfaceFlush(Canvas canvas, BufferedImage frame) {
        // Some games start a render thread from the Canvas constructor and flush direct-draw frames
        // before Display.setCurrent(canvas) runs. Preserve that latest frame so it is still visible
        // once the Canvas becomes current, instead of dropping it as "not current yet".
        if (canvas == currentFrame || currentFrame == null) {
            presentedCanvas = canvas;
            presentedFrame = frame == null ? snapshotCanvasImage(canvas) : frame;
            repaintWindow();
        }
    }

    private void ensureCanvasSurface(Canvas canvas) {
        invokeCanvasMethod(canvas, "ensureSurface", new Class<?>[]{int.class, int.class}, displayWidth(), displayHeight());
    }

    private BufferedImage getCanvasImage(Canvas canvas) {
        surfaceLock.lock();
        try {
            Object surface = invokeCanvasMethod(canvas, "surface", new Class<?>[0]);
            if (surface == null) {
                return null;
            }
            try {
                Method imageMethod = surface.getClass().getDeclaredMethod("image");
                imageMethod.setAccessible(true);
                return (BufferedImage) imageMethod.invoke(surface);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException("Failed to access canvas surface image", e);
            }
        } finally {
            surfaceLock.unlock();
        }
    }

    private BufferedImage snapshotCanvasImage(Canvas canvas) {
        BufferedImage image = getCanvasImage(canvas);
        return copyCanvasImage(image);
    }

    private boolean usesDirectGraphicsMode(Canvas canvas) {
        Object direct = invokeCanvasMethod(canvas, "directGraphicsMode", new Class<?>[0]);
        return direct instanceof Boolean value && value;
    }

    private Graphics runtimeGraphics(Canvas canvas) {
        Object graphics = invokeCanvasMethod(canvas, "runtimeGraphics", new Class<?>[0]);
        return (Graphics) Objects.requireNonNull(graphics, "Canvas.runtimeGraphics() returned null");
    }

    private BufferedImage copyCanvasImage(BufferedImage image) {
        if (image == null) {
            return null;
        }
        BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = copy.createGraphics();
        try {
            g2.drawImage(image, 0, 0, null);
        } finally {
            g2.dispose();
        }
        return copy;
    }

    private Object invokeCanvasMethod(Canvas canvas, String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = Canvas.class.getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method.invoke(canvas, args);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Failed to invoke Canvas#" + methodName, e);
        }
    }

    private static InputStream openResourceStream(String path, LaunchConfig launchConfig, ClassLoader preferredLoader) throws IOException {
        String normalized = normalizeResourcePath(path);
        if (preferredLoader != null) {
            InputStream preferred = preferredLoader.getResourceAsStream(normalized);
            if (preferred != null) {
                return toBufferedResourceStream(preferred);
            }
        }
        InputStream contextIn = Thread.currentThread().getContextClassLoader().getResourceAsStream(normalized);
        if (contextIn != null) {
            return toBufferedResourceStream(contextIn);
        }
        Path filesystemPath = Path.of(normalized);
        if (Files.exists(filesystemPath)) {
            return new ByteArrayInputStream(Files.readAllBytes(filesystemPath));
        }
        Path relativeToSource = resolveRelativeToSourceUrl(launchConfig, normalized);
        if (relativeToSource != null && Files.exists(relativeToSource)) {
            return new ByteArrayInputStream(Files.readAllBytes(relativeToSource));
        }
        throw new IOException("Resource not found: " + path);
    }

    private static InputStream toBufferedResourceStream(InputStream raw) throws IOException {
        try (InputStream in = raw) {
            // DoJa games often treat resource streams like memory-backed blobs: available() is expected
            // to be the full remaining length, and a single read(byte[]) is expected to fill that buffer.
            return new ByteArrayInputStream(in.readAllBytes());
        }
    }

    private static String normalizeResourcePath(String path) {
        String normalized = path;
        if (normalized.startsWith("resource:///")) {
            normalized = normalized.substring("resource:///".length());
        } else if (normalized.startsWith("resource://")) {
            normalized = normalized.substring("resource://".length());
        } else if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static Path resolveRelativeToSourceUrl(LaunchConfig launchConfig, String normalized) {
        if (launchConfig == null || launchConfig.sourceUrl() == null || launchConfig.sourceUrl().isBlank()) {
            return null;
        }
        try {
            URI sourceUri = URI.create(launchConfig.sourceUrl());
            if (!"file".equalsIgnoreCase(sourceUri.getScheme())) {
                return null;
            }
            Path sourcePath = Path.of(sourceUri);
            Path base = Files.isDirectory(sourcePath) ? sourcePath : sourcePath.getParent();
            if (base == null) {
                return null;
            }
            return base.resolve(normalized).normalize();
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void createScratchpadRoot() {
        try {
            Files.createDirectories(scratchpadRoot());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create scratchpad root " + scratchpadRoot(), e);
        }
    }

    private void createWindowIfPossible() {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            frameWindow = new JFrame(config.title());
            frameWindow.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            frameWindow.getContentPane().setBackground(Color.BLACK);
            frameWindow.setBackground(Color.BLACK);
            frameWindow.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosed(java.awt.event.WindowEvent e) {
                    shutdown();
                }
            });
            frameWindow.add(hostPanel);
            frameWindow.pack();
            frameWindow.setLocationByPlatform(true);
            frameWindow.setVisible(true);
            hostPanel.requestFocusInWindow();
        });
    }

    private static int keyMask(int keyCode) {
        if (keyCode < 0 || keyCode > 30) {
            return 0;
        }
        return 1 << keyCode;
    }

    private void closeShutdownResources() {
        for (AutoCloseable resource : shutdownResources.toArray(AutoCloseable[]::new)) {
            try {
                resource.close();
            } catch (Exception ignored) {
            } finally {
                shutdownResources.remove(resource);
            }
        }
    }

    private int mapKeyCode(int awtKeyCode) {
        return switch (awtKeyCode) {
            case KeyEvent.VK_0, KeyEvent.VK_NUMPAD0 -> Display.KEY_0;
            case KeyEvent.VK_1, KeyEvent.VK_NUMPAD1 -> Display.KEY_1;
            case KeyEvent.VK_2, KeyEvent.VK_NUMPAD2 -> Display.KEY_2;
            case KeyEvent.VK_3, KeyEvent.VK_NUMPAD3 -> Display.KEY_3;
            case KeyEvent.VK_4, KeyEvent.VK_NUMPAD4 -> Display.KEY_4;
            case KeyEvent.VK_5, KeyEvent.VK_NUMPAD5 -> Display.KEY_5;
            case KeyEvent.VK_6, KeyEvent.VK_NUMPAD6 -> Display.KEY_6;
            case KeyEvent.VK_7, KeyEvent.VK_NUMPAD7 -> Display.KEY_7;
            case KeyEvent.VK_8, KeyEvent.VK_NUMPAD8 -> Display.KEY_8;
            case KeyEvent.VK_9, KeyEvent.VK_NUMPAD9 -> Display.KEY_9;
            case KeyEvent.VK_ASTERISK, KeyEvent.VK_MULTIPLY -> Display.KEY_ASTERISK;
            case KeyEvent.VK_NUMBER_SIGN, KeyEvent.VK_DIVIDE -> Display.KEY_POUND;
            case KeyEvent.VK_LEFT -> Display.KEY_LEFT;
            case KeyEvent.VK_UP -> Display.KEY_UP;
            case KeyEvent.VK_RIGHT -> Display.KEY_RIGHT;
            case KeyEvent.VK_DOWN -> Display.KEY_DOWN;
            case KeyEvent.VK_ENTER, KeyEvent.VK_SPACE -> Display.KEY_SELECT;
            case KeyEvent.VK_ESCAPE, KeyEvent.VK_BACK_SPACE -> Display.KEY_CLEAR;
            case KeyEvent.VK_F1 -> Display.KEY_SOFT1;
            case KeyEvent.VK_F2 -> Display.KEY_SOFT2;
            case KeyEvent.VK_M -> Display.KEY_MENU;
            case KeyEvent.VK_C -> Display.KEY_CAMERA;
            default -> -1;
        };
    }

    private static final class HostPanel extends JPanel {
        private static final int SCALE = 2;
        private final DoJaRuntime runtime;

        private HostPanel(DoJaRuntime runtime) {
            this.runtime = runtime;
            setPreferredSize(new Dimension(runtime.displayWidth() * SCALE, runtime.displayHeight() * SCALE));
            setBackground(Color.BLACK);
            setOpaque(true);
            setFocusable(true);
            addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    dispatchKey(e, Display.KEY_PRESSED_EVENT);
                }

                @Override
                public void keyReleased(KeyEvent e) {
                    dispatchKey(e, Display.KEY_RELEASED_EVENT);
                }

                private void dispatchKey(KeyEvent event, int eventType) {
                    int dojaKey = runtime.mapKeyCode(event.getKeyCode());
                    if (dojaKey < 0) {
                        return;
                    }
                    runtime.dispatchSyntheticKey(dojaKey, eventType);
                    event.consume();
                }
            });
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2.setColor(Color.BLACK);
            g2.fillRect(0, 0, getWidth(), getHeight());
            if (runtime.currentFrame instanceof Canvas canvas) {
                BufferedImage image = runtime.presentedFrame;
                if (image != null) {
                    g2.drawImage(image, 0, 0, image.getWidth() * SCALE, image.getHeight() * SCALE, null);
                }
            }
            g2.dispose();
        }
    }
}
