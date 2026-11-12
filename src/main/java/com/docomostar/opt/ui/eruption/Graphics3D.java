package com.docomostar.opt.ui.eruption;

import com.docomostar.ui.Canvas;
import com.nttdocomo.ui.ogl.GraphicsOGL;
import com.nttdocomo.ui.ogl.math.Matrix4f;
import com.nttdocomo.ui.ogl.math.Point3f;
import com.nttdocomo.ui.ogl.math.Vector4f;
import opendoja.host.ogl.HostDirectBuffers.HostByteBuffer;
import opendoja.host.ogl.HostDirectBuffers.HostFloatBuffer;
import opendoja.host.ogl.HostDirectBuffers.HostShortBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import static com.nttdocomo.ui.ogl.GraphicsOGL.*;

/** Eruption scene submission delegated to DoJa's shared GLES renderer and canvas surface. */
public final class Graphics3D {
    public static final int MCE_CLEAR_DEPTH=1, MCE_CLEAR_COLOR=2, MCE_CAMERA_SORT=1, MCE_FRUSTUM_CULLING=2;
    private static final int[] SOURCE_BLEND_FACTORS={GL_ZERO,GL_ONE,GL_DST_COLOR,GL_ONE_MINUS_DST_COLOR,
            GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA,GL_DST_ALPHA,GL_ONE_MINUS_DST_ALPHA,GL_SRC_ALPHA_SATURATE};
    private static final int[] DESTINATION_BLEND_FACTORS={GL_ZERO,GL_ONE,GL_SRC_COLOR,GL_ONE_MINUS_SRC_COLOR,
            GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA,GL_DST_ALPHA,GL_ONE_MINUS_DST_ALPHA};
    private final GraphicsOGL defaultGl;
    private GraphicsOGL gl;
    private final List<Runnable> queue=new ArrayList<>();
    private final Map<GraphicsOGL,Map<Texture,int[]>> uploadsByContext=new IdentityHashMap<>();
    private final Matrix4f view=new Matrix4f(),projection=new Matrix4f();
    private int x,y,width,height;
    private int view2dX,view2dY,view2dWidth,view2dHeight;
    private Camera camera;
    private boolean disposed;
    private Graphics3D(Canvas canvas) {
        defaultGl=gl=canvas.getGraphics().hostGraphicsOGL();
        width=canvas.getWidth();height=canvas.getHeight();view2dWidth=width;view2dHeight=height;
        view.setIdentity();projection.setIdentity();
    }
    public static Graphics3D getInstance(Canvas canvas) { return new Graphics3D(Objects.requireNonNull(canvas)); }
    private void requireLive() {
        if(disposed) throw new IllegalStateException("Disposed Eruption renderer");
        gl=opendoja.host.ogl.EruptionGraphicsContext.current(defaultGl);
    }
    public void setViewport(int x,int y,int width,int height) {
        requireLive();if(width<=0||height<=0) throw new IllegalArgumentException("Viewport");
        this.x=x;this.y=y;this.width=width;this.height=height;gl.glViewport(x,y,width,height);
    }
    public void setClip(int x,int y,int width,int height) {
        requireLive();gl.glEnable(GL_SCISSOR_TEST);gl.glScissor(x,this.height-y-height,width,height);
    }
    public void setViewport2D(int x,int y,int width,int height) {
        requireLive();if(width<=0||height<=0) throw new IllegalArgumentException("2D viewport");
        view2dX=x;view2dY=y;view2dWidth=width;view2dHeight=height;
    }
    /** Desktop output uses a single view; stereoscopic separation has no effect. */
    public void setStereoCameraParam(float separation,float distance) { }
    public void setStereoCameraParam2(float separation,float distance) { }
    public void setStereoReduceFactor(float factor) { }
    public float getStereoReduceFactor() { return 0; }
    public void clear(int flags,int color) {
        requireLive();gl.glDepthMask(true);gl.glColorMask(true,true,true,true);
        gl.glClearColor((color&255)/255f,(color>>>8&255)/255f,(color>>>16&255)/255f,(color>>>24)/255f);
        gl.glClear(((flags&1)!=0?GL_DEPTH_BUFFER_BIT:0)|((flags&2)!=0?GL_COLOR_BUFFER_BIT:0));
    }
    public void setCamera(Camera camera,Vector3D position,Vector3D direction,Vector3D up) {
        requireLive();this.camera=Objects.requireNonNull(camera);
        // Eruption takes a direction vector rather than an absolute target point.
        view.lookAt(new Point3f(position.getX(),position.getY(),position.getZ()),
                new Point3f(position.getX()+direction.getX(),position.getY()+direction.getY(),position.getZ()+direction.getZ()),up.value);
        applyCamera();
    }
    public void setCamera(Camera camera,Transform transform) {
        requireLive();this.camera=Objects.requireNonNull(camera);
        System.arraycopy(transform.matrix.m,0,view.m,0,16);view.invert();applyCamera();
    }
    private void applyCamera() {
        gl.glMatrixMode(GL_PROJECTION);gl.glLoadIdentity();
        if(camera.getCameraType()==Camera.MCE_CAMERA_TYPE_PARALLEL)
            gl.glOrthof(-camera.getWidth()/2f,camera.getWidth()/2f,-camera.getHeight()/2f,camera.getHeight()/2f,camera.getNear(),camera.getFar());
        else {
            float top=(float)Math.tan(camera.getAngle()*Math.PI)*camera.getNear();
            gl.glFrustumf(-top*camera.getAspect(),top*camera.getAspect(),-top,top,camera.getNear(),camera.getFar());
        }
        gl.glGetFloatv(GL_PROJECTION_MATRIX,projection.m);
        gl.glMatrixMode(GL_MODELVIEW);gl.glLoadMatrixf(view.m);
    }
    public void getProjectedPoint(Vector3D point,Vector3D result) {
        requireLive();Vector4f value=new Vector4f(point.getX(),point.getY(),point.getZ(),1);
        view.transform(value);projection.transform(value);
        float inverse=value.w==0?1:1/value.w;
        result.set(x+(value.x*inverse+1)*width/2,y+(1-value.y*inverse)*height/2,value.z*inverse);
    }
    public void addDrawQueueFigure(Figure figure,Transform transform) {
        requireLive();Objects.requireNonNull(figure).requireLive();Transform copy=transform.snapshot();
        float frame=figure.getFrame();ActionTable action=figure.action();
        queue.add(()->drawFigure(figure,copy,action,frame));
    }
    public void drawFigure(Figure figure,Transform transform,int flags) {
        drawFigure(figure,transform,figure.action(),figure.getFrame());
    }
    private void drawFigure(Figure figure,Transform transform,ActionTable action,float frame) {
        Transform[] world=figure.tree==null?null:figure.tree.world(action,frame);
        drawFigure(figure,transform,figure.tree,world,action,frame);
    }
    private void drawFigure(Figure figure,Transform transform,EruptionModel.Tree tree,Transform[] world,ActionTable action,float frame) {
        if(!figure.isRenderEnable()) return;
        VertexBuffer vertices=figure.vertices;
        if(vertices!=null&&figure.skin!=null&&tree!=null) vertices=figure.posed=figure.skin.pose(vertices,tree,world,figure.posed);
        if(vertices!=null) for(IndexBuffer index:figure.indices) drawVertexBuffer(vertices,index,transform);
        for(Figure.Part part:figure.parts) {
            if(action!=null&&!action.visible(part.node(),frame)) continue;
            Transform child=transform.snapshot();
            // Skinned vertices are already in the transform tree's world space.
            if(part.figure().skin==null&&world!=null&&part.node()>=0&&part.node()<world.length) child.multiply(world[part.node()]);
            drawFigure(part.figure(),child,tree,world,action,frame);
        }
    }
    public void flushDrawQueue(int flags) {
        requireLive();List<Runnable> pending=new ArrayList<>(queue);queue.clear();for(Runnable draw:pending) draw.run();
    }
    public void resetDrawQueue() { queue.clear(); }
    public void flush() { requireLive();gl.glFlush(); }
    public void finish() { flushDrawQueue(0);gl.glFlush(); }
    public void drawVertexBuffer(VertexBuffer vertices,IndexBuffer indices,Transform transform) {
        requireLive();vertices.requireLive();indices.requireLive();
        Appearance appearance=indices.appearance;
        if(appearance==null) appearance=new Appearance();
        if((appearance.properties&Appearance.MCE_VISIBLE)==0) return;
        if(camera!=null) applyCamera();
        Matrix4f model=new Matrix4f();model.mul(view,transform.matrix);
        gl.glMatrixMode(GL_MODELVIEW);gl.glLoadMatrixf(model.m);
        applyAppearance(appearance,vertices);
        gl.glEnableClientState(GL_VERTEX_ARRAY);gl.glVertexPointer(3,GL_FLOAT,0,vertices.positionBuffer);
        int mode=switch(indices.type) {
            case 1 -> GL_POINTS;case 2 -> GL_LINES;case 3 -> GL_LINE_STRIP;
            case 4 -> GL_TRIANGLES;case 5 -> GL_TRIANGLE_STRIP;case 6 -> GL_TRIANGLE_FAN;
            default -> throw new IllegalArgumentException("Unsupported Eruption primitive: "+indices.type);
        };
        int at=0;
        for(int length:indices.lengths) {
            if(indices.indices==null) gl.glDrawArrays(mode,indices.first+at,length);
            else {
                HostShortBuffer buffer=new HostShortBuffer(indices.indices);buffer.setSegment(at,length);
                gl.glDrawElements(mode,length,GL_UNSIGNED_SHORT,buffer);
            }
            at+=length;
        }
    }
    private void applyAppearance(Appearance a,VertexBuffer vertices) {
        enabled(GL_DEPTH_TEST,(a.properties&8)!=0);gl.glDepthFunc(GL_LEQUAL);gl.glDepthMask((a.properties&16)!=0);
        boolean color=(a.properties&4)!=0;gl.glColorMask(color,color,color,(a.properties&2)!=0);
        enabled(GL_CULL_FACE,a.cullingMode!=2);gl.glCullFace(a.cullingMode==1?GL_FRONT:GL_BACK);
        gl.glDisable(GL_LIGHTING);
        enabled(GL_BLEND,a.blendSource!=Appearance.MCE_BLEND_MODE_SRC_ONE||a.blendDestination!=Appearance.MCE_BLEND_MODE_DST_ZERO);
        gl.glBlendFunc(SOURCE_BLEND_FACTORS[a.blendSource],DESTINATION_BLEND_FACTORS[a.blendDestination]);
        enabled(GL_ALPHA_TEST,a.alphaThreshold>0);gl.glAlphaFunc(GL_GREATER,a.alphaThreshold/255f);
        boolean colors=(a.properties&64)!=0&&vertices!=null&&vertices.colorBuffer!=null;
        if(colors) { gl.glEnableClientState(GL_COLOR_ARRAY);gl.glColorPointer(4,GL_UNSIGNED_BYTE,0,vertices.colorBuffer); }
        else gl.glDisableClientState(GL_COLOR_ARRAY);
        setColor(a.colors[Appearance.MCE_MATERIAL_TYPE_DIFFUSE]);
        for(int unit=0;unit<2;unit++) {
            gl.glActiveTexture(GL_TEXTURE0+unit);gl.glClientActiveTexture(GL_TEXTURE0+unit);
            Texture texture=a.textures[unit];enabled(GL_TEXTURE_2D,texture!=null);
            if(texture!=null) {
                gl.glBindTexture(GL_TEXTURE_2D,upload(texture));
                gl.glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,a.filters[unit][0]==1?GL_LINEAR:GL_NEAREST);
                gl.glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,a.filters[unit][1]==1?GL_LINEAR:GL_NEAREST);
                gl.glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);
                gl.glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
                gl.glTexEnvi(GL_TEXTURE_ENV,GL_TEXTURE_ENV_MODE,a.textureBlend[unit]==0?GL_REPLACE:GL_MODULATE);
            }
            if(texture!=null&&vertices!=null&&vertices.uvBuffers[unit]!=null) {
                gl.glEnableClientState(GL_TEXTURE_COORD_ARRAY);gl.glTexCoordPointer(2,GL_FLOAT,0,vertices.uvBuffers[unit]);
            } else gl.glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        }
        gl.glActiveTexture(GL_TEXTURE0);gl.glClientActiveTexture(GL_TEXTURE0);
    }
    private void enabled(int cap,boolean enabled) { if(enabled) gl.glEnable(cap);else gl.glDisable(cap); }
    private void setColor(int color) {
        // Eruption render colours use red in the least significant byte (ABGR).
        gl.glColor4f((color&255)/255f,(color>>>8&255)/255f,(color>>>16&255)/255f,(color>>>24)/255f);
    }
    int upload(Texture texture) {
        requireLive();texture.requireLive();
        Map<Texture,int[]> uploaded=uploadsByContext.computeIfAbsent(gl,ignored->new IdentityHashMap<>());
        int[] state=uploaded.get(texture);
        if(state==null) { int[] id=new int[1];gl.glGenTextures(1,id);state=new int[]{id[0],-1};uploaded.put(texture,state); }
        gl.glBindTexture(GL_TEXTURE_2D,state[0]);
        if(state[1]!=texture.revision) {
            byte[] rgba=new byte[texture.pixels.length*4];
            for(int i=0;i<texture.pixels.length;i++) {
                int argb=texture.pixels[i];rgba[i*4]=(byte)(argb>>>16);rgba[i*4+1]=(byte)(argb>>>8);
                rgba[i*4+2]=(byte)argb;rgba[i*4+3]=(byte)(argb>>>24);
            }
            gl.glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,texture.width,texture.height,0,GL_RGBA,GL_UNSIGNED_BYTE,new HostByteBuffer(rgba));
            state[1]=texture.revision;
        }
        return state[0];
    }
    void purge(Texture texture) {
        requireLive();
        for(var entry:uploadsByContext.entrySet()) {
            int[] state=entry.getValue().remove(texture);
            if(state!=null) entry.getKey().glDeleteTextures(1,new int[]{state[0]});
        }
    }
    public void drawRect(short x0,short y0,short rectWidth,short rectHeight,float depth,int color,Appearance appearance,RegionF[] uv) {
        requireLive();applyAppearance(appearance,null);
        if(rectWidth<=0||rectHeight<=0) return;
        int x1=x0+rectWidth,y1=y0+rectHeight;
        gl.glViewport(x,y,width,height);gl.glMatrixMode(GL_PROJECTION);gl.glLoadIdentity();
        gl.glOrthof(view2dX,view2dX+view2dWidth,view2dY+view2dHeight,view2dY,-1,1);
        gl.glMatrixMode(GL_MODELVIEW);gl.glLoadIdentity();gl.glDisable(GL_CULL_FACE);
        setColor(color);
        gl.glDisableClientState(GL_COLOR_ARRAY);gl.glEnableClientState(GL_VERTEX_ARRAY);
        gl.glVertexPointer(3,GL_FLOAT,0,new HostFloatBuffer(new float[]{x0,y0,depth,x0,y1,depth,x1,y0,depth,x1,y1,depth}));
        for(int unit=0;unit<2;unit++) if(appearance.textures[unit]!=null) {
            RegionF region=uv!=null&&unit<uv.length?uv[unit]:null;
            float u0=region==null?0:region.getX0(),v0=region==null?0:region.getY0();
            float u1=region==null?1:region.getX1(),v1=region==null?1:region.getY1();
            gl.glClientActiveTexture(GL_TEXTURE0+unit);gl.glEnableClientState(GL_TEXTURE_COORD_ARRAY);
            gl.glTexCoordPointer(2,GL_FLOAT,0,new HostFloatBuffer(new float[]{u0,v0,u0,v1,u1,v0,u1,v1}));
        }
        gl.glClientActiveTexture(GL_TEXTURE0);gl.glDrawArrays(GL_TRIANGLE_STRIP,0,4);
        if(camera!=null) applyCamera();
    }
    public void dispose() {
        if(disposed)return;
        for(var entry:uploadsByContext.entrySet()) for(int[] state:entry.getValue().values())
            entry.getKey().glDeleteTextures(1,new int[]{state[0]});
        uploadsByContext.clear();queue.clear();disposed=true;
    }
}
