package opendoja.host.flash;

import com.jpexs.decompiler.flash.SWF;
import com.jpexs.decompiler.flash.exporters.commonshape.Matrix;
import com.jpexs.decompiler.flash.tags.SetBackgroundColorTag;
import com.jpexs.decompiler.flash.types.ColorTransform;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/** SWF timeline rendering supplied by FFDec; does not execute ActionScript or network requests. */
public final class FlashMovie {
    private final SWF swf;
    private final Color background;

    public FlashMovie(byte[] data) throws IOException, InterruptedException {
        swf = new SWF(new ByteArrayInputStream(java.util.Objects.requireNonNull(data)), true);
        if (swf.getFrameCount() < 1 || !Float.isFinite(swf.frameRate) || swf.frameRate <= 0)
            throw new IOException("SWF has no playable timeline");
        long width = (swf.displayRect.getWidth() + 19L) / 20;
        long height = (swf.displayRect.getHeight() + 19L) / 20;
        if (width <= 0 || height <= 0 || width > 4096 || height > 4096 || width * height > 8_388_608)
            throw new IOException("Unsupported SWF stage size");
        Color color = Color.WHITE;
        for (var tag : swf.getTags()) if (tag instanceof SetBackgroundColorTag bg) color = bg.backgroundColor.toColor();
        background = color;
    }

    public int frameCount() { return swf.getFrameCount(); }
    public long frameIntervalMillis() { return Math.max(1, Math.round(1000.0 / swf.frameRate)); }
    public com.nttdocomo.ui.Image render(int frame, int time, boolean smoothing) {
        BufferedImage image = SWF.frameToImageGet(swf.getTimeline(), frame, time, null, 0,
                swf.displayRect, new Matrix(), new ColorTransform(), background, 1, smoothing, 1).getBufferedImage();
        return new FrameImage(image);
    }

    private static final class FrameImage extends com.nttdocomo.ui.Image {
        private final BufferedImage image;
        FrameImage(BufferedImage image) { this.image = image; }
        @Override public int getWidth() { return image.getWidth(); }
        @Override public int getHeight() { return image.getHeight(); }
        @Override protected BufferedImage renderForDisplayImpl() { return image; }
        @Override public void dispose() { image.flush(); }
    }
}
