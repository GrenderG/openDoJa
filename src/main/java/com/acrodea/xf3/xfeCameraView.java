package com.acrodea.xf3;

import com.acrodea.xf3.math.xfMatrix4;

public class xfeCameraView {
    private final xfMatrix4 viewMatrix;

    public xfeCameraView(xfMatrix4 viewMatrix) {
        this.viewMatrix = new xfMatrix4(viewMatrix);
    }

    public xfMatrix4 getViewMatrix() {
        return new xfMatrix4(viewMatrix);
    }
}
