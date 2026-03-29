package opendoja.demo;

import com.nttdocomo.ui.Graphics;
import com.nttdocomo.ui.Image;
import com.nttdocomo.ui.PalettedImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.io.IOException;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ChakurangerPalettedTransparencyProbe {
    private static final Path GAME_JAR = Path.of(
            "resources/sample_games/Chakuranger_Cart_Neo_doja/F906i_Version/bin/Chakuranger_Cart_Neo.jar");
    private static final int BACKGROUND = Graphics.getColorOfRGB(17, 34, 221);

    private ChakurangerPalettedTransparencyProbe() {
    }

    public static void main(String[] args) throws Exception {
        verifyTransparentGif("21");
        verifyTransparentGif("42");
        System.out.println("verified assets 21 and 42");
    }

    private static void verifyTransparentGif(String entryName) throws Exception {
        byte[] data = readEntry(entryName);
        SamplePoints points = locateSamplePoints(data, entryName);
        PalettedImage image = PalettedImage.createPalettedImage(data);
        Image target = Image.createImage(image.getWidth(), image.getHeight());
        Graphics graphics = target.getGraphics();
        graphics.setColor(BACKGROUND);
        graphics.fillRect(0, 0, target.getWidth(), target.getHeight());
        graphics.drawImage(image, 0, 0);

        int transparentPixel = graphics.getRGBPixel(points.transparentX(), points.transparentY());
        if (transparentPixel != BACKGROUND) {
            throw new IllegalStateException(String.format(
                    "asset %s transparent pixel stayed opaque: %08x",
                    entryName,
                    transparentPixel));
        }

        int opaquePixel = graphics.getRGBPixel(points.opaqueX(), points.opaqueY()) & 0x00FFFFFF;
        if (opaquePixel == (BACKGROUND & 0x00FFFFFF)) {
            throw new IllegalStateException("asset " + entryName + " opaque pixel disappeared");
        }
        if (opaquePixel != points.opaqueRgb()) {
            throw new IllegalStateException(String.format(
                    "asset %s opaque pixel mismatch: %06x != %06x",
                    entryName,
                    opaquePixel,
                    points.opaqueRgb()));
        }
    }

    private static byte[] readEntry(String entryName) throws IOException {
        try (ZipFile zip = new ZipFile(GAME_JAR.toFile())) {
            ZipEntry entry = zip.getEntry(entryName);
            if (entry == null) {
                throw new IOException("Missing jar entry: " + entryName);
            }
            return zip.getInputStream(entry).readAllBytes();
        }
    }

    private static SamplePoints locateSamplePoints(byte[] data, String entryName) throws IOException {
        BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(data));
        if (image == null) {
            throw new IOException("Unsupported image: " + entryName);
        }
        if (!(image.getColorModel() instanceof IndexColorModel colorModel)) {
            throw new IOException("Expected indexed GIF for asset " + entryName);
        }
        int transparentIndex = colorModel.getTransparentPixel();
        if (transparentIndex < 0) {
            throw new IOException("Expected transparent index for asset " + entryName);
        }
        Raster raster = image.getRaster();
        int transparentX = -1;
        int transparentY = -1;
        int opaqueX = -1;
        int opaqueY = -1;
        int opaqueRgb = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int index = raster.getSample(x, y, 0);
                if (index == transparentIndex) {
                    if (transparentX < 0) {
                        transparentX = x;
                        transparentY = y;
                    }
                    continue;
                }
                if (opaqueX < 0) {
                    opaqueX = x;
                    opaqueY = y;
                    opaqueRgb = colorModel.getRGB(index) & 0x00FFFFFF;
                }
                if (transparentX >= 0 && opaqueX >= 0) {
                    return new SamplePoints(transparentX, transparentY, opaqueX, opaqueY, opaqueRgb);
                }
            }
        }
        throw new IOException("Could not find both transparent and opaque pixels in asset " + entryName);
    }

    private record SamplePoints(int transparentX, int transparentY, int opaqueX, int opaqueY, int opaqueRgb) {
    }
}
