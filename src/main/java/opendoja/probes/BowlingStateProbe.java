package opendoja.probes;

import com.acrodea.xf3.xfeCamera;
import com.acrodea.xf3.xfeGroup;
import com.acrodea.xf3.math.xfMatrix4;
import com.acrodea.xf3.math.xfVector3;
import com.nttdocomo.ui.Display;
import com.nttdocomo.ui.Frame;
import com.nttdocomo.ui.ogl.math.Matrix4f;
import com.nttdocomo.ui.ogl.math.Point4f;
import opendoja.host.DoJaRuntime;
import opendoja.host.JamLauncher;

import java.lang.reflect.Field;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;

public final class BowlingStateProbe {
    private BowlingStateProbe() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 4 || ((args.length - 2) % 2) != 0) {
            throw new IllegalArgumentException(
                    "Usage: BowlingStateProbe <jam-path> <initial-delay-ms> (<key> <after-ms>)+");
        }
        Path jamPath = Path.of(args[0]);
        long initialDelay = Long.parseLong(args[1]);

        Thread launchThread = new Thread(() -> {
            try {
                JamLauncher.launch(jamPath);
            } catch (Throwable throwable) {
                throwable.printStackTrace(System.err);
            }
        }, "bowling-state-probe-launch");
        launchThread.setDaemon(true);
        launchThread.start();

        Throwable failure = null;
        try {
            waitForRuntime();
            Thread.sleep(Math.max(0L, initialDelay));
            for (int i = 2; i < args.length; i += 2) {
                int key = parseKey(args[i]);
                long afterMillis = Long.parseLong(args[i + 1]);
                DoJaRuntime runtime = requireRuntime();
                runtime.dispatchSyntheticKey(key, Display.KEY_PRESSED_EVENT);
                Thread.sleep(200L);
                runtime.dispatchSyntheticKey(key, Display.KEY_RELEASED_EVENT);
                Thread.sleep(Math.max(0L, afterMillis));
            }
            dumpState();
        } catch (Throwable throwable) {
            failure = throwable;
            throwable.printStackTrace(System.err);
        } finally {
            DoJaRuntime runtime = DoJaRuntime.current();
            if (runtime != null) {
                runtime.shutdown();
            }
            System.exit(failure == null ? 0 : 1);
        }
    }

    private static void dumpState() throws Exception {
        Frame frame = requireRuntime().getCurrentFrame();
        System.out.println("frame=" + frame.getClass().getName());

        Object cameraController = getField(frame, "bfr");
        Object camera = getField(frame, "bfo");
        Object root = getField(frame, "mRoot");
        Object avatarEngine = getField(frame, "bec");

        System.out.println("root=" + (root == null ? "null" : root.getClass().getName()));
        System.out.println("cameraController=" + (cameraController == null ? "null" : cameraController.getClass().getName()));
        System.out.println("camera=" + (camera == null ? "null" : camera.getClass().getName()));
        System.out.println("avatarEngine=" + (avatarEngine == null ? "null" : avatarEngine.getClass().getName()));

        if (cameraController != null) {
            System.out.println("controller.mPosition=" + vectorString((xfVector3) getField(cameraController, "mPosition")));
            System.out.println("controller.lookAt=" + vectorString((xfVector3) getField(cameraController, "lookAt")));
            System.out.println("controller.mPositionW=" + vectorString((xfVector3) getField(cameraController, "mPositionW")));
            System.out.println("controller.lookAtW=" + vectorString((xfVector3) getField(cameraController, "lookAtW")));
        }
        if (camera instanceof xfeCamera xfeCamera) {
            System.out.println("camera.localPublic=" + matrixString(xfeCamera.getTransformation().getMatrix()));
            System.out.println("camera.localInternal=" + matrixString(xfeCamera.getTransformation().getInternalMatrix()));
            System.out.println("camera.worldInternal=" + matrixString(worldMatrix(xfeCamera)));
        }
        dumpAvatarState(frame, avatarEngine);
    }

    private static void dumpAvatarState(Object frame, Object avatarEngine) throws Exception {
        System.out.println("avatar.apn=" + getIntField(frame, "apn"));
        System.out.println("avatar.avm=" + getIntField(frame, "avm"));
        System.out.println("avatar.blb=" + getBooleanField(frame, "blb"));
        System.out.println("avatar.blc=" + getBooleanField(frame, "blc"));
        System.out.println("avatar.avf=" + intArrayString((int[]) getField(frame, "avf")));
        System.out.println("avatar.avg=" + intArrayString((int[]) getField(frame, "avg")));
        System.out.println("avatar.avi=" + intArrayString((int[]) getField(frame, "avi")));
        System.out.println("avatar.bgd=" + matrix4fString((Matrix4f) getField(frame, "bgd")));
        System.out.println("avatar.omatP=" + xoMatrixString(getField(frame, "omatP")));
        dumpEsAvatarMatrix("avatar.bic", getField(frame, "bic"));
        dumpEsAvatarMatrix("avatar.bid", getField(frame, "bid"));
        dumpEsAvatarMatrix("avatar.bqr", getField(frame, "bqr"));
        if (avatarEngine != null) {
            dumpFixedMatrix("engine.world", (int[]) getField(avatarEngine, "a"));
            dumpFixedMatrix("engine.avatar", (int[]) getField(avatarEngine, "d"));
            dumpFixedMatrix("engine.camera", (int[]) getField(avatarEngine, "e"));
        }
        dumpAvatarVertexBounds("avatar.engineBounds", getField(frame, "bed"), avatarEngine == null ? null : (int[]) getField(avatarEngine, "a"), frame);
        dumpAvatarVertexBounds("avatar.playerBounds", getField(frame, "bed"), matrixElements(getField(frame, "bic")), frame);
        dumpAvatarVertexBounds("avatar.ballBounds", getField(frame, "bed"), matrixElements(getField(frame, "bid")), frame);
        dumpAvatarSurfaceBounds(frame, getField(frame, "bed"), matrixElements(getField(frame, "bic")));
    }

    private static xfMatrix4 worldMatrix(xfeCamera camera) {
        xfMatrix4 result = new xfMatrix4();
        ArrayList<xfMatrix4> chain = new ArrayList<>();
        xfeGroup current = camera;
        while (current != null) {
            chain.add(current.getTransformation().getInternalMatrix());
            current = current.getParent();
        }
        for (int i = chain.size() - 1; i >= 0; i--) {
            result.mul(chain.get(i));
        }
        return result;
    }

    private static Object getField(Object target, String name) throws Exception {
        Field field = findField(target.getClass(), name);
        return field.get(target);
    }

    private static int getIntField(Object target, String name) throws Exception {
        Field field = findField(target.getClass(), name);
        return field.getInt(target);
    }

    private static boolean getBooleanField(Object target, String name) throws Exception {
        Field field = findField(target.getClass(), name);
        return field.getBoolean(target);
    }

    private static Field findField(Class<?> type, String name) throws Exception {
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static String vectorString(xfVector3 vector) {
        if (vector == null) {
            return "null";
        }
        return "[" + vector.x + "," + vector.y + "," + vector.z + "]";
    }

    private static String matrixString(xfMatrix4 matrix) {
        if (matrix == null) {
            return "null";
        }
        StringBuilder out = new StringBuilder();
        for (int row = 0; row < 4; row++) {
            if (row > 0) {
                out.append('|');
            }
            out.append('[');
            for (int column = 0; column < 4; column++) {
                if (column > 0) {
                    out.append(',');
                }
                out.append(matrix.m[row][column]);
            }
            out.append(']');
        }
        return out.toString();
    }

    private static void dumpEsAvatarMatrix(String label, Object matrixObject) throws Exception {
        if (matrixObject == null) {
            System.out.println(label + "=null");
            return;
        }
        Field elementField = matrixObject.getClass().getDeclaredField("a");
        elementField.setAccessible(true);
        dumpFixedMatrix(label, (int[]) elementField.get(matrixObject));
    }

    private static void dumpFixedMatrix(String label, int[] values) {
        if (values == null) {
            System.out.println(label + "=null");
            return;
        }
        int determinant = (values[0] * ((values[4] * values[8]) - (values[5] * values[7])))
                - (values[3] * ((values[1] * values[8]) - (values[2] * values[7])))
                + (values[6] * ((values[1] * values[5]) - (values[2] * values[4])));
        System.out.println(label
                + "="
                + "[" + values[0] + "," + values[3] + "," + values[6] + "," + values[9] + "]"
                + "|[" + values[1] + "," + values[4] + "," + values[7] + "," + values[10] + "]"
                + "|[" + values[2] + "," + values[5] + "," + values[8] + "," + values[11] + "]"
                + " det3x3=" + determinant);
    }

    private static String matrix4fString(Matrix4f matrix) {
        if (matrix == null) {
            return "null";
        }
        float[] values = matrix.m;
        return "[" + values[0] + "," + values[4] + "," + values[8] + "," + values[12] + "]"
                + "|[" + values[1] + "," + values[5] + "," + values[9] + "," + values[13] + "]"
                + "|[" + values[2] + "," + values[6] + "," + values[10] + "," + values[14] + "]"
                + "|[" + values[3] + "," + values[7] + "," + values[11] + "," + values[15] + "]";
    }

    private static String intArrayString(int[] values) {
        if (values == null) {
            return "null";
        }
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(values[i]);
        }
        return out.append(']').toString();
    }

    private static String xoMatrixString(Object xo) throws Exception {
        if (xo == null) {
            return "null";
        }
        Field matrixField = xo.getClass().getDeclaredField("m");
        matrixField.setAccessible(true);
        return intArrayString((int[]) matrixField.get(xo));
    }

    private static int[] matrixElements(Object matrixObject) throws Exception {
        if (matrixObject == null) {
            return null;
        }
        Field elementField = matrixObject.getClass().getDeclaredField("a");
        elementField.setAccessible(true);
        return (int[]) elementField.get(matrixObject);
    }

    private static void dumpAvatarVertexBounds(String label, Object avatar, int[] world, Object frame) throws Exception {
        if (world == null) {
            return;
        }
        if (avatar == null) {
            System.out.println(label + "=null");
            return;
        }
        Matrix4f view = (Matrix4f) getField(frame, "bgd");
        Field partsField = avatar.getClass().getDeclaredField("b");
        partsField.setAccessible(true);
        Object[] parts = (Object[]) partsField.get(avatar);
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        int vertexCount = 0;
        int behindCamera = 0;
        int pastNear = 0;
        float minScreenX = Float.POSITIVE_INFINITY;
        float minScreenY = Float.POSITIVE_INFINITY;
        float maxScreenX = Float.NEGATIVE_INFINITY;
        float maxScreenY = Float.NEGATIVE_INFINITY;
        int cwTriangles = 0;
        int ccwTriangles = 0;
        float near = (20480f * Display.getHeight()) / 650f;
        Projection projection = bowlingProjection(near, (57344f * Display.getHeight()) / 650f, 341);
        for (Object part : parts) {
            if (part == null) {
                continue;
            }
            Object avatarModel = invokeNoArg(part, "getModel");
            if (avatarModel == null) {
                continue;
            }
            Object model = getField(avatarModel, "b");
            if (model == null) {
                model = getField(avatarModel, "a");
            }
            if (model == null) {
                continue;
            }
            int[] vertices = (int[]) invokeNoArg(model, "getVertices");
            float[] projected = new float[(vertices.length / 3) * 2];
            for (int i = 0; i + 2 < vertices.length; i += 3) {
                float worldX = fixedTransform(world, vertices[i], vertices[i + 1], vertices[i + 2], 0);
                float worldY = fixedTransform(world, vertices[i], vertices[i + 1], vertices[i + 2], 1);
                float worldZ = fixedTransform(world, vertices[i], vertices[i + 1], vertices[i + 2], 2);
                Point4f tuple = new Point4f(worldX, worldY, worldZ, 1f);
                view.transform(tuple);
                minX = Math.min(minX, tuple.x);
                minY = Math.min(minY, tuple.y);
                minZ = Math.min(minZ, tuple.z);
                maxX = Math.max(maxX, tuple.x);
                maxY = Math.max(maxY, tuple.y);
                maxZ = Math.max(maxZ, tuple.z);
                if (tuple.z >= 0f) {
                    behindCamera++;
                }
                if (tuple.z >= -near) {
                    pastNear++;
                }
                float[] screen = projectPoint(tuple.x, tuple.y, tuple.z, projection);
                projected[(i / 3) * 2] = screen[0];
                projected[(i / 3) * 2 + 1] = screen[1];
                minScreenX = Math.min(minScreenX, screen[0]);
                minScreenY = Math.min(minScreenY, screen[1]);
                maxScreenX = Math.max(maxScreenX, screen[0]);
                maxScreenY = Math.max(maxScreenY, screen[1]);
                vertexCount++;
            }
            short[] indices = (short[]) invokeNoArg(model, "getVertexIndices");
            for (int i = 0; i + 2 < indices.length; i += 3) {
                int i0 = indices[i] & 0xFFFF;
                int i1 = indices[i + 1] & 0xFFFF;
                int i2 = indices[i + 2] & 0xFFFF;
                float area = edge(
                        projected[i0 * 2], projected[i0 * 2 + 1],
                        projected[i1 * 2], projected[i1 * 2 + 1],
                        projected[i2 * 2], projected[i2 * 2 + 1]);
                if (area < 0f) {
                    cwTriangles++;
                } else if (area > 0f) {
                    ccwTriangles++;
                }
            }
        }
        System.out.println(label + ".view="
                + "[" + minX + "," + minY + "," + minZ + "]->[" + maxX + "," + maxY + "," + maxZ + "]"
                + " vertices=" + vertexCount
                + " behindCamera=" + behindCamera
                + " pastNear=" + pastNear
                + " near=" + near);
        System.out.println(label + ".screen="
                + "[" + minScreenX + "," + minScreenY + "]->[" + maxScreenX + "," + maxScreenY + "]"
                + " cwTriangles=" + cwTriangles
                + " ccwTriangles=" + ccwTriangles);
    }

    private static void dumpAvatarSurfaceBounds(Object frame, Object avatar, int[] world) throws Exception {
        if (avatar == null || world == null) {
            return;
        }
        Matrix4f view = (Matrix4f) getField(frame, "bgd");
        Projection projection = bowlingProjection((20480f * Display.getHeight()) / 650f, (57344f * Display.getHeight()) / 650f, 341);
        Field partsField = avatar.getClass().getDeclaredField("b");
        partsField.setAccessible(true);
        Object[] parts = (Object[]) partsField.get(avatar);
        Set<Integer> textureIds = new HashSet<>();
        for (int partIndex = 0; partIndex < parts.length; partIndex++) {
            Object part = parts[partIndex];
            if (part == null) {
                continue;
            }
            Object avatarModel = invokeNoArg(part, "getModel");
            if (avatarModel == null) {
                continue;
            }
            Object model = getField(avatarModel, "b");
            if (model == null) {
                model = getField(avatarModel, "a");
            }
            if (model == null) {
                continue;
            }
            int surfaceCount = (int) invokeNoArg(model, "getSurfaceCount");
            for (int surfaceIndex = 0; surfaceIndex < surfaceCount; surfaceIndex++) {
                model.getClass().getMethod("setCurrentSurface", int.class).invoke(model, surfaceIndex);
                int[] vertices = (int[]) invokeNoArg(model, "getVertices");
                short[] indices = (short[]) invokeNoArg(model, "getVertexIndices");
                if (vertices == null || indices == null || indices.length == 0) {
                    continue;
                }
                float minScreenX = Float.POSITIVE_INFINITY;
                float minScreenY = Float.POSITIVE_INFINITY;
                float maxScreenX = Float.NEGATIVE_INFINITY;
                float maxScreenY = Float.NEGATIVE_INFINITY;
                int cwTriangles = 0;
                int ccwTriangles = 0;
                float[] projected = new float[(vertices.length / 3) * 2];
                for (int i = 0; i + 2 < vertices.length; i += 3) {
                    float worldX = fixedTransform(world, vertices[i], vertices[i + 1], vertices[i + 2], 0);
                    float worldY = fixedTransform(world, vertices[i], vertices[i + 1], vertices[i + 2], 1);
                    float worldZ = fixedTransform(world, vertices[i], vertices[i + 1], vertices[i + 2], 2);
                    Point4f tuple = new Point4f(worldX, worldY, worldZ, 1f);
                    view.transform(tuple);
                    float[] screen = projectPoint(tuple.x, tuple.y, tuple.z, projection);
                    projected[(i / 3) * 2] = screen[0];
                    projected[(i / 3) * 2 + 1] = screen[1];
                    minScreenX = Math.min(minScreenX, screen[0]);
                    minScreenY = Math.min(minScreenY, screen[1]);
                    maxScreenX = Math.max(maxScreenX, screen[0]);
                    maxScreenY = Math.max(maxScreenY, screen[1]);
                }
                for (int i = 0; i + 2 < indices.length; i += 3) {
                    int i0 = indices[i] & 0xFFFF;
                    int i1 = indices[i + 1] & 0xFFFF;
                    int i2 = indices[i + 2] & 0xFFFF;
                    float area = edge(
                            projected[i0 * 2], projected[i0 * 2 + 1],
                            projected[i1 * 2], projected[i1 * 2 + 1],
                            projected[i2 * 2], projected[i2 * 2 + 1]);
                    if (area < 0f) {
                        cwTriangles++;
                    } else if (area > 0f) {
                        ccwTriangles++;
                    }
                }
                float screenArea = (maxScreenX - minScreenX) * (maxScreenY - minScreenY);
                int textureId = (int) invokeNoArg(model, "getTextureId");
                textureIds.add(textureId);
                System.out.println("avatar.playerSurface part=" + partIndex
                        + " surface=" + surfaceIndex
                        + " texture=" + textureId
                        + " useTexture=" + invokeNoArg(model, "checkUseTexture")
                        + " screen=[" + minScreenX + "," + minScreenY + "]->[" + maxScreenX + "," + maxScreenY + "]"
                        + " area=" + screenArea
                        + " cwTriangles=" + cwTriangles
                        + " ccwTriangles=" + ccwTriangles);
            }
        }
        dumpTextureStats(frame, textureIds);
    }

    private static void dumpTextureStats(Object frame, Set<Integer> textureIds) throws Exception {
        Object graphics = getField(frame, "m_o");
        Field oglField = findField(graphics.getClass(), "ogl");
        Object ogl = oglField.get(graphics);
        Field texturesField = findField(ogl.getClass(), "textures");
        @SuppressWarnings("unchecked")
        Map<Integer, Object> textures = (Map<Integer, Object>) texturesField.get(ogl);
        for (int textureId : textureIds) {
        Object texture = textures.get(textureId);
        if (texture == null) {
            System.out.println("avatar.playerTexture id=" + textureId + " missing");
            continue;
        }
        Field widthField = findField(texture.getClass(), "width");
        Field heightField = findField(texture.getClass(), "height");
        Field pixelsField = findField(texture.getClass(), "pixels");
            int width = widthField.getInt(texture);
            int height = heightField.getInt(texture);
            int[] pixels = (int[]) pixelsField.get(texture);
            int opaque = 0;
            int nonBlack = 0;
            for (int pixel : pixels) {
                if (((pixel >>> 24) & 0xFF) != 0) {
                    opaque++;
                }
                if ((pixel & 0x00FFFFFF) != 0) {
                    nonBlack++;
                }
            }
            System.out.println("avatar.playerTexture id=" + textureId
                    + " size=" + width + "x" + height
                    + " opaque=" + opaque + "/" + pixels.length
                    + " nonBlack=" + nonBlack + "/" + pixels.length
                    + " first=" + (pixels.length == 0 ? 0 : pixels[0]));
            dumpTexturePng(textureId, width, height, pixels);
        }
    }

    private static void dumpTexturePng(int textureId, int width, int height, int[] pixels) throws Exception {
        if (width <= 0 || height <= 0 || pixels.length != width * height) {
            return;
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, pixels, 0, width);
        ImageIO.write(image, "png", new File("/tmp/bowling-player-texture-" + textureId + ".png"));
    }

    private static Object invokeNoArg(Object target, String name) throws Exception {
        return target.getClass().getMethod(name).invoke(target);
    }

    private static float fixedTransform(int[] matrix, int x, int y, int z, int row) {
        int translated = switch (row) {
            case 0 -> (((matrix[0] * x) + (matrix[3] * y) + (matrix[6] * z)) >> 12) + matrix[9];
            case 1 -> (((matrix[1] * x) + (matrix[4] * y) + (matrix[7] * z)) >> 12) + matrix[10];
            case 2 -> (((matrix[2] * x) + (matrix[5] * y) + (matrix[8] * z)) >> 12) + matrix[11];
            default -> throw new IllegalArgumentException("row");
        };
        return translated;
    }

    private static Projection bowlingProjection(float near, float far, int angle) {
        float halfAngle = angle * 0.5f;
        float halfHeight = near * (float) Math.tan(Math.toRadians((halfAngle / 4096f) * 360f));
        float fullHeight = halfHeight * 2f;
        float fullWidth = fullHeight;
        int width = Display.getWidth();
        int height = Display.getHeight();
        if (height > width) {
            fullWidth = fullHeight * width / height;
        } else if (height < width) {
            fullHeight = fullHeight * height / width;
        }
        return new Projection(-fullWidth * 0.5f, fullWidth * 0.5f, -fullHeight * 0.5f, fullHeight * 0.5f, near, far, width, height);
    }

    private static float[] projectPoint(float x, float y, float z, Projection projection) {
        float clipX = ((2f * projection.near) / (projection.right - projection.left)) * x
                + ((projection.right + projection.left) / (projection.right - projection.left)) * z;
        float clipY = ((2f * projection.near) / (projection.top - projection.bottom)) * y
                + ((projection.top + projection.bottom) / (projection.top - projection.bottom)) * z;
        float clipZ = -((projection.far + projection.near) / (projection.far - projection.near)) * z
                - ((2f * projection.far * projection.near) / (projection.far - projection.near));
        float clipW = -z;
        float ndcX = clipX / clipW;
        float ndcY = clipY / clipW;
        float screenX = ((ndcX + 1f) * 0.5f) * projection.width;
        float screenY = ((1f - ndcY) * 0.5f) * projection.height;
        return new float[]{screenX, screenY, clipZ / clipW};
    }

    private static float edge(float ax, float ay, float bx, float by, float px, float py) {
        return ((px - ax) * (by - ay)) - ((py - ay) * (bx - ax));
    }

    private record Projection(float left, float right, float bottom, float top, float near, float far, int width, int height) {
    }

    private static int parseKey(String keyName) {
        return switch (keyName.toUpperCase()) {
            case "SELECT", "ENTER" -> Display.KEY_SELECT;
            case "LEFT" -> Display.KEY_LEFT;
            case "RIGHT" -> Display.KEY_RIGHT;
            case "UP" -> Display.KEY_UP;
            case "DOWN" -> Display.KEY_DOWN;
            case "SOFT1" -> Display.KEY_SOFT1;
            case "SOFT2" -> Display.KEY_SOFT2;
            default -> throw new IllegalArgumentException("Unsupported key: " + keyName);
        };
    }

    private static void waitForRuntime() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000L;
        while (DoJaRuntime.current() == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(20L);
        }
        if (DoJaRuntime.current() == null) {
            throw new IllegalStateException("DoJa runtime did not initialize");
        }
    }

    private static DoJaRuntime requireRuntime() {
        DoJaRuntime runtime = DoJaRuntime.current();
        if (runtime == null) {
            throw new IllegalStateException("DoJa runtime exited before probe completed");
        }
        return runtime;
    }
}
