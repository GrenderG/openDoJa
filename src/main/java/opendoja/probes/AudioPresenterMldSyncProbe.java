package opendoja.probes;

import com.nttdocomo.ui.AudioPresenter;
import com.nttdocomo.ui.MediaListener;
import com.nttdocomo.ui.MediaManager;
import com.nttdocomo.ui.MediaPresenter;
import com.nttdocomo.ui.MediaSound;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

public final class AudioPresenterMldSyncProbe {
    private AudioPresenterMldSyncProbe() {
    }

    public static void main(String[] args) throws Exception {
        DemoLog.enableInfoLogging();
        if (args.length != 4) {
            throw new IllegalArgumentException(
                    "usage: AudioPresenterMldSyncProbe <mld-file> <channel> <note> <wait-ms>");
        }

        byte[] bytes = Files.readAllBytes(Path.of(args[0]));
        int channel = Integer.parseInt(args[1]);
        int note = Integer.parseInt(args[2]);
        long waitMillis = Long.parseLong(args[3]);

        MediaSound sound = MediaManager.getSound(bytes);
        sound.use();

        AudioPresenter presenter = AudioPresenter.getAudioPresenter();
        AtomicInteger syncCount = new AtomicInteger();
        presenter.setSound(sound);
        presenter.setMediaListener(new LoggingListener(syncCount));
        presenter.setAttribute(AudioPresenter.SYNC_MODE, AudioPresenter.ATTR_SYNC_ON);
        presenter.setSyncEvent(channel, note);
        presenter.play();
        Thread.sleep(Math.max(0L, waitMillis));
        presenter.stop();

        DemoLog.info(AudioPresenterMldSyncProbe.class,
                "summary syncCount=" + syncCount.get()
                        + " currentTime=" + presenter.getCurrentTime()
                        + " totalTime=" + presenter.getTotalTime());
    }

    private static final class LoggingListener implements MediaListener {
        private final AtomicInteger syncCount;

        private LoggingListener(AtomicInteger syncCount) {
            this.syncCount = syncCount;
        }

        @Override
        public void mediaAction(MediaPresenter presenter, int type, int param) {
            if (type == AudioPresenter.AUDIO_SYNC) {
                int count = syncCount.incrementAndGet();
                DemoLog.info(AudioPresenterMldSyncProbe.class,
                        "audio-sync #" + count + " param=" + param);
            } else if (type == AudioPresenter.AUDIO_PLAYING
                    || type == AudioPresenter.AUDIO_COMPLETE
                    || type == AudioPresenter.AUDIO_STOPPED
                    || type == AudioPresenter.AUDIO_LOOPED) {
                DemoLog.info(AudioPresenterMldSyncProbe.class,
                        "audio-event type=" + type + " param=" + param);
            }
        }
    }
}
