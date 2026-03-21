package com.nttdocomo.ui;

import com.nttdocomo.io.ConnectionException;
import com.nttdocomo.lang.IterationAbortedException;
import opendoja.audio.mld.ma3.MLD;
import opendoja.host.DoJaRuntime;

import javax.imageio.ImageIO;
import javax.microedition.io.Connector;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
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
            return new BasicMediaSound(trimSoundBytes(readAllBytes(in)), name);
        } catch (IOException e) {
            throw new UIException(UIException.UNSUPPORTED_FORMAT, e.getMessage());
        }
    }

    public static MediaSound getSound(InputStream inputStream) {
        try {
            return new BasicMediaSound(trimSoundBytes(readAllBytes(inputStream)), null);
        } catch (IOException e) {
            throw new UIException(UIException.UNSUPPORTED_FORMAT, e.getMessage());
        }
    }

    public static MediaSound getSound(byte[] data) {
        return new BasicMediaSound(trimSoundBytes(data), null);
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

    private static byte[] trimSoundBytes(byte[] data) {
        if (data == null || data.length < 8) {
            return data == null ? new byte[0] : data;
        }
        if (startsWith(data, "melo")) {
            int payloadLength = ((data[4] & 0xFF) << 24)
                    | ((data[5] & 0xFF) << 16)
                    | ((data[6] & 0xFF) << 8)
                    | (data[7] & 0xFF);
            int totalLength = Math.min(data.length, Math.max(8, payloadLength + 8));
            return Arrays.copyOf(data, totalLength);
        }
        return data;
    }

    private static boolean startsWith(byte[] data, String prefix) {
        if (data.length < prefix.length()) {
            return false;
        }
        for (int i = 0; i < prefix.length(); i++) {
            if (data[i] != (byte) prefix.charAt(i)) {
                return false;
            }
        }
        return true;
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
        private volatile PreparedSound prepared;

        BasicMediaSound(byte[] data, String sourceName) {
            this.data = data == null ? new byte[0] : data.clone();
            this.sourceName = sourceName;
        }

        @Override
        public void use() throws ConnectionException {
            try {
                prepared();
            } catch (IOException e) {
                ConnectionException failure = new ConnectionException(ConnectionException.NO_RESOURCE, e.getMessage());
                failure.initCause(e);
                throw failure;
            }
        }

        byte[] bytes() {
            return data.clone();
        }

        byte[] byteView() {
            return data;
        }

        PreparedSound prepared() throws IOException {
            PreparedSound cached = prepared;
            if (cached != null) {
                return cached;
            }
            synchronized (this) {
                if (prepared == null) {
                    prepared = PreparedSound.prepare(data);
                }
                return prepared;
            }
        }

        String sourceName() {
            return sourceName;
        }
    }

    public static final class PreparedSound {
        public enum Kind {
            MIDI,
            MLD,
            SAMPLED,
            UNKNOWN
        }

        private final Kind kind;
        private final byte[] bytes;
        private final AudioFormat sampledFormat;
        private final MLD mld;

        private PreparedSound(Kind kind, byte[] bytes, AudioFormat sampledFormat, MLD mld) {
            this.kind = kind;
            this.bytes = bytes;
            this.sampledFormat = sampledFormat;
            this.mld = mld;
        }

        static PreparedSound prepare(byte[] data) throws IOException {
            if (startsWith(data, "MThd")) {
                return new PreparedSound(Kind.MIDI, data, null, null);
            }
            if (startsWith(data, "melo")) {
                return new PreparedSound(Kind.MLD, data, null, new MLD(data));
            }
            try (AudioInputStream raw = AudioSystem.getAudioInputStream(new ByteArrayInputStream(data))) {
                AudioFormat sourceFormat = raw.getFormat();
                AudioFormat targetFormat = normalizeSampledFormat(sourceFormat);
                byte[] pcmBytes;
                if (audioFormatsEqual(sourceFormat, targetFormat)) {
                    pcmBytes = readAllBytes(raw);
                } else {
                    try (AudioInputStream decoded = AudioSystem.getAudioInputStream(targetFormat, raw)) {
                        pcmBytes = readAllBytes(decoded);
                    }
                }
                return new PreparedSound(Kind.SAMPLED, pcmBytes, targetFormat, null);
            } catch (Exception e) {
                if (e instanceof IOException ioException) {
                    throw ioException;
                }
                IOException failure = new IOException("Unsupported audio format", e);
                throw failure;
            }
        }

        public Kind kind() {
            return kind;
        }

        public byte[] bytes() {
            return bytes;
        }

        public AudioFormat sampledFormat() {
            return sampledFormat;
        }

        public MLD mld() {
            return mld;
        }

        private static AudioFormat normalizeSampledFormat(AudioFormat format) {
            if (format == null) {
                return null;
            }
            int channels = Math.max(1, format.getChannels());
            float sampleRate = format.getSampleRate() > 0 ? format.getSampleRate() : 8000.0f;
            return new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    sampleRate,
                    16,
                    channels,
                    channels * 2,
                    sampleRate,
                    false);
        }

        private static boolean audioFormatsEqual(AudioFormat a, AudioFormat b) {
            return a.getEncoding().equals(b.getEncoding())
                    && a.getSampleRate() == b.getSampleRate()
                    && a.getSampleSizeInBits() == b.getSampleSizeInBits()
                    && a.getChannels() == b.getChannels()
                    && a.getFrameSize() == b.getFrameSize()
                    && a.getFrameRate() == b.getFrameRate()
                    && a.isBigEndian() == b.isBigEndian();
        }
    }

    static final class BasicAvatarData extends AbstractMediaResource implements AvatarData {
        private final MediaData delegate;

        BasicAvatarData(MediaData delegate) {
            this.delegate = delegate;
        }
    }
}
