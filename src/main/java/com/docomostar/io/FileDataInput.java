package com.docomostar.io;

import java.io.*;

/** STAR view retaining the host file entity and accessor lifetime rules. */
public class FileDataInput implements java.io.DataInput, RandomAccessible, AutoCloseable {
    private final com.nttdocomo.io.FileDataInput delegate;

    FileDataInput(com.nttdocomo.io.FileDataInput delegate) { this.delegate = delegate; }
    public long getSize() throws IOException { return delegate.getSize(); }
    public long getPosition() throws IOException { return delegate.getPosition(); }
    public void setPosition(long position) throws IOException { delegate.setPosition(position); }
    public void setPositionRelative(long position) throws IOException { delegate.setPositionRelative(position); }
    public void close() throws IOException { delegate.close(); }
    public String readString() throws IOException { return delegate.readString(); }
    public String readString(int bytes) throws IOException { return delegate.readString(bytes); }
    public boolean readBoolean() throws IOException { return delegate.readBoolean(); }
    public byte readByte() throws IOException { return delegate.readByte(); }
    public char readChar() throws IOException { return delegate.readChar(); }
    public double readDouble() throws IOException { return delegate.readDouble(); }
    public float readFloat() throws IOException { return delegate.readFloat(); }
    public void readFully(byte[] b, int off, int len) throws IOException { delegate.readFully(b, off, len); }
    public void readFully(byte[] b) throws IOException { delegate.readFully(b); }
    public int readInt() throws IOException { return delegate.readInt(); }
    public String readLine() throws IOException { return delegate.readLine(); }
    public long readLong() throws IOException { return delegate.readLong(); }
    public short readShort() throws IOException { return delegate.readShort(); }
    public int readUnsignedByte() throws IOException { return delegate.readUnsignedByte(); }
    public int readUnsignedShort() throws IOException { return delegate.readUnsignedShort(); }
    public String readUTF() throws IOException { return delegate.readUTF(); }
    public int skipBytes(int n) throws IOException { return delegate.skipBytes(n); }
}
