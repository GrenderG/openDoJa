package com.docomostar.opt.ui.eruption;

import java.util.ArrayList;
import java.util.List;

public final class Figure extends Object3D {
    VertexBuffer vertices;
    final List<IndexBuffer> indices=new ArrayList<>();
    final List<Part> parts=new ArrayList<>();
    final Transform transform=new Transform();
    EruptionModel.Tree tree;
    EruptionModel.Skin skin;
    VertexBuffer posed;
    private Camera camera;
    private ActionTable action;
    private float frame;
    record Part(Figure figure,int node) { }
    public Figure(VertexBuffer vertices,IndexBuffer[] indices) {
        this(); this.vertices=vertices;this.indices.addAll(List.of(indices));
    }
    Figure() { super(MCE_CLASS_TYPE_FIGURE); }
    public VertexBuffer getVertexBuffer() { return vertices; }
    public IndexBuffer[] getIndexBuffer() { return indices.toArray(IndexBuffer[]::new); }
    public void setCamera(Camera camera) { requireLive(); this.camera=camera; }
    public Camera getCamera() { return camera; }
    public void setFrame(float frame) { requireLive(); this.frame=frame; }
    public float getFrame() { return frame; }
    public void bindAction(ActionTable action) { requireLive();this.action=action; }
    ActionTable action() { return action; }
    public void bindTexture(TextureTable table) {
        requireLive();
        for(IndexBuffer index:indices) if(index.appearance!=null)
            for(int unit=0;unit<2;unit++) for(Texture texture:table.textures)
                if(texture.gid==index.appearance.textureIds[unit]) index.appearance.textures[unit]=texture;
        for(Part part:parts) part.figure.bindTexture(table);
    }
    @Override List<? extends Object3D> references() {
        List<Object3D> result=new ArrayList<>(indices);
        if(vertices!=null) result.add(vertices);
        for(Part part:parts) result.add(part.figure);
        return result;
    }
}
