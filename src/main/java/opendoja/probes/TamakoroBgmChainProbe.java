package opendoja.probes;

import com.nttdocomo.ui.AudioPresenter;
import com.nttdocomo.ui.MediaListener;
import com.nttdocomo.ui.MediaManager;
import com.nttdocomo.ui.MediaPresenter;
import com.nttdocomo.ui.MediaSound;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Emulates a simple two-step BGM handoff where an intro track completes once
 * and the follow-up track is replayed on later completions.
 */
public final class TamakoroBgmChainProbe {
    private TamakoroBgmChainProbe() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("usage: TamakoroBgmChainProbe <bgm7> <bgm8> <observe-ms>");
        }

        MediaSound bgm7 = MediaManager.getSound(Files.readAllBytes(Path.of(args[0])));
        MediaSound bgm8 = MediaManager.getSound(Files.readAllBytes(Path.of(args[1])));
        long observeMillis = Long.parseLong(args[2]);
        bgm7.use();
        bgm8.use();

        AudioPresenter presenter = AudioPresenter.getAudioPresenter(0);
        long startedAt = System.nanoTime();
        AtomicInteger currentTrack = new AtomicInteger(7);
        AtomicInteger completes = new AtomicInteger();

        presenter.setMediaListener(new MediaListener() {
            @Override
            public void mediaAction(MediaPresenter media, int type, int param) {
                AudioPresenter audio = (AudioPresenter) media;
                long nowMs = millisBetween(startedAt, System.nanoTime());
                System.out.println("event@" + nowMs + "ms type=" + type
                        + " track=" + currentTrack.get()
                        + " current=" + audio.getCurrentTime()
                        + " total=" + audio.getTotalTime());
                if (type != AudioPresenter.AUDIO_COMPLETE) {
                    return;
                }
                completes.incrementAndGet();
                currentTrack.set(8);
                presenter.stop();
                presenter.setSound(bgm8);
                presenter.play();
            }
        });

        presenter.setSound(bgm7);
        presenter.play();
        Thread.sleep(Math.max(0L, observeMillis));
        System.out.println("finalTrack=" + currentTrack.get());
        System.out.println("completes=" + completes.get());
        System.out.println("currentTime=" + presenter.getCurrentTime());
        System.out.println("totalTime=" + presenter.getTotalTime());
        presenter.stop();
        presenter.close();
    }

    private static long millisBetween(long start, long end) {
        return Math.round((end - start) / 1_000_000.0d);
    }
}
