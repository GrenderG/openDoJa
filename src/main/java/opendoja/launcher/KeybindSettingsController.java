package opendoja.launcher;

import opendoja.host.HostControlAction;
import opendoja.host.HostInputBinding;
import opendoja.host.HostKeybindConfiguration;
import opendoja.host.HostKeybindProfile;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.atomic.AtomicReference;

final class KeybindSettingsController {
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
        JButton cancelButton = new JButton("Cancel");
        cancelButton.setFocusable(false);

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
                "<html>Press a keyboard key for " + slotLabel + ".<br>Modifier-only keys are ignored.</html>");
        instructions.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));
        instructions.setFocusable(false);

        JLabel captureHint = new JLabel("Input is captured immediately while this dialog is focused.");
        captureHint.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Separator.foreground")),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        captureHint.setFocusable(false);
        captureSurface.add(captureHint, BorderLayout.CENTER);

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
        return captured.get();
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
