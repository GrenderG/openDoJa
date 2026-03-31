package opendoja.launcher;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class JamLaunchService {
    private final JamGameJarResolver gameJarResolver = new JamGameJarResolver();
    private final LauncherProcessSupport processSupport = new LauncherProcessSupport();
    private final LauncherPreferencesStore preferencesStore;

    JamLaunchService() {
        this(new LauncherPreferencesStore());
    }

    JamLaunchService(LauncherPreferencesStore preferencesStore) {
        this.preferencesStore = preferencesStore;
    }

    Path chooseJamFile(Component parent) {
        Path initialDirectory = preferencesStore.lastDirectory();
        JFileChooser chooser = initialDirectory == null
                ? new JFileChooser()
                : new JFileChooser(initialDirectory.toFile());
        chooser.setDialogTitle("Load JAM");
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setMultiSelectionEnabled(false);
        chooser.setFileFilter(new FileNameExtensionFilter("JAM files (*.jam)", "jam"));
        int result = chooser.showOpenDialog(parent);
        if (result != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) {
            return null;
        }
        Path jamPath = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
        if (jamPath != null) {
            preferencesStore.rememberLastDirectory(jamPath.getParent());
        }
        return jamPath;
    }

    GameLaunchResult launchInSeparateProcess(Path jamPath) throws IOException {
        GameLaunchSelection selection = gameJarResolver.resolve(jamPath);
        Process process = processSupport.startInBackground(selection, preferencesStore.loadSettings());
        preferencesStore.rememberLaunchedJam(selection.jamPath());
        return new GameLaunchResult(selection, process.pid());
    }

    static Path droppedJamPath(List<Path> droppedPaths) throws IOException {
        if (droppedPaths == null || droppedPaths.isEmpty()) {
            throw new IOException("Drop a single .jam file.");
        }
        if (droppedPaths.size() != 1) {
            throw new IOException("Drop exactly one .jam file.");
        }
        Path normalizedJam = droppedPaths.getFirst().toAbsolutePath().normalize();
        String fileName = normalizedJam.getFileName() == null ? normalizedJam.toString() : normalizedJam.getFileName().toString();
        if (!fileName.toLowerCase().endsWith(".jam")) {
            throw new IOException("Dropped file is not a .jam: " + fileName);
        }
        if (!Files.isRegularFile(normalizedJam)) {
            throw new IOException("Dropped JAM file does not exist: " + normalizedJam);
        }
        return normalizedJam;
    }

    List<Path> recentJamPaths() {
        return preferencesStore.recentJamPaths();
    }

    void removeRecentJam(Path jamPath) {
        preferencesStore.removeRecentJam(jamPath);
    }

    LauncherSettings loadSettings() {
        return preferencesStore.loadSettings();
    }

    void saveSettings(LauncherSettings settings) {
        preferencesStore.saveSettings(settings);
    }
}
