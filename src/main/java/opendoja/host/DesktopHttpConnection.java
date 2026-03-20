package opendoja.host;

import com.nttdocomo.io.HttpConnection;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

public final class DesktopHttpConnection implements HttpConnection {
    private final URL url;
    private final int mode;
    private final boolean timeouts;
    private HttpURLConnection connection;
    private String requestMethod = GET;
    private long ifModifiedSince = 0L;

    public DesktopHttpConnection(URL url, int mode, boolean timeouts) {
        this.url = url;
        this.mode = mode;
        this.timeouts = timeouts;
    }

    @Override
    public void connect() throws IOException {
        ensureConnection();
        connection.connect();
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
        return connection.getInputStream();
    }

    @Override
    public DataInputStream openDataInputStream() throws IOException {
        return new DataInputStream(openInputStream());
    }

    @Override
    public OutputStream openOutputStream() throws IOException {
        ensureConnection();
        connection.setDoOutput(true);
        return connection.getOutputStream();
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
        ensureConnection();
        connection.setRequestProperty(key, value);
    }

    @Override
    public int getResponseCode() throws IOException {
        ensureConnection();
        return connection.getResponseCode();
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
    }
}
