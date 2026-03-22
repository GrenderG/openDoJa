package opendoja.demo;

import opendoja.host.OpenDoJaLog;

import java.util.function.Supplier;

final class DemoLog {
    private DemoLog() {
    }

    static void enableInfoLogging() {
        OpenDoJaLog.configureIfUnset(OpenDoJaLog.Level.INFO);
    }

    static void info(Class<?> source, String message) {
        OpenDoJaLog.info(source, message);
    }

    static void info(Class<?> source, Supplier<String> messageSupplier) {
        OpenDoJaLog.info(source, messageSupplier);
    }

    static void error(Class<?> source, String message, Throwable throwable) {
        OpenDoJaLog.error(source, message, throwable);
    }
}
