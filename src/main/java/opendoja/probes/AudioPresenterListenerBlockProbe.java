package opendoja.probes;

import com.nttdocomo.ui.AudioPresenter;
import com.nttdocomo.ui.MediaListener;
import com.nttdocomo.ui.MediaManager;
import com.nttdocomo.ui.MediaPresenter;
import com.nttdocomo.ui.MediaSound;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Demonstrates whether blocking a media listener stalls presenter time
 * advancement or only delays callback delivery.
 */
public final class AudioPresenterListenerBlockProbe {
    private AudioPresenterListenerBlockProbe() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            throw new IllegalArgumentException(
                    "usage: AudioPresenterListenerBlockProbe <mld-file> <channel> <note> <block-ms> <observe-ms>");
        }

        MediaSound sound = MediaManager.getSound(Files.readAllBytes(Path.of(args[0])));
        int channel = Integer.parseInt(args[1]);
        int note = Integer.parseInt(args[2]);
        long blockMillis = Long.parseLong(args[3]);
        long observeMillis = Long.parseLong(args[4]);
        sound.use();

        AudioPresenter presenter = AudioPresenter.getAudioPresenter();
        presenter.setSyncEvent(channel, note);
        presenter.setAttribute(AudioPresenter.SYNC_MODE, AudioPresenter.ATTR_SYNC_ON);

        long startedAt = System.nanoTime();
        AtomicBoolean blocked = new AtomicBoolean();
        presenter.setMediaListener(new MediaListener() {
            @Override
            public void mediaAction(MediaPresenter media, int type, int param) {
                long nowMs = millisBetween(startedAt, System.nanoTime());
                AudioPresenter audio = (AudioPresenter) media;
                System.out.println("event@" + nowMs + "ms type=" + type
                        + " thread=" + Thread.currentThread().getName()
                        + " current=" + audio.getCurrentTime()
                        + " total=" + audio.getTotalTime());
                if (type == AudioPresenter.AUDIO_SYNC && blocked.compareAndSet(false, true)) {
                    try {
                        Thread.sleep(Math.max(0L, blockMillis));
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        });

        presenter.setSound(sound);
        presenter.play();
        long deadline = System.nanoTime() + observeMillis * 1_000_000L;
        while (System.nanoTime() < deadline) {
            Thread.sleep(250L);
            long nowMs = millisBetween(startedAt, System.nanoTime());
            System.out.println("poll@" + nowMs + "ms thread=" + Thread.currentThread().getName()
                    + " current=" + presenter.getCurrentTime()
                    + " total=" + presenter.getTotalTime());
        }
        presenter.stop();
        presenter.close();
    }

    private static long millisBetween(long start, long end) {
        return Math.round((end - start) / 1_000_000.0d);
    }
}
