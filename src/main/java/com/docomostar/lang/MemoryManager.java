package com.docomostar.lang;

/** STAR view of the host's memory partitions. */
public final class MemoryManager {
    public static final int JAVA_HEAP = 0;
    private static final MemoryManager INSTANCE = new MemoryManager();
    private final com.nttdocomo.lang.MemoryManager delegate = com.nttdocomo.lang.MemoryManager.getMemoryManager();
    private MemoryManager() { }
    public static MemoryManager getMemoryManager() { return INSTANCE; }
    public long[] totalMemory() { return delegate.totalMemory(); }
    public long[] freeMemory() { return delegate.freeMemory(); }
    public long[] maxContiguousMemory() { return delegate.maxContiguousMemory(); }
    public int getNumPartitions() { return delegate.getNumPartitions(); }
}
