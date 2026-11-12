package com.docomostar.ui.graphics3d;

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
    public Primitive(int type, int param, int count) { this(new com.nttdocomo.ui.graphics3d.Primitive(type, param, count)); }
    Primitive(com.nttdocomo.ui.graphics3d.Primitive delegate) {
        super(delegate);
        com.nttdocomo.ui.graphics3d._PrimitiveRenderStateAccess.configureStarPrimitive(delegate);
    }
    private com.nttdocomo.ui.graphics3d.Primitive primitive() { return (com.nttdocomo.ui.graphics3d.Primitive) delegate; }
    public int getPrimitiveType() { return primitive().getPrimitiveType(); }
    public int getPrimitiveParam() { return primitive().getPrimitiveParam(); }
    public int size() { return primitive().size(); }
    public int[] getVertexArray() { return primitive().getVertexArray(); }
    public int[] getNormalArray() { return primitive().getNormalArray(); }
    public int[] getColorArray() { return primitive().getColorArray(); }
    public int[] getTextureCoordArray() { return primitive().getTextureCoordArray(); }
    public int[] getPointSpriteArray() { return primitive().getPointSpriteArray(); }
    public static void normalize(int[] vector, int offset) { com.nttdocomo.ui.graphics3d.Primitive.normalize(vector, offset); }
    public static int convertAngle(float angle) { return com.nttdocomo.ui.graphics3d.Primitive.convertAngle(angle); }
    public static float convertAngle(int angle) { return com.nttdocomo.ui.graphics3d.Primitive.convertAngle(angle); }
    public void setTexture(Texture texture) { primitive().setTexture((com.nttdocomo.ui.graphics3d.Texture) unwrap(texture)); }
}
