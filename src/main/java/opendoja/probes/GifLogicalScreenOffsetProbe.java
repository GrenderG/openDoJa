package opendoja.probes;

import com.nttdocomo.ui.Image;
import com.nttdocomo.ui.MediaImage;
import com.nttdocomo.ui.MediaManager;

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
import java.util.Iterator;

public final class GifLogicalScreenOffsetProbe {
    private static final int TRANSPARENT = 0x00000000;
    private static final int RED = 0xFFFF0000;
    private static final int BLUE = 0xFF0000FF;

    private GifLogicalScreenOffsetProbe() {
    }

    public static void main(String[] args) throws Exception {
        byte[] gif = createOffsetGif();
        MediaImage mediaImage = MediaManager.getImage(gif);
        mediaImage.use();
        Image image = mediaImage.getImage();
        check(image.getWidth() == 4, "logical width");
        check(image.getHeight() == 4, "logical height");
        check(image.getGraphics().getRGBPixel(0, 0) == TRANSPARENT, "top transparent row preserved");
        check(image.getGraphics().getRGBPixel(0, 1) == TRANSPARENT, "second transparent row preserved");
        check(image.getGraphics().getRGBPixel(0, 2) == RED, "offset row 0");
        check(image.getGraphics().getRGBPixel(0, 3) == BLUE, "offset row 1");
        mediaImage.unuse();
        System.out.println("GIF logical-screen offset probe OK");
    }

    private static byte[] createOffsetGif() throws Exception {
        BufferedImage image = new BufferedImage(4, 2, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 4; x++) {
            image.setRGB(x, 0, RED);
            image.setRGB(x, 1, BLUE);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("gif");
        check(writers.hasNext(), "GIF writer unavailable");
        ImageWriter writer = writers.next();
        try (MemoryCacheImageOutputStream imageOut = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(imageOut);
            IIOMetadata streamMetadata = writer.getDefaultStreamMetadata(defaultWriteParam(writer));
            streamMetadata.setFromTree(streamMetadata.getNativeMetadataFormatName(), streamRoot(4, 4));
            IIOMetadata imageMetadata = writer.getDefaultImageMetadata(
                    ImageTypeSpecifier.createFromBufferedImageType(image.getType()),
                    defaultWriteParam(writer));
            imageMetadata.setFromTree(imageMetadata.getNativeMetadataFormatName(), imageRoot(image, 0, 2));
            writer.write(streamMetadata, new IIOImage(image, null, imageMetadata), defaultWriteParam(writer));
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static ImageWriteParam defaultWriteParam(ImageWriter writer) {
        return writer.getDefaultWriteParam();
    }

    private static IIOMetadataNode streamRoot(int logicalWidth, int logicalHeight) {
        IIOMetadataNode root = new IIOMetadataNode("javax_imageio_gif_stream_1.0");
        IIOMetadataNode version = new IIOMetadataNode("Version");
        version.setAttribute("value", "89a");
        root.appendChild(version);

        IIOMetadataNode descriptor = new IIOMetadataNode("LogicalScreenDescriptor");
        descriptor.setAttribute("logicalScreenWidth", Integer.toString(logicalWidth));
        descriptor.setAttribute("logicalScreenHeight", Integer.toString(logicalHeight));
        descriptor.setAttribute("colorResolution", "8");
        descriptor.setAttribute("pixelAspectRatio", "0");
        root.appendChild(descriptor);
        return root;
    }

    private static IIOMetadataNode imageRoot(BufferedImage image, int left, int top) {
        IIOMetadataNode root = new IIOMetadataNode("javax_imageio_gif_image_1.0");

        IIOMetadataNode descriptor = new IIOMetadataNode("ImageDescriptor");
        descriptor.setAttribute("imageLeftPosition", Integer.toString(left));
        descriptor.setAttribute("imageTopPosition", Integer.toString(top));
        descriptor.setAttribute("imageWidth", Integer.toString(image.getWidth()));
        descriptor.setAttribute("imageHeight", Integer.toString(image.getHeight()));
        descriptor.setAttribute("interlaceFlag", "FALSE");
        root.appendChild(descriptor);

        IIOMetadataNode control = new IIOMetadataNode("GraphicControlExtension");
        control.setAttribute("disposalMethod", "none");
        control.setAttribute("userInputFlag", "FALSE");
        control.setAttribute("transparentColorFlag", "TRUE");
        control.setAttribute("delayTime", "0");
        control.setAttribute("transparentColorIndex", "0");
        root.appendChild(control);

        return root;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
