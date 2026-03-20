package opendoja.g3d;

public final class MbacModel {
    private final int[] modelVertices;
    private final int[] rawVertices;
    private final float[] vertices;
    private final Polygon[] polygons;
    private final int numPatterns;
    private final Bone[] bones;

    public MbacModel(int[] modelVertices, int[] rawVertices, Polygon[] polygons, int numPatterns, Bone[] bones) {
        this.modelVertices = modelVertices == null ? new int[0] : modelVertices;
        this.rawVertices = rawVertices == null ? new int[0] : rawVertices;
        this.vertices = toFloatVertices(this.rawVertices);
        this.polygons = polygons;
        this.numPatterns = numPatterns;
        this.bones = bones == null ? new Bone[0] : bones;
    }

    public int[] modelVertices() {
        return modelVertices;
    }

    public int[] rawVertices() {
        return rawVertices;
    }

    public float[] vertices() {
        return vertices;
    }

    public Polygon[] polygons() {
        return polygons;
    }

    public int numPatterns() {
        return numPatterns;
    }

    public Bone[] bones() {
        return bones;
    }

    public static final class Bone {
        private final int length;
        private final int parent;
        private final int[] matrix;
        private float[] floatMatrix;

        public Bone(int length, int parent, int[] matrix) {
            this.length = length;
            this.parent = parent;
            this.matrix = matrix;
        }

        public int length() {
            return length;
        }

        public int parent() {
            return parent;
        }

        public int[] fixedMatrix() {
            return matrix;
        }

        public float[] matrix() {
            if (floatMatrix == null) {
                floatMatrix = new float[]{
                        matrix[0] / 4096f, matrix[1] / 4096f, matrix[2] / 4096f, matrix[3],
                        matrix[4] / 4096f, matrix[5] / 4096f, matrix[6] / 4096f, matrix[7],
                        matrix[8] / 4096f, matrix[9] / 4096f, matrix[10] / 4096f, matrix[11]
                };
            }
            return floatMatrix;
        }
    }

    public static final class Polygon {
        private final int[] indices;
        private final float[] textureCoords;
        private final int color;
        private final int textureIndex;
        private final int patternMask;
        private final int blendMode;
        private final boolean doubleSided;
        private final boolean transparent;

        public Polygon(int[] indices, float[] textureCoords, int color, int textureIndex, int patternMask, int blendMode,
                       boolean doubleSided, boolean transparent) {
            this.indices = indices;
            this.textureCoords = textureCoords;
            this.color = color;
            this.textureIndex = textureIndex;
            this.patternMask = patternMask;
            this.blendMode = blendMode;
            this.doubleSided = doubleSided;
            this.transparent = transparent;
        }

        public int[] indices() {
            return indices;
        }

        public float[] textureCoords() {
            return textureCoords;
        }

        public int color() {
            return color;
        }

        public int textureIndex() {
            return textureIndex;
        }

        public int patternMask() {
            return patternMask;
        }

        public int blendMode() {
            return blendMode;
        }

        public boolean doubleSided() {
            return doubleSided;
        }

        public boolean transparent() {
            return transparent;
        }
    }

    private static float[] toFloatVertices(int[] rawVertices) {
        float[] converted = new float[rawVertices.length];
        for (int i = 0; i < rawVertices.length; i++) {
            converted[i] = rawVertices[i];
        }
        return converted;
    }
}
