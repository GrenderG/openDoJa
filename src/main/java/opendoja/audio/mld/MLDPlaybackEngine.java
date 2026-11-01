package opendoja.audio.mld;

/**
 * Synth-owned MLD playback behavior.
 *
 * The core {@link MLDPlayer} remains responsible for parsing, timing, and
 * normalized sequencing. Synth families own everything that depends on a
 * concrete playback backend: ext-B quirks, track-control policy selection, and
 * optional top-level resource-audio rendering.
 */
public interface MLDPlaybackEngine
{
    /**
     * The scheduler model this synth wants for raw track-control opcodes.
     *
     * @return The synth-selected track-control mode.
     */
    MLDTrackControlMode trackControlMode();

    /**
     * Reset synth-owned MLD playback state.
     */
    void reset();

    /** Separate source production from command consumption on clocked backends. */
    default void setReadAheadMode(boolean enabled) {
        if (enabled) throw new UnsupportedOperationException("Backend has no command producer");
    }

    default void beginSourceEvent(int ticks) {}

    default void endSourceEvent(boolean trackEnded, boolean instantaneousEnd) {}

    /** Host volume percentage for backends that apply it before rendering. */
    default void setHostVolumeLevel(int level)
    {
    }

    /** Return the accepted tempo, or zero when this track/event is ignored. */
    default int eventTempo(int track, int timebase, int tempo)
    {
        return timebase > 0 && tempo > 0 ? tempo : 0;
    }

    /** Positive native integer tick duration, or zero for the generic float clock. */
    default int tickDurationMicros(int timebase, int tempo)
    {
        return 0;
    }

    /** Backend-specific lane assignment bits from raw E5. */
    default int assignedChannel(int current, int parameter) { return parameter & 63; }

    /** Convert a decoded MFi velocity to the sampler's normalized domain. */
    default float noteVelocity(float velocity)
    {
        return velocity;
    }

    /** Whether the backend accepts this decoded note and gate. */
    default boolean acceptsNote(int channel, int key, int gateTime)
    {
        return true;
    }

    /** A backend may own the driver's timed-note records instead of generic note gates. */
    default boolean ownsNoteGates() { return false; }

    default void handleNote(int channel, int key, float velocity, int gateTime) {}

    default int ticksUntilNoteGate() { return -1; }

    default void advanceNoteGates(int elapsedTicks) {}

    default void retireNoteGates() {}

    /**
     * Handle an ext-B event.
     *
     * @param player The active player.
     * @param track The source track.
     * @param event The parsed event.
     */
    void handleExtB(MLDPlayer player, MLDPlayerTrack track, MLDEvent event);

    /**
     * Handle a top-level resource event.
     *
     * @param player The active player.
     * @param track The source track.
     * @param event The parsed event.
     */
    void handleResource(MLDPlayer player, MLDPlayerTrack track, MLDEvent event);

    /**
     * Render synth-owned overlay/resource audio.
     */
    void render(float[] samples, int offset, int frames, float left,
        float right, boolean clamp, long framePosition);

    /**
     * Whether synth-owned overlay/resource audio is still active.
     *
     * @param framePosition The current player frame position.
     * @return {@code true} if synth-owned audio is still live.
     */
    boolean hasLiveAudio(long framePosition);

    /**
     * The number of frames until synth-owned overlay/resource audio becomes
     * silent.
     *
     * @param framePosition The current player frame position.
     * @return The number of remaining frames, or {@code 0}.
     */
    int framesUntilSilence(long framePosition);
}
