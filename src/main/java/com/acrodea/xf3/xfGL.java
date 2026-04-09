package com.acrodea.xf3;

import com.acrodea.xf3.math.xfMatrix4;

public final class xfGL {
    private static final xfGL INSTANCE = new xfGL();

    private xfGL() {
    }

    public static xfGL getGL() {
        return INSTANCE;
    }

    public void setMatrix(int mode, xfMatrix4 matrix) {
        if (xfeOGLContext.mGL == null || matrix == null) {
            return;
        }
        xfeOGLContext.mGL.glMatrixMode(mode);
        xfeOGLContext.mGL.glLoadMatrixf(matrix.toColumnMajorArray());
    }
}
