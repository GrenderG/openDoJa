package opendoja.audio.mld.fuetrek;

import opendoja.audio.mld.*;

public final class FueTrekMLDPlaybackEngine
    implements MLDPlaybackEngine
{
    private final Sampler sampler;
    private final FueTrekResourceAudioEngine resourceAudioEngine;
    private final FueTrekMfiCommandCompiler compiler;
    private FueTrekCommandSerializer serializer;
    private FueTrekCommandPipe commandPipe;
    private FueTrekDriverStream driver;
    private final byte[] transport = new byte[1536];
    private int hostVolumeLevel = 100;
    private boolean readAhead;
    private boolean producerYield;
    private int sourceDelta;
    private boolean sourceDeltaConsumed;
    private boolean portControlMode;
    private boolean consumerStarted;
    private FueTrekSynthRefillHost interruptRefillHost;

    public FueTrekMLDPlaybackEngine(Sampler sampler, MLD mld, float sampleRate)
    {
        this.sampler = sampler;
        this.resourceAudioEngine = new FueTrekResourceAudioEngine(mld,
            sampleRate);
        this.resetDriver();
        String version = mld.getVersion();
        int major = version == null ? 5 : Integer.parseInt(version.substring(0, 2));
        // Classic format descriptor95b80 -> dependency table95b10 selects
        // tone profile1;13de0 passes it to14cd0/14d50 unchanged.
        this.compiler = new FueTrekMfiCommandCompiler(major, 1, new FueTrekMfiCommandCompiler.Listener() {
            public void command(int command, int channel, int delta, int argument) {
                if (sampler instanceof FueTrekSampler fueTrek)
                    fueTrek.setMfiResourceGlobal(command, argument);
                if (command == 14) resourceAudioEngine.setMasterLevel(argument);
                else if (command == 15) resourceAudioEngine.setMasterPan(argument);
                commandPipe.submit(command, channel, delta, argument);
                if (!readAhead) drainEventCommands();
            }
            public void channelEnabled(int channel, int value) { serializer.setChannelEnabled(channel, value); }
        });
    }

    private void resetDriver() {
        this.portControlMode = this.consumerStarted = false;
        this.interruptRefillHost = null;
        this.serializer = new FueTrekCommandSerializer(1536, 1000, 1, 0);
        // The current event bridge has no queued component jobs. Its virtual
        // parent satisfies the native kind3 reserve (20 records); timed
        // production will use the pipe's yield callback instead of draining
        // immediately after each event.
        this.producerYield = false;
        this.commandPipe = new FueTrekCommandPipe(this.serializer, 0, 20, 20, 0, () -> this.producerYield = true);
        this.driver = new FueTrekDriverStream(5000, 2, 10000, 100, 2500, this::moduleCall);
    }

    private void moduleCall(int entry, int... arguments) {
        if (this.sampler instanceof FueTrekSampler fueTrek) fueTrek.driverModuleCall(entry, arguments);
    }

    @Override
    public void setReadAheadMode(boolean enabled) { this.readAhead = enabled; }

    @Override
    public void beginSourceEvent(int ticks) { this.sourceDelta = ticks; this.sourceDeltaConsumed = false; }

    private int consumeSourceDelta() {
        if (!this.readAhead || this.sourceDeltaConsumed) return 0;
        this.sourceDeltaConsumed = true;
        return this.sourceDelta;
    }

    @Override
    public void endSourceEvent(boolean trackEnded, boolean instantaneousEnd) {
        if (!this.sourceDeltaConsumed) this.compiler.nop(consumeSourceDelta());
        if (trackEnded) this.compiler.endTrack(instantaneousEnd);
    }

    /**
     * Explicit host input: clear the yield flag (native312 with a nonzero
     * query-and-clear argument). Native313 broadcasts a child message instead;
     * the public SDK's resume/refill policy is a separate integration step.
     */
    public void resumeProducer() { this.producerYield = false; }
    public boolean producerYieldRequested() { return this.producerYield; }
    public int readCompiledBytes(byte[] bytes, int offset, int length) { return this.serializer.readInto(bytes, offset, length); }
    public byte[] compiledStateBytes() { return this.serializer.stateBytes(); }
    public byte[] producerStateBytes() { return this.compiler.stateBytes(); }
    public void beginPublicPrefill(int millis) { this.compiler.beginPrefill(millis); }
    public void setSourceMode(int mode) { this.compiler.setSourceMode(mode); }
    public void restartClassicSource() { this.compiler.restartClassicSource(); }
    public FueTrekMfiCommandCompiler.PrefillWait prefillWait(int ticks) { return this.compiler.prefillWait(ticks); }
    public int[] prefillStateWords() { return this.compiler.prefillStateWords(); }
    public int[] commandPipeStateWords() { return this.commandPipe.stateWords(); }

    /** Feed bounded transport bytes, then run the actual timed driver instead of the event bridge. */
    public int consumeCompiledAt(int nowMillis, int byteBudget) {
        if (this.portControlMode) throw new IllegalStateException("Use the port-control consumer");
        int used = this.transferCompiledBytes(byteBudget);
        this.driver.advance(nowMillis);
        return used;
    }

    /** Select the initialized SDK512-byte port before the first consumer action. */
    public void setPortControlMode() {
        if (!this.readAhead || this.consumerStarted)
            throw new IllegalStateException("Configure a read-ahead port before consuming bytes");
        this.driver = FueTrekDriverStream.initializedPort(this::moduleCall);
        this.portControlMode = true;
    }

    /** Bounded native FIFO transport followed by original0d8c0 control/clock semantics. */
    public int consumeCompiledPortAt(int nowMillis, int byteBudget, int control, int tempo) {
        if (!this.portControlMode) throw new IllegalStateException("Enable port control first");
        int used = this.transferCompiledBytes(byteBudget);
        this.driver.pollPortControl(control, tempo, nowMillis, true);
        return used;
    }

    /** Register-driven09d50 refill; the caller supplies the host callback boundary. */
    public int refillCompiledPortAt(int nowMillis) {
        if (!this.portControlMode) throw new IllegalStateException("Enable port control first");
        this.consumerStarted = true;
        int used = FueTrekSynthTransport.refill(this.serializer, this.driver);
        this.driver.pollPort(nowMillis, true);
        return used;
    }

    public void pollCompiledPortAt(int nowMillis) {
        if (!this.portControlMode) throw new IllegalStateException("Enable port control first");
        this.consumerStarted = true;
        this.driver.pollPort(nowMillis, true);
    }

    public void writeCompiledPortRegister(int address, int value, int nowMillis) {
        if (!this.portControlMode) throw new IllegalStateException("Enable port control first");
        this.consumerStarted = true;
        this.driver.writeRegister(address, value, nowMillis);
        if (this.interruptRefillHost != null && (address & 255) == 0x11)
            this.interruptRefillHost.setBindingState(value >>> 3 & 3);
    }

    /** Bind a running port0 producer callback; startup, resources and completion remain external. */
    public void enableInterruptRefill(Runnable producer) {
        if (!this.portControlMode || this.consumerStarted)
            throw new IllegalStateException("Configure interrupt refill before port I/O");
        this.interruptRefillHost = new FueTrekSynthRefillHost(this.driver, this.serializer, producer);
    }

    /** Bind a one-pass parent/child drain and completion lifecycle on the running port. */
    public void enableInterruptCompletion(Runnable producer, java.util.function.BooleanSupplier ended,
                                          java.util.function.IntConsumer events) {
        if (!this.portControlMode || this.consumerStarted)
            throw new IllegalStateException("Configure interrupt completion before port I/O");
        this.interruptRefillHost = new FueTrekSynthRefillHost(this.driver, this.serializer, this.commandPipe,
                producer, ended, () -> this.producerYield, this::resumeProducer, events);
    }

    /** Prepare an allocated C6 port without resource children; public source prefill remains external. */
    public void enableInterruptPreparation(Runnable producer, java.util.function.BooleanSupplier ended,
                                           java.util.function.IntConsumer events, int tempo, int startupLevel) {
        if (!this.portControlMode || this.consumerStarted)
            throw new IllegalStateException("Configure interrupt preparation before port I/O");
        this.serializer.setMode(1); //18250 note mode1 -> ordinary port format2.
        this.interruptRefillHost = new FueTrekSynthRefillHost(this.driver, this.serializer, this.commandPipe,
                producer, ended, () -> this.producerYield, this::resumeProducer, events, true, tempo, startupLevel);
        this.driver.writeRegister(0x1b, 100, 0);
        this.interruptRefillHost.prepare();
        this.consumerStarted = true;
    }

    public boolean isCompiledPortPrepared() {
        return this.interruptRefillHost != null && this.interruptRefillHost.isPrepared();
    }

    /** Allocate the internal public whole-file lifecycle before source prefill. */
    public void configurePublicPreparation(Runnable producer, java.util.function.BooleanSupplier ended,
                                           java.util.function.IntConsumer events) {
        if (!this.portControlMode || this.consumerStarted)
            throw new IllegalStateException("Configure public preparation before port I/O");
        this.serializer.setMode(1);
        this.interruptRefillHost = new FueTrekSynthRefillHost(this.driver, this.serializer, this.commandPipe,
                producer, ended, () -> this.producerYield, this::resumeProducer, events, true, 100, 100);
        this.interruptRefillHost.configurePublicPrefill(this.compiler::replayCache);
        this.driver.writeRegister(0x1b, 100, 0);
    }

    public void finishPublicPrefill() {
        if (this.interruptRefillHost == null) throw new IllegalStateException("Configure public preparation first");
        this.compiler.setSourceMode(1);
        this.interruptRefillHost.prepareAt(Integer.divideUnsigned(this.compiler.prefillStateWords()[2], 1000));
        this.consumerStarted = true;
    }

    public void playPreparedPort() {
        if (this.interruptRefillHost == null) throw new IllegalStateException("Enable interrupt preparation first");
        this.interruptRefillHost.play();
    }

    public void pollInterruptRefillAt(int nowMillis) {
        if (this.interruptRefillHost == null) throw new IllegalStateException("Enable interrupt refill first");
        this.consumerStarted = true;
        this.interruptRefillHost.poll(nowMillis);
    }

    public int[] interruptRefillStateWords() {
        if (this.interruptRefillHost == null) throw new IllegalStateException("Enable interrupt refill first");
        return this.interruptRefillHost.stateWords();
    }

    public byte[] interruptRefillDriverStateBytes() {
        if (this.interruptRefillHost == null) throw new IllegalStateException("Enable interrupt refill first");
        byte[] state = this.driver.stateBytes();
        var output = java.nio.ByteBuffer.allocate(state.length + 36)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        output.put(state);
        for (int word : this.driver.portStateWords()) output.putInt(word);
        return output.array();
    }

    private int transferCompiledBytes(int byteBudget) {
        if (!this.readAhead) throw new IllegalStateException("Timed consumption requires a read-ahead source");
        if (byteBudget < 0) throw new IllegalArgumentException("Negative transport budget");
        this.consumerStarted = true;
        int size = Math.min(this.transport.length, Math.min(byteBudget, this.driver.remainingCapacity()));
        int used = this.serializer.readInto(this.transport, 0, size);
        if (this.driver.offer(this.transport, 0, used) != used) throw new AssertionError("Driver capacity changed");
        return used;
    }

    private void drainEventCommands() {
        int length = this.serializer.readInto(this.transport, 0, this.transport.length);
        if (this.driver.offer(this.transport, 0, length) != length)
            throw new IllegalStateException("MFi event bridge overflow");
        // MLDPlayer still controls delivery time. Read-ahead and timed polling
        // will replace this bridge once the complete producer is connected.
        this.driver.dispatchBufferedWithoutClock();
    }

    @Override
    public MLDTrackControlMode trackControlMode()
    {
        return MLDTrackControlMode.FUETREK;
    }

    @Override
    public int eventTempo(int track, int timebase, int tempo)
    {
        return track == 0 && timebase > 0 ? Math.max(20, tempo) : 0;
    }

    @Override
    public int tickDurationMicros(int timebase, int tempo)
    {
        return FueTrekSequencerClock.tickDurationMicros(timebase, tempo);
    }

    @Override
    public int assignedChannel(int current, int parameter) {
        return this.compiler.version() == 1 || current == 255 ? current : parameter & 15;
    }

    @Override
    public void reset()
    {
        this.resourceAudioEngine.reset();
        this.resourceAudioEngine.setHostVolumeLevel(this.hostVolumeLevel);
        this.resetDriver();
        this.compiler.reset();
    }

    @Override
    public boolean ownsNoteGates() { return this.sampler instanceof FueTrekSampler; }

    @Override
    public void handleNote(int channel, int key, float velocity, int gateTime)
    {
        if (!(this.sampler instanceof FueTrekSampler)) return;
        this.compiler.note(consumeSourceDelta(), channel, key, Math.round(velocity * 63), gateTime);
    }

    @Override
    public int ticksUntilNoteGate()
    {
        long ticks = this.driver.ticksUntilNextGate();
        return ticks < 0 ? -1 : (int)Math.min(Integer.MAX_VALUE, ticks);
    }

    @Override
    public void advanceNoteGates(int elapsedTicks)
    {
        this.driver.retireGates(elapsedTicks);
        this.driver.advanceGates(elapsedTicks);
    }

    @Override
    public void retireNoteGates() { this.driver.retireGates(0); }

    /** Native record layout for independent compatibility diagnostics. */
    public byte[] noteGateStateBytes() { return this.driver.noteGateStateBytes(); }

    @Override
    public void setHostVolumeLevel(int level)
    {
        if (level < 0 || level > 100)
            throw new IllegalArgumentException("Host volume must be in 0..100");
        this.hostVolumeLevel = level;
        this.resourceAudioEngine.setHostVolumeLevel(level);
        if (this.sampler instanceof FueTrekSampler fueTrek)
            fueTrek.setHostVolumeLevel(level);
    }

    @Override
    public float noteVelocity(float velocity)
    {
        // Public SDK component parser 10024fa0, not the unbound embedded
        // Player parser: all 64 codes become odd values 1..127.
        int code = Math.max(0, Math.min(63, Math.round(velocity * 63)));
        return (code * 2 + 1) / 127.0f;
    }

    @Override
    public void handleResource(MLDPlayer player, MLDPlayerTrack track,
        MLDEvent event)
    {
        this.resourceAudioEngine.handleEvent(player.framePosition(), event);
        player.advanceTrack(track);
    }

    @Override
    public void render(float[] samples, int offset, int frames, float left,
        float right, boolean clamp, long framePosition)
    {
        this.resourceAudioEngine.render(samples, offset, frames, left, right,
            clamp, framePosition);
    }

    @Override
    public boolean hasLiveAudio(long framePosition)
    {
        return this.resourceAudioEngine.hasLiveAudio(framePosition);
    }

    @Override
    public int framesUntilSilence(long framePosition)
    {
        int customFrames = this.sampler instanceof FueTrekSampler fueTrek
            ? fueTrek.customFramesUntilSilence() : 0;
        return Math.max(customFrames,
            this.resourceAudioEngine.framesUntilSilence(framePosition));
    }

    @Override
    public void handleExtB(MLDPlayer player, MLDPlayerTrack track,
        MLDEvent event)
    {
        if (event.getId() == MLD.EVENT_TIMEBASE_TEMPO) {
            this.compiler.extB(track.getIndex(), 0, event.getSourceOpcode(), event.getParam(), consumeSourceDelta());
            MLDNormalizedEventDispatcher.dispatch(player, track, event);
            return;
        }
        if (!this.interceptsExtBEvent(event.getId()))
        {
            MLDNormalizedEventDispatcher.dispatch(player, track, event);
            return;
        }

        if (!(this.sampler instanceof MLDRawExtBHandler))
        {
            player.advanceTrack(track);
            return;
        }

        int channel = player.resolveRuntimeChannel(track,
            event.getChannelIndex(), event.getChannel());
        this.compiler.extB(track.getIndex(), channel, event.getSourceOpcode(), event.getParam(), consumeSourceDelta());
        if ((event.getId() & 255) == 0xbc) player.setBackendTempo(this.compiler.timebase(), this.compiler.tempo());
        player.advanceTrack(track);
    }

    private boolean interceptsExtBEvent(int eventId)
    {
        switch (eventId & 0xff)
        {
            case MLD.EVENT_MASTER_VOLUME:
            case 0xb1:
            case 0xbc:
            case 0xbd:
            case MLD.EVENT_X_DRUM_ENABLE:
            case MLD.EVENT_PROGRAM_CHANGE:
            case MLD.EVENT_BANK_CHANGE:
            case MLD.EVENT_VOLUME:
            case MLD.EVENT_PANPOT:
            case MLD.EVENT_PITCHBEND:
            case 0xe6:
            case MLD.EVENT_PITCHBEND_RANGE:
            case MLD.EVENT_WAVE_CHANNEL_VOLUME:
            case MLD.EVENT_WAVE_CHANNEL_PANPOT:
            case 0xea:
                return true;

            default:
                return false;
        }
    }
}
