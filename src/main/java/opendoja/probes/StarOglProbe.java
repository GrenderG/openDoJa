package opendoja.probes;

import com.docomostar.ui.Graphics;
import com.docomostar.ui.Image;
import com.docomostar.ui.ogl.*;
import com.docomostar.ui.ogl.math.*;

/** Verifies STAR casts, shared buffer storage, segments, typed uploads, and mutable math fields. */
public final class StarOglProbe {
    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");
        verifyMath();
        verifyBuffersAndRendering();
        verifyMotionStubs();
        System.out.println("star-ogl-probe-ok");
    }

    private static void verifyMath() {
        var matrix = new Matrix4f();
        matrix.setIdentity();
        matrix.m = matrix.m.clone();
        matrix.m[12] = 7;
        var vector = new Vector4f(1, 2, 3, 1);
        matrix.transform(vector);
        check(vector.x == 8 && vector.y == 2 && vector.w == 1, "Public matrix writes reach the backend");
        var copy = new Matrix4f(matrix);
        matrix.m[12] = 4;
        check(copy.m[12] == 7 && copy.m != matrix.m, "Matrix copy owns its array");
        var expected = new com.nttdocomo.ui.ogl.math.Matrix4f();
        expected.setIdentity(); expected.m[12] = 4;
        matrix.rotate(0.5f, 0, 1, 0); expected.rotate(0.5f, 0, 1, 0);
        matrix.scale(2, 3, 4); expected.scale(2, 3, 4);
        matrix.invert(); expected.invert();
        check(java.util.Arrays.equals(matrix.m, expected.m), "STAR operations preserve host matrix semantics");
        var x = new Vector3f(3, 0, 0); x.normalize();
        var z = new Vector3f(); z.cross(x, new Vector3f(0, 1, 0));
        check(z.z == 1 && x.dot(new Vector3f(2, 0, 0)) == 2, "Shared vector operations");
        z.cross(z, new Vector3f(0, 1, 0));
        check(z.x == -1 && z.y == 0 && z.z == 0, "Vector operations allow aliased input and output");
        class GameVector extends Tuple4f { GameVector() { super(1, 2, 3, 1); } }
        var subclass = new GameVector(); subclass.mul(copy);
        check(subclass.x == 8, "Game subclasses retain tuple fields and STAR overloads");
    }

    private static void verifyBuffersAndRendering() {
        var factory = DirectBufferFactory.getFactory();
        float[] leftQuad = {-1, -1, 0, 0, -1, 0, -1, 1, 0, -1, 1, 0, 0, -1, 0, 0, 1, 0};
        var vertices = factory.allocateFloatBuffer(21);
        vertices.put(0, new float[]{999, 999, 999});
        vertices.put(3, leftQuad);
        vertices.setSegment(3, 18);
        var hostVertices = (com.nttdocomo.ui.ogl.FloatBuffer) DirectBufferFactory.unwrap(vertices);
        check(DirectBufferFactory.unwrap(vertices) == hostVertices, "Buffer wrapper preserves host allocation identity");
        hostVertices.put(3, new float[]{-1});
        check(vertices.get(3, new float[1])[0] == -1, "Host writes remain visible through STAR");

        var image = Image.createImage(48, 24);
        Graphics graphics = image.getGraphics();
        check(graphics.copy() instanceof GraphicsOGL, "Graphics copies retain STAR OpenGL support");
        GraphicsOGL gl = graphics;
        gl.beginDrawing();
        try {
            gl.glViewport(0, 0, 48, 24);
            gl.glMatrixMode(GraphicsOGL.GL_PROJECTION); gl.glLoadIdentity();
            gl.glMatrixMode(GraphicsOGL.GL_MODELVIEW); gl.glLoadIdentity();
            gl.glDisable(GraphicsOGL.GL_CULL_FACE); gl.glDisable(GraphicsOGL.GL_DEPTH_TEST);
            gl.glDisable(GraphicsOGL.GL_LIGHTING); gl.glDisable(GraphicsOGL.GL_TEXTURE_2D);
            gl.glClearColor(0, 0, 0, 1); gl.glClear(GraphicsOGL.GL_COLOR_BUFFER_BIT);
            gl.glEnableClientState(GraphicsOGL.GL_VERTEX_ARRAY);
            gl.glVertexPointer(3, GraphicsOGL.GL_FLOAT, 0, vertices);
            gl.glColor4f(1, 0, 0, 1);
            var indices = factory.allocateShortBuffer(new short[]{99, 0, 1, 2, 3, 4, 5});
            indices.setSegment(1, 6);
            gl.glDrawElements(GraphicsOGL.GL_TRIANGLES, 6, indices);
            gl.glFlush();
            pixel(graphics, 5, 12, 0xff0000, "Segmented STAR vertex/index buffers draw correctly");
            pixel(graphics, 40, 12, 0, "Sentinel vertices must not be rendered");

            float[] rightQuad = leftQuad.clone();
            for (int i = 0; i < rightQuad.length; i += 3) rightQuad[i] += 1;
            vertices.put(3, rightQuad);
            gl.glClear(GraphicsOGL.GL_COLOR_BUFFER_BIT);
            gl.glDrawArrays(GraphicsOGL.GL_TRIANGLES, 0, 6); gl.glFlush();
            pixel(graphics, 5, 12, 0, "Previous vertex positions are cleared");
            pixel(graphics, 40, 12, 0xff0000, "Draw uses live host buffer contents");

            byte[] paletteTexture = new byte[72]; // 16 RGBA palette entries and 16 four-bit pixels.
            paletteTexture[0] = (byte)255; paletteTexture[3] = (byte)255;
            var texture = factory.allocateByteBuffer(paletteTexture);
            int[] names = new int[1]; gl.glGenTextures(names); gl.glBindTexture(GraphicsOGL.GL_TEXTURE_2D, names[0]);
            gl.glCompressedTexImage2D(GraphicsOGL.GL_TEXTURE_2D, 0, GraphicsOGL.GL_PALETTE4_RGBA8_OES, 4, 4, 0, texture);
            gl.glTexParameterf(GraphicsOGL.GL_TEXTURE_2D, GraphicsOGL.GL_TEXTURE_MIN_FILTER, GraphicsOGL.GL_NEAREST);
            gl.glTexParameterf(GraphicsOGL.GL_TEXTURE_2D, GraphicsOGL.GL_TEXTURE_MAG_FILTER, GraphicsOGL.GL_NEAREST);
            gl.glEnable(GraphicsOGL.GL_TEXTURE_2D); gl.glColor4f(1, 1, 1, 1);
            gl.glEnableClientState(GraphicsOGL.GL_TEXTURE_COORD_ARRAY);
            gl.glTexCoordPointer(2, GraphicsOGL.GL_FLOAT, 0, factory.allocateFloatBuffer(new float[]{0, 0, 1, 0, 0, 1, 0, 1, 1, 0, 1, 1}));
            gl.glClear(GraphicsOGL.GL_COLOR_BUFFER_BIT);
            gl.glDrawArrays(GraphicsOGL.GL_TRIANGLES, 0, 6); gl.glFlush();
            pixel(graphics, 40, 12, 0xff0000, "STAR compressed palette texture reaches the shared renderer");
            check(gl.glGetError() == GraphicsOGL.GL_NO_ERROR, "No GL errors from typed STAR calls");
        } finally { gl.endDrawing(); }

        var values = factory.allocateFloatBuffer(new float[]{1, 2, 3});
        var result = factory.allocateFloatBuffer(3);
        check(result.madd(values, values, 2) == result, "Float math retains wrapper identity");
        check(java.util.Arrays.equals(result.get(0, new float[3]), new float[]{3, 6, 9}), "Shared multiply-add");
        var translate = new Matrix4f(); translate.setIdentity(); translate.m[12] = 10;
        check(result.transform(values, translate, 3, 1) == result, "Transform retains wrapper identity");
        check(result.get(0, new float[3])[0] == 11, "Float transform reads the public matrix array");
    }

    private static void verifyMotionStubs() {
        var gyro = com.docomostar.device.location.GyroSensor.getGyroSensor();
        gyro.setEnabled(true); gyro.start(100, false);
        check(gyro.getData().size() == 1 && gyro.getData().getAngularVelocityX()[0] == 0, "Neutral gyro samples");
        gyro.stop(); check(gyro.getData().size() == 0, "Stopped gyro has no buffered samples");
        gyro.setEnabled(false); check(gyro.getCurrentData().size() == 0, "Disabled gyro is inactive");
        gyro.setEnabled(true);
        var accel = com.docomostar.device.location.AccelerationSensor.getAccelerationSensor().getCurrentData();
        check(accel.size() > 0 && accel.getRoll()[0] == 0 && accel.getPitch()[0] == 0, "Acceleration facade uses neutral host samples");
    }
    private static void pixel(Graphics graphics, int x, int y, int expected, String message) {
        int actual = graphics.getRGBPixel(x, y) & 0xffffff;
        check(actual == expected, message + ": " + Integer.toHexString(actual));
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
