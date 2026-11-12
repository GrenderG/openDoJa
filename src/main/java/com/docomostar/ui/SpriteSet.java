package com.docomostar.ui;

import java.util.Objects;

/** Retains the application's live array while delegating drawing and collision to DoJa. */
public class SpriteSet {
    private final Sprite[] sprites;
    private final com.nttdocomo.ui.Sprite[] hostSprites;
    private final com.nttdocomo.ui.SpriteSet delegate;

    public SpriteSet(Sprite[] sprites) {
        this.sprites = Objects.requireNonNull(sprites, "sprites");
        hostSprites = new com.nttdocomo.ui.Sprite[sprites.length];
        delegate = new com.nttdocomo.ui.SpriteSet(hostSprites);
        host();
    }
    com.nttdocomo.ui.SpriteSet host() {
        for (int i = 0; i < sprites.length; i++) {
            hostSprites[i] = sprites[i] == null ? null : sprites[i].delegate;
        }
        return delegate;
    }
    public int getCount() { return delegate.getCount(); }
    public Sprite[] getSprites() { return sprites; }
    public Sprite getSprite(int index) { return sprites[index]; }
    public void setCollisionAll() { host().setCollisionAll(); }
    public void setCollisionOf(int index) { host().setCollisionOf(index); }
    public boolean isCollision(int left, int right) { return delegate.isCollision(left, right); }
    public int getCollisionFlag(int index) { return delegate.getCollisionFlag(index); }
}
