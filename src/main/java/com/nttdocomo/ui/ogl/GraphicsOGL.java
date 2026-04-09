package com.nttdocomo.ui.ogl;

/**
 * Defines the DoJa OpenGL ES 1.x drawing interface and constants.
 */
public interface GraphicsOGL {
    int GL_DEPTH_BUFFER_BIT = 256;
    int GL_STENCIL_BUFFER_BIT = 1024;
    int GL_COLOR_BUFFER_BIT = 16384;
    boolean GL_FALSE = false;
    boolean GL_TRUE = true;
    int GL_POINTS = 0;
    int GL_LINES = 1;
    int GL_LINE_LOOP = 2;
    int GL_LINE_STRIP = 3;
    int GL_TRIANGLES = 4;
    int GL_TRIANGLE_STRIP = 5;
    int GL_TRIANGLE_FAN = 6;
    int GL_NEVER = 512;
    int GL_LESS = 513;
    int GL_EQUAL = 514;
    int GL_LEQUAL = 515;
    int GL_GREATER = 516;
    int GL_NOTEQUAL = 517;
    int GL_GEQUAL = 518;
    int GL_ALWAYS = 519;
    int GL_ZERO = 0;
    int GL_ONE = 1;
    int GL_SRC_COLOR = 768;
    int GL_ONE_MINUS_SRC_COLOR = 769;
    int GL_SRC_ALPHA = 770;
    int GL_ONE_MINUS_SRC_ALPHA = 771;
    int GL_DST_ALPHA = 772;
    int GL_ONE_MINUS_DST_ALPHA = 773;
    int GL_DST_COLOR = 774;
    int GL_ONE_MINUS_DST_COLOR = 775;
    int GL_SRC_ALPHA_SATURATE = 776;
    int GL_FRONT = 1028;
    int GL_BACK = 1029;
    int GL_FRONT_AND_BACK = 1032;
    int GL_FOG = 2912;
    int GL_LIGHTING = 2896;
    int GL_TEXTURE_2D = 3553;
    int GL_CULL_FACE = 2884;
    int GL_ALPHA_TEST = 3008;
    int GL_BLEND = 3042;
    int GL_COLOR_LOGIC_OP = 3058;
    int GL_DITHER = 3024;
    int GL_STENCIL_TEST = 2960;
    int GL_DEPTH_TEST = 2929;
    int GL_POINT_SMOOTH = 2832;
    int GL_LINE_SMOOTH = 2848;
    int GL_SCISSOR_TEST = 3089;
    int GL_COLOR_MATERIAL = 2903;
    int GL_NORMALIZE = 2977;
    int GL_RESCALE_NORMAL = 32826;
    int GL_POLYGON_OFFSET_FILL = 32823;
    int GL_VERTEX_ARRAY = 32884;
    int GL_NORMAL_ARRAY = 32885;
    int GL_COLOR_ARRAY = 32886;
    int GL_TEXTURE_COORD_ARRAY = 32888;
    int GL_MULTISAMPLE = 32925;
    int GL_SAMPLE_ALPHA_TO_COVERAGE = 32926;
    int GL_SAMPLE_ALPHA_TO_ONE = 32927;
    int GL_SAMPLE_COVERAGE = 32928;
    int GL_NO_ERROR = 0;
    int GL_INVALID_ENUM = 1280;
    int GL_INVALID_VALUE = 1281;
    int GL_INVALID_OPERATION = 1282;
    int GL_STACK_OVERFLOW = 1283;
    int GL_STACK_UNDERFLOW = 1284;
    int GL_OUT_OF_MEMORY = 1285;
    int GL_EXP = 2048;
    int GL_EXP2 = 2049;
    int GL_FOG_DENSITY = 2914;
    int GL_FOG_START = 2915;
    int GL_FOG_END = 2916;
    int GL_FOG_MODE = 2917;
    int GL_FOG_COLOR = 2918;
    int GL_CW = 2304;
    int GL_CCW = 2305;
    int GL_SMOOTH_POINT_SIZE_RANGE = 2834;
    int GL_SMOOTH_LINE_WIDTH_RANGE = 2850;
    int GL_ALIASED_POINT_SIZE_RANGE = 33901;
    int GL_ALIASED_LINE_WIDTH_RANGE = 33902;
    int GL_MAX_LIGHTS = 3377;
    int GL_MAX_TEXTURE_SIZE = 3379;
    int GL_MAX_MODELVIEW_STACK_DEPTH = 3382;
    int GL_MAX_PROJECTION_STACK_DEPTH = 3384;
    int GL_MAX_TEXTURE_STACK_DEPTH = 3385;
    int GL_MAX_VIEWPORT_DIMS = 3386;
    int GL_MAX_ELEMENTS_VERTICES = 33000;
    int GL_MAX_ELEMENTS_INDICES = 33001;
    int GL_MAX_TEXTURE_UNITS = 34018;
    int GL_SUBPIXEL_BITS = 3408;
    int GL_RED_BITS = 3410;
    int GL_GREEN_BITS = 3411;
    int GL_BLUE_BITS = 3412;
    int GL_ALPHA_BITS = 3413;
    int GL_DEPTH_BITS = 3414;
    int GL_STENCIL_BITS = 3415;
    int GL_NUM_COMPRESSED_TEXTURE_FORMATS = 34466;
    int GL_COMPRESSED_TEXTURE_FORMATS = 34467;
    int GL_DONT_CARE = 4352;
    int GL_FASTEST = 4353;
    int GL_NICEST = 4354;
    int GL_PERSPECTIVE_CORRECTION_HINT = 3152;
    int GL_POINT_SMOOTH_HINT = 3153;
    int GL_LINE_SMOOTH_HINT = 3154;
    int GL_FOG_HINT = 3156;
    int GL_LIGHT_MODEL_AMBIENT = 2899;
    int GL_LIGHT_MODEL_TWO_SIDE = 2898;
    int GL_AMBIENT = 4608;
    int GL_DIFFUSE = 4609;
    int GL_SPECULAR = 4610;
    int GL_POSITION = 4611;
    int GL_SPOT_DIRECTION = 4612;
    int GL_SPOT_EXPONENT = 4613;
    int GL_SPOT_CUTOFF = 4614;
    int GL_CONSTANT_ATTENUATION = 4615;
    int GL_LINEAR_ATTENUATION = 4616;
    int GL_QUADRATIC_ATTENUATION = 4617;
    int GL_BYTE = 5120;
    int GL_UNSIGNED_BYTE = 5121;
    int GL_SHORT = 5122;
    int GL_UNSIGNED_SHORT = 5123;
    int GL_FLOAT = 5126;
    int GL_CLEAR = 5376;
    int GL_AND = 5377;
    int GL_AND_REVERSE = 5378;
    int GL_COPY = 5379;
    int GL_AND_INVERTED = 5380;
    int GL_NOOP = 5381;
    int GL_XOR = 5382;
    int GL_OR = 5383;
    int GL_NOR = 5384;
    int GL_EQUIV = 5385;
    int GL_INVERT = 5386;
    int GL_OR_REVERSE = 5387;
    int GL_COPY_INVERTED = 5388;
    int GL_OR_INVERTED = 5389;
    int GL_NAND = 5390;
    int GL_SET = 5391;
    int GL_EMISSION = 5632;
    int GL_SHININESS = 5633;
    int GL_AMBIENT_AND_DIFFUSE = 5634;
    int GL_MODELVIEW = 5888;
    int GL_PROJECTION = 5889;
    int GL_TEXTURE = 5890;
    int GL_ALPHA = 6406;
    int GL_RGB = 6407;
    int GL_RGBA = 6408;
    int GL_LUMINANCE = 6409;
    int GL_LUMINANCE_ALPHA = 6410;
    int GL_UNPACK_ALIGNMENT = 3317;
    int GL_PACK_ALIGNMENT = 3333;
    int GL_UNSIGNED_SHORT_4_4_4_4 = 32819;
    int GL_UNSIGNED_SHORT_5_5_5_1 = 32820;
    int GL_UNSIGNED_SHORT_5_6_5 = 33635;
    int GL_FLAT = 7424;
    int GL_SMOOTH = 7425;
    int GL_KEEP = 7680;
    int GL_REPLACE = 7681;
    int GL_INCR = 7682;
    int GL_DECR = 7683;
    int GL_MODULATE = 8448;
    int GL_DECAL = 8449;
    int GL_ADD = 260;
    int GL_TEXTURE_ENV_MODE = 8704;
    int GL_TEXTURE_ENV_COLOR = 8705;
    int GL_TEXTURE_ENV = 8960;
    int GL_NEAREST = 9728;
    int GL_LINEAR = 9729;
    int GL_NEAREST_MIPMAP_NEAREST = 9984;
    int GL_LINEAR_MIPMAP_NEAREST = 9985;
    int GL_NEAREST_MIPMAP_LINEAR = 9986;
    int GL_LINEAR_MIPMAP_LINEAR = 9987;
    int GL_TEXTURE_MAG_FILTER = 10240;
    int GL_TEXTURE_MIN_FILTER = 10241;
    int GL_TEXTURE_WRAP_S = 10242;
    int GL_TEXTURE_WRAP_T = 10243;
    int GL_TEXTURE0 = 33984;
    int GL_TEXTURE1 = 33985;
    int GL_TEXTURE2 = 33986;
    int GL_TEXTURE3 = 33987;
    int GL_TEXTURE4 = 33988;
    int GL_TEXTURE5 = 33989;
    int GL_TEXTURE6 = 33990;
    int GL_TEXTURE7 = 33991;
    int GL_TEXTURE8 = 33992;
    int GL_TEXTURE9 = 33993;
    int GL_TEXTURE10 = 33994;
    int GL_TEXTURE11 = 33995;
    int GL_TEXTURE12 = 33996;
    int GL_TEXTURE13 = 33997;
    int GL_TEXTURE14 = 33998;
    int GL_TEXTURE15 = 33999;
    int GL_TEXTURE16 = 34000;
    int GL_TEXTURE17 = 34001;
    int GL_TEXTURE18 = 34002;
    int GL_TEXTURE19 = 34003;
    int GL_TEXTURE20 = 34004;
    int GL_TEXTURE21 = 34005;
    int GL_TEXTURE22 = 34006;
    int GL_TEXTURE23 = 34007;
    int GL_TEXTURE24 = 34008;
    int GL_TEXTURE25 = 34009;
    int GL_TEXTURE26 = 34010;
    int GL_TEXTURE27 = 34011;
    int GL_TEXTURE28 = 34012;
    int GL_TEXTURE29 = 34013;
    int GL_TEXTURE30 = 34014;
    int GL_TEXTURE31 = 34015;
    int GL_REPEAT = 10497;
    int GL_CLAMP_TO_EDGE = 33071;
    int GL_PALETTE4_RGB8_OES = 35728;
    int GL_PALETTE4_RGBA8_OES = 35729;
    int GL_PALETTE4_R5_G6_B5_OES = 35730;
    int GL_PALETTE4_RGBA4_OES = 35731;
    int GL_PALETTE4_RGB5_A1_OES = 35732;
    int GL_PALETTE8_RGB8_OES = 35733;
    int GL_PALETTE8_RGBA8_OES = 35734;
    int GL_PALETTE8_R5_G6_B5_OES = 35735;
    int GL_PALETTE8_RGBA4_OES = 35736;
    int GL_PALETTE8_RGB5_A1_OES = 35737;
    int GL_LIGHT0 = 16384;
    int GL_LIGHT1 = 16385;
    int GL_LIGHT2 = 16386;
    int GL_LIGHT3 = 16387;
    int GL_LIGHT4 = 16388;
    int GL_LIGHT5 = 16389;
    int GL_LIGHT6 = 16390;
    int GL_LIGHT7 = 16391;
    int GL_ACTIVE_TEXTURE = 34016;
    int GL_ADD_SIGNED = 34164;
    int GL_ALPHA_SCALE = 3356;
    int GL_ALPHA_TEST_FUNC = 3009;
    int GL_ALPHA_TEST_REF = 3010;
    int GL_ARRAY_BUFFER = 34962;
    int GL_ARRAY_BUFFER_BINDING = 34964;
    int GL_BLEND_DST = 3040;
    int GL_BLEND_SRC = 3041;
    int GL_BUFFER_ACCESS = 35003;
    int GL_BUFFER_SIZE = 34660;
    int GL_BUFFER_USAGE = 34661;
    int GL_CLIENT_ACTIVE_TEXTURE = 34017;
    int GL_CLIP_PLANE0 = 12288;
    int GL_CLIP_PLANE1 = 12289;
    int GL_CLIP_PLANE2 = 12290;
    int GL_CLIP_PLANE3 = 12291;
    int GL_CLIP_PLANE4 = 12292;
    int GL_CLIP_PLANE5 = 12293;
    int GL_COLOR_ARRAY_BUFFER_BINDING = 34968;
    int GL_COLOR_ARRAY_POINTER = 32912;
    int GL_COLOR_ARRAY_SIZE = 32897;
    int GL_COLOR_ARRAY_STRIDE = 32899;
    int GL_COLOR_ARRAY_TYPE = 32898;
    int GL_COLOR_CLEAR_VALUE = 3106;
    int GL_COLOR_WRITEMASK = 3107;
    int GL_COMBINE = 34160;
    int GL_COMBINE_ALPHA = 34162;
    int GL_COMBINE_RGB = 34161;
    int GL_CONSTANT = 34166;
    int GL_COORD_REPLACE_OES = 34914;
    int GL_CULL_FACE_MODE = 2885;
    int GL_CURRENT_COLOR = 2816;
    int GL_CURRENT_NORMAL = 2818;
    int GL_CURRENT_TEXTURE_COORDS = 2819;
    int GL_DEPTH_CLEAR_VALUE = 2931;
    int GL_DEPTH_FUNC = 2932;
    int GL_DEPTH_RANGE = 2928;
    int GL_DEPTH_WRITEMASK = 2930;
    int GL_DOT3_RGB = 34478;
    int GL_DOT3_RGBA = 34479;
    int GL_DYNAMIC_DRAW = 35048;
    int GL_ELEMENT_ARRAY_BUFFER = 34963;
    int GL_ELEMENT_ARRAY_BUFFER_BINDING = 34965;
    int GL_EXTENSIONS = 7939;
    int GL_FALSE_I = 0;
    int GL_FRONT_FACE = 2886;
    int GL_GENERATE_MIPMAP = 33169;
    int GL_GENERATE_MIPMAP_HINT = 33170;
    int GL_INTERPOLATE = 34165;
    int GL_LINE_WIDTH = 2849;
    int GL_LOGIC_OP_MODE = 3056;
    int GL_MATRIX_INDEX_ARRAY_BUFFER_BINDING_OES = 35742;
    int GL_MATRIX_INDEX_ARRAY_OES = 34884;
    int GL_MATRIX_INDEX_ARRAY_POINTER_OES = 34889;
    int GL_MATRIX_INDEX_ARRAY_SIZE_OES = 34886;
    int GL_MATRIX_INDEX_ARRAY_STRIDE_OES = 34888;
    int GL_MATRIX_INDEX_ARRAY_TYPE_OES = 34887;
    int GL_MATRIX_MODE = 2976;
    int GL_MATRIX_PALETTE_OES = 34880;
    int GL_MAX_CLIP_PLANES = 3378;
    int GL_MAX_PALETTE_MATRICES_OES = 34882;
    int GL_CURRENT_PALETTE_MATRIX_OES = 34883;
    int GL_MAX_VERTEX_UNITS_OES = 34468;
    int GL_MODELVIEW_MATRIX = 2982;
    int GL_MODELVIEW_MATRIX_FLOAT_AS_INT_BITS_OES = 35213;
    int GL_MODELVIEW_STACK_DEPTH = 2979;
    int GL_NORMAL_ARRAY_BUFFER_BINDING = 34967;
    int GL_NORMAL_ARRAY_POINTER = 32911;
    int GL_NORMAL_ARRAY_STRIDE = 32895;
    int GL_NORMAL_ARRAY_TYPE = 32894;
    int GL_OPERAND0_ALPHA = 34200;
    int GL_OPERAND0_RGB = 34192;
    int GL_OPERAND1_ALPHA = 34201;
    int GL_OPERAND1_RGB = 34193;
    int GL_OPERAND2_ALPHA = 34202;
    int GL_OPERAND2_RGB = 34194;
    int GL_POINT_DISTANCE_ATTENUATION = 33065;
    int GL_POINT_FADE_THRESHOLD_SIZE = 33064;
    int GL_POINT_SIZE = 2833;
    int GL_POINT_SIZE_ARRAY_BUFFER_BINDING_OES = 35743;
    int GL_POINT_SIZE_ARRAY_OES = 35740;
    int GL_POINT_SIZE_ARRAY_POINTER_OES = 35212;
    int GL_POINT_SIZE_ARRAY_STRIDE_OES = 35211;
    int GL_POINT_SIZE_ARRAY_TYPE_OES = 35210;
    int GL_POINT_SIZE_MAX = 33063;
    int GL_POINT_SIZE_MIN = 33062;
    int GL_POINT_SPRITE_OES = 34913;
    int GL_POLYGON_OFFSET_FACTOR = 32824;
    int GL_POLYGON_OFFSET_UNITS = 10752;
    int GL_PREVIOUS = 34168;
    int GL_PRIMARY_COLOR = 34167;
    int GL_PROJECTION_MATRIX = 2983;
    int GL_PROJECTION_MATRIX_FLOAT_AS_INT_BITS_OES = 35214;
    int GL_PROJECTION_STACK_DEPTH = 2980;
    int GL_RENDERER = 7937;
    int GL_RGB_SCALE = 34163;
    int GL_SAMPLE_BUFFERS = 32936;
    int GL_SAMPLE_COVERAGE_INVERT = 32939;
    int GL_SAMPLE_COVERAGE_VALUE = 32938;
    int GL_SAMPLES = 32937;
    int GL_SCISSOR_BOX = 3088;
    int GL_SHADE_MODEL = 2900;
    int GL_SRC0_ALPHA = 34184;
    int GL_SRC0_RGB = 34176;
    int GL_SRC1_ALPHA = 34185;
    int GL_SRC1_RGB = 34177;
    int GL_SRC2_ALPHA = 34186;
    int GL_SRC2_RGB = 34178;
    int GL_STATIC_DRAW = 35044;
    int GL_STENCIL_CLEAR_VALUE = 2961;
    int GL_STENCIL_FAIL = 2964;
    int GL_STENCIL_FUNC = 2962;
    int GL_STENCIL_PASS_DEPTH_FAIL = 2965;
    int GL_STENCIL_PASS_DEPTH_PASS = 2966;
    int GL_STENCIL_REF = 2967;
    int GL_STENCIL_VALUE_MASK = 2963;
    int GL_STENCIL_WRITEMASK = 2968;
    int GL_SUBTRACT = 34023;
    int GL_TEXTURE_BINDING_2D = 32873;
    int GL_TEXTURE_COORD_ARRAY_BUFFER_BINDING = 34970;
    int GL_TEXTURE_COORD_ARRAY_POINTER = 32914;
    int GL_TEXTURE_COORD_ARRAY_SIZE = 32904;
    int GL_TEXTURE_COORD_ARRAY_STRIDE = 32906;
    int GL_TEXTURE_COORD_ARRAY_TYPE = 32905;
    int GL_TEXTURE_CROP_RECT_OES = 35741;
    int GL_TEXTURE_MATRIX = 2984;
    int GL_TEXTURE_MATRIX_FLOAT_AS_INT_BITS_OES = 35215;
    int GL_TEXTURE_STACK_DEPTH = 2981;
    int GL_TRUE_I = 1;
    int GL_VENDOR = 7936;
    int GL_VERSION = 7938;
    int GL_VERTEX_ARRAY_BUFFER_BINDING = 34966;
    int GL_VERTEX_ARRAY_POINTER = 32910;
    int GL_VERTEX_ARRAY_SIZE = 32890;
    int GL_VERTEX_ARRAY_STRIDE = 32892;
    int GL_VERTEX_ARRAY_TYPE = 32891;
    int GL_VIEWPORT = 2978;
    int GL_WEIGHT_ARRAY_BUFFER_BINDING_OES = 34974;
    int GL_WEIGHT_ARRAY_OES = 34477;
    int GL_WEIGHT_ARRAY_POINTER_OES = 34476;
    int GL_WEIGHT_ARRAY_SIZE_OES = 34475;
    int GL_WEIGHT_ARRAY_STRIDE_OES = 34474;
    int GL_WEIGHT_ARRAY_TYPE_OES = 34473;
    int GL_WRITE_ONLY = 35001;

    default void beginDrawing() {}

    default void endDrawing() {}

    default void glActiveTexture(int texture) {}

    default void glAlphaFunc(int func, float ref) {}

    default void glBindTexture(int target, int texture) {}

    default void glBlendFunc(int sfactor, int dfactor) {}

    default void glClear(int mask) {}

    default void glClearColor(float red, float green, float blue, float alpha) {}

    default void glClearDepthf(float depth) {}

    default void glClearStencil(int s) {}

    default void glClientActiveTexture(int texture) {}

    default void glColor4f(float red, float green, float blue, float alpha) {}

    default void glColorMask(boolean red, boolean green, boolean blue, boolean alpha) {}

    default void glColorPointer(int size, int type, int stride, DirectBuffer pointer) {}

    default void glColorPointer(int size, int type, int stride, int pointer) {}

    default void glCompressedTexImage2D(int target, int level, int internalformat, int width, int height, int border, int imageSize, DirectBuffer data) {}

    default void glCompressedTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int imageSize, DirectBuffer data) {}

    default void glCopyTexImage2D(int target, int level, int internalformat, int x, int y, int width, int height, int border) {}

    default void glCopyTexSubImage2D(int target, int level, int xoffset, int yoffset, int x, int y, int width, int height) {}

    default void glCullFace(int mode) {}

    default void glDeleteTextures(int n, int[] textures) {}

    default void glDepthFunc(int func) {}

    default void glDepthMask(boolean flag) {}

    default void glDepthRangef(float zNear, float zFar) {}

    default void glDisable(int cap) {}

    default void glDisableClientState(int array) {}

    default void glDrawArrays(int mode, int first, int count) {}

    default void glDrawElements(int mode, int count, int type, DirectBuffer indices) {}

    default void glDrawElements(int mode, int count, int type, int indices) {}

    default void glEnable(int cap) {}

    default void glEnableClientState(int array) {}

    default void glFlush() {}

    default void glFogf(int pname, float param) {}

    default void glFogfv(int pname, float[] params) {}

    default void glFrontFace(int mode) {}

    default void glFrustumf(float left, float right, float bottom, float top, float zNear, float zFar) {}

    default void glGenTextures(int n, int[] textures) {}

    default int glGetError() { return 0; }

    default void glGetIntegerv(int pname, int[] params) {}

    default void glHint(int target, int mode) {}

    default void glLightModelf(int pname, float param) {}

    default void glLightModelfv(int pname, float[] params) {}

    default void glLightf(int light, int pname, float param) {}

    default void glLightfv(int light, int pname, float[] params) {}

    default void glLineWidth(float width) {}

    default void glLoadIdentity() {}

    default void glLoadMatrixf(float[] m) {}

    default void glLogicOp(int opcode) {}

    default void glMaterialf(int face, int pname, float param) {}

    default void glMaterialfv(int face, int pname, float[] params) {}

    default void glMatrixMode(int mode) {}

    default void glMultMatrixf(float[] m) {}

    default void glMultiTexCoord4f(int target, float s, float t, float r, float q) {}

    default void glNormal3f(float nx, float ny, float nz) {}

    default void glNormalPointer(int type, int stride, DirectBuffer pointer) {}

    default void glNormalPointer(int type, int stride, int pointer) {}

    default void glOrthof(float left, float right, float bottom, float top, float zNear, float zFar) {}

    default void glPixelStorei(int pname, int param) {}

    default void glPointSize(float size) {}

    default void glPolygonOffset(float factor, float units) {}

    default void glPopMatrix() {}

    default void glPushMatrix() {}

    default void glRotatef(float angle, float x, float y, float z) {}

    default void glSampleCoverage(float value, boolean invert) {}

    default void glScalef(float x, float y, float z) {}

    default void glScissor(int x, int y, int width, int height) {}

    default void glShadeModel(int mode) {}

    default void glStencilFunc(int func, int ref, int mask) {}

    default void glStencilMask(int mask) {}

    default void glStencilOp(int fail, int zfail, int zpass) {}

    default void glTexCoordPointer(int size, int type, int stride, DirectBuffer pointer) {}

    default void glTexCoordPointer(int size, int type, int stride, int pointer) {}

    default void glTexEnvf(int target, int pname, float param) {}

    default void glTexEnvfv(int target, int pname, float[] params) {}

    default void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, DirectBuffer pixels) {}

    default void glTexParameterf(int target, int pname, float param) {}

    default void glTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, DirectBuffer pixels) {}

    default void glTranslatef(float x, float y, float z) {}

    default void glVertexPointer(int size, int type, int stride, DirectBuffer pointer) {}

    default void glVertexPointer(int size, int type, int stride, int pointer) {}

    default void glViewport(int x, int y, int width, int height) {}

    default void glCompressedTexImage2D(int target, int level, int internalformat, int width, int height, int border, ByteBuffer data) {}

    default void glCompressedTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, ByteBuffer data) {}

    default void glDeleteTextures(int[] textures) {}

    default void glDrawElements(int mode, int count, DirectBuffer indices) {}

    default void glGenTextures(int[] textures) {}

    default String glGetString(int name) { return ""; }

    default void glBindBuffer(int target, int buffer) {}

    default void glBufferData(int target, DirectBuffer data, int usage) {}

    default void glBufferSubData(int target, int offset, DirectBuffer data) {}

    default void glClipPlanef(int plane, float[] equation) {}

    default void glColor4ub(short red, short green, short blue, short alpha) {}

    default void glCurrentPaletteMatrixOES(int matrixpaletteindex) {}

    default void glDeleteBuffers(int[] buffers) {}

    default void glDrawTexfOES(float x, float y, float z, float width, float height) {}

    default void glDrawTexiOES(int x, int y, int z, int width, int height) {}

    default void glDrawTexsOES(short x, short y, short z, short width, short height) {}

    default void glGenBuffers(int[] buffers) {}

    default void glGetBooleanv(int pname, boolean[] params) {}

    default void glGetBufferParameteriv(int target, int pname, int[] params) {}

    default void glGetClipPlanef(int pname, float[] eqn) {}

    default void glGetFloatv(int pname, float[] params) {}

    default void glGetLightfv(int light, int pname, float[] params) {}

    default void glGetMaterialfv(int face, int pname, float[] params) {}

    default void glGetTexEnvfv(int env, int pname, float[] params) {}

    default void glGetTexEnviv(int env, int pname, int[] params) {}

    default void glGetTexParameterfv(int target, int pname, float[] params) {}

    default void glGetTexParameteriv(int target, int pname, int[] params) {}

    default boolean glIsBuffer(int buffer) { return false; }

    default boolean glIsEnabled(int cap) { return false; }

    default boolean glIsTexture(int texture) { return false; }

    default void glLoadPaletteFromModelViewMatrixOES() {}

    default void glMatrixIndexPointerOES(int size, int type, int stride, DirectBuffer pointer) {}

    default void glMatrixIndexPointerOES(int size, int type, int stride, int pointer) {}

    default void glPointParameterf(int pname, float param) {}

    default void glPointParameterfv(int pname, float[] params) {}

    default void glPointSizePointerOES(int type, int stride, DirectBuffer pointer) {}

    default void glPointSizePointerOES(int type, int stride, int pointer) {}

    default void glTexEnvi(int target, int pname, int param) {}

    default void glTexEnviv(int target, int pname, int[] params) {}

    default void glTexParameterfv(int target, int pname, float[] params) {}

    default void glTexParameteri(int target, int pname, int param) {}

    default void glTexParameteriv(int target, int pname, int[] params) {}

    default void glWeightPointerOES(int size, int type, int stride, DirectBuffer pointer) {}

    default void glWeightPointerOES(int size, int type, int stride, int pointer) {}
}
