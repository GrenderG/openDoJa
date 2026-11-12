package com.docomostar.system;

public class PhoneSystem extends com.nttdocomo.ui.PhoneSystem {
    public static final int DEV_MAX_BATTERY_LEVEL = 12, DEV_BATTERY_LEVEL = 13, DEV_POWER_SOURCE = 14;
    public static final int ATTR_POWER_BATTERY = 0, ATTR_POWER_EXTERNAL = 1;

    /** STAR's numeric battery view of the existing emulated DoJa battery state. */
    public static int getAttribute(int device) {
        if (device == DEV_MAX_BATTERY_LEVEL) return 100;
        if (device == DEV_BATTERY_LEVEL) {
            int state = com.nttdocomo.ui.PhoneSystem.getAttribute(DEV_BATTERY);
            return state == ATTR_BATTERY_PARTIAL ? 50 : 100;
        }
        if (device == DEV_POWER_SOURCE) return com.nttdocomo.ui.PhoneSystem.getAttribute(DEV_BATTERY)
                == ATTR_BATTERY_CHARGING ? ATTR_POWER_EXTERNAL : ATTR_POWER_BATTERY;
        return com.nttdocomo.ui.PhoneSystem.getAttribute(device);
    }
    public static boolean isAvailable(int device) {
        return device == DEV_MAX_BATTERY_LEVEL || device == DEV_BATTERY_LEVEL || device == DEV_POWER_SOURCE
                || com.nttdocomo.ui.PhoneSystem.isAvailable(device);
    }
}
