package opendoja.probes;

import com.nttdocomo.ui.AudioPresenter;
import com.nttdocomo.ui.Font;
import com.nttdocomo.ui.MediaImage;
import com.nttdocomo.ui.MediaManager;
import com.nttdocomo.ui.MediaSound;
import opendoja.audio.MidiEventPlayer;
import opendoja.host.DoJaRuntime;
import opendoja.host.LaunchConfig;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;

public final class BmsPlayerMidiKeySoundProbe {
    private BmsPlayerMidiKeySoundProbe() {
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        int iterations = args.length >= 1 ? Integer.parseInt(args[0]) : 64;
        double maxAllowedP95Millis = args.length >= 2 ? Double.parseDouble(args[1]) : Double.POSITIVE_INFINITY;

        Class<?> rawAppClass = Class.forName("mdj");
        Class<? extends com.nttdocomo.ui.IApplication> appClass =
                (Class<? extends com.nttdocomo.ui.IApplication>) rawAppClass;
        LaunchConfig config = LaunchConfig.builder(appClass)
                .externalFrameEnabled(false)
                .parameter("UseStorage", "1")
                .scratchpadSizes(new int[]{1024, 10240, 102400})
                .scratchpadPackedFile(Path.of("resources/sample_games/BMS Player/BMS Player/mdj_qvga.sp"))
                .build();
        DoJaRuntime.prepareLaunch(config);
        DoJaRuntime runtime = DoJaRuntime.bootstrap(config);
        AudioPresenter[] presenters = {
                AudioPresenter.getAudioPresenter(),
                AudioPresenter.getAudioPresenter()
        };
        try {
            runtime.attachApplication((com.nttdocomo.ui.IApplication) appClass.getDeclaredConstructor().newInstance());

            Class<?> canClass = Class.forName("can");
            Object can = instantiate(canClass);
            initializeFonts(can);
            loadBuiltInImages(canClass, can);
            loadBmsList(canClass, can);

            setInt(can, "select", 0);
            Object bmsList = getField(can, "bmsList");
            Object selectedBms = invoke(bmsList.getClass(), bmsList, "get", int.class, 0);
            setInt(can, "bpm", (Integer) getField(selectedBms, "bpm"));
            setInt(can, "total", (Integer) getField(selectedBms, "total"));
            invoke(canClass, can, "LoadBmsNotes");
            MediaSound[] sounds = new MediaSound[8];
            for (int i = 0; i < sounds.length; i++) {
                sounds[i] = (MediaSound) invoke(canClass, can, "makeShortMID", int.class, i);
            }
            int directCandidates = 0;
            for (MediaSound sound : sounds) {
                Object prepared = invoke(sound.getClass(), sound, "prepared");
                if (MidiEventPlayer.isLowLatencyCandidate((MediaManager.PreparedSound) prepared)) {
                    directCandidates++;
                }
            }
            int lanes = Math.min(8, sounds.length);
            long[] samples = new long[iterations * lanes];
            int sampleCount = 0;
            boolean useFirst = true;
            for (int i = 0; i < iterations; i++) {
                for (int lane = 0; lane < lanes; lane++) {
                    MediaSound sound = sounds[lane];
                    if (sound == null) {
                        continue;
                    }
                    AudioPresenter presenter = presenters[useFirst ? 0 : 1];
                    useFirst = !useFirst;
                    long start = System.nanoTime();
                    try {
                        presenter.stop();
                    } catch (Exception ignored) {
                    }
                    presenter.setSound(sound);
                    presenter.play();
                    samples[sampleCount++] = System.nanoTime() - start;
                }
            }

            Arrays.sort(samples, 0, sampleCount);
            double avgMillis = averageMillis(samples, sampleCount);
            double p95Millis = percentileMillis(samples, sampleCount, 95);
            double maxMillis = sampleCount == 0 ? 0.0d : samples[sampleCount - 1] / 1_000_000.0d;
            System.out.printf("bms-midi-key-sound samples=%d directCandidates=%d avgMs=%.3f p95Ms=%.3f maxMs=%.3f%n",
                    sampleCount, directCandidates, avgMillis, p95Millis, maxMillis);
            if (p95Millis > maxAllowedP95Millis) {
                throw new IllegalStateException("BMS MIDI key-sound p95 " + p95Millis
                        + " ms exceeds " + maxAllowedP95Millis + " ms");
            }
        } finally {
            for (AudioPresenter presenter : presenters) {
                try {
                    presenter.close();
                } catch (Exception ignored) {
                }
            }
            runtime.shutdown();
            DoJaRuntime.clearPreparedLaunch();
        }
    }

    private static double averageMillis(long[] samples, int count) {
        if (count == 0) {
            return 0.0d;
        }
        long total = 0L;
        for (int i = 0; i < count; i++) {
            total += samples[i];
        }
        return total / 1_000_000.0d / count;
    }

    private static double percentileMillis(long[] samples, int count, int percentile) {
        if (count == 0) {
            return 0.0d;
        }
        int index = Math.max(0, Math.min(count - 1, (int) Math.ceil(count * (percentile / 100.0d)) - 1));
        return samples[index] / 1_000_000.0d;
    }

    private static void initializeFonts(Object can) throws Exception {
        Font font = Font.getDefaultFont();
        setField(can, "SYSTEM", font);
        setField(can, "L_BOLD", font);
        setInt(can, "stringh", font.getHeight());
        setInt(can, "stringhLB", font.getHeight());
    }

    private static void loadBuiltInImages(Class<?> canClass, Object can) throws Exception {
        Field imgField = canClass.getDeclaredField("img");
        imgField.setAccessible(true);
        Object[] img = (Object[]) imgField.get(can);
        for (int i = 0; i <= 26; i++) {
            String name = "resource:///" + i + ".gif";
            try {
                MediaImage image = MediaManager.getImage(name);
                image.use();
                img[i] = image.getImage();
            } catch (Exception ignored) {
                img[i] = null;
            }
        }
    }

    private static void loadBmsList(Class<?> canClass, Object can) throws Exception {
        Class<?> zipUtilClass = Class.forName("ZipUtil");
        Object zipUtil = instantiate(zipUtilClass, int.class, String.class, 0, "BMS.ZIP");
        invoke(zipUtilClass, zipUtil, "useSD");
        Object fileList = invoke(zipUtilClass, zipUtil, "getList");

        setField(can, "zu", zipUtil);
        setField(can, "fileList", fileList);
        setField(can, "bmsList", instantiate(Class.forName("ArrayListOR")));

        int fileListCount = Array.getLength(fileList);
        int loadedCount = (Integer) invoke(canClass, can, "loadAllBmsData", boolean.class, false);
        if (loadedCount <= 0) {
            throw new IllegalStateException("No BMS entries loaded from " + fileListCount + " ZIP entries");
        }
    }

    private static void setInt(Object target, String name, int value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(target, value);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Object instantiate(Class<?> type, Class<?> argType1, Class<?> argType2, Object arg1, Object arg2)
            throws Exception {
        Constructor<?> constructor = type.getDeclaredConstructor(argType1, argType2);
        constructor.setAccessible(true);
        return constructor.newInstance(arg1, arg2);
    }

    private static Object instantiate(Class<?> type) throws Exception {
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static Object invoke(Class<?> type, Object target, String name, Class<?> argType, Object arg) throws Exception {
        Method method = type.getDeclaredMethod(name, argType);
        method.setAccessible(true);
        return method.invoke(target, arg);
    }

    private static Object invoke(Class<?> type, Object target, String name) throws Exception {
        Method method = type.getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }
}
