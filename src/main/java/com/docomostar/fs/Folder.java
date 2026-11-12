package com.docomostar.fs;

import com.docomostar.device.StorageDevice;
import java.io.IOException;

public class Folder {
    private final StorageDevice device;
    private final AccessToken token;
    private final com.nttdocomo.fs.Folder delegate;

    protected Folder(StorageDevice device, AccessToken token, String path) {
        this(device, token, new HostFolder(device.hostDevice(), StarAccessToken.unwrap(token), path));
    }

    /** Internal bridge from the host's SD-Binding namespace. */
    public Folder(StorageDevice device, AccessToken token, com.nttdocomo.fs.Folder delegate) {
        this.device = device;
        this.token = token;
        this.delegate = delegate;
    }

    public StorageDevice getStorageDevice() { return device; }
    public AccessToken getAccessToken() { return token; }
    public String getPath() { return delegate.getPath(); }
    public long getFreeSize() throws IOException { return delegate.getFreeSize(); }
    public boolean isFileAttributeSupported(Class<?> attribute) {
        if (attribute == null) throw new NullPointerException("clazz");
        if (!FileAttribute.class.isAssignableFrom(attribute)) throw new IllegalArgumentException("Not a STAR file attribute");
        return delegate.isFileAttributeSupported(attribute);
    }
    public File createFile(String name) throws IOException { return createFile(name, null); }
    public File createFile(String name, FileAttribute[] attributes) throws IOException {
        try { return new File(this, delegate.createFile(name, attributes)); }
        catch (IOException failure) { throw _StorageExceptions.translate(failure); }
    }
    public File getFile(String name) throws IOException {
        try { return new File(this, delegate.getFile(name)); }
        catch (IOException failure) { throw _StorageExceptions.translate(failure); }
    }
    public File[] getFiles() throws IOException {
        try {
            var files = delegate.getFiles();
            File[] result = new File[files.length];
            for (int i = 0; i < files.length; i++) result[i] = new File(this, files[i]);
            return result;
        } catch (IOException failure) { throw _StorageExceptions.translate(failure); }
    }

    private static final class HostFolder extends com.nttdocomo.fs.Folder {
        HostFolder(com.nttdocomo.device.StorageDevice device, com.nttdocomo.fs.AccessToken token, String path) {
            super(device, token, path);
        }
    }
}
