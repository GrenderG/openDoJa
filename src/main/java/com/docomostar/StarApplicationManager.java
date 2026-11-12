package com.docomostar;

import com.nttdocomo.ui.IApplication;
import com.nttdocomo.ui.PushManager;
import com.nttdocomo.util.ScheduleDate;

public final class StarApplicationManager {
    private final StarApplication application;
    private final java.util.Map<Integer, StarEventObject> pushedEvents = new java.util.concurrent.ConcurrentHashMap<>();

    public StarApplicationManager(StarApplication application) {
        this.application = application;
    }

    public boolean isMoved() {
        return application.isMoved();
    }

    public void clearMoved() {
        application.clearMoved();
    }

    public boolean isMovedFromOtherTerminal() {
        return application.isMovedFromOtherTerminal();
    }

    public void setLaunchTime(int index, ScheduleDate date) {
        application.setLaunchTime(index, date);
    }

    public ScheduleDate getLaunchTime(int index) {
        return application.getLaunchTime(index);
    }

    public static String getSourceURL() {
        StarApplication application = StarApplication.getThisStarApplication();
        return application == null ? null : application.getSourceURL();
    }

    public static int getLaunchType() {
        StarApplication application = StarApplication.getThisStarApplication();
        return application == null ? IApplication.LAUNCHED_FROM_MENU : application.getLaunchType();
    }

    public static String getParameter(String key) {
        StarApplication application = StarApplication.getThisStarApplication();
        return application == null ? null : application.getParameter(key);
    }

    public static String[] getArgs() {
        StarApplication application = StarApplication.getThisStarApplication();
        return application == null ? new String[0] : application.getArgs();
    }

    public int getSuspendInfo() {
        return application.getSuspendInfo();
    }

    public StarEventObject getPushedEvent(int eventType) {
        StarEventObject.checkType(eventType);
        return pushedEvents.get(eventType);
    }

    /** Host integration: retain an actual event that caused this launch; normal launches have none. */
    public void recordPushedEventForHost(StarEventObject event) {
        java.util.Objects.requireNonNull(event, "event");
        StarEventObject.checkType(event.getType());
        pushedEvents.put(event.getType(), event);
    }

    public PushManager getPushManager() throws SecurityException {
        return application.getPushManager();
    }

    public void launch(int type, String[] args) {
        application.launch(type, args);
    }

    public void terminate() {
        application.terminate();
    }

    public void upgrade() {
        return;
    }
}
