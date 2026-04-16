package com.nttdocomo.ui;

import com.nttdocomo.io.ConnectionException;
import com.nttdocomo.lang.IterationAbortedException;
import opendoja.audio.mld.MLD;
import opendoja.host.DesktopVideoSupport;
import opendoja.host.DoJaRuntime;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import javax.microedition.io.Connector;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Defines the factory and batch-use operations for DoJa media resources.
 */
public final class MediaManager {
    private static final String GIF_STREAM_METADATA_FORMAT = "javax_imageio_gif_stream_1.0";
    private static final String GIF_IMAGE_METADATA_FORMAT = "javax_imageio_gif_image_1.0";

    private MediaManager() {
    }

    /**
     * Gets a media-data object for the specified location.
     *
     * @param name the media location
     * @return the media-data object
     */
    public static MediaData getData(String name) {
        try (InputStream in = openNamedInputStream(name)) {
            return new BasicMediaData(readAllBytes(in));
        } catch (IOException e) {
            throw new UIException(UIException.UNSUPPORTED_FORMAT, e.getMessage());
        }
    }

    /**
     * Gets a media-data object from an input stream that supplies the media
     * file image.
     *
     * @param inputStream the media-data stream
     * @return the media-data object
     */
    public static MediaData getData(InputStream inputStream) {
        try {
            return new BasicMediaData(readAllBytes(inputStream));
        } catch (IOException e) {
            throw new UIException(UIException.UNSUPPORTED_FORMAT, e.getMessage());
        }
    }

    /**
     * Gets a media-data object from a byte array that contains the media file
     * image.
     *
     * @param data the media-data bytes
     * @return the media-data object
     */
    public static MediaData getData(byte[] data) {
        return new BasicMediaData(data);
    }

    /**
     * Gets a media-image object for the specified location.
     *
     * @param name the media location
     * @return the media-image object
     */
    public static MediaImage getImage(String name) {
        try (InputStream in = openNamedInputStream(name)) {
            return getImage(readAllBytes(in));
        } catch (IOException e) {
            throw new UIException(UIException.UNSUPPORTED_FORMAT, e.getMessage());
        }
    }

    /**
     * Gets a media-image object from an input stream that supplies the image
     * file image.
     *
     * @param inputStream the image stream
     * @return the media-image object
     */
    public static MediaImage getImage(InputStream inputStream) {
        try {
            return getImage(readAllBytes(inputStream));
        } catch (IOException e) {
            throw new UIException(UIException.UNSUPPORTED_FORMAT, e.getMessage());
        }
    }

    /**
     * Gets a media-image object from a byte array that contains the image file
     * image.
     *
     * @param data the image bytes
     * @return the media-image object
     */
    public static MediaImage getImage(byte[] data) {
        byte[] bytes = data == null ? new byte[0] : data.clone();
        MediaImage decodedImage = decodeMediaImage(bytes);
        if (decodedImage != null) {
            return decodedImage;
        }
        DesktopVideoSupport.VideoMetadata video = DesktopVideoSupport.probe(bytes);
        if (video.isVideo()) {
            return new DesktopVideoMediaImage(video.data());
        }
        throw new UIException(UIException.UNSUPPORTED_FORMAT, "Unsupported image format");
    }

    /**
     * Gets a pseudo-streaming media-image object for the specified location and
     * MIME type.
     *
     * @param location the media location
     * @param contentType the MIME type of the content
     * @return the media-image object used for pseudo streaming
     */
    public static MediaImage getStreamingImage(String location, String contentType) {
        return getImage(location);
    }

    /**
     * Gets a media-sound object for the specified location.
     *
     * @param name the media location
     * @return the media-sound object
     */
    public static MediaSound getSound(String name) {
        try (InputStream in = openNamedInputStream(name)) {
            return new BasicMediaSound(trimSoundBytes(readAllBytes(in)), name);
        } catch (IOException e) {
            throw new UIException(UIException.UNSUPPORTED_FORMAT, e.getMessage());
        }
    }

    /**
     * Gets a media-sound object from an input stream that supplies the sound
     * file image.
     *
     * @param inputStream the sound stream
     * @return the media-sound object
     */
    public static MediaSound getSound(InputStream inputStream) {
        try {
            return new BasicMediaSound(trimSoundBytes(readAllBytes(inputStream)), null);
        } catch (IOException e) {
            throw new UIException(UIException.UNSUPPORTED_FORMAT, e.getMessage());
        }
    }

    /**
     * Gets a media-sound object from a byte array that contains the sound file
     * image.
     *
     * @param data the sound bytes
     * @return the media-sound object
     */
    public static MediaSound getSound(byte[] data) {
        return new BasicMediaSound(trimSoundBytes(data), null);
    }

    /**
     * Gets an avatar-data object for the specified location.
     *
     * @param name the avatar-data location
     * @return the avatar-data object
     */
    public static AvatarData getAvatarData(String name) {
        return new BasicAvatarData(getData(name));
    }

    /**
     * Gets an avatar-data object from an input stream.
     *
     * @param inputStream the avatar-data stream
     * @return the avatar-data object
     */
    public static AvatarData getAvatarData(InputStream inputStream) {
        return new BasicAvatarData(getData(inputStream));
    }

    /**
     * Gets an avatar-data object from a byte array.
     *
     * @param data the avatar-data bytes
     * @return the avatar-data object
     */
    public static AvatarData getAvatarData(byte[] data) {
        return new BasicAvatarData(getData(data));
    }

    /**
     * Calls {@code use()} on each image in the supplied array.
     *
     * @param mediaImages the image array to make usable
     * @param exclusive {@code true} if the resources are intended for one-time use
     * @throws IterationAbortedException if iteration stops because one element fails
     */
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

    /**
     * Calls {@code use()} on each sound in the supplied array.
     *
     * @param mediaSounds the sound array to make usable
     * @param exclusive {@code true} if the resources are intended for one-time use
     * @throws IterationAbortedException if iteration stops because one element fails
     */
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

    /**
     * Creates an empty media-image buffer with the specified size.
     *
     * @param width the image width in pixels
     * @param height the image height in pixels
     * @return the created empty media image
     */
    public static MediaImage createMediaImage(int width, int height) {
        return new BasicMediaImage((DesktopImage) Image.createImage(width, height));
    }

    /**
     * Creates an empty media-sound buffer sized to hold the specified number
     * of bytes.
     *
     * @param size the sound-data size in bytes
     * @return the created empty media sound
     */
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

    private static MediaImage decodeMediaImage(byte[] data) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            if (in == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, false, false);
                IIOMetadata streamMetadata = reader.getStreamMetadata();
                // The standard GIF ImageIO metadata exposes per-image offsets inside a larger
                // logical screen. Preserve that canvas when decoding so frame padding is not lost.
                if (streamMetadata != null) {
                    String[] metadataFormats = streamMetadata.getMetadataFormatNames();
                    if (metadataFormats != null) {
                        for (String metadataFormat : metadataFormats) {
                            if (GIF_STREAM_METADATA_FORMAT.equals(metadataFormat)) {
                                return decodeGifMediaImage(reader, streamMetadata);
                            }
                        }
                    }
                }
                return decodeSimpleMediaImage(reader);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            return null;
        }
    }

    private static MediaImage decodeSimpleMediaImage(ImageReader reader) throws IOException {
        List<DesktopImage> frames = new ArrayList<>();
        for (int index = 0; ; index++) {
            BufferedImage bufferedImage;
            try {
                bufferedImage = reader.read(index);
            } catch (IndexOutOfBoundsException e) {
                break;
            }
            if (bufferedImage == null) {
                break;
            }
            frames.add(new DesktopImage(bufferedImage));
        }
        return mediaImageFromFrames(frames);
    }

    private static MediaImage decodeGifMediaImage(ImageReader reader, IIOMetadata streamMetadata) throws IOException {
        IIOMetadataNode streamRoot = (IIOMetadataNode) streamMetadata.getAsTree(GIF_STREAM_METADATA_FORMAT);
        IIOMetadataNode logicalScreen = child(streamRoot, "LogicalScreenDescriptor");
        int logicalWidth = intAttribute(logicalScreen, "logicalScreenWidth");
        int logicalHeight = intAttribute(logicalScreen, "logicalScreenHeight");
        List<DesktopImage> frames = new ArrayList<>();
        // Compose each decoded frame into the full GIF logical screen so imageLeftPosition /
        // imageTopPosition padding is preserved for still images and animated sequences alike.
        BufferedImage composite = new BufferedImage(logicalWidth, logicalHeight, BufferedImage.TYPE_INT_ARGB);
        for (int index = 0; ; index++) {
            BufferedImage frameImage;
            try {
                frameImage = reader.read(index);
            } catch (IndexOutOfBoundsException e) {
                break;
            }
            if (frameImage == null) {
                break;
            }
            IIOMetadataNode imageRoot = (IIOMetadataNode) reader.getImageMetadata(index).getAsTree(GIF_IMAGE_METADATA_FORMAT);
            IIOMetadataNode imageDescriptor = child(imageRoot, "ImageDescriptor");
            IIOMetadataNode control = child(imageRoot, "GraphicControlExtension");
            int left = intAttribute(imageDescriptor, "imageLeftPosition");
            int top = intAttribute(imageDescriptor, "imageTopPosition");
            String disposalMethod = stringAttribute(control, "disposalMethod");
            BufferedImage previousComposite = "restoreToPrevious".equals(disposalMethod) ? copyImage(composite) : null;
            Graphics2D graphics = composite.createGraphics();
            try {
                graphics.setComposite(AlphaComposite.SrcOver);
                graphics.drawImage(frameImage, left, top, null);
            } finally {
                graphics.dispose();
            }
            frames.add(new DesktopImage(copyImage(composite)));
            switch (disposalMethod) {
                case "restoreToBackgroundColor" -> clearGifFrameRegion(composite, left, top, frameImage.getWidth(), frameImage.getHeight());
                case "restoreToPrevious" -> {
                    if (previousComposite != null) {
                        composite = previousComposite;
                    }
                }
                default -> {
                }
            }
        }
        return mediaImageFromFrames(frames);
    }

    private static MediaImage mediaImageFromFrames(List<DesktopImage> frames) {
        if (frames.isEmpty()) {
            return null;
        }
        if (frames.size() == 1) {
            return new BasicMediaImage(frames.get(0));
        }
        return new AnimatedMediaImage(frames.toArray(DesktopImage[]::new));
    }

    private static IIOMetadataNode child(IIOMetadataNode root, String name) {
        if (root == null || name == null) {
            return null;
        }
        for (org.w3c.dom.Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof IIOMetadataNode metadataNode && name.equals(metadataNode.getNodeName())) {
                return metadataNode;
            }
        }
        return null;
    }

    private static int intAttribute(IIOMetadataNode node, String attribute) {
        if (node == null || attribute == null) {
            return 0;
        }
        String value = node.getAttribute(attribute);
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String stringAttribute(IIOMetadataNode node, String attribute) {
        if (node == null || attribute == null) {
            return "";
        }
        String value = node.getAttribute(attribute);
        return value == null ? "" : value;
    }

    private static BufferedImage copyImage(BufferedImage image) {
        BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = copy.createGraphics();
        try {
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return copy;
    }

    private static void clearGifFrameRegion(BufferedImage composite, int left, int top, int width, int height) {
        Graphics2D graphics = composite.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Clear);
            graphics.fillRect(left, top, width, height);
        } finally {
            graphics.dispose();
        }
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
        throw new FileNotFoundException("Media resource not found: " + name);
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
        private int state = MediaResource.UNUSE;

        @Override
        public void use() throws ConnectionException {
            state = MediaResource.USE;
        }

        @Override
        public void use(MediaResource other, boolean exclusive) throws ConnectionException {
            use();
        }

        @Override
        public void unuse() {
            state = MediaResource.UNUSE;
        }

        @Override
        public void dispose() {
            state = MediaResource.DISPOSE;
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

        final boolean isUsed() {
            return state == MediaResource.USE;
        }

        final int state() {
            return state;
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

    static final class AnimatedMediaImage extends AbstractMediaResource implements MediaImage {
        private final DesktopImage[] frames;
        private ExifData exifData = new ExifData();

        AnimatedMediaImage(DesktopImage[] frames) {
            this.frames = frames.clone();
        }

        @Override
        public int getWidth() {
            return frames[0].getWidth();
        }

        @Override
        public int getHeight() {
            return frames[0].getHeight();
        }

        @Override
        public Image getImage() {
            return frames[0];
        }

        @Override
        public ExifData getExifData() {
            return exifData;
        }

        @Override
        public void setExifData(ExifData exifData) {
            this.exifData = exifData == null ? new ExifData() : exifData;
        }

        int frameCount() {
            return frames.length;
        }

        Image frame(int index) {
            return frames[index];
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
                super.use();
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
        private final Sequence midiSequence;
        private final MLD mld;

        private PreparedSound(Kind kind, byte[] bytes, AudioFormat sampledFormat, Sequence midiSequence, MLD mld) {
            this.kind = kind;
            this.bytes = bytes;
            this.sampledFormat = sampledFormat;
            this.midiSequence = midiSequence;
            this.mld = mld;
        }

        static PreparedSound prepare(byte[] data) throws IOException {
            if (startsWith(data, "MThd")) {
                try {
                    Sequence sequence = MidiSystem.getSequence(new ByteArrayInputStream(data));
                    return new PreparedSound(Kind.MIDI, data, null, sequence, null);
                } catch (Exception e) {
                    if (e instanceof IOException ioException) {
                        throw ioException;
                    }
                    throw new IOException("Unsupported MIDI format", e);
                }
            }
            if (startsWith(data, "melo")) {
                return new PreparedSound(Kind.MLD, data, null, null, new MLD(data));
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
                return new PreparedSound(Kind.SAMPLED, pcmBytes, targetFormat, null, null);
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

        public Sequence midiSequence() {
            return midiSequence;
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
