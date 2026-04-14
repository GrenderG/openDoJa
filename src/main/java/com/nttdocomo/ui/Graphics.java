package com.nttdocomo.ui;

import com.nttdocomo.lang.XString;
import com.nttdocomo.opt.ui.j3d._Opt3DInternalAccess;
import com.nttdocomo.opt.ui.j3d.PrimitiveArray;
import com.nttdocomo.ui.graphics3d._Graphics3DInternalAccess;
import com.nttdocomo.ui.graphics3d._PrimitiveRenderStateAccess;
import com.nttdocomo.ui.ogl.ByteBuffer;
import com.nttdocomo.ui.ogl.DirectBuffer;
import com.nttdocomo.ui.ogl.DirectBufferFactory;
import com.nttdocomo.ui.ogl.FloatBuffer;
import com.nttdocomo.ui.ogl.IntBuffer;
import com.nttdocomo.ui.ogl.ShortBuffer;
import com.nttdocomo.ui.util3d._TransformInternalAccess;
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
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Represents the graphics context used for canvases and images.
 */
public class Graphics implements com.nttdocomo.ui.graphics3d.Graphics3D, com.nttdocomo.opt.ui.j3d.Graphics3D, com.nttdocomo.ui.ogl.GraphicsOGL {
    private static final boolean TRACE_FAILURES = opendoja.host.OpenDoJaLaunchArgs.getBoolean(opendoja.host.OpenDoJaLaunchArgs.TRACE_FAILURES);
    private static final boolean TRACE_3D_CALLS = opendoja.host.OpenDoJaLaunchArgs.getBoolean(opendoja.host.OpenDoJaLaunchArgs.DEBUG3D_CALLS);
    private static final double DOJAAFFINE_FIXED_POINT_SCALE = 4096.0;
    private static final int LEGACY_OPT_COMMAND_LIST_VERSION_1 = 1;
    private static final int OPT_RENDER_OP_REPL = 0;
    private static final int OPT_RENDER_OP_ADD = 1;
    private static final int OPT_RENDER_OP_SUB = 2;
    private static final int OGL_TEXTURE_UNIT_COUNT = 1;
    private static final int OGL_MAX_VERTEX_UNITS = 8;
    private static final int OGL_MAX_PALETTE_MATRICES = 32;
    private static final int OPT_COMMAND_PREFIX_MASK = 0xFF00_0000;
    private static final int OPT_COMMAND_INLINE_VALUE_MASK = 0x00FF_FFFF;
    private static final int OPT_COMMAND_RENDER_COUNT_MASK = 0x00FF_0000;
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
    private Graphics2D delegate;
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
    private final ClipVector eyeVectorTemp = new ClipVector();
    private final ClipVector normalVectorTemp = new ClipVector();
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
        ogl.viewportX = 0;
        ogl.viewportY = 0;
        ogl.viewportWidth = surface.width();
        ogl.viewportHeight = surface.height();
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
        ogl.enableCap(cap);
        if (cap == com.nttdocomo.ui.ogl.GraphicsOGL.GL_COLOR_MATERIAL) {
            syncColorMaterial(unpackColor(ogl.color));
        }
    }

    @Override
    public void glDisable(int cap) {
        ogl.disableCap(cap);
    }

    @Override
    public void glEnableClientState(int array) {
        ogl.enableClientState(array);
    }

    @Override
    public void glDisableClientState(int array) {
        ogl.disableClientState(array);
    }

    @Override
    public void glMatrixMode(int mode) {
        switch (mode) {
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_MODELVIEW,
                    com.nttdocomo.ui.ogl.GraphicsOGL.GL_PROJECTION,
                    com.nttdocomo.ui.ogl.GraphicsOGL.GL_TEXTURE,
                    com.nttdocomo.ui.ogl.GraphicsOGL.GL_MATRIX_PALETTE_OES,
                    1,
                    2 -> ogl.matrixMode = mode;
            default -> ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_ENUM;
        }
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
    public void glPushMatrix() {
        pushCurrentMatrix(ogl.matrixMode);
    }

    @Override
    public void glPopMatrix() {
        popCurrentMatrix(ogl.matrixMode);
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
        if (!isValidBlendSrcFactor(sfactor) || !isValidBlendDstFactor(dfactor)) {
            ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_ENUM;
            return;
        }
        ogl.blendSrcFactor = sfactor;
        ogl.blendDstFactor = dfactor;
    }

    @Override
    public void glColor4f(float red, float green, float blue, float alpha) {
        ogl.color = ((clampOglChannel(alpha) & 0xFF) << 24)
                | ((clampOglChannel(red) & 0xFF) << 16)
                | ((clampOglChannel(green) & 0xFF) << 8)
                | (clampOglChannel(blue) & 0xFF);
        syncColorMaterial(unpackColor(ogl.color));
    }

    @Override
    public void glColor4ub(short red, short green, short blue, short alpha) {
        ogl.color = ((alpha & 0xFF) << 24)
                | ((red & 0xFF) << 16)
                | ((green & 0xFF) << 8)
                | (blue & 0xFF);
        syncColorMaterial(unpackColor(ogl.color));
    }

    @Override
    public void glLightModelf(int pname, float param) {
        glLightModelfv(pname, new float[]{param});
    }

    @Override
    public void glLightModelfv(int pname, float[] params) {
        if (params == null) {
            throw new NullPointerException("params");
        }
        switch (pname) {
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_LIGHT_MODEL_AMBIENT -> {
                if (params.length < 4) {
                    throw new IllegalArgumentException("params");
                }
                copyColor(params, ogl.lightModelAmbient);
            }
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_LIGHT_MODEL_TWO_SIDE -> {
                if (params.length < 1) {
                    throw new IllegalArgumentException("params");
                }
                ogl.lightModelTwoSide = params[0] != 0f;
            }
            default -> ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_ENUM;
        }
    }

    @Override
    public void glLightf(int light, int pname, float param) {
        glLightfv(light, pname, new float[]{param});
    }

    @Override
    public void glLightfv(int light, int pname, float[] params) {
        if (params == null) {
            throw new NullPointerException("params");
        }
        OglLight oglLight = ogl.light(light);
        if (oglLight == null) {
            ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_ENUM;
            return;
        }
        switch (pname) {
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_AMBIENT -> {
                requireLength(params, 4);
                copyColor(params, oglLight.ambient);
            }
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_DIFFUSE -> {
                requireLength(params, 4);
                copyColor(params, oglLight.diffuse);
            }
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_SPECULAR -> {
                requireLength(params, 4);
                copyColor(params, oglLight.specular);
            }
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_POSITION -> {
                requireLength(params, 4);
                multiply(eyeVectorTemp, ogl.modelViewMatrix, params[0], params[1], params[2], params[3]);
                oglLight.position[0] = eyeVectorTemp.x;
                oglLight.position[1] = eyeVectorTemp.y;
                oglLight.position[2] = eyeVectorTemp.z;
                oglLight.position[3] = eyeVectorTemp.w;
            }
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_SPOT_DIRECTION -> {
                requireLength(params, 3);
                transformLightDirection(ogl.modelViewMatrix, params[0], params[1], params[2], oglLight.spotDirection);
            }
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_SPOT_EXPONENT -> {
                requireLength(params, 1);
                if (params[0] < 0f || params[0] > 128f) {
                    ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_VALUE;
                    return;
                }
                oglLight.spotExponent = params[0];
            }
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_SPOT_CUTOFF -> {
                requireLength(params, 1);
                if ((params[0] < 0f || params[0] > 90f) && params[0] != 180f) {
                    ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_VALUE;
                    return;
                }
                oglLight.spotCutoff = params[0];
            }
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_CONSTANT_ATTENUATION -> {
                requireLength(params, 1);
                if (params[0] < 0f) {
                    ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_VALUE;
                    return;
                }
                oglLight.constantAttenuation = params[0];
            }
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_LINEAR_ATTENUATION -> {
                requireLength(params, 1);
                if (params[0] < 0f) {
                    ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_VALUE;
                    return;
                }
                oglLight.linearAttenuation = params[0];
            }
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_QUADRATIC_ATTENUATION -> {
                requireLength(params, 1);
                if (params[0] < 0f) {
                    ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_VALUE;
                    return;
                }
                oglLight.quadraticAttenuation = params[0];
            }
            default -> ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_ENUM;
        }
    }

    @Override
    public void glMaterialf(int face, int pname, float param) {
        glMaterialfv(face, pname, new float[]{param});
    }

    @Override
    public void glMaterialfv(int face, int pname, float[] params) {
        if (params == null) {
            throw new NullPointerException("params");
        }
        if (face != com.nttdocomo.ui.ogl.GraphicsOGL.GL_FRONT_AND_BACK) {
            ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_ENUM;
            return;
        }
        switch (pname) {
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_AMBIENT -> {
                requireLength(params, 4);
                if (!ogl.colorMaterialTracksAmbientAndDiffuse()) {
                    copyColor(params, ogl.materialAmbient);
                }
            }
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_DIFFUSE -> {
                requireLength(params, 4);
                if (!ogl.colorMaterialTracksAmbientAndDiffuse()) {
                    copyColor(params, ogl.materialDiffuse);
                }
            }
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_AMBIENT_AND_DIFFUSE -> {
                requireLength(params, 4);
                if (!ogl.colorMaterialTracksAmbientAndDiffuse()) {
                    copyColor(params, ogl.materialAmbient);
                    copyColor(params, ogl.materialDiffuse);
                }
            }
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_SPECULAR -> {
                requireLength(params, 4);
                copyColor(params, ogl.materialSpecular);
            }
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_EMISSION -> {
                requireLength(params, 4);
                copyColor(params, ogl.materialEmission);
            }
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_SHININESS -> {
                requireLength(params, 1);
                if (params[0] < 0f || params[0] > 128f) {
                    ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_VALUE;
                    return;
                }
                ogl.materialShininess = params[0];
            }
            default -> ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_ENUM;
        }
    }

    @Override
    public void glNormal3f(float nx, float ny, float nz) {
        ogl.currentNormal[0] = nx;
        ogl.currentNormal[1] = ny;
        ogl.currentNormal[2] = nz;
    }

    @Override
    public void glTexEnvi(int target, int pname, int param) {
        if (target != com.nttdocomo.ui.ogl.GraphicsOGL.GL_TEXTURE_ENV) {
            ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_ENUM;
            return;
        }
        switch (pname) {
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_TEXTURE_ENV_MODE -> ogl.textureEnvMode = param;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_COMBINE_RGB -> ogl.combineRgb = param;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_COMBINE_ALPHA -> ogl.combineAlpha = param;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_SRC0_RGB -> ogl.srcRgb[0] = param;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_SRC1_RGB -> ogl.srcRgb[1] = param;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_SRC2_RGB -> ogl.srcRgb[2] = param;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_SRC0_ALPHA -> ogl.srcAlpha[0] = param;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_SRC1_ALPHA -> ogl.srcAlpha[1] = param;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_SRC2_ALPHA -> ogl.srcAlpha[2] = param;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_OPERAND0_RGB -> ogl.operandRgb[0] = param;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_OPERAND1_RGB -> ogl.operandRgb[1] = param;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_OPERAND2_RGB -> ogl.operandRgb[2] = param;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_OPERAND0_ALPHA -> ogl.operandAlpha[0] = param;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_OPERAND1_ALPHA -> ogl.operandAlpha[1] = param;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_OPERAND2_ALPHA -> ogl.operandAlpha[2] = param;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_RGB_SCALE -> ogl.rgbScale = sanitizeTextureScale(param);
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_ALPHA_SCALE -> ogl.alphaScale = sanitizeTextureScale(param);
            default -> ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_ENUM;
        }
    }

    @Override
    public void glTexEnvfv(int target, int pname, float[] params) {
        if (target != com.nttdocomo.ui.ogl.GraphicsOGL.GL_TEXTURE_ENV) {
            ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_ENUM;
            return;
        }
        if (params == null) {
            throw new NullPointerException("params");
        }
        if (pname == com.nttdocomo.ui.ogl.GraphicsOGL.GL_TEXTURE_ENV_COLOR) {
            if (params.length < 4) {
                throw new IllegalArgumentException("params");
            }
            ogl.textureEnvColor = packColor(params[0], params[1], params[2], params[3]);
            return;
        }
        if (params.length < 1) {
            throw new IllegalArgumentException("params");
        }
        if (pname == com.nttdocomo.ui.ogl.GraphicsOGL.GL_RGB_SCALE) {
            ogl.rgbScale = sanitizeTextureScale(Math.round(params[0]));
            return;
        }
        if (pname == com.nttdocomo.ui.ogl.GraphicsOGL.GL_ALPHA_SCALE) {
            ogl.alphaScale = sanitizeTextureScale(Math.round(params[0]));
            return;
        }
        glTexEnvi(target, pname, Math.round(params[0]));
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
    public void glViewport(int x, int y, int width, int height) {
        if (width < 0 || height < 0) {
            ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_VALUE;
            return;
        }
        ogl.viewportX = x;
        ogl.viewportY = y;
        ogl.viewportWidth = width;
        ogl.viewportHeight = height;
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
    public void glTexParameteri(int target, int pname, int param) {
        glTexParameterf(target, pname, param);
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
        if (!OglTexture.supportsCompressedInternalFormat(internalformat)) {
            ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_ENUM;
            return;
        }
        if (!texture.loadCompressed(internalformat, width, height, raw)) {
            ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_VALUE;
        }
    }

    @Override
    public void glCompressedTexImage2D(int target, int level, int internalformat, int width, int height, int border,
                                       com.nttdocomo.ui.ogl.ByteBuffer pixels) {
        if (pixels == null) {
            throw new NullPointerException("pixels");
        }
        glCompressedTexImage2D(target, level, internalformat, width, height, border, pixels.length(), pixels);
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
    public void glGenBuffers(int[] buffers) {
        if (buffers == null) {
            throw new NullPointerException("buffers");
        }
        for (int i = 0; i < buffers.length; i++) {
            int id = ogl.nextBufferId++;
            ogl.buffers.put(id, new OglBufferObject());
            buffers[i] = id;
        }
    }

    @Override
    public void glDeleteBuffers(int[] buffers) {
        if (buffers == null) {
            throw new NullPointerException("buffers");
        }
        for (int bufferId : buffers) {
            ogl.buffers.remove(bufferId);
            if (ogl.boundArrayBufferId == bufferId) {
                ogl.boundArrayBufferId = 0;
            }
            if (ogl.boundElementArrayBufferId == bufferId) {
                ogl.boundElementArrayBufferId = 0;
            }
        }
    }

    @Override
    public void glBindBuffer(int target, int buffer) {
        switch (target) {
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_ARRAY_BUFFER -> {
                if (buffer != 0) {
                    ogl.buffers.computeIfAbsent(buffer, ignored -> new OglBufferObject());
                }
                ogl.boundArrayBufferId = buffer;
            }
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_ELEMENT_ARRAY_BUFFER -> {
                if (buffer != 0) {
                    ogl.buffers.computeIfAbsent(buffer, ignored -> new OglBufferObject());
                }
                ogl.boundElementArrayBufferId = buffer;
            }
            default -> ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_ENUM;
        }
    }

    @Override
    public void glBufferData(int target, DirectBuffer data, int usage) {
        OglBufferObject buffer = ogl.boundBuffer(target);
        if (buffer == null) {
            ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_OPERATION;
            return;
        }
        if (data == null) {
            throw new NullPointerException("data");
        }
        byte[] bytes = copyBufferBytes(data);
        buffer.replace(bytes, usage);
    }

    @Override
    public void glBufferSubData(int target, int offset, DirectBuffer data) {
        OglBufferObject buffer = ogl.boundBuffer(target);
        if (buffer == null) {
            ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_OPERATION;
            return;
        }
        if (offset < 0) {
            throw new ArrayIndexOutOfBoundsException("offset");
        }
        if (data == null) {
            throw new NullPointerException("data");
        }
        byte[] bytes = copyBufferBytes(data);
        if (!buffer.update(offset, bytes)) {
            ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_VALUE;
        }
    }

    @Override
    public void glVertexPointer(int size, int type, int stride, DirectBuffer pointer) {
        ogl.vertexPointer = OglPointer.direct(size, type, stride, pointer);
    }

    @Override
    public void glVertexPointer(int size, int type, int stride, int pointer) {
        ogl.vertexPointer = OglPointer.bufferObject(size, type, stride, requireArrayBuffer(), pointer);
    }

    @Override
    public void glTexCoordPointer(int size, int type, int stride, DirectBuffer pointer) {
        ogl.texCoordPointer = OglPointer.direct(size, type, stride, pointer);
    }

    @Override
    public void glTexCoordPointer(int size, int type, int stride, int pointer) {
        ogl.texCoordPointer = OglPointer.bufferObject(size, type, stride, requireArrayBuffer(), pointer);
    }

    @Override
    public void glNormalPointer(int type, int stride, DirectBuffer pointer) {
        ogl.normalPointer = OglPointer.direct(3, type, stride, pointer);
    }

    @Override
    public void glNormalPointer(int type, int stride, int pointer) {
        ogl.normalPointer = OglPointer.bufferObject(3, type, stride, requireArrayBuffer(), pointer);
    }

    @Override
    public void glColorPointer(int size, int type, int stride, DirectBuffer pointer) {
        ogl.colorPointer = OglPointer.direct(size, type, stride, pointer);
    }

    @Override
    public void glColorPointer(int size, int type, int stride, int pointer) {
        ogl.colorPointer = OglPointer.bufferObject(size, type, stride, requireArrayBuffer(), pointer);
    }

    @Override
    public void glCurrentPaletteMatrixOES(int matrixpaletteindex) {
        if (matrixpaletteindex < 0 || matrixpaletteindex >= OGL_MAX_PALETTE_MATRICES) {
            ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_VALUE;
            return;
        }
        ogl.currentPaletteMatrix = matrixpaletteindex;
    }

    @Override
    public void glLoadPaletteFromModelViewMatrixOES() {
        ogl.paletteMatrices[ogl.currentPaletteMatrix] = ogl.modelViewMatrix.clone();
    }

    @Override
    public void glMatrixIndexPointerOES(int size, int type, int stride, DirectBuffer pointer) {
        ogl.matrixIndexPointer = OglPointer.direct(size, type, stride, pointer);
    }

    @Override
    public void glMatrixIndexPointerOES(int size, int type, int stride, int pointer) {
        ogl.matrixIndexPointer = OglPointer.bufferObject(size, type, stride, requireArrayBuffer(), pointer);
    }

    @Override
    public void glWeightPointerOES(int size, int type, int stride, DirectBuffer pointer) {
        ogl.weightPointer = OglPointer.direct(size, type, stride, pointer);
    }

    @Override
    public void glWeightPointerOES(int size, int type, int stride, int pointer) {
        ogl.weightPointer = OglPointer.bufferObject(size, type, stride, requireArrayBuffer(), pointer);
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
        drawOgl(mode, 0, count, OglIndexSource.direct((ShortBuffer) indices));
    }

    @Override
    public void glDrawElements(int mode, int count, int type, int indices) {
        if (type != com.nttdocomo.ui.ogl.GraphicsOGL.GL_UNSIGNED_SHORT) {
            ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_ENUM;
            return;
        }
        if (indices < 0) {
            throw new ArrayIndexOutOfBoundsException("indices");
        }
        OglBufferObject buffer = ogl.boundElementArrayBuffer();
        if (buffer == null) {
            ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_OPERATION;
            return;
        }
        drawOgl(mode, 0, count, OglIndexSource.bufferObject(buffer, indices));
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

    @Override
    public void glGetIntegerv(int pname, int[] params) {
        if (params == null) {
            throw new NullPointerException("params");
        }
        if (params.length < 1) {
            throw new IllegalArgumentException("params");
        }
        switch (pname) {
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_MAX_LIGHTS -> params[0] = 8;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_MAX_TEXTURE_UNITS -> params[0] = OGL_TEXTURE_UNIT_COUNT;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_MAX_PALETTE_MATRICES_OES -> params[0] = OGL_MAX_PALETTE_MATRICES;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_MAX_VERTEX_UNITS_OES -> params[0] = OGL_MAX_VERTEX_UNITS;
            default -> ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_ENUM;
        }
    }

    @Override
    public void glGetFloatv(int pname, float[] params) {
        if (params == null) {
            throw new NullPointerException("params");
        }
        switch (pname) {
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_MODELVIEW_MATRIX -> copyFloatState(ogl.modelViewMatrix, params, 16);
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_PROJECTION_MATRIX -> copyFloatState(ogl.projectionMatrix, params, 16);
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_TEXTURE_MATRIX -> copyFloatState(ogl.textureMatrix, params, 16);
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_VIEWPORT -> {
                requireLength(params, 4);
                params[0] = ogl.viewportX;
                params[1] = ogl.viewportY;
                params[2] = ogl.viewportWidth;
                params[3] = ogl.viewportHeight;
            }
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_DEPTH_RANGE -> {
                requireLength(params, 2);
                params[0] = ogl.depthRangeNear;
                params[1] = ogl.depthRangeFar;
            }
            default -> ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_ENUM;
        }
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
        delegate.dispose();
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
        syncOptRendererState();
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
        delegate.drawPolyline(drawX, drawY, n);
        flushSurfacePresentation();
    }

    /**
     * Draws rect.
     */
    public void drawRect(int x, int y, int width, int height) {
        Rectangle bounds = normalizedRectangle(originX + x, originY + y, width + 1, height + 1);
        if (drawWithOptRenderMode(bounds, graphics -> graphics.drawRect(originX + x, originY + y, width, height))) {
            return;
        }
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
        Rectangle bounds = normalizedRectangle(originX + x, originY + y, width, height);
        if (drawWithOptRenderMode(bounds, graphics -> graphics.fillRect(originX + x, originY + y, width, height))) {
            return;
        }
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
        Rectangle bounds = normalizedRectangle(originX + x, originY + y, width, height);
        if (drawWithOptRenderMode(bounds, graphics -> graphics.drawArc(originX + x, originY + y, width, height, startAngle, arcAngle))) {
            return;
        }
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
        if (!surface.hasRepaintHook()) {
            return;
        }
        DoJaRuntime runtime = DoJaRuntime.current();
        if (runtime != null && runtime.surfaceLock().isHeldByCurrentThread()) {
            return;
        }
        surface.flush(copyImage(surface.image()), false);
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
        blendOptRenderLayer(layer, targetBounds.x, targetBounds.y);
        flushSurfacePresentation();
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

    private static int clampOglChannel(float value) {
        if (Float.isNaN(value)) {
            return 0;
        }
        float clamped = Math.max(0.0f, Math.min(1.0f, value));
        return Math.round(clamped * 255.0f);
    }

    private static int packColor(float red, float green, float blue, float alpha) {
        return ((clampOglChannel(alpha) & 0xFF) << 24)
                | ((clampOglChannel(red) & 0xFF) << 16)
                | ((clampOglChannel(green) & 0xFF) << 8)
                | (clampOglChannel(blue) & 0xFF);
    }

    private static void requireLength(float[] params, int minimumLength) {
        if (params.length < minimumLength) {
            throw new IllegalArgumentException("params");
        }
    }

    private static void copyColor(float[] source, float[] destination) {
        destination[0] = source[0];
        destination[1] = source[1];
        destination[2] = source[2];
        destination[3] = source[3];
    }

    private static void copyFloatState(float[] source, float[] destination, int requiredLength) {
        requireLength(destination, requiredLength);
        System.arraycopy(source, 0, destination, 0, requiredLength);
    }

    private void syncColorMaterial(float[] color) {
        if (!ogl.colorMaterialTracksAmbientAndDiffuse()) {
            return;
        }
        copyColor(color, ogl.materialAmbient);
        copyColor(color, ogl.materialDiffuse);
    }

    private void transformLightDirection(float[] matrix, float x, float y, float z, float[] destination) {
        if (matrix == null) {
            destination[0] = x;
            destination[1] = y;
            destination[2] = z;
            return;
        }
        destination[0] = (matrix[0] * x) + (matrix[4] * y) + (matrix[8] * z);
        destination[1] = (matrix[1] * x) + (matrix[5] * y) + (matrix[9] * z);
        destination[2] = (matrix[2] * x) + (matrix[6] * y) + (matrix[10] * z);
    }

    private int sanitizeTextureScale(int scale) {
        if (scale == 1 || scale == 2 || scale == 4) {
            return scale;
        }
        ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_VALUE;
        return 1;
    }

    private void flushSurfaceFrame() {
        // `Graphics3D.flush()` is the pass boundary for staged opt draws inside one locked frame.
        // Finish any deferred blended primitive batches before ending the shared depth frame.
        flushPending3DPasses();
        pendingOptRenderedContent = false;
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
        threeD.flushPendingOptPrimitiveBlends(delegate, surface.image());
    }

    private void prepare3DDepthFrame() {
        // Games can submit 3D assets through separate 3D calls inside one
        // lock/unlock frame. They must share one z-buffer or later props ignore ramp depth.
        pendingOptRenderedContent = true;
        threeD.setFrameDepthBuffer(surface.depthBufferForFrame());
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
            float[] matrix = transform == null ? Software3DContext.identity() : _TransformInternalAccess.raw(transform);
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
            _Graphics3DInternalAccess.LightState lightState = _Graphics3DInternalAccess.lightState(light);
            threeD.addUiLight(lightState.mode(), lightState.intensity(), lightState.color());
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
            MascotFigure handle = _Graphics3DInternalAccess.handle(figure);
            _Graphics3DInternalAccess.DrawableRenderState renderState = _Graphics3DInternalAccess.drawableRenderState(figure);
            prepare3DDepthFrame();
            if (TRACE_3D_CALLS) {
                int polygons = handle == null || handle.model() == null ? -1 : handle.model().polygons().length;
                OpenDoJaLog.debug(Graphics.class, () -> "3D call renderObject3D type=Figure polygons=" + polygons
                        + " textures=" + (handle == null ? -1 : handle.numTextures())
                        + " pattern=" + (handle == null ? -1 : handle.patternMask())
                        + " transform=" + (objectMatrix == null ? "null" : Arrays.toString(objectMatrix)));
            }
            threeD.renderUiFigure(delegate, surface.image(), originX, originY, surface.width(), surface.height(),
                    handle, objectMatrix, renderState.blendMode(), renderState.transparency());
            return;
        }
        if (object instanceof com.nttdocomo.ui.graphics3d.Primitive primitive) {
            _PrimitiveRenderStateAccess.PrimitiveRenderState renderState = _PrimitiveRenderStateAccess.snapshot(primitive);
            _Graphics3DInternalAccess.DrawableRenderState drawableState = _Graphics3DInternalAccess.drawableRenderState(primitive);
            prepare3DDepthFrame();
            if (TRACE_3D_CALLS) {
                OpenDoJaLog.debug(Graphics.class, () -> "3D call renderObject3D type=Primitive primitiveType="
                        + primitive.getPrimitiveType()
                        + " count=" + primitive.size()
                        + " transform=" + (objectMatrix == null ? "null" : Arrays.toString(objectMatrix)));
            }
            threeD.renderUiPrimitive(delegate, surface.image(), originX, originY, surface.width(), surface.height(),
                    primitive.getPrimitiveType(), primitive.getPrimitiveParam(), primitive.size(),
                    primitive.getVertexArray(), primitive.getColorArray(), primitive.getTextureCoordArray(), renderState.textureHandle(),
                    objectMatrix, drawableState.blendMode(), drawableState.transparency(),
                    renderState.textureWrapEnabled(),
                    renderState.textureCoordinateTranslateU(),
                    renderState.textureCoordinateTranslateV(),
                    renderState.depthTestEnabled(),
                    renderState.depthWriteEnabled(),
                    renderState.doubleSided());
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
            float[] matrix = transform == null ? Software3DContext.identity() : _Opt3DInternalAccess.toFloatMatrix(transform);
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
            MascotFigure handle = _Opt3DInternalAccess.handle(figure);
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
        SoftwareTexture handle = _Opt3DInternalAccess.handle(texture);
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
            SoftwareTexture handle = texture == null ? null : _Opt3DInternalAccess.handle(texture);
            OpenDoJaLog.debug(Graphics.class, () -> "3D call setPrimitiveTextureArray single texture=" + describeTexture(handle));
        }
        threeD.setPrimitiveTextures(texture == null ? null : new SoftwareTexture[]{_Opt3DInternalAccess.handle(texture)});
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
            converted[i] = _Opt3DInternalAccess.handle(textures[i]);
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
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_MATRIX_PALETTE_OES ->
                    ogl.paletteMatrices[ogl.currentPaletteMatrix] = matrix;
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
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_MATRIX_PALETTE_OES ->
                    ogl.paletteMatrices[ogl.currentPaletteMatrix] = multiplyMatrices(
                            ogl.paletteMatrices[ogl.currentPaletteMatrix],
                            matrix);
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

    private void pushCurrentMatrix(int mode) {
        switch (mode) {
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_MODELVIEW -> ogl.modelViewStack.push(ogl.modelViewMatrix.clone());
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_PROJECTION -> ogl.projectionStack.push(ogl.projectionMatrix.clone());
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_TEXTURE -> ogl.textureStack.push(ogl.textureMatrix.clone());
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_MATRIX_PALETTE_OES ->
                    ogl.paletteMatrixStacks[ogl.currentPaletteMatrix].push(ogl.paletteMatrices[ogl.currentPaletteMatrix].clone());
            case 1 -> ogl.acrodeaWorldStack.push(ogl.acrodeaWorldMatrix.clone());
            case 2 -> ogl.acrodeaCameraStack.push(
                    (ogl.acrodeaCameraMatrix == null ? OglState.identityMatrix() : ogl.acrodeaCameraMatrix).clone());
            default -> ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_ENUM;
        }
    }

    private void popCurrentMatrix(int mode) {
        switch (mode) {
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_MODELVIEW -> {
                if (ogl.modelViewStack.isEmpty()) {
                    ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_STACK_UNDERFLOW;
                    return;
                }
                ogl.modelViewMatrix = ogl.modelViewStack.pop();
                ogl.standardModelViewConfigured = true;
            }
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_PROJECTION -> {
                if (ogl.projectionStack.isEmpty()) {
                    ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_STACK_UNDERFLOW;
                    return;
                }
                ogl.projectionMatrix = ogl.projectionStack.pop();
                ogl.standardProjectionConfigured = true;
            }
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_TEXTURE -> {
                if (ogl.textureStack.isEmpty()) {
                    ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_STACK_UNDERFLOW;
                    return;
                }
                ogl.textureMatrix = ogl.textureStack.pop();
            }
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_MATRIX_PALETTE_OES -> {
                if (ogl.paletteMatrixStacks[ogl.currentPaletteMatrix].isEmpty()) {
                    ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_STACK_UNDERFLOW;
                    return;
                }
                ogl.paletteMatrices[ogl.currentPaletteMatrix] = ogl.paletteMatrixStacks[ogl.currentPaletteMatrix].pop();
            }
            case 1 -> {
                if (ogl.acrodeaWorldStack.isEmpty()) {
                    ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_STACK_UNDERFLOW;
                    return;
                }
                ogl.acrodeaWorldMatrix = ogl.acrodeaWorldStack.pop();
            }
            case 2 -> {
                if (ogl.acrodeaCameraStack.isEmpty()) {
                    ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_STACK_UNDERFLOW;
                    return;
                }
                ogl.acrodeaCameraMatrix = ogl.acrodeaCameraStack.pop();
            }
            default -> ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_ENUM;
        }
    }

    private OglBufferObject requireArrayBuffer() {
        OglBufferObject buffer = ogl.boundArrayBuffer();
        if (buffer == null) {
            ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_OPERATION;
        }
        return buffer;
    }

    private static byte[] copyBufferBytes(DirectBuffer data) {
        if (data instanceof ByteBuffer byteBuffer) {
            int offset = DirectBufferFactory.getSegmentOffset(byteBuffer);
            int length = DirectBufferFactory.getSegmentLength(byteBuffer);
            byte[] raw = new byte[length];
            byteBuffer.get(offset, raw, 0, raw.length);
            return raw;
        }
        if (data instanceof ShortBuffer shortBuffer) {
            int offset = DirectBufferFactory.getSegmentOffset(shortBuffer);
            int length = DirectBufferFactory.getSegmentLength(shortBuffer);
            short[] raw = new short[length];
            shortBuffer.get(offset, raw, 0, raw.length);
            byte[] bytes = new byte[raw.length * 2];
            for (int i = 0; i < raw.length; i++) {
                int value = raw[i] & 0xFFFF;
                int byteOffset = i * 2;
                bytes[byteOffset] = (byte) value;
                bytes[byteOffset + 1] = (byte) (value >>> 8);
            }
            return bytes;
        }
        if (data instanceof FloatBuffer floatBuffer) {
            int offset = DirectBufferFactory.getSegmentOffset(floatBuffer);
            int length = DirectBufferFactory.getSegmentLength(floatBuffer);
            float[] raw = new float[length];
            floatBuffer.get(offset, raw, 0, raw.length);
            byte[] bytes = new byte[raw.length * 4];
            for (int i = 0; i < raw.length; i++) {
                int value = Float.floatToIntBits(raw[i]);
                int byteOffset = i * 4;
                bytes[byteOffset] = (byte) value;
                bytes[byteOffset + 1] = (byte) (value >>> 8);
                bytes[byteOffset + 2] = (byte) (value >>> 16);
                bytes[byteOffset + 3] = (byte) (value >>> 24);
            }
            return bytes;
        }
        if (data instanceof IntBuffer intBuffer) {
            int offset = DirectBufferFactory.getSegmentOffset(intBuffer);
            int length = DirectBufferFactory.getSegmentLength(intBuffer);
            int[] raw = new int[length];
            intBuffer.get(offset, raw, 0, raw.length);
            byte[] bytes = new byte[raw.length * 4];
            for (int i = 0; i < raw.length; i++) {
                int value = raw[i];
                int byteOffset = i * 4;
                bytes[byteOffset] = (byte) value;
                bytes[byteOffset + 1] = (byte) (value >>> 8);
                bytes[byteOffset + 2] = (byte) (value >>> 16);
                bytes[byteOffset + 3] = (byte) (value >>> 24);
            }
            return bytes;
        }
        throw new IllegalArgumentException("data");
    }

    private void drawOgl(int mode, int first, int count, OglIndexSource indexSource) {
        if (ogl.vertexPointer == null || count <= 0) {
            return;
        }
        int primitiveCount = count;
        if (indexSource != null) {
            primitiveCount = Math.min(Math.max(0, count), indexSource.elementCount());
        }
        Rectangle clip = delegate.getClipBounds();
        BufferedImage target = surface.image();
        int[] pixels = ((DataBufferInt) target.getRaster().getDataBuffer()).getData();
        float[] depthBuffer = surface.depthBufferForFrame();
        RasterVertex scratch0 = new RasterVertex();
        RasterVertex scratch1 = new RasterVertex();
        RasterVertex scratch2 = new RasterVertex();
        RasterVertex[] clipInput = createRasterVertexArray(12);
        RasterVertex[] clipScratch = createRasterVertexArray(12);
        RasterVertex project0 = new RasterVertex();
        RasterVertex project1 = new RasterVertex();
        RasterVertex project2 = new RasterVertex();
        ClipVector clip0 = new ClipVector();
        ClipVector clip1 = new ClipVector();
        switch (mode) {
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_TRIANGLES -> {
                for (int i = 0; i + 2 < primitiveCount; i += 3) {
                    if (!populateRasterVertex(scratch0, clip0, resolveVertexIndex(first, indexSource, i))
                            || !populateRasterVertex(scratch1, clip1, resolveVertexIndex(first, indexSource, i + 1))
                            || !populateRasterVertex(scratch2, clip0, resolveVertexIndex(first, indexSource, i + 2))) {
                        continue;
                    }
                    drawRasterTriangle(scratch0, scratch1, scratch2, pixels, depthBuffer, clip, target.getWidth(), target.getHeight(),
                            clipInput, clipScratch, project0, project1, project2);
                }
            }
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_TRIANGLE_STRIP -> {
                if (primitiveCount < 3) {
                    return;
                }
                if (!populateRasterVertex(scratch0, clip0, resolveVertexIndex(first, indexSource, 0))
                        || !populateRasterVertex(scratch1, clip1, resolveVertexIndex(first, indexSource, 1))) {
                    return;
                }
                RasterVertex previous0 = scratch0;
                RasterVertex previous1 = scratch1;
                RasterVertex next = scratch2;
                ClipVector nextClip = clip0;
                for (int i = 2; i < primitiveCount; i++) {
                    if (!populateRasterVertex(next, nextClip, resolveVertexIndex(first, indexSource, i))) {
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
                    drawRasterTriangle(v0, v1, v2, pixels, depthBuffer, clip, target.getWidth(), target.getHeight(),
                            clipInput, clipScratch, project0, project1, project2);
                    previous0 = previous1;
                    previous1 = next;
                    next = v0;
                    nextClip = next == scratch0 ? clip0 : clip1;
                }
            }
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_LINE_LOOP ->
                    drawLineLoop(first, primitiveCount, indexSource, clip, target.getWidth(), target.getHeight(),
                            scratch0, scratch1, scratch2, clip0, clip1);
            default -> ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_ENUM;
        }
    }

    private int resolveVertexIndex(int first, OglIndexSource elementIndices, int primitiveIndex) {
        if (elementIndices == null) {
            return first + primitiveIndex;
        }
        int index = elementIndices.indexAt(primitiveIndex);
        if (index < 0) {
            ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_VALUE;
            return 0;
        }
        return index;
    }

    private boolean populateRasterVertex(RasterVertex targetVertex, ClipVector clipVector, int vertexIndex) {
        OglPointer vertexPointer = ogl.vertexPointer;
        if (vertexPointer == null) {
            return false;
        }
        if (vertexPointer.type() != com.nttdocomo.ui.ogl.GraphicsOGL.GL_FLOAT) {
            ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_ENUM;
            return false;
        }
        int positionSize = Math.max(1, vertexPointer.size());
        float x = readFloatComponent(vertexPointer, vertexIndex, 0);
        if (ogl.lastError != com.nttdocomo.ui.ogl.GraphicsOGL.GL_NO_ERROR) {
            return false;
        }
        float y = positionSize > 1 ? readFloatComponent(vertexPointer, vertexIndex, 1) : 0f;
        if (ogl.lastError != com.nttdocomo.ui.ogl.GraphicsOGL.GL_NO_ERROR) {
            return false;
        }
        float z = positionSize > 2 ? readFloatComponent(vertexPointer, vertexIndex, 2) : 0f;
        if (ogl.lastError != com.nttdocomo.ui.ogl.GraphicsOGL.GL_NO_ERROR) {
            return false;
        }
        float u = 0f;
        float v = 0f;
        OglPointer texCoordPointer = ogl.texCoordArrayEnabled
                ? ogl.texCoordPointer
                : null;
        if (texCoordPointer != null) {
            if (texCoordPointer.type() != com.nttdocomo.ui.ogl.GraphicsOGL.GL_FLOAT) {
                ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_ENUM;
                return false;
            }
            int texSize = Math.max(1, texCoordPointer.size());
            u = readFloatComponent(texCoordPointer, vertexIndex, 0);
            if (ogl.lastError != com.nttdocomo.ui.ogl.GraphicsOGL.GL_NO_ERROR) {
                return false;
            }
            if (texSize > 1) {
                v = readFloatComponent(texCoordPointer, vertexIndex, 1);
                if (ogl.lastError != com.nttdocomo.ui.ogl.GraphicsOGL.GL_NO_ERROR) {
                    return false;
                }
            }
        }
        boolean useAcrodeaMatrices = positionSize >= 3 && ogl.usesAcrodeaMatrices();
        transformVertex(clipVector, eyeVectorTemp, x, y, z, vertexIndex, useAcrodeaMatrices);
        float w = Math.abs(clipVector.w) < 0.000001f ? (clipVector.w < 0f ? -0.000001f : 0.000001f) : clipVector.w;
        float ndcX = clipVector.x / w;
        float ndcY = clipVector.y / w;
        float ndcZ = clipVector.z / w;
        float windowDepth = ogl.depthRangeNear + (((ndcZ + 1f) * 0.5f) * (ogl.depthRangeFar - ogl.depthRangeNear));
        float depth = 1f - windowDepth;
        float reciprocalW = 1f / Math.max(0.000001f, Math.abs(w));
        int primaryColor = resolvePrimaryColor(vertexIndex);
        if (ogl.lastError != com.nttdocomo.ui.ogl.GraphicsOGL.GL_NO_ERROR) {
            return false;
        }
        int backColor = primaryColor;
        if (ogl.lightingEnabled()) {
            int sourceColor = primaryColor;
            primaryColor = resolveLitColor(vertexIndex, eyeVectorTemp, sourceColor, false);
            if (ogl.lastError != com.nttdocomo.ui.ogl.GraphicsOGL.GL_NO_ERROR) {
                return false;
            }
            backColor = ogl.lightModelTwoSide
                    ? resolveLitColor(vertexIndex, eyeVectorTemp, sourceColor, true)
                    : primaryColor;
            if (ogl.lastError != com.nttdocomo.ui.ogl.GraphicsOGL.GL_NO_ERROR) {
                return false;
            }
        }
        targetVertex.set(
                clipVector.x,
                clipVector.y,
                clipVector.z,
                clipVector.w,
                viewportX(ndcX),
                viewportY(ndcY),
                depth,
                reciprocalW,
                u,
                v,
                primaryColor,
                backColor
        );
        return true;
    }

    private int resolvePrimaryColor(int vertexIndex) {
        if (!ogl.colorArrayEnabled || ogl.colorPointer == null) {
            return ogl.color;
        }
        OglPointer colorPointer = ogl.colorPointer;
        if (colorPointer.type() != com.nttdocomo.ui.ogl.GraphicsOGL.GL_UNSIGNED_BYTE) {
            ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_ENUM;
            return 0;
        }
        int red = colorPointer.size() > 0 ? readUnsignedByteComponent(colorPointer, vertexIndex, 0) : 255;
        if (ogl.lastError != com.nttdocomo.ui.ogl.GraphicsOGL.GL_NO_ERROR) {
            return 0;
        }
        int green = colorPointer.size() > 1 ? readUnsignedByteComponent(colorPointer, vertexIndex, 1) : red;
        if (ogl.lastError != com.nttdocomo.ui.ogl.GraphicsOGL.GL_NO_ERROR) {
            return 0;
        }
        int blue = colorPointer.size() > 2 ? readUnsignedByteComponent(colorPointer, vertexIndex, 2) : red;
        if (ogl.lastError != com.nttdocomo.ui.ogl.GraphicsOGL.GL_NO_ERROR) {
            return 0;
        }
        int alpha = colorPointer.size() > 3 ? readUnsignedByteComponent(colorPointer, vertexIndex, 3) : 255;
        if (ogl.lastError != com.nttdocomo.ui.ogl.GraphicsOGL.GL_NO_ERROR) {
            return 0;
        }
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private int resolveLitColor(int vertexIndex, ClipVector eyePosition, int sourceColor, boolean backFace) {
        float sourceRed = packedComponent(sourceColor, 0);
        float sourceGreen = packedComponent(sourceColor, 1);
        float sourceBlue = packedComponent(sourceColor, 2);
        float sourceAlpha = packedComponent(sourceColor, 3);
        boolean colorMaterial = ogl.colorMaterialTracksAmbientAndDiffuse();
        float ambientRed = colorMaterial ? sourceRed : ogl.materialAmbient[0];
        float ambientGreen = colorMaterial ? sourceGreen : ogl.materialAmbient[1];
        float ambientBlue = colorMaterial ? sourceBlue : ogl.materialAmbient[2];
        float diffuseRed = colorMaterial ? sourceRed : ogl.materialDiffuse[0];
        float diffuseGreen = colorMaterial ? sourceGreen : ogl.materialDiffuse[1];
        float diffuseBlue = colorMaterial ? sourceBlue : ogl.materialDiffuse[2];
        float diffuseAlpha = colorMaterial ? sourceAlpha : ogl.materialDiffuse[3];
        resolveEyeNormal(normalVectorTemp, vertexIndex, backFace);
        if (ogl.lastError != com.nttdocomo.ui.ogl.GraphicsOGL.GL_NO_ERROR) {
            return 0;
        }
        return computeLightingColor(eyePosition, normalVectorTemp.x, normalVectorTemp.y, normalVectorTemp.z,
                ambientRed, ambientGreen, ambientBlue,
                diffuseRed, diffuseGreen, diffuseBlue, diffuseAlpha,
                ogl.materialSpecular[0], ogl.materialSpecular[1], ogl.materialSpecular[2],
                ogl.materialEmission[0], ogl.materialEmission[1], ogl.materialEmission[2],
                ogl.materialShininess);
    }

    private void transformVertex(ClipVector target, ClipVector eyeTarget, float x, float y, float z, int vertexIndex, boolean useAcrodeaMatrices) {
        if (ogl.usesMatrixPalette()) {
            float eyeX = 0f;
            float eyeY = 0f;
            float eyeZ = 0f;
            float eyeW = 0f;
            int unitCount = Math.min(
                    Math.min(ogl.matrixIndexPointer.size(), ogl.weightPointer.size()),
                    OGL_MAX_VERTEX_UNITS);
            boolean weighted = false;
            for (int unit = 0; unit < unitCount; unit++) {
                int matrixIndex = readUnsignedByteComponent(ogl.matrixIndexPointer, vertexIndex, unit);
                if (ogl.lastError != com.nttdocomo.ui.ogl.GraphicsOGL.GL_NO_ERROR) {
                    return;
                }
                if (matrixIndex < 0 || matrixIndex >= OGL_MAX_PALETTE_MATRICES) {
                    ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_VALUE;
                    return;
                }
                float weight = readFloatComponent(ogl.weightPointer, vertexIndex, unit);
                if (ogl.lastError != com.nttdocomo.ui.ogl.GraphicsOGL.GL_NO_ERROR) {
                    return;
                }
                if (Math.abs(weight) < 0.000001f) {
                    continue;
                }
                multiply(clipVectorTemp, ogl.paletteMatrices[matrixIndex], x, y, z, 1f);
                eyeX += clipVectorTemp.x * weight;
                eyeY += clipVectorTemp.y * weight;
                eyeZ += clipVectorTemp.z * weight;
                eyeW += clipVectorTemp.w * weight;
                weighted = true;
            }
            if (weighted) {
                eyeTarget.set(eyeX, eyeY, eyeZ, eyeW);
                multiply(target, ogl.projectionMatrix, eyeX, eyeY, eyeZ, eyeW);
                return;
            }
        }
        if (useAcrodeaMatrices) {
            multiply(clipVectorTemp, ogl.acrodeaWorldMatrix, x, y, z, 1f);
            eyeTarget.set(clipVectorTemp.x, clipVectorTemp.y, clipVectorTemp.z, clipVectorTemp.w);
            multiply(target, ogl.acrodeaCameraMatrix, clipVectorTemp.x, clipVectorTemp.y, clipVectorTemp.z, clipVectorTemp.w);
            return;
        }
        multiply(eyeTarget, ogl.modelViewMatrix, x, y, z, 1f);
        multiply(target, ogl.projectionMatrix, eyeTarget.x, eyeTarget.y, eyeTarget.z, eyeTarget.w);
    }

    private void transformNormal(ClipVector target, float nx, float ny, float nz, int vertexIndex) {
        if (ogl.usesMatrixPalette()) {
            float eyeNx = 0f;
            float eyeNy = 0f;
            float eyeNz = 0f;
            int unitCount = Math.min(
                    Math.min(ogl.matrixIndexPointer.size(), ogl.weightPointer.size()),
                    OGL_MAX_VERTEX_UNITS);
            boolean weighted = false;
            for (int unit = 0; unit < unitCount; unit++) {
                int matrixIndex = readUnsignedByteComponent(ogl.matrixIndexPointer, vertexIndex, unit);
                if (ogl.lastError != com.nttdocomo.ui.ogl.GraphicsOGL.GL_NO_ERROR) {
                    return;
                }
                float weight = readFloatComponent(ogl.weightPointer, vertexIndex, unit);
                if (ogl.lastError != com.nttdocomo.ui.ogl.GraphicsOGL.GL_NO_ERROR) {
                    return;
                }
                if (Math.abs(weight) < 0.000001f) {
                    continue;
                }
                transformNormalByMatrix(ogl.paletteMatrices[matrixIndex], nx, ny, nz, clipVectorTemp);
                eyeNx += clipVectorTemp.x * weight;
                eyeNy += clipVectorTemp.y * weight;
                eyeNz += clipVectorTemp.z * weight;
                weighted = true;
            }
            if (weighted) {
                target.set(eyeNx, eyeNy, eyeNz, 0f);
                return;
            }
        }
        transformNormalByMatrix(ogl.modelViewMatrix, nx, ny, nz, target);
    }

    private void transformNormalByMatrix(float[] matrix, float nx, float ny, float nz, ClipVector target) {
        if (matrix == null) {
            target.set(nx, ny, nz, 0f);
            return;
        }
        float a00 = matrix[0];
        float a01 = matrix[4];
        float a02 = matrix[8];
        float a10 = matrix[1];
        float a11 = matrix[5];
        float a12 = matrix[9];
        float a20 = matrix[2];
        float a21 = matrix[6];
        float a22 = matrix[10];
        float c00 = (a11 * a22) - (a12 * a21);
        float c01 = (a02 * a21) - (a01 * a22);
        float c02 = (a01 * a12) - (a02 * a11);
        float c10 = (a12 * a20) - (a10 * a22);
        float c11 = (a00 * a22) - (a02 * a20);
        float c12 = (a02 * a10) - (a00 * a12);
        float c20 = (a10 * a21) - (a11 * a20);
        float c21 = (a01 * a20) - (a00 * a21);
        float c22 = (a00 * a11) - (a01 * a10);
        float determinant = (a00 * c00) + (a01 * c10) + (a02 * c20);
        if (Math.abs(determinant) < 0.000001f) {
            target.set(nx, ny, nz, 0f);
            return;
        }
        float reciprocalDeterminant = 1f / determinant;
        target.set(
                ((c00 * nx) + (c10 * ny) + (c20 * nz)) * reciprocalDeterminant,
                ((c01 * nx) + (c11 * ny) + (c21 * nz)) * reciprocalDeterminant,
                ((c02 * nx) + (c12 * ny) + (c22 * nz)) * reciprocalDeterminant,
                0f
        );
        if (ogl.normalizeEnabled || ogl.rescaleNormalEnabled) {
            float length = vectorLength(target.x, target.y, target.z);
            if (length > 0.000001f) {
                float inverseLength = 1f / length;
                target.x *= inverseLength;
                target.y *= inverseLength;
                target.z *= inverseLength;
            }
        }
    }

    private void resolveEyeNormal(ClipVector target, int vertexIndex, boolean backFace) {
        float nx;
        float ny;
        float nz;
        if (!ogl.normalArrayEnabled || ogl.normalPointer == null) {
            nx = ogl.currentNormal[0];
            ny = ogl.currentNormal[1];
            nz = ogl.currentNormal[2];
        } else {
            nx = readNormalComponent(ogl.normalPointer, vertexIndex, 0);
            if (ogl.lastError != com.nttdocomo.ui.ogl.GraphicsOGL.GL_NO_ERROR) {
                target.set(0f, 0f, 1f, 0f);
                return;
            }
            ny = readNormalComponent(ogl.normalPointer, vertexIndex, 1);
            if (ogl.lastError != com.nttdocomo.ui.ogl.GraphicsOGL.GL_NO_ERROR) {
                target.set(0f, 0f, 1f, 0f);
                return;
            }
            nz = readNormalComponent(ogl.normalPointer, vertexIndex, 2);
            if (ogl.lastError != com.nttdocomo.ui.ogl.GraphicsOGL.GL_NO_ERROR) {
                target.set(0f, 0f, 1f, 0f);
                return;
            }
        }
        transformNormal(target, nx, ny, nz, vertexIndex);
        float length = vectorLength(target.x, target.y, target.z);
        if (length <= 0.000001f) {
            target.set(0f, 0f, 0f, 0f);
            return;
        }
        float inverseLength = 1f / length;
        target.x *= inverseLength;
        target.y *= inverseLength;
        target.z *= inverseLength;
        if (backFace) {
            target.x = -target.x;
            target.y = -target.y;
            target.z = -target.z;
        }
    }

    private int computeLightingColor(ClipVector eyePosition, float normalX, float normalY, float normalZ,
                                     float ambientRed, float ambientGreen, float ambientBlue,
                                     float diffuseRed, float diffuseGreen, float diffuseBlue, float diffuseAlpha,
                                     float specularRed, float specularGreen, float specularBlue,
                                     float emissionRed, float emissionGreen, float emissionBlue,
                                     float shininess) {
        float red = emissionRed + (ambientRed * ogl.lightModelAmbient[0]);
        float green = emissionGreen + (ambientGreen * ogl.lightModelAmbient[1]);
        float blue = emissionBlue + (ambientBlue * ogl.lightModelAmbient[2]);
        for (int i = 0; i < ogl.lights.length; i++) {
            OglLight light = ogl.lights[i];
            if (!ogl.lightEnabled[i]) {
                continue;
            }
            float lightX;
            float lightY;
            float lightZ;
            float attenuation;
            float lightDistance = 0f;
            if (Math.abs(light.position[3]) < 0.000001f) {
                float length = vectorLength(light.position[0], light.position[1], light.position[2]);
                if (length <= 0.000001f) {
                    lightX = 0f;
                    lightY = 0f;
                    lightZ = 0f;
                } else {
                    float inverseLength = 1f / length;
                    lightX = light.position[0] * inverseLength;
                    lightY = light.position[1] * inverseLength;
                    lightZ = light.position[2] * inverseLength;
                }
                attenuation = 1f;
            } else {
                float lx = light.position[0] - eyePosition.x;
                float ly = light.position[1] - eyePosition.y;
                float lz = light.position[2] - eyePosition.z;
                lightDistance = vectorLength(lx, ly, lz);
                if (lightDistance > 0.000001f) {
                    float inverseDistance = 1f / lightDistance;
                    lightX = lx * inverseDistance;
                    lightY = ly * inverseDistance;
                    lightZ = lz * inverseDistance;
                } else {
                    lightX = 0f;
                    lightY = 0f;
                    lightZ = 0f;
                }
                attenuation = 1f / Math.max(0.000001f, light.constantAttenuation
                        + (light.linearAttenuation * lightDistance)
                        + (light.quadraticAttenuation * lightDistance * lightDistance));
            }
            float spotlight = 1f;
            if (light.spotCutoff != 180f) {
                float spotLength = vectorLength(light.spotDirection[0], light.spotDirection[1], light.spotDirection[2]);
                float spotX = 0f;
                float spotY = 0f;
                float spotZ = 0f;
                if (spotLength > 0.000001f) {
                    float inverseSpotLength = 1f / spotLength;
                    spotX = light.spotDirection[0] * inverseSpotLength;
                    spotY = light.spotDirection[1] * inverseSpotLength;
                    spotZ = light.spotDirection[2] * inverseSpotLength;
                }
                float spotCos = dot(-lightX, -lightY, -lightZ, spotX, spotY, spotZ);
                float cutoffCos = (float) Math.cos(Math.toRadians(light.spotCutoff));
                if (spotCos < cutoffCos) {
                    spotlight = 0f;
                } else {
                    spotlight = (float) Math.pow(Math.max(0f, spotCos), light.spotExponent);
                }
            }
            float scale = attenuation * spotlight;
            if (scale <= 0f) {
                continue;
            }
            red += ambientRed * light.ambient[0] * scale;
            green += ambientGreen * light.ambient[1] * scale;
            blue += ambientBlue * light.ambient[2] * scale;

            float diffuseFactor = Math.max(0f, dot(normalX, normalY, normalZ, lightX, lightY, lightZ));
            red += diffuseRed * light.diffuse[0] * diffuseFactor * scale;
            green += diffuseGreen * light.diffuse[1] * diffuseFactor * scale;
            blue += diffuseBlue * light.diffuse[2] * diffuseFactor * scale;

            if (diffuseFactor > 0f && shininess > 0f
                    && (specularRed != 0f || specularGreen != 0f || specularBlue != 0f)
                    && (light.specular[0] != 0f || light.specular[1] != 0f || light.specular[2] != 0f)) {
                float halfX = lightX;
                float halfY = lightY;
                float halfZ = lightZ + 1f;
                float halfLength = vectorLength(halfX, halfY, halfZ);
                if (halfLength > 0.000001f) {
                    float inverseHalfLength = 1f / halfLength;
                    halfX *= inverseHalfLength;
                    halfY *= inverseHalfLength;
                    halfZ *= inverseHalfLength;
                }
                float specularFactor = (float) Math.pow(Math.max(0f,
                        dot(normalX, normalY, normalZ, halfX, halfY, halfZ)), shininess);
                red += specularRed * light.specular[0] * specularFactor * scale;
                green += specularGreen * light.specular[1] * specularFactor * scale;
                blue += specularBlue * light.specular[2] * specularFactor * scale;
            }
        }
        return packColor(red, green, blue, diffuseAlpha);
    }

    private float readFloatComponent(OglPointer pointer, int vertexIndex, int componentIndex) {
        int byteOffset = pointer.componentByteOffset(vertexIndex, componentIndex);
        if (pointer.bufferObject() != null) {
            return readFloat(pointer.bufferObject().data(), byteOffset);
        }
        if (pointer.pointer() instanceof FloatBuffer floatBuffer) {
            if ((byteOffset & 3) != 0) {
                ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_VALUE;
                return 0f;
            }
            int elementIndex = DirectBufferFactory.getSegmentOffset(floatBuffer) + (byteOffset / 4);
            if (elementIndex < 0 || elementIndex >= DirectBufferFactory.getSegmentOffset(floatBuffer)
                    + DirectBufferFactory.getSegmentLength(floatBuffer)) {
                ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_VALUE;
                return 0f;
            }
            return DirectBufferFactory.getFloat(floatBuffer, elementIndex);
        }
        if (pointer.pointer() instanceof ByteBuffer byteBuffer) {
            byte[] raw = new byte[4];
            int baseIndex = DirectBufferFactory.getSegmentOffset(byteBuffer) + byteOffset;
            if (baseIndex < 0 || baseIndex + raw.length > DirectBufferFactory.getSegmentOffset(byteBuffer)
                    + DirectBufferFactory.getSegmentLength(byteBuffer)) {
                ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_VALUE;
                return 0f;
            }
            byteBuffer.get(baseIndex, raw, 0, raw.length);
            return readFloat(raw, 0);
        }
        ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_VALUE;
        return 0f;
    }

    private float readNormalComponent(OglPointer pointer, int vertexIndex, int componentIndex) {
        int byteOffset = pointer.componentByteOffset(vertexIndex, componentIndex);
        if (pointer.bufferObject() != null) {
            return readTypedFloat(pointer.type(), pointer.bufferObject().data(), byteOffset);
        }
        return switch (pointer.type()) {
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_FLOAT -> readFloatComponent(pointer, vertexIndex, componentIndex);
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_BYTE -> readDirectByteComponent(pointer, byteOffset);
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_SHORT -> readDirectShortComponent(pointer, byteOffset);
            default -> {
                ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_ENUM;
                yield 0f;
            }
        };
    }

    private int readUnsignedByteComponent(OglPointer pointer, int vertexIndex, int componentIndex) {
        int byteOffset = pointer.componentByteOffset(vertexIndex, componentIndex);
        if (pointer.bufferObject() != null) {
            return readUnsignedByte(pointer.bufferObject().data(), byteOffset);
        }
        if (pointer.pointer() instanceof ByteBuffer byteBuffer) {
            int baseIndex = DirectBufferFactory.getSegmentOffset(byteBuffer) + byteOffset;
            if (baseIndex < 0 || baseIndex >= DirectBufferFactory.getSegmentOffset(byteBuffer)
                    + DirectBufferFactory.getSegmentLength(byteBuffer)) {
                ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_VALUE;
                return 0;
            }
            byte[] raw = new byte[1];
            byteBuffer.get(baseIndex, raw, 0, 1);
            return raw[0] & 0xFF;
        }
        ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_VALUE;
        return 0;
    }

    private float readDirectByteComponent(OglPointer pointer, int byteOffset) {
        if (!(pointer.pointer() instanceof ByteBuffer byteBuffer)) {
            ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_VALUE;
            return 0f;
        }
        int baseIndex = DirectBufferFactory.getSegmentOffset(byteBuffer) + byteOffset;
        if (baseIndex < 0 || baseIndex >= DirectBufferFactory.getSegmentOffset(byteBuffer)
                + DirectBufferFactory.getSegmentLength(byteBuffer)) {
            ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_VALUE;
            return 0f;
        }
        byte[] raw = new byte[1];
        byteBuffer.get(baseIndex, raw, 0, 1);
        return raw[0];
    }

    private float readDirectShortComponent(OglPointer pointer, int byteOffset) {
        if (pointer.pointer() instanceof ShortBuffer shortBuffer) {
            if ((byteOffset & 1) != 0) {
                ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_VALUE;
                return 0f;
            }
            int elementIndex = DirectBufferFactory.getSegmentOffset(shortBuffer) + (byteOffset / 2);
            if (elementIndex < 0 || elementIndex >= DirectBufferFactory.getSegmentOffset(shortBuffer)
                    + DirectBufferFactory.getSegmentLength(shortBuffer)) {
                ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_VALUE;
                return 0f;
            }
            return DirectBufferFactory.getShort(shortBuffer, elementIndex);
        }
        if (pointer.pointer() instanceof ByteBuffer byteBuffer) {
            int baseIndex = DirectBufferFactory.getSegmentOffset(byteBuffer) + byteOffset;
            if (baseIndex < 0 || baseIndex + 1 >= DirectBufferFactory.getSegmentOffset(byteBuffer)
                    + DirectBufferFactory.getSegmentLength(byteBuffer)) {
                ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_VALUE;
                return 0f;
            }
            byte[] raw = new byte[2];
            byteBuffer.get(baseIndex, raw, 0, raw.length);
            return readSignedShort(raw, 0);
        }
        ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_VALUE;
        return 0f;
    }

    private float readTypedFloat(int type, byte[] data, int byteOffset) {
        return switch (type) {
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_FLOAT -> readFloat(data, byteOffset);
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_BYTE -> readSignedByte(data, byteOffset);
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_SHORT -> readSignedShort(data, byteOffset);
            default -> {
                ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_ENUM;
                yield 0f;
            }
        };
    }

    private static int readUnsignedShort(byte[] data, int byteOffset) {
        if (byteOffset < 0 || byteOffset + 1 >= data.length) {
            return -1;
        }
        return (data[byteOffset] & 0xFF) | ((data[byteOffset + 1] & 0xFF) << 8);
    }

    private float readSignedByte(byte[] data, int byteOffset) {
        if (byteOffset < 0 || byteOffset >= data.length) {
            ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_VALUE;
            return 0f;
        }
        return data[byteOffset];
    }

    private float readSignedShort(byte[] data, int byteOffset) {
        if (byteOffset < 0 || byteOffset + 1 >= data.length) {
            ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_VALUE;
            return 0f;
        }
        return (short) ((data[byteOffset] & 0xFF) | ((data[byteOffset + 1] & 0xFF) << 8));
    }

    private int readUnsignedByte(byte[] data, int byteOffset) {
        if (byteOffset < 0 || byteOffset >= data.length) {
            ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_VALUE;
            return 0;
        }
        return data[byteOffset] & 0xFF;
    }

    private float readFloat(byte[] data, int byteOffset) {
        if (byteOffset < 0 || byteOffset + 3 >= data.length) {
            ogl.lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_INVALID_VALUE;
            return 0f;
        }
        int bits = (data[byteOffset] & 0xFF)
                | ((data[byteOffset + 1] & 0xFF) << 8)
                | ((data[byteOffset + 2] & 0xFF) << 16)
                | ((data[byteOffset + 3] & 0xFF) << 24);
        return Float.intBitsToFloat(bits);
    }

    private void drawRasterTriangle(RasterVertex v0, RasterVertex v1, RasterVertex v2, int[] pixels, float[] depthBuffer,
                                    Rectangle clip, int width, int height, RasterVertex[] clipInput,
                                    RasterVertex[] clipScratch, RasterVertex project0, RasterVertex project1,
                                    RasterVertex project2) {
        RasterVertex[] input = clipInput;
        RasterVertex[] scratch = clipScratch;
        input[0].copyFrom(v0);
        input[1].copyFrom(v1);
        input[2].copyFrom(v2);
        int count = 3;
        for (int plane = 0; plane < 6 && count > 0; plane++) {
            count = clipPolygonAgainstPlane(input, count, scratch, plane);
            RasterVertex[] swap = input;
            input = scratch;
            scratch = swap;
        }
        if (count < 3) {
            return;
        }
        RasterVertex first = input[0];
        for (int i = 1; i + 1 < count; i++) {
            rasterizeProjectedTriangle(projectClipVertex(project0, first),
                    projectClipVertex(project1, input[i]),
                    projectClipVertex(project2, input[i + 1]),
                    pixels,
                    depthBuffer,
                    clip,
                    width,
                    height);
        }
    }

    private int clipPolygonAgainstPlane(RasterVertex[] input, int inputCount, RasterVertex[] output, int plane) {
        int outputCount = 0;
        RasterVertex previous = input[inputCount - 1];
        float previousDistance = clipDistance(previous, plane);
        boolean previousInside = previousDistance >= 0f;
        for (int i = 0; i < inputCount; i++) {
            RasterVertex current = input[i];
            float currentDistance = clipDistance(current, plane);
            boolean currentInside = currentDistance >= 0f;
            if (currentInside != previousInside) {
                float t = previousDistance / (previousDistance - currentDistance);
                output[outputCount++].interpolateFrom(previous, current, t);
            }
            if (currentInside) {
                output[outputCount++].copyFrom(current);
            }
            previous = current;
            previousDistance = currentDistance;
            previousInside = currentInside;
        }
        return outputCount;
    }

    private float clipDistance(RasterVertex vertex, int plane) {
        return switch (plane) {
            case 0 -> vertex.clipX + vertex.clipW;
            case 1 -> vertex.clipW - vertex.clipX;
            case 2 -> vertex.clipY + vertex.clipW;
            case 3 -> vertex.clipW - vertex.clipY;
            case 4 -> vertex.clipZ + vertex.clipW;
            case 5 -> vertex.clipW - vertex.clipZ;
            default -> 0f;
        };
    }

    private RasterVertex projectClipVertex(RasterVertex projected, RasterVertex clipVertex) {
        float w = Math.abs(clipVertex.clipW) < 0.000001f
                ? (clipVertex.clipW < 0f ? -0.000001f : 0.000001f)
                : clipVertex.clipW;
        float ndcX = clipVertex.clipX / w;
        float ndcY = clipVertex.clipY / w;
        float ndcZ = clipVertex.clipZ / w;
        float windowDepth = ogl.depthRangeNear + (((ndcZ + 1f) * 0.5f) * (ogl.depthRangeFar - ogl.depthRangeNear));
        float depth = 1f - windowDepth;
        float reciprocalW = 1f / Math.max(0.000001f, Math.abs(w));
        projected.set(
                clipVertex.clipX,
                clipVertex.clipY,
                clipVertex.clipZ,
                clipVertex.clipW,
                viewportX(ndcX),
                viewportY(ndcY),
                depth,
                reciprocalW,
                clipVertex.u,
                clipVertex.v,
                clipVertex.color,
                clipVertex.backColor
        );
        return projected;
    }

    private static RasterVertex[] createRasterVertexArray(int length) {
        RasterVertex[] vertices = new RasterVertex[length];
        for (int i = 0; i < vertices.length; i++) {
            vertices[i] = new RasterVertex();
        }
        return vertices;
    }

    private float viewportX(float ndcX) {
        return ogl.viewportX + (((ndcX + 1f) * 0.5f) * ogl.viewportWidth);
    }

    private float viewportY(float ndcY) {
        float bottomLeftY = ogl.viewportY + (((ndcY + 1f) * 0.5f) * ogl.viewportHeight);
        return surface.height() - bottomLeftY;
    }

    private void rasterizeProjectedTriangle(RasterVertex v0, RasterVertex v1, RasterVertex v2, int[] pixels, float[] depthBuffer,
                                            Rectangle clip, int width, int height) {
        if (isCulled(v0, v1, v2)) {
            return;
        }
        float area = edge(v0.x, v0.y, v1.x, v1.y, v2.x, v2.y);
        if (Math.abs(area) < 0.0001f) {
            return;
        }
        boolean useBackColor = ogl.lightModelTwoSide && !isFrontFacing(v0, v1, v2);
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
        boolean texturing = ogl.textureEnabled();
        OglTexture texture = texturing ? ogl.boundTexture() : null;
        boolean depthEnabled = ogl.depthEnabled();
        boolean blendEnabled = ogl.blendCapEnabled;
        boolean depthWriteEnabled = ogl.depthMask && depthEnabled;
        float startX = minX + 0.5f;
        float startY = minY + 0.5f;
        float edge0Row = edge(v1.x, v1.y, v2.x, v2.y, startX, startY);
        float edge1Row = edge(v2.x, v2.y, v0.x, v0.y, startX, startY);
        float edge2Row = edge(v0.x, v0.y, v1.x, v1.y, startX, startY);
        float edge0StepX = v2.y - v1.y;
        float edge1StepX = v0.y - v2.y;
        float edge2StepX = v1.y - v0.y;
        float edge0StepY = v1.x - v2.x;
        float edge1StepY = v2.x - v0.x;
        float edge2StepY = v0.x - v1.x;
        for (int y = minY; y <= maxY; y++) {
            float edge0Value = edge0Row;
            float edge1Value = edge1Row;
            float edge2Value = edge2Row;
            for (int x = minX; x <= maxX; x++) {
                float w0 = edge0Value * inverseArea;
                float w1 = edge1Value * inverseArea;
                float w2 = edge2Value * inverseArea;
                edge0Value += edge0StepX;
                edge1Value += edge1StepX;
                edge2Value += edge2StepX;
                if (w0 < 0f || w1 < 0f || w2 < 0f) {
                    continue;
                }
                float depth = (w0 * v0.depth) + (w1 * v1.depth) + (w2 * v2.depth);
                int offset = (y * width) + x;
                if (!passesDepth(depth, depthBuffer[offset])) {
                    continue;
                }
                float denominator = Math.max(0.000001f,
                        (w0 * reciprocalW0) + (w1 * reciprocalW1) + (w2 * reciprocalW2));
                float u;
                float v;
                if (texturing) {
                    u = ((w0 * v0.u * reciprocalW0) + (w1 * v1.u * reciprocalW1) + (w2 * v2.u * reciprocalW2)) / denominator;
                    v = ((w0 * v0.v * reciprocalW0) + (w1 * v1.v * reciprocalW1) + (w2 * v2.v * reciprocalW2)) / denominator;
                } else {
                    u = 0f;
                    v = 0f;
                }
                int primaryColor = interpolateColor(v0, v1, v2, w0, w1, w2, denominator, useBackColor);
                int sampled = texturing ? texture.sample(u, v) : 0xFFFFFFFF;
                int fragmentColor = applyTextureEnvironment(primaryColor, sampled, texture);
                if (!passesAlphaTest(fragmentColor)) {
                    continue;
                }
                int blended = blendEnabled
                        ? blend(fragmentColor, pixels[offset], ogl.blendSrcFactor, ogl.blendDstFactor)
                        : fragmentColor;
                pixels[offset] = blended;
                if (depthWriteEnabled) {
                    depthBuffer[offset] = depth;
                }
            }
            edge0Row += edge0StepY;
            edge1Row += edge1StepY;
            edge2Row += edge2StepY;
        }
    }

    private void drawLineLoop(int first, int primitiveCount, OglIndexSource elementIndices, Rectangle clip, int width, int height,
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
        delegate.setColor(new Color(firstVertex.color, true));
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
        if (!ogl.cullFaceEnabled) {
            return false;
        }
        boolean frontFacing = isFrontFacing(v0, v1, v2);
        return switch (ogl.cullFace) {
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_FRONT -> frontFacing;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_FRONT_AND_BACK -> true;
            default -> !frontFacing;
        };
    }

    private boolean isFrontFacing(RasterVertex v0, RasterVertex v1, RasterVertex v2) {
        float area = edge(v0.x, v0.y, v1.x, v1.y, v2.x, v2.y);
        boolean ccw = area > 0f;
        return ogl.frontFace == com.nttdocomo.ui.ogl.GraphicsOGL.GL_CCW ? ccw : !ccw;
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
        if (!ogl.alphaTestEnabled) {
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

    private int interpolateColor(RasterVertex v0, RasterVertex v1, RasterVertex v2,
                                 float w0, float w1, float w2, float denominator, boolean useBackColor) {
        int c0 = useBackColor ? v0.backColor : v0.color;
        int c1 = useBackColor ? v1.backColor : v1.color;
        int c2 = useBackColor ? v2.backColor : v2.color;
        if (ogl.shadeModel == com.nttdocomo.ui.ogl.GraphicsOGL.GL_FLAT) {
            return c2;
        }
        float red = interpolateChannel((c0 >>> 16) & 0xFF, (c1 >>> 16) & 0xFF, (c2 >>> 16) & 0xFF,
                w0, w1, w2, v0.reciprocalW, v1.reciprocalW, v2.reciprocalW, denominator);
        float green = interpolateChannel((c0 >>> 8) & 0xFF, (c1 >>> 8) & 0xFF, (c2 >>> 8) & 0xFF,
                w0, w1, w2, v0.reciprocalW, v1.reciprocalW, v2.reciprocalW, denominator);
        float blue = interpolateChannel(c0 & 0xFF, c1 & 0xFF, c2 & 0xFF,
                w0, w1, w2, v0.reciprocalW, v1.reciprocalW, v2.reciprocalW, denominator);
        float alpha = interpolateChannel((c0 >>> 24) & 0xFF, (c1 >>> 24) & 0xFF, (c2 >>> 24) & 0xFF,
                w0, w1, w2, v0.reciprocalW, v1.reciprocalW, v2.reciprocalW, denominator);
        return (clamp(Math.round(alpha), 0, 255) << 24)
                | (clamp(Math.round(red), 0, 255) << 16)
                | (clamp(Math.round(green), 0, 255) << 8)
                | clamp(Math.round(blue), 0, 255);
    }

    private float interpolateChannel(float c0, float c1, float c2,
                                     float w0, float w1, float w2,
                                     float reciprocalW0, float reciprocalW1, float reciprocalW2,
                                     float denominator) {
        return ((w0 * c0 * reciprocalW0) + (w1 * c1 * reciprocalW1) + (w2 * c2 * reciprocalW2)) / denominator;
    }

    private int applyTextureEnvironment(int primaryColor, int sampledColor, OglTexture texture) {
        if (texture == null) {
            return primaryColor;
        }
        int baseFormat = texture.baseFormat();
        float pr = packedComponent(primaryColor, 0);
        float pg = packedComponent(primaryColor, 1);
        float pb = packedComponent(primaryColor, 2);
        float pa = packedComponent(primaryColor, 3);
        float tr = textureComponent(sampledColor, 0, baseFormat);
        float tg = textureComponent(sampledColor, 1, baseFormat);
        float tb = textureComponent(sampledColor, 2, baseFormat);
        float ta = textureComponent(sampledColor, 3, baseFormat);
        float er = packedComponent(ogl.textureEnvColor, 0);
        float eg = packedComponent(ogl.textureEnvColor, 1);
        float eb = packedComponent(ogl.textureEnvColor, 2);
        return switch (ogl.textureEnvMode) {
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_REPLACE ->
                    applyReplaceTextureFunction(pr, pg, pb, pa, tr, tg, tb, ta, baseFormat);
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_MODULATE ->
                    applyModulateTextureFunction(pr, pg, pb, pa, tr, tg, tb, ta, baseFormat);
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_DECAL ->
                    applyDecalTextureFunction(pr, pg, pb, pa, tr, tg, tb, ta, baseFormat);
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_BLEND ->
                    applyBlendTextureFunction(pr, pg, pb, pa, tr, tg, tb, ta, er, eg, eb, baseFormat);
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_ADD ->
                    applyAddTextureFunction(pr, pg, pb, pa, tr, tg, tb, ta, baseFormat);
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_COMBINE ->
                    applyCombineTextureEnvironment(primaryColor, sampledColor, baseFormat);
            default -> primaryColor;
        };
    }

    private int applyReplaceTextureFunction(float pr, float pg, float pb, float pa,
                                            float tr, float tg, float tb, float ta, int baseFormat) {
        return switch (baseFormat) {
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_ALPHA -> packColor(pr, pg, pb, ta);
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_LUMINANCE -> packColor(tr, tg, tb, pa);
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_LUMINANCE_ALPHA,
                    com.nttdocomo.ui.ogl.GraphicsOGL.GL_RGBA -> packColor(tr, tg, tb, ta);
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_RGB -> packColor(tr, tg, tb, pa);
            default -> packColor(tr, tg, tb, ta);
        };
    }

    private int applyModulateTextureFunction(float pr, float pg, float pb, float pa,
                                             float tr, float tg, float tb, float ta, int baseFormat) {
        return switch (baseFormat) {
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_ALPHA -> packColor(pr, pg, pb, pa * ta);
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_LUMINANCE,
                    com.nttdocomo.ui.ogl.GraphicsOGL.GL_RGB -> packColor(pr * tr, pg * tg, pb * tb, pa);
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_LUMINANCE_ALPHA,
                    com.nttdocomo.ui.ogl.GraphicsOGL.GL_RGBA -> packColor(pr * tr, pg * tg, pb * tb, pa * ta);
            default -> packColor(pr * tr, pg * tg, pb * tb, pa * ta);
        };
    }

    private int applyDecalTextureFunction(float pr, float pg, float pb, float pa,
                                          float tr, float tg, float tb, float ta, int baseFormat) {
        return switch (baseFormat) {
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_RGB -> packColor(tr, tg, tb, pa);
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_RGBA -> packColor(
                    (pr * (1f - ta)) + (tr * ta),
                    (pg * (1f - ta)) + (tg * ta),
                    (pb * (1f - ta)) + (tb * ta),
                    pa);
            default -> packColor(pr, pg, pb, pa);
        };
    }

    private int applyBlendTextureFunction(float pr, float pg, float pb, float pa,
                                          float tr, float tg, float tb, float ta,
                                          float er, float eg, float eb, int baseFormat) {
        return switch (baseFormat) {
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_ALPHA -> packColor(pr, pg, pb, pa * ta);
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_LUMINANCE,
                    com.nttdocomo.ui.ogl.GraphicsOGL.GL_RGB -> packColor(
                    (pr * (1f - tr)) + (er * tr),
                    (pg * (1f - tg)) + (eg * tg),
                    (pb * (1f - tb)) + (eb * tb),
                    pa);
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_LUMINANCE_ALPHA,
                    com.nttdocomo.ui.ogl.GraphicsOGL.GL_RGBA -> packColor(
                    (pr * (1f - tr)) + (er * tr),
                    (pg * (1f - tg)) + (eg * tg),
                    (pb * (1f - tb)) + (eb * tb),
                    pa * ta);
            default -> packColor(
                    (pr * (1f - tr)) + (er * tr),
                    (pg * (1f - tg)) + (eg * tg),
                    (pb * (1f - tb)) + (eb * tb),
                    pa * ta);
        };
    }

    private int applyAddTextureFunction(float pr, float pg, float pb, float pa,
                                        float tr, float tg, float tb, float ta, int baseFormat) {
        return switch (baseFormat) {
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_ALPHA -> packColor(pr, pg, pb, pa * ta);
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_LUMINANCE,
                    com.nttdocomo.ui.ogl.GraphicsOGL.GL_RGB -> packColor(pr + tr, pg + tg, pb + tb, pa);
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_LUMINANCE_ALPHA,
                    com.nttdocomo.ui.ogl.GraphicsOGL.GL_RGBA -> packColor(pr + tr, pg + tg, pb + tb, pa * ta);
            default -> packColor(pr + tr, pg + tg, pb + tb, pa * ta);
        };
    }

    private int applyCombineTextureEnvironment(int primaryColor, int sampledColor, int baseFormat) {
        float r = combineRgbComponent(0, primaryColor, sampledColor, baseFormat);
        float g = combineRgbComponent(1, primaryColor, sampledColor, baseFormat);
        float b = combineRgbComponent(2, primaryColor, sampledColor, baseFormat);
        float alpha0 = textureAlphaArgument(0, primaryColor, sampledColor, baseFormat);
        float alpha1 = textureAlphaArgument(1, primaryColor, sampledColor, baseFormat);
        float alpha2 = textureAlphaArgument(2, primaryColor, sampledColor, baseFormat);
        float a = switch (ogl.combineAlpha) {
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_REPLACE -> alpha0;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_MODULATE -> alpha0 * alpha1;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_ADD -> alpha0 + alpha1;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_ADD_SIGNED -> alpha0 + alpha1 - 0.5f;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_INTERPOLATE -> (alpha0 * alpha2) + (alpha1 * (1f - alpha2));
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_SUBTRACT -> alpha0 - alpha1;
            default -> textureComponent(sampledColor, 3, baseFormat);
        };
        return packColor(clampUnit(r * ogl.rgbScale), clampUnit(g * ogl.rgbScale),
                clampUnit(b * ogl.rgbScale), clampUnit(a * ogl.alphaScale));
    }

    private float combineRgbComponent(int channel, int primaryColor, int sampledColor, int baseFormat) {
        float arg0 = textureRgbArgument(0, channel, primaryColor, sampledColor, baseFormat);
        float arg1 = textureRgbArgument(1, channel, primaryColor, sampledColor, baseFormat);
        float arg2 = textureRgbArgument(2, channel, primaryColor, sampledColor, baseFormat);
        return switch (ogl.combineRgb) {
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_REPLACE -> arg0;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_MODULATE -> arg0 * arg1;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_ADD -> arg0 + arg1;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_ADD_SIGNED -> arg0 + arg1 - 0.5f;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_INTERPOLATE -> (arg0 * arg2) + (arg1 * (1f - arg2));
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_SUBTRACT -> arg0 - arg1;
            default -> textureComponent(sampledColor, channel, baseFormat);
        };
    }

    private float textureRgbArgument(int index, int channel, int primaryColor, int sampledColor, int baseFormat) {
        int source = ogl.srcRgb[index];
        int operand = ogl.operandRgb[index];
        float component = textureSourceComponent(source, channel, primaryColor, sampledColor, baseFormat);
        float alpha = textureSourceComponent(source, 3, primaryColor, sampledColor, baseFormat);
        return switch (operand) {
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_SRC_ALPHA -> alpha;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_ONE_MINUS_SRC_ALPHA -> 1f - alpha;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_ONE_MINUS_SRC_COLOR -> 1f - component;
            default -> component;
        };
    }

    private float textureAlphaArgument(int index, int primaryColor, int sampledColor, int baseFormat) {
        float alpha = textureSourceComponent(ogl.srcAlpha[index], 3, primaryColor, sampledColor, baseFormat);
        return ogl.operandAlpha[index] == com.nttdocomo.ui.ogl.GraphicsOGL.GL_ONE_MINUS_SRC_ALPHA ? 1f - alpha : alpha;
    }

    private float textureSourceComponent(int source, int channel, int primaryColor, int sampledColor, int baseFormat) {
        return switch (source) {
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_TEXTURE -> textureComponent(sampledColor, channel, baseFormat);
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_CONSTANT -> packedComponent(ogl.textureEnvColor, channel);
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_PRIMARY_COLOR,
                    com.nttdocomo.ui.ogl.GraphicsOGL.GL_PREVIOUS -> packedComponent(primaryColor, channel);
            default -> packedComponent(primaryColor, channel);
        };
    }

    private static float textureComponent(int packed, int channel, int baseFormat) {
        return switch (baseFormat) {
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_ALPHA -> channel == 3 ? packedComponent(packed, 3) : 0f;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_LUMINANCE -> channel == 3 ? 1f : packedComponent(packed, 0);
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_LUMINANCE_ALPHA ->
                    channel == 3 ? packedComponent(packed, 3) : packedComponent(packed, 0);
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_RGB -> channel == 3 ? 1f : packedComponent(packed, channel);
            default -> packedComponent(packed, channel);
        };
    }

    private static float packedComponent(int packed, int channel) {
        int shift = switch (channel) {
            case 0 -> 16;
            case 1 -> 8;
            case 2 -> 0;
            default -> 24;
        };
        return ((packed >>> shift) & 0xFF) / 255f;
    }

    private float[] unpackColor(int packed) {
        return new float[]{
                ((packed >>> 16) & 0xFF) / 255f,
                ((packed >>> 8) & 0xFF) / 255f,
                (packed & 0xFF) / 255f,
                ((packed >>> 24) & 0xFF) / 255f
        };
    }

    private float vectorLength(float x, float y, float z) {
        return (float) Math.sqrt((x * x) + (y * y) + (z * z));
    }

    private float dot(float ax, float ay, float az, float bx, float by, float bz) {
        return (ax * bx) + (ay * by) + (az * bz);
    }

    private float clampUnit(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static int blend(int source, int destination, int srcFactor, int dstFactor) {
        float sourceR = ((source >>> 16) & 0xFF) / 255f;
        float sourceG = ((source >>> 8) & 0xFF) / 255f;
        float sourceB = (source & 0xFF) / 255f;
        float sourceA = ((source >>> 24) & 0xFF) / 255f;
        float destinationR = ((destination >>> 16) & 0xFF) / 255f;
        float destinationG = ((destination >>> 8) & 0xFF) / 255f;
        float destinationB = (destination & 0xFF) / 255f;
        float destinationA = ((destination >>> 24) & 0xFF) / 255f;
        float srcBlendR = blendFactorComponent(srcFactor, 0, sourceR, sourceG, sourceB, sourceA,
                destinationR, destinationG, destinationB, destinationA);
        float srcBlendG = blendFactorComponent(srcFactor, 1, sourceR, sourceG, sourceB, sourceA,
                destinationR, destinationG, destinationB, destinationA);
        float srcBlendB = blendFactorComponent(srcFactor, 2, sourceR, sourceG, sourceB, sourceA,
                destinationR, destinationG, destinationB, destinationA);
        float srcBlendA = blendFactorComponent(srcFactor, 3, sourceR, sourceG, sourceB, sourceA,
                destinationR, destinationG, destinationB, destinationA);
        float dstBlendR = blendFactorComponent(dstFactor, 0, sourceR, sourceG, sourceB, sourceA,
                destinationR, destinationG, destinationB, destinationA);
        float dstBlendG = blendFactorComponent(dstFactor, 1, sourceR, sourceG, sourceB, sourceA,
                destinationR, destinationG, destinationB, destinationA);
        float dstBlendB = blendFactorComponent(dstFactor, 2, sourceR, sourceG, sourceB, sourceA,
                destinationR, destinationG, destinationB, destinationA);
        float dstBlendA = blendFactorComponent(dstFactor, 3, sourceR, sourceG, sourceB, sourceA,
                destinationR, destinationG, destinationB, destinationA);
        return (clamp(Math.round(((sourceA * srcBlendA) + (destinationA * dstBlendA)) * 255f), 0, 255) << 24)
                | (clamp(Math.round(((sourceR * srcBlendR) + (destinationR * dstBlendR)) * 255f), 0, 255) << 16)
                | (clamp(Math.round(((sourceG * srcBlendG) + (destinationG * dstBlendG)) * 255f), 0, 255) << 8)
                | clamp(Math.round(((sourceB * srcBlendB) + (destinationB * dstBlendB)) * 255f), 0, 255);
    }

    private static float blendFactorComponent(int factor, int channel,
                                              float sourceR, float sourceG, float sourceB, float sourceA,
                                              float destinationR, float destinationG, float destinationB,
                                              float destinationA) {
        return switch (factor) {
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_ZERO -> 0f;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_ONE -> 1f;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_SRC_COLOR -> channelComponent(channel, sourceR, sourceG, sourceB, sourceA);
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_ONE_MINUS_SRC_COLOR ->
                    1f - channelComponent(channel, sourceR, sourceG, sourceB, sourceA);
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_DST_COLOR ->
                    channelComponent(channel, destinationR, destinationG, destinationB, destinationA);
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_ONE_MINUS_DST_COLOR ->
                    1f - channelComponent(channel, destinationR, destinationG, destinationB, destinationA);
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_SRC_ALPHA -> sourceA;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_ONE_MINUS_SRC_ALPHA -> 1f - sourceA;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_DST_ALPHA -> destinationA;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_ONE_MINUS_DST_ALPHA -> 1f - destinationA;
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_SRC_ALPHA_SATURATE ->
                    channel == 3 ? 1f : Math.min(sourceA, 1f - destinationA);
            default -> 1f;
        };
    }

    private static float channelComponent(int channel, float red, float green, float blue, float alpha) {
        return switch (channel) {
            case 0 -> red;
            case 1 -> green;
            case 2 -> blue;
            default -> alpha;
        };
    }

    private static boolean isValidBlendSrcFactor(int factor) {
        return switch (factor) {
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_ZERO,
                    com.nttdocomo.ui.ogl.GraphicsOGL.GL_ONE,
                    com.nttdocomo.ui.ogl.GraphicsOGL.GL_SRC_COLOR,
                    com.nttdocomo.ui.ogl.GraphicsOGL.GL_ONE_MINUS_SRC_COLOR,
                    com.nttdocomo.ui.ogl.GraphicsOGL.GL_DST_COLOR,
                    com.nttdocomo.ui.ogl.GraphicsOGL.GL_ONE_MINUS_DST_COLOR,
                    com.nttdocomo.ui.ogl.GraphicsOGL.GL_SRC_ALPHA,
                    com.nttdocomo.ui.ogl.GraphicsOGL.GL_ONE_MINUS_SRC_ALPHA,
                    com.nttdocomo.ui.ogl.GraphicsOGL.GL_DST_ALPHA,
                    com.nttdocomo.ui.ogl.GraphicsOGL.GL_ONE_MINUS_DST_ALPHA,
                    com.nttdocomo.ui.ogl.GraphicsOGL.GL_SRC_ALPHA_SATURATE -> true;
            default -> false;
        };
    }

    private static boolean isValidBlendDstFactor(int factor) {
        return switch (factor) {
            case com.nttdocomo.ui.ogl.GraphicsOGL.GL_ZERO,
                    com.nttdocomo.ui.ogl.GraphicsOGL.GL_ONE,
                    com.nttdocomo.ui.ogl.GraphicsOGL.GL_SRC_COLOR,
                    com.nttdocomo.ui.ogl.GraphicsOGL.GL_ONE_MINUS_SRC_COLOR,
                    com.nttdocomo.ui.ogl.GraphicsOGL.GL_SRC_ALPHA,
                    com.nttdocomo.ui.ogl.GraphicsOGL.GL_ONE_MINUS_SRC_ALPHA,
                    com.nttdocomo.ui.ogl.GraphicsOGL.GL_DST_ALPHA,
                    com.nttdocomo.ui.ogl.GraphicsOGL.GL_ONE_MINUS_DST_ALPHA -> true;
            default -> false;
        };
    }

    private static float edge(float ax, float ay, float bx, float by, float px, float py) {
        return ((px - ax) * (by - ay)) - ((py - ay) * (bx - ax));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
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

    private void syncUiFogState() {
        if (uiFog == null) {
            threeD.setUiFog(null, 0f, 0f, 0f, 0);
            return;
        }
        _Graphics3DInternalAccess.FogState fogState = _Graphics3DInternalAccess.fogState(uiFog);
        threeD.setUiFog(
                fogState.mode(),
                fogState.linearNear(),
                fogState.linearFar(),
                fogState.density(),
                fogState.color()
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
        float clipX;
        float clipY;
        float clipZ;
        float clipW;
        float x;
        float y;
        float depth;
        float reciprocalW;
        float u;
        float v;
        int color;
        int backColor;

        void set(float clipX, float clipY, float clipZ, float clipW,
                 float x, float y, float depth, float reciprocalW, float u, float v, int color, int backColor) {
            this.clipX = clipX;
            this.clipY = clipY;
            this.clipZ = clipZ;
            this.clipW = clipW;
            this.x = x;
            this.y = y;
            this.depth = depth;
            this.reciprocalW = reciprocalW;
            this.u = u;
            this.v = v;
            this.color = color;
            this.backColor = backColor;
        }

        void copyFrom(RasterVertex other) {
            set(other.clipX, other.clipY, other.clipZ, other.clipW,
                    other.x, other.y, other.depth, other.reciprocalW, other.u, other.v, other.color, other.backColor);
        }

        RasterVertex copyOf(RasterVertex other) {
            copyFrom(other);
            return this;
        }

        void interpolateFrom(RasterVertex from, RasterVertex to, float t) {
            set(
                    lerp(from.clipX, to.clipX, t),
                    lerp(from.clipY, to.clipY, t),
                    lerp(from.clipZ, to.clipZ, t),
                    lerp(from.clipW, to.clipW, t),
                    0f,
                    0f,
                    0f,
                    0f,
                    lerp(from.u, to.u, t),
                    lerp(from.v, to.v, t),
                    lerpColor(from.color, to.color, t),
                    lerpColor(from.backColor, to.backColor, t)
            );
        }

        private static float lerp(float from, float to, float t) {
            return from + ((to - from) * t);
        }

        private static int lerpColor(int from, int to, float t) {
            int fromA = (from >>> 24) & 0xFF;
            int fromR = (from >>> 16) & 0xFF;
            int fromG = (from >>> 8) & 0xFF;
            int fromB = from & 0xFF;
            int toA = (to >>> 24) & 0xFF;
            int toR = (to >>> 16) & 0xFF;
            int toG = (to >>> 8) & 0xFF;
            int toB = to & 0xFF;
            int alpha = Math.round(fromA + ((toA - fromA) * t));
            int red = Math.round(fromR + ((toR - fromR) * t));
            int green = Math.round(fromG + ((toG - fromG) * t));
            int blue = Math.round(fromB + ((toB - fromB) * t));
            return (alpha << 24) | (red << 16) | (green << 8) | blue;
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

    private record OglPointer(int size, int type, int stride, DirectBuffer pointer,
                              OglBufferObject bufferObject, int byteOffset) {
        static OglPointer direct(int size, int type, int stride, DirectBuffer pointer) {
            return new OglPointer(size, type, stride, pointer, null, 0);
        }

        static OglPointer bufferObject(int size, int type, int stride, OglBufferObject bufferObject, int byteOffset) {
            if (bufferObject == null) {
                return null;
            }
            return new OglPointer(size, type, stride, null, bufferObject, byteOffset);
        }

        int componentByteOffset(int vertexIndex, int componentIndex) {
            int componentSize = bytesPerComponent(type);
            if (componentSize <= 0) {
                return -1;
            }
            int byteStride = stride > 0 ? stride : Math.max(1, size) * componentSize;
            return byteOffset + (vertexIndex * byteStride) + (componentIndex * componentSize);
        }

        private static int bytesPerComponent(int type) {
            return switch (type) {
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_UNSIGNED_BYTE,
                        com.nttdocomo.ui.ogl.GraphicsOGL.GL_BYTE -> 1;
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_SHORT,
                        com.nttdocomo.ui.ogl.GraphicsOGL.GL_UNSIGNED_SHORT -> 2;
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_FLOAT -> 4;
                default -> -1;
            };
        }
    }

    private static final class OglBufferObject {
        private byte[] data = new byte[0];
        private int usage = com.nttdocomo.ui.ogl.GraphicsOGL.GL_STATIC_DRAW;

        byte[] data() {
            return data;
        }

        void replace(byte[] data, int usage) {
            this.data = data == null ? new byte[0] : data.clone();
            this.usage = usage;
        }

        boolean update(int offset, byte[] updateBytes) {
            if (offset < 0 || updateBytes == null) {
                return false;
            }
            int requiredLength = offset + updateBytes.length;
            if (requiredLength > data.length) {
                data = Arrays.copyOf(data, requiredLength);
            }
            System.arraycopy(updateBytes, 0, data, offset, updateBytes.length);
            return true;
        }
    }

    private record OglIndexSource(ShortBuffer pointer, OglBufferObject bufferObject, int byteOffset) {
        static OglIndexSource direct(ShortBuffer pointer) {
            return new OglIndexSource(pointer, null, 0);
        }

        static OglIndexSource bufferObject(OglBufferObject bufferObject, int byteOffset) {
            return new OglIndexSource(null, bufferObject, byteOffset);
        }

        int elementCount() {
            if (pointer != null) {
                return DirectBufferFactory.getSegmentLength(pointer);
            }
            if (bufferObject == null || byteOffset < 0 || byteOffset > bufferObject.data().length) {
                return 0;
            }
            return (bufferObject.data().length - byteOffset) / 2;
        }

        int indexAt(int primitiveIndex) {
            if (pointer != null) {
                int elementIndex = DirectBufferFactory.getSegmentOffset(pointer) + primitiveIndex;
                return DirectBufferFactory.getShort(pointer, elementIndex) & 0xFFFF;
            }
            if (bufferObject == null) {
                return 0;
            }
            return readUnsignedShort(bufferObject.data(), byteOffset + (primitiveIndex * 2));
        }
    }

    private static final class OglTexture {
        private int width;
        private int height;
        private int[] pixels = new int[0];
        private int baseFormat = com.nttdocomo.ui.ogl.GraphicsOGL.GL_RGBA;
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

        static boolean supportsCompressedInternalFormat(int internalFormat) {
            return CompressedPaletteFormatInfo.forInternalFormat(internalFormat) != null;
        }

        boolean loadCompressed(int internalFormat, int width, int height, byte[] raw) {
            CompressedPaletteFormatInfo format = CompressedPaletteFormatInfo.forInternalFormat(internalFormat);
            if (format == null) {
                return false;
            }
            this.width = Math.max(1, width);
            this.height = Math.max(1, height);
            this.baseFormat = format.baseFormat();
            int pixelCount = this.width * this.height;
            int paletteBytes = format.paletteEntries() * format.paletteEntrySize();
            int indexBytes = format.indexByteCount(pixelCount);
            if (raw.length < paletteBytes + indexBytes) {
                return false;
            }
            int[] palette = new int[format.paletteEntries()];
            for (int i = 0; i < palette.length; i++) {
                int entryOffset = i * format.paletteEntrySize();
                palette[i] = decodePaletteEntry(format, raw, entryOffset);
            }
            this.pixels = new int[pixelCount];
            if (format.bitsPerIndex() == 8) {
                for (int i = 0; i < this.pixels.length; i++) {
                    this.pixels[i] = palette[raw[paletteBytes + i] & 0xFF];
                }
                return true;
            }
            for (int i = 0; i < this.pixels.length; i += 2) {
                int packed = raw[paletteBytes + (i >> 1)] & 0xFF;
                this.pixels[i] = palette[(packed >>> 4) & 0x0F];
                if (i + 1 < this.pixels.length) {
                    this.pixels[i + 1] = palette[packed & 0x0F];
                }
            }
            return true;
        }

        boolean loadUncompressed(int width, int height, int format, int type, DirectBuffer source) {
            this.width = Math.max(1, width);
            this.height = Math.max(1, height);
            this.baseFormat = format;
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

        int baseFormat() {
            return baseFormat;
        }

        private int bilinearSample(float u, float v) {
            float x = u * (width - 1);
            float y = v * (height - 1);
            int x0 = clamp((int) x, 0, width - 1);
            int y0 = clamp((int) y, 0, height - 1);
            int x1 = x0 + 1 < width ? x0 + 1 : x0;
            int y1 = y0 + 1 < height ? y0 + 1 : y0;
            float tx = x - x0;
            float ty = y - y0;
            int c00 = pixels[(y0 * width) + x0];
            int c10 = pixels[(y0 * width) + x1];
            int c01 = pixels[(y1 * width) + x0];
            int c11 = pixels[(y1 * width) + x1];
            int alpha = bilinearChannel(c00, c10, c01, c11, 24, tx, ty);
            int red = bilinearChannel(c00, c10, c01, c11, 16, tx, ty);
            int green = bilinearChannel(c00, c10, c01, c11, 8, tx, ty);
            int blue = bilinearChannel(c00, c10, c01, c11, 0, tx, ty);
            return (alpha << 24) | (red << 16) | (green << 8) | blue;
        }

        private static int bilinearChannel(int c00, int c10, int c01, int c11, int shift, float tx, float ty) {
            float top = ((c00 >>> shift) & 0xFF)
                    + ((((c10 >>> shift) & 0xFF) - ((c00 >>> shift) & 0xFF)) * tx);
            float bottom = ((c01 >>> shift) & 0xFF)
                    + ((((c11 >>> shift) & 0xFF) - ((c01 >>> shift) & 0xFF)) * tx);
            return clamp(Math.round(top + ((bottom - top) * ty)), 0, 255);
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

        private static int decodePaletteEntry(CompressedPaletteFormatInfo format, byte[] raw, int offset) {
            return switch (format.paletteEntryType()) {
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_UNSIGNED_BYTE ->
                        decodeBytePixel(raw, offset, format.baseFormat(), com.nttdocomo.ui.ogl.GraphicsOGL.GL_UNSIGNED_BYTE);
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_UNSIGNED_SHORT_5_6_5 ->
                        decodeRgb565(readPackedShort(raw, offset));
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_UNSIGNED_SHORT_4_4_4_4 ->
                        decodeRgba4444(readPackedShort(raw, offset));
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_UNSIGNED_SHORT_5_5_5_1 ->
                        decodeRgb5a1(readPackedShort(raw, offset));
                default -> 0;
            };
        }

        private static int readPackedShort(byte[] raw, int offset) {
            return (raw[offset] & 0xFF) | ((raw[offset + 1] & 0xFF) << 8);
        }
    }

    private record CompressedPaletteFormatInfo(int internalFormat, int baseFormat, int paletteEntryType,
                                               int paletteEntries, int paletteEntrySize) {
        private static final CompressedPaletteFormatInfo[] VALUES = {
                new CompressedPaletteFormatInfo(com.nttdocomo.ui.ogl.GraphicsOGL.GL_PALETTE4_RGB8_OES,
                        com.nttdocomo.ui.ogl.GraphicsOGL.GL_RGB,
                        com.nttdocomo.ui.ogl.GraphicsOGL.GL_UNSIGNED_BYTE,
                        16,
                        3),
                new CompressedPaletteFormatInfo(com.nttdocomo.ui.ogl.GraphicsOGL.GL_PALETTE4_RGBA8_OES,
                        com.nttdocomo.ui.ogl.GraphicsOGL.GL_RGBA,
                        com.nttdocomo.ui.ogl.GraphicsOGL.GL_UNSIGNED_BYTE,
                        16,
                        4),
                new CompressedPaletteFormatInfo(com.nttdocomo.ui.ogl.GraphicsOGL.GL_PALETTE4_R5_G6_B5_OES,
                        com.nttdocomo.ui.ogl.GraphicsOGL.GL_RGB,
                        com.nttdocomo.ui.ogl.GraphicsOGL.GL_UNSIGNED_SHORT_5_6_5,
                        16,
                        2),
                new CompressedPaletteFormatInfo(com.nttdocomo.ui.ogl.GraphicsOGL.GL_PALETTE4_RGBA4_OES,
                        com.nttdocomo.ui.ogl.GraphicsOGL.GL_RGBA,
                        com.nttdocomo.ui.ogl.GraphicsOGL.GL_UNSIGNED_SHORT_4_4_4_4,
                        16,
                        2),
                new CompressedPaletteFormatInfo(com.nttdocomo.ui.ogl.GraphicsOGL.GL_PALETTE4_RGB5_A1_OES,
                        com.nttdocomo.ui.ogl.GraphicsOGL.GL_RGBA,
                        com.nttdocomo.ui.ogl.GraphicsOGL.GL_UNSIGNED_SHORT_5_5_5_1,
                        16,
                        2),
                new CompressedPaletteFormatInfo(com.nttdocomo.ui.ogl.GraphicsOGL.GL_PALETTE8_RGB8_OES,
                        com.nttdocomo.ui.ogl.GraphicsOGL.GL_RGB,
                        com.nttdocomo.ui.ogl.GraphicsOGL.GL_UNSIGNED_BYTE,
                        256,
                        3),
                new CompressedPaletteFormatInfo(com.nttdocomo.ui.ogl.GraphicsOGL.GL_PALETTE8_RGBA8_OES,
                        com.nttdocomo.ui.ogl.GraphicsOGL.GL_RGBA,
                        com.nttdocomo.ui.ogl.GraphicsOGL.GL_UNSIGNED_BYTE,
                        256,
                        4),
                new CompressedPaletteFormatInfo(com.nttdocomo.ui.ogl.GraphicsOGL.GL_PALETTE8_R5_G6_B5_OES,
                        com.nttdocomo.ui.ogl.GraphicsOGL.GL_RGB,
                        com.nttdocomo.ui.ogl.GraphicsOGL.GL_UNSIGNED_SHORT_5_6_5,
                        256,
                        2),
                new CompressedPaletteFormatInfo(com.nttdocomo.ui.ogl.GraphicsOGL.GL_PALETTE8_RGBA4_OES,
                        com.nttdocomo.ui.ogl.GraphicsOGL.GL_RGBA,
                        com.nttdocomo.ui.ogl.GraphicsOGL.GL_UNSIGNED_SHORT_4_4_4_4,
                        256,
                        2),
                new CompressedPaletteFormatInfo(com.nttdocomo.ui.ogl.GraphicsOGL.GL_PALETTE8_RGB5_A1_OES,
                        com.nttdocomo.ui.ogl.GraphicsOGL.GL_RGBA,
                        com.nttdocomo.ui.ogl.GraphicsOGL.GL_UNSIGNED_SHORT_5_5_5_1,
                        256,
                        2)
        };

        static CompressedPaletteFormatInfo forInternalFormat(int internalFormat) {
            for (CompressedPaletteFormatInfo value : VALUES) {
                if (value.internalFormat == internalFormat) {
                    return value;
                }
            }
            return null;
        }

        int bitsPerIndex() {
            return paletteEntries == 16 ? 4 : 8;
        }

        int indexByteCount(int pixelCount) {
            return (pixelCount * bitsPerIndex() + 7) / 8;
        }
    }

    private static final class OglLight {
        private final float[] ambient = {0f, 0f, 0f, 1f};
        private final float[] diffuse;
        private final float[] specular;
        private final float[] position = {0f, 0f, 1f, 0f};
        private final float[] spotDirection = {0f, 0f, -1f};
        private float spotExponent;
        private float spotCutoff = 180f;
        private float constantAttenuation = 1f;
        private float linearAttenuation;
        private float quadraticAttenuation;

        private OglLight(int index) {
            diffuse = index == 0 ? new float[]{1f, 1f, 1f, 1f} : new float[]{0f, 0f, 0f, 1f};
            specular = index == 0 ? new float[]{1f, 1f, 1f, 1f} : new float[]{0f, 0f, 0f, 1f};
        }
    }

    private static final class OglState {
        private final Map<Integer, OglTexture> textures = new HashMap<>();
        private final Map<Integer, OglBufferObject> buffers = new HashMap<>();
        private final Set<Integer> enabledCaps = new HashSet<>();
        private final Set<Integer> enabledClientStates = new HashSet<>();
        private final boolean[] lightEnabled = new boolean[8];
        private boolean texture2DEnabled;
        private boolean blendCapEnabled;
        private boolean depthTestEnabled;
        private boolean lightingCapEnabled;
        private boolean colorMaterialEnabled;
        private boolean matrixPaletteEnabled;
        private boolean normalizeEnabled;
        private boolean rescaleNormalEnabled;
        private boolean cullFaceEnabled;
        private boolean alphaTestEnabled;
        private boolean texCoordArrayEnabled;
        private boolean colorArrayEnabled;
        private boolean normalArrayEnabled;
        private boolean matrixIndexArrayEnabled;
        private boolean weightArrayEnabled;
        private int nextTextureId = 1;
        private int nextBufferId = 1;
        private int lastError = com.nttdocomo.ui.ogl.GraphicsOGL.GL_NO_ERROR;
        private int matrixMode = com.nttdocomo.ui.ogl.GraphicsOGL.GL_MODELVIEW;
        private int textureEnvMode = com.nttdocomo.ui.ogl.GraphicsOGL.GL_MODULATE;
        private int combineRgb = com.nttdocomo.ui.ogl.GraphicsOGL.GL_MODULATE;
        private int combineAlpha = com.nttdocomo.ui.ogl.GraphicsOGL.GL_MODULATE;
        private final int[] srcRgb = {
                com.nttdocomo.ui.ogl.GraphicsOGL.GL_TEXTURE,
                com.nttdocomo.ui.ogl.GraphicsOGL.GL_PREVIOUS,
                com.nttdocomo.ui.ogl.GraphicsOGL.GL_CONSTANT
        };
        private final int[] srcAlpha = {
                com.nttdocomo.ui.ogl.GraphicsOGL.GL_TEXTURE,
                com.nttdocomo.ui.ogl.GraphicsOGL.GL_PREVIOUS,
                com.nttdocomo.ui.ogl.GraphicsOGL.GL_CONSTANT
        };
        private final int[] operandRgb = {
                com.nttdocomo.ui.ogl.GraphicsOGL.GL_SRC_COLOR,
                com.nttdocomo.ui.ogl.GraphicsOGL.GL_SRC_COLOR,
                com.nttdocomo.ui.ogl.GraphicsOGL.GL_SRC_ALPHA
        };
        private final int[] operandAlpha = {
                com.nttdocomo.ui.ogl.GraphicsOGL.GL_SRC_ALPHA,
                com.nttdocomo.ui.ogl.GraphicsOGL.GL_SRC_ALPHA,
                com.nttdocomo.ui.ogl.GraphicsOGL.GL_SRC_ALPHA
        };
        private int textureEnvColor;
        private int rgbScale = 1;
        private int alphaScale = 1;
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
        private int viewportX;
        private int viewportY;
        private int viewportWidth = 1;
        private int viewportHeight = 1;
        private int unpackAlignment = 1;
        private int boundTextureId;
        private int boundArrayBufferId;
        private int boundElementArrayBufferId;
        private int color = 0xFFFFFFFF;
        private final float[] currentNormal = {0f, 0f, 1f};
        private final float[] materialAmbient = {0.2f, 0.2f, 0.2f, 1f};
        private final float[] materialDiffuse = {0.8f, 0.8f, 0.8f, 1f};
        private final float[] materialSpecular = {0f, 0f, 0f, 1f};
        private final float[] materialEmission = {0f, 0f, 0f, 1f};
        private float materialShininess;
        private final float[] lightModelAmbient = {0.2f, 0.2f, 0.2f, 1f};
        private boolean lightModelTwoSide;
        private final OglLight[] lights = createLights();
        private float[] modelViewMatrix = identityMatrix();
        private float[] projectionMatrix = identityMatrix();
        private float[] textureMatrix = identityMatrix();
        private final float[][] paletteMatrices = new float[OGL_MAX_PALETTE_MATRICES][];
        private boolean standardModelViewConfigured;
        private boolean standardProjectionConfigured;
        private float[] acrodeaWorldMatrix = identityMatrix();
        private float[] acrodeaCameraMatrix;
        private int currentPaletteMatrix;
        private final Deque<float[]> modelViewStack = new ArrayDeque<>();
        private final Deque<float[]> projectionStack = new ArrayDeque<>();
        private final Deque<float[]> textureStack = new ArrayDeque<>();
        private final Deque<float[]> acrodeaWorldStack = new ArrayDeque<>();
        private final Deque<float[]> acrodeaCameraStack = new ArrayDeque<>();
        private final Deque<float[]>[] paletteMatrixStacks = createPaletteMatrixStacks();
        private OglPointer vertexPointer;
        private OglPointer texCoordPointer;
        private OglPointer normalPointer;
        private OglPointer colorPointer;
        private OglPointer matrixIndexPointer;
        private OglPointer weightPointer;

        private OglState() {
            for (int i = 0; i < paletteMatrices.length; i++) {
                paletteMatrices[i] = identityMatrix();
            }
        }

        void enableCap(int cap) {
            enabledCaps.add(cap);
            switch (cap) {
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_TEXTURE_2D -> texture2DEnabled = true;
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_BLEND -> blendCapEnabled = true;
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_DEPTH_TEST -> depthTestEnabled = true;
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_LIGHTING -> lightingCapEnabled = true;
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_COLOR_MATERIAL -> colorMaterialEnabled = true;
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_MATRIX_PALETTE_OES -> matrixPaletteEnabled = true;
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_NORMALIZE -> normalizeEnabled = true;
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_RESCALE_NORMAL -> rescaleNormalEnabled = true;
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_CULL_FACE -> cullFaceEnabled = true;
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_ALPHA_TEST -> alphaTestEnabled = true;
                default -> {
                    int lightIndex = cap - com.nttdocomo.ui.ogl.GraphicsOGL.GL_LIGHT0;
                    if (lightIndex >= 0 && lightIndex < lightEnabled.length) {
                        lightEnabled[lightIndex] = true;
                    }
                }
            }
        }

        void disableCap(int cap) {
            enabledCaps.remove(cap);
            switch (cap) {
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_TEXTURE_2D -> texture2DEnabled = false;
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_BLEND -> blendCapEnabled = false;
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_DEPTH_TEST -> depthTestEnabled = false;
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_LIGHTING -> lightingCapEnabled = false;
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_COLOR_MATERIAL -> colorMaterialEnabled = false;
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_MATRIX_PALETTE_OES -> matrixPaletteEnabled = false;
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_NORMALIZE -> normalizeEnabled = false;
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_RESCALE_NORMAL -> rescaleNormalEnabled = false;
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_CULL_FACE -> cullFaceEnabled = false;
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_ALPHA_TEST -> alphaTestEnabled = false;
                default -> {
                    int lightIndex = cap - com.nttdocomo.ui.ogl.GraphicsOGL.GL_LIGHT0;
                    if (lightIndex >= 0 && lightIndex < lightEnabled.length) {
                        lightEnabled[lightIndex] = false;
                    }
                }
            }
        }

        void enableClientState(int array) {
            enabledClientStates.add(array);
            switch (array) {
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_TEXTURE_COORD_ARRAY -> texCoordArrayEnabled = true;
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_COLOR_ARRAY -> colorArrayEnabled = true;
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_NORMAL_ARRAY -> normalArrayEnabled = true;
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_MATRIX_INDEX_ARRAY_OES -> matrixIndexArrayEnabled = true;
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_WEIGHT_ARRAY_OES -> weightArrayEnabled = true;
                default -> {
                }
            }
        }

        void disableClientState(int array) {
            enabledClientStates.remove(array);
            switch (array) {
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_TEXTURE_COORD_ARRAY -> texCoordArrayEnabled = false;
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_COLOR_ARRAY -> colorArrayEnabled = false;
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_NORMAL_ARRAY -> normalArrayEnabled = false;
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_MATRIX_INDEX_ARRAY_OES -> matrixIndexArrayEnabled = false;
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_WEIGHT_ARRAY_OES -> weightArrayEnabled = false;
                default -> {
                }
            }
        }

        void beginDrawing() {
            standardModelViewConfigured = false;
            standardProjectionConfigured = false;
            acrodeaWorldMatrix = identityMatrix();
            acrodeaCameraMatrix = null;
        }

        void endDrawing() {
        }

        boolean textureEnabled() {
            return texture2DEnabled && texCoordArrayEnabled && boundTexture() != null;
        }

        boolean blendEnabled() {
            return blendCapEnabled;
        }

        boolean depthEnabled() {
            return depthTestEnabled;
        }

        boolean lightingEnabled() {
            return lightingCapEnabled;
        }

        boolean colorMaterialTracksAmbientAndDiffuse() {
            return colorMaterialEnabled;
        }

        boolean usesAcrodeaMatrices() {
            return acrodeaCameraMatrix != null && !standardModelViewConfigured && !standardProjectionConfigured;
        }

        boolean usesMatrixPalette() {
            return matrixPaletteEnabled
                    && matrixIndexArrayEnabled
                    && weightArrayEnabled
                    && matrixIndexPointer != null
                    && weightPointer != null;
        }

        OglTexture boundTexture() {
            return boundTextureId == 0 ? null : textures.get(boundTextureId);
        }

        OglBufferObject boundArrayBuffer() {
            return boundArrayBufferId == 0 ? null : buffers.get(boundArrayBufferId);
        }

        OglBufferObject boundElementArrayBuffer() {
            return boundElementArrayBufferId == 0 ? null : buffers.get(boundElementArrayBufferId);
        }

        OglBufferObject boundBuffer(int target) {
            return switch (target) {
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_ARRAY_BUFFER -> boundArrayBuffer();
                case com.nttdocomo.ui.ogl.GraphicsOGL.GL_ELEMENT_ARRAY_BUFFER -> boundElementArrayBuffer();
                default -> null;
            };
        }

        int sampleBoundTexture(float u, float v) {
            OglTexture texture = boundTexture();
            return texture == null ? 0xFFFFFFFF : texture.sample(u, v);
        }

        OglLight light(int lightEnum) {
            int index = lightEnum - com.nttdocomo.ui.ogl.GraphicsOGL.GL_LIGHT0;
            return index < 0 || index >= lights.length ? null : lights[index];
        }

        @SuppressWarnings("unchecked")
        private static Deque<float[]>[] createPaletteMatrixStacks() {
            Deque<float[]>[] stacks = (Deque<float[]>[]) new Deque<?>[OGL_MAX_PALETTE_MATRICES];
            for (int i = 0; i < stacks.length; i++) {
                stacks[i] = new ArrayDeque<>();
            }
            return stacks;
        }

        private static OglLight[] createLights() {
            OglLight[] lights = new OglLight[8];
            for (int i = 0; i < lights.length; i++) {
                lights[i] = new OglLight(i);
            }
            return lights;
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
