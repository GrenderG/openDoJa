package opendoja.host.ogl;

import com.jogamp.opengl.*;
import com.nttdocomo.ui.ogl.GraphicsOGL;
import opendoja.host.DesktopSurface;
import opendoja.host.OpenDoJaLaunchArgs;
import opendoja.host.OpenDoJaLog;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

final class OglHardwareBackend {
    private final OglRenderer owner;
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
    private java.nio.ByteBuffer vertexBufferBytes;
    private java.nio.ByteBuffer normalBufferBytes;
    private java.nio.ByteBuffer texCoordBufferBytes;
    private java.nio.ByteBuffer colorBufferBytes;
    private java.nio.ByteBuffer indexBufferBytes;
    private int resolveTextureId;
    private int resolveTextureWidth = -1;
    private int resolveTextureHeight = -1;
    private int[] lastHardwareSnapshot;
    private int[] outsideLockOverlaySnapshot;
    private Rectangle outsideLockOverlayBounds;

    OglHardwareBackend(OglRenderer owner) {
        this.owner = owner;
    }

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
                float[] clearColor = owner.unpackColor(owner.oglClearColor());
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
                    gl.glDepthMask(owner.oglState().depthMask);
                }
                if ((mask & GraphicsOGL.GL_COLOR_BUFFER_BIT) != 0) {
                    readbackPending = true;
                }
            }
            return gl.glGetError() == GL.GL_NO_ERROR;
        });
    }

    boolean draw(int mode, int first, int count, OglRenderer.OglIndexSource indexSource) {
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
            int[] currentHardwareSnapshot = copySurfacePixels(owner.host().surface().image());
            reapplyOutsideLockOverlay(lastHardwareSnapshot, outsideLockOverlaySnapshot);
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
        outsideLockOverlaySnapshot = copySurfacePixels(owner.host().surface().image());
        Rectangle surfaceBounds = new Rectangle(0, 0, owner.host().surface().width(), owner.host().surface().height());
        if (bounds != null) {
            outsideLockOverlayBounds = bounds.intersection(surfaceBounds);
        } else {
            Rectangle clip = owner.host().delegate().getClipBounds();
            outsideLockOverlayBounds = clip == null ? surfaceBounds : clip.intersection(surfaceBounds);
        }
        surfaceDirty = true;
    }

    void onHostDelegateRecreated() {
        surfaceDirty = true;
    }

    void close() {
        if (drawable == null) {
            resetState();
            return;
        }
        withContext(gl -> {
            int[] textureIds = new int[textureCache.size() + (surfaceTextureId == 0 ? 0 : 1) + (resolveTextureId == 0 ? 0 : 1)];
            int offset = 0;
            for (HardwareTexture texture : textureCache.values()) {
                if (texture.textureId() != 0) {
                    textureIds[offset++] = texture.textureId();
                }
            }
            if (surfaceTextureId != 0) {
                textureIds[offset++] = surfaceTextureId;
            }
            if (resolveTextureId != 0) {
                textureIds[offset++] = resolveTextureId;
            }
            if (offset > 0) {
                gl.glDeleteTextures(offset, textureIds, 0);
            }
            return true;
        });
        drawable.destroy();
        drawable = null;
        resetState();
    }

    private void resetState() {
        textureCache.clear();
        surfaceTextureId = 0;
        surfaceTextureWidth = -1;
        surfaceTextureHeight = -1;
        readbackBuffer = null;
        surfaceUploadBuffer = null;
        textureUploadBuffer = null;
        drawableWidth = -1;
        drawableHeight = -1;
        resolveTextureId = 0;
        resolveTextureWidth = -1;
        resolveTextureHeight = -1;
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
        int width = renderWidth();
        int height = renderHeight();
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

    private PreparedArrayState prepareArrayState(int first, int primitiveCount, OglRenderer.OglIndexSource indexSource) {
        OglRenderer.OglState ogl = owner.oglState();
        OglRenderer.OglPointer vertexPointer = ogl.vertexPointer;
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
        OglRenderer.ClipVector clip = emulateMatrixPalette ? new OglRenderer.ClipVector() : null;
        OglRenderer.ClipVector eye = emulateMatrixPalette ? new OglRenderer.ClipVector() : null;
        OglRenderer.ClipVector normal = (emulateMatrixPalette && normalValues != null) ? new OglRenderer.ClipVector() : null;
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
                                        boolean emulateMatrixPalette, OglRenderer.ClipVector clip, OglRenderer.ClipVector eye) {
        OglRenderer.OglState ogl = owner.oglState();
        int positionSize = Math.max(1, ogl.vertexPointer.size());
        float x = owner.readFloatComponent(ogl.vertexPointer, sourceVertexIndex, 0);
        float y = positionSize > 1 ? owner.readFloatComponent(ogl.vertexPointer, sourceVertexIndex, 1) : 0f;
        float z = positionSize > 2 ? owner.readFloatComponent(ogl.vertexPointer, sourceVertexIndex, 2) : 0f;
        float w = positionSize > 3 ? owner.readFloatComponent(ogl.vertexPointer, sourceVertexIndex, 3) : 1f;
        int destinationOffset = vertexSlot * 4;
        if (emulateMatrixPalette) {
            owner.transformVertex(clip, eye, x, y, z, sourceVertexIndex, false);
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
        OglRenderer.OglPointer texCoordPointer = owner.oglState().texCoordPointer;
        if (texCoordPointer == null || texCoordPointer.type() != GraphicsOGL.GL_FLOAT) {
            return;
        }
        int size = Math.max(1, texCoordPointer.size());
        int destinationOffset = vertexSlot * 2;
        destination[destinationOffset] = owner.readFloatComponent(texCoordPointer, sourceVertexIndex, 0);
        destination[destinationOffset + 1] = size > 1 ? owner.readFloatComponent(texCoordPointer, sourceVertexIndex, 1) : 0f;
    }

    private void populateHardwareColor(byte[] destination, int vertexSlot, int sourceVertexIndex) {
        OglRenderer.OglPointer colorPointer = owner.oglState().colorPointer;
        if (colorPointer == null || colorPointer.type() != GraphicsOGL.GL_UNSIGNED_BYTE) {
            return;
        }
        int size = Math.max(1, colorPointer.size());
        int destinationOffset = vertexSlot * 4;
        int red = owner.readUnsignedByteComponent(colorPointer, sourceVertexIndex, 0);
        int green = size > 1 ? owner.readUnsignedByteComponent(colorPointer, sourceVertexIndex, 1) : red;
        int blue = size > 2 ? owner.readUnsignedByteComponent(colorPointer, sourceVertexIndex, 2) : red;
        int alpha = size > 3 ? owner.readUnsignedByteComponent(colorPointer, sourceVertexIndex, 3) : 255;
        destination[destinationOffset] = (byte) red;
        destination[destinationOffset + 1] = (byte) green;
        destination[destinationOffset + 2] = (byte) blue;
        destination[destinationOffset + 3] = (byte) alpha;
    }

    private void populateHardwareNormal(float[] destination, int vertexSlot, int sourceVertexIndex,
                                        boolean emulateMatrixPalette, OglRenderer.ClipVector normal) {
        OglRenderer.OglState ogl = owner.oglState();
        int destinationOffset = vertexSlot * 3;
        if (emulateMatrixPalette) {
            float nx = ogl.normalArrayEnabled && ogl.normalPointer != null
                    ? owner.readNormalComponent(ogl.normalPointer, sourceVertexIndex, 0)
                    : ogl.currentNormal[0];
            float ny = ogl.normalArrayEnabled && ogl.normalPointer != null
                    ? owner.readNormalComponent(ogl.normalPointer, sourceVertexIndex, 1)
                    : ogl.currentNormal[1];
            float nz = ogl.normalArrayEnabled && ogl.normalPointer != null
                    ? owner.readNormalComponent(ogl.normalPointer, sourceVertexIndex, 2)
                    : ogl.currentNormal[2];
            if (ogl.lastError != GraphicsOGL.GL_NO_ERROR) {
                return;
            }
            owner.transformNormal(normal, nx, ny, nz, sourceVertexIndex);
            destination[destinationOffset] = normal.x;
            destination[destinationOffset + 1] = normal.y;
            destination[destinationOffset + 2] = normal.z;
            return;
        }
        destination[destinationOffset] = ogl.normalArrayEnabled && ogl.normalPointer != null
                ? owner.readNormalComponent(ogl.normalPointer, sourceVertexIndex, 0)
                : ogl.currentNormal[0];
        destination[destinationOffset + 1] = ogl.normalArrayEnabled && ogl.normalPointer != null
                ? owner.readNormalComponent(ogl.normalPointer, sourceVertexIndex, 1)
                : ogl.currentNormal[1];
        destination[destinationOffset + 2] = ogl.normalArrayEnabled && ogl.normalPointer != null
                ? owner.readNormalComponent(ogl.normalPointer, sourceVertexIndex, 2)
                : ogl.currentNormal[2];
    }

    private int resolveVertexCount(int primitiveCount, OglRenderer.OglIndexSource indexSource) {
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
        DesktopSurface surface = owner.host().surface();
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
        gl.glViewport(0, 0, renderWidth(), renderHeight());
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
        clearOutsideLockOverlay();
    }

    private void clearOutsideLockOverlay() {
        outsideLockOverlaySnapshot = null;
        outsideLockOverlayBounds = null;
    }

    private void applyClipState(GL2 gl) {
        Rectangle clip = owner.host().delegate().getClipBounds();
        if (clip == null) {
            gl.glDisable(GraphicsOGL.GL_SCISSOR_TEST);
            return;
        }
        Rectangle clipped = clip.intersection(new Rectangle(0, 0, owner.host().surface().width(), owner.host().surface().height()));
        if (clipped.isEmpty()) {
            gl.glEnable(GraphicsOGL.GL_SCISSOR_TEST);
            gl.glScissor(0, 0, 0, 0);
            return;
        }
        int scale = supersampleScale();
        gl.glEnable(GraphicsOGL.GL_SCISSOR_TEST);
        gl.glScissor(
                clipped.x * scale,
                renderHeight() - ((clipped.y + clipped.height) * scale),
                clipped.width * scale,
                clipped.height * scale);
    }

    private void applyViewportState(GL2 gl) {
        OglRenderer.OglState ogl = owner.oglState();
        int scale = supersampleScale();
        gl.glViewport(
                ogl.viewportX * scale,
                ogl.viewportY * scale,
                Math.max(1, ogl.viewportWidth * scale),
                Math.max(1, ogl.viewportHeight * scale));
    }

    private void applyRenderState(GL2 gl, boolean emulateMatrixPalette) {
        OglRenderer.OglState ogl = owner.oglState();
        float[] modelViewMatrix = emulateMatrixPalette ? OglRenderer.OglState.identityMatrix() : currentHardwareModelViewMatrix();
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
        float[] currentColor = owner.unpackColor(ogl.color);
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
            OglRenderer.OglLight light = ogl.lights[i];
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
        return baseProjectionMatrix;
    }

    private float[] currentHardwareModelViewMatrix() {
        if (owner.oglState().usesExtensionMatrices()) {
            float[] matrix = owner.extensionWorldMatrix();
            return matrix == null ? OglRenderer.OglState.identityMatrix() : matrix;
        }
        return owner.oglState().modelViewMatrix;
    }

    private float[] currentHardwareProjectionMatrix() {
        if (owner.oglState().usesExtensionMatrices()) {
            float[] matrix = owner.extensionCameraMatrix();
            return matrix == null ? OglRenderer.OglState.identityMatrix() : matrix;
        }
        return owner.oglState().projectionMatrix;
    }

    private void applyTextureState(GL2 gl) {
        OglRenderer.OglState ogl = owner.oglState();
        if (!ogl.textureEnabled()) {
            gl.glDisable(GraphicsOGL.GL_TEXTURE_2D);
            return;
        }
        OglRenderer.OglTexture texture = ogl.boundTexture();
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
        gl.glTexEnvfv(GraphicsOGL.GL_TEXTURE_ENV, GraphicsOGL.GL_TEXTURE_ENV_COLOR, owner.unpackColor(ogl.textureEnvColor), 0);
        gl.glTexEnvi(GraphicsOGL.GL_TEXTURE_ENV, GraphicsOGL.GL_RGB_SCALE, ogl.rgbScale);
        gl.glTexEnvi(GraphicsOGL.GL_TEXTURE_ENV, GraphicsOGL.GL_ALPHA_SCALE, ogl.alphaScale);
        gl.glMatrixMode(GraphicsOGL.GL_MODELVIEW);
    }

    private void bindArrayState(GL2 gl, PreparedArrayState arrayState) {
        OglRenderer.OglState ogl = owner.oglState();
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
        DesktopSurface surface = owner.host().surface();
        int width = surface.width();
        int height = surface.height();
        int scale = supersampleScale();
        if (scale > 1) {
            resolveSupersampledReadback(gl, width, height, scale);
        }
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

    private void resolveSupersampledReadback(GL2 gl, int width, int height, int scale) {
        int renderWidth = width * scale;
        int renderHeight = height * scale;
        if (resolveTextureId == 0) {
            int[] textureIds = new int[1];
            gl.glGenTextures(1, textureIds, 0);
            resolveTextureId = textureIds[0];
        }
        gl.glBindTexture(GraphicsOGL.GL_TEXTURE_2D, resolveTextureId);
        gl.glTexParameteri(GraphicsOGL.GL_TEXTURE_2D, GraphicsOGL.GL_TEXTURE_MIN_FILTER, GraphicsOGL.GL_NEAREST);
        gl.glTexParameteri(GraphicsOGL.GL_TEXTURE_2D, GraphicsOGL.GL_TEXTURE_MAG_FILTER, GraphicsOGL.GL_NEAREST);
        gl.glTexParameteri(GraphicsOGL.GL_TEXTURE_2D, GraphicsOGL.GL_TEXTURE_WRAP_S, GraphicsOGL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GraphicsOGL.GL_TEXTURE_2D, GraphicsOGL.GL_TEXTURE_WRAP_T, GraphicsOGL.GL_CLAMP_TO_EDGE);
        if (resolveTextureWidth != renderWidth || resolveTextureHeight != renderHeight) {
            gl.glCopyTexImage2D(GraphicsOGL.GL_TEXTURE_2D, 0, GraphicsOGL.GL_RGBA, 0, 0, renderWidth, renderHeight, 0);
            resolveTextureWidth = renderWidth;
            resolveTextureHeight = renderHeight;
        } else {
            gl.glCopyTexSubImage2D(GraphicsOGL.GL_TEXTURE_2D, 0, 0, 0, 0, 0, renderWidth, renderHeight);
        }
        gl.glDisable(GraphicsOGL.GL_SCISSOR_TEST);
        gl.glViewport(0, 0, width, height);
        gl.glMatrixMode(GraphicsOGL.GL_PROJECTION);
        gl.glLoadIdentity();
        gl.glMatrixMode(GraphicsOGL.GL_MODELVIEW);
        gl.glLoadIdentity();
        gl.glDisable(GraphicsOGL.GL_ALPHA_TEST);
        gl.glDisable(GraphicsOGL.GL_LIGHTING);
        gl.glDisable(GraphicsOGL.GL_CULL_FACE);
        gl.glDisable(GraphicsOGL.GL_DEPTH_TEST);
        gl.glDepthMask(false);
        gl.glEnable(GraphicsOGL.GL_TEXTURE_2D);
        gl.glTexEnvi(GraphicsOGL.GL_TEXTURE_ENV, GraphicsOGL.GL_TEXTURE_ENV_MODE, GraphicsOGL.GL_MODULATE);
        float sampleWeight = 1f / (scale * scale);
        for (int sy = 0; sy < scale; sy++) {
            for (int sx = 0; sx < scale; sx++) {
                float translateS = ((sx + 0.5f) / renderWidth) - (0.5f / width);
                float translateT = ((sy + 0.5f) / renderHeight) - (0.5f / height);
                gl.glMatrixMode(GraphicsOGL.GL_TEXTURE);
                gl.glLoadIdentity();
                gl.glTranslatef(translateS, translateT, 0f);
                gl.glColor4f(sampleWeight, sampleWeight, sampleWeight, sampleWeight);
                if (sx == 0 && sy == 0) {
                    gl.glDisable(GraphicsOGL.GL_BLEND);
                } else {
                    gl.glEnable(GraphicsOGL.GL_BLEND);
                    gl.glBlendFunc(GraphicsOGL.GL_ONE, GraphicsOGL.GL_ONE);
                }
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
        }
        gl.glMatrixMode(GraphicsOGL.GL_TEXTURE);
        gl.glLoadIdentity();
        gl.glMatrixMode(GraphicsOGL.GL_MODELVIEW);
        gl.glDisable(GraphicsOGL.GL_BLEND);
    }

    private int reapplyOutsideLockOverlay(int[] previousHardwareSnapshot, int[] overlaySnapshot) {
        if (previousHardwareSnapshot == null || overlaySnapshot == null || outsideLockOverlayBounds == null
                || outsideLockOverlayBounds.isEmpty()) {
            return 0;
        }
        int[] destination = ((DataBufferInt) owner.host().surface().image().getRaster().getDataBuffer()).getData();
        int applied = 0;
        int width = owner.host().surface().width();
        int left = Math.max(0, outsideLockOverlayBounds.x);
        int top = Math.max(0, outsideLockOverlayBounds.y);
        int right = Math.min(width, outsideLockOverlayBounds.x + outsideLockOverlayBounds.width);
        int bottom = Math.min(owner.host().surface().height(), outsideLockOverlayBounds.y + outsideLockOverlayBounds.height);
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

    private TextureUpload prepareTextureUpload(OglRenderer.OglTexture texture, boolean flipVertically) {
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

    private static TextureUpload toTextureUpload(OglRenderer.OglTexture texture, boolean flipVertically,
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

    private int supersampleScale() {
        return OpenDoJaLaunchArgs.openGlesSupersampleScale();
    }

    private int renderWidth() {
        return Math.max(1, owner.host().surface().width() * supersampleScale());
    }

    private int renderHeight() {
        return Math.max(1, owner.host().surface().height() * supersampleScale());
    }

    private interface HardwareGlCall {
        boolean execute(GL2 gl);
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
}
