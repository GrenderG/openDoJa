package com.docomostar.opt.ui.j3d;

import java.io.IOException;
import java.io.InputStream;

public class ActionTable extends com.nttdocomo.opt.ui.j3d.ActionTable {
    public ActionTable(byte[] data) { super(data); }
    public ActionTable(InputStream input) throws IOException { super(input); }
}
