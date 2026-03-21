package opendoja.demo;

import com.nttdocomo.ui.IApplication;
import opendoja.host.DoJaRuntime;

import java.util.concurrent.TimeUnit;

public final class BlockingTerminateDemo extends IApplication {
    @Override
    public void start() {
        DoJaRuntime runtime = DoJaRuntime.current();
        if (runtime == null) {
            terminate();
            return;
        }
        runtime.scheduler().schedule(this::terminate, 250, TimeUnit.MILLISECONDS);
        try {
            Thread.sleep(60_000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
