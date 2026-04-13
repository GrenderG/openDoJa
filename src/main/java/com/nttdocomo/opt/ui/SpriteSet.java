package com.nttdocomo.opt.ui;

import com.nttdocomo.ui.Image;
import com.nttdocomo.ui._ImageInternalAccess;

/**
 * Defines a set of optional sprites.
 */
public class SpriteSet {
    private final Sprite[] sprites;
    private final int[] collisionFlags;

    /**
     * Creates a sprite set.
     *
     * @param sprites the sprites to hold
     */
    public SpriteSet(Sprite[] sprites) {
        if (sprites == null) {
            throw new NullPointerException("sprites");
        }
        if (sprites.length == 0 || sprites.length > 32) {
            throw new IllegalArgumentException("sprites");
        }
        this.sprites = sprites;
        this.collisionFlags = new int[sprites.length];
    }

    /**
     * Returns the number of sprites.
     *
     * @return the number of sprites
     */
    public int getCount() {
        return sprites.length;
    }

    /**
     * Returns a copy of the sprite array.
     *
     * @return the sprites in this set
     */
    public Sprite[] getSprites() {
        return sprites;
    }

    /**
     * Returns the sprite at the specified index.
     *
     * @param index the index to query
     * @return the sprite
     */
    public Sprite getSprite(int index) {
        return sprites[index];
    }

    /**
     * Marks all sprites as collision targets.
     */
    public void setCollisionAll() {
        validateSprites();
        for (int i = 0; i < sprites.length; i++) {
            collisionFlags[i] = computeCollisionFlag(i);
        }
    }

    /**
     * Marks the specified sprite as a collision target.
     *
     * @param index the sprite index
     */
    public void setCollisionOf(int index) {
        collisionFlags[index] = 0;
        validateSprites();
        collisionFlags[index] = computeCollisionFlag(index);
    }

    /**
     * Returns whether two sprites collide.
     *
     * @param leftIndex the first sprite index
     * @param rightIndex the second sprite index
     * @return {@code true} if the sprites overlap
     */
    public boolean isCollision(int leftIndex, int rightIndex) {
        if (leftIndex == rightIndex) {
            return false;
        }
        Sprite left = sprites[leftIndex];
        Sprite right = sprites[rightIndex];
        if (!isCollisionCandidate(left) || !isCollisionCandidate(right)) {
            return false;
        }
        return overlaps(left, right);
    }

    /**
     * Returns the collision flag mask.
     *
     * @param index the sprite index
     * @return the collision mask
     */
    public int getCollisionFlag(int index) {
        return collisionFlags[index];
    }

    private void validateSprites() {
        for (Sprite sprite : sprites) {
            if (sprite == null) {
                throw new NullPointerException("sprite");
            }
        }
    }

    private int computeCollisionFlag(int index) {
        Sprite target = sprites[index];
        if (!isCollisionCandidate(target)) {
            return 0;
        }
        int flag = 0;
        for (int otherIndex = 0; otherIndex < sprites.length; otherIndex++) {
            if (otherIndex == index) {
                continue;
            }
            Sprite other = sprites[otherIndex];
            if (isCollisionCandidate(other) && overlaps(target, other)) {
                flag |= 1 << otherIndex;
            }
        }
        return flag;
    }

    private static boolean isCollisionCandidate(Sprite sprite) {
        if (sprite == null || !sprite.isVisible()) {
            return false;
        }
        Image image = sprite.image();
        if (image == null || _ImageInternalAccess.isDisposed(image)) {
            return false;
        }
        return true;
    }

    private static boolean overlaps(Sprite left, Sprite right) {
        return left.getX() < right.getX() + right.getWidth()
                && left.getX() + left.getWidth() > right.getX()
                && left.getY() < right.getY() + right.getHeight()
                && left.getY() + left.getHeight() > right.getY();
    }
}
