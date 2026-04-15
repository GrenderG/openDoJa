package opendoja.launcher;

import opendoja.host.HostControlAction;
import opendoja.host.HostInputBinding;
import opendoja.host.HostKeybindConfiguration;
import opendoja.host.HostKeybindProfile;
import opendoja.host.input.ControllerBindingDescriptor;
import opendoja.host.input.ControllerDeviceInfo;
import opendoja.host.input.ControllerInputEvent;
import opendoja.host.input.ControllerInputListener;
import opendoja.host.input.ControllerInputManager;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

final class KeybindSettingsController {
    private static final long CONTROLLER_CAPTURE_DEBOUNCE_NANOS = 250_000_000L;
    private static final int CONTROLLER_CAPTURE_POLL_INTERVAL_MS = 40;
    private static final float CONTROLLER_CAPTURE_AXIS_THRESHOLD = 0.55f;

    HostKeybindConfiguration editKeybinds(Component parent, HostKeybindConfiguration currentConfiguration) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        KeybindDialog dialog = new KeybindDialog(owner,
                currentConfiguration == null ? HostKeybindConfiguration.defaults() : currentConfiguration);
        dialog.setVisible(true);
        return dialog.confirmedConfiguration();
    }

    private static String promptProfileName(Component parent, int profileIndex) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        String entered = (String) JOptionPane.showInputDialog(
                owner,
                "Enter a name for the new keybind profile.",
                "New Profile",
                JOptionPane.PLAIN_MESSAGE,
                null,
                null,
                "Profile " + (profileIndex + 1));
        if (entered == null) {
            return null;
        }
        String normalized = entered.trim();
        if (normalized.isEmpty()) {
            JOptionPane.showMessageDialog(
                    owner,
                    "Profile name must not be empty.",
                    "New Profile",
                    JOptionPane.ERROR_MESSAGE);
            return null;
        }
        return normalized;
    }

    private static HostInputBinding captureBinding(Component parent, String slotLabel) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, "Set " + slotLabel, Dialog.ModalityType.APPLICATION_MODAL);
        AtomicReference<HostInputBinding> captured = new AtomicReference<>();
        AtomicBoolean controllerCaptureArmed = new AtomicBoolean(false);
        AtomicReference<ControllerInputEvent> pendingControllerCandidate = new AtomicReference<>();
        JButton cancelButton = new JButton("Cancel");
        cancelButton.setFocusable(false);
        JLabel controllerStatusLabel = new JLabel("Scanning for controllers...");
        controllerStatusLabel.setFocusable(false);

        Frame controllerOwner = resolveOwnerFrame(owner);
        ControllerInputManager controllerInputManager = new ControllerInputManager(controllerOwner);
        Timer debounceSnapshotTimer = new Timer((int) (CONTROLLER_CAPTURE_DEBOUNCE_NANOS / 1_000_000L), event -> {
            controllerCaptureArmed.set(true);
            // Keep the strongest input that arrived during the debounce window so an analog stick
            // that was already moving when capture opened does not need to be moved a second time.
            ControllerInputEvent pendingCandidate = pendingControllerCandidate.getAndSet(null);
            if (pendingCandidate != null && acceptableControllerCapture(pendingCandidate)) {
                captured.compareAndSet(null, pendingCandidate.genericBinding());
                if (captured.get() != null) {
                    dialog.dispose();
                    return;
                }
            }
            HostInputBinding activeBinding = activeControllerBinding(controllerInputManager);
            if (activeBinding != null) {
                captured.set(activeBinding);
                dialog.dispose();
            }
        });
        debounceSnapshotTimer.setRepeats(false);
        Timer activePollTimer = new Timer(CONTROLLER_CAPTURE_POLL_INTERVAL_MS, event -> {
            if (captured.get() != null) {
                return;
            }
            if (!controllerCaptureArmed.get()) {
                return;
            }
            HostInputBinding activeBinding = activeControllerBinding(controllerInputManager);
            if (activeBinding != null) {
                captured.set(activeBinding);
                dialog.dispose();
            }
        });
        activePollTimer.setRepeats(true);
        debounceSnapshotTimer.start();
        activePollTimer.start();
        controllerInputManager.addListener(new ControllerInputListener() {
            @Override
            public void onDevicesChanged(List<ControllerDeviceInfo> devices) {
                SwingUtilities.invokeLater(() -> controllerStatusLabel.setText(controllerStatusText(devices)));
            }

            @Override
            public void onInput(ControllerInputEvent event) {
                if (!event.active()) {
                    return;
                }
                if (!acceptableControllerCapture(event)) {
                    return;
                }
                if (!controllerCaptureArmed.get()) {
                    pendingControllerCandidate.accumulateAndGet(event, KeybindSettingsController::strongerEvent);
                    return;
                }
                SwingUtilities.invokeLater(() -> {
                    captured.compareAndSet(null, event.genericBinding());
                    if (captured.get() != null) {
                        dialog.dispose();
                    }
                });
            }
        });

        JPanel captureSurface = new JPanel(new BorderLayout());
        captureSurface.setFocusable(true);
        captureSurface.setFocusTraversalKeysEnabled(false);
        captureSurface.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                int keyCode = event.getKeyCode();
                if (!HostInputBinding.isBindableKeyboardKeyCode(keyCode)) {
                    Toolkit.getDefaultToolkit().beep();
                    event.consume();
                    return;
                }
                captured.set(HostInputBinding.keyboard(keyCode));
                event.consume();
                dialog.dispose();
            }
        });
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                SwingUtilities.invokeLater(captureSurface::requestFocusInWindow);
            }

            @Override
            public void windowActivated(WindowEvent e) {
                SwingUtilities.invokeLater(captureSurface::requestFocusInWindow);
            }
        });

        JLabel instructions = new JLabel(
                "<html>Press a keyboard key or move/press a controller input for " + slotLabel
                        + ".<br>Modifier-only keys are ignored. Existing analog noise is filtered.</html>");
        instructions.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));
        instructions.setFocusable(false);

        JPanel centerPanel = new JPanel(new BorderLayout(0, 8));

        JLabel captureHint = new JLabel("Keyboard and controller input is captured immediately while this dialog is focused.");
        captureHint.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Separator.foreground")),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        captureHint.setFocusable(false);
        centerPanel.add(captureHint, BorderLayout.NORTH);

        controllerStatusLabel.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 0));
        centerPanel.add(controllerStatusLabel, BorderLayout.CENTER);

        if (isLinux()) {
            centerPanel.add(buildLinuxControllerNote(), BorderLayout.SOUTH);
        }
        captureSurface.add(centerPanel, BorderLayout.CENTER);

        cancelButton.addActionListener(event -> dialog.dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.add(cancelButton);

        JPanel content = new JPanel(new BorderLayout(0, 0));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(instructions, BorderLayout.NORTH);
        content.add(captureSurface, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        dialog.setResizable(false);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        debounceSnapshotTimer.stop();
        activePollTimer.stop();
        controllerInputManager.close();
        return captured.get();
    }

    private static ControllerInputEvent strongerEvent(ControllerInputEvent left, ControllerInputEvent right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return right.value() > left.value() ? right : left;
    }

    private static HostInputBinding activeControllerBinding(ControllerInputManager controllerInputManager) {
        List<ControllerInputEvent> activeInputs = controllerInputManager.activeInputs();
        ControllerInputEvent bestEvent = null;
        for (ControllerInputEvent activeInput : activeInputs) {
            if (!acceptableControllerCapture(activeInput)) {
                continue;
            }
            if (bestEvent == null || activeInput.value() > bestEvent.value()) {
                bestEvent = activeInput;
            }
        }
        return bestEvent == null ? null : bestEvent.genericBinding();
    }

    private static boolean acceptableControllerCapture(ControllerInputEvent event) {
        if (event == null || !event.active()) {
            return false;
        }
        if (event.bindingDescriptor().kind() == ControllerBindingDescriptor.Kind.AXIS) {
            return event.value() >= CONTROLLER_CAPTURE_AXIS_THRESHOLD;
        }
        return true;
    }

    private static JComponent buildLinuxControllerNote() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Linux Controller Access"),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)));

        JLabel noteLabel = new JLabel("<html>Controller input on Linux may require additional permissions. "
                + "Make sure your user is part of the input group.</html>");
        noteLabel.setFocusable(false);
        panel.add(noteLabel, BorderLayout.NORTH);

        JTextArea commandArea = new JTextArea("sudo usermod -aG input $USER");
        commandArea.setEditable(false);
        commandArea.setLineWrap(false);
        commandArea.setWrapStyleWord(false);
        commandArea.setFocusable(true);
        commandArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, commandArea.getFont().getSize()));
        commandArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Separator.foreground")),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)));
        panel.add(commandArea, BorderLayout.CENTER);

        JLabel reminderLabel = new JLabel("Log out and back in for the changes to take effect.");
        reminderLabel.setFocusable(false);
        panel.add(reminderLabel, BorderLayout.SOUTH);
        return panel;
    }

    private static Frame resolveOwnerFrame(Window owner) {
        Window current = owner;
        while (current != null && !(current instanceof Frame)) {
            current = current.getOwner();
        }
        return current instanceof Frame frame ? frame : null;
    }

    private static String controllerStatusText(List<ControllerDeviceInfo> devices) {
        if (devices == null || devices.isEmpty()) {
            return "No supported controllers detected. Keyboard capture remains available.";
        }
        String joined = devices.stream()
                .map(ControllerDeviceInfo::displayName)
                .limit(2)
                .reduce((left, right) -> left + ", " + right)
                .orElse("Controller");
        if (devices.size() > 2) {
            joined += " +" + (devices.size() - 2) + " more";
        }
        return "Controllers: " + joined;
    }

    private static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("linux");
    }

    private static final class KeybindDialog extends JDialog {
        private final JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
        private final JButton newProfileButton = new JButton("New Profile");
        private final JButton deleteProfileButton = new JButton("Delete Profile");
        private final JLabel profileHintLabel = new JLabel();
        private HostKeybindConfiguration workingConfiguration;
        private HostKeybindConfiguration confirmedConfiguration;

        private KeybindDialog(Window owner, HostKeybindConfiguration initialConfiguration) {
            super(owner, "Keybinds", Dialog.ModalityType.APPLICATION_MODAL);
            this.workingConfiguration = initialConfiguration;
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            setContentPane(buildContent());
            rebuildTabs(initialConfiguration.activeProfileIndex());
            pack();
            setResizable(false);
            setLocationRelativeTo(owner);
        }

        private JComponent buildContent() {
            tabs.addChangeListener(event -> {
                int selectedIndex = tabs.getSelectedIndex();
                if (selectedIndex >= 0) {
                    workingConfiguration = workingConfiguration.withActiveProfileIndex(selectedIndex);
                }
                updateProfileControls();
            });

            profileHintLabel.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

            newProfileButton.addActionListener(event -> {
                int newProfileIndex = workingConfiguration.profiles().size();
                String profileName = promptProfileName(this, newProfileIndex);
                if (profileName == null) {
                    return;
                }
                workingConfiguration = workingConfiguration.addProfile(profileName);
                rebuildTabs(workingConfiguration.activeProfileIndex());
            });
            deleteProfileButton.addActionListener(event -> deleteSelectedProfile());

            JPanel headerButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            headerButtons.add(newProfileButton);
            headerButtons.add(deleteProfileButton);

            JLabel hintLabel = new JLabel(
                    "Selected tab is the active launch profile. Assigning a key moves it within that profile.");

            JPanel header = new JPanel(new BorderLayout(12, 0));
            header.add(hintLabel, BorderLayout.CENTER);
            header.add(headerButtons, BorderLayout.EAST);

            JButton applyButton = new JButton("Apply");
            applyButton.addActionListener(event -> {
                confirmedConfiguration = workingConfiguration;
                dispose();
            });

            JButton cancelButton = new JButton("Cancel");
            cancelButton.addActionListener(event -> dispose());

            JPanel footerButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            footerButtons.add(applyButton);
            footerButtons.add(cancelButton);

            JPanel footer = new JPanel(new BorderLayout(0, 0));
            footer.add(profileHintLabel, BorderLayout.WEST);
            footer.add(footerButtons, BorderLayout.EAST);

            JPanel content = new JPanel(new BorderLayout(0, 12));
            content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
            content.add(header, BorderLayout.NORTH);
            content.add(tabs, BorderLayout.CENTER);
            content.add(footer, BorderLayout.SOUTH);
            return content;
        }

        private void deleteSelectedProfile() {
            int selectedIndex = tabs.getSelectedIndex();
            if (selectedIndex < 0) {
                return;
            }
            workingConfiguration = workingConfiguration.withActiveProfileIndex(selectedIndex);
            workingConfiguration = workingConfiguration.deleteProfile(selectedIndex);
            rebuildTabs(workingConfiguration.activeProfileIndex());
        }

        private void rebuildTabs(int selectedIndex) {
            tabs.removeAll();
            for (int profileIndex = 0; profileIndex < workingConfiguration.profiles().size(); profileIndex++) {
                int currentProfileIndex = profileIndex;
                HostKeybindProfile profile = workingConfiguration.profiles().get(profileIndex);
                tabs.addTab(
                        workingConfiguration.profileLabel(profileIndex),
                        new ProfilePanel(workingConfiguration.profileLabel(profileIndex), profile, updatedProfile ->
                                workingConfiguration = workingConfiguration.withProfile(currentProfileIndex, updatedProfile)));
            }
            tabs.setSelectedIndex(Math.clamp(selectedIndex, 0, tabs.getTabCount() - 1));
            updateProfileControls();
        }

        private void updateProfileControls() {
            int profileCount = workingConfiguration.profiles().size();
            int selectedIndex = tabs.getSelectedIndex();
            newProfileButton.setEnabled(workingConfiguration.canAddProfile());
            deleteProfileButton.setEnabled(selectedIndex >= 0 && workingConfiguration.canDeleteProfile(selectedIndex));
            String activeProfileName = selectedIndex >= 0
                    ? workingConfiguration.profileLabel(selectedIndex)
                    : workingConfiguration.profileLabel(workingConfiguration.activeProfileIndex());
            profileHintLabel.setText("Profiles: " + profileCount + "/" + HostKeybindConfiguration.MAX_PROFILES
                    + "    Active Profile: " + activeProfileName);
        }

        private HostKeybindConfiguration confirmedConfiguration() {
            return confirmedConfiguration;
        }
    }

    private static final class ProfilePanel extends JPanel {
        private static final HostControlAction[] ACTIONS = HostControlAction.values();

        private final String profileName;
        private final ProfileBindingsTableModel tableModel;
        private final JTable table;
        private final JButton setBinding1Button = new JButton("Set Binding 1...");
        private final JButton setBinding2Button = new JButton("Set Binding 2...");
        private final JButton clearBinding1Button = new JButton("Clear Binding 1");
        private final JButton clearBinding2Button = new JButton("Clear Binding 2");
        private final JButton resetButton = new JButton("Reset to Default");
        private final java.util.function.Consumer<HostKeybindProfile> profileConsumer;
        private HostKeybindProfile profile;

        private ProfilePanel(String profileName, HostKeybindProfile profile,
                             java.util.function.Consumer<HostKeybindProfile> profileConsumer) {
            super(new BorderLayout(0, 12));
            this.profileName = profileName;
            this.profile = profile;
            this.profileConsumer = profileConsumer;
            this.tableModel = new ProfileBindingsTableModel(profile);
            this.table = buildTable();
            if (table.getRowCount() > 0) {
                table.setRowSelectionInterval(0, 0);
            }
            setBorder(BorderFactory.createTitledBorder(profileName + " bindings"));

            add(buildTablePane(), BorderLayout.CENTER);
            add(buildButtonRow(), BorderLayout.SOUTH);
            updateSelectionActions();
        }

        private JTable buildTable() {
            JTable bindingsTable = new JTable(tableModel);
            bindingsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            bindingsTable.setRowSelectionAllowed(true);
            bindingsTable.setColumnSelectionAllowed(false);
            bindingsTable.setFillsViewportHeight(true);
            bindingsTable.setShowGrid(false);
            bindingsTable.setIntercellSpacing(new Dimension(0, 0));
            bindingsTable.getSelectionModel().addListSelectionListener(event -> updateSelectionActions());
            bindingsTable.setPreferredScrollableViewportSize(new Dimension(660, 340));
            return bindingsTable;
        }

        private JComponent buildTablePane() {
            JScrollPane scrollPane = new JScrollPane(table);
            scrollPane.setBorder(BorderFactory.createEmptyBorder());
            return scrollPane;
        }

        private JComponent buildButtonRow() {
            setBinding1Button.addActionListener(event -> assignBinding(0));
            setBinding2Button.addActionListener(event -> assignBinding(1));
            clearBinding1Button.addActionListener(event -> clearBinding(0));
            clearBinding2Button.addActionListener(event -> clearBinding(1));
            resetButton.addActionListener(event -> updateProfile(HostKeybindProfile.defaults()));

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            actions.add(setBinding1Button);
            actions.add(setBinding2Button);
            actions.add(clearBinding1Button);
            actions.add(clearBinding2Button);
            actions.add(resetButton);
            return actions;
        }

        private void assignBinding(int slotIndex) {
            int selectedRow = table.getSelectedRow();
            if (selectedRow < 0) {
                return;
            }
            HostControlAction action = ACTIONS[selectedRow];
            HostInputBinding binding = captureBinding(this,
                    "Binding " + (slotIndex + 1) + " for " + action.displayName());
            if (binding == null) {
                return;
            }
            updateProfile(profile.withBinding(action, slotIndex, binding));
            table.setRowSelectionInterval(selectedRow, selectedRow);
        }

        private void clearBinding(int slotIndex) {
            int selectedRow = table.getSelectedRow();
            if (selectedRow < 0) {
                return;
            }
            HostControlAction action = ACTIONS[selectedRow];
            updateProfile(profile.withoutBinding(action, slotIndex));
            table.setRowSelectionInterval(selectedRow, selectedRow);
        }

        private void updateProfile(HostKeybindProfile updatedProfile) {
            this.profile = updatedProfile;
            this.tableModel.setProfile(updatedProfile);
            this.profileConsumer.accept(updatedProfile);
            updateSelectionActions();
        }

        private void updateSelectionActions() {
            if (table == null) {
                return;
            }
            int selectedRow = table.getSelectedRow();
            boolean hasSelection = selectedRow >= 0;
            setBinding1Button.setEnabled(hasSelection);
            setBinding2Button.setEnabled(hasSelection);
            clearBinding1Button.setEnabled(hasSelection && profile.bindingAt(ACTIONS[selectedRow], 0) != null);
            clearBinding2Button.setEnabled(hasSelection && profile.bindingAt(ACTIONS[selectedRow], 1) != null);
            resetButton.setEnabled(!profile.equals(HostKeybindProfile.defaults()));
        }
    }

    private static final class ProfileBindingsTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Action", "Binding 1", "Binding 2"};
        private static final HostControlAction[] ACTIONS = HostControlAction.values();

        private HostKeybindProfile profile;

        private ProfileBindingsTableModel(HostKeybindProfile profile) {
            this.profile = profile;
        }

        private void setProfile(HostKeybindProfile profile) {
            this.profile = profile;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return ACTIONS.length;
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            HostControlAction action = ACTIONS[rowIndex];
            return switch (columnIndex) {
                case 0 -> action.displayName();
                case 1 -> labelFor(action, 0);
                case 2 -> labelFor(action, 1);
                default -> "";
            };
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }

        private String labelFor(HostControlAction action, int slotIndex) {
            HostInputBinding binding = profile.bindingAt(action, slotIndex);
            return binding == null ? "" : binding.displayLabel();
        }
    }
}
