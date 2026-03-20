package opendoja.g3d;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public final class MascotLoader {
    private static final int[] SIZE_TABLE = {8, 10, 13, 16};
    private static final int[] POOL_NORMALS = {0, 0, 64, 0, 0, -64, 0, 0};
    private static final int MATERIAL_TRANSPARENT = 0x01;
    private static final int MATERIAL_DOUBLE_SIDED = 0x10;
    private static final int MATERIAL_BLEND_MASK = 0x06;

    private MascotLoader() {
    }

    public static MbacModel loadFigure(InputStream inputStream) throws IOException {
        return loadFigure(inputStream.readAllBytes());
    }

    public static MbacModel loadFigure(byte[] bytes) throws IOException {
        Reader reader = new Reader(bytes);
        if (reader.readUByte() != 'M' || reader.readUByte() != 'B') {
            throw new IOException("Not a MBAC file");
        }
        int version = reader.readUByte();
        if (reader.readUByte() != 0 || version < 2 || version > 5) {
            throw new IOException("Unsupported MBAC version: " + version);
        }
        int vertexFormat;
        int normalFormat;
        int polygonFormat;
        int boneFormat;
        if (version > 3) {
            vertexFormat = reader.readUByte();
            normalFormat = reader.readUByte();
            polygonFormat = reader.readUByte();
            boneFormat = reader.readUByte();
        } else {
            vertexFormat = 1;
            normalFormat = 0;
            polygonFormat = 1;
            boneFormat = 1;
        }
        if (boneFormat != 1) {
            throw new IOException("Unexpected bone format: " + boneFormat);
        }

        int numVertices = reader.readUShort();
        int numPolyT3 = reader.readUShort();
        int numPolyT4 = reader.readUShort();
        int numBones = reader.readUShort();

        int numTextures;
        int numColors;
        int numPolyC3;
        int numPolyC4;
        int numPatterns;
        if (polygonFormat < 3) {
            numTextures = 1;
            numPolyC3 = 0;
            numPolyC4 = 0;
            numPatterns = 1;
            numColors = 0;
        } else {
            numPolyC3 = reader.readUShort();
            numPolyC4 = reader.readUShort();
            numTextures = reader.readUShort();
            numPatterns = reader.readUShort();
            numColors = reader.readUShort();
        }

        int[][][] patterns = new int[Math.max(1, numPatterns)][java.lang.Math.max(1, numTextures + 1)][2];
        if (version == 5) {
            for (int i = 0; i < numPatterns; i++) {
                patterns[i][0][0] = reader.readUShort();
                patterns[i][0][1] = reader.readUShort();
                for (int j = 1; j <= numTextures; j++) {
                    patterns[i][j][0] = reader.readUShort();
                    patterns[i][j][1] = reader.readUShort();
                }
            }
        } else {
            patterns[0] = new int[][]{{numPolyC3, numPolyC4}, {numPolyT3, numPolyT4}};
        }

        int[] vertices = new int[numVertices * 3];
        if (vertexFormat == 1) {
            readVerticesV1(reader, vertices);
        } else if (vertexFormat == 2) {
            readVerticesV2(reader, vertices);
        } else {
            throw new IOException("Unexpected vertex format: " + vertexFormat);
        }
        reader.clearBits();

        if (normalFormat == 1) {
            readNormalsV1(reader, numVertices);
            reader.clearBits();
        } else if (normalFormat == 2) {
            readNormalsV2(reader, numVertices);
            reader.clearBits();
        }

        Poly[] colorPolys = numPolyC3 + numPolyC4 > 0 ? readColorPolygons(reader, numVertices, numColors, numPolyC3, numPolyC4) : new Poly[0];
        Poly[] texturePolys;
        if (numPolyT3 + numPolyT4 <= 0) {
            texturePolys = new Poly[0];
        } else if (polygonFormat == 1) {
            texturePolys = readTexturePolygonsV1(reader, numVertices, numPolyT3, numPolyT4);
        } else if (polygonFormat == 2) {
            texturePolys = readTexturePolygonsV2(reader, numVertices, numPolyT3, numPolyT4);
        } else if (polygonFormat == 3) {
            texturePolys = readTexturePolygonsV3(reader, numVertices, numPolyT3, numPolyT4);
        } else {
            throw new IOException("Unexpected polygon format: " + polygonFormat);
        }
        reader.clearBits();

        assignPatterns(patterns, texturePolys, colorPolys, numTextures, numPolyT3);
        MbacModel.Bone[] bones = readBones(reader, numBones, numVertices);

        List<MbacModel.Polygon> polygons = new ArrayList<>(texturePolys.length + colorPolys.length);
        for (Poly poly : texturePolys) {
            polygons.add(poly.toPolygon());
        }
        for (Poly poly : colorPolys) {
            polygons.add(poly.toPolygon());
        }
        return new MbacModel(vertices, vertices.clone(), polygons.toArray(MbacModel.Polygon[]::new), java.lang.Math.max(1, numPatterns), bones);
    }

    public static MascotActionTableData loadActionTable(InputStream inputStream) throws IOException {
        return loadActionTable(inputStream.readAllBytes());
    }

    public static MascotActionTableData loadActionTable(byte[] bytes) throws IOException {
        Reader reader = new Reader(bytes);
        if (reader.readUByte() != 'M' || reader.readUByte() != 'T') {
            throw new IOException("Not a MTRA file");
        }
        int version = reader.readUByte();
        if (reader.readUByte() != 0 || version < 2 || version > 5) {
            throw new IOException("Unsupported MTRA version: " + version);
        }
        int numActions = reader.readUShort();
        int numBones = reader.readUShort();
        for (int i = 0; i < 8; i++) {
            reader.readUShort();
        }
        reader.readInt();

        MascotActionTableData.Action[] actions = new MascotActionTableData.Action[numActions];
        for (int action = 0; action < numActions; action++) {
            int keyframes = reader.readUShort();
            MascotActionTableData.BoneAction[] boneActions = new MascotActionTableData.BoneAction[numBones];
            for (int bone = 0; bone < numBones; bone++) {
                boneActions[bone] = readBoneAction(reader);
            }
            int[] dynamicFrames = new int[0];
            int[] dynamicPatterns = new int[0];
            if (version >= 5) {
                int count = reader.readUShort();
                dynamicFrames = new int[count];
                dynamicPatterns = new int[count];
                for (int i = 0; i < count; i++) {
                    dynamicFrames[i] = reader.readUShort();
                    dynamicPatterns[i] = reader.readInt();
                }
            }
            actions[action] = new MascotActionTableData.Action(keyframes, boneActions, dynamicFrames, dynamicPatterns);
        }
        return new MascotActionTableData(actions, numBones);
    }

    private static MbacModel.Bone[] readBones(Reader reader, int numBones, int numVertices) throws IOException {
        if (numBones <= 0) {
            return new MbacModel.Bone[0];
        }
        MbacModel.Bone[] bones = new MbacModel.Bone[numBones];
        int totalVertices = 0;
        for (int i = 0; i < numBones; i++) {
            int length = reader.readUShort();
            int parent = reader.readShort();
            if (parent < -1) {
                throw new IOException("Invalid bone parent index");
            }
            int[] matrix = new int[12];
            for (int row = 0; row < 3; row++) {
                int offset = row * 4;
                matrix[offset] = reader.readShort();
                matrix[offset + 1] = reader.readShort();
                matrix[offset + 2] = reader.readShort();
                matrix[offset + 3] = reader.readShort();
            }
            bones[i] = new MbacModel.Bone(length, parent, matrix);
            totalVertices += length;
        }
        if (totalVertices != numVertices) {
            throw new IOException("Bone vertex coverage mismatch: " + totalVertices + " != " + numVertices);
        }
        return bones;
    }

    private static MascotActionTableData.BoneAction readBoneAction(Reader reader) throws IOException {
        int type = reader.readUByte();
        switch (type) {
            case 0 -> {
                int[] matrix = new int[12];
                for (int row = 0; row < 3; row++) {
                    int offset = row * 4;
                    matrix[offset] = reader.readShort();
                    matrix[offset + 1] = reader.readShort();
                    matrix[offset + 2] = reader.readShort();
                    matrix[offset + 3] = reader.readShort();
                }
                return new MascotActionTableData.BoneAction(type, matrix, null, null, null, null);
            }
            case 1 -> {
                return new MascotActionTableData.BoneAction(type, MascotActionTableData.IDENTITY_AFFINE.clone(), null, null, null, null);
            }
            case 2 -> {
                MascotActionTableData.Animation translate = readKeyedTranslations(reader);
                MascotActionTableData.Animation scale = readKeyedScales(reader);
                MascotActionTableData.Animation rotate = readKeyedRotations(reader);
                MascotActionTableData.RollAnimation roll = readKeyedRolls(reader);
                return new MascotActionTableData.BoneAction(type, null, translate, scale, rotate, roll);
            }
            case 3 -> {
                MascotActionTableData.Animation translate = new MascotActionTableData.Animation(1);
                translate.set(0, 0, reader.readShort(), reader.readShort(), reader.readShort());
                MascotActionTableData.Animation rotate = readKeyedRotations(reader);
                MascotActionTableData.RollAnimation roll = new MascotActionTableData.RollAnimation(1);
                roll.set(0, 0, reader.readShort());
                return new MascotActionTableData.BoneAction(type, null, translate, null, rotate, roll);
            }
            case 4 -> {
                MascotActionTableData.Animation rotate = readKeyedRotations(reader);
                MascotActionTableData.RollAnimation roll = readKeyedRolls(reader);
                return new MascotActionTableData.BoneAction(type, null, null, null, rotate, roll);
            }
            case 5 -> {
                MascotActionTableData.Animation rotate = readKeyedRotations(reader);
                return new MascotActionTableData.BoneAction(type, null, null, null, rotate, null);
            }
            case 6 -> {
                MascotActionTableData.Animation translate = readKeyedTranslations(reader);
                MascotActionTableData.Animation rotate = readKeyedRotations(reader);
                MascotActionTableData.RollAnimation roll = readKeyedRolls(reader);
                return new MascotActionTableData.BoneAction(type, null, translate, null, rotate, roll);
            }
            default -> throw new IOException("Unsupported bone animation type: " + type);
        }
    }

    private static MascotActionTableData.Animation readKeyedTranslations(Reader reader) throws IOException {
        int count = reader.readUShort();
        MascotActionTableData.Animation animation = new MascotActionTableData.Animation(count);
        for (int i = 0; i < count; i++) {
            animation.set(i, reader.readUShort(), reader.readShort(), reader.readShort(), reader.readShort());
        }
        return animation;
    }

    private static MascotActionTableData.Animation readKeyedScales(Reader reader) throws IOException {
        int count = reader.readUShort();
        MascotActionTableData.Animation animation = new MascotActionTableData.Animation(count);
        for (int i = 0; i < count; i++) {
            animation.set(
                    i,
                    reader.readUShort(),
                    reader.readShort(),
                    reader.readShort(),
                    reader.readShort()
            );
        }
        return animation;
    }

    private static MascotActionTableData.Animation readKeyedRotations(Reader reader) throws IOException {
        int count = reader.readUShort();
        MascotActionTableData.Animation animation = new MascotActionTableData.Animation(count);
        for (int i = 0; i < count; i++) {
            animation.set(i, reader.readUShort(), reader.readShort(), reader.readShort(), reader.readShort());
        }
        return animation;
    }

    private static MascotActionTableData.RollAnimation readKeyedRolls(Reader reader) throws IOException {
        int count = reader.readUShort();
        MascotActionTableData.RollAnimation animation = new MascotActionTableData.RollAnimation(count);
        for (int i = 0; i < count; i++) {
            animation.set(i, reader.readUShort(), reader.readShort());
        }
        return animation;
    }

    private static void assignPatterns(int[][][] patterns, Poly[] texturePolys, Poly[] colorPolys, int numTextures, int numPolyT3) {
        int c3 = 0;
        int c4 = java.lang.Math.min(colorPolys.length, countTriangles(colorPolys));
        int t3 = 0;
        int t4 = java.lang.Math.min(texturePolys.length, numPolyT3);
        for (int i = 0; i < patterns.length; i++) {
            int patternMask = i == 0 ? 0 : 1 << i;
            int[][] pattern = patterns[i];
            int colorTriangleCount = pattern[0][0];
            int colorQuadCount = pattern[0][1];
            for (int j = 0; j < colorTriangleCount && c3 < colorPolys.length; j++) {
                colorPolys[c3++].patternMask = patternMask;
            }
            for (int j = 0; j < colorQuadCount && c4 < colorPolys.length; j++) {
                colorPolys[c4++].patternMask = patternMask;
            }
            for (int face = 0; face < numTextures; face++) {
                int texturedTriangleCount = pattern[face + 1][0];
                int texturedQuadCount = pattern[face + 1][1];
                for (int j = 0; j < texturedTriangleCount && t3 < texturePolys.length; j++) {
                    texturePolys[t3].patternMask = patternMask;
                    texturePolys[t3].textureIndex = face;
                    t3++;
                }
                for (int j = 0; j < texturedQuadCount && t4 < texturePolys.length; j++) {
                    texturePolys[t4].patternMask = patternMask;
                    texturePolys[t4].textureIndex = face;
                    t4++;
                }
            }
        }
    }

    private static int countTriangles(Poly[] polys) {
        int count = 0;
        for (Poly poly : polys) {
            if (poly.indices.length == 3) {
                count++;
            }
        }
        return count;
    }

    private static Poly[] readColorPolygons(Reader reader, int numVertices, int numColors, int triangleCount, int quadCount) throws IOException {
        int materialBits = reader.readUByte();
        int vertexIndexBits = reader.readUByte();
        int colorBits = reader.readUByte();
        int colorIdBits = reader.readUByte();
        reader.readUByte();

        byte[] colors = new byte[numColors * 3];
        for (int i = 0; i < colors.length; i++) {
            colors[i] = (byte) reader.readUBits(colorBits);
        }

        Poly[] result = new Poly[triangleCount + quadCount];
        for (int i = 0; i < triangleCount; i++) {
            int material = reader.readUBits(materialBits) << 1;
            int a = reader.readUBits(vertexIndexBits);
            int b = reader.readUBits(vertexIndexBits);
            int c = reader.readUBits(vertexIndexBits);
            verifyVertexIndices(numVertices, a, b, c);
            int colorId = reader.readUBits(colorIdBits) * 3;
            result[i] = Poly.colored(new int[]{a, b, c}, colors[colorId] & 0xFF, colors[colorId + 1] & 0xFF, colors[colorId + 2] & 0xFF, material);
        }
        for (int i = 0; i < quadCount; i++) {
            int material = reader.readUBits(materialBits) << 1;
            int a = reader.readUBits(vertexIndexBits);
            int b = reader.readUBits(vertexIndexBits);
            int c = reader.readUBits(vertexIndexBits);
            int d = reader.readUBits(vertexIndexBits);
            verifyVertexIndices(numVertices, a, b, c, d);
            int colorId = reader.readUBits(colorIdBits) * 3;
            result[triangleCount + i] = Poly.colored(new int[]{a, b, c, d}, colors[colorId] & 0xFF, colors[colorId + 1] & 0xFF, colors[colorId + 2] & 0xFF, material);
        }
        return result;
    }

    private static Poly[] readTexturePolygonsV1(Reader reader, int numVertices, int triangleCount, int quadCount) throws IOException {
        Poly[] result = new Poly[triangleCount + quadCount];
        for (int i = 0; i < triangleCount; i++) {
            int material = reader.readUShort();
            int a = reader.readUShort();
            int b = reader.readUShort();
            int c = reader.readUShort();
            verifyVertexIndices(numVertices, a, b, c);
            float[] uv = new float[]{
                    rawUv(reader.readByte()), rawUv(reader.readByte()),
                    rawUv(reader.readByte()), rawUv(reader.readByte()),
                    rawUv(reader.readByte()), rawUv(reader.readByte())
            };
            result[i] = Poly.textured(new int[]{a, b, c}, uv, material);
        }
        for (int i = 0; i < quadCount; i++) {
            int material = reader.readUShort();
            int a = reader.readUShort();
            int b = reader.readUShort();
            int c = reader.readUShort();
            int d = reader.readUShort();
            verifyVertexIndices(numVertices, a, b, c, d);
            float uA = rawUv(reader.readByte());
            float vA = rawUv(reader.readByte());
            float uB = rawUv(reader.readByte());
            float vB = rawUv(reader.readByte());
            float uC = rawUv(reader.readByte());
            float vC = rawUv(reader.readByte());
            float uD = rawUv(reader.readByte());
            float vD = rawUv(reader.readByte());
            float[] uv = new float[]{uA, vA, uB, vB, uC, vC, uD, vD};
            result[triangleCount + i] = Poly.textured(new int[]{a, b, c, d}, uv, material);
        }
        return result;
    }

    private static Poly[] readTexturePolygonsV2(Reader reader, int numVertices, int triangleCount, int quadCount) throws IOException {
        int materialBits = reader.readUByte();
        int vertexIndexBits = reader.readUByte();
        Poly[] result = new Poly[triangleCount + quadCount];
        for (int i = 0; i < triangleCount; i++) {
            int material = reader.readUBits(materialBits);
            int a = reader.readUBits(vertexIndexBits);
            int b = reader.readUBits(vertexIndexBits);
            int c = reader.readUBits(vertexIndexBits);
            verifyVertexIndices(numVertices, a, b, c);
            float[] uv = new float[]{
                    rawUv(reader.readUBits(7)), rawUv(reader.readUBits(7)),
                    rawUv(reader.readUBits(7)), rawUv(reader.readUBits(7)),
                    rawUv(reader.readUBits(7)), rawUv(reader.readUBits(7))
            };
            result[i] = Poly.textured(new int[]{a, b, c}, uv, material);
        }
        for (int i = 0; i < quadCount; i++) {
            int material = reader.readUBits(materialBits);
            int a = reader.readUBits(vertexIndexBits);
            int b = reader.readUBits(vertexIndexBits);
            int c = reader.readUBits(vertexIndexBits);
            int d = reader.readUBits(vertexIndexBits);
            verifyVertexIndices(numVertices, a, b, c, d);
            float uA = rawUv(reader.readUBits(7));
            float vA = rawUv(reader.readUBits(7));
            float uB = rawUv(reader.readUBits(7));
            float vB = rawUv(reader.readUBits(7));
            float uC = rawUv(reader.readUBits(7));
            float vC = rawUv(reader.readUBits(7));
            float uD = rawUv(reader.readUBits(7));
            float vD = rawUv(reader.readUBits(7));
            float[] uv = new float[]{uA, vA, uB, vB, uC, vC, uD, vD};
            result[triangleCount + i] = Poly.textured(new int[]{a, b, c, d}, uv, material);
        }
        return result;
    }

    private static Poly[] readTexturePolygonsV3(Reader reader, int numVertices, int triangleCount, int quadCount) throws IOException {
        int materialBits = reader.readUBits(8);
        int vertexIndexBits = reader.readUBits(8);
        int uvBits = reader.readUBits(8);
        reader.readUBits(8);
        Poly[] result = new Poly[triangleCount + quadCount];
        for (int i = 0; i < triangleCount; i++) {
            int material = reader.readUBits(materialBits);
            int a = reader.readUBits(vertexIndexBits);
            int b = reader.readUBits(vertexIndexBits);
            int c = reader.readUBits(vertexIndexBits);
            verifyVertexIndices(numVertices, a, b, c);
            float[] uv = new float[]{
                    rawUv(reader.readUBits(uvBits)), rawUv(reader.readUBits(uvBits)),
                    rawUv(reader.readUBits(uvBits)), rawUv(reader.readUBits(uvBits)),
                    rawUv(reader.readUBits(uvBits)), rawUv(reader.readUBits(uvBits))
            };
            result[i] = Poly.textured(new int[]{a, b, c}, uv, material);
        }
        for (int i = 0; i < quadCount; i++) {
            int material = reader.readUBits(materialBits);
            int a = reader.readUBits(vertexIndexBits);
            int b = reader.readUBits(vertexIndexBits);
            int c = reader.readUBits(vertexIndexBits);
            int d = reader.readUBits(vertexIndexBits);
            verifyVertexIndices(numVertices, a, b, c, d);
            float uA = rawUv(reader.readUBits(uvBits));
            float vA = rawUv(reader.readUBits(uvBits));
            float uB = rawUv(reader.readUBits(uvBits));
            float vB = rawUv(reader.readUBits(uvBits));
            float uC = rawUv(reader.readUBits(uvBits));
            float vC = rawUv(reader.readUBits(uvBits));
            float uD = rawUv(reader.readUBits(uvBits));
            float vD = rawUv(reader.readUBits(uvBits));
            float[] uv = new float[]{uA, vA, uB, vB, uC, vC, uD, vD};
            result[triangleCount + i] = Poly.textured(new int[]{a, b, c, d}, uv, material);
        }
        return result;
    }

    private static void readVerticesV1(Reader reader, int[] vertices) throws IOException {
        for (int i = 0; i < vertices.length; i++) {
            vertices[i] = reader.readShort();
        }
    }

    private static void readVerticesV2(Reader reader, int[] vertices) throws IOException {
        int index = 0;
        while (index < vertices.length) {
            int chunk = reader.readUBits(8);
            int type = chunk >> 6;
            int size = SIZE_TABLE[type];
            int count = (chunk & 0x3F) + 1;
            for (int i = 0; i < count && index < vertices.length; i++) {
                vertices[index++] = reader.readBits(size);
                vertices[index++] = reader.readBits(size);
                vertices[index++] = reader.readBits(size);
            }
        }
    }

    private static void readNormalsV1(Reader reader, int numVertices) throws IOException {
        for (int i = 0; i < numVertices * 3; i++) {
            reader.readShort();
        }
    }

    private static void readNormalsV2(Reader reader, int numVertices) throws IOException {
        for (int i = 0; i < numVertices; i++) {
            int x = reader.readUBits(7);
            if (x == 64) {
                int type = reader.readUBits(3);
                if (type > 5) {
                    throw new IOException("Invalid normal type");
                }
                type++;
                type++;
                type++;
                continue;
            }
            reader.readUBits(7);
            reader.readUBits(1);
        }
    }

    private static void verifyVertexIndices(int numVertices, int... indices) throws IOException {
        for (int index : indices) {
            if (index < 0 || index >= numVertices) {
                throw new IOException("Vertex index out of range");
            }
        }
    }

    private static float rawUv(byte raw) {
        return raw & 0xFF;
    }

    private static float rawUv(int raw) {
        return raw;
    }

    private static final class Poly {
        private final int[] indices;
        private final float[] textureCoords;
        private final int color;
        private final int blendMode;
        private final boolean doubleSided;
        private final boolean transparent;
        private int textureIndex = -1;
        private int patternMask;

        private Poly(int[] indices, float[] textureCoords, int color, int blendMode, boolean doubleSided, boolean transparent) {
            this.indices = indices;
            this.textureCoords = textureCoords;
            this.color = color;
            this.blendMode = blendMode;
            this.doubleSided = doubleSided;
            this.transparent = transparent;
        }

        static Poly textured(int[] indices, float[] textureCoords, int material) {
            return new Poly(indices, textureCoords, 0, material & MATERIAL_BLEND_MASK,
                    (material & MATERIAL_DOUBLE_SIDED) != 0,
                    (material & MATERIAL_TRANSPARENT) != 0);
        }

        static Poly colored(int[] indices, int r, int g, int b, int material) {
            return new Poly(indices, null, 0xFF000000 | (r << 16) | (g << 8) | b, material & MATERIAL_BLEND_MASK,
                    (material & MATERIAL_DOUBLE_SIDED) != 0,
                    (material & MATERIAL_TRANSPARENT) != 0);
        }

        MbacModel.Polygon toPolygon() {
            return new MbacModel.Polygon(indices, textureCoords, color, textureIndex, patternMask, blendMode, doubleSided, transparent);
        }
    }

    private static final class Reader {
        private final byte[] bytes;
        private int pos;
        private int bitCache;
        private int cachedBits;

        private Reader(byte[] bytes) {
            this.bytes = bytes;
        }

        private byte readByte() throws IOException {
            if (pos >= bytes.length) {
                throw new EOFException();
            }
            return bytes[pos++];
        }

        private int readUByte() throws IOException {
            return readByte() & 0xFF;
        }

        private short readShort() throws IOException {
            if (pos + 1 >= bytes.length) {
                throw new EOFException();
            }
            return (short) ((bytes[pos++] & 0xFF) | (bytes[pos++] << 8));
        }

        private int readUShort() throws IOException {
            if (pos + 1 >= bytes.length) {
                throw new EOFException();
            }
            return (bytes[pos++] & 0xFF) | ((bytes[pos++] & 0xFF) << 8);
        }

        private int readInt() throws IOException {
            if (pos + 3 >= bytes.length) {
                throw new EOFException();
            }
            return (bytes[pos++] & 0xFF) | ((bytes[pos++] & 0xFF) << 8) | ((bytes[pos++] & 0xFF) << 16) | (bytes[pos++] << 24);
        }

        private int readUBits(int size) throws IOException {
            while (size > cachedBits) {
                bitCache |= readUByte() << cachedBits;
                cachedBits += 8;
            }
            int mask = ~(0xFFFFFFFF << size);
            int value = bitCache & mask;
            bitCache >>>= size;
            cachedBits -= size;
            return value;
        }

        private int readBits(int size) throws IOException {
            int shift = 32 - size;
            return (readUBits(size) << shift) >> shift;
        }

        private void clearBits() {
            bitCache = 0;
            cachedBits = 0;
        }
    }
}
