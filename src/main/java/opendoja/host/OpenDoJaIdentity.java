package opendoja.host;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Pattern;

/**
 * Centralizes the user-configurable terminal and user identifiers exposed to games and network code.
 * It also rewrites the legacy default UID token in outbound HTTP traffic to the active configured UID.
 */
public final class OpenDoJaIdentity {
    private static final String DEFAULT_TERMINAL_ID = "000000000000000";
    private static final String DEFAULT_USER_ID = "NULLGWDOCOMO";
    private static final Pattern TERMINAL_ID_PATTERN = Pattern.compile("[A-Z0-9]{15}");
    private static final Pattern USER_ID_PATTERN = Pattern.compile("[A-Za-z0-9]{12}");

    private OpenDoJaIdentity() {
    }

    public static String defaultTerminalId() {
        return DEFAULT_TERMINAL_ID;
    }

    public static String defaultUserId() {
        return DEFAULT_USER_ID;
    }

    public static String terminalId() {
        return normalizeTerminalId(OpenDoJaLaunchArgs.get(OpenDoJaLaunchArgs.TERMINAL_ID));
    }

    public static String userId() {
        return normalizeUserId(OpenDoJaLaunchArgs.get(OpenDoJaLaunchArgs.USER_ID));
    }

    public static String normalizeTerminalId(String candidate) {
        if (candidate == null) {
            return DEFAULT_TERMINAL_ID;
        }
        String normalized = candidate.trim().toUpperCase();
        return isValidTerminalId(normalized) ? normalized : DEFAULT_TERMINAL_ID;
    }

    public static String normalizeUserId(String candidate) {
        if (candidate == null) {
            return DEFAULT_USER_ID;
        }
        String normalized = candidate.trim();
        return isValidUserId(normalized) ? normalized : DEFAULT_USER_ID;
    }

    public static boolean isValidTerminalId(String candidate) {
        return candidate != null && TERMINAL_ID_PATTERN.matcher(candidate).matches();
    }

    public static boolean isValidUserId(String candidate) {
        return candidate != null && USER_ID_PATTERN.matcher(candidate).matches();
    }

    public static String replaceDefaultUserIdToken(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return value.replace(DEFAULT_USER_ID, userId());
    }

    public static URL replaceDefaultUserIdToken(URL url) throws MalformedURLException {
        if (url == null) {
            return null;
        }
        String rewritten = replaceDefaultUserIdToken(url.toString());
        if (rewritten.equals(url.toString())) {
            return url;
        }
        return new URL(rewritten);
    }
}
