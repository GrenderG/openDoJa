package opendoja.host.ogl;

import com.nttdocomo.ui.ogl.ByteBuffer;
import com.nttdocomo.ui.ogl.DirectBufferFactory;
import com.nttdocomo.ui.ogl.FloatBuffer;
import com.nttdocomo.ui.ogl.IntBuffer;
import com.nttdocomo.ui.ogl.ShortBuffer;
import com.nttdocomo.ui.ogl.math.Matrix4f;

public final class HostDirectBuffers {
    private HostDirectBuffers() {
    }

    public abstract static class HostDirectBufferBase {
        private int segmentOffset;
        private int segmentLength;

        protected abstract int lengthValue();

        public final int segmentOffset() {
            return segmentOffset;
        }

        public final int segmentLength() {
            return segmentLength;
        }

        protected final void setSegmentInternal(int offset, int length) {
            if (offset < 0 || length <= 0 || offset + length > lengthValue()) {
                throw new ArrayIndexOutOfBoundsException();
            }
            segmentOffset = offset;
            segmentLength = length;
        }

        protected final void clearSegmentInternal() {
            segmentOffset = 0;
            segmentLength = lengthValue();
        }

        protected static void checkRange(int index, int bufferLength, int offset, int arrayLength, int length) {
            if (index < 0 || offset < 0 || length < 0 || offset + length > arrayLength || index + length > bufferLength) {
                throw new IllegalArgumentException();
            }
        }
    }

    public static class HostByteBuffer extends HostDirectBufferBase implements ByteBuffer {
        private final byte[] values;

        public HostByteBuffer(byte[] values) {
            this.values = values;
            clearSegmentInternal();
        }

        public final byte[] values() {
            return values;
        }

        @Override
        public int length() {
            return values.length;
        }

        @Override
        protected int lengthValue() {
            return values.length;
        }

        @Override
        public void setSegment(int offset, int length) {
            setSegmentInternal(offset, length);
        }

        @Override
        public void clearSegment() {
            clearSegmentInternal();
        }

        @Override
        public byte[] get(int index, byte[] buff) {
            if (buff == null) {
                throw new NullPointerException("buff");
            }
            return get(index, buff, 0, buff.length);
        }

        @Override
        public byte[] get(int index, byte[] buff, int offset, int length) {
            checkRange(index, values.length, offset, buff == null ? -1 : buff.length, length);
            System.arraycopy(values, index, buff, offset, length);
            return buff;
        }

        @Override
        public void put(int index, byte[] buff) {
            if (buff == null) {
                throw new NullPointerException("buff");
            }
            put(index, buff, 0, buff.length);
        }

        @Override
        public void put(int index, byte[] buff, int offset, int length) {
            checkRange(index, values.length, offset, buff == null ? -1 : buff.length, length);
            System.arraycopy(buff, offset, values, index, length);
        }
    }

    public static class HostShortBuffer extends HostDirectBufferBase implements ShortBuffer {
        private final short[] values;

        public HostShortBuffer(short[] values) {
            this.values = values;
            clearSegmentInternal();
        }

        public final short[] values() {
            return values;
        }

        @Override
        public int length() {
            return values.length;
        }

        @Override
        protected int lengthValue() {
            return values.length;
        }

        @Override
        public void setSegment(int offset, int length) {
            setSegmentInternal(offset, length);
        }

        @Override
        public void clearSegment() {
            clearSegmentInternal();
        }

        @Override
        public short[] get(int index, short[] buff) {
            if (buff == null) {
                throw new NullPointerException("buff");
            }
            return get(index, buff, 0, buff.length);
        }

        @Override
        public short[] get(int index, short[] buff, int offset, int length) {
            checkRange(index, values.length, offset, buff == null ? -1 : buff.length, length);
            System.arraycopy(values, index, buff, offset, length);
            return buff;
        }

        @Override
        public void put(int index, short[] buff) {
            if (buff == null) {
                throw new NullPointerException("buff");
            }
            put(index, buff, 0, buff.length);
        }

        @Override
        public void put(int index, short[] buff, int offset, int length) {
            checkRange(index, values.length, offset, buff == null ? -1 : buff.length, length);
            System.arraycopy(buff, offset, values, index, length);
        }
    }

    public static class HostIntBuffer extends HostDirectBufferBase implements IntBuffer {
        private final int[] values;

        public HostIntBuffer(int[] values) {
            this.values = values;
            clearSegmentInternal();
        }

        public final int[] values() {
            return values;
        }

        @Override
        public int length() {
            return values.length;
        }

        @Override
        protected int lengthValue() {
            return values.length;
        }

        @Override
        public void setSegment(int offset, int length) {
            setSegmentInternal(offset, length);
        }

        @Override
        public void clearSegment() {
            clearSegmentInternal();
        }

        @Override
        public int[] get(int index, int[] buff) {
            if (buff == null) {
                throw new NullPointerException("buff");
            }
            return get(index, buff, 0, buff.length);
        }

        @Override
        public int[] get(int index, int[] buff, int offset, int length) {
            checkRange(index, values.length, offset, buff == null ? -1 : buff.length, length);
            System.arraycopy(values, index, buff, offset, length);
            return buff;
        }

        @Override
        public void put(int index, int[] buff) {
            if (buff == null) {
                throw new NullPointerException("buff");
            }
            put(index, buff, 0, buff.length);
        }

        @Override
        public void put(int index, int[] buff, int offset, int length) {
            checkRange(index, values.length, offset, buff == null ? -1 : buff.length, length);
            System.arraycopy(buff, offset, values, index, length);
        }
    }

    public static class HostFloatBuffer extends HostDirectBufferBase implements FloatBuffer {
        private final float[] values;

        public HostFloatBuffer(float[] values) {
            this.values = values;
            clearSegmentInternal();
        }

        public final float[] values() {
            return values;
        }

        @Override
        public int length() {
            return values.length;
        }

        @Override
        protected int lengthValue() {
            return values.length;
        }

        @Override
        public void setSegment(int offset, int length) {
            setSegmentInternal(offset, length);
        }

        @Override
        public void clearSegment() {
            clearSegmentInternal();
        }

        @Override
        public float[] get(int index, float[] buff) {
            if (buff == null) {
                throw new NullPointerException("buff");
            }
            return get(index, buff, 0, buff.length);
        }

        @Override
        public float[] get(int index, float[] buff, int offset, int length) {
            checkRange(index, values.length, offset, buff == null ? -1 : buff.length, length);
            System.arraycopy(values, index, buff, offset, length);
            return buff;
        }

        @Override
        public void put(int index, float[] buff) {
            if (buff == null) {
                throw new NullPointerException("buff");
            }
            put(index, buff, 0, buff.length);
        }

        @Override
        public void put(int index, float[] buff, int offset, int length) {
            checkRange(index, values.length, offset, buff == null ? -1 : buff.length, length);
            System.arraycopy(buff, offset, values, index, length);
        }

        @Override
        public FloatBuffer madd(FloatBuffer src1, FloatBuffer src2, float multiplier) {
            if (src1 == null && src2 == null) {
                throw new NullPointerException();
            }
            int expectedLength = segmentLength();
            if ((src1 != null && DirectBufferFactory.getSegmentLength(src1) != expectedLength)
                    || (src2 != null && DirectBufferFactory.getSegmentLength(src2) != expectedLength)) {
                throw new IllegalArgumentException("segment");
            }
            int dst = segmentOffset();
            int leftIndex = src1 == null ? 0 : DirectBufferFactory.getSegmentOffset(src1);
            int rightIndex = src2 == null ? 0 : DirectBufferFactory.getSegmentOffset(src2);
            for (int i = 0; i < expectedLength; i++) {
                float value = 0f;
                if (src1 != null) {
                    value += DirectBufferFactory.getFloat(src1, leftIndex + i);
                }
                if (src2 != null) {
                    value += multiplier * DirectBufferFactory.getFloat(src2, rightIndex + i);
                }
                values[dst + i] = value;
            }
            return this;
        }

        @Override
        public FloatBuffer transform(FloatBuffer src, Matrix4f matrix, int itemSize, int itemCount) {
            if (matrix == null) {
                throw new NullPointerException("matrix");
            }
            if (itemSize != 2 && itemSize != 3 && itemSize != 4) {
                throw new IllegalArgumentException("itemSize");
            }
            if (itemCount < 0) {
                throw new IllegalArgumentException("itemCount");
            }
            if (matrix.m == null) {
                throw new NullPointerException("matrix.m");
            }
            if (matrix.m.length < 16) {
                throw new ArrayIndexOutOfBoundsException("matrix.m");
            }
            int required = itemSize * itemCount;
            if (required > segmentLength() || required > DirectBufferFactory.getSegmentLength(src)) {
                throw new ArrayIndexOutOfBoundsException();
            }
            int dst = segmentOffset();
            int srcIndex = DirectBufferFactory.getSegmentOffset(src);
            float[] m = matrix.m;
            for (int item = 0; item < itemCount; item++) {
                float x = DirectBufferFactory.getFloat(src, srcIndex++);
                float y = DirectBufferFactory.getFloat(src, srcIndex++);
                if (itemSize == 2) {
                    values[dst++] = m[0] * x + m[4] * y + m[12];
                    values[dst++] = m[1] * x + m[5] * y + m[13];
                    continue;
                }
                float z = DirectBufferFactory.getFloat(src, srcIndex++);
                if (itemSize == 3) {
                    values[dst++] = m[0] * x + m[4] * y + m[8] * z + m[12];
                    values[dst++] = m[1] * x + m[5] * y + m[9] * z + m[13];
                    values[dst++] = m[2] * x + m[6] * y + m[10] * z + m[14];
                    continue;
                }
                float w = DirectBufferFactory.getFloat(src, srcIndex++);
                values[dst++] = m[0] * x + m[4] * y + m[8] * z + m[12] * w;
                values[dst++] = m[1] * x + m[5] * y + m[9] * z + m[13] * w;
                values[dst++] = m[2] * x + m[6] * y + m[10] * z + m[14] * w;
                values[dst++] = m[3] * x + m[7] * y + m[11] * z + m[15] * w;
            }
            return this;
        }
    }
}
