package com.docomostar.device;

import java.util.function.Supplier;
import com.docomostar.system.InterruptedOperationException;

/** Keeps STAR catch clauses working when an existing DoJa device service fails. */
public final class _DeviceExceptions {
    private _DeviceExceptions() { }

    public static RuntimeException translate(RuntimeException failure) {
        if (failure instanceof com.nttdocomo.device.DeviceException device) return DeviceException.from(device);
        if (failure instanceof com.nttdocomo.device.BluetoothException bluetooth) return BluetoothException.from(bluetooth);
        if (failure instanceof com.nttdocomo.lang.UnsupportedOperationException) {
            var translated = new com.docomostar.lang.UnsupportedOperationException(failure.getMessage());
            translated.initCause(failure);
            return translated;
        }
        return failure;
    }

    public static <T> T call(Supplier<T> action) {
        try { return action.get(); }
        catch (RuntimeException failure) { throw translate(failure); }
    }

    public static void run(Runnable action) {
        call(() -> { action.run(); return null; });
    }

    public static <T> T interruptible(InterruptibleSupplier<T> action) throws InterruptedOperationException {
        try { return action.get(); }
        catch (com.nttdocomo.system.InterruptedOperationException failure) {
            var translated = new InterruptedOperationException(failure.getMessage());
            translated.initCause(failure);
            throw translated;
        } catch (RuntimeException failure) { throw translate(failure); }
    }

    @FunctionalInterface
    public interface InterruptibleSupplier<T> {
        T get() throws com.nttdocomo.system.InterruptedOperationException;
    }
}
