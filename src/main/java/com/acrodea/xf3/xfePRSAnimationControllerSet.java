package com.acrodea.xf3;

public class xfePRSAnimationControllerSet extends xfeController {
    private boolean playing;
    private boolean looping;
    private int tick;

    public xfePRSAnimationControllerSet(xfeRoot root, String name) {
        super(root, name);
    }

    public void setPlaying(boolean playing) {
        this.playing = playing;
    }

    public void setLooping(boolean looping) {
        this.looping = looping;
    }

    public void setTick(int tick) {
        this.tick = tick;
    }

    public boolean isPlaying() {
        return playing;
    }

    public boolean isLooping() {
        return looping;
    }

    public int getTick() {
        return tick;
    }
}
