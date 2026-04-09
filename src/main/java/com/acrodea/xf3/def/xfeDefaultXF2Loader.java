package com.acrodea.xf3.def;

import com.acrodea.xf3.xfeActor;
import com.acrodea.xf3.xfeCamera;
import com.acrodea.xf3.xfeController;
import com.acrodea.xf3.xfeGroup;
import com.acrodea.xf3.xfeMatrixTransformation;
import com.acrodea.xf3.xfeNode;
import com.acrodea.xf3.xfeNodeList;
import com.acrodea.xf3.xfePRSAnimationController;
import com.acrodea.xf3.xfeParameterDataSet;
import com.acrodea.xf3.xfeRoot;
import com.acrodea.xf3.xfeZone;
import com.acrodea.xf3.math.xfMatrix4;
import com.acrodea.xf3.loader.xfeXF2ChunkLoader;
import com.acrodea.xf3.loader.xfeXF2Context;
import com.acrodea.xf3.loader.xfeXF2ParameterizedControllerLoader;
import com.acrodea.xf3.loader.xfeXF2Reader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class xfeDefaultXF2Loader {
    private static final int RECORD_LOADER_NODE = 1;
    private static final int RECORD_ZONE = 100;
    private static final int RECORD_ACTOR = 101;
    private static final int RECORD_NODE = 103;
    private static final int RECORD_LIGHT_SHAPE = 106;
    private static final int RECORD_CAMERA_SHAPE = 107;
    private static final int RECORD_SHAPE = 108;
    private static final int RECORD_GEOMETRY = 200;
    private static final int RECORD_BATCH = 201;
    private static final int RECORD_MATERIAL = 203;
    private static final int RECORD_PRS_REFERENCE = 302;
    private static final int RECORD_PARAMETERIZED_CONTROLLER = 303;
    private static final int RECORD_SKIN = 304;
    private static final int RECORD_RESOURCE_NAME = 204;

    private final Map<Integer, xfeXF2ChunkLoader> chunkLoaders = new ConcurrentHashMap<>();
    private String errorMessage = "";

    public boolean registerChunkLoader(int chunkType, xfeXF2ChunkLoader chunkLoader) {
        chunkLoaders.put(chunkType, chunkLoader);
        return true;
    }

    public boolean load(String path, xfeRoot root, xfeNodeList nodeList, xfeXF2Context context, xfeXF2Reader reader) {
        if (root == null || path == null || path.isBlank()) {
            errorMessage = "Missing XF2 root or path";
            return false;
        }
        root.clear();
        root.setLoadedSceneName(path.trim());
        ParseState state = new ParseState(root, nodeList);
        try {
            loadScene(path.trim(), state);
        } catch (IOException e) {
            errorMessage = e.getMessage();
            return false;
        }
        root.setSceneImageCandidates(new ArrayList<>(state.sceneImageCandidates));
        root.setSceneShapes(new ArrayList<>(state.sceneShapes));
        errorMessage = "";
        return true;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    private void loadScene(String path, ParseState state) throws IOException {
        if (!state.loadedPaths.add(path)) {
            return;
        }
        byte[] data = readResource(path);
        if (data == null) {
            throw new IOException("Resource not found: " + path);
        }

        List<XF2Record> records = parseRecords(data);
        Map<Integer, xfeNode> nodesByRecord = new LinkedHashMap<>();
        Map<Integer, xfeNode> nodesByRef = new LinkedHashMap<>();
        Map<Integer, GeometryRecord> geometriesByParentId = new LinkedHashMap<>();
        Map<Integer, SliceRecord> slicesByParentId = new LinkedHashMap<>();
        Map<Integer, SkinRecord> skinsByShapeRef = new LinkedHashMap<>();
        Map<Integer, Integer> tintByParentId = new LinkedHashMap<>();
        Map<Integer, String> texturesByParentId = new LinkedHashMap<>();
        List<ShapeRecord> shapes = new ArrayList<>();
        List<ParameterizedControllerRecord> controllers = new ArrayList<>();
        List<String> referencedScenes = new ArrayList<>();
        xfeCamera lastCamera = null;

        for (int recordIndex = 0; recordIndex < records.size(); recordIndex++) {
            XF2Record record = records.get(recordIndex);
            switch (record.type) {
                case RECORD_ZONE -> {
                    String name = readCountedString(record.payload, 0);
                    if (name != null) {
                        xfeZone zone = new xfeZone(name);
                        registerNode(state, nodesByRecord, nodesByRef, recordIndex, record.parent, zone, -1);
                        state.zones.add(zone);
                        for (xfeActor actor : state.actors) {
                            actor.addZone(zone);
                        }
                    }
                }
                case RECORD_ACTOR -> {
                    String name = readCountedString(record.payload, 4);
                    if (name != null) {
                        NodeTransform transform = parseNodeTransform(record.payload, 4);
                        xfeActor actor = new xfeActor(name);
                        applyLocalTransform(actor, transform);
                        for (xfeZone zone : state.zones) {
                            actor.addZone(zone);
                        }
                        registerNode(state, nodesByRecord, nodesByRef, recordIndex, record.parent, actor, readInt(record.payload, 0));
                        state.actors.add(actor);
                        state.transformsByName.put(name, transform);
                    }
                }
                case RECORD_NODE -> {
                    String name = readCountedString(record.payload, 4);
                    if (name != null) {
                        NodeTransform transform = parseNodeTransform(record.payload, 4);
                        xfeNode node = createNode(name);
                        applyLocalTransform(node, transform);
                        registerNode(state, nodesByRecord, nodesByRef, recordIndex, record.parent, node, readInt(record.payload, 0));
                        state.transformsByName.put(name, transform);
                        if (node instanceof xfeCamera camera) {
                            lastCamera = camera;
                        }
                    }
                }
                case RECORD_LIGHT_SHAPE -> {
                }
                case RECORD_CAMERA_SHAPE -> {
                    CameraShapeRecord cameraShape = parseCameraShape(record.payload);
                    xfeCamera camera = resolveCamera(cameraShape, state, lastCamera);
                    if (camera != null && cameraShape != null) {
                        applyCameraShape(camera, cameraShape);
                    }
                }
                case 301 -> {
                    String name = readCountedString(record.payload, 8);
                    NodeTransform baseTransform = name == null ? null : state.transformsByName.get(name);
                    NodeTransform overrideTransform = parseTerminalScaleOverride(record.payload, baseTransform);
                    if (name != null && overrideTransform != null) {
                        state.transformsByName.put(name, overrideTransform);
                        xfeNode node = state.nodesByName.get(name);
                        applyLocalTransform(node, overrideTransform);
                    }
                }
                case RECORD_SHAPE -> {
                    ShapeRecord shape = parseShapeRecord(record.parent, record.payload);
                    if (shape != null) {
                        shapes.add(shape);
                    }
                }
                case RECORD_LOADER_NODE -> {
                    String scenePath = readTrailingCountedString(record.payload);
                    if (scenePath != null && scenePath.endsWith(".xf2")) {
                        referencedScenes.add(resolveReferencedPath(path, scenePath));
                    }
                }
                case RECORD_GEOMETRY -> {
                    GeometryRecord geometry = parseGeometry(record.payload);
                    if (geometry != null) {
                        geometriesByParentId.put(record.parent, geometry);
                    }
                }
                case RECORD_BATCH -> {
                    SliceRecord slice = parseSlice(record.payload);
                    if (slice != null) {
                        slicesByParentId.put(record.parent, slice);
                    }
                }
                case RECORD_MATERIAL -> tintByParentId.put(record.parent, readRgbaColor(record.payload, 0));
                case RECORD_PRS_REFERENCE -> {
                    String name = readCountedString(record.payload, 4);
                    if (name != null) {
                        addNode(state.root, state.nodeList, new xfePRSAnimationController(state.root, name));
                    }
                }
                case RECORD_PARAMETERIZED_CONTROLLER -> {
                    ParameterizedControllerRecord controller = parseParameterizedController(record.payload);
                    if (controller != null) {
                        controllers.add(controller);
                    }
                }
                case RECORD_SKIN -> {
                    SkinRecord skin = parseSkinRecord(record.payload);
                    if (skin != null) {
                        skinsByShapeRef.put(skin.shapeRef(), skin);
                    }
                }
                case RECORD_RESOURCE_NAME -> {
                    String resourceName = readCountedString(record.payload, 0);
                    if (isImageResource(resourceName)) {
                        state.sceneImageCandidates.add(resourceName);
                        texturesByParentId.put(record.parent, resourceName);
                    }
                }
                default -> {
                }
            }
        }

        for (ShapeRecord shape : shapes) {
            xfeNode node = nodesByRef.get(shape.nodeRef());
            xfeRoot.SceneSkin sceneSkin = buildSceneSkin(skinsByShapeRef.get(shape.shapeRef()), nodesByRef);
            GeometryRecord lastGeometry = null;
            String lastTexture = null;
            int lastTint = 0xFFFFFFFF;
            List<xfeRoot.SceneBatch> batches = new ArrayList<>();
            for (ShapeBatchRecord batch : shape.batches()) {
                if (batch.geometryRef() >= 0) {
                    lastGeometry = geometriesByParentId.get(batch.geometryRef());
                }
                if (batch.textureRef() >= 0) {
                    lastTexture = texturesByParentId.get(batch.textureRef());
                }
                if (batch.tintRef() >= 0) {
                    lastTint = tintByParentId.getOrDefault(batch.tintRef(), lastTint);
                }
                SliceRecord slice = batch.sliceRef() < 0 ? null : slicesByParentId.get(batch.sliceRef());
                if (node == null || lastGeometry == null || slice == null) {
                    continue;
                }
                xfeRoot.SceneGeometry geometry = decodeGeometry(lastGeometry);
                if (geometry == null) {
                    continue;
                }
                batches.add(new xfeRoot.SceneBatch(
                        slice.indices(),
                        batch.indexStart(),
                        batch.indexCount() > 0 ? batch.indexCount() : slice.indices().length,
                        lastTexture,
                        lastTint));
                state.sceneShapes.add(new xfeRoot.SceneShape(node, geometry, new ArrayList<>(batches), sceneSkin));
                batches.clear();
            }
        }

        for (String referencedScene : referencedScenes) {
            loadScene(referencedScene, state);
        }

        for (ParameterizedControllerRecord controller : controllers) {
            xfeNode node = state.nodesByName.get(controller.nodeName);
            if (node == null) {
                continue;
            }
            xfeXF2ChunkLoader chunkLoader = chunkLoaders.get(RECORD_PARAMETERIZED_CONTROLLER);
            if (!(chunkLoader instanceof xfeXF2ParameterizedControllerLoader parameterizedLoader)) {
                continue;
            }
            xfeController created = parameterizedLoader.getFactory()
                    .createController(controller.parameters, state.root, controller.nodeName, node);
            if (created != null) {
                addNode(state.root, state.nodeList, created);
            }
        }
    }

    private static void registerNode(ParseState state, Map<Integer, xfeNode> nodesByRecord, Map<Integer, xfeNode> nodesByRef,
                                     int recordIndex, int nodeRef, xfeNode node, int parentRef) {
        if (parentRef >= 0) {
            xfeNode parent = nodesByRef.get(parentRef);
            if (parent == null) {
                parent = nodesByRecord.get(parentRef);
            }
            if (parent instanceof xfeGroup group) {
                group.addChild(node);
            }
        }
        addNode(state.root, state.nodeList, node);
        nodesByRecord.put(recordIndex, node);
        nodesByRef.put(nodeRef, node);
        String name = node.getName();
        if (name != null && !name.isEmpty()) {
            state.nodesByName.put(name, node);
        }
    }

    private static xfeNode createNode(String name) {
        if (name.startsWith("camera")) {
            return new xfeCamera(name);
        }
        return new xfeGroup(name);
    }

    private static void applyLocalTransform(xfeNode node, NodeTransform transform) {
        if (node == null || transform == null) {
            return;
        }
        xfMatrix4 matrix = toMatrix(transform);
        xfeMatrixTransformation transformation = new xfeMatrixTransformation();
        transformation.setInternalTransformation(matrix, 0);
        if (node instanceof xfeActor actor) {
            actor.setLocalTransformation(transformation);
        } else if (node instanceof xfeGroup group) {
            group.getTransformation().setInternalTransformation(matrix, 0);
        }
    }

    private static NodeTransform parseNodeTransform(byte[] payload, int nameOffset) {
        if (payload == null || nameOffset < 0 || nameOffset >= payload.length) {
            return null;
        }
        String name = readCountedString(payload, nameOffset);
        if (name == null) {
            return null;
        }
        int offset = nameOffset + 1 + name.length();
        return parseTransform(payload, offset);
    }

    private static NodeTransform parseTransform(byte[] payload, int offset) {
        if (payload == null || offset < 0 || offset + 40 > payload.length) {
            return null;
        }
        return new NodeTransform(
                readFloat(payload, offset),
                readFloat(payload, offset + 4),
                readFloat(payload, offset + 8),
                readFloat(payload, offset + 12),
                readFloat(payload, offset + 16),
                readFloat(payload, offset + 20),
                readFloat(payload, offset + 24),
                readFloat(payload, offset + 28),
                readFloat(payload, offset + 32),
                readFloat(payload, offset + 36));
    }

    private static xfMatrix4 toMatrix(NodeTransform transform) {
        xfMatrix4 matrix = new xfMatrix4();
        float qx = transform.qx();
        float qy = transform.qy();
        float qz = transform.qz();
        float qw = transform.qw();
        float lengthSquared = (qx * qx) + (qy * qy) + (qz * qz) + (qw * qw);
        if (lengthSquared > 0.000001f) {
            float inverseLength = 1f / (float) Math.sqrt(lengthSquared);
            qx *= inverseLength;
            qy *= inverseLength;
            qz *= inverseLength;
            qw *= inverseLength;
        } else {
            qx = 0f;
            qy = 0f;
            qz = 0f;
            qw = 1f;
        }
        float xx = qx * qx;
        float yy = qy * qy;
        float zz = qz * qz;
        float xy = qx * qy;
        float xz = qx * qz;
        float yz = qy * qz;
        float wx = qw * qx;
        float wy = qw * qy;
        float wz = qw * qz;

        matrix.m[0][0] = (1f - (2f * (yy + zz))) * transform.scaleX();
        matrix.m[0][1] = (2f * (xy - wz)) * transform.scaleY();
        matrix.m[0][2] = (2f * (xz + wy)) * transform.scaleZ();
        matrix.m[1][0] = (2f * (xy + wz)) * transform.scaleX();
        matrix.m[1][1] = (1f - (2f * (xx + zz))) * transform.scaleY();
        matrix.m[1][2] = (2f * (yz - wx)) * transform.scaleZ();
        matrix.m[2][0] = (2f * (xz - wy)) * transform.scaleX();
        matrix.m[2][1] = (2f * (yz + wx)) * transform.scaleY();
        matrix.m[2][2] = (1f - (2f * (xx + yy))) * transform.scaleZ();
        matrix.m[0][3] = transform.x();
        matrix.m[1][3] = transform.y();
        matrix.m[2][3] = transform.z();
        matrix.m[3][0] = 0f;
        matrix.m[3][1] = 0f;
        matrix.m[3][2] = 0f;
        matrix.m[3][3] = 1f;
        return matrix;
    }

    private static void addNode(xfeRoot root, xfeNodeList nodeList, xfeNode node) {
        root.addNode(node);
        if (nodeList != null) {
            nodeList.add(node);
        }
    }

    private static List<XF2Record> parseRecords(byte[] data) throws IOException {
        List<XF2Record> records = new ArrayList<>();
        int offset = 0;
        while (offset + 12 <= data.length) {
            int type = readUnsignedShort(data, offset);
            int id = readUnsignedShort(data, offset + 2);
            int parent = readInt(data, offset + 4);
            int size = readInt(data, offset + 8);
            int end = offset + 12 + size;
            if (size < 0 || end > data.length) {
                throw new IOException("Malformed XF2 record at offset " + offset);
            }
            byte[] payload = new byte[size];
            System.arraycopy(data, offset + 12, payload, 0, size);
            records.add(new XF2Record(type, id, parent, payload));
            offset = end;
        }
        if (offset != data.length) {
            throw new IOException("Trailing XF2 data at offset " + offset);
        }
        return records;
    }

    private static ParameterizedControllerRecord parseParameterizedController(byte[] payload) {
        String nodeName = readCountedString(payload, 8);
        if (nodeName == null) {
            return null;
        }
        int offset = 8 + 1 + nodeName.length();
        int count = readInt(payload, offset);
        offset += 4;
        xfeParameterDataSet parameters = new xfeParameterDataSet();
        for (int i = 0; i < count && offset < payload.length; i++) {
            if (payload[offset] == 0x24) {
                offset++;
            }
            String key = readCountedString(payload, offset);
            if (key == null) {
                return null;
            }
            offset += 1 + key.length();
            String value = readCountedString(payload, offset);
            if (value == null) {
                return null;
            }
            offset += 1 + value.length();
            parameters.putString(key, value);
        }
        return new ParameterizedControllerRecord(nodeName, parameters);
    }

    private static ShapeRecord parseShapeRecord(int shapeRef, byte[] payload) {
        if (payload.length < 10) {
            return null;
        }
        int nodeRef = readInt(payload, 0);
        String name = readCountedString(payload, 4);
        if (name == null) {
            return null;
        }
        int offset = 5 + name.length();
        if (offset + 4 > payload.length) {
            return null;
        }
        int batchCount = readInt(payload, offset);
        offset += 4;
        List<ShapeBatchRecord> batches = new ArrayList<>();
        for (int i = 0; i < batchCount && offset + 45 <= payload.length; i++) {
            ShapeBatchRecord batch = parseShapeBatchRecord(payload, offset);
            if (batch != null) {
                batches.add(batch);
            }
            offset += 45;
        }
        return batches.isEmpty() ? null : new ShapeRecord(shapeRef, nodeRef, name, batches);
    }

    private static NodeTransform parseTerminalScaleOverride(byte[] payload, NodeTransform baseTransform) {
        if (payload == null || baseTransform == null) {
            return null;
        }
        Float scaleX = findTerminalComponentValue(payload, baseTransform.scaleX());
        Float scaleY = findTerminalComponentValue(payload, baseTransform.scaleY());
        Float scaleZ = findTerminalComponentValue(payload, baseTransform.scaleZ());
        if (scaleX == null && scaleY == null && scaleZ == null) {
            return null;
        }
        return new NodeTransform(
                baseTransform.x(),
                baseTransform.y(),
                baseTransform.z(),
                baseTransform.qx(),
                baseTransform.qy(),
                baseTransform.qz(),
                baseTransform.qw(),
                scaleX == null ? baseTransform.scaleX() : scaleX,
                scaleY == null ? baseTransform.scaleY() : scaleY,
                scaleZ == null ? baseTransform.scaleZ() : scaleZ);
    }

    // Type 301 clearly carries animated PRS state. Until its packed curve section is fully decoded,
    // recover only the plain terminal scale envelopes that are stored verbatim near the tail.
    private static Float findTerminalComponentValue(byte[] payload, float baseValue) {
        if (!(baseValue > 0f)) {
            return null;
        }
        byte[] baseBytes = floatToBytes(baseValue);
        for (int offset = payload.length - 8; offset >= 0; offset--) {
            if (!matches(payload, offset, baseBytes)) {
                continue;
            }
            float delta = readFloat(payload, offset + 4);
            float candidate = baseValue + delta;
            if (delta > 0f && candidate > baseValue && candidate < 256f) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean matches(byte[] payload, int offset, byte[] pattern) {
        if (offset < 0 || offset + pattern.length > payload.length) {
            return false;
        }
        for (int index = 0; index < pattern.length; index++) {
            if (payload[offset + index] != pattern[index]) {
                return false;
            }
        }
        return true;
    }

    private static byte[] floatToBytes(float value) {
        int bits = Float.floatToIntBits(value);
        return new byte[]{
                (byte) (bits & 0xFF),
                (byte) ((bits >>> 8) & 0xFF),
                (byte) ((bits >>> 16) & 0xFF),
                (byte) ((bits >>> 24) & 0xFF)};
    }

    private static ShapeBatchRecord parseShapeBatchRecord(byte[] payload, int offset) {
        if (offset + 45 > payload.length) {
            return null;
        }
        int valuesOffset = offset + 9;
        int tintRef = readInt(payload, valuesOffset + 4);
        int textureRef = readInt(payload, valuesOffset + 8);
        int geometryRef = readInt(payload, valuesOffset + 20);
        int sliceRef = readInt(payload, valuesOffset + 24);
        int indexStart = readInt(payload, valuesOffset + 28);
        int indexCount = readInt(payload, valuesOffset + 32);
        return new ShapeBatchRecord(
                normalizeRef(tintRef),
                normalizeRef(textureRef),
                geometryRef,
                sliceRef,
                Math.max(0, indexStart),
                Math.max(0, indexCount));
    }

    private static int normalizeRef(int value) {
        return value < 0 ? -1 : value;
    }

    private static GeometryRecord parseGeometry(byte[] payload) {
        if (payload.length < 9) {
            return null;
        }
        int format = readInt(payload, 0);
        int count = readInt(payload, 4);
        if (count <= 0) {
            return null;
        }
        return new GeometryRecord(format, count, payload);
    }

    private static SliceRecord parseSlice(byte[] payload) {
        if (payload.length < 9) {
            return null;
        }
        int count = readInt(payload, 0);
        int offset = readInt(payload, 4);
        if (count < 0) {
            return null;
        }
        int bits = payload[8] & 0xFF;
        int size = ((count * bits) + 7) / 8;
        if (9 + size > payload.length) {
            return null;
        }
        int[] indices = new int[count];
        for (int index = 0; index < count; index++) {
            int value = bits <= 0 ? 0 : readPackedBitsMsb(payload, 9, index * bits, bits);
            indices[index] = offset + value;
        }
        return new SliceRecord(count, offset, indices);
    }

    private static xfeRoot.SceneGeometry decodeGeometry(GeometryRecord geometry) {
        return switch (geometry.format()) {
            case 13 -> decodeQuantizedGeometry(geometry, 5, false);
            case 29 -> decodeQuantizedGeometry(geometry, 5, false);
            case 15, 31 -> decodeQuantizedGeometry(geometry);
            default -> null;
        };
    }

    private static xfeRoot.SceneGeometry decodeQuantizedGeometry(GeometryRecord geometry) {
        return decodeQuantizedGeometry(geometry, 8, geometry.format() == 31);
    }

    private static xfeRoot.SceneGeometry decodeQuantizedGeometry(GeometryRecord geometry, int componentCount,
                                                                 boolean parseSkinWeights) {
        byte[] payload = geometry.payload();
        int count = geometry.count();
        int offset = 8;
        float[][] components = new float[componentCount][count];
        for (int component = 0; component < componentCount; component++) {
            if (offset + 9 > payload.length) {
                return null;
            }
            float base = readFloat(payload, offset);
            float scale = readFloat(payload, offset + 4);
            int bits = payload[offset + 8] & 0xFF;
            int size = ((count * bits) + 7) / 8;
            int dataOffset = offset + 9;
            if (dataOffset + size > payload.length) {
                return null;
            }
            decodeQuantizedComponent(payload, dataOffset, bits, count, base, scale, components[component]);
            offset = dataOffset + size;
        }
        int[][] jointIndices = null;
        float[][] jointWeights = null;
        if (parseSkinWeights) {
            SkinWeights skinWeights = parseSkinWeights(payload, offset, count);
            if (skinWeights == null) {
                return null;
            }
            jointIndices = skinWeights.jointIndices();
            jointWeights = skinWeights.jointWeights();
        }
        float[] u = componentCount >= 5 ? components[3] : components[6];
        float[] v = componentCount >= 5 ? components[4] : components[7];
        if (componentCount >= 8) {
            u = components[6];
            v = components[7];
        }
        normalizeUvRange(u, v);
        return new xfeRoot.SceneGeometry(
                geometry.format(),
                count,
                components[0],
                components[1],
                components[2],
                u,
                v,
                jointIndices,
                jointWeights);
    }

    private static void normalizeUvRange(float[] u, float[] v) {
        if (u == null || v == null) {
            return;
        }
        float maxAbs = 0f;
        for (float value : u) {
            maxAbs = Math.max(maxAbs, Math.abs(value));
        }
        for (float value : v) {
            maxAbs = Math.max(maxAbs, Math.abs(value));
        }
        // Observed XF2 geometry stores UVs as 16-bit normalized coordinates expanded to floats.
        if (maxAbs <= 2f || maxAbs > 70000f) {
            return;
        }
        for (int index = 0; index < u.length; index++) {
            u[index] /= 65535f;
        }
        for (int index = 0; index < v.length; index++) {
            v[index] /= 65535f;
        }
    }

    private static SkinWeights parseSkinWeights(byte[] payload, int offset, int count) {
        if (offset + 8 > payload.length) {
            return null;
        }
        int[][] jointIndices = new int[count][];
        float[][] jointWeights = new float[count][];
        int cursor = offset + 8;
        for (int vertex = 0; vertex < count; vertex++) {
            if (cursor >= payload.length) {
                return null;
            }
            int influenceCount = payload[cursor] & 0xFF;
            cursor++;
            int[] indices = new int[influenceCount];
            float[] weights = new float[influenceCount];
            for (int influence = 0; influence < influenceCount; influence++) {
                if (cursor + 5 > payload.length) {
                    return null;
                }
                indices[influence] = payload[cursor] & 0xFF;
                weights[influence] = readFloat(payload, cursor + 1);
                cursor += 5;
            }
            jointIndices[vertex] = indices;
            jointWeights[vertex] = weights;
        }
        return cursor == payload.length ? new SkinWeights(jointIndices, jointWeights) : null;
    }

    private static CameraShapeRecord parseCameraShape(byte[] payload) {
        String name = readCountedString(payload, 4);
        if (name == null) {
            return null;
        }
        int offset = 5 + name.length();
        if (offset + 28 > payload.length) {
            return null;
        }
        return new CameraShapeRecord(
                name,
                readInt(payload, offset),
                readInt(payload, offset + 4),
                readFloat(payload, offset + 8),
                readFloat(payload, offset + 12),
                readFloat(payload, offset + 16),
                readFloat(payload, offset + 20),
                readFloat(payload, offset + 24));
    }

    private static xfeCamera resolveCamera(CameraShapeRecord record, ParseState state, xfeCamera fallback) {
        if (record == null) {
            return fallback;
        }
        if (record.name().startsWith("cameraShape")) {
            xfeNode candidate = state.nodesByName.get("camera" + record.name().substring("cameraShape".length()));
            if (candidate instanceof xfeCamera camera) {
                return camera;
            }
        }
        return fallback;
    }

    private static void applyCameraShape(xfeCamera camera, CameraShapeRecord record) {
        camera.getPreferredView().setWidth(record.width());
        camera.getPreferredView().setHeight(record.height());
        camera.getPreferredView().setNearClip(record.nearClip());
        camera.getPreferredView().setFarClip(record.farClip());
        camera.getPreferredView().setFOV((float) Math.toDegrees(2d * Math.atan(record.fovRadians())));
    }

    private static SkinRecord parseSkinRecord(byte[] payload) {
        if (payload == null || payload.length < 10) {
            return null;
        }
        int rootRecordIndex = readInt(payload, 0);
        int shapeRef = readInt(payload, 4);
        String name = readCountedString(payload, 8);
        if (name == null) {
            return null;
        }
        int offset = 9 + name.length();
        if (offset < payload.length) {
            offset++;
        }
        if ((payload.length - offset) % 44 != 0) {
            return null;
        }
        List<SkinEntryRecord> entries = new ArrayList<>();
        while (offset + 44 <= payload.length) {
            int nodeRef = readInt(payload, offset);
            NodeTransform transform = parseTransform(payload, offset + 4);
            if (transform == null) {
                return null;
            }
            entries.add(new SkinEntryRecord(nodeRef, transform));
            offset += 44;
        }
        return new SkinRecord(rootRecordIndex, shapeRef, name, entries);
    }

    private static xfeRoot.SceneSkin buildSceneSkin(SkinRecord record, Map<Integer, xfeNode> nodesByRef) {
        if (record == null || record.entries().isEmpty()) {
            return null;
        }
        int maxIndex = record.entries().size();
        xfeNode[] joints = new xfeNode[maxIndex + 1];
        xfMatrix4[] bindLocalMatrices = new xfMatrix4[maxIndex + 1];
        Map<xfeNode, xfMatrix4> bindLocalsByNode = new LinkedHashMap<>();
        for (int index = 0; index < record.entries().size(); index++) {
            SkinEntryRecord entry = record.entries().get(index);
            xfeNode node = nodesByRef.get(entry.nodeRef());
            if (node == null) {
                continue;
            }
            xfMatrix4 bindMatrix = toMatrix(entry.transform());
            joints[index + 1] = node;
            bindLocalMatrices[index + 1] = bindMatrix;
            bindLocalsByNode.put(node, bindMatrix);
        }
        return new xfeRoot.SceneSkin(joints, bindLocalMatrices, bindLocalsByNode);
    }

    private static void decodeQuantizedComponent(byte[] payload, int offset, int bits, int count,
                                                 float base, float scale, float[] target) {
        if (bits <= 0) {
            for (int i = 0; i < count; i++) {
                target[i] = base;
            }
            return;
        }
        int maxValue = bits >= 31 ? -1 : (1 << bits) - 1;
        for (int index = 0; index < count; index++) {
            int raw = readPackedBitsMsb(payload, offset, index * bits, bits);
            if (maxValue <= 0) {
                target[index] = base;
            } else {
                target[index] = base + (scale * (raw / (float) maxValue));
            }
        }
    }

    private static int readPackedBitsMsb(byte[] payload, int offset, int bitOffset, int bits) {
        int value = 0;
        for (int i = 0; i < bits; i++) {
            int absoluteBit = bitOffset + i;
            int source = payload[offset + (absoluteBit / 8)] & 0xFF;
            int bit = (source >> (7 - (absoluteBit % 8))) & 1;
            value = (value << 1) | bit;
        }
        return value;
    }

    private static String resolveReferencedPath(String currentPath, String relativePath) {
        int slash = currentPath.lastIndexOf('/');
        if (slash < 0) {
            return relativePath;
        }
        return currentPath.substring(0, slash + 1) + relativePath;
    }

    private static boolean isImageResource(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.toLowerCase();
        return lower.endsWith(".gif") || lower.endsWith(".bmp") || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg") || lower.endsWith(".png");
    }

    private static int readUnsignedShort(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private static int readInt(byte[] data, int offset) {
        return (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16)
                | ((data[offset + 3] & 0xFF) << 24);
    }

    private static float readFloat(byte[] data, int offset) {
        return Float.intBitsToFloat(readInt(data, offset));
    }

    private static int readRgbaColor(byte[] data, int offset) {
        if (offset + 4 > data.length) {
            return 0xFFFFFFFF;
        }
        int red = data[offset] & 0xFF;
        int green = data[offset + 1] & 0xFF;
        int blue = data[offset + 2] & 0xFF;
        int alpha = data[offset + 3] & 0xFF;
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static String readCountedString(byte[] data, int offset) {
        if (offset < 0 || offset >= data.length) {
            return null;
        }
        int length = data[offset] & 0xFF;
        if (length <= 0 || offset + 1 + length > data.length) {
            return null;
        }
        return new String(data, offset + 1, length, StandardCharsets.US_ASCII);
    }

    private static String readTrailingCountedString(byte[] data) {
        int start = data.length;
        while (start > 0) {
            int value = data[start - 1] & 0xFF;
            if (value < 32 || value > 126) {
                break;
            }
            start--;
        }
        if (start <= 0 || start >= data.length) {
            return null;
        }
        int length = data[start - 1] & 0xFF;
        if (data.length - start != length) {
            return null;
        }
        return new String(data, start, length, StandardCharsets.US_ASCII);
    }

    private static byte[] readResource(String path) throws IOException {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = xfeDefaultXF2Loader.class.getClassLoader();
        }
        if (loader == null) {
            return null;
        }
        try (InputStream stream = loader.getResourceAsStream(path)) {
            return stream == null ? null : stream.readAllBytes();
        }
    }

    private static final class ParseState {
        private final xfeRoot root;
        private final xfeNodeList nodeList;
        private final Set<String> loadedPaths = new LinkedHashSet<>();
        private final Map<String, xfeNode> nodesByName = new LinkedHashMap<>();
        private final Map<String, NodeTransform> transformsByName = new LinkedHashMap<>();
        private final List<xfeZone> zones = new ArrayList<>();
        private final List<xfeActor> actors = new ArrayList<>();
        private final LinkedHashSet<String> sceneImageCandidates = new LinkedHashSet<>();
        private final List<xfeRoot.SceneShape> sceneShapes = new ArrayList<>();

        private ParseState(xfeRoot root, xfeNodeList nodeList) {
            this.root = root;
            this.nodeList = nodeList;
        }
    }

    private static final class XF2Record {
        private final int type;
        private final int id;
        private final int parent;
        private final byte[] payload;

        private XF2Record(int type, int id, int parent, byte[] payload) {
            this.type = type;
            this.id = id;
            this.parent = parent;
            this.payload = payload;
        }
    }

    private static final class ParameterizedControllerRecord {
        private final String nodeName;
        private final xfeParameterDataSet parameters;

        private ParameterizedControllerRecord(String nodeName, xfeParameterDataSet parameters) {
            this.nodeName = nodeName;
            this.parameters = parameters;
        }
    }

    private record ShapeRecord(int shapeRef, int nodeRef, String name, List<ShapeBatchRecord> batches) {
    }

    private record ShapeBatchRecord(int tintRef, int textureRef, int geometryRef, int sliceRef,
                                    int indexStart, int indexCount) {
    }

    private record GeometryRecord(int format, int count, byte[] payload) {
    }

    private record SliceRecord(int count, int offset, int[] indices) {
    }

    private record NodeTransform(float x, float y, float z, float qx, float qy, float qz, float qw,
                                 float scaleX, float scaleY, float scaleZ) {
    }

    private record CameraShapeRecord(String name, int width, int height, float nearClip, float farClip,
                                     float fovRadians, float unknownA, float unknownB) {
    }

    private record SkinWeights(int[][] jointIndices, float[][] jointWeights) {
    }

    private record SkinRecord(int rootRecordIndex, int shapeRef, String name, List<SkinEntryRecord> entries) {
    }

    private record SkinEntryRecord(int nodeRef, NodeTransform transform) {
    }
}
