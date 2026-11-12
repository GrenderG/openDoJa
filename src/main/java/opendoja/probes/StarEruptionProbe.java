package opendoja.probes;

import com.docomostar.opt.ui.eruption.*;
import com.docomostar.opt.ui.stereoscope.StereoscopicCanvas;
import com.docomostar.ui.*;
import opendoja.host.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.jar.JarFile;

/** Asset-free regression checks; optionally also checks every Eruption asset in a game JAR. */
public final class StarEruptionProbe {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        transforms();
        loaders();
        DesktopLauncher.launch(LaunchConfig.builder(StarApp.class).parameter("AppType", "FullApp").build());
        try { render(); }
        finally { DoJaRuntime.current().shutdown(); }
        if(args.length>0) try(var jar=new JarFile(args[0])) {
            int assets=0;
            for(var entries=jar.entries();entries.hasMoreElements();) {
                var entry=entries.nextElement();
                if(entry.getName().matches(".*\\.mc[mta]")) try(var input=jar.getInputStream(entry)) {
                    check(Loader.load(input,0).length>0,"Asset roots: "+entry.getName());assets++;
                }
            }
            check(assets>0,"Game contains Eruption assets");
            System.out.println("eruption-assets-ok: "+assets);
        }
        System.out.println("star-eruption-probe-ok");
    }
    private static void transforms() {
        float[] rowMajor={1,0,0,7, 0,1,0,8, 0,0,1,9, 0,0,0,1};
        Transform transform=new Transform(rowMajor);
        rowMajor[3]=99;
        Vector3D point=new Vector3D(1,2,3);
        transform.transPosition(point);
        check(point.getX()==8&&point.getY()==10&&point.getZ()==12,"Row-major public translation");
        float[] roundTrip=new float[16];transform.get(roundTrip);
        check(roundTrip[3]==7&&roundTrip[7]==8&&roundTrip[11]==9,"Row-major get and independent storage");
        Transform inverse=new Transform();inverse.setInvert(transform);inverse.transPosition(point);
        check(point.getX()==1&&point.getY()==2&&point.getZ()==3,"Shared matrix inversion");
        Transform scale=new Transform();scale.setScale(2,3,4);transform.multiply(scale);
        point.set(1,1,1);transform.transPosition(point);
        check(point.getX()==9&&point.getY()==11&&point.getZ()==13,"Translation times scale");
    }
    private static void loaders() throws Exception {
        // A two-pixel palette texture with GIF-style LZW clear/literal/end codes.
        ByteBuffer b=ByteBuffer.allocate(59).order(ByteOrder.LITTLE_ENDIAN);
        b.put(new byte[]{'M','C','T','_'}).putInt(0).putShort((short)1);
        b.putInt(49).putInt(0).putInt(31).putInt(73).putInt(27).putInt(4);
        b.putShort((short)2).putShort((short)1).put((byte)1).put((byte)0).put((byte)4).put((byte)0);
        // Minimum code size 8: clear(256), 0, 248, 1, 0, end(257).
        int[] codes={256,0,248,1,0,257};long packed=0;
        for(int i=0;i<codes.length;i++) packed|=(long)codes[i]<<(i*9);
        b.put((byte)8).put((byte)7);
        for(int i=0;i<7;i++)b.put((byte)(packed>>>(i*8)));
        b.put((byte)0);
        byte[] asset=Arrays.copyOf(b.array(),b.position());
        ByteBuffer.wrap(asset).order(ByteOrder.LITTLE_ENDIAN).putInt(10,asset.length-10);
        Object3D root=Loader.load(asset,0)[0];
        Texture texture=(Texture)root.findObject3D(Object3D.MCE_CLASS_TYPE_TEXTURE,73);
        check(texture!=null&&texture.getGid()==27,"Record user ID is independent of internal ID");
        for(byte[] bad:new byte[][]{new byte[0],Arrays.copyOf(asset,asset.length-4),{'N','O','P','E',0,0,0,0}}) {
            try { Loader.load(bad,0);throw new AssertionError("Malformed asset accepted"); }
            catch(IOException expected) { }
        }
    }
    private static void render() {
        Graphics3D renderer=Graphics3D.getInstance((Canvas)Display.getCurrent());
        Camera camera=new Camera();camera.setParallel(2,2,0.1f,10);
        VertexBuffer vertices=new VertexBuffer(VertexBuffer.MCE_POSITION,4);
        float[] positions=(float[])vertices.getVerticesArray(VertexBuffer.MCE_POSITION);
        System.arraycopy(new float[]{-1,-1,0, 0,-1,0, -1,1,0, 0,1,0},0,positions,0,12);
        vertices.updateVertices(VertexBuffer.MCE_POSITION);
        Appearance appearance=new Appearance();appearance.setCullingMode(Appearance.MCE_CULLING_MODE_CULL_NONE);
        appearance.setBlendMode(Appearance.MCE_BLEND_MODE_REPLACE);
        IndexBuffer indices=new IndexBuffer(4);indices.set(IndexBuffer.MCE_TRIANGLE_STRIP,new int[]{4},new short[]{0,1,2,3});
        indices.setAppearance(appearance);indices.fix();
        Figure figure=new Figure(vertices,new IndexBuffer[]{indices});
        Graphics graphics=Image.createImage(64,64).getGraphics();
        graphics.beginEruption();
        try {
            renderer.setViewport(0,0,64,64);renderer.setViewport2D(0,0,64,64);
            renderer.clear(Graphics3D.MCE_CLEAR_COLOR|Graphics3D.MCE_CLEAR_DEPTH,0xffff0000);
            renderer.setCamera(camera,new Vector3D(),new Vector3D(0,0,-1),new Vector3D(0,1,0));
            Vector3D screen=new Vector3D();renderer.getProjectedPoint(new Vector3D(0,0,-2),screen);
            check(screen.getX()==32&&screen.getY()==32,"Projection uses the current STAR surface");
            Transform transform=new Transform();transform.setTranslate(0,0,-2);
            renderer.addDrawQueueFigure(figure,transform);transform.setTranslate(2,0,-2);
            renderer.flushDrawQueue(0);renderer.flush();
            pixel(graphics,8,32,0xffffff,"Queued transform snapshot");pixel(graphics,56,32,0x0000ff,"ABGR clear and queue isolation");
        } finally { graphics.endEruption(); }

        Image source=Image.createImage(2,1);Graphics pixels=source.getGraphics();
        pixels.setColor(0xffff00ff);pixels.fillRect(0,0,1,1);pixels.setColor(0xff00ff00);pixels.fillRect(1,0,1,1);
        Texture texture=new Texture(1,Texture.MCE_PIXEL_FORMAT_RGBA5551,2,1,false);
        texture.setImage(Texture.MCE_PIXEL_FORMAT_RGBA5551,0,0,0,source,0xff00ff00);
        Appearance overlay=new Appearance();overlay.setProperties(Appearance.MCE_VISIBLE|Appearance.MCE_COLOR_WRITE);
        overlay.setTexture(0,texture);
        // Reuse the renderer on two independently opened image Graphics contexts.
        for(int i=0;i<2;i++) {
            Graphics target=Image.createImage(64,64).getGraphics();target.beginEruption();
            try {
                renderer.clear(Graphics3D.MCE_CLEAR_COLOR|Graphics3D.MCE_CLEAR_DEPTH,0xff000000);
                renderer.drawRect((short)8,(short)8,(short)16,(short)16,0,-1,overlay,null);renderer.flush();
                pixel(target,10,16,0,"RGBA colour key preserves background");
                pixel(target,21,16,0x00ff00,"Texture uploaded to the active Graphics context");
                pixel(target,28,16,0,"Rectangle arguments are width and height");
            } finally { target.endEruption(); }
        }
        renderer.setStereoCameraParam(1,2);renderer.setStereoCameraParam2(1,2);renderer.setStereoReduceFactor(1);
        check(renderer.getStereoReduceFactor()==0,"Monoscopic stereoscopic API stub");
        texture.purgeGLobject(renderer);renderer.dispose();
        check(!figure.equals(vertices)&&figure.equals(figure),"Object identity");
    }
    private static void pixel(Graphics graphics,int x,int y,int expected,String message) {
        int actual=graphics.getRGBPixel(x,y)&0xffffff;
        check(actual==expected,message+": "+Integer.toHexString(actual));
    }
    private static void check(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
    public static final class StarApp extends com.docomostar.StarApplication {
        @Override public void started(int reason) {
            Display.setCurrent(new StereoscopicCanvas(true) { @Override public void paint(Graphics graphics) { } });
        }
    }
}
