package com.nttdocomo.opt.ui.j3d;

public class PrimitiveArray {
    private final int type;
    private final int param;
    private final int size;
    private final int[] vertexArray;
    private final int[] normalArray;
    private final int[] colorArray;
    private final int[] textureCoordArray;
    private final int[] pointSpriteArray;

    public PrimitiveArray(int type, int param, int size) {
        this.type = type;
        this.param = param;
        this.size = size;
        int verticesPerPrimitive = switch (type) {
            case Graphics3D.PRIMITIVE_POINTS, Graphics3D.PRIMITIVE_POINT_SPRITES -> 1;
            case Graphics3D.PRIMITIVE_LINES -> 2;
            case Graphics3D.PRIMITIVE_TRIANGLES -> 3;
            case Graphics3D.PRIMITIVE_QUADS -> 4;
            default -> throw new IllegalArgumentException("type");
        };
        int vertexCount = size * verticesPerPrimitive;
        this.vertexArray = new int[vertexCount * 3];
        int normalMode = param & 0x0300;
        this.normalArray = switch (normalMode) {
            case Graphics3D.NORMAL_PER_FACE -> new int[size * 3];
            case Graphics3D.NORMAL_PER_VERTEX -> new int[vertexCount * 3];
            default -> new int[0];
        };
        int colorMode = param & 0x0C00;
        this.colorArray = switch (colorMode) {
            case Graphics3D.COLOR_PER_COMMAND, Graphics3D.COLOR_PER_FACE -> new int[size];
            default -> new int[0];
        };
        this.textureCoordArray = (param & 0x3000) == Graphics3D.TEXTURE_COORD_PER_VERTEX ? new int[vertexCount * 2] : new int[0];
        this.pointSpriteArray = type == Graphics3D.PRIMITIVE_POINT_SPRITES ? new int[size * 2] : new int[0];
    }

    public int getType() {
        return type;
    }

    public int getParam() {
        return param;
    }

    public int size() {
        return size;
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
}
