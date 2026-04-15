package opendoja.probes;

import com.nttdocomo.ui.Display;
import com.nttdocomo.util.Phone;
import opendoja.host.DoJaRuntime;
import opendoja.host.JamLauncher;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Verifies the public Phone constructor contract and the Chocomate enter path
 * that instantiates {@link Phone} before using its static methods.
 */
public final class ChocomatePhoneCompatibilityProbe {
    private ChocomatePhoneCompatibilityProbe() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: ChocomatePhoneCompatibilityProbe <chocomate.jam>");
        }
        verifyPhoneConstructor();
        verifyChocomateEnterPath(Path.of(args[0]));
        System.out.println("chocomate-phone-compatibility-probe-ok");
    }

    private static void verifyPhoneConstructor() throws Exception {
        Phone phone = Phone.class.getConstructor().newInstance();
        if (phone == null) {
            throw new IllegalStateException("Phone() did not create an instance");
        }
    }

    private static void verifyChocomateEnterPath(Path jamPath) throws Exception {
        AtomicReference<Throwable> launchFailure = new AtomicReference<>();
        Thread launchThread = new Thread(() -> {
            try {
                JamLauncher.launch(jamPath);
            } catch (Throwable throwable) {
                launchFailure.set(throwable);
            }
        }, "chocomate-launch");
        launchThread.setDaemon(true);
        launchThread.start();

        Throwable failure = null;
        try {
            waitForRuntime(launchFailure);
            Thread.sleep(1500L);
            DoJaRuntime runtime = requireRuntime(launchFailure);
            runtime.dispatchSyntheticKey(Display.KEY_SELECT, Display.KEY_PRESSED_EVENT);
            Thread.sleep(200L);
            runtime.dispatchSyntheticKey(Display.KEY_SELECT, Display.KEY_RELEASED_EVENT);
            Thread.sleep(1000L);
            requireRuntime(launchFailure);
        } catch (Throwable throwable) {
            failure = throwable;
            throw throwable;
        } finally {
            DoJaRuntime runtime = DoJaRuntime.current();
            if (runtime != null) {
                runtime.shutdown();
            }
            if (failure == null && launchFailure.get() != null) {
                throw propagate(launchFailure.get());
            }
        }
    }

    private static void waitForRuntime(AtomicReference<Throwable> launchFailure) throws Exception {
        long deadline = System.currentTimeMillis() + 5000L;
        while (DoJaRuntime.current() == null && System.currentTimeMillis() < deadline) {
            Throwable failure = launchFailure.get();
            if (failure != null) {
                throw propagate(failure);
            }
            Thread.sleep(20L);
        }
        if (DoJaRuntime.current() == null) {
            throw new IllegalStateException("DoJa runtime did not initialize");
        }
    }

    private static DoJaRuntime requireRuntime(AtomicReference<Throwable> launchFailure) throws Exception {
        Throwable failure = launchFailure.get();
        if (failure != null) {
            throw propagate(failure);
        }
        DoJaRuntime runtime = DoJaRuntime.current();
        if (runtime == null) {
            throw new IllegalStateException("DoJa runtime exited before probe completed");
        }
        return runtime;
    }

    private static Exception propagate(Throwable throwable) {
        if (throwable instanceof Exception exception) {
            return exception;
        }
        return new IllegalStateException("Launch failed", throwable);
    }
}
