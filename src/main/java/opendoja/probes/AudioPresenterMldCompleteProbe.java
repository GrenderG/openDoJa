package opendoja.probes;

import com.nttdocomo.nec.sound.SoundManager;
import com.nttdocomo.ui.AudioPresenter;
import com.nttdocomo.ui.MediaListener;
import com.nttdocomo.ui.MediaManager;
import com.nttdocomo.ui.MediaPresenter;
import com.nttdocomo.ui.MediaSound;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Logs presenter-visible playback events for one MLD over a fixed observation
 * window.
 */
public final class AudioPresenterMldCompleteProbe {
    private AudioPresenterMldCompleteProbe() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) {
            throw new IllegalArgumentException("usage: AudioPresenterMldCompleteProbe <mld-file> <observe-ms> [standard|nec]");
        }

        MediaSound sound = MediaManager.getSound(Files.readAllBytes(Path.of(args[0])));
        long observeMillis = Long.parseLong(args[1]);
        sound.use();

        AudioPresenter presenter = args.length == 3 && "nec".equals(args[2])
                ? SoundManager.getAudioPresenter()
                : AudioPresenter.getAudioPresenter();
        long startedAt = System.nanoTime();
        presenter.setMediaListener(new MediaListener() {
            @Override
            public void mediaAction(MediaPresenter media, int type, int param) {
                AudioPresenter audio = (AudioPresenter) media;
                long nowMs = Math.round((System.nanoTime() - startedAt) / 1_000_000.0d);
                System.out.println("event@" + nowMs + "ms type=" + type
                        + " current=" + audio.getCurrentTime()
                        + " total=" + audio.getTotalTime());
            }
        });

        presenter.setSound(sound);
        presenter.play();
        Thread.sleep(Math.max(0L, observeMillis));
        presenter.stop();
        presenter.close();
    }
}
