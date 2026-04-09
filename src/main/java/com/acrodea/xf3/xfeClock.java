package com.acrodea.xf3;

public class xfeClock {
    private long tick;

    public xfeClockAdvanceState advance() {
        tick++;
        xfeClockAdvanceState state = new xfeClockAdvanceState();
        state.set(true, true);
        return state;
    }

    public void resetTick(long tick) {
        this.tick = tick;
    }

    public long getTick() {
        return tick;
    }
}
