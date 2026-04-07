package opendoja.probes;

import com.nttdocomo.ui.AudioPresenter;
import com.nttdocomo.ui.MediaListener;
import com.nttdocomo.ui.MediaManager;
import com.nttdocomo.ui.MediaPresenter;
import com.nttdocomo.ui.MediaSound;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Exercises stop/play replacement timing to detect stale completion callbacks
 * that arrive after a new MLD has already started.
 */
public final class MldRestartCompleteRaceProbe {
    private MldRestartCompleteRaceProbe() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            throw new IllegalArgumentException(
                    "usage: MldRestartCompleteRaceProbe <short-mld> <long-mld> <restart-delay-ms> <observe-ms> <iterations>");
        }

        MediaSound shortSound = MediaManager.getSound(Files.readAllBytes(Path.of(args[0])));
        MediaSound longSound = MediaManager.getSound(Files.readAllBytes(Path.of(args[1])));
        long restartDelayMillis = Long.parseLong(args[2]);
        long observeMillis = Long.parseLong(args[3]);
        int iterations = Integer.parseInt(args[4]);

        shortSound.use();
        longSound.use();

        int staleCompletes = 0;
        for (int i = 0; i < iterations; i++) {
            AudioPresenter presenter = AudioPresenter.getAudioPresenter();
            List<String> events = new ArrayList<>();
            long startedAt = System.nanoTime();
            presenter.setMediaListener(new LoggingListener(startedAt, events));

            presenter.setSound(shortSound);
            presenter.play();
            Thread.sleep(Math.max(0L, restartDelayMillis));
            long secondPlayAt = System.nanoTime();
            presenter.setSound(longSound);
            presenter.play();
            Thread.sleep(Math.max(0L, observeMillis));

            boolean staleComplete = false;
            for (String event : events) {
                if (event.contains(" type=" + AudioPresenter.AUDIO_COMPLETE + " ")) {
                    long eventMillis = parseEventMillis(event);
                    long secondPlayMillis = millisBetween(startedAt, secondPlayAt);
                    if (eventMillis >= secondPlayMillis && eventMillis < secondPlayMillis + observeMillis) {
                        staleComplete = true;
                        break;
                    }
                }
            }
            if (staleComplete) {
                staleCompletes++;
            }
            System.out.println("iteration=" + i + " staleComplete=" + staleComplete);
            for (String event : events) {
                System.out.println("  " + event);
            }
            presenter.stop();
            presenter.close();
        }

        System.out.println("staleCompletes=" + staleCompletes + "/" + iterations);
    }

    private static long parseEventMillis(String event) {
        int at = event.indexOf('@');
        int ms = event.indexOf("ms");
        return Long.parseLong(event.substring(at + 1, ms));
    }

    private static long millisBetween(long start, long end) {
        return Math.round((end - start) / 1_000_000.0d);
    }

    private static final class LoggingListener implements MediaListener {
        private final long startedAt;
        private final List<String> events;

        private LoggingListener(long startedAt, List<String> events) {
            this.startedAt = startedAt;
            this.events = events;
        }

        @Override
        public void mediaAction(MediaPresenter presenter, int type, int param) {
            long now = System.nanoTime();
            AudioPresenter audio = (AudioPresenter) presenter;
            events.add("event@" + millisBetween(startedAt, now)
                    + "ms type=" + type
                    + " param=" + param
                    + " current=" + audio.getCurrentTime()
                    + " total=" + audio.getTotalTime());
        }
    }
}
