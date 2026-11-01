package opendoja.audio.mld.fuetrek;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/** The SDK driver's 100 timed-note records, separate from the synth's 96 DSP voices. */
public final class FueTrekGateQueue {
    public interface Listener {
        void keyOn(int channel, int note, int velocity);
        void keyOff(int channel, int note);
        void sustain(int channel, int value);
    }

    private static final int[] CHANNEL_PRIORITY = {
        0x62,0x5f,0x5c,0x58,0x55,0x52,0x4f,0x4c,0x49,0x68,0x43,0x40,0x3d,0x3a,0x38,0x35
    };
    private static final class Gate {
        boolean active;
        int channel, note, transpose, velocity, ticks;
        boolean held;
    }

    private final Gate[] gates = new Gate[100];
    private final boolean[] percussion = new boolean[16];
    private final boolean[] sustain = new boolean[16];
    private final Listener listener;
    private int transpose;

    public FueTrekGateQueue(Listener listener) {
        this.listener = listener;
        for (int i = 0; i < gates.length; i++) gates[i] = new Gate();
        reset();
    }

    /** Native0fa40 clears activity and channel flags, retaining inactive record data. */
    public void reset() {
        for (Gate gate : gates) gate.active = false;
        Arrays.fill(percussion, false);
        percussion[9] = true;
        Arrays.fill(sustain, false);
    }

    public void setTranspose(int semitones) {
        transpose = semitones;
    }

    public void setPercussion(int channel, boolean enabled) {
        percussion[channel] = enabled;
    }

    /** Native0fdf0 updates only the gate when this raw channel/note is already active. */
    public void noteOn(int channel, int note, int velocity, int ticks) {
        startNote(channel, note, velocity, ticks, true);
    }

    /** Format0 explicit key-down allocates a record even when that raw key is already active. */
    public void noteOnExplicit(int channel, int note, int velocity) {
        startNote(channel, note, velocity, -1, false);
    }

    private void startNote(int channel, int note, int velocity, int ticks, boolean refresh) {
        note &= 255;
        velocity &= 255;
        for (Gate gate : gates) {
            if (refresh && gate.active && gate.channel == channel && gate.note == note) {
                gate.ticks = ticks;
                return;
            }
        }
        Gate selected = null;
        for (Gate gate : gates) {
            if (!gate.active) {
                selected = gate;
                break;
            }
        }
        if (selected == null) {
            int priority = priority(channel, velocity, false);
            for (Gate gate : gates) {
                int candidate = priority(gate.channel, gate.velocity, gate.held);
                if (candidate <= priority) {
                    selected = gate;
                    priority = candidate;
                }
            }
            if (selected == null) return;
            noteOff(selected.channel, selected.note);
        }
        selected.active = true;
        selected.channel = channel;
        selected.note = note;
        selected.transpose = transpose;
        selected.velocity = velocity;
        selected.ticks = ticks;
        selected.held = false;
        int actual = outputNote(note, transpose, percussion[channel]);
        if (percussion[channel]) listener.keyOff(channel, actual);
        listener.keyOn(channel, actual, velocity);
    }

    private static int priority(int channel, int velocity, boolean held) {
        int result = CHANNEL_PRIORITY[channel] * 256 + velocity;
        return held ? result >>> 2 : result;
    }

    private static int outputNote(int note, int transpose, boolean percussion) {
        if (percussion) return note;
        int result = note + transpose;
        while (result < 21) result += 12;
        while (result > 120) result -= 12;
        return result;
    }

    public void noteOff(int channel, int note) {
        note &= 255;
        for (Gate gate : gates) {
            if (!gate.active || gate.channel != channel || gate.note != note) continue;
            if (sustain[channel]) {
                gate.held = true;
            } else {
                gate.active = false;
                if (!percussion[channel])
                    listener.keyOff(channel, outputNote(note, gate.transpose, false));
            }
        }
    }

    /** Native E850 sends the synth controller before0ff90 updates its own gate flags. */
    public void sustain(int channel, int value) {
        listener.sustain(channel, value);
        // This driver treats any nonzero byte as down; SynthModule itself uses 64.
        sustain[channel] = (value & 255) != 0;
        if (!sustain[channel]) {
            // Native0ff90 deliberately scans held records from every channel,
            // and sends key-up even for percussion records.
            for (Gate gate : gates) {
                if (!gate.active || !gate.held) continue;
                gate.active = false;
                gate.held = false;
                listener.keyOff(gate.channel, outputNote(gate.note, gate.transpose, percussion[gate.channel]));
            }
        }
    }

    /** Native100c0 checks expiry independently of the explicit key-up hold latch. */
    public void retire(int elapsedTicks) {
        for (Gate gate : gates) {
            if (gate.active && Integer.compareUnsigned(gate.ticks, elapsedTicks) <= 0) {
                if (!percussion[gate.channel])
                    listener.keyOff(gate.channel, outputNote(gate.note, gate.transpose, false));
                gate.active = false;
            }
        }
    }

    public void advance(int elapsedTicks) {
        for (Gate gate : gates) {
            if (gate.active) gate.ticks = Integer.compareUnsigned(elapsedTicks, gate.ticks) < 0
                    ? gate.ticks - elapsedTicks : 0;
        }
    }

    public long ticksUntilNext() {
        long result = -1;
        for (Gate gate : gates) {
            long ticks = Integer.toUnsignedLong(gate.ticks);
            if (gate.active && (result < 0 || ticks < result)) result = ticks;
        }
        return result;
    }

    /** Native20-byte record layout, followed by channel flags and port transpose. */
    public byte[] stateBytes() {
        var result = ByteBuffer.allocate(2036).order(ByteOrder.LITTLE_ENDIAN);
        for (Gate gate : gates) {
            result.put((byte)(gate.active ? 1 : 0)).put((byte)gate.channel).put((byte)gate.note).put((byte)0);
            result.putInt(gate.transpose).put((byte)gate.velocity).put(new byte[3]);
            result.putInt(gate.ticks).put((byte)(gate.held ? 1 : 0)).put(new byte[3]);
        }
        for (boolean value : percussion) result.put((byte)(value ? 1 : 0));
        for (boolean value : sustain) result.put((byte)(value ? 1 : 0));
        result.putInt(transpose);
        return result.array();
    }
}
