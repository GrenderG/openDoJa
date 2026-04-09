package com.acrodea.xf3.math;

public class xfVector3 {
    public float x;
    public float y;
    public float z;

    public xfVector3() {
    }

    public xfVector3(float x, float y, float z) {
        set(x, y, z);
    }

    public xfVector3(xfVector3 value) {
        this(value == null ? 0f : value.x, value == null ? 0f : value.y, value == null ? 0f : value.z);
    }

    public void set(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
}
