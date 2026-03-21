package opendoja.demo;

import opendoja.host.DoJaRuntime;
import opendoja.host.JamLauncher;

import java.nio.file.Path;

public final class ShutdownProbe {
    private ShutdownProbe() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: ShutdownProbe <jam-path> <delay-ms>");
        }
        Path jamPath = Path.of(args[0]);
        long delayMillis = Long.parseLong(args[1]);
        System.out.println("probe-launch");
        JamLauncher.launch(jamPath);
        System.out.println("probe-launched");
        Thread.sleep(Math.max(0L, delayMillis));
        System.out.println("probe-shutdown-start");
        DoJaRuntime runtime = DoJaRuntime.current();
        if (runtime == null) {
            throw new IllegalStateException("DoJa runtime did not initialize");
        }
        runtime.shutdown();
        runtime.awaitShutdown();
        System.out.println("probe-shutdown-done");
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.isAlive() && !thread.isDaemon() && thread != Thread.currentThread()) {
                System.out.println("non-daemon-thread=" + thread.getName() + " state=" + thread.getState());
            }
        }
        System.out.println("shutdown-complete");
    }
}
