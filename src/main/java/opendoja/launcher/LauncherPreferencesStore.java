package opendoja.launcher;

import opendoja.host.OpenDoJaIdentity;
import opendoja.host.OpenDoJaLaunchArgs;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.prefs.Preferences;

final class LauncherPreferencesStore {
    private static final int MAX_RECENTS = 5;
    private static final String HOST_SCALE_KEY = "hostScale";
    private static final String SYNTH_ID_KEY = "synthId";
    private static final String TERMINAL_ID_KEY = "terminalId";
    private static final String USER_ID_KEY = "userId";
    private static final String LAST_DIRECTORY_KEY = "lastDirectory";
    private static final String RECENT_JAM_KEY_PREFIX = "recentJam.";

    private final Preferences preferences = Preferences.userNodeForPackage(OpenDoJaLauncher.class);

    LauncherSettings loadSettings() {
        int storedHostScale = preferences.getInt(HOST_SCALE_KEY, OpenDoJaLaunchArgs.getInt(OpenDoJaLaunchArgs.HOST_SCALE));
        String storedSynthId = preferences.get(SYNTH_ID_KEY, OpenDoJaLaunchArgs.get(OpenDoJaLaunchArgs.MLD_SYNTH));
        String storedTerminalId = preferences.get(TERMINAL_ID_KEY, OpenDoJaIdentity.defaultTerminalId());
        String storedUserId = preferences.get(USER_ID_KEY, OpenDoJaIdentity.defaultUserId());
        return new LauncherSettings(
                OpenDoJaLaunchArgs.getInt(OpenDoJaLaunchArgs.HOST_SCALE, storedHostScale),
                OpenDoJaLaunchArgs.get(OpenDoJaLaunchArgs.MLD_SYNTH, storedSynthId),
                OpenDoJaLaunchArgs.get(OpenDoJaLaunchArgs.TERMINAL_ID, storedTerminalId),
                OpenDoJaLaunchArgs.get(OpenDoJaLaunchArgs.USER_ID, storedUserId));
    }

    void saveSettings(LauncherSettings settings) {
        preferences.putInt(HOST_SCALE_KEY, settings.hostScale());
        preferences.put(SYNTH_ID_KEY, settings.synthId());
        preferences.put(TERMINAL_ID_KEY, settings.terminalId());
        preferences.put(USER_ID_KEY, settings.userId());
    }

    Path lastDirectory() {
        String raw = preferences.get(LAST_DIRECTORY_KEY, null);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return Path.of(raw).toAbsolutePath().normalize();
    }

    void rememberLastDirectory(Path directory) {
        if (directory == null) {
            preferences.remove(LAST_DIRECTORY_KEY);
            return;
        }
        preferences.put(LAST_DIRECTORY_KEY, directory.toAbsolutePath().normalize().toString());
    }

    List<Path> recentJamPaths() {
        List<Path> recents = new ArrayList<>(MAX_RECENTS);
        for (int i = 0; i < MAX_RECENTS; i++) {
            String raw = preferences.get(RECENT_JAM_KEY_PREFIX + i, null);
            if (raw == null || raw.isBlank()) {
                continue;
            }
            recents.add(Path.of(raw).toAbsolutePath().normalize());
        }
        return List.copyOf(recents);
    }

    void rememberLaunchedJam(Path jamPath) {
        Path normalized = jamPath.toAbsolutePath().normalize();
        rememberLastDirectory(normalized.getParent());

        LinkedHashSet<Path> updated = new LinkedHashSet<>();
        updated.add(normalized);
        updated.addAll(recentJamPaths());

        int index = 0;
        for (Path recent : updated) {
            if (index >= MAX_RECENTS) {
                break;
            }
            preferences.put(RECENT_JAM_KEY_PREFIX + index, recent.toString());
            index++;
        }
        while (index < MAX_RECENTS) {
            preferences.remove(RECENT_JAM_KEY_PREFIX + index);
            index++;
        }
    }

    void removeRecentJam(Path jamPath) {
        Path normalized = jamPath.toAbsolutePath().normalize();
        List<Path> filtered = recentJamPaths().stream()
                .filter(path -> !path.equals(normalized))
                .toList();
        for (int i = 0; i < MAX_RECENTS; i++) {
            preferences.remove(RECENT_JAM_KEY_PREFIX + i);
        }
        for (int i = 0; i < filtered.size() && i < MAX_RECENTS; i++) {
            preferences.put(RECENT_JAM_KEY_PREFIX + i, filtered.get(i).toString());
        }
    }
}
