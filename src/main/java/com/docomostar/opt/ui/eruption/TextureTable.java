package com.docomostar.opt.ui.eruption;

import java.util.List;

public final class TextureTable extends Object3D {
    final List<Texture> textures;
    TextureTable(List<Texture> textures) { super(MCE_CLASS_TYPE_TEXTURETABLE); this.textures=List.copyOf(textures); }
    public Texture getTexture(int index) { return textures.get(index); }
    public int getTextureNum() { return textures.size(); }
    public void purgeGLobjectAll(Graphics3D graphics) { for(Texture t:textures) graphics.purge(t); }
    @Override List<? extends Object3D> references() { return textures; }
}
