package com.acrodea.xf3;

import com.nttdocomo.ui.ogl.GraphicsOGL;

public final class xfeOGLContext {
    public static GraphicsOGL mGL;

    private xfeOGLContext() {
    }

    public static void init(GraphicsOGL graphics) {
        mGL = graphics;
    }
}
