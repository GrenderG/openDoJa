package opendoja.launcher;

import opendoja.audio.mld.MldSynth;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.ButtonGroup;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

final class OpenDoJaLauncherFrame extends JFrame {
    private final JamLaunchService jamLaunchService;
    private final LauncherSettingsController settingsController;
    private final Action loadJamAction;
    private final JMenu recentMenu;

    OpenDoJaLauncherFrame(JamLaunchService jamLaunchService, LauncherSettingsController settingsController) {
        super(OpenDoJaLauncher.APP_NAME);
        this.jamLaunchService = jamLaunchService;
        this.settingsController = settingsController;
        this.loadJamAction = new AbstractAction("Load JAM") {
            @Override
            public void actionPerformed(ActionEvent event) {
                chooseAndLaunchJam();
            }
        };
        this.recentMenu = new JMenu("Load recents...");
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
        fileMenu.addSeparator();
        fileMenu.add(new JMenuItem(new AbstractAction("Exit") {
            @Override
            public void actionPerformed(ActionEvent event) {
                dispose();
            }
        }));

        JMenu settingsMenu = new JMenu("Settings");
        settingsMenu.add(new JMenuItem(new AbstractAction("Keybinds") {
            @Override
            public void actionPerformed(ActionEvent event) {
                settingsController.showKeybinds(OpenDoJaLauncherFrame.this);
            }
        }));
        settingsMenu.addSeparator();
        settingsMenu.add(buildHostScaleMenu());
        settingsMenu.add(buildSynthMenu());
        settingsMenu.addSeparator();
        settingsMenu.add(new JMenuItem(new AbstractAction("Terminal ID...") {
            @Override
            public void actionPerformed(ActionEvent event) {
                updateTerminalId();
            }
        }));
        settingsMenu.add(new JMenuItem(new AbstractAction("User ID...") {
            @Override
            public void actionPerformed(ActionEvent event) {
                updateUserId();
            }
        }));

        JMenu helpMenu = new JMenu("Help");
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

        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.add(loadButton);

        JPanel root = new JPanel(new BorderLayout());
        root.add(centerPanel, BorderLayout.CENTER);
        return root;
    }

    private void chooseAndLaunchJam() {
        Path jamPath = jamLaunchService.chooseJamFile(this);
        if (jamPath == null) {
            return;
        }
        launchJam(jamPath);
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
        for (int scale = 1; scale <= 4; scale++) {
            final int selectedScale = scale;
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(new AbstractAction(Integer.toString(scale)) {
                @Override
                public void actionPerformed(ActionEvent event) {
                    LauncherSettings current = jamLaunchService.loadSettings();
                    jamLaunchService.saveSettings(new LauncherSettings(
                            selectedScale,
                            current.synthId(),
                            current.terminalId(),
                            current.userId()));
                }
            });
            item.setSelected(settings.hostScale() == scale);
            group.add(item);
            hostScaleMenu.add(item);
        }
        return hostScaleMenu;
    }

    private JMenu buildSynthMenu() {
        LauncherSettings settings = jamLaunchService.loadSettings();
        JMenu synthMenu = new JMenu("Synth");
        ButtonGroup group = new ButtonGroup();
        for (MldSynth synth : settingsController.availableSynths()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(new AbstractAction(formatSynthLabel(synth)) {
                @Override
                public void actionPerformed(ActionEvent event) {
                    LauncherSettings current = jamLaunchService.loadSettings();
                    jamLaunchService.saveSettings(new LauncherSettings(
                            current.hostScale(),
                            synth.id,
                            current.terminalId(),
                            current.userId()));
                }
            });
            item.setSelected(settings.synthId().equals(synth.id));
            group.add(item);
            synthMenu.add(item);
        }
        return synthMenu;
    }

    private void updateTerminalId() {
        LauncherSettings current = jamLaunchService.loadSettings();
        String updated = settingsController.promptTerminalId(this, current.terminalId());
        if (updated == null) {
            return;
        }
        jamLaunchService.saveSettings(new LauncherSettings(
                current.hostScale(),
                current.synthId(),
                updated,
                current.userId()));
    }

    private void updateUserId() {
        LauncherSettings current = jamLaunchService.loadSettings();
        String updated = settingsController.promptUserId(this, current.userId());
        if (updated == null) {
            return;
        }
        jamLaunchService.saveSettings(new LauncherSettings(
                current.hostScale(),
                current.synthId(),
                current.terminalId(),
                updated));
    }

    private static String formatSynthLabel(MldSynth synth) {
        String id = synth.id.replace('_', ' ');
        return id.substring(0, 1).toUpperCase(Locale.ROOT) + id.substring(1);
    }

    private void showAboutDialog() {
        JOptionPane.showMessageDialog(
                this,
                OpenDoJaLauncher.APP_NAME + "\nVersion " + OpenDoJaLauncher.VERSION
                        + "\nDesktop launcher for DoJa games.",
                "About",
                JOptionPane.INFORMATION_MESSAGE);
    }
}
