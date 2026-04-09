package opendoja.launcher;

import opendoja.audio.mld.MLDSynth;
import opendoja.host.OpenDoJaIdentity;
import opendoja.host.OpenDoJaLaunchArgs;

import java.awt.Component;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import javax.swing.JOptionPane;

final class LauncherSettingsController {
    private final KeybindSettingsController keybindSettingsController = new KeybindSettingsController();

    void showKeybinds(Component parent) {
        keybindSettingsController.showDialog(parent);
    }

    List<MLDSynth> availableSynths() {
        return List.of(MLDSynth.values());
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

    String promptHttpOverrideDomain(Component parent, String currentValue) {
        String entered = promptValue(parent, "HTTP Host Override", currentValue,
                "Enter the hostname to use for all outbound HTTP requests. Leave blank to disable.");
        if (entered == null) {
            return null;
        }
        String normalized = normalizeHttpOverrideDomain(entered);
        if (normalized == null) {
            JOptionPane.showMessageDialog(
                    parent,
                    "Enter only a hostname such as example.com or localhost. Do not include a scheme, path, query, or port.",
                    "HTTP Host Override",
                    JOptionPane.ERROR_MESSAGE);
            return null;
        }
        return normalized;
    }

    String promptMicroeditionPlatformOverride(Component parent, String currentValue) {
        String entered = promptValue(parent, "Phone Model", currentValue,
                "Enter the value to return for microedition.platform. Leave blank to use the JAM/default platform.");
        if (entered == null) {
            return null;
        }
        return OpenDoJaLaunchArgs.normalizeMicroeditionPlatformOverride(entered);
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

    private static String normalizeHttpOverrideDomain(String candidate) {
        String normalized = candidate == null ? "" : candidate.trim();
        if (normalized.isEmpty()) {
            return "";
        }
        if (normalized.contains("://")
                || normalized.contains("/")
                || normalized.contains("?")
                || normalized.contains("#")
                || normalized.contains(":")) {
            return null;
        }
        try {
            URI uri = new URI("http://" + normalized);
            String host = uri.getHost();
            if (host == null || !normalized.equalsIgnoreCase(host)) {
                return null;
            }
            return host.toLowerCase(Locale.ROOT);
        } catch (URISyntaxException exception) {
            return null;
        }
    }
}
