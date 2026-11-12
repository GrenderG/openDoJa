package com.docomostar.opt.ui.eruption;

import java.util.ArrayList;
import java.util.List;

public final class ActionTable extends Object3D {
    final List<Track> tracks=new ArrayList<>();
    private float maxFrame;
    ActionTable() { super(MCE_CLASS_TYPE_ACTIONTABLE); }
    void includeMaxFrame(float value) { maxFrame=Math.max(maxFrame,value); }
    public float getMaxFrame() { requireLive();return maxFrame; }
    record Key(float frame,int interpolation,float[][] values) { }
    record Track(int target,int node,int component,Key[] keys) {
        float[] value(float frame) {
            int lo=0;
            while(lo+1<keys.length&&keys[lo+1].frame<=frame) lo++;
            int hi=Math.min(lo+1,keys.length-1);
            Key left=keys[lo],right=keys[hi];float[] value=left.values[0].clone();
            if(lo==hi||left.interpolation==3) return value;
            float t=Math.clamp((frame-left.frame)/(right.frame-left.frame),0,1);
            if(left.interpolation==2&&left.values.length>1&&right.values.length>1) {
                float t2=t*t,t3=t2*t;
                for(int c=0;c<value.length;c++) value[c]=(2*t3-3*t2+1)*value[c]
                        +(-2*t3+3*t2)*right.values[0][c]
                        +(t3-2*t2+t)*left.values[left.values.length-1][c]
                        +(t3-t2)*right.values[1][c];
            } else for(int c=0;c<value.length;c++) value[c]+=(right.values[0][c]-value[c])*t;
            return value;
        }
    }
    void animate(int node,float frame,Transform transform) {
        float[] translation=null,rotation=null,scale=null;
        for(Track track:tracks) if((track.target>>>16)==7&&track.node==node&&track.keys.length>0) {
            switch(track.component) {
                case 1 -> translation=track.value(frame);
                case 9 -> rotation=track.value(frame);
                case 11 -> scale=track.value(frame);
            }
        }
        if(rotation!=null) {
            float x=transform.matrix.m[12],y=transform.matrix.m[13],z=transform.matrix.m[14];
            transform.setRotateEuler(Transform.MCE_ROTATE_EULER_ZXY,new Vector3D(rotation[0],rotation[1],rotation[2]));
            transform.matrix.m[12]=x;transform.matrix.m[13]=y;transform.matrix.m[14]=z;
        }
        if(translation!=null) {
            transform.matrix.m[12]=translation[0];transform.matrix.m[13]=translation[1];transform.matrix.m[14]=translation[2];
        }
        if(scale!=null) {
            if(rotation==null) for(int c=0;c<3;c++) {
                float[] m=transform.matrix.m;
                float length=(float)Math.sqrt(m[c*4]*m[c*4]+m[c*4+1]*m[c*4+1]+m[c*4+2]*m[c*4+2]);
                if(length>0) for(int r=0;r<3;r++) m[c*4+r]/=length;
            }
            transform.matrix.scale(scale[0],scale[1],scale[2]);
        }
    }
    boolean visible(int node,float frame) {
        for(Track track:tracks) if((track.target>>>16)==10&&track.node==node&&track.component==12&&track.keys.length>0)
            return track.value(frame)[0]!=0;
        return true;
    }
}
