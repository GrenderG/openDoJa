package opendoja.probes;

import com.docomostar.StarApplication;
import com.docomostar.device.StorageDevice;
import com.docomostar.fs.*;
import com.docomostar.opt.device.MediaPlayer;
import com.docomostar.ui.*;
import opendoja.host.*;

/** Exercises the STAR adapters used by Kingdom Hearts, including STAR's polygon offset contract. */
public final class StarKingdomApisProbe {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        DesktopLauncher.launch(LaunchConfig.builder(ProbeApp.class).parameter("AppType", "FullApp")
                .parameter("AppName", "star-kingdom-probe-" + java.util.UUID.randomUUID())
                .parameter("UseStorage", "ext0").build());
        try {
            verifyPolygons();
            verifyEncoder();
            verifyPlayer();
        } finally { DoJaRuntime.current().shutdown(); }
        System.out.println("star-kingdom-apis-probe-ok");
    }

    private static void verifyPolygons() {
        Graphics graphics = Image.createImage(32, 32).getGraphics();
        int[] xs = {29, 29, 3, 14, 3, 29}, ys = {29, 29, 3, 3, 14, 29};
        graphics.setColor(0xff000000); graphics.fillRect(0, 0, 32, 32);
        graphics.setColor(0xffff0000);
        graphics.fillPolygon(xs, ys, 2, 3);
        check(graphics.getRGBPixel(5, 5) == 0xff0000, "Selected triangle is filled: " + Integer.toHexString(graphics.getRGBPixel(5, 5)));
        check(graphics.getRGBPixel(25, 25) == 0, "Prefix/suffix vertices are excluded");
        graphics.setColor(0xff00ff00);
        graphics.drawPolyline(new int[]{0, 3, 14, 14, 0}, new int[]{0, 20, 20, 26, 0}, 1, 3);
        check(graphics.getRGBPixel(8, 20) == 0x00ff00 && graphics.getRGBPixel(14, 23) == 0x00ff00,
                "Polyline uses offset then count, rather than DoJa count then mode");
        expect(IndexOutOfBoundsException.class, () -> graphics.fillPolygon(xs, ys, 5, 3));
        expect(NullPointerException.class, () -> graphics.drawPolyline(null, ys, 0, 2));
    }

    private static void verifyEncoder() throws Exception {
        Image image = Image.createImage(8, 6);
        Graphics graphics = image.getGraphics();
        graphics.setColor(0xffff0000);
        graphics.fillRect(0, 0, 8, 6);
        ImageEncoder encoder = ImageEncoder.getEncoder("JPEG");
        encoder.setAttribute(ImageEncoder.QUALITY, ImageEncoder.ATTR_QUALITY_HIGH);
        check(encoder.isAvailable(ImageEncoder.QUALITY), "Native JPEG quality capability");
        var encoded = encoder.encode(image, 1, 1, 4, 3);
        byte[] bytes = encoded.getInputStream().readAllBytes();
        check(bytes.length == encoded.size() && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8,
                "Encoder returns real JPEG bytes");
        var decoded = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
        check(decoded.getWidth() == 4 && decoded.getHeight() == 3, "JPEG crop dimensions");
        check(encoded.getInputStream() != encoded.getInputStream(), "Independent encoded streams");
        check(encoded.getImage() == encoded.getImage() && encoded.getImage().getWidth() == 4,
                "Stable media wrapper over native encoded image");
        Canvas current = (Canvas) Display.getCurrent();
        check(encoder.encode(current, 0, 0, 2, 2).size() > 0, "Current canvas uses shared JPEG encoder");
        expect(UIException.class, () -> encoder.encode(new ProbeCanvas(), 0, 0, 2, 2));
        expect(UIException.class, () -> ImageEncoder.getEncoder("unsupported"));
    }

    private static void verifyPlayer() throws Exception {
        var folder = StorageDevice.getInstance("/ext0").getFolder(StarStorageService.getAccessToken(2, 8));
        File file = folder.createFile("player.bin");
        try {
            MediaPlayer player = MediaPlayer.getMediaPlayer();
            check(player == MediaPlayer.getMediaPlayer(), "Stable player service");
            int hostStatus = com.nttdocomo.opt.device.MediaPlayer.getMediaPlayer().play(file.hostFile());
            check(player.play(file) == hostStatus, "Player uses the existing host result");
            expect(IllegalArgumentException.class, () -> player.play(file, -1));
            expect(NullPointerException.class, () -> player.play(null));
            file.delete();
            try { player.play(file); throw new AssertionError("Missing file accepted"); }
            catch (FileNotAccessibleException expected) {
                check(expected.getStatus() == FileNotAccessibleException.NOT_FOUND, "Storage failure translated to STAR");
            }
        } finally {
            try { file.delete(); } catch (FileNotAccessibleException ignored) { }
        }
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void expect(Class<? extends Throwable> type, Action action) {
        try { action.run(); } catch (Throwable failure) {
            if (type.isInstance(failure)) return;
            throw new AssertionError("Expected " + type.getName(), failure);
        }
        throw new AssertionError("Expected " + type.getName());
    }
    @FunctionalInterface private interface Action { void run() throws Exception; }
    public static final class ProbeApp extends StarApplication {
        @Override public void started(int reason) { Display.setCurrent(new ProbeCanvas()); }
    }
    private static final class ProbeCanvas extends Canvas { @Override public void paint(Graphics graphics) { } }
}
