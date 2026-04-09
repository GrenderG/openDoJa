package com.acrodea.xf3;

public class xfeClockAdvanceState {
    private boolean ready;
    private boolean didIteration;

    public boolean isReady() {
        return ready;
    }

    public boolean didIteration() {
        return didIteration;
    }

    public void set(boolean ready, boolean didIteration) {
        this.ready = ready;
        this.didIteration = didIteration;
    }
}
