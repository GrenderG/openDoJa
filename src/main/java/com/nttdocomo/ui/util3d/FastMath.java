package com.nttdocomo.ui.util3d;

import opendoja.g3d.FixedPoint;

/**
 * Defines utility methods for fast numeric operations on {@code float}
 * values.
 */
public class FastMath {
    /**
     * Applications cannot create this utility class directly.
     */
    protected FastMath() {
    }

    /**
     * Converts a {@code float} into the internal {@code int} representation
     * used by the engine.
     *
     * @param value the float value to convert
     * @return the corresponding internal integer value
     */
    public static int floatToInnerInt(float value) {
        return FixedPoint.fromFloat(value);
    }

    /**
     * Converts an internal engine integer value back to {@code float}.
     *
     * @param value the internal integer value
     * @return the corresponding float value
     */
    public static float innerIntToFloat(int value) {
        return FixedPoint.toFloat(value);
    }

    /**
     * Calculates the approximate sum of two values.
     *
     * @param left the left operand
     * @param right the right operand
     * @return the approximate sum
     */
    public static float add(float left, float right) {
        return left + right;
    }

    /**
     * Calculates the approximate difference of two values.
     *
     * @param left the left operand
     * @param right the right operand
     * @return the approximate difference
     */
    public static float sub(float left, float right) {
        return left - right;
    }

    /**
     * Calculates the approximate product of two values.
     *
     * @param left the left operand
     * @param right the right operand
     * @return the approximate product
     */
    public static float mul(float left, float right) {
        return left * right;
    }

    /**
     * Calculates the approximate quotient of two values.
     *
     * @param left the dividend
     * @param right the divisor
     * @return the approximate quotient
     */
    public static float div(float left, float right) {
        return left / right;
    }

    /**
     * Calculates an approximate square root.
     *
     * @param value the input value
     * @return the approximate square root
     */
    public static float sqrt(float value) {
        return (float) java.lang.Math.sqrt(value);
    }

    /**
     * Calculates an approximate sine value.
     *
     * @param value the angle in degrees
     * @return the approximate sine
     */
    public static float sin(float value) {
        return (float) java.lang.Math.sin(java.lang.Math.toRadians(value));
    }

    /**
     * Calculates an approximate cosine value.
     *
     * @param value the angle in degrees
     * @return the approximate cosine
     */
    public static float cos(float value) {
        return (float) java.lang.Math.cos(java.lang.Math.toRadians(value));
    }

    /**
     * Calculates an approximate tangent value.
     *
     * @param value the angle in degrees
     * @return the approximate tangent
     */
    public static float tan(float value) {
        return (float) java.lang.Math.tan(java.lang.Math.toRadians(value));
    }

    /**
     * Calculates an approximate arcsine value in degrees.
     *
     * @param value the input value
     * @return the approximate arcsine in degrees
     */
    public static float asin(float value) {
        return (float) java.lang.Math.toDegrees(java.lang.Math.asin(value));
    }

    /**
     * Calculates an approximate arccosine value in degrees.
     *
     * @param value the input value
     * @return the approximate arccosine in degrees
     */
    public static float acos(float value) {
        return (float) java.lang.Math.toDegrees(java.lang.Math.acos(value));
    }

    /**
     * Calculates an approximate arctangent value in degrees.
     *
     * @param value the input value
     * @return the approximate arctangent in degrees
     */
    public static float atan(float value) {
        return (float) java.lang.Math.toDegrees(java.lang.Math.atan(value));
    }

    /**
     * Calculates an approximate two-argument arctangent value in degrees.
     *
     * @param y the y coordinate
     * @param x the x coordinate
     * @return the approximate angle in degrees
     */
    public static float atan2(float y, float x) {
        return (float) java.lang.Math.toDegrees(java.lang.Math.atan2(y, x));
    }

    /**
     * Calculates an approximate absolute value.
     *
     * @param value the input value
     * @return the approximate absolute value
     */
    public static float abs(float value) {
        return java.lang.Math.abs(value);
    }
}
