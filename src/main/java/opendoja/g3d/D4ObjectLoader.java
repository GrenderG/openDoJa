package opendoja.g3d;

import com.nttdocomo.ui.graphics3d.Primitive;
import com.nttdocomo.ui.util3d.Transform;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Host-side loader for proprietary D4 group-mesh containers used by some DoJa titles.
 * D4 is not a public DoJa object format, so this loader stays in the runtime-owned
 * g3d package beside {@link MascotLoader}. The public graphics3d package only assembles
 * the final mesh group because mesh-group construction uses package-private state.
 */
public final class D4ObjectLoader {
    private static final int M3G_KEYFRAME_LINEAR = 0xB0;
    private static final int M3G_REPEAT_LOOP = 0xC1;
    private static final int M3G_WRAP_REPEAT = 0xF1;
    private static final int M3G_ANIM_TRANSLATION = 275;

    private D4ObjectLoader() {
    }

    public static DecodedGroup load(byte[] data) throws IOException {
        if (data == null || data.length < 10 || data[0] != 'D' || data[1] != '4') {
            return null;
        }
        Map<Integer, D4Object> objects = parseObjects(data);
        if (objects.isEmpty()) {
            return null;
        }
        Map<Integer, D4TextureData> textures = new LinkedHashMap<>();
        for (D4Object object : objects.values()) {
            if (object.type != 10) {
                continue;
            }
            textures.put(object.id, decodeTexture(object));
        }
        int rootId = resolveRootGroupId(objects);
        if (rootId < 0) {
            return null;
        }
        D4Object root = objects.get(rootId);
        int arrayBindingId = firstShift16TargetOfType(root, objects, 21);
        if (arrayBindingId < 0) {
            return null;
        }
        D4MeshArrays arrays = decodeMeshArrays(objects.get(arrayBindingId), objects);
        if (arrays.positions == null || arrays.uvs == null) {
            return null;
        }
        Transform transform = groupTransformOf(arrays);
        List<DecodedPrimitive> elements = new ArrayList<>();
        for (D4Pair pair : decodeMeshPairs(root, objects)) {
            D4IndexSet indexSet = decodeIndexSet(objects.get(pair.indicesId));
            D4MeshDescriptor descriptor = decodeMeshDescriptor(objects.get(pair.descriptorId), objects);
            DecodedPrimitive primitive = buildPrimitive(indexSet, descriptor, arrays, textures);
            if (primitive != null) {
                elements.add(primitive);
            }
        }
        return elements.isEmpty() ? null : new DecodedGroup(transform, List.copyOf(elements));
    }

    private static Map<Integer, D4Object> parseObjects(byte[] data) throws IOException {
        int payloadSize = littleInt(data, 4);
        int payloadEnd = 10 + payloadSize;
        if (payloadEnd < 10 || payloadEnd > data.length) {
            throw new IOException("Invalid D4 payload size");
        }
        Map<Integer, D4Object> objects = new LinkedHashMap<>();
        int offset = 10;
        while (offset < payloadEnd) {
            if (offset + 9 > payloadEnd) {
                throw new IOException("Truncated D4 section header");
            }
            int compress = unsigned(data[offset]);
            if (compress != 0) {
                throw new IOException("Unsupported compressed D4 section");
            }
            int sectionSize = littleInt(data, offset + 1);
            int binarySize = littleInt(data, offset + 5);
            if (sectionSize < 13 || binarySize < 0 || offset + sectionSize > payloadEnd) {
                throw new IOException("Invalid D4 section bounds");
            }
            int objectOffset = offset + 9;
            int objectEnd = objectOffset + binarySize;
            if (objectEnd + 4 != offset + sectionSize) {
                throw new IOException("Invalid D4 section size");
            }
            while (objectOffset < objectEnd) {
                if (objectOffset + 9 > objectEnd) {
                    throw new IOException("Truncated D4 object header");
                }
                int type = unsigned(data[objectOffset]);
                int size = littleInt(data, objectOffset + 1);
                if (size < 4 || objectOffset + 5 + size > objectEnd) {
                    throw new IOException("Invalid D4 object size");
                }
                int id = littleInt(data, objectOffset + 5);
                byte[] payload = slice(data, objectOffset + 9, size - 4);
                objects.put(id, new D4Object(id, type, payload));
                objectOffset += 5 + size;
            }
            offset += sectionSize;
        }
        return objects;
    }

    private static int resolveRootGroupId(Map<Integer, D4Object> objects) {
        for (D4Object object : objects.values()) {
            if (object.type != 22) {
                continue;
            }
            int ref = firstPlainTargetOfType(object, objects, 14);
            if (ref >= 0) {
                return ref;
            }
        }
        for (D4Object object : objects.values()) {
            if (object.type == 14) {
                return object.id;
            }
        }
        return -1;
    }

    private static List<D4Pair> decodeMeshPairs(D4Object object, Map<Integer, D4Object> objects) {
        List<Integer> refs = new ArrayList<>();
        for (int offset = 0; offset + 3 < object.payload.length; offset += 4) {
            int ref = decodeShift16Ref(littleInt(object.payload, offset));
            D4Object target = objects.get(ref);
            if (target == null) {
                continue;
            }
            if (target.type == 11 || target.type == 3) {
                refs.add(ref);
            }
        }
        List<D4Pair> pairs = new ArrayList<>();
        for (int i = 0; i + 1 < refs.size(); i += 2) {
            D4Object indices = objects.get(refs.get(i));
            D4Object descriptor = objects.get(refs.get(i + 1));
            if (indices != null && descriptor != null && indices.type == 11 && descriptor.type == 3) {
                pairs.add(new D4Pair(indices.id, descriptor.id));
            }
        }
        return pairs;
    }

    private static D4MeshArrays decodeMeshArrays(D4Object object, Map<Integer, D4Object> objects) throws IOException {
        List<D4Array> arrays = new ArrayList<>();
        for (int offset = 0; offset + 3 < object.payload.length; offset += 4) {
            int ref = decodePlainRef(littleInt(object.payload, offset));
            D4Object target = objects.get(ref);
            if (target == null || target.type != 20) {
                continue;
            }
            arrays.add(decodeArray(target));
        }
        D4Array positions = null;
        D4Array uvs = null;
        D4Array colors = null;
        for (D4Array array : arrays) {
            if (array.components == 3 && positions == null) {
                positions = array;
            } else if (array.components == 2 && uvs == null) {
                uvs = array;
            } else if (array.components == 4 && colors == null) {
                colors = array;
            }
        }
        float originX = object.payload.length >= 20 ? littleFloat(object.payload, 16) : 0f;
        float originY = object.payload.length >= 24 ? littleFloat(object.payload, 20) : 0f;
        float originZ = object.payload.length >= 28 ? littleFloat(object.payload, 24) : 0f;
        float vertexScale = object.payload.length >= 32 ? littleFloat(object.payload, 28) : 1f;
        float texCoordBiasU = 0f;
        float texCoordBiasV = 0f;
        float texCoordScale = 1f / 4096f;
        if (object.payload.length >= 64 && littleInt(object.payload, 40) > 0) {
            texCoordBiasU = littleFloat(object.payload, 48);
            texCoordBiasV = littleFloat(object.payload, 52);
            float decodedScale = littleFloat(object.payload, 60);
            if (Float.isFinite(decodedScale) && decodedScale != 0f) {
                texCoordScale = decodedScale;
            }
        }
        return new D4MeshArrays(positions, uvs, colors, originX, originY, originZ, vertexScale,
                texCoordBiasU, texCoordBiasV, texCoordScale);
    }

    private static D4Array decodeArray(D4Object object) throws IOException {
        if (object.payload.length < 13) {
            throw new IOException("Truncated D4 array");
        }
        int elementSize = unsigned(object.payload[8]);
        int components = unsigned(object.payload[9]);
        int count = littleShort(object.payload, 11);
        int dataOffset = 13;
        int valueCount = count * components;
        int byteCount = valueCount * elementSize;
        if (elementSize != 1 && elementSize != 2) {
            throw new IOException("Unsupported D4 array element size");
        }
        if (dataOffset + byteCount > object.payload.length) {
            throw new IOException("Truncated D4 array payload");
        }
        int[] values = new int[valueCount];
        if (elementSize == 1) {
            for (int i = 0; i < valueCount; i++) {
                values[i] = object.payload[dataOffset + i];
            }
        } else {
            for (int i = 0; i < valueCount; i++) {
                values[i] = (short) littleShort(object.payload, dataOffset + i * 2);
            }
        }
        return new D4Array(components, values);
    }

    private static D4IndexSet decodeIndexSet(D4Object object) throws IOException {
        if (object.payload.length < 17) {
            throw new IOException("Truncated D4 index set");
        }
        int format = unsigned(object.payload[8]);
        int indexSize = switch (format) {
            case 0x81 -> 1;
            case 0x82 -> 2;
            default -> throw new IOException("Unsupported D4 index format");
        };
        int indexCount = littleInt(object.payload, 9);
        int indexOffset = 13;
        int primitiveCountOffset = indexOffset + indexCount * indexSize;
        if (primitiveCountOffset + 4 > object.payload.length) {
            throw new IOException("Truncated D4 index data");
        }
        int primitiveCount = littleInt(object.payload, primitiveCountOffset);
        int lengthsOffset = primitiveCountOffset + 4;
        if (primitiveCount < 0 || lengthsOffset + primitiveCount * 4 > object.payload.length) {
            throw new IOException("Invalid D4 primitive lengths");
        }
        int[] indices = new int[indexCount];
        if (indexSize == 1) {
            for (int i = 0; i < indexCount; i++) {
                indices[i] = unsigned(object.payload[indexOffset + i]);
            }
        } else {
            for (int i = 0; i < indexCount; i++) {
                indices[i] = littleShort(object.payload, indexOffset + i * 2);
            }
        }
        int[] lengths = new int[primitiveCount];
        for (int i = 0; i < primitiveCount; i++) {
            lengths[i] = littleInt(object.payload, lengthsOffset + i * 4);
        }
        return new D4IndexSet(indices, lengths);
    }

    private static D4MeshDescriptor decodeMeshDescriptor(D4Object object, Map<Integer, D4Object> objects) {
        int textureBindingId = -1;
        int textureFlagsId = -1;
        for (int offset = 0; offset + 3 < object.payload.length; offset += 4) {
            int ref = decodeShift8Ref(littleInt(object.payload, offset));
            D4Object target = objects.get(ref);
            if (target == null) {
                continue;
            }
            if (target.type == 6 && textureFlagsId < 0) {
                textureFlagsId = ref;
            } else if (target.type == 17 && textureBindingId < 0) {
                textureBindingId = ref;
            }
        }
        D4TextureBinding textureBinding = null;
        if (textureBindingId >= 0) {
            textureBinding = decodeTextureBinding(objects.get(textureBindingId), objects);
        }
        boolean textureWrapEnabled = textureBinding == null
                ? decodeTextureWrapEnabled(objects.get(textureFlagsId))
                : textureBinding.wrapS && textureBinding.wrapT;
        int primitiveType = object.payload.length == 0 ? Primitive.PRIMITIVE_TRIANGLES
                : unsigned(object.payload[object.payload.length - 1]);
        return new D4MeshDescriptor(textureBinding, primitiveType, textureWrapEnabled);
    }

    private static DecodedPrimitive buildPrimitive(D4IndexSet indexSet, D4MeshDescriptor descriptor,
                                                   D4MeshArrays arrays, Map<Integer, D4TextureData> textures) {
        if (indexSet.indices.length == 0 || arrays.positions.values.length == 0) {
            return null;
        }
        int triangleCount = 0;
        int consumed = 0;
        for (int length : indexSet.lengths) {
            if (length < 3 || consumed + length > indexSet.indices.length) {
                break;
            }
            triangleCount += length - 2;
            consumed += length;
        }
        if (triangleCount <= 0) {
            return null;
        }
        int textureId = descriptor.textureBinding == null ? -1 : descriptor.textureBinding.textureId;
        int primitiveParam = textureId >= 0 && arrays.uvs != null
                ? Primitive.TEXTURE_COORD_PER_VERTEX
                : Primitive.COLOR_NONE;
        if (arrays.colors != null) {
            primitiveParam |= Primitive.COLOR_PER_VERTEX_INTERNAL;
        }
        D4TextureData textureData = textures.get(textureId);
        if (textureData != null && textureData.transparentPaletteZero) {
            primitiveParam |= Primitive.TEXTURE_COLORKEY;
        }
        int[] vertices = new int[triangleCount * 9];
        int[] colors = (primitiveParam & 0x0C00) == Primitive.COLOR_PER_VERTEX_INTERNAL
                ? new int[triangleCount * 3]
                : null;
        int[] uvs = (primitiveParam & 0x3000) == Primitive.TEXTURE_COORD_PER_VERTEX
                ? new int[triangleCount * 6]
                : null;
        int cursor = 0;
        int triangle = 0;
        for (int length : indexSet.lengths) {
            if (length < 3 || cursor + length > indexSet.indices.length) {
                break;
            }
            int first = indexSet.indices[cursor];
            for (int i = 1; i < length - 1; i++) {
                writeVertex(vertices, triangle * 9, arrays.positions, first);
                writeVertex(vertices, triangle * 9 + 3, arrays.positions, indexSet.indices[cursor + i]);
                writeVertex(vertices, triangle * 9 + 6, arrays.positions, indexSet.indices[cursor + i + 1]);
                if (colors != null) {
                    writeColor(colors, triangle * 3, arrays.colors, first);
                    writeColor(colors, triangle * 3 + 1, arrays.colors, indexSet.indices[cursor + i]);
                    writeColor(colors, triangle * 3 + 2, arrays.colors, indexSet.indices[cursor + i + 1]);
                }
                if (uvs != null) {
                    writeUv(uvs, triangle * 6, arrays.uvs, first, textureData, arrays);
                    writeUv(uvs, triangle * 6 + 2, arrays.uvs, indexSet.indices[cursor + i], textureData, arrays);
                    writeUv(uvs, triangle * 6 + 4, arrays.uvs, indexSet.indices[cursor + i + 1], textureData, arrays);
                }
                triangle++;
            }
            cursor += length;
        }
        return new DecodedPrimitive(primitiveParam, vertices, colors, uvs,
                textureData == null ? null : textureData.texture, descriptor.textureWrapEnabled,
                descriptor.textureBinding == null ? TextureCoordinateTransform.IDENTITY : descriptor.textureBinding.textureCoordinateTransform);
    }

    private static void writeVertex(int[] target, int offset, D4Array positions, int index) {
        int source = index * positions.components;
        if (source + 2 >= positions.values.length) {
            return;
        }
        target[offset] = positions.values[source];
        target[offset + 1] = positions.values[source + 1];
        target[offset + 2] = positions.values[source + 2];
    }

    private static void writeUv(int[] target, int offset, D4Array uvs, int index, D4TextureData textureData,
                                D4MeshArrays arrays) {
        int source = index * uvs.components;
        if (source + 1 >= uvs.values.length) {
            return;
        }
        if (textureData == null) {
            target[offset] = uvs.values[source];
            target[offset + 1] = uvs.values[source + 1];
            return;
        }
        target[offset] = scaleTextureCoord(uvs.values[source], textureData.texture.width(),
                arrays.texCoordBiasU, arrays.texCoordScale);
        target[offset + 1] = scaleTextureCoord(uvs.values[source + 1], textureData.texture.height(),
                arrays.texCoordBiasV, arrays.texCoordScale);
    }

    private static void writeColor(int[] target, int offset, D4Array colors, int index) {
        int source = index * colors.components;
        if (source + 3 >= colors.values.length) {
            return;
        }
        int red = colors.values[source] & 0xFF;
        int green = colors.values[source + 1] & 0xFF;
        int blue = colors.values[source + 2] & 0xFF;
        int alpha = colors.values[source + 3] & 0xFF;
        target[offset] = (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static boolean decodeTextureWrapEnabled(D4Object object) {
        if (object == null || object.payload.length < 10) {
            return false;
        }
        // In the captured D4 fixtures, type 6 starts with 0x0101 on every repeating mesh.
        // Treat that pair as the container's repeat flag and keep the behavior internal to
        // the loader instead of exposing a public API change.
        return littleShort(object.payload, 8) == 0x0101;
    }

    private static int scaleTextureCoord(int encoded, int textureSize, float bias, float scale) {
        return java.lang.Math.round((bias + encoded * scale) * textureSize);
    }

    private static Transform groupTransformOf(D4MeshArrays arrays) {
        Transform transform = new Transform();
        if (arrays == null || !Float.isFinite(arrays.vertexScale) || arrays.vertexScale <= 0f) {
            return transform;
        }
        transform.set(new float[]{
                arrays.vertexScale, 0f, 0f, arrays.originX,
                0f, arrays.vertexScale, 0f, arrays.originY,
                0f, 0f, arrays.vertexScale, arrays.originZ,
                0f, 0f, 0f, 1f
        });
        return transform;
    }

    private static D4TextureData decodeTexture(D4Object object) throws IOException {
        if (object.payload.length < 26) {
            throw new IOException("Truncated D4 texture");
        }
        int marker = unsigned(object.payload[8]);
        int width = littleInt(object.payload, 10);
        int height = littleInt(object.payload, 14);
        int paletteBytes = littleInt(object.payload, 18);
        int paletteOffset = 22;
        int pixelCountOffset = paletteOffset + paletteBytes;
        if (width <= 0 || height <= 0 || pixelCountOffset + 4 > object.payload.length) {
            throw new IOException("Invalid D4 texture header");
        }
        int pixelCount = littleInt(object.payload, pixelCountOffset);
        int pixelOffset = pixelCountOffset + 4;
        if (pixelCount < width * height || pixelOffset + pixelCount > object.payload.length) {
            throw new IOException("Invalid D4 texture pixel data");
        }
        int entrySize = switch (marker) {
            case 99 -> 3;
            case 100 -> 4;
            default -> throw new IOException("Unsupported D4 texture format");
        };
        if (paletteBytes % entrySize != 0) {
            throw new IOException("Invalid D4 texture palette size");
        }
        int[] palette = new int[paletteBytes / entrySize];
        boolean allAlphaZero = true;
        boolean allAlphaFull = true;
        boolean transparentPaletteZero = false;
        int[] rawAlpha = entrySize == 4 ? new int[palette.length] : null;
        for (int i = 0; i < palette.length; i++) {
            int base = paletteOffset + i * entrySize;
            int r = unsigned(object.payload[base]);
            int g = unsigned(object.payload[base + 1]);
            int b = unsigned(object.payload[base + 2]);
            int a = 0xFF;
            if (entrySize == 4) {
                a = unsigned(object.payload[base + 3]);
                rawAlpha[i] = a;
                allAlphaZero &= a == 0;
                allAlphaFull &= a == 0xFF;
            }
            palette[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
        if (entrySize == 4 && (allAlphaZero || allAlphaFull)) {
            // Some D4 image objects use BMP-derived palettes where the fourth byte is copied
            // through as a reserved value, not meaningful alpha. Treat uniform 0x00 / 0xFF
            // palettes as opaque and only preserve alpha when the palette actually mixes values.
            for (int i = 0; i < palette.length; i++) {
                palette[i] = 0xFF000000 | (palette[i] & 0x00FFFFFF);
            }
        } else if (entrySize == 4 && palette.length > 0 && rawAlpha[0] == 0) {
            transparentPaletteZero = true;
        }
        byte[] pixels = slice(object.payload, pixelOffset, width * height);
        SoftwareTexture texture = SoftwareTexture.fromIndexed(width, height, palette, pixels, true);
        return new D4TextureData(texture, transparentPaletteZero);
    }

    private static D4TextureBinding decodeTextureBinding(D4Object object, Map<Integer, D4Object> objects) {
        if (object == null || object.payload.length < 12) {
            return null;
        }
        D4ObjectData objectData = decodeObjectData(object);
        int offset = objectData.payloadOffset;
        float translationU = 0f;
        float translationV = 0f;
        if (offset < object.payload.length) {
            int hasComponentTransform = unsigned(object.payload[offset++]);
            if (hasComponentTransform != 0) {
                if (offset + 40 > object.payload.length) {
                    return null;
                }
                translationU = littleFloat(object.payload, offset);
                translationV = littleFloat(object.payload, offset + 4);
                offset += 40;
            }
        }
        if (offset < object.payload.length) {
            int hasGenericTransform = unsigned(object.payload[offset++]);
            if (hasGenericTransform != 0) {
                if (offset + 64 > object.payload.length) {
                    return null;
                }
                offset += 64;
            }
        }
        if (offset + 12 > object.payload.length) {
            return null;
        }
        int textureId = decodePlainRef(littleInt(object.payload, offset));
        if (textureId < 0 || objects.get(textureId) == null || objects.get(textureId).type != 10) {
            return null;
        }
        boolean wrapS = unsigned(object.payload[offset + 8]) == M3G_WRAP_REPEAT;
        boolean wrapT = unsigned(object.payload[offset + 9]) == M3G_WRAP_REPEAT;
        TextureCoordinateTransform.LinearTranslation animation = null;
        for (int animationTrackId : objectData.animationTrackIds) {
            animation = decodeTranslationAnimation(objects.get(animationTrackId), objects);
            if (animation != null) {
                break;
            }
        }
        return new D4TextureBinding(textureId, wrapS, wrapT,
                new TextureCoordinateTransform(translationU, translationV, animation));
    }

    private static TextureCoordinateTransform.LinearTranslation decodeTranslationAnimation(
            D4Object animationTrack, Map<Integer, D4Object> objects) {
        if (animationTrack == null || animationTrack.type != 2) {
            return null;
        }
        D4ObjectData objectData = decodeObjectData(animationTrack);
        int offset = objectData.payloadOffset;
        if (offset + 12 > animationTrack.payload.length) {
            return null;
        }
        int keyframeSequenceId = decodePlainRef(littleInt(animationTrack.payload, offset));
        int property = littleInt(animationTrack.payload, offset + 8);
        if (property != M3G_ANIM_TRANSLATION) {
            return null;
        }
        return decodeLinearTranslationSequence(objects.get(keyframeSequenceId));
    }

    private static TextureCoordinateTransform.LinearTranslation decodeLinearTranslationSequence(D4Object keyframeSequence) {
        if (keyframeSequence == null || keyframeSequence.type != 19) {
            return null;
        }
        D4ObjectData objectData = decodeObjectData(keyframeSequence);
        int offset = objectData.payloadOffset;
        if (offset + 23 > keyframeSequence.payload.length) {
            return null;
        }
        int interpolation = unsigned(keyframeSequence.payload[offset++]);
        int repeatMode = unsigned(keyframeSequence.payload[offset++]);
        int encoding = unsigned(keyframeSequence.payload[offset++]);
        if (interpolation != M3G_KEYFRAME_LINEAR || encoding != 0) {
            return null;
        }
        int duration = littleInt(keyframeSequence.payload, offset);
        offset += 4;
        offset += 8; // valid range first/last
        int components = littleInt(keyframeSequence.payload, offset);
        offset += 4;
        int keyframes = littleInt(keyframeSequence.payload, offset);
        offset += 4;
        if (components < 2 || keyframes <= 0 || offset + keyframes * (4 + components * 4) > keyframeSequence.payload.length) {
            return null;
        }
        int[] times = new int[keyframes];
        float[] uValues = new float[keyframes];
        float[] vValues = new float[keyframes];
        for (int i = 0; i < keyframes; i++) {
            times[i] = littleInt(keyframeSequence.payload, offset);
            offset += 4;
            uValues[i] = littleFloat(keyframeSequence.payload, offset);
            vValues[i] = littleFloat(keyframeSequence.payload, offset + 4);
            offset += components * 4;
        }
        return new TextureCoordinateTransform.LinearTranslation(duration, repeatMode == M3G_REPEAT_LOOP,
                times, uValues, vValues);
    }

    private static D4ObjectData decodeObjectData(D4Object object) {
        if (object == null || object.payload.length < 8) {
            return new D4ObjectData(0, new int[0]);
        }
        int animationTrackCount = littleInt(object.payload, 0);
        int offset = 4;
        if (animationTrackCount < 0 || animationTrackCount > (object.payload.length - offset) / 4) {
            return new D4ObjectData(8, new int[0]);
        }
        int[] animationTrackIds = new int[animationTrackCount];
        for (int i = 0; i < animationTrackCount; i++) {
            animationTrackIds[i] = decodePlainRef(littleInt(object.payload, offset));
            offset += 4;
        }
        if (offset + 4 > object.payload.length) {
            return new D4ObjectData(java.lang.Math.min(offset, object.payload.length), animationTrackIds);
        }
        int userParamCount = littleInt(object.payload, offset);
        offset += 4;
        for (int i = 0; i < userParamCount && offset + 8 <= object.payload.length; i++) {
            offset += 4;
            int userParamLength = littleInt(object.payload, offset);
            offset += 4;
            if (userParamLength < 0 || offset + userParamLength > object.payload.length) {
                return new D4ObjectData(object.payload.length, animationTrackIds);
            }
            offset += userParamLength;
        }
        return new D4ObjectData(offset, animationTrackIds);
    }

    private static int firstPlainTargetOfType(D4Object object, Map<Integer, D4Object> objects, int type) {
        for (int offset = 0; offset + 3 < object.payload.length; offset += 4) {
            int ref = decodePlainRef(littleInt(object.payload, offset));
            D4Object target = objects.get(ref);
            if (target != null && target.type == type) {
                return ref;
            }
        }
        return -1;
    }

    private static int firstShift16TargetOfType(D4Object object, Map<Integer, D4Object> objects, int type) {
        if (object == null) {
            return -1;
        }
        for (int offset = 0; offset + 3 < object.payload.length; offset += 4) {
            int ref = decodeShift16Ref(littleInt(object.payload, offset));
            D4Object target = objects.get(ref);
            if (target != null && target.type == type) {
                return ref;
            }
        }
        return -1;
    }

    private static int decodePlainRef(int encoded) {
        return encoded <= 0 ? -1 : encoded - 1;
    }

    private static int decodeShift16Ref(int encoded) {
        int ref = encoded >>> 16;
        return ref == 0 ? -1 : ref - 1;
    }

    private static int decodeShift8Ref(int encoded) {
        int ref = encoded >>> 8;
        return ref == 0 ? -1 : ref - 1;
    }

    private static int littleInt(byte[] data, int offset) {
        return unsigned(data[offset])
                | (unsigned(data[offset + 1]) << 8)
                | (unsigned(data[offset + 2]) << 16)
                | (unsigned(data[offset + 3]) << 24);
    }

    private static int littleShort(byte[] data, int offset) {
        return unsigned(data[offset]) | (unsigned(data[offset + 1]) << 8);
    }

    private static float littleFloat(byte[] data, int offset) {
        return Float.intBitsToFloat(littleInt(data, offset));
    }

    private static int unsigned(byte value) {
        return value & 0xFF;
    }

    private static byte[] slice(byte[] data, int offset, int length) {
        byte[] copy = new byte[length];
        System.arraycopy(data, offset, copy, 0, length);
        return copy;
    }

    private record D4Object(int id, int type, byte[] payload) {
    }

    private record D4Pair(int indicesId, int descriptorId) {
    }

    private record D4Array(int components, int[] values) {
    }

    private record D4MeshArrays(D4Array positions, D4Array uvs, D4Array colors,
                                float originX, float originY, float originZ,
                                float vertexScale, float texCoordBiasU,
                                float texCoordBiasV, float texCoordScale) {
    }

    private record D4IndexSet(int[] indices, int[] lengths) {
    }

    private record D4MeshDescriptor(D4TextureBinding textureBinding, int primitiveType, boolean textureWrapEnabled) {
    }

    private record D4TextureData(SoftwareTexture texture, boolean transparentPaletteZero) {
    }

    private record D4TextureBinding(int textureId, boolean wrapS, boolean wrapT,
                                    TextureCoordinateTransform textureCoordinateTransform) {
    }

    private record D4ObjectData(int payloadOffset, int[] animationTrackIds) {
    }

    public record DecodedGroup(Transform transform, List<DecodedPrimitive> elements) {
    }

    public record DecodedPrimitive(int primitiveParam, int[] vertices, int[] colors, int[] textureCoords,
                                   SoftwareTexture textureHandle, boolean textureWrapEnabled,
                                   TextureCoordinateTransform textureCoordinateTransform) {
    }
}
