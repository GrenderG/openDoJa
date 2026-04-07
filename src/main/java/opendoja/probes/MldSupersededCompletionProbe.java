package opendoja.probes;

import opendoja.audio.mld.MLDPCMPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Verifies that a queued completion from an obsolete playback lifecycle is
 * dropped after the player is stopped.
 */
public final class MldSupersededCompletionProbe {
    private MldSupersededCompletionProbe() {
    }

    public static void main(String[] args) throws Exception {
        AtomicInteger completes = new AtomicInteger();
        MLDPCMPlayer player = new MLDPCMPlayer(new MLDPCMPlayer.Listener() {
            @Override
            public void onLoop() {
            }

            @Override
            public void onSync(int timeMillis) {
            }

            @Override
            public void onComplete() {
                completes.incrementAndGet();
            }

            @Override
            public void onFailure(Exception exception) {
                throw new AssertionError("unexpected failure callback", exception);
            }
        });

        Object handle = getHandle(player);
        setBoolean(handle, "completionPending", true);
        setBoolean(handle, "completionNeedsCurrentWrite", false);
        setLong(handle, "completionTargetFrame", 0L);
        setLong(handle, "completionGeneration", getLong(handle, "generation"));

        List<Runnable> notifications = new ArrayList<>();
        Method dispatchReadyCompletion = handle.getClass().getDeclaredMethod(
                "dispatchReadyCompletion", long.class, List.class);
        dispatchReadyCompletion.setAccessible(true);
        dispatchReadyCompletion.invoke(handle, 0L, notifications);
        if (notifications.isEmpty()) {
            throw new AssertionError("expected a queued completion notification");
        }

        player.stop();
        notifications.forEach(Runnable::run);
        player.close();

        if (completes.get() != 0) {
            throw new AssertionError("stale completion escaped after stop()");
        }
        System.out.println("MldSupersededCompletionProbe OK");
    }

    private static Object getHandle(MLDPCMPlayer player) throws Exception {
        Field handleField = MLDPCMPlayer.class.getDeclaredField("handle");
        handleField.setAccessible(true);
        return handleField.get(player);
    }

    private static void setBoolean(Object target, String fieldName, boolean value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setBoolean(target, value);
    }

    private static void setLong(Object target, String fieldName, long value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setLong(target, value);
    }

    private static long getLong(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getLong(target);
    }
}
