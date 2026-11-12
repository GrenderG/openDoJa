package opendoja.g3d;

import com.nttdocomo.ui.graphics3d.*;
import com.nttdocomo.ui.util3d.Transform;
import com.nttdocomo.ui.util3d._TransformInternalAccess;
import opendoja.host.OpenDoJaLog;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Arrays;

public final class DojaGraphics3DRenderer {
    private final Software3DContext context = new Software3DContext();
    private final boolean traceCalls;
    private Fog fog;

    public DojaGraphics3DRenderer(boolean traceCalls) {
        this.traceCalls = traceCalls;
    }

    public Software3DContext context() {
        return context;
    }

    public void copyStateFrom(DojaGraphics3DRenderer source) {
        this.fog = source.fog;
        syncFogState();
    }

    public void setFrameDepthBuffer(float[] depthBuffer) {
        context.setFrameDepthBuffer(depthBuffer);
    }

    public void flushPendingOptPrimitiveBlends(Graphics2D graphics, BufferedImage image) {
        context.flushPendingOptPrimitiveBlends(graphics, image);
    }

    public void setClipRectFor3D(int originX, int originY, int x, int y, int width, int height) {
        context.setUiClip(originX + x, originY + y, width, height);
    }

    public boolean setParallelView(int width, int height) {
        boolean changedDepthMode = context.setUiParallelView(width, height);
        if (traceCalls) {
            OpenDoJaLog.debug(DojaGraphics3DRenderer.class, () -> "3D call setParallelView width=" + width + " height=" + height);
        }
        return changedDepthMode;
    }

    public boolean setPerspectiveView(float a, float b, int c, int d) {
        boolean changedDepthMode = context.setUiPerspectiveView(a, b, c, d);
        if (traceCalls) {
            OpenDoJaLog.debug(DojaGraphics3DRenderer.class, () -> "3D call setPerspectiveViewWH near=" + a + " far=" + b + " width=" + c + " height=" + d);
        }
        return changedDepthMode;
    }

    public boolean setPerspectiveView(float a, float b, float c) {
        boolean changedDepthMode = context.setUiPerspectiveView(a, b, c);
        if (traceCalls) {
            OpenDoJaLog.debug(DojaGraphics3DRenderer.class, () -> "3D call setPerspectiveViewFov near=" + a + " far=" + b + " angle=" + c);
        }
        return changedDepthMode;
    }

    public void setTransform(Transform transform) {
        float[] matrix = transform == null ? Software3DContext.identity() : _TransformInternalAccess.raw(transform);
        context.setUiTransform(matrix);
        if (traceCalls) {
            OpenDoJaLog.debug(DojaGraphics3DRenderer.class, () -> "3D call setTransform matrix=" + Arrays.toString(matrix));
        }
    }

    public void addLight(Light light) {
        if (light == null) {
            return;
        }
        _Graphics3DInternalAccess.LightState lightState = _Graphics3DInternalAccess.lightState(light);
        context.addUiLight(lightState.mode(), lightState.intensity(), lightState.color());
    }

    public void resetLights() {
        context.resetUiLights();
    }

    public void setFog(Fog fog) {
        this.fog = fog;
        syncFogState();
    }

    public void renderObject3D(RenderTarget target, DrawableObject3D object, Transform transform, Runnable prepareDepthFrame) {
        if (object == null) {
            return;
        }
        syncFogState();
        renderObject3DRecursive(target, object, transform == null ? null : matrixOf(transform), prepareDepthFrame);
    }

    private void renderObject3DRecursive(RenderTarget target, DrawableObject3D object, float[] objectMatrix, Runnable prepareDepthFrame) {
        if (object == null || object.getType() == Object3D.TYPE_NONE) {
            return;
        }
        if (object instanceof Group group) {
            float[] combined = composeGroupTransform(objectMatrix, group);
            if (traceCalls) {
                OpenDoJaLog.debug(DojaGraphics3DRenderer.class, () -> "3D call renderObject3D type=Group elements=" + group.getNumElements()
                        + " transform=" + (combined == null ? "null" : Arrays.toString(combined)));
            }
            for (int i = 0; i < group.getNumElements(); i++) {
                Object3D element = group.getElement(i);
                if (element instanceof DrawableObject3D drawable) {
                    renderObject3DRecursive(target, drawable, combined, prepareDepthFrame);
                }
            }
            return;
        }
        if (object instanceof Figure figure) {
            MascotFigure handle = _Graphics3DInternalAccess.handle(figure);
            _Graphics3DInternalAccess.DrawableRenderState renderState = _Graphics3DInternalAccess.drawableRenderState(figure);
            prepareDepthFrame.run();
            if (traceCalls) {
                int polygons = handle == null || handle.model() == null ? -1 : handle.model().polygons().length;
                OpenDoJaLog.debug(DojaGraphics3DRenderer.class, () -> "3D call renderObject3D type=Figure polygons=" + polygons
                        + " textures=" + (handle == null ? -1 : handle.numTextures())
                        + " pattern=" + (handle == null ? -1 : handle.patternMask())
                        + " transform=" + (objectMatrix == null ? "null" : Arrays.toString(objectMatrix)));
            }
            context.renderUiFigure(target.graphics(), target.image(), target.originX(), target.originY(), target.width(), target.height(),
                    handle, objectMatrix, renderState.blendMode(), renderState.transparency());
            return;
        }
        if (object instanceof Primitive primitive) {
            _PrimitiveRenderStateAccess.PrimitiveRenderState renderState = _PrimitiveRenderStateAccess.snapshot(primitive);
            _Graphics3DInternalAccess.DrawableRenderState drawableState = _Graphics3DInternalAccess.drawableRenderState(primitive);
            prepareDepthFrame.run();
            if (traceCalls) {
                OpenDoJaLog.debug(DojaGraphics3DRenderer.class, () -> "3D call renderObject3D type=Primitive primitiveType="
                        + primitive.getPrimitiveType()
                        + " count=" + primitive.size()
                        + " transform=" + (objectMatrix == null ? "null" : Arrays.toString(objectMatrix)));
            }
            context.renderUiPrimitive(target.graphics(), target.image(), target.originX(), target.originY(), target.width(), target.height(),
                    primitive.getPrimitiveType(), renderState.primitiveParam(), primitive.size(),
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

    private void syncFogState() {
        if (fog == null) {
            context.setUiFog(null, 0f, 0f, 0f, 0);
            return;
        }
        _Graphics3DInternalAccess.FogState fogState = _Graphics3DInternalAccess.fogState(fog);
        context.setUiFog(
                fogState.mode(),
                fogState.linearNear(),
                fogState.linearFar(),
                fogState.density(),
                fogState.color()
        );
    }

    private static float[] composeGroupTransform(float[] parentMatrix, Group group) {
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

    public record RenderTarget(Graphics2D graphics, BufferedImage image, int originX, int originY, int width, int height) {
    }
}
