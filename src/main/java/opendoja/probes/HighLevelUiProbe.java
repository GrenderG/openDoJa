package opendoja.probes;

import com.nttdocomo.lang.XString;
import com.nttdocomo.lang._XStringSupport;
import com.nttdocomo.ui.*;
import com.nttdocomo.util.Phone;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

/**
 * Headless verification for high-level DoJa UI component behavior.
 */
public final class HighLevelUiProbe {
    private HighLevelUiProbe() {
    }

    public static void main(String[] args) throws Exception {
        verifyPanelFocus();
        verifyTextBox();
        verifyListBox();
        verifyLoggedUnimplementedApis();
        System.out.println("high-level-ui-probe-ok");
    }

    private static void verifyPanelFocus() throws Exception {
        Panel panel = new Panel();
        if (panel.getFocusManager() == null) {
            throw new IllegalStateException("Panel did not expose a default focus manager");
        }
        try {
            panel.setFocusManager(null);
            throw new IllegalStateException("Panel accepted a null focus manager");
        } catch (NullPointerException expected) {
            // Expected path.
        }
        try {
            panel.setFocusManager(new FocusManager() {
            });
            throw new IllegalStateException("Panel accepted a non-system focus manager");
        } catch (IllegalArgumentException expected) {
            // Expected path.
        }

        Button button = new Button("ok");
        button.setSize(10, 10);
        panel.add(button);
        try {
            panel.add(button);
            throw new IllegalStateException("Panel accepted a duplicate component");
        } catch (UIException expected) {
            if (expected.getStatus() != UIException.ILLEGAL_STATE) {
                throw expected;
            }
        }

        Method focusedMethod = Component.class.getDeclaredMethod("focused");
        focusedMethod.setAccessible(true);
        button.requestFocus();
        if (!((Boolean) focusedMethod.invoke(button))) {
            throw new IllegalStateException("Button did not receive focus");
        }

        button.setVisible(false);
        if (((Boolean) focusedMethod.invoke(button))) {
            throw new IllegalStateException("Invisible button kept focus");
        }
    }

    private static void verifyTextBox() {
        TextBox textBox = new TextBox(new XString("abc"), 4, 1, TextBox.DISPLAY_ANY);
        if (textBox.getText() != null) {
            throw new IllegalStateException("XString text box returned a normal string");
        }
        if (textBox.getXText() == null || !"abc".equals(_XStringSupport.value(textBox.getXText(), "text"))) {
            throw new IllegalStateException("XString text box lost its XString text");
        }
        try {
            textBox.setEditable(true);
            throw new IllegalStateException("XString text box allowed editing");
        } catch (com.nttdocomo.lang.IllegalStateException expected) {
            // Expected path.
        }

        textBox.setText("plain");
        if (!"plain".equals(textBox.getText()) || textBox.getXText() != null) {
            throw new IllegalStateException("String text box mode did not replace XString mode");
        }

        textBox.setInputSize(5);
        try {
            textBox.setText("toolong");
            throw new IllegalStateException("Text box accepted text longer than the limit");
        } catch (UIException expected) {
            if (expected.getStatus() != UIException.ILLEGAL_STATE) {
                throw expected;
            }
        }
    }

    private static void verifyListBox() {
        ListBox choice = new ListBox(ListBox.CHOICE, 3);
        choice.append(new XString("x"));
        if (choice.getItem(0) != null) {
            throw new IllegalStateException("XString list item returned a normal string");
        }
        if (choice.getXItem(0) == null || !"x".equals(_XStringSupport.value(choice.getXItem(0), "item"))) {
            throw new IllegalStateException("List box lost its XString item");
        }
        choice.select(0);
        try {
            choice.deselect(0);
            throw new IllegalStateException("CHOICE list box allowed deselect(int)");
        } catch (UIException expected) {
            if (expected.getStatus() != UIException.ILLEGAL_STATE) {
                throw expected;
            }
        }
    }

    private static void verifyLoggedUnimplementedApis() {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            try {
                Phone.call("12345");
                throw new IllegalStateException("Phone.call(String) did not throw");
            } catch (com.nttdocomo.lang.UnsupportedOperationException expected) {
                // Expected path.
            }

            IApplication app = new IApplication() {
                @Override
                public void start() {
                }
            };
            try {
                app.launch(IApplication.LAUNCH_BROWSER, new String[]{"http://example.invalid"});
                throw new IllegalStateException("IApplication.launch(int, String[]) did not throw");
            } catch (com.nttdocomo.lang.UnsupportedOperationException expected) {
                // Expected path.
            }
        } finally {
            System.err.flush();
            System.setErr(originalErr);
        }

        String output = captured.toString(StandardCharsets.UTF_8);
        if (!output.contains("com.nttdocomo.util.Phone.call(java.lang.String)")) {
            throw new IllegalStateException("Missing Phone.call log output: " + output);
        }
        if (!output.contains("com.nttdocomo.ui.IApplication.launch(int, java.lang.String[])")) {
            throw new IllegalStateException("Missing IApplication.launch log output: " + output);
        }
    }
}
