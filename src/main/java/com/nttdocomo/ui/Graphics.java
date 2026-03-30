package com.nttdocomo.ui;

import com.nttdocomo.lang.XString;
import opendoja.host.DesktopSurface;
import opendoja.g3d.MascotFigure;
import opendoja.g3d.Software3DContext;
import opendoja.g3d.SoftwareTexture;
import opendoja.host.DoJaApiUnimplemented;
import opendoja.host.DoJaRuntime;
import opendoja.host.OpenDoJaLog;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

public class Graphics implements com.nttdocomo.ui.graphics3d.Graphics3D, com.nttdocomo.opt.ui.j3d.Graphics3D {
    private static final boolean TRACE_FAILURES = opendoja.host.OpenDoJaLaunchArgs.getBoolean(opendoja.host.OpenDoJaLaunchArgs.TRACE_FAILURES);
    private static final boolean TRACE_3D_CALLS = opendoja.host.OpenDoJaLaunchArgs.getBoolean(opendoja.host.OpenDoJaLaunchArgs.DEBUG3D_CALLS);
    public static final int BLACK = 0;
    public static final int BLUE = 1;
    public static final int LIME = 2;
    public static final int AQUA = 3;
    public static final int RED = 4;
    public static final int FUCHSIA = 5;
    public static final int YELLOW = 6;
    public static final int WHITE = 7;
    public static final int GRAY = 8;
    public static final int NAVY = 9;
    public static final int GREEN = 10;
    public static final int TEAL = 11;
    public static final int MAROON = 12;
    public static final int PURPLE = 13;
    public static final int OLIVE = 14;
    public static final int SILVER = 15;
    public static final int FLIP_NONE = 0;
    public static final int FLIP_HORIZONTAL = 1;
    public static final int FLIP_VERTICAL = 2;
    public static final int FLIP_ROTATE = 3;
    public static final int FLIP_ROTATE_LEFT = 4;
    public static final int FLIP_ROTATE_RIGHT = 5;
    public static final int FLIP_ROTATE_RIGHT_HORIZONTAL = 6;
    public static final int FLIP_ROTATE_RIGHT_VERTICAL = 7;

    private final DesktopSurface surface;
    private final Graphics2D delegate;
    private final Software3DContext threeD = new Software3DContext();
    private int originX;
    private int originY;
    private int color = getColorOfName(BLACK);
    private Font font = Font.getDefaultFont();
    private int flipMode = FLIP_NONE;
    private com.nttdocomo.opt.ui.j3d.AffineTrans[] optViewTransforms = new com.nttdocomo.opt.ui.j3d.AffineTrans[0];
    private static volatile java.lang.reflect.Constructor<?> platformGraphicsConstructor;

    /**
     * Applications cannot create this object directly.
     */
    protected Graphics() {
        this(new DesktopSurface(1, 1));
    }

    protected Graphics(DesktopSurface surface) {
        this.surface = surface;
        this.delegate = surface.image().createGraphics();
        this.delegate.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        this.delegate.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        this.delegate.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, Font.textAntialiasHint());
        this.delegate.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        this.delegate.setColor(new Color(color, true));
        this.delegate.setFont(font.awtFont());
        clearClip();
    }

    static Graphics createPlatformGraphics(DesktopSurface surface) {
        try {
            java.lang.reflect.Constructor<?> constructor = platformGraphicsConstructor;
            if (constructor == null) {
                Class<?> clazz = Class.forName("com.nttdocomo.opt.ui.Graphics2");
                constructor = clazz.getDeclaredConstructor(DesktopSurface.class);
                constructor.setAccessible(true);
                platformGraphicsConstructor = constructor;
            }
            return (Graphics) constructor.newInstance(surface);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return new Graphics(surface);
        }
    }

    public void clearClip() {
        delegate.setClip(0, 0, surface.width(), surface.height());
    }

    public void clearRect(int x, int y, int width, int height) {
        Color old = delegate.getColor();
        delegate.setColor(new Color(surface.backgroundColor(), true));
        delegate.fillRect(originX + x, originY + y, width, height);
        delegate.setColor(old);
        flushSurfacePresentation();
    }

    public void clipRect(int x, int y, int width, int height) {
        delegate.clipRect(originX + x, originY + y, width, height);
    }

    public Graphics copy() {
        Graphics copy = createPlatformGraphics(surface);
        copy.originX = originX;
        copy.originY = originY;
        copy.color = color;
        copy.font = font;
        copy.flipMode = flipMode;
        Shape clip = delegate.getClip();
        if (clip != null) {
            copy.delegate.setClip(clip);
        }
        copy.delegate.setColor(new Color(color, true));
        copy.delegate.setFont(font.awtFont());
        return copy;
    }

    /**
     * Returns the current origin X coordinate.
     */
    protected final int getOriginX() {
        return originX;
    }

    /**
     * Returns the current origin Y coordinate.
     */
    protected final int getOriginY() {
        return originY;
    }

    /**
     * Captures an image from the current drawing surface.
     *
     * @param x the left edge of the region
     * @param y the top edge of the region
     * @param width the region width
     * @param height the region height
     * @return a new image containing the requested region
     */
    protected final Image getImageRegion(int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return Image.createImage(1, 1);
        }
        int srcX = Math.max(0, originX + x);
        int srcY = Math.max(0, originY + y);
        int srcWidth = Math.max(0, java.lang.Math.min(width, surface.width() - srcX));
        int srcHeight = Math.max(0, java.lang.Math.min(height, surface.height() - srcY));
        BufferedImage copy = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        if (srcWidth > 0 && srcHeight > 0) {
            Graphics2D g2 = copy.createGraphics();
            try {
                g2.drawImage(surface.image(),
                        0,
                        0,
                        srcWidth,
                        srcHeight,
                        srcX,
                        srcY,
                        srcX + srcWidth,
                        srcY + srcHeight,
                        null);
            } finally {
                g2.dispose();
            }
        }
        return new DesktopImage(copy);
    }

    // DoJa titles scroll cached surfaces with copyArea requests that can hang partly outside
    // the backing image; clip the source rectangle but keep dx/dy as translation offsets.
    public void copyArea(int x, int y, int width, int height, int dx, int dy) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int srcX = originX + x;
        int srcY = originY + y;
        int clippedSrcX = java.lang.Math.max(0, srcX);
        int clippedSrcY = java.lang.Math.max(0, srcY);
        int clippedSrcRight = java.lang.Math.min(srcX + width, surface.width());
        int clippedSrcBottom = java.lang.Math.min(srcY + height, surface.height());
        int clippedWidth = clippedSrcRight - clippedSrcX;
        int clippedHeight = clippedSrcBottom - clippedSrcY;
        if (clippedWidth <= 0 || clippedHeight <= 0) {
            return;
        }

        int destX = clippedSrcX + dx;
        int destY = clippedSrcY + dy;
        BufferedImage source = new BufferedImage(clippedWidth, clippedHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D sourceGraphics = source.createGraphics();
        try {
            sourceGraphics.drawImage(surface.image(),
                    0,
                    0,
                    clippedWidth,
                    clippedHeight,
                    clippedSrcX,
                    clippedSrcY,
                    clippedSrcX + clippedWidth,
                    clippedSrcY + clippedHeight,
                    null);
        } finally {
            sourceGraphics.dispose();
        }
        delegate.drawImage(source, destX, destY, null);
        flushSurfacePresentation();
    }

    public void dispose() {
        delegate.dispose();
    }

    public void drawChars(char[] data, int x, int y, int offset, int length) {
        // DoJa uses the order (chars, x, y, offset, length), unlike Java SE/AWT.
        drawString(new String(data, offset, length), x, y);
    }

    public void drawImage(Image image, int x, int y) {
        drawSubImage(image, x, y, 0, 0, image.getWidth(), image.getHeight(), image.getWidth(), image.getHeight());
    }

    public void drawSpriteSet(SpriteSet spriteSet) {
        drawSpriteSet(spriteSet, 0, 0);
    }

    public void drawSpriteSet(SpriteSet spriteSet, int dx, int dy) {
        if (spriteSet == null) {
            return;
        }
        for (Sprite sprite : spriteSet.getSprites()) {
            if (sprite != null && sprite.isVisible()) {
                drawImage(sprite.image(), dx + sprite.getX(), dy + sprite.getY(), sprite.sourceX(), sprite.sourceY(), sprite.getWidth(), sprite.getHeight());
            }
        }
    }

    public void drawImageMap(ImageMap imageMap, int x, int y) {
        if (imageMap != null) {
            imageMap.draw(this, x, y);
        }
    }

    public void drawImage(Image image, int x, int y, int sx, int sy, int width, int height) {
        drawSubImage(image, x, y, sx, sy, width, height, width, height);
    }

    public void drawLine(int x1, int y1, int x2, int y2) {
        delegate.drawLine(originX + x1, originY + y1, originX + x2, originY + y2);
        flushSurfacePresentation();
    }

    public void drawPolyline(int[] xs, int[] ys, int n) {
        drawPolyline(xs, ys, n, 0);
    }

    public void drawPolyline(int[] xs, int[] ys, int n, int mode) {
        if (xs == null || ys == null || n <= 0) {
            return;
        }
        int[] drawX = new int[n];
        int[] drawY = new int[n];
        for (int i = 0; i < n; i++) {
            drawX[i] = originX + xs[i];
            drawY[i] = originY + ys[i];
        }
        delegate.drawPolyline(drawX, drawY, n);
        flushSurfacePresentation();
    }

    public void drawRect(int x, int y, int width, int height) {
        delegate.drawRect(originX + x, originY + y, width, height);
        flushSurfacePresentation();
    }

    public void drawString(String text, int x, int y) {
        if (text == null) {
            return;
        }
        font.drawString(delegate, text, originX + x, originY + y, color);
        flushSurfacePresentation();
    }

    public void fillPolygon(int[] xs, int[] ys, int n) {
        fillPolygon(xs, ys, n, 0);
    }

    public void fillPolygon(int[] xs, int[] ys, int n, int mode) {
        if (xs == null || ys == null || n <= 0) {
            return;
        }
        int[] drawX = new int[n];
        int[] drawY = new int[n];
        for (int i = 0; i < n; i++) {
            drawX[i] = originX + xs[i];
            drawY[i] = originY + ys[i];
        }
        delegate.fillPolygon(drawX, drawY, n);
        flushSurfacePresentation();
    }

    public void fillRect(int x, int y, int width, int height) {
        delegate.fillRect(originX + x, originY + y, width, height);
        flushSurfacePresentation();
    }

    public static int getColorOfName(int name) {
        return switch (name) {
            case BLACK -> 0xFF000000;
            case BLUE -> 0xFF0000FF;
            case LIME -> 0xFF00FF00;
            case AQUA -> 0xFF00FFFF;
            case RED -> 0xFFFF0000;
            case FUCHSIA -> 0xFFFF00FF;
            case YELLOW -> 0xFFFFFF00;
            case WHITE -> 0xFFFFFFFF;
            case GRAY -> 0xFF808080;
            case NAVY -> 0xFF000080;
            case GREEN -> 0xFF008000;
            case TEAL -> 0xFF008080;
            case MAROON -> 0xFF800000;
            case PURPLE -> 0xFF800080;
            case OLIVE -> 0xFF808000;
            case SILVER -> 0xFFC0C0C0;
            default -> 0xFF000000;
        };
    }

    public static int getColorOfRGB(int red, int green, int blue) {
        return getColorOfRGB(red, green, blue, 255);
    }

    public static int getColorOfRGB(int red, int green, int blue, int alpha) {
        return ((alpha & 0xFF) << 24) | ((red & 0xFF) << 16) | ((green & 0xFF) << 8) | (blue & 0xFF);
    }

    public void lock() {
        DoJaRuntime runtime = DoJaRuntime.current();
        if (runtime != null) {
            runtime.surfaceLock().lock();
        }
    }

    public void setClip(int x, int y, int width, int height) {
        delegate.setClip(originX + x, originY + y, width, height);
    }

    public void setColor(int color) {
        this.color = color;
        delegate.setColor(new Color(color, true));
    }

    public void setFont(Font font) {
        if (font == null) {
            return;
        }
        this.font = font;
        delegate.setFont(font.awtFont());
    }

    public void setOrigin(int x, int y) {
        this.originX = x;
        this.originY = y;
    }

    public void setPictoColorEnabled(boolean enabled) {
        DoJaApiUnimplemented.noOp(
                "com.nttdocomo.ui.Graphics.setPictoColorEnabled(boolean)",
                "Picto-color toggling has no separate desktop host implementation"
        );
    }

    public void unlock(boolean flush) {
        DoJaRuntime runtime = DoJaRuntime.current();
        BufferedImage presentedFrame = null;
        boolean outermostUnlock = runtime == null || runtime.surfaceLock().getHoldCount() == 1;
        boolean pacedPresentation = flush;
        // opt `renderPrimitives(...)` framebuffer blends participate in the same staged 3D pass
        // as later opaque draws. Replay them only at the pass boundary so opaque geometry fills
        // z first, then the blended pass can depth-test against that result.
        if (flush || outermostUnlock) {
            flushPending3DPasses();
        }
        if (flush) {
            presentedFrame = copyImage(surface.image());
        } else if (outermostUnlock && surface.hasRepaintHook()) {
            // Some games draw directly to Canvas.getGraphics() and finish the frame with unlock(false)
            // rather than unlock(true). Canvas surfaces still need to present at the end of that
            // outermost lock scope, while offscreen Image surfaces must remain offscreen.
            // These loops already own their pacing, so present without the sync-unlock delay that
            // explicit unlock(true)/flush() paths use.
            presentedFrame = copyImage(surface.image());
            pacedPresentation = false;
        }
        if (runtime != null) {
            runtime.surfaceLock().unlock();
        }
        if (outermostUnlock && presentedFrame == null) {
            surface.endDepthFrame();
        }
        if (presentedFrame != null) {
            surface.flush(presentedFrame, pacedPresentation);
        }
    }

    public void setFlipMode(int flipMode) {
        this.flipMode = flipMode;
    }

    public void drawScaledImage(Image image, int dx, int dy, int dw, int dh, int sx, int sy, int sw, int sh) {
        drawSubImage(image, dx, dy, sx, sy, sw, sh, dw, dh);
    }

    public void drawString(XString text, int x, int y) {
        drawString(text == null ? null : text.toString(), x, y);
    }

    public void drawString(XString text, int x, int y, int offset, int length) {
        if (text == null) {
            return;
        }
        String value = text.toString();
        int start = Math.max(0, offset);
        int end = Math.min(value.length(), start + Math.max(0, length));
        drawString(value.substring(start, end), x, y);
    }

    public void drawArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        delegate.drawArc(originX + x, originY + y, width, height, startAngle, arcAngle);
        flushSurfacePresentation();
    }

    public void fillArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        delegate.fillArc(originX + x, originY + y, width, height, startAngle, arcAngle);
        flushSurfacePresentation();
    }

    public int getPixel(int x, int y) {
        return surface.image().getRGB(originX + x, originY + y);
    }

    public int getRGBPixel(int x, int y) {
        return getPixel(x, y);
    }

    public int[] getPixels(int x, int y, int width, int height, int[] pixels, int offset) {
        return getRGBPixels(x, y, width, height, pixels, offset);
    }

    public int[] getRGBPixels(int x, int y, int width, int height, int[] pixels, int offset) {
        int[] target = pixels == null ? new int[offset + (width * height)] : pixels;
        surface.image().getRGB(originX + x, originY + y, width, height, target, offset, width);
        return target;
    }

    public void setPixel(int x, int y) {
        setRGBPixel(x, y, color);
    }

    public void setPixel(int x, int y, int color) {
        setRGBPixel(x, y, color);
    }

    public void setRGBPixel(int x, int y, int color) {
        surface.image().setRGB(originX + x, originY + y, color);
        flushSurfacePresentation();
    }

    public void setPixels(int x, int y, int width, int height, int[] pixels, int offset) {
        setRGBPixels(x, y, width, height, pixels, offset);
    }

    public void setRGBPixels(int x, int y, int width, int height, int[] pixels, int offset) {
        surface.image().setRGB(originX + x, originY + y, width, height, pixels, offset, width);
        flushSurfacePresentation();
    }

    public void drawImage(Image image, int[] points, int sx, int sy, int width, int height) {
        if (points == null || points.length < 4) {
            drawImage(image, 0, 0, sx, sy, width, height);
            return;
        }
        int minX = points[0];
        int minY = points[1];
        int maxX = points[0];
        int maxY = points[1];
        for (int i = 0; i + 1 < points.length; i += 2) {
            minX = Math.min(minX, points[i]);
            minY = Math.min(minY, points[i + 1]);
            maxX = Math.max(maxX, points[i]);
            maxY = Math.max(maxY, points[i + 1]);
        }
        drawScaledImage(image, minX, minY, Math.max(1, maxX - minX), Math.max(1, maxY - minY), sx, sy, width, height);
    }

    public void drawImage(Image image, int[] points) {
        drawImage(image, points, 0, 0, image.getWidth(), image.getHeight());
    }

    private void drawSubImage(Image image, int dx, int dy, int sx, int sy, int sw, int sh, int dw, int dh) {
        BufferedImage source = image == null ? null : image.renderForDisplay();
        if (source == null || sw <= 0 || sh <= 0 || dw == 0 || dh == 0) {
            return;
        }
        int srcX1 = sx;
        int srcY1 = sy;
        int srcX2 = sx + sw;
        int srcY2 = sy + sh;
        int clippedSrcX1 = Math.max(0, srcX1);
        int clippedSrcY1 = Math.max(0, srcY1);
        int clippedSrcX2 = Math.min(source.getWidth(), srcX2);
        int clippedSrcY2 = Math.min(source.getHeight(), srcY2);
        if (clippedSrcX1 >= clippedSrcX2 || clippedSrcY1 >= clippedSrcY2) {
            return;
        }
        // DoJa-style source rectangles can fall partially outside the image. Clip them and remap
        // the destination proportionally instead of delegating to getSubimage(), which throws.
        int destX1 = originX + dx + Math.round((clippedSrcX1 - srcX1) * (dw / (float) sw));
        int destY1 = originY + dy + Math.round((clippedSrcY1 - srcY1) * (dh / (float) sh));
        int destX2 = originX + dx + Math.round((clippedSrcX2 - srcX1) * (dw / (float) sw));
        int destY2 = originY + dy + Math.round((clippedSrcY2 - srcY1) * (dh / (float) sh));
        AffineTransform oldTransform = delegate.getTransform();
        try {
            applyFlipTransform(dx, dy, dw, dh);
            if (image.getAlpha() < 255) {
                delegate.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, image.getAlpha() / 255.0f));
            }
            delegate.drawImage(source, destX1, destY1, destX2, destY2, clippedSrcX1, clippedSrcY1, clippedSrcX2, clippedSrcY2, null);
            delegate.setComposite(AlphaComposite.SrcOver);
        } finally {
            delegate.setTransform(oldTransform);
        }
        flushSurfacePresentation();
    }

    private BufferedImage copyImage(BufferedImage image) {
        BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = copy.createGraphics();
        try {
            g2.drawImage(image, 0, 0, null);
        } finally {
            g2.dispose();
        }
        return copy;
    }

    private void flushSurfacePresentation() {
        if (!surface.immediatePresentationEnabled()) {
            return;
        }
        DoJaRuntime runtime = DoJaRuntime.current();
        if (runtime != null && runtime.surfaceLock().isHeldByCurrentThread()) {
            return;
        }
        surface.presentImmediately(copyImage(surface.image()));
    }

    private void flushSurfaceFrame() {
        // `Graphics3D.flush()` is the pass boundary for staged opt draws inside one locked frame.
        // Finish any deferred blended primitive batches before ending the shared depth frame.
        flushPending3DPasses();
        DoJaRuntime runtime = DoJaRuntime.current();
        if (runtime != null && runtime.surfaceLock().isHeldByCurrentThread()) {
            // opt.ui.j3d titles can issue multiple flushes inside one locked Canvas frame to
            // separate 3D passes. Do not present mid-frame, but do end the shared z-frame so a
            // later orthographic/translucent pass does not depth-test against the earlier scene.
            surface.endDepthFrame();
            return;
        }
        surface.flush(copyImage(surface.image()));
    }

    private void flushPending3DPasses() {
        threeD.flushPendingOptPrimitiveBlends(delegate, surface.image());
    }

    private void prepare3DDepthFrame() {
        // Games can submit 3D assets through separate 3D calls inside one
        // lock/unlock frame. They must share one z-buffer or later props ignore ramp depth.
        threeD.setFrameDepthBuffer(surface.depthBufferForFrame());
    }

    private void applyFlipTransform(int dx, int dy, int dw, int dh) {
        switch (flipMode) {
            case FLIP_NONE -> {
            }
            case FLIP_HORIZONTAL -> {
                delegate.translate(originX + dx + dw, originY + dy);
                delegate.scale(-1, 1);
                delegate.translate(-(originX + dx), -(originY + dy));
            }
            case FLIP_VERTICAL -> {
                delegate.translate(originX + dx, originY + dy + dh);
                delegate.scale(1, -1);
                delegate.translate(-(originX + dx), -(originY + dy));
            }
            case FLIP_ROTATE, FLIP_ROTATE_RIGHT -> {
                delegate.rotate(Math.toRadians(90), originX + dx + dw / 2.0, originY + dy + dh / 2.0);
            }
            case FLIP_ROTATE_LEFT -> {
                delegate.rotate(Math.toRadians(-90), originX + dx + dw / 2.0, originY + dy + dh / 2.0);
            }
            case FLIP_ROTATE_RIGHT_HORIZONTAL -> {
                delegate.rotate(Math.toRadians(90), originX + dx + dw / 2.0, originY + dy + dh / 2.0);
                delegate.scale(-1, 1);
            }
            case FLIP_ROTATE_RIGHT_VERTICAL -> {
                delegate.rotate(Math.toRadians(90), originX + dx + dw / 2.0, originY + dy + dh / 2.0);
                delegate.scale(1, -1);
            }
            default -> {
            }
        }
    }

    @Override
    public void setClipRectFor3D(int x, int y, int width, int height) {
        try {
            threeD.setUiClip(originX + x, originY + y, width, height);
        } catch (RuntimeException e) {
            throw traceFailure("setClipRectFor3D", e);
        }
    }

    @Override
    public void setParallelView(int width, int height) {
        try {
            threeD.setUiParallelView(width, height);
            if (TRACE_3D_CALLS) {
                OpenDoJaLog.debug(Graphics.class, () -> "3D call setParallelView width=" + width + " height=" + height);
            }
        } catch (RuntimeException e) {
            throw traceFailure("setParallelView", e);
        }
    }

    @Override
    public void setPerspectiveView(float a, float b, int c, int d) {
        try {
            threeD.setUiPerspectiveView(a, b, c, d);
            if (TRACE_3D_CALLS) {
                OpenDoJaLog.debug(Graphics.class, () -> "3D call setPerspectiveViewWH near=" + a + " far=" + b + " width=" + c + " height=" + d);
            }
        } catch (RuntimeException e) {
            throw traceFailure("setPerspectiveView(float,float,int,int)", e);
        }
    }

    @Override
    public void setPerspectiveView(float a, float b, float c) {
        try {
            threeD.setUiPerspectiveView(a, b, c);
            if (TRACE_3D_CALLS) {
                OpenDoJaLog.debug(Graphics.class, () -> "3D call setPerspectiveViewFov near=" + a + " far=" + b + " angle=" + c);
            }
        } catch (RuntimeException e) {
            throw traceFailure("setPerspectiveView(float,float,float)", e);
        }
    }

    @Override
    public void flushBuffer() {
        try {
            if (TRACE_3D_CALLS) {
                OpenDoJaLog.debug(Graphics.class, "3D call flushBuffer");
            }
            flushSurfaceFrame();
        } catch (RuntimeException e) {
            throw traceFailure("flushBuffer", e);
        }
    }

    @Override
    public void setTransform(com.nttdocomo.ui.util3d.Transform transform) {
        try {
            float[] matrix = transform == null ? Software3DContext.identity() : invokeHidden(transform, "raw", float[].class);
            threeD.setUiTransform(matrix);
            if (TRACE_3D_CALLS) {
                OpenDoJaLog.debug(Graphics.class, () -> "3D call setTransform matrix=" + Arrays.toString(matrix));
            }
        } catch (RuntimeException e) {
            throw traceFailure("setTransform", e);
        }
    }

    @Override
    public void addLight(com.nttdocomo.ui.graphics3d.Light light, com.nttdocomo.ui.util3d.Transform transform) {
        if (light == null) {
            return;
        }
        try {
            threeD.addUiLight(invokeHiddenInt(light, "mode"), invokeHiddenFloat(light, "intensity"), invokeHiddenInt(light, "color"));
        } catch (RuntimeException e) {
            throw traceFailure("addLight", e);
        }
    }

    @Override
    public void resetLights() {
        threeD.resetUiLights();
    }

    @Override
    public void setFog(com.nttdocomo.ui.graphics3d.Fog fog) {
        DoJaApiUnimplemented.noOp(
                "com.nttdocomo.ui.Graphics.setFog(com.nttdocomo.ui.graphics3d.Fog)",
                "UI fog state is not yet wired into the desktop 3D renderer"
        );
    }

    @Override
    public void renderObject3D(com.nttdocomo.ui.graphics3d.DrawableObject3D object, com.nttdocomo.ui.util3d.Transform transform) {
        if (object == null) {
            return;
        }
        try {
            float[] objectMatrix = transform == null ? null : invokeHidden(transform, "raw", float[].class);
            if (object instanceof com.nttdocomo.ui.graphics3d.Figure figure) {
                MascotFigure handle = invokeHidden(figure, "handle", MascotFigure.class);
                prepare3DDepthFrame();
                if (TRACE_3D_CALLS) {
                    int polygons = handle == null || handle.model() == null ? -1 : handle.model().polygons().length;
                    OpenDoJaLog.debug(Graphics.class, () -> "3D call renderObject3D type=Figure polygons=" + polygons
                            + " textures=" + (handle == null ? -1 : handle.numTextures())
                            + " pattern=" + (handle == null ? -1 : handle.patternMask())
                            + " transform=" + (objectMatrix == null ? "null" : Arrays.toString(objectMatrix)));
                }
                threeD.renderUiFigure(delegate, surface.image(), originX, originY, surface.width(), surface.height(), handle, objectMatrix, invokeHiddenInt(object, "blendModeValue"), invokeHiddenFloat(object, "transparencyValue"));
            } else if (object instanceof com.nttdocomo.ui.graphics3d.Primitive primitive) {
                SoftwareTexture texture = invokeHidden(primitive, "textureHandle", SoftwareTexture.class);
                prepare3DDepthFrame();
                if (TRACE_3D_CALLS) {
                    OpenDoJaLog.debug(Graphics.class, () -> "3D call renderObject3D type=Primitive primitiveType="
                            + primitive.getPrimitiveType()
                            + " count=" + primitive.size()
                            + " transform=" + (objectMatrix == null ? "null" : Arrays.toString(objectMatrix)));
                }
                threeD.renderUiPrimitive(delegate, surface.image(), originX, originY, surface.width(), surface.height(), primitive.getPrimitiveType(), primitive.getPrimitiveParam(), primitive.size(), primitive.getVertexArray(), primitive.getColorArray(), primitive.getTextureCoordArray(), texture, objectMatrix, invokeHiddenInt(object, "blendModeValue"), invokeHiddenFloat(object, "transparencyValue"));
            }
        } catch (RuntimeException e) {
            throw traceFailure("renderObject3D", e);
        }
    }

    @Override
    public void setViewTrans(com.nttdocomo.opt.ui.j3d.AffineTrans transform) {
        try {
            float[] matrix = transform == null ? Software3DContext.identity() : invokeHidden(transform, "toFloatMatrix", float[].class);
            if (TRACE_3D_CALLS) {
                OpenDoJaLog.debug(Graphics.class, () -> "3D call setViewTrans matrix=" + Arrays.toString(matrix));
            }
            threeD.setOptViewTransform(matrix);
        } catch (RuntimeException e) {
            throw traceFailure("setViewTrans", e);
        }
    }

    @Override
    public void setViewTransArray(com.nttdocomo.opt.ui.j3d.AffineTrans[] transforms) {
        this.optViewTransforms = transforms == null ? new com.nttdocomo.opt.ui.j3d.AffineTrans[0] : transforms.clone();
    }

    @Override
    public void setViewTrans(int index) {
        setViewTrans(optViewTransforms[index]);
    }

    @Override
    public void setScreenCenter(int x, int y) {
        try {
            if (TRACE_3D_CALLS) {
                OpenDoJaLog.debug(Graphics.class, () -> "3D call setScreenCenter x=" + x + " y=" + y);
            }
            threeD.setOptScreenCenter(originX + x, originY + y);
        } catch (RuntimeException e) {
            throw traceFailure("setScreenCenter", e);
        }
    }

    @Override
    public void setScreenScale(int x, int y) {
        try {
            if (TRACE_3D_CALLS) {
                OpenDoJaLog.debug(Graphics.class, () -> "3D call setScreenScale x=" + x + " y=" + y);
            }
            threeD.setOptScreenScale(x, y);
        } catch (RuntimeException e) {
            throw traceFailure("setScreenScale", e);
        }
    }

    @Override
    public void setScreenView(int x, int y) {
        try {
            if (TRACE_3D_CALLS) {
                OpenDoJaLog.debug(Graphics.class, () -> "3D call setScreenView width=" + x + " height=" + y);
            }
            // DoJa opt `setScreenView()` configures the parallel-projection extent, not a screen-space position.
            threeD.setOptScreenView(x, y);
        } catch (RuntimeException e) {
            throw traceFailure("setScreenView", e);
        }
    }

    @Override
    public void setPerspective(int near, int far, int width) {
        try {
            if (TRACE_3D_CALLS) {
                OpenDoJaLog.debug(Graphics.class, () -> "3D call setPerspective near=" + near + " far=" + far + " width=" + width);
            }
            threeD.setOptPerspective(near, far, width);
        } catch (RuntimeException e) {
            throw traceFailure("setPerspective(int,int,int)", e);
        }
    }

    @Override
    public void setPerspective(int near, int far, int width, int height) {
        try {
            if (TRACE_3D_CALLS) {
                OpenDoJaLog.debug(Graphics.class, () -> "3D call setPerspectiveWH near=" + near + " far=" + far + " width=" + width + " height=" + height);
            }
            threeD.setOptPerspective(near, far, width, height);
        } catch (RuntimeException e) {
            throw traceFailure("setPerspective(int,int,int,int)", e);
        }
    }

    @Override
    public void drawFigure(com.nttdocomo.opt.ui.j3d.Figure figure) {
        renderFigure(figure);
        flush();
    }

    @Override
    public void renderFigure(com.nttdocomo.opt.ui.j3d.Figure figure) {
        if (figure == null) {
            return;
        }
        try {
            MascotFigure handle = invokeHidden(figure, "handle", MascotFigure.class);
            prepare3DDepthFrame();
            if (TRACE_3D_CALLS) {
                OpenDoJaLog.debug(Graphics.class, () -> "3D call renderFigure " + describeOptFigure(handle));
            }
            threeD.renderOptFigure(delegate, surface.image(), originX, originY, surface.width(), surface.height(), handle);
        } catch (RuntimeException e) {
            throw traceFailure("renderFigure", e);
        }
    }

    @Override
    public void flush() {
        try {
            if (TRACE_3D_CALLS) {
                OpenDoJaLog.debug(Graphics.class, "3D call flush");
            }
            flushSurfaceFrame();
        } catch (RuntimeException e) {
            throw traceFailure("flush", e);
        }
    }

    @Override
    public void enableSphereMap(boolean enabled) {
        DoJaApiUnimplemented.noOp(
                "com.nttdocomo.ui.Graphics.enableSphereMap(boolean)",
                "Sphere mapping is not yet implemented for the opt 3D renderer"
        );
    }

    @Override
    public void setSphereTexture(com.nttdocomo.opt.ui.j3d.Texture texture) {
        DoJaApiUnimplemented.noOp(
                "com.nttdocomo.ui.Graphics.setSphereTexture(com.nttdocomo.opt.ui.j3d.Texture)",
                "Sphere-map textures are not yet implemented for the opt 3D renderer"
        );
    }

    @Override
    public void enableLight(boolean enabled) {
        if (TRACE_3D_CALLS) {
            OpenDoJaLog.debug(Graphics.class, () -> "3D call enableLight enabled=" + enabled);
        }
        threeD.enableOptLight(enabled);
    }

    @Override
    public void setAmbientLight(int color) {
        if (TRACE_3D_CALLS) {
            OpenDoJaLog.debug(Graphics.class, () -> "3D call setAmbientLight value=" + color);
        }
    }

    @Override
    public void setDirectionLight(com.nttdocomo.opt.ui.j3d.Vector3D direction, int color) {
        if (TRACE_3D_CALLS) {
            OpenDoJaLog.debug(Graphics.class, () -> "3D call setDirectionLight dir=("
                    + (direction == null ? 0 : direction.x) + ","
                    + (direction == null ? 0 : direction.y) + ","
                    + (direction == null ? 0 : direction.z) + ") value=" + color);
        }
    }

    @Override
    public void enableSemiTransparent(boolean enabled) {
        threeD.enableOptSemiTransparent(enabled);
    }

    @Override
    public void setClipRect3D(int x, int y, int width, int height) {
        try {
            if (TRACE_3D_CALLS) {
                OpenDoJaLog.debug(Graphics.class, () -> "3D call setClipRect3D x=" + x + " y=" + y + " width=" + width + " height=" + height);
            }
            // `setClipRect3D()` is defined in absolute canvas coordinates and ignores Graphics.setOrigin().
            threeD.setOptClip(x, y, width, height);
        } catch (RuntimeException e) {
            throw traceFailure("setClipRect3D", e);
        }
    }

    private RuntimeException traceFailure(String operation, RuntimeException failure) {
        if (TRACE_FAILURES) {
            OpenDoJaLog.error(Graphics.class, "openDoJa graphics failure in " + operation, failure);
        }
        return failure;
    }

    @Override
    public void enableToonShader(boolean enabled) {
        DoJaApiUnimplemented.noOp(
                "com.nttdocomo.ui.Graphics.enableToonShader(boolean)",
                "Toon shading is not yet implemented for the opt 3D renderer"
        );
    }

    @Override
    public void setToonParam(int highlight, int mid, int shadow) {
        DoJaApiUnimplemented.noOp(
                "com.nttdocomo.ui.Graphics.setToonParam(int, int, int)",
                "Toon shader parameters are not yet implemented for the opt 3D renderer"
        );
    }

    @Override
    public void setPrimitiveTextureArray(com.nttdocomo.opt.ui.j3d.Texture texture) {
        if (TRACE_3D_CALLS) {
            SoftwareTexture handle = texture == null ? null : invokeHidden(texture, "handle", SoftwareTexture.class);
            OpenDoJaLog.debug(Graphics.class, () -> "3D call setPrimitiveTextureArray single texture=" + describeTexture(handle));
        }
        threeD.setPrimitiveTextures(texture == null ? null : new SoftwareTexture[]{invokeHidden(texture, "handle", SoftwareTexture.class)});
    }

    @Override
    public void setPrimitiveTextureArray(com.nttdocomo.opt.ui.j3d.Texture[] textures) {
        if (textures == null) {
            if (TRACE_3D_CALLS) {
                OpenDoJaLog.debug(Graphics.class, "3D call setPrimitiveTextureArray array=null");
            }
            threeD.setPrimitiveTextures(null);
            return;
        }
        SoftwareTexture[] converted = new SoftwareTexture[textures.length];
        for (int i = 0; i < textures.length; i++) {
            converted[i] = invokeHidden(textures[i], "handle", SoftwareTexture.class);
        }
        if (TRACE_3D_CALLS) {
            OpenDoJaLog.debug(Graphics.class, () -> "3D call setPrimitiveTextureArray array=" + describeTextures(converted));
        }
        threeD.setPrimitiveTextures(converted);
    }

    @Override
    public void setPrimitiveTexture(int index) {
        if (TRACE_3D_CALLS) {
            OpenDoJaLog.debug(Graphics.class, () -> "3D call setPrimitiveTexture index=" + index);
        }
        threeD.setPrimitiveTexture(index);
    }

    @Override
    public void renderPrimitives(com.nttdocomo.opt.ui.j3d.PrimitiveArray primitives, int attr) {
        prepare3DDepthFrame();
        if (TRACE_3D_CALLS && primitives != null) {
            OpenDoJaLog.debug(Graphics.class, () -> "3D call renderPrimitives type=" + primitives.getType()
                    + " param=" + primitives.getParam()
                    + " size=" + primitives.size()
                    + " attr=" + attr
                    + " textures=" + describeTextures(threeD.primitiveTexturesSnapshot())
                    + " selectedTexture=" + threeD.primitiveTextureIndex());
        }
        threeD.renderOptPrimitives(delegate, surface.image(), originX, originY, surface.width(), surface.height(), primitives, attr);
    }

    @Override
    public void renderPrimitives(com.nttdocomo.opt.ui.j3d.PrimitiveArray primitives, int start, int count, int attr) {
        prepare3DDepthFrame();
        if (TRACE_3D_CALLS && primitives != null) {
            OpenDoJaLog.debug(Graphics.class, () -> "3D call renderPrimitivesRange type=" + primitives.getType()
                    + " param=" + primitives.getParam()
                    + " size=" + primitives.size()
                    + " start=" + start
                    + " count=" + count
                    + " attr=" + attr
                    + " textures=" + describeTextures(threeD.primitiveTexturesSnapshot())
                    + " selectedTexture=" + threeD.primitiveTextureIndex());
        }
        // The three-int DoJa overload renders a slice of the PrimitiveArray.
        threeD.renderOptPrimitivesRange(delegate, surface.image(), originX, originY, surface.width(), surface.height(), primitives, start, count, attr);
    }

    @Override
    public void executeCommandList(int[] commands) {
        if (commands == null) {
            throw new NullPointerException("commands");
        }
        for (int i = 0; i < commands.length; i++) {
            int command = commands[i];
            if (command == com.nttdocomo.opt.ui.j3d.Graphics3D.COMMAND_END) {
                return;
            }
            if (command == com.nttdocomo.opt.ui.j3d.Graphics3D.COMMAND_FLUSH) {
                flush();
            }
        }
    }

    private static <T> T invokeHidden(Object target, String methodName, Class<T> returnType) {
        try {
            Method method = findHiddenMethod(target.getClass(), methodName);
            method.setAccessible(true);
            return returnType.cast(method.invoke(target));
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Failed to invoke " + target.getClass().getName() + "#" + methodName, e);
        }
    }

    private static int invokeHiddenInt(Object target, String methodName) {
        return invokeHidden(target, methodName, Integer.class);
    }

    private static float invokeHiddenFloat(Object target, String methodName) {
        return invokeHidden(target, methodName, Float.class);
    }

    private static String describeTextures(SoftwareTexture[] textures) {
        if (textures == null) {
            return "null";
        }
        String[] description = new String[textures.length];
        for (int i = 0; i < textures.length; i++) {
            description[i] = describeTexture(textures[i]);
        }
        return Arrays.toString(description);
    }

    private static String describeTexture(SoftwareTexture texture) {
        if (texture == null) {
            return "null";
        }
        return texture.width() + "x" + texture.height() + (texture.sphereMap() ? ":sphere" : ":model");
    }

    private static String describeOptFigure(MascotFigure figure) {
        if (figure == null) {
            return "figure=null";
        }
        int textureCount = figure.numTextures();
        int polygonCount = figure.model() == null ? -1 : figure.model().polygons().length;
        float[] vertices = figure.vertices();
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (int i = 0; i + 2 < vertices.length; i += 3) {
            minX = Math.min(minX, vertices[i]);
            minY = Math.min(minY, vertices[i + 1]);
            minZ = Math.min(minZ, vertices[i + 2]);
            maxX = Math.max(maxX, vertices[i]);
            maxY = Math.max(maxY, vertices[i + 1]);
            maxZ = Math.max(maxZ, vertices[i + 2]);
        }
        return String.format(
                "polygons=%d textures=%d pattern=%d bounds=[%.1f,%.1f,%.1f]->[%.1f,%.1f,%.1f]",
                polygonCount,
                textureCount,
                figure.patternMask(),
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                maxZ
        );
    }

    private static Method findHiddenMethod(Class<?> type, String methodName) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(methodName);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new IllegalStateException("Failed to locate hidden method " + type.getName() + "#" + methodName);
    }
}
