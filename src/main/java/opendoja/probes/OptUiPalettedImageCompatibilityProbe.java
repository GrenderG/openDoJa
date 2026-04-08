package opendoja.probes;

import com.nttdocomo.lang.UnsupportedOperationException;
import com.nttdocomo.opt.ui.Palette;
import com.nttdocomo.opt.ui.PalettedImage;
import com.nttdocomo.ui.Graphics;
import com.nttdocomo.ui.Image;
import com.nttdocomo.ui.UIException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.io.ByteArrayOutputStream;

public final class OptUiPalettedImageCompatibilityProbe {
    private OptUiPalettedImageCompatibilityProbe() {
    }

    public static void main(String[] args) throws Exception {
        verifyPaletteArrayConstructorKeepsDocumentedLength();
        verifyCreatePalettedImageRequiresGif();
        verifyPaletteReplacementUsesReferenceAndAllowsLargerPalettes();
        verifyGetGraphicsAlwaysThrows();

        System.out.println("opt.ui paletted-image compatibility probe OK");
    }

    private static void verifyPaletteArrayConstructorKeepsDocumentedLength() {
        Palette palette = new Palette(new int[300]);
        check(palette.getEntryCount() == 300,
                "Palette(int[]) should keep colors.length entries in opt.ui");
    }

    private static void verifyCreatePalettedImageRequiresGif() throws Exception {
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFFFF0000);
        check(ImageIO.write(image, "png", png), "PNG writer unavailable");

        try {
            PalettedImage.createPalettedImage(png.toByteArray());
            throw new IllegalStateException("Non-GIF data should be rejected");
        } catch (UIException e) {
            check(e.getStatus() == UIException.UNSUPPORTED_FORMAT,
                    "Non-GIF data should fail with UNSUPPORTED_FORMAT");
        }
    }

    private static void verifyPaletteReplacementUsesReferenceAndAllowsLargerPalettes() throws Exception {
        PalettedImage image = PalettedImage.createPalettedImage(createIndexedGif());
        check(image.getPalette().getEntryCount() == 2, "GIF should expose its original palette size");

        Palette larger = new Palette(4);
        larger.setEntry(0, Graphics.getColorOfRGB(255, 0, 0));
        larger.setEntry(1, Graphics.getColorOfRGB(0, 0, 255));
        image.setPalette(larger);
        check(image.getPalette() == larger, "setPalette should retain the same Palette reference");

        Image target = Image.createImage(2, 1);
        Graphics graphics = target.getGraphics();
        graphics.drawImage(image, 0, 0);
        check((graphics.getRGBPixel(0, 0) & 0x00FFFFFF) == 0xFF0000,
                "palette entry 0 should draw as red");
        check((graphics.getRGBPixel(1, 0) & 0x00FFFFFF) == 0x0000FF,
                "palette entry 1 should draw as blue");

        larger.setEntry(1, Graphics.getColorOfRGB(0, 255, 0));
        graphics.drawImage(image, 0, 0);
        check((graphics.getRGBPixel(1, 0) & 0x00FFFFFF) == 0x00FF00,
                "palette mutations should be reflected on the next draw");

        try {
            image.setPalette(new Palette(1));
            throw new IllegalStateException("Smaller palette should be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void verifyGetGraphicsAlwaysThrows() throws Exception {
        PalettedImage image = PalettedImage.createPalettedImage(createIndexedGif());
        try {
            image.getGraphics();
            throw new IllegalStateException("getGraphics() should not succeed");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    private static byte[] createIndexedGif() throws Exception {
        byte[] reds = {(byte) 0xFF, 0};
        byte[] greens = {0, 0};
        byte[] blues = {0, (byte) 0xFF};
        IndexColorModel colorModel = new IndexColorModel(8, 2, reds, greens, blues);
        WritableRaster raster = colorModel.createCompatibleWritableRaster(2, 1);
        byte[] pixels = ((DataBufferByte) raster.getDataBuffer()).getData();
        pixels[0] = 0;
        pixels[1] = 1;
        BufferedImage indexed = new BufferedImage(colorModel, raster, false, null);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        check(ImageIO.write(indexed, "gif", out), "GIF writer unavailable");
        return out.toByteArray();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
