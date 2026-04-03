package com.nttdocomo.ui;

/**
 * Defines the push Manager type.
 */
public class PushManager {
    private String parameter;
    private long pushedTime;
    private ParameterPushListener listener;

    PushManager() {
    }

    /**
     * Gets parameter.
     */
    public String getParameter() {
        return parameter;
    }

    /**
     * Gets parameter Pushed Time.
     */
    public long getParameterPushedTime() {
        return pushedTime;
    }

    /**
     * Sets parameter Push Listener.
     */
    public void setParameterPushListener(ParameterPushListener listener) {
        this.listener = listener;
    }

    void push(String parameter) {
        this.parameter = parameter;
        this.pushedTime = System.currentTimeMillis();
        if (listener != null) {
            listener.parameterPushed(this);
        }
    }
}
