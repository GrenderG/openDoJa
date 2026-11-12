package com.nttdocomo.ui;

import com.nttdocomo.io.ConnectionException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Host bridge for STAR's named image handles: acquisition occurs during use(). */
public final class _DeferredMediaImage extends MediaManager.AbstractMediaResource implements MediaImage {
    private final String location;
    private final Map<String, String> pendingProperties = new HashMap<>();
    private volatile MediaImage image;

    public _DeferredMediaImage(String location) {
        this.location = Objects.requireNonNull(location, "location");
    }

    /** Native consumers that inspect animated image frames need the prepared backend object. */
    public static MediaResource preparedResource(MediaResource resource) {
        if (resource instanceof _DeferredMediaImage deferred && deferred.image != null) return deferred.image;
        return resource;
    }

    private MediaImage prepare() throws ConnectionException {
        if (state() == MediaResource.DISPOSE) {
            throw new UIException(UIException.ILLEGAL_STATE, "Media image is disposed");
        }
        if (image == null) {
            try (InputStream input = MediaManager.openNamedInputStream(location)) {
                MediaImage loaded = MediaManager.getImage(input);
                for (var entry : pendingProperties.entrySet()) loaded.setProperty(entry.getKey(), entry.getValue());
                loaded.setRedistributable(super.isRedistributable());
                image = loaded;
                pendingProperties.clear();
            } catch (IOException failure) {
                if (failure instanceof ConnectionException connection) throw connection;
                ConnectionException translated = new ConnectionException(ConnectionException.NO_RESOURCES, failure.getMessage());
                translated.initCause(failure);
                throw translated;
            }
        }
        return image;
    }

    private MediaImage preparedImage() {
        MediaImage prepared = image;
        if (prepared == null || state() == MediaResource.DISPOSE) {
            throw new UIException(UIException.ILLEGAL_STATE, "Media image has not been prepared");
        }
        return prepared;
    }

    @Override public synchronized void use() throws ConnectionException {
        prepare().use();
        super.use();
    }

    @Override public synchronized void use(MediaResource overwritten, boolean useOnce) throws ConnectionException {
        prepare().use(preparedResource(overwritten), useOnce);
        super.use();
    }

    @Override public synchronized void unuse() {
        if (image != null) image.unuse();
        super.unuse();
    }

    @Override public synchronized void dispose() {
        if (image != null) image.dispose();
        super.dispose();
    }

    @Override public synchronized void setProperty(String key, String value) {
        super.setProperty(key, value);
        if (image == null) pendingProperties.put(key, value); else image.setProperty(key, value);
    }

    @Override public synchronized boolean setRedistributable(boolean redistributable) {
        super.setRedistributable(redistributable);
        return image == null || image.setRedistributable(redistributable);
    }

    @Override public int getWidth() { return preparedImage().getWidth(); }
    @Override public int getHeight() { return preparedImage().getHeight(); }
    @Override public Image getImage() { return preparedImage().getImage(); }
    @Override public ExifData getExifData() { return preparedImage().getExifData(); }
    @Override public void setExifData(ExifData exifData) { preparedImage().setExifData(exifData); }
}
