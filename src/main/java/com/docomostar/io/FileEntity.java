package com.docomostar.io;

import java.io.*;

/** STAR view retaining the host file entity and accessor lifetime rules. */
public class FileEntity implements AutoCloseable {
    private final com.nttdocomo.io.FileEntity delegate;

    public FileEntity(com.nttdocomo.io.FileEntity delegate) { this.delegate = delegate; }
    public void close() throws IOException { delegate.close(); }
    public void setBufferSize(int bufferSize) throws IOException { delegate.setBufferSize(bufferSize); }
    public int getBufferSize() throws IOException { return delegate.getBufferSize(); }
    public FileDataInput openDataInput() throws IOException { return new FileDataInput(delegate.openDataInput()); }
    public FileDataOutput openDataOutput() throws IOException { return new FileDataOutput(delegate.openDataOutput()); }
    public InputStream openInputStream() throws IOException { return delegate.openInputStream(); }
    public OutputStream openOutputStream() throws IOException { return delegate.openOutputStream(); }
}
