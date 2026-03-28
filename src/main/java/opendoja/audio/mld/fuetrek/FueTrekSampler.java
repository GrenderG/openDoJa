package opendoja.audio.mld.fuetrek;

import opendoja.audio.mld.MLD;
import opendoja.audio.mld.Sampler;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Sample-based FueTrek renderer built from the extracted MFiSynth_ft ROM image.
 */
final class FueTrekSampler implements Sampler {
    private static final boolean DEBUG_NOTES = Boolean.getBoolean("opendoja.debugFueTrekNotes");
    private static final boolean DEBUG_CONTROL = Boolean.getBoolean("opendoja.debugFueTrekControl");
    private static final int CHANNEL_COUNT = 16;
    private static final int CUSTOM_SLOT_COUNT = 16;
    private static final int MIDI_A4 = 69;
    private static final float ROM_SAMPLE_RATE = FueTrekSamplerProvider.SAMPLE_RATE;
    private static final float OUTPUT_SCALE = 1.0f / 32768.0f;
    private static final int MIX_PROFILE_MODE = Integer.getInteger("opendoja.fuetrekMixProfile", 0);

    private final FueTrekRom rom;
    private final ChannelState[] channels = new ChannelState[CHANNEL_COUNT];
    private final Voice[] voiceSlots;
    private final ArrayList<CustomSlotVoice> customVoices = new ArrayList<>();
    private final MixState mixState = new MixState();
    private final WrapperState wrapperState = new WrapperState();
    private final SelectorCache selectorCache = new SelectorCache();
    private final int maxPolyphony;
    private final float sampleRate;
    private final float romStepScale;

    private float masterTuneSemitones = 0.0f;
    private long nextVoiceAge = 1;
    private int controlCounter = 0x78;
    private int hostGlobalVolumeByte = 0x7f;
    private int rawGlobalLaneB0 = 0x7f;
    private int rawGlobalLaneB1 = 0x40;

    FueTrekSampler(FueTrekRom rom, float sampleRate, int maxPolyphony) {
        this.rom = rom;
        this.sampleRate = sampleRate;
        this.maxPolyphony = maxPolyphony;
        this.voiceSlots = new Voice[maxPolyphony];
        this.romStepScale = ROM_SAMPLE_RATE / sampleRate;
        for (int i = 0; i < channels.length; i++) {
            channels[i] = new ChannelState(i);
        }
    }

    @Override
    public void bankChange(int channel, int bank) {
        ChannelState state = channel(channel);
        if (state == null) {
            return;
        }
        state.bank = bank & 0x3f;
    }

    @Override
    public void drumEnable(int channel, boolean enable) {
        ChannelState state = channel(channel);
        if (state == null) {
            return;
        }
        state.applyFamilyMode(enable ? 1 : 0);
    }

    @Override
    public boolean isFinished() {
        for (Voice voice : voiceSlots) {
            if (voice != null) {
                return false;
            }
        }
        return customVoices.isEmpty();
    }

    @Override
    public void keyOff(int channel, int key) {
        for (Voice voice : voiceSlots) {
            if (voice != null && voice.channelIndex == channel && voice.key == key) {
                voice.release();
            }
        }
    }

    @Override
    public void keyOn(int channel, int key, float velocity) {
        if (Float.isNaN(velocity) || Float.isInfinite(velocity) || velocity < 0.0f) {
            throw new IllegalArgumentException("Invalid velocity.");
        }
        ChannelState state = channel(channel);
        if (state == null) {
            return;
        }
        // `Sampler.keyOn()` receives the generic player key domain: semitones
        // relative to A4. The lib002/raw-parser `0x2d/0x23` bases apply one
        // stage earlier while decoding the packed MLD note bytes, so the live
        // sampler input still needs the MIDI A4 base here.
        int noteByte = wrapPlayableNoteByte(MIDI_A4 + key - (state.familyMode == 1 ? 10 : 0));
        SelectorState selector = selectorCache.apply(state.selectorState(noteByte));
        ResolvedZone resolved = resolveZone(state, selector);
        if (resolved == null || resolved.zone.sampleA == null) {
            return;
        }
        cullNativeEvenGroupConflicts(channel, resolved);
        if (!state.drum) {
            keyOff(channel, key);
        }
        Voice voice = new Voice(
                rom,
                mixState,
                state,
                channel,
                key,
                FueTrekMixTables.noteVelocityByte(velocity),
                resolved,
                state.snapshotTemplate(resolved),
                romStepScale,
                MIX_PROFILE_MODE,
                nextVoiceAge++);
        if (DEBUG_NOTES) {
            System.err.printf(
                    "[FueTrekNote] ch=%d key=%d noteByte=%d group=0x%02x sub=%d sel=%d rootA=%d rootB=%d tuneA=%d tuneB=%d baseNote=%d staticQ16=%d oscBMode=%d%n",
                    channel,
                    key,
                    resolved.noteByte,
                    resolved.groupId & 0xff,
                    resolved.subGroupId,
                    resolved.selectorIndex,
                    voice.rootA,
                    voice.rootB,
                    voice.tuneA,
                    voice.tuneB,
                    voice.baseNote,
                    voice.staticPitchQ16,
                    voice.oscBMode);
        }
        int slotIndex = allocateVoiceSlot();
        if (slotIndex < 0) {
            return;
        }
        voiceSlots[slotIndex] = voice;
    }

    @Override
    public void masterTune(float semitones) {
        if (Float.isNaN(semitones) || Float.isInfinite(semitones)) {
            throw new IllegalArgumentException("Invalid semitones.");
        }
        masterTuneSemitones = semitones;
    }

    @Override
    public void masterVolume(float volume) {
        if (Float.isNaN(volume) || Float.isInfinite(volume) || volume < 0.0f) {
            throw new IllegalArgumentException("Invalid volume.");
        }
        hostGlobalVolumeByte = FueTrekMixTables.amplitudeToGainByte(volume);
        refreshGlobalVolumeByte();
    }

    @Override
    public void panpot(int channel, float panpot) {
        if (Float.isNaN(panpot) || Float.isInfinite(panpot) || panpot < -1.0f || panpot > 1.0f) {
            throw new IllegalArgumentException("Invalid panpot.");
        }
        ChannelState state = channel(channel);
        if (state == null) {
            return;
        }
        state.channelPanByte = floatPanToSignedByte(panpot);
    }

    @Override
    public void pitchBend(int channel, float semitones) {
        if (Float.isNaN(semitones) || Float.isInfinite(semitones)) {
            throw new IllegalArgumentException("Invalid semitones.");
        }
        ChannelState state = channel(channel);
        if (state == null) {
            return;
        }
        state.setRawPitchWord(rawPitchWordFromSemitones(semitones, state.bendRangeByte));
        commitChannelPitch(state);
    }

    @Override
    public void pitchBendRange(int channel, float range) {
        if (Float.isNaN(range) || Float.isInfinite(range) || range < 0.0f) {
            throw new IllegalArgumentException("Invalid range.");
        }
        ChannelState state = channel(channel);
        if (state == null) {
            return;
        }
        state.setBendRangeByte(Math.round(range));
        updateActiveVoicePitchRange(state);
    }

    @Override
    public void programChange(int channel, int program) {
        ChannelState state = channel(channel);
        if (state == null) {
            return;
        }
        state.program = program & 0x3f;
    }

    @Override
    public void render(float[] samples, int offset, int frames) {
        render(samples, offset, frames, 1.0f, 1.0f, true, true);
    }

    @Override
    public void render(float[] samples, int offset, int frames, float amplitude) {
        render(samples, offset, frames, amplitude, amplitude, true, true);
    }

    @Override
    public void render(float[] samples, int offset, int frames, float left, float right) {
        render(samples, offset, frames, left, right, true, true);
    }

    @Override
    public void render(
            float[] samples,
            int offset,
            int frames,
            float left,
            float right,
            boolean erase,
            boolean clamp) {
        if (frames < 0) {
            throw new IllegalArgumentException("Negative frame count.");
        }
        if (erase) {
            int end = offset + frames * 2;
            for (int i = offset; i < end; i++) {
                samples[i] = 0.0f;
            }
        }
        for (int i = 0; i < voiceSlots.length; i++) {
            Voice voice = voiceSlots[i];
            if (voice == null) {
                continue;
            }
            boolean active = true;
            for (int frame = 0; frame < frames; frame++) {
                if (active) {
                    active = voice.render(masterTuneSemitones, controlCounter);
                    if (active) {
                        int sampleIndex = offset + frame * 2;
                        samples[sampleIndex] += voice.outLeft * left;
                        samples[sampleIndex + 1] += voice.outRight * right;
                    }
                }
                controlCounter = (controlCounter + 1) & 0x7F;
            }
            if (!active) {
                voiceSlots[i] = null;
            }
        }
        for (int i = 0; i < customVoices.size(); i++) {
            CustomSlotVoice voice = customVoices.get(i);
            boolean active = true;
            for (int frame = 0; frame < frames; frame++) {
                if (active) {
                    active = voice.render();
                    if (active) {
                        int sampleIndex = offset + frame * 2;
                        samples[sampleIndex] += voice.outLeft * left;
                        samples[sampleIndex + 1] += voice.outRight * right;
                    }
                }
            }
            if (!active) {
                customVoices.remove(i--);
            }
        }
        if (clamp) {
            int end = offset + frames * 2;
            for (int i = offset; i < end; i++) {
                samples[i] = clampAudio(samples[i]);
            }
        }
    }

    @Override
    public void reset() {
        clearAllVoices();
        masterTuneSemitones = 0.0f;
        controlCounter = 0x78;
        hostGlobalVolumeByte = 0x7f;
        rawGlobalLaneB0 = 0x7f;
        rawGlobalLaneB1 = 0x40;
        mixState.reset();
        refreshGlobalVolumeByte();
        wrapperState.reset();
        selectorCache.reset();
        for (ChannelState state : channels) {
            state.reset();
        }
    }

    @Override
    public float sampleRate() {
        return sampleRate;
    }

    @Override
    public void stopAll() {
        clearAllVoices();
    }

    @Override
    public void sysEx(byte[] message) {
        if (message == null || message.length < 2) {
            return;
        }
        if ((message[0] & 0xff) == 0x71) {
            debugControl("wrapper", -1, message[1] & 0xff, message, 2);
            applyWrapperMessage(message);
            return;
        }
        int channelIndex = message[0] & 0xff;
        if (channelIndex < 0 || channelIndex >= channels.length) {
            return;
        }
        ChannelState state = channels[channelIndex];
        int controlId = message[1] & 0xff;
        byte[] payload = new byte[message.length - 2];
        System.arraycopy(message, 2, payload, 0, payload.length);
        debugControl("sysex", channelIndex, controlId, message, 2);
        switch (controlId) {
            case 0x02:
                state.applyLargeControl(rom, channels, channelIndex, payload);
                break;
            case 0x00:
                state.clearControlTemplate();
                break;
            case 0x01:
                state.reloadSelectedTemplate();
                break;
            case 0x10:
                state.applySelectControl(rom, payload);
                break;
            case 0x11:
                state.applyPitchPairControl(rom, channels, channelIndex, payload);
                break;
            case 0x20:
                state.applyLinkControl(channels, payload);
                break;
            case 0x21:
                state.applyOscBalanceControl(payload);
                break;
            case 0x30:
                state.applyTemplateByte14Control(payload);
                break;
            case 0x40:
                state.applyTemplateEnvAControl(payload);
                break;
            case 0x50:
                state.applyTemplateEnvBControl(payload);
                break;
            case 0x51:
                state.applyTemplateShapeMode(payload);
                break;
            case 0x52:
                state.applyTemplateShapeW4(payload);
                break;
            case 0x53:
                state.applyTemplateShapeW2(payload);
                break;
            case 0x54:
                state.applyTemplateEnvBC(payload);
                break;
            case 0x55:
                state.applyTemplatePitchTrack(payload);
                break;
            case 0x60:
                state.applyTemplateModMode(payload);
                break;
            case 0x61:
                state.applyTemplateModA(payload);
                break;
            case 0x62:
                state.applyTemplateMod8(payload);
                break;
            case 0x63:
                state.applyTemplateModScale2(payload);
                break;
            case 0x64:
                state.applyTemplateByte38(payload);
                break;
            default:
                break;
        }
    }

    private static void debugControl(String source, int channelIndex, int controlId, byte[] message, int offset) {
        if (!DEBUG_CONTROL) {
            return;
        }
        StringBuilder sb = new StringBuilder(128);
        sb.append("[FueTrekCtrl] ")
                .append(source)
                .append(" ch=")
                .append(channelIndex)
                .append(" id=0x")
                .append(Integer.toHexString(controlId));
        for (int i = offset; i < message.length; i++) {
            sb.append(i == offset ? " payload=" : " ");
            sb.append(String.format("%02x", message[i] & 0xff));
        }
        System.err.println(sb);
    }

    @Override
    public boolean interceptsExtBEvent(int eventId) {
        switch (eventId & 0xff) {
            case MLD.EVENT_MASTER_VOLUME:
            case 0xb1:
            case MLD.EVENT_X_DRUM_ENABLE:
            case MLD.EVENT_PROGRAM_CHANGE:
            case MLD.EVENT_BANK_CHANGE:
            case MLD.EVENT_VOLUME:
            case MLD.EVENT_PANPOT:
            case MLD.EVENT_PITCHBEND:
            case 0xe6:
            case MLD.EVENT_PITCHBEND_RANGE:
            case MLD.EVENT_WAVE_CHANNEL_VOLUME:
            case MLD.EVENT_WAVE_CHANNEL_PANPOT:
            case 0xea:
                return true;
            default:
                return false;
        }
    }

    @Override
    public void handleExtBEvent(int eventId, int channel, int rawParam) {
        if ((eventId & 0xff) == MLD.EVENT_X_DRUM_ENABLE) {
            int targetChannel = (rawParam >> 3) & 0xf;
            ChannelState target = channel(targetChannel);
            if (target == null) {
                return;
            }
            // lib002 player `0x1000f48a` / synth sink `0x10002d14` prove `0xba`
            // targets the encoded channel `(raw >> 3) & 0xf`, keeps the full
            // 3-bit family mode, and forwards the raw upper nibble as the
            // per-channel profile index `+ 3`.
            target.applyFamilyMode(rawParam & 0x7);
            target.nativePanModeIndex = clampRange((rawParam >> 4) + 3, 0, 0xff);
            return;
        }
        ChannelState state = channel(channel);
        if (state == null) {
            return;
        }
        int value = rawParam & 0x3f;
        switch (eventId & 0xff) {
            case MLD.EVENT_MASTER_VOLUME:
                // The old split player multiplies this live lane with a host-owned
                // `0x7f` byte and forwards the resulting descriptor `+0x05` volume.
                // lib002 only exposes the cache write, but the shared defaults and
                // sink ownership still line up with that contract.
                rawGlobalLaneB0 = rawParam & 0x7f;
                refreshGlobalVolumeByte();
                return;
            case 0xb1:
                // Same visible contract as 0xb0, but cached into player +0xf55.
                rawGlobalLaneB1 = rawParam & 0x7f;
                return;
            case MLD.EVENT_PROGRAM_CHANGE:
                state.program = value;
                return;
            case MLD.EVENT_BANK_CHANGE:
                state.bank = value;
                return;
            case MLD.EVENT_VOLUME:
                state.rawLevel6 = value;
                state.updateRawLevelChannelVolume();
                return;
            case MLD.EVENT_PANPOT:
                // The old split player+synth pair proves `0xe3` is a real signed
                // pan lane: the player expands the raw 6-bit value by `<< 1`, and
                // the synth sink stores `(value - 0x40)` into the per-channel pan
                // byte. `lib002` keeps the same caller-side lane setup even though
                // the visible wrapper call falls into a local stub in this build.
                state.setRawPan6(value);
                return;
            case MLD.EVENT_PITCHBEND:
                state.rawPitchHigh6 = value;
                state.updateRawPitchBend();
                commitChannelPitch(state);
                return;
            case 0xe6:
                state.rawLevel6 = clampRange(value + state.rawLevel6 - 0x20, 0, 0x3f);
                state.updateRawLevelChannelVolume();
                return;
            case MLD.EVENT_PITCHBEND_RANGE:
                // Native split/lib002 synths map `0xe7` to the channel `+0x19`
                // seed lane, not to pitch-bend range. The live sink clamps the
                // sum of `+0x18/+0x19` and refreshes voice vtbl `+0x34`.
                state.modPair19Byte = clampRange(value << 1, 0, 0x7f);
                updateActiveVoiceModSeed(state);
                return;
            case MLD.EVENT_WAVE_CHANNEL_VOLUME:
                // lib002 player `0x1003d1ac` writes the low 6-bit pitch cache
                // at `+0xd5`, rebuilds `(((hi << 5) + lo) << 3) - 0x100`, and
                // commits that word immediately through the synth pitch setter.
                state.rawPitchLow6 = value;
                state.updateRawPitchBend();
                commitChannelPitch(state);
                return;
            case MLD.EVENT_WAVE_CHANNEL_PANPOT:
                // lib002 player `0x1003d200` only updates the cached low 6-bit
                // lane at `+0xd5`; it does not commit the paired pitch word.
                state.rawPitchLow6 = value;
                return;
            case 0xea:
                state.modPair18Byte = clampRange(value << 1, 0, 0x7f);
                updateActiveVoiceModSeed(state);
                return;
            default:
                return;
        }
    }

    @Override
    public SequenceControlMode sequenceControlMode() {
        return SequenceControlMode.FUETREK;
    }

    @Override
    public boolean suppressActiveKeyRetrigger() {
        return true;
    }

    @Override
    public void volume(int channel, float volume) {
        if (Float.isNaN(volume) || Float.isInfinite(volume) || volume < 0.0f) {
            throw new IllegalArgumentException("Invalid volume.");
        }
        ChannelState state = channel(channel);
        if (state == null) {
            return;
        }
        // OpenDoJa exposes the channel-volume event but not a separate expression
        // controller, so only the native channel +0x05 byte is driven here.
        int gainByte = FueTrekMixTables.amplitudeToGainByte(volume);
        state.channelVolumeByte = gainByte;
    }

    private ChannelState channel(int index) {
        if (index < 0 || index >= channels.length) {
            return null;
        }
        return channels[index];
    }

    private void refreshGlobalVolumeByte() {
        mixState.globalVolumeByte = combineVolumeByteLanes(hostGlobalVolumeByte, rawGlobalLaneB0);
    }

    private static int combineVolumeByteLanes(int left, int right) {
        int lhs = clampRange(left, 0, 0x7f);
        int rhs = clampRange(right, 0, 0x7f);
        return (lhs * rhs) / 0x7f;
    }

    private void commitChannelPitch(ChannelState state) {
        for (Voice voice : voiceSlots) {
            if (voice != null && voice.channelIndex == state.channelIndex) {
                voice.applyPitchCommit(state.rawPitchWord);
            }
        }
    }

    private void updateActiveVoicePitchRange(ChannelState state) {
        for (Voice voice : voiceSlots) {
            if (voice != null && voice.channelIndex == state.channelIndex) {
                voice.applyPitchRange(state.bendRangeByte, state.rawPitchWord);
            }
        }
    }

    private void updateActiveVoiceModSeed(ChannelState state) {
        int modSeedByte = state.modSeedByte();
        for (Voice voice : voiceSlots) {
            if (voice != null && voice.channelIndex == state.channelIndex) {
                voice.applyModSeedByte(modSeedByte);
            }
        }
    }

    private static int rawPitchWordFromSemitones(float semitones, int rangeByte) {
        if (rangeByte <= 0) {
            return 0x2000;
        }
        long centered = Math.round((double) semitones * 65536.0 / ((long) rangeByte << 3));
        return clampRange((int) centered + 0x2000, 0, 0x3fff);
    }

    private ResolvedZone resolveZone(ChannelState state, SelectorState selector) {
        if (selector == null) {
            return null;
        }
        if (state.selectedZoneOverride != null) {
            return state.selectedZoneOverride.withNoteByte(selector.noteByte);
        }
        int groupId = selector.groupId;
        if (groupId < 0) {
            return null;
        }
        FueTrekRom.Group group = rom.group(groupId);
        if (group == null) {
            groupId = (groupId & 1) != 0 ? 0x79 : 0x78;
            group = rom.group(groupId);
        }
        if (group == null) {
            return null;
        }

        int noteByte = selector.noteByte;
        int index = ((group.id & 1) != 0) ? selector.selectorIndex : noteByte;
        FueTrekRom.ObjectHeader header = group.entry(index);
        if (header == null || noteByte < header.lowKey) {
            return null;
        }
        for (FueTrekRom.Zone zone : header.zones) {
            if (zone == null) {
                continue;
            }
            if (noteByte <= zone.keyHigh) {
                return new ResolvedZone(zone, group.id, selector.subGroupId, index, noteByte);
            }
        }
        return null;
    }

    private int allocateVoiceSlot() {
        for (int i = 0; i < voiceSlots.length; i++) {
            if (voiceSlots[i] == null) {
                return i;
            }
        }
        int oldestIndex = -1;
        long oldestAge = Long.MAX_VALUE;
        for (int i = 0; i < voiceSlots.length; i++) {
            Voice voice = voiceSlots[i];
            if (voice != null && voice.age < oldestAge) {
                oldestAge = voice.age;
                oldestIndex = i;
            }
        }
        return oldestIndex;
    }

    private void cullNativeEvenGroupConflicts(int channel, ResolvedZone resolved) {
        if ((resolved.groupId & 1) != 0) {
            return;
        }
        for (int i = 0; i < voiceSlots.length; i++) {
            Voice voice = voiceSlots[i];
            if (voice == null || voice.channelIndex != channel || voice.groupId != resolved.groupId) {
                continue;
            }
            // Native even-group note-on walks the live voice list and forces an
            // exact same-note conflict through the hard-stop vtable slot before
            // allocating the new voice (`0x10004140 -> 0x10004276..0x1000428a`).
            if (voice.midiKey == resolved.noteByte) {
                voiceSlots[i] = null;
            }
        }
    }

    private void clearAllVoices() {
        Arrays.fill(voiceSlots, null);
        customVoices.clear();
    }

    private void applyWrapperMessage(byte[] message) {
        int command = message[1] & 0xff;
        switch (command) {
            case 0x81:
                wrapperState.applyLane0(message, 2);
                break;
            case 0x82:
                wrapperState.applyLane1(message, 2);
                break;
            case 0x83:
            case 0x84:
                CustomSlot slot = wrapperState.installCustomSlot(message, 2, command);
                if (slot != null && slot.autoplay) {
                    installOrReplaceCustomVoice(slot);
                }
                break;
            case 0x8f:
                wrapperState.applyTlv(message, 2);
                break;
            default:
                break;
        }
    }

    private static float clampAudio(float sample) {
        if (sample < -1.0f) {
            return -1.0f;
        }
        if (sample > 1.0f) {
            return 1.0f;
        }
        return sample;
    }

    private static int mulRoundShift(int a, int b, int shift) {
        int product = a * b;
        return roundNearestShift(product, shift);
    }

    private static int mulSignRoundShift(int a, int b, int shift) {
        int product = a * b;
        return (product >> shift) + (product < 0 ? 1 : 0);
    }

    private static int roundNearestShift(int value, int shift) {
        int roundBit = (value >> (shift - 1)) & 1;
        return (value >> shift) + roundBit;
    }

    private static int scaleUnsignedWord(int word, int scale) {
        return ((word & 0xffff) * scale) >> 15;
    }

    private static int wrapPlayableNoteByte(int note) {
        while (note < 0x15) {
            note += 12;
        }
        while (note > 0x78) {
            note -= 12;
        }
        return note;
    }

    private static int floatPanToIndex(float pan) {
        return Math.max(0, Math.min(0x7f, Math.round((pan + 1.0f) * 63.5f)));
    }

    private static int floatPanToSignedByte(float pan) {
        return floatPanToIndex(pan) - 0x40;
    }

    private static int clamp16(int value) {
        if (value < Short.MIN_VALUE) {
            return Short.MIN_VALUE;
        }
        if (value > Short.MAX_VALUE) {
            return Short.MAX_VALUE;
        }
        return value;
    }

    private static int clampRange(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private static int decodeSigned14(byte[] payload, int offset) {
        int value = ((payload[offset] & 0x3f) << 8) | (payload[offset + 1] & 0xff);
        return value - 0x2000;
    }

    private void installOrReplaceCustomVoice(CustomSlot slot) {
        if (slot.slotId < 0 || slot.slotId >= CUSTOM_SLOT_COUNT) {
            return;
        }
        for (int i = 0; i < customVoices.size(); i++) {
            if (customVoices.get(i).slot.slotId == slot.slotId) {
                customVoices.remove(i);
                break;
            }
        }
        // Native wrapper installs cache per-slot owners/state separately from the
        // synth voice pool (`+0x260/+0x35c/+0x458`), so do not steal melodic voices.
        customVoices.add(new CustomSlotVoice(slot, wrapperState, mixState, sampleRate, nextVoiceAge++));
    }

    private static final class SelectorState {
        final int groupId;
        final int subGroupId;
        final int selectorIndex;
        final int noteByte;

        SelectorState(int groupId, int subGroupId, int selectorIndex, int noteByte) {
            this.groupId = groupId;
            this.subGroupId = subGroupId;
            this.selectorIndex = selectorIndex;
            this.noteByte = noteByte;
        }
    }

    private static final class ResolvedZone {
        final FueTrekRom.Zone zone;
        final int groupId;
        final int subGroupId;
        final int selectorIndex;
        final int noteByte;

        ResolvedZone(FueTrekRom.Zone zone, int groupId, int subGroupId, int selectorIndex, int noteByte) {
            this.zone = zone;
            this.groupId = groupId;
            this.subGroupId = subGroupId;
            this.selectorIndex = selectorIndex;
            this.noteByte = noteByte;
        }

        ResolvedZone withNoteByte(int nextNoteByte) {
            if (noteByte == nextNoteByte) {
                return this;
            }
            return new ResolvedZone(zone, groupId, subGroupId, selectorIndex, nextNoteByte);
        }
    }

    private static final class SelectorCache {
        private static final int ENTRY_COUNT = 16;

        final SelectorCacheEntry[] entries = new SelectorCacheEntry[ENTRY_COUNT];

        SelectorCache() {
            reset();
        }

        void reset() {
            for (int i = 0; i < entries.length; i++) {
                entries[i] = new SelectorCacheEntry();
            }
        }

        SelectorState apply(SelectorState selector) {
            if (selector == null) {
                return null;
            }
            int groupId = selector.groupId;
            int subGroupId = selector.subGroupId;
            int selectorIndex = selector.selectorIndex;
            int noteByte = selector.noteByte;
            for (SelectorCacheEntry entry : entries) {
                if (!entry.matches(groupId, subGroupId, selectorIndex, noteByte)) {
                    continue;
                }
                groupId = entry.replace0;
                subGroupId = entry.replace1;
                selectorIndex = entry.replace2;
                if (entry.match3 != 0xff) {
                    noteByte = entry.replace3;
                }
                break;
            }
            return new SelectorState(groupId, subGroupId, selectorIndex, noteByte);
        }
    }

    private static final class SelectorCacheEntry {
        int match0 = 0xff;
        int match1 = 0xff;
        int match2 = 0xff;
        int match3 = 0xff;
        int replace0 = 0xff;
        int replace1 = 0xff;
        int replace2 = 0xff;
        int replace3 = 0xff;

        boolean matches(int groupId, int subGroupId, int selectorIndex, int noteByte) {
            if (match0 != (groupId & 0xff) || match1 != (subGroupId & 0xff) || match2 != (selectorIndex & 0xff)) {
                return false;
            }
            return match3 == 0xff || match3 == (noteByte & 0xff);
        }
    }

    private static final class MixState {
        int masterPanByte = 0;
        int globalVolumeByte = 0x7f;
        int global338Byte = 0x7f;
        int global339Byte = 0x40;
        final byte[] panModes = new byte[9];

        MixState() {
            reset();
        }

        void reset() {
            masterPanByte = 0;
            globalVolumeByte = 0x7f;
            global338Byte = 0x7f;
            // Owner default at 0x1000318c seeds descriptor +0x339 from 0x4a22 = 0x40.
            global339Byte = 0x40;
            for (int i = 0; i < panModes.length; i++) {
                panModes[i] = 2;
            }
        }
    }

    private static final class WrapperState {
        final int[] lane0 = new int[CUSTOM_SLOT_COUNT];
        final int[] lane1 = new int[CUSTOM_SLOT_COUNT];
        final int[] slotStates = new int[CUSTOM_SLOT_COUNT];
        final CustomSlot[] slots = new CustomSlot[CUSTOM_SLOT_COUNT];
        int global0;
        int global1;
        boolean hostFlag20;

        WrapperState() {
            reset();
        }

        void reset() {
            Arrays.fill(lane0, 0x7f);
            Arrays.fill(lane1, 0x40);
            Arrays.fill(slotStates, 0);
            Arrays.fill(slots, null);
            global0 = 0x7f;
            global1 = 0x40;
            hostFlag20 = false;
        }

        void applyLane0(byte[] message, int offset) {
            applyCompactLane(message, offset, lane0);
        }

        void applyLane1(byte[] message, int offset) {
            applyCompactLane(message, offset, lane1);
        }

        int lane0Byte(int slotId) {
            if (slotId < 0 || slotId >= lane0.length) {
                return 0x7f;
            }
            return lane0[slotId];
        }

        int lane1Byte(int slotId) {
            if (slotId < 0 || slotId >= lane1.length) {
                return 0x40;
            }
            return lane1[slotId];
        }

        void applyTlv(byte[] message, int offset) {
            int cursor = offset;
            while (cursor + 1 < message.length) {
                int type = message[cursor] & 0xff;
                int length = message[cursor + 1] & 0xff;
                cursor += 2;
                if (cursor + length > message.length) {
                    return;
                }
                if ((type == 0x83 || type == 0x8b) && length > 0 && message[cursor] != 0) {
                    hostFlag20 = true;
                }
                cursor += length;
            }
        }

        CustomSlot installCustomSlot(byte[] message, int offset, int command) {
            boolean hasMetadata = (command & 0xff) == 0x84;
            if (offset + (hasMetadata ? 7 : 3) > message.length) {
                return null;
            }
            int slotId = message[offset] & 0x3f;
            if (slotId < 0 || slotId >= slots.length) {
                return null;
            }
            int modeA = wrapperMode(message[offset] >>> 6);
            int installMode = wrapperMode(message[offset + 1] >>> 6);
            int formatId = message[offset + 1] & 0x3f;
            if (installMode == 2) {
                // `0x10026e2a..0x10026e36` forces the native mode-2 path to
                // use low6 format id `4` before `0x10027360` translates it.
                formatId = 4;
            }
            CustomFormat format = CustomFormat.decode(formatId);
            if (modeA < 0 || installMode < 0 || format == null) {
                return null;
            }
            int flags = message[offset + 2] & 0xff;
            if (installMode == 2) {
                flags = 0;
                installMode = 3;
            }
            if ((flags & 0xfe) != 0) {
                return null;
            }
            int metadata = -1;
            int payloadOffset = offset + 3;
            if (hasMetadata) {
                metadata = ((message[offset + 3] & 0xff) << 24)
                        | ((message[offset + 4] & 0xff) << 16)
                        | ((message[offset + 5] & 0xff) << 8)
                        | (message[offset + 6] & 0xff);
                payloadOffset = offset + 7;
            }
            byte[] payload = Arrays.copyOfRange(message, payloadOffset, message.length);
            int previousState = slotStates[slotId];
            CustomSlot slot = new CustomSlot(
                    slotId,
                    modeA,
                    installMode,
                    format,
                    flags,
                    metadata,
                    payload,
                    hostFlag20);
            slots[slotId] = slot;
            slotStates[slotId] = flags;
            if (!hostFlag20 || payload.length == 0) {
                return null;
            }
            if (previousState == 1) {
                // Existing slot-state `1` takes the native `0x10021dc0` update
                // path instead of the fresh install path, but in both cases the
                // active slot source for this id is replaced in-place.
                return slot;
            }
            if (installMode == 1) {
                return slot;
            }
            return null;
        }

        private static void applyCompactLane(byte[] message, int offset, int[] target) {
            if (offset >= message.length) {
                return;
            }
            int packed = message[offset] & 0xff;
            int index = packed >>> 6;
            if (index < 0 || index >= target.length) {
                return;
            }
            int value = (packed & 0x3f) << 1;
            target[index] = value;
        }

        private static int wrapperMode(int value) {
            return switch (value & 0xff) {
                case 0, 1, 2 -> value & 0xff;
                default -> -1;
            };
        }
    }

    private static final class CustomFormat {
        final int formatId;
        final int frameKind;
        final int rateKHz;

        CustomFormat(int formatId, int frameKind, int rateKHz) {
            this.formatId = formatId;
            this.frameKind = frameKind;
            this.rateKHz = rateKHz;
        }

        int nativeScaledMetadata(int metadata) {
            if (metadata < 0) {
                return metadata;
            }
            int shift = 1;
            if (frameKind == 4) {
                shift++;
            }
            if (rateKHz != 32) {
                shift += 2;
            }
            if (rateKHz == 16) {
                shift++;
            }
            return (metadata * 1000) >> shift;
        }

        static CustomFormat decode(int id) {
            return switch (id) {
                case 4 -> new CustomFormat(4, 2, 8);
                case 5 -> new CustomFormat(5, 4, 8);
                case 12 -> new CustomFormat(12, 2, 16);
                case 13 -> new CustomFormat(13, 4, 16);
                case 20 -> new CustomFormat(20, 2, 32);
                case 21 -> new CustomFormat(21, 4, 32);
                default -> null;
            };
        }
    }

    private static final class CustomSlot {
        final int slotId;
        final int modeA;
        final int installMode;
        final CustomFormat format;
        final int flags;
        final int metadata;
        final int nativeScaledMetadata;
        final byte[] payload;
        final boolean autoplay;

        CustomSlot(
                int slotId,
                int modeA,
                int installMode,
                CustomFormat format,
                int flags,
                int metadata,
                byte[] payload,
                boolean autoplay) {
            this.slotId = slotId;
            this.modeA = modeA;
            this.installMode = installMode;
            this.format = format;
            this.flags = flags;
            this.metadata = metadata;
            this.nativeScaledMetadata = format.nativeScaledMetadata(metadata);
            this.payload = payload;
            this.autoplay = autoplay;
        }
    }

    private static final class CustomSlotVoice {
        final CustomSlot slot;
        final WrapperState wrapperState;
        final MixState mixState;
        final float step;
        final long age;
        double position = 0.0;
        float outLeft;
        float outRight;

        CustomSlotVoice(
                CustomSlot slot,
                WrapperState wrapperState,
                MixState mixState,
                float outputSampleRate,
                long age) {
            this.slot = slot;
            this.wrapperState = wrapperState;
            this.mixState = mixState;
            // The native path definitely treats the wrapper payload as a custom
            // stream descriptor/input, not confirmed PCM8. Java still renders it
            // as a best-effort byte stream until the downstream codec path is
            // ported instruction-for-instruction.
            this.step = (slot.format.rateKHz * 1000.0f) / outputSampleRate;
            this.age = age;
        }

        boolean render() {
            if (slot.payload.length == 0 || position >= slot.payload.length) {
                outLeft = 0.0f;
                outRight = 0.0f;
                return false;
            }
            int index = (int) position;
            float frac = (float) (position - index);
            int s0 = slot.payload[index];
            int s1 = (index + 1 < slot.payload.length) ? slot.payload[index + 1] : 0;
            float mono = (s0 + (s1 - s0) * frac) / 128.0f;

            int gain = mulWord(
                    FueTrekMixTables.gainWord(wrapperState.lane0Byte(slot.slotId)),
                    FueTrekMixTables.gainWord(mixState.globalVolumeByte));
            gain = mulWord(gain, FueTrekMixTables.gainWord(mixState.global338Byte));
            gain = mulWord(gain, FueTrekMixTables.gainWord(mixState.global339Byte));

            int panByte = clampRange(wrapperState.lane1Byte(slot.slotId) - 0x40, -0x40, 0x3f);
            int leftWord = mulWord(gain, FueTrekMixTables.stereoWord(-mixState.masterPanByte));
            int rightWord = mulWord(gain, FueTrekMixTables.stereoWord(mixState.masterPanByte));
            leftWord = mulWord(leftWord, FueTrekMixTables.stereoWord(-panByte));
            rightWord = mulWord(rightWord, FueTrekMixTables.stereoWord(panByte));

            outLeft = mono * (leftWord / 32767.0f);
            outRight = mono * (rightWord / 32767.0f);

            position += step;
            if (position >= slot.payload.length) {
                outLeft = 0.0f;
                outRight = 0.0f;
                return false;
            }
            return true;
        }

        private static int mulWord(int lhs, int rhs) {
            return (lhs * rhs) >> 15;
        }
    }

    private static final class ChannelState {
        final int channelIndex;
        int bank = 0;
        int program = 0;
        boolean drum = false;
        int familyMode = 0;
        int rawPitchWord = 0x2000;
        int bendRangeByte = 2;
        int rawLevel6 = 0x3f;
        int rawPitchHigh6 = 0x20;
        int rawPitchLow6 = 0x20;
        int channelVolumeByte = 0x64;
        int channelPanByte = 0;
        int expressionByte = 0x7f;
        int modPair18Byte = 0;
        int modPair19Byte = 0;
        int nativePanModeIndex = 0;
        boolean nativeVelocityRemapEnabled = false;
        int nativeVelocityRemapBaseNote = 0;
        double nativeVelocityRemapSlope = 0.0;
        ResolvedZone selectedZoneOverride;
        final TemplateState template = new TemplateState();
        int linkSource = -1;
        int auxPitchWord = 0x400;
        int auxNote = 0x15;

        ChannelState(int channelIndex) {
            this.channelIndex = channelIndex;
            reset();
        }

        void reset() {
            bank = 0;
            program = 0;
            // Native channel reset marks channel 9 as the drum family by default.
            applyFamilyMode(channelIndex == 9 ? 1 : 0);
            rawPitchWord = 0x2000;
            bendRangeByte = 2;
            rawLevel6 = 0x3f;
            rawPitchHigh6 = 0x20;
            rawPitchLow6 = 0x20;
            channelVolumeByte = 0x64;
            channelPanByte = 0;
            expressionByte = 0x7f;
            modPair18Byte = 0;
            modPair19Byte = 0;
            nativePanModeIndex = 0;
            nativeVelocityRemapEnabled = false;
            nativeVelocityRemapBaseNote = 0;
            nativeVelocityRemapSlope = 0.0;
            clearControlTemplate();
        }

        void applyFamilyMode(int mode) {
            familyMode = mode & 0x7;
            drum = familyMode == 1;
        }

        void updateRawPitchBend() {
            int rawPitchWord = ((((rawPitchHigh6 & 0x3f) << 5) + (rawPitchLow6 & 0x3f)) << 3) - 0x100;
            setRawPitchWord(rawPitchWord);
        }

        void updateRawLevelChannelVolume() {
            channelVolumeByte = clampRange(rawLevel6 << 1, 0, 0x7f);
        }

        void setRawPan6(int rawPan6) {
            channelPanByte = clampRange((rawPan6 << 1) - 0x40, -0x40, 0x3f);
        }

        void setRawPitchWord(int rawPitchWord) {
            this.rawPitchWord = clampRange(rawPitchWord, 0, 0x3fff);
        }

        int modSeedByte() {
            return clampRange(modPair18Byte + modPair19Byte, 0, 0x7f);
        }

        void setBendRangeByte(int bendRangeByte) {
            this.bendRangeByte = clampRange(bendRangeByte, 0, 0x18);
        }

        VoiceTemplate snapshotTemplate(ResolvedZone resolved) {
            if (!template.loaded && selectedZoneOverride != null) {
                template.loadFromZone(selectedZoneOverride);
            }
            if (template.loaded) {
                return template.snapshot();
            }
            return VoiceTemplate.fromZone(resolved.zone);
        }

        void clearControlTemplate() {
            selectedZoneOverride = null;
            linkSource = -1;
            auxPitchWord = 0x400;
            auxNote = 0x15;
            template.clear();
        }

        void reloadSelectedTemplate() {
            if (selectedZoneOverride != null) {
                template.loadFromZone(selectedZoneOverride);
            }
        }

        SelectorState selectorState(int noteByte) {
            int bankByte = bank & 0xff;
            int programIndex = program & 0x7f;
            if ((bankByte & 1) != 0) {
                programIndex = clampRange(programIndex + 0x40, 0, 0x7f);
            }
            if (familyMode == 1) {
                if (bankByte == 0x34) {
                    return new SelectorState(0x14, 0, program & 0x7f, noteByte);
                }
                if (bankByte == 0x36) {
                    return new SelectorState(0x10, 0, program & 0x7f, noteByte);
                }
                if (bankByte == 0x04 || bankByte == 0x05) {
                    return new SelectorState(0x78, 1, 0x19, noteByte);
                }
                if (bankByte < 0x34) {
                    return new SelectorState(0x78, 0, programIndex, noteByte);
                }
                return new SelectorState(-1, 0, 0, noteByte);
            }
            if (familyMode != 0) {
                return new SelectorState(-1, 0, 0, noteByte);
            }
            if (bankByte == 0x00) {
                return new SelectorState(0x7d, 0, program & 0x7f, noteByte);
            }
            if (bankByte == 0x36) {
                return new SelectorState(0x11, 0, program & 0x7f, noteByte);
            }
            if (bankByte == 0x04 || bankByte == 0x05) {
                return new SelectorState(0x79, 1, programIndex, noteByte);
            }
            if (bankByte < 0x34) {
                return new SelectorState(0x79, 0, programIndex, noteByte);
            }
            return new SelectorState(-1, 0, 0, noteByte);
        }

        void applySelectControl(FueTrekRom rom, byte[] payload) {
            if (!isValidSelectPayload(payload)) {
                return;
            }
            int flags = payload[0] & 0xff;
            if ((flags & 1) != 0) {
                // The preset-slot branch depends on runtime slot uploads handled by
                // other lib002 control messages. The fixed ROM backend only supports
                // the direct branch here.
                return;
            }
            int groupId = directSelectGroupId(payload);
            int objectIndex = directSelectObjectIndex(payload);
            if (groupId < 0 || objectIndex < 0) {
                return;
            }
            FueTrekRom.Group group = rom.group(groupId);
            if (group == null) {
                return;
            }
            FueTrekRom.ObjectHeader object = group.entry(objectIndex);
            if (object == null) {
                return;
            }
            int key = payload[5] & 0x7f;
            for (FueTrekRom.Zone zone : object.zones) {
                if (zone != null && key <= zone.keyHigh) {
                    selectedZoneOverride = new ResolvedZone(zone, groupId, 0, objectIndex, key);
                    template.loadFromZone(selectedZoneOverride);
                    return;
                }
            }
        }

        void applyPitchPairControl(FueTrekRom rom, ChannelState[] channels, int channelIndex, byte[] payload) {
            applyPitchPairControl(rom, channels, channelIndex, payload, 0);
        }

        void applyLinkControl(ChannelState[] channels, byte[] payload) {
            applyLinkControl(channels, payload, 0);
        }

        void applyOscBalanceControl(byte[] payload) {
            applyOscBalanceControl(payload, 0);
        }

        void applyTemplateByte38(byte[] payload) {
            applyTemplateByte38(payload, 0);
        }

        void applyLargeControl(FueTrekRom rom, ChannelState[] channels, int channelIndex, byte[] payload) {
            if (payload == null || payload.length < 0x2c || !isValidSelectPayload(payload)) {
                return;
            }
            if (!isValidPitchPairPayload(payload, 6)
                    || !isValidLinkPayload(payload, 8)
                    || !isValidPanPayload(payload, 9)
                    || !isValidPairPayload(payload, 10)
                    || !isValidSevenBytePacket(payload, 12)
                    || !isValidSevenBytePacket(payload, 19)
                    || !isValidEnumPayload(payload, 26, 2)
                    || !isValidPairPayload(payload, 27)
                    || !isValidPairPayload(payload, 29)
                    || !isValidPairPayload(payload, 31)
                    || !isValidPitchTrackPayload(payload, 33)
                    || !isValidEnumPayload(payload, 36, 3)
                    || !isValidPairPayload(payload, 37)
                    || !isValidPairPayload(payload, 39)
                    || !isValidPairPayload(payload, 41)
                    || !isValidByte38Payload(payload, 43)) {
                return;
            }
            if ((payload[0] & 1) != 0) {
                // Runtime upload slots are managed by other control families that
                // are still outside the packed fixed-ROM backend.
                return;
            }
            applySelectControl(rom, payload);
            applyPitchPairControl(rom, channels, channelIndex, payload, 6);
            applyLinkControl(channels, payload, 8);
            applyOscBalanceControl(payload, 9);
            applyTemplateByte14Control(payload, 10);
            applyTemplateEnvAControl(payload, 12);
            applyTemplateEnvBControl(payload, 19);
            applyTemplateShapeMode(payload, 26);
            applyTemplateShapeW4(payload, 27);
            applyTemplateShapeW2(payload, 29);
            applyTemplateEnvBC(payload, 31);
            applyTemplatePitchTrack(payload, 33);
            applyTemplateModMode(payload, 36);
            applyTemplateModA(payload, 37);
            applyTemplateMod8(payload, 39);
            applyTemplateModScale2(payload, 41);
            applyTemplateByte38(payload, 43);
        }

        void applyTemplateByte14Control(byte[] payload) {
            applyTemplateByte14Control(payload, 0);
        }

        void applyTemplateEnvAControl(byte[] payload) {
            applyTemplateEnvAControl(payload, 0);
        }

        void applyTemplateEnvBControl(byte[] payload) {
            applyTemplateEnvBControl(payload, 0);
        }

        void applyTemplateShapeMode(byte[] payload) {
            applyTemplateShapeMode(payload, 0);
        }

        void applyTemplateShapeW4(byte[] payload) {
            applyTemplateShapeW4(payload, 0);
        }

        void applyTemplateShapeW2(byte[] payload) {
            applyTemplateShapeW2(payload, 0);
        }

        void applyTemplateEnvBC(byte[] payload) {
            applyTemplateEnvBC(payload, 0);
        }

        void applyTemplatePitchTrack(byte[] payload) {
            applyTemplatePitchTrack(payload, 0);
        }

        void applyTemplateModMode(byte[] payload) {
            applyTemplateModMode(payload, 0);
        }

        void applyTemplateModA(byte[] payload) {
            applyTemplateModA(payload, 0);
        }

        void applyTemplateMod8(byte[] payload) {
            applyTemplateMod8(payload, 0);
        }

        void applyTemplateModScale2(byte[] payload) {
            applyTemplateModScale2(payload, 0);
        }

        private void applyPitchPairControl(
                FueTrekRom rom,
                ChannelState[] channels,
                int channelIndex,
                byte[] payload,
                int offset) {
            if (!isValidPitchPairPayload(payload, offset)) {
                return;
            }
            int note = payload[offset] & 0x7f;
            int encoded = payload[offset + 1] & 0xff;
            auxNote = note;
            auxPitchWord = rom.nativeRootKeyWord(encoded);
            propagateAuxPair(channels, channelIndex);
        }

        private void applyLinkControl(ChannelState[] channels, byte[] payload, int offset) {
            if (!isValidLinkPayload(payload, offset)) {
                return;
            }
            int source = payload[offset] & 0x7f;
            ChannelState leader = channels[source];
            linkSource = source;
            auxNote = leader.auxNote;
            auxPitchWord = leader.auxPitchWord;
        }

        private void applyOscBalanceControl(byte[] payload, int offset) {
            if (!isValidPanPayload(payload, offset)) {
                return;
            }
            template.ensureLoaded();
            int value = payload[offset] & 0xff;
            if (value < 0x80) {
                template.ampA = 0x1ff;
                template.ampB = value << 2;
            } else {
                template.ampA = (0xff - value) << 2;
                template.ampB = 0x1ff;
            }
        }

        private void applyTemplateByte14Control(byte[] payload, int offset) {
            if (!isValidPairPayload(payload, offset)) {
                return;
            }
            template.ensureLoaded();
            int value = clampRange(decodeSigned14(payload, offset) >> 8, 0, 0x1f);
            template.zoneGainByte = value << 1;
        }

        private void applyTemplateEnvAControl(byte[] payload, int offset) {
            if (!isValidSevenBytePacket(payload, offset)) {
                return;
            }
            template.ensureLoaded();
            template.envA6 = FueTrekControlTables.quantize(
                    FueTrekControlTables.CURVE_28, decodeSigned14(payload, offset) >> 2);
            template.envA8 = FueTrekControlTables.quantize(
                    FueTrekControlTables.CURVE_2A, decodeSigned14(payload, offset + 2) >> 2);
            template.envAA = FueTrekControlTables.quantize(
                    FueTrekControlTables.CURVE_2C, decodeSigned14(payload, offset + 4) >> 2);
            template.envAE = clampRange(((payload[offset + 6] & 0x7f) - 0x40) >> 1, 0, 0x1f);
        }

        private void applyTemplateEnvBControl(byte[] payload, int offset) {
            if (!isValidSevenBytePacket(payload, offset)) {
                return;
            }
            template.ensureLoaded();
            template.envB2 = FueTrekControlTables.quantize(
                    FueTrekControlTables.CURVE_16, decodeSigned14(payload, offset) >> 2);
            template.envB4 = FueTrekControlTables.quantize(
                    FueTrekControlTables.CURVE_1E, decodeSigned14(payload, offset + 2) >> 2);
            template.envB6 = FueTrekControlTables.quantize(
                    FueTrekControlTables.CURVE_18, decodeSigned14(payload, offset + 4) >> 2);
            template.envBA = clampRange(((payload[offset + 6] & 0x7f) - 0x40) >> 1, 0, 0x1f);
        }

        private void applyTemplateShapeMode(byte[] payload, int offset) {
            if (!isValidEnumPayload(payload, offset, 2)) {
                return;
            }
            template.ensureLoaded();
            template.shapeMode = payload[offset] & 0x7f;
        }

        private void applyTemplateShapeW4(byte[] payload, int offset) {
            if (!isValidPairPayload(payload, offset)) {
                return;
            }
            template.ensureLoaded();
            template.shapeW4 = FueTrekControlTables.quantize(
                    FueTrekControlTables.CURVE_20, decodeSigned14(payload, offset) >> 2);
        }

        private void applyTemplateShapeW2(byte[] payload, int offset) {
            if (!isValidPairPayload(payload, offset)) {
                return;
            }
            template.ensureLoaded();
            template.shapeW2 = FueTrekControlTables.quantize(
                    FueTrekControlTables.CURVE_1A, decodeSigned14(payload, offset) >> 2);
        }

        private void applyTemplateEnvBC(byte[] payload, int offset) {
            if (!isValidPairPayload(payload, offset)) {
                return;
            }
            template.ensureLoaded();
            template.envBC = FueTrekControlTables.quantize(
                    FueTrekControlTables.CURVE_24, decodeSigned14(payload, offset) >> 2);
        }

        private void applyTemplatePitchTrack(byte[] payload, int offset) {
            if (!isValidPitchTrackPayload(payload, offset)) {
                return;
            }
            template.ensureLoaded();
            template.envBE = payload[offset] & 0x7f;
            template.envB10 = clampRange(decodeSigned14(payload, offset + 1) >> 7, -0x40, 0x3f);
        }

        private void applyTemplateModMode(byte[] payload, int offset) {
            if (!isValidEnumPayload(payload, offset, 3)) {
                return;
            }
            template.ensureLoaded();
            template.modMode = payload[offset] & 0x7f;
        }

        private void applyTemplateModA(byte[] payload, int offset) {
            if (!isValidPairPayload(payload, offset)) {
                return;
            }
            template.ensureLoaded();
            template.modA = FueTrekControlTables.quantize(
                    FueTrekControlTables.CURVE_32, decodeSigned14(payload, offset) >> 4);
        }

        private void applyTemplateMod8(byte[] payload, int offset) {
            if (!isValidPairPayload(payload, offset)) {
                return;
            }
            template.ensureLoaded();
            template.mod8 = FueTrekControlTables.quantize(
                    FueTrekControlTables.CURVE_34, decodeSigned14(payload, offset) >> 4);
        }

        private void applyTemplateModScale2(byte[] payload, int offset) {
            if (!isValidPairPayload(payload, offset)) {
                return;
            }
            template.ensureLoaded();
            template.modScale2 = FueTrekControlTables.quantize(
                    FueTrekControlTables.CURVE_36, decodeSigned14(payload, offset) >> 4);
        }

        private void applyTemplateByte38(byte[] payload, int offset) {
            if (!isValidByte38Payload(payload, offset)) {
                return;
            }
            template.ensureLoaded();
            template.modDelay = payload[offset] & 0x7f;
        }

        private void propagateAuxPair(ChannelState[] channels, int sourceIndex) {
            for (int i = 0; i < channels.length; i++) {
                if (i != sourceIndex && channels[i].linkSource == sourceIndex) {
                    channels[i].auxNote = auxNote;
                    channels[i].auxPitchWord = auxPitchWord;
                }
            }
        }

        private static boolean isValidSelectPayload(byte[] payload) {
            if (payload == null || payload.length < 6) {
                return false;
            }
            int flags = payload[0] & 0xff;
            if ((flags & 0xfc) != 0) {
                return false;
            }
            if ((payload[1] & 0x80) != 0 || (payload[2] & 0xf8) != 0
                    || (payload[3] & 0xc0) != 0 || (payload[4] & 0xc0) != 0
                    || (payload[5] & 0x80) != 0) {
                return false;
            }
            int note = payload[5] & 0x7f;
            if (note < 0x15 || note > 0x78) {
                return false;
            }
            if ((flags & 1) != 0) {
                return (payload[0] & 0x7f) < 0x10;
            }
            int mode = payload[2] & 7;
            int byte3 = payload[3] & 0x3f;
            int byte4 = payload[4] & 0x3f;
            if (mode == 0) {
                if (byte3 == 0) {
                    return byte4 <= 5;
                }
                return byte3 == 1 || byte3 == 2;
            }
            if (mode == 1) {
                if (byte4 == 2) {
                    return note >= 0x23 && note <= 0x51;
                }
                if (byte4 == 0x32) {
                    return note >= 0x23 && note <= 0x42;
                }
            }
            return false;
        }

        private static boolean isValidPitchPairPayload(byte[] payload, int offset) {
            if (payload == null || offset < 0 || offset + 1 >= payload.length) {
                return false;
            }
            int note = payload[offset] & 0x7f;
            int encoded = payload[offset + 1] & 0xff;
            return note >= 0x15 && note <= 0x78 && encoded >= 0x1d && encoded <= 0x80;
        }

        private static boolean isValidLinkPayload(byte[] payload, int offset) {
            return payload != null
                    && offset >= 0
                    && offset < payload.length
                    && (payload[offset] & 0x7f) < CHANNEL_COUNT;
        }

        private static boolean isValidPanPayload(byte[] payload, int offset) {
            return payload != null && offset >= 0 && offset < payload.length;
        }

        private static boolean isValidPairPayload(byte[] payload, int offset) {
            return payload != null
                    && offset >= 0
                    && offset + 1 < payload.length
                    && (payload[offset] & 0xc0) == 0;
        }

        private static boolean isValidSevenBytePacket(byte[] payload, int offset) {
            return isValidPairPayload(payload, offset)
                    && isValidPairPayload(payload, offset + 2)
                    && isValidPairPayload(payload, offset + 4)
                    && offset + 6 < payload.length
                    && payload[offset + 6] >= 0;
        }

        private static boolean isValidPitchTrackPayload(byte[] payload, int offset) {
            if (payload == null || offset < 0 || offset + 2 >= payload.length) {
                return false;
            }
            int note = payload[offset] & 0x7f;
            return note >= 0x15 && note <= 0x78 && isValidPairPayload(payload, offset + 1);
        }

        private static boolean isValidEnumPayload(byte[] payload, int offset, int max) {
            return payload != null
                    && offset >= 0
                    && offset < payload.length
                    && payload[offset] >= 0
                    && (payload[offset] & 0x7f) <= max;
        }

        private static boolean isValidByte38Payload(byte[] payload, int offset) {
            return payload != null
                    && offset >= 0
                    && offset < payload.length
                    && payload[offset] >= 0
                    && (payload[offset] & 0x7f) <= 0x13;
        }

        private static int directSelectGroupId(byte[] payload) {
            int mode = payload[2] & 7;
            int byte3 = payload[3] & 0x3f;
            int byte4 = payload[4] & 0x3f;
            if (mode == 0) {
                if (byte3 == 0) {
                    return 0x7d;
                }
                if (byte3 == 1 || byte3 == 2) {
                    return 0x79;
                }
                return -1;
            }
            if (mode == 1) {
                if (byte4 == 2) {
                    return 0x78;
                }
                if (byte4 == 0x32) {
                    return 0x14;
                }
            }
            return -1;
        }

        private static int directSelectObjectIndex(byte[] payload) {
            int mode = payload[2] & 7;
            int byte3 = payload[3] & 0x3f;
            int byte4 = payload[4] & 0x3f;
            int note = payload[5] & 0x7f;
            if (mode == 0) {
                if (byte3 == 0) {
                    return byte4;
                }
                if (byte3 == 1) {
                    return byte4 + 0x40;
                }
                if (byte3 == 2) {
                    return byte4;
                }
                return -1;
            }
            if (mode == 1 && (byte4 == 2 || byte4 == 0x32)) {
                return note;
            }
            return -1;
        }
    }

    private static final class TemplateState {
        boolean loaded;
        int ampA;
        int ampB;
        int zoneGainByte;
        int modMode;
        int modA;
        int mod8;
        int modScale2;
        int modDelay;
        int envA6;
        int envA8;
        int envAA;
        int envAE;
        int envB2;
        int envB4;
        int envB6;
        int notePitchMulWord;
        int envBA;
        int envBC;
        int envBE;
        int envB10;
        int shapeMode;
        int shapeW2;
        int shapeW4;

        void ensureLoaded() {
            loaded = true;
        }

        void clear() {
            loaded = false;
            ampA = 0;
            ampB = 0;
            zoneGainByte = 0;
            modMode = 0;
            modA = 0;
            mod8 = 0;
            modScale2 = 0;
            modDelay = 0;
            envA6 = 0;
            envA8 = 0;
            envAA = 0;
            envAE = 0;
            envB2 = 0;
            envB4 = 0;
            envB6 = 0;
            notePitchMulWord = 0;
            envBA = 0;
            envBC = 0;
            envBE = 0;
            envB10 = 0;
            shapeMode = 0;
            shapeW2 = 0;
            shapeW4 = 0;
        }

        void loadFromZone(ResolvedZone resolved) {
            loadFromZone(resolved.zone);
        }

        void loadFromZone(FueTrekRom.Zone zone) {
            loaded = true;
            ampA = zone.s16(0x0c);
            ampB = zone.s16(0x0e);
            zoneGainByte = zone.s8(0x14);
            modMode = zone.s8(0x30);
            modA = zone.s16(0x32);
            mod8 = zone.s16(0x34);
            modScale2 = zone.s16(0x36);
            modDelay = zone.s8(0x38);
            envA6 = zone.s16(0x28);
            envA8 = zone.s16(0x2a);
            envAA = zone.s16(0x2c);
            envAE = zone.s8(0x2f);
            envB2 = zone.s16(0x16);
            envB4 = zone.s16(0x1e);
            envB6 = zone.s16(0x18);
            notePitchMulWord = zone.s16(0x1c);
            envBA = zone.s8(0x22);
            envBC = zone.s16(0x24);
            envBE = zone.s8(0x40);
            envB10 = zone.s16(0x42);
            shapeMode = zone.s8(0x15);
            shapeW2 = zone.s16(0x1a);
            shapeW4 = zone.s16(0x20);
        }

        VoiceTemplate snapshot() {
            return new VoiceTemplate(
                    ampA,
                    ampB,
                    zoneGainByte,
                    modMode,
                    modA,
                    mod8,
                    modScale2,
                    modDelay,
                    envA6,
                    envA8,
                    envAA,
                    envAE,
                    envB2,
                    envB4,
                    envB6,
                    notePitchMulWord,
                    envBA,
                    envBC,
                    envBE,
                    envB10,
                    shapeMode,
                    shapeW2,
                    shapeW4);
        }
    }

    private static final class VoiceTemplate {
        final int ampA;
        final int ampB;
        final int zoneGainByte;
        final int modMode;
        final int modA;
        final int mod8;
        final int modScale2;
        final int modDelay;
        final int envA6;
        final int envA8;
        final int envAA;
        final int envAE;
        final int envB2;
        final int envB4;
        final int envB6;
        final int notePitchMulWord;
        final int envBA;
        final int envBC;
        final int envBE;
        final int envB10;
        final int shapeMode;
        final int shapeW2;
        final int shapeW4;

        VoiceTemplate(
                int ampA,
                int ampB,
                int zoneGainByte,
                int modMode,
                int modA,
                int mod8,
                int modScale2,
                int modDelay,
                int envA6,
                int envA8,
                int envAA,
                int envAE,
                int envB2,
                int envB4,
                int envB6,
                int notePitchMulWord,
                int envBA,
                int envBC,
                int envBE,
                int envB10,
                int shapeMode,
                int shapeW2,
                int shapeW4) {
            this.ampA = ampA;
            this.ampB = ampB;
            this.zoneGainByte = zoneGainByte;
            this.modMode = modMode;
            this.modA = modA;
            this.mod8 = mod8;
            this.modScale2 = modScale2;
            this.modDelay = modDelay;
            this.envA6 = envA6;
            this.envA8 = envA8;
            this.envAA = envAA;
            this.envAE = envAE;
            this.envB2 = envB2;
            this.envB4 = envB4;
            this.envB6 = envB6;
            this.notePitchMulWord = notePitchMulWord;
            this.envBA = envBA;
            this.envBC = envBC;
            this.envBE = envBE;
            this.envB10 = envB10;
            this.shapeMode = shapeMode;
            this.shapeW2 = shapeW2;
            this.shapeW4 = shapeW4;
        }

        static VoiceTemplate fromZone(FueTrekRom.Zone zone) {
            TemplateState state = new TemplateState();
            state.loadFromZone(zone);
            return state.snapshot();
        }
    }

    private static final class Voice {
        final FueTrekRom rom;
        final MixState mixState;
        final ChannelState channel;
        final int channelIndex;
        final int key;
        final int midiKey;
        final long age;
        final int velocityByte;
        final Oscillator oscA;
        final Oscillator oscB;
        final int groupId;
        final int drumPanIndex;
        final int rootA;
        final int rootB;
        final int subGroupId;
        final int selectorIndex;
        final int tuneA;
        final int tuneB;
        final int ampA;
        final int ampB;
        final int oscBMode;
        final int zoneGainByte;
        final int baseNote;
        final ModState modState;
        final EnvAState envA;
        final EnvBState envB;
        final ShapeState toneShape;
        final int[] noiseState = new int[16];
        final int mixProfileMode;
        int bendRangeByte;
        final int staticPitchQ16;
        int livePitchQ16;
        int noteCoarse;
        int noteFine;
        int mixLeftWord;
        int mixRightWord;
        float outLeft = 0.0f;
        float outRight = 0.0f;

        Voice(
                FueTrekRom rom,
                MixState mixState,
                ChannelState channel,
                int channelIndex,
                int key,
                int velocityByte,
                ResolvedZone resolved,
                VoiceTemplate template,
                float romStepScale,
                int mixProfileMode,
                long age) {
            this.rom = rom;
            this.mixState = mixState;
            this.channel = channel;
            this.channelIndex = channelIndex;
            this.key = key;
            this.midiKey = resolved.noteByte;
            this.velocityByte = velocityByte;
            this.age = age;
            this.groupId = resolved.groupId;
            this.subGroupId = resolved.subGroupId;
            this.selectorIndex = resolved.selectorIndex;
            this.mixProfileMode = Math.max(0, Math.min(3, mixProfileMode));
            this.drumPanIndex = (resolved.groupId == 0x78) ? (rom.drumPanTable[resolved.noteByte] & 0x7f) : 0x40;
            FueTrekRom.Zone zone = resolved.zone;
            FueTrekRom.Sample sampleA = zone.sampleA;
            FueTrekRom.Sample sampleB = zone.sampleB != null ? zone.sampleB : sampleA;
            if (sampleB == null) {
                sampleB = sampleA;
            }
            // Native note-on can force a fixed playback note from zone +0x3c.
            int fixedNote = zone.s32(0x3c);
            this.baseNote = (fixedNote != -1) ? fixedNote : resolved.noteByte;
            this.rootA = sampleA.rootKey + zone.s8(0x10);
            this.rootB = sampleB.rootKey + zone.s8(0x10);
            this.tuneA = sampleA.tune1024;
            this.tuneB = sampleB.tune1024 + zone.s16(0x12);
            this.ampA = template.ampA;
            this.ampB = template.ampB;
            this.oscBMode = sampleB.controlByte & 0xFF;
            this.zoneGainByte = template.zoneGainByte;
            this.staticPitchQ16 = 0;
            this.modState = new ModState(template);
            this.envA = new EnvAState(template);
            this.envB = new EnvBState(template);
            this.toneShape = new ShapeState(template);
            for (int i = 0; i < noiseState.length; i++) {
                noiseState[i] = 1;
            }
            oscA = new Oscillator(sampleA, rom.interpolationTable, romStepScale);
            oscB = new Oscillator(sampleB, rom.interpolationTable, romStepScale);
            bendRangeByte = channel.bendRangeByte;
            applyModSeedByte(channel.modSeedByte());
            applyPitchCommit(channel.rawPitchWord);
            updatePitchWords(0.0f);
            updateMixWords();
        }

        void release() {
            envA.state = 6;
            envB.state = 6;
        }

        void applyPitchCommit(int rawPitchWord) {
            int centeredPitchWord = clampRange(rawPitchWord - 0x2000, -0x2000, 0x1fff);
            livePitchQ16 = (int) (((long) centeredPitchWord * (long) bendRangeByte) << 3);
        }

        void applyPitchRange(int bendRangeByte, int rawPitchWord) {
            this.bendRangeByte = clampRange(bendRangeByte, 0, 0x18);
            applyPitchCommit(rawPitchWord);
        }

        void applyModSeedByte(int modSeedByte) {
            modState.applySeedByte(modSeedByte);
        }

        boolean render(float masterTuneSemitones, int controlCounter) {
            if (envA.state == 0) {
                outLeft = 0.0f;
                outRight = 0.0f;
                return false;
            }
            updatePitchWords(masterTuneSemitones);
            updateMixWords();
            noiseShift(noiseState);
            modState.step(controlCounter, noiseState);
            int envAOut = envA.step(controlCounter, modState);
            int envBOut = envB.step(controlCounter, noteCoarse, toneShape.w4);

            int mixed = 0;
            if (ampA > 0) {
                int oscAValue = oscA.next(
                        noteCoarse,
                        rootA,
                        noteFine,
                        tuneA,
                        modState.pitchMod(),
                        rom.pitchRatioTable);
                mixed = clamp16(mulRoundShift(ampA, oscAValue, 9));
            }
            int oscBValue = 0;
            if (oscBMode == 1) {
                oscBValue = noiseExpandA(noiseState);
            } else if (oscBMode == 2) {
                oscBValue = noiseExpandB(noiseState);
            } else {
                oscBValue = oscB.next(
                        noteCoarse,
                        rootB,
                        noteFine,
                        tuneB,
                        modState.pitchMod(),
                        rom.pitchRatioTable);
            }
            if (ampB > 0) {
                int scaledB = clamp16(mulRoundShift(ampB, oscBValue, 9));
                mixed = clamp16(mixed + scaledB);
            }
            int shaped = toneShape.apply((short) (envBOut >> 4), (short) mixed);
            int envDriven = clamp16(mulRoundShift(zoneGainByte << 5, envAOut, 11));
            int leftControl = clamp16(mulRoundShift((mixLeftWord >> 2) & ~3, envDriven, 11));
            int leftVoice = clamp16(mulRoundShift((leftControl >> 3) & ~7, shaped, 9));
            int rightControl = clamp16(mulRoundShift((mixRightWord >> 2) & ~3, envDriven, 11));
            int rightVoice = clamp16(mulRoundShift((rightControl >> 3) & ~7, shaped, 9));

            outLeft = (leftVoice << 1) * OUTPUT_SCALE;
            outRight = (rightVoice << 1) * OUTPUT_SCALE;
            if (envA.state == 0) {
                outLeft = 0.0f;
                outRight = 0.0f;
                return false;
            }
            return true;
        }

        private void updatePitchWords(float masterTuneSemitones) {
            int pitchQ16 = staticPitchQ16 + livePitchQ16 + Math.round(masterTuneSemitones * 65536.0f);
            int coarse;
            int fine;
            if (pitchQ16 < 0) {
                int neg = -pitchQ16;
                int fracBase = (0x10000 - (neg & 0xFFFF)) >> 11;
                coarse = ((fracBase >> 5) - (neg >> 16)) + baseNote - 1;
                fine = fracBase & 0x1F;
            } else {
                coarse = (pitchQ16 >> 16) + baseNote;
                fine = (pitchQ16 >> 11) & 0x1F;
            }
            if (coarse < 0x15) {
                coarse = 0x15;
            } else if (coarse > 0x7f) {
                coarse = 0x7f;
            }
            noteCoarse = coarse;
            noteFine = fine;
        }

        private void updateMixWords() {
            int gain = mulWord(
                    FueTrekMixTables.gainWord(channel.channelVolumeByte),
                    FueTrekMixTables.gainWord(mixState.globalVolumeByte));
            int velocity = velocityByte;
            if (channel.nativeVelocityRemapEnabled && !channel.drum) {
                double remapped = ((channel.nativeVelocityRemapBaseNote - midiKey) * channel.nativeVelocityRemapSlope)
                        + velocityByte;
                if (remapped < 0.0) {
                    remapped = 0.0;
                } else if (remapped > 127.0) {
                    remapped = 127.0;
                }
                velocity = clampRange((int) Math.floor(remapped + 0.5), 0, 0x7f);
            }
            gain = mulWord(gain, FueTrekMixTables.gainWord(velocity));
            gain = mulWord(gain, FueTrekMixTables.gainWord(channel.expressionByte));
            gain = mulWord(gain, FueTrekMixTables.gainWord(mixState.global338Byte));
            gain = mulWord(gain, FueTrekMixTables.gainWord(mixState.global339Byte));

            int leftWord;
            int rightWord;
            int panMode = mixState.panModes[clampRange(channel.nativePanModeIndex, 0, mixState.panModes.length - 1)] & 0xff;
            if (panMode == 2) {
                int rightBase = mulWord(gain, FueTrekMixTables.stereoWord(mixState.masterPanByte));
                int leftBase = mulWord(gain, FueTrekMixTables.stereoWord(-mixState.masterPanByte));
                leftWord = mulWord(leftBase, FueTrekMixTables.stereoWord(-channel.channelPanByte));
                rightWord = mulWord(rightBase, FueTrekMixTables.stereoWord(channel.channelPanByte));
            } else {
                int centered = mulWord(gain, FueTrekMixTables.stereoWord(0));
                leftWord = mulWord(centered, FueTrekMixTables.stereoWord(0));
                rightWord = leftWord;
            }
            if (mixProfileMode != 0) {
                int index = ((groupId & 1) != 0) ? selectorIndex : midiKey;
                int scale = rom.mixProfileScale(mixProfileMode, groupId, subGroupId, index);
                leftWord = (leftWord * scale) >> 13;
                rightWord = (rightWord * scale) >> 13;
            }
            if (groupId == 0x78 && subGroupId == 0) {
                leftWord = scaleUnsignedWord(leftWord, rom.panLawTable[0x7f - drumPanIndex]);
                rightWord = scaleUnsignedWord(rightWord, rom.panLawTable[drumPanIndex]);
                int shapeIndex = FueTrekNoteShapeTables.noteShapeIndex(midiKey);
                leftWord = FueTrekNoteShapeTables.scaleReverse(leftWord, shapeIndex);
                rightWord = FueTrekNoteShapeTables.scaleForward(rightWord, shapeIndex);
            }
            mixLeftWord = clampRange(leftWord << 1, 0, 0x7fff);
            mixRightWord = clampRange(rightWord << 1, 0, 0x7fff);
        }

        private static int mulCurve(int lhs, int rhs) {
            return mulWord(FueTrekMixTables.gainWord(lhs), FueTrekMixTables.gainWord(rhs));
        }

        private static int mulWord(int lhs, int rhs) {
            return (lhs * rhs) >> 15;
        }
    }

    private static final class Oscillator {
        final FueTrekRom.Sample sample;
        final short[] interpolationTable;
        final float romStepScale;
        int phaseQ12 = 0;

        Oscillator(FueTrekRom.Sample sample, short[] interpolationTable, float romStepScale) {
            this.sample = sample;
            this.interpolationTable = interpolationTable;
            this.romStepScale = romStepScale;
        }

        int next(int note, int root, int fine, int tune, int pitchMod, int[] pitchRatioTable) {
            int stepQ12 = nativeStepQ12(note, root, fine, tune, pitchMod, pitchRatioTable);
            if (stepQ12 <= 0 || sample.sampleCount() < 4) {
                return 0;
            }
            int index = phaseQ12 >> 12;
            int frac = ((phaseQ12 >> 3) & 0x1ff) + 0x200;
            int idx0 = frac;
            int idx1 = frac - 0x200;
            int idx2 = 0x400 - frac;
            int idx3 = 0x600 - frac;

            int s0 = sampleAt(index);
            int s1 = sampleAt(index + 1);
            int s2 = sampleAt(index + 2);
            int s3 = sampleAt(index + 3);

            int mixed = interpolationTable[idx0] * s0;
            mixed += interpolationTable[idx1] * s1;
            mixed += interpolationTable[idx2] * s2;
            mixed += interpolationTable[idx3] * s3;
            mixed >>= 9;

            phaseQ12 += stepQ12;
            int loopStartQ12 = sample.loopStart << 12;
            int loopEndQ12 = sample.loopEnd << 12;
            int loopLengthQ12 = loopEndQ12 - loopStartQ12;
            if (loopLengthQ12 > 0 && phaseQ12 >= loopEndQ12) {
                phaseQ12 = loopStartQ12 + Math.floorMod(phaseQ12 - loopStartQ12, loopLengthQ12);
            }
            return (short) mixed;
        }

        private int nativeStepQ12(int note, int root, int fine, int tune, int pitchMod, int[] pitchRatioTable) {
            int pitchDelta = (fine >> 5) - root + note;
            int octaveShift = pitchDelta / 12;
            int remainder = pitchDelta % 12;
            int step = ((fine & 0x1f) + 0x200) * tune;
            step <<= 1;
            step = (step + ((step >> 31) & 0x3ff)) >> 10;
            step *= pitchMod + 0x200;
            step = (step + ((step >> 31) & 0x1ff)) >> 9;
            // Native `0x100092c0` indexes directly from the `0x200` ratio entry
            // at `MFiSynth_ft.dll:0x1001149c`, so remainder `0` stays on that
            // entry and negative remainders wrap naturally to the tail.
            int ratio = pitchRatioTable[Math.floorMod(remainder, pitchRatioTable.length)];
            int scaled = ((short) step) * ratio;
            scaled >>= 7;
            scaled &= ~7;
            if (note > root) {
                if (remainder != 0) {
                    octaveShift++;
                }
                if (octaveShift >= 30) {
                    scaled = Integer.MAX_VALUE;
                } else if (octaveShift > 0) {
                    scaled <<= octaveShift;
                }
            } else if (octaveShift < 0) {
                scaled >>= -octaveShift;
            }
            if (scaled < 0) {
                scaled = 0;
            }
            return Math.max(0, (int) Math.round(scaled * romStepScale));
        }

        private int sampleAt(int index) {
            int length = sample.sampleCount();
            if (index >= 0 && index < length) {
                return sample.pcm8[index];
            }
            int loopLen = sample.loopEnd - sample.loopStart;
            if (loopLen <= 0 || length <= 0) {
                return 0;
            }
            int wrapped = sample.loopStart + Math.floorMod(index - sample.loopStart, loopLen);
            if (wrapped < 0) {
                wrapped = 0;
            } else if (wrapped >= length) {
                wrapped = length - 1;
            }
            return sample.pcm8[wrapped];
        }
    }

    private static void noiseShift(int[] n) {
        int x15 = n[15];
        n[15] = n[14];
        n[14] = n[13];
        n[13] = n[12];
        n[12] = n[11] ^ x15;
        n[11] = n[10];
        n[10] = n[9];
        n[9] = n[8];
        n[8] = n[7];
        n[7] = n[6];
        n[6] = n[5];
        n[5] = n[4];
        n[4] = n[3];
        n[3] = n[2] ^ x15;
        n[2] = n[1];
        n[1] = n[0] ^ x15;
        n[0] = x15;
    }

    private static int noiseExpandA(int[] n) {
        int bits = (n[0] != 0) ? -32 : 0;
        bits |= n[1] << 4;
        bits |= n[2] << 3;
        bits |= n[3] << 2;
        bits |= n[4] << 1;
        bits |= n[5];
        return bits << 6;
    }

    private static int noiseExpandB(int[] n) {
        int bits = (n[2] != 0) ? -32 : 0;
        bits |= n[3] << 4;
        bits |= n[4] << 3;
        bits |= n[5] << 2;
        bits |= n[6] << 1;
        bits |= n[7];
        return bits << 6;
    }

    private static final class ModState {
        int value0;
        int countdown4;
        int tick6;
        int value8;
        int valueA;
        int valueC;
        int valueE;
        int state10;
        int phase14;
        int scale2;
        final int baseMod8;

        ModState(VoiceTemplate template) {
            int mode = template.modMode;
            if (mode == 2) {
                value0 = 0x1ff;
            } else if (mode == 3) {
                value0 = (short) 0xfe01;
            } else {
                value0 = 0;
            }
            countdown4 = template.modDelay;
            tick6 = 0;
            baseMod8 = template.mod8;
            value8 = baseMod8;
            valueA = template.modA;
            valueC = 0;
            valueE = 0;
            state10 = mode;
            phase14 = 1;
            scale2 = template.modScale2;
        }

        void applySeedByte(int seedByte) {
            value8 = baseMod8 + (seedByte >> 2);
        }

        int pitchMod() {
            return (short) valueE;
        }

        void step(int controlCounter, int[] noiseState) {
            if (controlCounter == 0) {
                if ((short) countdown4 == 0) {
                    switch (state10) {
                        case 0:
                            value0 = (short) (value0 + (short) valueA * phase14);
                            if (Math.abs((short) value0) >= 0x1ff) {
                                value0 = (short) (phase14 * 0x1ff);
                                phase14 = -phase14;
                            }
                            valueC = (short) value0;
                            break;
                        case 1:
                            value0 = (short) (value0 + (short) valueA * phase14);
                            if (Math.abs((short) value0) >= 0x1ff) {
                                value0 = (short) (phase14 * 0x1ff);
                                phase14 = -phase14;
                            }
                            valueC = (short) (phase14 * 0x1ff);
                            break;
                        case 2:
                            if ((short) value0 > (short) valueA) {
                                value0 = (short) ((short) value0 - (short) valueA);
                                valueC = (short) value0;
                            } else {
                                value0 = 0;
                                valueC = 0;
                            }
                            break;
                        case 3:
                            if ((short) value0 + (short) valueA < 0) {
                                value0 = (short) ((short) value0 + (short) valueA);
                            } else {
                                value0 = 0;
                            }
                            valueC = (short) value0;
                            break;
                        default:
                            break;
                    }
                } else {
                    tick6 = (tick6 + 1) & 0xff;
                    if (tick6 == 0x18) {
                        tick6 = 0;
                        countdown4 = (short) (countdown4 - 1);
                    }
                    if (state10 == 1) {
                        valueC = (short) (phase14 * 0x1ff);
                    } else {
                        valueC = (short) value0;
                    }
                }
            }
            int scaled = clamp16(roundNearestShift((((short) valueC) << 6) * (short) value8, 9));
            valueE = (short) (scaled >> 6);
        }
    }

    private static final class EnvAState {
        int value0;
        int value2;
        int value4;
        int value6;
        int value8;
        int valueA;
        int valueC;
        int valueE;
        int state;
        int output;

        EnvAState(VoiceTemplate template) {
            value0 = 0;
            value2 = 0;
            value4 = 0;
            value6 = template.envA6 << 4;
            value8 = template.envA8;
            valueA = template.envAA;
            valueC = 0x1f;
            valueE = template.envAE << 10;
            state = 1;
            output = 0;
        }

        int step(int controlCounter, ModState modState) {
            if (state >= 0 && state <= 7) {
                switch (state) {
                    case 0:
                        if ((controlCounter & 1) == 0) {
                            state = 1;
                            updateLinear(controlCounter);
                        }
                        break;
                    case 1:
                        if ((controlCounter & 1) == 0) {
                            state = 2;
                            updateLinear(controlCounter);
                        }
                        break;
                    case 2:
                        if ((controlCounter & 1) == 0) {
                            value4 = (short) value6;
                            value2 = (short) value6;
                            value0 = (short) value6;
                            state = 3;
                        }
                        break;
                    case 3:
                        if ((controlCounter & 0x7f) == 0) {
                            value4 = (short) value2;
                            int next = clamp16(mulSignRoundShift((short) value2, 0x7ff, 11) + (short) value6);
                            value2 = (short) next;
                            int delta = (short) value2 - (short) value4;
                            int current = clamp16(mulSignRoundShift(delta, controlCounter << 1, 8) + (short) value4);
                            value0 = (short) current;
                            if ((short) value2 >= 0x7c00) {
                                if ((short) value0 >= 0x7c00) {
                                    value0 = 0x7c00;
                                    value4 = 0x7c00;
                                    value2 = 0x7c00;
                                }
                                state = 4;
                            }
                        }
                        break;
                    case 4:
                        if ((controlCounter & 0x7f) == 0) {
                            value4 = (short) value2;
                            int next = clamp16(mulSignRoundShift((short) value8, (short) value2, 11));
                            value2 = (short) next;
                            int delta = (short) value2 - (short) value4;
                            int current = clamp16(mulSignRoundShift(delta, controlCounter << 1, 8) + (short) value4);
                            value0 = (short) current;
                            if ((short) value2 <= (short) valueE) {
                                value2 = (short) valueE;
                                state = 5;
                            }
                        }
                        break;
                    case 5:
                        if ((controlCounter & 0x7f) == 0) {
                            value4 = (short) value2;
                            value0 = (short) value2;
                        }
                        break;
                    case 6:
                        if ((controlCounter & 0x7f) == 0) {
                            value4 = (short) value2;
                            int next = clamp16(mulSignRoundShift((short) valueA, (short) value2, 11));
                            value2 = (short) next;
                            int delta = (short) value2 - (short) value4;
                            int current = clamp16(mulSignRoundShift(delta, controlCounter << 1, 8) + (short) value4);
                            value0 = (short) current;
                            if ((short) value0 <= 0) {
                                state = 0;
                                value0 = 0;
                            }
                        }
                        break;
                    case 7:
                        if ((controlCounter & 1) == 0) {
                            value4 = (short) value2;
                            int next = clamp16(mulSignRoundShift((short) value2, 0x7dc, 11));
                            value2 = (short) next;
                            int delta = (short) value2 - (short) value4;
                            int current = clamp16(mulSignRoundShift(delta, controlCounter << 1, 8) + (short) value4);
                            value0 = (short) current;
                            if ((short) value0 <= 0) {
                                state = 0;
                                value0 = 0;
                            }
                        }
                        break;
                    default:
                        break;
                }
            }

            int stage = clamp16(mulRoundShift((modState.valueC << 6), ((short) value0) >> 4, 11));
            stage = clamp16(mulRoundShift(modState.scale2 << 2, stage, 11));
            int curve = clamp16(mulRoundShift((short) value0, 0x7ff, 11));
            int mixed = clamp16(stage + curve);
            output = (short) mixed;
            return (short) output;
        }

        private void updateLinear(int controlCounter) {
            value4 = (short) value2;
            int delta = -((controlCounter << 1) * (short) value4);
            int slope = clamp16((delta >> 8) + (delta < 0 ? 1 : 0) + (short) value4);
            value2 = 0;
            value0 = (short) slope;
        }
    }

    private static final class EnvBState {
        int value0;
        int value2;
        int value4;
        int value6;
        int value8;
        int valueA;
        int valueC;
        int valueE;
        int value10;
        int state;
        int output;

        EnvBState(VoiceTemplate template) {
            value0 = 0;
            value2 = template.envB2 << 4;
            value4 = template.envB4;
            value6 = template.envB6;
            value8 = 0x1f;
            valueA = template.envBA << 10;
            valueC = template.envBC;
            valueE = template.envBE;
            value10 = template.envB10 << 4;
            state = 1;
            output = 0;
        }

        int step(int controlCounter, int note, int shapeMix) {
            if (state >= 0 && state <= 7) {
                switch (state) {
                    case 0:
                        if ((controlCounter & 1) == 0) {
                            state = 1;
                            value0 = 0;
                        }
                        break;
                    case 1:
                        if ((controlCounter & 1) == 0) {
                            state = 2;
                            value0 = 0;
                        }
                        break;
                    case 2:
                        if ((controlCounter & 1) == 0) {
                            int curve = clamp16(mulSignRoundShift((short) value0, 0x7ff, 11) + (short) value2);
                            value0 = (short) curve;
                            if ((short) value0 >= 0x7c00) {
                                state = 4;
                                value0 = 0x7c00;
                            } else {
                                state = 3;
                            }
                        }
                        break;
                    case 3:
                        if ((controlCounter & 0x7f) == 0) {
                            int curve = clamp16(mulSignRoundShift((short) value0, 0x7ff, 11) + (short) value2);
                            value0 = (short) curve;
                            if ((short) value0 >= 0x7c00) {
                                state = 4;
                                value0 = 0x7c00;
                            }
                        }
                        break;
                    case 4:
                        if ((controlCounter & 0x7f) == 0) {
                            int curve = clamp16(mulSignRoundShift((short) value4, (short) value0, 11));
                            value0 = (short) curve;
                            if ((short) value0 <= (short) valueA) {
                                value0 = (short) valueA;
                                state = 5;
                            }
                        }
                        break;
                    case 5:
                        if ((controlCounter & 0x7f) == 0) {
                            // Native keeps state 5 as sustain-like branch.
                        }
                        break;
                    case 6:
                        if ((controlCounter & 0x7f) == 0) {
                            int curve = clamp16(mulSignRoundShift((short) value6, (short) value0, 11));
                            value0 = (short) curve;
                            if ((short) value0 <= 0) {
                                state = 0;
                                value0 = 0;
                            }
                        }
                        break;
                    case 7:
                        if ((controlCounter & 1) == 0) {
                            int curve = clamp16(mulSignRoundShift((short) value0, 0x7dc, 11));
                            value0 = (short) curve;
                            if ((short) value0 <= 0) {
                                state = 0;
                                value0 = 0;
                            }
                        }
                        break;
                    default:
                        break;
                }
            }

            int pitchProduct = ((valueE - (note & 0xff)) * value10) << 7;
            int pitchCurve = clamp16(roundNearestShift(pitchProduct, 8) + shapeMix);
            int levelCurve = mulRoundShift((short) valueC, (short) value0, 11);
            output = (short) clamp16(levelCurve + pitchCurve);
            return (short) output;
        }
    }

    private static final class ShapeState {
        int mode;
        int w2;
        int w4;
        int w6;
        int w8;

        ShapeState(VoiceTemplate template) {
            this.mode = template.shapeMode;
            this.w2 = clamp16(template.shapeW2);
            this.w4 = clamp16(template.shapeW4 << 4);
            this.w6 = 0;
            this.w8 = 0;
        }

        int apply(short control, short input) {
            int first = clamp16(mulRoundShift(w6, control, 10) + w8);
            int mixed = clamp16(mulRoundShift(w2, w6, 10) - first + input);
            int next = clamp16(mulRoundShift(control, mixed, 10) + w6);
            w6 = next;
            w8 = first;
            if (mode == 1) {
                return mixed;
            }
            if (mode == 2) {
                return next;
            }
            return first;
        }
    }
}
