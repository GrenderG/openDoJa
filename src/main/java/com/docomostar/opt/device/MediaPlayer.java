package com.docomostar.opt.device;

import com.docomostar.fs.File;
import com.docomostar.fs._StorageExceptions;
import java.io.IOException;

/** STAR view of the shared host native-player service. */
public class MediaPlayer {
    public static final int STATUS_STOPPED = com.nttdocomo.opt.device.MediaPlayer.STATUS_STOPPED;
    public static final int STATUS_COMPLETED = com.nttdocomo.opt.device.MediaPlayer.STATUS_COMPLETED;
    public static final int STATUS_PREVIOUS_SELECTED = com.nttdocomo.opt.device.MediaPlayer.STATUS_PREVIOUS_SELECTED;
    public static final int STATUS_NEXT_SELECTED = com.nttdocomo.opt.device.MediaPlayer.STATUS_NEXT_SELECTED;
    public static final int STATUS_SUSPENDED = com.nttdocomo.opt.device.MediaPlayer.STATUS_SUSPENDED;
    public static final int STATUS_FAILED = com.nttdocomo.opt.device.MediaPlayer.STATUS_FAILED;
    private static final MediaPlayer INSTANCE = new MediaPlayer();
    private final com.nttdocomo.opt.device.MediaPlayer delegate = com.nttdocomo.opt.device.MediaPlayer.getMediaPlayer();

    MediaPlayer() { }
    public static MediaPlayer getMediaPlayer() { return INSTANCE; }
    public int play(File file) throws IOException { return play(file, 0L); }
    public int play(File file, long position) throws IOException {
        java.util.Objects.requireNonNull(file, "file");
        try { return delegate.play(file.hostFile(), position); }
        catch (IOException failure) { throw _StorageExceptions.translate(failure); }
    }
    public long getLastStoppedPosition() { return delegate.getLastStoppedPosition(); }
}
