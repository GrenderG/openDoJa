package com.docomostar.ui.ogl.math;

public abstract class Tuple4f extends com.nttdocomo.ui.ogl.math.Tuple4f {
    public Tuple4f() { }
    public Tuple4f(float x, float y, float z, float w) { super(x, y, z, w); }
    public void add(Tuple4f tuple) { super.add(tuple); }
    public void mul(Matrix4f matrix) { Matrix4f.unwrap(matrix).transform(this); }
}
