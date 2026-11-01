package opendoja.audio.mld.fuetrek;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/** Classic24FA0 tone-event production; its caches must remain separate from sounding voices. */
public final class FueTrekMfiCommandCompiler {
    public interface Listener {
        void command(int command, int channel, int delta, int argument);
        void channelEnabled(int channel, int value);
        /** The component reset also resets resource/thread state outside the tone compiler. */
        default void componentReset() {}
    }

    private final Listener listener;
    private final int version;
    private final int profile;
    // Native context190..210: level, bank, requested/active family, program,
    // high pitch, committed low pitch, cached low pitch, then global level.
    private final byte[] context = new byte[129];
    // Pointer-free native14e20 tone cache,0c..1cb. Kept in wire units rather
    // than normalized sampler floats so a future producer can read ahead.
    private final byte[] tone = new byte[448];
    private int timebase, tempo, ticksProduced;
    private int channelMask = 65535;
    private int sourceMode = 1, prefillElapsed, prefillBudget, prefillLength;

    public FueTrekMfiCommandCompiler(int version, int profile, Listener listener) {
        if (version < 1 || version > 5 || profile < 0 || profile > 1)
            throw new IllegalArgumentException("Invalid classic MFi version or tone profile");
        this.version = version;
        this.profile = profile;
        this.listener = listener;
        reset();
    }

    public void reset() {
        Arrays.fill(context, (byte)0);
        ticksProduced = 0;
        sourceMode = 1;
        prefillElapsed = prefillBudget = prefillLength = 0;
        channelMask = 65535;
        resetTone();
        resetContext();
    }

    private void resetTone() {
        Arrays.fill(tone, (byte)0);
        tone[0] = (byte)(profile == 1 ? 100 : 127);
        tone[1] = 64;
        put16(2, 48);
        put32(4, 60000000);
        put32(8, 125);
        put32(12, 10000);
        for (int channel = 0; channel < 16; channel++) {
            tone[16 + channel] = (byte)(profile == 1 ? 127 : 100);
            tone[32 + channel] = 127;
            tone[48 + channel] = 64;
            put16(64 + channel * 2, 0x4000);
            tone[96 + channel] = 2;
            int group = channel == 9 ? 0x78 : profile == 1 ? 0x7d : 0x79;
            tone[128 + channel] = tone[144 + channel] = (byte)group;
            put32(192 + channel * 4, 1);
            tone[256 + channel * 12 + 6] = 60;
            tone[256 + channel * 12 + 7] = 64;
        }
    }

    private void resetContext() {
        timebase = 48;
        tempo = 125;
        Arrays.fill(context, 0, 16, (byte)63);
        Arrays.fill(context, 16, 32, (byte)0);
        Arrays.fill(context, 32, 64, (byte)0);
        context[32 + 9] = context[48 + 9] = 1;
        //24bf0 deliberately retains the sixteen program bytes at1d0.
        Arrays.fill(context, 80, 128, (byte)32);
        context[128] = 100;
        put16(2, timebase);
        put32(4, 60000000);
        put32(8, tempo);
        put32(12, tickDurationMicros());
    }

    public int tickDurationMicros() { return FueTrekSequencerClock.tickDurationMicros(timebase, tempo); }
    public int timebase() { return timebase; }
    public int tempo() { return tempo; }
    public int version() { return version; }
    public void setChannelMask(int mask) { channelMask = mask & 65535; }

    /**14da0/24570: classic whole-file prefill starts with silent cache updates. */
    public void beginPrefill(int millis) {
        sourceMode = 0;
        prefillElapsed = prefillLength = 0;
        prefillBudget = millis * 1000;
        channelMask = 65535;
        resetTone();
        resetContext();
    }

    public void setSourceMode(int mode) { sourceMode = mode; }

    public record PrefillWait(boolean boundary, int ticks) {}

    /**14f90 uses unsigned32 arithmetic and leaves a boundary event unconsumed. */
    public PrefillWait prefillWait(int ticks) {
        int proposed = get32(12) * ticks + prefillElapsed;
        if (Integer.compareUnsigned(proposed, prefillBudget) < 0) {
            prefillElapsed = proposed;
            return new PrefillWait(false, ticks);
        }
        return new PrefillWait(true, Integer.divideUnsigned(prefillBudget - prefillElapsed, get32(12)));
    }

    /**15510/24b50: classic MFi resets its cache and retains frontend program bytes. */
    public void restartClassicSource() {
        resetTone();
        emit(18, 0, 0, 0);
        for (int channel = 0; channel < 16; channel++) listener.channelEnabled(channel, 1);
        ticksProduced = 0;
        resetContext();
        emit(16, 0, 0, tickDurationMicros());
    }

    /**15400 emits tempo unconditionally, followed by nondefault cached controls. */
    public void replayCache() {
        listener.command(16, 0, 0, get32(12));
        if ((tone[0] & 255) != (profile == 1 ? 100 : 127)) listener.command(14, 0, 0, tone[0] & 255);
        if (tone[1] != 64) listener.command(15, 0, 0, tone[1] & 255);
        for (int channel = 0; channel < 16; channel++) {
            int group = tone[128 + channel] & 255, program = tone[160 + channel] & 255;
            int normal = channel == 9 ? 0x78 : profile == 1 ? 0x7d : 0x79;
            if (group != normal || program != 0) {
                listener.command(5, channel, 0, (128 | group) << 8);
                listener.command(6, channel, 0, program);
            }
            if ((tone[16 + channel] & 255) != (profile == 1 ? 127 : 100)) listener.command(7, channel, 0, tone[16 + channel] & 255);
            if ((tone[32 + channel] & 255) != 127) listener.command(9, channel, 0, tone[32 + channel] & 255);
            if (tone[48 + channel] != 64) listener.command(8, channel, 0, tone[48 + channel] & 255);
            if (tone[176 + channel] != 0) listener.command(13, channel, 0, tone[176 + channel] & 255);
            if (tone[96 + channel] != 2) listener.command(12, channel, 0, tone[96 + channel] & 255);
            int pitch = (tone[64 + channel * 2] & 255) | (tone[65 + channel * 2] & 255) << 8;
            if (pitch != 0x4000) listener.command(11, channel, 0, pitch);
            if (tone[112 + channel] != 0) listener.command(10, channel, 0, tone[112 + channel] & 255);
            if (get32(192 + channel * 4) != 1) listener.channelEnabled(channel, get32(192 + channel * 4));
        }
    }

    public int[] prefillStateWords() {
        return new int[]{sourceMode, prefillElapsed, prefillBudget, prefillLength, channelMask};
    }

    private int value(int array, int channel) { return context[array + channel] & 255; }
    private void value(int array, int channel, int value) { context[array + channel] = (byte)value; }
    private void emit(int command, int channel, int delta, int argument) {
        if (sourceMode == 1) listener.command(command, channel, delta, argument);
    }
    public void nop(int delta) { emit(24, 0, delta, 0); ticksProduced += delta; }

    /** Native24fa0 emits an additional boundary command after finishing a source track. */
    public void endTrack(boolean instantaneousEnd) { emit(instantaneousEnd ? 22 : 24, 0, 0, 0); }

    /** Key is the decoded semitone offset from A4; velocity is the six-bit MFi code. */
    public void note(int delta, int channel, int key, int velocity, int gate) {
        if (channel < 0 || channel >= 16 || (gate == 0 && value(48, channel) == 0)
                || ((channelMask >>> channel) & 1) == 0) {
            nop(delta);
            return;
        }
        int note = (69 + key - (value(48, channel) == 0 ? 0 : 10)) & 255;
        int level = (velocity & 63) * 2 + 1;
        int remap = 256 + channel * 12;
        if (get32(remap) == 1) {
            int adjustment = ((tone[remap + 6] & 255) - note) * (tone[remap + 7] & 255) >> 6;
            if (tone[remap + 8] != 0) adjustment = -adjustment;
            level = Math.max(1, Math.min(127, level + adjustment));
        }
        emit(2, channel, delta, ((gate & 255) << 16) | (note << 8) | level);
        ticksProduced += delta;
    }

    /** Compile a raw ext-B opcode, before normalization changes its meaning or parameter bits. */
    public void extB(int track, int channel, int opcode, int parameter, int delta) {
        opcode &= 255;
        parameter &= 255;
        int six = parameter & 63;
        boolean lane = channel >= 0 && channel < 16;
        switch (opcode) {
            case 0xb0, 0xb1 -> {
                if (version == 1 || track != 0 || parameter >= 128) { nop(delta); return; }
                if (opcode == 0xb0) context[128] = (byte)parameter;
                tone[opcode == 0xb0 ? 0 : 1] = (byte)parameter;
                emit(opcode == 0xb0 ? 14 : 15, 0, delta, parameter);
            }
            case 0xba -> {
                if (version == 1 || track != 0 || parameter >= 128) { nop(delta); return; }
                int target = parameter >>> 3 & 15, family = Math.min(parameter & 7, 2);
                value(32, target, family);
                tone[144 + target] = (byte)bankGroup(value(16, target), family);
                emit(24, 0, delta, 0);
                if (family == 1) {
                    commitProgram(target, 0, programIndex(value(16, target), value(64, target)));
                    enable(target, bankEnabled(value(16, target), family));
                    value(48, target, family);
                }
            }
            case 0xbc -> {
                if (version == 1 || track != 0 || parameter >= 128) { nop(delta); return; }
                tempo = Math.max(20, Math.min(255, tempo - 64 + parameter));
                emitTempo(delta);
            }
            case 0xbd -> {
                if (version == 1 || track != 0 || parameter >= 128) { nop(delta); return; }
                int level = Math.max(0, Math.min(127, (context[128] & 255) - 64 + parameter));
                context[128] = tone[0] = (byte)level;
                emit(14, 0, delta, level);
            }
            case 0xbe -> {
                if (version == 1 || track != 0 || parameter != 0) { nop(delta); return; }
                emit(23, 0, delta, 0);
            }
            case 0xbf -> {
                if (version == 1 || track != 0) { nop(delta); return; }
                resetTone();
                emit(17, 0, delta, 0);
                if (sourceMode == 1)
                    for (int index = 0; index < 16; index++) listener.channelEnabled(index, 1);
                listener.componentReset();
                resetContext();
                emit(16, 0, 0, tickDurationMicros());
            }
            case 0xe0 -> {
                if (!lane) { nop(delta); return; }
                value(64, channel, six);
                commitProgram(channel, delta, programIndex(value(16, channel), six));
                enable(channel, bankEnabled(value(16, channel), value(32, channel)));
                value(48, channel, value(32, channel));
            }
            case 0xe1 -> {
                if (version == 1 || !lane) { nop(delta); return; }
                value(16, channel, six);
                int family = value(32, channel);
                tone[144 + channel] = (byte)bankGroup(six, family);
                emit(24, 0, delta, 0);
                if (family == 1) {
                    commitProgram(channel, 0, programIndex(six, value(64, channel)));
                    enable(channel, bankEnabled(six, family));
                }
            }
            case 0xe2, 0xe6 -> {
                if (!lane || (opcode == 0xe6 && version == 1)) { nop(delta); return; }
                int level = opcode == 0xe2 ? six : Math.max(0, Math.min(63, six - 32 + value(0, channel)));
                value(0, channel, level);
                tone[16 + channel] = (byte)(level * 2);
                emit(7, channel, delta, level * 2);
            }
            case 0xe3 -> {
                if (version == 1 || !lane) { nop(delta); return; }
                tone[48 + channel] = (byte)(six * 2);
                emit(8, channel, delta, six * 2);
            }
            case 0xe4, 0xe8 -> {
                if (version < 3 || !lane) { nop(delta); return; }
                if (opcode == 0xe4) value(80, channel, six);
                else value(112, channel, six);
                value(96, channel, value(112, channel));
                int pitch = Math.max(0, (value(80, channel) * 32 + value(112, channel)) * 8 - 256);
                int packed = ((pitch & 0x3f80) << 1) | (pitch & 127);
                put16(64 + channel * 2, packed);
                emit(11, channel, delta, packed);
            }
            case 0xe7 -> {
                if (version < 3 || !lane || six >= 25) { nop(delta); return; }
                tone[96 + channel] = (byte)six;
                emit(12, channel, delta, six);
            }
            case 0xe9 -> {
                if (version >= 3 && lane) value(112, channel, six);
                nop(delta);
                return;
            }
            case 0xea -> {
                if (version < 3 || !lane) { nop(delta); return; }
                tone[112 + channel] = (byte)(six * 2);
                emit(10, channel, delta, six * 2);
            }
            default -> {
                if (opcode >= 0xc0 && opcode <= 0xcf && track == 0 && (opcode & 7) != 7) {
                    timebase = ((opcode & 8) == 0 ? 6 : 15) << (opcode & 7);
                    tempo = Math.max(20, parameter);
                    emitTempo(delta);
                } else {
                    nop(delta);
                    return;
                }
            }
        }
        ticksProduced += delta;
    }

    private void emitTempo(int delta) {
        put16(2, timebase);
        put32(8, tempo);
        put32(12, tickDurationMicros());
        emit(16, 0, delta, tickDurationMicros());
    }

    private void commitProgram(int channel, int delta, int program) {
        tone[128 + channel] = tone[144 + channel];
        tone[160 + channel] = (byte)program;
        emit(5, channel, delta, (tone[128 + channel] & 255) << 8);
        emit(6, channel, 0, program & 255);
    }

    private void enable(int channel, boolean enabled) {
        put32(192 + channel * 4, enabled ? 1 : 0);
        if (sourceMode == 1) listener.channelEnabled(channel, enabled ? 1 : 0);
    }

    private int bankGroup(int bank, int family) {
        if (version == 1) return 0x7d;
        return switch (bank) {
            case 0, 1 -> family == 0 ? 0x7d : 0x78;
            case 0x34 -> family == 0 ? 0x79 : 0x14;
            case 0x36, 0x37 -> family == 0 ? 0x11 : 0x10;
            default -> family == 0 ? 0x79 : 0x78;
        };
    }

    private static int programIndex(int bank, int program) {
        return (bank == 0 ? Math.min(5, program) : program) + (bank & 1) * 64;
    }

    private boolean bankEnabled(int bank, int family) {
        if (family == 0) return bank <= 0x33 || bank == 0x36;
        return family == 1 && version >= 2 && (bank == 2 || bank == 0x34 || bank == 0x36
            || (bank >= 3 && bank <= 0x33 && (bank & 1) == 0));
    }

    private void put16(int offset, int value) {
        tone[offset] = (byte)value;
        tone[offset + 1] = (byte)(value >>> 8);
    }
    private void put32(int offset, int value) {
        put16(offset, value); put16(offset + 2, value >>> 16);
    }
    private int get32(int offset) {
        return (tone[offset] & 255) | ((tone[offset + 1] & 255) << 8)
            | ((tone[offset + 2] & 255) << 16) | (tone[offset + 3] << 24);
    }

    /** Raw context tempo bytes, frontend caches, tone state, and producer tick accumulator. */
    public byte[] stateBytes() {
        var state = ByteBuffer.allocate(589).order(ByteOrder.LITTLE_ENDIAN);
        state.putShort((short)timebase).put((byte)tempo).put(new byte[5]);
        state.put(context).put(tone).putInt(ticksProduced);
        return state.array();
    }
}
