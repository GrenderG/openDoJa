package opendoja.audio.mld;

import opendoja.audio.mld.ma3.MA3SamplerProvider;
import opendoja.audio.mld.ma3.MLD;
import opendoja.audio.mld.ma3.MLDPlayer;
import opendoja.audio.mld.ma3.MLDPlayerEvent;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

public final class MldPcmPlayer implements AutoCloseable {
    private static final float DEFAULT_SAMPLE_RATE = Float.parseFloat(
            System.getProperty("opendoja.mldSampleRate",
                    Float.toString(MA3SamplerProvider.SAMPLE_RATE)));
    private static final int BUFFER_FRAMES = Integer.getInteger("opendoja.mldBufferFrames", 1024);
    private static final AudioFormat OUTPUT_FORMAT = new AudioFormat(
            DEFAULT_SAMPLE_RATE, 16, 2, true, false);

    public interface Listener {
        void onLoop();

        void onComplete();

        void onFailure(Exception exception);
    }

    private final Object stateLock = new Object();
    private final Listener listener;
    private final MLD mld;
    private final MLDPlayer player;
    private final float[] sampleBuffer = new float[BUFFER_FRAMES * 2];
    private final byte[] pcmBuffer = new byte[BUFFER_FRAMES * 4];

    private SourceDataLine line;
    private Thread worker;
    private boolean paused;
    private boolean stopRequested;
    private boolean cuepointLooping;
    private int remainingRepeats;
    private int requestedLoopCount;

    public MldPcmPlayer(byte[] bytes, Listener listener) {
        this.listener = listener;
        this.mld = new MLD(bytes);
        this.player = new MLDPlayer(mld,
                new MA3SamplerProvider(
                        MA3SamplerProvider.FM_MA3_4OP,
                        MA3SamplerProvider.FM_MA3_4OP,
                        MA3SamplerProvider.WAVE_DRUM_MA3),
                DEFAULT_SAMPLE_RATE);
        this.player.setPlaybackEventsEnabled(true);
        this.player.setLoopStopAll(true);
    }

    public void start(int loopCount) throws Exception {
        synchronized (stateLock) {
            stopRequested = false;
            paused = false;
            player.reset();
            requestedLoopCount = loopCount;
            configureLooping(loopCount);
            line = AudioSystem.getSourceDataLine(OUTPUT_FORMAT);
            line.open(OUTPUT_FORMAT, pcmBuffer.length * 8);
            line.start();
            worker = new Thread(this::runRenderLoop, "opendoja-mld");
            worker.setDaemon(true);
            worker.start();
        }
    }

    public void pause() {
        synchronized (stateLock) {
            if (line != null) {
                paused = true;
                line.stop();
            }
        }
    }

    public void restart() {
        synchronized (stateLock) {
            if (line != null) {
                paused = false;
                line.start();
                stateLock.notifyAll();
            }
        }
    }

    public int getCurrentTimeMillis() {
        synchronized (stateLock) {
            return (int) Math.round(player.getTime() * 1000.0);
        }
    }

    public int getTotalTimeMillis() {
        double baseSeconds = mld.getDuration(true);
        if (!Double.isFinite(baseSeconds)) {
            return 0;
        }
        if (requestedLoopCount <= 0) {
            return (int) Math.round(baseSeconds * 1000.0);
        }
        return (int) Math.round(baseSeconds * requestedLoopCount * 1000.0);
    }

    public void stop() {
        Thread threadToJoin;
        synchronized (stateLock) {
            stopRequested = true;
            paused = false;
            threadToJoin = worker;
            worker = null;
            stateLock.notifyAll();
        }
        if (threadToJoin != null && threadToJoin != Thread.currentThread()) {
            try {
                threadToJoin.join(1000L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        closeLine();
    }

    @Override
    public void close() {
        stop();
    }

    private void configureLooping(int loopCount) {
        cuepointLooping = Double.isInfinite(mld.getDuration(false));
        if (loopCount <= 0) {
            remainingRepeats = Integer.MAX_VALUE;
            player.setLoopEnabled(cuepointLooping);
            return;
        }
        remainingRepeats = Math.max(0, loopCount - 1);
        player.setLoopEnabled(cuepointLooping && remainingRepeats > 0);
    }

    private void runRenderLoop() {
        try {
            while (true) {
                int frames;
                MLDPlayerEvent[] events;
                synchronized (stateLock) {
                    while (paused && !stopRequested) {
                        stateLock.wait();
                    }
                    if (stopRequested) {
                        break;
                    }
                    frames = player.render(sampleBuffer, 0, BUFFER_FRAMES, 1.0f, 1.0f, true, true);
                    events = player.getEvents();
                }

                if (frames > 0) {
                    int length = encodePcm(frames);
                    line.write(pcmBuffer, 0, length);
                }

                boolean restarted = false;
                boolean finished = frames < 0;
                for (MLDPlayerEvent event : events) {
                    if (event.type == MLDPlayer.EVENT_LOOP) {
                        if (listener != null) {
                            listener.onLoop();
                        }
                        // MLD cuepoint loops happen inside the sequence, so
                        // the loop budget is consumed on LOOP rather than END.
                        if (remainingRepeats != Integer.MAX_VALUE && remainingRepeats > 0) {
                            remainingRepeats--;
                            if (remainingRepeats == 0) {
                                synchronized (stateLock) {
                                    player.setLoopEnabled(false);
                                }
                            }
                        }
                    } else if (event.type == MLDPlayer.EVENT_END) {
                        if (remainingRepeats == Integer.MAX_VALUE) {
                            synchronized (stateLock) {
                                player.reset();
                            }
                            restarted = true;
                        } else if (!cuepointLooping && remainingRepeats > 0) {
                            // Non-looping sequences need an explicit restart to
                            // honor AudioPresenter.play(loopCount).
                            remainingRepeats--;
                            synchronized (stateLock) {
                                player.reset();
                            }
                            restarted = true;
                        } else {
                            finished = true;
                        }
                    }
                }

                if (restarted) {
                    continue;
                }
                if (finished) {
                    if (line != null) {
                        line.drain();
                    }
                    if (listener != null) {
                        listener.onComplete();
                    }
                    break;
                }
            }
        } catch (Exception exception) {
            if (listener != null) {
                listener.onFailure(exception);
            }
        } finally {
            closeLine();
        }
    }

    private int encodePcm(int frames) {
        int output = 0;
        for (int i = 0; i < frames * 2; i++) {
            float sample = Math.max(-1.0f, Math.min(1.0f, sampleBuffer[i]));
            int value = Math.round(sample * 32767.0f);
            pcmBuffer[output++] = (byte) (value & 0xFF);
            pcmBuffer[output++] = (byte) ((value >>> 8) & 0xFF);
        }
        return output;
    }

    private void closeLine() {
        synchronized (stateLock) {
            if (line != null) {
                line.stop();
                line.flush();
                line.close();
                line = null;
            }
        }
    }
}
