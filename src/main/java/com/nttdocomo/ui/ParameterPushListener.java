package com.nttdocomo.ui;

import com.nttdocomo.util.EventListener;

public interface ParameterPushListener extends EventListener {
    void parameterPushed(PushManager pushManager);
}
