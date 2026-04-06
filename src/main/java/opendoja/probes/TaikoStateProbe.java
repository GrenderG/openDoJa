package opendoja.probes;

import com.nttdocomo.ui.Canvas;
import com.nttdocomo.ui.Display;
import com.nttdocomo.ui.Frame;
import opendoja.host.DoJaRuntime;
import opendoja.host.JamLauncher;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Launches a Taiko sample and prints selected canvas fields after scripted input.
 */
public final class TaikoStateProbe {
    private static final String[] WATCH_FIELDS = {
            "b", "k", "l", "m", "n", "aa", "fe",
            "ib", "jb", "kb", "lb", "mb", "nb", "ob", "ud",
            "dd", "ed"
    };

    private TaikoStateProbe() {
    }

    public static void main(String[] args) throws Exception {
        DemoLog.enableInfoLogging();
        if (args.length < 2 || ((args.length - 2) % 2) != 0) {
            throw new IllegalArgumentException(
                    "Usage: TaikoStateProbe <jam-path> <initial-delay-ms> [<key> <after-ms>]...");
        }

        Path jamPath = Path.of(args[0]);
        long initialDelay = Long.parseLong(args[1]);

        Thread launchThread = new Thread(() -> {
            try {
                JamLauncher.launch(jamPath);
            } catch (Throwable throwable) {
                DemoLog.error(TaikoStateProbe.class, "Launch failed", throwable);
            }
        }, "taiko-state-probe-launch");
        launchThread.setDaemon(true);
        launchThread.start();

        Throwable failure = null;
        try {
            waitForRuntime();
            Thread.sleep(Math.max(0L, initialDelay));
            dumpState("initial");
            for (int i = 2; i < args.length; i += 2) {
                int key = parseKey(args[i]);
                long afterMillis = Long.parseLong(args[i + 1]);
                DoJaRuntime runtime = requireRuntime();
                runtime.dispatchSyntheticKey(key, Display.KEY_PRESSED_EVENT);
                Thread.sleep(200L);
                runtime.dispatchSyntheticKey(key, Display.KEY_RELEASED_EVENT);
                Thread.sleep(Math.max(0L, afterMillis));
                dumpState("after " + args[i]);
            }
        } catch (Throwable throwable) {
            failure = throwable;
            DemoLog.error(TaikoStateProbe.class, "Probe failed", throwable);
        } finally {
            DoJaRuntime runtime = DoJaRuntime.current();
            if (runtime != null) {
                runtime.shutdown();
            }
            System.exit(failure == null ? 0 : 1);
        }
    }

    private static void dumpState(String label) throws Exception {
        Frame frame = requireRuntime().getCurrentFrame();
        System.out.println("STATE " + label);
        if (frame == null) {
            System.out.println("  frame=null");
            return;
        }
        System.out.println("  frameClass=" + frame.getClass().getName());
        if (!(frame instanceof Canvas canvas)) {
            return;
        }
        Class<?> type = canvas.getClass();
        Map<String, Object> values = new LinkedHashMap<>();
        for (String name : WATCH_FIELDS) {
            Field field = findField(type, name);
            if (field == null) {
                continue;
            }
            field.setAccessible(true);
            values.put(name, summarize(field.get(canvas)));
        }
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            System.out.println("  " + entry.getKey() + "=" + entry.getValue());
        }
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Object summarize(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Thread thread) {
            return thread.getName() + "{alive=" + thread.isAlive() + ", state=" + thread.getState() + "}";
        }
        Class<?> type = value.getClass();
        if (!type.isArray()) {
            return value;
        }
        int length = Array.getLength(value);
        StringBuilder builder = new StringBuilder();
        builder.append(type.getComponentType().getSimpleName()).append('[').append(length).append(']');
        builder.append(" first=");
        int preview = Math.min(length, 8);
        builder.append('[');
        for (int i = 0; i < preview; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            Object element = Array.get(value, i);
            builder.append(element);
        }
        if (length > preview) {
            builder.append(", ...");
        }
        builder.append(']');
        return builder.toString();
    }

    private static int parseKey(String keyName) {
        return switch (keyName.toUpperCase()) {
            case "SELECT", "ENTER" -> Display.KEY_SELECT;
            case "LEFT" -> Display.KEY_LEFT;
            case "RIGHT" -> Display.KEY_RIGHT;
            case "UP" -> Display.KEY_UP;
            case "DOWN" -> Display.KEY_DOWN;
            case "SOFT1" -> Display.KEY_SOFT1;
            case "SOFT2" -> Display.KEY_SOFT2;
            default -> throw new IllegalArgumentException("Unsupported key: " + keyName);
        };
    }

    private static void waitForRuntime() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000L;
        while (DoJaRuntime.current() == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(20L);
        }
        if (DoJaRuntime.current() == null) {
            throw new IllegalStateException("DoJa runtime did not initialize");
        }
    }

    private static DoJaRuntime requireRuntime() {
        DoJaRuntime runtime = DoJaRuntime.current();
        if (runtime == null) {
            throw new IllegalStateException("DoJa runtime exited before probe completed");
        }
        return runtime;
    }
}
