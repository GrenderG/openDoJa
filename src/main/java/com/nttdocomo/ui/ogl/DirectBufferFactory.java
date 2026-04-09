package com.nttdocomo.ui.ogl;

import com.nttdocomo.ui.ogl.math.Matrix4f;
import opendoja.host.ogl.HostDirectBuffers.HostByteBuffer;
import opendoja.host.ogl.HostDirectBuffers.HostDirectBufferBase;
import opendoja.host.ogl.HostDirectBuffers.HostFloatBuffer;
import opendoja.host.ogl.HostDirectBuffers.HostIntBuffer;
import opendoja.host.ogl.HostDirectBuffers.HostShortBuffer;

import java.nio.ByteOrder;

/**
 * Allocates direct buffers for the DoJa OpenGL utility APIs.
 */
public final class DirectBufferFactory {
    private static final DirectBufferFactory FACTORY = new DirectBufferFactory();

    private DirectBufferFactory() {
    }

    /**
     * Returns the singleton factory instance.
     *
     * @return the shared factory
     */
    public static DirectBufferFactory getFactory() {
        return FACTORY;
    }

    /**
     * Allocates a byte buffer of the specified length.
     *
     * @param size the number of elements
     * @return the allocated buffer
     */
    public ByteBuffer allocateByteBuffer(int size) {
        return new HostByteBuffer(new byte[checkedSize(size)]);
    }

    /**
     * Allocates a byte buffer initialized from a byte array.
     *
     * @param initialData the initial data
     * @return the allocated buffer
     */
    public ByteBuffer allocateByteBuffer(byte[] initialData) {
        if (initialData == null) {
            throw new NullPointerException("initialData");
        }
        return new HostByteBuffer(initialData.clone());
    }

    /**
     * Allocates a byte buffer initialized from another system buffer.
     *
     * @param buff the source buffer
     * @return the allocated buffer
     */
    public ByteBuffer allocateByteBuffer(ByteBuffer buff) {
        return new HostByteBuffer(requireByteArray(buff).clone());
    }

    /**
     * Allocates a short buffer of the specified length.
     *
     * @param size the number of elements
     * @return the allocated buffer
     */
    public ShortBuffer allocateShortBuffer(int size) {
        return new HostShortBuffer(new short[checkedSize(size)]);
    }

    /**
     * Allocates a short buffer initialized from bytes.
     *
     * @param initialData the initial bytes
     * @return the allocated buffer
     */
    public ShortBuffer allocateShortBuffer(byte[] initialData) {
        if (initialData == null) {
            throw new NullPointerException("initialData");
        }
        short[] values = new short[initialData.length / 2];
        for (int i = 0; i < values.length; i++) {
            int offset = i * 2;
            values[i] = (short) (((initialData[offset] & 0xFF) << 8) | (initialData[offset + 1] & 0xFF));
        }
        return new HostShortBuffer(values);
    }

    /**
     * Allocates a short buffer initialized from another system buffer.
     *
     * @param buff the source buffer
     * @return the allocated buffer
     */
    public ShortBuffer allocateShortBuffer(ShortBuffer buff) {
        return new HostShortBuffer(requireShortArray(buff).clone());
    }

    /**
     * Allocates an int buffer of the specified length.
     *
     * @param size the number of elements
     * @return the allocated buffer
     */
    public IntBuffer allocateIntBuffer(int size) {
        return new HostIntBuffer(new int[checkedSize(size)]);
    }

    /**
     * Allocates an int buffer initialized from bytes.
     *
     * @param initialData the initial bytes
     * @return the allocated buffer
     */
    public IntBuffer allocateIntBuffer(byte[] initialData) {
        if (initialData == null) {
            throw new NullPointerException("initialData");
        }
        int[] values = new int[initialData.length / 4];
        for (int i = 0; i < values.length; i++) {
            int offset = i * 4;
            values[i] = ((initialData[offset] & 0xFF) << 24)
                    | ((initialData[offset + 1] & 0xFF) << 16)
                    | ((initialData[offset + 2] & 0xFF) << 8)
                    | (initialData[offset + 3] & 0xFF);
        }
        return new HostIntBuffer(values);
    }

    /**
     * Allocates an int buffer initialized from another system buffer.
     *
     * @param buff the source buffer
     * @return the allocated buffer
     */
    public IntBuffer allocateIntBuffer(IntBuffer buff) {
        return new HostIntBuffer(requireIntArray(buff).clone());
    }

    /**
     * Allocates a float buffer of the specified length.
     *
     * @param size the number of elements
     * @return the allocated buffer
     */
    public FloatBuffer allocateFloatBuffer(int size) {
        return new HostFloatBuffer(new float[checkedSize(size)]);
    }

    /**
     * Allocates a float buffer initialized from bytes.
     *
     * @param initialData the initial bytes
     * @return the allocated buffer
     */
    public FloatBuffer allocateFloatBuffer(byte[] initialData) {
        if (initialData == null) {
            throw new NullPointerException("initialData");
        }
        float[] values = new float[initialData.length / 4];
        for (int i = 0; i < values.length; i++) {
            int offset = i * 4;
            int bits = ((initialData[offset] & 0xFF) << 24)
                    | ((initialData[offset + 1] & 0xFF) << 16)
                    | ((initialData[offset + 2] & 0xFF) << 8)
                    | (initialData[offset + 3] & 0xFF);
            values[i] = Float.intBitsToFloat(bits);
        }
        return new HostFloatBuffer(values);
    }

    /**
     * Allocates a float buffer initialized from another system buffer.
     *
     * @param buff the source buffer
     * @return the allocated buffer
     */
    public FloatBuffer allocateFloatBuffer(FloatBuffer buff) {
        return new HostFloatBuffer(requireFloatArray(buff).clone());
    }

    /**
     * Allocates a byte buffer initialized from short values.
     *
     * @param initialData the initial values
     * @return the allocated buffer
     */
    public ByteBuffer allocateByteBuffer(short[] initialData) {
        if (initialData == null) {
            throw new NullPointerException("initialData");
        }
        byte[] values = new byte[initialData.length * 2];
        for (int i = 0; i < initialData.length; i++) {
            int offset = i * 2;
            values[offset] = (byte) (initialData[i] >>> 8);
            values[offset + 1] = (byte) initialData[i];
        }
        return new HostByteBuffer(values);
    }

    /**
     * Allocates a float buffer initialized from float values.
     *
     * @param initialData the initial values
     * @return the allocated buffer
     */
    public FloatBuffer allocateFloatBuffer(float[] initialData) {
        if (initialData == null) {
            throw new NullPointerException("initialData");
        }
        return new HostFloatBuffer(initialData.clone());
    }

    /**
     * Allocates an int buffer initialized from int values.
     *
     * @param initialData the initial values
     * @return the allocated buffer
     */
    public IntBuffer allocateIntBuffer(int[] initialData) {
        if (initialData == null) {
            throw new NullPointerException("initialData");
        }
        return new HostIntBuffer(initialData.clone());
    }

    /**
     * Allocates a short buffer initialized from short values.
     *
     * @param initialData the initial values
     * @return the allocated buffer
     */
    public ShortBuffer allocateShortBuffer(short[] initialData) {
        if (initialData == null) {
            throw new NullPointerException("initialData");
        }
        return new HostShortBuffer(initialData.clone());
    }

    private static int checkedSize(int size) {
        if (size < 0) {
            throw new NegativeArraySizeException("size");
        }
        return size;
    }

    private static byte[] requireByteArray(ByteBuffer buff) {
        if (buff == null) {
            throw new NullPointerException("buff");
        }
        if (buff instanceof HostByteBuffer array) {
            return array.values();
        }
        if (buff instanceof ArrayByteBuffer array) {
            return array.values;
        }
        throw new IllegalArgumentException("buff");
    }

    private static short[] requireShortArray(ShortBuffer buff) {
        if (buff == null) {
            throw new NullPointerException("buff");
        }
        if (buff instanceof HostShortBuffer array) {
            return array.values();
        }
        if (buff instanceof ArrayShortBuffer array) {
            return array.values;
        }
        throw new IllegalArgumentException("buff");
    }

    private static int[] requireIntArray(IntBuffer buff) {
        if (buff == null) {
            throw new NullPointerException("buff");
        }
        if (buff instanceof HostIntBuffer array) {
            return array.values();
        }
        if (buff instanceof ArrayIntBuffer array) {
            return array.values;
        }
        throw new IllegalArgumentException("buff");
    }

    private static float[] requireFloatArray(FloatBuffer buff) {
        if (buff == null) {
            throw new NullPointerException("buff");
        }
        if (buff instanceof HostFloatBuffer array) {
            return array.values();
        }
        if (buff instanceof ArrayFloatBuffer array) {
            return array.values;
        }
        throw new IllegalArgumentException("buff");
    }

    /**
     * Returns the active segment offset for a host direct buffer.
     *
     * @param buff the buffer
     * @return the segment offset
     */
    public static int getSegmentOffset(DirectBuffer buff) {
        if (buff == null) {
            throw new NullPointerException("buff");
        }
        if (buff instanceof AbstractBuffer buffer) {
            return buffer.segmentOffset();
        }
        if (buff instanceof HostDirectBufferBase hostBuffer) {
            return hostBuffer.segmentOffset();
        }
        throw new IllegalArgumentException("buff");
    }

    /**
     * Returns the active segment length for a host direct buffer.
     *
     * @param buff the buffer
     * @return the segment length
     */
    public static int getSegmentLength(DirectBuffer buff) {
        if (buff == null) {
            throw new NullPointerException("buff");
        }
        if (buff instanceof AbstractBuffer buffer) {
            return buffer.segmentLength();
        }
        if (buff instanceof HostDirectBufferBase hostBuffer) {
            return hostBuffer.segmentLength();
        }
        throw new IllegalArgumentException("buff");
    }

    /**
     * Returns one short value from a host short buffer.
     *
     * @param buff the buffer
     * @param index the element index
     * @return the short value
     */
    public static short getShort(ShortBuffer buff, int index) {
        return requireShortArray(buff)[index];
    }

    /**
     * Returns one float value from a host float buffer.
     *
     * @param buff the buffer
     * @param index the element index
     * @return the float value
     */
    public static float getFloat(FloatBuffer buff, int index) {
        return requireFloatArray(buff)[index];
    }

    public abstract static class AbstractBuffer {
        int segmentOffset;
        int segmentLength;

        abstract int lengthValue();

        int segmentOffset() {
            return segmentOffset;
        }

        int segmentLength() {
            return segmentLength;
        }

        final void setSegmentInternal(int offset, int length) {
            if (offset < 0 || length <= 0 || offset + length > lengthValue()) {
                throw new ArrayIndexOutOfBoundsException();
            }
            segmentOffset = offset;
            segmentLength = length;
        }

        final void clearSegmentInternal() {
            segmentOffset = 0;
            segmentLength = lengthValue();
        }
    }

    public static final class ArrayByteBuffer extends AbstractBuffer implements ByteBuffer {
        private final byte[] values;

        public ArrayByteBuffer(byte[] values) {
            this.values = values;
            clearSegmentInternal();
        }

        @Override
        public int length() {
            return values.length;
        }

        @Override
        int lengthValue() {
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

    public static final class ArrayShortBuffer extends AbstractBuffer implements ShortBuffer {
        private final short[] values;

        public ArrayShortBuffer(short[] values) {
            this.values = values;
            clearSegmentInternal();
        }

        @Override
        public int length() {
            return values.length;
        }

        @Override
        int lengthValue() {
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

    public static final class ArrayIntBuffer extends AbstractBuffer implements IntBuffer {
        private final int[] values;

        public ArrayIntBuffer(int[] values) {
            this.values = values;
            clearSegmentInternal();
        }

        @Override
        public int length() {
            return values.length;
        }

        @Override
        int lengthValue() {
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

    public static final class ArrayFloatBuffer extends AbstractBuffer implements FloatBuffer {
        private final float[] values;

        public ArrayFloatBuffer(float[] values) {
            this.values = values;
            clearSegmentInternal();
        }

        @Override
        public int length() {
            return values.length;
        }

        @Override
        int lengthValue() {
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
            if ((src1 != null && getSegmentLength(src1) != expectedLength)
                    || (src2 != null && getSegmentLength(src2) != expectedLength)) {
                throw new IllegalArgumentException("segment");
            }
            int dst = segmentOffset();
            int leftIndex = src1 == null ? 0 : getSegmentOffset(src1);
            int rightIndex = src2 == null ? 0 : getSegmentOffset(src2);
            for (int i = 0; i < expectedLength; i++) {
                float value = 0f;
                if (src1 != null) {
                    value += getFloat(src1, leftIndex + i);
                }
                if (src2 != null) {
                    value += multiplier * getFloat(src2, rightIndex + i);
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
            if (required > segmentLength() || required > getSegmentLength(src)) {
                throw new ArrayIndexOutOfBoundsException();
            }
            int dst = segmentOffset();
            int srcIndex = getSegmentOffset(src);
            float[] m = matrix.m;
            for (int item = 0; item < itemCount; item++) {
                float x = getFloat(src, srcIndex++);
                float y = getFloat(src, srcIndex++);
                if (itemSize == 2) {
                    values[dst++] = m[0] * x + m[4] * y + m[12];
                    values[dst++] = m[1] * x + m[5] * y + m[13];
                    continue;
                }
                float z = getFloat(src, srcIndex++);
                if (itemSize == 3) {
                    values[dst++] = m[0] * x + m[4] * y + m[8] * z + m[12];
                    values[dst++] = m[1] * x + m[5] * y + m[9] * z + m[13];
                    values[dst++] = m[2] * x + m[6] * y + m[10] * z + m[14];
                    continue;
                }
                float w = getFloat(src, srcIndex++);
                values[dst++] = m[0] * x + m[4] * y + m[8] * z + m[12] * w;
                values[dst++] = m[1] * x + m[5] * y + m[9] * z + m[13] * w;
                values[dst++] = m[2] * x + m[6] * y + m[10] * z + m[14] * w;
                values[dst++] = m[3] * x + m[7] * y + m[11] * z + m[15] * w;
            }
            return this;
        }
    }

    private static void checkRange(int index, int bufferLength, int offset, int arrayLength, int length) {
        if (index < 0 || offset < 0 || length < 0 || offset + length > arrayLength || index + length > bufferLength) {
            throw new IllegalArgumentException();
        }
    }
}
