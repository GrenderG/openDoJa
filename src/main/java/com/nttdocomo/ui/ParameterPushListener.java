package com.nttdocomo.ui;

import com.nttdocomo.util.EventListener;

/**
 * Defines the parameter Push Listener type.
 */
public interface ParameterPushListener extends EventListener {
    void parameterPushed(PushManager pushManager);
}
