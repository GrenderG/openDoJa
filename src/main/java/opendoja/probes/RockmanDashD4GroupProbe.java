package opendoja.probes;

import com.nttdocomo.ui.graphics3d.Group;
import com.nttdocomo.ui.graphics3d.Object3D;
import com.nttdocomo.ui.graphics3d.Primitive;
import com.nttdocomo.ui.util3d.Transform;
import opendoja.g3d.SoftwareTexture;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.IdentityHashMap;

public final class RockmanDashD4GroupProbe {
    // Captured from the fixture title's D4 creation path after the byte buffer is assembled.
    private static final String D4_FIXTURE = "opendoja/probes/rockman_dash/a5_1.d4";
    private static final float EXPECTED_GROUP_TRANSLATE_X = -12.0f;
    private static final float EXPECTED_GROUP_TRANSLATE_Y = 16.25f;
    private static final float EXPECTED_GROUP_TRANSLATE_Z = 7.0f;
    private static final float EXPECTED_GROUP_SCALE = 0.003112888f;
    private static final float EXPECTED_MIN_X = -96.00128f;
    private static final float EXPECTED_MAX_X = 72.00128f;
    private static final float EXPECTED_MIN_Y = -0.50045f;
    private static final float EXPECTED_MAX_Y = 33.00045f;
    private static final float EXPECTED_MIN_Z = -95.0f;
    private static final float EXPECTED_MAX_Z = 109.0f;
    private static final float TRANSFORM_EPSILON = 0.00001f;
    private static final float BOUNDS_EPSILON = 0.01f;
    private static final float TEXTURE_TRANSLATION_EPSILON = 0.02f;
    private static final ExpectedPrimitive[] EXPECTED_PRIMITIVES = {
            new ExpectedPrimitive(128, 128, 1, 126, 0, 100, new int[]{1, 17, 63, 17, 63, 74},
                    new int[]{0xffffffff, 0xffffffff, 0xffbfbfbf}, 102, 0),
            new ExpectedPrimitive(64, 64, 0, 64, -58, 134, new int[]{0, -58, 0, 134, 64, 134},
                    new int[]{0xffc0c0c0, 0xffc0c0c0, 0xffc0c0c0}, 12, 0),
            new ExpectedPrimitive(256, 256, -512, 768, -448, 256, new int[]{0, 256, 0, 128, 256, 128},
                    new int[]{0xffbf7f3f, 0xffbf7f3f, 0xffbf7f3f}, 2317, 0),
            new ExpectedPrimitive(128, 128, 0, 128, 0, 128, new int[]{96, 128, 83, 128, 83, 11},
                    new int[]{0xffffbf7f, 0xffffbf7f, 0xff8c8c8c}, 798, 0),
            new ExpectedPrimitive(32, 32, -224, 224, -144, 208, new int[]{128, 112, 128, 48, 32, -48},
                    new int[]{0x6c999999, 0x6cffffff, 0x6cffffff}, 477, 477),
            new ExpectedPrimitive(32, 32, -240, 240, -160, 224, new int[]{-48, -128, -32, 160, -32, -96},
                    new int[]{0x0cffbf7f, 0x6cff7e18, 0x6cff7e18}, 2063, 1743),
            new ExpectedPrimitive(64, 64, 0, 26, 45, 63, new int[]{26, 63, 0, 63, 13, 45},
                    new int[]{0xffbf5959, 0xffbf5959, 0xffffffff}, 108, 0),
            new ExpectedPrimitive(32, 32, -103, 103, -128, 271, new int[]{-48, -64, 0, -12, 48, -64},
                    new int[]{0xff000000, 0xffffffff, 0xffffffff}, 251, 0)
    };

    private RockmanDashD4GroupProbe() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        DemoLog.enableInfoLogging();

        byte[] data = readFixture();
        Object3D loaded = Object3D.createInstance(data);
        if (!(loaded instanceof Group group)) {
            throw new IllegalStateException("Expected Group from captured Rockman D4 fixture, got "
                    + (loaded == null ? "null" : loaded.getClass().getName()));
        }
        if (group.getType() != Object3D.TYPE_GROUP_MESH) {
            throw new IllegalStateException("Expected TYPE_GROUP_MESH, got " + group.getType());
        }
        if (group.getNumElements() != 8) {
            throw new IllegalStateException("Expected 8 mesh primitives, got " + group.getNumElements());
        }
        verifyGroupTransform(group);

        Method textureHandle = Primitive.class.getDeclaredMethod("textureHandle");
        textureHandle.setAccessible(true);
        Method textureWrapEnabled = Primitive.class.getDeclaredMethod("textureWrapEnabled");
        textureWrapEnabled.setAccessible(true);
        Method textureCoordinateTranslateU = Primitive.class.getDeclaredMethod("textureCoordinateTranslateU");
        textureCoordinateTranslateU.setAccessible(true);
        Method textureCoordinateTranslateV = Primitive.class.getDeclaredMethod("textureCoordinateTranslateV");
        textureCoordinateTranslateV.setAccessible(true);
        IdentityHashMap<SoftwareTexture, Boolean> verifiedTextures = new IdentityHashMap<>();
        int primitiveCount = 0;
        int triangleCount = 0;
        for (int i = 0; i < group.getNumElements(); i++) {
            if (!(group.getElement(i) instanceof Primitive primitive)) {
                throw new IllegalStateException("Expected only Primitive children, got " + group.getElement(i).getClass().getName());
            }
            SoftwareTexture texture = (SoftwareTexture) textureHandle.invoke(primitive);
            if (texture == null) {
                throw new IllegalStateException("Primitive " + i + " lost its texture");
            }
            if (!verifiedTextures.containsKey(texture)) {
                verifyTextureHasOpaquePixels(i, texture);
                verifiedTextures.put(texture, Boolean.TRUE);
            }
            verifyDecodedPrimitive(i, primitive, texture, textureWrapEnabled);
            primitiveCount++;
            triangleCount += primitive.size();
        }
        if (triangleCount <= 0) {
            throw new IllegalStateException("Decoded group produced no triangles");
        }
        verifyTextureAnimation(group, textureCoordinateTranslateU, textureCoordinateTranslateV);
        verifyTransformedBounds(group);

        group.setPerspectiveCorrectionEnabled(true);
        DemoLog.info(RockmanDashD4GroupProbe.class,
                "groupType=" + group.getType() + " bytes=" + data.length
                        + " elements=" + primitiveCount + " triangles=" + triangleCount);
    }

    private static byte[] readFixture() throws Exception {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(D4_FIXTURE)) {
            if (in == null) {
                throw new IllegalStateException("Missing D4 fixture resource: " + D4_FIXTURE);
            }
            return in.readAllBytes();
        }
    }

    private static void verifyGroupTransform(Group group) {
        Transform transform = new Transform();
        group.getTransform(transform);
        assertClose("group scale x", transform.get(0), EXPECTED_GROUP_SCALE, TRANSFORM_EPSILON);
        assertClose("group scale y", transform.get(5), EXPECTED_GROUP_SCALE, TRANSFORM_EPSILON);
        assertClose("group scale z", transform.get(10), EXPECTED_GROUP_SCALE, TRANSFORM_EPSILON);
        assertClose("group translate x", transform.get(3), EXPECTED_GROUP_TRANSLATE_X, TRANSFORM_EPSILON);
        assertClose("group translate y", transform.get(7), EXPECTED_GROUP_TRANSLATE_Y, TRANSFORM_EPSILON);
        assertClose("group translate z", transform.get(11), EXPECTED_GROUP_TRANSLATE_Z, TRANSFORM_EPSILON);
    }

    private static void verifyDecodedPrimitive(int primitiveIndex, Primitive primitive, SoftwareTexture texture,
                                               Method textureWrapEnabled) throws Exception {
        ExpectedPrimitive expected = EXPECTED_PRIMITIVES[primitiveIndex];
        if (texture.width() != expected.textureWidth || texture.height() != expected.textureHeight) {
            throw new IllegalStateException("Primitive " + primitiveIndex + " texture size mismatch: "
                    + texture.width() + "x" + texture.height());
        }
        if (!Boolean.TRUE.equals(textureWrapEnabled.invoke(primitive))) {
            throw new IllegalStateException("Primitive " + primitiveIndex + " lost its D4 texture wrap flag");
        }
        int[] uvs = primitive.getTextureCoordArray();
        if (uvs == null) {
            throw new IllegalStateException("Primitive " + primitiveIndex + " has no texture coordinates");
        }
        int minU = Integer.MAX_VALUE;
        int maxU = Integer.MIN_VALUE;
        int minV = Integer.MAX_VALUE;
        int maxV = Integer.MIN_VALUE;
        for (int i = 0; i + 1 < uvs.length; i += 2) {
            minU = java.lang.Math.min(minU, uvs[i]);
            maxU = java.lang.Math.max(maxU, uvs[i]);
            minV = java.lang.Math.min(minV, uvs[i + 1]);
            maxV = java.lang.Math.max(maxV, uvs[i + 1]);
        }
        if (minU != expected.minU || maxU != expected.maxU || minV != expected.minV || maxV != expected.maxV) {
            throw new IllegalStateException("Primitive " + primitiveIndex + " UV span mismatch: ["
                    + minU + "," + maxU + "] x [" + minV + "," + maxV + "]");
        }
        int[] firstTriangle = Arrays.copyOf(uvs, expected.firstTriangleUvs.length);
        if (!Arrays.equals(firstTriangle, expected.firstTriangleUvs)) {
            throw new IllegalStateException("Primitive " + primitiveIndex + " first triangle UV mismatch: "
                    + Arrays.toString(firstTriangle));
        }
        if ((primitive.getPrimitiveParam() & 0x0C00) != Primitive.COLOR_PER_VERTEX_INTERNAL) {
            throw new IllegalStateException("Primitive " + primitiveIndex + " lost its D4 per-vertex color mode");
        }
        int[] colors = primitive.getColorArray();
        if (colors == null || colors.length == 0) {
            throw new IllegalStateException("Primitive " + primitiveIndex + " has no decoded D4 vertex colors");
        }
        int[] firstTriangleColors = Arrays.copyOf(colors, expected.firstTriangleColors.length);
        if (!Arrays.equals(firstTriangleColors, expected.firstTriangleColors)) {
            throw new IllegalStateException("Primitive " + primitiveIndex + " first triangle color mismatch: "
                    + Arrays.toString(firstTriangleColors));
        }
        int nonWhite = 0;
        int translucent = 0;
        for (int color : colors) {
            if (color != 0xFFFFFFFF) {
                nonWhite++;
            }
            if (((color >>> 24) & 0xFF) != 0xFF) {
                translucent++;
            }
        }
        if (nonWhite != expected.nonWhiteVertexColors || translucent != expected.translucentVertexColors) {
            throw new IllegalStateException("Primitive " + primitiveIndex + " vertex color stats mismatch: nonWhite="
                    + nonWhite + " translucent=" + translucent);
        }
    }

    private static void verifyTransformedBounds(Group group) {
        Transform transform = new Transform();
        group.getTransform(transform);
        float scaleX = transform.get(0);
        float scaleY = transform.get(5);
        float scaleZ = transform.get(10);
        float translateX = transform.get(3);
        float translateY = transform.get(7);
        float translateZ = transform.get(11);
        float minX = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < group.getNumElements(); i++) {
            Primitive primitive = (Primitive) group.getElement(i);
            int[] vertices = primitive.getVertexArray();
            for (int vertex = 0; vertex + 2 < vertices.length; vertex += 3) {
                float x = vertices[vertex] * scaleX + translateX;
                float y = vertices[vertex + 1] * scaleY + translateY;
                float z = vertices[vertex + 2] * scaleZ + translateZ;
                minX = java.lang.Math.min(minX, x);
                maxX = java.lang.Math.max(maxX, x);
                minY = java.lang.Math.min(minY, y);
                maxY = java.lang.Math.max(maxY, y);
                minZ = java.lang.Math.min(minZ, z);
                maxZ = java.lang.Math.max(maxZ, z);
            }
        }
        assertClose("world minX", minX, EXPECTED_MIN_X, BOUNDS_EPSILON);
        assertClose("world maxX", maxX, EXPECTED_MAX_X, BOUNDS_EPSILON);
        assertClose("world minY", minY, EXPECTED_MIN_Y, BOUNDS_EPSILON);
        assertClose("world maxY", maxY, EXPECTED_MAX_Y, BOUNDS_EPSILON);
        assertClose("world minZ", minZ, EXPECTED_MIN_Z, BOUNDS_EPSILON);
        assertClose("world maxZ", maxZ, EXPECTED_MAX_Z, BOUNDS_EPSILON);
    }

    private static void verifyTextureHasOpaquePixels(int primitiveIndex, SoftwareTexture texture) {
        int opaque = 0;
        for (int y = 0; y < texture.height(); y++) {
            for (int x = 0; x < texture.width(); x++) {
                if (((texture.image().getRGB(x, y) >>> 24) & 0xFF) != 0) {
                    opaque++;
                }
            }
        }
        if (opaque == 0) {
            throw new IllegalStateException("Primitive " + primitiveIndex + " texture decoded fully transparent");
        }
    }

    private static void verifyTextureAnimation(Group group, Method textureCoordinateTranslateU,
                                               Method textureCoordinateTranslateV) throws Exception {
        Primitive staticPrimitive = (Primitive) group.getElement(0);
        Primitive animatedPrimitive = (Primitive) group.getElement(7);

        group.setTime(0);
        assertClose("static texture translation U at t=0",
                ((Number) textureCoordinateTranslateU.invoke(staticPrimitive)).floatValue(), 0f, TEXTURE_TRANSLATION_EPSILON);
        assertClose("animated texture translation U at t=0",
                ((Number) textureCoordinateTranslateU.invoke(animatedPrimitive)).floatValue(), 0f, TEXTURE_TRANSLATION_EPSILON);
        assertClose("animated texture translation V at t=0",
                ((Number) textureCoordinateTranslateV.invoke(animatedPrimitive)).floatValue(), 0f, TEXTURE_TRANSLATION_EPSILON);

        group.setTime(483);
        assertClose("static texture translation V at t=483",
                ((Number) textureCoordinateTranslateV.invoke(staticPrimitive)).floatValue(), 0f, TEXTURE_TRANSLATION_EPSILON);
        assertClose("animated texture translation U at t=483",
                ((Number) textureCoordinateTranslateU.invoke(animatedPrimitive)).floatValue(), 0f, TEXTURE_TRANSLATION_EPSILON);
        assertClose("animated texture translation V at t=483",
                ((Number) textureCoordinateTranslateV.invoke(animatedPrimitive)).floatValue(),
                483f / 967f * 32f, TEXTURE_TRANSLATION_EPSILON);

        group.setTime(967);
        assertClose("looped animated texture translation V",
                ((Number) textureCoordinateTranslateV.invoke(animatedPrimitive)).floatValue(), 0f, TEXTURE_TRANSLATION_EPSILON);
        group.setTime(0);
    }

    private static void assertClose(String label, float actual, float expected, float epsilon) {
        if (java.lang.Math.abs(actual - expected) > epsilon) {
            throw new IllegalStateException(label + " mismatch: expected " + expected + ", got " + actual);
        }
    }

    private record ExpectedPrimitive(int textureWidth, int textureHeight, int minU, int maxU, int minV, int maxV,
                                     int[] firstTriangleUvs, int[] firstTriangleColors,
                                     int nonWhiteVertexColors, int translucentVertexColors) {
    }
}
