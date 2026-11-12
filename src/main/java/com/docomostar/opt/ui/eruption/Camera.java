package com.docomostar.opt.ui.eruption;

public final class Camera extends Object3D {
    public static final int MCE_CAMERA_TYPE_PARALLEL = 0, MCE_CAMERA_TYPE_PERSPECTIVE = 1, MCE_CAMERA_TYPE_FRUSTUM = 2;
    private int type = MCE_CAMERA_TYPE_PERSPECTIVE, width, height;
    private float aspect = 1, angle = 0.125f, near = 0.1f, far = 1000;
    public Camera() { super(MCE_CLASS_TYPE_CAMERA); }
    public void setPerspectiveFov(float aspect, float angle, float near, float far) {
        requireLive();
        if (!(aspect > 0) || !(angle > 0 && angle < 0.5f) || !(near > 0 && far > near))
            throw new IllegalArgumentException("Perspective parameters");
        type = MCE_CAMERA_TYPE_PERSPECTIVE;
        this.aspect=aspect; this.angle=angle; this.near=near; this.far=far;
    }
    public void setParallel(int width, int height, float near, float far) {
        requireLive();
        if (width <= 0 || height <= 0 || !(far > near)) throw new IllegalArgumentException("Parallel parameters");
        type=MCE_CAMERA_TYPE_PARALLEL; this.width=width; this.height=height; this.near=near; this.far=far;
    }
    public int getCameraType() { return type; }
    public float getAspect() { return aspect; }
    public float getAngle() { return angle; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public float getNear() { return near; }
    public float getFar() { return far; }
}
