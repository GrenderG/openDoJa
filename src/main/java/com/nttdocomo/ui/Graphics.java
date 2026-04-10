package com.nttdocomo.ui;

import com.nttdocomo.lang.XString;
import com.nttdocomo.opt.ui.j3d.PrimitiveArray;
import com.nttdocomo.ui.ogl.DirectBuffer;
import com.nttdocomo.ui.ogl.DirectBufferFactory;
import com.nttdocomo.ui.ogl.FloatBuffer;
import com.nttdocomo.ui.ogl.ShortBuffer;
import opendoja.host.DesktopSurface;
import opendoja.g3d.MascotFigure;
import opendoja.g3d.Software3DContext;
import opendoja.g3d.SoftwareTexture;
import opendoja.host.DoJaRuntime;
import opendoja.host.OpenDoJaLog;
import com.nttdocomo.ui.util3d.Transform;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Represents the graphics context used for canvases and images.
 */
public class Graphics implements com.nttdocomo.ui.graphics3d.Graphics3D, com.nttdocomo.opt.ui.j3d.Graphics3D, com.nttdocomo.ui.ogl.GraphicsOGL {
    private static final boolean TRACE_FAILURES = opendoja.host.OpenDoJaLaunchArgs.getBoolean(opendoja.host.OpenDoJaLaunchArgs.TRACE_FAILURES);
    private static final boolean TRACE_3D_CALLS = opendoja.host.OpenDoJaLaunchArgs.getBoolean(opendoja.host.OpenDoJaLaunchArgs.DEBUG3D_CALLS);
    private static final double DOJAAFFINE_FIXED_POINT_SCALE = 4096.0;
    private static final int LEGACY_OPT_COMMAND_LIST_VERSION_1 = 1;
    private static final int OPT_COMMAND_PREFIX_MASK = 0xFF00_0000;
    private static final int OPT_COMMAND_INLINE_VALUE_MASK = 0x00FF_FFFF;
    private static final int OPT_COMMAND_RENDER_COUNT_MASK = 0x00FF_0000;
    private static final long OPT_RENDER_SYNC_INTERVAL_NANOS = 16_000_000L;
    private static final int OPT_COMMAND_ATTR_MASK =
            com.nttdocomo.opt.ui.j3d.Graphics3D.ATTR_LIGHT
                    | com.nttdocomo.opt.ui.j3d.Graphics3D.ATTR_SPHERE_MAP
                    | com.nttdocomo.opt.ui.j3d.Graphics3D.ATTR_COLOR_KEY
                    | com.nttdocomo.opt.ui.j3d.Graphics3D.ATTR_BLEND_HALF
                    | com.nttdocomo.opt.ui.j3d.Graphics3D.ATTR_BLEND_ADD
                    | com.nttdocomo.opt.ui.j3d.Graphics3D.ATTR_BLEND_SUB;
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
    private final Graphics2D delegate;
    private final Software3DContext threeD = new Software3DContext();
    private boolean pendingOptRenderedContent;
    private int originX;
    private int originY;
    private int color = getColorOfName(BLACK);
    private Font font = Font.getDefaultFont();
    private int flipMode = FLIP_NONE;
    private boolean pictoColorEnabled;
    private com.nttdocomo.ui.graphics3d.Fog uiFog;
    private int oglClearColor = 0xFF000000;
    private boolean optSphereMapEnabled;
    private SoftwareTexture optSphereTexture;
    private boolean optToonShaderEnabled;
    private int optToonThreshold = 128;
    private int optToonMid = 255;
    private int optToonShadow = 96;
    private com.nttdocomo.opt.ui.j3d.AffineTrans[] optViewTransforms = new com.nttdocomo.opt.ui.j3d.AffineTrans[0];
    private final OglState ogl = new OglState();
    private final ClipVector clipVectorTemp = new ClipVector();
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
        this.delegate.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        this.delegate.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        this.delegate.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, Font.textAntialiasHint());
        this.delegate.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        this.delegate.setColor(new Color(color, true));
        this.delegate.setFont(font.awtFont());
        clearClip();
        syncOptRendererState();
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
        copy.uiFog = uiFog;
        copy.optSphereMapEnabled = optSphereMapEnabled;
        copy.optSphereTexture = optSphereTexture;
        copy.optToonShaderEnabled = optToonShaderEnabled;
        copy.optToonThreshold = optToonThreshold;
        copy.optToonMid = optToonMid;
        copy.optToonShadow = optToonShadow;
        copy.syncUiFogState();
        copy.syncOptRendererState();
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

    @Override
    public void glClearColor(float red, float green, float blue, float alpha) {
        oglClearColor = ((clampOglChannel(alpha) & 0xFF) << 24)
                | ((clampOglChannel(red) & 0xFF) << 16)
                | ((clampOglChannel(green) & 0xFF) << 8)
                | (clampOglChannel(blue) & 0xFF);
    }

    @Override
    public void glClear(int mask) {
        if ((mask & com.nttdocomo.ui.ogl.GraphicsOGL.GL_DEPTH_BUFFER_BIT) != 0) {
            surface.endDepthFrame();
        }
        if ((mask & com.nttdocomo.ui.ogl.GraphicsOGL.GL_COLOR_BUFFER_BIT) == 0) {
            return;
        }
        Rectangle clip = delegate.getClipBounds();
        int x = clip == null ? 0 : clip.x;
        int y = clip == null ? 0 : clip.y;
        int width = clip == null ? surface.width() : clip.width;
        int height = clip == null ? surface.height() : clip.height;
        Color old = delegate.getColor();
        delegate.setColor(new Color(oglClearColor, true));
        delegate.fillRect(x, y, width, height);
        delegate.setColor(old);
        flushSurfacePresentation();
    }

    @Override
    public void beginDrawing() {
        ogl.beginDrawing();
    }

    @Override
    public void endDrawing() {
        ogl.endDrawing();
    }

    @Override
    public void glEnable(int cap) {
        ogl.enabledCaps.add(cap);
    }

    @Override
    public void glDisable(int cap) {
        ogl.enabledCaps.remove(cap);
    }

    @Override
    public void glEnableClientState(int array) {
        ogl.enabledClientStates.add(array);
    }

    @Override
    public void glDisableClientState(int array) {
        ogl.enabledClientStates.remove(array);
    }

    @Override
    public void glMatrixMode(int mode) {
        ogl.matrixMode = mode;
    }

    @Override
    public void glLoadIdentity() {
        loadMatrix(ogl.matrixMode, OglState.identityMatrix());
    }

    @Override
    public void glLoadMatrixf(float[] m) {
        if (m == null || m.length < 16) {
            ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_VALUE;
            return;
        }
        loadMatrix(ogl.matrixMode, m.clone());
    }

    @Override
    public void glMultMatrixf(float[] m) {
        if (m == null || m.length < 16) {
            ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_VALUE;
            return;
        }
        multiplyCurrentMatrix(m.clone());
    }

    @Override
    public void glOrthof(float left, float right, float bottom, float top, float zNear, float zFar) {
        float[] matrix = OglState.identityMatrix();
        matrix[0] = 2f / Math.max(0.0001f, right - left);
        matrix[5] = 2f / Math.max(0.0001f, top - bottom);
        matrix[10] = -2f / Math.max(0.0001f, zFar - zNear);
        matrix[12] = -((right + left) / Math.max(0.0001f, right - left));
        matrix[13] = -((top + bottom) / Math.max(0.0001f, top - bottom));
        matrix[14] = -((zFar + zNear) / Math.max(0.0001f, zFar - zNear));
        loadMatrix(ogl.matrixMode, matrix);
    }

    @Override
    public void glFrustumf(float left, float right, float bottom, float top, float zNear, float zFar) {
        float near = Math.max(0.0001f, zNear);
        float far = Math.max(near + 0.0001f, zFar);
        float width = Math.max(0.0001f, right - left);
        float height = Math.max(0.0001f, top - bottom);
        float depth = Math.max(0.0001f, far - near);
        float[] matrix = new float[16];
        matrix[0] = (2f * near) / width;
        matrix[5] = (2f * near) / height;
        matrix[8] = (right + left) / width;
        matrix[9] = (top + bottom) / height;
        matrix[10] = -((far + near) / depth);
        matrix[11] = -1f;
        matrix[14] = -((2f * far * near) / depth);
        multiplyCurrentMatrix(matrix);
    }

    @Override
    public void glDepthRangef(float zNear, float zFar) {
        ogl.depthRangeNear = Math.max(0f, Math.min(1f, zNear));
        ogl.depthRangeFar = Math.max(0f, Math.min(1f, zFar));
    }

    @Override
    public void glRotatef(float angle, float x, float y, float z) {
        float magnitude = (float) Math.sqrt((x * x) + (y * y) + (z * z));
        if (magnitude <= 0.000001f) {
            return;
        }
        float nx = x / magnitude;
        float ny = y / magnitude;
        float nz = z / magnitude;
        float radians = (float) Math.toRadians(angle);
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        float inverseCos = 1f - cos;
        float[] matrix = OglState.identityMatrix();
        matrix[0] = cos + (nx * nx * inverseCos);
        matrix[1] = (ny * nx * inverseCos) + (nz * sin);
        matrix[2] = (nz * nx * inverseCos) - (ny * sin);
        matrix[4] = (nx * ny * inverseCos) - (nz * sin);
        matrix[5] = cos + (ny * ny * inverseCos);
        matrix[6] = (nz * ny * inverseCos) + (nx * sin);
        matrix[8] = (nx * nz * inverseCos) + (ny * sin);
        matrix[9] = (ny * nz * inverseCos) - (nx * sin);
        matrix[10] = cos + (nz * nz * inverseCos);
        multiplyCurrentMatrix(matrix);
    }

    @Override
    public void glScalef(float x, float y, float z) {
        float[] matrix = OglState.identityMatrix();
        matrix[0] = x;
        matrix[5] = y;
        matrix[10] = z;
        multiplyCurrentMatrix(matrix);
    }

    @Override
    public void glTranslatef(float x, float y, float z) {
        float[] matrix = OglState.identityMatrix();
        matrix[12] = x;
        matrix[13] = y;
        matrix[14] = z;
        multiplyCurrentMatrix(matrix);
    }

    @Override
    public void glAlphaFunc(int func, float ref) {
        ogl.alphaFunc = func;
        ogl.alphaRef = Math.max(0f, Math.min(1f, ref));
    }

    @Override
    public void glDepthMask(boolean flag) {
        ogl.depthMask = flag;
    }

    @Override
    public void glDepthFunc(int func) {
        ogl.depthFunc = func;
    }

    @Override
    public void glBlendFunc(int sfactor, int dfactor) {
        ogl.blendSrcFactor = sfactor;
        ogl.blendDstFactor = dfactor;
    }

    @Override
    public void glColor4f(float red, float green, float blue, float alpha) {
        ogl.color = ((clampOglChannel(alpha) & 0xFF) << 24)
                | ((clampOglChannel(red) & 0xFF) << 16)
                | ((clampOglChannel(green) & 0xFF) << 8)
                | (clampOglChannel(blue) & 0xFF);
    }

    @Override
    public void glTexEnvi(int target, int pname, int param) {
        if (target == com.nttdocomo.ui.ogl.GraphicsOGL.GL_TEXTURE_ENV
                && pname == com.nttdocomo.ui.ogl.GraphicsOGL.GL_TEXTURE_ENV_MODE) {
            ogl.textureEnvMode = param;
        }
    }

    @Override
    public void glShadeModel(int mode) {
        ogl.shadeModel = mode;
    }

    @Override
    public void glClientActiveTexture(int texture) {
        ogl.clientActiveTexture = texture;
    }

    @Override
    public void glPixelStorei(int pname, int param) {
        if (pname == com.nttdocomo.ui.ogl.GraphicsOGL.GL_UNPACK_ALIGNMENT) {
            ogl.unpackAlignment = Math.max(1, param);
        }
    }

    @Override
    public void glHint(int target, int mode) {
    }

    @Override
    public void glFrontFace(int mode) {
        ogl.frontFace = mode;
    }

    @Override
    public void glCullFace(int mode) {
        ogl.cullFace = mode;
    }

    @Override
    public void glTexParameterf(int target, int pname, float param) {
        OglTexture texture = ogl.boundTexture();
        if (texture == null || target != com.nttdocomo.ui.ogl.GraphicsOGL.GL_TEXTURE_2D) {
            return;
        }
        texture.setParameter(pname, Math.round(param));
    }

    @Override
    public void glBindTexture(int target, int texture) {
        if (target != com.nttdocomo.ui.ogl.GraphicsOGL.GL_TEXTURE_2D) {
            ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_ENUM;
            return;
        }
        ogl.boundTextureId = texture;
    }

    @Override
    public void glGenTextures(int n, int[] textures) {
        if (textures == null) {
            throw new NullPointerException("textures");
        }
        int count = Math.min(n, textures.length);
        for (int i = 0; i < count; i++) {
            int id = ogl.nextTextureId++;
            ogl.textures.put(id, new OglTexture());
            textures[i] = id;
        }
    }

    @Override
    public void glGenTextures(int[] textures) {
        glGenTextures(textures == null ? 0 : textures.length, textures);
    }

    @Override
    public void glDeleteTextures(int n, int[] textures) {
        if (textures == null) {
            throw new NullPointerException("textures");
        }
        int count = Math.min(n, textures.length);
        for (int i = 0; i < count; i++) {
            int textureId = textures[i];
            ogl.textures.remove(textureId);
            if (ogl.boundTextureId == textureId) {
                ogl.boundTextureId = 0;
            }
        }
    }

    @Override
    public void glDeleteTextures(int[] textures) {
        glDeleteTextures(textures == null ? 0 : textures.length, textures);
    }

    @Override
    public void glCompressedTexImage2D(int target, int level, int internalformat, int width, int height, int border, int imageSize, DirectBuffer pixels) {
        if (target != com.nttdocomo.ui.ogl.GraphicsOGL.GL_TEXTURE_2D || level != 0 || border != 0) {
            ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_VALUE;
            return;
        }
        OglTexture texture = ogl.boundTexture();
        if (texture == null) {
            ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_OPERATION;
            return;
        }
        if (!(pixels instanceof com.nttdocomo.ui.ogl.ByteBuffer byteBuffer)) {
            ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_VALUE;
            return;
        }
        byte[] raw = new byte[Math.max(0, imageSize)];
        int offset = DirectBufferFactory.getSegmentOffset(byteBuffer);
        byteBuffer.get(offset, raw, 0, raw.length);
        texture.loadCompressed(internalformat, width, height, raw);
    }

    @Override
    public void glTexImage2D(int target, int level, int internalformat, int width, int height, int border,
                             int format, int type, DirectBuffer pixels) {
        if (target != com.nttdocomo.ui.ogl.GraphicsOGL.GL_TEXTURE_2D || level != 0 || border != 0 || width <= 0 || height <= 0) {
            ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_VALUE;
            return;
        }
        OglTexture texture = ogl.boundTexture();
        if (texture == null) {
            ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_OPERATION;
            return;
        }
        if (!texture.loadUncompressed(width, height, format, type, pixels)) {
            ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_VALUE;
        }
    }

    @Override
    public void glVertexPointer(int size, int type, int stride, DirectBuffer pointer) {
        ogl.vertexPointer = new OglPointer(size, type, stride, pointer);
    }

    @Override
    public void glTexCoordPointer(int size, int type, int stride, DirectBuffer pointer) {
        ogl.texCoordPointer = new OglPointer(size, type, stride, pointer);
    }

    @Override
    public void glDrawArrays(int mode, int first, int count) {
        drawOgl(mode, first, count, null);
    }

    @Override
    public void glDrawElements(int mode, int count, int type, DirectBuffer indices) {
        if (type != com.nttdocomo.ui.ogl.GraphicsOGL.GL_UNSIGNED_SHORT) {
            ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_ENUM;
            return;
        }
        if (!(indices instanceof ShortBuffer)) {
            ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_VALUE;
            return;
        }
        drawOgl(mode, 0, count, indices);
    }

    @Override
    public void glDrawElements(int mode, int count, DirectBuffer indices) {
        glDrawElements(mode, count, com.nttdocomo.ui.ogl.GraphicsOGL.GL_UNSIGNED_SHORT, indices);
    }

    @Override
    public void glFlush() {
    }

    @Override
    public int glGetError() {
        int error = ogl.lastError;
        ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_NO_ERROR;
        return error;
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
        delegate.dispose();
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
        drawSpriteSet(spriteSet, 0, 0);
    }

    /**
     * Draws sprite Set.
     */
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
        for (int i = 0; i < n; i++) {
            drawX[i] = originX + xs[i];
            drawY[i] = originY + ys[i];
        }
        delegate.drawPolyline(drawX, drawY, n);
        flushSurfacePresentation();
    }

    /**
     * Draws rect.
     */
    public void drawRect(int x, int y, int width, int height) {
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
        delegate.fillPolygon(drawX, drawY, n);
        flushSurfacePresentation();
    }

    /**
     * Fills rect.
     */
    public void fillRect(int x, int y, int width, int height) {
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
        drawString(text == null ? null : text.toString(), x, y);
    }

    /**
     * Draws string.
     */
    public void drawString(XString text, int x, int y, int offset, int length) {
        if (text == null) {
            return;
        }
        String value = text.toString();
        int start = Math.max(0, offset);
        int end = Math.min(value.length(), start + Math.max(0, length));
        drawString(value.substring(start, end), x, y);
    }

    /**
     * Draws arc.
     */
    public void drawArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        delegate.drawArc(originX + x, originY + y, width, height, startAngle, arcAngle);
        flushSurfacePresentation();
    }

    /**
     * Fills arc.
     */
    public void fillArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
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
        surface.image().setRGB(originX + x, originY + y, color);
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
        surface.image().setRGB(originX + x, originY + y, width, height, pixels, offset, width);
        flushSurfacePresentation();
    }

    /**
     * Draws image.
     */
    public void drawImage(Image image, int[] matrix, int sx, int sy, int width, int height) {
        validateAffineImageArguments(image, matrix);
        validateAffineImageRegion(width, height);
        drawAffineImageValidated(image, matrix, sx, sy, width, height);
    }

    /**
     * Draws image.
     */
    public void drawImage(Image image, int[] matrix) {
        validateAffineImageArguments(image, matrix);
        drawAffineImageValidated(image, matrix, 0, 0, image.getWidth(), image.getHeight());
    }

    private void drawAffineImageValidated(Image image, int[] matrix, int sx, int sy, int width, int height) {
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

        // The DoJa overload takes a six-element 4.12 fixed-point affine matrix, not destination
        // corner points. When the requested source rectangle falls partly outside the image, keep
        // the remaining pixels at their original local coordinates instead of scaling them to fill
        // the full target area.
        AffineTransform localTransform = createDoJaAffineTransform(matrix);
        localTransform.translate(clippedSrcX1 - srcX1, clippedSrcY1 - srcY1);
        Rectangle localBounds = transformedBounds(localTransform, clippedSource.getWidth(), clippedSource.getHeight());
        AffineTransform oldTransform = delegate.getTransform();
        Composite oldComposite = delegate.getComposite();
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

    private static AffineTransform createDoJaAffineTransform(int[] matrix) {
        return new AffineTransform(
                matrix[0] / DOJAAFFINE_FIXED_POINT_SCALE,
                matrix[3] / DOJAAFFINE_FIXED_POINT_SCALE,
                matrix[1] / DOJAAFFINE_FIXED_POINT_SCALE,
                matrix[4] / DOJAAFFINE_FIXED_POINT_SCALE,
                matrix[2] / DOJAAFFINE_FIXED_POINT_SCALE,
                matrix[5] / DOJAAFFINE_FIXED_POINT_SCALE);
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
        if (!surface.hasRepaintHook()) {
            return;
        }
        DoJaRuntime runtime = DoJaRuntime.current();
        if (runtime != null && runtime.surfaceLock().isHeldByCurrentThread()) {
            return;
        }
        surface.flush(copyImage(surface.image()), false);
    }

    private static int clampOglChannel(float value) {
        if (Float.isNaN(value)) {
            return 0;
        }
        float clamped = Math.max(0.0f, Math.min(1.0f, value));
        return Math.round(clamped * 255.0f);
    }

    private void flushSurfaceFrame() {
        // `Graphics3D.flush()` is the pass boundary for staged opt draws inside one locked frame.
        // Finish any deferred blended primitive batches before ending the shared depth frame.
        flushPending3DPasses();
        if (pendingOptRenderedContent) {
            pendingOptRenderedContent = false;
            // `Graphics3D.flush()` applies the pending render result, but some titles also issue
            // state-only flushes between draw submissions. Only pace flushes that actually follow
            // 3D rendering work. The official emulator exposes a hidden Render3D/frameDuration
            // path, and the only recovered native sync interval is `16000us`, so keep rendered
            // opt passes on that cadence only.
            surface.waitForRenderSync(OPT_RENDER_SYNC_INTERVAL_NANOS);
        }
        DoJaRuntime runtime = DoJaRuntime.current();
        if (runtime != null && runtime.surfaceLock().isHeldByCurrentThread()) {
            // opt.ui.j3d titles can issue multiple flushes inside one locked Canvas frame to
            // separate 3D passes. Do not present mid-frame, but do end the shared z-frame so a
            // later orthographic/translucent pass does not depth-test against the earlier scene.
            surface.endDepthFrame();
            return;
        }
        surface.flush(copyImage(surface.image()), false);
    }

    private void flushPending3DPasses() {
        threeD.flushPendingOptPrimitiveBlends(delegate, surface.image());
    }

    private void prepare3DDepthFrame() {
        // Games can submit 3D assets through separate 3D calls inside one
        // lock/unlock frame. They must share one z-buffer or later props ignore ramp depth.
        pendingOptRenderedContent = true;
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
            case FLIP_ROTATE -> {
                delegate.rotate(Math.toRadians(180), originX + dx + dw / 2.0, originY + dy + dh / 2.0);
            }
            case FLIP_ROTATE_RIGHT -> {
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

    /**
     * Sets clip Rect For3 D.
     */
    @Override
    public void setClipRectFor3D(int x, int y, int width, int height) {
        try {
            threeD.setUiClip(originX + x, originY + y, width, height);
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
            threeD.setUiParallelView(width, height);
            if (TRACE_3D_CALLS) {
                OpenDoJaLog.debug(Graphics.class, () -> "3D call setParallelView width=" + width + " height=" + height);
            }
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
            threeD.setUiPerspectiveView(a, b, c, d);
            if (TRACE_3D_CALLS) {
                OpenDoJaLog.debug(Graphics.class, () -> "3D call setPerspectiveViewWH near=" + a + " far=" + b + " width=" + c + " height=" + d);
            }
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
            threeD.setUiPerspectiveView(a, b, c);
            if (TRACE_3D_CALLS) {
                OpenDoJaLog.debug(Graphics.class, () -> "3D call setPerspectiveViewFov near=" + a + " far=" + b + " angle=" + c);
            }
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
            float[] matrix = transform == null ? Software3DContext.identity() : invokeHidden(transform, "raw", float[].class);
            threeD.setUiTransform(matrix);
            if (TRACE_3D_CALLS) {
                OpenDoJaLog.debug(Graphics.class, () -> "3D call setTransform matrix=" + Arrays.toString(matrix));
            }
        } catch (RuntimeException e) {
            throw traceFailure("setTransform", e);
        }
    }

    /**
     * Adds light.
     */
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

    /**
     * Resets lights.
     */
    @Override
    public void resetLights() {
        threeD.resetUiLights();
    }

    /**
     * Sets fog.
     */
    @Override
    public void setFog(com.nttdocomo.ui.graphics3d.Fog fog) {
        this.uiFog = fog;
        syncUiFogState();
    }

    /**
     * Renders object3 D.
     */
    @Override
    public void renderObject3D(com.nttdocomo.ui.graphics3d.DrawableObject3D object, com.nttdocomo.ui.util3d.Transform transform) {
        if (object == null) {
            return;
        }
        try {
            syncUiFogState();
            renderObject3DRecursive(object, transform == null ? null : matrixOf(transform));
        } catch (RuntimeException e) {
            throw traceFailure("renderObject3D", e);
        }
    }

    private void renderObject3DRecursive(com.nttdocomo.ui.graphics3d.DrawableObject3D object, float[] objectMatrix) {
        if (object == null || object.getType() == com.nttdocomo.ui.graphics3d.Object3D.TYPE_NONE) {
            return;
        }
        if (object instanceof com.nttdocomo.ui.graphics3d.Group group) {
            float[] combined = composeGroupTransform(objectMatrix, group);
            if (TRACE_3D_CALLS) {
                OpenDoJaLog.debug(Graphics.class, () -> "3D call renderObject3D type=Group elements=" + group.getNumElements()
                        + " transform=" + (combined == null ? "null" : Arrays.toString(combined)));
            }
            for (int i = 0; i < group.getNumElements(); i++) {
                com.nttdocomo.ui.graphics3d.Object3D element = group.getElement(i);
                if (element instanceof com.nttdocomo.ui.graphics3d.DrawableObject3D drawable) {
                    renderObject3DRecursive(drawable, combined);
                }
            }
            return;
        }
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
            return;
        }
        if (object instanceof com.nttdocomo.ui.graphics3d.Primitive primitive) {
            SoftwareTexture texture = invokeHidden(primitive, "textureHandle", SoftwareTexture.class);
            prepare3DDepthFrame();
            if (TRACE_3D_CALLS) {
                OpenDoJaLog.debug(Graphics.class, () -> "3D call renderObject3D type=Primitive primitiveType="
                        + primitive.getPrimitiveType()
                        + " count=" + primitive.size()
                        + " transform=" + (objectMatrix == null ? "null" : Arrays.toString(objectMatrix)));
            }
            threeD.renderUiPrimitive(delegate, surface.image(), originX, originY, surface.width(), surface.height(),
                    primitive.getPrimitiveType(), primitive.getPrimitiveParam(), primitive.size(),
                    primitive.getVertexArray(), primitive.getColorArray(), primitive.getTextureCoordArray(), texture,
                    objectMatrix, invokeHiddenInt(object, "blendModeValue"), invokeHiddenFloat(object, "transparencyValue"),
                    invokeHiddenBoolean(primitive, "textureWrapEnabled"),
                    invokeHiddenFloat(primitive, "textureCoordinateTranslateU"),
                    invokeHiddenFloat(primitive, "textureCoordinateTranslateV"));
        }
    }

    private static float[] composeGroupTransform(float[] parentMatrix, com.nttdocomo.ui.graphics3d.Group group) {
        Transform groupTransform = new Transform();
        group.getTransform(groupTransform);
        float[] groupMatrix = matrixOf(groupTransform);
        return parentMatrix == null ? groupMatrix : Software3DContext.multiply(parentMatrix, groupMatrix);
    }

    private static float[] matrixOf(Transform transform) {
        float[] matrix = new float[16];
        for (int i = 0; i < 16; i++) {
            matrix[i] = transform.get(i);
        }
        return matrix;
    }

    /**
     * Sets view Trans.
     */
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

    /**
     * Sets view Trans Array.
     */
    @Override
    public void setViewTransArray(com.nttdocomo.opt.ui.j3d.AffineTrans[] transforms) {
        this.optViewTransforms = transforms == null ? new com.nttdocomo.opt.ui.j3d.AffineTrans[0] : transforms.clone();
    }

    /**
     * Sets view Trans.
     */
    @Override
    public void setViewTrans(int index) {
        setViewTrans(optViewTransforms[index]);
    }

    /**
     * Sets screen Center.
     */
    @Override
    public void setScreenCenter(int x, int y) {
        try {
            if (TRACE_3D_CALLS) {
                OpenDoJaLog.debug(Graphics.class, () -> "3D call setScreenCenter x=" + x + " y=" + y);
            }
            // opt.ui.j3d screen-space state is defined in absolute canvas coordinates rather
            // than inheriting Graphics.setOrigin() like the 2D drawing methods do.
            threeD.setOptScreenCenter(x, y);
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
            if (TRACE_3D_CALLS) {
                OpenDoJaLog.debug(Graphics.class, () -> "3D call setScreenScale x=" + x + " y=" + y);
            }
            threeD.setOptScreenScale(x, y);
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
            if (TRACE_3D_CALLS) {
                OpenDoJaLog.debug(Graphics.class, () -> "3D call setScreenView width=" + x + " height=" + y);
            }
            // DoJa opt `setScreenView()` configures the parallel-projection extent, not a screen-space position.
            threeD.setOptScreenView(x, y);
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
            if (TRACE_3D_CALLS) {
                OpenDoJaLog.debug(Graphics.class, () -> "3D call setPerspective near=" + near + " far=" + far + " width=" + width);
            }
            threeD.setOptPerspective(near, far, width);
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
            if (TRACE_3D_CALLS) {
                OpenDoJaLog.debug(Graphics.class, () -> "3D call setPerspectiveWH near=" + near + " far=" + far + " width=" + width + " height=" + height);
            }
            threeD.setOptPerspective(near, far, width, height);
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
        if (figure == null) {
            return;
        }
        try {
            MascotFigure handle = invokeHidden(figure, "handle", MascotFigure.class);
            prepare3DDepthFrame();
            if (TRACE_3D_CALLS) {
                OpenDoJaLog.debug(Graphics.class, () -> "3D call renderFigure " + describeOptFigure(handle));
            }
            threeD.renderOptFigure(delegate, surface.image(), 0, 0, surface.width(), surface.height(), handle);
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
        this.optSphereMapEnabled = enabled;
        threeD.enableOptSphereMap(enabled);
    }

    /**
     * Sets sphere Texture.
     */
    @Override
    public void setSphereTexture(com.nttdocomo.opt.ui.j3d.Texture texture) {
        if (texture == null) {
            throw new NullPointerException("texture");
        }
        SoftwareTexture handle = invokeHidden(texture, "handle", SoftwareTexture.class);
        if (!handle.sphereMap()) {
            throw new IllegalArgumentException("texture must be an environment-mapping texture");
        }
        this.optSphereTexture = handle;
        threeD.setOptSphereTexture(handle);
    }

    /**
     * Enables or disables light.
     */
    @Override
    public void enableLight(boolean enabled) {
        if (TRACE_3D_CALLS) {
            OpenDoJaLog.debug(Graphics.class, () -> "3D call enableLight enabled=" + enabled);
        }
        threeD.enableOptLight(enabled);
    }

    /**
     * Sets ambient Light.
     */
    @Override
    public void setAmbientLight(int color) {
        if (TRACE_3D_CALLS) {
            OpenDoJaLog.debug(Graphics.class, () -> "3D call setAmbientLight value=" + color);
        }
    }

    /**
     * Sets direction Light.
     */
    @Override
    public void setDirectionLight(com.nttdocomo.opt.ui.j3d.Vector3D direction, int color) {
        if (TRACE_3D_CALLS) {
            OpenDoJaLog.debug(Graphics.class, () -> "3D call setDirectionLight dir=("
                    + (direction == null ? 0 : direction.x) + ","
                    + (direction == null ? 0 : direction.y) + ","
                    + (direction == null ? 0 : direction.z) + ") value=" + color);
        }
    }

    /**
     * Enables or disables semi Transparent.
     */
    @Override
    public void enableSemiTransparent(boolean enabled) {
        threeD.enableOptSemiTransparent(enabled);
    }

    /**
     * Sets clip Rect3 D.
     */
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

    /**
     * Enables or disables toon Shader.
     */
    @Override
    public void enableToonShader(boolean enabled) {
        this.optToonShaderEnabled = enabled;
        threeD.enableOptToonShader(enabled);
    }

    /**
     * Sets toon Param.
     */
    @Override
    public void setToonParam(int highlight, int mid, int shadow) {
        validateToonParameter(highlight, "highlight");
        validateToonParameter(mid, "mid");
        validateToonParameter(shadow, "shadow");
        this.optToonThreshold = highlight;
        this.optToonMid = mid;
        this.optToonShadow = shadow;
        threeD.setOptToonShader(highlight, mid, shadow);
    }

    /**
     * Sets primitive Texture Array.
     */
    @Override
    public void setPrimitiveTextureArray(com.nttdocomo.opt.ui.j3d.Texture texture) {
        if (TRACE_3D_CALLS) {
            SoftwareTexture handle = texture == null ? null : invokeHidden(texture, "handle", SoftwareTexture.class);
            OpenDoJaLog.debug(Graphics.class, () -> "3D call setPrimitiveTextureArray single texture=" + describeTexture(handle));
        }
        threeD.setPrimitiveTextures(texture == null ? null : new SoftwareTexture[]{invokeHidden(texture, "handle", SoftwareTexture.class)});
    }

    /**
     * Sets primitive Texture Array.
     */
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

    /**
     * Sets primitive Texture.
     */
    @Override
    public void setPrimitiveTexture(int index) {
        if (TRACE_3D_CALLS) {
            OpenDoJaLog.debug(Graphics.class, () -> "3D call setPrimitiveTexture index=" + index);
        }
        threeD.setPrimitiveTexture(index);
    }

    /**
     * Renders primitives.
     */
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
        threeD.renderOptPrimitives(delegate, surface.image(), 0, 0, surface.width(), surface.height(), primitives, attr);
    }

    /**
     * Renders primitives.
     */
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
        threeD.renderOptPrimitivesRange(delegate, surface.image(), 0, 0, surface.width(), surface.height(), primitives, start, count, attr);
    }

    /**
     * Executes command List.
     */
    @Override
    public void executeCommandList(int[] commands) {
        if (commands == null) {
            throw new NullPointerException("commands");
        }
        if (commands.length == 0 || !isSupportedOptCommandListVersion(commands[0])) {
            throw new IllegalArgumentException("Unsupported command list version: "
                    + (commands.length == 0 ? "<empty>" : Integer.toString(commands[0])));
        }
        for (int i = 1; i < commands.length; ) {
            int command = commands[i];
            if (command == com.nttdocomo.opt.ui.j3d.Graphics3D.COMMAND_END) {
                return;
            }
            i++;
            switch (command & OPT_COMMAND_PREFIX_MASK) {
                case com.nttdocomo.opt.ui.j3d.Graphics3D.COMMAND_NOP -> i = skipOptCommandOperands(commands, i, command & OPT_COMMAND_INLINE_VALUE_MASK);
                case com.nttdocomo.opt.ui.j3d.Graphics3D.COMMAND_FLUSH -> flush();
                case com.nttdocomo.opt.ui.j3d.Graphics3D.COMMAND_ATTRIBUTE -> applyOptCommandAttributes(command & OPT_COMMAND_INLINE_VALUE_MASK);
                case com.nttdocomo.opt.ui.j3d.Graphics3D.COMMAND_CLIP_RECT -> {
                    ensureOptCommandOperands(commands, i, 4, "COMMAND_CLIP_RECT");
                    int left = commands[i++];
                    int top = commands[i++];
                    int right = commands[i++];
                    int bottom = commands[i++];
                    setClipRect3D(left, top, java.lang.Math.max(0, right - left), java.lang.Math.max(0, bottom - top));
                }
                case com.nttdocomo.opt.ui.j3d.Graphics3D.COMMAND_SCREEN_CENTER -> {
                    ensureOptCommandOperands(commands, i, 2, "COMMAND_SCREEN_CENTER");
                    setScreenCenter(commands[i++], commands[i++]);
                }
                case com.nttdocomo.opt.ui.j3d.Graphics3D.COMMAND_TEXTURE -> setPrimitiveTexture(command & OPT_COMMAND_INLINE_VALUE_MASK);
                case com.nttdocomo.opt.ui.j3d.Graphics3D.COMMAND_VIEW_TRANS -> setViewTrans(command & OPT_COMMAND_INLINE_VALUE_MASK);
                case com.nttdocomo.opt.ui.j3d.Graphics3D.COMMAND_SCREEN_SCALE -> {
                    ensureOptCommandOperands(commands, i, 2, "COMMAND_SCREEN_SCALE");
                    setScreenScale(commands[i++], commands[i++]);
                }
                case com.nttdocomo.opt.ui.j3d.Graphics3D.COMMAND_SCREEN_VIEW -> {
                    ensureOptCommandOperands(commands, i, 2, "COMMAND_SCREEN_VIEW");
                    setScreenView(commands[i++], commands[i++]);
                }
                case com.nttdocomo.opt.ui.j3d.Graphics3D.COMMAND_PERSPECTIVE1 -> {
                    ensureOptCommandOperands(commands, i, 3, "COMMAND_PERSPECTIVE1");
                    setPerspective(commands[i++], commands[i++], commands[i++]);
                }
                case com.nttdocomo.opt.ui.j3d.Graphics3D.COMMAND_PERSPECTIVE2 -> {
                    ensureOptCommandOperands(commands, i, 4, "COMMAND_PERSPECTIVE2");
                    setPerspective(commands[i++], commands[i++], commands[i++], commands[i++]);
                }
                case com.nttdocomo.opt.ui.j3d.Graphics3D.COMMAND_AMBIENT_LIGHT -> {
                    ensureOptCommandOperands(commands, i, 1, "COMMAND_AMBIENT_LIGHT");
                    setAmbientLight(commands[i++]);
                }
                case com.nttdocomo.opt.ui.j3d.Graphics3D.COMMAND_DIRECTION_LIGHT -> {
                    ensureOptCommandOperands(commands, i, 4, "COMMAND_DIRECTION_LIGHT");
                    setDirectionLight(new com.nttdocomo.opt.ui.j3d.Vector3D(commands[i++], commands[i++], commands[i++]), commands[i++]);
                }
                case com.nttdocomo.opt.ui.j3d.Graphics3D.COMMAND_TOON_PARAM -> {
                    ensureOptCommandOperands(commands, i, 3, "COMMAND_TOON_PARAM");
                    setToonParam(commands[i++], commands[i++], commands[i++]);
                }
                case com.nttdocomo.opt.ui.j3d.Graphics3D.COMMAND_RENDER_POINTS,
                        com.nttdocomo.opt.ui.j3d.Graphics3D.COMMAND_RENDER_LINES,
                        com.nttdocomo.opt.ui.j3d.Graphics3D.COMMAND_RENDER_TRIANGLES,
                        com.nttdocomo.opt.ui.j3d.Graphics3D.COMMAND_RENDER_QUADS,
                        com.nttdocomo.opt.ui.j3d.Graphics3D.COMMAND_RENDER_POINT_SPRITES -> i = renderOptCommandPrimitive(commands, i, command);
                default -> throw new IllegalArgumentException("Unsupported opt command: " + command);
            }
        }
    }

    private static boolean isSupportedOptCommandListVersion(int version) {
        // Some older handset stacks write the v1 command-list header as a plain literal `1`
        // instead of the later packed constant `0xfe000001`.
        return version == com.nttdocomo.opt.ui.j3d.Graphics3D.COMMAND_LIST_VERSION_1
                || version == LEGACY_OPT_COMMAND_LIST_VERSION_1;
    }

    private int skipOptCommandOperands(int[] commands, int index, int count) {
        ensureOptCommandOperands(commands, index, count, "COMMAND_NOP");
        return index + count;
    }

    private void applyOptCommandAttributes(int attributes) {
        threeD.enableOptLight((attributes & com.nttdocomo.opt.ui.j3d.Graphics3D.ENV_ATTR_LIGHT) != 0);
        threeD.enableOptSemiTransparent((attributes & com.nttdocomo.opt.ui.j3d.Graphics3D.ENV_ATTR_SEMI_TRANSPARENT) != 0);
        enableSphereMap((attributes & com.nttdocomo.opt.ui.j3d.Graphics3D.ENV_ATTR_SPHERE_MAP) != 0);
        enableToonShader((attributes & com.nttdocomo.opt.ui.j3d.Graphics3D.ENV_ATTR_TOON_SHADER) != 0);
    }

    private int renderOptCommandPrimitive(int[] commands, int index, int command) {
        int primitiveType = switch (command & OPT_COMMAND_PREFIX_MASK) {
            case com.nttdocomo.opt.ui.j3d.Graphics3D.COMMAND_RENDER_POINTS -> com.nttdocomo.opt.ui.j3d.Graphics3D.PRIMITIVE_POINTS;
            case com.nttdocomo.opt.ui.j3d.Graphics3D.COMMAND_RENDER_LINES -> com.nttdocomo.opt.ui.j3d.Graphics3D.PRIMITIVE_LINES;
            case com.nttdocomo.opt.ui.j3d.Graphics3D.COMMAND_RENDER_TRIANGLES -> com.nttdocomo.opt.ui.j3d.Graphics3D.PRIMITIVE_TRIANGLES;
            case com.nttdocomo.opt.ui.j3d.Graphics3D.COMMAND_RENDER_QUADS -> com.nttdocomo.opt.ui.j3d.Graphics3D.PRIMITIVE_QUADS;
            case com.nttdocomo.opt.ui.j3d.Graphics3D.COMMAND_RENDER_POINT_SPRITES -> com.nttdocomo.opt.ui.j3d.Graphics3D.PRIMITIVE_POINT_SPRITES;
            default -> throw new IllegalArgumentException("Unsupported primitive command: " + command);
        };
        int primitiveCount = (command & OPT_COMMAND_RENDER_COUNT_MASK) >>> 16;
        if (primitiveCount <= 0) {
            throw new IllegalArgumentException("Invalid primitive count: " + primitiveCount);
        }
        PrimitiveArray primitives = new PrimitiveArray(primitiveType, extractOptPrimitiveParam(command, primitiveType), primitiveCount);
        // Command-list primitive payloads follow the PrimitiveArray storage order:
        // vertices, normals, texture/point-sprite data, then colors.
        index = copyOptCommandPayload(commands, index, primitives.getVertexArray(), "vertex");
        index = copyOptCommandPayload(commands, index, primitives.getNormalArray(), "normal");
        index = copyOptCommandPayload(commands, index, primitives.getTextureCoordArray(), "texture");
        index = copyOptCommandPayload(commands, index, primitives.getPointSpriteArray(), "pointSprite");
        index = copyOptCommandPayload(commands, index, primitives.getColorArray(), "color");
        renderPrimitives(primitives, command & OPT_COMMAND_ATTR_MASK);
        return index;
    }

    private static int extractOptPrimitiveParam(int command, int primitiveType) {
        int sharedParam = command & (com.nttdocomo.opt.ui.j3d.Graphics3D.NORMAL_PER_FACE
                | com.nttdocomo.opt.ui.j3d.Graphics3D.NORMAL_PER_VERTEX
                | com.nttdocomo.opt.ui.j3d.Graphics3D.COLOR_PER_COMMAND
                | com.nttdocomo.opt.ui.j3d.Graphics3D.COLOR_PER_FACE
                | com.nttdocomo.opt.ui.j3d.Graphics3D.TEXTURE_COORD_PER_VERTEX);
        if (primitiveType != com.nttdocomo.opt.ui.j3d.Graphics3D.PRIMITIVE_POINT_SPRITES) {
            return sharedParam;
        }
        return sharedParam | (command & (com.nttdocomo.opt.ui.j3d.Graphics3D.POINT_SPRITE_FLAG_PIXEL_SIZE
                | com.nttdocomo.opt.ui.j3d.Graphics3D.POINT_SPRITE_FLAG_NO_PERSPECTIVE));
    }

    private static int copyOptCommandPayload(int[] commands, int index, int[] target, String label) {
        if (target == null || target.length == 0) {
            return index;
        }
        ensureOptCommandOperands(commands, index, target.length, label);
        System.arraycopy(commands, index, target, 0, target.length);
        return index + target.length;
    }

    private static void ensureOptCommandOperands(int[] commands, int index, int count, String label) {
        if (commands.length - index < count) {
            throw new IllegalArgumentException("Truncated " + label + " payload");
        }
    }

    private void loadMatrix(int mode, float[] matrix) {
        switch (mode) {
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_MODELVIEW -> {
                ogl.modelViewMatrix = matrix;
                ogl.standardModelViewConfigured = true;
            }
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_PROJECTION -> {
                ogl.projectionMatrix = matrix;
                ogl.standardProjectionConfigured = true;
            }
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_TEXTURE -> ogl.textureMatrix = matrix;
            case 1 -> ogl.acrodeaWorldMatrix = matrix;
            case 2 -> ogl.acrodeaCameraMatrix = matrix;
            default -> ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_ENUM;
        }
    }

    private void multiplyCurrentMatrix(float[] matrix) {
        switch (ogl.matrixMode) {
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_MODELVIEW -> {
                ogl.modelViewMatrix = multiplyMatrices(ogl.modelViewMatrix, matrix);
                ogl.standardModelViewConfigured = true;
            }
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_PROJECTION -> {
                ogl.projectionMatrix = multiplyMatrices(ogl.projectionMatrix, matrix);
                ogl.standardProjectionConfigured = true;
            }
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_TEXTURE ->
                    ogl.textureMatrix = multiplyMatrices(ogl.textureMatrix, matrix);
            case 1 -> ogl.acrodeaWorldMatrix = multiplyMatrices(ogl.acrodeaWorldMatrix, matrix);
            case 2 -> ogl.acrodeaCameraMatrix = multiplyMatrices(
                    ogl.acrodeaCameraMatrix == null ? OglState.identityMatrix() : ogl.acrodeaCameraMatrix,
                    matrix);
            default -> ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_ENUM;
        }
    }

    private static float[] multiplyMatrices(float[] left, float[] right) {
        float[] product = new float[16];
        for (int column = 0; column < 4; column++) {
            int columnOffset = column * 4;
            for (int row = 0; row < 4; row++) {
                product[columnOffset + row] =
                        left[row] * right[columnOffset]
                                + left[row + 4] * right[columnOffset + 1]
                                + left[row + 8] * right[columnOffset + 2]
                                + left[row + 12] * right[columnOffset + 3];
            }
        }
        return product;
    }

    private void drawOgl(int mode, int first, int count, DirectBuffer elementIndices) {
        if (ogl.vertexPointer == null || ogl.vertexPointer.pointer() == null || count <= 0) {
            return;
        }
        ShortBuffer indexBuffer = null;
        int primitiveCount = count;
        if (elementIndices != null) {
            if (!(elementIndices instanceof ShortBuffer shortBuffer)) {
                ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_VALUE;
                return;
            }
            indexBuffer = shortBuffer;
            primitiveCount = Math.min(Math.max(0, count), DirectBufferFactory.getSegmentLength(shortBuffer));
        }
        Rectangle clip = delegate.getClipBounds();
        BufferedImage target = surface.image();
        int[] pixels = ((DataBufferInt) target.getRaster().getDataBuffer()).getData();
        float[] depthBuffer = surface.depthBufferForFrame();
        RasterVertex scratch0 = new RasterVertex();
        RasterVertex scratch1 = new RasterVertex();
        RasterVertex scratch2 = new RasterVertex();
        ClipVector clip0 = new ClipVector();
        ClipVector clip1 = new ClipVector();
        switch (mode) {
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_TRIANGLES -> {
                for (int i = 0; i + 2 < primitiveCount; i += 3) {
                    if (!populateRasterVertex(scratch0, clip0, resolveVertexIndex(first, indexBuffer, i))
                            || !populateRasterVertex(scratch1, clip1, resolveVertexIndex(first, indexBuffer, i + 1))
                            || !populateRasterVertex(scratch2, clip0, resolveVertexIndex(first, indexBuffer, i + 2))) {
                        continue;
                    }
                    drawRasterTriangle(scratch0, scratch1, scratch2, pixels, depthBuffer, clip, target.getWidth(), target.getHeight());
                }
            }
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_TRIANGLE_STRIP -> {
                if (primitiveCount < 3) {
                    return;
                }
                if (!populateRasterVertex(scratch0, clip0, resolveVertexIndex(first, indexBuffer, 0))
                        || !populateRasterVertex(scratch1, clip1, resolveVertexIndex(first, indexBuffer, 1))) {
                    return;
                }
                RasterVertex previous0 = scratch0;
                RasterVertex previous1 = scratch1;
                RasterVertex next = scratch2;
                ClipVector nextClip = clip0;
                for (int i = 2; i < primitiveCount; i++) {
                    if (!populateRasterVertex(next, nextClip, resolveVertexIndex(first, indexBuffer, i))) {
                        continue;
                    }
                    RasterVertex v0 = previous0;
                    RasterVertex v1 = previous1;
                    RasterVertex v2 = next;
                    if ((i & 1) != 0) {
                        RasterVertex swap = v1;
                        v1 = v0;
                        v0 = swap;
                    }
                    drawRasterTriangle(v0, v1, v2, pixels, depthBuffer, clip, target.getWidth(), target.getHeight());
                    previous0 = previous1;
                    previous1 = next;
                    next = v0;
                    nextClip = next == scratch0 ? clip0 : clip1;
                }
            }
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_LINE_LOOP ->
                    drawLineLoop(first, primitiveCount, indexBuffer, clip, target.getWidth(), target.getHeight(),
                            scratch0, scratch1, scratch2, clip0, clip1);
            default -> ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_ENUM;
        }
    }

    private int resolveVertexIndex(int first, ShortBuffer elementIndices, int primitiveIndex) {
        if (elementIndices == null) {
            return first + primitiveIndex;
        }
        return DirectBufferFactory.getShort(elementIndices,
                DirectBufferFactory.getSegmentOffset(elementIndices) + primitiveIndex) & 0xFFFF;
    }

    private boolean populateRasterVertex(RasterVertex targetVertex, ClipVector clipVector, int vertexIndex) {
        OglPointer vertexPointer = ogl.vertexPointer;
        if (vertexPointer == null || vertexPointer.pointer() == null) {
            return false;
        }
        if (!(vertexPointer.pointer() instanceof FloatBuffer floatBuffer)) {
            ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_VALUE;
            return false;
        }
        if (vertexPointer.type() != com.nttdocomo.ui.ogl.GraphicsOGL.GL_FLOAT) {
            ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_ENUM;
            return false;
        }
        int positionSize = Math.max(1, vertexPointer.size());
        int positionStrideFloats = vertexPointer.stride() > 0 ? Math.max(positionSize, vertexPointer.stride() / 4) : positionSize;
        int positionBase = DirectBufferFactory.getSegmentOffset(floatBuffer) + (vertexIndex * positionStrideFloats);
        if (positionBase < 0 || positionBase + positionSize > floatBuffer.length()) {
            ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_VALUE;
            return false;
        }
        float x = DirectBufferFactory.getFloat(floatBuffer, positionBase);
        float y = positionSize > 1 ? DirectBufferFactory.getFloat(floatBuffer, positionBase + 1) : 0f;
        float z = positionSize > 2 ? DirectBufferFactory.getFloat(floatBuffer, positionBase + 2) : 0f;
        float u = 0f;
        float v = 0f;
        OglPointer texCoordPointer = ogl.texCoordPointer;
        if (texCoordPointer != null && texCoordPointer.pointer() != null) {
            if (!(texCoordPointer.pointer() instanceof FloatBuffer texFloatBuffer)) {
                ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_VALUE;
                return false;
            }
            if (texCoordPointer.type() != com.nttdocomo.ui.ogl.GraphicsOGL.GL_FLOAT) {
                ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_ENUM;
                return false;
            }
            int texSize = Math.max(1, texCoordPointer.size());
            int texStrideFloats = texCoordPointer.stride() > 0 ? Math.max(texSize, texCoordPointer.stride() / 4) : texSize;
            int texBase = DirectBufferFactory.getSegmentOffset(texFloatBuffer) + (vertexIndex * texStrideFloats);
            if (texBase < 0 || texBase + texSize > texFloatBuffer.length()) {
                ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_VALUE;
                return false;
            }
            u = DirectBufferFactory.getFloat(texFloatBuffer, texBase);
            if (texSize > 1) {
                v = DirectBufferFactory.getFloat(texFloatBuffer, texBase + 1);
            }
        }
        boolean useAcrodeaMatrices = positionSize >= 3 && ogl.usesAcrodeaMatrices();
        transformVertex(clipVector, x, y, z, useAcrodeaMatrices);
        float w = Math.abs(clipVector.w) < 0.000001f ? (clipVector.w < 0f ? -0.000001f : 0.000001f) : clipVector.w;
        float ndcX = clipVector.x / w;
        float ndcY = clipVector.y / w;
        float ndcZ = clipVector.z / w;
        float windowDepth = ogl.depthRangeNear + (((ndcZ + 1f) * 0.5f) * (ogl.depthRangeFar - ogl.depthRangeNear));
        float depth = 1f - windowDepth;
        float reciprocalW = 1f / Math.max(0.000001f, Math.abs(w));
        targetVertex.set(
                ((ndcX + 1f) * 0.5f) * surface.width(),
                ((1f - ndcY) * 0.5f) * surface.height(),
                depth,
                reciprocalW,
                u,
                v
        );
        return true;
    }

    private void transformVertex(ClipVector target, float x, float y, float z, boolean useAcrodeaMatrices) {
        if (useAcrodeaMatrices) {
            multiply(clipVectorTemp, ogl.acrodeaWorldMatrix, x, y, z, 1f);
            multiply(target, ogl.acrodeaCameraMatrix, clipVectorTemp.x, clipVectorTemp.y, clipVectorTemp.z, clipVectorTemp.w);
            return;
        }
        multiply(clipVectorTemp, ogl.modelViewMatrix, x, y, z, 1f);
        multiply(target, ogl.projectionMatrix, clipVectorTemp.x, clipVectorTemp.y, clipVectorTemp.z, clipVectorTemp.w);
    }

    private void drawRasterTriangle(RasterVertex v0, RasterVertex v1, RasterVertex v2, int[] pixels, float[] depthBuffer,
                                    Rectangle clip, int width, int height) {
        if (isCulled(v0, v1, v2)) {
            return;
        }
        float area = edge(v0.x, v0.y, v1.x, v1.y, v2.x, v2.y);
        if (Math.abs(area) < 0.0001f) {
            return;
        }
        int minX = clamp((int) Math.floor(Math.min(v0.x, Math.min(v1.x, v2.x))), 0, width - 1);
        int maxX = clamp((int) Math.ceil(Math.max(v0.x, Math.max(v1.x, v2.x))), 0, width - 1);
        int minY = clamp((int) Math.floor(Math.min(v0.y, Math.min(v1.y, v2.y))), 0, height - 1);
        int maxY = clamp((int) Math.ceil(Math.max(v0.y, Math.max(v1.y, v2.y))), 0, height - 1);
        if (clip != null) {
            minX = Math.max(minX, clip.x);
            minY = Math.max(minY, clip.y);
            maxX = Math.min(maxX, clip.x + clip.width - 1);
            maxY = Math.min(maxY, clip.y + clip.height - 1);
        }
        if (minX > maxX || minY > maxY) {
            return;
        }
        float inverseArea = 1f / area;
        float reciprocalW0 = v0.reciprocalW;
        float reciprocalW1 = v1.reciprocalW;
        float reciprocalW2 = v2.reciprocalW;
        for (int y = minY; y <= maxY; y++) {
            float sampleY = y + 0.5f;
            for (int x = minX; x <= maxX; x++) {
                float sampleX = x + 0.5f;
                float w0 = edge(v1.x, v1.y, v2.x, v2.y, sampleX, sampleY) * inverseArea;
                float w1 = edge(v2.x, v2.y, v0.x, v0.y, sampleX, sampleY) * inverseArea;
                float w2 = edge(v0.x, v0.y, v1.x, v1.y, sampleX, sampleY) * inverseArea;
                if (w0 < 0f || w1 < 0f || w2 < 0f) {
                    continue;
                }
                float depth = (w0 * v0.depth) + (w1 * v1.depth) + (w2 * v2.depth);
                int offset = (y * width) + x;
                if (!passesDepth(depth, depthBuffer[offset])) {
                    continue;
                }
                float u;
                float v;
                if (ogl.textureEnabled() && ogl.texCoordPointer != null) {
                    float denominator = Math.max(0.000001f,
                            (w0 * reciprocalW0) + (w1 * reciprocalW1) + (w2 * reciprocalW2));
                    u = ((w0 * v0.u * reciprocalW0) + (w1 * v1.u * reciprocalW1) + (w2 * v2.u * reciprocalW2)) / denominator;
                    v = ((w0 * v0.v * reciprocalW0) + (w1 * v1.v * reciprocalW1) + (w2 * v2.v * reciprocalW2)) / denominator;
                } else {
                    u = 0f;
                    v = 0f;
                }
                int source = ogl.textureEnabled() ? ogl.sampleBoundTexture(u, v) : 0xFFFFFFFF;
                int tinted = multiplyColor(source, ogl.color);
                if (!passesAlphaTest(tinted)) {
                    continue;
                }
                int blended = ogl.blendEnabled() ? blend(tinted, pixels[offset], ogl.blendSrcFactor, ogl.blendDstFactor) : tinted;
                pixels[offset] = blended;
                if (ogl.depthMask && ogl.depthEnabled()) {
                    depthBuffer[offset] = depth;
                }
            }
        }
    }

    private void drawLineLoop(int first, int primitiveCount, ShortBuffer elementIndices, Rectangle clip, int width, int height,
                              RasterVertex firstVertex, RasterVertex previousVertex, RasterVertex currentVertex,
                              ClipVector clip0, ClipVector clip1) {
        if (primitiveCount < 2) {
            return;
        }
        if (!populateRasterVertex(firstVertex, clip0, resolveVertexIndex(first, elementIndices, 0))) {
            return;
        }
        previousVertex.copyFrom(firstVertex);
        Color old = delegate.getColor();
        Shape oldClip = delegate.getClip();
        delegate.setColor(new Color(ogl.color, true));
        if (clip != null) {
            delegate.setClip(clip);
        }
        try {
            for (int i = 1; i < primitiveCount; i++) {
                if (!populateRasterVertex(currentVertex, clip1, resolveVertexIndex(first, elementIndices, i))) {
                    continue;
                }
                delegate.drawLine(clamp(Math.round(previousVertex.x), 0, width - 1),
                        clamp(Math.round(previousVertex.y), 0, height - 1),
                        clamp(Math.round(currentVertex.x), 0, width - 1),
                        clamp(Math.round(currentVertex.y), 0, height - 1));
                RasterVertex swap = previousVertex;
                previousVertex = currentVertex;
                currentVertex = swap;
            }
            delegate.drawLine(clamp(Math.round(previousVertex.x), 0, width - 1),
                    clamp(Math.round(previousVertex.y), 0, height - 1),
                    clamp(Math.round(firstVertex.x), 0, width - 1),
                    clamp(Math.round(firstVertex.y), 0, height - 1));
        } finally {
            delegate.setColor(old);
            delegate.setClip(oldClip);
        }
    }

    private boolean isCulled(RasterVertex v0, RasterVertex v1, RasterVertex v2) {
        if (!ogl.enabledCaps.contains(com.nttdocomo.ui.ogl.GraphicsOGL.GL_CULL_FACE)) {
            return false;
        }
        float area = edge(v0.x, v0.y, v1.x, v1.y, v2.x, v2.y);
        // `edge(...)` is the negated 2D cross-product form, so after mapping into
        // top-left screen coordinates CCW window winding corresponds to a positive area.
        boolean ccw = area > 0f;
        boolean frontFacing = ogl.frontFace == com.nttdocomo.ui.ogl.GraphicsOGL.GL_CCW ? ccw : !ccw;
        return switch (ogl.cullFace) {
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_FRONT -> frontFacing;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_FRONT_AND_BACK -> true;
            default -> !frontFacing;
        };
    }

    private boolean passesDepth(float incoming, float existing) {
        if (!ogl.depthEnabled()) {
            return true;
        }
        return switch (ogl.depthFunc) {
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_LESS -> incoming > existing + 0.00001f;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_LEQUAL -> incoming >= existing - 0.00001f;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_EQUAL -> Math.abs(incoming - existing) <= 0.00001f;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_GREATER -> incoming < existing - 0.00001f;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_GEQUAL -> incoming <= existing + 0.00001f;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_ALWAYS -> true;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_NEVER -> false;
            default -> incoming > existing + 0.00001f;
        };
    }

    private boolean passesAlphaTest(int color) {
        if (!ogl.enabledCaps.contains(com.nttdocomo.ui.ogl.GraphicsOGL.GL_ALPHA_TEST)) {
            return true;
        }
        float alpha = ((color >>> 24) & 0xFF) / 255f;
        return switch (ogl.alphaFunc) {
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_NOTEQUAL -> Math.abs(alpha - ogl.alphaRef) > 0.0001f;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_GREATER -> alpha > ogl.alphaRef;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_GEQUAL -> alpha >= ogl.alphaRef;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_EQUAL -> Math.abs(alpha - ogl.alphaRef) <= 0.0001f;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_LESS -> alpha < ogl.alphaRef;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_LEQUAL -> alpha <= ogl.alphaRef;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_ALWAYS -> true;
            default -> true;
        };
    }

    private static void multiply(ClipVector out, float[] matrix, float x, float y, float z, float w) {
        if (matrix == null) {
            out.set(x, y, z, w);
            return;
        }
        out.set(
                matrix[0] * x + matrix[4] * y + matrix[8] * z + matrix[12] * w,
                matrix[1] * x + matrix[5] * y + matrix[9] * z + matrix[13] * w,
                matrix[2] * x + matrix[6] * y + matrix[10] * z + matrix[14] * w,
                matrix[3] * x + matrix[7] * y + matrix[11] * z + matrix[15] * w
        );
    }

    private static int multiplyColor(int source, int tint) {
        int sourceA = (source >>> 24) & 0xFF;
        int sourceR = (source >>> 16) & 0xFF;
        int sourceG = (source >>> 8) & 0xFF;
        int sourceB = source & 0xFF;
        int tintA = (tint >>> 24) & 0xFF;
        int tintR = (tint >>> 16) & 0xFF;
        int tintG = (tint >>> 8) & 0xFF;
        int tintB = tint & 0xFF;
        return ((sourceA * tintA) / 255 << 24)
                | ((sourceR * tintR) / 255 << 16)
                | ((sourceG * tintG) / 255 << 8)
                | ((sourceB * tintB) / 255);
    }

    private static int blend(int source, int destination, int srcFactor, int dstFactor) {
        int sourceA = (source >>> 24) & 0xFF;
        int destinationA = (destination >>> 24) & 0xFF;
        int srcWeight = factorWeight(srcFactor, sourceA, destinationA);
        int dstWeight = factorWeight(dstFactor, sourceA, destinationA);
        int outA = clamp(((sourceA * srcWeight) + (destinationA * dstWeight)) / 255, 0, 255);
        int outR = clamp(((((source >>> 16) & 0xFF) * srcWeight) + (((destination >>> 16) & 0xFF) * dstWeight)) / 255, 0, 255);
        int outG = clamp(((((source >>> 8) & 0xFF) * srcWeight) + (((destination >>> 8) & 0xFF) * dstWeight)) / 255, 0, 255);
        int outB = clamp((((source & 0xFF) * srcWeight) + ((destination & 0xFF) * dstWeight)) / 255, 0, 255);
        return (outA << 24) | (outR << 16) | (outG << 8) | outB;
    }

    private static int factorWeight(int factor, int sourceA, int destinationA) {
        return switch (factor) {
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_ZERO -> 0;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_ONE -> 255;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_SRC_ALPHA -> sourceA;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_ONE_MINUS_SRC_ALPHA -> 255 - sourceA;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_DST_ALPHA -> destinationA;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_ONE_MINUS_DST_ALPHA -> 255 - destinationA;
            default -> 255;
        };
    }

    private static float edge(float ax, float ay, float bx, float by, float px, float py) {
        return ((px - ax) * (by - ay)) - ((py - ay) * (bx - ax));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
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

    private static boolean invokeHiddenBoolean(Object target, String methodName) {
        return invokeHidden(target, methodName, Boolean.class);
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

    private void syncUiFogState() {
        if (uiFog == null) {
            threeD.setUiFog(null, 0f, 0f, 0f, 0);
            return;
        }
        threeD.setUiFog(
                invokeHidden(uiFog, "mode", Integer.class),
                invokeHidden(uiFog, "linearNear", Float.class),
                invokeHidden(uiFog, "linearFar", Float.class),
                invokeHidden(uiFog, "density", Float.class),
                invokeHidden(uiFog, "color", Integer.class)
        );
    }

    private void syncOptRendererState() {
        threeD.enableOptSphereMap(optSphereMapEnabled);
        threeD.setOptSphereTexture(optSphereTexture);
        threeD.enableOptToonShader(optToonShaderEnabled);
        threeD.setOptToonShader(optToonThreshold, optToonMid, optToonShadow);
    }

    private static void validateToonParameter(int value, String name) {
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException(name + " must be in [0,255]: " + value);
        }
    }

    private static final class RasterVertex {
        float x;
        float y;
        float depth;
        float reciprocalW;
        float u;
        float v;

        void set(float x, float y, float depth, float reciprocalW, float u, float v) {
            this.x = x;
            this.y = y;
            this.depth = depth;
            this.reciprocalW = reciprocalW;
            this.u = u;
            this.v = v;
        }

        void copyFrom(RasterVertex other) {
            set(other.x, other.y, other.depth, other.reciprocalW, other.u, other.v);
        }
    }

    private static final class ClipVector {
        float x;
        float y;
        float z;
        float w;

        void set(float x, float y, float z, float w) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.w = w;
        }
    }

    private record OglPointer(int size, int type, int stride, DirectBuffer pointer) {
    }

    private static final class OglTexture {
        private int width;
        private int height;
        private int[] pixels = new int[0];
        private int minFilter = com.nttdocomo.ui.ogl.GraphicsOGL.GL_NEAREST;
        private int magFilter = com.nttdocomo.ui.ogl.GraphicsOGL.GL_NEAREST;
        private int wrapS = com.nttdocomo.ui.ogl.GraphicsOGL.GL_REPEAT;
        private int wrapT = com.nttdocomo.ui.ogl.GraphicsOGL.GL_REPEAT;

        void setParameter(int pname, int value) {
            switch (pname) {
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_TEXTURE_MIN_FILTER -> minFilter = value;
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_TEXTURE_MAG_FILTER -> magFilter = value;
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_TEXTURE_WRAP_S -> wrapS = value;
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_TEXTURE_WRAP_T -> wrapT = value;
                default -> {
                }
            }
        }

        void loadCompressed(int internalFormat, int width, int height, byte[] raw) {
            this.width = Math.max(1, width);
            this.height = Math.max(1, height);
            int paletteEntries = internalFormat == com.nttdocomo.ui.ogl.GraphicsOGL.GL_PALETTE4_RGB5_A1_OES ? 16 : 256;
            int bitsPerIndex = paletteEntries == 16 ? 4 : 8;
            int[] palette = new int[paletteEntries];
            for (int i = 0; i < paletteEntries; i++) {
                int offset = i * 2;
                int value = (raw[offset] & 0xFF) | ((raw[offset + 1] & 0xFF) << 8);
                palette[i] = decodeRgb5a1(value);
            }
            int paletteBytes = paletteEntries * 2;
            this.pixels = new int[this.width * this.height];
            if (bitsPerIndex == 8) {
                for (int i = 0; i < this.pixels.length && paletteBytes + i < raw.length; i++) {
                    this.pixels[i] = palette[raw[paletteBytes + i] & 0xFF];
                }
                return;
            }
            for (int i = 0; i < this.pixels.length; i++) {
                int packed = raw[paletteBytes + (i >> 1)] & 0xFF;
                int index = (i & 1) == 0 ? (packed & 0x0F) : ((packed >>> 4) & 0x0F);
                this.pixels[i] = palette[index];
            }
        }

        boolean loadUncompressed(int width, int height, int format, int type, DirectBuffer source) {
            this.width = Math.max(1, width);
            this.height = Math.max(1, height);
            int pixelCount = this.width * this.height;
            int[] decoded = new int[pixelCount];
            if (source instanceof ShortBuffer shortBuffer) {
                int offset = DirectBufferFactory.getSegmentOffset(shortBuffer);
                if (offset < 0 || offset + pixelCount > shortBuffer.length()) {
                    return false;
                }
                for (int i = 0; i < pixelCount; i++) {
                    int value = DirectBufferFactory.getShort(shortBuffer, offset + i) & 0xFFFF;
                    decoded[i] = switch (type) {
                        case com.nttdocomo.ui.ogl.GraphicsOGL.GL_UNSIGNED_SHORT_4_4_4_4 ->
                                decodeRgba4444(value);
                        case com.nttdocomo.ui.ogl.GraphicsOGL.GL_UNSIGNED_SHORT_5_5_5_1 ->
                                decodeRgb5a1(value);
                        case com.nttdocomo.ui.ogl.GraphicsOGL.GL_UNSIGNED_SHORT_5_6_5 ->
                                decodeRgb565(value);
                        default -> 0;
                    };
                    if (decoded[i] == 0 && type != com.nttdocomo.ui.ogl.GraphicsOGL.GL_UNSIGNED_SHORT_4_4_4_4
                            && type != com.nttdocomo.ui.ogl.GraphicsOGL.GL_UNSIGNED_SHORT_5_5_5_1
                            && type != com.nttdocomo.ui.ogl.GraphicsOGL.GL_UNSIGNED_SHORT_5_6_5) {
                        return false;
                    }
                }
                this.pixels = decoded;
                return true;
            }
            if (source instanceof com.nttdocomo.ui.ogl.ByteBuffer byteBuffer) {
                int bytesPerPixel = bytesPerPixel(format, type);
                if (bytesPerPixel <= 0) {
                    return false;
                }
                int totalBytes = pixelCount * bytesPerPixel;
                int offset = DirectBufferFactory.getSegmentOffset(byteBuffer);
                if (offset < 0 || offset + totalBytes > byteBuffer.length()) {
                    return false;
                }
                byte[] raw = new byte[totalBytes];
                byteBuffer.get(offset, raw, 0, raw.length);
                for (int i = 0; i < pixelCount; i++) {
                    decoded[i] = decodeBytePixel(raw, i * bytesPerPixel, format, type);
                }
                this.pixels = decoded;
                return true;
            }
            return false;
        }

        int sample(float u, float v) {
            if (pixels.length == 0 || width <= 0 || height <= 0) {
                return 0xFFFFFFFF;
            }
            float sampledU = wrapCoordinate(u, wrapS);
            float sampledV = wrapCoordinate(v, wrapT);
            if (minFilter == com.nttdocomo.ui.ogl.GraphicsOGL.GL_LINEAR || magFilter == com.nttdocomo.ui.ogl.GraphicsOGL.GL_LINEAR) {
                return bilinearSample(sampledU, sampledV);
            }
            int x = clamp(Math.round(sampledU * (width - 1)), 0, width - 1);
            int y = clamp(Math.round(sampledV * (height - 1)), 0, height - 1);
            return pixels[(y * width) + x];
        }

        private int bilinearSample(float u, float v) {
            float x = u * (width - 1);
            float y = v * (height - 1);
            int x0 = clamp((int) Math.floor(x), 0, width - 1);
            int y0 = clamp((int) Math.floor(y), 0, height - 1);
            int x1 = clamp(x0 + 1, 0, width - 1);
            int y1 = clamp(y0 + 1, 0, height - 1);
            float tx = x - x0;
            float ty = y - y0;
            int c00 = pixels[(y0 * width) + x0];
            int c10 = pixels[(y0 * width) + x1];
            int c01 = pixels[(y1 * width) + x0];
            int c11 = pixels[(y1 * width) + x1];
            return mixColors(mixColors(c00, c10, tx), mixColors(c01, c11, tx), ty);
        }

        private static float wrapCoordinate(float value, int wrapMode) {
            if (wrapMode == com.nttdocomo.ui.ogl.GraphicsOGL.GL_CLAMP_TO_EDGE) {
                return Math.max(0f, Math.min(1f, value));
            }
            float wrapped = value - (float) Math.floor(value);
            return wrapped < 0f ? wrapped + 1f : wrapped;
        }

        private static int decodeRgb5a1(int value) {
            int alpha = (value & 0x1) != 0 ? 0xFF : 0x00;
            int red = ((value >>> 11) & 0x1F) * 255 / 31;
            int green = ((value >>> 6) & 0x1F) * 255 / 31;
            int blue = ((value >>> 1) & 0x1F) * 255 / 31;
            return (alpha << 24) | (red << 16) | (green << 8) | blue;
        }

        private static int decodeRgba4444(int value) {
            int red = ((value >>> 12) & 0x0F) * 17;
            int green = ((value >>> 8) & 0x0F) * 17;
            int blue = ((value >>> 4) & 0x0F) * 17;
            int alpha = (value & 0x0F) * 17;
            return (alpha << 24) | (red << 16) | (green << 8) | blue;
        }

        private static int decodeRgb565(int value) {
            int red = ((value >>> 11) & 0x1F) * 255 / 31;
            int green = ((value >>> 5) & 0x3F) * 255 / 63;
            int blue = (value & 0x1F) * 255 / 31;
            return 0xFF000000 | (red << 16) | (green << 8) | blue;
        }

        private static int bytesPerPixel(int format, int type) {
            if (type != com.nttdocomo.ui.ogl.GraphicsOGL.GL_UNSIGNED_BYTE) {
                return -1;
            }
            return switch (format) {
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_ALPHA,
                        com.nttdocomo.ui.ogl.GraphicsOGL.GL_LUMINANCE -> 1;
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_LUMINANCE_ALPHA -> 2;
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_RGB -> 3;
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_RGBA -> 4;
                default -> -1;
            };
        }

        private static int decodeBytePixel(byte[] raw, int offset, int format, int type) {
            if (type != com.nttdocomo.ui.ogl.GraphicsOGL.GL_UNSIGNED_BYTE) {
                return 0;
            }
            return switch (format) {
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_ALPHA ->
                        ((raw[offset] & 0xFF) << 24) | 0x00FFFFFF;
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_LUMINANCE -> {
                    int l = raw[offset] & 0xFF;
                    yield 0xFF000000 | (l << 16) | (l << 8) | l;
                }
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_LUMINANCE_ALPHA -> {
                    int l = raw[offset] & 0xFF;
                    int a = raw[offset + 1] & 0xFF;
                    yield (a << 24) | (l << 16) | (l << 8) | l;
                }
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_RGB -> {
                    int r = raw[offset] & 0xFF;
                    int g = raw[offset + 1] & 0xFF;
                    int b = raw[offset + 2] & 0xFF;
                    yield 0xFF000000 | (r << 16) | (g << 8) | b;
                }
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_RGBA -> {
                    int r = raw[offset] & 0xFF;
                    int g = raw[offset + 1] & 0xFF;
                    int b = raw[offset + 2] & 0xFF;
                    int a = raw[offset + 3] & 0xFF;
                    yield (a << 24) | (r << 16) | (g << 8) | b;
                }
                default -> 0;
            };
        }

        private static int mixColors(int left, int right, float ratio) {
            int leftA = (left >>> 24) & 0xFF;
            int leftR = (left >>> 16) & 0xFF;
            int leftG = (left >>> 8) & 0xFF;
            int leftB = left & 0xFF;
            int rightA = (right >>> 24) & 0xFF;
            int rightR = (right >>> 16) & 0xFF;
            int rightG = (right >>> 8) & 0xFF;
            int rightB = right & 0xFF;
            int a = Math.round(leftA + ((rightA - leftA) * ratio));
            int r = Math.round(leftR + ((rightR - leftR) * ratio));
            int g = Math.round(leftG + ((rightG - leftG) * ratio));
            int b = Math.round(leftB + ((rightB - leftB) * ratio));
            return (a << 24) | (r << 16) | (g << 8) | b;
        }
    }

    private static final class OglState {
        private final Map<Integer, OglTexture> textures = new HashMap<>();
        private final Set<Integer> enabledCaps = new HashSet<>();
        private final Set<Integer> enabledClientStates = new HashSet<>();
        private int nextTextureId = 1;
        private int lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_NO_ERROR;
        private int matrixMode = com.nttdocomo.ui.ogl.GraphicsOGL.GL_MODELVIEW;
        private int textureEnvMode = com.nttdocomo.ui.ogl.GraphicsOGL.GL_MODULATE;
        private int shadeModel = com.nttdocomo.ui.ogl.GraphicsOGL.GL_SMOOTH;
        private int clientActiveTexture = com.nttdocomo.ui.ogl.GraphicsOGL.GL_TEXTURE0;
        private int alphaFunc = com.nttdocomo.ui.ogl.GraphicsOGL.GL_ALWAYS;
        private float alphaRef;
        private boolean depthMask = true;
        private int depthFunc = com.nttdocomo.ui.ogl.GraphicsOGL.GL_LESS;
        private float depthRangeNear;
        private float depthRangeFar = 1f;
        private int blendSrcFactor = com.nttdocomo.ui.ogl.GraphicsOGL.GL_ONE;
        private int blendDstFactor = com.nttdocomo.ui.ogl.GraphicsOGL.GL_ZERO;
        private int frontFace = com.nttdocomo.ui.ogl.GraphicsOGL.GL_CCW;
        private int cullFace = com.nttdocomo.ui.ogl.GraphicsOGL.GL_BACK;
        private int unpackAlignment = 1;
        private int boundTextureId;
        private int color = 0xFFFFFFFF;
        private float[] modelViewMatrix = identityMatrix();
        private float[] projectionMatrix = identityMatrix();
        private float[] textureMatrix = identityMatrix();
        private boolean standardModelViewConfigured;
        private boolean standardProjectionConfigured;
        private float[] acrodeaWorldMatrix = identityMatrix();
        private float[] acrodeaCameraMatrix;
        private OglPointer vertexPointer;
        private OglPointer texCoordPointer;

        void beginDrawing() {
            standardModelViewConfigured = false;
            standardProjectionConfigured = false;
            acrodeaWorldMatrix = identityMatrix();
            acrodeaCameraMatrix = null;
        }

        void endDrawing() {
        }

        boolean textureEnabled() {
            return enabledCaps.contains(com.nttdocomo.ui.ogl.GraphicsOGL.GL_TEXTURE_2D)
                    && enabledClientStates.contains(com.nttdocomo.ui.ogl.GraphicsOGL.GL_TEXTURE_COORD_ARRAY)
                    && boundTexture() != null
                    && textureEnvMode == com.nttdocomo.ui.ogl.GraphicsOGL.GL_MODULATE;
        }

        boolean blendEnabled() {
            return enabledCaps.contains(com.nttdocomo.ui.ogl.GraphicsOGL.GL_BLEND);
        }

        boolean depthEnabled() {
            return enabledCaps.contains(com.nttdocomo.ui.ogl.GraphicsOGL.GL_DEPTH_TEST);
        }

        boolean usesAcrodeaMatrices() {
            return acrodeaCameraMatrix != null && !standardModelViewConfigured && !standardProjectionConfigured;
        }

        OglTexture boundTexture() {
            return boundTextureId == 0 ? null : textures.get(boundTextureId);
        }

        int sampleBoundTexture(float u, float v) {
            OglTexture texture = boundTexture();
            return texture == null ? 0xFFFFFFFF : texture.sample(u, v);
        }

        static float[] identityMatrix() {
            return new float[]{
                    1f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f,
                    0f, 0f, 1f, 0f,
                    0f, 0f, 0f, 1f
            };
        }
    }
}
