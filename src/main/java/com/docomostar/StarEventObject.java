package com.docomostar;

public class StarEventObject {
    public static final int STAR_STATECHANGE_CLAM_OPEN = 1;
    public static final int STAR_STATECHANGE_CLAM_CLOSE = 2;
    public static final int STAR_FACE_STATECHANGE_MINI_FOCUSED = 3;
    public static final int STAR_FACE_STATECHANGE_MINI_UNFOCUSED = 4;
    public static final int STAR_FACE_STATECHANGE_MINI_SELECTED = 5;
    public static final int STAR_CLOCK_UPDATED = 6;
    public static final int STAR_FRAME_CHANGED = 7;
    public static final int STAR_MESSAGE_FOLDER_CHANGED = 8;
    public static final int STAR_CALLED_BY_DTV = 9;
    public static final int STAR_INVITED = 10;
    public static final int STAR_FELICA_PUSHED = 11;
    public static final int STAR_FELICA_ADHOC_REQUEST_RECEIVED = 12;
    public static final int STAR_AVPLAYER_NOTIFIED = 13;
    public static final int STAR_AUDIO_OUTPUT_PATH_CHANGED = 14;
    public static final int STAR_WALKING_NAVIGATION_STOPPED = 15;
    public static final int STAR_BLUETOOTH_OPERATION_RECEIVED = 16;
    public static final int STAR_BLUETOOTH_CONNECT_REQUEST_RECEIVED = 17;

    private final int type;

    public StarEventObject(int type) {
        this.type = type;
    }

    public int getType() {
        return type;
    }

    static void checkType(int type) {
        if (type < STAR_STATECHANGE_CLAM_OPEN || type > STAR_BLUETOOTH_CONNECT_REQUEST_RECEIVED) {
            throw new IllegalArgumentException("Unknown STAR event type: " + type);
        }
    }
}
