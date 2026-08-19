package opendoja.host.ogl;

import com.nttdocomo.ui.ogl.GraphicsOGL;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;

final class OglSoftwareRenderer {
    private final OglRenderer owner;
    private final DrawScratch drawScratch = new DrawScratch();

    OglSoftwareRenderer(OglRenderer owner) {
        this.owner = owner;
    }

    void draw(int mode, int first, int count, OglRenderer.OglIndexSource indexSource) {
        OglRenderer.OglState ogl = owner.oglState();
        int primitiveCount = count;
        if (indexSource != null) {
            primitiveCount = Math.min(Math.max(0, count), indexSource.elementCount());
        }
        drawScratch.beginDraw(indexSource != null, primitiveCount);
        Rectangle clip = owner.host().delegate().getClipBounds();
        BufferedImage target = owner.host().surface().image();
        int[] pixels = ((DataBufferInt) target.getRaster().getDataBuffer()).getData();
        float[] depthBuffer = owner.host().surface().depthBufferForFrame();
        OglRenderer.RasterVertex scratch0 = drawScratch.scratch0;
        OglRenderer.RasterVertex scratch1 = drawScratch.scratch1;
        OglRenderer.RasterVertex scratch2 = drawScratch.scratch2;
        OglRenderer.RasterVertex[] clipInput = drawScratch.clipInput;
        OglRenderer.RasterVertex[] clipScratch = drawScratch.clipScratch;
        OglRenderer.RasterVertex project0 = drawScratch.project0;
        OglRenderer.RasterVertex project1 = drawScratch.project1;
        OglRenderer.RasterVertex project2 = drawScratch.project2;
        OglRenderer.ClipVector clip0 = drawScratch.clip0;
        OglRenderer.ClipVector clip1 = drawScratch.clip1;
        switch (mode) {
            case GraphicsOGL.GL_TRIANGLES -> {
                for (int i = 0; i + 2 < primitiveCount; i += 3) {
                    if (!populateRasterVertex(scratch0, clip0, resolveVertexIndex(first, indexSource, i))
                            || !populateRasterVertex(scratch1, clip1, resolveVertexIndex(first, indexSource, i + 1))
                            || !populateRasterVertex(scratch2, clip0, resolveVertexIndex(first, indexSource, i + 2))) {
                        continue;
                    }
                    owner.drawRasterTriangle(scratch0, scratch1, scratch2, pixels, depthBuffer, clip,
                            target.getWidth(), target.getHeight(), clipInput, clipScratch, project0, project1, project2);
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
                OglRenderer.RasterVertex previous0 = scratch0;
                OglRenderer.RasterVertex previous1 = scratch1;
                OglRenderer.RasterVertex next = scratch2;
                OglRenderer.ClipVector nextClip = clip0;
                for (int i = 2; i < primitiveCount; i++) {
                    if (!populateRasterVertex(next, nextClip, resolveVertexIndex(first, indexSource, i))) {
                        continue;
                    }
                    OglRenderer.RasterVertex v0 = previous0;
                    OglRenderer.RasterVertex v1 = previous1;
                    OglRenderer.RasterVertex v2 = next;
                    if ((i & 1) != 0) {
                        OglRenderer.RasterVertex swap = v1;
                        v1 = v0;
                        v0 = swap;
                    }
                    owner.drawRasterTriangle(v0, v1, v2, pixels, depthBuffer, clip,
                            target.getWidth(), target.getHeight(), clipInput, clipScratch, project0, project1, project2);
                    previous0 = previous1;
                    previous1 = next;
                    next = v0;
                    nextClip = next == scratch0 ? clip0 : clip1;
                }
            }
            case GraphicsOGL.GL_LINE_LOOP ->
                    owner.drawLineLoop(first, primitiveCount, indexSource, clip, target.getWidth(), target.getHeight(),
                            scratch0, scratch1, scratch2, clip0, clip1);
            default -> ogl.lastError = GraphicsOGL.GL_INVALID_ENUM;
        }
    }

    int resolveVertexIndex(int first, OglRenderer.OglIndexSource elementIndices, int primitiveIndex) {
        if (elementIndices == null) {
            return first + primitiveIndex;
        }
        int index = elementIndices.indexAt(primitiveIndex);
        if (index < 0) {
            owner.oglState().lastError = GraphicsOGL.GL_INVALID_VALUE;
            return 0;
        }
        return index;
    }

    boolean populateRasterVertex(OglRenderer.RasterVertex targetVertex, OglRenderer.ClipVector clipVector, int vertexIndex) {
        OglRenderer.OglState ogl = owner.oglState();
        if (drawScratch.tryLoadCachedVertex(vertexIndex, targetVertex)) {
            return true;
        }
        OglRenderer.OglPointer vertexPointer = ogl.vertexPointer;
        if (vertexPointer == null) {
            return false;
        }
        int positionSize = Math.max(1, vertexPointer.size());
        float x = owner.readFloatComponent(vertexPointer, vertexIndex, 0);
        if (ogl.lastError != GraphicsOGL.GL_NO_ERROR) {
            return false;
        }
        float y = positionSize > 1 ? owner.readFloatComponent(vertexPointer, vertexIndex, 1) : 0f;
        if (ogl.lastError != GraphicsOGL.GL_NO_ERROR) {
            return false;
        }
        float z = positionSize > 2 ? owner.readFloatComponent(vertexPointer, vertexIndex, 2) : 0f;
        if (ogl.lastError != GraphicsOGL.GL_NO_ERROR) {
            return false;
        }
        float u = 0f;
        float v = 0f;
        OglRenderer.OglPointer texCoordPointer = ogl.texCoordArrayEnabled ? ogl.texCoordPointer : null;
        if (texCoordPointer != null) {
            int texSize = Math.max(1, texCoordPointer.size());
            u = owner.readFloatComponent(texCoordPointer, vertexIndex, 0);
            if (ogl.lastError != GraphicsOGL.GL_NO_ERROR) {
                return false;
            }
            if (texSize > 1) {
                v = owner.readFloatComponent(texCoordPointer, vertexIndex, 1);
                if (ogl.lastError != GraphicsOGL.GL_NO_ERROR) {
                    return false;
                }
            }
        }
        boolean useExtensionMatrices = positionSize >= 3 && ogl.usesExtensionMatrices();
        owner.transformVertex(clipVector, owner.eyeVectorTemp(), x, y, z, vertexIndex, useExtensionMatrices);
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
            primaryColor = resolveLitColor(vertexIndex, owner.eyeVectorTemp(), sourceColor, false);
            if (ogl.lastError != GraphicsOGL.GL_NO_ERROR) {
                return false;
            }
            backColor = ogl.lightModelTwoSide
                    ? resolveLitColor(vertexIndex, owner.eyeVectorTemp(), sourceColor, true)
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
                owner.viewportX(ndcX),
                owner.viewportY(ndcY),
                depth,
                reciprocalW,
                u,
                v,
                primaryColor,
                backColor
        );
        drawScratch.cacheVertex(vertexIndex, targetVertex);
        return true;
    }

    private int resolvePrimaryColor(int vertexIndex) {
        OglRenderer.OglState ogl = owner.oglState();
        if (!ogl.colorArrayEnabled || ogl.colorPointer == null) {
            return ogl.color;
        }
        OglRenderer.OglPointer colorPointer = ogl.colorPointer;
        int red = colorPointer.size() > 0 ? owner.readColorComponent(colorPointer, vertexIndex, 0) : 255;
        if (ogl.lastError != GraphicsOGL.GL_NO_ERROR) {
            return 0;
        }
        int green = colorPointer.size() > 1 ? owner.readColorComponent(colorPointer, vertexIndex, 1) : red;
        if (ogl.lastError != GraphicsOGL.GL_NO_ERROR) {
            return 0;
        }
        int blue = colorPointer.size() > 2 ? owner.readColorComponent(colorPointer, vertexIndex, 2) : red;
        if (ogl.lastError != GraphicsOGL.GL_NO_ERROR) {
            return 0;
        }
        int alpha = colorPointer.size() > 3 ? owner.readColorComponent(colorPointer, vertexIndex, 3) : 255;
        if (ogl.lastError != GraphicsOGL.GL_NO_ERROR) {
            return 0;
        }
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private int resolveLitColor(int vertexIndex, OglRenderer.ClipVector eyePosition, int sourceColor, boolean backFace) {
        OglRenderer.OglState ogl = owner.oglState();
        float sourceRed = OglRenderer.packedComponent(sourceColor, 0);
        float sourceGreen = OglRenderer.packedComponent(sourceColor, 1);
        float sourceBlue = OglRenderer.packedComponent(sourceColor, 2);
        float sourceAlpha = OglRenderer.packedComponent(sourceColor, 3);
        boolean colorMaterial = ogl.colorMaterialTracksAmbientAndDiffuse();
        float ambientRed = colorMaterial ? sourceRed : ogl.materialAmbient[0];
        float ambientGreen = colorMaterial ? sourceGreen : ogl.materialAmbient[1];
        float ambientBlue = colorMaterial ? sourceBlue : ogl.materialAmbient[2];
        float diffuseRed = colorMaterial ? sourceRed : ogl.materialDiffuse[0];
        float diffuseGreen = colorMaterial ? sourceGreen : ogl.materialDiffuse[1];
        float diffuseBlue = colorMaterial ? sourceBlue : ogl.materialDiffuse[2];
        float diffuseAlpha = colorMaterial ? sourceAlpha : ogl.materialDiffuse[3];
        owner.resolveEyeNormal(owner.normalVectorTemp(), vertexIndex, backFace);
        if (ogl.lastError != GraphicsOGL.GL_NO_ERROR) {
            return 0;
        }
        OglRenderer.ClipVector normal = owner.normalVectorTemp();
        return owner.computeLightingColor(eyePosition, normal.x, normal.y, normal.z,
                ambientRed, ambientGreen, ambientBlue,
                diffuseRed, diffuseGreen, diffuseBlue, diffuseAlpha,
                ogl.materialSpecular[0], ogl.materialSpecular[1], ogl.materialSpecular[2],
                ogl.materialEmission[0], ogl.materialEmission[1], ogl.materialEmission[2],
                ogl.materialShininess);
    }

    private static OglRenderer.RasterVertex[] createRasterVertexArray(int length) {
        OglRenderer.RasterVertex[] vertices = new OglRenderer.RasterVertex[length];
        for (int i = 0; i < vertices.length; i++) {
            vertices[i] = new OglRenderer.RasterVertex();
        }
        return vertices;
    }

    private static final class DrawScratch {
        private static final int EMPTY_VERTEX_CACHE_KEY = Integer.MIN_VALUE;

        final OglRenderer.RasterVertex scratch0 = new OglRenderer.RasterVertex();
        final OglRenderer.RasterVertex scratch1 = new OglRenderer.RasterVertex();
        final OglRenderer.RasterVertex scratch2 = new OglRenderer.RasterVertex();
        final OglRenderer.RasterVertex[] clipInput = createRasterVertexArray(12);
        final OglRenderer.RasterVertex[] clipScratch = createRasterVertexArray(12);
        final OglRenderer.RasterVertex project0 = new OglRenderer.RasterVertex();
        final OglRenderer.RasterVertex project1 = new OglRenderer.RasterVertex();
        final OglRenderer.RasterVertex project2 = new OglRenderer.RasterVertex();
        final OglRenderer.ClipVector clip0 = new OglRenderer.ClipVector();
        final OglRenderer.ClipVector clip1 = new OglRenderer.ClipVector();
        private int[] vertexCacheKeys = new int[0];
        private OglRenderer.RasterVertex[] vertexCacheValues = new OglRenderer.RasterVertex[0];
        private boolean vertexCacheEnabled;

        void beginDraw(boolean enableVertexCache, int primitiveCount) {
            vertexCacheEnabled = enableVertexCache;
            if (!enableVertexCache) {
                return;
            }
            ensureVertexCacheCapacity(Math.max(16, primitiveCount * 2));
            Arrays.fill(vertexCacheKeys, EMPTY_VERTEX_CACHE_KEY);
        }

        boolean tryLoadCachedVertex(int vertexIndex, OglRenderer.RasterVertex target) {
            if (!vertexCacheEnabled || vertexCacheKeys.length == 0) {
                return false;
            }
            int mask = vertexCacheKeys.length - 1;
            int slot = mix(vertexIndex) & mask;
            while (true) {
                int cachedIndex = vertexCacheKeys[slot];
                if (cachedIndex == EMPTY_VERTEX_CACHE_KEY) {
                    return false;
                }
                if (cachedIndex == vertexIndex) {
                    target.copyFrom(vertexCacheValues[slot]);
                    return true;
                }
                slot = (slot + 1) & mask;
            }
        }

        void cacheVertex(int vertexIndex, OglRenderer.RasterVertex source) {
            if (!vertexCacheEnabled || vertexCacheKeys.length == 0) {
                return;
            }
            int mask = vertexCacheKeys.length - 1;
            int slot = mix(vertexIndex) & mask;
            while (vertexCacheKeys[slot] != EMPTY_VERTEX_CACHE_KEY && vertexCacheKeys[slot] != vertexIndex) {
                slot = (slot + 1) & mask;
            }
            vertexCacheKeys[slot] = vertexIndex;
            vertexCacheValues[slot].copyFrom(source);
        }

        private void ensureVertexCacheCapacity(int desiredCapacity) {
            int capacity = 1;
            while (capacity < desiredCapacity) {
                capacity <<= 1;
            }
            if (capacity <= vertexCacheKeys.length) {
                return;
            }
            vertexCacheKeys = new int[capacity];
            vertexCacheValues = new OglRenderer.RasterVertex[capacity];
            for (int i = 0; i < capacity; i++) {
                vertexCacheValues[i] = new OglRenderer.RasterVertex();
            }
        }

        private static int mix(int value) {
            value ^= value >>> 16;
            value *= 0x7feb352d;
            value ^= value >>> 15;
            value *= 0x846ca68b;
            value ^= value >>> 16;
            return value;
        }
    }
}
