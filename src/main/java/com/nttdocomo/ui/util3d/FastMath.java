package com.nttdocomo.ui.util3d;

import opendoja.g3d.FixedPoint;

public class FastMath {
    public static int floatToInnerInt(float value) {
        return FixedPoint.fromFloat(value);
    }

    public static float innerIntToFloat(int value) {
        return FixedPoint.toFloat(value);
    }

    public static float add(float left, float right) {
        return left + right;
    }

    public static float sub(float left, float right) {
        return left - right;
    }

    public static float mul(float left, float right) {
        return left * right;
    }

    public static float div(float left, float right) {
        return left / right;
    }

    public static float sqrt(float value) {
        return (float) java.lang.Math.sqrt(value);
    }

    public static float sin(float value) {
        return (float) java.lang.Math.sin(java.lang.Math.toRadians(value));
    }

    public static float cos(float value) {
        return (float) java.lang.Math.cos(java.lang.Math.toRadians(value));
    }

    public static float tan(float value) {
        return (float) java.lang.Math.tan(java.lang.Math.toRadians(value));
    }

    public static float asin(float value) {
        return (float) java.lang.Math.toDegrees(java.lang.Math.asin(value));
    }

    public static float acos(float value) {
        return (float) java.lang.Math.toDegrees(java.lang.Math.acos(value));
    }

    public static float atan(float value) {
        return (float) java.lang.Math.toDegrees(java.lang.Math.atan(value));
    }

    public static float atan2(float y, float x) {
        return (float) java.lang.Math.toDegrees(java.lang.Math.atan2(y, x));
    }

    public static float abs(float value) {
        return java.lang.Math.abs(value);
    }
}
