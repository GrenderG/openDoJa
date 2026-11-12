package com.docomostar.opt.ui;

import com.docomostar.media.MediaImage;
import com.docomostar.media.MediaManager;
import com.docomostar.opt.ui.j3d.AffineTrans;
import com.docomostar.ui.Graphics;
import com.docomostar.ui.Image;
import com.docomostar.ui.UIException;

/** STAR optional 2D operations use the shared DoJa renderer and surface. */
public class Graphics2 extends Graphics {
    public static final int CM_NORMAL = 0;
    public static final int CM_ZOOM = 256;
    public static final int OP_REPL = 0;
    public static final int OP_ADD = 1;
    public static final int OP_SUB = 2;

    protected Graphics2() { super(); }
    /** Host integration; application graphics are created by Canvas and Image. */
    public Graphics2(com.nttdocomo.opt.ui.Graphics2 graphics, boolean initializeFont) {
        super(graphics, initializeFont);
    }
    private com.nttdocomo.opt.ui.Graphics2 graphics() {
        return (com.nttdocomo.opt.ui.Graphics2) delegate;
    }
    public void setCoordinateMode(int mode) { graphics().setCoordinateMode(mode); }
    public void setRenderMode(int operator, int sourceRatio, int destinationRatio) {
        graphics().setRenderMode(operator, sourceRatio, destinationRatio);
    }
    public Image getImage(int x, int y, int width, int height) {
        try { return Image.wrap(graphics().getImage(x, y, width, height)); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void drawNumber(int x, int y, int value, int digits) {
        try { graphics().drawNumber(x, y, value, digits); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void drawNthImage(MediaImage image, int index, int x, int y) {
        try { graphics().drawNthImage((com.nttdocomo.ui.MediaImage) MediaManager.unwrap(image), index, x, y); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public static int getIntermediateColor(int first, int second, int ratio) {
        return com.nttdocomo.opt.ui.Graphics2.getIntermediateColor(first, second, ratio);
    }
    public void drawImage(Image image, AffineTrans transform, int x, int y, int width, int height) {
        try { graphics().drawImage(Image.unwrap(image), transform, x, y, width, height); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void drawImage(Image image, AffineTrans transform) {
        try { graphics().drawImage(Image.unwrap(image), transform); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public int getSyncUnlockInterval() { return graphics().getSyncUnlockInterval(); }
    public int syncUnlock(int interval) { return graphics().syncUnlock(interval); }
}
