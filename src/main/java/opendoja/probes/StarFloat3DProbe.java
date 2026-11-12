package opendoja.probes;

import com.docomostar.ui.Graphics;
import com.docomostar.ui.Image;
import com.docomostar.ui.graphics3d.*;
import com.docomostar.ui.util3d.Transform;
import com.docomostar.ui.util3d.Vector3D;
import opendoja.host.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;

/** Exercises STAR factories, live primitive data and the shared float renderer. */
public final class StarFloat3DProbe {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        DesktopLauncher.launch(LaunchConfig.builder(ProbeApp.class).parameter("AppType", "FullApp").build());
        try {
            var canvas = new ProbeCanvas();
            check(canvas.getGraphics() instanceof Graphics3D, "Canvas float 3D interface");
            verifyFactories();
            verifyTransforms();
            verifyRendering();
            verifyPixels();
        } finally { DoJaRuntime.current().shutdown(); }
        System.out.println("star-float3d-probe-ok");
    }
    private static void verifyFactories() throws Exception {
        // Minimal empty model and one-action table; these fixtures contain no game assets.
        byte[] model = new byte[12]; model[0] = 'M'; model[1] = 'B'; model[2] = 3;
        Object3D loaded = Figure.createInstance(model);
        check(loaded instanceof Figure && loaded.getType() == Object3D.TYPE_FIGURE, "Typed model factory");
        Figure figure = (Figure) loaded;
        byte[] action = new byte[30]; action[0] = 'M'; action[1] = 'T'; action[2] = 3;
        action[4] = 1; action[28] = 10;
        ActionTable table = (ActionTable) ActionTable.createInstance(action);
        check(table.getNumActions() == 1 && table.getMaxFrame(0) == (10 << 16), "Typed action-table factory and 16.16 frame units");
        figure.setAction(table, 0); figure.setTime(7 << 16);
        check(figure.getActionTable() == table && figure.getTime() == (7 << 16), "Action identity and shared time");
        var bmp = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        bmp.setRGB(0, 0, 0xffff0000);
        var bytes = new ByteArrayOutputStream(); check(ImageIO.write(bmp, "bmp", bytes), "BMP writer");
        Texture texture = (Texture) Texture.createInstance(new ByteArrayInputStream(bytes.toByteArray()));
        check(texture.getType() == Object3D.TYPE_TEXTURE, "Typed texture factory");
        texture.setBlendMode(Texture.REPLACE); figure.setTexture(texture);
        figure.setTextures(new Texture[]{texture}); figure.setPerspectiveCorrectionEnabled(true);
        check(Object3D.createInstance(new byte[]{1, 2, 3}) == null, "Unsupported byte-array factory");
        try { Object3D.createInstance(new ByteArrayInputStream(new byte[]{1, 2, 3})); throw new AssertionError("Invalid stream accepted"); }
        catch (IOException expected) { }
        figure.dispose(); table.dispose(); texture.dispose();
        check(figure.getType() == Object3D.TYPE_NONE, "Disposal reaches original object");
    }
    private static void verifyTransforms() {
        Vector3D cross = new Vector3D();
        cross.cross(new Vector3D(1, 0, 0), new Vector3D(0, 1, 0));
        check(cross.getZ() == 1 && Vector3D.dot(cross, new Vector3D(0, 0, 2)) == 2, "STAR vector overloads");
        Transform transform = new Transform(); transform.translate(new Vector3D(2, 3, 4));
        Transform copy = new Transform(transform); Vector3D result = new Vector3D();
        copy.transVector(new Vector3D(1, 1, 1), result);
        check(result.getX() == 3 && result.getY() == 4 && result.getZ() == 5, "STAR transform descriptors");
        var light = new Light(); light.setVector(new Vector3D(1, 2, 3));
        var vector = light.getVector(); vector.setX(99);
        check(light.getVector().getX() == 1, "Light getters return component copies");
        light.dispose();
    }
    private static void verifyRendering() {
        Primitive primitive = new Primitive(Primitive.PRIMITIVE_QUADS, Primitive.COLOR_PER_PRIMITIVE, 1);
        int[] vertices = primitive.getVertexArray();
        int[] quad = {-12, -12, 16, 12, -12, 16, 12, 12, 16, -12, 12, 16};
        System.arraycopy(quad, 0, vertices, 0, quad.length);
        primitive.getColorArray()[0] = 0xffff0000;
        Group group = new Group(); group.addElement(primitive);
        check(group.getElement(0) == primitive, "Group preserves object identity");
        group.addElement(primitive); group.removeElement(0);
        check(group.getElement(0) == primitive, "Duplicate group membership preserves object identity");
        Image first = render(group, null);
        check((first.getGraphics().getRGBPixel(32, 32) & 0xffffff) == 0xff0000, "STAR primitive rendered by shared backend");
        primitive.getColorArray()[0] = 0xff00ff00;
        Transform translate = new Transform(); translate.translate(16, 0, 0);
        Image second = render(group, translate);
        check((second.getGraphics().getRGBPixel(48, 32) & 0xffffff) == 0x00ff00, "Live buffers and transform reach renderer");
        check((second.getGraphics().getRGBPixel(20, 32) & 0xffffff) == 0, "Transform moves the object");
        group.removeElement(0); check(group.getNumElements() == 0, "Group removal");
        first.dispose(); second.dispose(); group.dispose(); primitive.dispose();
    }
    private static Image render(DrawableObject3D object, Transform transform) {
        Image image = Image.createImage(64, 64); Graphics graphics = image.getGraphics();
        graphics.setColor(0xff000000); graphics.fillRect(0, 0, 64, 64);
        Graphics3D renderer = (Graphics3D) graphics;
        renderer.setParallelView(64, 64); renderer.resetLights(); renderer.setFog(null);
        renderer.renderObject3D(object, transform); renderer.flushBuffer();
        return image;
    }
    private static void verifyPixels() {
        Image image = Image.createImage(4, 4); Graphics graphics = image.getGraphics();
        graphics.setRGBPixels(1, 1, 2, 1, new int[]{0x00112233, 0xff445566}, 0);
        int[] pixels = new int[4];
        check(graphics.getRGBPixels(1, 1, 2, 1, pixels, 1) == pixels, "RGB reads retain destination array");
        check((pixels[1] & 0xffffff) == 0x112233 && (pixels[2] & 0xffffff) == 0x445566, "Pixel region descriptors");
        graphics.setColor(0xffabcdef); graphics.setPixel(0, 0);
        check((graphics.getPixel(0, 0) & 0xffffff) == 0xabcdef, "Single pixel access");
        graphics.drawPolyline(new int[]{0, 3}, new int[]{3, 3}, 2);
        check((graphics.getRGBPixel(1, 3) & 0xffffff) == 0xabcdef, "Polyline drawing");
        image.dispose();
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static final class ProbeApp extends com.docomostar.StarApplication { @Override public void started(int reason) { } }
    private static final class ProbeCanvas extends com.docomostar.ui.Canvas { @Override public void paint(Graphics graphics) { } }
}
