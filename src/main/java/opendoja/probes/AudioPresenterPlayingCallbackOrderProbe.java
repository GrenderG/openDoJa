package opendoja.probes;

import com.nttdocomo.ui.AudioPresenter;
import com.nttdocomo.ui.MediaListener;
import com.nttdocomo.ui.MediaManager;
import com.nttdocomo.ui.MediaPresenter;
import com.nttdocomo.ui.MediaSound;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Verifies that AUDIO_PLAYING is not delivered synchronously on the play()
 * caller thread. Some titles update their own presenter-slot state immediately
 * after play(), then clear the "starting" flag from the media callback.
 */
public final class AudioPresenterPlayingCallbackOrderProbe {
    private AudioPresenterPlayingCallbackOrderProbe() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "usage: AudioPresenterPlayingCallbackOrderProbe <sound-file>");
        }

        MediaSound sound = MediaManager.getSound(Files.readAllBytes(Path.of(args[0])));
        sound.use();

        CountDownLatch playing = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread playCaller = Thread.currentThread();
        AudioPresenter presenter = AudioPresenter.getAudioPresenter();
        presenter.setMediaListener(new MediaListener() {
            @Override
            public void mediaAction(MediaPresenter media, int type, int param) {
                if (type != AudioPresenter.AUDIO_PLAYING) {
                    return;
                }
                if (Thread.currentThread() == playCaller) {
                    failure.compareAndSet(null,
                            new AssertionError("AUDIO_PLAYING fired on the play() caller thread"));
                }
                playing.countDown();
            }
        });

        presenter.setSound(sound);
        presenter.play();

        try {
            if (!playing.await(2L, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for AUDIO_PLAYING");
            }
            Throwable callbackFailure = failure.get();
            if (callbackFailure != null) {
                throw new AssertionError("Invalid AUDIO_PLAYING callback order", callbackFailure);
            }
            System.out.println("AudioPresenterPlayingCallbackOrderProbe OK");
        } finally {
            presenter.stop();
            presenter.close();
        }
    }
}
