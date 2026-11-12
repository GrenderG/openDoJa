package com.docomostar.opt.ui.j3d;

import java.io.IOException;
import java.io.InputStream;

/** Shares the existing DoJa Mascot model loader and renderer. */
public class Figure extends com.nttdocomo.opt.ui.j3d.Figure {
    public Figure(byte[] data) { super(data); }
    public Figure(InputStream input) throws IOException { super(input); }
    public void setTexture(Texture texture) { super.setTexture(texture); }
    public void setTexture(Texture[] textures) { super.setTexture(textures); }
    public void setPosture(ActionTable actions, int action, int frame) { super.setPosture(actions, action, frame); }
}
