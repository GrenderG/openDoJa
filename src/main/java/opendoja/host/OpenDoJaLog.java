package opendoja.host;

import java.io.PrintStream;
import java.util.Locale;
import java.util.function.Supplier;

public final class OpenDoJaLog {
    private static final boolean PROPERTY_CONFIGURED = System.getProperty(OpenDoJaLaunchArgs.LOG_LEVEL) != null;
    private static volatile Level configuredLevel = Level.parse(System.getProperty(OpenDoJaLaunchArgs.LOG_LEVEL));
    private static volatile boolean configuredInCode;

    private OpenDoJaLog() {
    }

    public enum Level {
        OFF(0),
        ERROR(1),
        WARN(2),
        INFO(3),
        DEBUG(4);

        private final int priority;

        Level(int priority) {
            this.priority = priority;
        }

        private boolean includes(Level other) {
            return this != OFF && priority >= other.priority;
        }

        static Level parse(String rawValue) {
            if (rawValue == null) {
                return OFF;
            }
            return switch (rawValue.trim().toUpperCase(Locale.ROOT)) {
                case "", "OFF", "FALSE", "0" -> OFF;
                case "ERROR" -> ERROR;
                case "WARN", "WARNING" -> WARN;
                case "INFO", "TRUE", "1" -> INFO;
                case "DEBUG", "TRACE", "2" -> DEBUG;
                default -> OFF;
            };
        }
    }

    public static void configure(Level level) {
        configuredLevel = level == null ? Level.OFF : level;
        configuredInCode = true;
    }

    public static void configureIfUnset(Level level) {
        if (!PROPERTY_CONFIGURED && !configuredInCode) {
            configure(level);
        }
    }

    public static boolean isDebugEnabled() {
        return isEnabled(Level.DEBUG);
    }

    public static boolean isInfoEnabled() {
        return isEnabled(Level.INFO);
    }

    public static boolean isWarnEnabled() {
        return isEnabled(Level.WARN);
    }

    public static boolean isErrorEnabled() {
        return isEnabled(Level.ERROR);
    }

    public static boolean isEnabled(Level level) {
        return configuredLevel.includes(level);
    }

    public static void debug(Class<?> source, String message) {
        log(Level.DEBUG, source, message, null);
    }

    public static void debug(Class<?> source, Supplier<String> messageSupplier) {
        log(Level.DEBUG, source, messageSupplier, null);
    }

    public static void info(Class<?> source, String message) {
        log(Level.INFO, source, message, null);
    }

    public static void info(Class<?> source, Supplier<String> messageSupplier) {
        log(Level.INFO, source, messageSupplier, null);
    }

    public static void warn(Class<?> source, String message) {
        log(Level.WARN, source, message, null);
    }

    public static void warn(Class<?> source, Supplier<String> messageSupplier) {
        log(Level.WARN, source, messageSupplier, null);
    }

    public static void warn(Class<?> source, String message, Throwable throwable) {
        log(Level.WARN, source, message, throwable);
    }

    public static void error(Class<?> source, String message) {
        log(Level.ERROR, source, message, null);
    }

    public static void error(Class<?> source, Supplier<String> messageSupplier) {
        log(Level.ERROR, source, messageSupplier, null);
    }

    public static void error(Class<?> source, String message, Throwable throwable) {
        log(Level.ERROR, source, message, throwable);
    }

    public static void error(Class<?> source, Throwable throwable) {
        log(Level.ERROR, source, throwable == null ? "Unexpected failure" : throwable.toString(), throwable);
    }

    private static void log(Level level, Class<?> source, Supplier<String> messageSupplier, Throwable throwable) {
        if (!isEnabled(level)) {
            return;
        }
        String message;
        try {
            message = messageSupplier == null ? "" : messageSupplier.get();
        } catch (RuntimeException exception) {
            message = "log message supplier failed: " + exception;
            throwable = throwable == null ? exception : throwable;
            level = Level.ERROR;
        }
        write(level, source, message, throwable);
    }

    private static void log(Level level, Class<?> source, String message, Throwable throwable) {
        if (!isEnabled(level)) {
            return;
        }
        write(level, source, message, throwable);
    }

    private static void write(Level level, Class<?> source, String message, Throwable throwable) {
        PrintStream stream = level == Level.ERROR ? System.err : System.out;
        stream.print(level.name());
        stream.print(" [");
        stream.print(Thread.currentThread().getName());
        stream.print("] [");
        stream.print(source == null ? "unknown" : source.getSimpleName());
        stream.print("] ");
        stream.println(message == null ? "" : message);
        if (throwable != null) {
            throwable.printStackTrace(stream);
        }
    }
}
