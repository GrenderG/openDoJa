package com.nttdocomo.lang;

public final class MemoryManager {
    public static final int JAVA_HEAP = 0;
    private static final MemoryManager INSTANCE = new MemoryManager();

    private MemoryManager() {
    }

    public static MemoryManager getMemoryManager() {
        return INSTANCE;
    }

    public long[] totalMemory() {
        return new long[]{Runtime.getRuntime().totalMemory()};
    }

    public long[] freeMemory() {
        return new long[]{Runtime.getRuntime().freeMemory()};
    }

    public long[] maxContiguousMemory() {
        return new long[]{Runtime.getRuntime().maxMemory()};
    }

    public int getNumPartitions() {
        return 1;
    }
}
