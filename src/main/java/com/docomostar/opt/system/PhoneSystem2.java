package com.docomostar.opt.system;

public class PhoneSystem2 extends com.nttdocomo.opt.ui.PhoneSystem2 {
    public static int getAttribute(int device) {
        var runtime = opendoja.host.DoJaRuntime.current();
        if (device == DEV_DISPLAY_STYLE && runtime != null && runtime.hasLandscapePresentation()
                && opendoja.host.ApplicationProfile.current() == opendoja.host.ApplicationProfile.STAR) {
            // STAR titles use this value to select their landscape key layout as well as image rotation.
            return ATTR_DISPLAY_STYLE_HORIZONTAL_RIGHT;
        }
        return com.nttdocomo.opt.ui.PhoneSystem2.getAttribute(device);
    }
}
