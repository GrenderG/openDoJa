package opendoja.host;

import com.nttdocomo.io.HttpConnection;

import java.io.*;
import java.net.*;

public final class DesktopHttpConnection implements HttpConnection, com.docomostar.io.HttpConnection {
    private static final String DEFAULT_USER_AGENT = "DoCoMo/2.0 DOJA_INET_CLIENT(c100;TJ)";
    private static final String STAR_USER_AGENT = "DoCoMo/2.0 STAR_INET_CLIENT(c100;SJ)";

    private final URL url;
    private final int mode;
    private final boolean timeouts;
    private final boolean star = ApplicationProfile.current() == ApplicationProfile.STAR;
    private int responseCode = -1;
    private HttpURLConnection connection;
    private InputStream responseInputStream;
    private boolean closed;
    private String requestMethod = GET;
    private long ifModifiedSince = 0L;
    private boolean userAgentProvidedByGame;
    private boolean requestLogged;
    private boolean responseLogged;

    public DesktopHttpConnection(URL url, int mode, boolean timeouts) {
        this.url = rewriteOutboundUrl(url);
        this.mode = mode;
        this.timeouts = timeouts;
    }

    @Override
    public void connect() throws IOException {
        try {
            ensureConnection();
            logRequestIfNeeded();
            connection.connect();
        } catch (IOException exception) {
            OpenDoJaLog.warn(DesktopHttpConnection.class, "HTTP connect failed " + requestSummary(), exception);
            throw translate(exception);
        }
    }

    @Override
    public void close() throws IOException {
        if (closed) return;
        closed = true;
        try {
            if (responseInputStream != null) {
                http(() -> { responseInputStream.close(); return null; });
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    @Override
    public InputStream openInputStream() throws IOException {
        try {
            ensureConnection();
            logRequestIfNeeded();
            if (star) responseCode = connection.getResponseCode();
            if (responseInputStream == null) {
                responseInputStream = normalizeResponseInputStream(connection.getInputStream());
            }
            logResponseIfNeeded();
            return responseInput(responseInputStream);
        } catch (IOException exception) {
            OpenDoJaLog.warn(DesktopHttpConnection.class, "HTTP openInputStream failed " + requestSummary(), exception);
            throw translate(exception);
        }
    }

    @Override
    public DataInputStream openDataInputStream() throws IOException {
        return new DataInputStream(openInputStream());
    }

    @Override
    public OutputStream openOutputStream() throws IOException {
        try {
            ensureConnection();
            connection.setDoOutput(true);
            logRequestIfNeeded();
            OutputStream stream = maybeWrapOutputStream(connection.getOutputStream());
            return star ? starOutput(stream) : stream;
        } catch (IOException exception) {
            OpenDoJaLog.warn(DesktopHttpConnection.class, "HTTP openOutputStream failed " + requestSummary(), exception);
            throw translate(exception);
        }
    }

    @Override
    public DataOutputStream openDataOutputStream() throws IOException {
        return new DataOutputStream(openOutputStream());
    }

    @Override
    public String getURL() {
        return url.toString();
    }

    @Override
    public void setRequestMethod(String method) throws IOException {
        this.requestMethod = method;
        if (connection != null) {
            http(() -> { connection.setRequestMethod(method); return null; });
        }
    }

    @Override
    public void setRequestProperty(String key, String value) throws IOException {
        if (isUserAgentHeader(key)) {
            userAgentProvidedByGame = true;
        }
        http(() -> { ensureConnection(); return null; });
        connection.setRequestProperty(key, OpenDoJaIdentity.replaceDefaultUserIdToken(value));
    }

    @Override
    public int getResponseCode() throws IOException {
        try {
            ensureConnection();
            logRequestIfNeeded();
            int code = connection.getResponseCode();
            responseCode = code;
            logResponseIfNeeded(code);
            return code;
        } catch (IOException exception) {
            OpenDoJaLog.warn(DesktopHttpConnection.class, "HTTP getResponseCode failed " + requestSummary(), exception);
            throw translate(exception);
        }
    }

    @Override
    public String getResponseMessage() throws IOException {
        return http(() -> { ensureConnection(); return connection.getResponseMessage(); });
    }

    @Override
    public String getHeaderField(String name) {
        try {
            ensureConnection();
            return connection.getHeaderField(name);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public long getDate() {
        try {
            ensureConnection();
            return connection.getDate();
        } catch (IOException e) {
            return 0L;
        }
    }

    @Override
    public long getExpiration() {
        try {
            ensureConnection();
            return connection.getExpiration();
        } catch (IOException e) {
            return 0L;
        }
    }

    @Override
    public long getLastModified() {
        try {
            ensureConnection();
            return connection.getLastModified();
        } catch (IOException e) {
            return 0L;
        }
    }

    @Override
    public void setIfModifiedSince(long ifModifiedSince) {
        this.ifModifiedSince = ifModifiedSince;
        if (connection != null) {
            connection.setIfModifiedSince(ifModifiedSince);
        }
    }

    @Override
    public String getType() {
        try {
            ensureConnection();
            return connection.getContentType();
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public String getEncoding() {
        try {
            ensureConnection();
            return connection.getContentEncoding();
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public long getLength() {
        try {
            ensureConnection();
            return connection.getContentLengthLong();
        } catch (IOException e) {
            return -1L;
        }
    }

    private void ensureConnection() throws IOException {
        if (closed) throw new IOException("Connection closed");
        if (connection != null) {
            return;
        }
        URLConnection raw = url.openConnection();
        if (!(raw instanceof HttpURLConnection httpURLConnection)) {
            throw new IOException("Unsupported non-HTTP URL: " + url);
        }
        connection = httpURLConnection;
        connection.setRequestMethod(requestMethod);
        connection.setInstanceFollowRedirects(true);
        connection.setUseCaches(false);
        connection.setDoInput(mode != javax.microedition.io.Connector.WRITE);
        connection.setDoOutput(mode != javax.microedition.io.Connector.READ);
        if (timeouts) {
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);
        }
        if (ifModifiedSince > 0L) {
            connection.setIfModifiedSince(ifModifiedSince);
        }
        connection.setRequestProperty("Connection", "keep-alive");
        if (!userAgentProvidedByGame) {
            connection.setRequestProperty("User-Agent", star
                    ? STAR_USER_AGENT : DEFAULT_USER_AGENT);
        }
    }

    // Each connection retains its caller's profile, including later stream failures.
    // DoJa continues receiving the original host exceptions.
    private IOException translate(IOException failure) {
        if (!star || failure instanceof com.docomostar.io.ConnectionException) return failure;
        if (failure instanceof com.nttdocomo.io.ConnectionException doja) {
            return com.docomostar.io.ConnectionException.from(doja);
        }
        int status = com.docomostar.io.ConnectionException.UNDEFINED;
        if (failure instanceof SocketTimeoutException) status = com.docomostar.io.ConnectionException.TIMEOUT;
        else if (failure instanceof javax.net.ssl.SSLException) status = com.docomostar.io.ConnectionException.SSL_ERROR;
        else if (failure instanceof InterruptedIOException) status = com.docomostar.io.ConnectionException.SYSTEM_ABORT;
        else if (failure instanceof UnknownHostException || failure instanceof ConnectException || failure instanceof NoRouteToHostException)
            status = com.docomostar.io.ConnectionException.OUT_OF_SERVICE;
        else if (failure instanceof ProtocolException) status = com.docomostar.io.ConnectionException.ILLEGAL_STATE;
        else if (responseCode >= 400) status = com.docomostar.io.ConnectionException.HTTP_ERROR;
        var translated = new com.docomostar.io.ConnectionException(status, failure.getMessage());
        translated.initCause(failure);
        return translated;
    }

    @FunctionalInterface
    private interface IOOperation<T> { T run() throws IOException; }

    private <T> T http(IOOperation<T> operation) throws IOException {
        try { return operation.run(); }
        catch (IOException failure) { throw translate(failure); }
    }

    private InputStream responseInput(InputStream delegate) {
        return new FilterInputStream(delegate) {
            private boolean streamClosed;

            private <T> T readResponse(IOOperation<T> operation) throws IOException {
                return http(() -> {
                    if (streamClosed || closed) throw new IOException("Stream closed");
                    return operation.run();
                });
            }

            @Override public int read() throws IOException { return readResponse(in::read); }
            @Override public int read(byte[] b, int off, int len) throws IOException { return readResponse(() -> in.read(b, off, len)); }
            @Override public long skip(long n) throws IOException { return readResponse(() -> in.skip(n)); }
            @Override public int available() throws IOException { return readResponse(in::available); }
            @Override public synchronized void reset() throws IOException { readResponse(() -> { in.reset(); return null; }); }
            @Override public void close() { streamClosed = true; }
        };
    }

    private OutputStream starOutput(OutputStream delegate) {
        return new FilterOutputStream(delegate) {
            @Override public void write(int b) throws IOException { http(() -> { out.write(b); return null; }); }
            @Override public void write(byte[] b, int off, int len) throws IOException { http(() -> { out.write(b, off, len); return null; }); }
            @Override public void flush() throws IOException { http(() -> { out.flush(); return null; }); }
            @Override public void close() throws IOException { http(() -> { out.close(); return null; }); }
        };
    }

    private static boolean isUserAgentHeader(String key) {
        return key != null && "User-Agent".equalsIgnoreCase(key);
    }

    private static URL rewriteOutboundUrl(URL rawUrl) {
        try {
            return OpenDoJaIdentity.replaceDefaultUserIdToken(rewriteConfiguredHost(rawUrl));
        } catch (IOException exception) {
            return rawUrl;
        }
    }

    private static URL rewriteConfiguredHost(URL rawUrl) throws IOException {
        if (rawUrl == null) {
            return null;
        }
        String overrideHost = OpenDoJaLaunchArgs.get(OpenDoJaLaunchArgs.HTTP_OVERRIDE_DOMAIN, "").trim();
        if (overrideHost.isEmpty() || rawUrl.getHost() == null) {
            return rawUrl;
        }
        try {
            return new URI(
                    rawUrl.getProtocol(),
                    rawUrl.getUserInfo(),
                    overrideHost,
                    rawUrl.getPort(),
                    rawUrl.getPath(),
                    rawUrl.getQuery(),
                    rawUrl.getRef()).toURL();
        } catch (URISyntaxException exception) {
            throw new IOException("Could not rewrite outbound URL: " + rawUrl, exception);
        }
    }

    private static OutputStream maybeWrapOutputStream(OutputStream delegate) {
        if (OpenDoJaIdentity.defaultUserId().equals(OpenDoJaIdentity.userId())) {
            return delegate;
        }
        return new UserIdRewriteOutputStream(delegate);
    }

    private static InputStream normalizeResponseInputStream(InputStream delegate) {
        return new ReliableInputStream(delegate);
    }

    private void debug(java.util.function.Supplier<String> messageSupplier) {
        OpenDoJaLog.debug(DesktopHttpConnection.class, messageSupplier);
    }

    private void logRequestIfNeeded() {
        if (requestLogged) {
            return;
        }
        requestLogged = true;
        debug(() -> "HTTP request " + requestSummary());
    }

    private void logResponseIfNeeded() {
        if (responseLogged) {
            return;
        }
        int code;
        try {
            code = connection.getResponseCode();
        } catch (IOException exception) {
            code = -1;
        }
        logResponseIfNeeded(code);
    }

    private void logResponseIfNeeded(int code) {
        if (responseLogged) {
            return;
        }
        responseLogged = true;
        debug(() -> "HTTP response " + responseSummary(code));
    }

    private String requestSummary() {
        return "method=" + requestMethod
                + " url=" + url
                + " type=" + valueOrUnset(requestProperty("Content-Type"))
                + " length=" + valueOrUnset(requestProperty("Content-Length"));
    }

    private String requestProperty(String name) {
        if (connection == null) return null;
        try { return connection.getRequestProperty(name); }
        catch (IllegalStateException alreadyConnected) { return null; }
    }

    private String responseSummary() {
        int code;
        try {
            code = connection.getResponseCode();
        } catch (IOException exception) {
            code = -1;
        }
        return responseSummary(code);
    }

    private String responseSummary(int code) {
        String message;
        try {
            message = connection.getResponseMessage();
        } catch (IOException exception) {
            message = "<unavailable:" + exception.getClass().getSimpleName() + ">";
        }
        return requestSummary()
                + " code=" + code
                + " message=" + message
                + " type=" + connection.getContentType()
                + " encoding=" + connection.getContentEncoding()
                + " length=" + connection.getContentLengthLong();
    }

    private static String valueOrUnset(String value) {
        return value == null || value.isBlank() ? "<unset>" : value;
    }

    private static final class ReliableInputStream extends java.io.FilterInputStream {
        private ReliableInputStream(InputStream delegate) {
            super(delegate);
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (length == 0) {
                return 0;
            }
            // InputStream.read(byte[], int, int) may legally return fewer bytes than requested even when
            // more data will arrive later. Some DoJa titles treat one read as "the whole response", so we
            // keep reading until the caller's buffer is full or EOF while preserving the usual 0/-1 cases.
            int totalRead = 0;
            while (totalRead < length) {
                int count = super.read(buffer, offset + totalRead, length - totalRead);
                if (count < 0) {
                    return totalRead == 0 ? -1 : totalRead;
                }
                totalRead += count;
                if (count == 0) {
                    break;
                }
            }
            return totalRead;
        }
    }

    private static final class UserIdRewriteOutputStream extends FilterOutputStream {
        private final byte[] target = OpenDoJaIdentity.defaultUserId().getBytes(DoJaEncoding.DEFAULT_CHARSET);
        private final byte[] replacement = OpenDoJaIdentity.userId().getBytes(DoJaEncoding.DEFAULT_CHARSET);
        private final java.io.ByteArrayOutputStream pending = new java.io.ByteArrayOutputStream();
        private boolean closed;

        private UserIdRewriteOutputStream(OutputStream delegate) {
            super(delegate);
        }

        @Override
        public void write(int value) throws IOException {
            pending.write(value);
            drain(false);
        }

        @Override
        public void write(byte[] data, int offset, int length) throws IOException {
            pending.write(data, offset, length);
            drain(false);
        }

        @Override
        public void flush() throws IOException {
            drain(false);
            out.flush();
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            drain(true);
            out.flush();
            out.close();
        }

        private void drain(boolean finalFlush) throws IOException {
            byte[] buffered = pending.toByteArray();
            int keep = finalFlush ? 0 : Math.max(0, target.length - 1);
            int writeLength = Math.max(0, buffered.length - keep);
            if (writeLength > 0) {
                out.write(rewriteMatches(buffered, writeLength));
            }
            pending.reset();
            if (!finalFlush && keep > 0 && buffered.length > writeLength) {
                pending.write(buffered, writeLength, buffered.length - writeLength);
            }
        }

        private byte[] rewriteMatches(byte[] source, int length) {
            byte[] rewritten = java.util.Arrays.copyOf(source, length);
            int lastStart = length - target.length;
            for (int i = 0; i <= lastStart; i++) {
                if (!matchesAt(source, i)) {
                    continue;
                }
                System.arraycopy(replacement, 0, rewritten, i, target.length);
                i += target.length - 1;
            }
            return rewritten;
        }

        private boolean matchesAt(byte[] source, int offset) {
            for (int i = 0; i < target.length; i++) {
                if (source[offset + i] != target[i]) {
                    return false;
                }
            }
            return true;
        }
    }
}
