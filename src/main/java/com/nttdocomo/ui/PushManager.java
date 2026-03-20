package com.nttdocomo.ui;

public class PushManager {
    private String parameter;
    private long pushedTime;
    private ParameterPushListener listener;

    PushManager() {
    }

    public String getParameter() {
        return parameter;
    }

    public long getParameterPushedTime() {
        return pushedTime;
    }

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
