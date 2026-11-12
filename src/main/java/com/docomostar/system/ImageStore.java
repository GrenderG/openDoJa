package com.docomostar.system;

import com.docomostar.media.MediaImage;
import com.docomostar.media.MediaManager;
import java.io.InputStream;

/** STAR view of the existing DoJa managed-image store and its encoded image data. */
public final class ImageStore {
    private final com.nttdocomo.system.ImageStore delegate;
    private final MediaImage image;

    private ImageStore(com.nttdocomo.system.ImageStore delegate) {
        this.delegate = delegate;
        image = MediaManager.wrapImage(delegate.getImage());
    }
    private static ImageStore wrap(com.nttdocomo.system.ImageStore entry) {
        return entry == null ? null : new ImageStore(entry);
    }
    private static InterruptedOperationException interrupted(com.nttdocomo.system.InterruptedOperationException failure) {
        InterruptedOperationException translated = new InterruptedOperationException(failure.getMessage());
        translated.initCause(failure);
        return translated;
    }
    public static ImageStore selectEntry() throws InterruptedOperationException {
        try { return wrap(com.nttdocomo.system.ImageStore.selectEntry()); }
        catch (com.nttdocomo.system.InterruptedOperationException failure) { throw interrupted(failure); }
    }
    public static int selectEntryId() throws InterruptedOperationException {
        try { return com.nttdocomo.system.ImageStore.selectEntryId(); }
        catch (com.nttdocomo.system.InterruptedOperationException failure) { throw interrupted(failure); }
    }
    public static ImageStore getEntry(int id) throws StoreException {
        try { return wrap(com.nttdocomo.system.ImageStore.getEntry(id)); }
        catch (com.nttdocomo.system.StoreException failure) { throw StoreException.from(failure); }
    }
    public static int[] getEntryIds(int folder) throws StoreException {
        try { return com.nttdocomo.system.ImageStore.getEntryIds(folder); }
        catch (com.nttdocomo.system.StoreException failure) { throw StoreException.from(failure); }
    }
    public static int addEntry(MediaImage image) throws InterruptedOperationException {
        try { return com.nttdocomo.system.ImageStore.addEntry((com.nttdocomo.ui.MediaImage) MediaManager.unwrap(image)); }
        catch (com.nttdocomo.system.InterruptedOperationException failure) { throw interrupted(failure); }
    }
    public static int addEntry(MediaImage image, boolean exclusive) throws InterruptedOperationException {
        try { return com.nttdocomo.system.ImageStore.addEntry((com.nttdocomo.ui.MediaImage) MediaManager.unwrap(image), exclusive); }
        catch (com.nttdocomo.system.InterruptedOperationException failure) { throw interrupted(failure); }
    }
    public static int[] addEntry(MediaImage[] images) throws InterruptedOperationException {
        com.nttdocomo.ui.MediaImage[] converted = new com.nttdocomo.ui.MediaImage[images.length];
        for (int i = 0; i < images.length; i++) converted[i] = (com.nttdocomo.ui.MediaImage) MediaManager.unwrap(images[i]);
        try { return com.nttdocomo.system.ImageStore.addEntry(converted); }
        catch (com.nttdocomo.system.InterruptedOperationException failure) { throw interrupted(failure); }
    }
    public final int getId() { return delegate.getId(); }
    public final MediaImage getImage() { return image; }
    public final InputStream getInputStream() { return delegate.getInputStream(); }
}
