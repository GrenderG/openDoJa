package com.docomostar.ui.graphics3d;

public class ActionTable extends Object3D {
    ActionTable(com.nttdocomo.ui.graphics3d.ActionTable delegate) { super(delegate); }
    public int getNumActions() { return ((com.nttdocomo.ui.graphics3d.ActionTable) delegate).getNumActions(); }
    public int getMaxFrame(int action) { return ((com.nttdocomo.ui.graphics3d.ActionTable) delegate).getMaxFrame(action); }
}
