package opendoja.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LauncherUpdateService {
    private static final String GITHUB_ACCEPT = "application/vnd.github+json";
    private static final String GITHUB_API_VERSION = "2022-11-28";
    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)*)");

    private final ReleaseLookup releaseLookup;
    private final String latestReleaseUrl;

    LauncherUpdateService() {
        this(new GitHubReleaseLookup(OpenDoJaLauncher.GITHUB_LATEST_RELEASE_API_URL), OpenDoJaLauncher.LATEST_RELEASE_URL);
    }

    LauncherUpdateService(ReleaseLookup releaseLookup, String latestReleaseUrl) {
        this.releaseLookup = Objects.requireNonNull(releaseLookup, "releaseLookup");
        this.latestReleaseUrl = Objects.requireNonNull(latestReleaseUrl, "latestReleaseUrl");
    }

    UpdateCheckResult checkForUpdates() throws IOException {
        return checkForUpdates(OpenDoJaLauncher.VERSION);
    }

    UpdateCheckResult checkForUpdates(String currentVersion) throws IOException {
        ReleaseInfo release = releaseLookup.fetchLatestRelease();
        boolean updateAvailable = compareVersions(release.version(), currentVersion) > 0;
        return new UpdateCheckResult(currentVersion, release.version(), updateAvailable, latestReleaseUrl);
    }

    static ReleaseInfo parseLatestReleaseResponse(String body) throws IOException {
        return new ReleaseInfo(extractJsonString(body, "tag_name"));
    }

    static int compareVersions(String candidate, String baseline) {
        List<Integer> candidateParts = parseVersionParts(candidate);
        List<Integer> baselineParts = parseVersionParts(baseline);
        int size = Math.max(candidateParts.size(), baselineParts.size());
        for (int i = 0; i < size; i++) {
            int candidatePart = i < candidateParts.size() ? candidateParts.get(i) : 0;
            int baselinePart = i < baselineParts.size() ? baselineParts.get(i) : 0;
            if (candidatePart != baselinePart) {
                return Integer.compare(candidatePart, baselinePart);
            }
        }
        return 0;
    }

    private static List<Integer> parseVersionParts(String rawVersion) {
        String normalized = Objects.requireNonNull(rawVersion, "rawVersion").trim();
        Matcher matcher = VERSION_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Unsupported version text: " + rawVersion);
        }
        String[] tokens = matcher.group(1).split("\\.");
        List<Integer> parts = new ArrayList<>(tokens.length);
        for (String token : tokens) {
            parts.add(Integer.parseInt(token));
        }
        return parts;
    }

    private static String extractJsonString(String body, String fieldName) throws IOException {
        String json = Objects.requireNonNull(body, "body");
        String fieldToken = "\"" + fieldName + "\"";
        int fieldIndex = json.indexOf(fieldToken);
        if (fieldIndex < 0) {
            throw new IOException("GitHub response is missing " + fieldName);
        }
        int colonIndex = json.indexOf(':', fieldIndex + fieldToken.length());
        if (colonIndex < 0) {
            throw new IOException("GitHub response is missing a value for " + fieldName);
        }
        int valueIndex = colonIndex + 1;
        while (valueIndex < json.length() && Character.isWhitespace(json.charAt(valueIndex))) {
            valueIndex++;
        }
        if (valueIndex >= json.length() || json.charAt(valueIndex) != '"') {
            throw new IOException("GitHub response has a non-string " + fieldName);
        }
        StringBuilder value = new StringBuilder();
        for (int i = valueIndex + 1; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (ch == '\\') {
                if (i + 1 >= json.length()) {
                    throw new IOException("GitHub response ended in the middle of an escape sequence");
                }
                char escaped = json.charAt(++i);
                switch (escaped) {
                    case '"', '\\', '/' -> value.append(escaped);
                    case 'b' -> value.append('\b');
                    case 'f' -> value.append('\f');
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    case 'u' -> {
                        if (i + 4 >= json.length()) {
                            throw new IOException("GitHub response ended in the middle of a unicode escape");
                        }
                        String hex = json.substring(i + 1, i + 5);
                        try {
                            value.append((char) Integer.parseInt(hex, 16));
                        } catch (NumberFormatException exception) {
                            throw new IOException("GitHub response contained an invalid unicode escape", exception);
                        }
                        i += 4;
                    }
                    default -> throw new IOException("GitHub response contained an unsupported escape sequence");
                }
                continue;
            }
            if (ch == '"') {
                return value.toString();
            }
            value.append(ch);
        }
        throw new IOException("GitHub response ended before " + fieldName + " was closed");
    }

    record UpdateCheckResult(String currentVersion, String latestVersion, boolean updateAvailable, String latestReleaseUrl) {
    }

    record ReleaseInfo(String version) {
        ReleaseInfo {
            if (version == null || version.isBlank()) {
                throw new IllegalArgumentException("version");
            }
        }
    }

    interface ReleaseLookup {
        ReleaseInfo fetchLatestRelease() throws IOException;
    }

    private static final class GitHubReleaseLookup implements ReleaseLookup {
        private final String apiUrl;

        private GitHubReleaseLookup(String apiUrl) {
            this.apiUrl = apiUrl;
        }

        @Override
        public ReleaseInfo fetchLatestRelease() throws IOException {
            HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
            try {
                connection.setInstanceFollowRedirects(true);
                connection.setConnectTimeout(5_000);
                connection.setReadTimeout(5_000);
                connection.setRequestProperty("Accept", GITHUB_ACCEPT);
                connection.setRequestProperty("X-GitHub-Api-Version", GITHUB_API_VERSION);
                connection.setRequestProperty("User-Agent", OpenDoJaLauncher.APP_NAME + "/" + OpenDoJaLauncher.VERSION);

                int code = connection.getResponseCode();
                String body = readBody(code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream());
                if (code < 200 || code >= 300) {
                    throw new IOException(buildFailureMessage(code, connection.getResponseMessage(), body));
                }
                return parseLatestReleaseResponse(body);
            } finally {
                connection.disconnect();
            }
        }

        private static String readBody(InputStream stream) throws IOException {
            if (stream == null) {
                return "";
            }
            try (InputStream input = stream) {
                return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        private static String buildFailureMessage(int code, String responseMessage, String body) {
            StringBuilder message = new StringBuilder("GitHub update check failed with HTTP ").append(code);
            if (responseMessage != null && !responseMessage.isBlank()) {
                message.append(" (").append(responseMessage).append(')');
            }
            if (body != null && !body.isBlank()) {
                try {
                    String apiMessage = extractJsonString(body, "message");
                    if (!apiMessage.isBlank()) {
                        message.append(": ").append(apiMessage);
                    }
                } catch (IOException ignored) {
                }
            }
            return message.toString();
        }
    }
}
