package com.docomostar.opt.ui.eruption;

public final class RegionF {
    private float x0, y0, x1, y1;
    public RegionF() { }
    public RegionF(float x0, float y0, float x1, float y1) { set(x0, y0, x1, y1); }
    public void set(float x0, float y0, float x1, float y1) { this.x0=x0; this.y0=y0; this.x1=x1; this.y1=y1; }
    public float getX0() { return x0; }
    public float getY0() { return y0; }
    public float getX1() { return x1; }
    public float getY1() { return y1; }
    public void copy(RegionF source) { set(source.x0, source.y0, source.x1, source.y1); }
    public boolean compare(RegionF r) { return x0==r.x0 && y0==r.y0 && x1==r.x1 && y1==r.y1; }
    public boolean intersect(RegionF a, RegionF b) {
        set(Math.max(a.x0,b.x0), Math.max(a.y0,b.y0), Math.min(a.x1,b.x1), Math.min(a.y1,b.y1));
        return x0 < x1 && y0 < y1;
    }
    public void dispose() { }
}
