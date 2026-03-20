package com.nttdocomo.ui;

import com.nttdocomo.util.ScheduleDate;
import opendoja.host.DoJaRuntime;
import opendoja.host.LaunchConfig;

import java.util.HashMap;
import java.util.Map;

public abstract class IApplication {
    public static final int LAUNCHED_FROM_MENU = 0;
    public static final int LAUNCHED_AFTER_DOWNLOAD = 1;
    public static final int LAUNCHED_FROM_TIMER = 2;
    public static final int LAUNCHED_AS_CONCIERGE = 3;
    public static final int LAUNCHED_FROM_EXT = 4;
    public static final int LAUNCHED_FROM_BROWSER = 5;
    public static final int LAUNCHED_FROM_MAILER = 6;
    public static final int LAUNCHED_FROM_IAPPLI = 7;
    public static final int LAUNCHED_FROM_LAUNCHER = 8;
    public static final int LAUNCHED_AS_ILET = 9;
    public static final int LAUNCHED_MSG_RECEIVED = 10;
    public static final int LAUNCHED_MSG_SENT = 11;
    public static final int LAUNCHED_MSG_UNSENT = 12;
    public static final int LAUNCHED_FROM_LOCATION_INFO = 13;
    public static final int LAUNCHED_FROM_LOCATION_IMAGE = 14;
    public static final int LAUNCHED_FROM_PHONEBOOK = 15;
    public static final int LAUNCHED_FROM_DTV = 17;
    public static final int LAUNCHED_FROM_TORUCA = 18;
    public static final int LAUNCHED_FROM_FELICA_ADHOC = 19;
    public static final int LAUNCHED_FROM_MENU_FOR_DELETION = 20;
    public static final int LAUNCHED_FROM_BML = 21;
    public static final int LAUNCH_BROWSER = 1;
    public static final int LAUNCH_VERSIONUP = 2;
    public static final int LAUNCH_IAPPLI = 3;
    public static final int LAUNCH_AS_LAUNCHER = 4;
    public static final int LAUNCH_MAILMENU = 5;
    public static final int LAUNCH_SCHEDULER = 6;
    public static final int LAUNCH_MAIL_RECEIVED = 7;
    public static final int LAUNCH_MAIL_SENT = 8;
    public static final int LAUNCH_MAIL_UNSENT = 9;
    public static final int LAUNCH_MAIL_LAST_INCOMING = 10;
    public static final int LAUNCH_DTV = 12;
    public static final int LAUNCH_BROWSER_SUSPEND = 13;
    public static final int SUSPEND_BY_NATIVE = 1;
    public static final int SUSPEND_BY_IAPP = 2;
    public static final int SUSPEND_PACKETIN = 256;
    public static final int SUSPEND_CALL_OUT = 512;
    public static final int SUSPEND_CALL_IN = 1024;
    public static final int SUSPEND_MAIL_SEND = 2048;
    public static final int SUSPEND_MAIL_RECEIVE = 4096;
    public static final int SUSPEND_MESSAGE_RECEIVE = 8192;
    public static final int SUSPEND_SCHEDULE_NOTIFY = 16384;
    public static final int SUSPEND_MULTITASK_APPLICATION = 32768;

    private static volatile IApplication currentApp;

    private final String[] args;
    private final Map<String, String> parameters;
    private final String sourceUrl;
    private final int launchType;
    private final Map<Integer, ScheduleDate> launchTimes = new HashMap<>();
    private final PushManager pushManager = new PushManager();
    private boolean moved;
    private boolean movedFromOtherTerminal;
    private int suspendInfo;

    public IApplication() {
        LaunchConfig config = DoJaRuntime.consumePreparedLaunch();
        if (config == null) {
            this.args = null;
            this.parameters = new HashMap<>();
            this.sourceUrl = null;
            this.launchType = LAUNCHED_FROM_MENU;
        } else {
            this.args = config.args();
            this.parameters = new HashMap<>(config.parameters());
            this.sourceUrl = config.sourceUrl();
            this.launchType = config.launchType();
        }
        currentApp = this;
    }

    public static IApplication getCurrentApp() {
        return currentApp;
    }

    public final String[] getArgs() {
        return args == null ? null : args.clone();
    }

    public final String getParameter(String key) {
        return parameters.get(key);
    }

    public abstract void start();

    public void resume() {
    }

    public final void terminate() {
        DoJaRuntime runtime = DoJaRuntime.current();
        if (runtime != null) {
            runtime.shutdown();
        }
    }

    public final String getSourceURL() {
        return sourceUrl;
    }

    public final int getLaunchType() {
        return launchType;
    }

    public final void launch(int type, String[] args) {
        // Placeholder for native/browser/i-appli launches. Kept explicit instead of pretending.
    }

    public int getSuspendInfo() {
        return suspendInfo;
    }

    public void setLaunchTime(int kind, ScheduleDate launchTime) {
        launchTimes.put(kind, launchTime);
    }

    public ScheduleDate getLaunchTime(int kind) {
        return launchTimes.get(kind);
    }

    public PushManager getPushManager() throws SecurityException {
        return pushManager;
    }

    public final boolean isMoved() {
        return moved;
    }

    public final void clearMoved() {
        moved = false;
    }

    public final boolean isMovedFromOtherTerminal() {
        return movedFromOtherTerminal;
    }
}
