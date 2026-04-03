package com.nttdocomo.ui.graphics3d;

import opendoja.g3d.FixedPoint;
import opendoja.g3d.SoftwareTexture;

/**
 * Defines the primitive object that stores vertex and attribute data for
 * points, lines, polygons, and point sprites.
 */
public class Primitive extends DrawableObject3D {
    /**
     * Primitive type for points.
     */
    public static final int PRIMITIVE_POINTS = 1;
    /**
     * Primitive type for lines.
     */
    public static final int PRIMITIVE_LINES = 2;
    /**
     * Primitive type for triangles.
     */
    public static final int PRIMITIVE_TRIANGLES = 3;
    /**
     * Primitive type for quadrilaterals.
     */
    public static final int PRIMITIVE_QUADS = 4;
    /**
     * Primitive type for point sprites.
     */
    public static final int PRIMITIVE_POINT_SPRITES = 5;
    /**
     * Flag indicating that no normal data is stored.
     */
    public static final int NORMAL_NONE = 0;
    /**
     * Flag indicating that normals are stored per face.
     */
    public static final int NORMAL_PER_FACE = 512;
    /**
     * Flag indicating that normals are stored per vertex.
     */
    public static final int NORMAL_PER_VERTEX = 768;
    /**
     * Flag indicating that no color data is stored.
     */
    public static final int COLOR_NONE = 0;
    /**
     * Flag indicating that a single color is stored per primitive.
     */
    public static final int COLOR_PER_PRIMITIVE = 1024;
    /**
     * Flag indicating that color data is stored per face.
     */
    public static final int COLOR_PER_FACE = 2048;
    /**
     * Flag indicating that no texture coordinates are stored.
     */
    public static final int TEXTURE_COORD_NONE = 0;
    /**
     * Flag indicating that texture coordinates are stored per vertex.
     */
    public static final int TEXTURE_COORD_PER_VERTEX = 12288;
    /**
     * Flag enabling the transparent color in the assigned texture.
     */
    public static final int TEXTURE_COLORKEY = 16;
    /**
     * Flag indicating that a single point-sprite definition is stored per primitive.
     */
    public static final int POINT_SPRITE_PER_PRIMITIVE = 4096;
    /**
     * Flag indicating that point-sprite data is stored per vertex.
     */
    public static final int POINT_SPRITE_PER_VERTEX = 12288;
    /**
     * Point-sprite flag indicating that size is specified in local coordinates.
     */
    public static final int POINT_SPRITE_FLAG_LOCAL_SIZE = 0;
    /**
     * Point-sprite flag indicating that size is specified in pixels.
     */
    public static final int POINT_SPRITE_FLAG_PIXEL_SIZE = 1;
    /**
     * Point-sprite flag indicating that perspective scaling is enabled.
     */
    public static final int POINT_SPRITE_FLAG_PERSPECTIVE = 0;
    /**
     * Point-sprite flag indicating that perspective scaling is disabled.
     */
    public static final int POINT_SPRITE_FLAG_NO_PERSPECTIVE = 2;

    private final int primitiveType;
    private final int primitiveParam;
    private final int primitiveCount;
    private final int[] vertexArray;
    private final int[] normalArray;
    private final int[] colorArray;
    private final int[] textureCoordArray;
    private final int[] pointSpriteArray;
    private Texture texture;

    /**
     * Creates a primitive object with the specified type, parameter flags, and
     * primitive count.
     *
     * @param primitiveType the primitive type
     * @param primitiveParam the primitive parameter flags
     * @param primitiveCount the number of primitives
     */
    public Primitive(int primitiveType, int primitiveParam, int primitiveCount) {
        super(TYPE_PRIMITIVE);
        this.primitiveType = primitiveType;
        this.primitiveParam = primitiveParam;
        this.primitiveCount = primitiveCount;
        int verticesPerPrimitive = switch (primitiveType) {
            case PRIMITIVE_POINTS, PRIMITIVE_POINT_SPRITES -> 1;
            case PRIMITIVE_LINES -> 2;
            case PRIMITIVE_TRIANGLES -> 3;
            case PRIMITIVE_QUADS -> 4;
            default -> throw new IllegalArgumentException("primitiveType");
        };
        int vertexCount = primitiveCount * verticesPerPrimitive;
        this.vertexArray = new int[vertexCount * 3];
        int normalMode = primitiveParam & 0x0300;
        this.normalArray = switch (normalMode) {
            case NORMAL_PER_FACE -> new int[primitiveCount * 3];
            case NORMAL_PER_VERTEX -> new int[vertexCount * 3];
            default -> null;
        };
        int colorMode = primitiveParam & 0x0C00;
        this.colorArray = switch (colorMode) {
            case COLOR_PER_PRIMITIVE, COLOR_PER_FACE -> new int[primitiveCount];
            default -> null;
        };
        this.textureCoordArray = (primitiveParam & 0x3000) == TEXTURE_COORD_PER_VERTEX ? new int[vertexCount * 2] : null;
        int pointSpriteMode = primitiveParam & 0x3000;
        this.pointSpriteArray = primitiveType == PRIMITIVE_POINT_SPRITES && pointSpriteMode != 0 ? new int[primitiveCount * 2] : null;
    }

    /**
     * Gets the primitive type.
     *
     * @return the primitive type
     */
    public int getPrimitiveType() {
        return primitiveType;
    }

    /**
     * Gets the primitive parameter flags.
     *
     * @return the primitive parameter flags
     */
    public int getPrimitiveParam() {
        return primitiveParam;
    }

    /**
     * Gets the number of primitives stored in this object.
     *
     * @return the primitive count
     */
    public int size() {
        return primitiveCount;
    }

    /**
     * Gets the vertex array.
     *
     * @return the vertex array
     */
    public int[] getVertexArray() {
        return vertexArray;
    }

    /**
     * Gets the normal array.
     *
     * @return the normal array, or {@code null} if no normals are stored
     */
    public int[] getNormalArray() {
        return normalArray;
    }

    /**
     * Gets the color array.
     *
     * @return the color array, or {@code null} if no colors are stored
     */
    public int[] getColorArray() {
        return colorArray;
    }

    /**
     * Gets the texture-coordinate array.
     *
     * @return the texture-coordinate array, or {@code null} if no texture coordinates are stored
     */
    public int[] getTextureCoordArray() {
        return textureCoordArray;
    }

    /**
     * Gets the point-sprite array.
     *
     * @return the point-sprite array, or {@code null} if no point-sprite data is stored
     */
    public int[] getPointSpriteArray() {
        return pointSpriteArray;
    }

    /**
     * Normalizes a 3-component fixed-point vector in-place.
     *
     * @param vector the vector array
     * @param offset the offset of the x component
     */
    public static void normalize(int[] vector, int offset) {
        int x = vector[offset];
        int y = vector[offset + 1];
        int z = vector[offset + 2];
        int length = FixedPoint.sqrt(x * x + y * y + z * z);
        if (length == 0) {
            vector[offset] = 0;
            vector[offset + 1] = 0;
            vector[offset + 2] = FixedPoint.ONE;
            return;
        }
        vector[offset] = (x << 12) / length;
        vector[offset + 1] = (y << 12) / length;
        vector[offset + 2] = (z << 12) / length;
    }

    /**
     * Converts a radian angle to the integer angle representation used by the
     * engine.
     *
     * @param angle the angle in radians
     * @return the converted integer angle
     */
    public static int convertAngle(float angle) {
        return (int) java.lang.Math.round(angle * 2048.0 / java.lang.Math.PI);
    }

    /**
     * Converts an engine integer angle to radians.
     *
     * @param angle the engine integer angle
     * @return the angle in radians
     */
    public static float convertAngle(int angle) {
        return (float) (angle * java.lang.Math.PI / 2048.0);
    }

    /**
     * Sets the texture associated with this primitive object.
     *
     * @param texture the texture object
     */
    public void setTexture(Texture texture) {
        this.texture = texture;
    }

    /**
     * Enables or disables perspective correction.
     *
     * @param enabled {@code true} to enable perspective correction
     */
    @Override
    public void setPerspectiveCorrectionEnabled(boolean enabled) {
        setPerspectiveCorrectionEnabledInternal(enabled);
    }

    /**
     * Sets the primitive blend mode.
     *
     * @param blendMode the blend mode
     */
    @Override
    public void setBlendMode(int blendMode) {
        setBlendModeInternal(blendMode);
    }

    /**
     * Sets the transparency percentage.
     *
     * @param transparency the transparency value
     */
    @Override
    public void setTransparency(float transparency) {
        setTransparencyInternal(transparency);
    }

    Texture texture() {
        return texture;
    }

    SoftwareTexture textureHandle() {
        return texture == null ? null : texture.handle();
    }
}
