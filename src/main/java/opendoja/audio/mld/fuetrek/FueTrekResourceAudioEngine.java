package opendoja.audio.mld.fuetrek;

import opendoja.audio.mld.*;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * lib002/FueTrek implementation of top-level MLD resource audio.
 *
 * <p>This engine handles audio that lives outside the normal note stream:
 * top-level {@code ainf}/{@code adat} resources triggered by {@code 0x7f}
 * resource events. In practice this is the path used by titles such as Taiko's
 * top-level ADPCM effects and the FueTrek-routed stereo/3D resource playback
 * corpus.</p>
 *
 * <p>The generic MLD parser only surfaces the resource chunks and events.
 * Everything after that, including ADPCM decode shape, gain tables, route
 * defaults, and spatial motion handling, is FueTrek-specific behavior and
 * therefore lives in this synth package.</p>
 */
public final class FueTrekResourceAudioEngine
    implements MLDResourceAudioEngine {
    private static final double DEG_TO_RAD = Math.PI / 180.0;
    private static final int DEFAULT_LEVEL = 127;
    private static final int DEFAULT_PAN = 64;
    private static final int DEFAULT_BANK_LEVEL = 127;
    private static final int DEFAULT_BANK_PAN = 64;
    private static final int DEFAULT_GLOBAL_LEVEL = 64;
    private static final int PLUGIN_EXPORT_GAIN_SHIFT = 2;
    private static final int NATIVE_OUTPUT_SAMPLE_RATE = 32000;
    private static final float INV_MIX_SCALE = 1.0f / 8388608.0f;
    // DSModule rounds its output block up to a multiple of the callback size
    // covering at least 4096 frames (10003fd0). The SDK default callback is512.
    private static final int NATIVE_MOTION_BLOCK_FRAMES = 4096;
    private final DecodedResourceAudio[] decodedCatalog;
    private final ChannelState[] channelStates = new ChannelState[64];
    private final ArrayList<ActiveClip> activeClips = new ArrayList<>();
    private final int[] initialAudioRouteRaw = new int[64];
    private final int[] initialSynthRouteRaw = new int[64];
    private final int outputSampleRate;
    private int hostVolumePercent = 100;
    private int masterLevel = 127;
    private int masterPan = 64;

    public FueTrekResourceAudioEngine(MLD mld, float sampleRate) {
        this.outputSampleRate = Math.max(1, Math.round(sampleRate));
        MLDADPCM[] adpcms = mld.resourceAdpcms();
        this.decodedCatalog = new DecodedResourceAudio[adpcms.length];
        Arrays.fill(this.initialAudioRouteRaw, -1);
        Arrays.fill(this.initialSynthRouteRaw, -1);
        for (int i = 0; i < this.channelStates.length; i++) {
            this.channelStates[i] = new ChannelState();
        }
        for (int i = 0; i < adpcms.length; i++) {
            this.decodedCatalog[i] = this.decodeCatalogEntry(adpcms[i]);
        }
        this.parseThrdAudioRoutes(mld.resourceThreadDefaults());
        this.reset();
    }

    public void reset() {
        this.activeClips.clear();
        this.masterLevel = 127;
        this.masterPan = 64;
        for (ChannelState state : this.channelStates) {
            state.level = DEFAULT_LEVEL;
            state.pan = DEFAULT_PAN;
            state.auxMotion.clear();
        }
        for (int i = 0; i < this.channelStates.length; i++) {
            this.channelStates[i].audioRouteRaw = this.initialAudioRouteRaw[i];
            this.channelStates[i].synthRouteRaw = this.initialSynthRouteRaw[i];
        }
    }

    public void setHostVolumeLevel(int value) { this.hostVolumePercent = value; }
    public void setMasterLevel(int value) { this.masterLevel = value & 127; }
    public void setMasterPan(int value) { this.masterPan = value & 127; }

    public void handleEvent(long framePosition, MLDEvent event) {
        int logicalChannel = clamp(0, this.channelStates.length - 1,
            event.getChannel());
        switch (event.getId()) {
            case MLD.EVENT_RESOURCE_START:
                this.startClip(framePosition, logicalChannel, event);
                break;
            case MLD.EVENT_RESOURCE_STOP:
                this.stopClip(framePosition, logicalChannel,
                    event.getResourceIndex());
                break;
            case MLD.EVENT_RESOURCE_LEVEL:
                this.channelStates[logicalChannel].level = clamp(0, 127,
                    event.getValue2x() >= 0 ? event.getValue2x() :
                        DEFAULT_LEVEL);
                break;
            case MLD.EVENT_RESOURCE_PAN:
                this.channelStates[logicalChannel].pan = clamp(0, 127,
                    event.getValue2x() >= 0 ? event.getValue2x() :
                        DEFAULT_PAN);
                break;
            case MLD.EVENT_RESOURCE_CONFIG:
                if (event.getResourceAudioTarget()) {
                    int routeRaw = event.getResourceConfigClear() ? -1 :
                        Math.max(0, event.getResourceConfigRawValue());
                    this.channelStates[logicalChannel].audioRouteRaw = routeRaw;
                    this.updateLiveAudioRoute(framePosition, logicalChannel,
                        routeRaw, event.getResourceConfigClear());
                    if (event.getResourceConfigClear()) {
                        this.channelStates[logicalChannel].auxMotion.clear();
                    }
                } else {
                    int routeRaw = event.getResourceConfigClear() ? -1 :
                        Math.max(0, event.getResourceConfigRawValue());
                    this.channelStates[logicalChannel].synthRouteRaw = routeRaw;
                    this.updateLiveSynthRoute(logicalChannel, routeRaw);
                }
                break;
            case MLD.EVENT_RESOURCE_AUX:
                this.handleAuxMotion(framePosition, logicalChannel, event);
                break;
            default:
                break;
        }
    }

    public void render(float[] samples, int offset, int frames, float left, float right, boolean clamp, long framePosition) {
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
            long segmentStart = mixStart;
            while (segmentStart < mixEnd) {
                this.advanceClipMotion(clip, segmentStart);
                long segmentEnd = Math.min(mixEnd, this.nextSpatialBoundary(clip, segmentStart));
                int segmentFrames = (int) (segmentEnd - segmentStart);
                int segmentInputIndex = inputIndex + (int) (segmentStart - mixStart);
                int segmentOutputIndex = outputIndex + (int) (segmentStart - mixStart) * 2;
                StereoGain spatialGain = this.computeSpatialGain(clip);
                int leftGainQ16 = combineQ16(clip.leftGainQ16, spatialGain.leftQ16);
                int rightGainQ16 = combineQ16(clip.rightGainQ16, spatialGain.rightQ16);
                ChannelState channel = this.channelStates[clip.logicalChannel];
                //30320 combines channel/host/master before2f4f0 applies the
                //descriptor's start level. Apply curves after byte truncations.
                int level = channel.level * this.hostVolumePercent * this.masterLevel / 127 / 100 & 127;
                level = clip.startLevel * level / 127;
                int leftPan = FueTrekCustomAudioTables.outputPanQ12(level, channel.pan, this.masterPan, false);
                int rightPan = FueTrekCustomAudioTables.outputPanQ12(level, channel.pan, this.masterPan, true);
                for (int frame = 0; frame < segmentFrames; frame++) {
                    int sample = clip.audio.frames[segmentInputIndex + frame];
                    int rightSample = clip.audio.rightFrames[segmentInputIndex + frame];
                    int base = segmentOutputIndex + frame * 2;
                    if (clip.audio.nativePcm) {
                        //307e0 truncates the Q12 multiply;1ca00 exports the
                        //signed16 PCM*16384, or float PCM/128 in our mix units.
                        samples[base] += (sample * leftPan / 4096) / 128.0f * left;
                        samples[base + 1] += (rightSample * rightPan / 4096) / 128.0f * right;
                    } else {
                        //The alternative PCM selector retains the earlier
                        //adapter until its decoder binding is verified.
                        float host = this.hostVolumePercent / 100.0f;
                        samples[base] += scaleMixedSample(sample, leftGainQ16) * leftScale * host;
                        samples[base + 1] += scaleMixedSample(rightSample, rightGainQ16) * rightScale * host;
                    }
                    if (clamp) {
                        samples[base] = clampSample(samples[base]);
                        samples[base + 1] = clampSample(samples[base + 1]);
                    }
                }
                segmentStart = segmentEnd;
            }
        }
        this.pruneFinished(frameLimit);
    }

    public boolean hasLiveAudio(long framePosition) {
        this.pruneFinished(framePosition);
        return !this.activeClips.isEmpty();
    }

    public int framesUntilSilence(long framePosition) {
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
        int startLevel = event.getResourceStartLevel();
        if (event.getResourceIndex() < 0 ||
            event.getResourceIndex() >= 63 || startLevel < 0 ||
            event.getResourceIndex() >= this.decodedCatalog.length) {
            return;
        }
        DecodedResourceAudio decoded =
            this.decodedCatalog[event.getResourceIndex()];
        if (decoded == null || decoded.frames.length == 0) {
            return;
        }

        this.retireMatchingClips(framePosition, logicalChannel,
            event.getResourceIndex());
        StereoGain gain = this.computeStereoGain(
            this.channelStates[logicalChannel], DEFAULT_LEVEL);
        AuxMotionPoint[] auxMotion = snapshotAuxMotion(
            this.channelStates[logicalChannel], event.getResourceIndex(),
            framePosition);
        ActiveClip clip = new ActiveClip(
                logicalChannel,
                event.getResourceIndex(),
                startLevel,
                framePosition,
                decoded,
                this.channelStates[logicalChannel].audioRouteRaw,
                this.channelStates[logicalChannel].synthRouteRaw,
                nativeRouteSlotId(event.getResourceIndex()),
                auxMotion.length,
                gain.leftQ16,
                gain.rightQ16);
        for (AuxMotionPoint point : auxMotion) {
            this.queueMotion(clip, point);
        }
        this.advanceClipMotion(clip, framePosition);
        this.activeClips.add(clip);
    }

    private void appendLiveMotion(long framePosition, int logicalChannel, AuxMotionPoint point) {
        for (int i = 0; i < this.activeClips.size(); i++) {
            ActiveClip clip = this.activeClips.get(i);
            if (clip.stopFrame >= 0 || clip.logicalChannel != logicalChannel || clip.resourceIndex != point.resourceIndex) {
                continue;
            }
            this.advanceClipMotion(clip, framePosition);
            this.queueMotion(clip, point);
        }
    }

    private void updateLiveAudioRoute(long framePosition, int logicalChannel, int routeRaw, boolean clear) {
        for (int i = 0; i < this.activeClips.size(); i++) {
            ActiveClip clip = this.activeClips.get(i);
            if (clip.stopFrame >= 0 || clip.logicalChannel != logicalChannel) {
                continue;
            }
            this.advanceClipMotion(clip, framePosition);
            clip.audioRouteRaw = routeRaw;
            if (!clear) {
                continue;
            }
            clip.liveMotion.clear();
            clip.nextMotionIndex = 0;
            clip.motion.set(0, 0, 0, 0, 0, this.outputSampleRate, NATIVE_MOTION_BLOCK_FRAMES);
            clip.nextMotionBlockFrame = framePosition + NATIVE_MOTION_BLOCK_FRAMES;
        }
    }

    private void updateLiveSynthRoute(int logicalChannel, int routeRaw) {
        for (int i = 0; i < this.activeClips.size(); i++) {
            ActiveClip clip = this.activeClips.get(i);
            if (clip.stopFrame >= 0 || clip.logicalChannel != logicalChannel) {
                continue;
            }
            clip.synthRouteRaw = routeRaw;
        }
    }

    private void handleAuxMotion(long framePosition, int logicalChannel, MLDEvent event) {
        if (event.getResourceAuxStrength() < 0 ||
                event.getResourceAuxAzimuthDegrees() == Integer.MIN_VALUE ||
                event.getResourceAuxElevationDegrees() == Integer.MIN_VALUE ||
                event.getResourceAuxDurationRaw() < 0) {
            return;
        }
        AuxMotionPoint point = new AuxMotionPoint(
                framePosition,
                event.getResourceIndex(),
                event.getResourceAuxStrength(),
                event.getResourceAuxAzimuthDegrees(),
                event.getResourceAuxElevationDegrees(),
                event.getResourceAuxDurationRaw());
        this.channelStates[logicalChannel].auxMotion.add(point);
        this.appendLiveMotion(framePosition, logicalChannel, point);
    }

    private void stopClip(long framePosition, int logicalChannel, int resourceIndex) {
        for (int i = this.activeClips.size() - 1; i >= 0; i--) {
            ActiveClip clip = this.activeClips.get(i);
            if (clip.stopFrame >= 0 || clip.logicalChannel != logicalChannel) {
                continue;
            }
            if (resourceIndex < 0 || clip.resourceIndex == resourceIndex) {
                clip.stopFrame = Math.max(framePosition, clip.startFrame);
            }
        }
    }

    private void retireMatchingClips(long framePosition, int logicalChannel, int resourceIndex) {
        for (int i = this.activeClips.size() - 1; i >= 0; i--) {
            ActiveClip clip = this.activeClips.get(i);
            if (clip.stopFrame >= 0) {
                continue;
            }
            if (clip.logicalChannel != logicalChannel || clip.resourceIndex != resourceIndex) {
                continue;
            }
            clip.stopFrame = Math.max(framePosition, clip.startFrame);
        }
    }

    private static AuxMotionPoint[] snapshotAuxMotion(ChannelState state, int resourceIndex, long framePosition) {
        if (state.auxMotion.isEmpty()) {
            return new AuxMotionPoint[0];
        }
        ArrayList<AuxMotionPoint> matching = new ArrayList<>();
        for (int i = 0; i < state.auxMotion.size(); i++) {
            AuxMotionPoint point = state.auxMotion.get(i);
            if (point.framePosition > framePosition) {
                continue;
            }
            if (point.resourceIndex == resourceIndex) {
                matching.add(point);
            }
        }
        return matching.toArray(new AuxMotionPoint[0]);
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

    private void queueMotion(ActiveClip clip, AuxMotionPoint point) {
        clip.liveMotion.add(point);
    }

    private void advanceClipMotion(ActiveClip clip, long framePosition) {
        while (clip.nextMotionIndex < clip.liveMotion.size()) {
            AuxMotionPoint point = clip.liveMotion.get(clip.nextMotionIndex);
            if (point.framePosition > framePosition) {
                break;
            }
            this.advanceBlockMotion(clip, point.framePosition);
            clip.motion.set(2, point.nativeXCentiUnits, point.nativeYCentiUnits,
                    point.nativeZCentiUnits, point.durationRaw, this.outputSampleRate, NATIVE_MOTION_BLOCK_FRAMES);
            clip.nextMotionBlockFrame = point.framePosition + NATIVE_MOTION_BLOCK_FRAMES;
            clip.nextMotionIndex++;
        }
        this.advanceBlockMotion(clip, framePosition);
    }

    private void advanceBlockMotion(ActiveClip clip, long framePosition) {
        while (clip.nextMotionBlockFrame != Long.MAX_VALUE && clip.nextMotionBlockFrame <= framePosition) {
            clip.motion.advanceBlock();
            clip.nextMotionBlockFrame += NATIVE_MOTION_BLOCK_FRAMES;
            if (clip.motion.remainingBlocks == 0) {
                clip.nextMotionBlockFrame = Long.MAX_VALUE;
            }
        }
    }

    private long nextSpatialBoundary(ActiveClip clip, long framePosition) {
        long next = Long.MAX_VALUE;
        if (clip.nextMotionIndex < clip.liveMotion.size()) {
            long eventFrame = clip.liveMotion.get(clip.nextMotionIndex).framePosition;
            if (eventFrame > framePosition) {
                next = Math.min(next, eventFrame);
            }
        }
        if (clip.nextMotionBlockFrame > framePosition) {
            next = Math.min(next, clip.nextMotionBlockFrame);
        }
        return next;
    }

    private StereoGain computeSpatialGain(ActiveClip clip) {
        return new StereoGain(0x10000, 0x10000);
    }

    private DecodedResourceAudio decodeCatalogEntry(MLDADPCM adat) {
        if (adat == null || adat.sampleRateHz() <= 0 ||
            (adat.channelCount() != 1 && adat.channelCount() != 2)) {
            return null;
        }

        int[] slotFrames;
        int[] rightFrames;
        int decodedSampleRate;
        boolean nativePcm = false;
        if (adat.selectorId() == 0x81 &&
                !adat.variantBit() &&
                MLDNativeADPCMDecoder.supportsLivePath(adat.sampleRateHz(),
                    adat.codedBits(), adat.channelCount())) {
            byte[] payload = adat.payload();
            int planeLength = payload.length / adat.channelCount();
            byte[] leftPayload = adat.channelCount() == 1 ? payload
                    : Arrays.copyOfRange(payload, 0, planeLength);
            var decoder = new MLDNativeADPCMDecoder.LiveMonoStream(adat.sampleRateHz(), adat.codedBits());
            slotFrames = decoder.decodeNextBufferRawPcm16(leftPayload, leftPayload.length * decoder.framesPerByte());
            nativePcm = true;
            // lib002 0x10027030: right plane starts halfway through the
            // coded payload. Each plane has its own predictor/filter state.
            if (adat.channelCount() == 1) rightFrames = slotFrames;
            else {
                byte[] rightPayload = Arrays.copyOfRange(payload, planeLength, planeLength * 2);
                var rightDecoder = new MLDNativeADPCMDecoder.LiveMonoStream(adat.sampleRateHz(), adat.codedBits());
                rightFrames = rightDecoder.decodeNextBufferRawPcm16(rightPayload, rightPayload.length * rightDecoder.framesPerByte());
            }
            decodedSampleRate = NATIVE_OUTPUT_SAMPLE_RATE;
        } else if (adat.selectorId() == 0x80 && adat.codedBits() == 16 && adat.channelCount() == 1) {
            slotFrames = decodePcm16Mono(adat.payload());
            rightFrames = slotFrames;
            decodedSampleRate = adat.sampleRateHz();
        } else {
            return null;
        }

        if (this.outputSampleRate == decodedSampleRate) {
            return new DecodedResourceAudio(slotFrames, rightFrames, nativePcm);
        }
        int[] resampledLeft = resampleLinear(slotFrames, decodedSampleRate, this.outputSampleRate);
        return new DecodedResourceAudio(resampledLeft, rightFrames == slotFrames ? resampledLeft :
                resampleLinear(rightFrames, decodedSampleRate, this.outputSampleRate), nativePcm);
    }

    private static int[] decodePcm16Mono(byte[] payload) {
        if (payload.length < 2) {
            return new int[0];
        }
        int sampleCount = payload.length / 2;
        int[] frames = new int[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            int offset = i * 2;
            frames[i] = (short) ((payload[offset] & 0xFF) | (payload[offset + 1] << 8));
        }
        return frames;
    }

    private void parseThrdAudioRoutes(byte[] thrd) {
        if (thrd == null || thrd.length <= 1) {
            return;
        }
        boolean[] seenAudio = new boolean[this.initialAudioRouteRaw.length];
        boolean[] seenSynth = new boolean[this.initialSynthRouteRaw.length];
        for (int offset = 1; offset + 1 < thrd.length; offset += 2) {
            int logicalChannel = thrd[offset] & 0x0F;
            if (logicalChannel < 0 || logicalChannel >= this.initialAudioRouteRaw.length) {
                continue;
            }
            int packed = thrd[offset + 1] & 0xFF;
            boolean audioTarget = (packed & 0x20) != 0;
            if (audioTarget) {
                if (seenAudio[logicalChannel]) {
                    continue;
                }
                this.initialAudioRouteRaw[logicalChannel] = packed & 0x1F;
                seenAudio[logicalChannel] = true;
            } else {
                if (seenSynth[logicalChannel]) {
                    continue;
                }
                this.initialSynthRouteRaw[logicalChannel] = packed & 0x1F;
                seenSynth[logicalChannel] = true;
            }
        }
    }

    private StereoGain computeStereoGain(ChannelState state, int startLevel) {
        int combinedQ16 = combineNativeLevelQ16(startLevel, state.level, DEFAULT_BANK_LEVEL, DEFAULT_GLOBAL_LEVEL);
        int panIndex = clamp(0, 127, DEFAULT_BANK_PAN + state.pan - DEFAULT_PAN);
        int leftQ16 = combineQ16(combinedQ16,
            FueTrekResourceAudioTables.panLeftQ16(panIndex));
        int rightQ16 = combineQ16(combinedQ16,
            FueTrekResourceAudioTables.panRightQ16(panIndex));
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
        return FueTrekResourceAudioTables.levelQ16(value);
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

    private static int nativeRouteSlotId(int resourceIndex) {
        return resourceIndex + 2;
    }

    private static final class ChannelState {
        int level;
        int pan;
        int audioRouteRaw;
        int synthRouteRaw;
        final ArrayList<AuxMotionPoint> auxMotion = new ArrayList<>();
    }

    private record StereoGain(int leftQ16, int rightQ16) {
    }

    private record DecodedResourceAudio(int[] frames, int[] rightFrames, boolean nativePcm) {
    }

    private static final class ActiveClip {
        final int logicalChannel;
        final int resourceIndex;
        final int startLevel;
        final long startFrame;
        final DecodedResourceAudio audio;
        int audioRouteRaw;
        int synthRouteRaw;
        final int nativeRouteSlotId;
        final ArrayList<AuxMotionPoint> liveMotion;
        final int leftGainQ16;
        final int rightGainQ16;
        int nextMotionIndex;
        final FueTrekSpatialMotion motion = new FueTrekSpatialMotion();
        long nextMotionBlockFrame = Long.MAX_VALUE;
        long stopFrame = -1L;

        ActiveClip(
                int logicalChannel,
                int resourceIndex,
                int startLevel,
                long startFrame,
                DecodedResourceAudio audio,
                int audioRouteRaw,
                int synthRouteRaw,
                int nativeRouteSlotId,
                int initialMotionCapacity,
                int leftGainQ16,
                int rightGainQ16) {
            this.logicalChannel = logicalChannel;
            this.resourceIndex = resourceIndex;
            this.startLevel = startLevel;
            this.startFrame = startFrame;
            this.audio = audio;
            this.audioRouteRaw = audioRouteRaw;
            this.synthRouteRaw = synthRouteRaw;
            this.nativeRouteSlotId = nativeRouteSlotId;
            this.liveMotion = new ArrayList<>(initialMotionCapacity);
            this.leftGainQ16 = leftGainQ16;
            this.rightGainQ16 = rightGainQ16;
        }

        long endFrame() {
            return this.startFrame + this.audio.frames.length;
        }
    }

    static final class AuxMotionPoint {
        final long framePosition;
        final int resourceIndex;
        final int strength;
        final int azimuthDegrees;
        final int elevationDegrees;
        final int durationRaw;
        final short nativeXCentiUnits;
        final short nativeYCentiUnits;
        final short nativeZCentiUnits;

        AuxMotionPoint(
                long framePosition,
                int resourceIndex,
                int strength,
                int azimuthDegrees,
                int elevationDegrees,
                int durationRaw) {
            this.framePosition = framePosition;
            this.resourceIndex = resourceIndex;
            this.strength = strength;
            this.azimuthDegrees = azimuthDegrees;
            this.elevationDegrees = elevationDegrees;
            this.durationRaw = durationRaw;
            this.nativeXCentiUnits = nativeCartesianXCentiUnits(strength, azimuthDegrees, elevationDegrees);
            this.nativeYCentiUnits = nativeCartesianYCentiUnits(strength, elevationDegrees);
            this.nativeZCentiUnits = nativeCartesianZCentiUnits(strength, azimuthDegrees, elevationDegrees);
        }
    }

    private static short nativeCartesianXCentiUnits(int strength, int azimuthDegrees, int elevationDegrees) {
        double cosElevation = Math.cos(elevationDegrees * DEG_TO_RAD);
        double distance = nativeDistance(strength);
        return clampNativeCentiUnits(Math.sin(azimuthDegrees * DEG_TO_RAD) * cosElevation * distance);
    }

    private static short nativeCartesianYCentiUnits(int strength, int elevationDegrees) {
        double distance = nativeDistance(strength);
        return clampNativeCentiUnits(Math.sin(elevationDegrees * DEG_TO_RAD) * distance);
    }

    private static short nativeCartesianZCentiUnits(int strength, int azimuthDegrees, int elevationDegrees) {
        double cosElevation = Math.cos(elevationDegrees * DEG_TO_RAD);
        double distance = nativeDistance(strength);
        return clampNativeCentiUnits(Math.cos(azimuthDegrees * DEG_TO_RAD) * cosElevation * distance);
    }

    private static double nativeDistance(int strength) {
        return clamp(0, 127, strength) * 0.05;
    }

    private static short clampNativeCentiUnits(double value) {
        int centiUnits = (int) Math.round(value * 100.0);
        return (short) clamp(Short.MIN_VALUE, Short.MAX_VALUE, centiUnits);
    }

}
