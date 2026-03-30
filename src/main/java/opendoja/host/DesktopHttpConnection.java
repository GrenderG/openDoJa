package opendoja.host;

import com.nttdocomo.io.HttpConnection;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;

public final class DesktopHttpConnection implements HttpConnection {
    private static final String DEFAULT_USER_AGENT = "DoCoMo/2.0 DOJA_INET_CLIENT(c100;TJ)";

    private final URL url;
    private final int mode;
    private final boolean timeouts;
    private HttpURLConnection connection;
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
        ensureConnection();
        logRequestIfNeeded();
        try {
            connection.connect();
        } catch (IOException exception) {
            OpenDoJaLog.warn(DesktopHttpConnection.class, "HTTP connect failed " + requestSummary(), exception);
            throw exception;
        }
    }

    @Override
    public void close() throws IOException {
        if (connection != null) {
            connection.disconnect();
        }
    }

    @Override
    public InputStream openInputStream() throws IOException {
        ensureConnection();
        logRequestIfNeeded();
        try {
            InputStream stream = connection.getInputStream();
            logResponseIfNeeded();
            return stream;
        } catch (IOException exception) {
            OpenDoJaLog.warn(DesktopHttpConnection.class, "HTTP openInputStream failed " + requestSummary(), exception);
            throw exception;
        }
    }

    @Override
    public DataInputStream openDataInputStream() throws IOException {
        return new DataInputStream(openInputStream());
    }

    @Override
    public OutputStream openOutputStream() throws IOException {
        ensureConnection();
        connection.setDoOutput(true);
        logRequestIfNeeded();
        try {
            return maybeWrapOutputStream(connection.getOutputStream());
        } catch (IOException exception) {
            OpenDoJaLog.warn(DesktopHttpConnection.class, "HTTP openOutputStream failed " + requestSummary(), exception);
            throw exception;
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
            connection.setRequestMethod(method);
        }
    }

    @Override
    public void setRequestProperty(String key, String value) throws IOException {
        if (isUserAgentHeader(key)) {
            userAgentProvidedByGame = true;
        }
        ensureConnection();
        connection.setRequestProperty(key, OpenDoJaIdentity.replaceDefaultUserIdToken(value));
    }

    @Override
    public int getResponseCode() throws IOException {
        ensureConnection();
        logRequestIfNeeded();
        try {
            int code = connection.getResponseCode();
            logResponseIfNeeded(code);
            return code;
        } catch (IOException exception) {
            OpenDoJaLog.warn(DesktopHttpConnection.class, "HTTP getResponseCode failed " + requestSummary(), exception);
            throw exception;
        }
    }

    @Override
    public String getResponseMessage() throws IOException {
        ensureConnection();
        return connection.getResponseMessage();
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
            connection.setRequestProperty("User-Agent", DEFAULT_USER_AGENT);
        }
    }

    private static boolean isUserAgentHeader(String key) {
        return key != null && "User-Agent".equalsIgnoreCase(key);
    }

    private static URL rewriteOutboundUrl(URL rawUrl) {
        try {
            return OpenDoJaIdentity.replaceDefaultUserIdToken(rawUrl);
        } catch (IOException exception) {
            return rawUrl;
        }
    }

    private static OutputStream maybeWrapOutputStream(OutputStream delegate) {
        if (OpenDoJaIdentity.defaultUserId().equals(OpenDoJaIdentity.userId())) {
            return delegate;
        }
        return new UserIdRewriteOutputStream(delegate);
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
                + " type=" + valueOrUnset(connection.getRequestProperty("Content-Type"))
                + " length=" + valueOrUnset(connection.getRequestProperty("Content-Length"));
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

    private static final class UserIdRewriteOutputStream extends FilterOutputStream {
        private final byte[] target = OpenDoJaIdentity.defaultUserId().getBytes(StandardCharsets.US_ASCII);
        private final byte[] replacement = OpenDoJaIdentity.userId().getBytes(StandardCharsets.US_ASCII);
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
