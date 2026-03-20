package com.nttdocomo.ui.graphics3d;

import opendoja.g3d.MascotActionTableData;

public class ActionTable extends Object3D {
    private final MascotActionTableData handle;

    ActionTable(MascotActionTableData handle) {
        super(TYPE_ACTION_TABLE);
        this.handle = handle;
    }

    public int getNumActions() {
        return handle == null ? 0 : handle.numActions();
    }

    public int getMaxFrame(int action) {
        return handle.maxFrame(action);
    }

    MascotActionTableData handle() {
        return handle;
    }
}
