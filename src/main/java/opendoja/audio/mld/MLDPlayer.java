// -*- Mode: Java; indent-tabs-mode: nil; tab-width: 4 -*-
// ---------------------------------------------------------------------------
// Keitai Wiki Community Music Implementation
//     Originally written and contributed by Guy Perfect
//     Continued maintenance and upkeep by SquirrelJME/Stephanie Gawroriski
// ---------------------------------------------------------------------------
// This specific file is under the given license:
// This is free and unencumbered software released into the public domain.
//
// Anyone is free to copy, modify, publish, use, compile, sell, or
// distribute this software, either in source code form or as a compiled
// binary, for any purpose, commercial or non-commercial, and by any
// means.
//
// In jurisdictions that recognize copyright laws, the author or authors
// of this software dedicate any and all copyright interest in the
// software to the public domain. We make this dedication for the benefit
// of the public at large and to the detriment of our heirs and
// successors. We intend this dedication to be an overt act of
// relinquishment in perpetuity of all present and future rights to this
// software under copyright law.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
// EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
// MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
// IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
// OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
// ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
// OTHER DEALINGS IN THE SOFTWARE.
//
// For more information, please refer to <https://unlicense.org/>
// ---------------------------------------------------------------------------

package opendoja.audio.mld;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

/**
 * i-melody MLD sequence player. Uses a {@code Sampler} to generate output to a
 * sample buffer.
 *
 * @see MLD
 * @see SamplerProvider
 */

public class MLDPlayer
{
    public interface EventSink
    {
        void accept(MLDPlayerEvent event);
    }

    /**
     * Event type that notifies when a non-looping sequence finishes.
     *
     * @see MLDPlayerEvent
     */
    public static final int EVENT_END = 0;

    /**
     * Event type that notifies when a particular key is played.
     *
     * @see MLDPlayerEvent
     */
    public static final int EVENT_KEY = 2;

    /**
     * Event type that notifies when a sequence loops.
     *
     * @see MLDPlayerEvent
     */
    public static final int EVENT_LOOP = 1;


    /**
     * Key index bias
     */
    static final int A4 = 48;


    /**
     * Playback channels
     */
    final MLDChannel[] channels;

    /**
     * Pending events
     */
    final ArrayList<MLDPlayerEvent> events;

    /**
     * Key events enabled by key
     */
    final HashSet<Integer> evtKeys;

    /**
     * Key events enabled by channel/absolute-note pairs.
     */
    final HashSet<Long> evtNotes;

    /**
     * Sequence resource
     */
    final MLD mld;

    /**
     * Output sampling rate
     */
    final float sampleRate;

    /**
     * Sample generator
     */

    public final Sampler sampler;

    /**
     * Sequencer state
     */
    final MLDPlayerTrack[] tracks;

    final TrackControlEngine trackControlEngine;

    final MLDPlaybackEngine playbackEngine;

    /**
     * Playback events are enabled
     */
    boolean evtPlayback;

    /**
     * Sequencer has no more events
     */
    boolean finished;

    /**
     * Output frames in one tick
     */
    float framesPerTick;

    /**
     * Output frames to process
     */
    float pendingFrames;

    /**
     * Sequencer ticks to process
     */
    int pendingTicks;

    /**
     * Sequencer position in frames
     */
    long position;

    /**
     * Processing setTime()
     */
    boolean seeking;

    /**
     * Sequencer position in ticks
     */
    long tickNow;

    /** Looping is enabled. */
    boolean loopEnabled;

    /** Stop all notes when looping. */
    boolean loopStopAll;

    /**
     * Begin MLD playback. Instances of a {@code Sampler} are used in
     * conjunction with the given sampling rate to render the sequence to a
     * sample buffer.
     *
     * @param mld The MLD sequence to play.
     * @param sampler A {@code Sampler} from which instances will be taken to
     * generate output.
     * @param sampleRate The samples per second of the output.
     * @throws NullPointerException if {@code mld} or {@code sampler} is
     * {@code null}.
     * @throws IllegalArgumentException if {@code sampleRate} is a
     * non-number or is less than or equal to zero.
     * @see MLD
     * @see SamplerProvider
     */

    public MLDPlayer(MLD mld, SamplerProvider sampler, float sampleRate)
    {

        // Error checking
        if (mld == null)
            throw new NullPointerException("An MLD is required.");
        if (sampler == null)
            throw new NullPointerException("A sampler is required.");
        if (Float.isInfinite(sampleRate) || sampleRate <= 0.0f)
            throw new IllegalArgumentException("Invalid sampling rate.");

        this.channels = new MLDChannel[16];
        this.events = new ArrayList<>();
        this.evtKeys = new HashSet<>();
        this.evtNotes = new HashSet<>();
        this.evtPlayback = false;
        this.loopEnabled = true;
        this.loopStopAll = true;
        this.mld = mld;
        this.sampler = sampler.instance(sampleRate);
        this.sampleRate = sampleRate;
        this.seeking = false;
        this.tracks = new MLDPlayerTrack[mld.tracks.length];
        this.playbackEngine = this.sampler.createPlaybackEngine(mld,
            sampleRate);
        this.trackControlEngine = TrackControlEngine.create(
            this.playbackEngine.trackControlMode(), this.tracks.length);

        // Channels
        for (int x = 0; x < this.channels.length; x++)
        {
            MLDChannel chan = this.channels[x] = new MLDChannel();
            //  A0 .. C6
            chan.notesOn = new MLDNote[99];
            chan.notesOut = new ArrayList<>();
        }

        // Tracks
        for (int x = 0; x < this.tracks.length; x++)
        {
            MLDPlayerTrack track = this.tracks[x] = new MLDPlayerTrack();
            track.index = x;
            track.mld = mld.tracks[x];
            this.resetChannelMap(track);
        }

        // Prepare the initial state without chasing cuepoint loops before the
        // caller has configured the desired playback loop mode.
        boolean initialLoopEnabled = this.loopEnabled;
        this.loopEnabled = false;
        this.reset();
        this.loopEnabled = initialLoopEnabled;
    }

    /**
     * Determine whether looping is enabled.
     *
     * @return {@code true} if looping is enabled.
     * @see #setLoopEnabled(boolean)
     */
    public boolean getLoopEnabled()
    {
        return this.loopEnabled;
    }

    /**
     * Determine whether notes are stopped when looping.
     *
     * @return {@code true} if all notes are stopped when looping.
     * @see #setLoopStopAll(boolean)
     */
    public boolean getLoopStopAll()
    {
        return this.loopStopAll;
    }

    /**
     * Registers a key to raise events for during rendering. Key number 0 is
     * the note A<sub>4</sub>.
     *
     * @param key A key number to register.
     * @see MLDPlayerEvent
     * @see #getEvents()
     */
    public void addEventKey(int key)
    {
        this.evtKeys.add(key);
    }

    /**
     * Registers multiple keys to raise events for during rendering. Key
     * number
     * 0 is the note A<sub>4</sub>.
     *
     * @param keys A list of key numbers to register.
     * @throws NullPointerException if {@code keys} is {@code null}.
     * @see MLDPlayerEvent
     * @see #getEvents()
     */
    public void addEventKeys(int[] keys)
    {
        if (keys == null)
            throw new NullPointerException("Key array is required.");
        for (int key : keys)
            this.evtKeys.add(key);
    }

    /**
     * Registers a channel/absolute-note pair to raise events for during
     * rendering.
     *
     * @param channel The playback channel to match.
     * @param note The absolute note number to match.
     * @see MLDPlayerEvent
     * @see #getEvents()
     */
    public void addEventNote(int channel, int note)
    {
        this.evtNotes.add(packEventNote(channel, note));
    }

    /**
     * Determine the total length of the sequence in seconds. Equivalent to
     * invoking {@code getDuration(withoutLoops)} on the underlying {@code
     * MLD}
     * object.
     *
     * @param withoutLooping Whether or not to consider looping in the return
     * value.
     * @return If the sequence does not loop, the number of seconds in the
     * sequence. If the sequence loops and {@code withoutLooping} is
     * {@code false}, returns {@code Double.POSITIVE_INFINITY}. If the
     * sequence
     * loops and {@code withoutLooping} is {@code true}, returns the number of
     * seconds in the sequence up until the first loop occurs.
     * @see MLD#getDuration(boolean)
     */

    public double getDuration(boolean withoutLooping)
    {
        return this.mld.getDuration(withoutLooping);
    }


    /**
     * Retrieve and acknowledge all pending events. If this method is not
     * called, events will remain in the queue and prevent samples from being
     * rendered.
     *
     * @return An array of all pending events, now acknowledged.
     * @see MLDPlayerEvent
     * @see #addEventKey(int)
     * @see #addEventKeys(int[])
     * @see #setPlaybackEventsEnabled(boolean)
     */

    public MLDPlayerEvent[] getEvents()
    {
        MLDPlayerEvent[] ret = this.events.toArray(
            new MLDPlayerEvent[this.events.size()]);
        this.events.clear();
        return ret;
    }

    /**
     * Drain pending events without allocating a temporary array.
     *
     * @param sink Receives each pending event in order.
     */
    public void drainEvents(EventSink sink)
    {
        if (sink == null)
            throw new NullPointerException("NARG");
        for (int i = 0, n = this.events.size(); i < n; i++)
            sink.accept(this.events.get(i));
        this.events.clear();
    }


    /**
     * Retrieve the current playback position in the sequence. The range of
     * values represents the start of the sequence at 0.0 and either the
     * end of
     * the sequence or the point where looping occurs at 1.0.
     *
     * @return The proportion of the total sequence for the current playback
     * position.
     */
    public double getPosition()
    {
        return (double)this.tickNow / this.mld.tickEnd;
    }

    /**
     * Retrieve the total number of seconds played back so far.
     *
     * @return The number of seconds processed, relative to the start of the
     * sequence.
     * @see #setTime(double)
     * @see MLD#getDuration(boolean)
     */

    public double getTime()
    {
        return (double)this.position / this.sampleRate;
    }

    /**
     * Determine whether playback has completed. The sequence is considered
     * finished when all of its events have been processed and the last note
     * has stopped generating samples.
     *
     * @return {@code true} if all playback has completed.
     */
    public boolean isFinished()
    {
        if (this.hasLiveOutput())
            return false;
        return this.allTracksFinished();
    }

    /**
     * Unregisters a keys from raising events during rendering.
     *
     * @param key A key number to unregister.
     * @see MLDPlayerEvent
     * @see #getEvents()
     */
    public void removeEventKey(int key)
    {
        this.evtKeys.remove(key);
    }

    /**
     * Unregisters multiple keys from raising events during rendering.
     *
     * @param keys A list of key numbers to unregister.
     * @throws NullPointerException if {@code keys} is {@code null}.
     * @see MLDPlayerEvent
     * @see #getEvents()
     */
    public void removeEventKeys(int[] keys)
    {
        if (keys == null)
            throw new NullPointerException("Key array is required.");
        for (int key : keys)
            this.evtKeys.remove(key);
    }

    /**
     * Removes all registered channel/absolute-note pairs.
     */
    public void clearEventNotes()
    {
        this.evtNotes.clear();
    }

    /**
     * Generate output samples. This method is equivalent to
     * {@code render(samples, offset, frames, 1.0f, 1.0f, true, true)}.<br
     * ><br>
     * For information regarding the operations of this method, see
     * {@link Sampler#render(float[], int, int, float, float, boolean, boolean)}.
     *
     * @param samples Output sample buffer.
     * @param offset Index in {@code samples} of the first audio frame to
     * output.
     * @param frames The number of audio frames to output.
     * @return The number of samples generated, or -1 if playback has
     * finished.
     * May be less than {@code frames} if playback of the underlying sequence
     * completes before all frames have been processed.
     * @throws NullPointerException if {@code samples} is {@code null}.
     * @throws ArrayIndexOutOfBoundsException if {@code offset} is
     * negative, or if {@code offset + frames * 2 > samples.length}.
     * @throws IllegalArgumentException if {@code frames} is negative.
     * @see #render(float[], int, int, float, float, boolean, boolean)
     * @see Sampler#render(float[], int, int, float, float, boolean, boolean)
     */
    public int render(float[] samples, int offset, int frames)
    {
        return this.render(samples, offset, frames, 1.0f, 1.0f, true, true);
    }

    /**
     * Generate output samples. This method is equivalent to
     * {@code render(samples, offset, frames, amplitude, amplitude,
     * true, true)}.<br><br>
     * For information regarding the operations of this method, see
     * {@link Sampler#render(float[], int, int, float, float, boolean, boolean)}.
     *
     * @param samples Output sample buffer.
     * @param offset Index in {@code samples} of the first audio frame to
     * output.
     * @param frames The number of audio frames to output.
     * @param amplitude A multiplier that is applied to all samples
     * generated.
     * @return The number of samples generated, or -1 if playback has
     * finished.
     * May be less than {@code frames} if playback of the underlying sequence
     * completes before all frames have been processed.
     * @throws NullPointerException if {@code samples} is {@code null}.
     * @throws ArrayIndexOutOfBoundsException if {@code offset} is
     * negative, or if {@code offset + frames * 2 > samples.length}.
     * @throws IllegalArgumentException if {@code frames} is negative, or if
     * {@code amplitude} is a non-number or is negative.
     * @see #render(float[], int, int, float, float, boolean, boolean)
     * @see Sampler#render(float[], int, int, float, float, boolean, boolean)
     */
    public int render(float[] samples, int offset, int frames,
        float amplitude)
    {
        return this.render(samples, offset, frames, amplitude, amplitude,
            true,
            true);
    }

    /**
     * Generate output samples. This method is equivalent to
     * {@code render(samples, offset, frames, left, right, true, true)}.
     * <br><br>
     * For information regarding the operations of this method, see
     * {@link Sampler#render(float[], int, int, float, float, boolean, boolean)}.
     *
     * @param samples Output sample buffer.
     * @param offset Index in {@code samples} of the first audio frame to
     * output.
     * @param frames The number of audio frames to output.
     * @param left A multiplier that is applied to all left-stereo samples
     * generated.
     * @param right A multiplier that is applied to all right-stereo samples
     * generated.
     * @return The number of samples generated, or -1 if playback has
     * finished.
     * May be less than {@code frames} if playback of the underlying sequence
     * completes before all frames have been processed.
     * @throws NullPointerException if {@code samples} is {@code null}.
     * @throws ArrayIndexOutOfBoundsException if {@code offset} is
     * negative, or if {@code offset + frames * 2 > samples.length}.
     * @throws IllegalArgumentException if {@code frames} is negative, or if
     * {@code left} or {@code right} is a non-number or is negative.
     * @see #render(float[], int, int, float, float, boolean, boolean)
     * @see Sampler#render(float[], int, int, float, float, boolean, boolean)
     */
    public int render(float[] samples, int offset, int frames, float left,
        float right)
    {
        return this.render(samples, offset, frames, left, right, true, true);
    }

    /**
     * Generate output samples. <br><br>
     * For information regarding the operations of this method, see
     * {@link Sampler#render(float[], int, int, float, float, boolean, boolean)}.
     * <br><br>
     * If an event is raised during playback, rendering will stop and return
     * before generating any more samples. When this happens, the return value
     * may be less than {@code frames}. {@link #getEvents()} should be called
     * after every call to {@code render()} while events are enabled.
     *
     * @param samples Output sample buffer.
     * @param offset Index in {@code samples} of the first audio frame to
     * output.
     * @param frames The number of audio frames to output.
     * @param left A multiplier that is applied to all left-stereo samples
     * generated.
     * @param right A multiplier that is applied to all right-stereo samples
     * generated.
     * @param erase Replace the buffer contents when {@code true}, or add
     * to them when {@code false}
     * @param clamp Specifies whether to restrict the sample buffer values
     * to -1.0f to +1.0f inclusive.
     * @return The number of samples generated, or -1 if playback has
     * finished.
     * May be less than {@code frames} if playback of the underlying sequence
     * completes before all frames have been processed.
     * @throws NullPointerException if {@code samples} is {@code null}.
     * @throws ArrayIndexOutOfBoundsException if {@code offset} is
     * negative, or if {@code offset + frames * 2 > samples.length}.
     * @throws IllegalArgumentException if {@code frames} is negative, or if
     * {@code left} or {@code right} is a non-number or is negative.
     * @see Sampler#render(float[], int, int, float, float, boolean, boolean)
     * @see #getEvents()
     * @see #render(float[], int, int)
     * @see #render(float[], int, int, float)
     * @see #render(float[], int, int, float, float)
     */

    public int render(float[] samples, int offset, int frames, float left,
        float right, boolean erase, boolean clamp)
    {
        //  Total frames output so far
        int ret = 0;

        // Error checking
        if (!this.seeking)
        {
            if (samples == null)
                throw new NullPointerException(
                    "A sample buffer is required" + ".");
            if (frames < 0)
                throw new IllegalArgumentException("Invalid frames.");
            if (offset < 0 || offset + frames * 2 > samples.length)
            {
                throw new ArrayIndexOutOfBoundsException(
                    "Invalid range in sample buffer.");
            }
            if (Float.isInfinite(left) || left < 0.0f)
                throw new IllegalArgumentException("Invalid left amplitude.");
            if (Float.isInfinite(right) || right < 0.0f)
                throw new IllegalArgumentException(
                    "Invalid right amplitude" + ".");
        }

        // Sequencer is not playing
        if (this.finished && this.pendingFrames <= 0.0f)
        {
            if (!this.hasLiveOutput())
                return -1;
            // The event stream may be done while synth notes or top-level
            // resource audio are still sounding.
            this.pendingFrames = frames;
        }

        // Process all output frames
        while (frames > 0)
        {

            // Events are pending
            if (this.events.size() != 0)
                return ret;

            // Process output frames
            while (this.pendingFrames > 0)
            {

                // Render the samples
                int f = Math.min(frames, (int)Math.floor(this.pendingFrames));
                if (!this.seeking)
                {
                    this.sampler.render(samples, offset, f, left, right,
                        erase,
                        clamp);
                    this.playbackEngine.render(samples, offset, f, left, right,
                        clamp, this.position);
                }

                // State management
                frames -= f;
                offset += f * 2;
                this.pendingFrames -= f;
                this.position += f;
                ret += f;

                // All output frames have been processed
                if (frames == 0)
                    return ret > 0 ? ret :
                        (this.finished && !this.hasLiveOutput() ? -1 : ret);
            }

            // Process event ticks
            if (this.pendingTicks > 0)
            {
                // Sequencer
                this.tickNow += this.pendingTicks;

                // Notes
                for (MLDChannel chan : this.channels)
                    for (MLDNote note : chan.notesOut)
                        note.gateTime -= this.pendingTicks;
                // Retire zero-gate notes before dispatching same-tick events so
                // backends that suppress active-key retriggers can start a fresh
                // voice when a note repeats exactly at its gate boundary.
                for (MLDChannel chan : this.channels)
                    for (int x = 0; x < chan.notesOut.size(); x++)
                    {
                        MLDNote note = chan.notesOut.get(x);
                        if (note.gateTime != 0)
                            continue;
                        this.sampler.keyOff(note.channel, note.key);
                        chan.notesOut.remove(x--);
                        chan.notesOn[MLDPlayer.A4 + note.key] = null;
                    }

                // Tracks
                boolean restartRequested = false;
                for (MLDPlayerTrack track : this.tracks)
                {
                    if (!track.finished)
                        track.ticks -= this.pendingTicks;
                }
                for (MLDPlayerTrack track : this.tracks)
                {
                    this.process(track);
                    if (this.trackControlEngine.restartRequested())
                    {
                        this.trackControlEngine.clearRestartRequest();
                        restartRequested = true;
                        break;
                    }
                }
                if (restartRequested)
                {
                    this.pendingTicks = 0;
                    continue;
                }
            }

            // Replayed tracks may rewind onto a zero-delay event, so they need
            // another parser pass without advancing sequence time.
            else if (this.pendingTicks == 0)
            {
                for (MLDPlayerTrack track : this.tracks)
                {
                    this.process(track);
                    if (this.trackControlEngine.restartRequested())
                    {
                        this.trackControlEngine.clearRestartRequest();
                        break;
                    }
                }

            }

            // Determine how many ticks and frames can be processed next
            int untilTrack = this.untilTrack();
            if (untilTrack == -1)
            {
                this.finished = true;
                int liveTailFrames = this.liveTailFrames();
                if (liveTailFrames <= 0)
                {
                    this.queueEndEventIfReady();
                    if (ret > 0)
                        return ret;
                    return (this.events.size() != 0 ? ret : -1);
                }
                // Keep draining audible tail output after the sequence itself
                // has no more events to process.
                this.pendingTicks = 0;
                this.pendingFrames += liveTailFrames;
                continue;
            }
            int untilNote = this.untilNote();
            this.pendingTicks = untilNote == -1 ? untilTrack : Math.min(
                untilTrack, untilNote);
            this.pendingFrames += (float)Math.floor(
                this.pendingTicks * this.framesPerTick);
        }

        return ret;
    }

    /**
     * Specify whether or not to raise playback events. Playback events
     * include
     * {@code EVENT_END} and {@code EVENT_LOOP}.
     *
     * @param enabled Whether or not playback events can be raised during
     * rendering.
     * @see MLDPlayerEvent
     * @see #getEvents()
     */

    public void setPlaybackEventsEnabled(boolean enabled)
    {
        this.evtPlayback = enabled;
    }

    /**
     * Specify the playback position of the sequence in seconds. The resulting
     * position in the sequence will be the earliest internal time at or after
     * {@code seconds}.<br><br>
     * If the end of the sequence is encountered during seeking, this method
     * will return {@code true}. When this happens, it is possible that the
     * position in the sequence retrieved by subsequent calls to
     * {@code getTime()} may be less than {@code seconds}.
     *
     * @param seconds The number of seconds from the beginning of the
     * sequence.
     * @return {@code true} if the end of the sequence was encountered during
     * the operation.
     * @throws IllegalArgumentException if {@code seconds} is a non-number
     * or is negative.
     * @see #getTime()
     * @see MLD#getDuration(boolean)
     */

    public boolean setTime(double seconds)
    {

        // Error checking
        if (Double.isInfinite(seconds) || seconds < 0)
            throw new IllegalArgumentException("Invalid seconds.");

        // Compute the target number of frames
        long target = (long)Math.ceil(seconds * this.sampleRate);

        // Already at the target
        if (target == this.position)
            return this.isFinished();

        // Target is earlier than the current frame
        if (target < this.position)
            this.reset();

        // Seek forward to the target time
        this.seeking = true;
        this.render(null, 0, (int)(target - this.position), 0.0f, 0.0f, false,
            false);
        this.seeking = false;
        return this.isFinished();
    }


    /**
     * bank-change
     */
    void evtBankChange(MLDPlayerTrack track, MLDEvent event)
    {
        int channel = this.runtimeChannel(track, event.channelIndex,
            event.channel);
        if (channel >= 0)
            this.sampler.bankChange(channel, event.bank);
        this.setTrackOffset(track, track.offset + 1);
    }

    /**
     * channel-assign
     */
    void evtChannelAssign(MLDPlayerTrack track, MLDEvent event)
    {
        int lane = event.channelIndex;
        if (this.trackControlEngine.usesRuntimeChannelMap() &&
            lane >= 0 && lane < track.channelMap.length)
            track.channelMap[lane] = event.param & 0x3F;
        this.setTrackOffset(track, track.offset + 1);
    }

    /**
     * cuepoint
     */
    void evtCuepoint(MLDPlayerTrack track, MLDEvent event)
    {

        // cuepoint-end
        if (event.cuepoint == MLD.CUEPOINT_END && this.tracks[0].cuepoint != -1)
        {
            // Process only if looping is enabled
            if (this.loopEnabled)
            {
                if (this.loopStopAll)
                    this.sampler.stopAll();
                for (MLDPlayerTrack t : this.tracks)
                    this.setTrackOffset(t, t.cuepoint);
                if (this.evtPlayback)
                    this.events.add(
                        new MLDPlayerEvent(this.getTime(),
                            MLDPlayer.EVENT_LOOP, 0));
            }

            // Looping is disabled
            else
                this.setTrackOffset(track, track.offset + 1);

            return;
        }

        // Common processing
        this.setTrackOffset(track, track.offset + 1);

        // cuepoint-start
        if (event.cuepoint == MLD.CUEPOINT_START)
        {
            for (MLDPlayerTrack t : this.tracks)
                t.cuepoint = t.offset;
        }

    }

    /**
     * drum-enable
     */
    void evtDrumEnable(MLDPlayerTrack track, MLDEvent event)
    {
        this.sampler.drumEnable(event.channel, event.enable);
        this.setTrackOffset(track, track.offset + 1);
    }

    /**
     * end-of-track
     */
    void evtEndOfTrack(MLDPlayerTrack track, MLDEvent event)
    {
        track.trackControlReplayByte = 1;
        this.setTrackOffset(track, track.offset + 1);
    }

    /**
     * ext-B event
     */
    void evtExtB(MLDPlayerTrack track, MLDEvent e)
    {
        this.playbackEngine.handleExtB(this, track, e);
    }

    /**
     * ext-info event
     */
    void evtExtInfo(MLDPlayerTrack track, MLDEvent e)
    {
        this.sampler.sysEx(e.data);
        this.setTrackOffset(track, track.offset + 1);
    }

    void evtResource(MLDPlayerTrack track, MLDEvent event)
    {
        this.playbackEngine.handleResource(this, track, event);
    }

    /**
     * master-tune
     */
    void evtMasterTune(MLDPlayerTrack track, MLDEvent event)
    {
        this.sampler.masterTune(event.semitones);
        this.setTrackOffset(track, track.offset + 1);
    }

    /**
     * master-volume
     */
    void evtMasterVolume(MLDPlayerTrack track, MLDEvent event)
    {
        this.sampler.masterVolume(event.volume);
        this.setTrackOffset(track, track.offset + 1);
    }

    /**
     * note
     */
    void evtNote(MLDPlayerTrack track, MLDEvent event)
    {
        int channel = this.runtimeChannel(track, event.channelIndex,
            event.channel);

        // Common processing
        this.setTrackOffset(track, track.offset + 1);

        // Raise an event
        int absoluteNote = 69 + event.key;
        if (this.evtKeys.contains(event.key) ||
            this.evtNotes.contains(packEventNote(event.channel, absoluteNote)))
            this.events.add(
                new MLDPlayerEvent(this.getTime(), MLDPlayer.EVENT_KEY,
                    event.key, event.channel, absoluteNote, event.keyNumber));

        if (channel < 0)
            return;

        MLDChannel chan = this.channels[channel];
        MLDNote note = chan.notesOn[MLDPlayer.A4 + event.key];

        // Velocity 0 is regarded as key-off
        if (event.velocity == 0)
        {
            this.sampler.keyOff(channel, event.key);
            if (note != null)
            {
                chan.notesOn[MLDPlayer.A4 + event.key] = null;
                chan.notesOut.remove(note);
            }
            return;
        }

        // Velocity not zero is regarded as key-on. Some backends keep one
        // active voice per key and only refresh the scheduled gate when the
        // same key is struck again before release.
        if (!this.seeking &&
            (note == null || !this.sampler.suppressActiveKeyRetrigger()))
            this.sampler.keyOn(channel, event.key, event.velocity);

        // Get or create the note for this key
        if (note == null)
        {
            note = new MLDNote();
            note.channel = channel;
            note.key = event.key;
            chan.notesOn[MLDPlayer.A4 + event.key] = note;
            chan.notesOut.add(note);
        }

        // Reconfigure the note
        note.gateTime = event.gateTime;
    }

    /**
     * panpot
     */
    void evtPanPot(MLDPlayerTrack track, MLDEvent event)
    {
        int channel = this.runtimeChannel(track, event.channelIndex,
            event.channel);
        if (channel >= 0)
            this.sampler.panpot(channel, event.panpot);
        this.setTrackOffset(track, track.offset + 1);
    }

    /**
     * pitchbend
     */
    void evtPitchBend(MLDPlayerTrack track, MLDEvent event)
    {
        int channel = this.runtimeChannel(track, event.channelIndex,
            event.channel);
        if (channel >= 0)
            this.sampler.pitchBend(channel, event.semitones);
        this.setTrackOffset(track, track.offset + 1);
    }

    /**
     * pitchbend-range
     */
    void evtPitchRange(MLDPlayerTrack track, MLDEvent event)
    {
        int channel = this.runtimeChannel(track, event.channelIndex,
            event.channel);
        if (channel >= 0)
            this.sampler.pitchBendRange(channel, event.range);
        this.setTrackOffset(track, track.offset + 1);
    }

    /**
     * program-change
     */
    void evtProgramChange(MLDPlayerTrack track, MLDEvent event)
    {
        int channel = this.runtimeChannel(track, event.channelIndex,
            event.channel);
        if (channel >= 0)
            this.sampler.programChange(channel, event.program);
        this.setTrackOffset(track, track.offset + 1);
    }

    /**
     * timebase-tempo
     */
    void evtTimebaseTempo(MLDPlayerTrack track, MLDEvent event)
    {
        if (event.timebase == -1)
            return;
        float prev = this.framesPerTick;
        this.setTempo(event.timebase, event.tempo);
        this.pendingFrames = this.pendingFrames * this.framesPerTick / prev;
        this.setTrackOffset(track, track.offset + 1);
    }

    /**
     * volume
     */
    void evtVolume(MLDPlayerTrack track, MLDEvent event)
    {
        int channel = this.runtimeChannel(track, event.channelIndex,
            event.channel);
        if (channel >= 0)
            this.sampler.volume(channel, event.volume);
        this.setTrackOffset(track, track.offset + 1);
    }


    /**
     * Process events on a track
     */
    void process(MLDPlayerTrack track)
    {

        // The track has finished
        if (track.finished)
            return;
        if (track.ticks > 0)
            return;

        // Process all events this tick
        while (track.ticks == 0)
        {
            MLDEvent event = track.mld.get(track.offset);

            // Process the event
            switch (event.type)
            {
                case MLD.EVENT_TYPE_NOTE:
                    this.evtNote(track, event);
                    break;
                case MLD.EVENT_TYPE_RESOURCE:
                    this.evtResource(track, event);
                    break;
                case MLD.EVENT_TYPE_EXT_B:
                    this.evtExtB(track, event);
                    break;
                case MLD.EVENT_TYPE_EXT_INFO:
                    this.evtExtInfo(track, event);
                    break;
                default:
                    this.setTrackOffset(track, track.offset + 1);
            }

            // Stop processing events
            if (track.finished)
                return;
            if (track.trackControlSkipReschedule)
            {
                track.trackControlSkipReschedule = false;
                if (track.ticks == 0)
                    continue;
                return;
            }
            if (this.trackControlEngine.restartRequested())
            {
                return;
            }

            // Schedule the next event
            track.ticks = track.mld.get(track.offset).delta;
        }

    }

    /**
     * Initialize state in preparation for playback. All notes are stopped and
     * all sequencer state is reset to the beginning of the sequence.
     */

    public void reset()
    {
        // Instance fields
        this.pendingFrames = 0;
        this.pendingTicks = 0;
        this.position = 0;
        this.tickNow = 0;
        this.setTempo(48, 125);
        this.events.clear();

        // Initialize sampler
        this.sampler.reset();
        this.playbackEngine.reset();

        // Channels
        for (MLDChannel chan : this.channels)
        {
            Arrays.fill(chan.notesOn, null);
            chan.notesOut.clear();
        }

        // Tracks
        for (MLDPlayerTrack track : this.tracks)
        {
            this.resetChannelMap(track);
            track.cuepoint = -1;
            track.offset = track.mld.cue;
            track.finished = track.offset >= track.mld.size();
            track.trackControlSkipReschedule = false;
            track.trackControlReplayByte = 0;
            this.syncTrackControlCursor(track);
            track.trackControlModeByte = track.index;
            track.ticks = track.finished ? 0 : track.mld.get(track.offset).delta;
        }
        this.trackControlEngine.reset(this);

        // Initialize playback
        this.finished = true;
        for (MLDPlayerTrack track : this.tracks)
        {
            this.process(track);
            this.finished = this.finished && track.finished;
        }

    }

    int runtimeChannel(MLDPlayerTrack track, int lane, int fallback)
    {
        int channel = fallback;
        if (this.trackControlEngine.usesRuntimeChannelMap() &&
            track != null &&
            lane >= 0 &&
            lane < track.channelMap.length)
            channel = track.channelMap[lane];
        return (channel >= 0 && channel < this.channels.length ? channel : -1);
    }

    private void resetChannelMap(MLDPlayerTrack track)
    {
        for (int lane = 0; lane < track.channelMap.length; lane++)
            track.channelMap[lane] = track.index * 4 + lane;
    }

    void evtTrackControl(MLDPlayerTrack track, MLDEvent event)
    {
        if (this.trackControlEngine.handleExtB(this, track, event))
            return;
        this.setTrackOffset(track, track.offset + 1);
    }

    public void advanceTrack(MLDPlayerTrack track, int offset)
    {
        this.setTrackOffset(track, offset);
    }

    public void advanceTrack(MLDPlayerTrack track)
    {
        this.setTrackOffset(track, track.offset + 1);
    }

    public long framePosition()
    {
        return this.position;
    }

    public int resolveRuntimeChannel(MLDPlayerTrack track, int lane,
        int fallback)
    {
        return this.runtimeChannel(track, lane, fallback);
    }

    int trackControlRetryTicks(MLDPlayerTrack currentTrack)
    {
        int retry = -1;
        for (MLDPlayerTrack other : this.tracks)
        {
            if (other == currentTrack || other.finished || other.ticks <= 0)
                continue;
            if (retry == -1 || other.ticks < retry)
                retry = other.ticks;
        }
        int note = this.untilNote();
        if (note > 0 && (retry == -1 || note < retry))
            retry = note;
        return retry <= 0 ? 1 : retry;
    }

    int trackControlMinimumTrackTicks()
    {
        int minimum = 0;
        boolean found = false;
        for (MLDPlayerTrack track : this.tracks)
        {
            if (track.finished)
                continue;
            if (!found || track.ticks < minimum)
            {
                minimum = track.ticks;
                found = true;
            }
        }
        return found ? Math.max(0, minimum) : 0;
    }

    /**
     * Specify whether to enable looping. When disabled, loop points
     * defined in
     * the sequence data will not be processed.
     *
     * @param enabled If {@code true}, looping will be enabled.
     * @return the value of {@code enabled}
     * @see #getLoopEnabled()
     */
    public boolean setLoopEnabled(boolean enabled)
    {
        return this.loopEnabled = enabled;
    }

    /**
     * Specify whether to stop all notes when looping. If notes are not
     * stopped, it is possible for adjustments to volume or pitch-bend to
     * affect ongoing notes in undesirable ways. If notes <i>are</i> stopped,
     * it is possible for ongoing notes to be truncated in undesirable ways.
     *
     * @param stopAll If {@code true}, all notes will be stopped when looping.
     * @return the value of {@code stopAll}
     * @see #getLoopStopAll()
     */
    public boolean setLoopStopAll(boolean stopAll)
    {
        return this.loopStopAll = stopAll;
    }

    /**
     * Compute the number of output frames in one event tick
     */
    void setTempo(int timebase, int tempo)
    {
        this.framesPerTick = (60 * this.sampleRate) / (timebase * tempo);
    }

    /**
     * Specify the event offset of a track
     */
    void setTrackOffset(MLDPlayerTrack track, int offset)
    {

        // Configure the track
        track.offset = offset;
        track.finished = offset >= track.mld.size();
        this.syncTrackControlCursor(track);

        // Raise an event
        if (!track.finished || !this.evtPlayback)
            return;
        this.queueEndEventIfReady();
    }

    private void syncTrackControlCursor(MLDPlayerTrack track)
    {
        if (track == null)
            return;
        if (!track.finished)
        {
            MLDEvent event = track.mld.get(track.offset);
            track.trackControlRawOffset = event.offset;
            track.trackControlRawEndOffset = event.endOffset;
            return;
        }
        int terminal = 0;
        if (!track.mld.isEmpty())
            terminal = track.mld.get(track.mld.size() - 1).endOffset;
        track.trackControlRawOffset = terminal;
        track.trackControlRawEndOffset = terminal;
    }

    /**
     * Determine how many ticks can be processed until a note expires
     */
    int untilNote()
    {
        int ret = -1;
        for (MLDChannel chan : this.channels)
            for (MLDNote note : chan.notesOut)
            {
                if (ret == -1 || note.gateTime < ret)
                    ret = note.gateTime;
            }
        return ret;
    }

    /**
     * Determine how many ticks can be processed until the next event
     */
    int untilTrack()
    {
        int ret = -1;
        for (MLDPlayerTrack track : this.tracks)
        {
            if (track.finished)
                continue;
            if (ret == -1 || track.ticks < ret)
                ret = track.ticks;
        }
        return ret;
    }

    private boolean hasLiveOutput()
    {
        if (!this.sampler.isFinished())
            return true;
        return this.playbackEngine.hasLiveAudio(this.position);
    }

    private boolean allTracksFinished()
    {
        for (MLDPlayerTrack track : this.tracks)
        {
            if (!track.finished)
                return false;
        }
        return true;
    }

    private void queueEndEventIfReady()
    {
        // DoJa exposes sequence completion at the point where the event stream
        // ends. Any remaining synth/resource tail is still audible, but it
        // must not delay EVENT_END/AUDIO_COMPLETE delivery.
        if (!this.evtPlayback || !this.allTracksFinished())
            return;
        for (int i = 0; i < this.events.size(); i++)
        {
            if (this.events.get(i).type == MLDPlayer.EVENT_END)
                return;
        }
        this.events.add(new MLDPlayerEvent(this.getTime(),
            MLDPlayer.EVENT_END, 0));
    }

    private int liveTailFrames()
    {
        int resourceFrames = this.playbackEngine.framesUntilSilence(
            this.position);
        return resourceFrames > 0 ? Math.min(1024, resourceFrames) : 0;
    }

    private static long packEventNote(int channel, int note)
    {
        return ((long)channel << 32) | (note & 0xffffffffL);
    }
}
