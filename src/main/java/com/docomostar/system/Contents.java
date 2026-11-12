package com.docomostar.system;

import opendoja.host.OpenDoJaLog;

import java.util.Hashtable;

public final class Contents {
    private Contents() {
    }

    public static void sendMyMenuRequest(Hashtable parameters, boolean showDialog)
            throws ContentsException, InterruptedOperationException {
        OpenDoJaLog.info(Contents.class, () ->
                "Simulated Star Contents.sendMyMenuRequest params=" + parameters + " showDialog=" + showDialog);
    }
}
