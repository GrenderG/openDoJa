package com.nttdocomo.opt.ui.j3d;

import opendoja.g3d.FixedPoint;

public final class Math {
    private Math() {
    }

    public static int sqrt(int value) {
        return FixedPoint.sqrt(value);
    }

    public static int sin(int angle) {
        return FixedPoint.sin(angle);
    }

    public static int cos(int angle) {
        return FixedPoint.cos(angle);
    }

    public static int atan2(int y, int x) {
        return FixedPoint.atan2(y, x);
    }
}
