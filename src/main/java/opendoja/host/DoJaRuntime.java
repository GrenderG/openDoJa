package opendoja.host;

import com.nttdocomo.opt.ui.PhoneSystem2;
import com.nttdocomo.ui.*;
import com.nttdocomo.ui.Canvas;
import com.nttdocomo.ui.Frame;
import com.nttdocomo.ui.Graphics;
import opendoja.host.input.ControllerInputEvent;
import opendoja.host.input.ControllerInputListener;
import opendoja.host.input.ControllerInputManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

public final class DoJaRuntime {
    public static final int MIN_HOST_SCALE = HostScale.MIN_FIXED_SCALE;
    public static final int MAX_HOST_SCALE = HostScale.MAX_FIXED_SCALE;
    private static final double MIN_DYNAMIC_HOST_SCALE = 0.01d;

    private static final ThreadLocal<LaunchConfig> PREPARED_LAUNCH = new ThreadLocal<>();
    private static final boolean TRACE_EVENTS = opendoja.host.OpenDoJaLaunchArgs.getBoolean(opendoja.host.OpenDoJaLaunchArgs.TRACE_EVENTS);
    private static final long MINIMUM_SELECT_PRESS_NANOS =
            java.lang.Math.max(0L, opendoja.host.OpenDoJaLaunchArgs.getLong(opendoja.host.OpenDoJaLaunchArgs.INPUT_MINIMUM_SELECT_PRESS_MS)) * 1_000_000L;
    // A short release debounce collapses desktop auto-repeat release/press pairs into a stable hold.
    private static final int KEY_REPEAT_RELEASE_DEBOUNCE_MS =
            java.lang.Math.max(0, opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.INPUT_KEY_REPEAT_RELEASE_DEBOUNCE_MS));
    private static final Map<Integer, HostControlAction> DEFAULT_KEYBOARD_ACTIONS =
            HostKeybindProfile.defaults().keyboardActionsByKeyCode();
    private static volatile DoJaRuntime current;

    private final LaunchConfig config;
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
    // iDKDoja3.5 uses a bounded ScreenUpdater queue. openDoJa has one render slot,
    // so repaint calls that arrive while it is occupied are coalesced and replayed.
    private final AtomicReference<Canvas> pendingRepaint = new AtomicReference<>();
    private final Object renderMonitor = new Object();
    private final Set<AutoCloseable> shutdownResources = ConcurrentHashMap.newKeySet();
    private final HostPanel hostPanel;
    private final ExternalFrameRenderer externalFrameRenderer;
    private final OpenGlesFpsOverlay openGlesFpsOverlay;
    private final ScratchpadStorage scratchpadStorage;
    private final java.awt.event.WindowAdapter hostWindowLifecycleListener;
    private final java.awt.event.WindowAdapter hostWindowFocusListener;
    private volatile boolean openGlesFpsEnabled;
    private volatile String hostScaleSetting;
    private volatile IApplication application;
    private JFrame frameWindow;
    private GraphicsDevice fullScreenDevice;
    private Frame currentFrame;
    private volatile Canvas presentedCanvas;
    private volatile BufferedImage presentedFrame;
    private volatile int keypadState;
    private volatile long selectLatchedUntilNanos;
    private volatile Thread renderThread;
    private String previousMicroeditionPlatform;
    private String previousMicroeditionProfiles;
    private boolean restoreMicroeditionPlatform;
    private boolean restoreMicroeditionProfiles;

    private DoJaRuntime(LaunchConfig config) {
        this.config = config;
        this.hostScaleSetting = resolveHostScale(config);
        this.externalFrameRenderer = new ExternalFrameRenderer(
                resolveExternalFrameEnabled(config),
                config.statusBarIconDevice(),
                config.iAppliType());
        this.openGlesFpsOverlay = new OpenGlesFpsOverlay();
        this.openGlesFpsEnabled = OpenDoJaLaunchArgs.getBoolean(OpenDoJaLaunchArgs.SHOW_OPEN_GLES_FPS);
        if (openGlesFpsEnabled) {
            this.externalFrameRenderer.addOverlay(openGlesFpsOverlay);
        }
        this.scratchpadStorage = new ScratchpadStorage(
                config.scratchpadRoot(),
                config.scratchpadPackedFile(),
                config.scratchpadSizes());
        this.hostPanel = new HostPanel(this);
        this.hostWindowLifecycleListener = new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                if (!shutdown.get()) {
                    shutdown();
                }
            }
        };
        this.hostWindowFocusListener = new java.awt.event.WindowAdapter() {
            @Override
            public void windowGainedFocus(java.awt.event.WindowEvent e) {
                updateFullScreenTopHint((JFrame) e.getWindow(), true);
            }

            @Override
            public void windowLostFocus(java.awt.event.WindowEvent e) {
                updateFullScreenTopHint((JFrame) e.getWindow(), false);
            }
        };
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
        return Math.max(MIN_HOST_SCALE, (int) Math.round(hostScaleFactor()));
    }

    public double hostScaleFactor() {
        return resolveHostScaleFactor(hostScaleSetting);
    }

    public String hostScaleSetting() {
        return hostScaleSetting;
    }

    public void setHostScale(int scale) {
        setHostScale(Integer.toString(scale));
    }

    public void setHostScale(String scale) {
        String normalized = normalizeHostScale(scale);
        if (hostScaleSetting.equals(normalized)) {
            return;
        }
        hostScaleSetting = normalized;
        if (SwingUtilities.isEventDispatchThread()) {
            applyHostWindowModeIfNeeded();
            hostPanel.refreshPreferredSize();
            repackWindow();
            repaintWindow();
        } else {
            SwingUtilities.invokeLater(() -> {
                applyHostWindowModeIfNeeded();
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
        JFrame window = frameWindow;
        if (window != null) {
            SwingUtilities.invokeLater(() -> {
                exitFullScreenWindowIfNeeded();
                window.dispose();
            });
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

    public void requestRepaint(Canvas canvas) {
        requestRender(canvas, true);
    }

    public void requestRender(Canvas canvas) {
        requestRender(canvas, false);
    }

    private void requestRender(Canvas canvas, boolean repaintRequest) {
        Runnable paintTask = () -> {
            renderThread = Thread.currentThread();
            try {
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
                        g.refreshDelegateAfterHostPaint();
                    }
                }
            } catch (Throwable throwable) {
                OpenDoJaLog.error(DoJaRuntime.class, "Unhandled canvas paint failure", throwable);
            } finally {
                releaseRenderSlot();
                replayPendingRepaint();
            }
        };
        if (shutdown.get()) {
            return;
        }
        boolean queued = renderQueued.compareAndSet(false, true);
        boolean waitForBackpressure = false;
        if (queued) {
            try {
                renderExecutor.execute(paintTask);
            } catch (RejectedExecutionException e) {
                releaseRenderSlot();
                return;
            }
        } else if (repaintRequest) {
            waitForBackpressure = rememberPendingRepaint(canvas) && shouldWaitForRepaintBackpressure(canvas);
        }
        if (waitForBackpressure) {
            awaitRenderSlotAvailable();
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

    public boolean isOpenGlesFpsEnabled() {
        return openGlesFpsEnabled;
    }

    public void setOpenGlesFpsEnabled(boolean enabled) {
        if (openGlesFpsEnabled == enabled) {
            return;
        }
        openGlesFpsEnabled = enabled;
        if (enabled) {
            externalFrameRenderer.addOverlay(openGlesFpsOverlay);
        } else {
            externalFrameRenderer.removeOverlay(openGlesFpsOverlay);
        }
        OpenDoJaLaunchArgs.set(OpenDoJaLaunchArgs.SHOW_OPEN_GLES_FPS, Boolean.toString(enabled));
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
        if (openGlesFpsEnabled) {
            openGlesFpsOverlay.recordFrame(canvas, canvasUsesOpenGles(canvas));
        }
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

    private boolean rememberPendingRepaint(Canvas canvas) {
        if (shutdown.get() || currentFrame != canvas) {
            return false;
        }
        pendingRepaint.set(canvas);
        return true;
    }

    private void replayPendingRepaint() {
        Canvas repaint = pendingRepaint.getAndSet(null);
        if (repaint == null) {
            return;
        }
        if (!shutdown.get() && currentFrame == repaint) {
            requestRender(repaint);
        }
    }

    private boolean shouldWaitForRepaintBackpressure(Canvas canvas) {
        // Only public repaint calls are backpressured; host render wakeups stay async.
        return currentFrame == canvas
                && !shutdown.get()
                && Thread.currentThread() != renderThread
                && !Thread.holdsLock(canvas)
                && !SwingUtilities.isEventDispatchThread();
    }

    private void releaseRenderSlot() {
        synchronized (renderMonitor) {
            renderQueued.set(false);
            renderMonitor.notifyAll();
        }
    }

    private void awaitRenderSlotAvailable() {
        synchronized (renderMonitor) {
            while (!shutdown.get() && renderQueued.get()) {
                try {
                    renderMonitor.wait(20L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private BufferedImage getCanvasImage(Canvas canvas) {
        DesktopSurface surface = getCanvasSurface(canvas);
        if (surface == null) {
            return null;
        }
        surfaceLock.lock();
        try {
            return surface.image();
        } finally {
            surfaceLock.unlock();
        }
    }

    private boolean canvasUsesOpenGles(Canvas canvas) {
        DesktopSurface surface = getCanvasSurface(canvas);
        return surface != null && surface.hasOpenGlesActivity();
    }

    private DesktopSurface getCanvasSurface(Canvas canvas) {
        surfaceLock.lock();
        try {
            Object surface = invokeCanvasMethod(canvas, "surface", new Class<?>[0]);
            return surface instanceof DesktopSurface desktopSurface ? desktopSurface : null;
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
            JFrame window = buildHostWindow(isFullscreenHostScale());
            frameWindow = window;
            hostPanel.refreshPreferredSize();
            if (!shouldCreateHostWindow(false, shutdown.get())) {
                window.dispose();
                return;
            }
            showHostWindow(window, true);
            if (!shouldCreateHostWindow(false, shutdown.get())) {
                window.dispose();
                return;
            }
            hostPanel.requestFocusInWindow();
        });
    }

    static boolean shouldCreateHostWindow(boolean headless, boolean shutdownRequested) {
        return !headless && !shutdownRequested;
    }

    private void positionInitialWindow(Window window) {
        if (isFullscreenHostScale()) {
            return;
        }
        // GNOME/OpenJDK can ignore setLocationByPlatform(true) for non-resizable frames,
        // which leaves the host window near the primary-display origin instead of on the active screen.
        //window.setLocationByPlatform(true);
        centerOnCurrentScreen(window);
    }

    private static void centerOnCurrentScreen(Window window) {
        Rectangle usableBounds = usableScreenBounds(window);
        if (usableBounds == null) {
            return;
        }

        Dimension size = window.getSize();
        int x = usableBounds.x + (usableBounds.width - size.width) / 2;
        int y = usableBounds.y + (usableBounds.height - size.height) / 2;
        window.setLocation(x, y);
    }

    private static Rectangle usableScreenBounds(Window window) {
        if (GraphicsEnvironment.isHeadless()) {
            return null;
        }
        GraphicsConfiguration graphicsConfiguration = graphicsConfigurationFor(window);
        if (graphicsConfiguration == null) {
            return null;
        }
        Rectangle bounds = graphicsConfiguration.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(graphicsConfiguration);
        Rectangle usable = new Rectangle(
                bounds.x + insets.left,
                bounds.y + insets.top,
                Math.max(1, bounds.width - insets.left - insets.right),
                Math.max(1, bounds.height - insets.top - insets.bottom));
        Rectangle maximumWindowBounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        if (maximumWindowBounds != null && usable.intersects(maximumWindowBounds)) {
            usable = usable.intersection(maximumWindowBounds);
        }
        return usable;
    }

    private static GraphicsConfiguration graphicsConfigurationFor(Window window) {
        PointerInfo pointerInfo = MouseInfo.getPointerInfo();
        if (pointerInfo != null) {
            return pointerInfo.getDevice().getDefaultConfiguration();
        }
        if (window != null && window.getGraphicsConfiguration() != null) {
            return window.getGraphicsConfiguration();
        }
        return GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice()
                .getDefaultConfiguration();
    }

    private static int keyMask(int keyCode) {
        if (keyCode < 0 || keyCode > 30) {
            return 0;
        }
        return 1 << keyCode;
    }

    private static String resolveHostScale(LaunchConfig config) {
        return normalizeHostScale(opendoja.host.OpenDoJaLaunchArgs.get(opendoja.host.OpenDoJaLaunchArgs.HOST_SCALE));
    }

    static int normalizeHostScale(int scale) {
        return HostScale.normalizeFixedScale(scale);
    }

    static String normalizeHostScale(String scale) {
        return HostScale.normalizeId(scale);
    }

    private static boolean resolveExternalFrameEnabled(LaunchConfig config) {
        return opendoja.host.OpenDoJaLaunchArgs.getBoolean(
                opendoja.host.OpenDoJaLaunchArgs.EXTERNAL_FRAME,
                config == null || config.externalFrameEnabled());
    }

    private void repackWindow() {
        if (frameWindow == null) {
            return;
        }
        Dimension preferred = hostPreferredSize();
        hostPanel.setSize(preferred);
        if (isFullscreenHostScale()) {
            enterFullScreenWindowIfNeeded();
            frameWindow.validate();
            return;
        }
        frameWindow.pack();
        frameWindow.validate();
    }

    private void applyHostWindowModeIfNeeded() {
        JFrame window = frameWindow;
        if (window == null) {
            return;
        }
        boolean fullscreen = isFullscreenHostScale();
        if (fullscreen && window.isUndecorated() && isFullScreenActive()) {
            return;
        }
        if (!fullscreen && !window.isUndecorated()) {
            return;
        }
        rebuildHostWindowForCurrentMode();
    }

    private void enterFullScreenWindowIfNeeded() {
        JFrame window = frameWindow;
        if (window == null) {
            return;
        }
        GraphicsDevice targetDevice = fullScreenDeviceFor(window);
        if (targetDevice == null) {
            return;
        }
        if (fullScreenDevice == targetDevice
                && targetDevice.getFullScreenWindow() == window
                && window.isUndecorated()) {
            return;
        }

        if (fullScreenDevice != null
                && fullScreenDevice != targetDevice
                && fullScreenDevice.getFullScreenWindow() == window) {
            fullScreenDevice.setFullScreenWindow(null);
        }
        window.setResizable(true);
        fullScreenDevice = targetDevice;
        Dimension preferred = fullScreenHostPanelSize(logicalHostSize());
        hostPanel.setPreferredSize(preferred);
        hostPanel.setMinimumSize(preferred);
        hostPanel.setMaximumSize(preferred);
        hostPanel.setSize(preferred);
        Rectangle screenBounds = targetDevice.getDefaultConfiguration().getBounds();
        if (screenBounds != null) {
            window.setBounds(screenBounds);
        }
        if (!shutdown.get()) {
            if (!window.isVisible()) {
                window.setVisible(true);
            }
            hostPanel.ensureControllerInput(window);
            updateFullScreenTopHint(window, true);
            targetDevice.setFullScreenWindow(window);
            window.validate();
            window.toFront();
            hostPanel.requestFocusInWindow();
            reassertFullScreenWindow(targetDevice, window);
        }
    }

    private void exitFullScreenWindowIfNeeded() {
        JFrame window = frameWindow;
        if (window == null) {
            return;
        }
        GraphicsDevice previousDevice = fullScreenDevice;
        boolean wasFullscreen = previousDevice != null && previousDevice.getFullScreenWindow() == window;
        boolean needsDecorationChange = window.isUndecorated();
        if (!wasFullscreen && !needsDecorationChange) {
            fullScreenDevice = null;
            return;
        }

        updateFullScreenTopHint(window, false);
        if (wasFullscreen) {
            previousDevice.setFullScreenWindow(null);
        }
        fullScreenDevice = null;
    }

    private void rebuildHostWindowForCurrentMode() {
        JFrame previousWindow = frameWindow;
        if (previousWindow == null) {
            return;
        }
        // Linux/Swing fullscreen transitions can leave the native window carrying stale
        // fullscreen geometry/state. Rebuild the frame so the replacement starts clean.
        updateFullScreenTopHint(previousWindow, false);
        exitFullScreenWindowIfNeeded();
        previousWindow.removeWindowListener(hostWindowLifecycleListener);
        previousWindow.removeWindowFocusListener(hostWindowFocusListener);
        if (hostPanel.getParent() != null) {
            hostPanel.getParent().remove(hostPanel);
        }
        previousWindow.setVisible(false);
        previousWindow.dispose();
        if (shutdown.get()) {
            frameWindow = null;
            return;
        }
        JFrame replacement = buildHostWindow(isFullscreenHostScale());
        frameWindow = replacement;
        hostPanel.refreshPreferredSize();
        showHostWindow(replacement, true);
    }

    private Dimension hostPreferredSize() {
        if (isFullscreenHostScale()) {
            return fullScreenHostPanelSize(logicalHostSize());
        }
        return scaledHostFrameSize();
    }

    private Dimension scaledHostFrameSize() {
        return externalFrameRenderer.layoutFor(
                displayWidth(),
                displayHeight(),
                hostScaleFactor()).preferredSize();
    }

    private Dimension logicalHostSize() {
        return externalFrameRenderer.layoutFor(displayWidth(), displayHeight(), 1).preferredSize();
    }

    private double resolveHostScaleFactor(String scaleSetting) {
        String normalized = normalizeHostScale(scaleSetting);
        if (!HostScale.isFullscreen(normalized)) {
            return HostScale.fixedScaleOrDefault(normalized, MIN_HOST_SCALE);
        }
        Dimension logicalSize = logicalHostSize();
        Dimension availableSize = fullScreenHostPanelSize(logicalSize);
        return resolveFullscreenHostScale(logicalSize, availableSize);
    }

    private boolean isFullscreenHostScale() {
        return HostScale.isFullscreen(hostScaleSetting);
    }

    private Dimension fullScreenHostPanelSize(Dimension logicalSize) {
        if (isFullScreenActive() && hostPanel.getWidth() > 0 && hostPanel.getHeight() > 0) {
            return hostPanel.getSize();
        }
        Rectangle bounds = fullScreenScreenBounds();
        if (bounds == null) {
            return new Dimension(
                    Math.max(1, logicalSize.width * MAX_HOST_SCALE),
                    Math.max(1, logicalSize.height * MAX_HOST_SCALE));
        }
        return new Dimension(Math.max(1, bounds.width), Math.max(1, bounds.height));
    }

    static double resolveFullscreenHostScale(Dimension logicalSize, Dimension availableSize) {
        if (logicalSize == null || availableSize == null || logicalSize.width <= 0 || logicalSize.height <= 0) {
            return MIN_DYNAMIC_HOST_SCALE;
        }
        double widthScale = availableSize.width / (double) logicalSize.width;
        double heightScale = availableSize.height / (double) logicalSize.height;
        double scale = Math.min(widthScale, heightScale);
        if (!Double.isFinite(scale)) {
            return MIN_DYNAMIC_HOST_SCALE;
        }
        return Math.max(MIN_DYNAMIC_HOST_SCALE, scale);
    }

    static Point frameOriginForHostScale(String scaleSetting, Dimension contentSize, Dimension frameSize) {
        if (!HostScale.isFullscreen(scaleSetting) || contentSize == null || frameSize == null) {
            return new Point(0, 0);
        }
        return new Point(
                Math.max(0, (contentSize.width - frameSize.width) / 2),
                Math.max(0, (contentSize.height - frameSize.height) / 2));
    }

    private boolean isFullScreenActive() {
        JFrame window = frameWindow;
        GraphicsDevice device = fullScreenDevice;
        return window != null && device != null && device.getFullScreenWindow() == window;
    }

    private Rectangle fullScreenScreenBounds() {
        GraphicsDevice device = fullScreenDevice;
        if (device == null) {
            device = fullScreenDeviceFor(frameWindow);
        }
        if (device == null || device.getDefaultConfiguration() == null) {
            return null;
        }
        return device.getDefaultConfiguration().getBounds();
    }

    private static GraphicsDevice fullScreenDeviceFor(Window window) {
        if (GraphicsEnvironment.isHeadless()) {
            return null;
        }
        GraphicsConfiguration graphicsConfiguration = graphicsConfigurationFor(window);
        if (graphicsConfiguration == null) {
            return null;
        }
        return graphicsConfiguration.getDevice();
    }

    private JFrame buildHostWindow(boolean fullscreen) {
        JFrame window = new JFrame(config.title());
        window.setUndecorated(fullscreen);
        window.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        window.getContentPane().setBackground(Color.BLACK);
        window.setBackground(Color.BLACK);
        window.setAlwaysOnTop(false);
        window.addWindowListener(hostWindowLifecycleListener);
        window.addWindowFocusListener(hostWindowFocusListener);
        window.add(hostPanel);
        window.setResizable(!fullscreen);
        return window;
    }

    private void showHostWindow(JFrame window, boolean positionWindowed) {
        if (isFullscreenHostScale()) {
            enterFullScreenWindowIfNeeded();
            return;
        }
        window.pack();
        hostPanel.refreshPreferredSize();
        repackWindow();
        if (positionWindowed) {
            positionInitialWindow(window);
        }
        if (!shutdown.get()) {
            window.setVisible(true);
            hostPanel.ensureControllerInput(window);
            hostPanel.requestFocusInWindow();
        }
    }

    private void reassertFullScreenWindow(GraphicsDevice targetDevice, JFrame window) {
        SwingUtilities.invokeLater(() -> {
            if (shutdown.get() || frameWindow != window || !isFullscreenHostScale()) {
                return;
            }
            updateFullScreenTopHint(window, window.isFocused());
            targetDevice.setFullScreenWindow(window);
            window.toFront();
            hostPanel.requestFocusInWindow();
        });
    }

    private void updateFullScreenTopHint(JFrame window, boolean focused) {
        if (window == null || !window.isAlwaysOnTopSupported()) {
            return;
        }
        boolean shouldBeAlwaysOnTop = isFullscreenHostScale() && focused;
        if (window.isAlwaysOnTop() == shouldBeAlwaysOnTop) {
            return;
        }
        window.setAlwaysOnTop(shouldBeAlwaysOnTop);
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
        HostControlAction action = DEFAULT_KEYBOARD_ACTIONS.get(awtKeyCode);
        if (action == null || action.dispatchKind() != HostControlAction.DispatchKind.DOJA_KEY) {
            return -1;
        }
        return action.dispatchCode();
    }

    private static int mapHostSoftKey(int awtKeyCode) {
        HostControlAction action = DEFAULT_KEYBOARD_ACTIONS.get(awtKeyCode);
        if (action == null || action.dispatchKind() != HostControlAction.DispatchKind.HOST_SOFT_KEY) {
            return -1;
        }
        return action.dispatchCode();
    }

    private static final class HostPanel extends JPanel {
        private final DoJaRuntime runtime;
        private final DesktopKeyInputAdapter keyInputAdapter;
        private final DesktopKeyInputAdapter softKeyInputAdapter;
        private final HostInputRouter inputRouter;
        private volatile ControllerInputManager controllerInputManager;

        private HostPanel(DoJaRuntime runtime) {
            this.runtime = runtime;
            this.keyInputAdapter = new DesktopKeyInputAdapter(this::scheduleRelease, runtime::dispatchSyntheticKey,
                    KEY_REPEAT_RELEASE_DEBOUNCE_MS);
            this.softKeyInputAdapter = new DesktopKeyInputAdapter(this::scheduleRelease, runtime::dispatchHostSoftKey,
                    KEY_REPEAT_RELEASE_DEBOUNCE_MS);
            this.inputRouter = new HostInputRouter(HostKeybindProfile.fromLaunchArgs());
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
                    HostControlAction action = inputRouter.keyboardAction(event.getKeyCode());
                    if (action == null) {
                        return;
                    }
                    dispatchMappedAction(action, event.getID() == KeyEvent.KEY_PRESSED);
                    event.consume();
                }
            });
        }

        private void ensureControllerInput(JFrame ownerWindow) {
            if (controllerInputManager != null || GraphicsEnvironment.isHeadless()) {
                return;
            }
            ControllerInputManager manager = new ControllerInputManager(ownerWindow);
            manager.addListener(new ControllerInputListener() {
                @Override
                public void onInput(ControllerInputEvent event) {
                    SwingUtilities.invokeLater(() -> dispatchControllerEvent(event));
                }
            });
            runtime.registerShutdownResource(manager);
            controllerInputManager = manager;
        }

        private DesktopKeyInputAdapter.PendingRelease scheduleRelease(int delayMillis, Runnable task) {
            Timer timer = new Timer(java.lang.Math.max(0, delayMillis), e -> task.run());
            timer.setRepeats(false);
            timer.start();
            return timer::stop;
        }

        private void dispatchControllerEvent(ControllerInputEvent event) {
            if (event == null || !isShowing()) {
                return;
            }
            Window window = SwingUtilities.getWindowAncestor(this);
            if (window != null && !window.isFocused()) {
                return;
            }
            HostControlAction action = inputRouter.controllerAction(event);
            if (action == null) {
                return;
            }
            dispatchMappedAction(action, event.active());
        }

        private void dispatchMappedAction(HostControlAction action, boolean pressed) {
            if (action.dispatchKind() == HostControlAction.DispatchKind.HOST_SOFT_KEY) {
                int softKey = action.dispatchCode();
                if (pressed) {
                    softKeyInputAdapter.keyPressed(softKey);
                } else {
                    softKeyInputAdapter.keyReleased(softKey);
                }
                return;
            }
            int dojaKey = action.dispatchCode();
            if (pressed) {
                keyInputAdapter.keyPressed(dojaKey);
            } else {
                keyInputAdapter.keyReleased(dojaKey);
            }
        }

        private void installHostScalePopup() {
            MouseAdapter popupListener = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    if (SwingUtilities.isLeftMouseButton(event) && event.getClickCount() == 2) {
                        runtime.setHostScale(MIN_HOST_SCALE);
                        HostPanel.this.requestFocusInWindow();
                    }
                }

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
            JCheckBoxMenuItem showOpenGlesFpsItem = new JCheckBoxMenuItem("Show OpenGLES FPS");
            showOpenGlesFpsItem.setSelected(runtime.isOpenGlesFpsEnabled());
            showOpenGlesFpsItem.addActionListener(event -> {
                runtime.setOpenGlesFpsEnabled(showOpenGlesFpsItem.isSelected());
                HostPanel.this.requestFocusInWindow();
            });
            menu.add(showOpenGlesFpsItem);
            menu.addSeparator();
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
                item.setSelected(!HostScale.isFullscreen(runtime.hostScaleSetting())
                        && HostScale.fixedScaleOrDefault(runtime.hostScaleSetting(), MIN_HOST_SCALE) == scale);
                group.add(item);
                menu.add(item);
            }
            JRadioButtonMenuItem fullscreenItem = new JRadioButtonMenuItem(new AbstractAction(HostScale.label(HostScale.FULLSCREEN_ID)) {
                @Override
                public void actionPerformed(ActionEvent event) {
                    runtime.setHostScale(HostScale.FULLSCREEN_ID);
                    HostPanel.this.requestFocusInWindow();
                }
            });
            fullscreenItem.setSelected(HostScale.isFullscreen(runtime.hostScaleSetting()));
            group.add(fullscreenItem);
            menu.add(fullscreenItem);
            return menu;
        }

        private void refreshPreferredSize() {
            Dimension preferred = runtime.hostPreferredSize();
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
            try {
                Dimension frameSize = runtime.scaledHostFrameSize();
                Point origin = frameOriginForHostScale(runtime.hostScaleSetting(), getSize(), frameSize);
                g2.translate(origin.x, origin.y);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                runtime.externalFrameRenderer.paint(g2, runtime.currentFrame, runtime.presentedFrame,
                        runtime.displayWidth(), runtime.displayHeight(), runtime.hostScaleFactor());
            } finally {
                g2.dispose();
            }
        }
    }
}
