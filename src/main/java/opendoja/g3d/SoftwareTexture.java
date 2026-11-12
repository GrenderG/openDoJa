package opendoja.g3d;

import opendoja.host.OpenDoJaLog;

import javax.imageio.ImageIO;
import java.awt.image.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public final class SoftwareTexture {
    private static final boolean TRACE_3D_CALLS = opendoja.host.OpenDoJaLaunchArgs.getBoolean(opendoja.host.OpenDoJaLaunchArgs.DEBUG3D_CALLS);
    private final BufferedImage image;
    private final boolean sphereMap;
    private final int[] indexedPixels;
    private final IndexColorModel indexedColorModel;
    private boolean repeatEnabled;
    private boolean toonShaderEnabled;
    private int toonThreshold = 128;
    private int toonMid = 255;
    private int toonShadow = 96;

    public SoftwareTexture(byte[] bytes, boolean forModel) throws IOException {
        this(decode(bytes), bytes == null ? -1 : bytes.length, forModel);
    }

    public SoftwareTexture(InputStream inputStream, boolean forModel) throws IOException {
        this(readAllBytes(inputStream), forModel);
    }

    public static SoftwareTexture fromIndexed(int width, int height, int[] paletteArgb, byte[] indexedPixels, boolean forModel) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("width and height must be > 0");
        }
        if (paletteArgb == null || paletteArgb.length == 0) {
            throw new NullPointerException("paletteArgb");
        }
        if (indexedPixels == null || indexedPixels.length < width * height) {
            throw new IllegalArgumentException("indexedPixels");
        }
        int paletteSize = paletteArgb.length;
        byte[] reds = new byte[paletteSize];
        byte[] greens = new byte[paletteSize];
        byte[] blues = new byte[paletteSize];
        byte[] alphas = new byte[paletteSize];
        for (int i = 0; i < paletteSize; i++) {
            int argb = paletteArgb[i];
            alphas[i] = (byte) ((argb >>> 24) & 0xFF);
            reds[i] = (byte) ((argb >>> 16) & 0xFF);
            greens[i] = (byte) ((argb >>> 8) & 0xFF);
            blues[i] = (byte) (argb & 0xFF);
        }
        IndexColorModel colorModel = new IndexColorModel(8, paletteSize, reds, greens, blues, alphas);
        byte[] pixels = indexedPixels.clone();
        WritableRaster raster = Raster.createInterleavedRaster(
                new DataBufferByte(pixels, pixels.length), width, height, width, 1, new int[]{0}, null);
        BufferedImage indexed = new BufferedImage(colorModel, raster, false, null);
        int[] indices = new int[width * height];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = pixels[i] & 0xFF;
        }
        return new SoftwareTexture(indexed, indices, colorModel, !forModel, -1, forModel);
    }

    public BufferedImage image() {
        return image;
    }

    public boolean sphereMap() {
        return sphereMap;
    }

    public int width() {
        return image.getWidth();
    }

    public int height() {
        return image.getHeight();
    }

    public boolean hasPaletteZeroColor(int rgb) {
        return indexedPixels != null && indexedColorModel != null
                && (indexedColorModel.getRGB(0) & 0xffffff) == (rgb & 0xffffff);
    }

    public int sampleColor(float u, float v) {
        return sampleColor(u, v, false);
    }

    public int sampleColor(float u, float v, boolean transparentPaletteZero) {
        int x = repeatEnabled ? wrap((int) java.lang.Math.floor(u), width()) : clamp((int) java.lang.Math.floor(u), 0, Math.max(0, width() - 1));
        int y = repeatEnabled ? wrap((int) java.lang.Math.floor(v), height()) : clamp((int) java.lang.Math.floor(v), 0, Math.max(0, height() - 1));
        if (indexedPixels != null && indexedColorModel != null) {
            int index = indexedPixels[y * width() + x];
            if (transparentPaletteZero && index == 0) {
                return 0;
            }
            return indexedColorModel.getRGB(index);
        }
        return image.getRGB(x, y);
    }

    public void setNormalShader() {
        this.toonShaderEnabled = false;
    }

    public void setToonShader(int threshold, int mid, int shadow) {
        this.toonShaderEnabled = true;
        this.toonThreshold = threshold;
        this.toonMid = mid;
        this.toonShadow = shadow;
    }

    public boolean toonShaderEnabled() {
        return toonShaderEnabled;
    }

    public int toonThreshold() {
        return toonThreshold;
    }

    public int toonMid() {
        return toonMid;
    }

    public int toonShadow() {
        return toonShadow;
    }

    public void setRepeatEnabled(boolean repeatEnabled) {
        this.repeatEnabled = repeatEnabled;
    }

    private SoftwareTexture(BufferedImage image, int[] indexedPixels, IndexColorModel indexedColorModel,
                            boolean sphereMap, int sourceSize, boolean forModel) {
        this.image = image;
        this.indexedPixels = indexedPixels;
        this.indexedColorModel = indexedColorModel;
        this.sphereMap = sphereMap;
        if (TRACE_3D_CALLS) {
            int transparentPixels = countTransparentPixels(this.image, this.indexedPixels, this.indexedColorModel);
            OpenDoJaLog.debug(SoftwareTexture.class, () -> "3D texture decode forModel=" + forModel
                    + " size=" + this.image.getWidth() + "x" + this.image.getHeight()
                    + " indexed=" + (this.indexedPixels != null)
                    + " transparentPixels=" + transparentPixels
                    + " bytes=" + sourceSize);
        }
    }

    private SoftwareTexture(DecodedTexture decoded, int sourceSize, boolean forModel) {
        this(decoded.image(), decoded.indexedPixels(), decoded.indexedColorModel(), !forModel, sourceSize, forModel);
    }

    private static DecodedTexture decode(byte[] bytes) throws IOException {
        BufferedImage raw = ImageIO.read(new ByteArrayInputStream(bytes));
        if (raw == null) {
            throw new IOException("Unsupported texture image");
        }
        BufferedImage converted = new BufferedImage(raw.getWidth(), raw.getHeight(), BufferedImage.TYPE_INT_ARGB);
        if (raw.getColorModel() instanceof IndexColorModel colorModel) {
            Raster raster = raw.getRaster();
            int[] indexedPixels = new int[raw.getWidth() * raw.getHeight()];
            for (int y = 0; y < raw.getHeight(); y++) {
                for (int x = 0; x < raw.getWidth(); x++) {
                    int index = raster.getSample(x, y, 0);
                    indexedPixels[y * raw.getWidth() + x] = index;
                    int argb = colorModel.getRGB(index);
                    converted.setRGB(x, y, argb);
                }
            }
            return new DecodedTexture(converted, indexedPixels, colorModel);
        }
        java.awt.Graphics2D g2 = converted.createGraphics();
        try {
            g2.drawImage(raw, 0, 0, null);
        } finally {
            g2.dispose();
        }
        return new DecodedTexture(converted, null, null);
    }

    private static int clamp(int value, int min, int max) {
        return java.lang.Math.max(min, java.lang.Math.min(max, value));
    }

    private static int wrap(int value, int size) {
        if (size <= 0) {
            return 0;
        }
        return Math.floorMod(value, size);
    }

    private static int countTransparentPixels(BufferedImage image, int[] indexedPixels, IndexColorModel colorModel) {
        if (image == null) {
            return 0;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        int transparent = 0;
        if (indexedPixels != null && colorModel != null) {
            for (int index : indexedPixels) {
                if (((colorModel.getRGB(index) >>> 24) & 0xFF) == 0) {
                    transparent++;
                }
            }
            return transparent;
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (((image.getRGB(x, y) >>> 24) & 0xFF) == 0) {
                    transparent++;
                }
            }
        }
        return transparent;
    }

    private static byte[] readAllBytes(InputStream inputStream) throws IOException {
        return inputStream.readAllBytes();
    }

    private record DecodedTexture(BufferedImage image, int[] indexedPixels, IndexColorModel indexedColorModel) {
    }
}
