package opendoja.probes;

import com.docomostar.io.HttpConnection;
import com.sun.net.httpserver.HttpServer;
import javax.microedition.io.Connector;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import opendoja.host.DesktopLauncher;
import opendoja.host.DoJaRuntime;
import opendoja.host.LaunchConfig;
import com.nttdocomo.ui.IApplication;

/** Both API families must be able to cast and use the Connector's HTTP result. */
public final class StarHttpConnectionProbe {
    public static void main(String[] args) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var userAgent = new AtomicReference<String>();
        server.createContext("/status", exchange -> {
            userAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            byte[] response = "3".getBytes(StandardCharsets.US_ASCII);
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/status";
            for (boolean isStar : new boolean[]{false, true, false}) {
                var builder = LaunchConfig.builder(ProbeApp.class);
                if (isStar) builder.parameter("AppType", "FullApp");
                DesktopLauncher.launch(builder.build());
                try {
                    var connection = Connector.open(url, Connector.READ, true);
                    if (!(connection instanceof com.nttdocomo.io.HttpConnection)) throw new AssertionError("DoJa HTTP cast");
                    var star = (HttpConnection) connection;
                    try {
                        star.setRequestMethod(HttpConnection.GET);
                        star.connect();
                        try (var input = star.openInputStream()) {
                            if (!"3".equals(new String(input.readAllBytes(), StandardCharsets.US_ASCII))) {
                                throw new AssertionError("STAR HTTP response");
                            }
                        }
                        String expected = isStar ? "STAR_INET_CLIENT" : "DOJA_INET_CLIENT";
                        if (!userAgent.get().contains(expected)) throw new AssertionError("HTTP family identity: " + userAgent.get());
                    } finally { star.close(); }
                } finally { DoJaRuntime.current().shutdown(); }
            }
        } finally { server.stop(0); }
        System.out.println("star-http-connection-probe-ok");
    }

    public static final class ProbeApp extends IApplication {
        @Override public void start() { }
    }
}
