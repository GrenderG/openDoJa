package opendoja.audio;

import com.nttdocomo.ui.MediaManager;

import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class MidiEventPlayer implements AutoCloseable {
    private static final long MAX_DIRECT_MIDI_MICROS = 1_000_000L;
    private static final ScheduledExecutorService MIDI_EVENTS = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "opendoja-midi-events");
        thread.setDaemon(true);
        return thread;
    });
    private static final Object RECEIVER_LOCK = new Object();
    private static Receiver sharedReceiver;

    public interface Listener {
        void onComplete(long playbackToken);

        void onFailure(Exception exception, long playbackToken);
    }

    private final Object stateLock = new Object();
    private final Listener listener;
    private final List<ScheduledFuture<?>> tasks = new ArrayList<>();
    private final List<ShortMessage> activeNoteOffs = new ArrayList<>();
    private long startNanos;
    private int startPositionMillis;
    private int totalTimeMillis;
    private long playbackToken = Long.MIN_VALUE;
    private boolean active;
    private boolean closed;

    public MidiEventPlayer(Listener listener) {
        this.listener = listener;
    }

    public static boolean isLowLatencyCandidate(MediaManager.PreparedSound sound) {
        if (sound == null || sound.kind() != MediaManager.PreparedSound.Kind.MIDI) {
            return false;
        }
        Sequence sequence = sound.midiSequence();
        if (sequence == null || sequence.getMicrosecondLength() <= 0
                || sequence.getMicrosecondLength() > MAX_DIRECT_MIDI_MICROS) {
            return false;
        }
        // Restrict the fast path to simple short effects. Full MIDI sequencing
        // semantics, including later tempo changes, remain on Sequencer.
        for (Track track : sequence.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                MidiEvent event = track.get(i);
                MidiMessage message = event.getMessage();
                if (message instanceof ShortMessage) {
                    continue;
                }
                if (message instanceof MetaMessage meta && meta.getType() == 0x2F) {
                    continue;
                }
                if (message instanceof MetaMessage meta && meta.getType() == 0x51 && event.getTick() == 0L) {
                    continue;
                }
                return false;
            }
        }
        return true;
    }

    public void start(MediaManager.PreparedSound sound, int startPositionMillis, long playbackToken) throws Exception {
        Sequence sequence = sound.midiSequence();
        if (sequence == null) {
            throw new IllegalArgumentException("sound");
        }
        int durationMillis = (int) Math.round(sequence.getMicrosecondLength() / 1_000.0d);
        synchronized (stateLock) {
            if (closed) {
                return;
            }
            stopLocked();
            this.startNanos = System.nanoTime();
            this.startPositionMillis = Math.max(0, startPositionMillis);
            this.totalTimeMillis = durationMillis;
            this.playbackToken = playbackToken;
            this.active = true;
        }

        try {
            schedule(sequence, this.startPositionMillis, playbackToken);
        } catch (Exception exception) {
            synchronized (stateLock) {
                if (this.playbackToken == playbackToken) {
                    stopLocked();
                }
            }
            throw exception;
        }
    }

    public void stop() {
        synchronized (stateLock) {
            stopLocked();
        }
    }

    public boolean isActive() {
        synchronized (stateLock) {
            return active;
        }
    }

    public int getCurrentTimeMillis() {
        synchronized (stateLock) {
            if (!active) {
                return 0;
            }
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            return Math.min(totalTimeMillis, startPositionMillis + (int) elapsedMillis);
        }
    }

    public int getTotalTimeMillis() {
        synchronized (stateLock) {
            return totalTimeMillis;
        }
    }

    @Override
    public void close() {
        synchronized (stateLock) {
            closed = true;
            stopLocked();
        }
    }

    private void schedule(Sequence sequence, int offsetMillis, long playbackToken) throws Exception {
        long offsetMicros = Math.max(0, offsetMillis) * 1_000L;
        long totalMicros = sequence.getMicrosecondLength();
        for (Track track : sequence.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                MidiEvent event = track.get(i);
                if (!(event.getMessage() instanceof ShortMessage message)) {
                    continue;
                }
                long eventMicros = eventMicros(sequence, event);
                if (eventMicros < offsetMicros) {
                    continue;
                }
                long delayMicros = Math.max(0L, eventMicros - offsetMicros);
                ShortMessage copy = copyOf(message);
                addTask(MIDI_EVENTS.schedule(() -> sendScheduled(copy, playbackToken),
                        delayMicros, TimeUnit.MICROSECONDS));
            }
        }
        long completionDelayMicros = Math.max(0L, totalMicros - offsetMicros);
        addTask(MIDI_EVENTS.schedule(() -> complete(playbackToken), completionDelayMicros, TimeUnit.MICROSECONDS));
    }

    private void sendScheduled(ShortMessage message, long playbackToken) {
        synchronized (stateLock) {
            if (!active || this.playbackToken != playbackToken) {
                return;
            }
        }
        try {
            Receiver receiver = receiver();
            sendEvent(receiver, message, playbackToken);
        } catch (Exception exception) {
            fail(exception, playbackToken);
        }
    }

    private void complete(long playbackToken) {
        synchronized (stateLock) {
            if (!active || this.playbackToken != playbackToken) {
                return;
            }
            stopLocked();
        }
        if (listener != null) {
            listener.onComplete(playbackToken);
        }
    }

    private void fail(Exception exception, long playbackToken) {
        synchronized (stateLock) {
            if (this.playbackToken != playbackToken) {
                return;
            }
            stopLocked();
        }
        if (listener != null) {
            listener.onFailure(exception, playbackToken);
        }
    }

    private void stopLocked() {
        for (ScheduledFuture<?> task : tasks) {
            task.cancel(false);
        }
        tasks.clear();
        if (active) {
            sendActiveNotesOff();
        }
        active = false;
        playbackToken = Long.MIN_VALUE;
        startPositionMillis = 0;
    }

    private void addTask(ScheduledFuture<?> task) {
        synchronized (stateLock) {
            if (active) {
                tasks.add(task);
            } else {
                task.cancel(false);
            }
        }
    }

    private static long eventMicros(Sequence sequence, MidiEvent event) {
        long tickLength = sequence.getTickLength();
        long microsecondLength = sequence.getMicrosecondLength();
        if (tickLength <= 0 || microsecondLength <= 0) {
            return 0L;
        }
        return Math.round((double) event.getTick() * microsecondLength / tickLength);
    }

    private static Receiver receiver() throws Exception {
        synchronized (RECEIVER_LOCK) {
            if (sharedReceiver == null) {
                sharedReceiver = MidiSystem.getReceiver();
            }
            return sharedReceiver;
        }
    }

    private static void send(Receiver receiver, MidiMessage message) {
        synchronized (RECEIVER_LOCK) {
            receiver.send(message, -1);
        }
    }

    private void sendEvent(Receiver receiver, ShortMessage message, long playbackToken) {
        synchronized (stateLock) {
            if (!active || this.playbackToken != playbackToken) {
                return;
            }
            if (isNoteOn(message)) {
                activeNoteOffs.add(noteOffFor(message));
            } else if (isNoteOff(message)) {
                removeActiveNoteOff(message);
            }
        }
        send(receiver, message);
    }

    private static ShortMessage copyOf(ShortMessage message) throws Exception {
        ShortMessage copy = new ShortMessage();
        copy.setMessage(message.getCommand(), message.getChannel(), message.getData1(), message.getData2());
        return copy;
    }

    private void sendActiveNotesOff() {
        List<ShortMessage> notesOff = new ArrayList<>(activeNoteOffs);
        activeNoteOffs.clear();
        if (notesOff.isEmpty()) {
            return;
        }
        MIDI_EVENTS.execute(() -> {
            try {
                Receiver receiver = receiver();
                for (ShortMessage noteOff : notesOff) {
                    send(receiver, noteOff);
                }
            } catch (Exception ignored) {
            }
        });
    }

    private void removeActiveNoteOff(ShortMessage message) {
        for (int i = activeNoteOffs.size() - 1; i >= 0; i--) {
            ShortMessage active = activeNoteOffs.get(i);
            if (active.getChannel() == message.getChannel()
                    && active.getData1() == message.getData1()) {
                activeNoteOffs.remove(i);
                return;
            }
        }
    }

    private static boolean isNoteOn(ShortMessage message) {
        return message.getCommand() == ShortMessage.NOTE_ON && message.getData2() > 0;
    }

    private static boolean isNoteOff(ShortMessage message) {
        return message.getCommand() == ShortMessage.NOTE_OFF
                || (message.getCommand() == ShortMessage.NOTE_ON && message.getData2() == 0);
    }

    private static ShortMessage noteOffFor(ShortMessage message) {
        try {
            ShortMessage noteOff = new ShortMessage();
            noteOff.setMessage(ShortMessage.NOTE_OFF, message.getChannel(), message.getData1(), 0);
            return noteOff;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
