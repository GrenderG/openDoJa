package com.nttdocomo.ui;

import com.nttdocomo.lang.XString;
import com.nttdocomo.lang._XStringSupport;
import com.nttdocomo.ui.ogl.DirectBuffer;
import opendoja.g3d.DojaGraphics3DRenderer;
import opendoja.g3d.OptJ3DRenderer;
import opendoja.host.*;
import opendoja.host.ogl.AcrodeaOglRenderer;
import opendoja.host.ogl.OglRenderer;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.function.Consumer;

/**
 * Represents the graphics context used for canvases and images.
 */
public class Graphics implements com.nttdocomo.ui.graphics3d.Graphics3D, com.nttdocomo.opt.ui.j3d.Graphics3D, com.nttdocomo.ui.ogl.GraphicsOGL {
    private static final boolean TRACE_FAILURES = opendoja.host.OpenDoJaLaunchArgs.getBoolean(opendoja.host.OpenDoJaLaunchArgs.TRACE_FAILURES);
    private static final boolean TRACE_3D_CALLS = opendoja.host.OpenDoJaLaunchArgs.getBoolean(opendoja.host.OpenDoJaLaunchArgs.DEBUG3D_CALLS);
    private static final boolean TRACE_OPEN_GLES_SYNC = opendoja.host.OpenDoJaLaunchArgs.getBoolean(opendoja.host.OpenDoJaLaunchArgs.TRACE_OPEN_GLES_SYNC);
    private static final double DOJAAFFINE_FIXED_POINT_SCALE = 4096.0;
    private static final int OPT_RENDER_OP_REPL = 0;
    private static final int OPT_RENDER_OP_ADD = 1;
    private static final int OPT_RENDER_OP_SUB = 2;
    /**
     * Constant for black.
     */
    public static final int BLACK = 0;
    /**
     * Constant for blue.
     */
    public static final int BLUE = 1;
    /**
     * Constant for lime.
     */
    public static final int LIME = 2;
    /**
     * Constant for aqua.
     */
    public static final int AQUA = 3;
    /**
     * Constant for red.
     */
    public static final int RED = 4;
    /**
     * Constant for fuchsia.
     */
    public static final int FUCHSIA = 5;
    /**
     * Constant for yellow.
     */
    public static final int YELLOW = 6;
    /**
     * Constant for white.
     */
    public static final int WHITE = 7;
    /**
     * Constant for gray.
     */
    public static final int GRAY = 8;
    /**
     * Constant for navy.
     */
    public static final int NAVY = 9;
    /**
     * Constant for green.
     */
    public static final int GREEN = 10;
    /**
     * Constant for teal.
     */
    public static final int TEAL = 11;
    /**
     * Constant for maroon.
     */
    public static final int MAROON = 12;
    /**
     * Constant for purple.
     */
    public static final int PURPLE = 13;
    /**
     * Constant for olive.
     */
    public static final int OLIVE = 14;
    /**
     * Constant for silver.
     */
    public static final int SILVER = 15;
    /**
     * Image flip-mode constant for none.
     */
    public static final int FLIP_NONE = 0;
    /**
     * Image flip-mode constant for horizontal.
     */
    public static final int FLIP_HORIZONTAL = 1;
    /**
     * Image flip-mode constant for vertical.
     */
    public static final int FLIP_VERTICAL = 2;
    /**
     * Image flip-mode constant for rotate.
     */
    public static final int FLIP_ROTATE = 3;
    /**
     * Image flip-mode constant for rotate left.
     */
    public static final int FLIP_ROTATE_LEFT = 4;
    /**
     * Image flip-mode constant for rotate right.
     */
    public static final int FLIP_ROTATE_RIGHT = 5;
    /**
     * Image flip-mode constant for rotate right horizontal.
     */
    public static final int FLIP_ROTATE_RIGHT_HORIZONTAL = 6;
    /**
     * Image flip-mode constant for rotate right vertical.
     */
    public static final int FLIP_ROTATE_RIGHT_VERTICAL = 7;

    private final DesktopSurface surface;
    private Graphics2D delegate;
    private final DojaGraphics3DRenderer doja3D = new DojaGraphics3DRenderer(TRACE_3D_CALLS);
    private final OptJ3DRenderer opt3D = new OptJ3DRenderer(doja3D.context(), TRACE_3D_CALLS);
    private final OglRenderer oglRenderer;
    private boolean pendingOptRenderedContent;
    private int originX;
    private int originY;
    private int color = getColorOfName(BLACK);
    private Font font = Font.getDefaultFont();
    private int flipMode = FLIP_NONE;
    private boolean pictoColorEnabled;
    private static volatile java.lang.reflect.Constructor<?> platformGraphicsConstructor;

    /**
     * Applications cannot create this object directly.
     */
    protected Graphics() {
        this(new DesktopSurface(1, 1));
    }

    /**
     * Applications cannot create this object directly.
     */
    protected Graphics(DesktopSurface surface) {
        this.surface = surface;
        this.delegate = surface.image().createGraphics();
        configureDelegate(this.delegate);
        this.delegate.setColor(new Color(color, true));
        this.delegate.setFont(font.awtFont());
        this.oglRenderer = new AcrodeaOglRenderer(new GraphicsOglHost());
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

    /**
     * Clears clip.
     */
    public void clearClip() {
        delegate.setClip(0, 0, surface.width(), surface.height());
    }

    /**
     * Clears rect.
     */
    public void clearRect(int x, int y, int width, int height) {
        prepareSoftwareSurfaceMutation();
        Color old = delegate.getColor();
        delegate.setColor(new Color(surface.backgroundColor(), true));
        delegate.fillRect(originX + x, originY + y, width, height);
        delegate.setColor(old);
        flushSurfacePresentation();
    }

    /**
     * Clips rect.
     */
    public void clipRect(int x, int y, int width, int height) {
        delegate.clipRect(originX + x, originY + y, width, height);
    }

    /**
     * Copies copy.
     */
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
        copy.pictoColorEnabled = pictoColorEnabled;
        copy.doja3D.copyStateFrom(doja3D);
        copy.opt3D.copyStateFrom(opt3D);
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

    private final class GraphicsOglHost implements OglRenderer.Host {
        @Override
        public DesktopSurface surface() {
            return surface;
        }

        @Override
        public Graphics2D delegate() {
            return delegate;
        }

        @Override
        public void markOpenGlesActivity() {
            surface.markOpenGlesActivity();
        }

        @Override
        public void flushSurfacePresentation() {
            Graphics.this.flushSurfacePresentation();
        }

        @Override
        public void onSoftwareSurfaceMutation() {
            oglRenderer.onSoftwareSurfaceMutation();
        }
    }

    @Override
    public void glClearColor(float red, float green, float blue, float alpha) {
        oglRenderer.glClearColor(red, green, blue, alpha);
    }

    @Override
    public void glClear(int mask) {
        oglRenderer.glClear(mask);
    }

    @Override
    public void beginDrawing() {
        oglRenderer.beginDrawing();
    }

    @Override
    public void endDrawing() {
        oglRenderer.endDrawing();
    }

    @Override
    public void glEnable(int cap) {
        oglRenderer.glEnable(cap);
    }

    @Override
    public void glDisable(int cap) {
        oglRenderer.glDisable(cap);
    }

    @Override
    public void glEnableClientState(int array) {
        oglRenderer.glEnableClientState(array);
    }

    @Override
    public void glDisableClientState(int array) {
        oglRenderer.glDisableClientState(array);
    }

    @Override
    public void glMatrixMode(int mode) {
        oglRenderer.glMatrixMode(mode);
    }

    @Override
    public void glLoadIdentity() {
        oglRenderer.glLoadIdentity();
    }

    @Override
    public void glLoadMatrixf(float[] m) {
        oglRenderer.glLoadMatrixf(m);
    }

    @Override
    public void glMultMatrixf(float[] m) {
        oglRenderer.glMultMatrixf(m);
    }

    @Override
    public void glPushMatrix() {
        oglRenderer.glPushMatrix();
    }

    @Override
    public void glPopMatrix() {
        oglRenderer.glPopMatrix();
    }

    @Override
    public void glOrthof(float left, float right, float bottom, float top, float zNear, float zFar) {
        oglRenderer.glOrthof(left, right, bottom, top, zNear, zFar);
    }

    @Override
    public void glFrustumf(float left, float right, float bottom, float top, float zNear, float zFar) {
        oglRenderer.glFrustumf(left, right, bottom, top, zNear, zFar);
    }

    @Override
    public void glDepthRangef(float zNear, float zFar) {
        oglRenderer.glDepthRangef(zNear, zFar);
    }

    @Override
    public void glRotatef(float angle, float x, float y, float z) {
        oglRenderer.glRotatef(angle, x, y, z);
    }

    @Override
    public void glScalef(float x, float y, float z) {
        oglRenderer.glScalef(x, y, z);
    }

    @Override
    public void glTranslatef(float x, float y, float z) {
        oglRenderer.glTranslatef(x, y, z);
    }

    @Override
    public void glAlphaFunc(int func, float ref) {
        oglRenderer.glAlphaFunc(func, ref);
    }

    @Override
    public void glDepthMask(boolean flag) {
        oglRenderer.glDepthMask(flag);
    }

    @Override
    public void glDepthFunc(int func) {
        oglRenderer.glDepthFunc(func);
    }

    @Override
    public void glBlendFunc(int sfactor, int dfactor) {
        oglRenderer.glBlendFunc(sfactor, dfactor);
    }

    @Override
    public void glColor4f(float red, float green, float blue, float alpha) {
        oglRenderer.glColor4f(red, green, blue, alpha);
    }

    @Override
    public void glColor4ub(short red, short green, short blue, short alpha) {
        oglRenderer.glColor4ub(red, green, blue, alpha);
    }

    @Override
    public void glLightModelf(int pname, float param) {
        oglRenderer.glLightModelf(pname, param);
    }

    @Override
    public void glLightModelfv(int pname, float[] params) {
        oglRenderer.glLightModelfv(pname, params);
    }

    @Override
    public void glLightf(int light, int pname, float param) {
        oglRenderer.glLightf(light, pname, param);
    }

    @Override
    public void glLightfv(int light, int pname, float[] params) {
        oglRenderer.glLightfv(light, pname, params);
    }

    @Override
    public void glMaterialf(int face, int pname, float param) {
        oglRenderer.glMaterialf(face, pname, param);
    }

    @Override
    public void glMaterialfv(int face, int pname, float[] params) {
        oglRenderer.glMaterialfv(face, pname, params);
    }

    @Override
    public void glNormal3f(float nx, float ny, float nz) {
        oglRenderer.glNormal3f(nx, ny, nz);
    }

    @Override
    public void glTexEnvi(int target, int pname, int param) {
        oglRenderer.glTexEnvi(target, pname, param);
    }

    @Override
    public void glTexEnvfv(int target, int pname, float[] params) {
        oglRenderer.glTexEnvfv(target, pname, params);
    }

    @Override
    public void glShadeModel(int mode) {
        oglRenderer.glShadeModel(mode);
    }

    @Override
    public void glClientActiveTexture(int texture) {
        oglRenderer.glClientActiveTexture(texture);
    }

    @Override
    public void glPixelStorei(int pname, int param) {
        oglRenderer.glPixelStorei(pname, param);
    }

    @Override
    public void glHint(int target, int mode) {
        oglRenderer.glHint(target, mode);
    }

    @Override
    public void glFrontFace(int mode) {
        oglRenderer.glFrontFace(mode);
    }

    @Override
    public void glCullFace(int mode) {
        oglRenderer.glCullFace(mode);
    }

    @Override
    public void glViewport(int x, int y, int width, int height) {
        oglRenderer.glViewport(x, y, width, height);
    }

    @Override
    public void glTexParameterf(int target, int pname, float param) {
        oglRenderer.glTexParameterf(target, pname, param);
    }

    @Override
    public void glTexParameteri(int target, int pname, int param) {
        oglRenderer.glTexParameteri(target, pname, param);
    }

    @Override
    public void glBindTexture(int target, int texture) {
        oglRenderer.glBindTexture(target, texture);
    }

    @Override
    public void glGenTextures(int n, int[] textures) {
        oglRenderer.glGenTextures(n, textures);
    }

    @Override
    public void glGenTextures(int[] textures) {
        oglRenderer.glGenTextures(textures);
    }

    @Override
    public void glDeleteTextures(int n, int[] textures) {
        oglRenderer.glDeleteTextures(n, textures);
    }

    @Override
    public void glDeleteTextures(int[] textures) {
        oglRenderer.glDeleteTextures(textures);
    }

    @Override
    public void glCompressedTexImage2D(int target, int level, int internalformat, int width, int height, int border, int imageSize, DirectBuffer pixels) {
        oglRenderer.glCompressedTexImage2D(target, level, internalformat, width, height, border, imageSize, pixels);
    }

    @Override
    public void glCompressedTexImage2D(int target, int level, int internalformat, int width, int height, int border,
                                       com.nttdocomo.ui.ogl.ByteBuffer pixels) {
        oglRenderer.glCompressedTexImage2D(target, level, internalformat, width, height, border, pixels);
    }

    @Override
    public void glTexImage2D(int target, int level, int internalformat, int width, int height, int border,
                             int format, int type, DirectBuffer pixels) {
        oglRenderer.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
    }

    @Override
    public void glGenBuffers(int[] buffers) {
        oglRenderer.glGenBuffers(buffers);
    }

    @Override
    public void glDeleteBuffers(int[] buffers) {
        oglRenderer.glDeleteBuffers(buffers);
    }

    @Override
    public void glBindBuffer(int target, int buffer) {
        oglRenderer.glBindBuffer(target, buffer);
    }

    @Override
    public void glBufferData(int target, DirectBuffer data, int usage) {
        oglRenderer.glBufferData(target, data, usage);
    }

    @Override
    public void glBufferSubData(int target, int offset, DirectBuffer data) {
        oglRenderer.glBufferSubData(target, offset, data);
    }

    @Override
    public void glVertexPointer(int size, int type, int stride, DirectBuffer pointer) {
        oglRenderer.glVertexPointer(size, type, stride, pointer);
    }

    @Override
    public void glVertexPointer(int size, int type, int stride, int pointer) {
        oglRenderer.glVertexPointer(size, type, stride, pointer);
    }

    @Override
    public void glTexCoordPointer(int size, int type, int stride, DirectBuffer pointer) {
        oglRenderer.glTexCoordPointer(size, type, stride, pointer);
    }

    @Override
    public void glTexCoordPointer(int size, int type, int stride, int pointer) {
        oglRenderer.glTexCoordPointer(size, type, stride, pointer);
    }

    @Override
    public void glNormalPointer(int type, int stride, DirectBuffer pointer) {
        oglRenderer.glNormalPointer(type, stride, pointer);
    }

    @Override
    public void glNormalPointer(int type, int stride, int pointer) {
        oglRenderer.glNormalPointer(type, stride, pointer);
    }

    @Override
    public void glColorPointer(int size, int type, int stride, DirectBuffer pointer) {
        oglRenderer.glColorPointer(size, type, stride, pointer);
    }

    @Override
    public void glColorPointer(int size, int type, int stride, int pointer) {
        oglRenderer.glColorPointer(size, type, stride, pointer);
    }

    @Override
    public void glCurrentPaletteMatrixOES(int matrixpaletteindex) {
        oglRenderer.glCurrentPaletteMatrixOES(matrixpaletteindex);
    }

    @Override
    public void glLoadPaletteFromModelViewMatrixOES() {
        oglRenderer.glLoadPaletteFromModelViewMatrixOES();
    }

    @Override
    public void glMatrixIndexPointerOES(int size, int type, int stride, DirectBuffer pointer) {
        oglRenderer.glMatrixIndexPointerOES(size, type, stride, pointer);
    }

    @Override
    public void glMatrixIndexPointerOES(int size, int type, int stride, int pointer) {
        oglRenderer.glMatrixIndexPointerOES(size, type, stride, pointer);
    }

    @Override
    public void glWeightPointerOES(int size, int type, int stride, DirectBuffer pointer) {
        oglRenderer.glWeightPointerOES(size, type, stride, pointer);
    }

    @Override
    public void glWeightPointerOES(int size, int type, int stride, int pointer) {
        oglRenderer.glWeightPointerOES(size, type, stride, pointer);
    }

    @Override
    public void glDrawArrays(int mode, int first, int count) {
        oglRenderer.glDrawArrays(mode, first, count);
    }

    @Override
    public void glDrawElements(int mode, int count, int type, DirectBuffer indices) {
        oglRenderer.glDrawElements(mode, count, type, indices);
    }

    @Override
    public void glDrawElements(int mode, int count, int type, int indices) {
        oglRenderer.glDrawElements(mode, count, type, indices);
    }

    @Override
    public void glDrawElements(int mode, int count, DirectBuffer indices) {
        oglRenderer.glDrawElements(mode, count, indices);
    }

    @Override
    public void glFlush() {
        oglRenderer.glFlush();
    }

    @Override
    public int glGetError() {
        return oglRenderer.glGetError();
    }

    @Override
    public void glGetIntegerv(int pname, int[] params) {
        oglRenderer.glGetIntegerv(pname, params);
    }

    @Override
    public void glGetFloatv(int pname, float[] params) {
        oglRenderer.glGetFloatv(pname, params);
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
            return null;
        }
        int srcX = Math.max(0, originX + x);
        int srcY = Math.max(0, originY + y);
        int srcRight = Math.min(originX + x + width, surface.width());
        int srcBottom = Math.min(originY + y + height, surface.height());
        int srcWidth = srcRight - srcX;
        int srcHeight = srcBottom - srcY;
        if (srcWidth <= 0 || srcHeight <= 0) {
            return null;
        }
        BufferedImage copy = new BufferedImage(srcWidth, srcHeight, BufferedImage.TYPE_INT_ARGB);
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
        return new DesktopImage(copy);
    }

    // DoJa titles scroll cached surfaces with copyArea requests that can hang partly outside
    // the backing image; clip the source rectangle but keep dx/dy as translation offsets.
    /**
     * Copies area.
     */
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

    /**
     * Disposes this object and releases its resources.
     */
    public void dispose() {
        oglRenderer.close();
        delegate.dispose();
    }

    void syncOffscreenSurfaceForReadback() {
        flushPending3DPasses();
        pendingOptRenderedContent = false;
        oglRenderer.flushHardwarePresentation();
    }

    /**
     * Host-only lifecycle hook, not part of the original DoJa API. Runtime-owned
     * paint callbacks may need to release the Java2D delegate without invalidating
     * the DoJa wrapper seen by application code.
     */
    public void refreshDelegateAfterHostPaint() {
        Shape clip = delegate.getClip();
        delegate.dispose();
        delegate = surface.image().createGraphics();
        configureDelegate(delegate);
        delegate.setColor(new Color(color, true));
        delegate.setFont(font.awtFont());
        if (clip == null) {
            delegate.setClip(null);
        } else {
            delegate.setClip(clip);
        }
        oglRenderer.onHostDelegateRecreated();
    }

    /**
     * Draws chars.
     */
    public void drawChars(char[] data, int x, int y, int offset, int length) {
        // DoJa uses the order (chars, x, y, offset, length), unlike Java SE/AWT.
        drawString(new String(data, offset, length), x, y);
    }

    /**
     * Draws image.
     */
    public void drawImage(Image image, int x, int y) {
        drawSubImage(image, x, y, 0, 0, image.getWidth(), image.getHeight(), image.getWidth(), image.getHeight());
    }

    /**
     * Draws sprite Set.
     */
    public void drawSpriteSet(SpriteSet spriteSet) {
        if (spriteSet == null) {
            throw new NullPointerException("spriteSet");
        }
        drawSpriteSet(spriteSet, 0, spriteSet.getCount());
    }

    /**
     * Draws sprite Set.
     */
    public void drawSpriteSet(SpriteSet spriteSet, int offset, int count) {
        if (spriteSet == null) {
            throw new NullPointerException("spriteSet");
        }
        if (offset < 0 || count < 0 || offset + count > spriteSet.getCount()) {
            throw new ArrayIndexOutOfBoundsException();
        }
        for (int i = offset; i < offset + count; i++) {
            Sprite sprite = spriteSet.getSprite(i);
            if (sprite != null && sprite.isVisible()) {
                drawImage(sprite.image(), sprite.getX(), sprite.getY(), sprite.sourceX(), sprite.sourceY(), sprite.getWidth(), sprite.getHeight());
            }
        }
    }

    /**
     * Draws image Map.
     */
    public void drawImageMap(ImageMap imageMap, int x, int y) {
        if (imageMap != null) {
            imageMap.draw(this, x, y);
        }
    }

    /**
     * Draws image.
     */
    public void drawImage(Image image, int x, int y, int sx, int sy, int width, int height) {
        drawSubImage(image, x, y, sx, sy, width, height, width, height);
    }

    /**
     * Draws line.
     */
    public void drawLine(int x1, int y1, int x2, int y2) {
        Rectangle bounds = rectangleFromPoints(originX + x1, originY + y1, originX + x2, originY + y2);
        if (drawWithOptRenderMode(bounds, graphics -> graphics.drawLine(originX + x1, originY + y1, originX + x2, originY + y2))) {
            return;
        }
        prepareSoftwareSurfaceMutation();
        delegate.drawLine(originX + x1, originY + y1, originX + x2, originY + y2);
        flushSurfacePresentation();
    }

    /**
     * Draws polyline.
     */
    public void drawPolyline(int[] xs, int[] ys, int n) {
        drawPolyline(xs, ys, n, 0);
    }

    /**
     * Draws polyline.
     */
    public void drawPolyline(int[] xs, int[] ys, int n, int mode) {
        if (xs == null || ys == null || n <= 0) {
            return;
        }
        int[] drawX = new int[n];
        int[] drawY = new int[n];
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            drawX[i] = originX + xs[i];
            drawY[i] = originY + ys[i];
            minX = Math.min(minX, drawX[i]);
            minY = Math.min(minY, drawY[i]);
            maxX = Math.max(maxX, drawX[i]);
            maxY = Math.max(maxY, drawY[i]);
        }
        Rectangle bounds = rectangleFromPoints(minX, minY, maxX, maxY);
        if (drawWithOptRenderMode(bounds, graphics -> graphics.drawPolyline(drawX, drawY, n))) {
            return;
        }
        prepareSoftwareSurfaceMutation();
        delegate.drawPolyline(drawX, drawY, n);
        flushSurfacePresentation(bounds);
    }

    /**
     * Draws rect.
     */
    public void drawRect(int x, int y, int width, int height) {
        Rectangle bounds = normalizedRectangle(originX + x, originY + y, width + 1, height + 1);
        if (drawWithOptRenderMode(bounds, graphics -> graphics.drawRect(originX + x, originY + y, width, height))) {
            return;
        }
        prepareSoftwareSurfaceMutation();
        delegate.drawRect(originX + x, originY + y, width, height);
        flushSurfacePresentation();
    }

    /**
     * Draws string.
     */
    public void drawString(String text, int x, int y) {
        if (text == null) {
            return;
        }
        Rectangle bounds = new Rectangle(
                originX + x,
                originY + y - font.getAscent(),
                Math.max(1, font.stringWidth(text)),
                Math.max(1, font.getHeight()));
        if (drawWithOptRenderMode(bounds, graphics -> font.drawString(graphics, text, originX + x, originY + y, color))) {
            return;
        }
        prepareSoftwareSurfaceMutation();
        font.drawString(delegate, text, originX + x, originY + y, color);
        flushSurfacePresentation();
    }

    /**
     * Fills polygon.
     */
    public void fillPolygon(int[] xs, int[] ys, int n) {
        fillPolygon(xs, ys, n, 0);
    }

    /**
     * Fills polygon.
     */
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
        prepareSoftwareSurfaceMutation();
        delegate.fillPolygon(drawX, drawY, n);
        flushSurfacePresentation();
    }

    /**
     * Fills rect.
     */
    public void fillRect(int x, int y, int width, int height) {
        Rectangle bounds = normalizedRectangle(originX + x, originY + y, width, height);
        if (drawWithOptRenderMode(bounds, graphics -> graphics.fillRect(originX + x, originY + y, width, height))) {
            return;
        }
        prepareSoftwareSurfaceMutation();
        delegate.fillRect(originX + x, originY + y, width, height);
        flushSurfacePresentation();
    }

    /**
     * Gets color Of Name.
     */
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

    /**
     * Gets color Of R G B.
     */
    public static int getColorOfRGB(int red, int green, int blue) {
        return getColorOfRGB(red, green, blue, 255);
    }

    /**
     * Gets color Of R G B.
     */
    public static int getColorOfRGB(int red, int green, int blue, int alpha) {
        return ((alpha & 0xFF) << 24) | ((red & 0xFF) << 16) | ((green & 0xFF) << 8) | (blue & 0xFF);
    }

    /**
     * Starts double-buffered drawing.
     */
    public void lock() {
        DoJaRuntime runtime = DoJaRuntime.current();
        if (runtime != null) {
            // Direct `Canvas.getGraphics()` loops bypass `requestRender(...)`,
            // so service queued runtime callbacks at explicit frame-lock
            // boundaries as well.
            runtime.drainApplicationCallbacks();
            runtime.surfaceLock().lock();
        }
    }

    /**
     * Sets clip.
     */
    public void setClip(int x, int y, int width, int height) {
        delegate.setClip(originX + x, originY + y, width, height);
    }

    /**
     * Sets color.
     */
    public void setColor(int color) {
        this.color = color;
        delegate.setColor(new Color(color, true));
    }

    /**
     * Sets font.
     */
    public void setFont(Font font) {
        if (font == null) {
            return;
        }
        this.font = font;
        delegate.setFont(font.awtFont());
    }

    /**
     * Sets origin.
     */
    public void setOrigin(int x, int y) {
        this.originX = x;
        this.originY = y;
    }

    /**
     * Sets picto Color Enabled.
     */
    public void setPictoColorEnabled(boolean enabled) {
        this.pictoColorEnabled = enabled;
    }

    /**
     * Ends double-buffered drawing.
     */
    public void unlock(boolean flush) {
        DoJaRuntime runtime = DoJaRuntime.current();
        // DoJa specifies that unlock() is a no-op when the lock count is already zero.
        if (runtime != null && !runtime.surfaceLock().isHeldByCurrentThread()) {
            return;
        }
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
            traceOpenGlesSync("unlock present flush=true outermost=" + outermostUnlock);
            oglRenderer.flushHardwarePresentation();
            presentedFrame = copyImage(surface.image());
        } else if (outermostUnlock && surface.hasRepaintHook()) {
            // Some games draw directly to Canvas.getGraphics() and finish the frame with unlock(false)
            // rather than unlock(true). Canvas surfaces still need to present at the end of that
            // outermost lock scope, while offscreen Image surfaces must remain offscreen.
            // These loops already own their pacing, so present without the sync-unlock delay that
            // explicit unlock(true)/flush() paths use.
            traceOpenGlesSync("unlock present flush=false outermost=true");
            oglRenderer.flushHardwarePresentation();
            presentedFrame = copyImage(surface.image());
            pacedPresentation = false;
        }
        if (runtime != null) {
            runtime.surfaceLock().unlock();
        }
        if (runtime != null && outermostUnlock) {
            // An outermost unlock marks the end of one application-owned draw
            // slice, so it is another safe point to release queued callbacks.
            runtime.drainApplicationCallbacks();
        }
        if (outermostUnlock && presentedFrame == null) {
            surface.endDepthFrame();
        }
        if (presentedFrame != null) {
            surface.flush(presentedFrame, pacedPresentation);
        }
    }

    /**
     * Sets flip Mode.
     */
    public void setFlipMode(int flipMode) {
        this.flipMode = flipMode;
    }

    /**
     * Draws scaled Image.
     */
    public void drawScaledImage(Image image, int dx, int dy, int dw, int dh, int sx, int sy, int sw, int sh) {
        drawSubImage(image, dx, dy, sx, sy, sw, sh, dw, dh);
    }

    /**
     * Draws string.
     */
    public void drawString(XString text, int x, int y) {
        drawString(_XStringSupport.valueOrNull(text), x, y);
    }

    /**
     * Draws string.
     */
    public void drawString(XString text, int x, int y, int offset, int length) {
        if (text == null) {
            return;
        }
        String value = _XStringSupport.value(text, "text");
        int start = Math.max(0, offset);
        int end = Math.min(value.length(), start + Math.max(0, length));
        drawString(value.substring(start, end), x, y);
    }

    /**
     * Draws arc.
     */
    public void drawArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        Rectangle bounds = normalizedRectangle(originX + x, originY + y, width, height);
        if (drawWithOptRenderMode(bounds, graphics -> graphics.drawArc(originX + x, originY + y, width, height, startAngle, arcAngle))) {
            return;
        }
        prepareSoftwareSurfaceMutation();
        delegate.drawArc(originX + x, originY + y, width, height, startAngle, arcAngle);
        flushSurfacePresentation();
    }

    /**
     * Fills arc.
     */
    public void fillArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        Rectangle bounds = normalizedRectangle(originX + x, originY + y, width, height);
        if (drawWithOptRenderMode(bounds, graphics -> graphics.fillArc(originX + x, originY + y, width, height, startAngle, arcAngle))) {
            return;
        }
        prepareSoftwareSurfaceMutation();
        delegate.fillArc(originX + x, originY + y, width, height, startAngle, arcAngle);
        flushSurfacePresentation();
    }

    /**
     * Gets pixel.
     */
    public int getPixel(int x, int y) {
        return surface.image().getRGB(originX + x, originY + y);
    }

    /**
     * Gets r G B Pixel.
     */
    public int getRGBPixel(int x, int y) {
        return getPixel(x, y);
    }

    /**
     * Gets pixels.
     */
    public int[] getPixels(int x, int y, int width, int height, int[] pixels, int offset) {
        return getRGBPixels(x, y, width, height, pixels, offset);
    }

    /**
     * Gets r G B Pixels.
     */
    public int[] getRGBPixels(int x, int y, int width, int height, int[] pixels, int offset) {
        int[] target = pixels == null ? new int[offset + (width * height)] : pixels;
        surface.image().getRGB(originX + x, originY + y, width, height, target, offset, width);
        return target;
    }

    /**
     * Sets pixel.
     */
    public void setPixel(int x, int y) {
        setRGBPixel(x, y, color);
    }

    /**
     * Sets pixel.
     */
    public void setPixel(int x, int y, int color) {
        setRGBPixel(x, y, color);
    }

    /**
     * Sets r G B Pixel.
     */
    public void setRGBPixel(int x, int y, int color) {
        prepareSoftwareSurfaceMutation();
        surface.image().setRGB(originX + x, originY + y, toOpaqueRgb(color));
        flushSurfacePresentation();
    }

    /**
     * Sets pixels.
     */
    public void setPixels(int x, int y, int width, int height, int[] pixels, int offset) {
        setRGBPixels(x, y, width, height, pixels, offset);
    }

    /**
     * Sets r G B Pixels.
     */
    public void setRGBPixels(int x, int y, int width, int height, int[] pixels, int offset) {
        int length = checkedRgbPixelLength(pixels, offset, width, height);
        prepareSoftwareSurfaceMutation();
        if (hasNonOpaqueRgbPixels(pixels, offset, length)) {
            surface.image().setRGB(originX + x, originY + y, width, height,
                    opaqueRgbPixels(pixels, offset, length), 0, width);
        } else {
            surface.image().setRGB(originX + x, originY + y, width, height, pixels, offset, width);
        }
        flushSurfacePresentation();
    }

    private static int checkedRgbPixelLength(int[] pixels, int offset, int width, int height) {
        if (pixels == null) {
            throw new NullPointerException("pixels");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("width and height must be > 0");
        }
        int length = Math.multiplyExact(width, height);
        if (offset < 0 || offset > pixels.length - length) {
            throw new ArrayIndexOutOfBoundsException(offset);
        }
        return length;
    }

    private static boolean hasNonOpaqueRgbPixels(int[] pixels, int offset, int length) {
        for (int i = 0; i < length; i++) {
            if ((pixels[offset + i] & 0xFF000000) != 0xFF000000) {
                return true;
            }
        }
        return false;
    }

    private static int[] opaqueRgbPixels(int[] pixels, int offset, int length) {
        int[] normalized = new int[length];
        for (int i = 0; i < length; i++) {
            normalized[i] = toOpaqueRgb(pixels[offset + i]);
        }
        return normalized;
    }

    private static int toOpaqueRgb(int pixel) {
        return 0xFF000000 | (pixel & 0x00FFFFFF);
    }

    /**
     * Draws image.
     */
    public void drawImage(Image image, int[] matrix, int sx, int sy, int width, int height) {
        validateAffineImageArguments(image, matrix);
        validateAffineImageRegion(width, height);
        drawAffineImageValidated(image, createDoJaAffineTransform(matrix), sx, sy, width, height);
    }

    /**
     * Draws image.
     */
    public void drawImage(Image image, int[] matrix) {
        validateAffineImageArguments(image, matrix);
        drawAffineImageValidated(image, createDoJaAffineTransform(matrix), 0, 0, image.getWidth(), image.getHeight());
    }

    protected final void drawAffineImageValidated(Image image, AffineTransform localTransform, int sx, int sy, int width, int height) {
        BufferedImage source = image.renderForDisplay();
        if (source == null) {
            return;
        }

        int srcX1 = sx;
        int srcY1 = sy;
        int srcX2 = sx + width;
        int srcY2 = sy + height;
        int clippedSrcX1 = Math.max(0, srcX1);
        int clippedSrcY1 = Math.max(0, srcY1);
        int clippedSrcX2 = Math.min(source.getWidth(), srcX2);
        int clippedSrcY2 = Math.min(source.getHeight(), srcY2);
        if (clippedSrcX1 >= clippedSrcX2 || clippedSrcY1 >= clippedSrcY2) {
            return;
        }

        BufferedImage clippedSource = source.getSubimage(
                clippedSrcX1,
                clippedSrcY1,
                clippedSrcX2 - clippedSrcX1,
                clippedSrcY2 - clippedSrcY1);

        // Keep clipped source pixels at their original local coordinates instead of stretching
        // the visible remainder to fill the full requested region.
        localTransform.translate(clippedSrcX1 - srcX1, clippedSrcY1 - srcY1);
        Rectangle localBounds = transformedBounds(localTransform, clippedSource.getWidth(), clippedSource.getHeight());
        AffineTransform oldTransform = delegate.getTransform();
        Composite oldComposite = delegate.getComposite();
        Rectangle bounds = normalizedRectangle(
                originX + localBounds.x,
                originY + localBounds.y,
                localBounds.width,
                localBounds.height);
        if (drawWithOptRenderMode(bounds, graphics -> {
            AffineTransform drawTransform = new AffineTransform(localTransform);
            drawTransform.preConcatenate(AffineTransform.getTranslateInstance(originX, originY));
            AffineTransform savedTransform = graphics.getTransform();
            Composite savedComposite = graphics.getComposite();
            try {
                applyFlipTransform(graphics, localBounds.x, localBounds.y, localBounds.width, localBounds.height);
                if (image.getAlpha() < 255) {
                    graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, image.getAlpha() / 255.0f));
                }
                graphics.drawImage(clippedSource, drawTransform, null);
            } finally {
                graphics.setComposite(savedComposite);
                graphics.setTransform(savedTransform);
            }
        })) {
            return;
        }
        prepareSoftwareSurfaceMutation();
        try {
            applyFlipTransform(localBounds.x, localBounds.y, localBounds.width, localBounds.height);
            AffineTransform drawTransform = new AffineTransform(localTransform);
            drawTransform.preConcatenate(AffineTransform.getTranslateInstance(originX, originY));
            if (image.getAlpha() < 255) {
                delegate.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, image.getAlpha() / 255.0f));
            }
            delegate.drawImage(clippedSource, drawTransform, null);
        } finally {
            delegate.setComposite(oldComposite);
            delegate.setTransform(oldTransform);
        }
        flushSurfacePresentation();
    }

    private static void validateAffineImageArguments(Image image, int[] matrix) {
        if (image == null) {
            throw new NullPointerException("image");
        }
        if (matrix == null) {
            throw new NullPointerException("matrix");
        }
        if (matrix.length < 6) {
            throw new ArrayIndexOutOfBoundsException("matrix");
        }
    }

    private static void validateAffineImageRegion(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("width and height must be > 0");
        }
    }

    protected static final AffineTransform createDoJaAffineTransform(int[] matrix) {
        return new AffineTransform(
                matrix[0] / DOJAAFFINE_FIXED_POINT_SCALE,
                matrix[3] / DOJAAFFINE_FIXED_POINT_SCALE,
                matrix[1] / DOJAAFFINE_FIXED_POINT_SCALE,
                matrix[4] / DOJAAFFINE_FIXED_POINT_SCALE,
                matrix[2] / DOJAAFFINE_FIXED_POINT_SCALE,
                matrix[5] / DOJAAFFINE_FIXED_POINT_SCALE);
    }

    protected final void ensureImageNotDisposed(Image image) {
        if (image instanceof DesktopImage desktopImage) {
            desktopImage.ensureNotDisposed();
        }
    }

    protected final Image resolveNthMediaImageFrame(MediaImage image, int k) {
        if (image == null) {
            throw new NullPointerException("image");
        }
        if (image instanceof MediaManager.AbstractMediaResource tracked && tracked.state() != MediaResource.USE) {
            throw new UIException(UIException.ILLEGAL_STATE);
        }
        if (image instanceof MediaManager.AnimatedMediaImage animated) {
            if (k < 0 || k >= animated.frameCount()) {
                throw new IllegalArgumentException("k");
            }
            return animated.frame(k);
        }
        if (k != 0) {
            throw new IllegalArgumentException("k");
        }
        return image.getImage();
    }

    private static Rectangle transformedBounds(AffineTransform transform, int width, int height) {
        double[] corners = {
                0.0, 0.0,
                width, 0.0,
                0.0, height,
                width, height
        };
        transform.transform(corners, 0, corners, 0, corners.length / 2);
        double minX = corners[0];
        double minY = corners[1];
        double maxX = corners[0];
        double maxY = corners[1];
        for (int i = 2; i < corners.length; i += 2) {
            minX = Math.min(minX, corners[i]);
            minY = Math.min(minY, corners[i + 1]);
            maxX = Math.max(maxX, corners[i]);
            maxY = Math.max(maxY, corners[i + 1]);
        }
        int left = (int) Math.floor(minX);
        int top = (int) Math.floor(minY);
        int right = (int) Math.ceil(maxX);
        int bottom = (int) Math.ceil(maxY);
        return new Rectangle(left, top, Math.max(1, right - left), Math.max(1, bottom - top));
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
        Rectangle bounds = flippedBounds(rectangleFromPoints(destX1, destY1, destX2, destY2), dx, dy, dw, dh);
        if (drawWithOptRenderMode(bounds, graphics -> {
            AffineTransform savedTransform = graphics.getTransform();
            Composite savedComposite = graphics.getComposite();
            try {
                applyFlipTransform(graphics, dx, dy, dw, dh);
                if (image.getAlpha() < 255) {
                    graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, image.getAlpha() / 255.0f));
                }
                graphics.drawImage(source, destX1, destY1, destX2, destY2, clippedSrcX1, clippedSrcY1, clippedSrcX2, clippedSrcY2, null);
            } finally {
                graphics.setComposite(savedComposite);
                graphics.setTransform(savedTransform);
            }
        })) {
            return;
        }
        prepareSoftwareSurfaceMutation();
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
        Rectangle visibleBounds = bounds.intersection(new Rectangle(0, 0, surface.width(), surface.height()));
        if (shouldImmediatelyPresentOutsideLockImage(image, visibleBounds)) {
            oglRenderer.onSoftwareSurfaceMutation();
            traceOpenGlesSync("immediate software image present outside lock");
            surface.flush(copyImage(surface.image()), false);
            return;
        }
        flushSurfacePresentation(visibleBounds);
    }

    private boolean shouldImmediatelyPresentOutsideLockImage(Image image, Rectangle visibleBounds) {
        OpenGlesRendererMode mode = OpenDoJaLaunchArgs.openGlesRendererMode();
        if (mode != OpenGlesRendererMode.HARDWARE) {
            traceOutsideLockPresentDecision("image", false, "mode=" + mode, null, visibleBounds);
            return false;
        }
        if (!(image instanceof DesktopImage desktopImage)) {
            traceOutsideLockPresentDecision("image", false, "non-desktop-image", null, visibleBounds);
            return false;
        }
        if (desktopImage.width() != surface.width() || desktopImage.height() != surface.height()) {
            traceOutsideLockPresentDecision("image", false,
                    "size=" + desktopImage.width() + "x" + desktopImage.height()
                            + " surface=" + surface.width() + "x" + surface.height(),
                    desktopImage, visibleBounds);
            return false;
        }
        if (visibleBounds.isEmpty()) {
            traceOutsideLockPresentDecision("image", false, "empty-bounds", desktopImage, visibleBounds);
            return false;
        }
        if (visibleBounds.width < surface.width() - 32 || visibleBounds.height < surface.height() - 32) {
            traceOutsideLockPresentDecision("image", false,
                    "bounds=" + visibleBounds.width + "x" + visibleBounds.height,
                    desktopImage, visibleBounds);
            return false;
        }
        DoJaRuntime runtime = DoJaRuntime.current();
        boolean hasRepaintHook = surface.hasRepaintHook();
        boolean insideLock = runtime != null && runtime.surfaceLock().isHeldByCurrentThread();
        boolean immediate = hasRepaintHook && !insideLock;
        traceOutsideLockPresentDecision("image", immediate,
                "hook=" + hasRepaintHook + " insideLock=" + insideLock,
                desktopImage, visibleBounds);
        return immediate;
    }

    private BufferedImage copyImage(BufferedImage image) {
        if (image == surface.image()) {
            return surface.copyForPresentation();
        }
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
        flushSurfacePresentation(null);
    }

    private void flushSurfacePresentation(Rectangle bounds) {
        DoJaRuntime runtime = DoJaRuntime.current();
        boolean insideLock = runtime != null && runtime.surfaceLock().isHeldByCurrentThread();
        if (insideLock || !surface.hasRepaintHook()) {
            oglRenderer.onSoftwareSurfaceMutation();
            return;
        }
        if (OpenDoJaLaunchArgs.openGlesRendererMode() == OpenGlesRendererMode.HARDWARE) {
            // Immediate outside-lock presents can race the buffered hardware
            // frame and expose partially updated output. Keep the software
            // mutation on the backing surface, but let the next normal
            // unlock/present boundary publish it.
            Rectangle overlayBounds = bounds == null ? null : intersectSurfaceBounds(bounds);
            oglRenderer.onPresentedSoftwareOverlay(overlayBounds);
            traceOpenGlesSync("deferred software present outside lock"
                    + (overlayBounds == null
                    ? ""
                    : " bounds=" + overlayBounds.x + "," + overlayBounds.y + " "
                    + overlayBounds.width + "x" + overlayBounds.height));
            return;
        }
        oglRenderer.onPresentedSoftwareOverlay(bounds);
        traceOpenGlesSync("immediate software present outside lock");
        surface.flush(copyImage(surface.image()), false);
    }

    private void prepareSoftwareSurfaceMutation() {
        oglRenderer.prepareForSoftwareMutation();
    }

    private void traceOpenGlesSync(String message) {
        if (!TRACE_OPEN_GLES_SYNC) {
            return;
        }
        OpenDoJaLog.debug(Graphics.class, message);
    }

    protected boolean usesOptRenderMode() {
        return false;
    }

    protected int currentOptRenderOperator() {
        return OPT_RENDER_OP_REPL;
    }

    protected int currentOptRenderSourceRatio() {
        return 255;
    }

    protected int currentOptRenderDestinationRatio() {
        return 255;
    }

    private static void configureDelegate(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, Font.textAntialiasHint());
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
    }

    private boolean drawWithOptRenderMode(Rectangle bounds, Consumer<Graphics2D> painter) {
        if (!needsOptRenderModeBlend()) {
            return false;
        }
        Rectangle targetBounds = intersectSurfaceBounds(bounds);
        if (targetBounds == null) {
            return true;
        }
        BufferedImage layer = new BufferedImage(targetBounds.width, targetBounds.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = layer.createGraphics();
        try {
            configureDelegate(graphics);
            graphics.setColor(new Color(color, true));
            graphics.setFont(font.awtFont());
            graphics.translate(-targetBounds.x, -targetBounds.y);
            Rectangle clipBounds = delegate.getClipBounds();
            if (clipBounds != null) {
                graphics.setClip(clipBounds);
            }
            painter.accept(graphics);
        } finally {
            graphics.dispose();
        }
        prepareSoftwareSurfaceMutation();
        blendOptRenderLayer(layer, targetBounds.x, targetBounds.y);
        if (shouldImmediatelyPresentOptLayer(targetBounds)) {
            oglRenderer.onSoftwareSurfaceMutation();
            oglRenderer.flushHardwarePresentation();
            traceOpenGlesSync("immediate opt-layer present outside lock");
            surface.flush(copyImage(surface.image()), false);
            return true;
        }
        flushSurfacePresentation(targetBounds);
        return true;
    }

    private boolean needsOptRenderModeBlend() {
        return usesOptRenderMode()
                && (currentOptRenderOperator() != OPT_RENDER_OP_REPL || currentOptRenderSourceRatio() != 255);
    }

    private Rectangle intersectSurfaceBounds(Rectangle bounds) {
        if (bounds == null) {
            return null;
        }
        Rectangle clipped = bounds.intersection(new Rectangle(0, 0, surface.width(), surface.height()));
        Rectangle clipBounds = delegate.getClipBounds();
        if (clipBounds != null) {
            clipped = clipped.intersection(clipBounds);
        }
        return clipped.isEmpty() ? null : clipped;
    }

    private boolean shouldImmediatelyPresentOptLayer(Rectangle targetBounds) {
        OpenGlesRendererMode mode = OpenDoJaLaunchArgs.openGlesRendererMode();
        if (mode != OpenGlesRendererMode.HARDWARE) {
            traceOutsideLockPresentDecision("opt", false, "mode=" + mode, null, targetBounds);
            return false;
        }
        if (targetBounds == null || targetBounds.isEmpty()) {
            traceOutsideLockPresentDecision("opt", false, "empty-bounds", null, targetBounds);
            return false;
        }
        if (targetBounds.width < surface.width() - 32 || targetBounds.height < surface.height() - 32) {
            traceOutsideLockPresentDecision("opt", false,
                    "bounds=" + targetBounds.width + "x" + targetBounds.height,
                    null, targetBounds);
            return false;
        }
        DoJaRuntime runtime = DoJaRuntime.current();
        boolean hasRepaintHook = surface.hasRepaintHook();
        boolean insideLock = runtime != null && runtime.surfaceLock().isHeldByCurrentThread();
        boolean immediate = hasRepaintHook && !insideLock;
        traceOutsideLockPresentDecision("opt", immediate,
                "hook=" + hasRepaintHook + " insideLock=" + insideLock,
                null, targetBounds);
        return immediate;
    }

    private void traceOutsideLockPresentDecision(String kind, boolean immediate, String reason,
                                                 DesktopImage image, Rectangle bounds) {
        if (!TRACE_OPEN_GLES_SYNC) {
            return;
        }
        boolean oglImage = image != null && image.surface().hasOpenGlesActivity();
        String imageInfo = image == null
                ? ""
                : " image=" + image.width() + "x" + image.height() + " alpha=" + image.getAlpha()
                        + " ogl=" + oglImage;
        String boundsInfo = bounds == null
                ? " bounds=<null>"
                : " bounds=" + bounds.x + "," + bounds.y + " " + bounds.width + "x" + bounds.height;
        OpenDoJaLog.debug(Graphics.class,
                "outside-lock " + kind + " present immediate=" + immediate + " reason=" + reason
                        + imageInfo + boundsInfo);
    }

    private void blendOptRenderLayer(BufferedImage layer, int destX, int destY) {
        int[] destination = ((DataBufferInt) surface.image().getRaster().getDataBuffer()).getData();
        int[] source = ((DataBufferInt) layer.getRaster().getDataBuffer()).getData();
        int surfaceWidth = surface.width();
        int layerWidth = layer.getWidth();
        for (int y = 0; y < layer.getHeight(); y++) {
            int destIndex = (destY + y) * surfaceWidth + destX;
            int sourceIndex = y * layerWidth;
            for (int x = 0; x < layerWidth; x++, destIndex++, sourceIndex++) {
                int srcArgb = source[sourceIndex];
                int srcAlpha = (srcArgb >>> 24) & 0xFF;
                if (srcAlpha == 0) {
                    continue;
                }
                destination[destIndex] = blendOptRenderPixel(destination[destIndex], srcArgb);
            }
        }
    }

    private int blendOptRenderPixel(int dstArgb, int srcArgb) {
        int srcAlpha = (srcArgb >>> 24) & 0xFF;
        if (srcAlpha == 0) {
            return dstArgb;
        }
        int srcRatio = currentOptRenderSourceRatio();
        int dstRatio = currentOptRenderDestinationRatio();
        int uncoveredNumerator = (255 - srcAlpha) * 255;
        int coveredDstNumerator = srcAlpha * dstRatio;
        int srcNumerator = srcAlpha * srcRatio;

        int dstRed = (dstArgb >>> 16) & 0xFF;
        int dstGreen = (dstArgb >>> 8) & 0xFF;
        int dstBlue = dstArgb & 0xFF;
        int srcRed = (srcArgb >>> 16) & 0xFF;
        int srcGreen = (srcArgb >>> 8) & 0xFF;
        int srcBlue = srcArgb & 0xFF;

        int outRed = blendOptRenderChannel(dstRed, srcRed, uncoveredNumerator, coveredDstNumerator, srcNumerator);
        int outGreen = blendOptRenderChannel(dstGreen, srcGreen, uncoveredNumerator, coveredDstNumerator, srcNumerator);
        int outBlue = blendOptRenderChannel(dstBlue, srcBlue, uncoveredNumerator, coveredDstNumerator, srcNumerator);

        int dstAlpha = (dstArgb >>> 24) & 0xFF;
        int outAlpha = switch (currentOptRenderOperator()) {
            case OPT_RENDER_OP_REPL -> clamp(((dstAlpha * (255 - srcAlpha)) + srcNumerator + 127) / 255, 0, 255);
            case OPT_RENDER_OP_ADD, OPT_RENDER_OP_SUB -> Math.max(dstAlpha, (srcNumerator + 127) / 255);
            default -> Math.max(dstAlpha, srcAlpha);
        };
        return (outAlpha << 24) | (outRed << 16) | (outGreen << 8) | outBlue;
    }

    private int blendOptRenderChannel(int dst, int src, int uncoveredNumerator, int coveredDstNumerator, int srcNumerator) {
        long numerator = switch (currentOptRenderOperator()) {
            case OPT_RENDER_OP_REPL -> (long) dst * uncoveredNumerator + (long) src * srcNumerator;
            case OPT_RENDER_OP_ADD -> (long) dst * (uncoveredNumerator + coveredDstNumerator) + (long) src * srcNumerator;
            case OPT_RENDER_OP_SUB -> (long) dst * (uncoveredNumerator + coveredDstNumerator) - (long) src * srcNumerator;
            default -> (long) src * 65025L;
        };
        if (numerator <= 0L) {
            return 0;
        }
        long maxNumerator = 255L * 65025L;
        if (numerator >= maxNumerator) {
            return 255;
        }
        return (int) ((numerator + 32512L) / 65025L);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Rectangle normalizedRectangle(int x, int y, int width, int height) {
        int left = width >= 0 ? x : x + width;
        int top = height >= 0 ? y : y + height;
        int actualWidth = Math.abs(width);
        int actualHeight = Math.abs(height);
        return new Rectangle(left, top, Math.max(1, actualWidth), Math.max(1, actualHeight));
    }

    private static Rectangle rectangleFromPoints(int x1, int y1, int x2, int y2) {
        int left = Math.min(x1, x2);
        int top = Math.min(y1, y2);
        return new Rectangle(left, top, Math.max(1, Math.abs(x2 - x1) + 1), Math.max(1, Math.abs(y2 - y1) + 1));
    }

    private void flushSurfaceFrame() {
        // `Graphics3D.flush()` is the pass boundary for staged opt draws inside one locked frame.
        // Finish any deferred blended primitive batches before ending the shared depth frame.
        flushPending3DPasses();
        pendingOptRenderedContent = false;
        // Hardware presentation reads the GL framebuffer back into the software surface. That
        // does not make the surface "software dirty"; only later Java2D writes should trigger a
        // re-upload on the next GL pass, especially when the hardware path uses supersampling.
        oglRenderer.flushHardwarePresentation();
        DoJaRuntime runtime = DoJaRuntime.current();
        if (runtime != null && runtime.surfaceLock().isHeldByCurrentThread()) {
            // DoJa documents `Graphics3D.flush()` as applying pending 3D results, not as a
            // display-sync boundary. Some titles split one Canvas frame into several opt passes,
            // so syncing every flush would multiply the frame time. Keep the pass boundary
            // semantics here, but leave frame pacing to the eventual Canvas present.
            surface.endDepthFrame();
            return;
        }
        surface.flush(copyImage(surface.image()), false);
    }

    private void flushPending3DPasses() {
        doja3D.flushPendingOptPrimitiveBlends(delegate, surface.image());
    }

    private void prepare3DDepthFrame() {
        // Games can submit 3D assets through separate 3D calls inside one
        // lock/unlock frame. They must share one z-buffer or later props ignore ramp depth.
        pendingOptRenderedContent = true;
        doja3D.setFrameDepthBuffer(surface.depthBufferForFrame());
    }

    private DojaGraphics3DRenderer.RenderTarget renderTarget() {
        return new DojaGraphics3DRenderer.RenderTarget(
                delegate,
                surface.image(),
                originX,
                originY,
                surface.width(),
                surface.height());
    }

    private void applyFlipTransform(int dx, int dy, int dw, int dh) {
        applyFlipTransform(delegate, dx, dy, dw, dh);
    }

    private void applyFlipTransform(Graphics2D graphics, int dx, int dy, int dw, int dh) {
        graphics.transform(flipTransform(dx, dy, dw, dh));
    }

    private Rectangle flippedBounds(Rectangle bounds, int dx, int dy, int dw, int dh) {
        if (flipMode == FLIP_NONE) {
            return bounds;
        }
        return flipTransform(dx, dy, dw, dh).createTransformedShape(bounds).getBounds();
    }

    private AffineTransform flipTransform(int dx, int dy, int dw, int dh) {
        AffineTransform transform = new AffineTransform();
        switch (flipMode) {
            case FLIP_NONE -> {
            }
            case FLIP_HORIZONTAL -> {
                transform.translate(originX + dx + dw, originY + dy);
                transform.scale(-1, 1);
                transform.translate(-(originX + dx), -(originY + dy));
            }
            case FLIP_VERTICAL -> {
                transform.translate(originX + dx, originY + dy + dh);
                transform.scale(1, -1);
                transform.translate(-(originX + dx), -(originY + dy));
            }
            case FLIP_ROTATE -> {
                transform.rotate(Math.toRadians(180), originX + dx + dw / 2.0, originY + dy + dh / 2.0);
            }
            case FLIP_ROTATE_RIGHT -> {
                transform.translate(originX + dx + dh, originY + dy);
                transform.rotate(Math.toRadians(90));
                transform.translate(-(originX + dx), -(originY + dy));
            }
            case FLIP_ROTATE_LEFT -> {
                transform.translate(originX + dx, originY + dy + dw);
                transform.rotate(Math.toRadians(-90));
                transform.translate(-(originX + dx), -(originY + dy));
            }
            case FLIP_ROTATE_RIGHT_HORIZONTAL -> {
                transform.rotate(Math.toRadians(90), originX + dx + dw / 2.0, originY + dy + dh / 2.0);
                transform.scale(-1, 1);
            }
            case FLIP_ROTATE_RIGHT_VERTICAL -> {
                transform.rotate(Math.toRadians(90), originX + dx + dw / 2.0, originY + dy + dh / 2.0);
                transform.scale(1, -1);
            }
            default -> {
            }
        }
        return transform;
    }

    /**
     * Sets clip Rect For3 D.
     */
    @Override
    public void setClipRectFor3D(int x, int y, int width, int height) {
        try {
            doja3D.setClipRectFor3D(originX, originY, x, y, width, height);
        } catch (RuntimeException e) {
            throw traceFailure("setClipRectFor3D", e);
        }
    }

    /**
     * Sets parallel View.
     */
    @Override
    public void setParallelView(int width, int height) {
        try {
            doja3D.setParallelView(width, height);
        } catch (RuntimeException e) {
            throw traceFailure("setParallelView", e);
        }
    }

    /**
     * Sets perspective View.
     */
    @Override
    public void setPerspectiveView(float a, float b, int c, int d) {
        try {
            doja3D.setPerspectiveView(a, b, c, d);
        } catch (RuntimeException e) {
            throw traceFailure("setPerspectiveView(float,float,int,int)", e);
        }
    }

    /**
     * Sets perspective View.
     */
    @Override
    public void setPerspectiveView(float a, float b, float c) {
        try {
            doja3D.setPerspectiveView(a, b, c);
        } catch (RuntimeException e) {
            throw traceFailure("setPerspectiveView(float,float,float)", e);
        }
    }

    /**
     * Flushes any buffered drawing operations.
     */
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

    /**
     * Sets transform.
     */
    @Override
    public void setTransform(com.nttdocomo.ui.util3d.Transform transform) {
        try {
            doja3D.setTransform(transform);
        } catch (RuntimeException e) {
            throw traceFailure("setTransform", e);
        }
    }

    /**
     * Adds light.
     */
    @Override
    public void addLight(com.nttdocomo.ui.graphics3d.Light light, com.nttdocomo.ui.util3d.Transform transform) {
        try {
            doja3D.addLight(light);
        } catch (RuntimeException e) {
            throw traceFailure("addLight", e);
        }
    }

    /**
     * Resets lights.
     */
    @Override
    public void resetLights() {
        doja3D.resetLights();
    }

    /**
     * Sets fog.
     */
    @Override
    public void setFog(com.nttdocomo.ui.graphics3d.Fog fog) {
        doja3D.setFog(fog);
    }

    /**
     * Renders object3 D.
     */
    @Override
    public void renderObject3D(com.nttdocomo.ui.graphics3d.DrawableObject3D object, com.nttdocomo.ui.util3d.Transform transform) {
        try {
            doja3D.renderObject3D(renderTarget(), object, transform, this::prepare3DDepthFrame);
        } catch (RuntimeException e) {
            throw traceFailure("renderObject3D", e);
        }
    }

    /**
     * Sets view Trans.
     */
    @Override
    public void setViewTrans(com.nttdocomo.opt.ui.j3d.AffineTrans transform) {
        try {
            opt3D.setViewTrans(transform);
        } catch (RuntimeException e) {
            throw traceFailure("setViewTrans", e);
        }
    }

    /**
     * Sets view Trans Array.
     */
    @Override
    public void setViewTransArray(com.nttdocomo.opt.ui.j3d.AffineTrans[] transforms) {
        opt3D.setViewTransArray(transforms);
    }

    /**
     * Sets view Trans.
     */
    @Override
    public void setViewTrans(int index) {
        opt3D.setViewTrans(index);
    }

    /**
     * Sets screen Center.
     */
    @Override
    public void setScreenCenter(int x, int y) {
        try {
            opt3D.setScreenCenter(x, y);
        } catch (RuntimeException e) {
            throw traceFailure("setScreenCenter", e);
        }
    }

    /**
     * Sets screen Scale.
     */
    @Override
    public void setScreenScale(int x, int y) {
        try {
            opt3D.setScreenScale(x, y);
        } catch (RuntimeException e) {
            throw traceFailure("setScreenScale", e);
        }
    }

    /**
     * Sets screen View.
     */
    @Override
    public void setScreenView(int x, int y) {
        try {
            opt3D.setScreenView(x, y);
        } catch (RuntimeException e) {
            throw traceFailure("setScreenView", e);
        }
    }

    /**
     * Sets perspective.
     */
    @Override
    public void setPerspective(int near, int far, int width) {
        try {
            opt3D.setPerspective(near, far, width);
        } catch (RuntimeException e) {
            throw traceFailure("setPerspective(int,int,int)", e);
        }
    }

    /**
     * Sets perspective.
     */
    @Override
    public void setPerspective(int near, int far, int width, int height) {
        try {
            opt3D.setPerspective(near, far, width, height);
        } catch (RuntimeException e) {
            throw traceFailure("setPerspective(int,int,int,int)", e);
        }
    }

    /**
     * Draws figure.
     */
    @Override
    public void drawFigure(com.nttdocomo.opt.ui.j3d.Figure figure) {
        renderFigure(figure);
        flush();
    }

    /**
     * Renders figure.
     */
    @Override
    public void renderFigure(com.nttdocomo.opt.ui.j3d.Figure figure) {
        try {
            opt3D.renderFigure(figure, renderTarget(), this::prepare3DDepthFrame);
        } catch (RuntimeException e) {
            throw traceFailure("renderFigure", e);
        }
    }

    /**
     * Flushes any buffered drawing operations.
     */
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

    /**
     * Enables or disables sphere Map.
     */
    @Override
    public void enableSphereMap(boolean enabled) {
        opt3D.enableSphereMap(enabled);
    }

    /**
     * Sets sphere Texture.
     */
    @Override
    public void setSphereTexture(com.nttdocomo.opt.ui.j3d.Texture texture) {
        opt3D.setSphereTexture(texture);
    }

    /**
     * Enables or disables light.
     */
    @Override
    public void enableLight(boolean enabled) {
        opt3D.enableLight(enabled);
    }

    /**
     * Sets ambient Light.
     */
    @Override
    public void setAmbientLight(int color) {
        opt3D.setAmbientLight(color);
    }

    /**
     * Sets direction Light.
     */
    @Override
    public void setDirectionLight(com.nttdocomo.opt.ui.j3d.Vector3D direction, int color) {
        opt3D.setDirectionLight(direction, color);
    }

    /**
     * Enables or disables semi Transparent.
     */
    @Override
    public void enableSemiTransparent(boolean enabled) {
        opt3D.enableSemiTransparent(enabled);
    }

    /**
     * Sets clip Rect3 D.
     */
    @Override
    public void setClipRect3D(int x, int y, int width, int height) {
        try {
            opt3D.setClipRect3D(x, y, width, height);
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

    /**
     * Enables or disables toon Shader.
     */
    @Override
    public void enableToonShader(boolean enabled) {
        opt3D.enableToonShader(enabled);
    }

    /**
     * Sets toon Param.
     */
    @Override
    public void setToonParam(int highlight, int mid, int shadow) {
        opt3D.setToonParam(highlight, mid, shadow);
    }

    /**
     * Sets primitive Texture Array.
     */
    @Override
    public void setPrimitiveTextureArray(com.nttdocomo.opt.ui.j3d.Texture texture) {
        opt3D.setPrimitiveTextureArray(texture);
    }

    /**
     * Sets primitive Texture Array.
     */
    @Override
    public void setPrimitiveTextureArray(com.nttdocomo.opt.ui.j3d.Texture[] textures) {
        opt3D.setPrimitiveTextureArray(textures);
    }

    /**
     * Sets primitive Texture.
     */
    @Override
    public void setPrimitiveTexture(int index) {
        opt3D.setPrimitiveTexture(index);
    }

    /**
     * Renders primitives.
     */
    @Override
    public void renderPrimitives(com.nttdocomo.opt.ui.j3d.PrimitiveArray primitives, int attr) {
        opt3D.renderPrimitives(renderTarget(), primitives, attr, this::prepare3DDepthFrame);
    }

    /**
     * Renders primitives.
     */
    @Override
    public void renderPrimitives(com.nttdocomo.opt.ui.j3d.PrimitiveArray primitives, int start, int count, int attr) {
        opt3D.renderPrimitives(renderTarget(), primitives, start, count, attr, this::prepare3DDepthFrame);
    }

    /**
     * Executes command List.
     */
    @Override
    public void executeCommandList(int[] commands) {
        opt3D.executeCommandList(commands, renderTarget(), this::prepare3DDepthFrame, this::flushSurfaceFrame);
    }


}
