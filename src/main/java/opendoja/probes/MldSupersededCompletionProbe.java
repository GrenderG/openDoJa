package opendoja.probes;

import opendoja.audio.mld.MLDPCMPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Verifies that queued terminal callbacks keep the originating playback token
 * so the presenter layer can make stale/current ownership decisions itself.
 */
public final class MldSupersededCompletionProbe {
    private MldSupersededCompletionProbe() {
    }

    public static void main(String[] args) throws Exception {
        AtomicInteger completes = new AtomicInteger();
        AtomicLong completionToken = new AtomicLong(Long.MIN_VALUE);
        MLDPCMPlayer player = new MLDPCMPlayer(new MLDPCMPlayer.Listener() {
            @Override
            public void onLoop(long playbackToken) {
            }

            @Override
            public void onSync(int timeMillis, long playbackToken) {
            }

            @Override
            public void onComplete(long playbackToken) {
                completionToken.set(playbackToken);
                completes.incrementAndGet();
            }

            @Override
            public void onFailure(Exception exception, long playbackToken) {
                throw new AssertionError("unexpected failure callback", exception);
            }
        });

        Object handle = getHandle(player);
        setBoolean(handle, "completionPending", true);
        setBoolean(handle, "completionNeedsCurrentWrite", false);
        setLong(handle, "completionTargetFrame", 0L);
        setLong(handle, "completionPlaybackToken", 42L);

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

        if (completes.get() != 1) {
            throw new AssertionError("expected one completion callback but saw " + completes.get());
        }
        if (completionToken.get() != 42L) {
            throw new AssertionError("completion token mismatch: " + completionToken.get());
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
}
