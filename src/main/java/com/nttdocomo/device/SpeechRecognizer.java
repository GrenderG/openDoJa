package com.nttdocomo.device;

import com.nttdocomo.lang.IllegalStateException;
import com.nttdocomo.lang.UnsupportedOperationException;
import opendoja.host.DoJaApiUnimplemented;

import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Provides the means to access the speech-recognition front-end feature.
 * The speech-recognition front-end feature extracts feature values from speech input.
 * To access the speech-recognition front-end feature, it is necessary to call
 * {@link #getInstance()} and obtain an instance that represents the feature.
 * After an instance is obtained, feature extraction starts by calling
 * {@link #start(String, SpeechListener)}.
 * The extracted feature data is obtained by calling {@link #getFeature()}.
 * Recognition-result chunks returned by a speech-recognition back-end server can be converted to
 * {@link SpeechResultInformation} objects with {@link #getResultInformation(byte[], String)}.
 */
public class SpeechRecognizer {
    private static final String UNRESOLVED_RESULT_CHUNK_MESSAGE =
            "The official docs define this API but do not describe the recognition-result chunk wire format";
    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "opendoja-speech");
        thread.setDaemon(true);
        return thread;
    });
    private static final Object LOCK = new Object();

    private static SpeechRecognizer current;

    private final Deque<SpeechFeatureData> features = new ArrayDeque<>();

    private SpeechListener listener;
    private ScheduledFuture<?> featureTask;
    private ScheduledFuture<?> timeoutTask;
    private boolean available = true;
    private boolean started;
    private boolean startCalledSinceReset;
    private boolean finished;

    private SpeechRecognizer() {
    }

    /**
     * Gets an instance of the speech-recognition front-end feature.
     * While an available instance exists, repeated calls to this method return the same instance.
     * If no available instance exists, a new instance is created and returned.
     * On the desktop host, this optional device feature is not supported.
     *
     * @return never returns normally on the desktop host
     * @throws UnsupportedOperationException if the front-end speech-recognition feature is not supported
     */
    public static SpeechRecognizer getInstance() {
        if (!isSupported()) {
            throw new UnsupportedOperationException("Speech recognition front-end is not supported by openDoJa");
        }
        synchronized (LOCK) {
            if (current == null || !current.available) {
                current = new SpeechRecognizer();
            }
            return current;
        }
    }

    /**
     * Checks whether this front-end speech-recognition instance is available.
     * To use speech recognition again after an instance becomes unavailable, obtain a new instance with
     * {@link #getInstance()}.
     *
     * @return {@code true} if this instance is available, or {@code false} if it is unavailable
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * Gets the name and version of the speech-recognition front-end feature.
     * The name and version obtained here are used as the value of the {@code X-DDP-Frontend}
     * property in DDP communication.
     *
     * @return never returns normally on the desktop host
     * @throws UnsupportedOperationException because the desktop runtime does not support this optional device feature
     */
    public String getName() {
        ensureAvailable();
        return opendoja.host.OpenDoJaLaunchArgs.get(opendoja.host.OpenDoJaLaunchArgs.SPEECH_NAME);
    }

    /**
     * Gets the maximum speech time.
     * The ready time is included in the maximum speech time.
     *
     * @return never returns normally on the desktop host
     * @throws UnsupportedOperationException because the desktop runtime does not support this optional device feature
     */
    public int getMaxSpeechTime() {
        ensureAvailable();
        return opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.SPEECH_MAX_SPEECH_TIME);
    }

    /**
     * Gets the codecs supported by the speech-recognition front-end feature.
     *
     * @return never returns normally on the desktop host
     * @throws UnsupportedOperationException because the desktop runtime does not support this optional device feature
     */
    public String[] getAvailableCodec() {
        ensureAvailable();
        return availableCodecs().clone();
    }

    /**
     * Gets the recognition types supported by the speech-recognition front-end feature.
     *
     * @return never returns normally on the desktop host
     * @throws UnsupportedOperationException because the desktop runtime does not support this optional device feature
     */
    public int[] getAvailableType() {
        ensureAvailable();
        return availableTypes().clone();
    }

    /**
     * Gets the ready time.
     * The ready time is the time, in milliseconds, from the start of feature extraction until speech input
     * becomes possible.
     *
     * @param codec the codec to query
     * @return never returns normally on the desktop host
     * @throws UnsupportedOperationException because the desktop runtime does not support this optional device feature
     */
    public int getReadyTime(String codec) {
        ensureAvailable();
        requireSupportedCodec(codec);
        return opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.SPEECH_READY_TIME);
    }

    /**
     * Starts feature-extraction processing.
     * When feature extraction starts, any accumulated feature data is cleared.
     *
     * @param codec the codec to use
     * @param listener the listener that receives events that occur during feature extraction
     * @throws UnsupportedOperationException because the desktop runtime does not support this optional device feature
     */
    public void start(String codec, SpeechListener listener) {
        ensureAvailable();
        requireSupportedCodec(codec);
        if (listener == null) {
            throw new NullPointerException("listener");
        }
        synchronized (this) {
            if (started) {
                throw new IllegalStateException("Speech feature extraction has already started");
            }
            cancelTasksLocked();
            this.listener = listener;
            this.features.clear();
            this.started = true;
            this.startCalledSinceReset = true;
            this.finished = false;
            scheduleFeatureProductionLocked();
        }
    }

    /**
     * Stops feature-extraction processing.
     * If feature extraction has not been started, this method does nothing.
     *
     * @throws UnsupportedOperationException because the desktop runtime does not support this optional device feature
     */
    public void stop() {
        ensureAvailable();
        synchronized (this) {
            if (!started) {
                return;
            }
            started = false;
            cancelTimeoutLocked();
        }
        notifyEvent(listener, SpeechListener.EVENT_STOP, SpeechListener.STOP_TRIGGER);
    }

    /**
     * Resets feature-extraction processing.
     * Accumulated feature data is cleared and the object returns to the state in which feature extraction
     * can be started.
     *
     * @throws UnsupportedOperationException because the desktop runtime does not support this optional device feature
     */
    public void reset() {
        ensureAvailable();
        SpeechListener currentListener;
        boolean notifyStop;
        synchronized (this) {
            currentListener = listener;
            notifyStop = started;
            started = false;
            startCalledSinceReset = false;
            finished = false;
            features.clear();
            cancelTasksLocked();
        }
        if (notifyStop) {
            notifyEvent(currentListener, SpeechListener.EVENT_STOP, SpeechListener.STOP_RESET);
        }
    }

    /**
     * Gets the feature data accumulated in the speech-recognition front-end feature.
     * A new {@link SpeechFeatureData} instance is returned each time.
     *
     * @return never returns normally on the desktop host
     * @throws UnsupportedOperationException because the desktop runtime does not support this optional device feature
     */
    public SpeechFeatureData getFeature() {
        ensureAvailable();
        synchronized (this) {
            if (!startCalledSinceReset) {
                throw new IllegalStateException("Speech feature extraction has not been started");
            }
            SpeechFeatureData data = features.pollFirst();
            if (data != null) {
                return data;
            }
            if (finished) {
                throw new IllegalStateException("No speech feature data is available");
            }
        }
        return null;
    }

    /**
     * Gets recognition-result information from a recognition-result chunk.
     * Specify the character set designated in the {@code Content-Type} of the DDP response that returned
     * the recognition-result chunk.
     * A new {@link SpeechResultInformation} instance is returned each time.
     *
     * @param data the recognition-result chunk
     * @param charSet the character set to use when parsing the chunk
     * @return never returns normally on the desktop host
     * @throws UnsupportedOperationException because the desktop runtime does not support this optional device feature
     */
    public SpeechResultInformation getResultInformation(byte[] data, String charSet) {
        ensureAvailable();
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(charSet, "charSet");
        try {
            Charset.forName(charSet);
        } catch (UnsupportedCharsetException exception) {
            throw new java.lang.IllegalArgumentException("Unsupported character set: " + charSet);
        } catch (IllegalArgumentException exception) {
            throw new java.lang.IllegalArgumentException("Unsupported character set: " + charSet);
        }
        throw unsupported("getResultInformation(byte[], java.lang.String)");
    }

    private UnsupportedOperationException unsupported(String suffix) {
        return DoJaApiUnimplemented.unsupported(
                "com.nttdocomo.device.SpeechRecognizer." + suffix,
                UNRESOLVED_RESULT_CHUNK_MESSAGE
        );
    }

    private void ensureAvailable() {
        if (!available) {
            throw new IllegalStateException("SpeechRecognizer is unavailable");
        }
    }

    private static boolean isSupported() {
        return opendoja.host.OpenDoJaLaunchArgs.getBoolean(opendoja.host.OpenDoJaLaunchArgs.SPEECH_SUPPORTED);
    }

    private static String[] availableCodecs() {
        String raw = opendoja.host.OpenDoJaLaunchArgs.get(opendoja.host.OpenDoJaLaunchArgs.SPEECH_CODECS);
        String[] tokens = raw.split("[,;]");
        List<String> values = new ArrayList<>();
        for (String token : tokens) {
            String value = token.trim();
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values.isEmpty() ? new String[]{"audio/amr"} : values.toArray(new String[0]);
    }

    private static int[] availableTypes() {
        String raw = opendoja.host.OpenDoJaLaunchArgs.get(opendoja.host.OpenDoJaLaunchArgs.SPEECH_TYPES);
        String[] tokens = raw.split("[,;]");
        List<Integer> values = new ArrayList<>();
        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            try {
                values.add(Integer.decode(token.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        if (values.isEmpty()) {
            return new int[]{SpeechResultInformation.TYPE_NBEST};
        }
        int[] result = new int[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }

    private static byte[][] featureChunks() {
        String raw = opendoja.host.OpenDoJaLaunchArgs.get(opendoja.host.OpenDoJaLaunchArgs.SPEECH_FEATURE_CHUNKS);
        String[] tokens = raw.split(";");
        List<byte[]> result = new ArrayList<>();
        for (String token : tokens) {
            String value = token.trim();
            if (!value.isEmpty()) {
                result.add(decodeChunk(value));
            }
        }
        if (result.isEmpty()) {
            result.add(new byte[]{1, 2, 3, 4});
        }
        return result.toArray(new byte[0][]);
    }

    private static byte[] decodeChunk(String token) {
        String normalized = token.replace("0x", "").replace(" ", "");
        if ((normalized.length() & 1) == 1) {
            return token.getBytes(Charset.forName("US-ASCII"));
        }
        byte[] bytes = new byte[normalized.length() / 2];
        try {
            for (int i = 0; i < normalized.length(); i += 2) {
                bytes[i / 2] = (byte) Integer.parseInt(normalized.substring(i, i + 2), 16);
            }
            return bytes;
        } catch (NumberFormatException exception) {
            return token.getBytes(Charset.forName("US-ASCII"));
        }
    }

    private void requireSupportedCodec(String codec) {
        if (codec == null) {
            throw new NullPointerException("codec");
        }
        for (String supported : availableCodecs()) {
            if (supported.equalsIgnoreCase(codec.trim())) {
                return;
            }
        }
        throw new java.lang.IllegalArgumentException("Unsupported codec: " + codec);
    }

    private synchronized void scheduleFeatureProductionLocked() {
        byte[][] chunks = featureChunks();
        int readyTime = opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.SPEECH_READY_TIME);
        int interval = opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.SPEECH_FEATURE_INTERVAL);
        int level = opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.SPEECH_LEVEL);
        int snr = opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.SPEECH_SIGNAL_TO_NOISE_RATIO);
        int voiceActivity = opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.SPEECH_VOICE_ACTIVITY);
        featureTask = EXECUTOR.scheduleAtFixedRate(new Runnable() {
            private int index;

            @Override
            public void run() {
                SpeechListener currentListener;
                synchronized (SpeechRecognizer.this) {
                    if (!startCalledSinceReset || index >= chunks.length) {
                        if (featureTask != null) {
                            featureTask.cancel(false);
                            featureTask = null;
                        }
                        return;
                    }
                    features.addLast(new SpeechFeatureData(chunks[index].clone(), index == chunks.length - 1));
                    currentListener = listener;
                    index++;
                    if (index >= chunks.length && !started) {
                        finished = features.isEmpty();
                    }
                }
                if (currentListener != null) {
                    currentListener.notifyFeatureStored(
                            SpeechRecognizer.this,
                            new SpeechAssistantInformation(level, snr, voiceActivity)
                    );
                }
            }
        }, readyTime, Math.max(1, interval), TimeUnit.MILLISECONDS);
        timeoutTask = EXECUTOR.schedule(() -> {
            SpeechListener currentListener;
            synchronized (SpeechRecognizer.this) {
                if (!started) {
                    finished = features.isEmpty();
                    return;
                }
                started = false;
                finished = features.isEmpty();
                currentListener = listener;
            }
            notifyEvent(currentListener, SpeechListener.EVENT_STOP, SpeechListener.STOP_TIMEOUT);
        }, Math.max(readyTime, getMaxSpeechTime()), TimeUnit.MILLISECONDS);
    }

    private synchronized void cancelTimeoutLocked() {
        if (timeoutTask != null) {
            timeoutTask.cancel(false);
            timeoutTask = null;
        }
        if (features.isEmpty()) {
            finished = true;
        }
    }

    private synchronized void cancelTasksLocked() {
        if (featureTask != null) {
            featureTask.cancel(false);
            featureTask = null;
        }
        if (timeoutTask != null) {
            timeoutTask.cancel(false);
            timeoutTask = null;
        }
    }

    private void notifyEvent(SpeechListener target, int event, int param) {
        if (target != null) {
            target.notifyEvent(this, event, param);
        }
        synchronized (this) {
            if (event == SpeechListener.EVENT_STOP) {
                finished = features.isEmpty();
            }
        }
    }
}
