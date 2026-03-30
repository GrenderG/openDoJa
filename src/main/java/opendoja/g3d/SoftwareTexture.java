package opendoja.g3d;

import opendoja.host.OpenDoJaLog;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public final class SoftwareTexture {
    private static final boolean TRACE_3D_CALLS = opendoja.host.OpenDoJaLaunchArgs.getBoolean(opendoja.host.OpenDoJaLaunchArgs.DEBUG3D_CALLS);
    private final BufferedImage image;
    private final boolean sphereMap;
    private final int[] indexedPixels;
    private final IndexColorModel indexedColorModel;

    public SoftwareTexture(byte[] bytes, boolean forModel) throws IOException {
        DecodedTexture decoded = decode(bytes, forModel);
        this.image = decoded.image();
        this.indexedPixels = decoded.indexedPixels();
        this.indexedColorModel = decoded.indexedColorModel();
        this.sphereMap = !forModel;
        if (TRACE_3D_CALLS) {
            int transparentPixels = countTransparentPixels(this.image, this.indexedPixels, this.indexedColorModel);
            OpenDoJaLog.debug(SoftwareTexture.class, () -> "3D texture decode forModel=" + forModel
                    + " size=" + this.image.getWidth() + "x" + this.image.getHeight()
                    + " indexed=" + (this.indexedPixels != null)
                    + " transparentPixels=" + transparentPixels
                    + " bytes=" + (bytes == null ? -1 : bytes.length));
        }
    }

    public SoftwareTexture(InputStream inputStream, boolean forModel) throws IOException {
        this(readAllBytes(inputStream), forModel);
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

    public int sampleColor(float u, float v) {
        return sampleColor(u, v, false);
    }

    public int sampleColor(float u, float v, boolean transparentPaletteZero) {
        int x = clamp((int) java.lang.Math.floor(u), 0, Math.max(0, width() - 1));
        int y = clamp((int) java.lang.Math.floor(v), 0, Math.max(0, height() - 1));
        if (indexedPixels != null && indexedColorModel != null) {
            int index = indexedPixels[y * width() + x];
            if (transparentPaletteZero && index == 0) {
                return 0;
            }
            return indexedColorModel.getRGB(index);
        }
        return image.getRGB(x, y);
    }

    private static DecodedTexture decode(byte[] bytes, boolean forModel) throws IOException {
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
