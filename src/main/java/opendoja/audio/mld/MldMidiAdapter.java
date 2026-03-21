package opendoja.audio.mld;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class MldMidiAdapter {
    private static final int FOURCC_MELO = 0x6D656C6F;
    private static final int FOURCC_TRAC = 0x74726163;
    private static final int FOURCC_CUEP = 0x63756570;
    private static final int FOURCC_NOTE = 0x6E6F7465;
    private static final int EVENT_TYPE_NOTE = 0;
    private static final int EVENT_TYPE_EXT_B = 1;
    private static final int EVENT_END_OF_TRACK = 0xDF;
    private static final int EVENT_TIMEBASE_TEMPO = 0xC0;
    private static final int EVENT_PROGRAM_CHANGE = 0xE0;
    private static final int EVENT_BANK_CHANGE = 0xE1;
    private static final int EVENT_VOLUME = 0xE2;
    private static final int EVENT_PANPOT = 0xE3;
    private static final int EVENT_PITCHBEND = 0xE4;

    private MldMidiAdapter() {
    }

    public static Sequence toMidiSequence(byte[] bytes) throws IOException, InvalidMidiDataException {
        ParsedMld parsed = new ParsedMld(bytes).parse();
        Sequence sequence = new Sequence(Sequence.PPQ, 384);
        Track tempoTrack = sequence.createTrack();
        addTempo(tempoTrack, 0L, 120);
        for (int trackIndex = 0; trackIndex < parsed.tracks.size(); trackIndex++) {
            Track midiTrack = sequence.createTrack();
            double absoluteTicks = 0.0;
            int timebase = 48;
            int tempo = 120;
            List<PendingNoteOff> noteOffs = new ArrayList<>();
            for (Event event : parsed.tracks.get(trackIndex)) {
                absoluteTicks += event.delta * (384.0 / java.lang.Math.max(1, timebase));
                long tick = java.lang.Math.round(absoluteTicks);
                flushNoteOffs(midiTrack, noteOffs, tick);
                if (event.type == EVENT_TYPE_NOTE) {
                    int midiChannel = java.lang.Math.max(0, java.lang.Math.min(15, event.channel));
                    int midiKey = java.lang.Math.max(0, java.lang.Math.min(127, 69 + event.key));
                    int velocity = java.lang.Math.max(1, java.lang.Math.min(127, java.lang.Math.round(event.velocity * 127f)));
                    midiTrack.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, midiChannel, midiKey, velocity), tick));
                    long offTick = tick + java.lang.Math.max(1L, java.lang.Math.round(event.gateTime * (384.0 / java.lang.Math.max(1, timebase))));
                    noteOffs.add(new PendingNoteOff(offTick, midiChannel, midiKey));
                    continue;
                }
                if (event.type != EVENT_TYPE_EXT_B) {
                    continue;
                }
                if (event.id == EVENT_END_OF_TRACK) {
                    break;
                }
                if ((event.id & 0xF0) == EVENT_TIMEBASE_TEMPO) {
                    timebase = event.timebase;
                    tempo = event.tempo;
                    addTempo(tempoTrack, tick, tempo);
                    continue;
                }
                int midiChannel = java.lang.Math.max(0, java.lang.Math.min(15, event.channel));
                switch (event.id) {
                    case EVENT_PROGRAM_CHANGE -> midiTrack.add(new MidiEvent(new ShortMessage(ShortMessage.PROGRAM_CHANGE, midiChannel, event.param & 0x3F, 0), tick));
                    case EVENT_BANK_CHANGE -> midiTrack.add(new MidiEvent(new ShortMessage(ShortMessage.CONTROL_CHANGE, midiChannel, 0, event.param & 0x3F), tick));
                    case EVENT_VOLUME -> midiTrack.add(new MidiEvent(new ShortMessage(ShortMessage.CONTROL_CHANGE, midiChannel, 7, java.lang.Math.round((event.param & 0x3F) * (127f / 63f))), tick));
                    case EVENT_PANPOT -> midiTrack.add(new MidiEvent(new ShortMessage(ShortMessage.CONTROL_CHANGE, midiChannel, 10, java.lang.Math.round((event.param & 0x3F) * (127f / 63f))), tick));
                    case EVENT_PITCHBEND -> {
                        int bend = ((event.param & 0x3F) - 32) * 256 + 8192;
                        bend = java.lang.Math.max(0, java.lang.Math.min(16383, bend));
                        midiTrack.add(new MidiEvent(new ShortMessage(ShortMessage.PITCH_BEND, midiChannel, bend & 0x7F, (bend >> 7) & 0x7F), tick));
                    }
                    default -> {
                    }
                }
            }
            flushNoteOffs(midiTrack, noteOffs, Long.MAX_VALUE);
            midiTrack.add(endOfTrack(java.lang.Math.max(1L, java.lang.Math.round(absoluteTicks))));
        }
        return sequence;
    }

    private static void flushNoteOffs(Track track, List<PendingNoteOff> noteOffs, long upToTick) throws InvalidMidiDataException {
        noteOffs.sort(java.util.Comparator.comparingLong(PendingNoteOff::tick));
        for (int i = 0; i < noteOffs.size(); ) {
            PendingNoteOff noteOff = noteOffs.get(i);
            if (noteOff.tick > upToTick) {
                i++;
                continue;
            }
            track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_OFF, noteOff.channel, noteOff.key, 0), noteOff.tick));
            noteOffs.remove(i);
        }
    }

    private static void addTempo(Track track, long tick, int bpm) throws InvalidMidiDataException {
        int safeBpm = java.lang.Math.max(1, bpm);
        int mpqn = 60_000_000 / safeBpm;
        byte[] data = new byte[]{
                (byte) ((mpqn >> 16) & 0xFF),
                (byte) ((mpqn >> 8) & 0xFF),
                (byte) (mpqn & 0xFF)
        };
        MetaMessage message = new MetaMessage();
        message.setMessage(0x51, data, data.length);
        track.add(new MidiEvent(message, tick));
    }

    private static MidiEvent endOfTrack(long tick) throws InvalidMidiDataException {
        MetaMessage message = new MetaMessage();
        message.setMessage(0x2F, new byte[0], 0);
        return new MidiEvent(message, tick);
    }

    private record PendingNoteOff(long tick, int channel, int key) {
    }

    private static final class ParsedMld {
        private final byte[] data;
        private final List<List<Event>> tracks = new ArrayList<>();
        private int noteMode;
        private int[] cuep = new int[0];

        private ParsedMld(byte[] data) {
            this.data = data;
        }

        private ParsedMld parse() throws IOException {
            Reader stream = new Reader(data, 0, data.length);
            if (stream.u32() != FOURCC_MELO) {
                throw new IOException("Missing melo signature");
            }
            int length = stream.u32();
            Reader reader = new Reader(data, 8, length);
            header(reader);
            for (int i = 0; i < tracks.size(); i++) {
                tracks.set(i, track(reader, i));
            }
            return this;
        }

        private void header(Reader reader) throws IOException {
            Reader header = reader.reader(reader.u16());
            header.bytes(header.remaining());
            header.offset = header.start;
            int contentType = header.u16();
            if (contentType != 0x0101 && contentType != 0x0201) {
                throw new IOException("Unsupported MLD content type: " + contentType);
            }
            int numTracks = header.u8();
            this.cuep = new int[numTracks];
            for (int i = 0; i < numTracks; i++) {
                tracks.add(new ArrayList<>());
            }
            while (!header.isEof()) {
                int id = header.u32();
                Reader chunk = header.reader(header.u16());
                switch (id) {
                    case FOURCC_CUEP -> {
                        for (int i = 0; i < cuep.length; i++) {
                            cuep[i] = chunk.u32();
                        }
                    }
                    case FOURCC_NOTE -> noteMode = chunk.u16();
                    default -> chunk.skip(chunk.remaining());
                }
            }
        }

        private List<Event> track(Reader reader, int index) throws IOException {
            if (reader.u32() != FOURCC_TRAC) {
                throw new IOException("Missing trac chunk");
            }
            Reader track = reader.reader(reader.u32());
            int cue = track.offset + cuep[index];
            List<Event> events = new ArrayList<>();
            while (!track.isEof()) {
                if (track.offset == cue) {
                    // cue marker acknowledged but unused in the desktop adapter
                }
                events.add(event(track, index));
            }
            return events;
        }

        private Event event(Reader reader, int track) throws IOException {
            Event event = new Event();
            event.delta = reader.u8();
            event.status = reader.u8();
            if ((event.status & 0x3F) != 63) {
                event.type = EVENT_TYPE_NOTE;
                event.channelIndex = event.status >> 6;
                event.channel = track << 2 | event.channelIndex;
                event.gateTime = reader.u8();
                int keyNumber = event.status & 63;
                int octaveShift = 0;
                float velocity = 1f;
                if (noteMode != 0) {
                    int bits = reader.u8();
                    octaveShift = (bits << 30) >> 30;
                    velocity = (bits >> 2) / 63f;
                }
                event.key = octaveShift * 12 + keyNumber - 24;
                event.velocity = velocity;
                return event;
            }
            event.id = reader.u8();
            if (event.id >= 0xF0) {
                int len = reader.u16();
                reader.skip(len);
                return event;
            }
            if (event.id < 0x80) {
                reader.skip(2);
                return event;
            }
            event.type = EVENT_TYPE_EXT_B;
            event.param = reader.u8();
            event.channelIndex = event.param >> 6;
            event.channel = track << 2 | event.channelIndex;
            if ((event.id & 0xF0) == EVENT_TIMEBASE_TEMPO) {
                event.tempo = event.param;
                event.timebase = (event.id & 7) == 7 ? 48 : (((event.id & 15) > 7 ? 15 : 6) << (event.id & 7));
            }
            return event;
        }
    }

    private static final class Event {
        private int type;
        private int delta;
        private int status;
        private int id;
        private int param;
        private int channelIndex;
        private int channel;
        private int gateTime;
        private int key;
        private float velocity;
        private int tempo;
        private int timebase;
    }

    private static final class Reader {
        private final byte[] data;
        private final int start;
        private final int end;
        private int offset;

        private Reader(byte[] data, int start, int length) {
            this.data = data;
            this.start = start;
            this.end = start + length;
            this.offset = start;
        }

        private int remaining() {
            return end - offset;
        }

        private boolean isEof() {
            return offset >= end;
        }

        private byte[] bytes(int length) throws IOException {
            if (offset + length > end) {
                throw new IOException("Unexpected EOF");
            }
            byte[] out = new byte[length];
            System.arraycopy(data, offset, out, 0, length);
            offset += length;
            return out;
        }

        private Reader reader(int length) throws IOException {
            Reader child = new Reader(data, offset, length);
            skip(length);
            return child;
        }

        private void skip(int length) throws IOException {
            if (offset + length > end) {
                throw new IOException("Unexpected EOF");
            }
            offset += length;
        }

        private int u8() throws IOException {
            if (offset >= end) {
                throw new IOException("Unexpected EOF");
            }
            return data[offset++] & 0xFF;
        }

        private int u16() throws IOException {
            return (u8() << 8) | u8();
        }

        private int u32() throws IOException {
            return (u16() << 16) | u16();
        }
    }
}
