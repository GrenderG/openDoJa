package opendoja.probes;

import com.nttdocomo.lang.XString;
import com.nttdocomo.lang._XStringSupport;
import com.nttdocomo.ui.Canvas;
import com.nttdocomo.ui.Display;
import com.nttdocomo.ui.Font;
import com.nttdocomo.ui.Frame;
import com.nttdocomo.ui.Graphics;
import com.nttdocomo.ui.IApplication;
import opendoja.host.DesktopSurface;
import opendoja.host.DoJaRuntime;
import opendoja.host.LaunchConfig;
import opendoja.host.OpenDoJaLaunchArgs;

import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Verifies Chocomate's profile-memo phone-book fields render XString contents,
 * not XObject identity strings.
 *
 * <p>Run with `resources/sample_games/Chocomate/chocomate.jar` on the classpath.</p>
 */
public final class ChocomateProfileMemoXStringProbe {
    private ChocomateProfileMemoXStringProbe() {
    }

    public static void main(String[] args) throws Exception {
        String previousSelection = System.getProperty(OpenDoJaLaunchArgs.PHONEBOOK_SELECTION_INDEX);
        System.setProperty(OpenDoJaLaunchArgs.PHONEBOOK_SELECTION_INDEX, "2");
        DoJaRuntime runtime = DoJaRuntime.bootstrap(LaunchConfig.builder(ProbeApp.class)
                .parameter("AccessUserInfo", "true")
                .build());
        try {
            Object chocomateCanvas = newChocomateCanvas();
            int selectedId = invokePhoneBookIdSelect(chocomateCanvas);
            XString name = invokePhoneBookName(chocomateCanvas, selectedId);
            XString mail = invokePhoneBookMail(chocomateCanvas, selectedId);

            assertXStringText(name, "Contact 3", "profile memo name");
            assertXStringText(mail, "contact3@example.com", "profile memo email address");
            assertFontMetricsUseXStringText(name, "Contact 3", "profile memo name");
            assertFontMetricsUseXStringText(mail, "contact3@example.com", "profile memo email address");
            assertRenderedLikeString(name, "Contact 3", "profile memo name");
            assertRenderedLikeString(mail, "contact3@example.com", "profile memo email address");

            System.out.println("chocomate-profile-memo-xstring-probe-ok");
        } finally {
            runtime.shutdown();
            if (previousSelection == null) {
                System.clearProperty(OpenDoJaLaunchArgs.PHONEBOOK_SELECTION_INDEX);
            } else {
                System.setProperty(OpenDoJaLaunchArgs.PHONEBOOK_SELECTION_INDEX, previousSelection);
            }
        }
    }

    private static Object newChocomateCanvas() throws Exception {
        Class<?> cmClass = Class.forName("chocomate.Cm");
        Constructor<?> cmConstructor = cmClass.getDeclaredConstructor();
        cmConstructor.setAccessible(true);
        Object cm = cmConstructor.newInstance();

        Class<?> dmClass = Class.forName("chocomate.Dm");
        Constructor<?> dmConstructor = dmClass.getDeclaredConstructor(cmClass, int.class);
        dmConstructor.setAccessible(true);
        return dmConstructor.newInstance(cm, 0);
    }

    private static int invokePhoneBookIdSelect(Object canvas) throws Exception {
        Method f2 = canvas.getClass().getDeclaredMethod("f2");
        f2.setAccessible(true);
        return (Integer) f2.invoke(canvas);
    }

    private static XString invokePhoneBookName(Object canvas, int id) throws Exception {
        Method f3 = canvas.getClass().getDeclaredMethod("f3", int.class);
        f3.setAccessible(true);
        return (XString) f3.invoke(canvas, id);
    }

    private static XString invokePhoneBookMail(Object canvas, int id) throws Exception {
        Method f4 = canvas.getClass().getDeclaredMethod("f4", int.class);
        f4.setAccessible(true);
        return (XString) f4.invoke(canvas, id);
    }

    private static void assertXStringText(XString actual, String expected, String label) {
        if (actual == null || !expected.equals(_XStringSupport.value(actual, label))) {
            throw new IllegalStateException(label + " did not expose expected XString content");
        }
    }

    private static void assertFontMetricsUseXStringText(XString actual, String expected, String label) {
        Font font = Font.getDefaultFont();
        int xStringWidth = font.stringWidth(actual);
        int expectedWidth = font.stringWidth(expected);
        if (xStringWidth != expectedWidth) {
            throw new IllegalStateException(label + " XString metrics used object identity text");
        }
    }

    private static void assertRenderedLikeString(XString actual, String expected, String label) throws Exception {
        BufferedImage xStringImage = render(actual);
        BufferedImage stringImage = render(expected);
        if (!samePixels(xStringImage, stringImage)) {
            throw new IllegalStateException(label + " XString rendering differed from ordinary string rendering");
        }
    }

    private static BufferedImage render(Object text) throws Exception {
        ProbeCanvas canvas = new ProbeCanvas();
        Display.setCurrent((Frame) canvas);
        canvas.setBackground(Graphics.getColorOfName(Graphics.WHITE));
        Graphics graphics = canvas.getGraphics();
        graphics.setFont(Font.getDefaultFont());
        graphics.setColor(Graphics.getColorOfName(Graphics.WHITE));
        graphics.fillRect(0, 0, Display.getWidth(), Display.getHeight());
        graphics.setColor(Graphics.getColorOfName(Graphics.BLACK));
        int baseline = Font.getDefaultFont().getAscent() + 4;
        if (text instanceof XString xString) {
            graphics.drawString(xString, 4, baseline);
        } else {
            graphics.drawString((String) text, 4, baseline);
        }
        graphics.dispose();
        return surface(canvas).copyForPresentation();
    }

    private static DesktopSurface surface(Canvas canvas) throws Exception {
        Method surface = Canvas.class.getDeclaredMethod("surface");
        surface.setAccessible(true);
        return (DesktopSurface) surface.invoke(canvas);
    }

    private static boolean samePixels(BufferedImage first, BufferedImage second) {
        if (first.getWidth() != second.getWidth() || first.getHeight() != second.getHeight()) {
            return false;
        }
        int width = first.getWidth();
        int[] firstRow = new int[width];
        int[] secondRow = new int[width];
        for (int y = 0; y < first.getHeight(); y++) {
            first.getRGB(0, y, width, 1, firstRow, 0, width);
            second.getRGB(0, y, width, 1, secondRow, 0, width);
            if (!Arrays.equals(firstRow, secondRow)) {
                return false;
            }
        }
        return true;
    }

    public static final class ProbeApp extends IApplication {
        @Override
        public void start() {
        }
    }

    private static final class ProbeCanvas extends Canvas {
        @Override
        public void paint(Graphics g) {
        }
    }
}
