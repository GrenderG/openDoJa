package opendoja.launcher;

import opendoja.host.DoJaRuntime;
import opendoja.host.JamGameJarLocator;
import opendoja.host.JamMetadataResolver;
import opendoja.host.LinkedAppLaunchRequest;
import opendoja.host.OpenDoJaLog;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/** Resolves handset ADF URLs to local JAMs and launches each app in its own JVM. */
public final class LinkedAppLaunchService {
    private final Preferences preferences;
    private final JamSelector selector;

    public LinkedAppLaunchService() {
        this(Preferences.userNodeForPackage(LinkedAppLaunchService.class).node("linked-apps"),
                LinkedAppLaunchService::chooseJam);
    }

    LinkedAppLaunchService(Preferences preferences, JamSelector selector) {
        this.preferences = preferences;
        this.selector = selector;
    }

    public Process launch(DoJaRuntime runtime, String[] args) throws IOException {
        if (runtime == null || runtime.jamPath() == null) {
            throw new SecurityException("Linked launching requires a running JAM application");
        }
        String permission = runtime.parameters().get("LaunchApp");
        if (!"yes".equalsIgnoreCase(permission) && !"true".equalsIgnoreCase(permission)) {
            throw new SecurityException("The JAM must declare LaunchApp = yes");
        }
        Path targetJam = resolveJam(runtime, args[0]);
        Map<String, String> parameters = new LinkedHashMap<>();
        for (int i = 1; i < args.length; i += 2) {
            parameters.put(args[i], args[i + 1]);
        }
        Path request = new LinkedAppLaunchRequest(targetJam, parameters).write();
        try {
            GameLaunchSelection selection = new GameLaunchSelection(targetJam, JamGameJarLocator.locate(targetJam));
            Process process = new LauncherProcessSupport().startInBackground(selection, null, request);
            // The child owns the request file. Do not delete it when the source JVM exits.
            remember(args[0], targetJam);
            return process;
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(request);
            throw exception;
        }
    }

    Path resolveJam(DoJaRuntime runtime, String targetUrl) throws IOException {
        syncPreferences();
        Path sourceJam = runtime.jamPath().toAbsolutePath().normalize();
        String remembered = preferences.get(keyFor(targetUrl), null);
        if (remembered != null) {
            try {
                Path candidate = Path.of(remembered);
                validateTarget(sourceJam, candidate);
                return candidate;
            } catch (IOException | IllegalArgumentException | SecurityException exception) {
                preferences.remove(keyFor(targetUrl));
            }
        }
        // An unchanged download layout can resolve without a picker. Renamed archives
        // still need an explicit selection because PackageURL identifies the JAR, not the ADF.
        String name = applicationName(targetUrl);
        Path sibling = sourceJam.resolveSibling(name);
        if (!sibling.equals(sourceJam) && Files.isRegularFile(sibling)) {
            validateTarget(sourceJam, sibling);
            return sibling;
        }
        Path selected = selector.select(runtime, targetUrl);
        if (selected == null) {
            throw new SecurityException("Linked application selection was cancelled: " + name);
        }
        selected = selected.toAbsolutePath().normalize();
        validateTarget(sourceJam, selected);
        return selected;
    }

    private static void validateTarget(Path sourceJam, Path targetJam) throws IOException {
        JamGameJarLocator.locate(targetJam);
        if (Files.isSameFile(sourceJam, targetJam)) {
            throw new SecurityException("Select the requested application, rather than the running JAM");
        }
        if ("deny".equalsIgnoreCase(JamMetadataResolver.loadJamProperties(targetJam).getProperty("LaunchByApp"))) {
            throw new SecurityException("The selected JAM prohibits linked launching (LaunchByApp = deny)");
        }
    }

    void remember(String targetUrl, Path jam) {
        try {
            preferences.put(keyFor(targetUrl), jam.toAbsolutePath().normalize().toString());
            preferences.flush();
        } catch (BackingStoreException | RuntimeException exception) {
            OpenDoJaLog.warn(LinkedAppLaunchService.class, "Could not remember linked JAM selection", exception);
        }
    }

    public void clearRememberedSelections() throws IOException {
        try {
            preferences.sync();
            preferences.clear();
            preferences.flush();
        } catch (BackingStoreException exception) {
            throw new IOException("Could not clear remembered linked JAM selections", exception);
        }
    }

    private void syncPreferences() throws IOException {
        try {
            // A separate launcher JVM may have cleared the selections while this game was running.
            preferences.sync();
        } catch (BackingStoreException exception) {
            throw new IOException("Could not read remembered linked JAM selections", exception);
        }
    }

    private static String keyFor(String url) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String applicationName(String url) {
        String path = URI.create(url).getPath();
        return path == null || path.endsWith("/") ? url : path.substring(path.lastIndexOf('/') + 1);
    }

    private static Path chooseJam(DoJaRuntime runtime, String targetUrl) throws IOException {
        if (GraphicsEnvironment.isHeadless()) {
            throw new SecurityException("No local JAM is registered for " + targetUrl);
        }
        AtomicReference<Path> selected = new AtomicReference<>();
        Runnable choose = () -> {
            runtime.beginModalDialog();
            try {
                JFileChooser chooser = new JFileChooser(runtime.jamPath().getParent().toFile());
                chooser.setDialogTitle("Select JAM for " + applicationName(targetUrl));
                chooser.setFileFilter(new FileNameExtensionFilter("JAM files (*.jam)", "jam"));
                chooser.setAcceptAllFileFilterUsed(false);
                JTextArea description = new JTextArea("This application wants to launch:\n\n"
                        + applicationName(targetUrl) + "\n\n" + targetUrl
                        + "\n\nSelect its local JAM file. This selection will be remembered.", 12, 28);
                description.setEditable(false);
                description.setLineWrap(true);
                description.setWrapStyleWord(true);
                description.setBackground(UIManager.getColor("Panel.background"));
                chooser.setAccessory(description);
                while (chooser.showOpenDialog(runtime.dialogParent()) == JFileChooser.APPROVE_OPTION) {
                    Path candidate = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
                    try {
                        validateTarget(runtime.jamPath(), candidate);
                        selected.set(candidate);
                        break;
                    } catch (IOException | SecurityException exception) {
                        JOptionPane.showMessageDialog(runtime.dialogParent(), exception.getMessage(),
                                "Cannot launch selected JAM", JOptionPane.ERROR_MESSAGE);
                    }
                }
            } finally {
                runtime.endModalDialog();
            }
        };
        try {
            if (SwingUtilities.isEventDispatchThread()) choose.run();
            else SwingUtilities.invokeAndWait(choose);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while selecting linked application", exception);
        } catch (InvocationTargetException exception) {
            throw new IOException("Could not select linked application", exception.getCause());
        }
        return selected.get();
    }

    @FunctionalInterface
    interface JamSelector {
        Path select(DoJaRuntime runtime, String targetUrl) throws IOException;
    }
}
