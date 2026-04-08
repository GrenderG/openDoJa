package opendoja.host;

import java.nio.charset.Charset;
import java.util.List;

/**
 * Shared DoJa text-encoding resolution.
 */
public final class DoJaEncoding {
    // Probe these in order and use the first charset the host JVM exposes.
    private static final List<String> DEFAULT_ENCODING_CANDIDATES = List.of("windows-31j", "Shift_JIS", "MS932", "UTF-8");
    public static final Charset DEFAULT_CHARSET = resolveDefaultCharset();

    private DoJaEncoding() {
    }

    public static Charset defaultCharset() {
        return DEFAULT_CHARSET;
    }

    public static String defaultCharsetName() {
        return DEFAULT_CHARSET.name();
    }

    public static List<String> defaultEncodingCandidates() {
        return DEFAULT_ENCODING_CANDIDATES;
    }

    private static Charset resolveDefaultCharset() {
        for (String candidate : DEFAULT_ENCODING_CANDIDATES) {
            try {
                return Charset.forName(candidate);
            } catch (RuntimeException ignored) {
            }
        }
        return Charset.defaultCharset();
    }
}
