package com.docomostar.io;

import java.io.*;

/** STAR view retaining the host file entity and accessor lifetime rules. */
public class FileDataOutput implements java.io.DataOutput, RandomAccessible, AutoCloseable {
    private final com.nttdocomo.io.FileDataOutput delegate;

    FileDataOutput(com.nttdocomo.io.FileDataOutput delegate) { this.delegate = delegate; }
    public long getSize() throws IOException { return delegate.getSize(); }
    public long getPosition() throws IOException { return delegate.getPosition(); }
    public void setPosition(long position) throws IOException { delegate.setPosition(position); }
    public void setPositionRelative(long position) throws IOException { delegate.setPositionRelative(position); }
    public void truncate(long size) throws IOException { delegate.truncate(size); }
    public void flush() throws IOException { delegate.flush(); }
    public void close() throws IOException { delegate.close(); }
    public void writeString(String s) throws IOException { delegate.writeString(s); }
    public void writeString(String s, int bytes) throws IOException { delegate.writeString(s, bytes); }
    public void write(byte[] b, int off, int len) throws IOException { delegate.write(b, off, len); }
    public void write(byte[] b) throws IOException { delegate.write(b); }
    public void write(int b) throws IOException { delegate.write(b); }
    public void writeBoolean(boolean v) throws IOException { delegate.writeBoolean(v); }
    public void writeByte(int v) throws IOException { delegate.writeByte(v); }
    public void writeBytes(String s) throws IOException { delegate.writeBytes(s); }
    public void writeChar(int v) throws IOException { delegate.writeChar(v); }
    public void writeChars(String s) throws IOException { delegate.writeChars(s); }
    public void writeDouble(double v) throws IOException { delegate.writeDouble(v); }
    public void writeFloat(float v) throws IOException { delegate.writeFloat(v); }
    public void writeInt(int v) throws IOException { delegate.writeInt(v); }
    public void writeLong(long v) throws IOException { delegate.writeLong(v); }
    public void writeShort(int v) throws IOException { delegate.writeShort(v); }
    public void writeUTF(String s) throws IOException { delegate.writeUTF(s); }
}
