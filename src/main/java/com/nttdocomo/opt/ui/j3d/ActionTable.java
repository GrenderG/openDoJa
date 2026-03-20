package com.nttdocomo.opt.ui.j3d;

import opendoja.g3d.MascotActionTableData;
import opendoja.g3d.MascotLoader;

import java.io.IOException;
import java.io.InputStream;

public class ActionTable {
    private final MascotActionTableData handle;

    public ActionTable(byte[] data) {
        try {
            this.handle = MascotLoader.loadActionTable(data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public ActionTable(InputStream inputStream) throws IOException {
        this.handle = MascotLoader.loadActionTable(inputStream);
    }

    ActionTable(MascotActionTableData handle) {
        this.handle = handle;
    }

    public int getNumAction() {
        return handle == null ? 0 : handle.numActions();
    }

    public int getMaxFrame(int action) {
        return handle.maxFrame(action);
    }

    MascotActionTableData handle() {
        return handle;
    }
}
