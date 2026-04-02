package opendoja.launcher;

import opendoja.host.storage.DoJaStorageHost;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

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
        Path droppedPath = droppedPaths.getFirst().toAbsolutePath().normalize();
        if (Files.isDirectory(droppedPath)) {
            return droppedJamPathFromDirectory(droppedPath);
        }
        String fileName = droppedPath.getFileName() == null ? droppedPath.toString() : droppedPath.getFileName().toString();
        if (!hasJamExtension(fileName)) {
            throw new IOException("Dropped file is not a .jam: " + fileName);
        }
        if (!Files.isRegularFile(droppedPath)) {
            throw new IOException("Dropped JAM file does not exist: " + droppedPath);
        }
        return droppedPath;
    }

    private static Path droppedJamPathFromDirectory(Path directory) throws IOException {
        Path rootJam = firstJamInDirectory(directory);
        if (rootJam != null) {
            return rootJam;
        }
        Path binDirectory = directory.resolve("bin");
        if (!Files.isDirectory(binDirectory)) {
            return null;
        }
        return firstJamInDirectory(binDirectory);
    }

    private static Path firstJamInDirectory(Path directory) throws IOException {
        try (Stream<Path> children = Files.list(directory)) {
            return children
                    .filter(Files::isRegularFile)
                    .filter(path -> hasJamExtension(path.getFileName().toString()))
                    .sorted()
                    .findFirst()
                    .orElse(null);
        }
    }

    private static boolean hasJamExtension(String fileName) {
        return fileName.toLowerCase(Locale.ROOT).endsWith(".jam");
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

    static Path ensureSdCardFolder() throws IOException {
        Path sdCardFolder = DoJaStorageHost.deviceRoot().toAbsolutePath().normalize();
        Files.createDirectories(sdCardFolder);
        return sdCardFolder;
    }
}
