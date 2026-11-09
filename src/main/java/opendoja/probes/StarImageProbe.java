package opendoja.probes;

import com.docomostar.ui.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayOutputStream;
import opendoja.host.DesktopLauncher;
import opendoja.host.DoJaRuntime;
import opendoja.host.LaunchConfig;

/** Checks STAR image descriptors, offscreen drawing and live indexed palettes. */
public final class StarImageProbe {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        DesktopLauncher.launch(LaunchConfig.builder(ProbeApp.class).parameter("AppType", "FullApp").build());
        try { verifyImages(); }
        finally { DoJaRuntime.current().shutdown(); }
        verifyDoJaProfiles();
        System.out.println("star-image-probe-ok");
    }

    private static void verifyImages() throws Exception {
        Image source = Image.createImage(2, 1, new int[]{0xffff0000, 0xff00ff00}, 0);
        Image target = Image.createImage(4, 2);
        Graphics graphics = target.getGraphics();
        clear(graphics);
        graphics.drawImage(source, 0, 0);
        check(pixel(target, 0) == 0xff0000 && pixel(target, 1) == 0x00ff00, "RGB source drawing");
        clear(graphics);
        graphics.drawImage(source, 0, 0, 1, 0, 1, 1);
        check(pixel(target, 0) == 0x00ff00, "Source-region drawing");
        source.setTransparentColor(Graphics.getColorOfRGB(255, 0, 0));
        source.setTransparentEnabled(true);
        clear(graphics);
        graphics.drawImage(source, 0, 0);
        check(pixel(target, 0) == 0 && pixel(target, 1) == 0x00ff00, "Transparent RGB color");

        Image offscreen = Image.createImage(1, 1);
        Graphics offscreenGraphics = offscreen.getGraphics();
        offscreenGraphics.setColor(Graphics.getColorOfRGB(0, 0, 255));
        offscreenGraphics.fillRect(0, 0, 1, 1);
        clear(graphics);
        graphics.drawImage(offscreen, new int[]{4096, 0, 0, 0, 4096, 0, 0, 0, 4096}, 0, 0, 1, 1);
        check(pixel(target, 0) == 0x0000ff, "Offscreen graphics and matrix drawing");

        PalettedImage indexed = PalettedImage.createPalettedImage(indexedGif());
        check(indexed.getWidth() == 2 && indexed.getHeight() == 1, "Indexed dimensions");
        Palette palette = indexed.getPalette();
        check(indexed.getPalette() == palette, "Stable palette wrapper");
        indexed.setTransparentIndex(0);
        indexed.setTransparentEnabled(true);
        clear(graphics); graphics.drawImage(indexed, 0, 0);
        check(pixel(target, 0) == 0 && pixel(target, 1) == 0x00ff00, "Transparent palette index");
        palette.setEntry(1, Graphics.getColorOfRGB(0, 0, 255));
        clear(graphics); graphics.drawImage(indexed, 0, 0);
        check(pixel(target, 1) == 0x0000ff, "Live palette updates reach renderer");
        verifyIndexedUpdates(indexed, graphics, target);
        try { PalettedImage.createPalettedImage(new byte[]{1, 2}); throw new AssertionError("Invalid image accepted"); }
        catch (UIException expected) { check(expected.getStatus() == UIException.UNSUPPORTED_FORMAT, "STAR exception type"); }
        source.dispose();
        try { source.getWidth(); throw new AssertionError("Disposed image accepted"); }
        catch (UIException expected) { check(expected.getStatus() == UIException.ILLEGAL_STATE, "Disposed-image status"); }
        indexed.dispose();
        try { indexed.getPalette(); throw new AssertionError("Disposed palette accepted"); }
        catch (UIException expected) { check(expected.getStatus() == UIException.ILLEGAL_STATE, "Disposed-palette status"); }
        offscreen.dispose(); target.dispose();
    }

    private static void verifyIndexedUpdates(PalettedImage indexed, Graphics graphics, Image target) throws Exception {
        indexed.setTransparentEnabled(false);
        clear(graphics); graphics.drawImage(indexed, 0, 0);
        check(pixel(target, 0) == 0xff0000 && pixel(target, 1) == 0x0000ff,
                "Disabling transparency redraws the transparent index");
        indexed.setTransparentIndex(1);
        clear(graphics); graphics.drawImage(indexed, 0, 0);
        check(pixel(target, 1) == 0x0000ff, "Changing the index while disabled keeps pixels opaque");
        indexed.setTransparentEnabled(true);
        clear(graphics); graphics.drawImage(indexed, 0, 0);
        check(pixel(target, 0) == 0xff0000 && pixel(target, 1) == 0,
                "Enabling transparency applies the new index");
        indexed.setTransparentIndex(0);
        Palette replacement = new Palette(new int[]{0xffffffff, 0xffffff00});
        indexed.setPalette(replacement);
        clear(graphics); graphics.drawImage(indexed, 0, 0);
        check(pixel(target, 0) == 0 && pixel(target, 1) == 0xffff00,
                "Replacing a palette updates pixels and preserves transparency");

        PalettedImage shared = PalettedImage.createPalettedImage(indexedGif());
        try {
            shared.setPalette(replacement);
            clear(graphics); graphics.drawImage(indexed, 0, 0); graphics.drawImage(shared, 2, 0);
            replacement.setEntry(1, 0xff00ff00);
            clear(graphics); graphics.drawImage(indexed, 0, 0); graphics.drawImage(shared, 2, 0);
            check(pixel(target, 1) == 0x00ff00 && pixel(target, 3) == 0x00ff00,
                    "A shared palette mutation updates every image using it");
            indexed.setAlpha(128);
            clear(graphics); graphics.drawImage(indexed, 0, 0);
            check(pixel(target, 1) == 0x008000, "Image alpha still applies to a previously drawn image");

            indexed.changeData(indexedGif());
            clear(graphics); graphics.drawImage(indexed, 0, 0);
            check(pixel(target, 0) == 0xff0000 && pixel(target, 1) == 0x00ff00,
                    "Replacing encoded data resets palette, alpha and transparency");
            var model = new IndexColorModel(8, 2, new byte[]{0, (byte)255},
                    new byte[]{0, (byte)255}, new byte[]{(byte)255, (byte)255});
            var smaller = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_INDEXED, model);
            var bytes = new ByteArrayOutputStream(); ImageIO.write(smaller, "gif", bytes);
            indexed.changeData(new java.io.ByteArrayInputStream(bytes.toByteArray()));
            clear(graphics); graphics.drawImage(indexed, 0, 0);
            check(indexed.getWidth() == 1 && pixel(target, 0) == 0x0000ff && pixel(target, 1) == 0,
                    "Stream data replacement updates image dimensions and pixels");
        } finally { shared.dispose(); }
    }

    private static byte[] indexedGif() throws Exception {
        var palette = new IndexColorModel(8, 2, new byte[]{(byte)255, 0}, new byte[]{0, (byte)255}, new byte[]{0, 0});
        var image = new BufferedImage(2, 1, BufferedImage.TYPE_BYTE_INDEXED, palette);
        image.getRaster().setSample(0, 0, 0, 0); image.getRaster().setSample(1, 0, 0, 1);
        var bytes = new ByteArrayOutputStream(); ImageIO.write(image, "gif", bytes); return bytes.toByteArray();
    }
    private static void verifyDoJaProfiles() {
        for (int version : new int[]{1, 5}) {
            DesktopLauncher.launch(LaunchConfig.builder(DojaProbeApp.class)
                    .parameter("ProfileVer", "DoJa-" + version + ".0").build());
            try {
                var image = com.nttdocomo.ui.Image.createImage(1, 1, new int[]{0xffff0000}, 0);
                image.setTransparentColor(0xffff0000);
                image.setTransparentEnabled(true);
                image.setAlpha(128);
                check(image.getAlpha() == (version == 5 ? 128 : 255), "Keep DoJa alpha version gate");
                int pixel = com.nttdocomo.ui._ImageInternalAccess.copyDisplayPixels(image)[0];
                check(pixel == (version == 5 ? 0 : 0xffff0000), "Keep DoJa transparent-color version gate");
                image.dispose();
                if (version == 1) check(image.getWidth() == 1, "Keep legacy DoJa disposal behavior");
                else {
                    try { image.getWidth(); throw new AssertionError("Disposed DoJa image accepted"); }
                    catch (com.nttdocomo.ui.UIException expected) { }
                }
            } finally { DoJaRuntime.current().shutdown(); }
        }
    }
    private static void clear(Graphics graphics) {
        graphics.setColor(Graphics.getColorOfRGB(0, 0, 0)); graphics.fillRect(0, 0, 4, 2);
    }
    private static int pixel(Image image, int index) throws Exception {
        var field = Image.class.getDeclaredField("delegate"); field.setAccessible(true);
        var host = (com.nttdocomo.ui.Image) field.get(image);
        return com.nttdocomo.ui._ImageInternalAccess.copyDisplayPixels(host)[index] & 0xffffff;
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    public static final class ProbeApp extends com.docomostar.StarApplication {
        @Override public void started(int reason) { }
    }
    public static final class DojaProbeApp extends com.nttdocomo.ui.IApplication {
        @Override public void start() { }
    }
}
