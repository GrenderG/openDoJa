package com.nttdocomo.ui;

import com.nttdocomo.io.ConnectionException;
import com.nttdocomo.lang.IterationAbortedException;
import opendoja.host.DoJaRuntime;

import javax.imageio.ImageIO;
import javax.microedition.io.Connector;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class MediaManager {
    private MediaManager() {
    }

    public static MediaData getData(String name) {
        try (InputStream in = openNamedInputStream(name)) {
            return new BasicMediaData(readAllBytes(in));
        } catch (IOException e) {
            throw new UIException(UIException.UNSUPPORTED_FORMAT, e.getMessage());
        }
    }

    public static MediaData getData(InputStream inputStream) {
        try {
            return new BasicMediaData(readAllBytes(inputStream));
        } catch (IOException e) {
            throw new UIException(UIException.UNSUPPORTED_FORMAT, e.getMessage());
        }
    }

    public static MediaData getData(byte[] data) {
        return new BasicMediaData(data);
    }

    public static MediaImage getImage(String name) {
        try (InputStream in = openNamedInputStream(name)) {
            return getImage(in);
        } catch (IOException e) {
            throw new UIException(UIException.UNSUPPORTED_FORMAT, e.getMessage());
        }
    }

    public static MediaImage getImage(InputStream inputStream) {
        try {
            java.awt.image.BufferedImage bufferedImage = ImageIO.read(inputStream);
            if (bufferedImage == null) {
                throw new UIException(UIException.UNSUPPORTED_FORMAT, "Unsupported image format");
            }
            return new BasicMediaImage(bufferedImage);
        } catch (IOException e) {
            throw new UIException(UIException.UNSUPPORTED_FORMAT, e.getMessage());
        }
    }

    public static MediaImage getImage(byte[] data) {
        return getImage(new ByteArrayInputStream(data));
    }

    public static MediaImage getStreamingImage(String location, String contentType) {
        return getImage(location);
    }

    public static MediaSound getSound(String name) {
        try (InputStream in = openNamedInputStream(name)) {
            return new BasicMediaSound(readAllBytes(in), name);
        } catch (IOException e) {
            throw new UIException(UIException.UNSUPPORTED_FORMAT, e.getMessage());
        }
    }

    public static MediaSound getSound(InputStream inputStream) {
        try {
            return new BasicMediaSound(readAllBytes(inputStream), null);
        } catch (IOException e) {
            throw new UIException(UIException.UNSUPPORTED_FORMAT, e.getMessage());
        }
    }

    public static MediaSound getSound(byte[] data) {
        return new BasicMediaSound(data, null);
    }

    public static AvatarData getAvatarData(String name) {
        return new BasicAvatarData(getData(name));
    }

    public static AvatarData getAvatarData(InputStream inputStream) {
        return new BasicAvatarData(getData(inputStream));
    }

    public static AvatarData getAvatarData(byte[] data) {
        return new BasicAvatarData(getData(data));
    }

    public static void use(MediaImage[] mediaImages, boolean exclusive) throws IterationAbortedException {
        if (mediaImages == null) {
            return;
        }
        for (int i = 0; i < mediaImages.length; i++) {
            try {
                if (mediaImages[i] != null) {
                    mediaImages[i].use();
                }
            } catch (ConnectionException e) {
                throw new IterationAbortedException(i, e, e.getMessage());
            }
        }
    }

    public static void use(MediaSound[] mediaSounds, boolean exclusive) throws IterationAbortedException {
        if (mediaSounds == null) {
            return;
        }
        for (int i = 0; i < mediaSounds.length; i++) {
            try {
                if (mediaSounds[i] != null) {
                    mediaSounds[i].use();
                }
            } catch (ConnectionException e) {
                throw new IterationAbortedException(i, e, e.getMessage());
            }
        }
    }

    public static MediaImage createMediaImage(int width, int height) {
        return new BasicMediaImage((DesktopImage) Image.createImage(width, height));
    }

    public static MediaSound createMediaSound(int size) {
        return new BasicMediaSound(new byte[Math.max(0, size)], null);
    }

    private static byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = inputStream.read(buffer)) >= 0) {
            if (read > 0) {
                out.write(buffer, 0, read);
            }
        }
        return out.toByteArray();
    }

    private static InputStream openNamedInputStream(String name) throws IOException {
        if (name == null || name.isBlank()) {
            throw new IOException("Empty media name");
        }
        if (name.contains("://")) {
            return Connector.openInputStream(name);
        }
        DoJaRuntime runtime = DoJaRuntime.current();
        if (runtime != null) {
            return runtime.openResourceStream(name);
        }
        String normalized = normalizeBareResourceName(name);
        InputStream classpath = Thread.currentThread().getContextClassLoader().getResourceAsStream(normalized);
        if (classpath != null) {
            return classpath;
        }
        Path filesystemPath = Path.of(normalized);
        if (Files.exists(filesystemPath)) {
            return Files.newInputStream(filesystemPath);
        }
        throw new IOException("Media resource not found: " + name);
    }

    private static String normalizeBareResourceName(String name) {
        String normalized = name.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    static abstract class AbstractMediaResource implements MediaResource {
        private final Map<String, String> properties = new HashMap<>();
        private boolean redistributable = true;

        @Override
        public void use() throws ConnectionException {
        }

        @Override
        public void use(MediaResource other, boolean exclusive) throws ConnectionException {
            use();
        }

        @Override
        public void unuse() {
        }

        @Override
        public void dispose() {
        }

        @Override
        public String getProperty(String key) {
            return properties.get(key);
        }

        @Override
        public void setProperty(String key, String value) {
            properties.put(key, value);
        }

        @Override
        public boolean isRedistributable() {
            return redistributable;
        }

        @Override
        public boolean setRedistributable(boolean redistributable) {
            this.redistributable = redistributable;
            return true;
        }
    }

    static final class BasicMediaData extends AbstractMediaResource implements MediaData {
        private final byte[] data;

        BasicMediaData(byte[] data) {
            this.data = data == null ? new byte[0] : data.clone();
        }

        byte[] bytes() {
            return data.clone();
        }
    }

    static final class BasicMediaImage extends AbstractMediaResource implements MediaImage {
        private final DesktopImage image;
        private ExifData exifData = new ExifData();

        BasicMediaImage(java.awt.image.BufferedImage bufferedImage) {
            this.image = new DesktopImage(bufferedImage);
        }

        BasicMediaImage(DesktopImage image) {
            this.image = image;
        }

        @Override
        public int getWidth() {
            return image.getWidth();
        }

        @Override
        public int getHeight() {
            return image.getHeight();
        }

        @Override
        public Image getImage() {
            return image;
        }

        @Override
        public ExifData getExifData() {
            return exifData;
        }

        @Override
        public void setExifData(ExifData exifData) {
            this.exifData = exifData == null ? new ExifData() : exifData;
        }
    }

    static final class BasicMediaSound extends AbstractMediaResource implements MediaSound {
        private final byte[] data;
        private final String sourceName;

        BasicMediaSound(byte[] data, String sourceName) {
            this.data = data == null ? new byte[0] : data.clone();
            this.sourceName = sourceName;
        }

        byte[] bytes() {
            return data.clone();
        }

        String sourceName() {
            return sourceName;
        }
    }

    static final class BasicAvatarData extends AbstractMediaResource implements AvatarData {
        private final MediaData delegate;

        BasicAvatarData(MediaData delegate) {
            this.delegate = delegate;
        }
    }
}
