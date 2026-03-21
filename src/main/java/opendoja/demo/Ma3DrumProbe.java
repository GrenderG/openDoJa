package opendoja.demo;

import opendoja.audio.mld.ma3.MA3SamplerProvider;
import opendoja.audio.mld.ma3.Sampler;

public final class Ma3DrumProbe {
    private Ma3DrumProbe() {
    }

    public static void main(String[] args) {
        Sampler sampler = new MA3SamplerProvider(
                MA3SamplerProvider.FM_MA3_4OP,
                MA3SamplerProvider.FM_MA3_4OP,
                MA3SamplerProvider.WAVE_DRUM_MA3)
                .instance(MA3SamplerProvider.SAMPLE_RATE);
        sampler.drumEnable(0, true);
        float[] samples = new float[256 * 2];
        int soundingKeys = 0;
        double totalRms = 0.0;
        for (int key = -24; key <= 36; key++) {
            sampler.reset();
            sampler.drumEnable(0, true);
            sampler.keyOn(0, key, 1.0f);
            sampler.render(samples, 0, 256);
            double energy = 0.0;
            for (float sample : samples) {
                energy += sample * sample;
            }
            double rms = Math.sqrt(energy / samples.length);
            if (rms > 0.0001d) {
                soundingKeys++;
                totalRms += rms;
            }
        }
        System.out.printf("soundingKeys=%d avgRms=%.6f%n",
                soundingKeys,
                soundingKeys == 0 ? 0.0 : totalRms / soundingKeys);
    }
}
