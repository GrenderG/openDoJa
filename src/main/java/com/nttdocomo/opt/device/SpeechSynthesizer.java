package com.nttdocomo.opt.device;

import com.nttdocomo.lang.XString;
import com.nttdocomo.lang._XStringSupport;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Provides the means to execute the speech-synthesis function.
 */
public class SpeechSynthesizer {
    /** Attribute used to set or get the voice kind (=0). */
    public static final int SPEECH_VOICE = 0;
    /** Attribute used to set or get the speech speed (=1). */
    public static final int SPEECH_SPEED = 1;
    /** Constant meaning the male voice (=0). */
    public static final int MALE_VOICE = 0;
    /** Constant meaning the female voice (=1). */
    public static final int FEMALE_VOICE = 1;
    /** Constant meaning to follow the terminal setting (=2). */
    public static final int DEFAULT_VOICE = 2;
    /** The minimum speech-speed value (=1). */
    public static final int MIN_SPEED = 1;
    /** The maximum speech-speed value (=6). */
    public static final int MAX_SPEED = 6;

    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "openDoJa-speech");
        thread.setDaemon(true);
        return thread;
    });
    private static final SpeechSynthesizer INSTANCE = new SpeechSynthesizer();
    private static final int DEFAULT_HOST_VOICE = FEMALE_VOICE;
    private static final int DEFAULT_HOST_SPEED = 3;

    private SpeechListener listener;
    private int voice = DEFAULT_VOICE;
    private int speed = DEFAULT_HOST_SPEED;
    private ScheduledFuture<?> activeSpeech;

    /**
     * Applications cannot create instances of this class directly.
     */
    protected SpeechSynthesizer() {
    }

    /**
     * Gets the speech-synthesis object.
     * When this method is called for the first time, the object is created and
     * returned.
     * After that, a reference to the same object is always returned.
     *
     * @return the speech-synthesis object
     */
    public static synchronized SpeechSynthesizer getSpeechSynthesizer() {
        return INSTANCE;
    }

    /**
     * Sets the listener that receives events notified from the speech-synthesis
     * function.
     * Only one listener can be registered.
     *
     * @param listener the listener to register, or {@code null} to clear the
     *        current listener
     */
    public synchronized void setSpeechListener(SpeechListener listener) {
        this.listener = listener;
    }

    /**
     * Speaks a normal string.
     * If this method is called while speaking is in progress, the current
     * speech is canceled and the new request is executed.
     *
     * @param words the string to speak
     * @throws IllegalArgumentException if the number of characters in
     *         {@code words} exceeds the maximum value
     */
    public void speak(String words) {
        int length = words == null ? 0 : words.length();
        if (length > 1000) {
            throw new IllegalArgumentException("Speech text exceeds 1000 characters");
        }
        ScheduledFuture<?> previous;
        synchronized (this) {
            previous = activeSpeech;
            if (previous != null) {
                previous.cancel(false);
                activeSpeech = null;
            }
        }
        if (previous != null) {
            notifyListener(SpeechListener.SPEECH_CANCEL);
        }
        if (words == null || words.isEmpty()) {
            notifyListener(SpeechListener.SPEECH_COMPLETE);
            return;
        }

        long durationMillis = java.lang.Math.max(10L,
                java.lang.Math.min(2_000L, (long) length * 40L / java.lang.Math.max(MIN_SPEED, speed)));
        synchronized (this) {
            activeSpeech = EXECUTOR.schedule(() -> {
                synchronized (SpeechSynthesizer.this) {
                    activeSpeech = null;
                }
                notifyListener(SpeechListener.SPEECH_COMPLETE);
            }, durationMillis, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Speaks an {@link XString}.
     *
     * @param words the text to speak
     * @throws IllegalArgumentException if the number of characters in
     *         {@code words} exceeds the maximum value
     */
    public void speak(XString words) {
        speak(_XStringSupport.valueOrNull(words));
    }

    /**
     * Cancels speech.
     * If speaking is not in progress, nothing happens.
     */
    public void stop() {
        ScheduledFuture<?> previous;
        synchronized (this) {
            previous = activeSpeech;
            if (previous == null) {
                return;
            }
            previous.cancel(false);
            activeSpeech = null;
        }
        notifyListener(SpeechListener.SPEECH_CANCEL);
    }

    /**
     * Sets an attribute value related to speech synthesis.
     * If a non-existent attribute is specified, the request is ignored.
     *
     * @param attr the attribute kind
     * @param value the attribute value
     * @throws IllegalArgumentException if {@code value} is invalid for a valid
     *         attribute
     */
    public synchronized void setAttribute(int attr, int value) {
        switch (attr) {
            case SPEECH_VOICE -> {
                if (value != MALE_VOICE && value != FEMALE_VOICE && value != DEFAULT_VOICE) {
                    throw new IllegalArgumentException("Unsupported speech voice: " + value);
                }
                voice = value;
            }
            case SPEECH_SPEED -> {
                if (value < MIN_SPEED || value > MAX_SPEED) {
                    throw new IllegalArgumentException("Speech speed out of range: " + value);
                }
                speed = value;
            }
            default -> {
            }
        }
    }

    /**
     * Gets an attribute value related to speech synthesis.
     *
     * @param attr the attribute kind
     * @return the attribute value
     * @throws IllegalArgumentException if {@code attr} is invalid
     */
    public synchronized int getAttribute(int attr) {
        return switch (attr) {
            case SPEECH_VOICE -> voice == DEFAULT_VOICE ? DEFAULT_HOST_VOICE : voice;
            case SPEECH_SPEED -> speed;
            default -> throw new IllegalArgumentException("Unsupported speech attribute: " + attr);
        };
    }

    private void notifyListener(int state) {
        SpeechListener currentListener;
        synchronized (this) {
            currentListener = listener;
        }
        if (currentListener != null) {
            currentListener.speechAction(state);
        }
    }
}
