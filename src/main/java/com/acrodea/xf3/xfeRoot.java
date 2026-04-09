package com.acrodea.xf3;

import com.acrodea.xf3.math.xfRectangleInt;
import com.acrodea.xf3.math.xfMath;
import com.acrodea.xf3.math.xfMatrix4;
import com.nttdocomo.ui.Display;
import com.nttdocomo.ui.Graphics;
import com.nttdocomo.ui.Image;
import com.nttdocomo.ui.MediaManager;
import com.nttdocomo.ui.UIException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class xfeRoot extends xfeSubTree {
    private static final int DEFAULT_TINT = 0xFFFFFFFF;

    private final xfeGameGraphFactory factory;
    private final xfeClock clock = new xfeClock();
    private final xfeControllerSet controllerSet = new xfeControllerSet();
    private final BasicResourceManager resourceManager = new BasicResourceManager();
    private final List<xfeNode> nodes = new ArrayList<>();
    private final Map<String, TextureData> textureCache = new LinkedHashMap<>();
    private String loadedSceneName;
    private List<String> sceneImageCandidates = Collections.emptyList();
    private List<SceneShape> sceneShapes = Collections.emptyList();
    private Image cachedSceneImage;
    private String cachedSceneImageName;
    private int[] renderPixels = new int[0];
    private float[] renderDepthBuffer = new float[0];
    private int[] croppedPixels = new int[0];

    public xfeRoot(xfeGameGraphFactory factory) {
        this.factory = factory;
    }

    public boolean clear() {
        nodes.clear();
        loadedSceneName = null;
        sceneImageCandidates = Collections.emptyList();
        sceneShapes = Collections.emptyList();
        cachedSceneImage = null;
        cachedSceneImageName = null;
        return true;
    }

    public xfeGameGraphNodeIterator getNodes() {
        xfeGameGraphNodeIterator iterator = new xfeGameGraphNodeIterator();
        iterator.reset(nodes);
        return iterator;
    }

    public xfeClock getClock() {
        return clock;
    }

    public xfeClockAdvanceState doIteration() {
        long previousTick = clock.getTick();
        xfeClockAdvanceState state = clock.advance();
        if (state.didIteration()) {
            int tickDelta = (int) Math.max(1L, clock.getTick() - previousTick);
            tickControllers(tickDelta);
        }
        return state;
    }

    public void update() {
    }

    public int render(xfeCamera camera, xfRectangleInt rectangle, boolean clear) {
        if (xfeOGLContext.mGL instanceof Graphics graphics) {
            drawScene(graphics, rectangle, camera);
        }
        return nodes.size();
    }

    public xfeControllerSet getControllerSet() {
        return controllerSet;
    }

    public xfeResourceManager getResourceManager() {
        return resourceManager;
    }

    public xfeGameGraphFactory getFactory() {
        return factory;
    }

    public String getLoadedSceneName() {
        return loadedSceneName;
    }

    public void setSceneImageCandidates(List<String> sceneImageCandidates) {
        this.sceneImageCandidates = sceneImageCandidates == null ? Collections.emptyList() : new ArrayList<>(sceneImageCandidates);
        this.cachedSceneImage = null;
        this.cachedSceneImageName = null;
    }

    public void setSceneShapes(List<SceneShape> sceneShapes) {
        this.sceneShapes = sceneShapes == null ? Collections.emptyList() : new ArrayList<>(sceneShapes);
    }

    public void addNode(xfeNode node) {
        if (node == null) {
            return;
        }
        nodes.add(node);
        if (node.getParent() == null) {
            addChild(node);
        }
        if (node instanceof xfeController controller) {
            controllerSet.addController(controller);
        }
    }

    private void tickControllers(int tickDelta) {
        IdentityHashMap<xfeController, Boolean> seen = new IdentityHashMap<>();
        tickControllerSet(controllerSet, tickDelta, seen);
        for (xfeNode node : nodes) {
            if (node instanceof xfeActor actor) {
                tickControllerSet(actor.getControllerSet(), tickDelta, seen);
            }
        }
    }

    private static void tickControllerSet(xfeControllerSet set, int tickDelta, IdentityHashMap<xfeController, Boolean> seen) {
        if (set == null) {
            return;
        }
        for (xfeController controller : set.snapshot()) {
            if (controller == null || seen.put(controller, Boolean.TRUE) != null) {
                continue;
            }
            controller.onTick(tickDelta);
        }
    }

    public void setLoadedSceneName(String loadedSceneName) {
        this.loadedSceneName = loadedSceneName;
    }

    private void drawScene(Graphics graphics, xfRectangleInt rectangle, xfeCamera preferredCamera) {
        int width = rectangle != null && rectangle.mWidth > 0 ? rectangle.mWidth : Display.getWidth();
        int height = rectangle != null && rectangle.mHeight > 0 ? rectangle.mHeight : Display.getHeight();
        int offsetX = rectangle != null ? rectangle.mX : 0;
        int offsetY = rectangle != null ? rectangle.mY : 0;
        if (drawDecodedScene(graphics, preferredCamera, width, height, offsetX, offsetY)) {
            return;
        }
        Image image = resolveSceneImage(width, height);
        graphics.setColor(Graphics.BLACK);
        graphics.fillRect(0, 0, width, height);
        if (image != null) {
            int drawX = Math.max(0, (width - image.getWidth()) / 2);
            int drawY = Math.max(0, (height - image.getHeight()) / 2);
            graphics.drawImage(image, drawX, drawY);
        }
    }

    private Image resolveSceneImage(int viewportWidth, int viewportHeight) {
        if (cachedSceneImage != null) {
            return cachedSceneImage;
        }
        for (String candidate : sceneImageCandidates) {
            try {
                Image image = MediaManager.getImage("resource:///" + candidate).getImage();
                if (!coversViewport(image, viewportWidth, viewportHeight)) {
                    continue;
                }
                cachedSceneImage = image;
                cachedSceneImageName = candidate;
                return image;
            } catch (UIException ignored) {
                cachedSceneImageName = null;
            }
        }
        return null;
    }

    private boolean drawDecodedScene(Graphics graphics, xfeCamera preferredCamera, int viewportWidth, int viewportHeight,
                                     int viewportOffsetX, int viewportOffsetY) {
        if (sceneShapes.isEmpty() || viewportWidth <= 0 || viewportHeight <= 0) {
            return false;
        }

        xfeCamera camera = preferredCamera != null ? preferredCamera : findFirstCamera();
        if (camera == null) {
            return false;
        }

        Map<xfeNode, xfMatrix4> worldMatrixCache = new IdentityHashMap<>();
        Map<xfeNode, xfMatrix4> bindWorldCache = new IdentityHashMap<>();
        xfMatrix4 cameraWorld = worldMatrix(camera, worldMatrixCache);
        xfMatrix4 viewMatrix = new xfMatrix4(cameraWorld);
        xfMath.matrixInverseFast(viewMatrix);

        float fovRadians = (float) Math.toRadians(camera.getPreferredView().getFOV());
        int renderHeight = viewportHeight + Math.abs(viewportOffsetY);
        int renderWidth = viewportWidth + Math.abs(viewportOffsetX);
        int cropX = Math.max(0, -viewportOffsetX);
        int cropY = Math.max(0, -viewportOffsetY);
        float viewportAspect = renderWidth / (float) Math.max(1, renderHeight);
        float tanHalfFov = (float) Math.tan(Math.max(0.01f, fovRadians / 2f));
        float projectionScaleX = (renderWidth / 2f) / Math.max(0.01f, tanHalfFov * viewportAspect);
        float projectionScaleY = (renderHeight / 2f) / Math.max(0.01f, tanHalfFov);
        float near = camera.getPreferredView().getNearClip();
        float far = camera.getPreferredView().getFarClip();
        ensureRenderBuffers(renderWidth * renderHeight, viewportWidth * viewportHeight);
        int[] pixels = renderPixels;
        float[] depthBuffer = renderDepthBuffer;
        Arrays.fill(pixels, 0, renderWidth * renderHeight, 0xFF000000);
        Arrays.fill(depthBuffer, 0, renderWidth * renderHeight, Float.POSITIVE_INFINITY);
        boolean drewAnyTriangle = false;
        for (SceneShape shape : sceneShapes) {
            SceneGeometry geometry = shape.geometry();
            if (geometry == null || !isRenderable(shape.node())) {
                continue;
            }
            xfMatrix4 modelMatrix = worldMatrix(shape.node(), worldMatrixCache);
            xfMatrix4[] skinMatrices = prepareSkinMatrices(shape.skin(), worldMatrixCache, bindWorldCache);
            for (SceneBatch batch : shape.batches()) {
                int[] indices = batch.indices();
                if (indices == null || indices.length < 3) {
                    continue;
                }
                int start = Math.max(0, batch.indexStart());
                int end = batch.indexCount() > 0
                        ? Math.min(indices.length, start + batch.indexCount())
                        : indices.length;
                if (end - start < 3) {
                    continue;
                }
                TextureData texture = resolveTexture(batch.textureName());
                for (int index = start; index + 2 < end; index += 3) {
                    int vertex0 = indices[index];
                    int vertex1 = indices[index + 1];
                    int vertex2 = indices[index + 2];
                    if (!isValidVertex(vertex0, geometry) || !isValidVertex(vertex1, geometry) || !isValidVertex(vertex2, geometry)) {
                        continue;
                    }
                    ProjectedVertex v0 = projectVertex(shape, vertex0, modelMatrix, skinMatrices, viewMatrix,
                            projectionScaleX, projectionScaleY, near, far, renderWidth, renderHeight);
                    ProjectedVertex v1 = projectVertex(shape, vertex1, modelMatrix, skinMatrices, viewMatrix,
                            projectionScaleX, projectionScaleY, near, far, renderWidth, renderHeight);
                    ProjectedVertex v2 = projectVertex(shape, vertex2, modelMatrix, skinMatrices, viewMatrix,
                            projectionScaleX, projectionScaleY, near, far, renderWidth, renderHeight);
                    if (v0 == null || v1 == null || v2 == null) {
                        continue;
                    }
                    if (drawTriangle(pixels, depthBuffer, renderWidth, renderHeight, v0, v1, v2, texture, batch.tintColor())) {
                        drewAnyTriangle = true;
                    }
                }
            }
        }

        if (!drewAnyTriangle) {
            return false;
        }
        if (cropX == 0 && cropY == 0 && renderWidth == viewportWidth && renderHeight == viewportHeight) {
            graphics.setRGBPixels(0, 0, viewportWidth, viewportHeight, pixels, 0);
            return true;
        }
        int[] visiblePixels = croppedPixels;
        for (int y = 0; y < viewportHeight; y++) {
            int sourceIndex = ((y + cropY) * renderWidth) + cropX;
            int targetIndex = y * viewportWidth;
            System.arraycopy(pixels, sourceIndex, visiblePixels, targetIndex, viewportWidth);
        }
        graphics.setRGBPixels(0, 0, viewportWidth, viewportHeight, visiblePixels, 0);
        return true;
    }

    private void ensureRenderBuffers(int renderPixelsLength, int croppedPixelsLength) {
        if (renderPixels.length < renderPixelsLength) {
            renderPixels = new int[renderPixelsLength];
        }
        if (renderDepthBuffer.length < renderPixelsLength) {
            renderDepthBuffer = new float[renderPixelsLength];
        }
        if (croppedPixels.length < croppedPixelsLength) {
            croppedPixels = new int[croppedPixelsLength];
        }
    }

    private xfeCamera findFirstCamera() {
        for (xfeNode node : nodes) {
            if (node instanceof xfeCamera camera) {
                return camera;
            }
        }
        return null;
    }

    private xfMatrix4 worldMatrix(xfeNode node, Map<xfeNode, xfMatrix4> cache) {
        if (node == null) {
            return new xfMatrix4();
        }
        xfMatrix4 cached = cache.get(node);
        if (cached != null) {
            return cached;
        }
        xfMatrix4 result;
        xfeNode parent = node.getParent();
        if (parent == null) {
            result = new xfMatrix4(localMatrix(node));
        } else {
            result = new xfMatrix4(worldMatrix(parent, cache));
            result.mul(localMatrix(node));
        }
        cache.put(node, result);
        return result;
    }

    private static boolean isValidVertex(int index, SceneGeometry geometry) {
        return geometry != null && index >= 0 && index < geometry.vertexCount();
    }

    private static boolean isRenderable(xfeNode node) {
        xfeNode current = node;
        while (current != null) {
            if (current instanceof xfeActor actor && !actor.isActive()) {
                return false;
            }
            current = current.getParent();
        }
        return true;
    }

    private static xfMatrix4 localMatrix(xfeNode node) {
        if (node instanceof xfeActor actor) {
            return actor.getTransformation().getInternalMatrix();
        }
        if (node instanceof xfeGroup group) {
            return group.getTransformation().getInternalMatrix();
        }
        return new xfMatrix4();
    }

    private xfMatrix4[] prepareSkinMatrices(SceneSkin skin, Map<xfeNode, xfMatrix4> worldMatrixCache,
                                            Map<xfeNode, xfMatrix4> bindWorldCache) {
        if (skin == null || skin.joints() == null || skin.joints().length == 0) {
            return null;
        }
        xfMatrix4[] skinMatrices = new xfMatrix4[skin.joints().length];
        for (int jointIndex = 1; jointIndex < skin.joints().length; jointIndex++) {
            xfeNode joint = skin.joints()[jointIndex];
            xfMatrix4 bindLocal = skin.bindLocalMatrices()[jointIndex];
            if (joint == null || bindLocal == null) {
                continue;
            }
            xfMatrix4 currentWorld = new xfMatrix4(worldMatrix(joint, worldMatrixCache));
            xfMatrix4 bindWorld = bindWorldMatrix(joint, skin.bindLocalsByNode(), bindWorldCache);
            xfMath.matrixInverseFast(bindWorld);
            currentWorld.mul(bindWorld);
            skinMatrices[jointIndex] = currentWorld;
        }
        return skinMatrices;
    }

    private xfMatrix4 bindWorldMatrix(xfeNode node, Map<xfeNode, xfMatrix4> bindLocalsByNode,
                                      Map<xfeNode, xfMatrix4> cache) {
        if (node == null) {
            return new xfMatrix4();
        }
        xfMatrix4 cached = cache.get(node);
        if (cached != null) {
            return new xfMatrix4(cached);
        }
        xfMatrix4 local = bindLocalsByNode.get(node);
        xfMatrix4 result;
        xfeNode parent = node.getParent();
        if (parent == null) {
            result = local == null ? new xfMatrix4(localMatrix(node)) : new xfMatrix4(local);
        } else {
            result = bindWorldMatrix(parent, bindLocalsByNode, cache);
            result.mul(local == null ? localMatrix(node) : local);
        }
        cache.put(node, new xfMatrix4(result));
        return result;
    }

    private ProjectedVertex projectVertex(SceneShape shape, int index, xfMatrix4 modelMatrix, xfMatrix4[] skinMatrices,
                                          xfMatrix4 viewMatrix, float projectionScaleX, float projectionScaleY,
                                          float near, float far, int viewportWidth, int viewportHeight) {
        SceneGeometry geometry = shape.geometry();
        float localX = geometry.positionX()[index];
        float localY = geometry.positionY()[index];
        float localZ = geometry.positionZ()[index];
        float worldX = transformX(modelMatrix, localX, localY, localZ);
        float worldY = transformY(modelMatrix, localX, localY, localZ);
        float worldZ = transformZ(modelMatrix, localX, localY, localZ);
        if (skinMatrices != null && geometry.jointIndices() != null && index < geometry.jointIndices().length) {
            SkinnedPosition skinnedPosition = applySkin(worldX, worldY, worldZ, geometry, index, skinMatrices);
            worldX = skinnedPosition.x();
            worldY = skinnedPosition.y();
            worldZ = skinnedPosition.z();
        }
        float cameraX = transformX(viewMatrix, worldX, worldY, worldZ);
        float cameraY = transformY(viewMatrix, worldX, worldY, worldZ);
        float cameraZ = transformZ(viewMatrix, worldX, worldY, worldZ);
        float depth = -cameraZ;
        if (depth < near || depth > far) {
            return null;
        }
        float screenX = (viewportWidth / 2f) + (cameraX * projectionScaleX / Math.max(0.0001f, depth));
        float screenY = (viewportHeight / 2f) - (cameraY * projectionScaleY / Math.max(0.0001f, depth));
        return new ProjectedVertex(screenX, screenY, depth, geometry.u()[index], geometry.v()[index]);
    }

    private static SkinnedPosition applySkin(float worldX, float worldY, float worldZ, SceneGeometry geometry, int index,
                                             xfMatrix4[] skinMatrices) {
        int[] jointIndices = geometry.jointIndices()[index];
        float[] jointWeights = geometry.jointWeights()[index];
        if (jointIndices == null || jointWeights == null || jointIndices.length == 0) {
            return new SkinnedPosition(worldX, worldY, worldZ);
        }
        float skinnedX = 0f;
        float skinnedY = 0f;
        float skinnedZ = 0f;
        float totalWeight = 0f;
        for (int influence = 0; influence < jointIndices.length && influence < jointWeights.length; influence++) {
            int jointIndex = jointIndices[influence];
            if (jointIndex <= 0 || jointIndex >= skinMatrices.length) {
                continue;
            }
            xfMatrix4 skinMatrix = skinMatrices[jointIndex];
            if (skinMatrix == null) {
                continue;
            }
            float weight = jointWeights[influence];
            skinnedX += transformX(skinMatrix, worldX, worldY, worldZ) * weight;
            skinnedY += transformY(skinMatrix, worldX, worldY, worldZ) * weight;
            skinnedZ += transformZ(skinMatrix, worldX, worldY, worldZ) * weight;
            totalWeight += weight;
        }
        if (totalWeight <= 0f) {
            return new SkinnedPosition(worldX, worldY, worldZ);
        }
        if (Math.abs(totalWeight - 1f) > 0.001f) {
            skinnedX /= totalWeight;
            skinnedY /= totalWeight;
            skinnedZ /= totalWeight;
        }
        return new SkinnedPosition(skinnedX, skinnedY, skinnedZ);
    }

    private static float transformX(xfMatrix4 matrix, float x, float y, float z) {
        return (matrix.m[0][0] * x) + (matrix.m[0][1] * y) + (matrix.m[0][2] * z) + matrix.m[0][3];
    }

    private static float transformY(xfMatrix4 matrix, float x, float y, float z) {
        return (matrix.m[1][0] * x) + (matrix.m[1][1] * y) + (matrix.m[1][2] * z) + matrix.m[1][3];
    }

    private static float transformZ(xfMatrix4 matrix, float x, float y, float z) {
        return (matrix.m[2][0] * x) + (matrix.m[2][1] * y) + (matrix.m[2][2] * z) + matrix.m[2][3];
    }

    private boolean drawTriangle(int[] pixels, float[] depthBuffer, int viewportWidth, int viewportHeight,
                                 ProjectedVertex v0, ProjectedVertex v1, ProjectedVertex v2,
                                 TextureData texture, int tintColor) {
        float area = edge(v0.screenX(), v0.screenY(), v1.screenX(), v1.screenY(), v2.screenX(), v2.screenY());
        if (Math.abs(area) < 0.0001f) {
            return false;
        }
        int minX = Math.max(0, (int) Math.floor(Math.min(v0.screenX(), Math.min(v1.screenX(), v2.screenX()))));
        int maxX = Math.min(viewportWidth - 1, (int) Math.ceil(Math.max(v0.screenX(), Math.max(v1.screenX(), v2.screenX()))));
        int minY = Math.max(0, (int) Math.floor(Math.min(v0.screenY(), Math.min(v1.screenY(), v2.screenY()))));
        int maxY = Math.min(viewportHeight - 1, (int) Math.ceil(Math.max(v0.screenY(), Math.max(v1.screenY(), v2.screenY()))));
        if (minX > maxX || minY > maxY) {
            return false;
        }

        boolean drew = false;
        float invArea = 1f / area;
        float invDepth0 = 1f / v0.depth();
        float invDepth1 = 1f / v1.depth();
        float invDepth2 = 1f / v2.depth();
        for (int y = minY; y <= maxY; y++) {
            float sampleY = y + 0.5f;
            for (int x = minX; x <= maxX; x++) {
                float sampleX = x + 0.5f;
                float w0 = edge(v1.screenX(), v1.screenY(), v2.screenX(), v2.screenY(), sampleX, sampleY) * invArea;
                float w1 = edge(v2.screenX(), v2.screenY(), v0.screenX(), v0.screenY(), sampleX, sampleY) * invArea;
                float w2 = edge(v0.screenX(), v0.screenY(), v1.screenX(), v1.screenY(), sampleX, sampleY) * invArea;
                if (w0 < 0f || w1 < 0f || w2 < 0f) {
                    continue;
                }
                float interpolatedInvDepth = (w0 * invDepth0) + (w1 * invDepth1) + (w2 * invDepth2);
                if (interpolatedInvDepth <= 0f) {
                    continue;
                }
                float depth = 1f / interpolatedInvDepth;
                int pixelIndex = (y * viewportWidth) + x;
                if (depth >= depthBuffer[pixelIndex]) {
                    continue;
                }
                float u = ((w0 * v0.u() * invDepth0) + (w1 * v1.u() * invDepth1) + (w2 * v2.u() * invDepth2)) / interpolatedInvDepth;
                float v = ((w0 * v0.v() * invDepth0) + (w1 * v1.v() * invDepth1) + (w2 * v2.v() * invDepth2)) / interpolatedInvDepth;
                int sourceColor = sampleTexture(texture, u, v);
                int color = multiplyColor(sourceColor, tintColor);
                if (((color >>> 24) & 0xFF) == 0) {
                    continue;
                }
                pixels[pixelIndex] = blend(color, pixels[pixelIndex]);
                depthBuffer[pixelIndex] = depth;
                drew = true;
            }
        }
        return drew;
    }

    private TextureData resolveTexture(String textureName) {
        if (textureName == null || textureName.isBlank()) {
            return null;
        }
        TextureData cached = textureCache.get(textureName);
        if (cached != null) {
            return cached;
        }
        try {
            Image image = MediaManager.getImage("resource:///" + textureName).getImage();
            Graphics graphics = image.getGraphics();
            int width = image.getWidth();
            int height = image.getHeight();
            TextureData loaded = new TextureData(width, height, graphics.getRGBPixels(0, 0, width, height, null, 0));
            textureCache.put(textureName, loaded);
            return loaded;
        } catch (Exception ignored) {
            textureCache.put(textureName, TextureData.MISSING);
            return null;
        }
    }

    private static int sampleTexture(TextureData texture, float u, float v) {
        if (texture == null || texture == TextureData.MISSING) {
            return DEFAULT_TINT;
        }
        int x = clamp(Math.round(u * (texture.width() - 1)), 0, texture.width() - 1);
        int y = clamp(Math.round(v * (texture.height() - 1)), 0, texture.height() - 1);
        return texture.pixels()[(y * texture.width()) + x];
    }

    private static int multiplyColor(int left, int right) {
        int leftA = (left >>> 24) & 0xFF;
        int leftR = (left >>> 16) & 0xFF;
        int leftG = (left >>> 8) & 0xFF;
        int leftB = left & 0xFF;
        int rightA = (right >>> 24) & 0xFF;
        int rightR = (right >>> 16) & 0xFF;
        int rightG = (right >>> 8) & 0xFF;
        int rightB = right & 0xFF;
        int a = (leftA * rightA) / 255;
        int r = (leftR * rightR) / 255;
        int g = (leftG * rightG) / 255;
        int b = (leftB * rightB) / 255;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int blend(int source, int destination) {
        int sourceA = (source >>> 24) & 0xFF;
        if (sourceA >= 255) {
            return source;
        }
        int destinationA = (destination >>> 24) & 0xFF;
        int inverseA = 255 - sourceA;
        int outA = sourceA + ((destinationA * inverseA) / 255);
        int outR = (((source >>> 16) & 0xFF) * sourceA + ((destination >>> 16) & 0xFF) * inverseA) / 255;
        int outG = (((source >>> 8) & 0xFF) * sourceA + ((destination >>> 8) & 0xFF) * inverseA) / 255;
        int outB = ((source & 0xFF) * sourceA + (destination & 0xFF) * inverseA) / 255;
        return (outA << 24) | (outR << 16) | (outG << 8) | outB;
    }

    private static float edge(float ax, float ay, float bx, float by, float px, float py) {
        return ((px - ax) * (by - ay)) - ((py - ay) * (bx - ax));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public record SceneGeometry(int format, int vertexCount, float[] positionX, float[] positionY, float[] positionZ,
                                float[] u, float[] v, int[][] jointIndices, float[][] jointWeights) {
    }

    public record SceneBatch(int[] indices, int indexStart, int indexCount, String textureName, int tintColor) {
    }

    public record SceneShape(xfeNode node, SceneGeometry geometry, List<SceneBatch> batches, SceneSkin skin) {
    }

    public record SceneSkin(xfeNode[] joints, xfMatrix4[] bindLocalMatrices, Map<xfeNode, xfMatrix4> bindLocalsByNode) {
    }

    private record ProjectedVertex(float screenX, float screenY, float depth, float u, float v) {
    }

    private record SkinnedPosition(float x, float y, float z) {
    }

    private record TextureData(int width, int height, int[] pixels) {
        private static final TextureData MISSING = new TextureData(0, 0, new int[0]);
    }

    private static boolean coversViewport(Image image, int viewportWidth, int viewportHeight) {
        if (image == null) {
            return false;
        }
        return image.getWidth() >= viewportWidth && image.getHeight() >= viewportHeight;
    }

    private static final class BasicResourceManager implements xfeResourceManager {
        private final Map<String, Integer> idsByName = new LinkedHashMap<>();
        private final Map<Integer, xfeResource> resources = new LinkedHashMap<>();
        private int nextId = 1;

        @Override
        public int getResourceId(String name) {
            Integer id = idsByName.get(name);
            if (id != null) {
                return id;
            }
            int next = nextId++;
            idsByName.put(name, next);
            return next;
        }

        @Override
        public xfeResource getResource(int id) {
            return resources.get(id);
        }

        @Override
        public void addResource(int id, xfeResource resource) {
            resources.put(id, resource);
        }
    }
}
