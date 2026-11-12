package com.docomostar.opt.ui.eruption;

import java.util.List;

public final class IndexBuffer extends Object3D {
    public static final int MCE_POINTS=1, MCE_LINES=2, MCE_LINE_STRIP=3, MCE_TRIANGLES=4,
            MCE_TRIANGLE_STRIP=5, MCE_TRIANGLE_FAN=6, MCE_SPRITES=7;
    int type, first;
    int[] lengths=new int[0];
    short[] indices;
    Appearance appearance;
    private boolean fixed;
    public IndexBuffer(int capacity) { this(capacity,0); }
    public IndexBuffer(int capacity,int flags) {
        super(MCE_CLASS_TYPE_INDEXBUFFER);
        if(capacity<0) throw new IllegalArgumentException("Index capacity");
    }
    public void set(int type,int first,int[] lengths) {
        requireLive(); checkType(type); this.first=first; this.lengths=lengths.clone(); indices=null; fixed=false;
    }
    public void set(int type,int[] lengths,short[] indices) {
        requireLive(); checkType(type); this.lengths=lengths.clone(); this.indices=indices.clone(); fixed=false;
    }
    private void checkType(int value) {
        if(value<1||value>7) throw new IllegalArgumentException("Primitive type"); type=value;
    }
    public void fix() { requireLive(); fixed=true; }
    public boolean isFix() { return fixed; }
    public void setAppearance(Appearance value) { requireLive(); appearance=value; }
    public Appearance getAppearance() { return appearance; }
    public int getIndexType() { return type; }
    public int getIndexLengths(int[] result) {
        if(result!=null) System.arraycopy(lengths,0,result,0,lengths.length); return lengths.length;
    }
    @Override List<? extends Object3D> references() { return appearance==null?List.of():List.of(appearance); }
}
