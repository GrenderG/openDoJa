package opendoja.host;

import com.nttdocomo.opt.ui.PhoneSystem2;
import com.nttdocomo.ui.Canvas;
import com.nttdocomo.ui.Display;
import com.nttdocomo.ui.Frame;
import com.nttdocomo.ui.Graphics;
import com.nttdocomo.ui.IApplication;

import javax.swing.AbstractAction;
import javax.swing.ButtonGroup;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.Timer;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.MouseInfo;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

public final class DoJaRuntime {
    public static final int MIN_HOST_SCALE = 1;
    public static final int MAX_HOST_SCALE = 4;
    private static final long LEGACY_FRAME_PACE_NANOS = 10_000_000L;

    private static final ThreadLocal<LaunchConfig> PREPARED_LAUNCH = new ThreadLocal<>();
    private static final boolean TRACE_EVENTS = opendoja.host.OpenDoJaLaunchArgs.getBoolean(opendoja.host.OpenDoJaLaunchArgs.TRACE_EVENTS);
    private static final long MINIMUM_SELECT_PRESS_NANOS =
            java.lang.Math.max(0L, opendoja.host.OpenDoJaLaunchArgs.getLong(opendoja.host.OpenDoJaLaunchArgs.INPUT_MINIMUM_SELECT_PRESS_MS)) * 1_000_000L;
    // A short release debounce collapses desktop auto-repeat release/press pairs into a stable hold.
    private static final int KEY_REPEAT_RELEASE_DEBOUNCE_MS =
            java.lang.Math.max(0, opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.INPUT_KEY_REPEAT_RELEASE_DEBOUNCE_MS));
    private static volatile DoJaRuntime current;

    private final LaunchConfig config;
    private final boolean legacyFramePacingEnabled;
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
    // Runtime-posted callbacks let host-side services feed work back onto the
    // same execution model that DoJa titles already use for paint/event code.
    private final ConcurrentLinkedQueue<Runnable> applicationCallbacks = new ConcurrentLinkedQueue<>();
    private final ThreadLocal<Boolean> drainingApplicationCallbacks =
            ThreadLocal.withInitial(() -> false);
    private final AtomicBoolean renderQueued = new AtomicBoolean();
    private final Object renderMonitor = new Object();
    private final Set<AutoCloseable> shutdownResources = ConcurrentHashMap.newKeySet();
    private final HostPanel hostPanel;
    private final ExternalFrameRenderer externalFrameRenderer;
    private final ScratchpadStorage scratchpadStorage;
    private volatile int hostScale;
    private volatile IApplication application;
    private JFrame frameWindow;
    private Frame currentFrame;
    private volatile Canvas presentedCanvas;
    private volatile BufferedImage presentedFrame;
    private volatile int keypadState;
    private volatile long selectLatchedUntilNanos;
    private volatile Thread renderThread;
    private volatile long renderRequestedGeneration;
    private volatile long renderCompletedGeneration;
    private volatile long nextLegacyRenderAtNanos;
    private String previousMicroeditionPlatform;
    private String previousMicroeditionProfiles;
    private boolean restoreMicroeditionPlatform;
    private boolean restoreMicroeditionProfiles;

    private DoJaRuntime(LaunchConfig config) {
        this.config = config;
        DoJaProfile profile = DoJaProfile.fromParametersOrDocumentedDeviceIdentity(config.parameters());
        this.legacyFramePacingEnabled = profile.isKnown() && profile.isBefore(4, 0);
        this.hostScale = resolveHostScale(config);
        this.externalFrameRenderer = new ExternalFrameRenderer(
                resolveExternalFrameEnabled(config),
                config.statusBarIconDevice(),
                config.iAppliType());
        this.scratchpadStorage = new ScratchpadStorage(
                config.scratchpadRoot(),
                config.scratchpadPackedFile(),
                config.scratchpadSizes());
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

    public static DoJaRuntime bootstrap(LaunchConfig config) {
        DoJaRuntime runtime = new DoJaRuntime(config);
        runtime.prepareScratchpadStorage();
        runtime.createWindowIfPossible();
        current = runtime;
        runtime.applyLaunchSystemProperties();
        PhoneSystem2.resetRuntimeDefaults();
        return runtime;
    }

    public static void bindPreparedApplication(IApplication application) {
        DoJaRuntime runtime = current;
        LaunchConfig prepared = PREPARED_LAUNCH.get();
        if (runtime == null || prepared == null || !prepared.applicationClass().isInstance(application)) {
            return;
        }
        runtime.attachApplication(application);
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

    public void postApplicationCallback(Runnable callback) {
        if (callback == null || shutdown.get()) {
            return;
        }
        applicationCallbacks.add(callback);
        Frame frame = currentFrame;
        if (frame instanceof Canvas canvas) {
            // Direct-canvas titles commonly drive their own synchronized paint
            // loop. Request a render so the callback is drained on that app
            // path instead of on an unrelated host thread.
            requestRender(canvas);
            return;
        }
        // Non-canvas frames still need a host-side wakeup to service the queue.
        SwingUtilities.invokeLater(this::drainApplicationCallbacks);
    }

    public void drainApplicationCallbacks() {
        if (shutdown.get() || drainingApplicationCallbacks.get()) {
            return;
        }
        drainingApplicationCallbacks.set(true);
        try {
            Runnable callback;
            while ((callback = applicationCallbacks.poll()) != null) {
                try {
                    callback.run();
                } catch (Throwable throwable) {
                    // Match the rest of the host runtime: callbacks are best
                    // effort and should not tear down the scheduler thread.
                    OpenDoJaLog.error(DoJaRuntime.class,
                            "Unhandled application callback failure", throwable);
                }
            }
        } finally {
            drainingApplicationCallbacks.set(false);
        }
    }

    public IApplication application() {
        return application;
    }

    public void attachApplication(IApplication application) {
        Objects.requireNonNull(application, "application");
        IApplication attached = this.application;
        if (attached == application) {
            return;
        }
        if (attached != null) {
            throw new IllegalStateException("Runtime already attached to " + attached.getClass().getName());
        }
        this.application = application;
    }

    public int displayWidth() {
        return config.width();
    }

    public int displayHeight() {
        return config.height();
    }

    public int hostScale() {
        return hostScale;
    }

    public void setHostScale(int scale) {
        int normalized = normalizeHostScale(scale);
        if (hostScale == normalized) {
            return;
        }
        hostScale = normalized;
        if (SwingUtilities.isEventDispatchThread()) {
            hostPanel.refreshPreferredSize();
            repackWindow();
            repaintWindow();
        } else {
            SwingUtilities.invokeLater(() -> {
                hostPanel.refreshPreferredSize();
                repackWindow();
                repaintWindow();
            });
        }
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
        IApplication app = application;
        if (app == null) {
            throw new IllegalStateException("No application attached to runtime");
        }
        app.start();
    }

    public void shutdown() {
        shutdown(config.exitOnShutdown());
    }

    void abortLaunch() {
        shutdown(false);
    }

    private void shutdown(boolean exitProcess) {
        if (!shutdown.compareAndSet(false, true)) {
            return;
        }
        closeShutdownResources();
        scheduler.shutdownNow();
        renderExecutor.shutdownNow();
        if (frameWindow != null) {
            SwingUtilities.invokeLater(() -> frameWindow.dispose());
        }
        restoreLaunchSystemProperties();
        if (current == this) {
            current = null;
        }
        shutdownLatch.countDown();
        if (exitProcess) {
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
            requestRender(canvas);
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
        long requestedGeneration = 0L;
        boolean waitForLegacyFrame = shouldWaitForLegacyFrame(canvas);
        if (waitForLegacyFrame) {
            requestedGeneration = markRenderRequested();
        }
        Runnable paintTask = () -> {
            try {
                waitForLegacyFrameSlot();
                synchronized (canvas) {
                    // Drain queued callbacks immediately before application
                    // paint code so titles observe them at a normal frame
                    // boundary, not from host input/audio threads.
                    drainApplicationCallbacks();
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
            } catch (Throwable throwable) {
                OpenDoJaLog.error(DoJaRuntime.class, "Unhandled canvas paint failure", throwable);
            } finally {
                LegacyDrawFlagState drawFlagState = inspectLegacyDrawFlag();
                boolean replayLegacyRender = shouldReplayLegacyRender(canvas, drawFlagState);
                recoverLegacyDrawFlag(drawFlagState);
                renderQueued.set(false);
                finishRender();
                if (replayLegacyRender) {
                    requestRender(canvas);
                }
            }
        };
        if (shutdown.get()) {
            return;
        }
        if (renderQueued.compareAndSet(false, true)) {
            renderExecutor.execute(paintTask);
        }
        if (waitForLegacyFrame) {
            awaitRenderCompletion(requestedGeneration);
        }
    }

    public void repaintWindow() {
        if (frameWindow != null) {
            hostPanel.repaint();
        }
    }

    public boolean isExternalFrameEnabled() {
        return externalFrameRenderer.enabled();
    }

    public void setExternalFrameEnabled(boolean enabled) {
        if (externalFrameRenderer.enabled() == enabled) {
            return;
        }
        externalFrameRenderer.setEnabled(enabled);
        if (SwingUtilities.isEventDispatchThread()) {
            hostPanel.refreshPreferredSize();
            repackWindow();
        } else {
            SwingUtilities.invokeLater(() -> {
                hostPanel.refreshPreferredSize();
                repackWindow();
            });
        }
        repaintWindow();
    }

    public void addExternalFrameOverlay(HostOverlayRenderer overlay) {
        externalFrameRenderer.addOverlay(overlay);
        repaintWindow();
    }

    public void removeExternalFrameOverlay(HostOverlayRenderer overlay) {
        externalFrameRenderer.removeOverlay(overlay);
        repaintWindow();
    }

    public Path scratchpadRoot() {
        return config.scratchpadRoot();
    }

    public Path scratchpadPackedFile() {
        return config.scratchpadPackedFile();
    }

    public LaunchConfig.IAppliType iAppliType() {
        return config.iAppliType();
    }

    public InputStream openScratchpadInput(int index, long position, long length) throws IOException {
        return scratchpadStorage.openInput(index, position, length);
    }

    public OutputStream openScratchpadOutput(int index, long position, long length) throws IOException {
        return scratchpadStorage.openOutput(index, position, length);
    }

    public int keypadState() {
        int state = keypadState;
        int selectMask = keyMask(Display.KEY_SELECT);
        if (MINIMUM_SELECT_PRESS_NANOS <= 0L) {
            return state;
        }
        if (selectLatchedUntilNanos > System.nanoTime()) {
            return state | selectMask;
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

    public void dispatchHostSoftKey(int softKey, int eventType) {
        Frame frame = currentFrame;
        if (frame == null) {
            return;
        }
        Runnable eventTask = () -> {
            if (TRACE_EVENTS) {
                OpenDoJaLog.debug(DoJaRuntime.class,
                        () -> "soft-key event type=" + eventType + " key=" + softKey + " frame=" + frame.getClass().getName());
            }
            frame.processSoftKeyEvent(eventType, softKey);
            repaintWindow();
        };
        if (SwingUtilities.isEventDispatchThread()) {
            eventTask.run();
        } else {
            SwingUtilities.invokeLater(eventTask);
        }
    }

    public InputStream openResourceStream(String path) throws IOException {
        return openResourceStream(path, config, config.applicationClass().getClassLoader());
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

    private boolean shouldWaitForLegacyFrame(Canvas canvas) {
        return legacyFramePacingEnabled
                && currentFrame == canvas
                && !shutdown.get()
                && Thread.currentThread() != renderThread
                && !Thread.holdsLock(canvas)
                && !SwingUtilities.isEventDispatchThread();
    }

    private long markRenderRequested() {
        synchronized (renderMonitor) {
            return ++renderRequestedGeneration;
        }
    }

    private void waitForLegacyFrameSlot() {
        if (!legacyFramePacingEnabled) {
            renderThread = Thread.currentThread();
            return;
        }
        renderThread = Thread.currentThread();
        long target = nextLegacyRenderAtNanos;
        if (target <= 0L) {
            return;
        }
        long remaining = target - System.nanoTime();
        if (remaining > 0L) {
            LockSupport.parkNanos(remaining);
        }
    }

    private void finishRender() {
        if (!legacyFramePacingEnabled) {
            return;
        }
        synchronized (renderMonitor) {
            renderCompletedGeneration = java.lang.Math.max(renderCompletedGeneration, renderRequestedGeneration);
            nextLegacyRenderAtNanos = System.nanoTime() + LEGACY_FRAME_PACE_NANOS;
            renderMonitor.notifyAll();
        }
    }

    private void awaitRenderCompletion(long requestedGeneration) {
        if (!legacyFramePacingEnabled || requestedGeneration <= 0L) {
            return;
        }
        synchronized (renderMonitor) {
            while (!shutdown.get() && renderCompletedGeneration < requestedGeneration) {
                try {
                    renderMonitor.wait(20L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void recoverLegacyDrawFlag(LegacyDrawFlagState drawFlagState) {
        if (drawFlagState == null || drawFlagState.value() != 3) {
            return;
        }
        try {
            drawFlagState.field().setInt(null, 0);
            OpenDoJaLog.debug(DoJaRuntime.class,
                    "Recovered wedged legacy draw flag on " + drawFlagState.ownerName());
        } catch (IllegalAccessException e) {
            OpenDoJaLog.error(DoJaRuntime.class,
                    "Failed to reset legacy draw flag on " + drawFlagState.ownerName(), e);
        }
    }

    private boolean shouldReplayLegacyRender(Canvas canvas, LegacyDrawFlagState drawFlagState) {
        if (shutdown.get() || currentFrame != canvas) {
            return false;
        }
        return drawFlagState != null && drawFlagState.value() == 2;
    }

    private LegacyDrawFlagState inspectLegacyDrawFlag() {
        IApplication currentApplication = application;
        if (currentApplication == null) {
            return null;
        }
        Class<?> type = currentApplication.getClass();
        while (type != null) {
            try {
                java.lang.reflect.Field drawFlag = type.getDeclaredField("m_drawFlag");
                if (!java.lang.reflect.Modifier.isStatic(drawFlag.getModifiers())
                        || drawFlag.getType() != int.class) {
                    return null;
                }
                drawFlag.setAccessible(true);
                return new LegacyDrawFlagState(drawFlag, drawFlag.getInt(null), type.getName());
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                OpenDoJaLog.error(DoJaRuntime.class,
                        "Failed to inspect legacy draw flag on " + type.getName(), e);
                return null;
            }
        }
        return null;
    }

    private record LegacyDrawFlagState(java.lang.reflect.Field field, int value, String ownerName) {
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
        throw new FileNotFoundException("Resource not found: " + path);
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

    private void prepareScratchpadStorage() {
        try {
            scratchpadStorage.initialize();
        } catch (IOException e) {
            Path packedFile = scratchpadPackedFile();
            if (packedFile != null) {
                throw new IllegalStateException("Failed to prepare packed scratchpad file " + packedFile, e);
            }
            throw new IllegalStateException("Failed to create scratchpad root " + scratchpadRoot(), e);
        }
    }

    private void createWindowIfPossible() {
        if (!shouldCreateHostWindow(GraphicsEnvironment.isHeadless(), shutdown.get())) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            // VerifyError fallback relaunch can abort startup before this queued EDT task runs.
            // If shutdown already started, do not create the stale first window at all.
            if (!shouldCreateHostWindow(false, shutdown.get())) {
                return;
            }
            JFrame window = new JFrame(config.title());
            window.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            window.getContentPane().setBackground(Color.BLACK);
            window.setBackground(Color.BLACK);
            window.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosed(java.awt.event.WindowEvent e) {
                    shutdown();
                }
            });
            window.add(hostPanel);
            window.setResizable(false);
            window.pack();
            // GNOME/OpenJDK can ignore setLocationByPlatform(true) for non-resizable frames,
            // which leaves the host window near the primary-display origin instead of on the active screen.
            //window.setLocationByPlatform(true);
            centerOnCurrentScreen(window);

            frameWindow = window;
            if (!shouldCreateHostWindow(false, shutdown.get())) {
                window.dispose();
                return;
            }
            window.setVisible(true);
            hostPanel.requestFocusInWindow();
        });
    }

    static boolean shouldCreateHostWindow(boolean headless, boolean shutdownRequested) {
        return !headless && !shutdownRequested;
    }

    private static void centerOnCurrentScreen(Window window) {
        PointerInfo pointerInfo = MouseInfo.getPointerInfo();
        GraphicsConfiguration graphicsConfiguration = pointerInfo != null
                ? pointerInfo.getDevice().getDefaultConfiguration()
                : window.getGraphicsConfiguration();
        if (graphicsConfiguration == null) {
            return;
        }

        Rectangle bounds = graphicsConfiguration.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(graphicsConfiguration);
        int usableX = bounds.x + insets.left;
        int usableY = bounds.y + insets.top;
        int usableWidth = bounds.width - insets.left - insets.right;
        int usableHeight = bounds.height - insets.top - insets.bottom;

        Dimension size = window.getSize();
        int x = usableX + (usableWidth - size.width) / 2;
        int y = usableY + (usableHeight - size.height) / 2;
        window.setLocation(x, y);
    }

    private static int keyMask(int keyCode) {
        if (keyCode < 0 || keyCode > 30) {
            return 0;
        }
        return 1 << keyCode;
    }

    private static int resolveHostScale(LaunchConfig config) {
        return normalizeHostScale(opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.HOST_SCALE));
    }

    static int normalizeHostScale(int scale) {
        return Math.clamp(scale, MIN_HOST_SCALE, MAX_HOST_SCALE);
    }

    private static boolean resolveExternalFrameEnabled(LaunchConfig config) {
        return opendoja.host.OpenDoJaLaunchArgs.getBoolean(
                opendoja.host.OpenDoJaLaunchArgs.EXTERNAL_FRAME,
                config == null || config.externalFrameEnabled());
    }

    private void repackWindow() {
        if (frameWindow != null) {
            Dimension preferred = hostPreferredSizeForScale(hostScale);
            hostPanel.setSize(preferred);
            if (!frameWindow.isDisplayable()) {
                frameWindow.pack();
            } else {
                java.awt.Insets insets = frameWindow.getInsets();
                frameWindow.setSize(
                        preferred.width + insets.left + insets.right,
                        preferred.height + insets.top + insets.bottom);
                frameWindow.validate();
            }
        }
    }

    private Dimension hostPreferredSizeForScale(int scale) {
        return externalFrameRenderer.layoutFor(
                displayWidth(),
                displayHeight(),
                normalizeHostScale(scale)).preferredSize();
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

    private void applyLaunchSystemProperties() {
        previousMicroeditionPlatform = OpenDoJaLaunchArgs.get("microedition.platform", null);
        String launchPlatform = launchMicroeditionPlatform();
        if ((previousMicroeditionPlatform == null || previousMicroeditionPlatform.isBlank())
                && launchPlatform != null
                && !launchPlatform.isBlank()) {
            System.setProperty("microedition.platform", launchPlatform);
            restoreMicroeditionPlatform = true;
        }
        previousMicroeditionProfiles = OpenDoJaLaunchArgs.get("microedition.profiles", null);
        String launchProfiles = launchMicroeditionProfiles();
        if ((previousMicroeditionProfiles == null || previousMicroeditionProfiles.isBlank())
                && launchProfiles != null
                && !launchProfiles.isBlank()) {
            System.setProperty("microedition.profiles", launchProfiles);
            restoreMicroeditionProfiles = true;
        }
    }

    private void restoreLaunchSystemProperties() {
        if (restoreMicroeditionPlatform) {
            if (previousMicroeditionPlatform == null) {
                System.clearProperty("microedition.platform");
            } else {
                System.setProperty("microedition.platform", previousMicroeditionPlatform);
            }
            restoreMicroeditionPlatform = false;
            previousMicroeditionPlatform = null;
        }
        if (restoreMicroeditionProfiles) {
            if (previousMicroeditionProfiles == null) {
                System.clearProperty("microedition.profiles");
            } else {
                System.setProperty("microedition.profiles", previousMicroeditionProfiles);
            }
            restoreMicroeditionProfiles = false;
            previousMicroeditionProfiles = null;
        }
    }

    private String launchMicroeditionPlatform() {
        String override = OpenDoJaLaunchArgs.microeditionPlatformOverride();
        if (!override.isBlank()) {
            return override;
        }
        String targetDevice = config.parameters().get("TargetDevice");
        if (targetDevice != null && !targetDevice.isBlank()) {
            return targetDevice.trim();
        }
        return "openDoJa";
    }

    private String launchMicroeditionProfiles() {
        DoJaProfile resolved = DoJaProfile.fromParametersOrDocumentedDeviceIdentity(config.parameters());
        if (resolved.isKnown()) {
            return resolved.toString();
        }
        return "";
    }

    private String launchDeviceIdentity() {
        String targetDevice = config.parameters().get("TargetDevice");
        if (targetDevice != null && !targetDevice.isBlank()) {
            return targetDevice.trim();
        }
        String packageUrl = config.parameters().get("PackageURL");
        if (packageUrl != null && !packageUrl.isBlank()) {
            return packageUrl.trim();
        }
        return null;
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

    private static int mapHostSoftKey(int awtKeyCode) {
        return switch (awtKeyCode) {
            case KeyEvent.VK_A -> Frame.SOFT_KEY_1;
            case KeyEvent.VK_S -> Frame.SOFT_KEY_2;
            default -> -1;
        };
    }

    private static final class HostPanel extends JPanel {
        private final DoJaRuntime runtime;
        private final DesktopKeyInputAdapter keyInputAdapter;
        private final DesktopKeyInputAdapter softKeyInputAdapter;

        private HostPanel(DoJaRuntime runtime) {
            this.runtime = runtime;
            this.keyInputAdapter = new DesktopKeyInputAdapter(this::scheduleRelease, runtime::dispatchSyntheticKey,
                    KEY_REPEAT_RELEASE_DEBOUNCE_MS);
            this.softKeyInputAdapter = new DesktopKeyInputAdapter(this::scheduleRelease, runtime::dispatchHostSoftKey,
                    KEY_REPEAT_RELEASE_DEBOUNCE_MS);
            refreshPreferredSize();
            setBackground(Color.BLACK);
            setOpaque(true);
            setFocusable(true);
            installHostScalePopup();
            addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    keyInputAdapter.releaseAll();
                    softKeyInputAdapter.releaseAll();
                }
            });
            addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    dispatchKey(e);
                }

                @Override
                public void keyReleased(KeyEvent e) {
                    dispatchKey(e);
                }

                private void dispatchKey(KeyEvent event) {
                    int softKey = mapHostSoftKey(event.getKeyCode());
                    if (softKey >= 0) {
                        if (event.getID() == KeyEvent.KEY_PRESSED) {
                            softKeyInputAdapter.keyPressed(softKey);
                        } else if (event.getID() == KeyEvent.KEY_RELEASED) {
                            softKeyInputAdapter.keyReleased(softKey);
                        }
                        event.consume();
                        return;
                    }
                    int dojaKey = runtime.mapKeyCode(event.getKeyCode());
                    if (dojaKey < 0) {
                        return;
                    }
                    if (event.getID() == KeyEvent.KEY_PRESSED) {
                        keyInputAdapter.keyPressed(dojaKey);
                    } else if (event.getID() == KeyEvent.KEY_RELEASED) {
                        keyInputAdapter.keyReleased(dojaKey);
                    }
                    event.consume();
                }
            });
        }

        private DesktopKeyInputAdapter.PendingRelease scheduleRelease(int delayMillis, Runnable task) {
            Timer timer = new Timer(java.lang.Math.max(0, delayMillis), e -> task.run());
            timer.setRepeats(false);
            timer.start();
            return timer::stop;
        }

        private void installHostScalePopup() {
            MouseAdapter popupListener = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    maybeShowPopup(event);
                }

                @Override
                public void mouseReleased(MouseEvent event) {
                    maybeShowPopup(event);
                }
            };
            addMouseListener(popupListener);
        }

        private void maybeShowPopup(MouseEvent event) {
            if (!event.isPopupTrigger()) {
                return;
            }
            JPopupMenu menu = buildHostScalePopup();
            menu.show(event.getComponent(), event.getX(), event.getY());
        }

        private JPopupMenu buildHostScalePopup() {
            JPopupMenu menu = new JPopupMenu();
            ButtonGroup group = new ButtonGroup();
            for (int scale = MIN_HOST_SCALE; scale <= MAX_HOST_SCALE; scale++) {
                int selectedScale = scale;
                JRadioButtonMenuItem item = new JRadioButtonMenuItem(new AbstractAction((scale * 100) + "%") {
                    @Override
                    public void actionPerformed(ActionEvent event) {
                        runtime.setHostScale(selectedScale);
                        HostPanel.this.requestFocusInWindow();
                    }
                });
                item.setSelected(runtime.hostScale() == scale);
                group.add(item);
                menu.add(item);
            }
            return menu;
        }

        private void refreshPreferredSize() {
            ExternalFrameLayout layout = runtime.externalFrameRenderer.layoutFor(
                    runtime.displayWidth(), runtime.displayHeight(), runtime.hostScale());
            Dimension preferred = layout.preferredSize();
            setPreferredSize(preferred);
            setMinimumSize(preferred);
            setMaximumSize(preferred);
            setSize(preferred);
            revalidate();
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            runtime.externalFrameRenderer.paint(g2, runtime.currentFrame, runtime.presentedFrame,
                    runtime.displayWidth(), runtime.displayHeight(), runtime.hostScale());
            g2.dispose();
        }
    }
}
