package opendoja.g3d;

import com.nttdocomo.opt.ui.j3d.PrimitiveArray;
import opendoja.host.OpenDoJaLog;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Software3DContext {
    private static final boolean DEBUG_3D = opendoja.host.OpenDoJaLaunchArgs.getBoolean(opendoja.host.OpenDoJaLaunchArgs.DEBUG3D);
    private static volatile boolean debugProjectionLogged;
    private static volatile boolean debugFigureStatsLogged;
    private static final float DEPTH_EPSILON = 0.000001f;
    private static final int RASTER_SUBPIXEL_SHIFT = 4;
    private static final int RASTER_SUBPIXEL_SCALE = 1 << RASTER_SUBPIXEL_SHIFT;
    private static final int CULL_SIGN = opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.CULL_SIGN);
    private static final boolean CULL_FIGURES = opendoja.host.OpenDoJaLaunchArgs.getBoolean(opendoja.host.OpenDoJaLaunchArgs.CULL_FIGURES);
    private static final boolean CLIP_SCREEN_PLANES = opendoja.host.OpenDoJaLaunchArgs.getBoolean(opendoja.host.OpenDoJaLaunchArgs.CLIP_SCREEN_PLANES);
    private static final float UI_FIGURE_VERTEX_SCALE = opendoja.host.OpenDoJaLaunchArgs.getFloat(opendoja.host.OpenDoJaLaunchArgs.UI_FIGURE_VERTEX_SCALE);

    private Rectangle uiClip;
    private float[] uiTransform = identity();
    private boolean uiPerspective;
    private float uiOrthoWidth = 400f;
    private float uiOrthoHeight = 400f;
    private PerspectiveMode uiPerspectiveMode = PerspectiveMode.FIELD_OF_VIEW;
    private float uiPerspectiveNear = 1f;
    private float uiPerspectiveFar = 32767f;
    private float uiPerspectiveAngleDegrees = 60f;
    private float uiPerspectiveWidth = 240f;
    private float uiPerspectiveHeight = 240f;
    private float uiAmbient = 1f;
    private FogState uiFog;

    private Rectangle optClip;
    private float[] optViewTransform = identity();
    private int optScreenCenterX;
    private int optScreenCenterY;
    private float optScaleX = 1f;
    private float optScaleY = 1f;
    private float optViewWidth;
    private float optViewHeight;
    private float optNear = 1f;
    private float optFar = 32767f;
    private float optPerspectiveAngle = 512f;
    private float optPerspectiveWidth = 240f;
    private float optPerspectiveHeight = 240f;
    private boolean optPerspectiveEnabled;
    private boolean optPerspectiveUsesExtent;
    private boolean optLightingEnabled;
    private boolean optSemiTransparent;
    private boolean optSphereMapEnabled;
    private SoftwareTexture optSphereTexture;
    private boolean optToonShaderEnabled;
    private ToonShaderParams optToonShader = new ToonShaderParams(128, 255, 96);
    private SoftwareTexture[] primitiveTextures = new SoftwareTexture[0];
    private int primitiveTextureIndex;
    private float[] frameDepthBuffer;
    private final List<PendingOptPrimitiveBlend> pendingOptPrimitiveBlends = new ArrayList<>();

    public void setUiClip(int x, int y, int width, int height) {
        this.uiClip = new Rectangle(x, y, width, height);
    }

    public void setUiParallelView(int width, int height) {
        this.uiPerspective = false;
        this.uiOrthoWidth = java.lang.Math.max(1f, width);
        this.uiOrthoHeight = java.lang.Math.max(1f, height);
    }

    public void setUiPerspectiveView(float a, float b, int c, int d) {
        this.uiPerspective = true;
        this.uiPerspectiveMode = PerspectiveMode.WIDTH_HEIGHT;
        this.uiPerspectiveNear = normalizeNear(a);
        this.uiPerspectiveFar = normalizeFar(uiPerspectiveNear, b);
        this.uiPerspectiveWidth = java.lang.Math.max(1f, c);
        this.uiPerspectiveHeight = java.lang.Math.max(1f, d);
    }

    public void setUiPerspectiveView(float a, float b, float c) {
        this.uiPerspective = true;
        this.uiPerspectiveMode = PerspectiveMode.FIELD_OF_VIEW;
        this.uiPerspectiveNear = normalizeNear(a);
        this.uiPerspectiveFar = normalizeFar(uiPerspectiveNear, b);
        this.uiPerspectiveAngleDegrees = normalizePerspectiveDegrees(c);
    }

    public void setUiTransform(float[] matrix) {
        this.uiTransform = matrix == null ? identity() : matrix.clone();
    }

    public void addUiLight(int mode, float intensity, int color) {
        if (mode == 128) {
            this.uiAmbient = java.lang.Math.max(0f, intensity);
        }
    }

    public void resetUiLights() {
        this.uiAmbient = 1f;
    }

    public void setUiFog(Integer mode, float linearNear, float linearFar, float density, int color) {
        this.uiFog = mode == null ? null : new FogState(mode, linearNear, linearFar, density, color);
    }

    public void setOptViewTransform(float[] matrix) {
        this.optViewTransform = matrix == null ? identity() : matrix.clone();
    }

    public void setOptScreenCenter(int x, int y) {
        this.optScreenCenterX = x;
        this.optScreenCenterY = y;
    }

    public void setOptScreenScale(int x, int y) {
        this.optPerspectiveEnabled = false;
        this.optScaleX = x == 0 ? 1f : x / 4096f;
        this.optScaleY = y == 0 ? 1f : y / 4096f;
    }

    public void setOptScreenView(int width, int height) {
        this.optPerspectiveEnabled = false;
        this.optViewWidth = java.lang.Math.max(1f, width);
        this.optViewHeight = java.lang.Math.max(1f, height);
    }

    public void setOptPerspective(int near, int far, int width) {
        // DoJa opt.ui.j3d treats the 3-int overload as a fixed-angle perspective call.
        this.optPerspectiveEnabled = true;
        this.optPerspectiveUsesExtent = false;
        this.optNear = java.lang.Math.max(1f, near);
        this.optFar = java.lang.Math.max(this.optNear + 1f, far);
        this.optPerspectiveAngle = width;
    }

    public void setOptPerspective(int near, int far, int width, int height) {
        this.optPerspectiveEnabled = true;
        this.optPerspectiveUsesExtent = true;
        this.optNear = java.lang.Math.max(1f, near);
        this.optFar = java.lang.Math.max(this.optNear + 1f, far);
        this.optPerspectiveWidth = java.lang.Math.max(1f, width);
        this.optPerspectiveHeight = java.lang.Math.max(1f, height);
    }

    public void enableOptLight(boolean enabled) {
        this.optLightingEnabled = enabled;
    }

    public void enableOptSemiTransparent(boolean enabled) {
        this.optSemiTransparent = enabled;
    }

    public void enableOptSphereMap(boolean enabled) {
        this.optSphereMapEnabled = enabled;
    }

    public void setOptSphereTexture(SoftwareTexture texture) {
        this.optSphereTexture = texture;
    }

    public void enableOptToonShader(boolean enabled) {
        this.optToonShaderEnabled = enabled;
    }

    public void setOptToonShader(int threshold, int mid, int shadow) {
        this.optToonShader = new ToonShaderParams(threshold, mid, shadow);
    }

    public void setOptClip(int x, int y, int width, int height) {
        this.optClip = new Rectangle(x, y, width, height);
    }

    public void setPrimitiveTextures(SoftwareTexture[] textures) {
        this.primitiveTextures = textures == null ? new SoftwareTexture[0] : textures.clone();
        if (primitiveTextureIndex >= this.primitiveTextures.length) {
            primitiveTextureIndex = 0;
        }
    }

    public void setPrimitiveTexture(int index) {
        this.primitiveTextureIndex = index;
    }

    public SoftwareTexture[] primitiveTexturesSnapshot() {
        return primitiveTextures.clone();
    }

    public int primitiveTextureIndex() {
        return primitiveTextureIndex;
    }

    public void setFrameDepthBuffer(float[] frameDepthBuffer) {
        this.frameDepthBuffer = frameDepthBuffer;
    }

    public void renderUiFigure(Graphics2D g, BufferedImage target, int originX, int originY, int surfaceWidth, int surfaceHeight,
                               MascotFigure figure, float[] objectTransform, int blendMode, float transparency) {
        if (figure == null || figure.model() == null) {
            return;
        }
        Projection projection = uiPerspective ? createUiProjection(surfaceWidth, surfaceHeight) : null;
        renderModel(g, target, originX, originY, surfaceWidth, surfaceHeight, figure, objectTransform == null ? uiTransform : multiply(uiTransform, objectTransform), projection, uiClip, surfaceWidth / 2f, surfaceHeight / 2f, uiOrthoWidth, uiOrthoHeight, true, false, blendMode, transparency, uiAmbient, UI_FIGURE_VERTEX_SCALE, uiFog, null, null, BlendSemantics.UI_GRAPHICS3D);
    }

    public void renderUiPrimitive(Graphics2D g, BufferedImage target, int originX, int originY, int surfaceWidth, int surfaceHeight,
                                  int primitiveType, int primitiveParam, int primitiveCount, int[] vertexArray, int[] colorArray,
                                  int[] textureCoordArray, SoftwareTexture texture, float[] objectTransform, int blendMode,
                                  float transparency, boolean wrapTextureCoords,
                                  float textureTranslateU, float textureTranslateV) {
        Projection projection = uiPerspective ? createUiProjection(surfaceWidth, surfaceHeight) : null;
        float[] effectiveTransform = objectTransform == null ? uiTransform : multiply(uiTransform, objectTransform);
        // UI `Primitive` keeps the palette-zero color-key bit in primitiveParam, unlike opt `PrimitiveArray`.
        // UI `graphics3d` primitive colors are RGB values; object transparency carries the alpha.
        if (texture != null) {
            texture.setRepeatEnabled(wrapTextureCoords);
        }
        renderPrimitiveBuffer(g, target, originX, originY, surfaceWidth, surfaceHeight, primitiveType, primitiveParam,
                0, primitiveCount, vertexArray, colorArray, textureCoordArray, null, texture, effectiveTransform,
                projection, uiClip, surfaceWidth / 2f, surfaceHeight / 2f, uiOrthoWidth, uiOrthoHeight, true,
                (primitiveParam & 0x10) != 0, blendMode, transparency, false, false, true, true,
                uiFog, null, null, textureTranslateU, textureTranslateV, BlendSemantics.UI_GRAPHICS3D);
    }

    public void renderOptFigure(Graphics2D g, BufferedImage target, int originX, int originY, int surfaceWidth, int surfaceHeight, MascotFigure figure) {
        if (figure == null || figure.model() == null) {
            return;
        }
        Projection projection = optPerspectiveEnabled ? createOptProjection(surfaceWidth, surfaceHeight) : null;
        boolean invertScreenY = resolveOptFigureScreenYFlip(optViewTransform);
        renderModel(g, target, originX, originY, surfaceWidth, surfaceHeight, figure, optViewTransform, projection, optClip,
                optScreenCenterX, optScreenCenterY, resolveOptOrthoWidth(surfaceWidth), resolveOptOrthoHeight(surfaceHeight),
                optSemiTransparent, invertScreenY, 0, 1f, optLightingEnabled ? 0.9f : 1f, 1f,
                null, optSphereMapEnabled ? optSphereTexture : null, optToonShaderEnabled ? optToonShader : null, BlendSemantics.FRAMEBUFFER);
    }

    public void renderOptPrimitives(Graphics2D g, BufferedImage target, int originX, int originY, int surfaceWidth, int surfaceHeight,
                                    PrimitiveArray primitives, int attr) {
        renderOptPrimitivesRange(g, target, originX, originY, surfaceWidth, surfaceHeight, primitives, 0, primitives == null ? 0 : primitives.size(), attr);
    }

    public void renderOptPrimitivesRange(Graphics2D g, BufferedImage target, int originX, int originY, int surfaceWidth, int surfaceHeight,
                                         PrimitiveArray primitives, int start, int count, int attr) {
        if (primitives == null) {
            return;
        }
        SoftwareTexture texture = primitiveTextures.length == 0 ? null : primitiveTextures[java.lang.Math.max(0, java.lang.Math.min(primitiveTextureIndex, primitiveTextures.length - 1))];
        SoftwareTexture sphereTexture = ((attr & com.nttdocomo.opt.ui.j3d.Graphics3D.ATTR_SPHERE_MAP) != 0 || optSphereMapEnabled) ? optSphereTexture : null;
        ToonShaderParams toonShader = optToonShaderEnabled ? optToonShader : null;
        // opt.ui.j3d keeps color-key and blend bits in the attr word supplied with each draw call.
        Projection projection = optPerspectiveEnabled ? createOptProjection(surfaceWidth, surfaceHeight) : null;
        boolean invertScreenY = resolveOptPrimitiveScreenYFlip(optViewTransform);
        // Framebuffer-blended opt primitive batches share a staged pass with later opaque draws.
        // Queue them until `flush()`/frame end so they are composited after opaque geometry has
        // established depth, matching the staged ordering expected by the opt renderer contract.
        if (optSemiTransparent && (attr & 0x60) != 0) {
            pendingOptPrimitiveBlends.add(new PendingOptPrimitiveBlend(
                    originX,
                    originY,
                    surfaceWidth,
                    surfaceHeight,
                    primitives.getType(),
                    primitives.getParam(),
                    start,
                    count,
                    primitives.getVertexArray() == null ? null : primitives.getVertexArray().clone(),
                    primitives.getColorArray() == null ? null : primitives.getColorArray().clone(),
                    primitives.getTextureCoordArray() == null ? null : primitives.getTextureCoordArray().clone(),
                    texture,
                    sphereTexture,
                    toonShader,
                    optViewTransform.clone(),
                    projection,
                    optClip == null ? null : new Rectangle(optClip),
                    optScreenCenterX,
                    optScreenCenterY,
                    resolveOptOrthoWidth(surfaceWidth),
                    resolveOptOrthoHeight(surfaceHeight),
                    (attr & 0x10) != 0,
                    attr & 0x60,
                    invertScreenY
            ));
            return;
        }
        renderPrimitiveBuffer(g, target, originX, originY, surfaceWidth, surfaceHeight, primitives.getType(), primitives.getParam(), start, count,
                primitives.getVertexArray(), primitives.getColorArray(), primitives.getTextureCoordArray(), primitives.getPointSpriteArray(),
                texture, optViewTransform, projection, optClip,
                optScreenCenterX, optScreenCenterY, resolveOptOrthoWidth(surfaceWidth), resolveOptOrthoHeight(surfaceHeight),
                optSemiTransparent, (attr & 0x10) != 0, attr & 0x60, 1f, true, invertScreenY, true, true,
                null, sphereTexture, toonShader, 0f, 0f, BlendSemantics.FRAMEBUFFER);
    }

    public void flushPendingOptPrimitiveBlends(Graphics2D g, BufferedImage target) {
        if (pendingOptPrimitiveBlends.isEmpty()) {
            return;
        }
        // Replay deferred framebuffer blends with depth test still active, but without updating z.
        // That lets the effect stay behind nearer opaque geometry without poisoning depth for
        // later draws in the same staged pass.
        for (PendingOptPrimitiveBlend pending : pendingOptPrimitiveBlends) {
            renderPrimitiveBuffer(g, target,
                    pending.originX(),
                    pending.originY(),
                    pending.surfaceWidth(),
                    pending.surfaceHeight(),
                    pending.primitiveType(),
                    pending.primitiveParam(),
                    pending.primitiveStart(),
                    pending.primitiveCount(),
                    pending.vertexArray(),
                    pending.colorArray(),
                    pending.textureCoordArray(),
                    null,
                    pending.texture(),
                    pending.transform(),
                    pending.projection(),
                    pending.clip(),
                    pending.centerX(),
                    pending.centerY(),
                    pending.orthoWidth(),
                    pending.orthoHeight(),
                    true,
                    pending.transparentPaletteZero(),
                    pending.blendMode(),
                    1f,
                    true,
                    pending.invertScreenY(),
                    true,
                    false,
                    null,
                    pending.sphereTexture(),
                    pending.toonShader(),
                    0f,
                    0f,
                    BlendSemantics.FRAMEBUFFER);
        }
        pendingOptPrimitiveBlends.clear();
    }

    private void renderModel(Graphics2D g, BufferedImage target, int originX, int originY, int surfaceWidth, int surfaceHeight,
                             MascotFigure figure, float[] transform, Projection projection, Rectangle clip,
                             float centerX, float centerY, float orthoWidth, float orthoHeight,
                             boolean allowMaterialBlend, boolean invertScreenY, int blendMode, float transparency, float lightScale, float vertexScale,
                             FogState fog, SoftwareTexture sphereTexture, ToonShaderParams defaultToonShader,
                             BlendSemantics blendSemantics) {
        MbacModel model = figure.model();
        int patternMask = figure.patternMask();
        float[] vertices = figure.vertices();
        List<ProjectedPolygon> projected = new ArrayList<>();
        if (DEBUG_3D && !debugProjectionLogged && projection != null) {
            float minX = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            float minZ = Float.POSITIVE_INFINITY;
            float maxZ = Float.NEGATIVE_INFINITY;
            for (int i = 0; i + 2 < vertices.length; i += 3) {
                float[] point = transformPoint(transform, vertices[i], vertices[i + 1], vertices[i + 2]);
                minX = java.lang.Math.min(minX, point[0]);
                maxX = java.lang.Math.max(maxX, point[0]);
                minY = java.lang.Math.min(minY, point[1]);
                maxY = java.lang.Math.max(maxY, point[1]);
                minZ = java.lang.Math.min(minZ, point[2]);
                maxZ = java.lang.Math.max(maxZ, point[2]);
            }
            debugProjectionLogged = true;
            String projectionMessage = "3D debug projection=" + uiPerspectiveMode
                    + " near=" + projection.near()
                    + " far=" + projection.far()
                    + " scaleX=" + projection.scaleX()
                    + " scaleY=" + projection.scaleY()
                    + " depthOffset=" + projection.depthOffset()
                    + " transformedX=[" + minX + "," + maxX + "]"
                    + " transformedY=[" + minY + "," + maxY + "]"
                    + " transformedZ=[" + minZ + "," + maxZ + "]"
                    + " matrix=[" + transform[0] + "," + transform[1] + "," + transform[2] + "," + transform[3]
                    + " | " + transform[4] + "," + transform[5] + "," + transform[6] + "," + transform[7]
                    + " | " + transform[8] + "," + transform[9] + "," + transform[10] + "," + transform[11] + "]";
            OpenDoJaLog.debug(Software3DContext.class, projectionMessage);
        }
        int consideredPolygons = 0;
        int clippedPolygons = 0;
        float projectedMinX = Float.POSITIVE_INFINITY;
        float projectedMaxX = Float.NEGATIVE_INFINITY;
        float projectedMinY = Float.POSITIVE_INFINITY;
        float projectedMaxY = Float.NEGATIVE_INFINITY;
        for (MbacModel.Polygon polygon : model.polygons()) {
            int polyPattern = polygon.patternMask();
            if (polyPattern != 0 && (polyPattern & patternMask) != polyPattern) {
                continue;
            }
            consideredPolygons++;
            int[] indices = polygon.indices();
            float[] transformed = new float[indices.length * 3];
            for (int i = 0; i < indices.length; i++) {
                int index = indices[i] * 3;
                float[] point = transformPoint(transform, vertices[index] * vertexScale, vertices[index + 1] * vertexScale, vertices[index + 2] * vertexScale);
                int destination = i * 3;
                transformed[destination] = point[0];
                transformed[destination + 1] = point[1];
                transformed[destination + 2] = point[2];
            }
            float[] textureCoords = polygon.textureCoords();
            if (projection != null) {
                ClippedPolygon clipped = clipPerspectivePolygon(transformed, textureCoords, null, projection,
                        centerX, centerY, surfaceWidth, surfaceHeight);
                if (clipped == null) {
                    continue;
                }
                if (clipped.points().length != transformed.length) {
                    clippedPolygons++;
                }
                transformed = clipped.points();
                textureCoords = clipped.textureCoords();
            }
            int vertexCount = transformed.length / 3;
            float[] xs = new float[vertexCount];
            float[] ys = new float[vertexCount];
            float[] depthValues = new float[vertexCount];
            float avgDepth = 0f;
            for (int i = 0; i < vertexCount; i++) {
                int source = i * 3;
                float pointX = transformed[source];
                float pointY = transformed[source + 1];
                float pointZ = transformed[source + 2];
                if (projection != null) {
                    float cameraDepth = pointZ + projection.depthOffset();
                    xs[i] = originX + centerX + (pointX * projection.scaleX() / cameraDepth);
                    ys[i] = projectScreenY(originY, centerY, pointY * projection.scaleY() / cameraDepth, invertScreenY);
                    depthValues[i] = 1.0f / java.lang.Math.max(0.0001f, cameraDepth);
                    avgDepth += cameraDepth;
                } else {
                    xs[i] = originX + centerX + (pointX * (surfaceWidth / java.lang.Math.max(1f, orthoWidth)));
                    ys[i] = projectScreenY(originY, centerY, pointY * (surfaceHeight / java.lang.Math.max(1f, orthoHeight)), invertScreenY);
                    depthValues[i] = -pointZ;
                    avgDepth += pointZ;
                }
                projectedMinX = java.lang.Math.min(projectedMinX, xs[i]);
                projectedMaxX = java.lang.Math.max(projectedMaxX, xs[i]);
                projectedMinY = java.lang.Math.min(projectedMinY, ys[i]);
                projectedMaxY = java.lang.Math.max(projectedMaxY, ys[i]);
            }
            avgDepth /= vertexCount;
            int effectiveBlendMode = allowMaterialBlend ? (blendMode | polygon.blendMode()) : blendMode;
            BlendOp effectiveBlendOp = resolveBlendMode(effectiveBlendMode, blendSemantics);
            SoftwareTexture polygonTexture = textureCoords == null ? null : figure.texture(polygon.textureIndex());
            ToonShaderParams toonShader = resolveToonShader(polygonTexture, defaultToonShader);
            if (polygonTexture != null) {
                int modulationColor = scaleColor(0xFFFFFFFF, transparency, lightScale);
                if (indices.length == 4 && vertexCount == 4) {
                    addProjectedFigureQuad(projected, xs, ys, depthValues, modulationColor, avgDepth, polygonTexture, textureCoords,
                            projection != null, polygon.doubleSided() || !CULL_FIGURES, polygon.transparent(),
                            effectiveBlendOp, true, fog, sphereTexture, toonShader);
                } else {
                    addProjectedFaces(projected, xs, ys, depthValues, modulationColor, avgDepth, polygonTexture, textureCoords,
                            null,
                            projection != null, polygon.doubleSided() || !CULL_FIGURES, polygon.transparent(),
                            effectiveBlendOp, true, fog, sphereTexture, toonShader);
                }
                continue;
            }
            int color = scaleColor(polygon.color(), transparency, lightScale);
            if (indices.length == 4 && vertexCount == 4) {
                addProjectedFigureQuad(projected, xs, ys, depthValues, color, avgDepth, null, null,
                        projection != null, polygon.doubleSided() || !CULL_FIGURES, false,
                        effectiveBlendOp, true, fog, sphereTexture, toonShader);
            } else {
                addProjectedFaces(projected, xs, ys, depthValues, color, avgDepth, null, null,
                        null,
                        projection != null, polygon.doubleSided() || !CULL_FIGURES, false, effectiveBlendOp, true, fog, sphereTexture, toonShader);
            }
        }
        if (DEBUG_3D && !debugFigureStatsLogged && projection != null && model.polygons().length >= 200) {
            debugFigureStatsLogged = true;
            String figureMessage = "3D debug figure polygons=" + model.polygons().length
                    + " considered=" + consideredPolygons
                    + " clipped=" + clippedPolygons
                    + " output=" + projected.size()
                    + " projectedX=[" + projectedMinX + "," + projectedMaxX + "]"
                    + " projectedY=[" + projectedMinY + "," + projectedMaxY + "]";
            OpenDoJaLog.debug(Software3DContext.class, figureMessage);
        }
        drawProjected(g, target, projected, clip);
    }

    private void renderPrimitiveBuffer(Graphics2D g, BufferedImage target, int originX, int originY, int surfaceWidth, int surfaceHeight,
                                       int primitiveType, int primitiveParam, int primitiveStart, int primitiveCount, int[] vertexArray,
                                       int[] colorArray, int[] textureCoordArray, int[] pointSpriteArray, SoftwareTexture texture,
                                       float[] transform, Projection projection, Rectangle clip,
                                       float centerX, float centerY, float orthoWidth, float orthoHeight,
                                       boolean allowBlend, boolean transparentPaletteZero, int blendMode, float transparency,
                                       boolean unsignedByteTextureCoords, boolean invertScreenY, boolean opaqueRgbColors, boolean depthWrite,
                                       FogState fog, SoftwareTexture sphereTexture, ToonShaderParams defaultToonShader,
                                       float textureTranslateU, float textureTranslateV, BlendSemantics blendSemantics) {
        if (vertexArray == null) {
            return;
        }
        int verticesPerPrimitive = switch (primitiveType) {
            case 1, 5 -> 1;
            case 2 -> 2;
            case 3 -> 3;
            case 4 -> 4;
            default -> 3;
        };
        int start = java.lang.Math.max(0, primitiveStart);
        int end = java.lang.Math.max(start, java.lang.Math.min(start + java.lang.Math.max(0, primitiveCount), vertexArray.length / (verticesPerPrimitive * 3)));
        int effectiveBlendMode = allowBlend ? blendMode : 0;
        BlendOp effectiveBlendOp = resolveBlendMode(effectiveBlendMode, blendSemantics);
        ToonShaderParams toonShader = resolveToonShader(texture, defaultToonShader);
        List<ProjectedPolygon> projected = new ArrayList<>();
        for (int primitive = start; primitive < end; primitive++) {
            boolean internalVertexColors = (primitiveParam & 0x0C00) == com.nttdocomo.ui.graphics3d.Primitive.COLOR_PER_VERTEX_INTERNAL;
            int vertexBase = primitive * verticesPerPrimitive * 3;
            if (vertexBase + verticesPerPrimitive * 3 > vertexArray.length) {
                break;
            }
            float[] transformed = new float[verticesPerPrimitive * 3];
            for (int i = 0; i < verticesPerPrimitive; i++) {
                int source = vertexBase + i * 3;
                float[] point = transformPoint(transform, vertexArray[source], vertexArray[source + 1], vertexArray[source + 2]);
                int destination = i * 3;
                transformed[destination] = point[0];
                transformed[destination + 1] = point[1];
                transformed[destination + 2] = point[2];
            }
            float[] uv = null;
            float[] vertexModulationColors = null;
            if (texture != null && verticesPerPrimitive >= 3 && textureCoordArray != null
                    && textureCoordArray.length >= primitive * verticesPerPrimitive * 2 + verticesPerPrimitive * 2) {
                uv = new float[verticesPerPrimitive * 2];
                int uvBase = primitive * verticesPerPrimitive * 2;
                for (int i = 0; i < uv.length; i++) {
                    int coordinate = textureCoordArray[uvBase + i];
                    // opt.ui.j3d primitive texture coordinates are consumed as unsigned bytes by the
                    // original renderer, so animated scrolling can legitimately pass through negative
                    // or >255 values before they wrap back into the 8-bit texture domain.
                    uv[i] = unsignedByteTextureCoords
                            ? (coordinate & 0xFF)
                            : coordinate;
                    uv[i] += (i & 1) == 0 ? textureTranslateU : textureTranslateV;
                }
            }
            if (texture != null && verticesPerPrimitive >= 3
                    && internalVertexColors
                    && colorArray != null
                    && colorArray.length >= primitive * verticesPerPrimitive + verticesPerPrimitive) {
                vertexModulationColors = new float[verticesPerPrimitive * 4];
                int colorBase = primitive * verticesPerPrimitive;
                for (int i = 0; i < verticesPerPrimitive; i++) {
                    int color = scaleColor(colorArray[colorBase + i], transparency, 1f);
                    int destination = i * 4;
                    vertexModulationColors[destination] = (color >>> 24) & 0xFF;
                    vertexModulationColors[destination + 1] = (color >>> 16) & 0xFF;
                    vertexModulationColors[destination + 2] = (color >>> 8) & 0xFF;
                    vertexModulationColors[destination + 3] = color & 0xFF;
                }
            }
            if (primitiveType == com.nttdocomo.opt.ui.j3d.Graphics3D.PRIMITIVE_POINT_SPRITES) {
                float[] xs = new float[1];
                float[] ys = new float[1];
                float[] depthValues = new float[1];
                float avgDepth;
                float pointX = transformed[0];
                float pointY = transformed[1];
                float pointZ = transformed[2];
                if (projection != null) {
                    float cameraDepth = pointZ + projection.depthOffset();
                    if (cameraDepth < projection.near() || cameraDepth > projection.far()) {
                        continue;
                    }
                    float projectedX = pointX * projection.scaleX() / java.lang.Math.max(0.0001f, cameraDepth);
                    float projectedY = pointY * projection.scaleY() / java.lang.Math.max(0.0001f, cameraDepth);
                    xs[0] = originX + centerX + projectedX;
                    ys[0] = projectScreenY(originY, centerY, projectedY, invertScreenY);
                    depthValues[0] = 1.0f / java.lang.Math.max(0.0001f, cameraDepth);
                    avgDepth = cameraDepth;
                } else {
                    xs[0] = originX + centerX + (pointX * (surfaceWidth / java.lang.Math.max(1f, orthoWidth)));
                    ys[0] = projectScreenY(originY, centerY, pointY * (surfaceHeight / java.lang.Math.max(1f, orthoHeight)), invertScreenY);
                    depthValues[0] = -pointZ;
                    avgDepth = pointZ;
                }
                int baseColor = internalVertexColors ? 0xFFFFFFFF : colorForPrimitive(primitive, primitiveParam, colorArray);
                if (opaqueRgbColors) {
                    baseColor = normalizeOpaqueRgbColor(baseColor);
                }
                int color = scaleColor(baseColor, transparency, 1f);
                addProjectedPointSprite(projected, primitiveParam, primitive, xs, ys, depthValues,
                        projection, surfaceWidth, surfaceHeight, orthoWidth, orthoHeight,
                        pointSpriteArray, color, avgDepth, texture, transparentPaletteZero,
                        effectiveBlendOp, depthWrite, fog, sphereTexture, toonShader);
                continue;
            }
            if (projection != null) {
                ClippedPolygon clipped = clipPerspectivePolygon(transformed, uv, vertexModulationColors, projection,
                        centerX, centerY, surfaceWidth, surfaceHeight);
                if (clipped == null) {
                    continue;
                }
                transformed = clipped.points();
                uv = clipped.textureCoords();
                vertexModulationColors = clipped.vertexModulationColors();
            }
            int vertexCount = transformed.length / 3;
            float[] xs = new float[vertexCount];
            float[] ys = new float[vertexCount];
            float[] depthValues = new float[vertexCount];
            float avgDepth = 0f;
            for (int i = 0; i < vertexCount; i++) {
                int source = i * 3;
                float pointX = transformed[source];
                float pointY = transformed[source + 1];
                float pointZ = transformed[source + 2];
                if (projection != null) {
                    float cameraDepth = pointZ + projection.depthOffset();
                    xs[i] = originX + centerX + (pointX * projection.scaleX() / java.lang.Math.max(0.0001f, cameraDepth));
                    ys[i] = projectScreenY(originY, centerY, pointY * projection.scaleY() / java.lang.Math.max(0.0001f, cameraDepth), invertScreenY);
                    depthValues[i] = 1.0f / java.lang.Math.max(0.0001f, cameraDepth);
                    avgDepth += cameraDepth;
                } else {
                    xs[i] = originX + centerX + (pointX * (surfaceWidth / java.lang.Math.max(1f, orthoWidth)));
                    ys[i] = projectScreenY(originY, centerY, pointY * (surfaceHeight / java.lang.Math.max(1f, orthoHeight)), invertScreenY);
                    depthValues[i] = -pointZ;
                    avgDepth += pointZ;
                }
            }
            avgDepth /= vertexCount;
            int baseColor = internalVertexColors ? 0xFFFFFFFF : colorForPrimitive(primitive, primitiveParam, colorArray);
            if (opaqueRgbColors) {
                baseColor = normalizeOpaqueRgbColor(baseColor);
            }
            int color = scaleColor(baseColor, transparency, 1f);
            if (texture != null && vertexCount >= 3 && uv != null) {
                if (primitiveType == 4 && vertexCount == 4) {
                    addProjectedPrimitiveQuad(projected, xs, ys, depthValues, color, avgDepth, texture, uv,
                            vertexModulationColors, projection != null, transparentPaletteZero, effectiveBlendOp, depthWrite,
                            fog, sphereTexture, toonShader);
                } else {
                    addProjectedFaces(projected, xs, ys, depthValues, color, avgDepth, texture, uv,
                            vertexModulationColors, projection != null, true, transparentPaletteZero, effectiveBlendOp, depthWrite,
                            fog, sphereTexture, toonShader);
                }
                continue;
            }
            if (primitiveType == 2) {
                projected.add(new ProjectedPolygon(xs, ys, depthValues, color, avgDepth, false, null, null,
                        null, false, false, effectiveBlendOp, depthWrite, fog, sphereTexture, toonShader));
            } else {
                if (primitiveType == 4 && vertexCount == 4) {
                    addProjectedPrimitiveQuad(projected, xs, ys, depthValues, color, avgDepth, null, null,
                            null, projection != null, false, effectiveBlendOp, depthWrite, fog, sphereTexture, toonShader);
                } else {
                    addProjectedFaces(projected, xs, ys, depthValues, color, avgDepth, null, null,
                            null, projection != null, true, false, effectiveBlendOp, depthWrite, fog, sphereTexture, toonShader);
                }
            }
        }
        drawProjected(g, target, projected, clip);
    }

    private static void addProjectedPointSprite(List<ProjectedPolygon> projected, int primitiveParam, int primitive,
                                                float[] xs, float[] ys, float[] depthValues, Projection projection,
                                                int surfaceWidth, int surfaceHeight, float orthoWidth, float orthoHeight,
                                                int[] pointSpriteArray, int color, float depth, SoftwareTexture texture,
                                                boolean transparentPaletteZero, BlendOp blendOp, boolean depthWrite,
                                                FogState fog, SoftwareTexture sphereTexture, ToonShaderParams toonShader) {
        if (xs.length == 0 || ys.length == 0 || depthValues.length == 0 || texture == null) {
            return;
        }
        int spriteBase = switch (primitiveParam & 0x3000) {
            case com.nttdocomo.opt.ui.j3d.Graphics3D.POINT_SPRITE_PER_COMMAND -> 0;
            case com.nttdocomo.opt.ui.j3d.Graphics3D.POINT_SPRITE_PER_VERTEX -> primitive * 8;
            default -> -1;
        };
        if (spriteBase < 0 || pointSpriteArray == null || spriteBase + 8 > pointSpriteArray.length) {
            return;
        }
        int widthParam = pointSpriteArray[spriteBase];
        int heightParam = pointSpriteArray[spriteBase + 1];
        int angleParam = pointSpriteArray[spriteBase + 2];
        int textureU0 = pointSpriteArray[spriteBase + 3];
        int textureV0 = pointSpriteArray[spriteBase + 4];
        int textureU1 = pointSpriteArray[spriteBase + 5];
        int textureV1 = pointSpriteArray[spriteBase + 6];
        int flags = pointSpriteArray[spriteBase + 7];
        if (textureU1 <= textureU0 || textureV1 <= textureV0) {
            return;
        }

        boolean pixelSize = (flags & com.nttdocomo.opt.ui.j3d.Graphics3D.POINT_SPRITE_FLAG_PIXEL_SIZE) != 0;
        boolean noPerspective = (flags & com.nttdocomo.opt.ui.j3d.Graphics3D.POINT_SPRITE_FLAG_NO_PERSPECTIVE) != 0;
        float width;
        float height;
        if (projection != null) {
            float cameraDepth = 1.0f / java.lang.Math.max(DEPTH_EPSILON, depthValues[0]);
            if (pixelSize) {
                float scale = noPerspective ? 1f : projection.near() / cameraDepth;
                width = widthParam * scale;
                height = heightParam * scale;
            } else {
                float referenceDepth = noPerspective ? projection.near() : cameraDepth;
                width = widthParam * projection.scaleX() / java.lang.Math.max(DEPTH_EPSILON, referenceDepth);
                height = heightParam * projection.scaleY() / java.lang.Math.max(DEPTH_EPSILON, referenceDepth);
            }
        } else if (pixelSize) {
            width = widthParam;
            height = heightParam;
        } else {
            width = widthParam * (surfaceWidth / java.lang.Math.max(1f, orthoWidth));
            height = heightParam * (surfaceHeight / java.lang.Math.max(1f, orthoHeight));
        }
        if (width <= 0f || height <= 0f) {
            return;
        }

        float centerX = xs[0];
        float centerY = ys[0];
        float halfWidth = width * 0.5f;
        float halfHeight = height * 0.5f;
        float radians = (float) (angleParam * java.lang.Math.PI / 2048.0);
        float sin = (float) java.lang.Math.sin(radians);
        float cos = (float) java.lang.Math.cos(radians);
        float[] spriteXs = new float[4];
        float[] spriteYs = new float[4];
        rotateScreenPoint(centerX, centerY, -halfWidth, -halfHeight, cos, sin, spriteXs, spriteYs, 0);
        rotateScreenPoint(centerX, centerY, halfWidth, -halfHeight, cos, sin, spriteXs, spriteYs, 1);
        rotateScreenPoint(centerX, centerY, halfWidth, halfHeight, cos, sin, spriteXs, spriteYs, 2);
        rotateScreenPoint(centerX, centerY, -halfWidth, halfHeight, cos, sin, spriteXs, spriteYs, 3);
        float[] spriteDepths = new float[]{depthValues[0], depthValues[0], depthValues[0], depthValues[0]};
        float[] spriteUvs = new float[]{
                textureU0, textureV0,
                textureU1, textureV0,
                textureU1, textureV1,
                textureU0, textureV1
        };
        // DoJa opt point sprites are center-anchored billboards encoded as:
        // width, height, angle, u0, v0, u1, v1, flags.
        addProjectedPrimitiveQuad(projected, spriteXs, spriteYs, spriteDepths, color, depth, texture, spriteUvs,
                null, projection != null, transparentPaletteZero, blendOp, depthWrite, fog, sphereTexture, toonShader);
    }

    private static void rotateScreenPoint(float centerX, float centerY, float localX, float localY,
                                          float cos, float sin, float[] xs, float[] ys, int index) {
        xs[index] = centerX + localX * cos - localY * sin;
        ys[index] = centerY + localX * sin + localY * cos;
    }

    // The native opt perspective primitive helper at `micro3d_v3_32.dll:0x1000a180` stores
    // screen-space X as `centerX + projectedX` and screen-space Y as `centerY - projectedY`.
    // Keep the API split explicit here so the shared software raster can reproduce that contract.
    private static float projectScreenY(int originY, float centerY, float projectedOffsetY, boolean invertScreenY) {
        return originY + centerY + (invertScreenY ? -projectedOffsetY : projectedOffsetY);
    }

    private static boolean hasIdentityBasis(float[] matrix) {
        return matrix != null
                && matrix.length >= 12
                && matrix[0] == 1f && matrix[1] == 0f && matrix[2] == 0f
                && matrix[4] == 0f && matrix[5] == 1f && matrix[6] == 0f
                && matrix[8] == 0f && matrix[9] == 0f && matrix[10] == 1f;
    }

    private static boolean resolveOptPrimitiveScreenYFlip(float[] matrix) {
        // opt.ui.j3d primitive scenes can submit either a reflected camera basis or
        // identity-pretransformed billboard vertices, so the screen-Y rule must follow
        // the submitted view transform instead of assuming one fixed primitive convention.
        if (hasIdentityBasis(matrix)) {
            return false;
        }
        return matrix == null || matrix.length < 6 || matrix[5] >= 0f;
    }

    private static void addProjectedPrimitiveQuad(List<ProjectedPolygon> projected, float[] xs, float[] ys, float[] depthValues,
                                                  int color, float depth, SoftwareTexture texture, float[] textureCoords,
                                                  float[] vertexModulationColors,
                                                  boolean perspective, boolean transparentPaletteZero, BlendOp blendOp, boolean depthWrite,
                                                  FogState fog, SoftwareTexture sphereTexture, ToonShaderParams toonShader) {
        addProjectedTriangle(projected,
                new float[]{xs[0], xs[1], xs[2]},
                new float[]{ys[0], ys[1], ys[2]},
                new float[]{depthValues[0], depthValues[1], depthValues[2]},
                color,
                depth,
                texture,
                textureCoords == null || textureCoords.length < 8
                        ? null
                        : new float[]{
                                textureCoords[0], textureCoords[1],
                                textureCoords[2], textureCoords[3],
                                textureCoords[4], textureCoords[5]
                        },
                vertexModulationColors == null || vertexModulationColors.length < 16
                        ? null
                        : new float[]{
                                vertexModulationColors[0], vertexModulationColors[1], vertexModulationColors[2], vertexModulationColors[3],
                                vertexModulationColors[4], vertexModulationColors[5], vertexModulationColors[6], vertexModulationColors[7],
                                vertexModulationColors[8], vertexModulationColors[9], vertexModulationColors[10], vertexModulationColors[11]
                        },
                perspective,
                true,
                transparentPaletteZero,
                blendOp,
                depthWrite,
                fog,
                sphereTexture,
                toonShader);
        addProjectedTriangle(projected,
                new float[]{xs[0], xs[2], xs[3]},
                new float[]{ys[0], ys[2], ys[3]},
                new float[]{depthValues[0], depthValues[2], depthValues[3]},
                color,
                depth,
                texture,
                textureCoords == null || textureCoords.length < 8
                        ? null
                        : new float[]{
                                textureCoords[0], textureCoords[1],
                                textureCoords[4], textureCoords[5],
                                textureCoords[6], textureCoords[7]
                        },
                vertexModulationColors == null || vertexModulationColors.length < 16
                        ? null
                        : new float[]{
                                vertexModulationColors[0], vertexModulationColors[1], vertexModulationColors[2], vertexModulationColors[3],
                                vertexModulationColors[8], vertexModulationColors[9], vertexModulationColors[10], vertexModulationColors[11],
                                vertexModulationColors[12], vertexModulationColors[13], vertexModulationColors[14], vertexModulationColors[15]
                        },
                perspective,
                true,
                transparentPaletteZero,
                blendOp,
                depthWrite,
                fog,
                sphereTexture,
                toonShader);
    }

    private static void addProjectedFigureQuad(List<ProjectedPolygon> projected, float[] xs, float[] ys, float[] depthValues,
                                               int color, float depth, SoftwareTexture texture, float[] textureCoords,
                                               boolean perspective, boolean doubleSided, boolean transparentPaletteZero,
                                               BlendOp blendOp, boolean depthWrite, FogState fog,
                                               SoftwareTexture sphereTexture, ToonShaderParams toonShader) {
        if (!doubleSided && isBackFacingPolygon(xs, ys)) {
            return;
        }
        addProjectedTriangle(projected,
                new float[]{xs[0], xs[1], xs[2]},
                new float[]{ys[0], ys[1], ys[2]},
                new float[]{depthValues[0], depthValues[1], depthValues[2]},
                color,
                depth,
                texture,
                textureCoords == null || textureCoords.length < 8
                        ? null
                        : new float[]{
                                textureCoords[0], textureCoords[1],
                                textureCoords[2], textureCoords[3],
                                textureCoords[4], textureCoords[5]
                        },
                null,
                perspective,
                doubleSided,
                transparentPaletteZero,
                blendOp,
                depthWrite,
                fog,
                sphereTexture,
                toonShader);
        addProjectedTriangle(projected,
                new float[]{xs[2], xs[1], xs[3]},
                new float[]{ys[2], ys[1], ys[3]},
                new float[]{depthValues[2], depthValues[1], depthValues[3]},
                color,
                depth,
                texture,
                textureCoords == null || textureCoords.length < 8
                        ? null
                        : new float[]{
                                textureCoords[4], textureCoords[5],
                                textureCoords[2], textureCoords[3],
                                textureCoords[6], textureCoords[7]
                        },
                null,
                perspective,
                doubleSided,
                transparentPaletteZero,
                blendOp,
                depthWrite,
                fog,
                sphereTexture,
                toonShader);
    }

    private static void addProjectedFaces(List<ProjectedPolygon> projected, float[] xs, float[] ys, float[] depthValues, int color, float depth,
                                          SoftwareTexture texture, float[] textureCoords, float[] vertexModulationColors, boolean perspective,
                                          boolean doubleSided, boolean transparentPaletteZero, BlendOp blendOp, boolean depthWrite,
                                          FogState fog, SoftwareTexture sphereTexture, ToonShaderParams toonShader) {
        if (xs.length < 3) {
            return;
        }
        if (!doubleSided && isBackFacingPolygon(xs, ys)) {
            return;
        }
        for (int i = 1; i < xs.length - 1; i++) {
            addProjectedTriangle(projected,
                    new float[]{xs[0], xs[i], xs[i + 1]},
                    new float[]{ys[0], ys[i], ys[i + 1]},
                    new float[]{depthValues[0], depthValues[i], depthValues[i + 1]},
                    color,
                    depth,
                    texture,
                    textureCoords == null || textureCoords.length < (i + 2) * 2
                            ? null
                            : new float[]{
                                    textureCoords[0], textureCoords[1],
                                    textureCoords[i * 2], textureCoords[i * 2 + 1],
                                    textureCoords[(i + 1) * 2], textureCoords[(i + 1) * 2 + 1]
                            },
                    vertexModulationColors == null || vertexModulationColors.length < (i + 2) * 4
                            ? null
                            : new float[]{
                                    vertexModulationColors[0], vertexModulationColors[1], vertexModulationColors[2], vertexModulationColors[3],
                                    vertexModulationColors[i * 4], vertexModulationColors[i * 4 + 1], vertexModulationColors[i * 4 + 2], vertexModulationColors[i * 4 + 3],
                                    vertexModulationColors[(i + 1) * 4], vertexModulationColors[(i + 1) * 4 + 1], vertexModulationColors[(i + 1) * 4 + 2], vertexModulationColors[(i + 1) * 4 + 3]
                            },
                    perspective,
                    doubleSided,
                    transparentPaletteZero,
                    blendOp,
                    depthWrite,
                    fog,
                    sphereTexture,
                    toonShader);
        }
    }

    private static void addProjectedTriangle(List<ProjectedPolygon> projected, float[] xs, float[] ys, float[] depthValues, int color, float depth,
                                             SoftwareTexture texture, float[] textureCoords, float[] vertexModulationColors, boolean perspective,
                                             boolean doubleSided, boolean transparentPaletteZero, BlendOp blendOp, boolean depthWrite,
                                             FogState fog, SoftwareTexture sphereTexture, ToonShaderParams toonShader) {
        projected.add(new ProjectedPolygon(xs, ys, depthValues, color, depth, true, texture, textureCoords,
                vertexModulationColors, perspective, transparentPaletteZero, blendOp, depthWrite, fog, sphereTexture, toonShader));
    }

    private static ClippedPolygon clipPerspectivePolygon(float[] points, float[] textureCoords, float[] vertexModulationColors, Projection projection,
                                                         float centerX, float centerY, int surfaceWidth, int surfaceHeight) {
        ClippedPolygon clipped = clipPlane(points, textureCoords, vertexModulationColors, 0f, 0f, 1f,
                projection.depthOffset() - projection.near());
        if (clipped == null) {
            return null;
        }
        if (!CLIP_SCREEN_PLANES) {
            return clipped;
        }
        clipped = clipPlane(clipped.points(), clipped.textureCoords(), clipped.vertexModulationColors(),
                projection.scaleX(), 0f, centerX, centerX * projection.depthOffset());
        if (clipped == null) {
            return null;
        }
        clipped = clipPlane(clipped.points(), clipped.textureCoords(), clipped.vertexModulationColors(),
                -projection.scaleX(), 0f, surfaceWidth - centerX, (surfaceWidth - centerX) * projection.depthOffset());
        if (clipped == null) {
            return null;
        }
        clipped = clipPlane(clipped.points(), clipped.textureCoords(), clipped.vertexModulationColors(),
                0f, projection.scaleY(), centerY, centerY * projection.depthOffset());
        if (clipped == null) {
            return null;
        }
        return clipPlane(clipped.points(), clipped.textureCoords(), clipped.vertexModulationColors(),
                0f, -projection.scaleY(), surfaceHeight - centerY, (surfaceHeight - centerY) * projection.depthOffset());
    }

    private static ClippedPolygon clipPlane(float[] points, float[] textureCoords, float[] vertexModulationColors,
                                            float ax, float ay, float az, float aw) {
        int count = points.length / 3;
        if (count < 3) {
            return null;
        }
        float[] clippedPoints = new float[Math.max(18, (count * 2 + 2) * 3)];
        float[] clippedTextureCoords = textureCoords == null ? null : new float[Math.max(12, (count * 2 + 2) * 2)];
        float[] clippedVertexModulationColors = vertexModulationColors == null ? null : new float[Math.max(24, (count * 2 + 2) * 4)];
        int outCount = 0;
        int previous = count - 1;
        float previousX = points[previous * 3];
        float previousY = points[previous * 3 + 1];
        float previousZ = points[previous * 3 + 2];
        float previousDistance = planeDistance(previousX, previousY, previousZ, ax, ay, az, aw);
        boolean previousInside = previousDistance >= 0f;
        float previousU = textureCoords == null ? 0f : textureCoords[previous * 2];
        float previousV = textureCoords == null ? 0f : textureCoords[previous * 2 + 1];
        float previousA = vertexModulationColors == null ? 0f : vertexModulationColors[previous * 4];
        float previousR = vertexModulationColors == null ? 0f : vertexModulationColors[previous * 4 + 1];
        float previousG = vertexModulationColors == null ? 0f : vertexModulationColors[previous * 4 + 2];
        float previousB = vertexModulationColors == null ? 0f : vertexModulationColors[previous * 4 + 3];
        for (int i = 0; i < count; i++) {
            float currentX = points[i * 3];
            float currentY = points[i * 3 + 1];
            float currentZ = points[i * 3 + 2];
            float currentDistance = planeDistance(currentX, currentY, currentZ, ax, ay, az, aw);
            boolean currentInside = currentDistance >= 0f;
            float currentU = textureCoords == null ? 0f : textureCoords[i * 2];
            float currentV = textureCoords == null ? 0f : textureCoords[i * 2 + 1];
            float currentA = vertexModulationColors == null ? 0f : vertexModulationColors[i * 4];
            float currentR = vertexModulationColors == null ? 0f : vertexModulationColors[i * 4 + 1];
            float currentG = vertexModulationColors == null ? 0f : vertexModulationColors[i * 4 + 2];
            float currentB = vertexModulationColors == null ? 0f : vertexModulationColors[i * 4 + 3];
            if (previousInside != currentInside) {
                float denominator = currentDistance - previousDistance;
                if (java.lang.Math.abs(denominator) > 0.0001f) {
                    float t = -previousDistance / denominator;
                    clippedPoints = ensureClipPointCapacity(clippedPoints, outCount);
                    clippedPoints[outCount * 3] = lerp(previousX, currentX, t);
                    clippedPoints[outCount * 3 + 1] = lerp(previousY, currentY, t);
                    clippedPoints[outCount * 3 + 2] = lerp(previousZ, currentZ, t);
                    if (clippedTextureCoords != null) {
                        clippedTextureCoords = ensureClipUvCapacity(clippedTextureCoords, outCount);
                        clippedTextureCoords[outCount * 2] = lerp(previousU, currentU, t);
                        clippedTextureCoords[outCount * 2 + 1] = lerp(previousV, currentV, t);
                    }
                    if (clippedVertexModulationColors != null) {
                        clippedVertexModulationColors = ensureClipColorCapacity(clippedVertexModulationColors, outCount);
                        clippedVertexModulationColors[outCount * 4] = lerp(previousA, currentA, t);
                        clippedVertexModulationColors[outCount * 4 + 1] = lerp(previousR, currentR, t);
                        clippedVertexModulationColors[outCount * 4 + 2] = lerp(previousG, currentG, t);
                        clippedVertexModulationColors[outCount * 4 + 3] = lerp(previousB, currentB, t);
                    }
                    outCount++;
                }
            }
            if (currentInside) {
                clippedPoints = ensureClipPointCapacity(clippedPoints, outCount);
                clippedPoints[outCount * 3] = currentX;
                clippedPoints[outCount * 3 + 1] = currentY;
                clippedPoints[outCount * 3 + 2] = currentZ;
                if (clippedTextureCoords != null) {
                    clippedTextureCoords = ensureClipUvCapacity(clippedTextureCoords, outCount);
                    clippedTextureCoords[outCount * 2] = currentU;
                    clippedTextureCoords[outCount * 2 + 1] = currentV;
                }
                if (clippedVertexModulationColors != null) {
                    clippedVertexModulationColors = ensureClipColorCapacity(clippedVertexModulationColors, outCount);
                    clippedVertexModulationColors[outCount * 4] = currentA;
                    clippedVertexModulationColors[outCount * 4 + 1] = currentR;
                    clippedVertexModulationColors[outCount * 4 + 2] = currentG;
                    clippedVertexModulationColors[outCount * 4 + 3] = currentB;
                }
                outCount++;
            }
            previousX = currentX;
            previousY = currentY;
            previousZ = currentZ;
            previousDistance = currentDistance;
            previousInside = currentInside;
            previousU = currentU;
            previousV = currentV;
            previousA = currentA;
            previousR = currentR;
            previousG = currentG;
            previousB = currentB;
        }
        if (outCount < 3) {
            return null;
        }
        return new ClippedPolygon(
                Arrays.copyOf(clippedPoints, outCount * 3),
                clippedTextureCoords == null ? null : Arrays.copyOf(clippedTextureCoords, outCount * 2),
                clippedVertexModulationColors == null ? null : Arrays.copyOf(clippedVertexModulationColors, outCount * 4)
        );
    }

    private void drawProjected(Graphics2D g, BufferedImage target, List<ProjectedPolygon> polygons, Rectangle clip) {
        Shape oldClip = g.getClip();
        Composite oldComposite = g.getComposite();
        Rectangle effectiveClip = combineClip(target, oldClip, clip);
        float[] depthBuffer = frameDepthBuffer;
        if (depthBuffer == null || depthBuffer.length != target.getWidth() * target.getHeight()) {
            depthBuffer = new float[target.getWidth() * target.getHeight()];
            Arrays.fill(depthBuffer, Float.NEGATIVE_INFINITY);
        }
        try {
            if (effectiveClip != null) {
                g.setClip(effectiveClip);
            }
            for (ProjectedPolygon polygon : polygons) {
                if (polygon.closed() && polygon.texture() != null && polygon.textureCoords() != null && target != null) {
                    drawTexturedPolygon(target, polygon, effectiveClip, depthBuffer);
                    continue;
                }
                if (polygon.closed()) {
                    drawColoredPolygon(target, polygon, effectiveClip, depthBuffer);
                } else {
                    Path2D.Float path = buildPath(polygon.xs(), polygon.ys(), false);
                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, ((polygon.color() >>> 24) & 0xFF) / 255f));
                    g.setColor(new Color(polygon.color(), true));
                    g.draw(path);
                }
            }
        } finally {
            g.setClip(oldClip);
            g.setComposite(oldComposite);
        }
    }

    private static Path2D.Float buildPath(float[] xs, float[] ys, boolean closed) {
        Path2D.Float path = new Path2D.Float();
        path.moveTo(xs[0], ys[0]);
        for (int i = 1; i < xs.length; i++) {
            path.lineTo(xs[i], ys[i]);
        }
        if (closed) {
            path.closePath();
        }
        return path;
    }

    private static Rectangle combineClip(BufferedImage target, Shape currentClip, Rectangle extraClip) {
        Rectangle bounds = new Rectangle(0, 0, target.getWidth(), target.getHeight());
        if (currentClip != null) {
            bounds = bounds.intersection(currentClip.getBounds());
        }
        if (extraClip != null) {
            bounds = bounds.intersection(extraClip);
        }
        return bounds.isEmpty() ? null : bounds;
    }

    private static void drawTexturedPolygon(BufferedImage target, ProjectedPolygon polygon, Rectangle clip, float[] depthBuffer) {
        float[] uvs = polygon.textureCoords();
        float[] vertexModulationColors = polygon.vertexModulationColors();
        int vertexCount = java.lang.Math.min(polygon.xs().length, uvs.length / 2);
        if (vertexCount < 3) {
            return;
        }
        for (int i = 1; i < vertexCount - 1; i++) {
            drawTexturedTriangle(target, clip, depthBuffer, polygon.texture(), polygon.color(), polygon.perspective(),
                    vertexModulationColors == null || vertexModulationColors.length < (i + 2) * 4
                            ? null
                            : new float[]{
                                    vertexModulationColors[0], vertexModulationColors[1], vertexModulationColors[2], vertexModulationColors[3],
                                    vertexModulationColors[i * 4], vertexModulationColors[i * 4 + 1], vertexModulationColors[i * 4 + 2], vertexModulationColors[i * 4 + 3],
                                    vertexModulationColors[(i + 1) * 4], vertexModulationColors[(i + 1) * 4 + 1], vertexModulationColors[(i + 1) * 4 + 2], vertexModulationColors[(i + 1) * 4 + 3]
                            },
                    polygon.transparentPaletteZero(), polygon.blendMode(), polygon.depthWrite(), polygon.fog(), polygon.sphereTexture(), polygon.toonShader(),
                    polygon.xs()[0], polygon.ys()[0], uvs[0], uvs[1],
                    polygon.depthValues()[0],
                    polygon.xs()[i], polygon.ys()[i], uvs[i * 2], uvs[i * 2 + 1],
                    polygon.depthValues()[i],
                    polygon.xs()[i + 1], polygon.ys()[i + 1], uvs[(i + 1) * 2], uvs[(i + 1) * 2 + 1],
                    polygon.depthValues()[i + 1]);
        }
    }

    private static void drawColoredPolygon(BufferedImage target, ProjectedPolygon polygon, Rectangle clip, float[] depthBuffer) {
        float[] depthValues = polygon.depthValues();
        int vertexCount = java.lang.Math.min(polygon.xs().length, depthValues.length);
        if (vertexCount < 3) {
            return;
        }
        for (int i = 1; i < vertexCount - 1; i++) {
            drawColoredTriangle(target, clip, depthBuffer, polygon.color(), polygon.blendMode(), polygon.depthWrite(), polygon.perspective(), polygon.fog(), polygon.sphereTexture(), polygon.toonShader(),
                    polygon.xs()[0], polygon.ys()[0], depthValues[0],
                    polygon.xs()[i], polygon.ys()[i], depthValues[i],
                    polygon.xs()[i + 1], polygon.ys()[i + 1], depthValues[i + 1]);
        }
    }

    private static void drawTexturedTriangle(BufferedImage target, Rectangle clip, float[] depthBuffer, SoftwareTexture texture, int modulationColor,
                                             boolean perspective, float[] vertexModulationColors, boolean transparentPaletteZero, BlendOp blendOp, boolean depthWrite,
                                             FogState fog, SoftwareTexture sphereTexture, ToonShaderParams toonShader,
                                             float x0, float y0, float u0, float v0, float d0,
                                             float x1, float y1, float u1, float v1, float d1,
                                             float x2, float y2, float u2, float v2, float d2) {
        float area = edge(x0, y0, x1, y1, x2, y2);
        if (java.lang.Math.abs(area) < 0.0001f) {
            return;
        }
        int fx0 = toRasterFixed(x0);
        int fy0 = toRasterFixed(y0);
        int fx1 = toRasterFixed(x1);
        int fy1 = toRasterFixed(y1);
        int fx2 = toRasterFixed(x2);
        int fy2 = toRasterFixed(y2);
        long rasterArea = edgeFixed(fx0, fy0, fx1, fy1, fx2, fy2);
        if (rasterArea == 0L) {
            return;
        }
        boolean flipped = rasterArea < 0L;
        boolean topLeft12 = isTopLeftEdge(fx1, fy1, fx2, fy2);
        boolean topLeft20 = isTopLeftEdge(fx2, fy2, fx0, fy0);
        boolean topLeft01 = isTopLeftEdge(fx0, fy0, fx1, fy1);
        int minX = clamp((int) java.lang.Math.floor(java.lang.Math.min(x0, java.lang.Math.min(x1, x2))), 0, target.getWidth() - 1);
        int maxX = clamp((int) java.lang.Math.ceil(java.lang.Math.max(x0, java.lang.Math.max(x1, x2))), 0, target.getWidth() - 1);
        int minY = clamp((int) java.lang.Math.floor(java.lang.Math.min(y0, java.lang.Math.min(y1, y2))), 0, target.getHeight() - 1);
        int maxY = clamp((int) java.lang.Math.ceil(java.lang.Math.max(y0, java.lang.Math.max(y1, y2))), 0, target.getHeight() - 1);
        if (clip != null) {
            minX = java.lang.Math.max(minX, clip.x);
            minY = java.lang.Math.max(minY, clip.y);
            maxX = java.lang.Math.min(maxX, clip.x + clip.width - 1);
            maxY = java.lang.Math.min(maxY, clip.y + clip.height - 1);
        }
        if (minX > maxX || minY > maxY) {
            return;
        }
        for (int y = minY; y <= maxY; y++) {
            float py = y + 0.5f;
            int rasterY = (y << RASTER_SUBPIXEL_SHIFT) + (RASTER_SUBPIXEL_SCALE >> 1);
            for (int x = minX; x <= maxX; x++) {
                float px = x + 0.5f;
                int rasterX = (x << RASTER_SUBPIXEL_SHIFT) + (RASTER_SUBPIXEL_SCALE >> 1);
                long coverage0 = edgeFixed(fx1, fy1, fx2, fy2, rasterX, rasterY);
                long coverage1 = edgeFixed(fx2, fy2, fx0, fy0, rasterX, rasterY);
                long coverage2 = edgeFixed(fx0, fy0, fx1, fy1, rasterX, rasterY);
                if (flipped) {
                    coverage0 = -coverage0;
                    coverage1 = -coverage1;
                    coverage2 = -coverage2;
                }
                if (coverage0 < 0L || (coverage0 == 0L && !topLeft12)
                        || coverage1 < 0L || (coverage1 == 0L && !topLeft20)
                        || coverage2 < 0L || (coverage2 == 0L && !topLeft01)) {
                    continue;
                }
                float w0 = edge(x1, y1, x2, y2, px, py) / area;
                float w1 = edge(x2, y2, x0, y0, px, py) / area;
                float w2 = edge(x0, y0, x1, y1, px, py) / area;
                float depth = d0 * w0 + d1 * w1 + d2 * w2;
                int offset = y * target.getWidth() + x;
                if (depth < depthBuffer[offset] - DEPTH_EPSILON) {
                    continue;
                }
                float u;
                float v;
                if (perspective) {
                    float weight = java.lang.Math.max(0.000001f, depth);
                    u = (u0 * d0 * w0 + u1 * d1 * w1 + u2 * d2 * w2) / weight;
                    v = (v0 * d0 * w0 + v1 * d1 * w1 + v2 * d2 * w2) / weight;
                } else {
                    u = u0 * w0 + u1 * w1 + u2 * w2;
                    v = v0 * w0 + v1 * w1 + v2 * w2;
                }
                int sample = multiplyColor(texture.sampleColor(u, v, transparentPaletteZero), modulationColor);
                if (vertexModulationColors != null) {
                    sample = multiplyColor(sample,
                            interpolateVertexModulationColor(vertexModulationColors, perspective, w0, w1, w2, d0, d1, d2, depth));
                }
                sample = applySphereMap(sample, sphereTexture, target.getWidth(), target.getHeight(), x, y);
                sample = applyToonShader(sample, toonShader);
                sample = applyFog(sample, fog, depth, perspective);
                if (((sample >>> 24) & 0xFF) <= 0) {
                    continue;
                }
                if (depthWrite) {
                    depthBuffer[offset] = depth;
                }
                blendPixel(target, x, y, sample, blendOp);
            }
        }
    }

    private static void drawColoredTriangle(BufferedImage target, Rectangle clip, float[] depthBuffer, int color, BlendOp blendOp, boolean depthWrite,
                                            boolean perspective, FogState fog, SoftwareTexture sphereTexture, ToonShaderParams toonShader,
                                            float x0, float y0, float d0,
                                            float x1, float y1, float d1,
                                            float x2, float y2, float d2) {
        float area = edge(x0, y0, x1, y1, x2, y2);
        if (java.lang.Math.abs(area) < 0.0001f) {
            return;
        }
        int fx0 = toRasterFixed(x0);
        int fy0 = toRasterFixed(y0);
        int fx1 = toRasterFixed(x1);
        int fy1 = toRasterFixed(y1);
        int fx2 = toRasterFixed(x2);
        int fy2 = toRasterFixed(y2);
        long rasterArea = edgeFixed(fx0, fy0, fx1, fy1, fx2, fy2);
        if (rasterArea == 0L) {
            return;
        }
        boolean flipped = rasterArea < 0L;
        boolean topLeft12 = isTopLeftEdge(fx1, fy1, fx2, fy2);
        boolean topLeft20 = isTopLeftEdge(fx2, fy2, fx0, fy0);
        boolean topLeft01 = isTopLeftEdge(fx0, fy0, fx1, fy1);
        int minX = clamp((int) java.lang.Math.floor(java.lang.Math.min(x0, java.lang.Math.min(x1, x2))), 0, target.getWidth() - 1);
        int maxX = clamp((int) java.lang.Math.ceil(java.lang.Math.max(x0, java.lang.Math.max(x1, x2))), 0, target.getWidth() - 1);
        int minY = clamp((int) java.lang.Math.floor(java.lang.Math.min(y0, java.lang.Math.min(y1, y2))), 0, target.getHeight() - 1);
        int maxY = clamp((int) java.lang.Math.ceil(java.lang.Math.max(y0, java.lang.Math.max(y1, y2))), 0, target.getHeight() - 1);
        if (clip != null) {
            minX = java.lang.Math.max(minX, clip.x);
            minY = java.lang.Math.max(minY, clip.y);
            maxX = java.lang.Math.min(maxX, clip.x + clip.width - 1);
            maxY = java.lang.Math.min(maxY, clip.y + clip.height - 1);
        }
        if (minX > maxX || minY > maxY) {
            return;
        }
        for (int y = minY; y <= maxY; y++) {
            float py = y + 0.5f;
            int rasterY = (y << RASTER_SUBPIXEL_SHIFT) + (RASTER_SUBPIXEL_SCALE >> 1);
            for (int x = minX; x <= maxX; x++) {
                float px = x + 0.5f;
                int rasterX = (x << RASTER_SUBPIXEL_SHIFT) + (RASTER_SUBPIXEL_SCALE >> 1);
                long coverage0 = edgeFixed(fx1, fy1, fx2, fy2, rasterX, rasterY);
                long coverage1 = edgeFixed(fx2, fy2, fx0, fy0, rasterX, rasterY);
                long coverage2 = edgeFixed(fx0, fy0, fx1, fy1, rasterX, rasterY);
                if (flipped) {
                    coverage0 = -coverage0;
                    coverage1 = -coverage1;
                    coverage2 = -coverage2;
                }
                if (coverage0 < 0L || (coverage0 == 0L && !topLeft12)
                        || coverage1 < 0L || (coverage1 == 0L && !topLeft20)
                        || coverage2 < 0L || (coverage2 == 0L && !topLeft01)) {
                    continue;
                }
                float w0 = edge(x1, y1, x2, y2, px, py) / area;
                float w1 = edge(x2, y2, x0, y0, px, py) / area;
                float w2 = edge(x0, y0, x1, y1, px, py) / area;
                float depth = d0 * w0 + d1 * w1 + d2 * w2;
                int offset = y * target.getWidth() + x;
                if (depth < depthBuffer[offset] - DEPTH_EPSILON) {
                    continue;
                }
                int shadedColor = applySphereMap(color, sphereTexture, target.getWidth(), target.getHeight(), x, y);
                shadedColor = applyToonShader(shadedColor, toonShader);
                shadedColor = applyFog(shadedColor, fog, depth, perspective);
                if (((shadedColor >>> 24) & 0xFF) <= 0) {
                    continue;
                }
                if (depthWrite) {
                    depthBuffer[offset] = depth;
                }
                blendPixel(target, x, y, shadedColor, blendOp);
            }
        }
    }

    private static float edge(float ax, float ay, float bx, float by, float px, float py) {
        return (px - ax) * (by - ay) - (py - ay) * (bx - ax);
    }

    private static long edgeFixed(int ax, int ay, int bx, int by, int px, int py) {
        return (long) (px - ax) * (by - ay) - (long) (py - ay) * (bx - ax);
    }

    private static boolean isTopLeftEdge(int ax, int ay, int bx, int by) {
        return ay < by || (ay == by && ax > bx);
    }

    private static int toRasterFixed(float coordinate) {
        return java.lang.Math.round(coordinate * RASTER_SUBPIXEL_SCALE);
    }

    private static float lerp(float start, float end, float delta) {
        return start + (end - start) * delta;
    }

    private static boolean isBackFacingPolygon(float[] xs, float[] ys) {
        if (CULL_SIGN == 0) {
            return false;
        }
        float area = 0f;
        int previous = xs.length - 1;
        for (int i = 0; i < xs.length; i++) {
            area += xs[previous] * ys[i] - xs[i] * ys[previous];
            previous = i;
        }
        // GL front faces become clockwise after mapping to screen coordinates with Y increasing downward,
        // but some games appear to rely on the opposite sign. Keep the default handset-facing choice and
        // allow the sign to be flipped while reverse engineering.
        return CULL_SIGN > 0 ? area >= 0f : area <= 0f;
    }

    private static float planeDistance(float x, float y, float z, float ax, float ay, float az, float aw) {
        return ax * x + ay * y + az * z + aw;
    }

    private static float[] ensureClipPointCapacity(float[] points, int vertexIndex) {
        int required = (vertexIndex + 1) * 3;
        if (required <= points.length) {
            return points;
        }
        return Arrays.copyOf(points, java.lang.Math.max(required, points.length * 2));
    }

    private static float[] ensureClipUvCapacity(float[] textureCoords, int vertexIndex) {
        int required = (vertexIndex + 1) * 2;
        if (required <= textureCoords.length) {
            return textureCoords;
        }
        return Arrays.copyOf(textureCoords, java.lang.Math.max(required, textureCoords.length * 2));
    }

    private static float[] ensureClipColorCapacity(float[] vertexModulationColors, int vertexIndex) {
        int required = (vertexIndex + 1) * 4;
        if (required <= vertexModulationColors.length) {
            return vertexModulationColors;
        }
        return Arrays.copyOf(vertexModulationColors, java.lang.Math.max(required, vertexModulationColors.length * 2));
    }

    private static int multiplyColor(int left, int right) {
        int la = (left >>> 24) & 0xFF;
        int lr = (left >>> 16) & 0xFF;
        int lg = (left >>> 8) & 0xFF;
        int lb = left & 0xFF;
        int ra = (right >>> 24) & 0xFF;
        int rr = (right >>> 16) & 0xFF;
        int rg = (right >>> 8) & 0xFF;
        int rb = right & 0xFF;
        int a = la * ra / 255;
        int r = lr * rr / 255;
        int g = lg * rg / 255;
        int b = lb * rb / 255;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int interpolateVertexModulationColor(float[] vertexModulationColors, boolean perspective,
                                                        float w0, float w1, float w2,
                                                        float d0, float d1, float d2, float depth) {
        float a;
        float r;
        float g;
        float b;
        if (perspective) {
            float weight = java.lang.Math.max(0.000001f, depth);
            a = (vertexModulationColors[0] * d0 * w0
                    + vertexModulationColors[4] * d1 * w1
                    + vertexModulationColors[8] * d2 * w2) / weight;
            r = (vertexModulationColors[1] * d0 * w0
                    + vertexModulationColors[5] * d1 * w1
                    + vertexModulationColors[9] * d2 * w2) / weight;
            g = (vertexModulationColors[2] * d0 * w0
                    + vertexModulationColors[6] * d1 * w1
                    + vertexModulationColors[10] * d2 * w2) / weight;
            b = (vertexModulationColors[3] * d0 * w0
                    + vertexModulationColors[7] * d1 * w1
                    + vertexModulationColors[11] * d2 * w2) / weight;
        } else {
            a = vertexModulationColors[0] * w0 + vertexModulationColors[4] * w1 + vertexModulationColors[8] * w2;
            r = vertexModulationColors[1] * w0 + vertexModulationColors[5] * w1 + vertexModulationColors[9] * w2;
            g = vertexModulationColors[2] * w0 + vertexModulationColors[6] * w1 + vertexModulationColors[10] * w2;
            b = vertexModulationColors[3] * w0 + vertexModulationColors[7] * w1 + vertexModulationColors[11] * w2;
        }
        return (clamp(java.lang.Math.round(a), 0, 255) << 24)
                | (clamp(java.lang.Math.round(r), 0, 255) << 16)
                | (clamp(java.lang.Math.round(g), 0, 255) << 8)
                | clamp(java.lang.Math.round(b), 0, 255);
    }

    private static ToonShaderParams resolveToonShader(SoftwareTexture texture, ToonShaderParams fallback) {
        if (texture != null && texture.toonShaderEnabled()) {
            return new ToonShaderParams(texture.toonThreshold(), texture.toonMid(), texture.toonShadow());
        }
        return fallback;
    }

    private static int applySphereMap(int color, SoftwareTexture sphereTexture, int targetWidth, int targetHeight, int x, int y) {
        if (sphereTexture == null || targetWidth <= 0 || targetHeight <= 0) {
            return color;
        }
        float u = (x + 0.5f) * sphereTexture.width() / targetWidth;
        float v = (y + 0.5f) * sphereTexture.height() / targetHeight;
        int sphereColor = sphereTexture.sampleColor(u, v, false);
        int alpha = (color >>> 24) & 0xFF;
        int red = ((((color >>> 16) & 0xFF) + ((sphereColor >>> 16) & 0xFF)) >> 1);
        int green = ((((color >>> 8) & 0xFF) + ((sphereColor >>> 8) & 0xFF)) >> 1);
        int blue = (((color & 0xFF) + (sphereColor & 0xFF)) >> 1);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static int applyToonShader(int color, ToonShaderParams toonShader) {
        if (toonShader == null) {
            return color;
        }
        int alpha = (color >>> 24) & 0xFF;
        int red = (color >>> 16) & 0xFF;
        int green = (color >>> 8) & 0xFF;
        int blue = color & 0xFF;
        int luminance = (red * 77 + green * 150 + blue * 29) >> 8;
        int scale = luminance >= toonShader.threshold() ? toonShader.mid() : toonShader.shadow();
        red = red * scale / 255;
        green = green * scale / 255;
        blue = blue * scale / 255;
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static int applyFog(int color, FogState fog, float depth, boolean perspective) {
        if (fog == null) {
            return color;
        }
        float distance = perspective
                ? 1f / java.lang.Math.max(0.000001f, depth)
                : java.lang.Math.abs(depth);
        float fogAmount;
        if (fog.mode() == 80) {
            fogAmount = 1f - (float) java.lang.Math.exp(-java.lang.Math.max(0f, fog.density()) * java.lang.Math.max(0f, distance));
        } else {
            float near = fog.linearNear();
            float far = java.lang.Math.max(near + 0.000001f, fog.linearFar());
            fogAmount = (distance - near) / (far - near);
        }
        fogAmount = java.lang.Math.max(0f, java.lang.Math.min(1f, fogAmount));
        if (fogAmount <= 0f) {
            return color;
        }
        int alpha = (color >>> 24) & 0xFF;
        int red = mixChannel((color >>> 16) & 0xFF, (fog.color() >>> 16) & 0xFF, fogAmount);
        int green = mixChannel((color >>> 8) & 0xFF, (fog.color() >>> 8) & 0xFF, fogAmount);
        int blue = mixChannel(color & 0xFF, fog.color() & 0xFF, fogAmount);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static int mixChannel(int start, int end, float amount) {
        return clamp(java.lang.Math.round(start + ((end - start) * amount)), 0, 255);
    }

    private static void blendPixel(BufferedImage target, int x, int y, int source, BlendOp blendOp) {
        int srcA = (source >>> 24) & 0xFF;
        if (srcA <= 0) {
            return;
        }
        int dest = target.getRGB(x, y);
        switch (blendOp) {
            case HALF -> {
                // Mascot material blend bits use framebuffer blend ops, not alpha-over.
                target.setRGB(x, y, averagePixel(dest, applySourceAlphaToBlendColor(source)));
                return;
            }
            case ADD -> {
                target.setRGB(x, y, addPixel(dest, applySourceAlphaToBlendColor(source)));
                return;
            }
            case SUB -> {
                target.setRGB(x, y, subtractPixel(dest, applySourceAlphaToBlendColor(source)));
                return;
            }
            case NORMAL -> {
            }
        }
        if (srcA >= 255) {
            target.setRGB(x, y, source);
            return;
        }
        float srcAlpha = srcA / 255f;
        float destAlpha = ((dest >>> 24) & 0xFF) / 255f;
        float outAlpha = srcAlpha + destAlpha * (1f - srcAlpha);
        if (outAlpha <= 0f) {
            target.setRGB(x, y, 0);
            return;
        }
        float srcRed = ((source >>> 16) & 0xFF) / 255f;
        float srcGreen = ((source >>> 8) & 0xFF) / 255f;
        float srcBlue = (source & 0xFF) / 255f;
        float destRed = ((dest >>> 16) & 0xFF) / 255f;
        float destGreen = ((dest >>> 8) & 0xFF) / 255f;
        float destBlue = (dest & 0xFF) / 255f;
        int outA = clamp(java.lang.Math.round(outAlpha * 255f), 0, 255);
        int outR = clamp(java.lang.Math.round(((srcRed * srcAlpha) + (destRed * destAlpha * (1f - srcAlpha))) / outAlpha * 255f), 0, 255);
        int outG = clamp(java.lang.Math.round(((srcGreen * srcAlpha) + (destGreen * destAlpha * (1f - srcAlpha))) / outAlpha * 255f), 0, 255);
        int outB = clamp(java.lang.Math.round(((srcBlue * srcAlpha) + (destBlue * destAlpha * (1f - srcAlpha))) / outAlpha * 255f), 0, 255);
        target.setRGB(x, y, (outA << 24) | (outR << 16) | (outG << 8) | outB);
    }

    private static int applySourceAlphaToBlendColor(int color) {
        int alpha = (color >>> 24) & 0xFF;
        if (alpha >= 255) {
            return color;
        }
        int red = ((color >>> 16) & 0xFF) * alpha / 255;
        int green = ((color >>> 8) & 0xFF) * alpha / 255;
        int blue = (color & 0xFF) * alpha / 255;
        return (color & 0xFF000000) | (red << 16) | (green << 8) | blue;
    }

    private static BlendOp resolveBlendMode(int blendMode, BlendSemantics blendSemantics) {
        int primaryBlend = blendMode & 0x60;
        if (primaryBlend != 0) {
            if (blendSemantics == BlendSemantics.UI_GRAPHICS3D && primaryBlend == 0x20) {
                return BlendOp.NORMAL;
            }
            if (primaryBlend == 0x20) {
                return BlendOp.HALF;
            }
            if (primaryBlend == 0x40) {
                return BlendOp.ADD;
            }
            if (primaryBlend == 0x60) {
                return BlendOp.SUB;
            }
        }
        int materialBlend = blendMode & 0x06;
        if (materialBlend == 0x02) {
            return BlendOp.HALF;
        }
        if (materialBlend == 0x04) {
            return BlendOp.ADD;
        }
        if (materialBlend == 0x06) {
            return BlendOp.SUB;
        }
        return BlendOp.NORMAL;
    }

    private static int averagePixel(int dest, int source) {
        int outA = java.lang.Math.max((dest >>> 24) & 0xFF, (source >>> 24) & 0xFF);
        int outR = (((dest >>> 16) & 0xFF) + ((source >>> 16) & 0xFF)) >> 1;
        int outG = (((dest >>> 8) & 0xFF) + ((source >>> 8) & 0xFF)) >> 1;
        int outB = ((dest & 0xFF) + (source & 0xFF)) >> 1;
        return (outA << 24) | (outR << 16) | (outG << 8) | outB;
    }

    private static int addPixel(int dest, int source) {
        int outA = java.lang.Math.max((dest >>> 24) & 0xFF, (source >>> 24) & 0xFF);
        int outR = clamp(((dest >>> 16) & 0xFF) + ((source >>> 16) & 0xFF), 0, 255);
        int outG = clamp(((dest >>> 8) & 0xFF) + ((source >>> 8) & 0xFF), 0, 255);
        int outB = clamp((dest & 0xFF) + (source & 0xFF), 0, 255);
        return (outA << 24) | (outR << 16) | (outG << 8) | outB;
    }

    private static int subtractPixel(int dest, int source) {
        int outA = (dest >>> 24) & 0xFF;
        int outR = clamp(((dest >>> 16) & 0xFF) - ((source >>> 16) & 0xFF), 0, 255);
        int outG = clamp(((dest >>> 8) & 0xFF) - ((source >>> 8) & 0xFF), 0, 255);
        int outB = clamp((dest & 0xFF) - (source & 0xFF), 0, 255);
        return (outA << 24) | (outR << 16) | (outG << 8) | outB;
    }

    private static int colorForPrimitive(int primitive, int primitiveParam, int[] colorArray) {
        if (colorArray == null || colorArray.length == 0) {
            return 0xFFFFFFFF;
        }
        int colorMode = primitiveParam & 0x0C00;
        if (colorMode == 0x0400 || colorMode == 0x0800) {
            return colorArray[java.lang.Math.min(primitive, colorArray.length - 1)];
        }
        return colorArray[0];
    }

    private static int normalizeOpaqueRgbColor(int color) {
        return (color & 0xFF000000) == 0 ? (0xFF000000 | color) : color;
    }

    private static int scaleColor(int color, float transparency, float lightScale) {
        int a = (color >>> 24) & 0xFF;
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8) & 0xFF;
        int b = color & 0xFF;
        float alphaScale = java.lang.Math.max(0f, java.lang.Math.min(1f, transparency));
        a = java.lang.Math.round(a * alphaScale);
        r = java.lang.Math.round(java.lang.Math.max(0f, java.lang.Math.min(255f, r * lightScale)));
        g = java.lang.Math.round(java.lang.Math.max(0f, java.lang.Math.min(255f, g * lightScale)));
        b = java.lang.Math.round(java.lang.Math.max(0f, java.lang.Math.min(255f, b * lightScale)));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private Projection createUiProjection(int surfaceWidth, int surfaceHeight) {
        if (uiPerspectiveMode == PerspectiveMode.WIDTH_HEIGHT) {
            float width = uiPerspectiveWidth <= 0f ? surfaceWidth : uiPerspectiveWidth;
            float height = uiPerspectiveHeight <= 0f ? surfaceHeight : uiPerspectiveHeight;
            float scaleX = surfaceWidth * uiPerspectiveNear / java.lang.Math.max(1f, width);
            float scaleY = surfaceHeight * uiPerspectiveNear / java.lang.Math.max(1f, height);
            return new Projection(uiPerspectiveNear, uiPerspectiveFar, scaleX, scaleY, 0f);
        }
        float radians = (float) java.lang.Math.toRadians(normalizePerspectiveDegrees(uiPerspectiveAngleDegrees));
        float pixelScale = (surfaceWidth * 0.5f) / (float) java.lang.Math.tan(radians * 0.5f);
        return new Projection(uiPerspectiveNear, uiPerspectiveFar, pixelScale, pixelScale, 0f);
    }

    private Projection createOptProjection(int surfaceWidth, int surfaceHeight) {
        if (optPerspectiveUsesExtent) {
            // Native `Render_setPerspectiveW/WH` normalizes the requested near-plane extent as a
            // 4.12 fixed-point value before folding it into the cached projection matrix.
            float scaleX = surfaceWidth * optNear * 4096f / java.lang.Math.max(1f, optPerspectiveWidth);
            float scaleY = surfaceHeight * optNear * 4096f / java.lang.Math.max(1f, optPerspectiveHeight);
            return new Projection(optNear, optFar, scaleX, scaleY, 0f);
        }
        float radians = normalizeOptPerspectiveRadians(optPerspectiveAngle);
        float pixelScale = (surfaceWidth * 0.5f) / (float) java.lang.Math.tan(radians * 0.5f);
        return new Projection(optNear, optFar, pixelScale, pixelScale, 0f);
    }

    private float resolveOptOrthoWidth(int surfaceWidth) {
        if (optViewWidth > 0f) {
            return optViewWidth;
        }
        return surfaceWidth / java.lang.Math.max(0.25f, optScaleX);
    }

    private float resolveOptOrthoHeight(int surfaceHeight) {
        if (optViewHeight > 0f) {
            return optViewHeight;
        }
        return surfaceHeight / java.lang.Math.max(0.25f, optScaleY);
    }

    private static float normalizeNear(float rawNear) {
        return rawNear <= 0f ? 1f : rawNear;
    }

    private static float normalizeFar(float near, float rawFar) {
        if (rawFar <= near) {
            return near + 1f;
        }
        return rawFar;
    }

    private static float normalizePerspectiveDegrees(float rawAngle) {
        if (rawAngle <= 0f) {
            return 60f;
        }
        float degrees = rawAngle <= (float) (java.lang.Math.PI * 2.0)
                ? (float) java.lang.Math.toDegrees(rawAngle)
                : rawAngle;
        return java.lang.Math.max(1f, java.lang.Math.min(179f, degrees));
    }

    private static float normalizeOptPerspectiveRadians(float rawAngle) {
        if (rawAngle <= 0f) {
            return (float) java.lang.Math.toRadians(45f);
        }
        float radians = rawAngle <= 2047f
                ? (float) (rawAngle * java.lang.Math.PI / 2048.0)
                : (float) java.lang.Math.toRadians(rawAngle);
        return java.lang.Math.max((float) java.lang.Math.toRadians(1f), java.lang.Math.min((float) java.lang.Math.toRadians(179f), radians));
    }

    private static float[] transformPoint(float[] matrix, float x, float y, float z) {
        return new float[]{
                matrix[0] * x + matrix[1] * y + matrix[2] * z + matrix[3],
                matrix[4] * x + matrix[5] * y + matrix[6] * z + matrix[7],
                matrix[8] * x + matrix[9] * y + matrix[10] * z + matrix[11]
        };
    }

    private static boolean resolveOptFigureScreenYFlip(float[] matrix) {
        // opt.ui.j3d figures rendered under the default camera basis still follow the native
        // centerY - projectedY projection rule. Once a title submits a non-identity basis, that
        // basis already encodes the camera orientation and must not be flipped again.
        return hasIdentityBasis(matrix);
    }

    private static int clamp(int value, int min, int max) {
        return java.lang.Math.max(min, java.lang.Math.min(max, value));
    }

    public static float[] identity() {
        return new float[]{
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f
        };
    }

    public static float[] multiply(float[] left, float[] right) {
        float[] result = identity();
        result[0] = left[0] * right[0] + left[1] * right[4] + left[2] * right[8];
        result[1] = left[0] * right[1] + left[1] * right[5] + left[2] * right[9];
        result[2] = left[0] * right[2] + left[1] * right[6] + left[2] * right[10];
        result[3] = left[0] * right[3] + left[1] * right[7] + left[2] * right[11] + left[3];
        result[4] = left[4] * right[0] + left[5] * right[4] + left[6] * right[8];
        result[5] = left[4] * right[1] + left[5] * right[5] + left[6] * right[9];
        result[6] = left[4] * right[2] + left[5] * right[6] + left[6] * right[10];
        result[7] = left[4] * right[3] + left[5] * right[7] + left[6] * right[11] + left[7];
        result[8] = left[8] * right[0] + left[9] * right[4] + left[10] * right[8];
        result[9] = left[8] * right[1] + left[9] * right[5] + left[10] * right[9];
        result[10] = left[8] * right[2] + left[9] * right[6] + left[10] * right[10];
        result[11] = left[8] * right[3] + left[9] * right[7] + left[10] * right[11] + left[11];
        return result;
    }

    private enum PerspectiveMode {
        FIELD_OF_VIEW,
        WIDTH_HEIGHT
    }

    private record ClippedPolygon(float[] points, float[] textureCoords, float[] vertexModulationColors) {
    }

    private record Projection(float near, float far, float scaleX, float scaleY, float depthOffset) {
    }

    private record ProjectedPolygon(float[] xs, float[] ys, float[] depthValues, int color, float depth, boolean closed,
                                    SoftwareTexture texture, float[] textureCoords, float[] vertexModulationColors, boolean perspective,
                                    boolean transparentPaletteZero, BlendOp blendMode, boolean depthWrite,
                                    FogState fog, SoftwareTexture sphereTexture, ToonShaderParams toonShader) {
    }

    private record PendingOptPrimitiveBlend(int originX, int originY, int surfaceWidth, int surfaceHeight,
                                            int primitiveType, int primitiveParam, int primitiveStart, int primitiveCount,
                                            int[] vertexArray, int[] colorArray, int[] textureCoordArray,
                                            SoftwareTexture texture, SoftwareTexture sphereTexture, ToonShaderParams toonShader,
                                            float[] transform, Projection projection, Rectangle clip,
                                            float centerX, float centerY, float orthoWidth, float orthoHeight,
                                            boolean transparentPaletteZero, int blendMode, boolean invertScreenY) {
    }

    private record FogState(int mode, float linearNear, float linearFar, float density, int color) {
    }

    private record ToonShaderParams(int threshold, int mid, int shadow) {
    }

    private enum BlendSemantics {
        UI_GRAPHICS3D,
        FRAMEBUFFER
    }

    private enum BlendOp {
        NORMAL,
        HALF,
        ADD,
        SUB
    }
}
