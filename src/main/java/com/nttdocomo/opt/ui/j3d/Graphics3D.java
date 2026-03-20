package com.nttdocomo.opt.ui.j3d;

public interface Graphics3D {
    int PRIMITIVE_POINTS = 1;
    int PRIMITIVE_LINES = 2;
    int PRIMITIVE_TRIANGLES = 3;
    int PRIMITIVE_QUADS = 4;
    int PRIMITIVE_POINT_SPRITES = 5;
    int PRIMITIVE_TYPE_MIN = 1;
    int PRIMITIVE_TYPE_MAX = 5;
    int NORMAL_NONE = 0;
    int NORMAL_PER_FACE = 512;
    int NORMAL_PER_VERTEX = 768;
    int COLOR_NONE = 0;
    int COLOR_PER_COMMAND = 1024;
    int COLOR_PER_FACE = 2048;
    int TEXTURE_COORD_NONE = 0;
    int TEXTURE_COORD_PER_VERTEX = 12288;
    int POINT_SPRITE_PER_COMMAND = 4096;
    int POINT_SPRITE_PER_VERTEX = 12288;
    int ATTR_BLEND_NORMAL = 0;
    int ATTR_BLEND_HALF = 32;
    int ATTR_BLEND_ADD = 64;
    int ATTR_BLEND_SUB = 96;
    int ATTR_COLOR_KEY = 16;
    int ATTR_LIGHT = 1;
    int ATTR_SPHERE_MAP = 2;
    int ENV_ATTR_LIGHT = 1;
    int ENV_ATTR_SPHERE_MAP = 2;
    int ENV_ATTR_TOON_SHADER = 4;
    int ENV_ATTR_SEMI_TRANSPARENT = 8;
    int POINT_SPRITE_FLAG_LOCAL_SIZE = 0;
    int POINT_SPRITE_FLAG_PIXEL_SIZE = 1;
    int POINT_SPRITE_FLAG_PERSPECTIVE = 0;
    int POINT_SPRITE_FLAG_NO_PERSPECTIVE = 2;
    int COMMAND_LIST_VERSION_1 = -33554431;
    int COMMAND_END = -2147483648;
    int COMMAND_NOP = -2130706432;
    int COMMAND_FLUSH = -2113929216;
    int COMMAND_ATTRIBUTE = -2097152000;
    int COMMAND_CLIP_RECT = -2080374784;
    int COMMAND_SCREEN_CENTER = -2063597568;
    int COMMAND_TEXTURE = -2046820352;
    int COMMAND_VIEW_TRANS = -2030043136;
    int COMMAND_SCREEN_SCALE = -1879048192;
    int COMMAND_SCREEN_VIEW = -1862270976;
    int COMMAND_PERSPECTIVE1 = -1845493760;
    int COMMAND_PERSPECTIVE2 = -1828716544;
    int COMMAND_AMBIENT_LIGHT = -1610612736;
    int COMMAND_DIRECTION_LIGHT = -1593835520;
    int COMMAND_TOON_PARAM = -1358954496;
    int COMMAND_RENDER_POINTS = 16777216;
    int COMMAND_RENDER_LINES = 33554432;
    int COMMAND_RENDER_TRIANGLES = 50331648;
    int COMMAND_RENDER_QUADS = 67108864;
    int COMMAND_RENDER_POINT_SPRITES = 83886080;

    void setViewTrans(AffineTrans transform);

    void setViewTransArray(AffineTrans[] transforms);

    void setViewTrans(int index);

    void setScreenCenter(int x, int y);

    void setScreenScale(int x, int y);

    void setScreenView(int x, int y);

    void setPerspective(int near, int far, int width);

    void setPerspective(int near, int far, int width, int height);

    void drawFigure(Figure figure);

    void renderFigure(Figure figure);

    void flush();

    void enableSphereMap(boolean enabled);

    void setSphereTexture(Texture texture);

    void enableLight(boolean enabled);

    void setAmbientLight(int color);

    void setDirectionLight(Vector3D direction, int color);

    void enableSemiTransparent(boolean enabled);

    void setClipRect3D(int x, int y, int width, int height);

    void enableToonShader(boolean enabled);

    void setToonParam(int highlight, int mid, int shadow);

    void setPrimitiveTextureArray(Texture texture);

    void setPrimitiveTextureArray(Texture[] textures);

    void setPrimitiveTexture(int index);

    void renderPrimitives(PrimitiveArray primitives, int attr);

    void renderPrimitives(PrimitiveArray primitives, int attr, int x, int y);

    void executeCommandList(int[] commands);
}
