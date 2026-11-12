package com.docomostar.ui.ogl;

import com.docomostar.ui.ogl.math.Matrix4f;

public interface FloatBuffer extends DirectBuffer, com.nttdocomo.ui.ogl.FloatBuffer {
    FloatBuffer madd(FloatBuffer left, FloatBuffer right, float multiplier);
    FloatBuffer transform(FloatBuffer source, Matrix4f matrix, int itemSize, int itemCount);
}
