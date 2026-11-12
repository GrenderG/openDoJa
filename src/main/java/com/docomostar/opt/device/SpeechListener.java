package com.docomostar.opt.device;

public interface SpeechListener extends com.docomostar.util.EventListener {
    int SPEECH_COMPLETE = 0, SPEECH_CANCEL = 1;
    void speechAction(int state);
}
