package com.docomostar.opt.device;

/** Uses DoJa's existing speech lifecycle and completion callbacks. */
public class SpeechSynthesizer {
    public static final int SPEECH_VOICE = 0, SPEECH_SPEED = 1, MALE_VOICE = 0,
            FEMALE_VOICE = 1, DEFAULT_VOICE = 2, MIN_SPEED = 1, MAX_SPEED = 6;
    private static final SpeechSynthesizer INSTANCE = new SpeechSynthesizer();
    private final com.nttdocomo.opt.device.SpeechSynthesizer delegate =
            com.nttdocomo.opt.device.SpeechSynthesizer.getSpeechSynthesizer();
    protected SpeechSynthesizer() { }
    public static SpeechSynthesizer getSpeechSynthesizer() { return INSTANCE; }
    public void setSpeechListener(SpeechListener listener) { delegate.setSpeechListener(listener == null ? null : listener::speechAction); }
    public void speak(String words) { delegate.speak(words); }
    public void speak(com.docomostar.lang.XString words) { delegate.speak(com.docomostar.lang._XStringSupport.unwrap(words)); }
    public void stop() { delegate.stop(); }
    public void setAttribute(int attribute, int value) { delegate.setAttribute(attribute, value); }
    public int getAttribute(int attribute) { return delegate.getAttribute(attribute); }
}
