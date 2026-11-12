package com.docomostar.device;

import com.docomostar.fs.*;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StorageDevice {
    public static final String CATEGORY_HARDWARE = com.nttdocomo.device.StorageDevice.CATEGORY_HARDWARE;
    public static final String CAPABILITY_SD = com.nttdocomo.device.StorageDevice.CAPABILITY_SD;
    public static final String CAPABILITY_MINISD = com.nttdocomo.device.StorageDevice.CAPABILITY_MINISD;
    public static final String CATEGORY_FILESYSTEM = com.nttdocomo.device.StorageDevice.CATEGORY_FILESYSTEM;
    public static final String CAPABILITY_FAT12 = com.nttdocomo.device.StorageDevice.CAPABILITY_FAT12;
    public static final String CAPABILITY_FAT16 = com.nttdocomo.device.StorageDevice.CAPABILITY_FAT16;
    public static final String CAPABILITY_FAT32 = com.nttdocomo.device.StorageDevice.CAPABILITY_FAT32;
    public static final String CAPABILITY_FAT_LONG_NAME = com.nttdocomo.device.StorageDevice.CAPABILITY_FAT_LONG_NAME;
    public static final String CATEGORY_ENCRYPTION = com.nttdocomo.device.StorageDevice.CATEGORY_ENCRYPTION;
    public static final String CAPABILITY_SD_BINDING = com.nttdocomo.device.StorageDevice.CAPABILITY_SD_BINDING;
    public static final String CAPABILITY_EXFAT = "exFAT";
    private static final Map<String, StorageDevice> DEVICES = new ConcurrentHashMap<>();
    private final com.nttdocomo.device.StorageDevice delegate;
    private StorageDevice(com.nttdocomo.device.StorageDevice delegate) { this.delegate = delegate; }
    public static StorageDevice getInstance(String name) {
        var host = com.nttdocomo.device.StorageDevice.getInstance(name);
        return DEVICES.computeIfAbsent(name, ignored -> new StorageDevice(host));
    }
    public com.nttdocomo.device.StorageDevice hostDevice() { return delegate; }
    public String getDeviceName() { return delegate.getDeviceName(); }
    public String getPrintName() { return delegate.getPrintName(); }
    public boolean isRemovable() { return delegate.isRemovable(); }
    public boolean isAccessible() { return delegate.isAccessible(); }
    public boolean isReadable() { return delegate.isReadable(); }
    public boolean isWritable() { return delegate.isWritable(); }
    public String[] getCapability(String category) throws IOException { return delegate.getCapability(category); }
    public String getMediaId() throws IOException { return delegate.getMediaId(); }
    public Folder getFolder(AccessToken token) throws IOException {
        try { return new Folder(this, token, delegate.getFolder(StarAccessToken.unwrap(token))); }
        catch (IOException failure) { throw _StorageExceptions.translate(failure); }
    }
}
