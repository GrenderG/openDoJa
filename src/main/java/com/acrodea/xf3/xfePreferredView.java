package com.acrodea.xf3;

public class xfePreferredView {
    private float farClip = 1024f;
    private float nearClip = 1f;
    private float fov = 45f;
    private float horizontalSafe = 0f;
    private float verticalSafe = 0f;
    private int width = 240;
    private int height = 240;

    public float getFarClip() {
        return farClip;
    }

    public void setFarClip(float farClip) {
        if (farClip > 0f) {
            this.farClip = farClip;
        }
    }

    public float getNearClip() {
        return nearClip;
    }

    public void setNearClip(float nearClip) {
        if (nearClip > 0f) {
            this.nearClip = nearClip;
        }
    }

    public float getFOV() {
        return fov;
    }

    public void setFOV(float fov) {
        if (fov > 0f) {
            this.fov = fov;
        }
    }

    public float getHorizontalSafe() {
        return horizontalSafe;
    }

    public void setHorizontalSafe(float horizontalSafe) {
        this.horizontalSafe = horizontalSafe;
    }

    public float getVerticalSafe() {
        return verticalSafe;
    }

    public void setVerticalSafe(float verticalSafe) {
        this.verticalSafe = verticalSafe;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        if (width > 0) {
            this.width = width;
        }
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        if (height > 0) {
            this.height = height;
        }
    }
}
