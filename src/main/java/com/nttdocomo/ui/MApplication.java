package com.nttdocomo.ui;

/**
 * Provides the template for a standby application.
 * Standby applications must extend this class.
 * Even a standby application follows the {@link IApplication} lifecycle when
 * it is started normally.
 */
public abstract class MApplication extends IApplication {
    /**
     * Indicates that the application changed from the inactive or sleep state
     * to the active state (=1).
     */
    public static final int MODE_CHANGED_EVENT = 1;
    /**
     * Indicates that the time set by the application has elapsed (=2).
     */
    public static final int WAKEUP_TIMER_EVENT = 2;
    /**
     * Indicates that the native clock time has changed (=3).
     */
    public static final int CLOCK_TICK_EVENT = 3;
    /**
     * Indicates that the fold state or flip state changed (=4).
     */
    public static final int FOLD_CHANGED_EVENT = 4;

    private int wakeupTimer;
    private boolean clockTick;
    private boolean active = true;

    /**
     * Creates an MApplication object.
     */
    public MApplication() {
    }

    /**
     * Called when an event related to the standby application occurs.
     * The default implementation does nothing.
     *
     * @param type the event type
     * @param param the event parameter
     */
    public void processSystemEvent(int type, int param) {
        return;
    }

    /**
     * Notifies JAM that the standby application enters sleep mode.
     */
    public final void sleep() {
        active = false;
    }

    /**
     * Sets the timer that moves the standby application from sleep mode to the
     * inactive state.
     *
     * @param minutes the timer value
     */
    public final void setWakeupTimer(int minutes) {
        this.wakeupTimer = minutes;
    }

    /**
     * Gets the timer value that moves the standby application from sleep mode
     * to the inactive state.
     *
     * @return the wakeup timer value
     */
    public final int getWakeupTimer() {
        return wakeupTimer;
    }

    /**
     * Cancels the timer that moves the standby application from sleep mode to
     * the inactive state.
     */
    public final void resetWakeupTimer() {
        wakeupTimer = 0;
    }

    /**
     * Sets whether clock-tick events are generated in sync with changes to the
     * native clock.
     *
     * @param enabled {@code true} to enable clock-tick events, or
     *                {@code false} to disable them
     */
    public final void setClockTick(boolean enabled) {
        this.clockTick = enabled;
    }

    /**
     * Switches the application from the active state to the inactive state.
     */
    public final void deactivate() {
        active = false;
    }

    /**
     * Host-only lifecycle hook, not part of the original DoJa API.
     * OpenDoJa uses this to reflect host-driven mode changes back into standby
     * applications that move themselves inactive during startup.
     *
     * @param active {@code true} if the host is activating the application, or
     *               {@code false} if the host is deactivating it
     */
    public final void setActiveForHost(boolean active) {
        this.active = active;
    }

    /**
     * Gets the current application state.
     *
     * @return {@code true} if the application is active, or {@code false} if
     *         it is inactive
     */
    public final boolean isActive() {
        return active;
    }
}
