package com.docomostar.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface SPPConnection extends BTConnection {
    InputStream openInputStream() throws IOException;
    OutputStream openOutputStream() throws IOException;
}
