package opendoja.audio.mld.fuetrek;

/** SDK0b680/0b5e0 timer requests and pump, with an explicit host millisecond clock. */
public final class FueTrekCallbackTimer {
    private final FueTrekCallbackQueue callbacks;
    private boolean running, pollingEnabled;
    private int contextClock, lastPollClock;

    public FueTrekCallbackTimer(FueTrekCallbackQueue.Listener listener) {
        callbacks = FueTrekCallbackQueue.timers(listener, owner -> owner);
    }

    /** The native clock exposes unsigned16 units of0.1ms, independently of the synth clock. */
    private static int clock(int nowMillis) { return nowMillis * 10 & 65535; }

    public void schedule(int delayMillis, int owner, int function, int argument, int nowMillis) {
        int now = clock(nowMillis), elapsed = 0;
        if (running) {
            elapsed = (now - contextClock) & 65535;
            contextClock = now;
        }
        int ticks = Math.min(65535, elapsed + (delayMillis & 65535) * 10);
        callbacks.register(new FueTrekCallbackQueue.Registration(29, owner, function, argument, ticks, 0, 0));
        if (!running) {
            contextClock = lastPollClock = now;
            pollingEnabled = running = true;
        }
    }

    /** Explicit VM timer-service boundary; thread/wakeup phase is supplied by the caller. */
    public void poll(int nowMillis) {
        if (!pollingEnabled) return;
        int now = clock(nowMillis);
        if (running) {
            contextClock = now;
            if (callbacks.has(29)) pollingEnabled = true;
        }
        int elapsed = (now - lastPollClock) & 65535;
        lastPollClock = now;
        if (elapsed != 0) {
            callbacks.dispatch(29, elapsed);
            lastPollClock = clock(nowMillis);
            if (running && !callbacks.has(29)) pollingEnabled = running = false;
        }
    }

    public boolean contains(int owner, int function) { return callbacks.contains(29, owner, function); }

    public int[] stateWords() {
        int[] queued = callbacks.stateWords();
        int[] words = new int[4 + queued.length];
        words[0] = running ? 1 : 0;
        words[1] = contextClock;
        words[2] = lastPollClock;
        words[3] = pollingEnabled ? 1 : 0;
        System.arraycopy(queued, 0, words, 4, queued.length);
        return words;
    }
}
