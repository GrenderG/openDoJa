package opendoja.host;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DoJaProfile {
    private static final Pattern DOCUMENTED_SERIES = Pattern.compile(
            "(?i)(503i|503|504i|504|505i|505|900i|900|901i|901|902i|902|903i|903|904i|904|905i|905|906i|906)");

    public enum Generation {
        UNKNOWN(0),
        DOJA_1(1),
        DOJA_2(2),
        DOJA_3(3),
        DOJA_4(4),
        DOJA_5(5);

        private final int major;

        Generation(int major) {
            this.major = major;
        }

        public int major() {
            return major;
        }

        static Generation fromMajor(int major) {
            return switch (major) {
                case 1 -> DOJA_1;
                case 2 -> DOJA_2;
                case 3 -> DOJA_3;
                case 4 -> DOJA_4;
                case 5 -> DOJA_5;
                default -> UNKNOWN;
            };
        }
    }

    public static final DoJaProfile UNKNOWN = new DoJaProfile(null, Generation.UNKNOWN, 0, 0, false);

    private final String rawValue;
    private final Generation generation;
    private final int major;
    private final int minor;
    private final boolean parsed;

    private DoJaProfile(String rawValue, Generation generation, int major, int minor, boolean parsed) {
        this.rawValue = rawValue;
        this.generation = generation;
        this.major = major;
        this.minor = minor;
        this.parsed = parsed;
    }

    public static DoJaProfile current() {
        return fromRuntime(DoJaRuntime.current());
    }

    public static DoJaProfile fromRuntime(DoJaRuntime runtime) {
        return runtime == null ? UNKNOWN : fromParameters(runtime.parameters());
    }

    public static DoJaProfile fromParameters(Map<String, String> parameters) {
        if (parameters == null) {
            return UNKNOWN;
        }
        return parse(parameters.get("ProfileVer"));
    }

    public static DoJaProfile fromDocumentedDeviceIdentity(String deviceIdentity) {
        if (deviceIdentity == null || deviceIdentity.isBlank()) {
            return UNKNOWN;
        }
        Matcher matcher = DOCUMENTED_SERIES.matcher(deviceIdentity);
        if (!matcher.find()) {
            return UNKNOWN;
        }
        // As per official documentation, these handset series map to the i-appli runtime profile:
        // 503i -> DoJa-1.0, 504i -> DoJa-2.0, 505i -> DoJa-3.0, 900i -> DoJa-3.5,
        // 901i -> DoJa-4.0, 902i -> DoJa-4.1, 903i/904i -> DoJa-5.0, 905i/906i -> DoJa-5.1.
        String series = matcher.group(1).toLowerCase();
        return switch (series) {
            case "503", "503i" -> parse("DoJa-1.0");
            case "504", "504i" -> parse("DoJa-2.0");
            case "505", "505i" -> parse("DoJa-3.0");
            case "900", "900i" -> parse("DoJa-3.5");
            case "901", "901i" -> parse("DoJa-4.0");
            case "902", "902i" -> parse("DoJa-4.1");
            case "903", "903i", "904", "904i" -> parse("DoJa-5.0");
            case "905", "905i", "906", "906i" -> parse("DoJa-5.1");
            default -> UNKNOWN;
        };
    }

    public static DoJaProfile parse(String rawValue) {
        if (rawValue == null) {
            return UNKNOWN;
        }
        String trimmed = rawValue.trim();
        if (trimmed.isEmpty()) {
            return UNKNOWN;
        }
        int dash = trimmed.indexOf('-');
        String version = dash >= 0 ? trimmed.substring(dash + 1).trim() : trimmed;
        if (version.isEmpty()) {
            return UNKNOWN;
        }
        int dot = version.indexOf('.');
        String majorPart = dot >= 0 ? version.substring(0, dot).trim() : version;
        String minorPart = dot >= 0 ? version.substring(dot + 1).trim() : "0";
        try {
            int major = Integer.parseInt(majorPart);
            int minor = Integer.parseInt(minorPart);
            return new DoJaProfile(trimmed, Generation.fromMajor(major), major, minor, true);
        } catch (NumberFormatException ignored) {
            return UNKNOWN;
        }
    }

    public String rawValue() {
        return rawValue;
    }

    public Generation generation() {
        return generation;
    }

    public int major() {
        return major;
    }

    public int minor() {
        return minor;
    }

    public boolean isKnown() {
        return parsed;
    }

    public boolean isAtLeast(int major, int minor) {
        return compareTo(major, minor) >= 0;
    }

    public boolean isBefore(int major, int minor) {
        return compareTo(major, minor) < 0;
    }

    private int compareTo(int otherMajor, int otherMinor) {
        if (!isKnown()) {
            return -1;
        }
        if (major != otherMajor) {
            return Integer.compare(major, otherMajor);
        }
        return Integer.compare(minor, otherMinor);
    }

    @Override
    public String toString() {
        if (!isKnown()) {
            return "UNKNOWN";
        }
        return "DoJa-" + major + "." + minor;
    }
}
