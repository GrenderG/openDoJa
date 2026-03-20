package com.nttdocomo.ui;

import com.nttdocomo.system.StoreException;

import java.awt.Toolkit;
import java.util.HashMap;
import java.util.Map;

public class PhoneSystem {
    public static final int ATTR_BACKLIGHT_OFF = 0;
    public static final int ATTR_BACKLIGHT_ON = 1;
    public static final int ATTR_FOLDING_CLOSE = 0;
    public static final int ATTR_FOLDING_OPEN = 1;
    public static final int ATTR_MAIL_AT_CENTER = 2;
    public static final int ATTR_MAIL_NONE = 0;
    public static final int ATTR_MAIL_RECEIVED = 1;
    public static final int ATTR_MESSAGE_AT_CENTER = 2;
    public static final int ATTR_MESSAGE_NONE = 0;
    public static final int ATTR_MESSAGE_RECEIVED = 1;
    public static final int ATTR_VIBRATOR_OFF = 0;
    public static final int ATTR_VIBRATOR_ON = 1;
    public static final int ATTR_BATTERY_PARTIAL = 0;
    public static final int ATTR_BATTERY_FULL = 1;
    public static final int ATTR_BATTERY_CHARGING = 2;
    public static final int ATTR_SERVICEAREA_OUTSIDE = 0;
    public static final int ATTR_SERVICEAREA_INSIDE = 1;
    public static final int ATTR_MANNER_OFF = 0;
    public static final int ATTR_MANNER_ON = 1;
    public static final int ATTR_SCREEN_INVISIBLE = 0;
    public static final int ATTR_SCREEN_VISIBLE = 1;
    public static final int ATTR_SURROUND_OFF = 0;
    public static final int ATTR_SURROUND_ON = 1;
    public static final int ATTR_AREAINFO_FOMA = 0;
    public static final int ATTR_AREAINFO_HSDPA = 1;
    public static final int ATTR_AREAINFO_OUTSIDE = 2;
    public static final int ATTR_AREAINFO_ROAMINGOUT = 3;
    public static final int ATTR_AREAINFO_SELFMODE = 4;
    public static final int ATTR_AREAINFO_COMMUNICATING = 5;
    public static final int ATTR_AREAINFO_UNKNOWN = 99;
    public static final int DEV_BACKLIGHT = 0;
    public static final int DEV_VIBRATOR = 1;
    public static final int DEV_FOLDING = 2;
    public static final int DEV_MAILBOX = 3;
    public static final int DEV_MESSAGEBOX = 4;
    public static final int DEV_BATTERY = 5;
    public static final int DEV_SERVICEAREA = 6;
    public static final int DEV_MANNER = 7;
    public static final int DEV_KEYPAD = 8;
    public static final int DEV_SCREEN_VISIBLE = 9;
    public static final int DEV_AUDIO_SURROUND = 10;
    public static final int DEV_AREAINFO = 11;
    public static final int MIN_VENDOR_ATTR = 64;
    public static final int MAX_VENDOR_ATTR = 127;
    public static final int MAX_OPTION_ATTR = 255;
    public static final int MIN_OPTION_ATTR = 128;
    public static final int SOUND_INFO = 0;
    public static final int SOUND_WARNING = 1;
    public static final int SOUND_ERROR = 2;
    public static final int SOUND_ALARM = 3;
    public static final int SOUND_CONFIRM = 4;
    public static final int THEME_STANDBY = 0;
    public static final int THEME_CALL_OUT = 1;
    public static final int THEME_CALL_IN = 2;
    public static final int THEME_MESSAGE_SEND = 3;
    public static final int THEME_MESSAGE_RECEIVE = 4;
    public static final int THEME_AV_CALL_IN = 5;
    public static final int THEME_CHAT_RECEIVED = 6;
    public static final int THEME_AV_CALLING = 7;

    private static final Map<Integer, Integer> ATTRIBUTES = new HashMap<>();

    static {
        ATTRIBUTES.put(DEV_BACKLIGHT, ATTR_BACKLIGHT_ON);
        ATTRIBUTES.put(DEV_VIBRATOR, ATTR_VIBRATOR_OFF);
        ATTRIBUTES.put(DEV_FOLDING, ATTR_FOLDING_OPEN);
        ATTRIBUTES.put(DEV_MAILBOX, ATTR_MAIL_NONE);
        ATTRIBUTES.put(DEV_MESSAGEBOX, ATTR_MESSAGE_NONE);
        ATTRIBUTES.put(DEV_BATTERY, ATTR_BATTERY_FULL);
        ATTRIBUTES.put(DEV_SERVICEAREA, ATTR_SERVICEAREA_INSIDE);
        ATTRIBUTES.put(DEV_MANNER, ATTR_MANNER_OFF);
        ATTRIBUTES.put(DEV_SCREEN_VISIBLE, ATTR_SCREEN_VISIBLE);
        ATTRIBUTES.put(DEV_AUDIO_SURROUND, ATTR_SURROUND_OFF);
        ATTRIBUTES.put(DEV_AREAINFO, ATTR_AREAINFO_FOMA);
    }

    protected PhoneSystem() {
    }

    public static void setAttribute(int device, int attribute) {
        ATTRIBUTES.put(device, attribute);
    }

    public static int getAttribute(int device) {
        return ATTRIBUTES.getOrDefault(device, 0);
    }

    public static boolean isAvailable(int device) {
        return true;
    }

    public static void playSound(int sound) {
        Toolkit.getDefaultToolkit().beep();
    }

    public static void setImageTheme(int theme, int value) throws StoreException {
    }

    public static void setSoundTheme(int theme, int value) throws StoreException {
    }

    public static void setMovieTheme(int theme, int value) throws StoreException {
    }

    public static void setMenuIcons(int[] menuIds, int[] iconIds) throws StoreException {
    }
}
