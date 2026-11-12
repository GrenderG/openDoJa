package com.docomostar.ui;

import com.docomostar.media.EncodedImage;

/** STAR image encoding through the existing DoJa JPEG encoder. */
public class ImageEncoder {
    public static final int QUALITY = com.nttdocomo.ui.ImageEncoder.QUALITY;
    public static final int ATTR_QUALITY_HIGH = com.nttdocomo.ui.ImageEncoder.ATTR_QUALITY_HIGH;
    public static final int ATTR_QUALITY_STANDARD = com.nttdocomo.ui.ImageEncoder.ATTR_QUALITY_STANDARD;
    public static final int ATTR_QUALITY_LOW = com.nttdocomo.ui.ImageEncoder.ATTR_QUALITY_LOW;
    private final com.nttdocomo.ui.ImageEncoder delegate;

    ImageEncoder(String format) { delegate = com.nttdocomo.ui.ImageEncoder.getEncoder(format); }
    public static ImageEncoder getEncoder(String format) {
        try { return new ImageEncoder(format); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void setAttribute(int attr, int value) { delegate.setAttribute(attr, value); }
    public final boolean isAvailable(int attr) { return delegate.isAvailable(attr); }
    public EncodedImage encode(Image image, int x, int y, int width, int height) {
        java.util.Objects.requireNonNull(image, "image");
        try { return new EncodedImage(delegate.encode(image.delegate, x, y, width, height)); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
        catch (com.nttdocomo.lang.UnsupportedOperationException failure) { throw unsupported(failure); }
    }
    public EncodedImage encode(Canvas canvas, int x, int y, int width, int height) {
        java.util.Objects.requireNonNull(canvas, "canvas");
        try { return new EncodedImage(delegate.encode((com.nttdocomo.ui.Canvas) canvas.hostFrame, x, y, width, height)); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
        catch (com.nttdocomo.lang.UnsupportedOperationException failure) { throw unsupported(failure); }
    }
    private static com.docomostar.lang.UnsupportedOperationException unsupported(RuntimeException failure) {
        var translated = new com.docomostar.lang.UnsupportedOperationException(failure.getMessage());
        translated.initCause(failure);
        return translated;
    }
}
