package com.docomostar.system;

/** Uses the same desktop Data Box folders and selection behavior as DoJa. */
public final class DataBoxFolder {
    public static final int FOLDER_MY_PICTURE = com.nttdocomo.system.DataBoxFolder.FOLDER_MY_PICTURE;
    private DataBoxFolder() { }
    public static int addEntry(int folder, String name) throws InterruptedOperationException {
        return com.docomostar.device._DeviceExceptions.interruptible(
                () -> com.nttdocomo.system.DataBoxFolder.addEntry(folder, name));
    }
    public static int selectEntryId(int folder) throws InterruptedOperationException {
        return com.docomostar.device._DeviceExceptions.interruptible(
                () -> com.nttdocomo.system.DataBoxFolder.selectEntryId(folder));
    }
}
