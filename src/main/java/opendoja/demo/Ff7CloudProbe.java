package opendoja.demo;

import opendoja.g3d.MascotActionTableData;
import opendoja.g3d.MascotFigure;
import opendoja.g3d.MascotLoader;
import opendoja.g3d.MbacModel;
import opendoja.g3d.Software3DContext;
import opendoja.g3d.SoftwareTexture;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Ff7CloudProbe {
    private static final int SURFACE_SIZE = 240;
    private static final float[] TITLE_TRANSFORM = {
            -1f, 0f, 0f, 0f,
            0f, -0.9995117f, 0f, 27f,
            0f, 0f, 0.9995117f, 85f,
            0f, 0f, 0f, 1f
    };

    private Ff7CloudProbe() {
    }

    public static void main(String[] args) throws Exception {
        Path outputDir = args.length == 0 ? Path.of("/tmp/opendoja-captures") : Path.of(args[0]);
        Files.createDirectories(outputDir);

        MbacModel model = loadFigure("cloud.mbac");
        MascotActionTableData actionTable = loadActionTable("cloud.mtra");
        SoftwareTexture texture = loadTexture("SB_basic.bmp");
        SoftwareTexture whiteTexture = createSolidTexture(0xFFFFFFFF);

        int[] patterns = {0, 4, -1};
        for (int pattern : patterns) {
            Path textured = outputDir.resolve("ff7-cloud-probe-textured-" + pattern + ".png");
            Path flat = outputDir.resolve("ff7-cloud-probe-flat-" + pattern + ".png");
            render(textured, model, actionTable, texture, pattern);
            render(flat, model, actionTable, whiteTexture, pattern);
            System.out.println(textured.toAbsolutePath());
            System.out.println(flat.toAbsolutePath());
        }
    }

    private static void render(Path output, MbacModel model, MascotActionTableData actionTable, SoftwareTexture texture, int pattern) throws IOException {
        MascotFigure figure = new MascotFigure(model);
        figure.setTexture(texture);
        figure.setPattern(pattern);
        figure.setAction(actionTable, 26);
        figure.setTime(0);

        BufferedImage image = new BufferedImage(SURFACE_SIZE, SURFACE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            Software3DContext context = new Software3DContext();
            context.setUiPerspectiveView(1f, 500f, 45f);
            context.setUiTransform(TITLE_TRANSFORM);
            context.renderUiFigure(g, image, 0, 0, SURFACE_SIZE, SURFACE_SIZE, figure, null, 0, 1f);
        } finally {
            g.dispose();
        }
        ImageIO.write(image, "png", output.toFile());
    }

    private static MbacModel loadFigure(String name) throws IOException {
        try (InputStream input = resource(name)) {
            return MascotLoader.loadFigure(input);
        }
    }

    private static MascotActionTableData loadActionTable(String name) throws IOException {
        try (InputStream input = resource(name)) {
            return MascotLoader.loadActionTable(input);
        }
    }

    private static SoftwareTexture loadTexture(String name) throws IOException {
        try (InputStream input = resource(name)) {
            return new SoftwareTexture(input, true);
        }
    }

    private static InputStream resource(String name) {
        InputStream input = Ff7CloudProbe.class.getClassLoader().getResourceAsStream(name);
        if (input == null) {
            throw new IllegalArgumentException("Missing resource: " + name);
        }
        return input;
    }

    private static SoftwareTexture createSolidTexture(int argb) throws IOException {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, argb);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return new SoftwareTexture(output.toByteArray(), true);
    }
}
