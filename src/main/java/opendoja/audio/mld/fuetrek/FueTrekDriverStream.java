package opendoja.audio.mld.fuetrek;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/** SDK port0 byte consumer (E7D0/E850/DD20), including incomplete packets and timed gates. */
public final class FueTrekDriverStream implements FueTrekSequencerClock.Source, FueTrekSynthTransport.Port {
    /** Entry is the SynthModule interface byte offset; arguments omit module and port handles. */
    public interface Listener {
        void moduleCall(int entry, int... arguments);
    }

    private final byte[] buffer;
    private int count;
    private int format;
    private final FueTrekSequencerClock clock;
    private final FueTrekGateQueue gates;
    private final Listener listener;
    private final byte[] banks = new byte[16];
    private final int[] enabled = new int[16];
    private int skip, tempoScratch, bankWord, bankFlag, a1Word, a1Flag, a2Word, marker;
    private int hostVolume, hostOther;
    private boolean controlledPort;
    private int controlState, previousControlState, portActive;
    private int interruptSignal, portStatus = 2, freeSpaceRegister = 255;
    private int globalInterruptSignal, globalPending;
    private final byte[] portRegisters = new byte[16];

    public FueTrekDriverStream(int capacity, int format, int tickMicros, int divisor,
                              int positionQuantumMicros, Listener listener) {
        if (capacity < 0 || capacity > 5000 || format < 0 || format > 3)
            throw new IllegalArgumentException("Invalid SDK driver buffer or note format");
        buffer = new byte[capacity];
        this.format = format;
        this.listener = listener;
        clock = new FueTrekSequencerClock(tickMicros, divisor, positionQuantumMicros);
        gates = new FueTrekGateQueue(new FueTrekGateQueue.Listener() {
            public void keyOn(int channel, int note, int velocity) { call(0x24, channel, note, velocity); }
            public void keyOff(int channel, int note) { call(0x28, channel, note); }
            public void sustain(int channel, int value) { call(0x48, channel, value); }
        });
        Arrays.fill(enabled, 1);
        Arrays.fill(banks, (byte)0x7d);
    }

    private void call(int entry, int... args) { listener.moduleCall(entry, args); }

    /** Native0b8a0(3)/c130 initializes four512-byte synth ports; this instance is port0. */
    public static FueTrekDriverStream initializedPort(Listener listener) {
        var port = new FueTrekDriverStream(512, 0, 1, 1, 2500, listener);
        port.controlledPort = true;
        port.hostVolume = 100;
        port.clock.initializePort();
        port.portRegisters[3] = 0x30;
        port.portRegisters[13] = 0x0f;
        port.portRegisters[14] = 0x2f;
        Arrays.fill(port.banks, (byte)0);
        Arrays.fill(port.enabled, 0);
        for (int channel = 0; channel < 16; channel++) port.gates.setPercussion(channel, false);
        return port;
    }

    private void signalInterrupt(int bits) {
        interruptSignal |= bits;
        globalInterruptSignal |= 16;
        globalPending |= 2;
    }

    private void publishFifoStatus() {
        freeSpaceRegister = Math.min(255, buffer.length - count);
        int flags = count == 0 ? 2 : count < 256 ? 4 : 0;
        if (count == buffer.length) flags |= 8;
        portStatus = portStatus & 0xf1 | flags;
    }

    private void resetTimedChannels() {
        gates.reset();
        Arrays.fill(enabled, 1);
        Arrays.fill(banks, (byte)0x79);
        banks[9] = (byte)0x78;
    }

    /** Ordinary register bank, port0 only. Reading10 acknowledges the latched IRQ. */
    @Override
    public int readRegister(int address) {
        if (!controlledPort) throw new IllegalStateException("Register I/O requires initializedPort");
        address &= 255;
        return switch (address) {
            case 6 -> globalInterruptSignal;
            case 0xf0 -> globalPending;
            case 0x10 -> {
                int value = interruptSignal;
                interruptSignal = 0;
                globalInterruptSignal &= ~16;
                if (globalInterruptSignal == 0) globalPending &= ~2;
                yield value;
            }
            case 0x12 -> portStatus;
            case 0x14 -> freeSpaceRegister;
            case 0x15 -> 0;
            case 0x17 -> {
                int value = portRegisters[7] & 255;
                portRegisters[7] &= 63;
                yield value;
            }
            case 0x1a -> marker;
            default -> {
                if (address < 0x10 || address > 0x1f)
                    throw new IllegalArgumentException("Unsupported port0 register");
                yield portRegisters[address - 0x10] & 255;
            }
        };
    }

    /** Native C7B0 polls the OLD control before storing a write to11. */
    public void writeRegister(int address, int value, int nowMillis) {
        if (!controlledPort) throw new IllegalStateException("Register I/O requires initializedPort");
        address &= 255;
        value &= 255;
        if (address == 6) return;
        if (address < 0x10 || address > 0x1f)
            throw new IllegalArgumentException("Unsupported port0 register");
        switch (address) {
            case 0x10, 0x12, 0x14, 0x1d -> { return; }
            case 0x11 -> pollPort(nowMillis, false);
            case 0x13 -> {
                if ((value & 1) != 0) {
                    clearPortFifo();
                    value &= 254;
                }
            }
            case 0x1a -> marker = value;
        }
        portRegisters[address - 0x10] = (byte)value;
    }

    private void clearPortFifo() {
        Arrays.fill(buffer, (byte)0);
        count = 0;
        publishFifoStatus();
        signalInterrupt(2);
    }

    private void publishPosition(int units) {
        portRegisters[7] = (byte)((portRegisters[7] & 0xc0) | (units >>> 16 & 15));
        portRegisters[8] = (byte)(units >>> 8);
        portRegisters[9] = (byte)units;
    }

    /** Poll the current register values at the emulated callback boundary. */
    public void pollPort(int nowMillis, boolean poll) {
        pollPortControl(portRegisters[1], portRegisters[11], nowMillis, poll);
    }

    /** Native0ced0 bulk writes to port0's command FIFO (register15). */
    @Override
    public void writeBytes(byte[] bytes, int offset, int length) { offer(bytes, offset, length); }

    /** Non-destructive register snapshot for compatibility diagnostics. */
    public byte[] portRegisterBytes() {
        byte[] result = portRegisters.clone();
        result[0] = (byte)interruptSignal;
        result[2] = (byte)portStatus;
        result[4] = (byte)freeSpaceRegister;
        result[10] = (byte)marker;
        return result;
    }

    /** Native0d8c0, ordinary SynthModule path; register and byte writes are explicit inputs. */
    public void pollPortControl(int controlRegister, int tempoRegister, int nowMillis, boolean poll) {
        if (!controlledPort) throw new IllegalStateException("Port control requires initializedPort");
        controlRegister &= 255;
        tempoRegister &= 255;
        portRegisters[1] = (byte)controlRegister;
        portRegisters[11] = (byte)tempoRegister;
        previousControlState = controlState;
        controlState = controlRegister >>> 3 & 3;
        format = controlRegister >>> 6;
        if (format == 1) format = 0;
        int divisor = tempoRegister < 20 ? 20 : tempoRegister < 200 ? tempoRegister
                : tempoRegister < 250 ? tempoRegister * 4 - 600 : 400;
        clock.setTimeDivisor(divisor);
        if ((portStatus >>> 6) != controlState) signalInterrupt(128);
        portStatus = portStatus & 63 | controlState << 6;
        if (controlState == 0) {
            if (previousControlState != 0) {
                portStatus &= 254;
                signalInterrupt(1);
                clearPortFifo();
                if (clock.elapsedMicros() != 0 && hostOther == 0) {
                    gates.setTranspose(0);
                    hostVolume = 100;
                }
                clock.stopPort();
                publishPosition(0);
                for (int channel = 0; channel < 16; channel++) call(0x58, channel);
                call(0x20, 0);
                portActive = 0;
            }
        } else if (controlState == 1) {
            if (previousControlState != 1)
                for (int channel = 0; channel < 16; channel++) call(0x58, channel);
            clock.pausePort(nowMillis);
        } else {
            if (previousControlState == 0) {
                marker = skip = 0;
                hostOther = 0;
                clock.startPort(format == 0 ? 1000 : 10000);
                publishPosition(0);
                resetTimedChannels();
                if (format == 2) {
                    Arrays.fill(banks, (byte)0x7d);
                    for (int channel = 0; channel < 16; channel++) call(0x2c, channel, 0x7d, 0, 0, 1);
                }
                portActive = 1;
            }
            if (poll) {
                advance(nowMillis);
                if (count != 0) portStatus |= 1;
                else {
                    if ((portStatus & 1) != 0) signalInterrupt(1);
                    portStatus &= 254;
                }
            }
        }
        publishFifoStatus();
    }

    public int[] portStateWords() {
        return new int[]{controlState, previousControlState, format, portActive,
                interruptSignal, portStatus, freeSpaceRegister, globalInterruptSignal, globalPending};
    }

    /** Native D4D0 accepts only available capacity and drops the rest of this write. */
    public int offer(byte[] bytes, int offset, int length) {
        java.util.Objects.checkFromIndexSize(offset, length, bytes.length);
        int accepted = Math.min(length, buffer.length - count);
        System.arraycopy(bytes, offset, buffer, count, accepted);
        count += accepted;
        if (controlledPort) {
            publishFifoStatus();
            if (count == buffer.length) signalInterrupt(8);
        }
        return accepted;
    }

    public int bufferedBytes() { return count; }

    public int remainingCapacity() { return buffer.length - count; }

    /** Event-driven bridge: the caller already supplied delivery time and gate ticks. */
    public void dispatchBufferedWithoutClock() {
        while (count != 0) {
            if (readDeltaTicks() == null || !dispatch())
                throw new IllegalStateException("Incomplete event-driven driver packet");
        }
    }

    public void retireGates(int ticks) { gates.retire(ticks); }
    public void advanceGates(int ticks) { gates.advance(ticks); }
    public long ticksUntilNextGate() { return gates.ticksUntilNext(); }
    public byte[] noteGateStateBytes() { return gates.stateBytes(); }

    private void consume(int length) {
        int previousCount = count;
        System.arraycopy(buffer, length, buffer, 0, count - length);
        Arrays.fill(buffer, count - length, count, (byte)0);
        count -= length;
        if (controlledPort && length > 0) {
            publishFifoStatus();
            if (previousCount >= 256 && count < 256) signalInterrupt(4);
            if (count == 0) signalInterrupt(2);
        }
    }

    @Override
    public Integer readDeltaTicks() {
        int ticks = 0;
        for (int index = 0; index < count; index++) {
            int value = buffer[index] & 255;
            ticks = (ticks << 7) | (value & 127);
            if ((value & 128) == 0) {
                consume(index + 1);
                return ticks;
            }
        }
        return null;
    }

    /** Native profile5's A1 layout is selected; no Windows MIDI-out transport is involved. */
    @Override
    public boolean dispatch() {
        if (skip != 0) {
            int used = Math.min(skip, count);
            skip -= used;
            consume(used);
            return skip == 0;
        }
        if (count == 0) return false;
        int opcode = buffer[0] & 255;
        int kind = packetKind(opcode), payload = payloadLength(kind);
        if (payload < 0) return false;
        if (kind == 0x90 && format == 0) payload = 2;
        int first = 0, second = 0, gate = 0;
        for (int index = 0; index < payload; index++) {
            if (index + 1 >= count) return false;
            int value = buffer[index + 1] & 255;
            if (index == 0) first = value;
            else if (index == 1) second = value;
            switch (kind) {
                case 0x10 -> {
                    if (index == 0) { bankWord = value & 127; bankFlag = value >>> 7; }
                    else bankWord = (bankWord + value * 128) & 65535;
                }
                case 0x90 -> {
                    if (index == 2) {
                        gate = value;
                        if (format == 3 && (value & 128) != 0) {
                            gate = ((value & 127) + gate) * 128;
                            payload++;
                        }
                    } else if ((index == 3 || index == 4) && format == 3 && (value & 128) != 0) {
                        gate = ((value & 127) + gate) * 128;
                        payload++;
                    } else if (index == 5) gate = (value + gate) * 128;
                }
                case 0xa1 -> {
                    if (index == 0) {
                        a1Word = (value & 1) << 8;
                        a1Flag = value >>> 7;
                        payload += a1Flag == 1 ? 9 : 1;
                    } else if (index == 1) a1Word |= value;
                }
                case 0xa2 -> {
                    if (index == 0) a2Word = value;
                    else if (index == 1) a2Word = (a2Word * 256 + value) & 65535;
                    else if (index == 2) skip = value;
                }
                case 0xb0 -> {
                    tempoScratch = index == 0 ? value : tempoScratch * 256 + value;
                    if (index == 2) clock.setTickMicros(tempoScratch);
                }
                case 0xc1 -> {
                    marker = value;
                    if (controlledPort) signalInterrupt(32);
                }
            }
        }
        int channel = opcode & 15;
        switch (kind) {
            case 0 -> {
                call(0x20, 0);
                resetTimedChannels();
            }
            case 1 -> call(0x64, first);
            case 2 -> call(0x68, first);
            case 0x10 -> {
                banks[channel] = (byte)first;
                call(0x2c, channel, first, 0, 0, 1);
            }
            case 0x20 -> {
                enabled[channel] = banks[channel] == 0x7d && first > 5 ? 0 : 1;
                // The native program handler sign-extends its cached bank byte.
                call(0x2c, channel, banks[channel], 0, first, 1);
            }
            case 0x30 -> call(0x30, channel, first);
            case 0x40 -> call(0x34, channel, first);
            case 0x50 -> call(0x38, channel, first);
            case 0x60 -> gates.sustain(channel, first);
            case 0x70 -> call(0x54, channel, first);
            case 0x80 -> gates.noteOff(channel, first);
            case 0x90 -> {
                if (format == 0) gates.noteOnExplicit(channel, first, second);
                else if (enabled[channel] != 0) gates.noteOn(channel, first, second, gate);
            }
            case 0xcc -> {
                // This consumer represents port0. Other ports have their own context.
                if ((first >>> 4) == 0) {
                    switch (first & 15) {
                        case 0 -> gates.setTranspose((byte)second);
                        case 1 -> { hostVolume = second; call(0x10, 1, second * 127 / 100); }
                        case 2 -> hostOther = second;
                    }
                }
            }
            case 0xe0 -> call(0x4c, channel, first * 128 + second);
            case 0xf0 -> {
                switch (first) {
                    case 0 -> call(0x50, channel, second);
                    case 7 -> {
                        gates.setPercussion(channel, (second & 1) != 0);
                        int bank = banks[channel] & 255;
                        if (bank != 0x11 && bank != 0x14 && bank != 0x7d)
                            banks[channel] = (byte)((~second & 1) | 0x78);
                        call(0x2c, channel, banks[channel] & 255, 0, 0, 1);
                    }
                    case 8 -> call(0x60, channel);
                    case 9 -> call(0x5c, channel);
                    case 10 -> call(0x58, channel);
                    default -> {
                        if ((first & 15) == 6 && first < 0x80)
                            call(0x1c, 0, channel, (first >>> 4) + 3);
                    }
                }
            }
        }
        int used = payload + 1;
        if (kind == 0xa2) {
            int available = Math.min(skip, count - used);
            skip -= available;
            used += available;
        }
        consume(used);
        return skip == 0;
    }

    private static int packetKind(int opcode) {
        if (opcode <= 7 || (opcode >= 0xa0 && opcode <= 0xa4)
                || opcode == 0xc1 || opcode == 0xc2 || (opcode >= 0xc5 && opcode <= 0xc8)
                || opcode == 0xcc || opcode == 0xcd || opcode == 0xcf) return opcode;
        int group = opcode & 0xf0;
        return group >= 0x10 && group <= 0x90 || group == 0xb0 || group >= 0xd0 ? group : -1;
    }

    private static int payloadLength(int kind) {
        return switch (kind) {
            case 0,1,2,3,4,5,6,7,0x20,0x30,0x40,0x50,0x60,0x70,
                0xc1,0xc2,0xc5,0xc6,0xc7 -> 1;
            case 0x10,0x80,0xa0,0xa4,0xcc,0xcd,0xe0,0xf0 -> 2;
            case 0x90,0xa2,0xa3,0xb0,0xc8 -> 3;
            case 0xa1 -> 25;
            case 0xcf -> 4;
            case 0xd0 -> 0;
            default -> -1;
        };
    }

    public void advance(int nowMillis) {
        clock.advance(nowMillis, this, new FueTrekSequencerClock.Listener() {
            public void elapsedTicks(int ticks, boolean commit) {
                if (commit) gates.advance(ticks);
                else gates.retire(ticks);
            }
            public void command(int token) { throw new AssertionError("Streaming driver expected"); }
            public void positionUpdated(int units) { if (controlledPort) publishPosition(units); }
            public void positionWrapped() {
                if (controlledPort) {
                    portRegisters[7] |= (byte)0x80;
                    signalInterrupt(16);
                }
            }
        });
    }

    /** Portable state, followed by unread FIFO bytes; does not contain host pointers. */
    public byte[] stateBytes() {
        var state = ByteBuffer.allocate(2196 + count).order(ByteOrder.LITTLE_ENDIAN);
        for (int word : clock.stateWords()) state.putInt(word);
        state.putInt(skip).putInt(tempoScratch).putInt(bankWord).putInt(bankFlag)
            .putInt(a1Word).putInt(a1Flag).putInt(a2Word).putInt(marker)
            .putInt(hostVolume).putInt(hostOther).put(banks);
        for (int word : enabled) state.putInt(word);
        state.put(gates.stateBytes()).putInt(count).put(buffer, 0, count);
        return state.array();
    }
}
