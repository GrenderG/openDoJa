package com.nttdocomo.ui;

import com.nttdocomo.util.EventListener;

public interface Audio3DListener extends EventListener {
    void audioAction(Audio3D audio3D, int type, int param);
}
