package opendoja.demo;

import opendoja.host.DoJaRuntime;
import opendoja.host.JamLauncher;

import java.nio.file.Path;

public final class ShutdownProbe {
    private ShutdownProbe() {
    }

    public static void main(String[] args) throws Exception {
        DemoLog.enableInfoLogging();
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: ShutdownProbe <jam-path> <delay-ms>");
        }
        Path jamPath = Path.of(args[0]);
        long delayMillis = Long.parseLong(args[1]);
        DemoLog.info(ShutdownProbe.class, "probe-launch");
        JamLauncher.launch(jamPath);
        DemoLog.info(ShutdownProbe.class, "probe-launched");
        Thread.sleep(Math.max(0L, delayMillis));
        DemoLog.info(ShutdownProbe.class, "probe-shutdown-start");
        DoJaRuntime runtime = DoJaRuntime.current();
        if (runtime == null) {
            throw new IllegalStateException("DoJa runtime did not initialize");
        }
        runtime.shutdown();
        runtime.awaitShutdown();
        DemoLog.info(ShutdownProbe.class, "probe-shutdown-done");
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.isAlive() && !thread.isDaemon() && thread != Thread.currentThread()) {
                DemoLog.info(ShutdownProbe.class, () -> "non-daemon-thread=" + thread.getName() + " state=" + thread.getState());
            }
        }
        DemoLog.info(ShutdownProbe.class, "shutdown-complete");
    }
}
