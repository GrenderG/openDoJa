package com.nttdocomo.opt.ui;

import com.nttdocomo.ui.PhoneSystem;
import opendoja.host.DoJaRuntime;

/**
 * Defines additional optional device attributes.
 */
public class PhoneSystem2 extends PhoneSystem {
    /**
     * Applications cannot create this object directly.
     */
    protected PhoneSystem2() {
    }

    /**
     * Device attribute identifier for indicator background.
     */
    public static final int DEV_INDICATOR_BACKGROUND = 128;
    /**
     * Device attribute identifier for melody volume.
     */
    public static final int DEV_MELODY_VOLUME = 129;
    /**
     * Device attribute identifier for se volume.
     */
    public static final int DEV_SE_VOLUME = 130;
    /**
     * Device attribute identifier for illumination.
     */
    public static final int DEV_ILLUMINATION = 133;
    /**
     * Device attribute identifier for memo led.
     */
    public static final int DEV_MEMO_LED = 134;
    /**
     * Device attribute identifier for key slant.
     */
    public static final int DEV_KEY_SLANT = 135;
    /**
     * Device attribute identifier for display brightness.
     */
    public static final int DEV_DISPLAY_BRIGHTNESS = 136;
    /**
     * Device attribute identifier for subdisplay brightness.
     */
    public static final int DEV_SUBDISPLAY_BRIGHTNESS = 137;
    /**
     * Device attribute identifier for display contrast.
     */
    public static final int DEV_DISPLAY_CONTRAST = 138;
    /**
     * Device attribute identifier for subdisplay contrast.
     */
    public static final int DEV_SUBDISPLAY_CONTRAST = 139;
    /**
     * Device attribute identifier for allocatable java memory.
     */
    public static final int DEV_ALLOCATABLE_JAVA_MEMORY = 140;
    /**
     * Device attribute identifier for display style.
     */
    public static final int DEV_DISPLAY_STYLE = 142;
    /**
     * Attribute value indicating volume min.
     */
    public static final int ATTR_VOLUME_MIN = 0;
    /**
     * Attribute value indicating volume max.
     */
    public static final int ATTR_VOLUME_MAX = 100;
    /**
     * Attribute value indicating illumination off.
     */
    public static final int ATTR_ILLUMINATION_OFF = 0;
    /**
     * Attribute value indicating illumination white.
     */
    public static final int ATTR_ILLUMINATION_WHITE = 1;
    /**
     * Attribute value indicating illumination orange.
     */
    public static final int ATTR_ILLUMINATION_ORANGE = 2;
    /**
     * Attribute value indicating illumination yellow.
     */
    public static final int ATTR_ILLUMINATION_YELLOW = 3;
    /**
     * Attribute value indicating illumination green.
     */
    public static final int ATTR_ILLUMINATION_GREEN = 4;
    /**
     * Attribute value indicating illumination skyblue.
     */
    public static final int ATTR_ILLUMINATION_SKYBLUE = 5;
    /**
     * Attribute value indicating illumination blue.
     */
    public static final int ATTR_ILLUMINATION_BLUE = 6;
    /**
     * Attribute value indicating illumination violet.
     */
    public static final int ATTR_ILLUMINATION_VIOLET = 7;
    /**
     * Attribute value indicating illumination rainbow.
     */
    public static final int ATTR_ILLUMINATION_RAINBOW = 8;
    /**
     * Attribute value indicating illumination gradually.
     */
    public static final int ATTR_ILLUMINATION_GRADUALLY = 0x01000000;
    /**
     * Attribute value indicating memo led off.
     */
    public static final int ATTR_MEMO_LED_OFF = 0;
    /**
     * Attribute value indicating memo led on.
     */
    public static final int ATTR_MEMO_LED_ON = -1;
    /**
     * Attribute value indicating key slant off.
     */
    public static final int ATTR_KEY_SLANT_OFF = 0;
    /**
     * Attribute value indicating key slant on.
     */
    public static final int ATTR_KEY_SLANT_ON = 1;
    /**
     * Attribute value indicating brightness min.
     */
    public static final int ATTR_BRIGHTNESS_MIN = 0;
    /**
     * Attribute value indicating brightness max.
     */
    public static final int ATTR_BRIGHTNESS_MAX = 255;
    /**
     * Attribute value indicating contrast min.
     */
    public static final int ATTR_CONTRAST_MIN = 0;
    /**
     * Attribute value indicating contrast max.
     */
    public static final int ATTR_CONTRAST_MAX = 255;
    /**
     * Attribute value indicating display style vertical.
     */
    public static final int ATTR_DISPLAY_STYLE_VERTICAL = 0;
    /**
     * Attribute value indicating display style horizontal right.
     */
    public static final int ATTR_DISPLAY_STYLE_HORIZONTAL_RIGHT = 1;
    /**
     * Attribute value indicating display style horizontal left.
     */
    public static final int ATTR_DISPLAY_STYLE_HORIZONTAL_LEFT = 2;
    /**
     * Attribute value indicating display style reverse.
     */
    public static final int ATTR_DISPLAY_STYLE_REVERSE = 3;

    static {
        resetRuntimeDefaults();
    }

    /**
     * Resets runtime Defaults.
     */
    public static void resetRuntimeDefaults() {
        PhoneSystem.setAttribute(DEV_MELODY_VOLUME, ATTR_VOLUME_MAX);
        PhoneSystem.setAttribute(DEV_SE_VOLUME, ATTR_VOLUME_MAX);
        PhoneSystem.setAttribute(DEV_ILLUMINATION, ATTR_ILLUMINATION_OFF);
        PhoneSystem.setAttribute(DEV_MEMO_LED, ATTR_MEMO_LED_OFF);
        PhoneSystem.setAttribute(DEV_KEY_SLANT, ATTR_KEY_SLANT_OFF);
        PhoneSystem.setAttribute(DEV_DISPLAY_BRIGHTNESS, ATTR_BRIGHTNESS_MAX);
        PhoneSystem.setAttribute(DEV_SUBDISPLAY_BRIGHTNESS, ATTR_BRIGHTNESS_MAX);
        PhoneSystem.setAttribute(DEV_DISPLAY_CONTRAST, ATTR_CONTRAST_MAX);
        PhoneSystem.setAttribute(DEV_SUBDISPLAY_CONTRAST, ATTR_CONTRAST_MAX);
        PhoneSystem.setAttribute(DEV_DISPLAY_STYLE, defaultDisplayStyle());
        PhoneSystem.setAttribute(DEV_ALLOCATABLE_JAVA_MEMORY,
                (int) java.lang.Math.min(Integer.MAX_VALUE, Runtime.getRuntime().maxMemory()));
    }

    private static int defaultDisplayStyle() {
        DoJaRuntime runtime = DoJaRuntime.current();
        if (runtime != null && runtime.displayWidth() > runtime.displayHeight()) {
            return ATTR_DISPLAY_STYLE_HORIZONTAL_RIGHT;
        }
        return ATTR_DISPLAY_STYLE_VERTICAL;
    }
}
