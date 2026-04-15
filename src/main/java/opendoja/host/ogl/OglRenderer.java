package opendoja.host.ogl;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLContext;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLOffscreenAutoDrawable;
import com.jogamp.opengl.GLProfile;
import com.nttdocomo.ui.ogl.ByteBuffer;
import com.nttdocomo.ui.ogl.DirectBuffer;
import com.nttdocomo.ui.ogl.DirectBufferFactory;
import com.nttdocomo.ui.ogl.FloatBuffer;
import com.nttdocomo.ui.ogl.GraphicsOGL;
import com.nttdocomo.ui.ogl.IntBuffer;
import com.nttdocomo.ui.ogl.ShortBuffer;
import opendoja.host.DesktopSurface;
import opendoja.host.OpenDoJaLaunchArgs;
import opendoja.host.OpenDoJaLog;
import opendoja.host.OpenGlesRendererMode;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Host-owned software OpenGL ES 1.1 renderer boundary.
 */
public class OglRenderer {
    private static final int OGL_TEXTURE_UNIT_COUNT = 1;
    private static final int OGL_MAX_VERTEX_UNITS = 8;
    private static final int OGL_MAX_PALETTE_MATRICES = 32;
    private static final SharedGlObjectStore SHARED_GL_OBJECT_STORE = new SharedGlObjectStore();

    public interface Host {
        DesktopSurface surface();

        Graphics2D delegate();

        void markOpenGlesActivity();

        void flushSurfacePresentation();

        void onSoftwareSurfaceMutation();
    }

    private final Host host;
    private int oglClearColor = 0x00000000;
    private final OglState ogl = new OglState(this);
    private final ClipVector clipVectorTemp = new ClipVector();
    private final ClipVector eyeVectorTemp = new ClipVector();
    private final ClipVector normalVectorTemp = new ClipVector();
    private final HardwareBackend hardware = new HardwareBackend();

    public OglRenderer(Host host) {
        this.host = Objects.requireNonNull(host, "host");
        ogl.viewportX = 0;
        ogl.viewportY = 0;
        ogl.viewportWidth = host.surface().width();
        ogl.viewportHeight = host.surface().height();
    }

    public void close() {
        hardware.close();
    }

    public void onHostDelegateRecreated() {
        hardware.onHostDelegateRecreated();
    }

    public void onSoftwareSurfaceMutation() {
        hardware.onSoftwareSurfaceMutation();
    }

    public void onPresentedSoftwareOverlay() {
        hardware.onPresentedSoftwareOverlay(null);
    }

    public void onPresentedSoftwareOverlay(Rectangle bounds) {
        hardware.onPresentedSoftwareOverlay(bounds);
    }

    public void prepareForSoftwareMutation() {
        hardware.prepareForSoftwareMutation();
    }

    public void flushHardwarePresentation() {
        hardware.flush();
    }

    public boolean acceptsExtensionMatrixMode(int mode) {
        return false;
    }

    public void onBeginDrawing() {
    }

    public boolean usesExtensionMatrices(boolean standardModelViewConfigured, boolean standardProjectionConfigured) {
        return false;
    }

    public float[] extensionWorldMatrix() {
        return null;
    }

    public float[] extensionCameraMatrix() {
        return null;
    }

    public void loadExtensionMatrix(int mode, float[] matrix) {
        throw new IllegalArgumentException("Unsupported extension matrix mode: " + mode);
    }

    public void multiplyExtensionMatrix(int mode, float[] matrix) {
        throw new IllegalArgumentException("Unsupported extension matrix mode: " + mode);
    }

    public void pushExtensionMatrix(int mode) {
        throw new IllegalArgumentException("Unsupported extension matrix mode: " + mode);
    }

    public boolean popExtensionMatrix(int mode) {
        throw new IllegalArgumentException("Unsupported extension matrix mode: " + mode);
    }

    private boolean usesHardwareRenderer() {
        // Offscreen Image surfaces do not have repaint hooks, but they still need to use the
        // same GL path as the canvas so renderImage()/syncOffscreenSurfaceForReadback() can
        // read back the finished frame before a later drawImage() composite.
        return OpenDoJaLaunchArgs.openGlesRendererMode() == OpenGlesRendererMode.HARDWARE;
    }

public final void glClearColor(float red, float green, float blue, float alpha) {
    oglClearColor = ((clampOglChannel(alpha) & 0xFF) << 24)
            | ((clampOglChannel(red) & 0xFF) << 16)
            | ((clampOglChannel(green) & 0xFF) << 8)
            | (clampOglChannel(blue) & 0xFF);
}

public final void glClear(int mask) {
    host.markOpenGlesActivity();
    if ((mask & GraphicsOGL.GL_DEPTH_BUFFER_BIT) != 0) {
        host.surface().endDepthFrame();
    }
    if (usesHardwareRenderer() && hardware.clear(mask)) {
        return;
    }
    if ((mask & GraphicsOGL.GL_COLOR_BUFFER_BIT) == 0) {
        return;
    }
    Rectangle clip = host.delegate().getClipBounds();
    int x = clip == null ? 0 : clip.x;
    int y = clip == null ? 0 : clip.y;
    int width = clip == null ? host.surface().width() : clip.width;
    int height = clip == null ? host.surface().height() : clip.height;
    Color old = host.delegate().getColor();
    host.delegate().setColor(new Color(oglClearColor, true));
    host.delegate().fillRect(x, y, width, height);
    host.delegate().setColor(old);
    host.flushSurfacePresentation();
}

public final void beginDrawing() {
    host.markOpenGlesActivity();
    ogl.beginDrawing();
}

public final void endDrawing() {
    host.markOpenGlesActivity();
    ogl.endDrawing();
    hardware.endDrawing();
}

public final void glEnable(int cap) {
    ogl.enableCap(cap);
    if (cap == GraphicsOGL.GL_COLOR_MATERIAL) {
        syncColorMaterial(unpackColor(ogl.color));
    }
}

public final void glDisable(int cap) {
    ogl.disableCap(cap);
}

public final void glEnableClientState(int array) {
    ogl.enableClientState(array);
}

public final void glDisableClientState(int array) {
    ogl.disableClientState(array);
}

public final void glMatrixMode(int mode) {
    switch (mode) {
        case GraphicsOGL.GL_MODELVIEW,
                GraphicsOGL.GL_PROJECTION,
                GraphicsOGL.GL_TEXTURE,
                GraphicsOGL.GL_MATRIX_PALETTE_OES -> ogl.matrixMode = mode;
        default -> {
            if (ogl.renderer.acceptsExtensionMatrixMode(mode)) {
                ogl.matrixMode = mode;
            } else {
                ogl.lastError = GraphicsOGL.GL_INVALID_ENUM;
            }
        }
    }
}

public final void glLoadIdentity() {
    loadMatrix(ogl.matrixMode, OglState.identityMatrix());
}

public final void glLoadMatrixf(float[] m) {
    if (m == null || m.length < 16) {
        ogl.lastError = GraphicsOGL.GL_INVALID_VALUE;
        return;
    }
    loadMatrix(ogl.matrixMode, m.clone());
}

public final void glMultMatrixf(float[] m) {
    if (m == null || m.length < 16) {
        ogl.lastError = GraphicsOGL.GL_INVALID_VALUE;
        return;
    }
    multiplyCurrentMatrix(m.clone());
}

public final void glPushMatrix() {
    pushCurrentMatrix(ogl.matrixMode);
}

public final void glPopMatrix() {
    popCurrentMatrix(ogl.matrixMode);
}

public final void glOrthof(float left, float right, float bottom, float top, float zNear, float zFar) {
    float[] matrix = OglState.identityMatrix();
    matrix[0] = 2f / Math.max(0.0001f, right - left);
    matrix[5] = 2f / Math.max(0.0001f, top - bottom);
    matrix[10] = -2f / Math.max(0.0001f, zFar - zNear);
    matrix[12] = -((right + left) / Math.max(0.0001f, right - left));
    matrix[13] = -((top + bottom) / Math.max(0.0001f, top - bottom));
    matrix[14] = -((zFar + zNear) / Math.max(0.0001f, zFar - zNear));
    loadMatrix(ogl.matrixMode, matrix);
}

public final void glFrustumf(float left, float right, float bottom, float top, float zNear, float zFar) {
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

public final void glDepthRangef(float zNear, float zFar) {
    ogl.depthRangeNear = Math.max(0f, Math.min(1f, zNear));
    ogl.depthRangeFar = Math.max(0f, Math.min(1f, zFar));
}

public final void glRotatef(float angle, float x, float y, float z) {
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

public final void glScalef(float x, float y, float z) {
    float[] matrix = OglState.identityMatrix();
    matrix[0] = x;
    matrix[5] = y;
    matrix[10] = z;
    multiplyCurrentMatrix(matrix);
}

public final void glTranslatef(float x, float y, float z) {
    float[] matrix = OglState.identityMatrix();
    matrix[12] = x;
    matrix[13] = y;
    matrix[14] = z;
    multiplyCurrentMatrix(matrix);
}

public final void glAlphaFunc(int func, float ref) {
    ogl.alphaFunc = func;
    ogl.alphaRef = Math.max(0f, Math.min(1f, ref));
}

public final void glDepthMask(boolean flag) {
    ogl.depthMask = flag;
}

public final void glDepthFunc(int func) {
    ogl.depthFunc = func;
}

public final void glBlendFunc(int sfactor, int dfactor) {
    if (!isValidBlendSrcFactor(sfactor) || !isValidBlendDstFactor(dfactor)) {
        ogl.lastError = GraphicsOGL.GL_INVALID_ENUM;
        return;
    }
    ogl.blendSrcFactor = sfactor;
    ogl.blendDstFactor = dfactor;
}

public final void glColor4f(float red, float green, float blue, float alpha) {
    ogl.color = ((clampOglChannel(alpha) & 0xFF) << 24)
            | ((clampOglChannel(red) & 0xFF) << 16)
            | ((clampOglChannel(green) & 0xFF) << 8)
            | (clampOglChannel(blue) & 0xFF);
    syncColorMaterial(unpackColor(ogl.color));
}

public final void glColor4ub(short red, short green, short blue, short alpha) {
    ogl.color = ((alpha & 0xFF) << 24)
            | ((red & 0xFF) << 16)
            | ((green & 0xFF) << 8)
            | (blue & 0xFF);
    syncColorMaterial(unpackColor(ogl.color));
}

public final void glLightModelf(int pname, float param) {
    glLightModelfv(pname, new float[]{param});
}

public final void glLightModelfv(int pname, float[] params) {
    if (params == null) {
        throw new NullPointerException("params");
    }
    switch (pname) {
        case GraphicsOGL.GL_LIGHT_MODEL_AMBIENT -> {
            if (params.length < 4) {
                throw new IllegalArgumentException("params");
            }
            copyColor(params, ogl.lightModelAmbient);
        }
        case GraphicsOGL.GL_LIGHT_MODEL_TWO_SIDE -> {
            if (params.length < 1) {
                throw new IllegalArgumentException("params");
            }
            ogl.lightModelTwoSide = params[0] != 0f;
        }
        default -> ogl.lastError = GraphicsOGL.GL_INVALID_ENUM;
    }
}

public final void glLightf(int light, int pname, float param) {
    glLightfv(light, pname, new float[]{param});
}

public final void glLightfv(int light, int pname, float[] params) {
    if (params == null) {
        throw new NullPointerException("params");
    }
    OglLight oglLight = ogl.light(light);
    if (oglLight == null) {
        ogl.lastError = GraphicsOGL.GL_INVALID_ENUM;
        return;
    }
    switch (pname) {
        case GraphicsOGL.GL_AMBIENT -> {
            requireLength(params, 4);
            copyColor(params, oglLight.ambient);
        }
        case GraphicsOGL.GL_DIFFUSE -> {
            requireLength(params, 4);
            copyColor(params, oglLight.diffuse);
        }
        case GraphicsOGL.GL_SPECULAR -> {
            requireLength(params, 4);
            copyColor(params, oglLight.specular);
        }
        case GraphicsOGL.GL_POSITION -> {
            requireLength(params, 4);
            multiply(eyeVectorTemp, ogl.modelViewMatrix, params[0], params[1], params[2], params[3]);
            oglLight.position[0] = eyeVectorTemp.x;
            oglLight.position[1] = eyeVectorTemp.y;
            oglLight.position[2] = eyeVectorTemp.z;
            oglLight.position[3] = eyeVectorTemp.w;
        }
        case GraphicsOGL.GL_SPOT_DIRECTION -> {
            requireLength(params, 3);
            transformLightDirection(ogl.modelViewMatrix, params[0], params[1], params[2], oglLight.spotDirection);
        }
        case GraphicsOGL.GL_SPOT_EXPONENT -> {
            requireLength(params, 1);
            if (params[0] < 0f || params[0] > 128f) {
                ogl.lastError = GraphicsOGL.GL_INVALID_VALUE;
                return;
            }
            oglLight.spotExponent = params[0];
        }
        case GraphicsOGL.GL_SPOT_CUTOFF -> {
            requireLength(params, 1);
            if ((params[0] < 0f || params[0] > 90f) && params[0] != 180f) {
                ogl.lastError = GraphicsOGL.GL_INVALID_VALUE;
                return;
            }
            oglLight.spotCutoff = params[0];
        }
        case GraphicsOGL.GL_CONSTANT_ATTENUATION -> {
            requireLength(params, 1);
            if (params[0] < 0f) {
                ogl.lastError = GraphicsOGL.GL_INVALID_VALUE;
                return;
            }
            oglLight.constantAttenuation = params[0];
        }
        case GraphicsOGL.GL_LINEAR_ATTENUATION -> {
            requireLength(params, 1);
            if (params[0] < 0f) {
                ogl.lastError = GraphicsOGL.GL_INVALID_VALUE;
                return;
            }
            oglLight.linearAttenuation = params[0];
        }
        case GraphicsOGL.GL_QUADRATIC_ATTENUATION -> {
            requireLength(params, 1);
            if (params[0] < 0f) {
                ogl.lastError = GraphicsOGL.GL_INVALID_VALUE;
                return;
            }
            oglLight.quadraticAttenuation = params[0];
        }
        default -> ogl.lastError = GraphicsOGL.GL_INVALID_ENUM;
    }
}

public final void glMaterialf(int face, int pname, float param) {
    glMaterialfv(face, pname, new float[]{param});
}

public final void glMaterialfv(int face, int pname, float[] params) {
    if (params == null) {
        throw new NullPointerException("params");
    }
    if (face != GraphicsOGL.GL_FRONT_AND_BACK) {
        ogl.lastError = GraphicsOGL.GL_INVALID_ENUM;
        return;
    }
    switch (pname) {
        case GraphicsOGL.GL_AMBIENT -> {
            requireLength(params, 4);
            if (!ogl.colorMaterialTracksAmbientAndDiffuse()) {
                copyColor(params, ogl.materialAmbient);
            }
        }
        case GraphicsOGL.GL_DIFFUSE -> {
            requireLength(params, 4);
            if (!ogl.colorMaterialTracksAmbientAndDiffuse()) {
                copyColor(params, ogl.materialDiffuse);
            }
        }
        case GraphicsOGL.GL_AMBIENT_AND_DIFFUSE -> {
            requireLength(params, 4);
            if (!ogl.colorMaterialTracksAmbientAndDiffuse()) {
                copyColor(params, ogl.materialAmbient);
                copyColor(params, ogl.materialDiffuse);
            }
        }
        case GraphicsOGL.GL_SPECULAR -> {
            requireLength(params, 4);
            copyColor(params, ogl.materialSpecular);
        }
        case GraphicsOGL.GL_EMISSION -> {
            requireLength(params, 4);
            copyColor(params, ogl.materialEmission);
        }
        case GraphicsOGL.GL_SHININESS -> {
            requireLength(params, 1);
            if (params[0] < 0f || params[0] > 128f) {
                ogl.lastError = GraphicsOGL.GL_INVALID_VALUE;
                return;
            }
            ogl.materialShininess = params[0];
        }
        default -> ogl.lastError = GraphicsOGL.GL_INVALID_ENUM;
    }
}

public final void glNormal3f(float nx, float ny, float nz) {
    ogl.currentNormal[0] = nx;
    ogl.currentNormal[1] = ny;
    ogl.currentNormal[2] = nz;
}

public final void glTexEnvi(int target, int pname, int param) {
    if (target != GraphicsOGL.GL_TEXTURE_ENV) {
        ogl.lastError = GraphicsOGL.GL_INVALID_ENUM;
        return;
    }
    switch (pname) {
        case GraphicsOGL.GL_TEXTURE_ENV_MODE -> ogl.textureEnvMode = param;
        case GraphicsOGL.GL_COMBINE_RGB -> ogl.combineRgb = param;
        case GraphicsOGL.GL_COMBINE_ALPHA -> ogl.combineAlpha = param;
        case GraphicsOGL.GL_SRC0_RGB -> ogl.srcRgb[0] = param;
        case GraphicsOGL.GL_SRC1_RGB -> ogl.srcRgb[1] = param;
        case GraphicsOGL.GL_SRC2_RGB -> ogl.srcRgb[2] = param;
        case GraphicsOGL.GL_SRC0_ALPHA -> ogl.srcAlpha[0] = param;
        case GraphicsOGL.GL_SRC1_ALPHA -> ogl.srcAlpha[1] = param;
        case GraphicsOGL.GL_SRC2_ALPHA -> ogl.srcAlpha[2] = param;
        case GraphicsOGL.GL_OPERAND0_RGB -> ogl.operandRgb[0] = param;
        case GraphicsOGL.GL_OPERAND1_RGB -> ogl.operandRgb[1] = param;
        case GraphicsOGL.GL_OPERAND2_RGB -> ogl.operandRgb[2] = param;
        case GraphicsOGL.GL_OPERAND0_ALPHA -> ogl.operandAlpha[0] = param;
        case GraphicsOGL.GL_OPERAND1_ALPHA -> ogl.operandAlpha[1] = param;
        case GraphicsOGL.GL_OPERAND2_ALPHA -> ogl.operandAlpha[2] = param;
        case GraphicsOGL.GL_RGB_SCALE -> ogl.rgbScale = sanitizeTextureScale(param);
        case GraphicsOGL.GL_ALPHA_SCALE -> ogl.alphaScale = sanitizeTextureScale(param);
        default -> ogl.lastError = GraphicsOGL.GL_INVALID_ENUM;
    }
}

public final void glTexEnvfv(int target, int pname, float[] params) {
    if (target != GraphicsOGL.GL_TEXTURE_ENV) {
        ogl.lastError = GraphicsOGL.GL_INVALID_ENUM;
        return;
    }
    if (params == null) {
        throw new NullPointerException("params");
    }
    if (pname == GraphicsOGL.GL_TEXTURE_ENV_COLOR) {
        if (params.length < 4) {
            throw new IllegalArgumentException("params");
        }
        ogl.textureEnvColor = packColor(params[0], params[1], params[2], params[3]);
        return;
    }
    if (params.length < 1) {
        throw new IllegalArgumentException("params");
    }
    if (pname == GraphicsOGL.GL_RGB_SCALE) {
        ogl.rgbScale = sanitizeTextureScale(Math.round(params[0]));
        return;
    }
    if (pname == GraphicsOGL.GL_ALPHA_SCALE) {
        ogl.alphaScale = sanitizeTextureScale(Math.round(params[0]));
        return;
    }
    glTexEnvi(target, pname, Math.round(params[0]));
}

public final void glShadeModel(int mode) {
    ogl.shadeModel = mode;
}

public final void glClientActiveTexture(int texture) {
    ogl.clientActiveTexture = texture;
}

public final void glPixelStorei(int pname, int param) {
    if (pname == GraphicsOGL.GL_UNPACK_ALIGNMENT) {
        ogl.unpackAlignment = Math.max(1, param);
    }
}

public final void glHint(int target, int mode) {
}

public final void glFrontFace(int mode) {
    ogl.frontFace = mode;
}

public final void glCullFace(int mode) {
    ogl.cullFace = mode;
}

public final void glViewport(int x, int y, int width, int height) {
    if (width < 0 || height < 0) {
        ogl.lastError = GraphicsOGL.GL_INVALID_VALUE;
        return;
    }
    ogl.viewportX = x;
    ogl.viewportY = y;
    ogl.viewportWidth = width;
    ogl.viewportHeight = height;
}

public final void glTexParameterf(int target, int pname, float param) {
    OglTexture texture = ogl.boundTexture();
    if (texture == null || target != GraphicsOGL.GL_TEXTURE_2D) {
        return;
    }
    texture.setParameter(pname, Math.round(param));
}

public final void glTexParameteri(int target, int pname, int param) {
    glTexParameterf(target, pname, param);
}

public final void glBindTexture(int target, int texture) {
    if (target != GraphicsOGL.GL_TEXTURE_2D) {
        ogl.lastError = GraphicsOGL.GL_INVALID_ENUM;
        return;
    }
    ogl.boundTextureId = texture;
}

public final void glGenTextures(int n, int[] textures) {
    if (textures == null) {
        throw new NullPointerException("textures");
    }
    int count = Math.min(n, textures.length);
    for (int i = 0; i < count; i++) {
        textures[i] = SHARED_GL_OBJECT_STORE.createTextureId();
    }
}

public final void glGenTextures(int[] textures) {
    glGenTextures(textures == null ? 0 : textures.length, textures);
}

public final void glDeleteTextures(int n, int[] textures) {
    if (textures == null) {
        throw new NullPointerException("textures");
    }
    int count = Math.min(n, textures.length);
    for (int i = 0; i < count; i++) {
        int textureId = textures[i];
        ogl.textures.remove(textureId);
        hardware.onTextureDeleted(textureId);
        if (ogl.boundTextureId == textureId) {
            ogl.boundTextureId = 0;
        }
    }
}

public final void glDeleteTextures(int[] textures) {
    glDeleteTextures(textures == null ? 0 : textures.length, textures);
}

public final void glCompressedTexImage2D(int target, int level, int internalformat, int width, int height, int border, int imageSize, DirectBuffer pixels) {
    if (target != GraphicsOGL.GL_TEXTURE_2D || level != 0 || border != 0) {
        ogl.lastError = GraphicsOGL.GL_INVALID_VALUE;
        return;
    }
    OglTexture texture = ogl.boundTexture();
    if (texture == null) {
        ogl.lastError = GraphicsOGL.GL_INVALID_OPERATION;
        return;
    }
    if (!(pixels instanceof com.nttdocomo.ui.ogl.ByteBuffer byteBuffer)) {
        ogl.lastError = GraphicsOGL.GL_INVALID_VALUE;
        return;
    }
    byte[] raw = new byte[Math.max(0, imageSize)];
    int offset = DirectBufferFactory.getSegmentOffset(byteBuffer);
    byteBuffer.get(offset, raw, 0, raw.length);
    if (!OglTexture.supportsCompressedInternalFormat(internalformat)) {
        ogl.lastError = GraphicsOGL.GL_INVALID_ENUM;
        return;
    }
    if (!texture.loadCompressed(internalformat, width, height, raw)) {
        ogl.lastError = GraphicsOGL.GL_INVALID_VALUE;
    }
}

public final void glCompressedTexImage2D(int target, int level, int internalformat, int width, int height, int border,
                                   com.nttdocomo.ui.ogl.ByteBuffer pixels) {
    if (pixels == null) {
        throw new NullPointerException("pixels");
    }
    glCompressedTexImage2D(target, level, internalformat, width, height, border, pixels.length(), pixels);
}

public final void glTexImage2D(int target, int level, int internalformat, int width, int height, int border,
                         int format, int type, DirectBuffer pixels) {
    if (target != GraphicsOGL.GL_TEXTURE_2D || level != 0 || border != 0 || width <= 0 || height <= 0) {
        ogl.lastError = GraphicsOGL.GL_INVALID_VALUE;
        return;
    }
    OglTexture texture = ogl.boundTexture();
    if (texture == null) {
        ogl.lastError = GraphicsOGL.GL_INVALID_OPERATION;
        return;
    }
    if (!texture.loadUncompressed(width, height, format, type, pixels)) {
        ogl.lastError = GraphicsOGL.GL_INVALID_VALUE;
    }
}

public final void glGenBuffers(int[] buffers) {
    if (buffers == null) {
        throw new NullPointerException("buffers");
    }
    for (int i = 0; i < buffers.length; i++) {
        buffers[i] = SHARED_GL_OBJECT_STORE.createBufferId();
    }
}

public final void glDeleteBuffers(int[] buffers) {
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

public final void glBindBuffer(int target, int buffer) {
    switch (target) {
        case GraphicsOGL.GL_ARRAY_BUFFER -> {
            if (buffer != 0) {
                ogl.buffers.computeIfAbsent(buffer, ignored -> new OglBufferObject());
            }
            ogl.boundArrayBufferId = buffer;
        }
        case GraphicsOGL.GL_ELEMENT_ARRAY_BUFFER -> {
            if (buffer != 0) {
                ogl.buffers.computeIfAbsent(buffer, ignored -> new OglBufferObject());
            }
            ogl.boundElementArrayBufferId = buffer;
        }
        default -> ogl.lastError = GraphicsOGL.GL_INVALID_ENUM;
    }
}

public final void glBufferData(int target, DirectBuffer data, int usage) {
    OglBufferObject buffer = ogl.boundBuffer(target);
    if (buffer == null) {
        ogl.lastError = GraphicsOGL.GL_INVALID_OPERATION;
        return;
    }
    if (data == null) {
        throw new NullPointerException("data");
    }
    byte[] bytes = copyBufferBytes(data);
    buffer.replace(bytes, usage);
}

public final void glBufferSubData(int target, int offset, DirectBuffer data) {
    OglBufferObject buffer = ogl.boundBuffer(target);
    if (buffer == null) {
        ogl.lastError = GraphicsOGL.GL_INVALID_OPERATION;
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
        ogl.lastError = GraphicsOGL.GL_INVALID_VALUE;
    }
}

public final void glVertexPointer(int size, int type, int stride, DirectBuffer pointer) {
    ogl.vertexPointer = OglPointer.direct(size, type, stride, pointer);
}

public final void glVertexPointer(int size, int type, int stride, int pointer) {
    ogl.vertexPointer = OglPointer.bufferObject(size, type, stride, requireArrayBuffer(), pointer);
}

public final void glTexCoordPointer(int size, int type, int stride, DirectBuffer pointer) {
    ogl.texCoordPointer = OglPointer.direct(size, type, stride, pointer);
}

public final void glTexCoordPointer(int size, int type, int stride, int pointer) {
    ogl.texCoordPointer = OglPointer.bufferObject(size, type, stride, requireArrayBuffer(), pointer);
}

public final void glNormalPointer(int type, int stride, DirectBuffer pointer) {
    ogl.normalPointer = OglPointer.direct(3, type, stride, pointer);
}

public final void glNormalPointer(int type, int stride, int pointer) {
    ogl.normalPointer = OglPointer.bufferObject(3, type, stride, requireArrayBuffer(), pointer);
}

public final void glColorPointer(int size, int type, int stride, DirectBuffer pointer) {
    ogl.colorPointer = OglPointer.direct(size, type, stride, pointer);
}

public final void glColorPointer(int size, int type, int stride, int pointer) {
    ogl.colorPointer = OglPointer.bufferObject(size, type, stride, requireArrayBuffer(), pointer);
}

public final void glCurrentPaletteMatrixOES(int matrixpaletteindex) {
    if (matrixpaletteindex < 0 || matrixpaletteindex >= OGL_MAX_PALETTE_MATRICES) {
        ogl.lastError = GraphicsOGL.GL_INVALID_VALUE;
        return;
    }
    ogl.currentPaletteMatrix = matrixpaletteindex;
}

public final void glLoadPaletteFromModelViewMatrixOES() {
    ogl.paletteMatrices[ogl.currentPaletteMatrix] = ogl.modelViewMatrix.clone();
}

public final void glMatrixIndexPointerOES(int size, int type, int stride, DirectBuffer pointer) {
    ogl.matrixIndexPointer = OglPointer.direct(size, type, stride, pointer);
}

public final void glMatrixIndexPointerOES(int size, int type, int stride, int pointer) {
    ogl.matrixIndexPointer = OglPointer.bufferObject(size, type, stride, requireArrayBuffer(), pointer);
}

public final void glWeightPointerOES(int size, int type, int stride, DirectBuffer pointer) {
    ogl.weightPointer = OglPointer.direct(size, type, stride, pointer);
}

public final void glWeightPointerOES(int size, int type, int stride, int pointer) {
    ogl.weightPointer = OglPointer.bufferObject(size, type, stride, requireArrayBuffer(), pointer);
}

public final void glDrawArrays(int mode, int first, int count) {
    host.markOpenGlesActivity();
    drawOgl(mode, first, count, null);
}

public final void glDrawElements(int mode, int count, int type, DirectBuffer indices) {
    host.markOpenGlesActivity();
    if (type != GraphicsOGL.GL_UNSIGNED_SHORT) {
        ogl.lastError = GraphicsOGL.GL_INVALID_ENUM;
        return;
    }
    if (!(indices instanceof ShortBuffer)) {
        ogl.lastError = GraphicsOGL.GL_INVALID_VALUE;
        return;
    }
    drawOgl(mode, 0, count, OglIndexSource.direct((ShortBuffer) indices));
}

public final void glDrawElements(int mode, int count, int type, int indices) {
    host.markOpenGlesActivity();
    if (type != GraphicsOGL.GL_UNSIGNED_SHORT) {
        ogl.lastError = GraphicsOGL.GL_INVALID_ENUM;
        return;
    }
    if (indices < 0) {
        throw new ArrayIndexOutOfBoundsException("indices");
    }
    OglBufferObject buffer = ogl.boundElementArrayBuffer();
    if (buffer == null) {
        ogl.lastError = GraphicsOGL.GL_INVALID_OPERATION;
        return;
    }
    drawOgl(mode, 0, count, OglIndexSource.bufferObject(buffer, indices));
}

public final void glDrawElements(int mode, int count, DirectBuffer indices) {
    glDrawElements(mode, count, GraphicsOGL.GL_UNSIGNED_SHORT, indices);
}

public final void glFlush() {
    hardware.flush();
}

public final int glGetError() {
    int error = ogl.lastError;
    ogl.lastError = GraphicsOGL.GL_NO_ERROR;
    return error;
}

public final void glGetIntegerv(int pname, int[] params) {
    if (params == null) {
        throw new NullPointerException("params");
    }
    if (params.length < 1) {
        throw new IllegalArgumentException("params");
    }
    switch (pname) {
        case GraphicsOGL.GL_MAX_LIGHTS -> params[0] = 8;
        case GraphicsOGL.GL_MAX_TEXTURE_UNITS -> params[0] = OGL_TEXTURE_UNIT_COUNT;
        case GraphicsOGL.GL_MAX_PALETTE_MATRICES_OES -> params[0] = OGL_MAX_PALETTE_MATRICES;
        case GraphicsOGL.GL_MAX_VERTEX_UNITS_OES -> params[0] = OGL_MAX_VERTEX_UNITS;
        default -> ogl.lastError = GraphicsOGL.GL_INVALID_ENUM;
    }
}

public final void glGetFloatv(int pname, float[] params) {
    if (params == null) {
        throw new NullPointerException("params");
    }
    switch (pname) {
        case GraphicsOGL.GL_MODELVIEW_MATRIX -> copyFloatState(ogl.modelViewMatrix, params, 16);
        case GraphicsOGL.GL_PROJECTION_MATRIX -> copyFloatState(ogl.projectionMatrix, params, 16);
        case GraphicsOGL.GL_TEXTURE_MATRIX -> copyFloatState(ogl.textureMatrix, params, 16);
        case GraphicsOGL.GL_VIEWPORT -> {
            requireLength(params, 4);
            params[0] = ogl.viewportX;
            params[1] = ogl.viewportY;
            params[2] = ogl.viewportWidth;
            params[3] = ogl.viewportHeight;
        }
        case GraphicsOGL.GL_DEPTH_RANGE -> {
            requireLength(params, 2);
            params[0] = ogl.depthRangeNear;
            params[1] = ogl.depthRangeFar;
        }
        default -> ogl.lastError = GraphicsOGL.GL_INVALID_ENUM;
    }
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
    ogl.lastError = GraphicsOGL.GL_INVALID_VALUE;
    return 1;
}
private void loadMatrix(int mode, float[] matrix) {
    switch (mode) {
        case GraphicsOGL.GL_MODELVIEW -> {
            ogl.modelViewMatrix = matrix;
            ogl.standardModelViewConfigured = true;
        }
        case GraphicsOGL.GL_PROJECTION -> {
            ogl.projectionMatrix = matrix;
            ogl.standardProjectionConfigured = true;
        }
        case GraphicsOGL.GL_TEXTURE -> ogl.textureMatrix = matrix;
        case GraphicsOGL.GL_MATRIX_PALETTE_OES ->
                ogl.paletteMatrices[ogl.currentPaletteMatrix] = matrix;
        default -> {
            if (ogl.renderer.acceptsExtensionMatrixMode(mode)) {
                ogl.renderer.loadExtensionMatrix(mode, matrix);
            } else {
                ogl.lastError = GraphicsOGL.GL_INVALID_ENUM;
            }
        }
    }
}

private void multiplyCurrentMatrix(float[] matrix) {
    switch (ogl.matrixMode) {
        case GraphicsOGL.GL_MODELVIEW -> {
            ogl.modelViewMatrix = multiplyMatrices(ogl.modelViewMatrix, matrix);
            ogl.standardModelViewConfigured = true;
        }
        case GraphicsOGL.GL_PROJECTION -> {
            ogl.projectionMatrix = multiplyMatrices(ogl.projectionMatrix, matrix);
            ogl.standardProjectionConfigured = true;
        }
        case GraphicsOGL.GL_TEXTURE ->
                ogl.textureMatrix = multiplyMatrices(ogl.textureMatrix, matrix);
        case GraphicsOGL.GL_MATRIX_PALETTE_OES ->
                ogl.paletteMatrices[ogl.currentPaletteMatrix] = multiplyMatrices(
                        ogl.paletteMatrices[ogl.currentPaletteMatrix],
                        matrix);
        default -> {
            if (ogl.renderer.acceptsExtensionMatrixMode(ogl.matrixMode)) {
                ogl.renderer.multiplyExtensionMatrix(ogl.matrixMode, matrix);
            } else {
                ogl.lastError = GraphicsOGL.GL_INVALID_ENUM;
            }
        }
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
        case GraphicsOGL.GL_MODELVIEW -> ogl.modelViewStack.push(ogl.modelViewMatrix.clone());
        case GraphicsOGL.GL_PROJECTION -> ogl.projectionStack.push(ogl.projectionMatrix.clone());
        case GraphicsOGL.GL_TEXTURE -> ogl.textureStack.push(ogl.textureMatrix.clone());
        case GraphicsOGL.GL_MATRIX_PALETTE_OES ->
                ogl.paletteMatrixStacks[ogl.currentPaletteMatrix].push(ogl.paletteMatrices[ogl.currentPaletteMatrix].clone());
        default -> {
            if (ogl.renderer.acceptsExtensionMatrixMode(mode)) {
                ogl.renderer.pushExtensionMatrix(mode);
            } else {
                ogl.lastError = GraphicsOGL.GL_INVALID_ENUM;
            }
        }
    }
}

private void popCurrentMatrix(int mode) {
    switch (mode) {
        case GraphicsOGL.GL_MODELVIEW -> {
            if (ogl.modelViewStack.isEmpty()) {
                ogl.lastError = GraphicsOGL.GL_STACK_UNDERFLOW;
                return;
            }
            ogl.modelViewMatrix = ogl.modelViewStack.pop();
            ogl.standardModelViewConfigured = true;
        }
        case GraphicsOGL.GL_PROJECTION -> {
            if (ogl.projectionStack.isEmpty()) {
                ogl.lastError = GraphicsOGL.GL_STACK_UNDERFLOW;
                return;
            }
            ogl.projectionMatrix = ogl.projectionStack.pop();
            ogl.standardProjectionConfigured = true;
        }
        case GraphicsOGL.GL_TEXTURE -> {
            if (ogl.textureStack.isEmpty()) {
                ogl.lastError = GraphicsOGL.GL_STACK_UNDERFLOW;
                return;
            }
            ogl.textureMatrix = ogl.textureStack.pop();
        }
        case GraphicsOGL.GL_MATRIX_PALETTE_OES -> {
            if (ogl.paletteMatrixStacks[ogl.currentPaletteMatrix].isEmpty()) {
                ogl.lastError = GraphicsOGL.GL_STACK_UNDERFLOW;
                return;
            }
            ogl.paletteMatrices[ogl.currentPaletteMatrix] = ogl.paletteMatrixStacks[ogl.currentPaletteMatrix].pop();
        }
        default -> {
            if (ogl.renderer.acceptsExtensionMatrixMode(mode)) {
                if (!ogl.renderer.popExtensionMatrix(mode)) {
                    ogl.lastError = GraphicsOGL.GL_STACK_UNDERFLOW;
                }
            } else {
                ogl.lastError = GraphicsOGL.GL_INVALID_ENUM;
            }
        }
    }
}

private OglBufferObject requireArrayBuffer() {
    OglBufferObject buffer = ogl.boundArrayBuffer();
    if (buffer == null) {
        ogl.lastError = GraphicsOGL.GL_INVALID_OPERATION;
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
    if (usesHardwareRenderer() && hardware.draw(mode, first, count, indexSource)) {
        return;
    }
    hardware.flush();
    host.onSoftwareSurfaceMutation();
    int primitiveCount = count;
    if (indexSource != null) {
        primitiveCount = Math.min(Math.max(0, count), indexSource.elementCount());
    }
    Rectangle clip = host.delegate().getClipBounds();
    BufferedImage target = host.surface().image();
    int[] pixels = ((DataBufferInt) target.getRaster().getDataBuffer()).getData();
    float[] depthBuffer = host.surface().depthBufferForFrame();
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
        case GraphicsOGL.GL_TRIANGLES -> {
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
        case GraphicsOGL.GL_TRIANGLE_STRIP -> {
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
        case GraphicsOGL.GL_LINE_LOOP ->
                drawLineLoop(first, primitiveCount, indexSource, clip, target.getWidth(), target.getHeight(),
                        scratch0, scratch1, scratch2, clip0, clip1);
        default -> ogl.lastError = GraphicsOGL.GL_INVALID_ENUM;
    }
}

private int resolveVertexIndex(int first, OglIndexSource elementIndices, int primitiveIndex) {
    if (elementIndices == null) {
        return first + primitiveIndex;
    }
    int index = elementIndices.indexAt(primitiveIndex);
    if (index < 0) {
        ogl.lastError = GraphicsOGL.GL_INVALID_VALUE;
        return 0;
    }
    return index;
}

private boolean populateRasterVertex(RasterVertex targetVertex, ClipVector clipVector, int vertexIndex) {
    OglPointer vertexPointer = ogl.vertexPointer;
    if (vertexPointer == null) {
        return false;
    }
    if (vertexPointer.type() != GraphicsOGL.GL_FLOAT) {
        ogl.lastError = GraphicsOGL.GL_INVALID_ENUM;
        return false;
    }
    int positionSize = Math.max(1, vertexPointer.size());
    float x = readFloatComponent(vertexPointer, vertexIndex, 0);
    if (ogl.lastError != GraphicsOGL.GL_NO_ERROR) {
        return false;
    }
    float y = positionSize > 1 ? readFloatComponent(vertexPointer, vertexIndex, 1) : 0f;
    if (ogl.lastError != GraphicsOGL.GL_NO_ERROR) {
        return false;
    }
    float z = positionSize > 2 ? readFloatComponent(vertexPointer, vertexIndex, 2) : 0f;
    if (ogl.lastError != GraphicsOGL.GL_NO_ERROR) {
        return false;
    }
    float u = 0f;
    float v = 0f;
    OglPointer texCoordPointer = ogl.texCoordArrayEnabled
            ? ogl.texCoordPointer
            : null;
    if (texCoordPointer != null) {
        if (texCoordPointer.type() != GraphicsOGL.GL_FLOAT) {
            ogl.lastError = GraphicsOGL.GL_INVALID_ENUM;
            return false;
        }
        int texSize = Math.max(1, texCoordPointer.size());
        u = readFloatComponent(texCoordPointer, vertexIndex, 0);
        if (ogl.lastError != GraphicsOGL.GL_NO_ERROR) {
            return false;
        }
        if (texSize > 1) {
            v = readFloatComponent(texCoordPointer, vertexIndex, 1);
            if (ogl.lastError != GraphicsOGL.GL_NO_ERROR) {
                return false;
            }
        }
    }
    boolean useExtensionMatrices = positionSize >= 3 && ogl.usesExtensionMatrices();
    transformVertex(clipVector, eyeVectorTemp, x, y, z, vertexIndex, useExtensionMatrices);
    float w = Math.abs(clipVector.w) < 0.000001f ? (clipVector.w < 0f ? -0.000001f : 0.000001f) : clipVector.w;
    float ndcX = clipVector.x / w;
    float ndcY = clipVector.y / w;
    float ndcZ = clipVector.z / w;
    float windowDepth = ogl.depthRangeNear + (((ndcZ + 1f) * 0.5f) * (ogl.depthRangeFar - ogl.depthRangeNear));
    float depth = 1f - windowDepth;
    float reciprocalW = 1f / Math.max(0.000001f, Math.abs(w));
    int primaryColor = resolvePrimaryColor(vertexIndex);
    if (ogl.lastError != GraphicsOGL.GL_NO_ERROR) {
        return false;
    }
    int backColor = primaryColor;
    if (ogl.lightingEnabled()) {
        int sourceColor = primaryColor;
        primaryColor = resolveLitColor(vertexIndex, eyeVectorTemp, sourceColor, false);
        if (ogl.lastError != GraphicsOGL.GL_NO_ERROR) {
            return false;
        }
        backColor = ogl.lightModelTwoSide
                ? resolveLitColor(vertexIndex, eyeVectorTemp, sourceColor, true)
                : primaryColor;
        if (ogl.lastError != GraphicsOGL.GL_NO_ERROR) {
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
    if (colorPointer.type() != GraphicsOGL.GL_UNSIGNED_BYTE) {
        ogl.lastError = GraphicsOGL.GL_INVALID_ENUM;
        return 0;
    }
    int red = colorPointer.size() > 0 ? readUnsignedByteComponent(colorPointer, vertexIndex, 0) : 255;
    if (ogl.lastError != GraphicsOGL.GL_NO_ERROR) {
        return 0;
    }
    int green = colorPointer.size() > 1 ? readUnsignedByteComponent(colorPointer, vertexIndex, 1) : red;
    if (ogl.lastError != GraphicsOGL.GL_NO_ERROR) {
        return 0;
    }
    int blue = colorPointer.size() > 2 ? readUnsignedByteComponent(colorPointer, vertexIndex, 2) : red;
    if (ogl.lastError != GraphicsOGL.GL_NO_ERROR) {
        return 0;
    }
    int alpha = colorPointer.size() > 3 ? readUnsignedByteComponent(colorPointer, vertexIndex, 3) : 255;
    if (ogl.lastError != GraphicsOGL.GL_NO_ERROR) {
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
    if (ogl.lastError != GraphicsOGL.GL_NO_ERROR) {
        return 0;
    }
    return computeLightingColor(eyePosition, normalVectorTemp.x, normalVectorTemp.y, normalVectorTemp.z,
            ambientRed, ambientGreen, ambientBlue,
            diffuseRed, diffuseGreen, diffuseBlue, diffuseAlpha,
            ogl.materialSpecular[0], ogl.materialSpecular[1], ogl.materialSpecular[2],
            ogl.materialEmission[0], ogl.materialEmission[1], ogl.materialEmission[2],
            ogl.materialShininess);
}

private void transformVertex(ClipVector target, ClipVector eyeTarget, float x, float y, float z, int vertexIndex, boolean useExtensionMatrices) {
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
            if (ogl.lastError != GraphicsOGL.GL_NO_ERROR) {
                return;
            }
            if (matrixIndex < 0 || matrixIndex >= OGL_MAX_PALETTE_MATRICES) {
                ogl.lastError = GraphicsOGL.GL_INVALID_VALUE;
                return;
            }
            float weight = readFloatComponent(ogl.weightPointer, vertexIndex, unit);
            if (ogl.lastError != GraphicsOGL.GL_NO_ERROR) {
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
    if (useExtensionMatrices) {
        multiply(clipVectorTemp, ogl.renderer.extensionWorldMatrix(), x, y, z, 1f);
        eyeTarget.set(clipVectorTemp.x, clipVectorTemp.y, clipVectorTemp.z, clipVectorTemp.w);
        multiply(target, ogl.renderer.extensionCameraMatrix(), clipVectorTemp.x, clipVectorTemp.y, clipVectorTemp.z, clipVectorTemp.w);
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
            if (ogl.lastError != GraphicsOGL.GL_NO_ERROR) {
                return;
            }
            float weight = readFloatComponent(ogl.weightPointer, vertexIndex, unit);
            if (ogl.lastError != GraphicsOGL.GL_NO_ERROR) {
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
        if (ogl.lastError != GraphicsOGL.GL_NO_ERROR) {
            target.set(0f, 0f, 1f, 0f);
            return;
        }
        ny = readNormalComponent(ogl.normalPointer, vertexIndex, 1);
        if (ogl.lastError != GraphicsOGL.GL_NO_ERROR) {
            target.set(0f, 0f, 1f, 0f);
            return;
        }
        nz = readNormalComponent(ogl.normalPointer, vertexIndex, 2);
        if (ogl.lastError != GraphicsOGL.GL_NO_ERROR) {
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
            ogl.lastError = GraphicsOGL.GL_INVALID_VALUE;
            return 0f;
        }
        int elementIndex = DirectBufferFactory.getSegmentOffset(floatBuffer) + (byteOffset / 4);
        if (elementIndex < 0 || elementIndex >= DirectBufferFactory.getSegmentOffset(floatBuffer)
                + DirectBufferFactory.getSegmentLength(floatBuffer)) {
            ogl.lastError = GraphicsOGL.GL_INVALID_VALUE;
            return 0f;
        }
        return DirectBufferFactory.getFloat(floatBuffer, elementIndex);
    }
    if (pointer.pointer() instanceof ByteBuffer byteBuffer) {
        byte[] raw = new byte[4];
        int baseIndex = DirectBufferFactory.getSegmentOffset(byteBuffer) + byteOffset;
        if (baseIndex < 0 || baseIndex + raw.length > DirectBufferFactory.getSegmentOffset(byteBuffer)
                + DirectBufferFactory.getSegmentLength(byteBuffer)) {
            ogl.lastError = GraphicsOGL.GL_INVALID_VALUE;
            return 0f;
        }
        byteBuffer.get(baseIndex, raw, 0, raw.length);
        return readFloat(raw, 0);
    }
    ogl.lastError = GraphicsOGL.GL_INVALID_VALUE;
    return 0f;
}

private float readNormalComponent(OglPointer pointer, int vertexIndex, int componentIndex) {
    int byteOffset = pointer.componentByteOffset(vertexIndex, componentIndex);
    if (pointer.bufferObject() != null) {
        return readTypedFloat(pointer.type(), pointer.bufferObject().data(), byteOffset);
    }
    return switch (pointer.type()) {
        case GraphicsOGL.GL_FLOAT -> readFloatComponent(pointer, vertexIndex, componentIndex);
        case GraphicsOGL.GL_BYTE -> readDirectByteComponent(pointer, byteOffset);
        case GraphicsOGL.GL_SHORT -> readDirectShortComponent(pointer, byteOffset);
        default -> {
            ogl.lastError = GraphicsOGL.GL_INVALID_ENUM;
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
            ogl.lastError = GraphicsOGL.GL_INVALID_VALUE;
            return 0;
        }
        byte[] raw = new byte[1];
        byteBuffer.get(baseIndex, raw, 0, 1);
        return raw[0] & 0xFF;
    }
    ogl.lastError = GraphicsOGL.GL_INVALID_VALUE;
    return 0;
}

private float readDirectByteComponent(OglPointer pointer, int byteOffset) {
    if (!(pointer.pointer() instanceof ByteBuffer byteBuffer)) {
        ogl.lastError = GraphicsOGL.GL_INVALID_VALUE;
        return 0f;
    }
    int baseIndex = DirectBufferFactory.getSegmentOffset(byteBuffer) + byteOffset;
    if (baseIndex < 0 || baseIndex >= DirectBufferFactory.getSegmentOffset(byteBuffer)
            + DirectBufferFactory.getSegmentLength(byteBuffer)) {
        ogl.lastError = GraphicsOGL.GL_INVALID_VALUE;
        return 0f;
    }
    byte[] raw = new byte[1];
    byteBuffer.get(baseIndex, raw, 0, 1);
    return raw[0];
}

private float readDirectShortComponent(OglPointer pointer, int byteOffset) {
    if (pointer.pointer() instanceof ShortBuffer shortBuffer) {
        if ((byteOffset & 1) != 0) {
            ogl.lastError = GraphicsOGL.GL_INVALID_VALUE;
            return 0f;
        }
        int elementIndex = DirectBufferFactory.getSegmentOffset(shortBuffer) + (byteOffset / 2);
        if (elementIndex < 0 || elementIndex >= DirectBufferFactory.getSegmentOffset(shortBuffer)
                + DirectBufferFactory.getSegmentLength(shortBuffer)) {
            ogl.lastError = GraphicsOGL.GL_INVALID_VALUE;
            return 0f;
        }
        return DirectBufferFactory.getShort(shortBuffer, elementIndex);
    }
    if (pointer.pointer() instanceof ByteBuffer byteBuffer) {
        int baseIndex = DirectBufferFactory.getSegmentOffset(byteBuffer) + byteOffset;
        if (baseIndex < 0 || baseIndex + 1 >= DirectBufferFactory.getSegmentOffset(byteBuffer)
                + DirectBufferFactory.getSegmentLength(byteBuffer)) {
            ogl.lastError = GraphicsOGL.GL_INVALID_VALUE;
            return 0f;
        }
        byte[] raw = new byte[2];
        byteBuffer.get(baseIndex, raw, 0, raw.length);
        return readSignedShort(raw, 0);
    }
    ogl.lastError = GraphicsOGL.GL_INVALID_VALUE;
    return 0f;
}

private float readTypedFloat(int type, byte[] data, int byteOffset) {
    return switch (type) {
        case GraphicsOGL.GL_FLOAT -> readFloat(data, byteOffset);
        case GraphicsOGL.GL_BYTE -> readSignedByte(data, byteOffset);
        case GraphicsOGL.GL_SHORT -> readSignedShort(data, byteOffset);
        default -> {
            ogl.lastError = GraphicsOGL.GL_INVALID_ENUM;
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
        ogl.lastError = GraphicsOGL.GL_INVALID_VALUE;
        return 0f;
    }
    return data[byteOffset];
}

private float readSignedShort(byte[] data, int byteOffset) {
    if (byteOffset < 0 || byteOffset + 1 >= data.length) {
        ogl.lastError = GraphicsOGL.GL_INVALID_VALUE;
        return 0f;
    }
    return (short) ((data[byteOffset] & 0xFF) | ((data[byteOffset + 1] & 0xFF) << 8));
}

private int readUnsignedByte(byte[] data, int byteOffset) {
    if (byteOffset < 0 || byteOffset >= data.length) {
        ogl.lastError = GraphicsOGL.GL_INVALID_VALUE;
        return 0;
    }
    return data[byteOffset] & 0xFF;
}

private float readFloat(byte[] data, int byteOffset) {
    if (byteOffset < 0 || byteOffset + 3 >= data.length) {
        ogl.lastError = GraphicsOGL.GL_INVALID_VALUE;
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
    return host.surface().height() - bottomLeftY;
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
    Color old = host.delegate().getColor();
    Shape oldClip = host.delegate().getClip();
    host.delegate().setColor(new Color(firstVertex.color, true));
    if (clip != null) {
        host.delegate().setClip(clip);
    }
    try {
        for (int i = 1; i < primitiveCount; i++) {
            if (!populateRasterVertex(currentVertex, clip1, resolveVertexIndex(first, elementIndices, i))) {
                continue;
            }
            host.delegate().drawLine(clamp(Math.round(previousVertex.x), 0, width - 1),
                    clamp(Math.round(previousVertex.y), 0, height - 1),
                    clamp(Math.round(currentVertex.x), 0, width - 1),
                    clamp(Math.round(currentVertex.y), 0, height - 1));
            RasterVertex swap = previousVertex;
            previousVertex = currentVertex;
            currentVertex = swap;
        }
        host.delegate().drawLine(clamp(Math.round(previousVertex.x), 0, width - 1),
                clamp(Math.round(previousVertex.y), 0, height - 1),
                clamp(Math.round(firstVertex.x), 0, width - 1),
                clamp(Math.round(firstVertex.y), 0, height - 1));
    } finally {
        host.delegate().setColor(old);
        host.delegate().setClip(oldClip);
    }
}

private boolean isCulled(RasterVertex v0, RasterVertex v1, RasterVertex v2) {
    if (!ogl.cullFaceEnabled) {
        return false;
    }
    boolean frontFacing = isFrontFacing(v0, v1, v2);
    return switch (ogl.cullFace) {
        case GraphicsOGL.GL_FRONT -> frontFacing;
        case GraphicsOGL.GL_FRONT_AND_BACK -> true;
        default -> !frontFacing;
    };
}

private boolean isFrontFacing(RasterVertex v0, RasterVertex v1, RasterVertex v2) {
    float area = edge(v0.x, v0.y, v1.x, v1.y, v2.x, v2.y);
    boolean ccw = area > 0f;
    return ogl.frontFace == GraphicsOGL.GL_CCW ? ccw : !ccw;
}

private boolean passesDepth(float incoming, float existing) {
    if (!ogl.depthEnabled()) {
        return true;
    }
    return switch (ogl.depthFunc) {
        case GraphicsOGL.GL_LESS -> incoming > existing + 0.00001f;
        case GraphicsOGL.GL_LEQUAL -> incoming >= existing - 0.00001f;
        case GraphicsOGL.GL_EQUAL -> Math.abs(incoming - existing) <= 0.00001f;
        case GraphicsOGL.GL_GREATER -> incoming < existing - 0.00001f;
        case GraphicsOGL.GL_GEQUAL -> incoming <= existing + 0.00001f;
        case GraphicsOGL.GL_ALWAYS -> true;
        case GraphicsOGL.GL_NEVER -> false;
        default -> incoming > existing + 0.00001f;
    };
}

private boolean passesAlphaTest(int color) {
    if (!ogl.alphaTestEnabled) {
        return true;
    }
    float alpha = ((color >>> 24) & 0xFF) / 255f;
    return switch (ogl.alphaFunc) {
        case GraphicsOGL.GL_NOTEQUAL -> Math.abs(alpha - ogl.alphaRef) > 0.0001f;
        case GraphicsOGL.GL_GREATER -> alpha > ogl.alphaRef;
        case GraphicsOGL.GL_GEQUAL -> alpha >= ogl.alphaRef;
        case GraphicsOGL.GL_EQUAL -> Math.abs(alpha - ogl.alphaRef) <= 0.0001f;
        case GraphicsOGL.GL_LESS -> alpha < ogl.alphaRef;
        case GraphicsOGL.GL_LEQUAL -> alpha <= ogl.alphaRef;
        case GraphicsOGL.GL_ALWAYS -> true;
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
    if (ogl.shadeModel == GraphicsOGL.GL_FLAT) {
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
        case GraphicsOGL.GL_REPLACE ->
                applyReplaceTextureFunction(pr, pg, pb, pa, tr, tg, tb, ta, baseFormat);
        case GraphicsOGL.GL_MODULATE ->
                applyModulateTextureFunction(pr, pg, pb, pa, tr, tg, tb, ta, baseFormat);
        case GraphicsOGL.GL_DECAL ->
                applyDecalTextureFunction(pr, pg, pb, pa, tr, tg, tb, ta, baseFormat);
        case GraphicsOGL.GL_BLEND ->
                applyBlendTextureFunction(pr, pg, pb, pa, tr, tg, tb, ta, er, eg, eb, baseFormat);
        case GraphicsOGL.GL_ADD ->
                applyAddTextureFunction(pr, pg, pb, pa, tr, tg, tb, ta, baseFormat);
        case GraphicsOGL.GL_COMBINE ->
                applyCombineTextureEnvironment(primaryColor, sampledColor, baseFormat);
        default -> primaryColor;
    };
}

private int applyReplaceTextureFunction(float pr, float pg, float pb, float pa,
                                        float tr, float tg, float tb, float ta, int baseFormat) {
    return switch (baseFormat) {
        case GraphicsOGL.GL_ALPHA -> packColor(pr, pg, pb, ta);
        case GraphicsOGL.GL_LUMINANCE -> packColor(tr, tg, tb, pa);
        case GraphicsOGL.GL_LUMINANCE_ALPHA,
                GraphicsOGL.GL_RGBA -> packColor(tr, tg, tb, ta);
        case GraphicsOGL.GL_RGB -> packColor(tr, tg, tb, pa);
        default -> packColor(tr, tg, tb, ta);
    };
}

private int applyModulateTextureFunction(float pr, float pg, float pb, float pa,
                                         float tr, float tg, float tb, float ta, int baseFormat) {
    return switch (baseFormat) {
        case GraphicsOGL.GL_ALPHA -> packColor(pr, pg, pb, pa * ta);
        case GraphicsOGL.GL_LUMINANCE,
                GraphicsOGL.GL_RGB -> packColor(pr * tr, pg * tg, pb * tb, pa);
        case GraphicsOGL.GL_LUMINANCE_ALPHA,
                GraphicsOGL.GL_RGBA -> packColor(pr * tr, pg * tg, pb * tb, pa * ta);
        default -> packColor(pr * tr, pg * tg, pb * tb, pa * ta);
    };
}

private int applyDecalTextureFunction(float pr, float pg, float pb, float pa,
                                      float tr, float tg, float tb, float ta, int baseFormat) {
    return switch (baseFormat) {
        case GraphicsOGL.GL_RGB -> packColor(tr, tg, tb, pa);
        case GraphicsOGL.GL_RGBA -> packColor(
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
        case GraphicsOGL.GL_ALPHA -> packColor(pr, pg, pb, pa * ta);
        case GraphicsOGL.GL_LUMINANCE,
                GraphicsOGL.GL_RGB -> packColor(
                (pr * (1f - tr)) + (er * tr),
                (pg * (1f - tg)) + (eg * tg),
                (pb * (1f - tb)) + (eb * tb),
                pa);
        case GraphicsOGL.GL_LUMINANCE_ALPHA,
                GraphicsOGL.GL_RGBA -> packColor(
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
        case GraphicsOGL.GL_ALPHA -> packColor(pr, pg, pb, pa * ta);
        case GraphicsOGL.GL_LUMINANCE,
                GraphicsOGL.GL_RGB -> packColor(pr + tr, pg + tg, pb + tb, pa);
        case GraphicsOGL.GL_LUMINANCE_ALPHA,
                GraphicsOGL.GL_RGBA -> packColor(pr + tr, pg + tg, pb + tb, pa * ta);
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
        case GraphicsOGL.GL_REPLACE -> alpha0;
        case GraphicsOGL.GL_MODULATE -> alpha0 * alpha1;
        case GraphicsOGL.GL_ADD -> alpha0 + alpha1;
        case GraphicsOGL.GL_ADD_SIGNED -> alpha0 + alpha1 - 0.5f;
        case GraphicsOGL.GL_INTERPOLATE -> (alpha0 * alpha2) + (alpha1 * (1f - alpha2));
        case GraphicsOGL.GL_SUBTRACT -> alpha0 - alpha1;
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
        case GraphicsOGL.GL_REPLACE -> arg0;
        case GraphicsOGL.GL_MODULATE -> arg0 * arg1;
        case GraphicsOGL.GL_ADD -> arg0 + arg1;
        case GraphicsOGL.GL_ADD_SIGNED -> arg0 + arg1 - 0.5f;
        case GraphicsOGL.GL_INTERPOLATE -> (arg0 * arg2) + (arg1 * (1f - arg2));
        case GraphicsOGL.GL_SUBTRACT -> arg0 - arg1;
        default -> textureComponent(sampledColor, channel, baseFormat);
    };
}

private float textureRgbArgument(int index, int channel, int primaryColor, int sampledColor, int baseFormat) {
    int source = ogl.srcRgb[index];
    int operand = ogl.operandRgb[index];
    float component = textureSourceComponent(source, channel, primaryColor, sampledColor, baseFormat);
    float alpha = textureSourceComponent(source, 3, primaryColor, sampledColor, baseFormat);
    return switch (operand) {
        case GraphicsOGL.GL_SRC_ALPHA -> alpha;
        case GraphicsOGL.GL_ONE_MINUS_SRC_ALPHA -> 1f - alpha;
        case GraphicsOGL.GL_ONE_MINUS_SRC_COLOR -> 1f - component;
        default -> component;
    };
}

private float textureAlphaArgument(int index, int primaryColor, int sampledColor, int baseFormat) {
    float alpha = textureSourceComponent(ogl.srcAlpha[index], 3, primaryColor, sampledColor, baseFormat);
    return ogl.operandAlpha[index] == GraphicsOGL.GL_ONE_MINUS_SRC_ALPHA ? 1f - alpha : alpha;
}

private float textureSourceComponent(int source, int channel, int primaryColor, int sampledColor, int baseFormat) {
    return switch (source) {
        case GraphicsOGL.GL_TEXTURE -> textureComponent(sampledColor, channel, baseFormat);
        case GraphicsOGL.GL_CONSTANT -> packedComponent(ogl.textureEnvColor, channel);
        case GraphicsOGL.GL_PRIMARY_COLOR,
                GraphicsOGL.GL_PREVIOUS -> packedComponent(primaryColor, channel);
        default -> packedComponent(primaryColor, channel);
    };
}

private static float textureComponent(int packed, int channel, int baseFormat) {
    return switch (baseFormat) {
        case GraphicsOGL.GL_ALPHA -> channel == 3 ? packedComponent(packed, 3) : 0f;
        case GraphicsOGL.GL_LUMINANCE -> channel == 3 ? 1f : packedComponent(packed, 0);
        case GraphicsOGL.GL_LUMINANCE_ALPHA ->
                channel == 3 ? packedComponent(packed, 3) : packedComponent(packed, 0);
        case GraphicsOGL.GL_RGB -> channel == 3 ? 1f : packedComponent(packed, channel);
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
        case GraphicsOGL.GL_ZERO -> 0f;
        case GraphicsOGL.GL_ONE -> 1f;
        case GraphicsOGL.GL_SRC_COLOR -> channelComponent(channel, sourceR, sourceG, sourceB, sourceA);
        case GraphicsOGL.GL_ONE_MINUS_SRC_COLOR ->
                1f - channelComponent(channel, sourceR, sourceG, sourceB, sourceA);
        case GraphicsOGL.GL_DST_COLOR ->
                channelComponent(channel, destinationR, destinationG, destinationB, destinationA);
        case GraphicsOGL.GL_ONE_MINUS_DST_COLOR ->
                1f - channelComponent(channel, destinationR, destinationG, destinationB, destinationA);
        case GraphicsOGL.GL_SRC_ALPHA -> sourceA;
        case GraphicsOGL.GL_ONE_MINUS_SRC_ALPHA -> 1f - sourceA;
        case GraphicsOGL.GL_DST_ALPHA -> destinationA;
        case GraphicsOGL.GL_ONE_MINUS_DST_ALPHA -> 1f - destinationA;
        case GraphicsOGL.GL_SRC_ALPHA_SATURATE ->
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
        case GraphicsOGL.GL_ZERO,
                GraphicsOGL.GL_ONE,
                GraphicsOGL.GL_SRC_COLOR,
                GraphicsOGL.GL_ONE_MINUS_SRC_COLOR,
                GraphicsOGL.GL_DST_COLOR,
                GraphicsOGL.GL_ONE_MINUS_DST_COLOR,
                GraphicsOGL.GL_SRC_ALPHA,
                GraphicsOGL.GL_ONE_MINUS_SRC_ALPHA,
                GraphicsOGL.GL_DST_ALPHA,
                GraphicsOGL.GL_ONE_MINUS_DST_ALPHA,
                GraphicsOGL.GL_SRC_ALPHA_SATURATE -> true;
        default -> false;
    };
}

private static boolean isValidBlendDstFactor(int factor) {
    return switch (factor) {
        case GraphicsOGL.GL_ZERO,
                GraphicsOGL.GL_ONE,
                GraphicsOGL.GL_SRC_COLOR,
                GraphicsOGL.GL_ONE_MINUS_SRC_COLOR,
                GraphicsOGL.GL_SRC_ALPHA,
                GraphicsOGL.GL_ONE_MINUS_SRC_ALPHA,
                GraphicsOGL.GL_DST_ALPHA,
                GraphicsOGL.GL_ONE_MINUS_DST_ALPHA -> true;
        default -> false;
    };
}

private static float edge(float ax, float ay, float bx, float by, float px, float py) {
    return ((px - ax) * (by - ay)) - ((py - ay) * (bx - ax));
}

private static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
}

private interface HardwareGlCall {
    boolean execute(GL2 gl);
}

private static final class SharedGlObjectStore {
    private final AtomicInteger nextTextureId = new AtomicInteger(1);
    private final AtomicInteger nextBufferId = new AtomicInteger(1);
    private final Map<Integer, OglTexture> textures = new ConcurrentHashMap<>();
    private final Map<Integer, OglBufferObject> buffers = new ConcurrentHashMap<>();

    int createTextureId() {
        int id = nextTextureId.getAndIncrement();
        textures.put(id, new OglTexture());
        return id;
    }

    int createBufferId() {
        int id = nextBufferId.getAndIncrement();
        buffers.put(id, new OglBufferObject());
        return id;
    }
}

private final class HardwareBackend {
    private final Map<Integer, HardwareTexture> textureCache = new HashMap<>();
    private GLOffscreenAutoDrawable drawable;
    private boolean available;
    private boolean failureLogged;
    private boolean surfaceDirty = true;
    private boolean readbackPending;
    private int drawableWidth = -1;
    private int drawableHeight = -1;
    private int surfaceTextureId;
    private int surfaceTextureWidth = -1;
    private int surfaceTextureHeight = -1;
    private java.nio.ByteBuffer readbackBuffer;
    private java.nio.ByteBuffer surfaceUploadBuffer;
    private java.nio.ByteBuffer textureUploadBuffer;
    private float[] vertexScratch = new float[0];
    private float[] normalScratch = new float[0];
    private float[] texCoordScratch = new float[0];
    private byte[] colorScratch = new byte[0];
    private short[] indexScratch = new short[0];
    private final float[] viewportProjectionScratch = new float[16];
    private final float[] viewportTransformScratch = new float[16];
    private java.nio.ByteBuffer vertexBufferBytes;
    private java.nio.ByteBuffer normalBufferBytes;
    private java.nio.ByteBuffer texCoordBufferBytes;
    private java.nio.ByteBuffer colorBufferBytes;
    private java.nio.ByteBuffer indexBufferBytes;
    private int[] lastHardwareSnapshot;
    private int[] outsideLockOverlaySnapshot;
    private Rectangle outsideLockOverlayBounds;

    boolean clear(int mask) {
        if ((mask & (GraphicsOGL.GL_COLOR_BUFFER_BIT | GraphicsOGL.GL_DEPTH_BUFFER_BIT)) == 0) {
            return true;
        }
        return withContext(gl -> {
            prepareFramebuffer(gl);
            applyViewportState(gl);
            applyClipState(gl);
            int glMask = 0;
            if ((mask & GraphicsOGL.GL_COLOR_BUFFER_BIT) != 0) {
                float[] clearColor = unpackColor(oglClearColor);
                gl.glClearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
                glMask |= GraphicsOGL.GL_COLOR_BUFFER_BIT;
            }
            if ((mask & GraphicsOGL.GL_DEPTH_BUFFER_BIT) != 0) {
                gl.glDepthMask(true);
                gl.glClearDepth(0d);
                glMask |= GraphicsOGL.GL_DEPTH_BUFFER_BIT;
            }
            if (glMask != 0) {
                gl.glClear(glMask);
                if ((mask & GraphicsOGL.GL_DEPTH_BUFFER_BIT) != 0) {
                    gl.glDepthMask(ogl.depthMask);
                }
                if ((mask & GraphicsOGL.GL_COLOR_BUFFER_BIT) != 0) {
                    readbackPending = true;
                }
            }
            return gl.glGetError() == GL.GL_NO_ERROR;
        });
    }

    boolean draw(int mode, int first, int count, OglIndexSource indexSource) {
        int primitiveCount = indexSource == null ? count : Math.min(Math.max(0, count), indexSource.elementCount());
        if (primitiveCount <= 0) {
            return true;
        }
        int glMode = toHardwarePrimitiveMode(mode);
        if (glMode == -1) {
            return false;
        }
        PreparedArrayState arrayState = prepareArrayState(first, primitiveCount, indexSource);
        if (arrayState == null) {
            return false;
        }
        return withContext(gl -> {
            prepareFramebuffer(gl);
            applyClipState(gl);
            applyViewportState(gl);
            applyRenderState(gl, arrayState.emulateMatrixPalette());
            applyTextureState(gl);
            bindArrayState(gl, arrayState);
            try {
                if (arrayState.indexBuffer() == null) {
                    gl.glDrawArrays(glMode, arrayState.drawFirst(), arrayState.drawCount());
                } else {
                    gl.glDrawElements(glMode, arrayState.drawCount(), GraphicsOGL.GL_UNSIGNED_SHORT, arrayState.indexBuffer());
                }
            } finally {
                unbindArrayState(gl);
            }
            readbackPending = true;
            return gl.glGetError() == GL.GL_NO_ERROR;
        });
    }

    void endDrawing() {
        flush();
    }

    void prepareForSoftwareMutation() {
        if (readbackPending) {
            flush();
        }
    }

    void flush() {
        if (!readbackPending) {
            return;
        }
        withContext(gl -> {
            gl.glFlush();
            readBackColorBuffer(gl);
            int[] currentHardwareSnapshot = copySurfacePixels(host.surface().image());
            int overlayPixels = reapplyOutsideLockOverlay(lastHardwareSnapshot, outsideLockOverlaySnapshot);
            readbackPending = false;
            surfaceDirty = false;
            lastHardwareSnapshot = currentHardwareSnapshot;
            return true;
        });
    }

    void onTextureDeleted(int textureId) {
        HardwareTexture removed = textureCache.remove(textureId);
        if (removed == null || removed.textureId() == 0) {
            return;
        }
        withContext(gl -> {
            gl.glDeleteTextures(1, new int[]{removed.textureId()}, 0);
            return true;
        });
    }

    void onSoftwareSurfaceMutation() {
        surfaceDirty = true;
    }

    void onPresentedSoftwareOverlay(Rectangle bounds) {
        outsideLockOverlaySnapshot = copySurfacePixels(host.surface().image());
        Rectangle surfaceBounds = new Rectangle(0, 0, host.surface().width(), host.surface().height());
        if (bounds != null) {
            outsideLockOverlayBounds = bounds.intersection(surfaceBounds);
        } else {
            Rectangle clip = host.delegate().getClipBounds();
            outsideLockOverlayBounds = clip == null ? surfaceBounds : clip.intersection(surfaceBounds);
        }
        if (lastHardwareSnapshot == null) {
            surfaceDirty = true;
        }
    }

    void onHostDelegateRecreated() {
        surfaceDirty = true;
    }

    void close() {
        if (drawable == null) {
            textureCache.clear();
            surfaceTextureId = 0;
            surfaceTextureWidth = -1;
            surfaceTextureHeight = -1;
            readbackBuffer = null;
            surfaceUploadBuffer = null;
            textureUploadBuffer = null;
            available = false;
            return;
        }
        withContext(gl -> {
            int[] textureIds = new int[textureCache.size() + (surfaceTextureId == 0 ? 0 : 1)];
            int offset = 0;
            for (HardwareTexture texture : textureCache.values()) {
                if (texture.textureId() != 0) {
                    textureIds[offset++] = texture.textureId();
                }
            }
            if (surfaceTextureId != 0) {
                textureIds[offset++] = surfaceTextureId;
            }
            if (offset > 0) {
                gl.glDeleteTextures(offset, textureIds, 0);
            }
            return true;
        });
        drawable.destroy();
        drawable = null;
        textureCache.clear();
        surfaceTextureId = 0;
        surfaceTextureWidth = -1;
        surfaceTextureHeight = -1;
        readbackBuffer = null;
        surfaceUploadBuffer = null;
        textureUploadBuffer = null;
        drawableWidth = -1;
        drawableHeight = -1;
        available = false;
        surfaceDirty = true;
        readbackPending = false;
        lastHardwareSnapshot = null;
        outsideLockOverlaySnapshot = null;
        outsideLockOverlayBounds = null;
    }

    private boolean withContext(HardwareGlCall call) {
        if (!ensureDrawable()) {
            return false;
        }
        GLContext context = drawable.getContext();
        boolean current = false;
        try {
            current = context.makeCurrent() != GLContext.CONTEXT_NOT_CURRENT;
            if (!current) {
                return false;
            }
            return call.execute(drawable.getGL().getGL2());
        } catch (RuntimeException exception) {
            available = false;
            if (!failureLogged) {
                failureLogged = true;
                OpenDoJaLog.warn(OglRenderer.class, "OpenGLES hardware backend unavailable, falling back to software", exception);
            }
            return false;
        } finally {
            if (current) {
                context.release();
            }
        }
    }

    private boolean ensureDrawable() {
        int width = Math.max(1, host.surface().width());
        int height = Math.max(1, host.surface().height());
        if (available && drawable != null && drawableWidth == width && drawableHeight == height) {
            return true;
        }
        if (drawable != null) {
            drawable.destroy();
            drawable = null;
        }
        try {
            GLProfile profile = GLProfile.getMaxFixedFunc(true);
            GLCapabilities capabilities = new GLCapabilities(profile);
            capabilities.setOnscreen(false);
            capabilities.setPBuffer(true);
            capabilities.setDoubleBuffered(false);
            capabilities.setAlphaBits(8);
            capabilities.setDepthBits(24);
            GLDrawableFactory factory = GLDrawableFactory.getFactory(profile);
            drawable = factory.createOffscreenAutoDrawable(null, capabilities, null, width, height);
            drawable.display();
            drawableWidth = width;
            drawableHeight = height;
            available = true;
            surfaceDirty = true;
            readbackPending = false;
            lastHardwareSnapshot = null;
            return true;
        } catch (RuntimeException exception) {
            drawable = null;
            drawableWidth = width;
            drawableHeight = height;
            available = false;
            if (!failureLogged) {
                failureLogged = true;
                OpenDoJaLog.warn(OglRenderer.class, "Failed to initialize OpenGLES hardware backend", exception);
            }
            return false;
        }
    }

    private PreparedArrayState prepareArrayState(int first, int primitiveCount, OglIndexSource indexSource) {
        OglPointer vertexPointer = ogl.vertexPointer;
        if (vertexPointer == null || vertexPointer.type() != GraphicsOGL.GL_FLOAT) {
            return null;
        }
        boolean emulateMatrixPalette = ogl.usesMatrixPalette();
        int vertexCount = indexSource == null ? primitiveCount : resolveVertexCount(primitiveCount, indexSource);
        if (vertexCount <= 0) {
            return null;
        }
        int vertexFloatCount = vertexCount * 4;
        float[] vertexValues = ensureFloatArray(vertexScratch, vertexFloatCount);
        vertexScratch = vertexValues;
        int normalFloatCount = vertexCount * 3;
        float[] normalValues = (ogl.lightingEnabled() || ogl.normalArrayEnabled)
                ? ensureFloatArray(normalScratch, normalFloatCount) : null;
        if (normalValues != null) {
            normalScratch = normalValues;
        }
        int texCoordFloatCount = vertexCount * 2;
        float[] texCoordValues = (ogl.texCoordArrayEnabled && ogl.texCoordPointer != null)
                ? ensureFloatArray(texCoordScratch, texCoordFloatCount) : null;
        if (texCoordValues != null) {
            texCoordScratch = texCoordValues;
        }
        int colorByteCount = vertexCount * 4;
        byte[] colorValues = (ogl.colorArrayEnabled && ogl.colorPointer != null)
                ? ensureByteArray(colorScratch, colorByteCount) : null;
        if (colorValues != null) {
            colorScratch = colorValues;
        }
        ClipVector clip = emulateMatrixPalette ? new ClipVector() : null;
        ClipVector eye = emulateMatrixPalette ? new ClipVector() : null;
        ClipVector normal = (emulateMatrixPalette && normalValues != null) ? new ClipVector() : null;
        int sourceBase = indexSource == null ? first : 0;
        for (int i = 0; i < vertexCount; i++) {
            int sourceVertexIndex = sourceBase + i;
            populateHardwareVertex(vertexValues, i, sourceVertexIndex, emulateMatrixPalette, clip, eye);
            if (ogl.lastError != GraphicsOGL.GL_NO_ERROR) {
                return null;
            }
            if (texCoordValues != null) {
                populateHardwareTexCoord(texCoordValues, i, sourceVertexIndex);
                if (ogl.lastError != GraphicsOGL.GL_NO_ERROR) {
                    return null;
                }
            }
            if (colorValues != null) {
                populateHardwareColor(colorValues, i, sourceVertexIndex);
                if (ogl.lastError != GraphicsOGL.GL_NO_ERROR) {
                    return null;
                }
            }
            if (normalValues != null) {
                populateHardwareNormal(normalValues, i, sourceVertexIndex, emulateMatrixPalette, normal);
                if (ogl.lastError != GraphicsOGL.GL_NO_ERROR) {
                    return null;
                }
            }
        }
        short[] indexValues = null;
        if (indexSource != null) {
            indexValues = ensureShortArray(indexScratch, primitiveCount);
            indexScratch = indexValues;
            for (int i = 0; i < primitiveCount; i++) {
                indexValues[i] = (short) indexSource.indexAt(i);
            }
        }
        return new PreparedArrayState(
                toReusableFloatBuffer(vertexValues, vertexFloatCount, BufferType.VERTEX),
                normalValues == null ? null : toReusableFloatBuffer(normalValues, normalFloatCount, BufferType.NORMAL),
                texCoordValues == null ? null : toReusableFloatBuffer(texCoordValues, texCoordFloatCount, BufferType.TEX_COORD),
                colorValues == null ? null : toReusableByteBuffer(colorValues, colorByteCount, BufferType.COLOR),
                indexValues == null ? null : toReusableShortBuffer(indexValues, primitiveCount),
                0,
                primitiveCount,
                emulateMatrixPalette
        );
    }

    private void populateHardwareVertex(float[] destination, int vertexSlot, int sourceVertexIndex,
                                        boolean emulateMatrixPalette, ClipVector clip, ClipVector eye) {
        int positionSize = Math.max(1, ogl.vertexPointer.size());
        float x = readFloatComponent(ogl.vertexPointer, sourceVertexIndex, 0);
        float y = positionSize > 1 ? readFloatComponent(ogl.vertexPointer, sourceVertexIndex, 1) : 0f;
        float z = positionSize > 2 ? readFloatComponent(ogl.vertexPointer, sourceVertexIndex, 2) : 0f;
        float w = positionSize > 3 ? readFloatComponent(ogl.vertexPointer, sourceVertexIndex, 3) : 1f;
        int destinationOffset = vertexSlot * 4;
        if (emulateMatrixPalette) {
            transformVertex(clip, eye, x, y, z, sourceVertexIndex, false);
            destination[destinationOffset] = eye.x;
            destination[destinationOffset + 1] = eye.y;
            destination[destinationOffset + 2] = eye.z;
            destination[destinationOffset + 3] = eye.w;
            return;
        }
        destination[destinationOffset] = x;
        destination[destinationOffset + 1] = y;
        destination[destinationOffset + 2] = z;
        destination[destinationOffset + 3] = w;
    }

    private void populateHardwareTexCoord(float[] destination, int vertexSlot, int sourceVertexIndex) {
        OglPointer texCoordPointer = ogl.texCoordPointer;
        if (texCoordPointer == null || texCoordPointer.type() != GraphicsOGL.GL_FLOAT) {
            return;
        }
        int size = Math.max(1, texCoordPointer.size());
        int destinationOffset = vertexSlot * 2;
        destination[destinationOffset] = readFloatComponent(texCoordPointer, sourceVertexIndex, 0);
        destination[destinationOffset + 1] = size > 1 ? readFloatComponent(texCoordPointer, sourceVertexIndex, 1) : 0f;
    }

    private void populateHardwareColor(byte[] destination, int vertexSlot, int sourceVertexIndex) {
        OglPointer colorPointer = ogl.colorPointer;
        if (colorPointer == null || colorPointer.type() != GraphicsOGL.GL_UNSIGNED_BYTE) {
            return;
        }
        int size = Math.max(1, colorPointer.size());
        int destinationOffset = vertexSlot * 4;
        int red = readUnsignedByteComponent(colorPointer, sourceVertexIndex, 0);
        int green = size > 1 ? readUnsignedByteComponent(colorPointer, sourceVertexIndex, 1) : red;
        int blue = size > 2 ? readUnsignedByteComponent(colorPointer, sourceVertexIndex, 2) : red;
        int alpha = size > 3 ? readUnsignedByteComponent(colorPointer, sourceVertexIndex, 3) : 255;
        destination[destinationOffset] = (byte) red;
        destination[destinationOffset + 1] = (byte) green;
        destination[destinationOffset + 2] = (byte) blue;
        destination[destinationOffset + 3] = (byte) alpha;
    }

    private void populateHardwareNormal(float[] destination, int vertexSlot, int sourceVertexIndex,
                                        boolean emulateMatrixPalette, ClipVector normal) {
        int destinationOffset = vertexSlot * 3;
        if (emulateMatrixPalette) {
            float nx = ogl.normalArrayEnabled && ogl.normalPointer != null
                    ? readNormalComponent(ogl.normalPointer, sourceVertexIndex, 0)
                    : ogl.currentNormal[0];
            float ny = ogl.normalArrayEnabled && ogl.normalPointer != null
                    ? readNormalComponent(ogl.normalPointer, sourceVertexIndex, 1)
                    : ogl.currentNormal[1];
            float nz = ogl.normalArrayEnabled && ogl.normalPointer != null
                    ? readNormalComponent(ogl.normalPointer, sourceVertexIndex, 2)
                    : ogl.currentNormal[2];
            if (ogl.lastError != GraphicsOGL.GL_NO_ERROR) {
                return;
            }
            transformNormal(normal, nx, ny, nz, sourceVertexIndex);
            destination[destinationOffset] = normal.x;
            destination[destinationOffset + 1] = normal.y;
            destination[destinationOffset + 2] = normal.z;
            return;
        }
        destination[destinationOffset] = ogl.normalArrayEnabled && ogl.normalPointer != null
                ? readNormalComponent(ogl.normalPointer, sourceVertexIndex, 0)
                : ogl.currentNormal[0];
        destination[destinationOffset + 1] = ogl.normalArrayEnabled && ogl.normalPointer != null
                ? readNormalComponent(ogl.normalPointer, sourceVertexIndex, 1)
                : ogl.currentNormal[1];
        destination[destinationOffset + 2] = ogl.normalArrayEnabled && ogl.normalPointer != null
                ? readNormalComponent(ogl.normalPointer, sourceVertexIndex, 2)
                : ogl.currentNormal[2];
    }

    private int resolveVertexCount(int primitiveCount, OglIndexSource indexSource) {
        int maxIndex = -1;
        for (int i = 0; i < primitiveCount; i++) {
            maxIndex = Math.max(maxIndex, indexSource.indexAt(i));
        }
        return maxIndex + 1;
    }

    private int toHardwarePrimitiveMode(int mode) {
        return switch (mode) {
            case GraphicsOGL.GL_TRIANGLES -> GraphicsOGL.GL_TRIANGLES;
            case GraphicsOGL.GL_TRIANGLE_STRIP -> GraphicsOGL.GL_TRIANGLE_STRIP;
            case GraphicsOGL.GL_LINE_LOOP -> GraphicsOGL.GL_LINE_LOOP;
            default -> -1;
        };
    }

    private void prepareFramebuffer(GL2 gl) {
        if (!surfaceDirty) {
            return;
        }
        syncSoftwareSurface(gl);
        surfaceDirty = false;
    }

    private void syncSoftwareSurface(GL2 gl) {
        DesktopSurface surface = host.surface();
        int width = surface.width();
        int height = surface.height();
        java.nio.ByteBuffer surfacePixels = toBgraByteBuffer(surface.image(), true, surfaceUploadBuffer);
        surfaceUploadBuffer = surfacePixels;
        if (surfaceTextureId == 0) {
            int[] textureIds = new int[1];
            gl.glGenTextures(1, textureIds, 0);
            surfaceTextureId = textureIds[0];
        }
        gl.glBindTexture(GraphicsOGL.GL_TEXTURE_2D, surfaceTextureId);
        gl.glTexParameteri(GraphicsOGL.GL_TEXTURE_2D, GraphicsOGL.GL_TEXTURE_MIN_FILTER, GraphicsOGL.GL_NEAREST);
        gl.glTexParameteri(GraphicsOGL.GL_TEXTURE_2D, GraphicsOGL.GL_TEXTURE_MAG_FILTER, GraphicsOGL.GL_NEAREST);
        gl.glTexParameteri(GraphicsOGL.GL_TEXTURE_2D, GraphicsOGL.GL_TEXTURE_WRAP_S, GraphicsOGL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GraphicsOGL.GL_TEXTURE_2D, GraphicsOGL.GL_TEXTURE_WRAP_T, GraphicsOGL.GL_CLAMP_TO_EDGE);
        gl.glPixelStorei(GraphicsOGL.GL_UNPACK_ALIGNMENT, 1);
        if (surfaceTextureWidth != width || surfaceTextureHeight != height) {
            gl.glTexImage2D(GraphicsOGL.GL_TEXTURE_2D, 0, GraphicsOGL.GL_RGBA, width, height, 0,
                    GL2.GL_BGRA, GraphicsOGL.GL_UNSIGNED_BYTE, surfacePixels);
            surfaceTextureWidth = width;
            surfaceTextureHeight = height;
        } else {
            gl.glTexSubImage2D(GraphicsOGL.GL_TEXTURE_2D, 0, 0, 0, width, height,
                    GL2.GL_BGRA, GraphicsOGL.GL_UNSIGNED_BYTE, surfacePixels);
        }
        gl.glDisable(GraphicsOGL.GL_SCISSOR_TEST);
        gl.glViewport(0, 0, width, height);
        gl.glMatrixMode(GraphicsOGL.GL_PROJECTION);
        gl.glLoadIdentity();
        gl.glMatrixMode(GraphicsOGL.GL_MODELVIEW);
        gl.glLoadIdentity();
        gl.glMatrixMode(GraphicsOGL.GL_TEXTURE);
        gl.glLoadIdentity();
        gl.glDisable(GraphicsOGL.GL_BLEND);
        gl.glDisable(GraphicsOGL.GL_ALPHA_TEST);
        gl.glDisable(GraphicsOGL.GL_LIGHTING);
        gl.glDisable(GraphicsOGL.GL_CULL_FACE);
        gl.glDisable(GraphicsOGL.GL_DEPTH_TEST);
        gl.glDepthMask(false);
        gl.glEnable(GraphicsOGL.GL_TEXTURE_2D);
        gl.glTexEnvi(GraphicsOGL.GL_TEXTURE_ENV, GraphicsOGL.GL_TEXTURE_ENV_MODE, GraphicsOGL.GL_REPLACE);
        gl.glColor4f(1f, 1f, 1f, 1f);
        gl.glBegin(GraphicsOGL.GL_TRIANGLE_STRIP);
        gl.glTexCoord2f(0f, 0f);
        gl.glVertex2f(-1f, -1f);
        gl.glTexCoord2f(1f, 0f);
        gl.glVertex2f(1f, -1f);
        gl.glTexCoord2f(0f, 1f);
        gl.glVertex2f(-1f, 1f);
        gl.glTexCoord2f(1f, 1f);
        gl.glVertex2f(1f, 1f);
        gl.glEnd();
    }

    private void applyClipState(GL2 gl) {
        Rectangle clip = host.delegate().getClipBounds();
        if (clip == null) {
            gl.glDisable(GraphicsOGL.GL_SCISSOR_TEST);
            return;
        }
        Rectangle clipped = clip.intersection(new Rectangle(0, 0, host.surface().width(), host.surface().height()));
        if (clipped.isEmpty()) {
            gl.glEnable(GraphicsOGL.GL_SCISSOR_TEST);
            gl.glScissor(0, 0, 0, 0);
            return;
        }
        gl.glEnable(GraphicsOGL.GL_SCISSOR_TEST);
        gl.glScissor(clipped.x, host.surface().height() - (clipped.y + clipped.height), clipped.width, clipped.height);
    }

    private void applyViewportState(GL2 gl) {
        gl.glViewport(0, 0, Math.max(1, host.surface().width()), Math.max(1, host.surface().height()));
    }

    private void applyRenderState(GL2 gl, boolean emulateMatrixPalette) {
        float[] modelViewMatrix = emulateMatrixPalette ? OglState.identityMatrix() : currentHardwareModelViewMatrix();
        float[] projectionMatrix = hardwareProjectionMatrix(
                emulateMatrixPalette ? ogl.projectionMatrix : currentHardwareProjectionMatrix());

        gl.glMatrixMode(GraphicsOGL.GL_PROJECTION);
        gl.glLoadMatrixf(projectionMatrix, 0);
        gl.glMatrixMode(GraphicsOGL.GL_MODELVIEW);
        gl.glLoadIdentity();
        if (ogl.depthEnabled()) {
            gl.glEnable(GraphicsOGL.GL_DEPTH_TEST);
            gl.glDepthFunc(toHardwareDepthFunc(ogl.depthFunc));
        } else {
            gl.glDisable(GraphicsOGL.GL_DEPTH_TEST);
        }
        gl.glDepthMask(ogl.depthMask);
        gl.glDepthRange(1.0 - ogl.depthRangeNear, 1.0 - ogl.depthRangeFar);
        if (ogl.blendEnabled()) {
            gl.glEnable(GraphicsOGL.GL_BLEND);
            gl.glBlendFunc(ogl.blendSrcFactor, ogl.blendDstFactor);
        } else {
            gl.glDisable(GraphicsOGL.GL_BLEND);
        }
        if (ogl.alphaTestEnabled) {
            gl.glEnable(GraphicsOGL.GL_ALPHA_TEST);
            gl.glAlphaFunc(ogl.alphaFunc, ogl.alphaRef);
        } else {
            gl.glDisable(GraphicsOGL.GL_ALPHA_TEST);
        }
        if (ogl.cullFaceEnabled) {
            gl.glEnable(GraphicsOGL.GL_CULL_FACE);
            gl.glFrontFace(ogl.frontFace);
            gl.glCullFace(ogl.cullFace);
        } else {
            gl.glDisable(GraphicsOGL.GL_CULL_FACE);
        }
        if (ogl.normalizeEnabled) {
            gl.glEnable(GraphicsOGL.GL_NORMALIZE);
        } else {
            gl.glDisable(GraphicsOGL.GL_NORMALIZE);
        }
        if (ogl.rescaleNormalEnabled) {
            gl.glEnable(GraphicsOGL.GL_RESCALE_NORMAL);
        } else {
            gl.glDisable(GraphicsOGL.GL_RESCALE_NORMAL);
        }
        gl.glShadeModel(ogl.shadeModel);
        float[] currentColor = unpackColor(ogl.color);
        gl.glColor4f(currentColor[0], currentColor[1], currentColor[2], currentColor[3]);
        gl.glNormal3f(ogl.currentNormal[0], ogl.currentNormal[1], ogl.currentNormal[2]);
        if (ogl.colorMaterialEnabled) {
            gl.glEnable(GraphicsOGL.GL_COLOR_MATERIAL);
            gl.glColorMaterial(GraphicsOGL.GL_FRONT_AND_BACK, GraphicsOGL.GL_AMBIENT_AND_DIFFUSE);
        } else {
            gl.glDisable(GraphicsOGL.GL_COLOR_MATERIAL);
        }
        gl.glMaterialfv(GraphicsOGL.GL_FRONT_AND_BACK, GraphicsOGL.GL_AMBIENT, ogl.materialAmbient, 0);
        gl.glMaterialfv(GraphicsOGL.GL_FRONT_AND_BACK, GraphicsOGL.GL_DIFFUSE, ogl.materialDiffuse, 0);
        gl.glMaterialfv(GraphicsOGL.GL_FRONT_AND_BACK, GraphicsOGL.GL_SPECULAR, ogl.materialSpecular, 0);
        gl.glMaterialfv(GraphicsOGL.GL_FRONT_AND_BACK, GraphicsOGL.GL_EMISSION, ogl.materialEmission, 0);
        gl.glMaterialf(GraphicsOGL.GL_FRONT_AND_BACK, GraphicsOGL.GL_SHININESS, ogl.materialShininess);
        gl.glLightModelfv(GraphicsOGL.GL_LIGHT_MODEL_AMBIENT, ogl.lightModelAmbient, 0);
        gl.glLightModeli(GraphicsOGL.GL_LIGHT_MODEL_TWO_SIDE, ogl.lightModelTwoSide ? GL.GL_TRUE : GL.GL_FALSE);
        for (int i = 0; i < ogl.lights.length; i++) {
            int lightEnum = GraphicsOGL.GL_LIGHT0 + i;
            OglLight light = ogl.lights[i];
            if (ogl.lightEnabled[i]) {
                gl.glEnable(lightEnum);
            } else {
                gl.glDisable(lightEnum);
            }
            gl.glLightfv(lightEnum, GraphicsOGL.GL_AMBIENT, light.ambient, 0);
            gl.glLightfv(lightEnum, GraphicsOGL.GL_DIFFUSE, light.diffuse, 0);
            gl.glLightfv(lightEnum, GraphicsOGL.GL_SPECULAR, light.specular, 0);
            gl.glLightfv(lightEnum, GraphicsOGL.GL_POSITION, light.position, 0);
            gl.glLightfv(lightEnum, GraphicsOGL.GL_SPOT_DIRECTION, light.spotDirection, 0);
            gl.glLightf(lightEnum, GraphicsOGL.GL_SPOT_EXPONENT, light.spotExponent);
            gl.glLightf(lightEnum, GraphicsOGL.GL_SPOT_CUTOFF, light.spotCutoff);
            gl.glLightf(lightEnum, GraphicsOGL.GL_CONSTANT_ATTENUATION, light.constantAttenuation);
            gl.glLightf(lightEnum, GraphicsOGL.GL_LINEAR_ATTENUATION, light.linearAttenuation);
            gl.glLightf(lightEnum, GraphicsOGL.GL_QUADRATIC_ATTENUATION, light.quadraticAttenuation);
        }
        if (ogl.lightingEnabled()) {
            gl.glEnable(GraphicsOGL.GL_LIGHTING);
        } else {
            gl.glDisable(GraphicsOGL.GL_LIGHTING);
        }
        gl.glMatrixMode(GraphicsOGL.GL_MODELVIEW);
        gl.glLoadMatrixf(modelViewMatrix, 0);
    }

    private float[] hardwareProjectionMatrix(float[] baseProjectionMatrix) {
        int surfaceWidth = Math.max(1, host.surface().width());
        int surfaceHeight = Math.max(1, host.surface().height());
        int viewportWidth = Math.max(1, ogl.viewportWidth);
        int viewportHeight = Math.max(1, ogl.viewportHeight);
        if (ogl.viewportX == 0 && ogl.viewportY == 0
                && viewportWidth == surfaceWidth && viewportHeight == surfaceHeight) {
            return baseProjectionMatrix;
        }
        float scaleX = viewportWidth / (float) surfaceWidth;
        float scaleY = viewportHeight / (float) surfaceHeight;
        float translateX = ((2f * ogl.viewportX) + viewportWidth) / (float) surfaceWidth - 1f;
        float translateY = ((2f * ogl.viewportY) + viewportHeight) / (float) surfaceHeight - 1f;
        setIdentityMatrix(viewportTransformScratch);
        viewportTransformScratch[0] = scaleX;
        viewportTransformScratch[5] = scaleY;
        viewportTransformScratch[12] = translateX;
        viewportTransformScratch[13] = translateY;
        multiplyColumnMajor(viewportProjectionScratch, viewportTransformScratch, baseProjectionMatrix);
        return viewportProjectionScratch;
    }

    private float[] currentHardwareModelViewMatrix() {
        if (ogl.usesExtensionMatrices()) {
            float[] matrix = extensionWorldMatrix();
            return matrix == null ? OglState.identityMatrix() : matrix;
        }
        return ogl.modelViewMatrix;
    }

    private float[] currentHardwareProjectionMatrix() {
        if (ogl.usesExtensionMatrices()) {
            float[] matrix = extensionCameraMatrix();
            return matrix == null ? OglState.identityMatrix() : matrix;
        }
        return ogl.projectionMatrix;
    }

    private void applyTextureState(GL2 gl) {
        if (!ogl.textureEnabled()) {
            gl.glDisable(GraphicsOGL.GL_TEXTURE_2D);
            return;
        }
        OglTexture texture = ogl.boundTexture();
        if (texture == null) {
            gl.glDisable(GraphicsOGL.GL_TEXTURE_2D);
            return;
        }
        gl.glEnable(GraphicsOGL.GL_TEXTURE_2D);
        HardwareTexture hardwareTexture = textureCache.computeIfAbsent(ogl.boundTextureId,
                ignored -> new HardwareTexture(0, -1, -1, -1, -1));
        int textureId = hardwareTexture.textureId();
        if (textureId == 0) {
            int[] textureIds = new int[1];
            gl.glGenTextures(1, textureIds, 0);
            textureId = textureIds[0];
            hardwareTexture = new HardwareTexture(textureId, -1, -1, -1, -1);
            textureCache.put(ogl.boundTextureId, hardwareTexture);
        }
        gl.glBindTexture(GraphicsOGL.GL_TEXTURE_2D, textureId);
        gl.glPixelStorei(GraphicsOGL.GL_UNPACK_ALIGNMENT, 1);
        if (hardwareTexture.uploadedRevision() != texture.uploadRevision()) {
            TextureUpload textureUpload = prepareTextureUpload(texture, false);
            if (hardwareTexture.matches(texture.uploadRevision(), texture.width, texture.height, textureUpload.format())) {
                gl.glTexSubImage2D(GraphicsOGL.GL_TEXTURE_2D, 0, 0, 0, texture.width, texture.height,
                        textureUpload.format(), GraphicsOGL.GL_UNSIGNED_BYTE, textureUpload.buffer());
            } else {
                gl.glTexImage2D(GraphicsOGL.GL_TEXTURE_2D, 0, textureUpload.format(), texture.width, texture.height, 0,
                        textureUpload.format(), GraphicsOGL.GL_UNSIGNED_BYTE, textureUpload.buffer());
            }
            hardwareTexture = new HardwareTexture(textureId, texture.uploadRevision(),
                    texture.width, texture.height, textureUpload.format());
            textureCache.put(ogl.boundTextureId, hardwareTexture);
        }
        gl.glTexParameteri(GraphicsOGL.GL_TEXTURE_2D, GraphicsOGL.GL_TEXTURE_MIN_FILTER, sanitizeHardwareMinFilter(texture.minFilter));
        gl.glTexParameteri(GraphicsOGL.GL_TEXTURE_2D, GraphicsOGL.GL_TEXTURE_MAG_FILTER, sanitizeHardwareMagFilter(texture.magFilter));
        gl.glTexParameteri(GraphicsOGL.GL_TEXTURE_2D, GraphicsOGL.GL_TEXTURE_WRAP_S, texture.wrapS);
        gl.glTexParameteri(GraphicsOGL.GL_TEXTURE_2D, GraphicsOGL.GL_TEXTURE_WRAP_T, texture.wrapT);
        gl.glMatrixMode(GraphicsOGL.GL_TEXTURE);
        gl.glLoadMatrixf(ogl.textureMatrix, 0);
        gl.glTexEnvi(GraphicsOGL.GL_TEXTURE_ENV, GraphicsOGL.GL_TEXTURE_ENV_MODE, ogl.textureEnvMode);
        gl.glTexEnvi(GraphicsOGL.GL_TEXTURE_ENV, GraphicsOGL.GL_COMBINE_RGB, ogl.combineRgb);
        gl.glTexEnvi(GraphicsOGL.GL_TEXTURE_ENV, GraphicsOGL.GL_COMBINE_ALPHA, ogl.combineAlpha);
        gl.glTexEnvi(GraphicsOGL.GL_TEXTURE_ENV, GraphicsOGL.GL_SRC0_RGB, ogl.srcRgb[0]);
        gl.glTexEnvi(GraphicsOGL.GL_TEXTURE_ENV, GraphicsOGL.GL_SRC1_RGB, ogl.srcRgb[1]);
        gl.glTexEnvi(GraphicsOGL.GL_TEXTURE_ENV, GraphicsOGL.GL_SRC2_RGB, ogl.srcRgb[2]);
        gl.glTexEnvi(GraphicsOGL.GL_TEXTURE_ENV, GraphicsOGL.GL_SRC0_ALPHA, ogl.srcAlpha[0]);
        gl.glTexEnvi(GraphicsOGL.GL_TEXTURE_ENV, GraphicsOGL.GL_SRC1_ALPHA, ogl.srcAlpha[1]);
        gl.glTexEnvi(GraphicsOGL.GL_TEXTURE_ENV, GraphicsOGL.GL_SRC2_ALPHA, ogl.srcAlpha[2]);
        gl.glTexEnvi(GraphicsOGL.GL_TEXTURE_ENV, GraphicsOGL.GL_OPERAND0_RGB, ogl.operandRgb[0]);
        gl.glTexEnvi(GraphicsOGL.GL_TEXTURE_ENV, GraphicsOGL.GL_OPERAND1_RGB, ogl.operandRgb[1]);
        gl.glTexEnvi(GraphicsOGL.GL_TEXTURE_ENV, GraphicsOGL.GL_OPERAND2_RGB, ogl.operandRgb[2]);
        gl.glTexEnvi(GraphicsOGL.GL_TEXTURE_ENV, GraphicsOGL.GL_OPERAND0_ALPHA, ogl.operandAlpha[0]);
        gl.glTexEnvi(GraphicsOGL.GL_TEXTURE_ENV, GraphicsOGL.GL_OPERAND1_ALPHA, ogl.operandAlpha[1]);
        gl.glTexEnvi(GraphicsOGL.GL_TEXTURE_ENV, GraphicsOGL.GL_OPERAND2_ALPHA, ogl.operandAlpha[2]);
        gl.glTexEnvfv(GraphicsOGL.GL_TEXTURE_ENV, GraphicsOGL.GL_TEXTURE_ENV_COLOR, unpackColor(ogl.textureEnvColor), 0);
        gl.glTexEnvi(GraphicsOGL.GL_TEXTURE_ENV, GraphicsOGL.GL_RGB_SCALE, ogl.rgbScale);
        gl.glTexEnvi(GraphicsOGL.GL_TEXTURE_ENV, GraphicsOGL.GL_ALPHA_SCALE, ogl.alphaScale);
        gl.glMatrixMode(GraphicsOGL.GL_MODELVIEW);
    }

    private void bindArrayState(GL2 gl, PreparedArrayState arrayState) {
        gl.glClientActiveTexture(GraphicsOGL.GL_TEXTURE0);
        gl.glEnableClientState(GraphicsOGL.GL_VERTEX_ARRAY);
        gl.glVertexPointer(4, GraphicsOGL.GL_FLOAT, 0, arrayState.vertexBuffer());
        if (arrayState.normalBuffer() != null && ogl.lightingEnabled()) {
            gl.glEnableClientState(GraphicsOGL.GL_NORMAL_ARRAY);
            gl.glNormalPointer(GraphicsOGL.GL_FLOAT, 0, arrayState.normalBuffer());
        } else {
            gl.glDisableClientState(GraphicsOGL.GL_NORMAL_ARRAY);
        }
        if (arrayState.colorBuffer() != null) {
            gl.glEnableClientState(GraphicsOGL.GL_COLOR_ARRAY);
            gl.glColorPointer(4, GraphicsOGL.GL_UNSIGNED_BYTE, 0, arrayState.colorBuffer());
        } else {
            gl.glDisableClientState(GraphicsOGL.GL_COLOR_ARRAY);
        }
        if (arrayState.texCoordBuffer() != null && ogl.textureEnabled()) {
            gl.glEnableClientState(GraphicsOGL.GL_TEXTURE_COORD_ARRAY);
            gl.glTexCoordPointer(2, GraphicsOGL.GL_FLOAT, 0, arrayState.texCoordBuffer());
        } else {
            gl.glDisableClientState(GraphicsOGL.GL_TEXTURE_COORD_ARRAY);
        }
    }

    private void unbindArrayState(GL2 gl) {
        gl.glDisableClientState(GraphicsOGL.GL_VERTEX_ARRAY);
        gl.glDisableClientState(GraphicsOGL.GL_NORMAL_ARRAY);
        gl.glDisableClientState(GraphicsOGL.GL_COLOR_ARRAY);
        gl.glDisableClientState(GraphicsOGL.GL_TEXTURE_COORD_ARRAY);
    }

    private void readBackColorBuffer(GL2 gl) {
        DesktopSurface surface = host.surface();
        int width = surface.width();
        int height = surface.height();
        int byteCount = Math.max(1, width * height * 4);
        if (readbackBuffer == null || readbackBuffer.capacity() < byteCount) {
            readbackBuffer = java.nio.ByteBuffer.allocateDirect(byteCount).order(ByteOrder.nativeOrder());
        }
        readbackBuffer.clear();
        gl.glReadPixels(0, 0, width, height, GL2.GL_BGRA, GraphicsOGL.GL_UNSIGNED_BYTE, readbackBuffer);
        int[] destination = ((DataBufferInt) surface.image().getRaster().getDataBuffer()).getData();
        java.nio.IntBuffer sourcePixels = readbackBuffer.asIntBuffer();
        for (int y = 0; y < height; y++) {
            int destinationRow = y * width;
            int sourceRow = (height - 1 - y) * width;
            sourcePixels.position(sourceRow);
            sourcePixels.get(destination, destinationRow, width);
        }
    }

    private int reapplyOutsideLockOverlay(int[] previousHardwareSnapshot, int[] overlaySnapshot) {
        if (previousHardwareSnapshot == null || overlaySnapshot == null || outsideLockOverlayBounds == null
                || outsideLockOverlayBounds.isEmpty()) {
            return 0;
        }
        int[] destination = ((DataBufferInt) host.surface().image().getRaster().getDataBuffer()).getData();
        int applied = 0;
        int width = host.surface().width();
        int left = Math.max(0, outsideLockOverlayBounds.x);
        int top = Math.max(0, outsideLockOverlayBounds.y);
        int right = Math.min(width, outsideLockOverlayBounds.x + outsideLockOverlayBounds.width);
        int bottom = Math.min(host.surface().height(), outsideLockOverlayBounds.y + outsideLockOverlayBounds.height);
        for (int y = top; y < bottom; y++) {
            int row = y * width;
            for (int x = left; x < right; x++) {
                int i = row + x;
                int overlayPixel = overlaySnapshot[i];
                if (overlayPixel == previousHardwareSnapshot[i]) {
                    continue;
                }
                destination[i] = overlayPixel;
                applied++;
            }
        }
        return applied;
    }

    private int sanitizeHardwareMinFilter(int filter) {
        return switch (filter) {
            case GraphicsOGL.GL_LINEAR,
                    GraphicsOGL.GL_LINEAR_MIPMAP_NEAREST,
                    GraphicsOGL.GL_LINEAR_MIPMAP_LINEAR -> GraphicsOGL.GL_LINEAR;
            default -> GraphicsOGL.GL_NEAREST;
        };
    }

    private int sanitizeHardwareMagFilter(int filter) {
        return filter == GraphicsOGL.GL_LINEAR ? GraphicsOGL.GL_LINEAR : GraphicsOGL.GL_NEAREST;
    }

    private int toHardwareDepthFunc(int depthFunc) {
        return switch (depthFunc) {
            case GraphicsOGL.GL_LESS -> GraphicsOGL.GL_GREATER;
            case GraphicsOGL.GL_LEQUAL -> GraphicsOGL.GL_GEQUAL;
            case GraphicsOGL.GL_GREATER -> GraphicsOGL.GL_LESS;
            case GraphicsOGL.GL_GEQUAL -> GraphicsOGL.GL_LEQUAL;
            default -> depthFunc;
        };
    }

    private TextureUpload prepareTextureUpload(OglTexture texture, boolean flipVertically) {
        TextureUpload upload = toTextureUpload(texture, flipVertically, textureUploadBuffer);
        textureUploadBuffer = upload.buffer();
        return upload;
    }

    private float[] ensureFloatArray(float[] current, int length) {
        return current.length >= length ? current : new float[length];
    }

    private byte[] ensureByteArray(byte[] current, int length) {
        return current.length >= length ? current : new byte[length];
    }

    private short[] ensureShortArray(short[] current, int length) {
        return current.length >= length ? current : new short[length];
    }

    private java.nio.FloatBuffer toReusableFloatBuffer(float[] values, int count, BufferType type) {
        int byteCount = count * Float.BYTES;
        java.nio.ByteBuffer storage = ensureBuffer(type, byteCount);
        storage.clear();
        storage.limit(byteCount);
        java.nio.FloatBuffer buffer = storage.asFloatBuffer();
        buffer.clear();
        buffer.put(values, 0, count);
        buffer.flip();
        return buffer;
    }

    private java.nio.ByteBuffer toReusableByteBuffer(byte[] values, int count, BufferType type) {
        java.nio.ByteBuffer storage = ensureBuffer(type, count);
        storage.clear();
        storage.limit(count);
        storage.put(values, 0, count);
        storage.flip();
        return storage;
    }

    private java.nio.ShortBuffer toReusableShortBuffer(short[] values, int count) {
        int byteCount = count * Short.BYTES;
        java.nio.ByteBuffer storage = ensureBuffer(BufferType.INDEX, byteCount);
        storage.clear();
        storage.limit(byteCount);
        java.nio.ShortBuffer buffer = storage.asShortBuffer();
        buffer.clear();
        buffer.put(values, 0, count);
        buffer.flip();
        return buffer;
    }

    private java.nio.ByteBuffer ensureBuffer(BufferType type, int byteCount) {
        java.nio.ByteBuffer storage = switch (type) {
            case VERTEX -> vertexBufferBytes;
            case NORMAL -> normalBufferBytes;
            case TEX_COORD -> texCoordBufferBytes;
            case COLOR -> colorBufferBytes;
            case INDEX -> indexBufferBytes;
        };
        if (storage == null || storage.capacity() < byteCount) {
            storage = java.nio.ByteBuffer.allocateDirect(byteCount).order(ByteOrder.nativeOrder());
            switch (type) {
                case VERTEX -> vertexBufferBytes = storage;
                case NORMAL -> normalBufferBytes = storage;
                case TEX_COORD -> texCoordBufferBytes = storage;
                case COLOR -> colorBufferBytes = storage;
                case INDEX -> indexBufferBytes = storage;
            }
        }
        return storage;
    }
}

private record PreparedArrayState(java.nio.FloatBuffer vertexBuffer,
                                  java.nio.FloatBuffer normalBuffer,
                                  java.nio.FloatBuffer texCoordBuffer,
                                  java.nio.ByteBuffer colorBuffer,
                                  java.nio.ShortBuffer indexBuffer,
                                  int drawFirst,
                                  int drawCount,
                                  boolean emulateMatrixPalette) {
}

private enum BufferType {
    VERTEX,
    NORMAL,
    TEX_COORD,
    COLOR,
    INDEX
}

private record HardwareTexture(int textureId, int uploadedRevision, int width, int height, int format) {
    boolean matches(int revision, int width, int height, int format) {
        return uploadedRevision == revision && this.width == width && this.height == height && this.format == format;
    }
}

private record TextureUpload(int format, java.nio.ByteBuffer buffer) {
}

private static java.nio.ByteBuffer toDirectByteBuffer(byte[] values) {
    java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocateDirect(values.length).order(ByteOrder.nativeOrder());
    buffer.put(values);
    buffer.flip();
    return buffer;
}

private static java.nio.ShortBuffer toDirectShortBuffer(short[] values) {
    java.nio.ShortBuffer buffer = java.nio.ByteBuffer.allocateDirect(values.length * Short.BYTES)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer();
    buffer.put(values);
    buffer.flip();
    return buffer;
}

private static java.nio.FloatBuffer toDirectFloatBuffer(float[] values) {
    java.nio.FloatBuffer buffer = java.nio.ByteBuffer.allocateDirect(values.length * Float.BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer();
    buffer.put(values);
    buffer.flip();
    return buffer;
}

private static java.nio.ByteBuffer toBgraByteBuffer(BufferedImage image, boolean flipVertically,
                                                    java.nio.ByteBuffer reusableBuffer) {
    int width = image.getWidth();
    int height = image.getHeight();
    int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    int byteCount = width * height * 4;
    java.nio.ByteBuffer buffer = reusableBuffer != null && reusableBuffer.capacity() >= byteCount
            ? reusableBuffer
            : java.nio.ByteBuffer.allocateDirect(byteCount).order(ByteOrder.nativeOrder());
    buffer.clear();
    java.nio.IntBuffer ints = buffer.asIntBuffer();
    for (int y = 0; y < height; y++) {
        int sourceY = flipVertically ? (height - 1 - y) : y;
        int rowOffset = sourceY * width;
        ints.put(pixels, rowOffset, width);
    }
    buffer.position(byteCount);
    buffer.flip();
    return buffer;
}

private static int[] copySurfacePixels(BufferedImage image) {
    int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    return Arrays.copyOf(pixels, pixels.length);
}

private static TextureUpload toTextureUpload(OglTexture texture, boolean flipVertically,
                                             java.nio.ByteBuffer reusableBuffer) {
    int width = Math.max(1, texture.width);
    int height = Math.max(1, texture.height);
    int format = texture.baseFormat();
    int bytesPerPixel = switch (format) {
        case GraphicsOGL.GL_ALPHA, GraphicsOGL.GL_LUMINANCE -> 1;
        case GraphicsOGL.GL_LUMINANCE_ALPHA -> 2;
        case GraphicsOGL.GL_RGB -> 3;
        default -> 4;
    };
    int byteCount = width * height * bytesPerPixel;
    java.nio.ByteBuffer buffer = reusableBuffer != null && reusableBuffer.capacity() >= byteCount
            ? reusableBuffer
            : java.nio.ByteBuffer.allocateDirect(byteCount).order(ByteOrder.nativeOrder());
    buffer.clear();
    for (int y = 0; y < height; y++) {
        int sourceY = flipVertically ? (height - 1 - y) : y;
        int rowOffset = sourceY * width;
        for (int x = 0; x < width; x++) {
            int packed = texture.pixels[rowOffset + x];
            int alpha = (packed >>> 24) & 0xFF;
            int red = (packed >>> 16) & 0xFF;
            int green = (packed >>> 8) & 0xFF;
            int blue = packed & 0xFF;
            switch (format) {
                case GraphicsOGL.GL_ALPHA -> buffer.put((byte) alpha);
                case GraphicsOGL.GL_LUMINANCE -> buffer.put((byte) red);
                case GraphicsOGL.GL_LUMINANCE_ALPHA -> {
                    buffer.put((byte) red);
                    buffer.put((byte) alpha);
                }
                case GraphicsOGL.GL_RGB -> {
                    buffer.put((byte) red);
                    buffer.put((byte) green);
                    buffer.put((byte) blue);
                }
                default -> {
                    buffer.put((byte) red);
                    buffer.put((byte) green);
                    buffer.put((byte) blue);
                    buffer.put((byte) alpha);
                }
            }
        }
    }
    buffer.flip();
    return new TextureUpload(format, buffer);
}

private static void setIdentityMatrix(float[] matrix) {
    Arrays.fill(matrix, 0f);
    matrix[0] = 1f;
    matrix[5] = 1f;
    matrix[10] = 1f;
    matrix[15] = 1f;
}

private static void multiplyColumnMajor(float[] out, float[] left, float[] right) {
    for (int column = 0; column < 4; column++) {
        int columnOffset = column * 4;
        for (int row = 0; row < 4; row++) {
            out[columnOffset + row] =
                    (left[row] * right[columnOffset]) +
                    (left[4 + row] * right[columnOffset + 1]) +
                    (left[8 + row] * right[columnOffset + 2]) +
                    (left[12 + row] * right[columnOffset + 3]);
        }
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
            case GraphicsOGL.GL_UNSIGNED_BYTE,
                    GraphicsOGL.GL_BYTE -> 1;
            case GraphicsOGL.GL_SHORT,
                    GraphicsOGL.GL_UNSIGNED_SHORT -> 2;
            case GraphicsOGL.GL_FLOAT -> 4;
            default -> -1;
        };
    }
}

private static final class OglBufferObject {
    private byte[] data = new byte[0];
    private int usage = GraphicsOGL.GL_STATIC_DRAW;

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
    private int baseFormat = GraphicsOGL.GL_RGBA;
    private int minFilter = GraphicsOGL.GL_NEAREST;
    private int magFilter = GraphicsOGL.GL_NEAREST;
    private int wrapS = GraphicsOGL.GL_REPEAT;
    private int wrapT = GraphicsOGL.GL_REPEAT;
    private int uploadRevision;

    void setParameter(int pname, int value) {
        switch (pname) {
            case GraphicsOGL.GL_TEXTURE_MIN_FILTER -> minFilter = value;
            case GraphicsOGL.GL_TEXTURE_MAG_FILTER -> magFilter = value;
            case GraphicsOGL.GL_TEXTURE_WRAP_S -> wrapS = value;
            case GraphicsOGL.GL_TEXTURE_WRAP_T -> wrapT = value;
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
        uploadRevision++;
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
                    case GraphicsOGL.GL_UNSIGNED_SHORT_4_4_4_4 ->
                            decodeRgba4444(value);
                    case GraphicsOGL.GL_UNSIGNED_SHORT_5_5_5_1 ->
                            decodeRgb5a1(value);
                    case GraphicsOGL.GL_UNSIGNED_SHORT_5_6_5 ->
                            decodeRgb565(value);
                    default -> 0;
                };
                if (decoded[i] == 0 && type != GraphicsOGL.GL_UNSIGNED_SHORT_4_4_4_4
                        && type != GraphicsOGL.GL_UNSIGNED_SHORT_5_5_5_1
                        && type != GraphicsOGL.GL_UNSIGNED_SHORT_5_6_5) {
                    return false;
                }
            }
            this.pixels = decoded;
            uploadRevision++;
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
            uploadRevision++;
            return true;
        }
        return false;
    }

    int uploadRevision() {
        return uploadRevision;
    }

    int sample(float u, float v) {
        if (pixels.length == 0 || width <= 0 || height <= 0) {
            return 0xFFFFFFFF;
        }
        float sampledU = wrapCoordinate(u, wrapS);
        float sampledV = wrapCoordinate(v, wrapT);
        if (minFilter == GraphicsOGL.GL_LINEAR || magFilter == GraphicsOGL.GL_LINEAR) {
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
        if (wrapMode == GraphicsOGL.GL_CLAMP_TO_EDGE) {
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
        if (type != GraphicsOGL.GL_UNSIGNED_BYTE) {
            return -1;
        }
        return switch (format) {
            case GraphicsOGL.GL_ALPHA,
                    GraphicsOGL.GL_LUMINANCE -> 1;
            case GraphicsOGL.GL_LUMINANCE_ALPHA -> 2;
            case GraphicsOGL.GL_RGB -> 3;
            case GraphicsOGL.GL_RGBA -> 4;
            default -> -1;
        };
    }

    private static int decodeBytePixel(byte[] raw, int offset, int format, int type) {
        if (type != GraphicsOGL.GL_UNSIGNED_BYTE) {
            return 0;
        }
        return switch (format) {
            case GraphicsOGL.GL_ALPHA ->
                    ((raw[offset] & 0xFF) << 24) | 0x00FFFFFF;
            case GraphicsOGL.GL_LUMINANCE -> {
                int l = raw[offset] & 0xFF;
                yield 0xFF000000 | (l << 16) | (l << 8) | l;
            }
            case GraphicsOGL.GL_LUMINANCE_ALPHA -> {
                int l = raw[offset] & 0xFF;
                int a = raw[offset + 1] & 0xFF;
                yield (a << 24) | (l << 16) | (l << 8) | l;
            }
            case GraphicsOGL.GL_RGB -> {
                int r = raw[offset] & 0xFF;
                int g = raw[offset + 1] & 0xFF;
                int b = raw[offset + 2] & 0xFF;
                yield 0xFF000000 | (r << 16) | (g << 8) | b;
            }
            case GraphicsOGL.GL_RGBA -> {
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
            case GraphicsOGL.GL_UNSIGNED_BYTE ->
                    decodeBytePixel(raw, offset, format.baseFormat(), GraphicsOGL.GL_UNSIGNED_BYTE);
            case GraphicsOGL.GL_UNSIGNED_SHORT_5_6_5 ->
                    decodeRgb565(readPackedShort(raw, offset));
            case GraphicsOGL.GL_UNSIGNED_SHORT_4_4_4_4 ->
                    decodeRgba4444(readPackedShort(raw, offset));
            case GraphicsOGL.GL_UNSIGNED_SHORT_5_5_5_1 ->
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
            new CompressedPaletteFormatInfo(GraphicsOGL.GL_PALETTE4_RGB8_OES,
                    GraphicsOGL.GL_RGB,
                    GraphicsOGL.GL_UNSIGNED_BYTE,
                    16,
                    3),
            new CompressedPaletteFormatInfo(GraphicsOGL.GL_PALETTE4_RGBA8_OES,
                    GraphicsOGL.GL_RGBA,
                    GraphicsOGL.GL_UNSIGNED_BYTE,
                    16,
                    4),
            new CompressedPaletteFormatInfo(GraphicsOGL.GL_PALETTE4_R5_G6_B5_OES,
                    GraphicsOGL.GL_RGB,
                    GraphicsOGL.GL_UNSIGNED_SHORT_5_6_5,
                    16,
                    2),
            new CompressedPaletteFormatInfo(GraphicsOGL.GL_PALETTE4_RGBA4_OES,
                    GraphicsOGL.GL_RGBA,
                    GraphicsOGL.GL_UNSIGNED_SHORT_4_4_4_4,
                    16,
                    2),
            new CompressedPaletteFormatInfo(GraphicsOGL.GL_PALETTE4_RGB5_A1_OES,
                    GraphicsOGL.GL_RGBA,
                    GraphicsOGL.GL_UNSIGNED_SHORT_5_5_5_1,
                    16,
                    2),
            new CompressedPaletteFormatInfo(GraphicsOGL.GL_PALETTE8_RGB8_OES,
                    GraphicsOGL.GL_RGB,
                    GraphicsOGL.GL_UNSIGNED_BYTE,
                    256,
                    3),
            new CompressedPaletteFormatInfo(GraphicsOGL.GL_PALETTE8_RGBA8_OES,
                    GraphicsOGL.GL_RGBA,
                    GraphicsOGL.GL_UNSIGNED_BYTE,
                    256,
                    4),
            new CompressedPaletteFormatInfo(GraphicsOGL.GL_PALETTE8_R5_G6_B5_OES,
                    GraphicsOGL.GL_RGB,
                    GraphicsOGL.GL_UNSIGNED_SHORT_5_6_5,
                    256,
                    2),
            new CompressedPaletteFormatInfo(GraphicsOGL.GL_PALETTE8_RGBA4_OES,
                    GraphicsOGL.GL_RGBA,
                    GraphicsOGL.GL_UNSIGNED_SHORT_4_4_4_4,
                    256,
                    2),
            new CompressedPaletteFormatInfo(GraphicsOGL.GL_PALETTE8_RGB5_A1_OES,
                    GraphicsOGL.GL_RGBA,
                    GraphicsOGL.GL_UNSIGNED_SHORT_5_5_5_1,
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
    private final OglRenderer renderer;
    private final Map<Integer, OglTexture> textures;
    private final Map<Integer, OglBufferObject> buffers;
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
    private int lastError = GraphicsOGL.GL_NO_ERROR;
    private int matrixMode = GraphicsOGL.GL_MODELVIEW;
    private int textureEnvMode = GraphicsOGL.GL_MODULATE;
    private int combineRgb = GraphicsOGL.GL_MODULATE;
    private int combineAlpha = GraphicsOGL.GL_MODULATE;
    private final int[] srcRgb = {
            GraphicsOGL.GL_TEXTURE,
            GraphicsOGL.GL_PREVIOUS,
            GraphicsOGL.GL_CONSTANT
    };
    private final int[] srcAlpha = {
            GraphicsOGL.GL_TEXTURE,
            GraphicsOGL.GL_PREVIOUS,
            GraphicsOGL.GL_CONSTANT
    };
    private final int[] operandRgb = {
            GraphicsOGL.GL_SRC_COLOR,
            GraphicsOGL.GL_SRC_COLOR,
            GraphicsOGL.GL_SRC_ALPHA
    };
    private final int[] operandAlpha = {
            GraphicsOGL.GL_SRC_ALPHA,
            GraphicsOGL.GL_SRC_ALPHA,
            GraphicsOGL.GL_SRC_ALPHA
    };
    private int textureEnvColor;
    private int rgbScale = 1;
    private int alphaScale = 1;
    private int shadeModel = GraphicsOGL.GL_SMOOTH;
    private int clientActiveTexture = GraphicsOGL.GL_TEXTURE0;
    private int alphaFunc = GraphicsOGL.GL_ALWAYS;
    private float alphaRef;
    private boolean depthMask = true;
    private int depthFunc = GraphicsOGL.GL_LESS;
    private float depthRangeNear;
    private float depthRangeFar = 1f;
    private int blendSrcFactor = GraphicsOGL.GL_ONE;
    private int blendDstFactor = GraphicsOGL.GL_ZERO;
    private int frontFace = GraphicsOGL.GL_CCW;
    private int cullFace = GraphicsOGL.GL_BACK;
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
    private int currentPaletteMatrix;
    private final Deque<float[]> modelViewStack = new ArrayDeque<>();
    private final Deque<float[]> projectionStack = new ArrayDeque<>();
    private final Deque<float[]> textureStack = new ArrayDeque<>();
    private final Deque<float[]>[] paletteMatrixStacks = createPaletteMatrixStacks();
    private OglPointer vertexPointer;
    private OglPointer texCoordPointer;
    private OglPointer normalPointer;
    private OglPointer colorPointer;
    private OglPointer matrixIndexPointer;
    private OglPointer weightPointer;

    private OglState(OglRenderer renderer) {
        this.renderer = renderer;
        this.textures = SHARED_GL_OBJECT_STORE.textures;
        this.buffers = SHARED_GL_OBJECT_STORE.buffers;
        for (int i = 0; i < paletteMatrices.length; i++) {
            paletteMatrices[i] = identityMatrix();
        }
    }

    void enableCap(int cap) {
        enabledCaps.add(cap);
        switch (cap) {
            case GraphicsOGL.GL_TEXTURE_2D -> texture2DEnabled = true;
            case GraphicsOGL.GL_BLEND -> blendCapEnabled = true;
            case GraphicsOGL.GL_DEPTH_TEST -> depthTestEnabled = true;
            case GraphicsOGL.GL_LIGHTING -> lightingCapEnabled = true;
            case GraphicsOGL.GL_COLOR_MATERIAL -> colorMaterialEnabled = true;
            case GraphicsOGL.GL_MATRIX_PALETTE_OES -> matrixPaletteEnabled = true;
            case GraphicsOGL.GL_NORMALIZE -> normalizeEnabled = true;
            case GraphicsOGL.GL_RESCALE_NORMAL -> rescaleNormalEnabled = true;
            case GraphicsOGL.GL_CULL_FACE -> cullFaceEnabled = true;
            case GraphicsOGL.GL_ALPHA_TEST -> alphaTestEnabled = true;
            default -> {
                int lightIndex = cap - GraphicsOGL.GL_LIGHT0;
                if (lightIndex >= 0 && lightIndex < lightEnabled.length) {
                    lightEnabled[lightIndex] = true;
                }
            }
        }
    }

    void disableCap(int cap) {
        enabledCaps.remove(cap);
        switch (cap) {
            case GraphicsOGL.GL_TEXTURE_2D -> texture2DEnabled = false;
            case GraphicsOGL.GL_BLEND -> blendCapEnabled = false;
            case GraphicsOGL.GL_DEPTH_TEST -> depthTestEnabled = false;
            case GraphicsOGL.GL_LIGHTING -> lightingCapEnabled = false;
            case GraphicsOGL.GL_COLOR_MATERIAL -> colorMaterialEnabled = false;
            case GraphicsOGL.GL_MATRIX_PALETTE_OES -> matrixPaletteEnabled = false;
            case GraphicsOGL.GL_NORMALIZE -> normalizeEnabled = false;
            case GraphicsOGL.GL_RESCALE_NORMAL -> rescaleNormalEnabled = false;
            case GraphicsOGL.GL_CULL_FACE -> cullFaceEnabled = false;
            case GraphicsOGL.GL_ALPHA_TEST -> alphaTestEnabled = false;
            default -> {
                int lightIndex = cap - GraphicsOGL.GL_LIGHT0;
                if (lightIndex >= 0 && lightIndex < lightEnabled.length) {
                    lightEnabled[lightIndex] = false;
                }
            }
        }
    }

    void enableClientState(int array) {
        enabledClientStates.add(array);
        switch (array) {
            case GraphicsOGL.GL_TEXTURE_COORD_ARRAY -> texCoordArrayEnabled = true;
            case GraphicsOGL.GL_COLOR_ARRAY -> colorArrayEnabled = true;
            case GraphicsOGL.GL_NORMAL_ARRAY -> normalArrayEnabled = true;
            case GraphicsOGL.GL_MATRIX_INDEX_ARRAY_OES -> matrixIndexArrayEnabled = true;
            case GraphicsOGL.GL_WEIGHT_ARRAY_OES -> weightArrayEnabled = true;
            default -> {
            }
        }
    }

    void disableClientState(int array) {
        enabledClientStates.remove(array);
        switch (array) {
            case GraphicsOGL.GL_TEXTURE_COORD_ARRAY -> texCoordArrayEnabled = false;
            case GraphicsOGL.GL_COLOR_ARRAY -> colorArrayEnabled = false;
            case GraphicsOGL.GL_NORMAL_ARRAY -> normalArrayEnabled = false;
            case GraphicsOGL.GL_MATRIX_INDEX_ARRAY_OES -> matrixIndexArrayEnabled = false;
            case GraphicsOGL.GL_WEIGHT_ARRAY_OES -> weightArrayEnabled = false;
            default -> {
            }
        }
    }

    void beginDrawing() {
        standardModelViewConfigured = false;
        standardProjectionConfigured = false;
        renderer.onBeginDrawing();
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

    boolean usesExtensionMatrices() {
        return renderer.usesExtensionMatrices(standardModelViewConfigured, standardProjectionConfigured);
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
            case GraphicsOGL.GL_ARRAY_BUFFER -> boundArrayBuffer();
            case GraphicsOGL.GL_ELEMENT_ARRAY_BUFFER -> boundElementArrayBuffer();
            default -> null;
        };
    }

    int sampleBoundTexture(float u, float v) {
        OglTexture texture = boundTexture();
        return texture == null ? 0xFFFFFFFF : texture.sample(u, v);
    }

    OglLight light(int lightEnum) {
        int index = lightEnum - GraphicsOGL.GL_LIGHT0;
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
