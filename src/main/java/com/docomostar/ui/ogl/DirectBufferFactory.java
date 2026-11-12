package com.docomostar.ui.ogl;

/** STAR buffer views over the existing host storage and range validation. */
public final class DirectBufferFactory {
    private static final DirectBufferFactory INSTANCE = new DirectBufferFactory();
    private final com.nttdocomo.ui.ogl.DirectBufferFactory host = com.nttdocomo.ui.ogl.DirectBufferFactory.getFactory();
    private DirectBufferFactory() { }
    public static DirectBufferFactory getFactory() { return INSTANCE; }

    /** Host integration: renderers must see the actual host allocation. */
    public static com.nttdocomo.ui.ogl.DirectBuffer unwrap(com.nttdocomo.ui.ogl.DirectBuffer buffer) {
        return buffer instanceof BufferAdapter<?> adapter ? adapter.host : buffer;
    }

    private abstract static class BufferAdapter<T extends com.nttdocomo.ui.ogl.DirectBuffer> implements DirectBuffer {
        final T host;
        BufferAdapter(T host) { this.host = host; }
        @Override public int length() { return host.length(); }
        @Override public void setSegment(int offset, int length) { host.setSegment(offset, length); }
        @Override public void clearSegment() { host.clearSegment(); }
    }
    public ByteBuffer allocateByteBuffer(int size) { return new ByteAdapter(host.allocateByteBuffer(size)); }
    public ByteBuffer allocateByteBuffer(byte[] values) { return new ByteAdapter(host.allocateByteBuffer(values)); }
    public ByteBuffer allocateByteBuffer(ByteBuffer source) { return new ByteAdapter(host.allocateByteBuffer((com.nttdocomo.ui.ogl.ByteBuffer) unwrap(source))); }
    public ByteBuffer allocateByteBuffer(short[] values) { return new ByteAdapter(host.allocateByteBuffer(values)); }

    private static final class ByteAdapter extends BufferAdapter<com.nttdocomo.ui.ogl.ByteBuffer> implements ByteBuffer {
        ByteAdapter(com.nttdocomo.ui.ogl.ByteBuffer host) { super(host); }
        @Override public byte[] get(int index, byte[] values) { return host.get(index, values); }
        @Override public byte[] get(int index, byte[] values, int offset, int length) { return host.get(index, values, offset, length); }
        @Override public void put(int index, byte[] values) { host.put(index, values); }
        @Override public void put(int index, byte[] values, int offset, int length) { host.put(index, values, offset, length); }
    }
    public ShortBuffer allocateShortBuffer(int size) { return new ShortAdapter(host.allocateShortBuffer(size)); }
    public ShortBuffer allocateShortBuffer(byte[] values) { return new ShortAdapter(host.allocateShortBuffer(values)); }
    public ShortBuffer allocateShortBuffer(ShortBuffer source) { return new ShortAdapter(host.allocateShortBuffer((com.nttdocomo.ui.ogl.ShortBuffer) unwrap(source))); }
    public ShortBuffer allocateShortBuffer(short[] values) { return new ShortAdapter(host.allocateShortBuffer(values)); }

    private static final class ShortAdapter extends BufferAdapter<com.nttdocomo.ui.ogl.ShortBuffer> implements ShortBuffer {
        ShortAdapter(com.nttdocomo.ui.ogl.ShortBuffer host) { super(host); }
        @Override public short[] get(int index, short[] values) { return host.get(index, values); }
        @Override public short[] get(int index, short[] values, int offset, int length) { return host.get(index, values, offset, length); }
        @Override public void put(int index, short[] values) { host.put(index, values); }
        @Override public void put(int index, short[] values, int offset, int length) { host.put(index, values, offset, length); }
    }
    public IntBuffer allocateIntBuffer(int size) { return new IntAdapter(host.allocateIntBuffer(size)); }
    public IntBuffer allocateIntBuffer(byte[] values) { return new IntAdapter(host.allocateIntBuffer(values)); }
    public IntBuffer allocateIntBuffer(IntBuffer source) { return new IntAdapter(host.allocateIntBuffer((com.nttdocomo.ui.ogl.IntBuffer) unwrap(source))); }
    public IntBuffer allocateIntBuffer(int[] values) { return new IntAdapter(host.allocateIntBuffer(values)); }

    private static final class IntAdapter extends BufferAdapter<com.nttdocomo.ui.ogl.IntBuffer> implements IntBuffer {
        IntAdapter(com.nttdocomo.ui.ogl.IntBuffer host) { super(host); }
        @Override public int[] get(int index, int[] values) { return host.get(index, values); }
        @Override public int[] get(int index, int[] values, int offset, int length) { return host.get(index, values, offset, length); }
        @Override public void put(int index, int[] values) { host.put(index, values); }
        @Override public void put(int index, int[] values, int offset, int length) { host.put(index, values, offset, length); }
    }
    public FloatBuffer allocateFloatBuffer(int size) { return new FloatAdapter(host.allocateFloatBuffer(size)); }
    public FloatBuffer allocateFloatBuffer(byte[] values) { return new FloatAdapter(host.allocateFloatBuffer(values)); }
    public FloatBuffer allocateFloatBuffer(FloatBuffer source) { return new FloatAdapter(host.allocateFloatBuffer((com.nttdocomo.ui.ogl.FloatBuffer) unwrap(source))); }
    public FloatBuffer allocateFloatBuffer(float[] values) { return new FloatAdapter(host.allocateFloatBuffer(values)); }

    private static final class FloatAdapter extends BufferAdapter<com.nttdocomo.ui.ogl.FloatBuffer> implements FloatBuffer {
        FloatAdapter(com.nttdocomo.ui.ogl.FloatBuffer host) { super(host); }
        @Override public float[] get(int index, float[] values) { return host.get(index, values); }
        @Override public float[] get(int index, float[] values, int offset, int length) { return host.get(index, values, offset, length); }
        @Override public void put(int index, float[] values) { host.put(index, values); }
        @Override public void put(int index, float[] values, int offset, int length) { host.put(index, values, offset, length); }
        @Override public FloatBuffer madd(FloatBuffer left, FloatBuffer right, float multiplier) {
            return madd((com.nttdocomo.ui.ogl.FloatBuffer) left, right, multiplier);
        }
        @Override public FloatBuffer madd(com.nttdocomo.ui.ogl.FloatBuffer left, com.nttdocomo.ui.ogl.FloatBuffer right, float multiplier) {
            host.madd((com.nttdocomo.ui.ogl.FloatBuffer) unwrap(left), (com.nttdocomo.ui.ogl.FloatBuffer) unwrap(right), multiplier);
            return this;
        }
        @Override public FloatBuffer transform(FloatBuffer source, com.docomostar.ui.ogl.math.Matrix4f matrix, int itemSize, int itemCount) {
            return transform(source, com.docomostar.ui.ogl.math.Matrix4f.unwrap(matrix), itemSize, itemCount);
        }
        @Override public FloatBuffer transform(com.nttdocomo.ui.ogl.FloatBuffer source, com.nttdocomo.ui.ogl.math.Matrix4f matrix, int itemSize, int itemCount) {
            host.transform((com.nttdocomo.ui.ogl.FloatBuffer) unwrap(source), matrix, itemSize, itemCount);
            return this;
        }
    }
}
