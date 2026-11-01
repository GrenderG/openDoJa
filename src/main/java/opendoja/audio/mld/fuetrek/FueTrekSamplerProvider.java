package opendoja.audio.mld.fuetrek;

import opendoja.audio.mld.Sampler;
import opendoja.audio.mld.SamplerProvider;

/**
 * FueTrek-oriented sampler profile validated against the authoritative lib002
 * stack (`MFiSoundLibMFi5.dll` plus `MFiSynth_ft.dll`), with
 * `SH_MFi4PlugIn.dll` retained as the matching plugin-side ABI wrapper.
 */
public final class FueTrekSamplerProvider implements SamplerProvider {
    // `PlayerGetProperty` writes the same capability block in lib002 and the plugin:
    //   +0x08 = 0x20, +0x0c = 0x7d00, +0x14 = 0x80, +0x18 = 0x1000.
    // `PlayerCreate` in both binaries proves the frame contract is 128-frame aligned
    // with a hard maximum of 4096. OpenDoJa uses 128 as the smallest positive
    // normalized block when sanitizing user buffer requests.
    public static final float SAMPLE_RATE = 32000.0f;
    // Advertised reservation. The native port enables borrowing from the
    // shared 96-voice DSP pool (100059d0 -> 10005bc0 -> 10005d40).
    public static final int MAX_POLYPHONY = 32;
    public static final int MIN_FRAME_SIZE = 128;
    public static final int MAX_FRAME_SIZE = 4096;
    public static final int FRAME_GRANULARITY = 128;

    private final FueTrekRom rom = FueTrekRom.load();

    @Override
    public Sampler instance(float sampleRate) {
        return new FueTrekSampler(rom, sampleRate, MAX_POLYPHONY);
    }
}
