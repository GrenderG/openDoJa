package opendoja.host;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DesktopExternalVideoPlayback implements AutoCloseable {
    private static final long COMPLETE_SLACK_MILLIS = 250L;

    private final DoJaRuntime runtime;
    private final byte[] data;
    private final String extension;
    private final long durationMillis;
    private final Runnable completeCallback;
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile Process process;
    private volatile Path tempFile;
    private volatile ScheduledFuture<?> completionFuture;

    public DesktopExternalVideoPlayback(DoJaRuntime runtime, byte[] data, String extension, long durationMillis,
                                        Runnable completeCallback) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.data = data == null ? new byte[0] : data.clone();
        this.extension = extension == null || extension.isBlank() ? "3gp" : extension.trim();
        this.durationMillis = durationMillis;
        this.completeCallback = Objects.requireNonNull(completeCallback, "completeCallback");
    }

    public void start() {
        if (closed.get()) {
            return;
        }
        runtime.registerShutdownResource(this);
        long completeAfterMillis = resolveCompletionDelayMillis();
        completionFuture = runtime.scheduler().schedule(
                () -> runtime.postApplicationCallback(this::completeFromTimer),
                completeAfterMillis,
                TimeUnit.MILLISECONDS);
        try {
            Path extracted = Files.createTempFile("opendoja-video-", "." + extension);
            Files.write(extracted, data, StandardOpenOption.TRUNCATE_EXISTING);
            tempFile = extracted;
            Process started = launchExternalPlayer(extracted);
            if (started != null) {
                process = started;
            } else {
                OpenDoJaLog.warn(DesktopExternalVideoPlayback.class,
                        "No external video player found; using completion shim only");
            }
        } catch (IOException e) {
            OpenDoJaLog.warn(DesktopExternalVideoPlayback.class,
                    "Failed to export external video playback file", e);
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        runtime.unregisterShutdownResource(this);
        ScheduledFuture<?> future = completionFuture;
        completionFuture = null;
        if (future != null) {
            future.cancel(false);
        }
        Process current = process;
        process = null;
        if (current != null && current.isAlive()) {
            current.destroy();
        }
        Path extracted = tempFile;
        tempFile = null;
        if (extracted != null) {
            try {
                Files.deleteIfExists(extracted);
            } catch (IOException ignored) {
            }
        }
    }

    private void completeFromTimer() {
        if (closed.get()) {
            return;
        }
        close();
        completeCallback.run();
    }

    private long resolveCompletionDelayMillis() {
        long configuredFallback = Math.max(0L,
                OpenDoJaLaunchArgs.getLong(OpenDoJaLaunchArgs.VISUAL_PLAYER_FALLBACK_DELAY_MS, 2000L));
        long base = durationMillis > 0L ? durationMillis : configuredFallback;
        return Math.max(0L, base + COMPLETE_SLACK_MILLIS);
    }

    private static Process launchExternalPlayer(Path file) {
        PlayerCommand command = resolvePlayerCommand(file);
        if (command == null) {
            return null;
        }
        try {
            return new ProcessBuilder(command.command())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (IOException e) {
            OpenDoJaLog.warn(DesktopExternalVideoPlayback.class,
                    "Failed to launch external video player: " + command.command(), e);
            return null;
        }
    }

    private static PlayerCommand resolvePlayerCommand(Path file) {
        String configuredPath = OpenDoJaLaunchArgs.get(OpenDoJaLaunchArgs.VISUAL_PLAYER_PATH, "").trim();
        if (!configuredPath.isEmpty()) {
            return buildPlayerCommand(Path.of(configuredPath), file);
        }
        for (String candidate : preferredPlayerCandidates()) {
            Path resolved = resolveExecutable(candidate);
            if (resolved != null) {
                return buildPlayerCommand(resolved, file);
            }
        }
        return null;
    }

    private static PlayerCommand buildPlayerCommand(Path executable, Path file) {
        String lower = executable.getFileName().toString().toLowerCase(Locale.ROOT);
        List<String> command = new ArrayList<>();
        command.add(executable.toString());
        if (lower.equals("vlc.exe") || lower.equals("vlc")) {
            command.add("--play-and-exit");
            command.add("--quiet");
        } else if (lower.equals("mpv.exe") || lower.equals("mpv")) {
            command.add("--force-window=yes");
            command.add("--keep-open=no");
            command.add("--really-quiet");
        } else if (lower.equals("ffplay.exe") || lower.equals("ffplay")) {
            command.add("-autoexit");
            command.add("-loglevel");
            command.add("error");
        }
        command.add(file.toString());
        return new PlayerCommand(command);
    }

    private static List<String> preferredPlayerCandidates() {
        List<String> candidates = new ArrayList<>();
        if (isWindows()) {
            candidates.add("C:\\Program Files\\VideoLAN\\VLC\\vlc.exe");
            candidates.add("C:\\Program Files (x86)\\VideoLAN\\VLC\\vlc.exe");
            candidates.add("C:\\Program Files\\mpv\\mpv.exe");
            candidates.add("vlc.exe");
            candidates.add("mpv.exe");
            candidates.add("ffplay.exe");
        } else {
            candidates.add("vlc");
            candidates.add("mpv");
            candidates.add("ffplay");
        }
        return candidates;
    }

    private static Path resolveExecutable(String candidate) {
        Path direct = Path.of(candidate);
        if (Files.isRegularFile(direct)) {
            return direct;
        }
        if (candidate.contains("\\") || candidate.contains("/") || candidate.contains(":")) {
            return null;
        }
        String pathValue = System.getenv("PATH");
        if (pathValue == null || pathValue.isBlank()) {
            return null;
        }
        String pathExt = System.getenv().getOrDefault("PATHEXT", ".EXE;.CMD;.BAT;.COM");
        List<String> suffixes = new ArrayList<>();
        if (isWindows()) {
            for (String suffix : pathExt.split(";")) {
                if (!suffix.isBlank()) {
                    suffixes.add(suffix.trim().toLowerCase(Locale.ROOT));
                }
            }
            if (suffixes.isEmpty()) {
                suffixes.add(".exe");
            }
        } else {
            suffixes.add("");
        }
        String lowerCandidate = candidate.toLowerCase(Locale.ROOT);
        for (String entry : pathValue.split(java.io.File.pathSeparator)) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            Path dir = Path.of(entry);
            if (isWindows() && lowerCandidate.contains(".")) {
                Path exact = dir.resolve(candidate);
                if (Files.isRegularFile(exact)) {
                    return exact;
                }
            }
            for (String suffix : suffixes) {
                String fileName = isWindows() && !lowerCandidate.endsWith(suffix) ? candidate + suffix : candidate;
                Path resolved = dir.resolve(fileName);
                if (Files.isRegularFile(resolved)) {
                    return resolved;
                }
            }
        }
        return null;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private record PlayerCommand(List<String> command) {
    }
}
