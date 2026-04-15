package opendoja.launcher;

import opendoja.audio.mld.MLDSynth;
import opendoja.host.DoJaRuntime;
import opendoja.host.LaunchConfig;
import opendoja.host.OpenDoJaLog;
import opendoja.host.OpenGlesRendererMode;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

final class OpenDoJaLauncherFrame extends JFrame {
    private final JamLaunchService jamLaunchService;
    private final LauncherSettingsController settingsController;
    private final LauncherUpdateService updateService;
    private final Action loadJamAction;
    private final Action checkUpdatesAction;
    private final JMenu recentMenu;
    private volatile boolean updateCheckInProgress;

    OpenDoJaLauncherFrame(JamLaunchService jamLaunchService, LauncherSettingsController settingsController) {
        this(jamLaunchService, settingsController, new LauncherUpdateService());
    }

    OpenDoJaLauncherFrame(JamLaunchService jamLaunchService,
                          LauncherSettingsController settingsController,
                          LauncherUpdateService updateService) {
        super(OpenDoJaLauncher.APP_NAME);
        this.jamLaunchService = jamLaunchService;
        this.settingsController = settingsController;
        this.updateService = updateService;
        this.loadJamAction = new AbstractAction("Load JAM") {
            @Override
            public void actionPerformed(ActionEvent event) {
                chooseAndLaunchJam();
            }
        };
        this.checkUpdatesAction = new AbstractAction("Check Updates") {
            @Override
            public void actionPerformed(ActionEvent event) {
                beginUpdateCheck(true);
            }
        };
        this.recentMenu = new JMenu("Load Recents...");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setJMenuBar(buildMenuBar());
        setContentPane(buildContent());
        setMinimumSize(new Dimension(360, 360));
        setSize(420, 420);
        setLocationByPlatform(true);
        rebuildRecentMenu();
    }

    private JMenuBar buildMenuBar() {
        JMenu fileMenu = new JMenu("File");
        fileMenu.add(new JMenuItem(loadJamAction));
        fileMenu.add(recentMenu);
        fileMenu.add(new JMenuItem(new AbstractAction("Open SD Card Folder") {
            @Override
            public void actionPerformed(ActionEvent event) {
                openSdCardFolder();
            }
        }));
        fileMenu.addSeparator();
        fileMenu.add(new JMenuItem(new AbstractAction("Exit") {
            @Override
            public void actionPerformed(ActionEvent event) {
                dispose();
            }
        }));

        JMenu settingsMenu = new JMenu("Settings");
        settingsMenu.add(buildHostScaleMenu());
        settingsMenu.add(buildSynthMenu());
        settingsMenu.add(buildFontTypeMenu());
        settingsMenu.add(buildExperimentalMenu());
        settingsMenu.addSeparator();
        settingsMenu.add(new JMenuItem(new AbstractAction("Set Terminal ID...") {
            @Override
            public void actionPerformed(ActionEvent event) {
                updateTerminalId();
            }
        }));
        settingsMenu.add(new JMenuItem(new AbstractAction("Set User ID...") {
            @Override
            public void actionPerformed(ActionEvent event) {
                updateUserId();
            }
        }));
        settingsMenu.addSeparator();
        settingsMenu.add(new JMenuItem(new AbstractAction("HTTP Host Override...") {
            @Override
            public void actionPerformed(ActionEvent event) {
                updateHttpOverrideDomain();
            }
        }));
        settingsMenu.add(new JMenuItem(new AbstractAction("Encoding Override...") {
            @Override
            public void actionPerformed(ActionEvent event) {
                updateFileEncodingOverride();
            }
        }));
        settingsMenu.add(new JMenuItem(new AbstractAction("Phone Model Override...") {
            @Override
            public void actionPerformed(ActionEvent event) {
                updateMicroeditionPlatformOverride();
            }
        }));

        JMenu helpMenu = new JMenu("Help");
        helpMenu.add(new JMenuItem(new AbstractAction("Keybinds") {
            @Override
            public void actionPerformed(ActionEvent event) {
                settingsController.showKeybinds(OpenDoJaLauncherFrame.this);
            }
        }));
        helpMenu.add(new JMenuItem(checkUpdatesAction));
        helpMenu.addSeparator();
        helpMenu.add(new JMenuItem(new AbstractAction("About") {
            @Override
            public void actionPerformed(ActionEvent event) {
                showAboutDialog();
            }
        }));

        JMenuBar menuBar = new JMenuBar();
        menuBar.add(fileMenu);
        menuBar.add(settingsMenu);
        menuBar.add(helpMenu);
        return menuBar;
    }

    private JPanel buildContent() {
        JButton loadButton = new JButton(loadJamAction);
        loadButton.setFont(loadButton.getFont().deriveFont(Font.BOLD, 20f));
        loadButton.setPreferredSize(new Dimension(180, 72));
        JLabel dragDropHint = new JLabel("Or, drag and drop a .jam file or game folder here.", SwingConstants.CENTER);
        dragDropHint.setFont(dragDropHint.getFont().deriveFont(13f));

        JPanel contentPanel = new JPanel(new BorderLayout(0, 10));
        contentPanel.add(loadButton, BorderLayout.CENTER);
        contentPanel.add(dragDropHint, BorderLayout.SOUTH);

        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.add(contentPanel);

        JPanel root = new JPanel(new BorderLayout());
        root.add(centerPanel, BorderLayout.CENTER);
        installJamDropHandler(root, loadButton);
        return root;
    }

    void handleInitialStartup() {
        boolean notificationsEnabled = jamLaunchService.updateNotificationsEnabled();
        if (jamLaunchService.shouldPromptForUpdateNotifications()) {
            int choice = JOptionPane.showConfirmDialog(
                    this,
                    "Enable notifications when a new stable release is available?",
                    OpenDoJaLauncher.APP_NAME,
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);
            notificationsEnabled = choice == JOptionPane.YES_OPTION;
            jamLaunchService.saveUpdateNotificationsPreference(notificationsEnabled);
        }
        if (notificationsEnabled) {
            beginUpdateCheck(false);
        }
    }

    private void chooseAndLaunchJam() {
        Path jamPath = jamLaunchService.chooseJamFile(this);
        if (jamPath == null) {
            return;
        }
        launchJam(jamPath);
    }

    private void openSdCardFolder() {
        try {
            FolderOpenSupport.openDirectory(JamLaunchService.ensureSdCardFolder());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(
                    this,
                    e.getMessage(),
                    OpenDoJaLauncher.APP_NAME,
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void launchJam(Path jamPath) {
        setLaunchControlsEnabled(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        Thread.ofVirtual().start(() -> {
            try {
                GameLaunchResult result = jamLaunchService.launchInSeparateProcess(jamPath);
                SwingUtilities.invokeLater(() -> {
                    setLaunchControlsEnabled(true);
                    setCursor(Cursor.getDefaultCursor());
                    rebuildRecentMenu();
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    setLaunchControlsEnabled(true);
                    setCursor(Cursor.getDefaultCursor());
                    JOptionPane.showMessageDialog(
                            this,
                            e.getMessage(),
                            OpenDoJaLauncher.APP_NAME,
                            JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }

    private void installJamDropHandler(JComponent... components) {
        TransferHandler handler = new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                if (!loadJamAction.isEnabled()) {
                    return false;
                }
                boolean supported = support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                        || support.isDataFlavorSupported(DataFlavor.stringFlavor);
                if (supported && support.isDrop()) {
                    support.setDropAction(COPY);
                }
                return supported;
            }

            @Override
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) {
                    return false;
                }
                try {
                    Path jamPath = JamLaunchService.droppedJamPath(extractDroppedPaths(support.getTransferable()));
                    if (jamPath == null) {
                        return true;
                    }
                    launchJam(jamPath);
                    return true;
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(
                            OpenDoJaLauncherFrame.this,
                            e.getMessage(),
                            OpenDoJaLauncher.APP_NAME,
                            JOptionPane.ERROR_MESSAGE);
                    return false;
                }
            }
        };
        setTransferHandler(handler);
        getRootPane().setTransferHandler(handler);
        for (JComponent component : components) {
            component.setTransferHandler(handler);
        }
    }

    private List<Path> extractDroppedPaths(Transferable transferable) throws IOException, UnsupportedFlavorException {
        if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            @SuppressWarnings("unchecked")
            List<File> files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
            return files.stream().map(File::toPath).toList();
        }
        if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            return parseUriList((String) transferable.getTransferData(DataFlavor.stringFlavor));
        }
        throw new IOException("Dropped content is not a file.");
    }

    private List<Path> parseUriList(String raw) throws IOException {
        List<Path> paths = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return paths;
        }
        for (String line : raw.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            try {
                URI uri = new URI(trimmed);
                if (!"file".equalsIgnoreCase(uri.getScheme())) {
                    continue;
                }
                paths.add(Path.of(uri));
            } catch (URISyntaxException | IllegalArgumentException e) {
                throw new IOException("Dropped file path is invalid.", e);
            }
        }
        return paths;
    }

    private void setLaunchControlsEnabled(boolean enabled) {
        loadJamAction.setEnabled(enabled);
        recentMenu.setEnabled(enabled && recentMenu.getItemCount() > 0);
    }

    private void rebuildRecentMenu() {
        recentMenu.removeAll();
        List<Path> recents = jamLaunchService.recentJamPaths();
        for (Path recent : recents) {
            recentMenu.add(new JMenuItem(new AbstractAction(recent.getFileName().toString()) {
                @Override
                public void actionPerformed(ActionEvent event) {
                    launchRecentJam(recent);
                }
            }));
        }
        recentMenu.setEnabled(!recents.isEmpty() && loadJamAction.isEnabled());
    }

    private void launchRecentJam(Path selected) {
        if (!Files.isRegularFile(selected)) {
            jamLaunchService.removeRecentJam(selected);
            rebuildRecentMenu();
            JOptionPane.showMessageDialog(
                    this,
                    selected + "\n\nThis recent JAM file no longer exists.",
                    OpenDoJaLauncher.APP_NAME,
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        launchJam(selected);
    }

    private JMenu buildHostScaleMenu() {
        LauncherSettings settings = jamLaunchService.loadSettings();
        JMenu hostScaleMenu = new JMenu("Host Scale");
        ButtonGroup group = new ButtonGroup();
        for (int scale = DoJaRuntime.MIN_HOST_SCALE; scale <= DoJaRuntime.MAX_HOST_SCALE; scale++) {
            final int selectedScale = scale;
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(new AbstractAction((scale * 100) + "%") {
                @Override
                public void actionPerformed(ActionEvent event) {
                    saveSettings(current -> current.withHostScale(selectedScale));
                }
            });
            item.setSelected(settings.hostScale() == scale);
            group.add(item);
            hostScaleMenu.add(item);
        }
        return hostScaleMenu;
    }

    private JMenu buildFontTypeMenu() {
        LauncherSettings settings = jamLaunchService.loadSettings();
        JMenu fontTypeMenu = new JMenu("Font Type");
        ButtonGroup group = new ButtonGroup();
        for (LaunchConfig.FontType fontType : LaunchConfig.FontType.values()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(new AbstractAction(fontType.label) {
                @Override
                public void actionPerformed(ActionEvent event) {
                    saveSettings(current -> current.withFontType(fontType.id));
                }
            });
            item.setSelected(settings.fontType().equals(fontType.id));
            group.add(item);
            fontTypeMenu.add(item);
        }
        return fontTypeMenu;
    }

    private JMenu buildSynthMenu() {
        LauncherSettings settings = jamLaunchService.loadSettings();
        JMenu synthMenu = new JMenu("Synth");
        ButtonGroup group = new ButtonGroup();
        for (MLDSynth synth : settingsController.availableSynths()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(new AbstractAction(formatSynthLabel(synth)) {
                @Override
                public void actionPerformed(ActionEvent event) {
                    saveSettings(current -> current.withSynthId(synth.id));
                }
            });
            item.setSelected(settings.synthId().equals(synth.id));
            group.add(item);
            synthMenu.add(item);
        }
        return synthMenu;
    }

    private JMenu buildExperimentalMenu() {
        LauncherSettings settings = jamLaunchService.loadSettings();
        JMenu experimentalMenu = new JMenu("Experimental");

        JMenu oglRendererMenu = new JMenu("OpenGLES Renderer");
        ButtonGroup oglRendererGroup = new ButtonGroup();
        for (OpenGlesRendererMode mode : OpenGlesRendererMode.values()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(mode.label());
            item.setSelected(settings.openGlesRendererMode() == mode);
            item.addActionListener(event -> saveSettings(current -> current.withOpenGlesRendererMode(mode)));
            oglRendererGroup.add(item);
            oglRendererMenu.add(item);
        }
        experimentalMenu.add(oglRendererMenu);

        JMenu oglSupersamplingMenu = new JMenu("OpenGLES Supersampling");
        ButtonGroup oglSupersamplingGroup = new ButtonGroup();
        int[] supersampleScales = {1, 2, 3, 4, 5};
        for (int scale : supersampleScales) {
            String label = scale == 1 ? "1x (Native)" : scale + "x";
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(label);
            item.setSelected(settings.openGlesSupersampleScale() == scale);
            item.addActionListener(event -> saveSettings(current -> current.withOpenGlesSupersampleScale(scale)));
            oglSupersamplingGroup.add(item);
            oglSupersamplingMenu.add(item);
        }
        experimentalMenu.add(oglSupersamplingMenu);

        JCheckBoxMenuItem showOpenGlesFpsItem = new JCheckBoxMenuItem("Show OpenGLES FPS");
        showOpenGlesFpsItem.setSelected(settings.showOpenGlesFps());
        showOpenGlesFpsItem.addActionListener(event -> saveSettings(
                current -> current.withShowOpenGlesFps(showOpenGlesFpsItem.isSelected())));
        experimentalMenu.add(showOpenGlesFpsItem);

        JCheckBoxMenuItem disableBytecodeVerificationItem = new JCheckBoxMenuItem("Disable Bytecode Verification");
        disableBytecodeVerificationItem.setSelected(settings.disableBytecodeVerification());
        disableBytecodeVerificationItem.addActionListener(event -> saveSettings(
                current -> current.withDisableBytecodeVerification(disableBytecodeVerificationItem.isSelected())));
        experimentalMenu.add(disableBytecodeVerificationItem);

        JCheckBoxMenuItem disableOsDpiScalingItem = new JCheckBoxMenuItem("Disable OS DPI Scaling");
        disableOsDpiScalingItem.setSelected(settings.disableOsDpiScaling());
        disableOsDpiScalingItem.addActionListener(event -> saveSettings(
                current -> current.withDisableOsDpiScaling(disableOsDpiScalingItem.isSelected())));
        experimentalMenu.add(disableOsDpiScalingItem);

        return experimentalMenu;
    }

    private void updateTerminalId() {
        LauncherSettings current = jamLaunchService.loadSettings();
        String updated = settingsController.promptTerminalId(this, current.terminalId());
        if (updated == null) {
            return;
        }
        jamLaunchService.saveSettings(current.withTerminalId(updated));
    }

    private void updateUserId() {
        LauncherSettings current = jamLaunchService.loadSettings();
        String updated = settingsController.promptUserId(this, current.userId());
        if (updated == null) {
            return;
        }
        jamLaunchService.saveSettings(current.withUserId(updated));
    }

    private void updateHttpOverrideDomain() {
        LauncherSettings current = jamLaunchService.loadSettings();
        String updated = settingsController.promptHttpOverrideDomain(this, current.httpOverrideDomain());
        if (updated == null) {
            return;
        }
        jamLaunchService.saveSettings(current.withHttpOverrideDomain(updated));
    }

    private void updateFileEncodingOverride() {
        LauncherSettings current = jamLaunchService.loadSettings();
        String updated = settingsController.promptFileEncodingOverride(this, current.fileEncodingOverride());
        if (updated == null) {
            return;
        }
        jamLaunchService.saveSettings(current.withFileEncodingOverride(updated));
    }

    private void updateMicroeditionPlatformOverride() {
        LauncherSettings current = jamLaunchService.loadSettings();
        String updated = settingsController.promptMicroeditionPlatformOverride(this, current.microeditionPlatformOverride());
        if (updated == null) {
            return;
        }
        jamLaunchService.saveSettings(current.withMicroeditionPlatformOverride(updated));
    }

    private void saveSettings(UnaryOperator<LauncherSettings> updater) {
        LauncherSettings current = jamLaunchService.loadSettings();
        jamLaunchService.saveSettings(updater.apply(current));
    }

    private static String formatSynthLabel(MLDSynth synth) {
        return switch (synth) {
            case FUETREK -> "FueTrek";
            case MA3 -> "MA3";
        };
    }

    private void beginUpdateCheck(boolean userInitiated) {
        if (updateCheckInProgress) {
            return;
        }
        updateCheckInProgress = true;
        checkUpdatesAction.setEnabled(false);
        if (userInitiated) {
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        }
        Thread.ofVirtual().start(() -> {
            try {
                LauncherUpdateService.UpdateCheckResult result = updateService.checkForUpdates();
                SwingUtilities.invokeLater(() -> handleUpdateCheckResult(userInitiated, result));
            } catch (Exception exception) {
                SwingUtilities.invokeLater(() -> handleUpdateCheckFailure(userInitiated, exception));
            } finally {
                SwingUtilities.invokeLater(() -> {
                    updateCheckInProgress = false;
                    checkUpdatesAction.setEnabled(true);
                    if (userInitiated) {
                        setCursor(Cursor.getDefaultCursor());
                    }
                });
            }
        });
    }

    private void handleUpdateCheckResult(boolean userInitiated, LauncherUpdateService.UpdateCheckResult result) {
        if (result.updateAvailable()) {
            JOptionPane.showMessageDialog(
                    this,
                    createHtmlPane("<html><body style='font-family:sans-serif;font-size:12px'>"
                            + "New stable release available: <b>" + escapeHtml(result.latestVersion()) + "</b><br><br>"
                            + "Download it at:<br>"
                            + "<a href='" + result.latestReleaseUrl() + "'>" + result.latestReleaseUrl() + "</a>"
                            + "</body></html>"),
                    "New Release Available",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (userInitiated) {
            JOptionPane.showMessageDialog(
                    this,
                    "You are already on the latest stable version.",
                    OpenDoJaLauncher.APP_NAME,
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void handleUpdateCheckFailure(boolean userInitiated, Exception exception) {
        if (!userInitiated) {
            OpenDoJaLog.debug(OpenDoJaLauncherFrame.class,
                    () -> "Automatic update check skipped: " + exception.getMessage());
            return;
        }
        JOptionPane.showMessageDialog(
                this,
                "Could not check for updates.\n\n" + exception.getMessage(),
                OpenDoJaLauncher.APP_NAME,
                JOptionPane.ERROR_MESSAGE);
    }

    private JEditorPane createHtmlPane(String html) {
        JEditorPane content = new JEditorPane("text/html", html);
        content.setEditable(false);
        content.setOpaque(false);
        content.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        content.addHyperlinkListener(event -> {
            if (event.getEventType() != javax.swing.event.HyperlinkEvent.EventType.ACTIVATED) {
                return;
            }
            if (!Desktop.isDesktopSupported()) {
                return;
            }
            try {
                Desktop.getDesktop().browse(event.getURL().toURI());
            } catch (IOException | URISyntaxException ignored) {
            }
        });
        return content;
    }

    private static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private void showAboutDialog() {
        JEditorPane content = createHtmlPane(
                "<html><body style='font-family:sans-serif;font-size:12px'>"
                        + "<b>" + OpenDoJaLauncher.APP_NAME + "</b><br>"
                        + "Version " + OpenDoJaLauncher.VERSION + "<br>"
                        + "Desktop launcher for DoJa games.<br><br>"
                        + "Source code: <a href='" + OpenDoJaLauncher.REPOSITORY_URL + "'>"
                        + OpenDoJaLauncher.REPOSITORY_URL + "</a>"
                        + "</body></html>");
        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(null);
        scrollPane.setPreferredSize(new Dimension(420, 150));
        JOptionPane.showMessageDialog(this, scrollPane, "About", JOptionPane.INFORMATION_MESSAGE);
    }
}
