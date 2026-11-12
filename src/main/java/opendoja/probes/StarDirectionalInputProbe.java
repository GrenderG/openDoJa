package opendoja.probes;

import com.nttdocomo.ui.Display;
import opendoja.host.*;
import javax.swing.SwingUtilities;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Checks user-selected STAR control rotation through the real host popup and shared dispatcher. */
public final class StarDirectionalInputProbe {
    private static final int UP = Display.KEY_UP, RIGHT = Display.KEY_RIGHT,
            DOWN = Display.KEY_DOWN, LEFT = Display.KEY_LEFT;
    private static final int[] DIRECTIONS = {UP, RIGHT, DOWN, LEFT};
    private static final HostControlAction[] ACTIONS = {HostControlAction.MOVE_UP, HostControlAction.MOVE_RIGHT,
            HostControlAction.MOVE_DOWN, HostControlAction.MOVE_LEFT};
    private static final int[] SIDEWAYS_DIRECTIONS = {RIGHT, DOWN, LEFT, UP};
    private static ProbeCanvas canvas;

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        String property = OpenDoJaLaunchArgs.INPUT_STAR_DIRECTION_ROTATION;
        String previous = System.getProperty(property);
        try {
            System.setProperty(property, "none");
            verifyLayout("P01A", 854, 480, true, DIRECTIONS);
            verifyLayout("P-03C", 854, 480, true, DIRECTIONS);
            verifyLayout("F-01A", 854, 480, true, DIRECTIONS);
            verifyLayout("P01A", 240, 427, true, DIRECTIONS);
            verifyLayout("P01A", 480, 480, true, DIRECTIONS);
            verifyLayout("N-01A", 427, 240, true, DIRECTIONS);
            verifyLayout("appli", 854, 480, true, DIRECTIONS);
            verifyLayout("P01A", 854, 480, false, DIRECTIONS);
            verifyLayout("F-01A", 854, 480, false, DIRECTIONS);
            verifyMenu("F-01A", 854, 480);
            verifyMenu("P01A", 240, 427);
            // Old launch arguments no longer select title/model-based detection.
            System.setProperty(property, "auto");
            verifyLayout("P01A", 854, 480, true, DIRECTIONS);
            System.setProperty(property, "left");
            verifyLayout("N-01A", 427, 240, true, new int[]{LEFT, UP, RIGHT, DOWN});
            System.setProperty(property, "right");
            verifyLayout("N-01A", 427, 240, true, new int[]{RIGHT, DOWN, LEFT, UP});
            System.setProperty(property, "upside-down");
            verifyLayout("N-01A", 427, 240, true, new int[]{DOWN, LEFT, UP, RIGHT});
            // Even an explicit STAR override must leave regular DoJa input untouched.
            verifyLayout("P01A", 854, 480, false, DIRECTIONS);
            System.setProperty(property, "none");
            verifyHolds("P01A");
            verifyHolds("F-01A");
            verifyLayout("F-01A", 854, 480, true, DIRECTIONS);
        } finally {
            if (previous == null) System.clearProperty(property); else System.setProperty(property, previous);
            if (DoJaRuntime.current() != null) DoJaRuntime.current().shutdown();
        }
        System.out.println("star-directional-input-probe-ok");
    }

    private static void launch(String device, int width, int height, boolean star) {
        var builder = LaunchConfig.builder(ProbeApp.class).viewport(width, height).parameter("TargetDevice", device);
        if (star) builder.parameter("AppType", "FullApp");
        DesktopLauncher.launch(builder.build());
    }

    private static void verifyLayout(String device, int width, int height, boolean star, int[] expected) throws Exception {
        launch(device, width, height, star);
        try {
            check((rotationMenu() != null) == star, "Only STAR exposes control rotation");
            verifyDirections(expected);
            // Number keys retain phone positions; these are action buttons in many STAR titles.
            mapped(HostControlAction.DIGIT_2, true); expectEvent(0, Display.KEY_2);
            mapped(HostControlAction.DIGIT_2, false); expectEvent(1, Display.KEY_2);
            DoJaRuntime.current().dispatchSyntheticKey(RIGHT, 0); expectEvent(0, RIGHT);
            DoJaRuntime.current().dispatchSyntheticKey(RIGHT, 1); expectEvent(1, RIGHT);
        } finally { DoJaRuntime.current().shutdown(); }
    }

    private static void verifyMenu(String device, int width, int height) throws Exception {
        launch(device, width, height, true);
        try {
            String[] labels = {"90° right", "180°", "90° left", "Normal"};
            int[][] expected = {SIDEWAYS_DIRECTIONS, {DOWN, LEFT, UP, RIGHT}, {LEFT, UP, RIGHT, DOWN}, DIRECTIONS};
            for (int i = 0; i < labels.length; i++) {
                chooseRotation(labels[i]);
                JMenu menu = rotationMenu();
                int selected = 0;
                for (int j = 0; j < menu.getItemCount(); j++) {
                    if (menu.getItem(j).isSelected()) {
                        selected++;
                        check(labels[i].equals(menu.getItem(j).getText()), "Popup remembers current layout");
                    }
                }
                check(selected == 1, "Exactly one rotation is selected");
                verifyDirections(expected[i]);
            }
        } finally { DoJaRuntime.current().shutdown(); }
    }

    private static void verifyDirections(int[] expected) throws Exception {
        for (int i = 0; i < DIRECTIONS.length; i++) {
            mapped(ACTIONS[i], true); expectEvent(0, expected[i]);
            check(canvas.getKeypadState() == 1 << expected[i], "Polling matches selected direction");
            mapped(ACTIONS[i], false); expectEvent(1, expected[i]);
            check(canvas.getKeypadState() == 0, "Selected direction releases");
        }
    }

    private static void verifyHolds(String device) throws Exception {
        launch(device, 854, 480, true);
        try {
            chooseRotation("90° right");
            var runtime = DoJaRuntime.current();
            mapped(HostControlAction.MOVE_UP, true); expectEvent(0, RIGHT);
            mapped(HostControlAction.MOVE_RIGHT, true); expectEvent(0, DOWN);
            check(canvas.getKeypadState() == ((1 << RIGHT) | (1 << DOWN)), "Overlapping directions stay held");
            chooseRotation("Normal");
            mapped(HostControlAction.MOVE_UP, false); expectEvent(1, RIGHT);
            check(canvas.getKeypadState() == 1 << DOWN, "Layout change preserves the other held key");
            // Both host keys now select the same handset key. Releasing one must retain the other.
            mapped(HostControlAction.MOVE_DOWN, true);
            mapped(HostControlAction.MOVE_RIGHT, false);
            Thread.sleep(60);
            check(canvas.getKeypadState() == 1 << DOWN, "Overlapping mappings retain the shared handset hold");
            mapped(HostControlAction.MOVE_DOWN, false); expectEvent(1, DOWN);
            check(canvas.getKeypadState() == 0, "Every mapped key releases after layout changes");
            chooseRotation("90° right");
            mapped(HostControlAction.MOVE_RIGHT, true); expectEvent(0, DOWN);
            // A desktop auto-repeat release/press pair must keep the original mapped hold.
            mapped(HostControlAction.MOVE_RIGHT, false);
            mapped(HostControlAction.MOVE_RIGHT, true);
            Thread.sleep(60);
            check(canvas.events.isEmpty() && canvas.getKeypadState() == 1 << DOWN,
                    "Auto-repeat retains the rotated hold without duplicate edges");
            Object panel = field(runtime, "hostPanel");
            Object adapter = field(panel, "keyInputAdapter");
            SwingUtilities.invokeAndWait(() -> ((DesktopKeyInputAdapter) adapter).releaseAll());
            expectEvent(1, DOWN);
            check(canvas.getKeypadState() == 0, "Focus-loss releaseAll clears rotated keys");
        } finally { DoJaRuntime.current().shutdown(); }
    }

    private static JMenu rotationMenu() throws Exception {
        Object panel = field(DoJaRuntime.current(), "hostPanel");
        var build = panel.getClass().getDeclaredMethod("buildHostScalePopup");
        build.setAccessible(true);
        JMenu[] result = new JMenu[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                for (var component : ((JPopupMenu) build.invoke(panel)).getComponents()) {
                    if (component instanceof JMenu menu && "Control rotation".equals(menu.getText())) result[0] = menu;
                }
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        });
        return result[0];
    }

    private static void chooseRotation(String label) throws Exception {
        JMenu menu = rotationMenu();
        check(menu != null, "STAR control rotation menu exists");
        SwingUtilities.invokeAndWait(() -> {
            for (int i = 0; i < menu.getItemCount(); i++) {
                if (label.equals(menu.getItem(i).getText())) {
                    ((JRadioButtonMenuItem) menu.getItem(i)).doClick();
                    return;
                }
            }
            throw new AssertionError("Missing rotation choice: " + label);
        });
    }

    private static void mapped(HostControlAction action, boolean pressed) throws Exception {
        Object panel = field(DoJaRuntime.current(), "hostPanel");
        var dispatch = panel.getClass().getDeclaredMethod("dispatchMappedAction", HostControlAction.class, boolean.class);
        dispatch.setAccessible(true);
        SwingUtilities.invokeAndWait(() -> {
            try { dispatch.invoke(panel, action, pressed); } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        });
    }
    private static Object field(Object value, String name) throws Exception {
        var field = value.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(value);
    }
    private static void expectEvent(int type, int key) throws Exception {
        String actual = canvas.events.poll(2, TimeUnit.SECONDS);
        check((type + ":" + key).equals(actual), "Expected " + type + ":" + key + ", got " + actual);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    public static final class ProbeApp extends com.nttdocomo.ui.IApplication {
        @Override public void start() { canvas = new ProbeCanvas(); Display.setCurrent(canvas); }
    }
    private static final class ProbeCanvas extends com.nttdocomo.ui.Canvas {
        final LinkedBlockingQueue<String> events = new LinkedBlockingQueue<>();
        @Override public void paint(com.nttdocomo.ui.Graphics graphics) { }
        @Override public void processEvent(int type, int param) {
            if (type == 0 || type == 1) events.add(type + ":" + param);
        }
    }
}
