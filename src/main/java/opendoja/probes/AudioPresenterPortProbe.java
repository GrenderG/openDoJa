package opendoja.probes;

import com.nttdocomo.ui.AudioPresenter;
import com.nttdocomo.ui.MediaManager;
import com.nttdocomo.ui.MediaSound;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AudioPresenterPortProbe {
    private AudioPresenterPortProbe() {
    }

    public static void main(String[] args) throws Exception {
        DemoLog.enableInfoLogging();
        if (args.length != 1) {
            throw new IllegalArgumentException("usage: AudioPresenterPortProbe <mld-file>");
        }

        System.setProperty(opendoja.host.OpenDoJaLaunchArgs.AUDIO_PRESENTER_PORTS, "4");

        MediaSound sound = MediaManager.getSound(Files.readAllBytes(Path.of(args[0])));
        sound.use();

        expectIllegalArgument(() -> AudioPresenter.getAudioPresenter(-1), "negative port");
        expectIllegalArgument(() -> AudioPresenter.getAudioPresenter(4), "out-of-range port");

        AudioPresenter port0a = AudioPresenter.getAudioPresenter(0);
        AudioPresenter port0b = AudioPresenter.getAudioPresenter(0);
        AudioPresenter port3 = AudioPresenter.getAudioPresenter(3);

        port0a.setSound(sound);
        port0b.setSound(sound);
        port3.setSound(sound);

        port0a.play();
        port3.play();
        port0b.play();
        DemoLog.info(AudioPresenterPortProbe.class, "same explicit port coexistence=ok");

        port0a.stop();
        port0b.stop();
        port3.stop();

        System.out.println("audio-presenter-port-probe-ok");
    }

    private static void expectIllegalArgument(ThrowingRunnable runnable, String label) throws Exception {
        try {
            runnable.run();
            throw new AssertionError(label + " did not throw IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            DemoLog.info(AudioPresenterPortProbe.class, label + "=ok");
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
