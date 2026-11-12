package com.docomostar.opt.ui.eruption;

import com.docomostar.ui.Image;

public final class Texture extends Object3D {
    public static final int MCE_PIXEL_FORMAT_RGB=1, MCE_PIXEL_FORMAT_RGBA=2,
            MCE_PIXEL_FORMAT_RGB565=3, MCE_PIXEL_FORMAT_RGBA5551=4, MCE_PIXEL_FORMAT_RGBA4444=5;
    final int gid, format, width, height;
    int[] pixels;
    int revision;
    public Texture(int gid,int format,int width,int height,boolean mipmap) {
        super(MCE_CLASS_TYPE_TEXTURE);
        if(width<=0||height<=0||width>2048||height>2048) throw new IllegalArgumentException("Texture size");
        this.gid=gid; this.format=format; this.width=width; this.height=height;
        pixels=new int[width*height];
    }
    public int getGid() { return gid; }
    public int getPixelFormat() { return format; }
    public void setImage(int format,int level,int x,int y,Image image,int transparentColor) {
        requireLive();
        if(level!=0) throw new IllegalArgumentException("Only base image upload is supported");
        int w=image.getWidth(),h=image.getHeight();
        if(x<0||y<0||x+w>width||y+h>height) throw new IllegalArgumentException("Texture image bounds");
        int[] source=Image.unwrap(image).getGraphics().getRGBPixels(0,0,w,h,null,0);
        for(int row=0;row<h;row++) for(int col=0;col<w;col++) {
            int rgb=source[row*w+col];
            // Eruption image-upload colours are packed RGBA, including the colour key.
            int alpha=(rgb&0xffffff)==(transparentColor>>>8)?0:255;
            pixels[(y+row)*width+x+col]=alpha<<24|(rgb&0xffffff);
        }
        revision++;
    }
    public void createGLobject(Graphics3D graphics) { graphics.upload(this); }
    public void purgeGLobject(Graphics3D graphics) { graphics.purge(this); }
}
