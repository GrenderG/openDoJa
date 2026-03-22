package opendoja.demo;

import com.nttdocomo.ui.Display;
import com.nttdocomo.ui.IApplication;
import opendoja.host.DoJaRuntime;
import opendoja.host.JamLauncher;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;

/**
 * Samples Monster Hunter's frame timestamp around scripted key presses.
 */
public final class MonsterHunterUiTimingProbe {
    private MonsterHunterUiTimingProbe() {
    }

    public static void main(String[] args) throws Exception {
        DemoLog.enableInfoLogging();
        Path jam = Path.of("resources/sample_games/ENGLISH_PATCH__Monster_Hunter_i_for_SH_doja/bin/ENGLISH_PATCH__Monster_Hunter_i_for_SH.jam");
        IApplication app = JamLauncher.launch(jam, false);
        Field canvasField = app.getClass().getDeclaredField("a");
        canvasField.setAccessible(true);
        Object canvas = canvasField.get(app);
        Class<?> gameClass = canvas.getClass();
        Field frameMillisField = frameMillisField(gameClass);

        Thread.sleep(20_000L);
        sampleWindow("title-idle", frameMillisField, 1_500L);
        press(Display.KEY_DOWN);
        sampleWindow("title-down", frameMillisField, 2_000L);
        press(Display.KEY_SELECT);
        sampleWindow("dialog-first-page", frameMillisField, 2_500L);
        press(Display.KEY_SELECT);
        sampleWindow("dialog-advance", frameMillisField, 2_000L);

        DoJaRuntime.current().shutdown();
    }

    private static Field frameMillisField(Class<?> gameClass) {
        for (Field field : gameClass.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == long.class) {
                field.setAccessible(true);
                return field;
            }
        }
        throw new IllegalStateException("No static long timing field found on " + gameClass.getName());
    }

    private static void press(int key) throws Exception {
        DoJaRuntime runtime = requireRuntime();
        runtime.dispatchSyntheticKey(key, Display.KEY_PRESSED_EVENT);
        Thread.sleep(200L);
        runtime.dispatchSyntheticKey(key, Display.KEY_RELEASED_EVENT);
    }

    private static void sampleWindow(String label, Field frameMillisField, long durationMillis) throws Exception {
        long deadline = System.currentTimeMillis() + durationMillis;
        long lastValue = frameMillisField.getLong(null);
        long lastChangeAt = System.nanoTime();
        long maxGapNanos = 0L;
        int frameChanges = 0;
        while (System.currentTimeMillis() < deadline) {
            long currentValue = frameMillisField.getLong(null);
            if (currentValue != lastValue) {
                long now = System.nanoTime();
                maxGapNanos = Math.max(maxGapNanos, now - lastChangeAt);
                lastChangeAt = now;
                lastValue = currentValue;
                frameChanges++;
            }
            Thread.sleep(5L);
        }
        long tailGap = System.nanoTime() - lastChangeAt;
        maxGapNanos = Math.max(maxGapNanos, tailGap);
        DemoLog.info(MonsterHunterUiTimingProbe.class, label
                + " frameChanges=" + frameChanges
                + " maxGapMs=" + (maxGapNanos / 1_000_000.0)
                + " tailGapMs=" + (tailGap / 1_000_000.0));
    }

    private static DoJaRuntime requireRuntime() {
        DoJaRuntime runtime = DoJaRuntime.current();
        if (runtime == null) {
            throw new IllegalStateException("DoJa runtime exited");
        }
        return runtime;
    }
}
