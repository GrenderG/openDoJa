package opendoja.demo;

import com.nttdocomo.ui.AudioPresenter;
import com.nttdocomo.ui.MediaManager;
import com.nttdocomo.ui.MediaSound;

public final class AudioShutdownProbe {
    private AudioShutdownProbe() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: AudioShutdownProbe <resource-name> <delay-ms>");
        }
        String resourceName = args[0];
        long delayMillis = Long.parseLong(args[1]);
        MediaSound sound = MediaManager.getSound(resourceName);
        AudioPresenter presenter = AudioPresenter.getAudioPresenter();
        presenter.setSound(sound);
        presenter.play();
        Thread.sleep(Math.max(0L, delayMillis));
        presenter.stop();
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.isAlive() && !thread.isDaemon() && thread != Thread.currentThread()) {
                System.out.println("non-daemon-thread=" + thread.getName() + " state=" + thread.getState());
            }
        }
        System.out.println("audio-stop-complete");
    }
}
