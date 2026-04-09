package opendoja.host;

import javax.microedition.io.Connector;
import java.net.URL;

public final class DesktopHttpConnectionProbe {
    private DesktopHttpConnectionProbe() {
    }

    public static void main(String[] args) throws Exception {
        verifyMatchingHostRewritesToLocalhost();
        verifyMismatchedHostIsLeftAlone();
        verifyBlankOverrideDisablesRewriting();

        System.out.println("Desktop HTTP connection probe OK");
    }

    private static void verifyMatchingHostRewritesToLocalhost() throws Exception {
        withOverride("Example.com", () -> {
            DesktopHttpConnection connection = new DesktopHttpConnection(
                    new URL("http://example.com:8080/path/file?a=1#frag"),
                    Connector.READ,
                    false);
            check("http://localhost:8080/path/file?a=1#frag".equals(connection.getURL()),
                    "matching host should rewrite to localhost while preserving port, path, query, and fragment");
        });
    }

    private static void verifyMismatchedHostIsLeftAlone() throws Exception {
        withOverride("example.com", () -> {
            DesktopHttpConnection connection = new DesktopHttpConnection(
                    new URL("http://example.org/path"),
                    Connector.READ,
                    false);
            check("http://example.org/path".equals(connection.getURL()),
                    "non-matching hosts should not be rewritten");
        });
    }

    private static void verifyBlankOverrideDisablesRewriting() throws Exception {
        withOverride("", () -> {
            DesktopHttpConnection connection = new DesktopHttpConnection(
                    new URL("http://example.com/path"),
                    Connector.READ,
                    false);
            check("http://example.com/path".equals(connection.getURL()),
                    "blank override should leave requests unchanged");
        });
    }

    private static void withOverride(String value, ThrowingRunnable runnable) throws Exception {
        String previous = System.getProperty(OpenDoJaLaunchArgs.HTTP_OVERRIDE_DOMAIN);
        try {
            if (value == null) {
                System.clearProperty(OpenDoJaLaunchArgs.HTTP_OVERRIDE_DOMAIN);
            } else {
                System.setProperty(OpenDoJaLaunchArgs.HTTP_OVERRIDE_DOMAIN, value);
            }
            runnable.run();
        } finally {
            if (previous == null) {
                System.clearProperty(OpenDoJaLaunchArgs.HTTP_OVERRIDE_DOMAIN);
            } else {
                System.setProperty(OpenDoJaLaunchArgs.HTTP_OVERRIDE_DOMAIN, previous);
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
