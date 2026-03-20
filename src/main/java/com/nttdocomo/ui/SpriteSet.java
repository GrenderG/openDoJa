package com.nttdocomo.ui;

public class SpriteSet {
    private final Sprite[] sprites;
    private int collisionMask;

    public SpriteSet(Sprite[] sprites) {
        this.sprites = sprites == null ? new Sprite[0] : sprites.clone();
    }

    public int getCount() {
        return sprites.length;
    }

    public Sprite[] getSprites() {
        return sprites.clone();
    }

    public Sprite getSprite(int index) {
        return sprites[index];
    }

    public void setCollisionAll() {
        collisionMask = -1;
    }

    public void setCollisionOf(int index) {
        collisionMask |= 1 << index;
    }

    public boolean isCollision(int leftIndex, int rightIndex) {
        Sprite left = sprites[leftIndex];
        Sprite right = sprites[rightIndex];
        if (left == null || right == null || !left.isVisible() || !right.isVisible()) {
            return false;
        }
        return left.getX() < right.getX() + right.getWidth()
                && left.getX() + left.getWidth() > right.getX()
                && left.getY() < right.getY() + right.getHeight()
                && left.getY() + left.getHeight() > right.getY();
    }

    public int getCollisionFlag(int index) {
        return collisionMask;
    }
}
