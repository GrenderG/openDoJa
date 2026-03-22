package opendoja.demo;

import com.nttdocomo.ui.Display;
import com.nttdocomo.ui.IApplication;
import opendoja.host.DoJaRuntime;
import opendoja.host.JamLauncher;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.nio.file.Path;

/**
 * Inspects Monster Hunter's title-menu cursor sound state before and after the first DOWN input.
 */
public final class MonsterHunterSoundStateProbe {
    private static final int CURSOR_SOUND_INDEX = 31;
    private static final long DEFAULT_INITIAL_WAIT_MILLIS = 20_000L;

    private MonsterHunterSoundStateProbe() {
    }

    public static void main(String[] args) throws Exception {
        DemoLog.enableInfoLogging();
        long initialWaitMillis = args.length > 0 ? Long.parseLong(args[0]) : DEFAULT_INITIAL_WAIT_MILLIS;
        Path jam = Path.of("resources/sample_games/ENGLISH_PATCH__Monster_Hunter_i_for_SH_doja/bin/ENGLISH_PATCH__Monster_Hunter_i_for_SH.jam");
        IApplication app = JamLauncher.launch(jam, false);
        Field canvasField = app.getClass().getDeclaredField("a");
        canvasField.setAccessible(true);
        Object canvas = canvasField.get(app);
        Class<?> gameClass = canvas.getClass();

        Thread.sleep(initialWaitMillis);
        Field soundArrayField = findPreparedSoundArrayField(gameClass);
        Object soundArray = soundArrayField.get(null);
        Object cursorSound = Array.get(soundArray, CURSOR_SOUND_INDEX);
        Field preparedField = cursorSound.getClass().getDeclaredField("prepared");
        preparedField.setAccessible(true);
        Field presenterArrayField = findPresenterArrayField(gameClass);
        Object presenterArray = presenterArrayField.get(null);
        Object effectPresenter = Array.get(presenterArray, 1);
        Field resourceField = effectPresenter.getClass().getDeclaredField("resource");
        resourceField.setAccessible(true);
        Field mldPlayerField = effectPresenter.getClass().getDeclaredField("mldPlayer");
        mldPlayerField.setAccessible(true);
        Field sampledPlayerField = effectPresenter.getClass().getDeclaredField("sampledPlayer");
        sampledPlayerField.setAccessible(true);

        dump("before-down", cursorSound, preparedField, effectPresenter, resourceField, mldPlayerField, sampledPlayerField);
        press(Display.KEY_DOWN);
        Thread.sleep(800L);
        dump("after-down", cursorSound, preparedField, effectPresenter, resourceField, mldPlayerField, sampledPlayerField);

        DoJaRuntime.current().shutdown();
    }

    private static void dump(String label,
                             Object cursorSound,
                             Field preparedField,
                             Object effectPresenter,
                             Field resourceField,
                             Field mldPlayerField,
                             Field sampledPlayerField) throws ReflectiveOperationException {
        Object prepared = preparedField.get(cursorSound);
        String kind = prepared == null ? "unprepared" : prepared.getClass().getMethod("kind").invoke(prepared).toString();
        DemoLog.info(MonsterHunterSoundStateProbe.class, label
                + " soundClass=" + cursorSound.getClass().getName()
                + " prepared=" + (prepared != null)
                + " kind=" + kind
                + " presenterResource=" + describe(resourceField.get(effectPresenter))
                + " mldPlayer=" + describe(mldPlayerField.get(effectPresenter))
                + " sampledPlayer=" + describe(sampledPlayerField.get(effectPresenter)));
    }

    private static void press(int key) throws Exception {
        DoJaRuntime runtime = requireRuntime();
        runtime.dispatchSyntheticKey(key, Display.KEY_PRESSED_EVENT);
        Thread.sleep(200L);
        runtime.dispatchSyntheticKey(key, Display.KEY_RELEASED_EVENT);
    }

    private static Field findPreparedSoundArrayField(Class<?> owner) throws IllegalAccessException {
        for (Field field : owner.getDeclaredFields()) {
            if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            Class<?> type = field.getType();
            if (!type.isArray()) {
                continue;
            }
            if (!"com.nttdocomo.ui.MediaSound".equals(type.getComponentType().getName())) {
                continue;
            }
            field.setAccessible(true);
            Object array = field.get(null);
            if (array == null || Array.getLength(array) <= CURSOR_SOUND_INDEX) {
                continue;
            }
            if (Array.get(array, CURSOR_SOUND_INDEX) != null) {
                return field;
            }
        }
        throw new IllegalStateException("No populated MediaSound[] field found on " + owner.getName());
    }

    private static Field findPresenterArrayField(Class<?> owner) throws IllegalAccessException {
        for (Field field : owner.getDeclaredFields()) {
            if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            Class<?> type = field.getType();
            if (!type.isArray() || !"com.nttdocomo.ui.AudioPresenter".equals(type.getComponentType().getName())) {
                continue;
            }
            field.setAccessible(true);
            Object array = field.get(null);
            if (array == null || Array.getLength(array) <= 1 || Array.get(array, 1) == null) {
                continue;
            }
            return field;
        }
        throw new IllegalStateException("No populated AudioPresenter[] field found on " + owner.getName());
    }

    private static String describe(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }

    private static DoJaRuntime requireRuntime() {
        DoJaRuntime runtime = DoJaRuntime.current();
        if (runtime == null) {
            throw new IllegalStateException("DoJa runtime exited");
        }
        return runtime;
    }
}
