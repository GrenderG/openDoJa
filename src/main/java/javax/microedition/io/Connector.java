package javax.microedition.io;

import opendoja.host.DesktopHttpConnection;
import opendoja.host.DoJaRuntime;
import opendoja.host.ScratchpadStreams;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.Locale;

public class Connector {
    public static final int READ = 1;
    public static final int WRITE = 2;
    public static final int READ_WRITE = 3;

    public static Connection open(String name) throws IOException {
        return open(name, READ_WRITE);
    }

    public static Connection open(String name, int mode) throws IOException {
        return open(name, mode, false);
    }

    public static Connection open(String name, int mode, boolean timeouts) throws IOException {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return new DesktopHttpConnection(new URL(name), mode, timeouts);
        }
        if (lower.startsWith("resource:///")) {
            return new SimpleStreamConnection(
                    openInputStream(name),
                    noOutput(),
                    name,
                    null,
                    -1L
            );
        }
        if (lower.startsWith("scratchpad:///")) {
            return new SimpleStreamConnection(
                    openInputStream(name),
                    openOutputStream(name),
                    name,
                    "application/octet-stream",
                    -1L
            );
        }
        throw new ConnectionNotFoundException("Unsupported connector URI: " + name);
    }

    public static DataInputStream openDataInputStream(String name) throws IOException {
        return new DataInputStream(openInputStream(name));
    }

    public static DataOutputStream openDataOutputStream(String name) throws IOException {
        return new DataOutputStream(openOutputStream(name));
    }

    public static InputStream openInputStream(String name) throws IOException {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.startsWith("resource:///")) {
            return DoJaRuntime.openLaunchResourceStream(name);
        }
        if (lower.startsWith("scratchpad:///")) {
            DoJaRuntime runtime = DoJaRuntime.current();
            if (runtime == null) {
                throw new IOException("No active DoJa runtime for scratchpad URI: " + name);
            }
            ScratchpadLocation location = ScratchpadLocation.parse(name);
            return ScratchpadStreams.openInput(runtime.scratchpadFile(location.index()), location.position(), location.length());
        }
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return ((DesktopHttpConnection) open(name, READ, false)).openInputStream();
        }
        throw new ConnectionNotFoundException("Unsupported input connector URI: " + name);
    }

    public static OutputStream openOutputStream(String name) throws IOException {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.startsWith("scratchpad:///")) {
            DoJaRuntime runtime = DoJaRuntime.current();
            if (runtime == null) {
                throw new IOException("No active DoJa runtime for scratchpad URI: " + name);
            }
            ScratchpadLocation location = ScratchpadLocation.parse(name);
            return ScratchpadStreams.openOutput(runtime.scratchpadFile(location.index()), location.position(), location.length());
        }
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return ((DesktopHttpConnection) open(name, READ_WRITE, false)).openOutputStream();
        }
        throw new ConnectionNotFoundException("Unsupported output connector URI: " + name);
    }

    private static OutputStream noOutput() {
        return null;
    }

    private record ScratchpadLocation(int index, long position, long length) {
        private static ScratchpadLocation parse(String raw) {
            String body = raw.substring("scratchpad:///".length());
            int split = 0;
            while (split < body.length() && Character.isDigit(body.charAt(split))) {
                split++;
            }
            int index = Integer.parseInt(body.substring(0, split).trim());
            long position = 0L;
            long length = -1L;
            String params = body.substring(split)
                    .replace(';', '&')
                    .replace(',', '&')
                    .replace(':', '&');
            for (String token : params.split("&")) {
                token = token.trim();
                if (token.isEmpty()) {
                    continue;
                }
                if (token.startsWith("pos=")) {
                    position = Long.parseLong(token.substring("pos=".length()).trim());
                } else if (token.startsWith("length=")) {
                    length = Long.parseLong(token.substring("length=".length()).trim());
                }
            }
            return new ScratchpadLocation(index, position, length);
        }
    }

    private static final class SimpleStreamConnection implements ContentConnection {
        private final InputStream input;
        private final OutputStream output;
        private final String encoding;
        private final String type;
        private final long length;

        private SimpleStreamConnection(InputStream input, OutputStream output, String encoding, String type, long length) {
            this.input = input;
            this.output = output;
            this.encoding = encoding;
            this.type = type;
            this.length = length;
        }

        @Override
        public InputStream openInputStream() {
            return input;
        }

        @Override
        public DataInputStream openDataInputStream() {
            return new DataInputStream(input);
        }

        @Override
        public OutputStream openOutputStream() throws IOException {
            if (output == null) {
                throw new IOException("Output not supported");
            }
            return output;
        }

        @Override
        public DataOutputStream openDataOutputStream() throws IOException {
            return new DataOutputStream(openOutputStream());
        }

        @Override
        public void close() throws IOException {
            if (input != null) {
                input.close();
            }
            if (output != null) {
                output.close();
            }
        }

        @Override
        public String getType() {
            return type;
        }

        @Override
        public String getEncoding() {
            return encoding;
        }

        @Override
        public long getLength() {
            return length;
        }
    }
}
