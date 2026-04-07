package opendoja.probes;

import opendoja.audio.mld.MLD;
import opendoja.audio.mld.MLDPlayer;
import opendoja.audio.mld.MLDPlayerEvent;
import opendoja.audio.mld.MLDSynth;
import opendoja.audio.mld.SamplerProvider;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reports sequence duration and playback-event timing for an MLD asset.
 */
public final class MldDurationProbe {
    private MldDurationProbe() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("usage: MldDurationProbe <mld-file> [loop-enabled]");
        }

        byte[] bytes = Files.readAllBytes(Path.of(args[0]));
        boolean loopEnabled = args.length == 2 && Boolean.parseBoolean(args[1]);
        MLDSynth synth = MLDSynth.resolveConfigured();
        SamplerProvider provider = synth.createSamplerProvider();
        float sampleRate = synth.defaultSampleRate;

        MLD mld = new MLD(bytes);
        MLDPlayer player = new MLDPlayer(mld, provider, sampleRate);
        player.setPlaybackEventsEnabled(true);
        player.setLoopEnabled(loopEnabled);

        float[] samples = new float[1024 * 2];
        long totalFrames = 0L;
        int endEvents = 0;
        int loopEvents = 0;
        int iterations = 0;
        double endEventSeconds = Double.NaN;

        while (true) {
            int rendered = player.render(samples, 0, 1024);
            if (rendered > 0) {
                totalFrames += rendered;
            }
            for (MLDPlayerEvent event : player.getEvents()) {
                if (event.type == MLDPlayer.EVENT_END) {
                    endEvents++;
                    if (Double.isNaN(endEventSeconds)) {
                        endEventSeconds = event.time;
                    }
                } else if (event.type == MLDPlayer.EVENT_LOOP) {
                    loopEvents++;
                }
            }
            if (rendered < 0) {
                break;
            }
            if (++iterations > 1_000_000) {
                throw new IllegalStateException("probe guard tripped");
            }
        }

        double renderedSeconds = totalFrames / sampleRate;
        System.out.println("sampleRate=" + sampleRate);
        System.out.println("declaredDuration=" + mld.getDuration(true));
        System.out.println("renderedSeconds=" + renderedSeconds);
        System.out.println("totalFrames=" + totalFrames);
        System.out.println("endEvents=" + endEvents);
        System.out.println("endEventSeconds=" + endEventSeconds);
        System.out.println("loopEvents=" + loopEvents);
        dumpTempoEvents(mld);
    }

    private static void dumpTempoEvents(MLD mld) throws Exception {
        Field tracksField = field(MLD.class, "tracks");
        Object[] tracks = (Object[]) tracksField.get(mld);
        Class<?> eventClass = Class.forName("opendoja.audio.mld.MLDEvent");
        Field typeField = field(eventClass, "type");
        Field idField = field(eventClass, "id");
        Field deltaField = field(eventClass, "delta");
        Field timebaseField = field(eventClass, "timebase");
        Field tempoField = field(eventClass, "tempo");
        int mldExtB = field(MLD.class, "EVENT_TYPE_EXT_B").getInt(null);
        int timebaseTempo = field(MLD.class, "EVENT_TIMEBASE_TEMPO").getInt(null);

        int tick = 0;
        for (int trackIndex = 0; trackIndex < tracks.length; trackIndex++) {
            java.util.List<?> track = (java.util.List<?>) tracks[trackIndex];
            tick = 0;
            for (Object event : track) {
                tick += deltaField.getInt(event);
                if (typeField.getInt(event) != mldExtB) {
                    continue;
                }
                int id = idField.getInt(event);
                if ((id & 0xF0) != timebaseTempo) {
                    continue;
                }
                System.out.println("tempoEvent track=" + trackIndex
                        + " tick=" + tick
                        + " id=0x" + Integer.toHexString(id)
                        + " timebase=" + timebaseField.getInt(event)
                        + " tempo=" + tempoField.getInt(event));
            }
        }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
