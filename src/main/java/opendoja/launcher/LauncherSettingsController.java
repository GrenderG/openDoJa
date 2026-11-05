package opendoja.launcher;

import opendoja.audio.mld.MLDSynth;
import opendoja.host.HostKeybindConfiguration;
import opendoja.host.OpenDoJaIdentity;
import opendoja.host.OpenDoJaLaunchArgs;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;

final class LauncherSettingsController {
    private final KeybindSettingsController keybindSettingsController = new KeybindSettingsController();

    HostKeybindConfiguration editKeybinds(Component parent, HostKeybindConfiguration currentConfiguration) {
        return keybindSettingsController.editKeybinds(parent, currentConfiguration);
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

    String promptUimId(Component parent, String currentValue) {
        String entered = promptValue(parent, "UIM ID", currentValue,
                "Enter a 20-character uppercase alphanumeric UIM ID.");
        if (entered == null) {
            return null;
        }
        String normalized = entered.trim().toUpperCase();
        if (!OpenDoJaIdentity.isValidUimId(normalized)) {
            JOptionPane.showMessageDialog(parent, "UIM ID must be 20 uppercase letters or digits.",
                    "UIM ID", JOptionPane.ERROR_MESSAGE);
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
        String entered = promptValue(parent, "Phone Model Override", currentValue,
                "Enter the value to return for microedition.platform. Leave blank to use the JAM/default platform.");
        if (entered == null) {
            return null;
        }
        return OpenDoJaLaunchArgs.normalizeMicroeditionPlatformOverride(entered);
    }

    String promptFileEncodingOverride(Component parent, String currentValue) {
        String entered = promptValue(parent, "Encoding Override", currentValue,
                "Enter the value to pass as -Dfile.encoding. Leave blank to use the automatic/default encoding.");
        if (entered == null) {
            return null;
        }
        return entered.trim();
    }

    String promptSystemFontOverride(Component parent, String currentValue) {
        List<String> installedFonts = installedFontFamilies();
        String pendingValue = currentValue;
        String resolvedFamily = null;
        while (resolvedFamily == null) {
            JComboBox<String> fontPicker = new JComboBox<>(installedFonts.toArray(new String[0]));
            fontPicker.setEditable(true);
            fontPicker.setSelectedItem(pendingValue == null ? "" : pendingValue);
            fontPicker.setPrototypeDisplayValue("Noto Sans Mono CJK JP");

            JPanel panel = new JPanel(new BorderLayout(0, 8));
            panel.add(new JLabel("<html>Select the desktop font family to force when <b>Font Type</b> is set to <b>System</b>."
                    + "<br>Leave blank to use automatic font resolution.</html>"), BorderLayout.NORTH);
            panel.add(fontPicker, BorderLayout.CENTER);

            int option = JOptionPane.showConfirmDialog(
                    parent,
                    panel,
                    "System Font Override",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE);
            if (option != JOptionPane.OK_OPTION) {
                return null;
            }

            Object selected = fontPicker.getEditor().getItem();
            String normalized = OpenDoJaLaunchArgs.normalizeSystemFontOverride(selected == null ? "" : selected.toString());
            if (normalized.isEmpty()) {
                return "";
            }
            resolvedFamily = resolveInstalledFontFamily(normalized, installedFonts);
            if (resolvedFamily == null) {
                JOptionPane.showMessageDialog(
                        parent,
                        "Choose one of the installed font families from the list, or leave the field blank to disable the override.",
                        "System Font Override",
                        JOptionPane.ERROR_MESSAGE);
                pendingValue = normalized;
            }
        }
        return resolvedFamily;
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

    private static List<String> installedFontFamilies() {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        try {
            String[] families = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
            Arrays.sort(families, String.CASE_INSENSITIVE_ORDER);
            names.addAll(Arrays.asList(families));
        } catch (HeadlessException | AWTError ignored) {
            // The launcher normally runs with a desktop font environment. If it does not, keep the
            // dialog functional by returning an empty list and relying on blank disable behavior.
        }
        return List.copyOf(new ArrayList<>(names));
    }

    private static String resolveInstalledFontFamily(String candidate, List<String> installedFonts) {
        if (installedFonts.isEmpty()) {
            return candidate;
        }
        for (String family : installedFonts) {
            if (family.equalsIgnoreCase(candidate)) {
                return family;
            }
        }
        return null;
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
