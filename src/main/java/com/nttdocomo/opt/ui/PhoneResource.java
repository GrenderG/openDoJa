package com.nttdocomo.opt.ui;

import opendoja.host.DoJaEncoding;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages optional phone resource data.
 */
public class PhoneResource {
    /**
     * Constant for informationdisplay data title.
     */
    public static final int INFORMATIONDISPLAY_DATA_TITLE = 0;
    private static final Map<Integer, InformationDisplay> DISPLAYS = new ConcurrentHashMap<>();

    /**
     * Applications cannot create this object directly.
     */
    protected PhoneResource() {
    }

    /**
     * Registers information-display data.
     *
     * @param targetId the destination ID
     * @param title the title string
     * @param expire the expiration time
     * @param repeat the repeat count
     * @param patterns the dot patterns
     */
    public static void setInformationDisplay(int targetId, String title, long expire, int repeat, int[][] patterns) {
        DISPLAYS.put(targetId, new InformationDisplay(title == null ? "" : title, expire, repeat, clonePatterns(patterns)));
    }

    /**
     * Returns the requested information-display data.
     *
     * @param targetId the source ID
     * @param dataType the data type to retrieve
     * @return the stored data, or {@code null} if it does not exist
     */
    public static byte[] getInformationDisplayData(int targetId, int dataType) {
        InformationDisplay display = DISPLAYS.get(targetId);
        if (display == null) {
            return null;
        }
        if (dataType == INFORMATIONDISPLAY_DATA_TITLE) {
            return display.title.getBytes(DoJaEncoding.DEFAULT_CHARSET);
        }
        return null;
    }

    private static int[][] clonePatterns(int[][] patterns) {
        if (patterns == null) {
            return new int[0][];
        }
        int[][] copy = new int[patterns.length][];
        for (int i = 0; i < patterns.length; i++) {
            copy[i] = patterns[i] == null ? null : patterns[i].clone();
        }
        return copy;
    }

    private record InformationDisplay(String title, long expire, int repeat, int[][] patterns) {
    }
}
