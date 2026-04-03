package com.nttdocomo.device.felica;

/**
 * Base class of purse-related FeliCa data.
 */
public abstract class PurseData extends FelicaData {
    private static final int MAX_EXEC_ID = 0x10000;
    private int execID;

    PurseData(int type, int execID) {
        super(type);
        setExecID(execID);
    }

    /**
     * Returns the execution ID.
     * It is used by purse services.
     *
     * @return the execution ID
     */
    public int getExecID() {
        return execID;
    }

    /**
     * Sets the specified value as the execution ID.
     * It is used by purse services.
     *
     * @param execID the execution ID; values from {@code 0} through
     *               {@code 2^16 - 1} can be specified
     * @throws IllegalArgumentException if {@code execID} is negative or is
     *         greater than or equal to {@code 2^16}
     */
    public void setExecID(int execID) {
        if (execID < 0 || execID >= MAX_EXEC_ID) {
            throw new IllegalArgumentException("execID");
        }
        this.execID = execID;
    }

    /**
     * Performs validate Unsigned Int32.
     */
    protected static void validateUnsignedInt32(long value, String label) {
        if (value < 0L || value > 0xFFFF_FFFFL) {
            throw new IllegalArgumentException(label);
        }
    }
}
