package opendoja.audio.mld;

import java.util.ArrayList;
import java.util.Arrays;

final class MLDResourceAudioEngine {
    private static final int DEFAULT_LEVEL = 127;
    private static final int DEFAULT_PAN = 64;
    private static final int DEFAULT_BANK_LEVEL = 127;
    private static final int DEFAULT_BANK_PAN = 64;
    private static final int DEFAULT_GLOBAL_LEVEL = 64;
    private static final int PLUGIN_EXPORT_GAIN_SHIFT = 2;
    private static final int NATIVE_OUTPUT_SAMPLE_RATE = 32000;
    private static final float INV_MIX_SCALE = 1.0f / 8388608.0f;
    private static final int[] NATIVE_LEVEL_Q16 = new int[] {
            0, 4, 16, 36, 65, 101, 146, 199,
            260, 329, 406, 491, 585, 686, 796, 914,
            1040, 1174, 1316, 1466, 1625, 1791, 1966, 2149,
            2340, 2539, 2746, 2962, 3185, 3417, 3656, 3904,
            4160, 4424, 4697, 4977, 5265, 5562, 5867, 6180,
            6501, 6830, 7167, 7512, 7866, 8227, 8597, 8975,
            9361, 9755, 10157, 10568, 10986, 11413, 11848, 12291,
            12742, 13201, 13668, 14143, 14627, 15119, 15618, 16126,
            16642, 17166, 17699, 18239, 18788, 19344, 19909, 20482,
            21063, 21652, 22249, 22855, 23468, 24090, 24720, 25358,
            26004, 26658, 27320, 27991, 28669, 29356, 30051, 30754,
            31465, 32184, 32911, 33647, 34390, 35142, 35902, 36670,
            37446, 38230, 39022, 39823, 40631, 41448, 42273, 43106,
            43947, 44796, 45653, 46519, 47392, 48274, 49164, 50062,
            50968, 51882, 52805, 53735, 54674, 55620, 56575, 57538,
            58509, 59488, 60476, 61471, 62475, 63487, 64507, 65535
    };

    private static final int[] NATIVE_PAN_LEFT_Q16 = new int[] {
            65535, 65529, 65514, 65489, 65454, 65409, 65354, 65289,
            65214, 65129, 65034, 64929, 64814, 64689, 64554, 64410,
            64255, 64091, 63917, 63733, 63540, 63336, 63123, 62901,
            62668, 62426, 62175, 61914, 61644, 61364, 61075, 60776,
            60468, 60151, 59825, 59489, 59145, 58791, 58428, 58057,
            57676, 57287, 56889, 56482, 56067, 55643, 55211, 54770,
            54320, 53863, 53397, 52923, 52441, 51951, 51453, 50947,
            50433, 49912, 49383, 48846, 48302, 47750, 47191, 46625,
            46052, 45472, 44885, 44291, 43690, 43083, 42469, 41848,
            41221, 40588, 39948, 39303, 38651, 37994, 37330, 36661,
            35986, 35306, 34621, 33930, 33234, 32533, 31827, 31116,
            30400, 29680, 28955, 28225, 27492, 26754, 26012, 25266,
            24516, 23762, 23005, 22244, 21480, 20713, 19942, 19169,
            18392, 17613, 16831, 16046, 15259, 14469, 13678, 12884,
            12088, 11291, 10492, 9691, 8888, 8085, 7280, 6473,
            5666, 4858, 4050, 3240, 2431, 1620, 810, 0
    };

    private static final int[] NATIVE_PAN_RIGHT_Q16 = new int[] {
            0, 0, 810, 1620, 2431, 3240, 4050, 4858,
            5666, 6473, 7280, 8085, 8888, 9691, 10492, 11291,
            12088, 12884, 13678, 14469, 15259, 16046, 16831, 17613,
            18392, 19169, 19942, 20713, 21480, 22244, 23005, 23762,
            24516, 25266, 26012, 26754, 27492, 28225, 28955, 29680,
            30400, 31116, 31827, 32533, 33234, 33930, 34621, 35306,
            35986, 36661, 37330, 37994, 38651, 39303, 39948, 40588,
            41221, 41848, 42469, 43083, 43690, 44291, 44885, 45472,
            46052, 46625, 47191, 47750, 48302, 48846, 49383, 49912,
            50433, 50947, 51453, 51951, 52441, 52923, 53397, 53863,
            54320, 54770, 55211, 55643, 56067, 56482, 56889, 57287,
            57676, 58057, 58428, 58791, 59145, 59489, 59825, 60151,
            60468, 60776, 61075, 61364, 61644, 61914, 62175, 62426,
            62668, 62901, 63123, 63336, 63540, 63733, 63917, 64091,
            64255, 64410, 64554, 64689, 64814, 64929, 65034, 65129,
            65214, 65289, 65354, 65409, 65454, 65489, 65514, 65529
    };

    private final DecodedResourceAudio[] decodedCatalog;
    private final ChannelState[] channelStates = new ChannelState[64];
    private final ArrayList<ActiveClip> activeClips = new ArrayList<>();
    private final int[] initialAudioRoutes = new int[64];
    private final int outputSampleRate;

    MLDResourceAudioEngine(MLD mld, float sampleRate) {
        this.outputSampleRate = Math.max(1, Math.round(sampleRate));
        this.decodedCatalog = new DecodedResourceAudio[mld.adpcms.length];
        for (int i = 0; i < this.channelStates.length; i++) {
            this.channelStates[i] = new ChannelState();
        }
        for (int i = 0; i < mld.adpcms.length; i++) {
            this.decodedCatalog[i] = this.decodeCatalogEntry(mld.adpcms[i]);
        }
        this.parseThrdAudioRoutes(mld.thrd);
        this.reset();
    }

    void reset() {
        this.activeClips.clear();
        for (ChannelState state : this.channelStates) {
            state.level = DEFAULT_LEVEL;
            state.pan = DEFAULT_PAN;
        }
        for (int i = 0; i < this.channelStates.length; i++) {
            this.channelStates[i].route = this.initialAudioRoutes[i];
        }
    }

    void handleEvent(long framePosition, MLDEvent event) {
        int logicalChannel = clamp(0, this.channelStates.length - 1, event.channel);
        switch (event.id) {
            case MLD.EVENT_RESOURCE_START:
                this.startClip(framePosition, logicalChannel, event);
                break;
            case MLD.EVENT_RESOURCE_STOP:
                this.stopClip(framePosition, logicalChannel, event.resourceIndex);
                break;
            case MLD.EVENT_RESOURCE_LEVEL:
                this.channelStates[logicalChannel].level = clamp(0, 127, event.value2x >= 0 ? event.value2x : DEFAULT_LEVEL);
                break;
            case MLD.EVENT_RESOURCE_PAN:
                this.channelStates[logicalChannel].pan = clamp(0, 127, event.value2x >= 0 ? event.value2x : DEFAULT_PAN);
                break;
            case MLD.EVENT_RESOURCE_CONFIG:
                if (event.resourceAudioTarget) {
                    this.channelStates[logicalChannel].route = Math.max(0,
                            event.resourceConfigValue >= 0 ? event.resourceConfigValue : 0);
                }
                break;
            default:
                break;
        }
    }

    void render(float[] samples, int offset, int frames, float left, float right, boolean clamp, long framePosition) {
        this.pruneFinished(framePosition);
        if (this.activeClips.isEmpty()) {
            return;
        }

        float leftScale = left * INV_MIX_SCALE;
        float rightScale = right * INV_MIX_SCALE;
        long frameLimit = framePosition + frames;
        for (int i = 0; i < this.activeClips.size(); i++) {
            ActiveClip clip = this.activeClips.get(i);
            long clipEnd = clip.stopFrame >= 0 ? clip.stopFrame : clip.endFrame();
            long mixStart = Math.max(framePosition, clip.startFrame);
            long mixEnd = Math.min(frameLimit, clipEnd);
            if (mixStart >= mixEnd) {
                continue;
            }

            int inputIndex = (int) (mixStart - clip.startFrame);
            int outputIndex = offset + (int) (mixStart - framePosition) * 2;
            int mixFrames = (int) (mixEnd - mixStart);
            for (int frame = 0; frame < mixFrames; frame++) {
                int sample = clip.audio.frames[inputIndex + frame];
                int base = outputIndex + frame * 2;
                samples[base] += scaleMixedSample(sample, clip.leftGainQ16) * leftScale;
                samples[base + 1] += scaleMixedSample(sample, clip.rightGainQ16) * rightScale;
                if (clamp) {
                    samples[base] = clampSample(samples[base]);
                    samples[base + 1] = clampSample(samples[base + 1]);
                }
            }
        }
        this.pruneFinished(frameLimit);
    }

    boolean hasLiveAudio(long framePosition) {
        this.pruneFinished(framePosition);
        return !this.activeClips.isEmpty();
    }

    int framesUntilSilence(long framePosition) {
        this.pruneFinished(framePosition);
        int remaining = -1;
        for (int i = 0; i < this.activeClips.size(); i++) {
            ActiveClip clip = this.activeClips.get(i);
            long end = clip.stopFrame >= 0 ? clip.stopFrame : clip.endFrame();
            if (end <= framePosition) {
                continue;
            }
            int frames = (int) Math.min(Integer.MAX_VALUE, end - framePosition);
            if (remaining == -1 || frames < remaining) {
                remaining = frames;
            }
        }
        return Math.max(0, remaining);
    }

    private void startClip(long framePosition, int logicalChannel, MLDEvent event) {
        if (event.resourceIndex < 0 || event.resourceIndex >= this.decodedCatalog.length) {
            return;
        }
        DecodedResourceAudio decoded = this.decodedCatalog[event.resourceIndex];
        if (decoded == null || decoded.frames.length == 0) {
            return;
        }

        StereoGain gain = this.computeStereoGain(this.channelStates[logicalChannel], DEFAULT_LEVEL);
        this.activeClips.add(new ActiveClip(
                logicalChannel,
                event.resourceIndex,
                framePosition,
                decoded,
                gain.leftQ16,
                gain.rightQ16));
    }

    private void stopClip(long framePosition, int logicalChannel, int resourceIndex) {
        for (int i = this.activeClips.size() - 1; i >= 0; i--) {
            ActiveClip clip = this.activeClips.get(i);
            if (clip.stopFrame >= 0 || clip.logicalChannel != logicalChannel) {
                continue;
            }
            if (resourceIndex < 0 || clip.resourceIndex == resourceIndex) {
                clip.stopFrame = Math.max(framePosition, clip.startFrame);
                return;
            }
        }
    }

    private void pruneFinished(long framePosition) {
        for (int i = 0; i < this.activeClips.size(); i++) {
            ActiveClip clip = this.activeClips.get(i);
            long end = clip.stopFrame >= 0 ? clip.stopFrame : clip.endFrame();
            if (end > framePosition) {
                continue;
            }
            this.activeClips.remove(i--);
        }
    }

    private DecodedResourceAudio decodeCatalogEntry(MLDADPCM adat) {
        if (adat == null ||
                adat.selectorId != 0x81 ||
                adat.sampleRateHz <= 0 ||
                adat.channelCount != 1 ||
                adat.variantBit ||
                !MLDNativeADPCMDecoder.supportsLivePath(adat.sampleRateHz, adat.codedBits, adat.channelCount)) {
            return null;
        }

        int[] nativeFrames = MLDNativeADPCMDecoder.decodeLiveMonoNativeLane0(
                adat.sampleRateHz,
                adat.codedBits,
                adat.payload);
        int[] slotFrames = convertSlotFrames(nativeFrames);
        if (this.outputSampleRate == NATIVE_OUTPUT_SAMPLE_RATE) {
            return new DecodedResourceAudio(slotFrames);
        }
        return new DecodedResourceAudio(resampleLinear(slotFrames, NATIVE_OUTPUT_SAMPLE_RATE, this.outputSampleRate));
    }

    private void parseThrdAudioRoutes(byte[] thrd) {
        if (thrd == null || thrd.length <= 1) {
            return;
        }
        boolean[] seen = new boolean[this.initialAudioRoutes.length];
        for (int offset = 1; offset + 1 < thrd.length; offset += 2) {
            int logicalChannel = thrd[offset] & 0x0F;
            if (logicalChannel < 0 || logicalChannel >= this.initialAudioRoutes.length || seen[logicalChannel]) {
                continue;
            }
            int packed = thrd[offset + 1] & 0xFF;
            if ((packed & 0x20) == 0) {
                continue;
            }
            int rawSubvalue = packed & 0x1F;
            this.initialAudioRoutes[logicalChannel] = (rawSubvalue == 31 ? 0 : rawSubvalue + 2);
            seen[logicalChannel] = true;
        }
    }

    private StereoGain computeStereoGain(ChannelState state, int startLevel) {
        int combinedQ16 = combineNativeLevelQ16(startLevel, state.level, DEFAULT_BANK_LEVEL, DEFAULT_GLOBAL_LEVEL);
        int panIndex = clamp(0, 127, DEFAULT_BANK_PAN + state.pan - DEFAULT_PAN);
        int leftQ16 = combineQ16(combinedQ16, NATIVE_PAN_LEFT_Q16[panIndex]);
        int rightQ16 = combineQ16(combinedQ16, NATIVE_PAN_RIGHT_Q16[panIndex]);
        return new StereoGain(leftQ16, rightQ16);
    }

    private static int combineNativeLevelQ16(int slotLevel, int channelLevel, int bankLevel, int globalLevel) {
        int channelBankQ16 = combineQ16(nativeLevelQ16(channelLevel), nativeLevelQ16(bankLevel));
        int slotChannelBankQ16 = combineQ16(nativeLevelQ16(slotLevel), channelBankQ16);
        return combineQ16(nativeLevelQ16(globalLevel), slotChannelBankQ16);
    }

    private static int combineQ16(int leftQ16, int rightQ16) {
        return (leftQ16 * rightQ16) >>> 16;
    }

    private static int nativeLevelQ16(int value) {
        return NATIVE_LEVEL_Q16[clamp(0, 127, value)];
    }

    private static int[] convertSlotFrames(int[] decodedFrames) {
        if (decodedFrames.length == 0) {
            return new int[0];
        }
        int[] converted = new int[decodedFrames.length];
        for (int i = 4; i < decodedFrames.length; i++) {
            converted[i] = decodedFrames[i - 4] >> 1;
        }
        return converted;
    }

    private static int[] resampleLinear(int[] input, int inputRate, int outputRate) {
        if (input.length == 0 || inputRate == outputRate) {
            return Arrays.copyOf(input, input.length);
        }
        int outputLength = Math.max(1, (int) (((long) input.length * outputRate) / inputRate));
        int[] output = new int[outputLength];
        for (int i = 0; i < outputLength; i++) {
            double sourcePosition = ((double) i * inputRate) / outputRate;
            int sourceIndex = (int) sourcePosition;
            double fraction = sourcePosition - sourceIndex;
            int left = input[Math.min(input.length - 1, sourceIndex)];
            int right = input[Math.min(input.length - 1, sourceIndex + 1)];
            output[i] = (int) Math.round(left + ((right - left) * fraction));
        }
        return output;
    }

    private static int scaleMixedSample(int sample, int gainQ16) {
        return (int) ((((long) sample) * gainQ16) >> (15 - PLUGIN_EXPORT_GAIN_SHIFT));
    }

    private static float clampSample(float value) {
        return Math.max(-1.0f, Math.min(1.0f, value));
    }

    private static int clamp(int min, int max, int value) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class ChannelState {
        int level;
        int pan;
        int route;
    }

    private static final class StereoGain {
        final int leftQ16;
        final int rightQ16;

        StereoGain(int leftQ16, int rightQ16) {
            this.leftQ16 = leftQ16;
            this.rightQ16 = rightQ16;
        }
    }

    private static final class DecodedResourceAudio {
        final int[] frames;

        DecodedResourceAudio(int[] frames) {
            this.frames = frames;
        }
    }

    private static final class ActiveClip {
        final int logicalChannel;
        final int resourceIndex;
        final long startFrame;
        final DecodedResourceAudio audio;
        final int leftGainQ16;
        final int rightGainQ16;
        long stopFrame = -1L;

        ActiveClip(
                int logicalChannel,
                int resourceIndex,
                long startFrame,
                DecodedResourceAudio audio,
                int leftGainQ16,
                int rightGainQ16) {
            this.logicalChannel = logicalChannel;
            this.resourceIndex = resourceIndex;
            this.startFrame = startFrame;
            this.audio = audio;
            this.leftGainQ16 = leftGainQ16;
            this.rightGainQ16 = rightGainQ16;
        }

        long endFrame() {
            return this.startFrame + this.audio.frames.length;
        }
    }
}
