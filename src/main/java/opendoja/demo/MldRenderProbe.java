package opendoja.demo;

import opendoja.audio.mld.ma3.MA3SamplerProvider;
import opendoja.audio.mld.ma3.MLD;
import opendoja.audio.mld.ma3.MLDPlayer;
import opendoja.audio.mld.ma3.MLDPlayerEvent;

import java.nio.file.Files;
import java.nio.file.Path;

public final class MldRenderProbe {
    private MldRenderProbe() {
    }

    public static void main(String[] args) throws Exception {
        DemoLog.enableInfoLogging();
        if (args.length != 1) {
            throw new IllegalArgumentException("usage: MldRenderProbe <scratchpad-or-mld-file>");
        }
        byte[] source = Files.readAllBytes(Path.of(args[0]));
        byte[] mldBytes = firstMeloChunk(source);
        MLD mld = new MLD(mldBytes);
        MLDPlayer player = new MLDPlayer(mld,
                new MA3SamplerProvider(
                        MA3SamplerProvider.FM_MA3_4OP,
                        MA3SamplerProvider.FM_MA3_4OP,
                        MA3SamplerProvider.WAVE_DRUM_MA3),
                MA3SamplerProvider.SAMPLE_RATE);
        player.setPlaybackEventsEnabled(true);
        float[] samples = new float[256 * 2];
        double energy = 0.0;
        int framesRendered = 0;
        int loopEvents = 0;
        int endEvents = 0;
        for (int i = 0; i < 8; i++) {
            int frames = player.render(samples, 0, 256);
            if (frames > 0) {
                framesRendered += frames;
                for (int s = 0; s < frames * 2; s++) {
                    energy += samples[s] * samples[s];
                }
            }
            for (MLDPlayerEvent event : player.getEvents()) {
                if (event.type == MLDPlayer.EVENT_LOOP) {
                    loopEvents++;
                } else if (event.type == MLDPlayer.EVENT_END) {
                    endEvents++;
                }
            }
            if (frames < 0) {
                break;
            }
        }
        double rms = framesRendered == 0 ? 0.0 : Math.sqrt(energy / (framesRendered * 2.0));
        DemoLog.info(MldRenderProbe.class, "frames=" + framesRendered
                + " rms=" + rms
                + " loopEvents=" + loopEvents
                + " endEvents=" + endEvents
                + " duration=" + mld.getDuration(true));
    }

    private static byte[] firstMeloChunk(byte[] source) {
        for (int i = 0; i <= source.length - 8; i++) {
            if (source[i] != 'm' || source[i + 1] != 'e' || source[i + 2] != 'l' || source[i + 3] != 'o') {
                continue;
            }
            int length = ((source[i + 4] & 0xFF) << 24)
                    | ((source[i + 5] & 0xFF) << 16)
                    | ((source[i + 6] & 0xFF) << 8)
                    | (source[i + 7] & 0xFF);
            int end = Math.min(source.length, i + 8 + Math.max(length, 0));
            byte[] mld = new byte[end - i];
            System.arraycopy(source, i, mld, 0, mld.length);
            return mld;
        }
        throw new IllegalArgumentException("No melo chunk found");
    }
}
