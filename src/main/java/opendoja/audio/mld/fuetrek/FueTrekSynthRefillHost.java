package opendoja.audio.mld.fuetrek;

import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/** Non-preview C6 port0 preparation/play/drain callbacks; public prefill/resources remain separate. */
public final class FueTrekSynthRefillHost {
    private static final int FOREGROUND = 1, PRODUCER = 2, OWNER = 1;
    private static final int PARENT = 2, PUBLIC = 3, MARKER = 3, SEQUENCE_END = 4,
            OUTPUT_END = 5, PUBLIC_PRODUCER = 6, PUBLIC_SEQUENCE_END = 7, PUBLIC_OUTPUT_END = 8;
    private static final int PREPARE_MARKER = 9, CHILD_PREPARE_MARKER = 10, PREPARED = 11;
    private static final int PARENT_PREPARED = 12, PUBLIC_PREPARED = 13;
    private final FueTrekDriverStream port;
    private final FueTrekCommandSerializer source;
    private final Runnable producer;
    private final FueTrekCallbackQueue callbacks;
    private final FueTrekCallbackTimer timers;
    private int nowMillis, bindingState, foregroundCalls, producerCalls, transferred, timerRequests;
    private final boolean completionMode;
    private final boolean startupMode;
    private final int tempoDivisor, startupLevel;
    private final FueTrekCommandPipe pipe;
    private final BooleanSupplier sourceEnded, yielded;
    private final Runnable clearYield;
    private final IntConsumer events;
    private FueTrekCallbackQueue parentCallbacks;
    private FueTrekStateMachine outer, child;
    private FueTrekStateMachine.State[] childStates, outerStates, prepareStates;
    private boolean draining, outputEnded, frozen, publicDone;
    private int heldPosition, markerWrap, lastMarker, loopCount;
    private boolean prepareMarker, playRequested;
    private int desiredPortState;
    private int positionOffset;
    private Runnable cacheReplay;
    private boolean publicMode, cacheReplayed;

    public FueTrekSynthRefillHost(FueTrekDriverStream port, FueTrekCommandSerializer source,
                                 Runnable producer) {
        this(port, source, null, producer, null, null, null, null);
    }

    public FueTrekSynthRefillHost(FueTrekDriverStream port, FueTrekCommandSerializer source,
                                 FueTrekCommandPipe pipe, Runnable producer,
                                 BooleanSupplier sourceEnded, BooleanSupplier yielded,
                                 Runnable clearYield, IntConsumer events) {
        this(port, source, pipe, producer, sourceEnded, yielded, clearYield, events, false, 100, 100);
    }

    public FueTrekSynthRefillHost(FueTrekDriverStream port, FueTrekCommandSerializer source,
                                 FueTrekCommandPipe pipe, Runnable producer,
                                 BooleanSupplier sourceEnded, BooleanSupplier yielded,
                                 Runnable clearYield, IntConsumer events,
                                 boolean prepare, int tempoDivisor, int startupLevel) {
        this.port = port;
        this.source = source;
        this.producer = java.util.Objects.requireNonNull(producer);
        this.pipe = pipe;
        this.sourceEnded = sourceEnded;
        this.yielded = yielded;
        this.clearYield = clearYield;
        this.events = events;
        completionMode = sourceEnded != null;
        if (prepare && !completionMode) throw new IllegalArgumentException("Preparation requires lifecycle callbacks");
        startupMode = prepare;
        this.tempoDivisor = tempoDivisor & 65535;
        this.startupLevel = startupLevel & 255;
        timers = new FueTrekCallbackTimer((function, owner, argument) -> {
            if (function != FOREGROUND || owner != OWNER || argument != 0)
                throw new AssertionError("Unknown synth timer callback");
            foreground();
        });
        callbacks = FueTrekCallbackQueue.synth(this::invokeCallback, owner -> owner);
        register(22, FOREGROUND);
        if (completionMode) initializeCompletion();
        else register(28, PRODUCER);
    }

    private void invokeCallback(int function, int context, int argument) {
        if (function == FOREGROUND) foreground();
        else if (function == PRODUCER) {
            if (completionMode) producerReady();
            else {
                producerCalls++;
                producer.run();
                register(28, PRODUCER);
            }
        } else if (completionMode && function == MARKER) {
            register(23, MARKER);
            if (bindingState == 2) {
                int value = port.readRegister(0x1a) & 127;
                if (value < lastMarker) markerWrap = (markerWrap + 1) & 65535;
                lastMarker = value;
                callbacks.dispatch(25, ((markerWrap << 7) + value) & 65535);
            }
        } else if (completionMode && function == SEQUENCE_END) {
            if (!callbacks.has(6)) parentCallbacks.dispatch(6, -1);
        } else if (completionMode && function == OUTPUT_END) {
            if (!callbacks.has(2)) parentCallbacks.dispatch(2, -1);
        } else if (startupMode && function == CHILD_PREPARE_MARKER) {
            parentCallbacks.dispatch(4, -1);
        } else if (startupMode && function == PREPARED) {
            //187b0: no resource children or other allocated ports in this binding.
            if (!callbacks.has(26)) callbacks.dispatch(7, -1);
        } else if (publicMode && function == PARENT_PREPARED) {
            if (!callbacks.has(7)) parentCallbacks.dispatch(7, -1);
        } else throw new AssertionError("Unknown synth refill callback");
    }

    private void initializeCompletion() {
        parentCallbacks = FueTrekCallbackQueue.parent((function, context, argument) -> {
            if (function == PUBLIC_PRODUCER) producePublic();
            else if (function == PUBLIC_SEQUENCE_END) events.accept(5);
            else if (function == PUBLIC_OUTPUT_END) events.accept(4);
            else if (startupMode && function == PREPARE_MARKER) prepareMarker = true;
            else if (publicMode && function == PUBLIC_PREPARED) events.accept(1);
            else throw new AssertionError("Unknown parent callback");
        }, owner -> owner);
        register(23, MARKER);
        if (!startupMode) armProducer();
        child = new FueTrekStateMachine(null, startupMode ? 0 : 3, startupMode ? 0 : 1, 1);
        outer = new FueTrekStateMachine(child, startupMode ? 0 : 2, startupMode ? 0 : 2, 1);
        var next1 = new FueTrekStateMachine.Transition(null, 2, 0, 1);
        var next2 = new FueTrekStateMachine.Transition(null, 2, 0, 2);
        var next3 = new FueTrekStateMachine.Transition(null, 2, 0, 3);
        childStates = new FueTrekStateMachine.State[]{
            new FueTrekStateMachine.State(null, m -> beginPlaying(), null, next1),
            new FueTrekStateMachine.State(null, m -> runningStep(), null, next2),
            new FueTrekStateMachine.State(null, m -> stopWhenDrained(), null, next3),
            new FueTrekStateMachine.State(null, m -> finishWhenStopped(), null)
        };
        outerStates = new FueTrekStateMachine.State[5];
        for (int i = 0; i < outerStates.length; i++)
            outerStates[i] = new FueTrekStateMachine.State(null,
                    m -> { throw new IllegalStateException("Startup/stop transition is not configured"); }, null);
        outerStates[2] = new FueTrekStateMachine.State(null, m -> {
            child.request(3);
            int result = child.run(childStates);
            return result == 2 && outer.different() ? 0 : result;
        }, m -> { if (bindingState != 0) freezePosition(); return 0; });
        if (startupMode) {
            prepareStates = new FueTrekStateMachine.State[]{
                new FueTrekStateMachine.State(null, m -> beginPreparation(), null, next1),
                new FueTrekStateMachine.State(null, m -> {
                    if (source.bufferStatus() != 0) refill();
                    return source.bufferStatus() == 0 ? 0 : 1;
                }, null, next2),
                new FueTrekStateMachine.State(null, m -> {
                    if (!fifoIdle() || port.readRegister(0x12) >>> 6 != 2) return 1;
                    stopPort();
                    return 0;
                }, null, next3),
                new FueTrekStateMachine.State(null, m -> {
                    if (!fifoIdle() || port.readRegister(0x12) >>> 6 != 0) return 1;
                    callbacks.dispatch(26, -1);
                    return 2;
                }, null)
            };
            outerStates[0] = new FueTrekStateMachine.State(null, null, null,
                    new FueTrekStateMachine.Transition(null, 1, 1, 1));
            outerStates[1] = new FueTrekStateMachine.State(null, m -> {
                child.request(3);
                int result = child.run(prepareStates);
                return result == 2 && outer.different() ? 0 : result;
            }, null);
        }
    }

    /**18570 for a previously allocated C6 port with no resource children. */
    void prepare() {
        prepareAt(0);
    }

    void prepareAt(int positionOffset) {
        if (!startupMode || playRequested) throw new IllegalStateException("Cannot prepare this binding");
        this.positionOffset = positionOffset;
        if (publicMode) {
            parentCallbacks.register(new FueTrekCallbackQueue.Registration(7, PUBLIC, PUBLIC_PREPARED, 0, 0, 0, 1));
            callbacks.register(new FueTrekCallbackQueue.Registration(7, PARENT, PARENT_PREPARED, OWNER, 0, 0, 0));
        }
        source.clearOutput();
        callbacks.clear(4);
        parentCallbacks.register(new FueTrekCallbackQueue.Registration(4, OWNER, PREPARE_MARKER, 0, 0, 0, 0));
        var marker = new FueTrekCallbackQueue.Registration(4, PARENT, CHILD_PREPARE_MARKER, OWNER, 0, 0, 0);
        callbacks.register(marker);
        source.serialize(25, 0, 0, marker.token);
        pipe.setParentFreeSlots(callbacks.freeRecords());
        pipe.submit(16, 0, 0, 1000);
        // The original C6 lookup has no matching static4b478 bank records.
        callbacks.register(new FueTrekCallbackQueue.Registration(26, OWNER, PREPARED, OWNER, 0, 0, 0));
        port.writeRegister(0x11, 128, nowMillis);
        draining = false;
        frozen = true;
        outer.request(1);
        foreground();
    }

    void configurePublicPrefill(Runnable replay) {
        if (!startupMode || playRequested) throw new IllegalStateException("Public prefill requires a fresh preparation binding");
        cacheReplay = java.util.Objects.requireNonNull(replay);
        publicMode = true;
    }

    public boolean isPrepared() {
        return startupMode && !playRequested && (outer.stateWords()[2] & 3) == 2;
    }

    /** Explicit public arm followed by18a90; source prefill/cache replay is external. */
    public void play() {
        if (!isPrepared()) throw new IllegalStateException("Play requires completed preparation");
        armProducer();
        desiredPortState = 2;
        outer.request(2);
        foreground();
        playRequested = true;
    }

    private void setTempo(int tempo) {
        if (tempo <= 400) port.writeRegister(0x1b, tempo < 200 ? tempo : 200 + (tempo - 200) / 4, nowMillis);
    }

    private void setPortState(int value) {
        if (bindingState == 0 && value != 0) markerWrap = lastMarker = 0;
        port.readRegister(0x12);
        bindingState = value;
        port.writeRegister(0x11, 128 | value << 3, nowMillis);
    }

    private int beginPreparation() {
        setTempo(Math.min(tempoDivisor, 100));
        port.writeRegister(0x13, port.readRegister(0x13) | 1, nowMillis);
        direct(0, 0xf0, 10, 63);
        direct(0, 0xcf, 1, 0xd9, 0, 1);
        // C6 descriptor95e68 host register pair0849/084c.
        direct(0, 0xcf, 8, 0x49, 0, startupLevel);
        direct(0, 0xcf, 8, 0x4c, 0, startupLevel);
        setPortState(2);
        direct(0, 0xcc, 15, 1);
        return 0;
    }

    private int beginPlaying() {
        setTempo(tempoDivisor);
        refill();
        callbacks.dispatch(28, -1);
        frozen = false;
        setPortState(2);
        return 0;
    }

    private void direct(int... bytes) {
        byte[] data = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) data[i] = (byte)bytes[i];
        port.writeBytes(data, 0, data.length);
    }

    private void stopPort() {
        direct(127, 0xd0);
        setPortState(0);
    }

    private void armProducer() {
        parentCallbacks.remove(1, PUBLIC, PUBLIC_PRODUCER, 0, 0);
        parentCallbacks.register(new FueTrekCallbackQueue.Registration(1, PUBLIC, PUBLIC_PRODUCER, 0, 0, 0, 1));
        callbacks.remove(28, PARENT, PRODUCER, 0, 0);
        callbacks.register(new FueTrekCallbackQueue.Registration(28, PARENT, PRODUCER, OWNER, 0, 0, 0));
    }

    private void producerReady() {
        callbacks.remove(28, PARENT, PRODUCER, 0, 0);
        pipe.setParentFreeSlots(callbacks.freeRecords());
        if (pipe.yieldReason() == 0) parentCallbacks.dispatch(1, -1);
        else {
            callbacks.register(new FueTrekCallbackQueue.Registration(28, PARENT, PRODUCER, OWNER, 0, 0, 0));
        }
    }

    /** One-pass08cf0 parent callback, including its actual309/30b drain notifications. */
    private void producePublic() {
        if (publicDone) return;
        parentCallbacks.remove(1, PUBLIC, PUBLIC_PRODUCER, 0, 0);
        if (publicMode && !cacheReplayed) {
            pipe.setParentFreeSlots(callbacks.freeRecords());
            cacheReplay.run();
            cacheReplayed = true;
        }
        do {
            producerCalls++;
            pipe.setParentFreeSlots(callbacks.freeRecords());
            producer.run();
            if (sourceEnded.getAsBoolean()) {
                loopCount = (loopCount + 1) & 65535;
                publicDone = true;
                parentCallbacks.remove(6, PUBLIC, PUBLIC_SEQUENCE_END, 0, 0);
                parentCallbacks.register(new FueTrekCallbackQueue.Registration(6, PUBLIC, PUBLIC_SEQUENCE_END, 0, 0, 0, 1));
                var marker = new FueTrekCallbackQueue.Registration(6, PARENT, SEQUENCE_END, OWNER, 0, 0, 0);
                callbacks.register(marker);
                pipe.setParentFreeSlots(callbacks.freeRecords());
                pipe.submit(22, 0, 0, 0);
                source.serialize(25, 0, 0, marker.token);
                draining = true;
                parentCallbacks.remove(2, PUBLIC, PUBLIC_OUTPUT_END, 0, 0);
                parentCallbacks.register(new FueTrekCallbackQueue.Registration(2, PUBLIC, PUBLIC_OUTPUT_END, 0, 0, 0, 1));
                callbacks.remove(2, PARENT, OUTPUT_END, 0, 0);
                callbacks.register(new FueTrekCallbackQueue.Registration(2, PARENT, OUTPUT_END, OWNER, 0, 0, 0));
            }
            boolean paused = yielded.getAsBoolean();
            clearYield.run();
            if (paused) { armProducer(); return; }
        } while (!publicDone);
    }

    private void register(int category, int function) {
        callbacks.register(new FueTrekCallbackQueue.Registration(category, OWNER, function, 0, 0, 0, 0));
    }

    public void setBindingState(int state) { bindingState = state; }

    private int refill() {
        int used = FueTrekSynthTransport.refill(source, port);
        transferred += used;
        return used;
    }

    /**19360 re-registers its category22 callback before the running29e20 foreground step. */
    private void foreground() {
        foregroundCalls++;
        callbacks.remove(22, OWNER, FOREGROUND, 0, 0);
        register(22, FOREGROUND);
        if (completionMode) {
            outer.activate();
            outer.run(outerStates);
        } else runningStep();
    }

    private int runningStep() {
        int used = refill();
        if (!draining) {
            callbacks.dispatch(28, -1);
            if (used == 0 && bindingState == 2) {
                refill();
                if ((port.readRegister(0x12) & 2) != 0) {
                    timerRequests++;
                    timers.schedule(10, OWNER, FOREGROUND, 0, nowMillis);
                }
            }
        }
        return draining && source.readableBytes() == 0 ? 0 : 1;
    }

    private boolean fifoIdle() {
        int status = port.readRegister(0x12);
        return (status & 3) == 2;
    }

    private void freezePosition() {
        if (frozen) return;
        int position = (port.readRegister(0x17) & 15) << 16
                | port.readRegister(0x18) << 8 | port.readRegister(0x19);
        heldPosition = positionOffset + position * 25 / 10;
        frozen = true;
    }

    private int stopWhenDrained() {
        if (!fifoIdle()) return 1;
        freezePosition();
        port.writeBytes(new byte[]{127, (byte)0xd0}, 0, 2);
        port.readRegister(0x12);
        bindingState = 0;
        int control = port.portRegisterBytes()[1] & 0xe0;
        port.writeRegister(0x11, control, nowMillis);
        return 0;
    }

    private int finishWhenStopped() {
        if (timers.contains(OWNER, FOREGROUND)) return 1;
        int state = port.readRegister(0x12) >>> 6;
        if (!fifoIdle() || state != 0) return 1;
        outputEnded = true;
        callbacks.dispatch(2, -1);
        return 2;
    }

    /**
     * Explicit host boundary: poll port, pump VM callback timers, then pump synth IRQs.
     * Other ports and hardware timers are idle. VM timer/thread phase is not inferred here.
     */
    public void poll(int nowMillis) {
        this.nowMillis = nowMillis;
        port.pollPort(nowMillis, true);
        timers.poll(nowMillis);
        //0a9d0 collects/acknowledges IRQs before1b310 invokes any component callbacks.
        boolean marker = false, work = false, position = false;
        for (int pass = 0; pass < 1000; pass++) {
            if ((port.readRegister(6) & 16) == 0) break;
            int bits = port.readRegister(0x10);
            marker |= (bits & 32) != 0;
            position |= (bits & 16) != 0;
            work |= (bits & (32 | 2 | 4 | 1 | 128)) != 0;
        }
        if (marker) callbacks.dispatch(23, -1);
        if (work) callbacks.dispatch(22, -1);
        if (position) callbacks.dispatch(24, -1);
    }

    /** Portable host state followed by the complete callback pool/head state. */
    public int[] stateWords() {
        int[] queued = callbacks.stateWords();
        int[] timed = timers.stateWords();
        int[] words = new int[6 + timed.length + queued.length];
        int index = 0;
        words[index++] = nowMillis;
        words[index++] = bindingState;
        words[index++] = foregroundCalls;
        words[index++] = producerCalls;
        words[index++] = transferred;
        words[index++] = timerRequests;
        System.arraycopy(timed, 0, words, index, timed.length);
        index += timed.length;
        System.arraycopy(queued, 0, words, index, queued.length);
        if (completionMode) {
            int[] parent = parentCallbacks.stateWords();
            int[] extended = java.util.Arrays.copyOf(words, words.length + 17 + parent.length);
            index = words.length;
            for (int value : new int[]{draining ? 1 : 0, outputEnded ? 1 : 0, frozen ? 1 : 0,
                    heldPosition, markerWrap, lastMarker, publicDone ? 1 : 0, 1, loopCount,
                    yielded.getAsBoolean() ? 1 : 0, pipe.ownerWord()}) extended[index++] = value;
            for (int value : outer.stateWords()) extended[index++] = value;
            for (int value : child.stateWords()) extended[index++] = value;
            System.arraycopy(parent, 0, extended, index, parent.length);
            if (startupMode) {
                int length = extended.length;
                extended = java.util.Arrays.copyOf(extended, length + 6);
                extended[length] = 1; //414: no resource children remain unprepared.
                extended[length + 1] = playRequested ? 1 : 0;
                extended[length + 2] = 1; //444: preparation traffic exists.
                extended[length + 3] = prepareMarker ? 1 : 0;
                extended[length + 4] = desiredPortState;
                extended[length + 5] = playRequested ? 1 : 0;
            }
            if (publicMode) {
                int length = extended.length;
                extended = java.util.Arrays.copyOf(extended, length + 2);
                extended[length] = 4;
                extended[length + 1] = cacheReplayed ? 1 : 0;
            }
            return extended;
        }
        return words;
    }
}
