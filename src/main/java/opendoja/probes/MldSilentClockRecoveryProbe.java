package opendoja.probes;

import com.nttdocomo.ui.AudioPresenter;
import com.nttdocomo.ui.MediaManager;
import com.nttdocomo.ui.MediaSound;

import javax.sound.sampled.SourceDataLine;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Forces the shared MLD line into silent-clock mode and checks whether the
 * engine can recover its normal output path.
 */
public final class MldSilentClockRecoveryProbe {
    private MldSilentClockRecoveryProbe() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("usage: MldSilentClockRecoveryProbe <mld-file>");
        }

        MediaSound sound = MediaManager.getSound(Files.readAllBytes(Path.of(args[0])));
        sound.use();

        AudioPresenter presenter = AudioPresenter.getAudioPresenter();
        presenter.setSound(sound);
        presenter.play();
        Thread.sleep(100L);

        Object engine = sharedEngine();
        Field engineLockField = engine.getClass().getDeclaredField("engineLock");
        Field lineField = engine.getClass().getDeclaredField("line");
        Field silentClockField = engine.getClass().getDeclaredField("silentClock");
        Field lineRetryAtNanosField = engine.getClass().getDeclaredField("lineRetryAtNanos");
        engineLockField.setAccessible(true);
        lineField.setAccessible(true);
        silentClockField.setAccessible(true);
        lineRetryAtNanosField.setAccessible(true);

        Object engineLock = engineLockField.get(engine);
        boolean lineInitiallyOpen;
        synchronized (engineLock) {
            lineInitiallyOpen = lineField.get(engine) != null;
        }
        if (!lineInitiallyOpen) {
            presenter.stop();
            presenter.close();
            System.out.println("host-line-unavailable=true");
            return;
        }

        synchronized (engineLock) {
            SourceDataLine line = (SourceDataLine) lineField.get(engine);
            line.stop();
            line.flush();
            line.close();
            lineField.set(engine, null);
            silentClockField.setBoolean(engine, true);
            lineRetryAtNanosField.setLong(engine, 0L);
            engineLock.notifyAll();
        }

        Thread.sleep(250L);

        boolean recovered;
        boolean silentClock;
        synchronized (engineLock) {
            recovered = lineField.get(engine) != null;
            silentClock = silentClockField.getBoolean(engine);
        }

        presenter.stop();
        presenter.close();

        System.out.println("lineInitiallyOpen=" + lineInitiallyOpen);
        System.out.println("recovered=" + recovered);
        System.out.println("silentClock=" + silentClock);
        if (!recovered || silentClock) {
            throw new IllegalStateException("MLD engine did not recover from forced silent clock");
        }
    }

    private static Object sharedEngine() throws Exception {
        Field engineField = Class.forName("opendoja.audio.mld.MLDPCMPlayer").getDeclaredField("ENGINE");
        engineField.setAccessible(true);
        return engineField.get(null);
    }
}
