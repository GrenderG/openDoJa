package com.docomostar.opt.ui.eruption;

/** Model-local bind poses and skeletal weights, independent of the renderer. */
final class EruptionModel {
    static final class Tree {
        final int[] parent;
        final Transform[] local;
        private Transform[] inverseBind;
        Tree(int count) { parent=new int[count];local=new Transform[count]; }
        Transform[] world(ActionTable action,float frame) {
            Transform[] world=new Transform[local.length];
            for(int i=0;i<world.length;i++) resolve(i,world,action,frame,0);
            return world;
        }
        private Transform resolve(int i,Transform[] result,ActionTable action,float frame,int depth) {
            if(depth>local.length) throw new IllegalArgumentException("Cyclic Eruption transform tree");
            if(result[i]!=null) return result[i];
            Transform value=local[i].snapshot();
            if(action!=null) action.animate(i,frame,value);
            int p=parent[i];
            if(p>=0) { Transform combined=resolve(p,result,action,frame,depth+1).snapshot();combined.multiply(value);value=combined; }
            return result[i]=value;
        }
        Transform[] inverseBind() {
            if(inverseBind==null) {
                Transform[] bind=world(null,0);inverseBind=new Transform[bind.length];
                for(int i=0;i<bind.length;i++) { inverseBind[i]=new Transform();inverseBind[i].setInvert(bind[i]); }
            }
            return inverseBind;
        }
    }
    static final class Skin {
        int attachmentNode=-1;
        int[] nodes;
        int[] copies;
        int[][] bones;
        float[][] weights;
        VertexBuffer pose(VertexBuffer source,Tree tree,Transform[] world,VertexBuffer output) {
            if(output==null) output=new VertexBuffer(source.getParams(),source.getMaxVerticesNum());
            Transform[] inverse=tree.inverseBind(),palette=new Transform[nodes.length];
            Transform attachment=null;
            if(attachmentNode>=0) { attachment=new Transform();attachment.setInvert(inverse[attachmentNode]); }
            for(int i=0;i<nodes.length;i++) {
                palette[i]=world[nodes[i]].snapshot();palette[i].multiply(inverse[nodes[i]]);
                // Source vertices are local to the attached figure's bind transform.
                if(attachment!=null) palette[i].multiply(attachment);
            }
            int destination=0;
            for(int vertex=0;vertex<bones.length;vertex++) {
                float x=source.positions[vertex*3],y=source.positions[vertex*3+1],z=source.positions[vertex*3+2];
                float px=0,py=0,pz=0;
                if(bones[vertex].length==0) { px=x;py=y;pz=z; }
                for(int j=0;j<bones[vertex].length;j++) {
                    float[] m=palette[bones[vertex][j]].matrix.m;float weight=weights[vertex][j];
                    px+=(m[0]*x+m[4]*y+m[8]*z+m[12])*weight;
                    py+=(m[1]*x+m[5]*y+m[9]*z+m[13])*weight;
                    pz+=(m[2]*x+m[6]*y+m[10]*z+m[14])*weight;
                }
                for(int copy=0;copy<copies[vertex];copy++) {
                    output.positions[destination*3]=px;output.positions[destination*3+1]=py;output.positions[destination*3+2]=pz;
                    destination++;
                }
            }
            for(int unit=0;unit<2;unit++) if(source.texCoords[unit]!=null)
                System.arraycopy(source.texCoords[unit],0,output.texCoords[unit],0,source.texCoords[unit].length);
            if(source.colors!=null) System.arraycopy(source.colors,0,output.colors,0,source.colors.length);
            return output;
        }
    }
}
