package opendoja.audio.mld.fuetrek;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/** Main SDK28be0 command transport, including its unchecked circular byte writer. */
public final class FueTrekCommandSerializer {
    private final byte[] ring;
    private final int reserve;
    private final int resetControlsCapability;
    private final int holdCapability;
    private final int[] enabled = new int[20];
    private final int[] heldChannels = new int[20];
    private final byte[] activeKeys = new byte[260];
    private final byte[] heldKeys = new byte[260];
    private int writePosition, readPosition;
    private int mode, deferredTicks, channelTag;
    private int scale = 65536, fraction;

    public FueTrekCommandSerializer(int capacity, int reserve, int resetControlsCapability, int holdCapability) {
        if (capacity < 0) throw new IllegalArgumentException("Negative command-ring capacity");
        ring = new byte[capacity];
        this.reserve = reserve;
        this.resetControlsCapability = resetControlsCapability;
        this.holdCapability = holdCapability;
        Arrays.fill(enabled, 1);
    }

    public void setMode(int mode) { this.mode = mode; }

    /**179f0 clears only the ring cursors; cached controls and backing bytes survive. */
    void clearOutput() { writePosition = readPosition = 0; }

    public void setChannelEnabled(int channel, int value) { enabled[channel] = value; }

    /** Transport input: move the consumer's circular cursor, without changing command state. */
    public void advanceReader(int bytes) {
        if (ring.length != 0)
            readPosition = (int)((readPosition + Integer.toUnsignedLong(bytes)) % ring.length);
    }

    public int bufferStatus() {
        int free = readPosition == writePosition ? ring.length
            : writePosition < readPosition ? readPosition - writePosition
            : ring.length - writePosition + readPosition;
        if (free == 0) return 3;
        if (Integer.compareUnsigned(free, reserve) <= 0) return 2;
        return free == ring.length ? 0 : 1;
    }

    /** Native cursor convention treats equal cursors as an empty output ring. */
    public int readableBytes() {
        return writePosition >= readPosition ? writePosition - readPosition
            : ring.length - readPosition + writePosition;
    }

    /** Copy available command bytes across the circular boundary and advance the consumer. */
    public int readInto(byte[] destination, int offset, int length) {
        java.util.Objects.checkFromIndexSize(offset, length, destination.length);
        int used = Math.min(length, readableBytes());
        for (int index = 0; index < used; index++) {
            destination[offset + index] = ring[readPosition++];
            if (readPosition == ring.length) readPosition = 0;
        }
        return used;
    }

    /** Native09de0 splits at the circular boundary, including a zero-length second write. */
    int writeTo(FueTrekSynthTransport.Port port, int length) {
        int used = Math.min(length, readableBytes());
        if (readPosition + used >= ring.length) {
            int first = ring.length - readPosition;
            port.writeBytes(ring, readPosition, first);
            port.writeBytes(ring, 0, used - first);
            readPosition = used - first;
        } else {
            port.writeBytes(ring, readPosition, used);
            readPosition += used;
        }
        return used;
    }

    /** Serialize one native four-word command; returns buffer status from BEFORE the write. */
    public int serialize(int command, int channel, int deltaTicks, int value) {
        int status = bufferStatus();
        if (Integer.compareUnsigned(command, 26) >= 0 || status == 3) return status;
        if (command == 24 && Integer.compareUnsigned(deferredTicks, 65536) <= 0) {
            deferredTicks += deltaTicks;
            return status;
        }
        channel &= 255;
        int tag = channel >>> 4;
        if (tag != channelTag) {
            vlq(0);
            bytes(7, channel);
        }
        channelTag = tag;
        int delta = deltaTicks + deferredTicks;
        if (scale != 65536) {
            int low = (delta & 65535) * scale + fraction;
            fraction = low & 65535;
            delta = (delta >>> 16) * scale + (low >>> 16);
        }
        vlq(delta);
        deferredTicks = 0;
        encode(command, channel & 15, value);
        return status;
    }

    private void encode(int command, int channel, int value) {
        int note = (value >>> 8) & 255;
        switch (command) {
            case 0 -> {
                if (!validNote(note)) { nop(); break; }
                keyBit(activeKeys, channel, note, false);
                if (heldChannels[channel] != 0) keyBit(heldKeys, channel, note, true);
                if (holdCapability == 0 && heldChannels[channel] != 0) nop();
                else bytes(channel | 0x80, note, value);
            }
            case 1 -> {
                if (!validNote(note) || enabled[channel] == 0) { nop(); break; }
                if (holdCapability == 0 && heldChannels[channel] != 0 && keyBit(heldKeys, channel, note))
                    bytes(channel | 0x80, note, 0, 0);
                keyBit(activeKeys, channel, note, true);
                keyBit(heldKeys, channel, note, false);
                bytes(channel | 0x90, note, value);
            }
            case 2, 3 -> {
                if (!validNote(note) || enabled[channel] == 0) { nop(); break; }
                int gate = (value >>> 16) & 255;
                if (command == 2 && gate == 0) gate = 1;
                bytes(channel | 0x90, note, value, gate);
            }
            case 4 -> {
                int group = (value >>> 8) & 127;
                boolean percussion = group == 0x78 || group == 0x14
                    || (group != 0x7d && group != 0x79 && channel == 9);
                bytes(channel | 0x10, value >>> 8, value, 0, channel | 0xf0, 7, percussion ? 1 : 0,
                    0, channel | 0x20, value >>> 16);
                enabled[channel] = value >>> 24;
            }
            case 5 -> {
                int group = (value >>> 8) & 127;
                boolean percussion = group == 0x78 || group == 0x14 || group == 0x10
                    || (group != 0x7d && group != 0x79 && group != 0x11 && channel == 9);
                bytes(channel | 0x10, value >>> 8, value, 0, channel | 0xf0, 7, percussion ? 1 : 0);
            }
            case 6 -> bytes(channel | 0x20, value);
            case 7 -> bytes(channel | 0x30, value);
            case 8 -> bytes(channel | 0x40, value);
            case 9 -> bytes(channel | 0x50, value);
            case 10 -> bytes(channel | 0x70, value);
            case 11 -> bytes(channel | 0xe0, value >>> 8, value);
            case 12 -> {
                if ((value & 255) < 25) bytes(channel | 0xf0, 0, value);
                else nop();
            }
            case 13 -> {
                boolean down = (value & 255) != 0;
                heldChannels[channel] = down ? 1 : 0;
                if (holdCapability == 0) {
                    if (!down) flushHeld(channel, false);
                    nop();
                } else bytes(channel | 0x60, down ? 1 : 0);
                if (!down) clearChannel(heldKeys, channel);
            }
            case 14 -> bytes(1, value);
            case 15 -> bytes(2, value);
            case 16 -> {
                int tempo = value;
                if (Integer.compareUnsigned(tempo, 500000) > 0) tempo = 500000;
                if (Integer.compareUnsigned(tempo, 100) < 0) tempo = 100;
                if (tempo < 2000) {
                    scale = (tempo << 16) / 2000;
                    tempo = 2000;
                } else scale = 65536;
                fraction = 0;
                bytes(0xb0, tempo >>> 16, tempo >>> 8, tempo);
            }
            case 17 -> {
                Arrays.fill(heldChannels, 0);
                Arrays.fill(activeKeys, (byte)0);
                Arrays.fill(heldKeys, (byte)0);
                bytes(0, 0, 0, channel | 0xf0, 10, 0x3f);
            }
            case 18 -> {
                Arrays.fill(heldChannels, 0);
                bytes(0, 0);
            }
            case 19 -> {
                if (heldChannels[channel] != 0) {
                    if (holdCapability == 0) flushHeld(channel, true);
                    clearChannel(heldKeys, channel);
                }
                heldChannels[channel] = 0;
                if (resetControlsCapability == 1) bytes(channel | 0xf0, 8, 0);
                else bytes(channel | 0x50, 127, 0, channel | 0x70, 0,
                    0, channel | 0xf0, 0, 2, 0, channel | 0xe0, 0x40, 0,
                    0, channel | 0x60, 0);
            }
            case 20 -> {
                clearChannel(activeKeys, channel);
                clearChannel(heldKeys, channel);
                bytes(channel | 0xf0, 10, value);
            }
            case 21 -> {
                // Native29660 clears the active mask before its software-hold
                // scan, so that scan cannot add any newly held keys.
                clearChannel(activeKeys, channel);
                if (holdCapability != 0 || heldChannels[channel] == 0) {
                    bytes(channel | 0xf0, 9, value);
                } else {
                    nop();
                }
            }
            case 22, 23 -> {
                Arrays.fill(activeKeys, (byte)0);
                Arrays.fill(heldKeys, (byte)0);
                bytes(0xf0, 10, command == 23 && mode != 0 ? 0x7f : 0x3f);
            }
            case 24 -> nop();
            case 25 -> bytes(0xc1, value & 127);
            default -> throw new AssertionError("Unknown serializer command");
        }
    }

    private void flushHeld(int channel, boolean resetBug) {
        // The native software-hold scan stops before note120. Reset19 also
        // shifts an 8-bit register by8, so its emitted note byte is always0.
        for (int note = 21; note < 120; note++)
            if (keyBit(heldKeys, channel, note)) bytes(channel | 0x80, resetBug ? 0 : note, 0, 0);
    }

    private static boolean validNote(int note) { return note > 20 && note < 121; }

    private static boolean keyBit(byte[] keys, int channel, int note) {
        int bit = note - 21;
        return (keys[channel * 13 + (bit >>> 3)] & (1 << (bit & 7))) != 0;
    }

    private static void keyBit(byte[] keys, int channel, int note, boolean value) {
        int bit = note - 21, index = channel * 13 + (bit >>> 3), mask = 1 << (bit & 7);
        keys[index] = (byte)(value ? keys[index] | mask : keys[index] & ~mask);
    }

    private static void clearChannel(byte[] keys, int channel) {
        Arrays.fill(keys, channel * 13, channel * 13 + 13, (byte)0);
    }

    private void nop() { bytes(0xd0); }

    private void vlq(int value) {
        // Native29820 emits at most four bytes, discarding bits above bit27.
        if (Integer.compareUnsigned(value, 0x001fffff) > 0) bytes((value >>> 21) | 128);
        if (Integer.compareUnsigned(value, 0x00003fff) > 0) bytes((value >>> 14) | 128);
        if (Integer.compareUnsigned(value, 127) > 0) bytes((value >>> 7) | 128);
        bytes(value & 127);
    }

    private void bytes(int... values) {
        for (int value : values) {
            ring[writePosition++] = (byte)value;
            if (writePosition == ring.length) writePosition = 0;
        }
    }

    /** Circular-buffer words, portable control state, and the complete ring backing bytes. */
    public byte[] stateBytes() {
        var result = ByteBuffer.allocate(716 + ring.length).order(ByteOrder.LITTLE_ENDIAN);
        result.putInt(ring.length).putInt(reserve).putInt(writePosition).putInt(readPosition);
        result.putInt(mode).putInt(deferredTicks).putInt(channelTag);
        for (int value : enabled) result.putInt(value);
        for (int value : heldChannels) result.putInt(value);
        result.put(activeKeys).put(heldKeys).putInt(scale).putInt(fraction).put(ring);
        return result.array();
    }
}
