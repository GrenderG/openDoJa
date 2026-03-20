package opendoja.g3d;

public final class MascotFigure {
    private static final int[] IDENTITY_AFFINE = {
            FixedPoint.ONE, 0, 0, 0,
            0, FixedPoint.ONE, 0, 0,
            0, 0, FixedPoint.ONE, 0
    };

    private final MbacModel model;
    private final float[] vertices;
    private final int[] actionMatrices;
    private final int[] localBindMatrices;
    private final int[] localPoseMatrices;
    private final int[] globalPoseMatrices;
    private SoftwareTexture[] textures = new SoftwareTexture[0];
    private int selectedTexture = -1;
    private MascotActionTableData actionTable;
    private int actionIndex;
    private int time;
    private int patternMask;

    public MascotFigure(MbacModel model) {
        this.model = model;
        this.vertices = model == null ? new float[0] : model.vertices().clone();
        int matrixCount = model == null ? 0 : model.bones().length * 12;
        this.actionMatrices = new int[matrixCount];
        this.localBindMatrices = new int[matrixCount];
        this.localPoseMatrices = new int[matrixCount];
        this.globalPoseMatrices = new int[matrixCount];
        fillIdentity(actionMatrices);
        if (model != null) {
            copyBindMatrices(model.bones(), localBindMatrices);
        }
        applyPose();
    }

    public MbacModel model() {
        return model;
    }

    public float[] vertices() {
        return vertices;
    }

    public void setTexture(SoftwareTexture texture) {
        this.textures = texture == null ? new SoftwareTexture[0] : new SoftwareTexture[]{texture};
        this.selectedTexture = texture == null ? -1 : 0;
    }

    public void setTextures(SoftwareTexture[] textures) {
        this.textures = textures == null ? new SoftwareTexture[0] : textures.clone();
        this.selectedTexture = this.textures.length == 1 ? 0 : java.lang.Math.min(this.selectedTexture, this.textures.length - 1);
    }

    public int numTextures() {
        return textures.length;
    }

    public SoftwareTexture texture() {
        if (textures.length == 0) {
            return null;
        }
        int index = selectedTexture;
        if (index < 0 || index >= textures.length) {
            index = 0;
        }
        return textures[index];
    }

    public SoftwareTexture texture(int polygonTextureIndex) {
        if (textures.length == 0) {
            return null;
        }
        int index = selectedTexture;
        if (index < 0) {
            index = polygonTextureIndex;
        }
        if (index < 0 || index >= textures.length) {
            index = 0;
        }
        return textures[index];
    }

    public void selectTexture(int index) {
        if (index < 0 || index >= textures.length) {
            throw new IllegalArgumentException("Texture index out of range");
        }
        this.selectedTexture = index;
    }

    public void setAction(MascotActionTableData actionTable, int actionIndex) {
        if (actionTable == null) {
            throw new NullPointerException("actionTable");
        }
        if (actionIndex < 0 || actionIndex >= actionTable.numActions()) {
            throw new IllegalArgumentException("actionIndex");
        }
        this.actionTable = actionTable;
        this.actionIndex = actionIndex;
        setTime(time);
    }

    public MascotActionTableData actionTable() {
        return actionTable;
    }

    public void setTime(int time) {
        this.time = java.lang.Math.max(0, time);
        if (actionTable != null) {
            this.patternMask = actionTable.patternForFrame(actionIndex, this.time, this.patternMask);
        }
        applyPose();
    }

    public int time() {
        return time;
    }

    public int numPatterns() {
        return model == null ? 0 : model.numPatterns();
    }

    public void setPattern(int patternMask) {
        this.patternMask = patternMask;
    }

    public int patternMask() {
        return patternMask;
    }

    private void applyPose() {
        if (model == null) {
            return;
        }
        MbacModel.Bone[] bones = model.bones();
        if (bones.length == 0) {
            System.arraycopy(model.vertices(), 0, vertices, 0, vertices.length);
            return;
        }
        if (actionTable == null) {
            buildGlobalPoseMatrices(bones, localBindMatrices, globalPoseMatrices);
        } else {
            actionTable.writeMatricesFixed(actionIndex, time, actionMatrices);
            buildLocalPoseMatrices(localBindMatrices, actionMatrices, localPoseMatrices);
            buildGlobalPoseMatrices(bones, localPoseMatrices, globalPoseMatrices);
        }
        transformVertices(model.modelVertices(), vertices, bones, globalPoseMatrices);
    }

    private static void copyBindMatrices(MbacModel.Bone[] bones, int[] bindMatrices) {
        for (int i = 0; i < bones.length; i++) {
            int offset = i * 12;
            System.arraycopy(bones[i].fixedMatrix(), 0, bindMatrices, offset, 12);
        }
    }

    private static void buildLocalPoseMatrices(int[] localBindMatrices, int[] actionMatrices, int[] localPoseMatrices) {
        for (int i = 0; i < localPoseMatrices.length; i += 12) {
            multiplyMM(localPoseMatrices, i, localBindMatrices, i, actionMatrices, i);
        }
    }

    private static void buildGlobalPoseMatrices(MbacModel.Bone[] bones, int[] localMatrices, int[] globalMatrices) {
        boolean[] built = new boolean[bones.length];
        for (int bone = 0; bone < bones.length; bone++) {
            buildGlobalPoseMatrix(bones, localMatrices, globalMatrices, built, bone);
        }
    }

    private static void buildGlobalPoseMatrix(MbacModel.Bone[] bones, int[] localMatrices, int[] globalMatrices,
                                              boolean[] built, int bone) {
        if (built[bone]) {
            return;
        }
        int offset = bone * 12;
        int parent = bones[bone].parent();
        if (parent < 0) {
            System.arraycopy(localMatrices, offset, globalMatrices, offset, 12);
        } else {
            buildGlobalPoseMatrix(bones, localMatrices, globalMatrices, built, parent);
            multiplyMM(globalMatrices, offset, globalMatrices, parent * 12, localMatrices, offset);
        }
        built[bone] = true;
    }

    private static void transformVertices(int[] source, float[] destination, MbacModel.Bone[] bones, int[] globalMatrices) {
        int sourceOffset = 0;
        int destinationOffset = 0;
        for (int i = 0; i < bones.length; i++) {
            int offset = i * 12;
            for (int vertex = 0; vertex < bones[i].length(); vertex++) {
                multiplyMV(destination, destinationOffset, source, sourceOffset, globalMatrices, offset);
                sourceOffset += 3;
                destinationOffset += 3;
            }
        }
    }

    private static void multiplyMM(int[] destination, int destinationOffset, int[] left, int leftOffset, int[] right, int rightOffset) {
        int l00 = left[leftOffset];
        int l01 = left[leftOffset + 1];
        int l02 = left[leftOffset + 2];
        int l03 = left[leftOffset + 3];
        int l10 = left[leftOffset + 4];
        int l11 = left[leftOffset + 5];
        int l12 = left[leftOffset + 6];
        int l13 = left[leftOffset + 7];
        int l20 = left[leftOffset + 8];
        int l21 = left[leftOffset + 9];
        int l22 = left[leftOffset + 10];
        int l23 = left[leftOffset + 11];
        int r00 = right[rightOffset];
        int r01 = right[rightOffset + 1];
        int r02 = right[rightOffset + 2];
        int r03 = right[rightOffset + 3];
        int r10 = right[rightOffset + 4];
        int r11 = right[rightOffset + 5];
        int r12 = right[rightOffset + 6];
        int r13 = right[rightOffset + 7];
        int r20 = right[rightOffset + 8];
        int r21 = right[rightOffset + 9];
        int r22 = right[rightOffset + 10];
        int r23 = right[rightOffset + 11];
        destination[destinationOffset] = FixedPoint.mul(l00, r00) + FixedPoint.mul(l01, r10) + FixedPoint.mul(l02, r20);
        destination[destinationOffset + 1] = FixedPoint.mul(l00, r01) + FixedPoint.mul(l01, r11) + FixedPoint.mul(l02, r21);
        destination[destinationOffset + 2] = FixedPoint.mul(l00, r02) + FixedPoint.mul(l01, r12) + FixedPoint.mul(l02, r22);
        destination[destinationOffset + 3] = FixedPoint.mul(l00, r03) + FixedPoint.mul(l01, r13) + FixedPoint.mul(l02, r23) + l03;
        destination[destinationOffset + 4] = FixedPoint.mul(l10, r00) + FixedPoint.mul(l11, r10) + FixedPoint.mul(l12, r20);
        destination[destinationOffset + 5] = FixedPoint.mul(l10, r01) + FixedPoint.mul(l11, r11) + FixedPoint.mul(l12, r21);
        destination[destinationOffset + 6] = FixedPoint.mul(l10, r02) + FixedPoint.mul(l11, r12) + FixedPoint.mul(l12, r22);
        destination[destinationOffset + 7] = FixedPoint.mul(l10, r03) + FixedPoint.mul(l11, r13) + FixedPoint.mul(l12, r23) + l13;
        destination[destinationOffset + 8] = FixedPoint.mul(l20, r00) + FixedPoint.mul(l21, r10) + FixedPoint.mul(l22, r20);
        destination[destinationOffset + 9] = FixedPoint.mul(l20, r01) + FixedPoint.mul(l21, r11) + FixedPoint.mul(l22, r21);
        destination[destinationOffset + 10] = FixedPoint.mul(l20, r02) + FixedPoint.mul(l21, r12) + FixedPoint.mul(l22, r22);
        destination[destinationOffset + 11] = FixedPoint.mul(l20, r03) + FixedPoint.mul(l21, r13) + FixedPoint.mul(l22, r23) + l23;
    }

    private static void fillIdentity(int[] matrices) {
        for (int i = 0; i < matrices.length; i += 12) {
            System.arraycopy(IDENTITY_AFFINE, 0, matrices, i, 12);
        }
    }

    private static void multiplyMV(float[] destination, int destinationOffset, int[] source, int sourceOffset, int[] matrix, int matrixOffset) {
        int x = source[sourceOffset];
        int y = source[sourceOffset + 1];
        int z = source[sourceOffset + 2];
        destination[destinationOffset] = FixedPoint.mul(x, matrix[matrixOffset]) + FixedPoint.mul(y, matrix[matrixOffset + 1]) + FixedPoint.mul(z, matrix[matrixOffset + 2]) + matrix[matrixOffset + 3];
        destination[destinationOffset + 1] = FixedPoint.mul(x, matrix[matrixOffset + 4]) + FixedPoint.mul(y, matrix[matrixOffset + 5]) + FixedPoint.mul(z, matrix[matrixOffset + 6]) + matrix[matrixOffset + 7];
        destination[destinationOffset + 2] = FixedPoint.mul(x, matrix[matrixOffset + 8]) + FixedPoint.mul(y, matrix[matrixOffset + 9]) + FixedPoint.mul(z, matrix[matrixOffset + 10]) + matrix[matrixOffset + 11];
    }
}
