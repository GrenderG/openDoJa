package opendoja.launcher;

import opendoja.audio.mld.MldSynth;
import opendoja.host.OpenDoJaIdentity;

import java.awt.Component;
import java.util.List;
import javax.swing.JOptionPane;

final class LauncherSettingsController {
    private final KeybindSettingsController keybindSettingsController = new KeybindSettingsController();

    void showKeybinds(Component parent) {
        keybindSettingsController.showDialog(parent);
    }

    List<MldSynth> availableSynths() {
        return List.of(MldSynth.values());
    }

    String promptTerminalId(Component parent, String currentValue) {
        String entered = promptValue(parent, "Terminal ID", currentValue,
                "Enter a 15-character uppercase alphanumeric terminal ID.");
        if (entered == null) {
            return null;
        }
        String normalized = entered.trim().toUpperCase();
        if (!OpenDoJaIdentity.isValidTerminalId(normalized)) {
            JOptionPane.showMessageDialog(
                    parent,
                    "Terminal ID must be 15 uppercase letters or digits.",
                    "Terminal ID",
                    JOptionPane.ERROR_MESSAGE);
            return null;
        }
        return normalized;
    }

    String promptUserId(Component parent, String currentValue) {
        String entered = promptValue(parent, "User ID", currentValue,
                "Enter a 12-character alphanumeric user ID.");
        if (entered == null) {
            return null;
        }
        String normalized = entered.trim();
        if (!OpenDoJaIdentity.isValidUserId(normalized)) {
            JOptionPane.showMessageDialog(
                    parent,
                    "User ID must be 12 letters or digits.",
                    "User ID",
                    JOptionPane.ERROR_MESSAGE);
            return null;
        }
        return normalized;
    }

    private String promptValue(Component parent, String title, String currentValue, String prompt) {
        String entered = (String) JOptionPane.showInputDialog(
                parent,
                prompt,
                title,
                JOptionPane.PLAIN_MESSAGE,
                null,
                null,
                currentValue);
        return entered == null ? null : entered;
    }
}
