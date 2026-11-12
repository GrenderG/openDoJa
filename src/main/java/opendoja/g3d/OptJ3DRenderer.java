package opendoja.g3d;

import com.nttdocomo.opt.ui.j3d.*;
import opendoja.host.OpenDoJaLog;

import java.lang.Math;
import java.util.Arrays;

public final class OptJ3DRenderer {
    private static final int LEGACY_COMMAND_LIST_VERSION_1 = 1;
    private static final int COMMAND_PREFIX_MASK = 0xFF00_0000;
    private static final int COMMAND_INLINE_VALUE_MASK = 0x00FF_FFFF;
    private static final int COMMAND_RENDER_COUNT_MASK = 0x00FF_0000;
    private static final int COMMAND_ATTR_MASK =
            Graphics3D.ATTR_LIGHT
                    | Graphics3D.ATTR_SPHERE_MAP
                    | Graphics3D.ATTR_COLOR_KEY
                    | Graphics3D.ATTR_BLEND_HALF
                    | Graphics3D.ATTR_BLEND_ADD
                    | Graphics3D.ATTR_BLEND_SUB;

    private final Software3DContext context;
    private final boolean traceCalls;
    private AffineTrans[] viewTransforms = new AffineTrans[0];
    private boolean sphereMapEnabled;
    private SoftwareTexture sphereTexture;
    private boolean toonShaderEnabled;
    private int toonThreshold = 128;
    private int toonMid = 255;
    private int toonShadow = 96;

    public OptJ3DRenderer(Software3DContext context, boolean traceCalls) {
        this.context = context;
        this.traceCalls = traceCalls;
        syncState();
    }

    public void copyStateFrom(OptJ3DRenderer source) {
        this.viewTransforms = source.viewTransforms.clone();
        this.sphereMapEnabled = source.sphereMapEnabled;
        this.sphereTexture = source.sphereTexture;
        this.toonShaderEnabled = source.toonShaderEnabled;
        this.toonThreshold = source.toonThreshold;
        this.toonMid = source.toonMid;
        this.toonShadow = source.toonShadow;
        syncState();
    }

    public void setViewTrans(AffineTrans transform) {
        float[] matrix = transform == null ? Software3DContext.identity() : _Opt3DInternalAccess.toFloatMatrix(transform);
        if (traceCalls) {
            OpenDoJaLog.debug(OptJ3DRenderer.class, () -> "3D call setViewTrans matrix=" + Arrays.toString(matrix));
        }
        context.setOptViewTransform(matrix);
    }

    public void setViewTransArray(AffineTrans[] transforms) {
        this.viewTransforms = transforms == null ? new AffineTrans[0] : transforms.clone();
    }

    public void setViewTrans(int index) {
        setViewTrans(viewTransforms[index]);
    }

    public void setScreenCenter(int x, int y) {
        if (traceCalls) {
            OpenDoJaLog.debug(OptJ3DRenderer.class, () -> "3D call setScreenCenter x=" + x + " y=" + y);
        }
        // opt.ui.j3d screen-space state is defined in absolute canvas coordinates rather
        // than inheriting Graphics.setOrigin() like the 2D drawing methods do.
        context.setOptScreenCenter(x, y);
    }

    public void setScreenScale(int x, int y) {
        if (traceCalls) {
            OpenDoJaLog.debug(OptJ3DRenderer.class, () -> "3D call setScreenScale x=" + x + " y=" + y);
        }
        context.setOptScreenScale(x, y);
    }

    public void setScreenView(int x, int y) {
        if (traceCalls) {
            OpenDoJaLog.debug(OptJ3DRenderer.class, () -> "3D call setScreenView width=" + x + " height=" + y);
        }
        // DoJa opt `setScreenView()` configures the parallel-projection extent, not a screen-space position.
        context.setOptScreenView(x, y);
    }

    public void setPerspective(int near, int far, int width) {
        if (traceCalls) {
            OpenDoJaLog.debug(OptJ3DRenderer.class, () -> "3D call setPerspective near=" + near + " far=" + far + " width=" + width);
        }
        context.setOptPerspective(near, far, width);
    }

    public void setPerspective(int near, int far, int width, int height) {
        if (traceCalls) {
            OpenDoJaLog.debug(OptJ3DRenderer.class, () -> "3D call setPerspectiveWH near=" + near + " far=" + far + " width=" + width + " height=" + height);
        }
        context.setOptPerspective(near, far, width, height);
    }

    public void renderFigure(Figure figure, DojaGraphics3DRenderer.RenderTarget target, Runnable prepareDepthFrame) {
        if (figure == null) {
            return;
        }
        MascotFigure handle = _Opt3DInternalAccess.handle(figure);
        prepareDepthFrame.run();
        if (traceCalls) {
            OpenDoJaLog.debug(OptJ3DRenderer.class, () -> "3D call renderFigure " + describeOptFigure(handle));
        }
        context.renderOptFigure(target.graphics(), target.image(), 0, 0, target.width(), target.height(), handle);
    }

    public void enableSphereMap(boolean enabled) {
        this.sphereMapEnabled = enabled;
        context.enableOptSphereMap(enabled);
    }

    public void setSphereTexture(Texture texture) {
        if (texture == null) {
            throw new NullPointerException("texture");
        }
        SoftwareTexture handle = _Opt3DInternalAccess.handle(texture);
        if (!handle.sphereMap()) {
            throw new IllegalArgumentException("texture must be an environment-mapping texture");
        }
        this.sphereTexture = handle;
        context.setOptSphereTexture(handle);
    }

    public void enableLight(boolean enabled) {
        if (traceCalls) {
            OpenDoJaLog.debug(OptJ3DRenderer.class, () -> "3D call enableLight enabled=" + enabled);
        }
        context.enableOptLight(enabled);
    }

    public void setAmbientLight(int color) {
        if (traceCalls) {
            OpenDoJaLog.debug(OptJ3DRenderer.class, () -> "3D call setAmbientLight value=" + color);
        }
    }

    public void setDirectionLight(Vector3D direction, int color) {
        if (traceCalls) {
            OpenDoJaLog.debug(OptJ3DRenderer.class, () -> "3D call setDirectionLight dir=("
                    + (direction == null ? 0 : direction.x) + ","
                    + (direction == null ? 0 : direction.y) + ","
                    + (direction == null ? 0 : direction.z) + ") value=" + color);
        }
    }

    public void enableSemiTransparent(boolean enabled) {
        context.enableOptSemiTransparent(enabled);
    }

    public void setClipRect3D(int x, int y, int width, int height) {
        if (traceCalls) {
            OpenDoJaLog.debug(OptJ3DRenderer.class, () -> "3D call setClipRect3D x=" + x + " y=" + y + " width=" + width + " height=" + height);
        }
        // `setClipRect3D()` is defined in absolute canvas coordinates and ignores Graphics.setOrigin().
        context.setOptClip(x, y, width, height);
    }

    public void enableToonShader(boolean enabled) {
        this.toonShaderEnabled = enabled;
        context.enableOptToonShader(enabled);
    }

    public void setToonParam(int highlight, int mid, int shadow) {
        validateToonParameter(highlight, "highlight");
        validateToonParameter(mid, "mid");
        validateToonParameter(shadow, "shadow");
        this.toonThreshold = highlight;
        this.toonMid = mid;
        this.toonShadow = shadow;
        context.setOptToonShader(highlight, mid, shadow);
    }

    public void setPrimitiveTextureArray(Texture texture) {
        if (traceCalls) {
            SoftwareTexture handle = texture == null ? null : _Opt3DInternalAccess.handle(texture);
            OpenDoJaLog.debug(OptJ3DRenderer.class, () -> "3D call setPrimitiveTextureArray single texture=" + describeTexture(handle));
        }
        context.setPrimitiveTextures(texture == null ? null : new SoftwareTexture[]{_Opt3DInternalAccess.handle(texture)});
    }

    public void setPrimitiveTextureArray(Texture[] textures) {
        if (textures == null) {
            if (traceCalls) {
                OpenDoJaLog.debug(OptJ3DRenderer.class, "3D call setPrimitiveTextureArray array=null");
            }
            context.setPrimitiveTextures(null);
            return;
        }
        SoftwareTexture[] converted = new SoftwareTexture[textures.length];
        for (int i = 0; i < textures.length; i++) {
            converted[i] = _Opt3DInternalAccess.handle(textures[i]);
        }
        if (traceCalls) {
            OpenDoJaLog.debug(OptJ3DRenderer.class, () -> "3D call setPrimitiveTextureArray array=" + describeTextures(converted));
        }
        context.setPrimitiveTextures(converted);
    }

    public void setPrimitiveTexture(int index) {
        if (traceCalls) {
            OpenDoJaLog.debug(OptJ3DRenderer.class, () -> "3D call setPrimitiveTexture index=" + index);
        }
        context.setPrimitiveTexture(index);
    }

    public void renderPrimitives(DojaGraphics3DRenderer.RenderTarget target, PrimitiveArray primitives, int attr, Runnable prepareDepthFrame) {
        prepareDepthFrame.run();
        if (traceCalls && primitives != null) {
            OpenDoJaLog.debug(OptJ3DRenderer.class, () -> "3D call renderPrimitives type=" + primitives.getType()
                    + " param=" + primitives.getParam()
                    + " size=" + primitives.size()
                    + " attr=" + attr
                    + " textures=" + describeTextures(context.primitiveTexturesSnapshot())
                    + " selectedTexture=" + context.primitiveTextureIndex());
        }
        context.renderOptPrimitives(target.graphics(), target.image(), 0, 0, target.width(), target.height(), primitives, attr);
    }

    public void renderPrimitives(DojaGraphics3DRenderer.RenderTarget target, PrimitiveArray primitives, int start, int count, int attr, Runnable prepareDepthFrame) {
        prepareDepthFrame.run();
        if (traceCalls && primitives != null) {
            OpenDoJaLog.debug(OptJ3DRenderer.class, () -> "3D call renderPrimitivesRange type=" + primitives.getType()
                    + " param=" + primitives.getParam()
                    + " size=" + primitives.size()
                    + " start=" + start
                    + " count=" + count
                    + " attr=" + attr
                    + " textures=" + describeTextures(context.primitiveTexturesSnapshot())
                    + " selectedTexture=" + context.primitiveTextureIndex());
        }
        context.renderOptPrimitivesRange(target.graphics(), target.image(), 0, 0, target.width(), target.height(), primitives, start, count, attr);
    }

    public void executeCommandList(int[] commands, DojaGraphics3DRenderer.RenderTarget target, Runnable prepareDepthFrame, Runnable flushSurfaceFrame) {
        if (commands == null) {
            throw new NullPointerException("commands");
        }
        if (commands.length == 0 || !isSupportedCommandListVersion(commands[0])) {
            throw new IllegalArgumentException("Unsupported command list version: "
                    + (commands.length == 0 ? "<empty>" : Integer.toString(commands[0])));
        }
        for (int i = 1; i < commands.length; ) {
            int command = commands[i];
            if (command == Graphics3D.COMMAND_END) {
                return;
            }
            i++;
            switch (command & COMMAND_PREFIX_MASK) {
                case Graphics3D.COMMAND_NOP -> i = skipCommandOperands(commands, i, command & COMMAND_INLINE_VALUE_MASK);
                case Graphics3D.COMMAND_FLUSH -> flushSurfaceFrame.run();
                case Graphics3D.COMMAND_ATTRIBUTE -> applyCommandAttributes(command & COMMAND_INLINE_VALUE_MASK);
                case Graphics3D.COMMAND_CLIP_RECT -> {
                    ensureCommandOperands(commands, i, 4, "COMMAND_CLIP_RECT");
                    int left = commands[i++];
                    int top = commands[i++];
                    int right = commands[i++];
                    int bottom = commands[i++];
                    setClipRect3D(left, top, java.lang.Math.max(0, right - left), java.lang.Math.max(0, bottom - top));
                }
                case Graphics3D.COMMAND_SCREEN_CENTER -> {
                    ensureCommandOperands(commands, i, 2, "COMMAND_SCREEN_CENTER");
                    setScreenCenter(commands[i++], commands[i++]);
                }
                case Graphics3D.COMMAND_TEXTURE -> setPrimitiveTexture(command & COMMAND_INLINE_VALUE_MASK);
                case Graphics3D.COMMAND_VIEW_TRANS -> {
                    if (viewTransforms.length > 0) {
                        setViewTrans(command & COMMAND_INLINE_VALUE_MASK);
                    }
                }
                case Graphics3D.COMMAND_SCREEN_SCALE -> {
                    ensureCommandOperands(commands, i, 2, "COMMAND_SCREEN_SCALE");
                    setScreenScale(commands[i++], commands[i++]);
                }
                case Graphics3D.COMMAND_SCREEN_VIEW -> {
                    ensureCommandOperands(commands, i, 2, "COMMAND_SCREEN_VIEW");
                    setScreenView(commands[i++], commands[i++]);
                }
                case Graphics3D.COMMAND_PERSPECTIVE1 -> {
                    ensureCommandOperands(commands, i, 3, "COMMAND_PERSPECTIVE1");
                    setPerspective(commands[i++], commands[i++], commands[i++]);
                }
                case Graphics3D.COMMAND_PERSPECTIVE2 -> {
                    ensureCommandOperands(commands, i, 4, "COMMAND_PERSPECTIVE2");
                    setPerspective(commands[i++], commands[i++], commands[i++], commands[i++]);
                }
                case Graphics3D.COMMAND_AMBIENT_LIGHT -> {
                    ensureCommandOperands(commands, i, 1, "COMMAND_AMBIENT_LIGHT");
                    setAmbientLight(commands[i++]);
                }
                case Graphics3D.COMMAND_DIRECTION_LIGHT -> {
                    ensureCommandOperands(commands, i, 4, "COMMAND_DIRECTION_LIGHT");
                    setDirectionLight(new Vector3D(commands[i++], commands[i++], commands[i++]), commands[i++]);
                }
                case Graphics3D.COMMAND_TOON_PARAM -> {
                    ensureCommandOperands(commands, i, 3, "COMMAND_TOON_PARAM");
                    setToonParam(commands[i++], commands[i++], commands[i++]);
                }
                case Graphics3D.COMMAND_RENDER_POINTS,
                        Graphics3D.COMMAND_RENDER_LINES,
                        Graphics3D.COMMAND_RENDER_TRIANGLES,
                        Graphics3D.COMMAND_RENDER_QUADS,
                        Graphics3D.COMMAND_RENDER_POINT_SPRITES -> i = renderCommandPrimitive(commands, i, command, target, prepareDepthFrame);
                default -> throw new IllegalArgumentException("Unsupported opt command: " + command);
            }
        }
    }

    private void syncState() {
        context.enableOptSphereMap(sphereMapEnabled);
        context.setOptSphereTexture(sphereTexture);
        context.enableOptToonShader(toonShaderEnabled);
        context.setOptToonShader(toonThreshold, toonMid, toonShadow);
    }

    private void applyCommandAttributes(int attributes) {
        context.enableOptLight((attributes & Graphics3D.ENV_ATTR_LIGHT) != 0);
        context.enableOptSemiTransparent((attributes & Graphics3D.ENV_ATTR_SEMI_TRANSPARENT) != 0);
        enableSphereMap((attributes & Graphics3D.ENV_ATTR_SPHERE_MAP) != 0);
        enableToonShader((attributes & Graphics3D.ENV_ATTR_TOON_SHADER) != 0);
    }

    private int renderCommandPrimitive(int[] commands, int index, int command, DojaGraphics3DRenderer.RenderTarget target, Runnable prepareDepthFrame) {
        int primitiveType = switch (command & COMMAND_PREFIX_MASK) {
            case Graphics3D.COMMAND_RENDER_POINTS -> Graphics3D.PRIMITIVE_POINTS;
            case Graphics3D.COMMAND_RENDER_LINES -> Graphics3D.PRIMITIVE_LINES;
            case Graphics3D.COMMAND_RENDER_TRIANGLES -> Graphics3D.PRIMITIVE_TRIANGLES;
            case Graphics3D.COMMAND_RENDER_QUADS -> Graphics3D.PRIMITIVE_QUADS;
            case Graphics3D.COMMAND_RENDER_POINT_SPRITES -> Graphics3D.PRIMITIVE_POINT_SPRITES;
            default -> throw new IllegalArgumentException("Unsupported primitive command: " + command);
        };
        int primitiveCount = (command & COMMAND_RENDER_COUNT_MASK) >>> 16;
        if (primitiveCount <= 0) {
            throw new IllegalArgumentException("Invalid primitive count: " + primitiveCount);
        }
        PrimitiveArray primitives = new PrimitiveArray(primitiveType, extractPrimitiveParam(command, primitiveType), primitiveCount);
        // Command-list primitive payloads follow the PrimitiveArray storage order:
        // vertices, normals, texture/point-sprite data, then colors.
        index = copyCommandPayload(commands, index, primitives.getVertexArray(), "vertex");
        index = copyCommandPayload(commands, index, primitives.getNormalArray(), "normal");
        index = copyCommandPayload(commands, index, primitives.getTextureCoordArray(), "texture");
        index = copyCommandPayload(commands, index, primitives.getPointSpriteArray(), "pointSprite");
        index = copyCommandPayload(commands, index, primitives.getColorArray(), "color");
        renderPrimitives(target, primitives, command & COMMAND_ATTR_MASK, prepareDepthFrame);
        return index;
    }

    private static boolean isSupportedCommandListVersion(int version) {
        // Some older handset stacks write the v1 command-list header as a plain literal `1`
        // instead of the later packed constant `0xfe000001`.
        return version == Graphics3D.COMMAND_LIST_VERSION_1
                || version == LEGACY_COMMAND_LIST_VERSION_1;
    }

    private static int skipCommandOperands(int[] commands, int index, int count) {
        ensureCommandOperands(commands, index, count, "COMMAND_NOP");
        return index + count;
    }

    private static int extractPrimitiveParam(int command, int primitiveType) {
        int sharedParam = command & (Graphics3D.NORMAL_PER_FACE
                | Graphics3D.NORMAL_PER_VERTEX
                | Graphics3D.COLOR_PER_COMMAND
                | Graphics3D.COLOR_PER_FACE
                | Graphics3D.TEXTURE_COORD_PER_VERTEX);
        if (primitiveType != Graphics3D.PRIMITIVE_POINT_SPRITES) {
            return sharedParam;
        }
        return sharedParam | (command & (Graphics3D.POINT_SPRITE_FLAG_PIXEL_SIZE
                | Graphics3D.POINT_SPRITE_FLAG_NO_PERSPECTIVE));
    }

    private static int copyCommandPayload(int[] commands, int index, int[] target, String label) {
        if (target == null || target.length == 0) {
            return index;
        }
        ensureCommandOperands(commands, index, target.length, label);
        System.arraycopy(commands, index, target, 0, target.length);
        return index + target.length;
    }

    private static void ensureCommandOperands(int[] commands, int index, int count, String label) {
        if (commands.length - index < count) {
            throw new IllegalArgumentException("Truncated " + label + " payload");
        }
    }

    private static void validateToonParameter(int value, String name) {
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException(name + " must be in [0,255]: " + value);
        }
    }

    private static String describeTextures(SoftwareTexture[] textures) {
        if (textures == null) {
            return "null";
        }
        String[] description = new String[textures.length];
        for (int i = 0; i < textures.length; i++) {
            description[i] = describeTexture(textures[i]);
        }
        return Arrays.toString(description);
    }

    private static String describeTexture(SoftwareTexture texture) {
        if (texture == null) {
            return "null";
        }
        return texture.width() + "x" + texture.height() + (texture.sphereMap() ? ":sphere" : ":model");
    }

    private static String describeOptFigure(MascotFigure figure) {
        if (figure == null) {
            return "figure=null";
        }
        int textureCount = figure.numTextures();
        int polygonCount = figure.model() == null ? -1 : figure.model().polygons().length;
        float[] vertices = figure.vertices();
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (int i = 0; i + 2 < vertices.length; i += 3) {
            minX = Math.min(minX, vertices[i]);
            minY = Math.min(minY, vertices[i + 1]);
            minZ = Math.min(minZ, vertices[i + 2]);
            maxX = Math.max(maxX, vertices[i]);
            maxY = Math.max(maxY, vertices[i + 1]);
            maxZ = Math.max(maxZ, vertices[i + 2]);
        }
        return String.format(
                "polygons=%d textures=%d pattern=%d bounds=[%.1f,%.1f,%.1f]->[%.1f,%.1f,%.1f]",
                polygonCount,
                textureCount,
                figure.patternMask(),
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                maxZ
        );
    }
}
