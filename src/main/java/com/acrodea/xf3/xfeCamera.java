package com.acrodea.xf3;

import com.acrodea.xf3.math.xfRectangleInt;
import com.acrodea.xf3.math.xfMath;
import com.acrodea.xf3.math.xfMatrix4;

public class xfeCamera extends xfeGroup {
    private final xfePreferredView preferredView = new xfePreferredView();

    public xfeCamera() {
    }

    public xfeCamera(String name) {
        super(name);
    }

    public xfePreferredView getPreferredView() {
        return preferredView;
    }

    public xfeCameraView getCameraView(xfRectangleInt rectangle) {
        xfMatrix4 cameraWorld = worldMatrix(this);
        xfMath.matrixInverseFast(cameraWorld);
        xfMatrix4 projection = projectionMatrix(rectangle);
        projection.mul(cameraWorld);
        return new xfeCameraView(xfMatrix4.transposedCopy(projection));
    }

    private xfMatrix4 projectionMatrix(xfRectangleInt rectangle) {
        int viewportWidth = rectangle != null && rectangle.mWidth > 0 ? rectangle.mWidth : preferredView.getWidth();
        int viewportHeight = rectangle != null && rectangle.mHeight > 0 ? rectangle.mHeight : preferredView.getHeight();
        float aspect = viewportWidth / (float) Math.max(1, viewportHeight);
        float near = Math.max(0.01f, preferredView.getNearClip());
        float far = Math.max(near + 0.01f, preferredView.getFarClip());
        float fovRadians = (float) Math.toRadians(Math.max(1f, preferredView.getFOV()));
        float tanHalfFov = (float) Math.tan(fovRadians / 2f);
        xfMatrix4 projection = new xfMatrix4();
        projection.m[0][0] = 1f / Math.max(0.0001f, tanHalfFov * aspect);
        projection.m[1][1] = 1f / Math.max(0.0001f, tanHalfFov);
        projection.m[2][2] = -((far + near) / (far - near));
        projection.m[2][3] = -((2f * far * near) / (far - near));
        projection.m[3][2] = -1f;
        projection.m[3][3] = 0f;
        return projection;
    }

    private static xfMatrix4 worldMatrix(xfeNode node) {
        xfMatrix4 result = new xfMatrix4();
        java.util.ArrayList<xfMatrix4> chain = new java.util.ArrayList<>();
        xfeNode current = node;
        while (current != null) {
            if (current instanceof xfeActor actor) {
                chain.add(actor.getTransformation().getInternalMatrix());
                current = current.getParent();
                continue;
            }
            if (current instanceof xfeGroup group) {
                chain.add(group.getTransformation().getInternalMatrix());
            }
            current = current.getParent();
        }
        for (int i = chain.size() - 1; i >= 0; i--) {
            result.mul(chain.get(i));
        }
        return result;
    }
}
