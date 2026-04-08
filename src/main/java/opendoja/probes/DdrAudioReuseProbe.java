package opendoja.probes;

import com.nttdocomo.ui.AudioPresenter;
import com.nttdocomo.ui.MediaManager;
import com.nttdocomo.ui.MediaSound;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Stresses DDR's shared-presenter audio pattern: short SE playback followed by
 * immediate BGM replacement on the same presenter.
 */
public final class DdrAudioReuseProbe {
    private DdrAudioReuseProbe() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            throw new IllegalArgumentException(
                    "usage: DdrAudioReuseProbe <short-mld> <long-mld> <handoff-delay-ms> <observe-ms> <iterations>");
        }

        MediaSound shortSound = MediaManager.getSound(Files.readAllBytes(Path.of(args[0])));
        MediaSound longSound = MediaManager.getSound(Files.readAllBytes(Path.of(args[1])));
        long handoffDelayMillis = Long.parseLong(args[2]);
        long observeMillis = Long.parseLong(args[3]);
        int iterations = Integer.parseInt(args[4]);

        shortSound.use();
        longSound.use();

        int stalled = 0;
        for (int i = 0; i < iterations; i++) {
            AudioPresenter presenter = AudioPresenter.getAudioPresenter();
            try {
                presenter.setSound(shortSound);
                presenter.play();
                Thread.sleep(Math.max(0L, handoffDelayMillis));

                presenter.setSound(longSound);
                presenter.play();
                Thread.sleep(Math.max(0L, observeMillis));

                int currentTime = presenter.getCurrentTime();
                int totalTime = presenter.getTotalTime();
                boolean playing = currentTime > Math.max(100, observeMillis / 3);
                if (!playing) {
                    stalled++;
                }
                System.out.println("iteration=" + i
                        + " currentTime=" + currentTime
                        + " totalTime=" + totalTime
                        + " stalled=" + !playing);
            } finally {
                presenter.stop();
                presenter.close();
            }
        }

        System.out.println("stalled=" + stalled + "/" + iterations);
        if (stalled > 0) {
            throw new IllegalStateException("DDR presenter reuse stalled in " + stalled + " iterations");
        }
    }
}
