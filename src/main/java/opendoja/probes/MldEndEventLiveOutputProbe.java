package opendoja.probes;

import opendoja.audio.mld.MLDPlaybackEngine;
import opendoja.audio.mld.MLDPlayer;
import opendoja.audio.mld.MLDPlayerEvent;
import opendoja.audio.mld.MLDPlayerTrack;
import opendoja.audio.mld.MLDTrackControlMode;
import opendoja.audio.mld.Sampler;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;

/**
 * Verifies that EVENT_END is not raised while MLD synth/resource output is
 * still audible after the event stream itself has finished.
 */
public final class MldEndEventLiveOutputProbe {
    private MldEndEventLiveOutputProbe() {
    }

    public static void main(String[] args) throws Exception {
        assertEndEventQueued(false, false, "no live output");
        assertEndEventQueued(true, false, "live synth tail");
        assertEndEventQueued(false, true, "live resource tail");
        System.out.println("MldEndEventLiveOutputProbe OK");
    }

    private static void assertEndEventQueued(boolean synthLive,
                                             boolean resourceLive,
                                             String label) throws Exception {
        MLDPlayer player = allocatePlayer(synthLive, resourceLive);
        invokeQueueEndEventIfReady(player);
        int endEvents = countEndEvents(player);

        if (synthLive || resourceLive) {
            if (endEvents != 0) {
                throw new AssertionError(label + " queued EVENT_END while audio was still live");
            }
            return;
        }
        if (endEvents != 1) {
            throw new AssertionError(label + " expected exactly one EVENT_END but saw " + endEvents);
        }
    }

    private static MLDPlayer allocatePlayer(boolean synthLive,
                                            boolean resourceLive) throws Exception {
        MLDPlayer player = (MLDPlayer) unsafe().allocateInstance(MLDPlayer.class);
        setField(player, "evtPlayback", true);
        setField(player, "events", new ArrayList<MLDPlayerEvent>());
        setField(player, "sampler", new ProbeSampler(synthLive));
        setField(player, "playbackEngine", new ProbePlaybackEngine(resourceLive));

        Class<?> trackType = Class.forName("opendoja.audio.mld.MLDPlayerTrack");
        Object tracks = Array.newInstance(trackType, 1);
        Object track = unsafe().allocateInstance(trackType);
        setField(track, "finished", true);
        Array.set(tracks, 0, track);
        setField(player, "tracks", tracks);
        return player;
    }

    private static int countEndEvents(MLDPlayer player) throws Exception {
        @SuppressWarnings("unchecked")
        ArrayList<MLDPlayerEvent> events = (ArrayList<MLDPlayerEvent>) getField(player, "events");
        int count = 0;
        for (MLDPlayerEvent event : events) {
            if (event.type == MLDPlayer.EVENT_END) {
                count++;
            }
        }
        return count;
    }

    private static void invokeQueueEndEventIfReady(MLDPlayer player) throws Exception {
        Method method = MLDPlayer.class.getDeclaredMethod("queueEndEventIfReady");
        method.setAccessible(true);
        method.invoke(player);
    }

    private static Object getField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static sun.misc.Unsafe unsafe() throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (sun.misc.Unsafe) field.get(null);
    }

    private static final class ProbeSampler implements Sampler {
        private final boolean synthLive;

        private ProbeSampler(boolean synthLive) {
            this.synthLive = synthLive;
        }

        @Override
        public void bankChange(int channel, int bank) {
        }

        @Override
        public void drumEnable(int channel, boolean enable) {
        }

        @Override
        public boolean isFinished() {
            return !synthLive;
        }

        @Override
        public MLDPlaybackEngine createPlaybackEngine(opendoja.audio.mld.MLD mld, float sampleRate) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void keyOff(int channel, int key) {
        }

        @Override
        public void keyOn(int channel, int key, float velocity) {
        }

        @Override
        public void masterTune(float semitones) {
        }

        @Override
        public void masterVolume(float volume) {
        }

        @Override
        public void panpot(int channel, float panpot) {
        }

        @Override
        public void pitchBend(int channel, float semitones) {
        }

        @Override
        public void pitchBendRange(int channel, float semitones) {
        }

        @Override
        public void programChange(int channel, int program) {
        }

        @Override
        public void render(float[] samples, int offset, int frames, float left, float right,
                           boolean erase, boolean clamp) {
        }

        @Override
        public void render(float[] samples, int offset, int frames) {
        }

        @Override
        public void render(float[] samples, int offset, int frames, float amplitude) {
        }

        @Override
        public void render(float[] samples, int offset, int frames, float left, float right) {
        }

        @Override
        public void reset() {
        }

        @Override
        public float sampleRate() {
            return 8000.0f;
        }

        @Override
        public void stopAll() {
        }

        @Override
        public void sysEx(byte[] message) {
        }

        @Override
        public void volume(int channel, float volume) {
        }
    }

    private static final class ProbePlaybackEngine implements MLDPlaybackEngine {
        private final boolean resourceLive;

        private ProbePlaybackEngine(boolean resourceLive) {
            this.resourceLive = resourceLive;
        }

        @Override
        public MLDTrackControlMode trackControlMode() {
            return MLDTrackControlMode.NONE;
        }

        @Override
        public void reset() {
        }

        @Override
        public void handleExtB(MLDPlayer player, MLDPlayerTrack track, opendoja.audio.mld.MLDEvent event) {
        }

        @Override
        public void handleResource(MLDPlayer player, MLDPlayerTrack track, opendoja.audio.mld.MLDEvent event) {
        }

        @Override
        public void render(float[] samples, int offset, int frames, float left, float right,
                           boolean clamp, long framePosition) {
        }

        @Override
        public boolean hasLiveAudio(long framePosition) {
            return resourceLive;
        }

        @Override
        public int framesUntilSilence(long framePosition) {
            return resourceLive ? 128 : 0;
        }
    }
}
