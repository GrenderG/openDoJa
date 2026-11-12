package com.docomostar.opt.ui.j3d;

import java.io.IOException;
import java.io.InputStream;

public class Texture extends com.nttdocomo.opt.ui.j3d.Texture {
    public Texture(byte[] data, boolean forEnvironment) { super(data, forEnvironment); }
    public Texture(InputStream stream, boolean forEnvironment) throws IOException { super(stream, forEnvironment); }
}
