package opendoja.host;

import javax.microedition.io.Connector;
import java.net.URL;

public final class DesktopHttpConnectionProbe {
    private DesktopHttpConnectionProbe() {
    }

    public static void main(String[] args) throws Exception {
        verifyConfiguredHostOverridesAnyRequestHost();
        verifyConfiguredHostPreservesOriginalPortAndPath();
        verifyBlankOverrideDisablesRewriting();

        System.out.println("Desktop HTTP connection probe OK");
    }

    private static void verifyConfiguredHostOverridesAnyRequestHost() throws Exception {
        withOverride("override.example", () -> {
            DesktopHttpConnection connection = new DesktopHttpConnection(
                    new URL("http://game.example/path/file?a=1#frag"),
                    Connector.READ,
                    false);
            check("http://override.example/path/file?a=1#frag".equals(connection.getURL()),
                    "configured host should replace any outbound request host");
        });
    }

    private static void verifyConfiguredHostPreservesOriginalPortAndPath() throws Exception {
        withOverride("localhost", () -> {
            DesktopHttpConnection connection = new DesktopHttpConnection(
                    new URL("http://another-host.test:8080/path"),
                    Connector.READ,
                    false);
            check("http://localhost:8080/path".equals(connection.getURL()),
                    "configured host should preserve the original scheme, port, and path");
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
