package opendoja.demo;

import com.nttdocomo.ui.Graphics;
import com.nttdocomo.ui.Image;
import com.nttdocomo.ui.graphics3d.Primitive;

public final class CrossCallDepthProbe {
    private CrossCallDepthProbe() {
    }

    public static void main(String[] args) {
        DemoLog.enableInfoLogging();
        Image image = Image.createImage(64, 64);
        Graphics graphics = image.getGraphics();
        graphics.setColor(Graphics.BLACK);
        graphics.fillRect(0, 0, 64, 64);
        graphics.setParallelView(64, 64);

        Primitive ramp = coloredQuad(0xFFFF0000,
                -24, 0, -20,
                24, 0, -20,
                24, 24, -20,
                -24, 24, -20);
        Primitive farProp = coloredQuad(0xFF00FF00,
                -18, -8, 0,
                18, -8, 0,
                18, 18, 0,
                -18, 18, 0);

        graphics.renderObject3D(ramp, null);
        graphics.renderObject3D(farProp, null);
        graphics.flushBuffer();

        int occludedPixel = graphics.getRGBPixel(32, 40);
        int visiblePixel = graphics.getRGBPixel(32, 26);
        if (occludedPixel != 0xFFFF0000 || visiblePixel != 0xFF00FF00) {
            throw new IllegalStateException(String.format(
                    "cross-call depth failed occluded=%08x visible=%08x",
                    occludedPixel,
                    visiblePixel));
        }

        graphics.setColor(Graphics.BLACK);
        graphics.fillRect(0, 0, 64, 64);
        graphics.renderObject3D(farProp, null);
        graphics.flushBuffer();
        int nextFramePixel = graphics.getRGBPixel(32, 40);
        if (nextFramePixel != 0xFF00FF00) {
            throw new IllegalStateException(String.format(
                    "depth buffer did not reset between frames pixel=%08x",
                    nextFramePixel));
        }

        DemoLog.info(CrossCallDepthProbe.class, String.format(
                "occluded=%08x visible=%08x nextFrame=%08x",
                occludedPixel,
                visiblePixel,
                nextFramePixel));
    }

    private static Primitive coloredQuad(int color,
                                         int x0, int y0, int z0,
                                         int x1, int y1, int z1,
                                         int x2, int y2, int z2,
                                         int x3, int y3, int z3) {
        Primitive primitive = new Primitive(
                Primitive.PRIMITIVE_QUADS,
                Primitive.COLOR_PER_PRIMITIVE,
                1);
        int[] vertices = primitive.getVertexArray();
        setVertex(vertices, 0, x0, y0, z0);
        setVertex(vertices, 1, x1, y1, z1);
        setVertex(vertices, 2, x2, y2, z2);
        setVertex(vertices, 3, x3, y3, z3);
        primitive.getColorArray()[0] = color;
        return primitive;
    }

    private static void setVertex(int[] vertices, int index, int x, int y, int z) {
        int offset = index * 3;
        vertices[offset] = x;
        vertices[offset + 1] = y;
        vertices[offset + 2] = z;
    }
}
