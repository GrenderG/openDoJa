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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

public final class MonsterHunterFigureProbe {
    private static final int SURFACE_WIDTH = 240;
    private static final int SURFACE_HEIGHT = 320;

    private MonsterHunterFigureProbe() {
    }

    public static void main(String[] args) throws Exception {
        DemoLog.enableInfoLogging();
        Path outputDir = args.length == 0 ? Path.of("/tmp/opendoja-captures") : Path.of(args[0]);
        Files.createDirectories(outputDir);

        MbacModel model = MascotLoader.loadFigure(Files.readAllBytes(Path.of("resources/mhi_mbac/player/pc1_gard_00.mbac")));
        MascotActionTableData actionTable = MascotLoader.loadActionTable(Files.readAllBytes(Path.of("resources/mhi_mbac/player/m_pc1.mtra")));
        SoftwareTexture[] textures = {
                new SoftwareTexture(Files.readAllBytes(Path.of("resources/mhi_mbac/player/gard_00.bmp")), false),
                new SoftwareTexture(Files.readAllBytes(Path.of("resources/mhi_mbac/player/arm_000.bmp")), false),
                new SoftwareTexture(Files.readAllBytes(Path.of("resources/mhi_mbac/player/gard_00.bmp")), false)
        };

        dumpStats(model);

        int[] interestingPatterns = {0, 2, 18, 34, 66, 130, 258, 514, 1026, 2050, 4098, 8194};
        for (int pattern : interestingPatterns) {
            Path output = outputDir.resolve("mh-player-" + pattern + ".png");
            int opaque = render(output, model, actionTable, textures, pattern);
            DemoLog.info(MonsterHunterFigureProbe.class, "pattern=" + pattern + " opaquePixels=" + opaque + " file=" + output.toAbsolutePath());
        }
    }

    private static int render(Path output, MbacModel model, MascotActionTableData actionTable, SoftwareTexture[] textures, int pattern)
            throws IOException {
        MascotFigure figure = new MascotFigure(model);
        figure.setTextures(textures);
        figure.setPattern(pattern);
        figure.setAction(actionTable, 0);
        figure.setTime(0);

        int[] bounds = bounds(model.rawVertices());
        float centerX = (bounds[0] + bounds[3]) * 0.5f;
        float centerY = (bounds[1] + bounds[4]) * 0.5f;
        float depth = Math.max(256f, bounds[5] - bounds[2] + 512f);
        float[] transform = {
                1f, 0f, 0f, -centerX,
                0f, -1f, 0f, centerY,
                0f, 0f, 1f, -bounds[2] + depth,
                0f, 0f, 0f, 1f
        };

        BufferedImage image = new BufferedImage(SURFACE_WIDTH, SURFACE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            Software3DContext context = new Software3DContext();
            context.setOptScreenCenter(SURFACE_WIDTH / 2, SURFACE_HEIGHT / 2);
            context.setOptPerspective(128, 10240, 512);
            context.setOptViewTransform(transform);
            context.enableOptSemiTransparent(true);
            context.renderOptFigure(g, image, 0, 0, SURFACE_WIDTH, SURFACE_HEIGHT, figure);
        } finally {
            g.dispose();
        }
        ImageIO.write(image, "png", output.toFile());
        return countOpaquePixels(image);
    }

    private static void dumpStats(MbacModel model) {
        int[] bounds = bounds(model.rawVertices());
        Map<Integer, Integer> textureCounts = new TreeMap<>();
        Map<Integer, Integer> patternCounts = new TreeMap<>();
        int textured = 0;
        int transparent = 0;
        for (MbacModel.Polygon polygon : model.polygons()) {
            textureCounts.merge(polygon.textureIndex(), 1, Integer::sum);
            patternCounts.merge(polygon.patternMask(), 1, Integer::sum);
            if (polygon.textureCoords() != null) {
                textured++;
            }
            if (polygon.transparent()) {
                transparent++;
            }
        }
        DemoLog.info(MonsterHunterFigureProbe.class, "patterns=" + model.numPatterns()
                + " polys=" + model.polygons().length
                + " bones=" + model.bones().length
                + " bounds=[" + bounds[0] + "," + bounds[1] + "," + bounds[2] + "]->[" + bounds[3] + "," + bounds[4] + "," + bounds[5] + "]"
                + " textured=" + textured
                + " transparent=" + transparent
                + " textureCounts=" + textureCounts
                + " patternCounts=" + patternCounts);
    }

    private static int[] bounds(int[] vertices) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int i = 0; i + 2 < vertices.length; i += 3) {
            minX = Math.min(minX, vertices[i]);
            minY = Math.min(minY, vertices[i + 1]);
            minZ = Math.min(minZ, vertices[i + 2]);
            maxX = Math.max(maxX, vertices[i]);
            maxY = Math.max(maxY, vertices[i + 1]);
            maxZ = Math.max(maxZ, vertices[i + 2]);
        }
        return new int[]{minX, minY, minZ, maxX, maxY, maxZ};
    }

    private static int countOpaquePixels(BufferedImage image) {
        int opaque = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (((image.getRGB(x, y) >>> 24) & 0xFF) != 0) {
                    opaque++;
                }
            }
        }
        return opaque;
    }
}
