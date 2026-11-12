package com.docomostar.media;

import com.nttdocomo.lang.IterationAbortedException;
import com.nttdocomo.ui.AvatarData;
import com.nttdocomo.ui.ExifData;
import com.docomostar.ui.Image;
import com.nttdocomo.ui.MediaData;

import java.io.InputStream;

public final class MediaManager {
    private MediaManager() {
    }

    private static <T> T media(java.util.function.Supplier<T> action) {
        try { return action.get(); }
        catch (com.nttdocomo.ui.UIException failure) { throw MediaException.from(failure); }
    }

    /** Host integration: resource adapters must not hide the backend's prepared media. */
    public static com.nttdocomo.ui.MediaResource unwrap(MediaResource resource) {
        if (resource == null) return null;
        if (resource instanceof MediaSoundAdapter sound) return sound.delegate;
        if (resource instanceof MediaImageAdapter image) return com.nttdocomo.ui._DeferredMediaImage.preparedResource(image.delegate);
        if (resource instanceof com.nttdocomo.ui.MediaResource host) return host;
        throw new IllegalArgumentException("Resource was not created by MediaManager");
    }

    public static MediaData getData(String name) {
        return com.nttdocomo.ui.MediaManager.getData(name);
    }

    public static MediaData getData(InputStream inputStream) {
        return com.nttdocomo.ui.MediaManager.getData(inputStream);
    }

    public static MediaData getData(byte[] data) {
        return com.nttdocomo.ui.MediaManager.getData(data);
    }

    public static MediaImage getImage(String name) {
        return adaptImage(new com.nttdocomo.ui._DeferredMediaImage(name));
    }

    public static MediaImage getImage(InputStream inputStream) {
        return media(() -> adaptImage(com.nttdocomo.ui.MediaManager.getImage(inputStream)));
    }

    public static MediaImage getImage(byte[] data) {
        return media(() -> adaptImage(com.nttdocomo.ui.MediaManager.getImage(data)));
    }

    public static MediaImage getStreamingImage(String location, String contentType) {
        return media(() -> adaptImage(com.nttdocomo.ui.MediaManager.getStreamingImage(location, contentType)));
    }

    public static MediaSound getSound(String name) {
        return media(() -> adaptSound(com.nttdocomo.ui.MediaManager.getSound(name)));
    }

    public static MediaSound getSound(InputStream inputStream) {
        return media(() -> adaptSound(com.nttdocomo.ui.MediaManager.getSound(inputStream)));
    }

    public static MediaSound getSound(byte[] data) {
        return media(() -> adaptSound(com.nttdocomo.ui.MediaManager.getSound(data)));
    }

    public static AvatarData getAvatarData(String name) {
        return com.nttdocomo.ui.MediaManager.getAvatarData(name);
    }

    public static AvatarData getAvatarData(InputStream inputStream) {
        return com.nttdocomo.ui.MediaManager.getAvatarData(inputStream);
    }

    public static AvatarData getAvatarData(byte[] data) {
        return com.nttdocomo.ui.MediaManager.getAvatarData(data);
    }

    public static void use(MediaImage[] mediaImages, boolean exclusive) throws IterationAbortedException {
        if (mediaImages == null) return;
        var images = new com.nttdocomo.ui.MediaImage[mediaImages.length];
        for (int i = 0; i < images.length; i++) images[i] = (com.nttdocomo.ui.MediaImage) unwrap(mediaImages[i]);
        com.nttdocomo.ui.MediaManager.use(images, exclusive);
    }

    public static void use(MediaSound[] mediaSounds, boolean exclusive) throws IterationAbortedException {
        if (mediaSounds == null) return;
        var sounds = new com.nttdocomo.ui.MediaSound[mediaSounds.length];
        for (int i = 0; i < sounds.length; i++) sounds[i] = (com.nttdocomo.ui.MediaSound) unwrap(mediaSounds[i]);
        com.nttdocomo.ui.MediaManager.use(sounds, exclusive);
    }

    public static MediaImage createMediaImage(int width, int height) {
        return media(() -> adaptImage(com.nttdocomo.ui.MediaManager.createMediaImage(width, height)));
    }

    public static MediaSound createMediaSound(int size) {
        return media(() -> adaptSound(com.nttdocomo.ui.MediaManager.createMediaSound(size)));
    }

    public static MediaImage wrapImage(com.nttdocomo.ui.MediaImage image) { return adaptImage(image); }

    private static MediaImage adaptImage(com.nttdocomo.ui.MediaImage image) {
        if (image == null) {
            return null;
        }
        if (image instanceof MediaImage mediaImage) {
            return mediaImage;
        }
        return new MediaImageAdapter(image);
    }

    private static MediaSound adaptSound(com.nttdocomo.ui.MediaSound sound) {
        if (sound == null) {
            return null;
        }
        if (sound instanceof MediaSound mediaSound) {
            return mediaSound;
        }
        return new MediaSoundAdapter(sound);
    }

    private static final class MediaImageAdapter implements MediaImage {
        private final com.nttdocomo.ui.MediaImage delegate;
        private com.nttdocomo.ui.Image hostImage;
        private Image image;

        private MediaImageAdapter(com.nttdocomo.ui.MediaImage delegate) {
            this.delegate = delegate;
        }

        @Override
        public int getWidth() {
            return media(delegate::getWidth);
        }

        @Override
        public int getHeight() {
            return media(delegate::getHeight);
        }

        @Override
        public synchronized Image getImage() {
            var current = media(delegate::getImage);
            if (current != hostImage) { hostImage = current; image = Image.wrap(current); }
            return image;
        }

        @Override
        public ExifData getExifData() {
            return delegate.getExifData();
        }

        @Override
        public void setExifData(ExifData exifData) {
            delegate.setExifData(exifData);
        }

        @Override
        public void use() throws com.docomostar.io.ConnectionException {
            try { delegate.use(); }
            catch (com.nttdocomo.io.ConnectionException failure) { throw com.docomostar.io.ConnectionException.from(failure); }
            catch (com.nttdocomo.ui.UIException failure) { throw MediaException.from(failure); }
        }

        @Override
        public void use(MediaResource other, boolean exclusive) throws com.docomostar.io.ConnectionException {
            try { delegate.use(unwrap(other), exclusive); }
            catch (com.nttdocomo.io.ConnectionException failure) { throw com.docomostar.io.ConnectionException.from(failure); }
            catch (com.nttdocomo.ui.UIException failure) { throw MediaException.from(failure); }
        }

        @Override
        public void unuse() {
            try { delegate.unuse(); }
            catch (com.nttdocomo.ui.UIException failure) { throw MediaException.from(failure); }
        }

        @Override
        public void dispose() {
            try { delegate.dispose(); }
            catch (com.nttdocomo.ui.UIException failure) { throw MediaException.from(failure); }
        }

        @Override
        public String getProperty(String key) {
            return delegate.getProperty(key);
        }

        @Override
        public void setProperty(String key, String value) {
            delegate.setProperty(key, value);
        }

        @Override
        public boolean isRedistributable() {
            return delegate.isRedistributable();
        }

        @Override
        public boolean setRedistributable(boolean redistributable) {
            return delegate.setRedistributable(redistributable);
        }
    }

    private static final class MediaSoundAdapter implements MediaSound {
        private final com.nttdocomo.ui.MediaSound delegate;

        private MediaSoundAdapter(com.nttdocomo.ui.MediaSound delegate) {
            this.delegate = delegate;
        }

        @Override
        public void use() throws com.docomostar.io.ConnectionException {
            try { delegate.use(); }
            catch (com.nttdocomo.io.ConnectionException failure) { throw com.docomostar.io.ConnectionException.from(failure); }
            catch (com.nttdocomo.ui.UIException failure) { throw MediaException.from(failure); }
        }

        @Override
        public void use(MediaResource other, boolean exclusive) throws com.docomostar.io.ConnectionException {
            try { delegate.use(unwrap(other), exclusive); }
            catch (com.nttdocomo.io.ConnectionException failure) { throw com.docomostar.io.ConnectionException.from(failure); }
            catch (com.nttdocomo.ui.UIException failure) { throw MediaException.from(failure); }
        }

        @Override
        public void unuse() {
            try { delegate.unuse(); }
            catch (com.nttdocomo.ui.UIException failure) { throw MediaException.from(failure); }
        }

        @Override
        public void dispose() {
            try { delegate.dispose(); }
            catch (com.nttdocomo.ui.UIException failure) { throw MediaException.from(failure); }
        }

        @Override
        public String getProperty(String key) {
            return delegate.getProperty(key);
        }

        @Override
        public void setProperty(String key, String value) {
            delegate.setProperty(key, value);
        }

        @Override
        public boolean isRedistributable() {
            return delegate.isRedistributable();
        }

        @Override
        public boolean setRedistributable(boolean redistributable) {
            return delegate.setRedistributable(redistributable);
        }
    }
}
