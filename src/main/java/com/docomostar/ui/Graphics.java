package com.docomostar.ui;

/** STAR graphics view over the same host context used for canvas presentation. */
public class Graphics implements com.docomostar.opt.ui.j3d.Graphics3D, com.docomostar.ui.graphics3d.Graphics3D, com.docomostar.ui.ogl.GraphicsOGL, com.docomostar.opt.ui.eruption.GraphicsEruption {
    public static final int BLACK = com.nttdocomo.ui.Graphics.BLACK;
    public static final int BLUE = com.nttdocomo.ui.Graphics.BLUE;
    public static final int LIME = com.nttdocomo.ui.Graphics.LIME;
    public static final int AQUA = com.nttdocomo.ui.Graphics.AQUA;
    public static final int RED = com.nttdocomo.ui.Graphics.RED;
    public static final int FUCHSIA = com.nttdocomo.ui.Graphics.FUCHSIA;
    public static final int YELLOW = com.nttdocomo.ui.Graphics.YELLOW;
    public static final int WHITE = com.nttdocomo.ui.Graphics.WHITE;
    public static final int GRAY = com.nttdocomo.ui.Graphics.GRAY;
    public static final int NAVY = com.nttdocomo.ui.Graphics.NAVY;
    public static final int GREEN = com.nttdocomo.ui.Graphics.GREEN;
    public static final int TEAL = com.nttdocomo.ui.Graphics.TEAL;
    public static final int MAROON = com.nttdocomo.ui.Graphics.MAROON;
    public static final int PURPLE = com.nttdocomo.ui.Graphics.PURPLE;
    public static final int OLIVE = com.nttdocomo.ui.Graphics.OLIVE;
    public static final int SILVER = com.nttdocomo.ui.Graphics.SILVER;
    public static final int FLIP_NONE = com.nttdocomo.ui.Graphics.FLIP_NONE;
    public static final int FLIP_HORIZONTAL = com.nttdocomo.ui.Graphics.FLIP_HORIZONTAL;
    public static final int FLIP_VERTICAL = com.nttdocomo.ui.Graphics.FLIP_VERTICAL;
    public static final int FLIP_ROTATE = com.nttdocomo.ui.Graphics.FLIP_ROTATE;
    public static final int FLIP_ROTATE_LEFT = com.nttdocomo.ui.Graphics.FLIP_ROTATE_LEFT;
    public static final int FLIP_ROTATE_RIGHT = com.nttdocomo.ui.Graphics.FLIP_ROTATE_RIGHT;
    public static final int FLIP_ROTATE_RIGHT_HORIZONTAL = com.nttdocomo.ui.Graphics.FLIP_ROTATE_RIGHT_HORIZONTAL;
    public static final int FLIP_ROTATE_RIGHT_VERTICAL = com.nttdocomo.ui.Graphics.FLIP_ROTATE_RIGHT_VERTICAL;

    protected final com.nttdocomo.ui.Graphics delegate;
    private StarOglContext oglContext;
    private long oglFragmentRevision = -1;

    @Override public void beginEruption() { opendoja.host.ogl.EruptionGraphicsContext.begin(delegate); }
    @Override public void endEruption() { opendoja.host.ogl.EruptionGraphicsContext.end(delegate); }

    @Override public com.nttdocomo.ui.ogl.GraphicsOGL hostGraphicsOGL() {
        StarOglContext current = StarOglContext.current();
        if (oglContext != current) {
            oglContext = current;
            oglFragmentRevision = -1;
        }
        oglFragmentRevision = current.bind(delegate, oglFragmentRevision);
        return delegate;
    }

    @Override public void glEnable(int cap) {
        try {
            hostGraphicsOGL().glEnable(cap);
            if (cap == GL_BLEND || cap == GL_ALPHA_TEST) rememberOglFragments();
        } catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }

    @Override public void glDisable(int cap) {
        try {
            hostGraphicsOGL().glDisable(cap);
            if (cap == GL_BLEND || cap == GL_ALPHA_TEST) rememberOglFragments();
        } catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }

    @Override public void glBlendFunc(int source, int destination) {
        try {
            hostGraphicsOGL().glBlendFunc(source, destination);
            rememberOglFragments();
        } catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }

    @Override public void glAlphaFunc(int function, float reference) {
        try {
            hostGraphicsOGL().glAlphaFunc(function, reference);
            rememberOglFragments();
        } catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }

    private void rememberOglFragments() {
        oglFragmentRevision = oglContext.remember(delegate);
    }
    protected Graphics() { this(com.nttdocomo.ui.Image.createImage(1, 1).getGraphics()); }
    Graphics(com.nttdocomo.ui.Graphics delegate) { this(delegate, true); }
    protected Graphics(com.nttdocomo.ui.Graphics delegate, boolean initializeFont) {
        this.delegate = delegate;
        if (initializeFont) delegate.setFont(Font.getDefaultFont().delegate);
    }
    /** Host integration: retain the optional graphics type on every drawing surface. */
    public static Graphics wrap(com.nttdocomo.ui.Graphics delegate) { return wrap(delegate, true); }
    private static Graphics wrap(com.nttdocomo.ui.Graphics delegate, boolean initializeFont) {
        return delegate instanceof com.nttdocomo.opt.ui.Graphics2 graphics
                ? new com.docomostar.opt.ui.Graphics2(graphics, initializeFont)
                : new Graphics(delegate, initializeFont);
    }
    public static int getColorOfRGB(int r, int g, int b) { return com.nttdocomo.ui.Graphics.getColorOfRGB(r, g, b); }
    public static int getColorOfRGB(int r, int g, int b, int a) { return com.nttdocomo.ui.Graphics.getColorOfRGB(r, g, b, a); }
    public static int getColorOfName(int name) { return com.nttdocomo.ui.Graphics.getColorOfName(name); }
    public void setFont(Font font) { delegate.setFont(font == null ? null : font.delegate); }
    public Graphics copy() { return wrap(delegate.copy(), false); }
    public void drawSpriteSet(SpriteSet sprites) {
        try { delegate.drawSpriteSet(sprites.host()); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void drawSpriteSet(SpriteSet sprites, int offset, int count) {
        try { delegate.drawSpriteSet(sprites.host(), offset, count); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void drawImageMap(ImageMap map, int x, int y) {
        try { delegate.drawImageMap(map == null ? null : map.host(), x, y); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void drawScaledImage(Image image, int x, int y, int width, int height,
                                int sourceX, int sourceY, int sourceWidth, int sourceHeight) {
        try { delegate.drawScaledImage(image.delegate, x, y, width, height, sourceX, sourceY, sourceWidth, sourceHeight); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void drawImage(Image image, int x, int y) {
        try { delegate.drawImage(image.delegate, x, y); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void drawImage(Image image, int x, int y, int sourceX, int sourceY, int width, int height) {
        try { delegate.drawImage(image.delegate, x, y, sourceX, sourceY, width, height); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void drawImage(Image image, int[] matrix, int sourceX, int sourceY, int width, int height) {
        try { delegate.drawImage(image.delegate, matrix, sourceX, sourceY, width, height); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void drawImage(Image image, int[] matrix) {
        try { delegate.drawImage(image.delegate, matrix); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void fillPolygon(int[] x, int[] y, int count) {
        try { delegate.fillPolygon(x, y, count); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void fillPolygon(int[] x, int[] y, int offset, int count) {
        int[] selectedX = polygonRange(x, offset, count);
        int[] selectedY = polygonRange(y, offset, count);
        fillPolygon(selectedX, selectedY, count);
    }
    public void clearClip() {
        try { delegate.clearClip(); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void clearRect(int x, int y, int width, int height) {
        try { delegate.clearRect(x, y, width, height); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void clipRect(int x, int y, int width, int height) {
        try { delegate.clipRect(x, y, width, height); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void setClip(int x, int y, int width, int height) {
        try { delegate.setClip(x, y, width, height); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void setColor(int color) {
        try { delegate.setColor(color); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void setOrigin(int x, int y) {
        try { delegate.setOrigin(x, y); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void setFlipMode(int mode) {
        try { delegate.setFlipMode(mode); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void lock() {
        try { delegate.lock(); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void unlock(boolean present) {
        try { delegate.unlock(present); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void drawString(String text, int x, int y) {
        try { delegate.drawString(text, x, y); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void drawString(com.docomostar.lang.XString text, int x, int y) {
        try { delegate.drawString(com.docomostar.lang._XStringSupport.unwrap(text), x, y); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void drawString(com.docomostar.lang.XString text, int x, int y, int offset, int length) {
        try { delegate.drawString(com.docomostar.lang._XStringSupport.unwrap(text), x, y, offset, length); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void drawChars(char[] text, int offset, int length, int x, int y) {
        try { delegate.drawChars(text, offset, length, x, y); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void drawLine(int x1, int y1, int x2, int y2) {
        try { delegate.drawLine(x1, y1, x2, y2); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void drawPolyline(int[] x, int[] y, int count) {
        try { delegate.drawPolyline(x, y, count); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void drawPolyline(int[] x, int[] y, int offset, int count) {
        int[] selectedX = polygonRange(x, offset, count);
        int[] selectedY = polygonRange(y, offset, count);
        drawPolyline(selectedX, selectedY, count);
    }
    private static int[] polygonRange(int[] points, int offset, int count) {
        java.util.Objects.requireNonNull(points, "points");
        java.util.Objects.checkFromIndexSize(offset, count, points.length);
        return java.util.Arrays.copyOfRange(points, offset, offset + count);
    }
    public int getPixel(int x, int y) {
        try { return delegate.getPixel(x, y); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public int getRGBPixel(int x, int y) {
        try { return delegate.getRGBPixel(x, y) & 0x00ffffff; }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public int[] getPixels(int x, int y, int width, int height, int[] pixels, int offset) {
        try { return delegate.getPixels(x, y, width, height, pixels, offset); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public int[] getRGBPixels(int x, int y, int width, int height, int[] pixels, int offset) {
        try {
            int[] result = delegate.getRGBPixels(x, y, width, height, pixels, offset);
            // RGB readback exposes colours, not the host surface's alpha byte.
            // Games use exact 0xRRGGBB comparisons to rebuild colour-keyed textures.
            for (int i = offset, end = offset + width * height; i < end; i++) result[i] &= 0x00ffffff;
            return result;
        }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void setPixel(int x, int y) {
        try { delegate.setPixel(x, y); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void setPixel(int x, int y, int color) {
        try { delegate.setPixel(x, y, color); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void setRGBPixels(int x, int y, int width, int height, int[] pixels, int offset) {
        try { delegate.setRGBPixels(x, y, width, height, pixels, offset); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void setPixels(int x, int y, int width, int height, int[] pixels, int offset) {
        try { delegate.setPixels(x, y, width, height, pixels, offset); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void setRGBPixel(int x, int y, int color) {
        try { delegate.setRGBPixel(x, y, color); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void drawRect(int x, int y, int width, int height) {
        try { delegate.drawRect(x, y, width, height); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void fillRect(int x, int y, int width, int height) {
        try { delegate.fillRect(x, y, width, height); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void drawArc(int x, int y, int width, int height, int start, int extent) {
        try { delegate.drawArc(x, y, width, height, start, extent); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void fillArc(int x, int y, int width, int height, int start, int extent) {
        try { delegate.fillArc(x, y, width, height, start, extent); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void copyArea(int x, int y, int width, int height, int dx, int dy) {
        try { delegate.copyArea(x, y, width, height, dx, dy); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void setPictoColorEnabled(boolean enabled) {
        try { delegate.setPictoColorEnabled(enabled); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void setViewTrans(com.docomostar.opt.ui.j3d.AffineTrans transform) {
        try { delegate.setViewTrans(transform); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void setViewTransArray(com.docomostar.opt.ui.j3d.AffineTrans[] transforms) {
        try { delegate.setViewTransArray(transforms); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void setViewTrans(int index) {
        try { delegate.setViewTrans(index); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void setScreenCenter(int x, int y) {
        try { delegate.setScreenCenter(x, y); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void setScreenScale(int x, int y) {
        try { delegate.setScreenScale(x, y); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void setScreenView(int x, int y) {
        try { delegate.setScreenView(x, y); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void setPerspective(int near, int far, int width) {
        try { delegate.setPerspective(near, far, width); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void setPerspective(int near, int far, int width, int height) {
        try { delegate.setPerspective(near, far, width, height); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void setPrimitiveTextureArray(com.docomostar.opt.ui.j3d.Texture texture) {
        try { delegate.setPrimitiveTextureArray(texture); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void setPrimitiveTextureArray(com.docomostar.opt.ui.j3d.Texture[] textures) {
        try { delegate.setPrimitiveTextureArray(textures); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void setPrimitiveTexture(int index) {
        try { delegate.setPrimitiveTexture(index); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void executeCommandList(int[] commands) {
        try { delegate.executeCommandList(commands); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void renderPrimitives(com.docomostar.opt.ui.j3d.PrimitiveArray primitives, int attributes) {
        try { delegate.renderPrimitives(primitives, attributes); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void renderPrimitives(com.docomostar.opt.ui.j3d.PrimitiveArray primitives, int start, int count, int attributes) {
        try { delegate.renderPrimitives(primitives, start, count, attributes); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void flush() {
        try { delegate.flush(); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void drawFigure(com.docomostar.opt.ui.j3d.Figure figure) {
        try { delegate.drawFigure(figure); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void renderFigure(com.docomostar.opt.ui.j3d.Figure figure) {
        try { delegate.renderFigure(figure); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void enableSphereMap(boolean enabled) { delegate.enableSphereMap(enabled); }
    public void setSphereTexture(com.docomostar.opt.ui.j3d.Texture texture) { delegate.setSphereTexture(texture); }
    public void setClipRect3D(int x, int y, int width, int height) {
        try { delegate.setClipRect3D(x, y, width, height); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void enableSemiTransparent(boolean enabled) {
        try { delegate.enableSemiTransparent(enabled); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void enableLight(boolean enabled) {
        try { delegate.enableLight(enabled); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void setAmbientLight(int color) {
        try { delegate.setAmbientLight(color); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void setDirectionLight(com.docomostar.opt.ui.j3d.Vector3D direction, int color) {
        try { delegate.setDirectionLight(direction, color); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void setClipRectFor3D(int x, int y, int width, int height) {
        try { delegate.setClipRectFor3D(x, y, width, height); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void setParallelView(int width, int height) {
        try { delegate.setParallelView(width, height); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void setPerspectiveView(float near, float far, int width, int height) {
        try { delegate.setPerspectiveView(near, far, width, height); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void setPerspectiveView(float near, float far, float angle) {
        try { delegate.setPerspectiveView(near, far, angle); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void flushBuffer() {
        try { delegate.flushBuffer(); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void setTransform(com.docomostar.ui.util3d.Transform transform) {
        try { delegate.setTransform(transform); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void addLight(com.docomostar.ui.graphics3d.Light light, com.docomostar.ui.util3d.Transform transform) {
        try { delegate.addLight((com.nttdocomo.ui.graphics3d.Light) com.docomostar.ui.graphics3d.Object3D.unwrap(light), transform); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void resetLights() {
        try { delegate.resetLights(); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void setFog(com.docomostar.ui.graphics3d.Fog fog) {
        try { delegate.setFog((com.nttdocomo.ui.graphics3d.Fog) com.docomostar.ui.graphics3d.Object3D.unwrap(fog)); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void renderObject3D(com.docomostar.ui.graphics3d.DrawableObject3D object, com.docomostar.ui.util3d.Transform transform) {
        try { delegate.renderObject3D((com.nttdocomo.ui.graphics3d.DrawableObject3D) com.docomostar.ui.graphics3d.Object3D.unwrap(object), transform); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
}
