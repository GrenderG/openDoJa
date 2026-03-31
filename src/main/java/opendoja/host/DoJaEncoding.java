package opendoja.host;

import java.nio.charset.Charset;
import java.util.List;

/**
 * Shared DoJa text-encoding resolution.
 */
public final class DoJaEncoding {
    // Probe these in order and use the first charset the host JVM exposes.
    private static final List<String> DEFAULT_ENCODING_CANDIDATES = List.of("Shift_JIS", "MS932", "windows-31j");
    public static final Charset DEFAULT_CHARSET = resolveDefaultCharset();

    private DoJaEncoding() {
    }

    public static Charset defaultCharset() {
        return DEFAULT_CHARSET;
    }

    public static String defaultCharsetName() {
        return DEFAULT_CHARSET.name();
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
