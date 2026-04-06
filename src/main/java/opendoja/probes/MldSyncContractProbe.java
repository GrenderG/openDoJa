package opendoja.probes;

import opendoja.audio.mld.MLD;
import opendoja.audio.mld.MLDEvent;
import opendoja.audio.mld.MLDPlayer;
import opendoja.audio.mld.MLDPlayerEvent;
import opendoja.audio.mld.MLDSynth;
import opendoja.audio.mld.SamplerProvider;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class MldSyncContractProbe {
    private MldSyncContractProbe() {
    }

    public static void main(String[] args) throws Exception {
        DemoLog.enableInfoLogging();
        if (args.length < 3 || args.length > 4) {
            throw new IllegalArgumentException(
                    "usage: MldSyncContractProbe <mld-file> <channel> <note> [chunks]");
        }

        Path path = Path.of(args[0]);
        int channel = Integer.parseInt(args[1]);
        int note = Integer.parseInt(args[2]);
        int chunks = args.length >= 4 ? Integer.parseInt(args[3]) : 512;

        byte[] bytes = Files.readAllBytes(path);
        MLD mld = new MLD(bytes);

        int staticMatches = dumpStaticMatches(mld, channel, note);
        int normalizedMatches = countNormalizedMatches(mld, channel, note);

        MLDSynth synth = MLDSynth.resolveConfigured();
        SamplerProvider provider = synth.createSamplerProvider();
        MLDPlayer player = new MLDPlayer(mld, provider, synth.defaultSampleRate);
        player.setPlaybackEventsEnabled(true);
        player.addEventNote(channel, note);

        float[] buffer = new float[256 * 2];
        int runtimeMatches = 0;
        for (int i = 0; i < chunks; i++) {
            int frames = player.render(buffer, 0, 256);
            for (MLDPlayerEvent event : player.getEvents()) {
                if (event.type != MLDPlayer.EVENT_KEY) {
                    continue;
                }
                runtimeMatches++;
                if (runtimeMatches <= 8) {
                    DemoLog.info(MldSyncContractProbe.class,
                            "runtime match #" + runtimeMatches
                                    + " time=" + event.time
                                    + " channel=" + event.channel
                                    + " note=" + event.note
                                    + " keyNumber=" + event.keyNumber
                                    + " normalizedKey=" + event.data);
                }
            }
            if (frames < 0) {
                break;
            }
        }

        DemoLog.info(MldSyncContractProbe.class,
                "summary file=" + path.getFileName()
                        + " channel=" + channel
                        + " note=" + note
                        + " staticMatches=" + staticMatches
                        + " normalizedKeyLiteralMatches=" + normalizedMatches
                        + " runtimeMatches=" + runtimeMatches);
    }

    private static int dumpStaticMatches(MLD mld, int channel, int note) throws Exception {
        Field tracksField = field(MLD.class, "tracks");
        Field typeField = field(MLDEvent.class, "type");
        Field channelField = field(MLDEvent.class, "channel");
        Field keyNumberField = field(MLDEvent.class, "keyNumber");
        Field keyField = field(MLDEvent.class, "key");
        Field offsetField = field(MLDEvent.class, "offset");
        Object[] tracks = (Object[]) tracksField.get(mld);
        int matches = 0;
        for (int trackIndex = 0; trackIndex < tracks.length; trackIndex++) {
            @SuppressWarnings("unchecked")
            List<Object> events = (List<Object>) tracks[trackIndex];
            for (Object event : events) {
                if (typeField.getInt(event) != 0) {
                    continue;
                }
                if (channelField.getInt(event) != channel) {
                    continue;
                }
                int normalizedKey = keyField.getInt(event);
                if (normalizedKey + 69 != note) {
                    continue;
                }
                matches++;
                if (matches <= 8) {
                    DemoLog.info(MldSyncContractProbe.class,
                            "static match #" + matches
                                    + " track=" + trackIndex
                                    + " offset=0x" + Integer.toHexString(offsetField.getInt(event))
                                    + " channel=" + channel
                                    + " note=" + note
                                    + " keyNumber=" + keyNumberField.getInt(event)
                                    + " normalizedKey=" + normalizedKey);
                }
            }
        }
        return matches;
    }

    private static int countNormalizedMatches(MLD mld, int channel, int normalizedKey) throws Exception {
        Field tracksField = field(MLD.class, "tracks");
        Field typeField = field(MLDEvent.class, "type");
        Field channelField = field(MLDEvent.class, "channel");
        Field keyField = field(MLDEvent.class, "key");
        Object[] tracks = (Object[]) tracksField.get(mld);
        int matches = 0;
        for (Object track : tracks) {
            for (Object event : (List<?>) track) {
                if (typeField.getInt(event) == 0 &&
                        channelField.getInt(event) == channel &&
                        keyField.getInt(event) == normalizedKey) {
                    matches++;
                }
            }
        }
        return matches;
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
