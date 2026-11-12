package com.docomostar.opt.ui.eruption;

import opendoja.host.ogl.HostDirectBuffers.HostFloatBuffer;
import opendoja.host.ogl.HostDirectBuffers.HostByteBuffer;

public final class VertexBuffer extends Object3D {
    public static final int MCE_POSITION=1, MCE_NORMAL=2, MCE_BINORMAL=4, MCE_TANGENT=8,
            MCE_COLOR=16, MCE_TEXTURE2D_0=32, MCE_TEXTURE2D_1=64, MCE_SPRITE=128;
    private final int params, count;
    final int[] streamCounts=new int[8];
    final float[] positions, normals;
    final byte[] colors;
    final float[][] texCoords=new float[2][];
    final HostFloatBuffer positionBuffer, normalBuffer;
    final HostByteBuffer colorBuffer;
    final HostFloatBuffer[] uvBuffers=new HostFloatBuffer[2];
    public VertexBuffer(int params, int count) {
        super(MCE_CLASS_TYPE_VERTEXBUFFER);
        if(count<0 || count>1_000_000) throw new IllegalArgumentException("Vertex count");
        this.params=params; this.count=count;
        for(int stream=0;stream<8;stream++) if((params&(1<<stream))!=0) streamCounts[stream]=count;
        positions=new float[Math.multiplyExact(count,3)];
        normals=(params&MCE_NORMAL)!=0 ? new float[count*3] : null;
        colors=(params&MCE_COLOR)!=0 ? new byte[count*4] : null;
        if(colors!=null) java.util.Arrays.fill(colors,(byte)255);
        positionBuffer=new HostFloatBuffer(positions);
        normalBuffer=normals==null?null:new HostFloatBuffer(normals);
        colorBuffer=colors==null?null:new HostByteBuffer(colors);
        for(int unit=0;unit<2;unit++) if((params&(32<<unit))!=0) {
            texCoords[unit]=new float[count*2]; uvBuffers[unit]=new HostFloatBuffer(texCoords[unit]);
        }
    }
    public Object getVerticesArray(int component) {
        requireLive();
        return switch(component) {
            case MCE_POSITION -> positions;
            case MCE_NORMAL -> normals;
            case MCE_COLOR -> colors;
            case MCE_TEXTURE2D_0 -> texCoords[0];
            case MCE_TEXTURE2D_1 -> texCoords[1];
            default -> throw new IllegalArgumentException("Vertex component");
        };
    }
    public void updateVertices(int component) { requireLive(); getVerticesArray(component); }
    public int getParams() { return params; }
    public int getMaxVerticesNum() { return count; }
    public int getVerticesNum(int component) {
        getVerticesArray(component);
        return streamCounts[Integer.numberOfTrailingZeros(component)];
    }
    public int getVerticesComponentNum(int component) { return component==16?4:component==32||component==64?2:3; }
    public void setVertices(int component,int first,float[] values,int offset,int length) {
        System.arraycopy(values,offset,(float[])getVerticesArray(component),first*getVerticesComponentNum(component),length);
    }
    public void setVertices(int component,int first,byte[] values,int offset,int length) {
        System.arraycopy(values,offset,(byte[])getVerticesArray(component),first*getVerticesComponentNum(component),length);
    }
}
