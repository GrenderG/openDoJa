package opendoja.probes;

import com.nttdocomo.ui.AudioPresenter;
import com.nttdocomo.ui.MediaListener;
import com.nttdocomo.ui.MediaManager;
import com.nttdocomo.ui.MediaPresenter;
import com.nttdocomo.ui.MediaSound;
import opendoja.audio.MidiEventPlayer;

import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class AudioPresenterShortMidiProbe {
    private AudioPresenterShortMidiProbe() {
    }

    public static void main(String[] args) throws Exception {
        int iterations = args.length >= 1 ? Integer.parseInt(args[0]) : 128;
        double maxAllowedP95Millis = args.length >= 2 ? Double.parseDouble(args[1]) : 5.0d;

        MediaSound sound = MediaManager.getSound(shortMidi());
        sound.use();
        Method preparedMethod = sound.getClass().getDeclaredMethod("prepared");
        preparedMethod.setAccessible(true);
        MediaManager.PreparedSound prepared = (MediaManager.PreparedSound) preparedMethod.invoke(sound);
        if (!MidiEventPlayer.isLowLatencyCandidate(prepared)) {
            throw new IllegalStateException("short MIDI was not accepted by the low-latency path");
        }

        AudioPresenter presenter = AudioPresenter.getAudioPresenter();
        CountDownLatch complete = new CountDownLatch(1);
        AtomicInteger stopped = new AtomicInteger();
        presenter.setMediaListener(new MediaListener() {
            @Override
            public void mediaAction(MediaPresenter media, int type, int param) {
                if (type == AudioPresenter.AUDIO_COMPLETE) {
                    complete.countDown();
                } else if (type == AudioPresenter.AUDIO_STOPPED) {
                    stopped.incrementAndGet();
                }
            }
        });
        presenter.setSound(sound);
        presenter.play();
        if (!complete.await(2, TimeUnit.SECONDS)) {
            if (stopped.get() > 0) {
                presenter.close();
                System.out.println("short-midi audio unavailable; skipped latency check");
                return;
            }
            throw new IllegalStateException("short MIDI did not complete");
        }

        long[] samples = new long[iterations];
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            presenter.stop();
            presenter.setSound(sound);
            presenter.play();
            samples[i] = System.nanoTime() - start;
        }
        presenter.close();

        Arrays.sort(samples);
        double p95Millis = samples[Math.max(0, Math.min(samples.length - 1,
                (int) Math.ceil(samples.length * 0.95d) - 1))] / 1_000_000.0d;
        double maxMillis = samples[samples.length - 1] / 1_000_000.0d;
        System.out.printf("short-midi samples=%d p95Ms=%.3f maxMs=%.3f%n",
                samples.length, p95Millis, maxMillis);
        if (p95Millis > maxAllowedP95Millis) {
            throw new IllegalStateException("short MIDI p95 " + p95Millis
                    + " ms exceeds " + maxAllowedP95Millis + " ms");
        }
    }

    private static byte[] shortMidi() throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, 24);
        Track track = sequence.createTrack();

        MetaMessage tempo = new MetaMessage();
        tempo.setMessage(0x51, new byte[]{0x07, (byte) 0xA1, 0x20}, 3);
        track.add(new MidiEvent(tempo, 0));

        ShortMessage program = new ShortMessage();
        program.setMessage(ShortMessage.PROGRAM_CHANGE, 0, 0, 0);
        track.add(new MidiEvent(program, 0));

        ShortMessage noteOn = new ShortMessage();
        noteOn.setMessage(ShortMessage.NOTE_ON, 0, 60, 64);
        track.add(new MidiEvent(noteOn, 0));

        ShortMessage noteOff = new ShortMessage();
        noteOff.setMessage(ShortMessage.NOTE_OFF, 0, 60, 0);
        track.add(new MidiEvent(noteOff, 12));

        MetaMessage end = new MetaMessage();
        end.setMessage(0x2F, new byte[0], 0);
        track.add(new MidiEvent(end, 12));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MidiSystem.write(sequence, 1, out);
        return out.toByteArray();
    }
}
