package opendoja.probes;

import com.nttdocomo.io.ConnectionException;
import com.nttdocomo.opt.ui.Graphics2;
import com.nttdocomo.opt.ui.Sprite;
import com.nttdocomo.opt.ui.SpriteSet;
import com.nttdocomo.opt.ui.j3d.AffineTrans;
import com.nttdocomo.ui.Graphics;
import com.nttdocomo.ui.Image;
import com.nttdocomo.ui.MediaImage;
import com.nttdocomo.ui.MediaManager;
import com.nttdocomo.ui.UIException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

public final class Graphics2ContractProbe {
    private static final int RED = 0xFFFF0000;
    private static final int BLUE = 0xFF0000FF;
    private static final int GREEN = 0xFF00FF00;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int BLACK = 0xFF000000;

    private Graphics2ContractProbe() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");

        verifyCoordinateAndRenderModeValidation();
        verifyGetImageContract();
        verifyAffineImageContract();
        verifyDrawNthImageContract();
        verifyDrawSpriteSetContract();
        verifyDrawNumberValidation();
        verifyIntermediateColorValidation();

        System.out.println("Graphics2 contract probe OK");
    }

    private static void verifyCoordinateAndRenderModeValidation() {
        Graphics2 graphics = graphics2(Image.createImage(2, 2).getGraphics());
        assertThrows("invalid coordinate mode", IllegalArgumentException.class, () -> graphics.setCoordinateMode(123));
        assertThrows("invalid render operator", IllegalArgumentException.class, () -> graphics.setRenderMode(9, 255, 255));
        assertThrows("invalid src ratio", IllegalArgumentException.class, () -> graphics.setRenderMode(Graphics2.OP_REPL, -1, 255));
        assertThrows("invalid dst ratio", IllegalArgumentException.class, () -> graphics.setRenderMode(Graphics2.OP_REPL, 255, 256));
    }

    private static void verifyGetImageContract() {
        Image canvas = Image.createImage(4, 4);
        Graphics2 graphics = graphics2(canvas.getGraphics());
        graphics.setRGBPixels(0, 0, 4, 4, new int[]{
                BLACK, RED, GREEN, BLUE,
                RED, GREEN, BLUE, WHITE,
                GREEN, BLUE, WHITE, BLACK,
                BLUE, WHITE, BLACK, RED
        }, 0);
        graphics.setClip(3, 3, 1, 1);

        Image partial = graphics.getImage(2, 1, 3, 3);
        check(partial != null, "partial getImage should return an image");
        check(partial.getWidth() == 2, "partial getImage width");
        check(partial.getHeight() == 3, "partial getImage height");
        assertPixel("partial getImage top-left", partial.getGraphics(), 0, 0, BLUE);
        assertPixel("partial getImage bottom-right", partial.getGraphics(), 1, 2, RED);

        Image outside = graphics.getImage(6, 6, 2, 2);
        check(outside == null, "fully clipped getImage should return null");

        assertThrows("getImage zero width", IllegalArgumentException.class, () -> graphics.getImage(0, 0, 0, 1));
        assertThrows("getImage zero height", IllegalArgumentException.class, () -> graphics.getImage(0, 0, 1, 0));
    }

    private static void verifyAffineImageContract() {
        Image source = Image.createImage(1, 1, new int[]{BLUE}, 0);
        Image target = Image.createImage(4, 4);
        Graphics2 graphics = graphics2(target.getGraphics());
        graphics.setRGBPixels(0, 0, 4, 4, fill(16, WHITE), 0);

        AffineTrans translate = new AffineTrans();
        translate.setIdentity();
        translate.m02 = 4096;
        translate.m12 = 8192;
        translate.m03 = 999 * 4096;
        translate.m13 = 999 * 4096;
        graphics.drawImage(source, translate);

        assertPixel("AffineTrans uses m02 for x translation", graphics, 1, 2, BLUE);
        assertPixel("AffineTrans ignores m03", graphics, 0, 0, WHITE);

        graphics.drawImage(source, translate, 0, 0, 0, 1);
        graphics.drawImage(source, translate, 0, 0, 1, 0);
        assertThrows("negative affine width", IllegalArgumentException.class,
                () -> graphics.drawImage(source, translate, 0, 0, -1, 1));

        source.dispose();
        assertThrows("disposed affine image", UIException.class, () -> graphics.drawImage(source, translate));
    }

    private static void verifyDrawNthImageContract() throws Exception {
        byte[] animatedGif = createAnimatedGif();
        MediaImage mediaImage = MediaManager.getImage(animatedGif);
        use(mediaImage);

        Image target = Image.createImage(2, 1);
        Graphics2 graphics = graphics2(target.getGraphics());
        graphics.setRGBPixels(0, 0, 2, 1, new int[]{WHITE, WHITE}, 0);
        graphics.drawNthImage(mediaImage, 1, 0, 0);
        assertPixel("drawNthImage second frame", graphics, 0, 0, BLUE);

        assertThrows("drawNthImage invalid frame", IllegalArgumentException.class, () -> graphics.drawNthImage(mediaImage, 2, 0, 0));
        mediaImage.unuse();
        assertThrows("drawNthImage unused image", UIException.class, () -> graphics.drawNthImage(mediaImage, 0, 0, 0));
    }

    private static void verifyDrawSpriteSetContract() {
        Graphics2 graphics = graphics2(Image.createImage(4, 4).getGraphics());

        Image spriteImage = Image.createImage(1, 1);
        Sprite sprite = new Sprite(spriteImage);
        spriteImage.dispose();
        assertThrows("drawSpriteSet disposed image", UIException.class,
                () -> graphics.drawSpriteSet(new SpriteSet(new Sprite[]{sprite})));
    }

    private static void verifyDrawNumberValidation() {
        Graphics2 graphics = graphics2(Image.createImage(4, 4).getGraphics());
        assertThrows("drawNumber digit <= 0", IllegalArgumentException.class, () -> graphics.drawNumber(0, 0, 12, 0));
    }

    private static void verifyIntermediateColorValidation() {
        check(Graphics2.getIntermediateColor(RED, BLUE, 0) == RED, "intermediate color ratio 0");
        check(Graphics2.getIntermediateColor(RED, BLUE, 255) == BLUE, "intermediate color ratio 255");
        assertThrows("intermediate color invalid ratio", IllegalArgumentException.class,
                () -> Graphics2.getIntermediateColor(RED, BLUE, 256));
    }

    private static byte[] createAnimatedGif() throws IOException {
        BufferedImage first = solid(RED);
        BufferedImage second = solid(BLUE);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("gif");
        check(writers.hasNext(), "GIF writer unavailable");
        ImageWriter writer = writers.next();
        try (MemoryCacheImageOutputStream imageOut = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(imageOut);
            writer.prepareWriteSequence(null);
            writer.writeToSequence(new IIOImage(first, null, frameMetadata(writer, first)), defaultWriteParam(writer));
            writer.writeToSequence(new IIOImage(second, null, frameMetadata(writer, second)), defaultWriteParam(writer));
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static ImageWriteParam defaultWriteParam(ImageWriter writer) {
        return writer.getDefaultWriteParam();
    }

    private static IIOMetadata frameMetadata(ImageWriter writer, BufferedImage image) throws IOException {
        ImageTypeSpecifier type = ImageTypeSpecifier.createFromBufferedImageType(image.getType());
        IIOMetadata metadata = writer.getDefaultImageMetadata(type, defaultWriteParam(writer));
        String format = metadata.getNativeMetadataFormatName();
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);

        IIOMetadataNode gce = child(root, "GraphicControlExtension");
        gce.setAttribute("disposalMethod", "none");
        gce.setAttribute("userInputFlag", "FALSE");
        gce.setAttribute("transparentColorFlag", "FALSE");
        gce.setAttribute("delayTime", "1");
        gce.setAttribute("transparentColorIndex", "0");

        IIOMetadataNode appExtensions = child(root, "ApplicationExtensions");
        IIOMetadataNode appExtension = new IIOMetadataNode("ApplicationExtension");
        appExtension.setAttribute("applicationID", "NETSCAPE");
        appExtension.setAttribute("authenticationCode", "2.0");
        appExtension.setUserObject(new byte[]{1, 0, 0});
        appExtensions.appendChild(appExtension);

        metadata.setFromTree(format, root);
        return metadata;
    }

    private static IIOMetadataNode child(IIOMetadataNode root, String name) {
        for (int i = 0; i < root.getLength(); i++) {
            if (name.equals(root.item(i).getNodeName())) {
                return (IIOMetadataNode) root.item(i);
            }
        }
        IIOMetadataNode child = new IIOMetadataNode(name);
        root.appendChild(child);
        return child;
    }

    private static BufferedImage solid(int argb) {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, argb);
        return image;
    }

    private static int[] fill(int length, int value) {
        int[] pixels = new int[length];
        for (int i = 0; i < length; i++) {
            pixels[i] = value;
        }
        return pixels;
    }

    private static void use(MediaImage mediaImage) {
        try {
            mediaImage.use();
        } catch (ConnectionException e) {
            throw new IllegalStateException("mediaImage.use() failed", e);
        }
    }

    private static Graphics2 graphics2(Graphics graphics) {
        check(graphics instanceof Graphics2, "Expected platform Graphics2");
        return (Graphics2) graphics;
    }

    private static void assertPixel(String label, Graphics graphics, int x, int y, int expected) {
        int actual = graphics.getRGBPixel(x, y);
        if (actual != expected) {
            throw new IllegalStateException(label + " expected=0x" + Integer.toHexString(expected)
                    + " actual=0x" + Integer.toHexString(actual));
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static void assertThrows(String label, Class<? extends Throwable> expected, ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Throwable throwable) {
            if (expected.isInstance(throwable)) {
                return;
            }
            throw new IllegalStateException(label + " expected=" + expected.getName()
                    + " actual=" + throwable.getClass().getName(), throwable);
        }
        throw new IllegalStateException(label + " expected exception " + expected.getName());
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Throwable;
    }
}
