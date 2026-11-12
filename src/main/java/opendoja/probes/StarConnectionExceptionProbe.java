package opendoja.probes;

import com.docomostar.io.ConnectionException;
import com.docomostar.media.MediaManager;
import com.sun.net.httpserver.HttpServer;
import opendoja.host.*;

import java.io.*;
import java.net.*;
import javax.microedition.io.Connector;

/** Exercises real HTTP errors, delayed stream failures, and shared media preparation. */
public final class StarConnectionExceptionProbe {
    public static void main(String[] args) throws Exception {
        check(new ConnectionException().getStatus() == 0, "Default status");
        check(new ConnectionException(17, "unavailable").getStatus() == 17, "STAR status");
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/missing", exchange -> { exchange.sendResponseHeaders(404, -1); exchange.close(); });
        server.createContext("/error", exchange -> { exchange.sendResponseHeaders(503, -1); exchange.close(); });
        server.start();
        try {
            for (boolean star : new boolean[]{false, true, false}) {
                var config = LaunchConfig.builder(ProbeApp.class);
                if (star) config.parameter("AppType", "FullApp");
                DesktopLauncher.launch(config.build());
                try {
                    for (String path : new String[]{"/missing", "/error"}) {
                        var http = (com.docomostar.io.HttpConnection) Connector.open(
                                "http://127.0.0.1:" + server.getAddress().getPort() + path, Connector.READ, true);
                        try {
                            check(http.getResponseCode() == (path.equals("/missing") ? 404 : 503), "HTTP status remains readable");
                            expect(star, ConnectionException.HTTP_ERROR, http::openInputStream);
                        } finally { http.close(); }
                    }
                    IOException timeout = new SocketTimeoutException("read timed out");
                    var input = faultConnection(timeout, null).openInputStream();
                    expect(star, ConnectionException.TIMEOUT, input::read);
                    expect(star, ConnectionException.TIMEOUT, () -> input.read(new byte[2]));
                    var backendFailure = new com.nttdocomo.io.ConnectionException(5, "service unavailable");
                    var backendInput = faultConnection(backendFailure, null).openInputStream();
                    expect(star, ConnectionException.OUT_OF_SERVICE, backendInput::read);
                    IOException ssl = new javax.net.ssl.SSLException("write failed");
                    var output = faultConnection(null, ssl).openOutputStream();
                    expect(star, ConnectionException.SSL_ERROR, () -> output.write(1));
                    expect(star, ConnectionException.SSL_ERROR, output::flush);
                    expect(star, ConnectionException.SSL_ERROR, output::close);
                    verifyMedia(star);
                } finally { DoJaRuntime.current().shutdown(); }
            }
            var host = new com.nttdocomo.io.ConnectionException(11, "scratchpad limit");
            var translated = ConnectionException.from(host);
            check(translated.getStatus() == 11 && translated.getCause() == host
                    && "scratchpad limit".equals(translated.getMessage()), "Backend error details retained");
        } finally { server.stop(0); }
        System.out.println("star-connection-exception-probe-ok");
    }

    private static void verifyMedia(boolean star) throws Exception {
        byte[] invalid = {1, 2, 3};
        for (boolean reuse : new boolean[]{false, true}) {
            if (star) {
                var sound = MediaManager.getSound(invalid);
                expect(true, ConnectionException.NO_RESOURCES, () -> {
                    if (reuse) sound.use(null, false); else sound.use();
                });
            } else {
                var sound = com.nttdocomo.ui.MediaManager.getSound(invalid);
                try {
                    if (reuse) sound.use(null, false); else sound.use();
                    throw new AssertionError("Invalid DoJa audio accepted");
                } catch (com.nttdocomo.io.ConnectionException expected) {
                    check(expected.getStatus() == 2, "DoJa media status unchanged");
                }
            }
        }
    }

    private static DesktopHttpConnection faultConnection(IOException readFailure, IOException writeFailure) throws Exception {
        URL url = new URL(null, "http://127.0.0.1/probe", new URLStreamHandler() {
            @Override protected URLConnection openConnection(URL url) {
                return new HttpURLConnection(url) {
                    @Override public void connect() { }
                    @Override public void disconnect() { }
                    @Override public boolean usingProxy() { return false; }
                    @Override public int getResponseCode() { return 200; }
                    @Override public String getResponseMessage() { return "OK"; }
                    @Override public InputStream getInputStream() {
                        return new InputStream() { @Override public int read() throws IOException { throw readFailure; } };
                    }
                    @Override public OutputStream getOutputStream() {
                        return new OutputStream() {
                            @Override public void write(int b) throws IOException { throw writeFailure; }
                            @Override public void flush() throws IOException { throw writeFailure; }
                            @Override public void close() throws IOException { throw writeFailure; }
                        };
                    }
                };
            }
        });
        return new DesktopHttpConnection(url, Connector.READ_WRITE, true);
    }

    private static void expect(boolean star, int status, IOAction action) throws Exception {
        try { action.run(); throw new AssertionError("Expected IO failure"); }
        catch (IOException failure) {
            if (star) {
                check(failure instanceof ConnectionException, "STAR error type: " + failure);
                check(((ConnectionException) failure).getStatus() == status, "STAR status: " + failure);
                check(failure.getCause() != null && failure.getMessage().equals(failure.getCause().getMessage()), "Original cause and message");
            } else check(!(failure instanceof ConnectionException), "DoJa exception behavior unchanged");
        }
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    @FunctionalInterface private interface IOAction { void run() throws Exception; }
    public static final class ProbeApp extends com.nttdocomo.ui.IApplication {
        @Override public void start() { }
    }
}
