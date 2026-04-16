package opendoja.host.system;

import opendoja.host.DoJaRuntime;
import opendoja.host.OpenDoJaLaunchArgs;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Host-side modal selection dialogs for native-style system APIs.
 */
public final class HostSelectionDialogs {
    private HostSelectionDialogs() {
    }

    public static Integer selectPhoneBookId(List<SelectionOption> options) {
        if (options == null || options.isEmpty()) {
            return null;
        }
        String automated = OpenDoJaLaunchArgs.get(OpenDoJaLaunchArgs.PHONEBOOK_SELECTION_INDEX, null);
        if (automated != null) {
            return automatedSelection(options, automated);
        }
        if (GraphicsEnvironment.isHeadless()) {
            return options.getFirst().id();
        }

        DoJaRuntime runtime = DoJaRuntime.current();
        if (runtime != null) {
            runtime.beginModalDialog();
        }
        try {
            Object selected = JOptionPane.showInputDialog(
                    dialogParent(runtime),
                    "Select contact",
                    "Phone Book",
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    options.toArray(),
                    options.getFirst());
            if (selected instanceof SelectionOption option) {
                return option.id();
            }
            return null;
        } finally {
            if (runtime != null) {
                runtime.endModalDialog();
            }
        }
    }

    private static Integer automatedSelection(List<SelectionOption> options, String raw) {
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return options.getFirst().id();
        }
        int index;
        try {
            index = Integer.parseInt(trimmed);
        } catch (NumberFormatException exception) {
            return null;
        }
        if (index < 0 || index >= options.size()) {
            return null;
        }
        return options.get(index).id();
    }

    private static Component dialogParent(DoJaRuntime runtime) {
        if (runtime != null) {
            Component parent = runtime.dialogParent();
            if (parent != null) {
                return parent;
            }
        }
        return KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
    }

    public record SelectionOption(int id, String label) {
        @Override
        public String toString() {
            return label;
        }
    }
}
