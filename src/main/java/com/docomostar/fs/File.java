package com.docomostar.fs;

import com.docomostar.io.FileEntity;
import java.io.IOException;

public class File {
    public static final int MODE_READ_ONLY = 0;
    public static final int MODE_WRITE_ONLY = 1;
    public static final int MODE_READ_WRITE = 2;
    private final Folder folder;
    private final com.nttdocomo.fs.File delegate;

    File(Folder folder, com.nttdocomo.fs.File delegate) { this.folder = folder; this.delegate = delegate; }
    /** Host integration for optional device services sharing the DoJa backend. */
    public final com.nttdocomo.fs.File hostFile() { return delegate; }
    public Folder getFolder() { return folder; }
    public AccessToken getAccessToken() { return folder.getAccessToken(); }
    public String getPath() { return delegate.getPath(); }
    public FileEntity open(int mode) throws IOException {
        try { return new FileEntity(delegate.open(mode)); }
        catch (IOException failure) { throw _StorageExceptions.translate(failure); }
    }
    public void delete() throws IOException {
        try { delegate.delete(); }
        catch (IOException failure) { throw _StorageExceptions.translate(failure); }
    }
    public long getLength() throws IOException {
        try { return delegate.getLength(); }
        catch (IOException failure) { throw _StorageExceptions.translate(failure); }
    }
    public long getLastModified() throws IOException {
        try { return delegate.getLastModified(); }
        catch (IOException failure) { throw _StorageExceptions.translate(failure); }
    }
}
