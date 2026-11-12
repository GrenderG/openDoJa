package com.docomostar.ui;

public class Display {
    public static final int KEY_SOFT3 = 12;
    public static final int KEY_SOFT4 = 13;

    public static final int KEY_PRESSED_EVENT = com.nttdocomo.ui.Display.KEY_PRESSED_EVENT;
    public static final int KEY_RELEASED_EVENT = com.nttdocomo.ui.Display.KEY_RELEASED_EVENT;
    public static final int RESUME_VM_EVENT = com.nttdocomo.ui.Display.RESUME_VM_EVENT;
    public static final int RESET_VM_EVENT = com.nttdocomo.ui.Display.RESET_VM_EVENT;
    public static final int UPDATE_VM_EVENT = com.nttdocomo.ui.Display.UPDATE_VM_EVENT;
    public static final int TIMER_EXPIRED_EVENT = com.nttdocomo.ui.Display.TIMER_EXPIRED_EVENT;
    public static final int MEDIA_EVENT = com.nttdocomo.ui.Display.MEDIA_EVENT;
    public static final int POINTER_MOVED_EVENT = com.nttdocomo.ui.Display.POINTER_MOVED_EVENT;
    public static final int FINGER_MOVED_EVENT = com.nttdocomo.ui.Display.FINGER_MOVED_EVENT;
    public static final int TOUCH_PRESSED_EVENT = 67;
    public static final int TOUCH_RELEASED_EVENT = 68;
    public static final int TOUCH_MOVEDSTART_EVENT = 69;
    public static final int TOUCH_MOVEDEND_EVENT = 70;
    public static final int TOUCH_PINCHOPEN_EVENT = 71;
    public static final int TOUCH_PINCHCLOSE_EVENT = 72;
    public static final int KEY_0 = com.nttdocomo.ui.Display.KEY_0;
    public static final int KEY_1 = com.nttdocomo.ui.Display.KEY_1;
    public static final int KEY_2 = com.nttdocomo.ui.Display.KEY_2;
    public static final int KEY_3 = com.nttdocomo.ui.Display.KEY_3;
    public static final int KEY_4 = com.nttdocomo.ui.Display.KEY_4;
    public static final int KEY_5 = com.nttdocomo.ui.Display.KEY_5;
    public static final int KEY_6 = com.nttdocomo.ui.Display.KEY_6;
    public static final int KEY_7 = com.nttdocomo.ui.Display.KEY_7;
    public static final int KEY_8 = com.nttdocomo.ui.Display.KEY_8;
    public static final int KEY_9 = com.nttdocomo.ui.Display.KEY_9;
    public static final int KEY_ASTERISK = com.nttdocomo.ui.Display.KEY_ASTERISK;
    public static final int KEY_POUND = com.nttdocomo.ui.Display.KEY_POUND;
    public static final int KEY_LEFT = com.nttdocomo.ui.Display.KEY_LEFT;
    public static final int KEY_UP = com.nttdocomo.ui.Display.KEY_UP;
    public static final int KEY_RIGHT = com.nttdocomo.ui.Display.KEY_RIGHT;
    public static final int KEY_DOWN = com.nttdocomo.ui.Display.KEY_DOWN;
    public static final int KEY_SELECT = com.nttdocomo.ui.Display.KEY_SELECT;
    public static final int KEY_SOFT1 = com.nttdocomo.ui.Display.KEY_SOFT1;
    public static final int KEY_SOFT2 = com.nttdocomo.ui.Display.KEY_SOFT2;
    public static final int KEY_IAPP = com.nttdocomo.ui.Display.KEY_IAPP;
    public static final int KEY_UPPER_LEFT = com.nttdocomo.ui.Display.KEY_UPPER_LEFT;
    public static final int KEY_UPPER_RIGHT = com.nttdocomo.ui.Display.KEY_UPPER_RIGHT;
    public static final int KEY_LOWER_RIGHT = com.nttdocomo.ui.Display.KEY_LOWER_RIGHT;
    public static final int KEY_LOWER_LEFT = com.nttdocomo.ui.Display.KEY_LOWER_LEFT;
    public static final int KEY_PAGE_UP = com.nttdocomo.ui.Display.KEY_PAGE_UP;
    public static final int KEY_PAGE_DOWN = com.nttdocomo.ui.Display.KEY_PAGE_DOWN;
    public static final int KEY_CLEAR = com.nttdocomo.ui.Display.KEY_CLEAR;
    public static final int KEY_MAIL = com.nttdocomo.ui.Display.KEY_MAIL;
    public static final int KEY_MEMO = com.nttdocomo.ui.Display.KEY_MEMO;
    public static final int KEY_MENU = com.nttdocomo.ui.Display.KEY_MENU;
    public static final int KEY_I_MODE = com.nttdocomo.ui.Display.KEY_I_MODE;
    public static final int KEY_PHONE_BOOK = com.nttdocomo.ui.Display.KEY_PHONE_BOOK;
    public static final int KEY_CALENDAR = com.nttdocomo.ui.Display.KEY_CALENDAR;
    public static final int KEY_VOICE = com.nttdocomo.ui.Display.KEY_VOICE;
    public static final int KEY_MANNER_MODE = com.nttdocomo.ui.Display.KEY_MANNER_MODE;
    public static final int KEY_DRIVE_MODE = com.nttdocomo.ui.Display.KEY_DRIVE_MODE;
    public static final int KEY_GPS = com.nttdocomo.ui.Display.KEY_GPS;
    public static final int KEY_ROLL_LEFT = com.nttdocomo.ui.Display.KEY_ROLL_LEFT;
    public static final int KEY_ROLL_RIGHT = com.nttdocomo.ui.Display.KEY_ROLL_RIGHT;
    public static final int KEY_SUB1 = com.nttdocomo.ui.Display.KEY_SUB1;
    public static final int KEY_SUB2 = com.nttdocomo.ui.Display.KEY_SUB2;
    public static final int KEY_SUB3 = com.nttdocomo.ui.Display.KEY_SUB3;
    public static final int KEY_MY_SELECT = com.nttdocomo.ui.Display.KEY_MY_SELECT;
    public static final int KEY_CAMERA = com.nttdocomo.ui.Display.KEY_CAMERA;
    public static final int KEY_CAMERA_ZOOM_IN = com.nttdocomo.ui.Display.KEY_CAMERA_ZOOM_IN;
    public static final int KEY_CAMERA_ZOOM_OUT = com.nttdocomo.ui.Display.KEY_CAMERA_ZOOM_OUT;
    public static final int KEY_CAMERA_SELECT = com.nttdocomo.ui.Display.KEY_CAMERA_SELECT;
    public static final int KEY_CAMERA_LIGHT = com.nttdocomo.ui.Display.KEY_CAMERA_LIGHT;
    public static final int KEY_CAMERA_SHOT = com.nttdocomo.ui.Display.KEY_CAMERA_SHOT;

    protected Display() { }
    public static int getWidth() { return com.nttdocomo.ui.Display.getWidth(); }
    public static int getHeight() { return com.nttdocomo.ui.Display.getHeight(); }
    public static boolean isColor() { return com.nttdocomo.ui.Display.isColor(); }
    public static int numColors() { return com.nttdocomo.ui.Display.numColors(); }

    public static void setCurrent(Frame frame) {
        com.nttdocomo.ui.Display.setCurrent(frame == null ? null : frame.hostFrame);
    }

    public static Frame getCurrent() {
        com.nttdocomo.ui.Frame frame = com.nttdocomo.ui.Display.getCurrent();
        return frame instanceof Frame.Host host ? host.starFrame() : null;
    }
}
