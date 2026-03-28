package opendoja.demo;

import opendoja.audio.mld.MLD;
import opendoja.audio.mld.MldSynth;
import opendoja.audio.mld.MLDPlayer;
import opendoja.audio.mld.MLDPlayerEvent;
import opendoja.audio.mld.SamplerProvider;

import java.nio.file.Files;
import java.nio.file.Path;

public final class MldRenderProbe {
    private MldRenderProbe() {
    }

    public static void main(String[] args) throws Exception {
        DemoLog.enableInfoLogging();
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("usage: MldRenderProbe <scratchpad-or-mld-file> [iterations]");
        }
        byte[] source = Files.readAllBytes(Path.of(args[0]));
        byte[] mldBytes = firstMeloChunk(source);
        MLD mld = new MLD(mldBytes);
        int iterations = args.length >= 2 ? Integer.parseInt(args[1]) : 8;
        if (iterations < 0) {
            throw new IllegalArgumentException("iterations must be non-negative");
        }
        MldSynth synth = MldSynth.resolveConfigured();
        SamplerProvider provider = synth.createSamplerProvider();
        float sampleRate = synth.defaultSampleRate;
        MLDPlayer player = new MLDPlayer(mld, provider, sampleRate);
        player.setPlaybackEventsEnabled(true);
        float[] samples = new float[256 * 2];
        double energy = 0.0;
        float peak = 0.0f;
        int hardClipped = 0;
        int framesRendered = 0;
        int loopEvents = 0;
        int endEvents = 0;
        for (int i = 0; i < iterations; i++) {
            int frames = player.render(samples, 0, 256);
            if (frames > 0) {
                framesRendered += frames;
                for (int s = 0; s < frames * 2; s++) {
                    float sample = samples[s];
                    float abs = Math.abs(sample);
                    energy += sample * sample;
                    if (abs > peak) {
                        peak = abs;
                    }
                    if (abs >= 1.0f) {
                        hardClipped++;
                    }
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
                + " max=" + peak
                + " hard=" + hardClipped
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
        return source;
    }
}
