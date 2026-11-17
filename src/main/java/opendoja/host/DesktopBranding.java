package opendoja.host;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Taskbar;
import java.awt.Window;
import java.awt.image.BaseMultiResolutionImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Shared desktop artwork for the launcher and each game process. */
public final class DesktopBranding {
    private static final int[] ICON_SIZES = {16, 24, 32, 48, 64, 128, 256, 512, 1024};
    private static final List<Image> ICONS = loadIcons();
    private static boolean taskbarConfigured;

    private DesktopBranding() {
    }

    public static void applyWindowIcon(Window window) {
        // Windows and Linux use these for both window decorations and taskbar entries.
        window.setIconImages(ICONS);
        configureTaskbar();
    }

    /** Returns a logical-size icon with larger variants for HiDPI displays. */
    public static ImageIcon icon(int size) {
        for (int i = 0; i < ICON_SIZES.length; i++) {
            if (ICON_SIZES[i] == size) {
                Image[] variants = ICONS.subList(i, ICONS.size()).toArray(Image[]::new);
                return new ImageIcon(new BaseMultiResolutionImage(variants), "openDoJa logo");
            }
        }
        throw new IllegalArgumentException("Unsupported logo size: " + size);
    }

    private static synchronized void configureTaskbar() {
        if (taskbarConfigured || GraphicsEnvironment.isHeadless()) {
            return;
        }
        taskbarConfigured = true;
        try {
            if (Taskbar.isTaskbarSupported()) {
                Taskbar taskbar = Taskbar.getTaskbar();
                if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                    // macOS uses the application Dock icon rather than the frame icons.
                    taskbar.setIconImage(icon(256).getImage());
                }
            }
        } catch (UnsupportedOperationException | SecurityException exception) {
            OpenDoJaLog.debug(DesktopBranding.class, "Taskbar icon unavailable: " + exception);
        }
    }

    private static List<Image> loadIcons() {
        List<Image> images = new ArrayList<>(ICON_SIZES.length);
        for (int size : ICON_SIZES) {
            String resource = "/opendoja/images/branding/logo-" + size + ".png";
            // The context classloader can belong to a game; use our own packaged assets.
            try (InputStream stream = DesktopBranding.class.getResourceAsStream(resource)) {
                if (stream == null) {
                    throw new IOException("Missing logo resource: " + resource);
                }
                Image image = ImageIO.read(stream);
                if (image == null) {
                    throw new IOException("Invalid logo resource: " + resource);
                }
                images.add(image);
            } catch (IOException exception) {
                throw new IllegalStateException("Could not load desktop logo", exception);
            }
        }
        return List.copyOf(images);
    }
}
