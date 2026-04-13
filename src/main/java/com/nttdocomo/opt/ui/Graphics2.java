package com.nttdocomo.opt.ui;

import com.nttdocomo.opt.ui.j3d.AffineTrans;
import com.nttdocomo.ui.Graphics;
import com.nttdocomo.ui.Image;
import com.nttdocomo.ui.MediaImage;
import opendoja.host.DesktopSurface;
import opendoja.host.DoJaRuntime;

import java.awt.geom.AffineTransform;
import java.util.concurrent.locks.LockSupport;

/**
 * Extends {@link Graphics} with DoJa 5.1 optional drawing features.
 */
public class Graphics2 extends Graphics {
    /**
     * Coordinate mode that uses normal pixel coordinates.
     */
    public static final int CM_NORMAL = 0;
    /**
     * Coordinate mode that uses zoom-style coordinates.
     */
    public static final int CM_ZOOM = 256;
    /**
     * Render operator that replaces the destination.
     */
    public static final int OP_REPL = 0;
    /**
     * Render operator that adds source and destination.
     */
    public static final int OP_ADD = 1;
    /**
     * Render operator that subtracts the source from the destination.
     */
    public static final int OP_SUB = 2;

    private static final int DEFAULT_SYNC_UNLOCK_INTERVAL_US = 1_000_000 / 60;

    private int coordinateMode = CM_NORMAL;
    private int renderOperator = OP_REPL;
    private int srcRatio = 255;
    private int dstRatio = 255;
    private int requestedOriginX;
    private int requestedOriginY;
    private long lastSyncTargetNanos;
    private long syncCount;

    /**
     * Applications cannot create this object directly.
     */
    protected Graphics2() {
        super();
    }

    Graphics2(DesktopSurface surface) {
        super(surface);
    }

    @Override
    protected boolean usesOptRenderMode() {
        return true;
    }

    @Override
    protected int currentOptRenderOperator() {
        return renderOperator;
    }

    @Override
    protected int currentOptRenderSourceRatio() {
        return srcRatio;
    }

    @Override
    protected int currentOptRenderDestinationRatio() {
        return dstRatio;
    }

    /**
     * Sets the drawing origin used by this graphics context.
     *
     * @param x the origin x coordinate
     * @param y the origin y coordinate
     */
    @Override
    public void setOrigin(int x, int y) {
        requestedOriginX = x;
        requestedOriginY = y;
        super.setOrigin(x, y);
    }

    /**
     * Sets the coordinate specification mode used by this graphics context.
     *
     * @param mode the coordinate mode
     */
    public void setCoordinateMode(int mode) {
        if (mode != CM_NORMAL && mode != CM_ZOOM) {
            throw new IllegalArgumentException("mode");
        }
        coordinateMode = mode;
    }

    /**
     * Sets the render operator and source/destination ratios.
     *
     * @param operator the operator
     * @param srcRatio the source ratio in the range 0..255
     * @param dstRatio the destination ratio in the range 0..255
     */
    public void setRenderMode(int operator, int srcRatio, int dstRatio) {
        if (operator != OP_REPL && operator != OP_ADD && operator != OP_SUB) {
            throw new IllegalArgumentException("operator");
        }
        if (srcRatio < 0 || srcRatio > 255 || dstRatio < 0 || dstRatio > 255) {
            throw new IllegalArgumentException("ratio");
        }
        this.renderOperator = operator;
        this.srcRatio = srcRatio;
        this.dstRatio = dstRatio;
    }

    /**
     * Returns a captured image of the specified region.
     *
     * @param x the left edge
     * @param y the top edge
     * @param width the width
     * @param height the height
     * @return the captured image
     */
    public Image getImage(int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("width");
        }
        return getImageRegion(actualCoordinateX(x) - getOriginX(), actualCoordinateY(y) - getOriginY(), width, height);
    }

    /**
     * Draws a number right-aligned in the specified digit field.
     *
     * @param x the x coordinate
     * @param y the baseline y coordinate
     * @param value the number to draw
     * @param digit the number of digits
     */
    public void drawNumber(int x, int y, int value, int digit) {
        if (digit <= 0) {
            throw new IllegalArgumentException("digit");
        }
        String text = Integer.toString(value);
        if (text.length() < digit) {
            text = " ".repeat(digit - text.length()) + text;
        } else if (text.length() > digit) {
            text = text.substring(text.length() - digit);
        }
        super.drawString(text, actualCoordinateX(x) - getOriginX(), actualCoordinateY(y) - getOriginY());
    }

    /**
     * Draws the specified image frame from a media image.
     *
     * @param image the media image
     * @param k the frame index
     * @param x the x coordinate
     * @param y the y coordinate
     */
    public void drawNthImage(MediaImage image, int k, int x, int y) {
        Image plainImage = resolveNthMediaImageFrame(image, k);
        super.drawImage(plainImage, actualCoordinateX(x) - getOriginX(), actualCoordinateY(y) - getOriginY());
    }

    /**
     * Draws a sprite set.
     *
     * @param sprites the sprite set
     */
    public void drawSpriteSet(SpriteSet sprites) {
        if (sprites == null) {
            throw new NullPointerException("sprites");
        }
        if (sprites.getCount() == 0) {
            throw new NullPointerException("sprites");
        }
        drawSpriteSet(sprites, 0, sprites.getCount());
    }

    /**
     * Draws part of a sprite set.
     *
     * @param sprites the sprite set
     * @param offset the first sprite index to draw
     * @param count the number of sprites to draw
     */
    public void drawSpriteSet(SpriteSet sprites, int offset, int count) {
        if (sprites == null) {
            throw new NullPointerException("sprites");
        }
        if (sprites.getCount() == 0) {
            throw new NullPointerException("sprites");
        }
        if (offset < 0 || count < 0 || offset + count > sprites.getCount()) {
            throw new ArrayIndexOutOfBoundsException();
        }
        int savedOperator = renderOperator;
        int savedSrcRatio = srcRatio;
        int savedDstRatio = dstRatio;
        for (int i = offset; i < offset + count; i++) {
            Sprite sprite = sprites.getSprite(i);
            if (sprite == null || sprite.image() == null) {
                throw new NullPointerException("sprite");
            }
            ensureImageNotDisposed(sprite.image());
            if (!sprite.isVisible()) {
                continue;
            }
            super.setFlipMode(sprite.flipMode());
            setRenderMode(sprite.renderOperator(), sprite.srcRatio(), sprite.dstRatio());
            super.drawImage(sprite.image(),
                    actualCoordinateX(sprite.getX()) - getOriginX(),
                    actualCoordinateY(sprite.getY()) - getOriginY(),
                    sprite.sourceX(),
                    sprite.sourceY(),
                    sprite.getWidth(),
                    sprite.getHeight());
        }
        super.setFlipMode(Graphics.FLIP_NONE);
        setRenderMode(savedOperator, savedSrcRatio, savedDstRatio);
    }

    /**
     * Returns an intermediate color between two colors.
     *
     * @param color1 the first color
     * @param color2 the second color
     * @param ratio how close the result should be to {@code color2}
     * @return the intermediate color
     */
    public static int getIntermediateColor(int color1, int color2, int ratio) {
        if (ratio < 0 || ratio > 255) {
            throw new IllegalArgumentException("ratio");
        }
        int a = mix((color1 >>> 24) & 0xFF, (color2 >>> 24) & 0xFF, ratio);
        int r = mix((color1 >>> 16) & 0xFF, (color2 >>> 16) & 0xFF, ratio);
        int g = mix((color1 >>> 8) & 0xFF, (color2 >>> 8) & 0xFF, ratio);
        int b = mix(color1 & 0xFF, color2 & 0xFF, ratio);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Draws an image using a 2D affine transformation.
     *
     * @param image the image to draw
     * @param at the affine transform
     * @param x the source x coordinate
     * @param y the source y coordinate
     * @param width the source width
     * @param height the source height
     */
    public void drawImage(Image image, AffineTrans at, int x, int y, int width, int height) {
        if (image == null || at == null) {
            throw new NullPointerException();
        }
        ensureImageNotDisposed(image);
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("width");
        }
        if (width == 0 || height == 0) {
            return;
        }
        drawAffineImageValidated(image, createAffineTransform(at), x, y, width, height);
    }

    /**
     * Draws an image using a 2D affine transformation.
     *
     * @param image the image to draw
     * @param at the affine transform
     */
    public void drawImage(Image image, AffineTrans at) {
        if (image == null || at == null) {
            throw new NullPointerException();
        }
        ensureImageNotDisposed(image);
        int width = image.getWidth();
        int height = image.getHeight();
        drawAffineImageValidated(image, createAffineTransform(at), 0, 0, width, height);
    }

    /**
     * Returns the display vertical sync interval in microseconds.
     *
     * @return the sync interval in microseconds
     */
    public int getSyncUnlockInterval() {
        return DEFAULT_SYNC_UNLOCK_INTERVAL_US;
    }

    /**
     * Waits for a display sync interval and finishes double buffering.
     *
     * @param interval the interval count to wait
     * @return the actual interval count waited
     */
    public int syncUnlock(int interval) {
        if (interval <= 0) {
            throw new IllegalArgumentException("interval");
        }
        DoJaRuntime runtime = DoJaRuntime.current();
        if (runtime == null || !runtime.surfaceLock().isHeldByCurrentThread()) {
            return 0;
        }
        long stepNanos = getSyncUnlockInterval() * 1000L;
        long targetCount = syncCount == 0 ? 1 : syncCount + interval;
        long targetTime = lastSyncTargetNanos == 0L ? System.nanoTime() + stepNanos : lastSyncTargetNanos + (interval * stepNanos);
        long now = System.nanoTime();
        if (targetTime > now) {
            LockSupport.parkNanos(targetTime - now);
        } else {
            long lag = now - targetTime;
            targetCount = java.lang.Math.min(Integer.MAX_VALUE, targetCount + (lag / stepNanos) + 1);
            targetTime += ((lag / stepNanos) + 1) * stepNanos;
        }
        syncCount = java.lang.Math.min(Integer.MAX_VALUE, targetCount);
        lastSyncTargetNanos = targetTime;
        super.unlock(true);
        return (int) java.lang.Math.min(Integer.MAX_VALUE, syncCount);
    }

    private int actualCoordinateX(int x) {
        if (coordinateMode == CM_NORMAL) {
            return x + getOriginX();
        }
        return (x + requestedOriginX) / 256;
    }

    private int actualCoordinateY(int y) {
        if (coordinateMode == CM_NORMAL) {
            return y + getOriginY();
        }
        return (y + requestedOriginY) / 256;
    }

    private static int mix(int left, int right, int ratio) {
        return ((255 - ratio) * left + ratio * right) / 255;
    }

    private AffineTransform createAffineTransform(AffineTrans at) {
        double scale = coordinateMode == CM_ZOOM ? (1.0 / 256.0) : 1.0;
        double translateX = (at.m02 / 4096.0) * scale;
        double translateY = (at.m12 / 4096.0) * scale;
        if (coordinateMode == CM_ZOOM) {
            // CM_ZOOM applies after the 2D affine result is produced from m00/m01/m02 and
            // m10/m11/m12, so fold the 1/256 scale and requested origin into the local transform.
            translateX -= (255.0 * requestedOriginX) / 256.0;
            translateY -= (255.0 * requestedOriginY) / 256.0;
        }
        return new AffineTransform(
                (at.m00 / 4096.0) * scale,
                (at.m10 / 4096.0) * scale,
                (at.m01 / 4096.0) * scale,
                (at.m11 / 4096.0) * scale,
                translateX,
                translateY);
    }
}
