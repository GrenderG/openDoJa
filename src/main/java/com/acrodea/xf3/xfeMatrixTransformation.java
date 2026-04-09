package com.acrodea.xf3;

import com.acrodea.xf3.math.xfMatrix4;

public class xfeMatrixTransformation extends xfeTransformation {
    private final xfMatrix4 matrix = new xfMatrix4();
    private int mode;

    public void setTransformation(xfMatrix4 value, int mode) {
        matrix.set(value);
        this.mode = mode;
    }

    public void setInternalTransformation(xfMatrix4 value, int mode) {
        matrix.set(xfMatrix4.transposedCopy(value));
        this.mode = mode;
    }

    public xfMatrix4 getMatrix() {
        return new xfMatrix4(matrix);
    }

    public xfMatrix4 getInternalMatrix() {
        return xfMatrix4.transposedCopy(matrix);
    }

    public int getMode() {
        return mode;
    }
}
