package com.nttdocomo.ui;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

public class ExifData {
    public static final int GPS_INFO_TAG = 34853;
    public static final int SUPPORT_GET = 1;
    public static final int SUPPORT_SET = 2;

    private final Map<String, Object> tags = new HashMap<>();

    public ExifData() {
    }

    public static int getSupportStatus(int tag, int fieldType) {
        return SUPPORT_GET | SUPPORT_SET;
    }

    public void setIntegerTag(int ifd, int tag, long[] value) {
        tags.put(key(ifd, tag), value);
    }

    public long[] getIntegerTag(int ifd, int tag) {
        return (long[]) tags.get(key(ifd, tag));
    }

    public void setRationalTag(int ifd, int tag, long[][] value) {
        tags.put(key(ifd, tag), value);
    }

    public long[][] getRationalTag(int ifd, int tag) {
        return (long[][]) tags.get(key(ifd, tag));
    }

    public void setAsciiTag(int ifd, int tag, String value) {
        tags.put(key(ifd, tag), value);
    }

    public String getAsciiTag(int ifd, int tag) {
        Object value = tags.get(key(ifd, tag));
        return value instanceof String string ? string : null;
    }

    public void setUndefinedTag(int ifd, int tag, byte[] value) {
        tags.put(key(ifd, tag), value);
    }

    public byte[] getUndefinedTag(int ifd, int tag) {
        return (byte[]) tags.get(key(ifd, tag));
    }

    public Enumeration<String> enumerateTags() {
        return Collections.enumeration(tags.keySet());
    }

    public com.nttdocomo.device.location.Location toLocation() {
        return null;
    }

    public void update(com.nttdocomo.device.location.Location location) {
    }

    private String key(int ifd, int tag) {
        return ifd + ":" + tag;
    }
}
