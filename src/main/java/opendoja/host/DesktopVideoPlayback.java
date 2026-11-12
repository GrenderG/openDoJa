package opendoja.host;

import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallbackAdapter;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallbackAdapter;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat;

import javax.swing.SwingUtilities;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/** Native decoding with frames composited into the existing handset display. */
public final class DesktopVideoPlayback implements AutoCloseable {
    public enum Result { COMPLETED, STOPPED, FAILED }

    private final DoJaRuntime runtime;
    private final byte[] data;
    private final String extension;
    private final boolean audioEnabled;
    private final long positionMillis;
    private final Dimension videoSize;
    private final CompletableFuture<Result> completion = new CompletableFuture<>();
    private volatile BufferedImage frame;
    private volatile boolean frameDecoded;
    private volatile long lengthMillis;
    private volatile long stoppedPosition;

    public DesktopVideoPlayback(DoJaRuntime runtime, byte[] data, String extension,
                                boolean audioEnabled, long positionMillis) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.data = Objects.requireNonNull(data, "data").clone();
        this.extension = "mp4".equals(extension) ? "mp4" : "3gp";
        this.audioEnabled = audioEnabled;
        this.positionMillis = Math.max(0L, positionMillis);
        this.videoSize = DesktopVideoSupport.videoSize(this.data);
        this.frame = new BufferedImage(runtime.displayWidth(), runtime.displayHeight(), BufferedImage.TYPE_INT_RGB);
    }

    /** Waits on the calling application thread; native callbacks never execute game code. */
    public Result playAndWait(Runnable startedCallback) throws IOException {
        runtime.beginVideoPlayback(this);
        runtime.registerShutdownResource(this);
        MediaPlayerFactory factory = null;
        EmbeddedMediaPlayer player = null;
        Path extracted = null;
        try {
            if (completion.isDone()) return Result.STOPPED;
            extracted = Files.createTempFile("opendoja-video-", "." + extension);
            Files.write(extracted, data);
            try {
                factory = new MediaPlayerFactory("--intf=dummy", "--quiet", "--no-video-title-show",
                        "--no-metadata-network-access", "--avcodec-hw=none");
                player = factory.mediaPlayers().newEmbeddedMediaPlayer();
                // The decoder owns its reusable buffer; publish a separate immutable image each frame.
                final int[] bufferSize = new int[2];
                RenderCallbackAdapter renderer = new RenderCallbackAdapter() {
                    @Override
                    protected void onDisplay(MediaPlayer mediaPlayer, int[] buffer) {
                        if (completion.isDone()) return;
                        BufferedImage decoded = new BufferedImage(bufferSize[0], bufferSize[1], BufferedImage.TYPE_INT_RGB);
                        decoded.setRGB(0, 0, bufferSize[0], bufferSize[1], buffer, 0, bufferSize[0]);
                        frame = fitToDisplay(decoded);
                        frameDecoded = true;
                        runtime.repaintWindow();
                    }
                };
                player.videoSurface().set(factory.videoSurfaces().newVideoSurface(new BufferFormatCallbackAdapter() {
                    @Override
                    public BufferFormat getBufferFormat(int width, int height) {
                        int outputWidth = videoSize.width > 0 ? videoSize.width : width;
                        int outputHeight = videoSize.height > 0 ? videoSize.height : height;
                        OpenDoJaLog.debug(DesktopVideoPlayback.class, () -> "Video buffer " + width + "x" + height
                                + " displayed at " + outputWidth + "x" + outputHeight);
                        bufferSize[0] = outputWidth;
                        bufferSize[1] = outputHeight;
                        renderer.setBuffer(new int[Math.multiplyExact(outputWidth, outputHeight)]);
                        return new RV32BufferFormat(outputWidth, outputHeight);
                    }
                }, renderer, true));
                player.events().addMediaPlayerEventListener(new MediaPlayerEventAdapter() {
                    @Override
                    public void finished(MediaPlayer mediaPlayer) {
                        if (frameDecoded) stoppedPosition = Math.max(stoppedPosition, lengthMillis);
                        completion.complete(frameDecoded ? Result.COMPLETED : Result.FAILED);
                    }

                    @Override
                    public void error(MediaPlayer mediaPlayer) {
                        completion.complete(Result.FAILED);
                    }

                    @Override
                    public void timeChanged(MediaPlayer mediaPlayer, long time) {
                        stoppedPosition = Math.max(0L, time);
                    }

                    @Override
                    public void lengthChanged(MediaPlayer mediaPlayer, long length) {
                        lengthMillis = Math.max(0L, length);
                    }
                });
                String audioOption = audioEnabled ? ":audio" : ":no-audio";
                String startOption = String.format(Locale.ROOT, ":start-time=%.3f", positionMillis / 1000.0);
                if (!completion.isDone() && !player.media().play(extracted.toString(), audioOption, startOption)) {
                    throw new IOException("The embedded video player could not open the video");
                }
            } catch (RuntimeException | LinkageError exception) {
                throw new IOException("Embedded video requires VLC 3.x installed with the same architecture as Java", exception);
            }
            if (startedCallback != null) startedCallback.run();
            return awaitCompletion();
        } finally {
            // Release outside LibVLC's event thread: stopping/releasing from a native callback can deadlock.
            try {
                if (player != null) player.release();
            } finally {
                try {
                    if (factory != null) factory.release();
                } finally {
                    close();
                    runtime.endVideoPlayback(this);
                    runtime.unregisterShutdownResource(this);
                    if (extracted != null) {
                        try {
                            Files.deleteIfExists(extracted);
                        } catch (IOException exception) {
                            extracted.toFile().deleteOnExit();
                        }
                    }
                }
            }
        }
    }

    private Result awaitCompletion() {
        if (SwingUtilities.isEventDispatchThread() && !completion.isDone()) {
            // Keep Swing painting and window-close events alive even if an API call came from the EDT.
            SecondaryLoop loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
            completion.whenComplete((result, failure) -> SwingUtilities.invokeLater(loop::exit));
            loop.enter();
        }
        try {
            return completion.get();
        } catch (InterruptedException exception) {
            close();
            Thread.currentThread().interrupt();
            return Result.STOPPED;
        } catch (ExecutionException exception) {
            return Result.FAILED;
        }
    }

    private BufferedImage fitToDisplay(BufferedImage decoded) {
        int width = runtime.displayWidth();
        int height = runtime.displayHeight();
        BufferedImage fitted = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        double scale = Math.min(width / (double) decoded.getWidth(), height / (double) decoded.getHeight());
        int videoWidth = Math.max(1, (int) Math.round(decoded.getWidth() * scale));
        int videoHeight = Math.max(1, (int) Math.round(decoded.getHeight() * scale));
        Graphics2D graphics = fitted.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(decoded, (width - videoWidth) / 2, (height - videoHeight) / 2,
                    videoWidth, videoHeight, null);
        } finally {
            graphics.dispose();
        }
        return fitted;
    }

    BufferedImage frame() {
        return frame;
    }

    public long stoppedPositionMillis() {
        return stoppedPosition;
    }

    /** Signals cancellation; the waiting application thread owns native resource cleanup. */
    @Override
    public void close() {
        completion.complete(Result.STOPPED);
    }
}
