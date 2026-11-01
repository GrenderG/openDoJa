package opendoja.audio.mld.fuetrek;

import java.util.ArrayDeque;

/** Integer command clock from SDK1000dd20; the callback clock is in milliseconds. */
public final class FueTrekSequencerClock {
    public interface Listener {
        /** Check gates against elapsed ticks; commit subtracts ticks from surviving gates. */
        void elapsedTicks(int ticks, boolean commit);

        void command(int token);

        /** False leaves the clock in its payload phase, without checking time or gates again. */
        default boolean tryCommand(int token) {
            command(token);
            return true;
        }

        default void positionWrapped() {}
        default void positionUpdated(int units) {}
    }

    /** A streaming source consumes a complete VLQ before its command payload is available. */
    public interface Source {
        Integer readDeltaTicks();
        boolean dispatch();
    }

    private record Command(int ticks, int nextTickMicros, int token) {}

    private final ArrayDeque<Command> queue = new ArrayDeque<>();
    private Command pending;
    private int phase;
    private Source pendingSource;
    private int tickMicros;
    private int timeDivisor;
    private final int positionQuantumMicros;
    private int deltaTicks;
    private int elapsedMicros;
    private int startMillis;
    private int delayMillis;
    private int snapshotMicros;
    private int carryMillis;

    public FueTrekSequencerClock(int tickMicros, int timeDivisor, int positionQuantumMicros) {
        if (tickMicros <= 0 || timeDivisor <= 0 || positionQuantumMicros <= 0)
            throw new IllegalArgumentException("Clock units and divisor must be positive");
        this.tickMicros = tickMicros;
        this.timeDivisor = timeDivisor;
        this.positionQuantumMicros = positionQuantumMicros;
    }

    /** Native155f0: unsigned integer microseconds per tick, with a minimum of one. */
    public static int tickDurationMicros(int timebase, int tempo) {
        if (timebase <= 0 || tempo <= 0)
            throw new IllegalArgumentException("Timebase and tempo must be positive");
        return Math.max(1, Integer.divideUnsigned(60000000, (timebase & 0xffff) * tempo));
    }

    /** Append a decoded driver command; zero nextTickMicros leaves tempo unchanged. */
    public void enqueue(int ticks, int nextTickMicros, int token) {
        if (ticks < 0 || ticks > 0x0fffffff || nextTickMicros < 0 || nextTickMicros > 0x00ffffff)
            throw new IllegalArgumentException("Invalid driver delta or tempo");
        queue.addLast(new Command(ticks, nextTickMicros, token));
    }

    public boolean hasQueuedCommands() {
        return pending != null || !queue.isEmpty();
    }

    public void setTimeDivisor(int divisor) {
        if (divisor <= 0) throw new IllegalArgumentException("Clock divisor must be positive");
        timeDivisor = divisor;
    }

    /** Native B0 accepts the complete unsigned24 tempo word. */
    public void setTickMicros(int micros) {
        if (micros < 0 || micros > 0x00ffffff) throw new IllegalArgumentException("Invalid driver tempo");
        tickMicros = micros;
    }

    /** Initial emulator register state before any port has started. */
    void initializePort() {
        queue.clear();
        pending = null;
        pendingSource = null;
        phase = tickMicros = timeDivisor = deltaTicks = elapsedMicros = 0;
        startMillis = delayMillis = snapshotMicros = carryMillis = 0;
    }

    /** Native0dc20 resets selected timing fields, retaining diagnostic latches. */
    void startPort(int micros) {
        phase = elapsedMicros = carryMillis = 0;
        tickMicros = micros;
        pending = null;
        pendingSource = null;
    }

    /** Native0dca0 retains the previous carry, delta, snapshot and time latches. */
    void stopPort() {
        phase = elapsedMicros = 0;
        pending = null;
        pendingSource = null;
    }

    void pausePort(int nowMillis) { startMillis = nowMillis; }
    int elapsedMicros() { return elapsedMicros; }

    /** Poll at an SDK callback boundary. All integer overflow is native unsigned32. */
    public void advance(int nowMillis, Listener listener) {
        advance(nowMillis, null, listener);
    }

    public void advance(int nowMillis, Source source, Listener listener) {
        while (true) {
            if (phase == 0) {
                if (!queue.isEmpty()) {
                    pending = queue.removeFirst();
                    pendingSource = null;
                } else {
                    Integer ticks = source == null ? null : source.readDeltaTicks();
                    if (ticks == null) return;
                    pending = new Command(ticks, 0, 0);
                    pendingSource = source;
                }
                deltaTicks = pending.ticks;
                snapshotMicros = elapsedMicros;
                delayMillis = Integer.divideUnsigned(tickMicros * deltaTicks, 1000);
                startMillis = nowMillis;
                phase = 1;
            }
            if (phase == 1) {
                int elapsedMillis = Integer.divideUnsigned((nowMillis - startMillis) * 100, timeDivisor)
                    + carryMillis;
                boolean ready = Integer.compareUnsigned(elapsedMillis, delayMillis) > 0;
                if (ready) carryMillis = elapsedMillis - delayMillis;
                int micros = elapsedMillis * 1000;
                elapsedMicros = snapshotMicros - carryMillis * 1000 + micros;
                // DF10 writes the wrapped, quantized position back into the accumulator.
                int units = Integer.divideUnsigned(elapsedMicros, positionQuantumMicros);
                listener.positionUpdated(units);
                if (Integer.compareUnsigned(units, 0x000fffff) > 0) {
                    elapsedMicros = (units & 0x000fffff) * positionQuantumMicros;
                    listener.positionWrapped();
                }
                int ticks = Integer.divideUnsigned(micros, tickMicros);
                listener.elapsedTicks(ticks, false);
                if (!ready) return;
                phase = 2;
                listener.elapsedTicks(ticks, true);
            }
            Command command = pending;
            if (!(pendingSource == null ? listener.tryCommand(command.token) : pendingSource.dispatch())) return;
            if (command.nextTickMicros != 0) tickMicros = command.nextTickMicros;
            pending = null;
            pendingSource = null;
            phase = 0;
        }
    }

    /** Native driver state at +4,+8,+c,+10,+14,+24,+28,+2c,+30, for diagnostics. */
    public int[] stateWords() {
        return new int[]{phase, tickMicros, deltaTicks, elapsedMicros,
                timeDivisor, startMillis, delayMillis, snapshotMicros, carryMillis};
    }
}
