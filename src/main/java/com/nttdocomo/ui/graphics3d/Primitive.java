package com.nttdocomo.ui.graphics3d;

import opendoja.g3d.FixedPoint;
import opendoja.g3d.SoftwareTexture;

public class Primitive extends DrawableObject3D {
    public static final int PRIMITIVE_POINTS = 1;
    public static final int PRIMITIVE_LINES = 2;
    public static final int PRIMITIVE_TRIANGLES = 3;
    public static final int PRIMITIVE_QUADS = 4;
    public static final int PRIMITIVE_POINT_SPRITES = 5;
    public static final int NORMAL_NONE = 0;
    public static final int NORMAL_PER_FACE = 512;
    public static final int NORMAL_PER_VERTEX = 768;
    public static final int COLOR_NONE = 0;
    public static final int COLOR_PER_PRIMITIVE = 1024;
    public static final int COLOR_PER_FACE = 2048;
    public static final int TEXTURE_COORD_NONE = 0;
    public static final int TEXTURE_COORD_PER_VERTEX = 12288;
    public static final int TEXTURE_COLORKEY = 16;
    public static final int POINT_SPRITE_PER_PRIMITIVE = 4096;
    public static final int POINT_SPRITE_PER_VERTEX = 12288;
    public static final int POINT_SPRITE_FLAG_LOCAL_SIZE = 0;
    public static final int POINT_SPRITE_FLAG_PIXEL_SIZE = 1;
    public static final int POINT_SPRITE_FLAG_PERSPECTIVE = 0;
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
            default -> new int[0];
        };
        int colorMode = primitiveParam & 0x0C00;
        this.colorArray = switch (colorMode) {
            case COLOR_PER_PRIMITIVE, COLOR_PER_FACE -> new int[primitiveCount];
            default -> new int[0];
        };
        this.textureCoordArray = (primitiveParam & 0x3000) == TEXTURE_COORD_PER_VERTEX ? new int[vertexCount * 2] : new int[0];
        int pointSpriteMode = primitiveParam & 0x3000;
        this.pointSpriteArray = primitiveType == PRIMITIVE_POINT_SPRITES && pointSpriteMode != 0 ? new int[primitiveCount * 2] : new int[0];
    }

    public int getPrimitiveType() {
        return primitiveType;
    }

    public int getPrimitiveParam() {
        return primitiveParam;
    }

    public int size() {
        return primitiveCount;
    }

    public int[] getVertexArray() {
        return vertexArray;
    }

    public int[] getNormalArray() {
        return normalArray;
    }

    public int[] getColorArray() {
        return colorArray;
    }

    public int[] getTextureCoordArray() {
        return textureCoordArray;
    }

    public int[] getPointSpriteArray() {
        return pointSpriteArray;
    }

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

    public static int convertAngle(float angle) {
        return (int) java.lang.Math.round(angle * 2048.0 / java.lang.Math.PI);
    }

    public static float convertAngle(int angle) {
        return (float) (angle * java.lang.Math.PI / 2048.0);
    }

    public void setTexture(Texture texture) {
        this.texture = texture;
    }

    @Override
    public void setPerspectiveCorrectionEnabled(boolean enabled) {
        setPerspectiveCorrectionEnabledInternal(enabled);
    }

    @Override
    public void setBlendMode(int blendMode) {
        setBlendModeInternal(blendMode);
    }

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
