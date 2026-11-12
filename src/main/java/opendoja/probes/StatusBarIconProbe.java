package opendoja.probes;

import com.nttdocomo.ui.Frame;
import opendoja.host.ExternalFrameLayout;
import opendoja.host.ExternalFrameRenderer;
import opendoja.host.JamLauncher;
import opendoja.host.LaunchConfig;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class StatusBarIconProbe {
    private static final int HOST_SCALE = 1;

    private StatusBarIconProbe() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2 && args.length != 3) {
            throw new IllegalArgumentException("Usage: StatusBarIconProbe <jam-path> <output-png> [normal|standby]");
        }
        Path jamPath = Path.of(args[0]);
        Path outputPath = Path.of(args[1]);
        LaunchConfig.LaunchTypeOption launchType = args.length == 3
                ? requireLaunchType(args[2])
                : LaunchConfig.LaunchTypeOption.resolveConfigured();
        LaunchConfig config = JamLauncher.buildLaunchConfig(jamPath, false, launchType);

        ExternalFrameRenderer renderer = new ExternalFrameRenderer(
                true,
                config.statusBarIconDevice(),
                config.iAppliType(),
                config.launchType(),
                config.applicationProfile());
        ExternalFrameLayout layout = renderer.layoutFor(config.width(), config.height(), HOST_SCALE);
        BufferedImage image = new BufferedImage(layout.preferredSize().width, layout.preferredSize().height,
                BufferedImage.TYPE_INT_ARGB);

        Frame frame = new Frame() {
        };
        frame.setSoftLabel(Frame.SOFT_KEY_1, "Menu");
        frame.setSoftLabel(Frame.SOFT_KEY_2, "Back");

        Graphics2D graphics = image.createGraphics();
        try {
            renderer.paint(graphics, frame, null, config.width(), config.height(), HOST_SCALE);
        } finally {
            graphics.dispose();
        }

        writeImage(image, outputPath);
        System.out.println("iAppliType=" + config.iAppliType());
        System.out.println("launchType=" + launchType.id);
        System.out.println("iconDevice=" + config.statusBarIconDevice());
        System.out.println("topBarNonBackgroundPixels=" + countTopBarNonBackgroundPixels(image));
        System.out.println("topBarFingerprint=" + topBarFingerprint(image));
        System.out.println("output=" + outputPath.toAbsolutePath());
    }

    private static LaunchConfig.LaunchTypeOption requireLaunchType(String value) {
        LaunchConfig.LaunchTypeOption launchType = LaunchConfig.LaunchTypeOption.fromId(value);
        if (launchType == null) {
            throw new IllegalArgumentException("Unknown launch type: " + value + ". Expected normal or standby.");
        }
        return launchType;
    }

    private static void writeImage(BufferedImage image, Path outputPath) throws IOException {
        Path parent = outputPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        ImageIO.write(image, "png", outputPath.toFile());
    }

    private static int countTopBarNonBackgroundPixels(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < 18 && y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) != 0xFFD7D8DB) {
                    count++;
                }
            }
        }
        return count;
    }

    private static String topBarFingerprint(BufferedImage image) {
        long hash = 0xcbf29ce484222325L;
        for (int y = 0; y < 18 && y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                hash ^= image.getRGB(x, y);
                hash *= 0x100000001b3L;
            }
        }
        return Long.toUnsignedString(hash, 16);
    }
}
