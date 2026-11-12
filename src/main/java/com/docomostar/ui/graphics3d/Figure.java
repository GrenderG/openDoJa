package com.docomostar.ui.graphics3d;

public class Figure extends DrawableObject3D {
    private ActionTable actionTable;
    Figure(com.nttdocomo.ui.graphics3d.Figure delegate) { super(delegate); }
    private com.nttdocomo.ui.graphics3d.Figure figure() { return (com.nttdocomo.ui.graphics3d.Figure) delegate; }
    public void setTexture(Texture texture) { figure().setTexture((com.nttdocomo.ui.graphics3d.Texture) unwrap(texture)); }
    public void setTextures(Texture[] textures) {
        var host = new com.nttdocomo.ui.graphics3d.Texture[textures.length];
        for (int i = 0; i < textures.length; i++) host[i] = (com.nttdocomo.ui.graphics3d.Texture) unwrap(textures[i]);
        figure().setTextures(host);
    }
    public void setAction(ActionTable table, int action) {
        figure().setAction((com.nttdocomo.ui.graphics3d.ActionTable) unwrap(table), action);
        actionTable = table;
    }
    public ActionTable getActionTable() {
        if (actionTable == null) actionTable = (ActionTable) wrap(figure().getActionTable());
        return actionTable;
    }
    public int getNumPatterns() { return figure().getNumPatterns(); }
    public void setPattern(int pattern) { figure().setPattern(pattern); }
}
