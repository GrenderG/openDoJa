package opendoja.probes;

import com.nttdocomo.opt.ui.Graphics2;
import com.nttdocomo.opt.ui.Sprite;
import com.nttdocomo.opt.ui.SpriteSet;
import com.nttdocomo.ui.Graphics;
import com.nttdocomo.ui.Image;

public final class OptSpriteContractProbe {
    private OptSpriteContractProbe() {
    }

    public static void main(String[] args) {
        verifySpriteValidation();
        verifySpriteSetConstructionContract();
        verifySpriteSetCollisionContract();
        verifySpriteRenderModeDrawContract();

        System.out.println("Opt sprite contract probe OK");
    }

    private static void verifySpriteValidation() {
        Image image = Image.createImage(4, 5);

        assertThrows("Sprite(Image) null", NullPointerException.class, () -> new Sprite((Image) null));
        assertThrows("Sprite(Image,x,y,w,h) null", NullPointerException.class, () -> new Sprite(null, 0, 0, 1, 1));
        assertThrows("Sprite(Image,x,y,w,h) negative width", IllegalArgumentException.class, () -> new Sprite(image, 0, 0, -1, 1));
        assertThrows("Sprite(Image,x,y,w,h) negative height", IllegalArgumentException.class, () -> new Sprite(image, 0, 0, 1, -1));

        Sprite zero = new Sprite(image, 0, 0, 0, 0);
        check(zero.getWidth() == 0, "zero-width sprite should be allowed");
        check(zero.getHeight() == 0, "zero-height sprite should be allowed");

        Sprite sprite = new Sprite(image);
        assertThrows("setImage(Image) null", NullPointerException.class, () -> sprite.setImage((Image) null));
        assertThrows("setImage(Image,x,y,w,h) null", NullPointerException.class, () -> sprite.setImage(null, 0, 0, 1, 1));
        assertThrows("setImage(Image,x,y,w,h) negative width", IllegalArgumentException.class, () -> sprite.setImage(image, 0, 0, -1, 1));
        assertThrows("setImage(Image,x,y,w,h) negative height", IllegalArgumentException.class, () -> sprite.setImage(image, 0, 0, 1, -1));

        sprite.setFlipMode(Graphics.FLIP_NONE);
        sprite.setFlipMode(Graphics.FLIP_HORIZONTAL);
        sprite.setFlipMode(Graphics.FLIP_VERTICAL);
        sprite.setFlipMode(Graphics.FLIP_ROTATE);
        assertThrows("setFlipMode rejects FLIP_ROTATE_LEFT", IllegalArgumentException.class,
                () -> sprite.setFlipMode(Graphics.FLIP_ROTATE_LEFT));

        sprite.setRenderMode(Graphics2.OP_REPL, 255, 255);
        sprite.setRenderMode(Graphics2.OP_ADD, 128, 64);
        sprite.setRenderMode(Graphics2.OP_SUB, 0, 255);
        assertThrows("setRenderMode invalid operator", IllegalArgumentException.class,
                () -> sprite.setRenderMode(9, 255, 255));
        assertThrows("setRenderMode invalid srcRatio", IllegalArgumentException.class,
                () -> sprite.setRenderMode(Graphics2.OP_REPL, -1, 255));
        assertThrows("setRenderMode invalid dstRatio", IllegalArgumentException.class,
                () -> sprite.setRenderMode(Graphics2.OP_REPL, 255, 256));
    }

    private static void verifySpriteSetConstructionContract() {
        assertThrows("SpriteSet null array", NullPointerException.class, () -> new SpriteSet(null));
        assertThrows("SpriteSet empty array", IllegalArgumentException.class, () -> new SpriteSet(new Sprite[0]));
        assertThrows("SpriteSet >32 array", IllegalArgumentException.class, () -> new SpriteSet(new Sprite[33]));

        Sprite red = sprite(0, 0);
        Sprite blue = sprite(8, 0);
        Sprite[] sprites = new Sprite[]{red};
        SpriteSet set = new SpriteSet(sprites);
        check(set.getSprites() == sprites, "SpriteSet should retain and return the caller array reference");
        sprites[0] = blue;
        check(set.getSprite(0) == blue, "SpriteSet should observe caller array element replacements");
    }

    private static void verifySpriteSetCollisionContract() {
        Sprite a = sprite(0, 0);
        Sprite b = sprite(4, 0);
        Sprite c = sprite(32, 0);
        Image disposedImage = Image.createImage(8, 8);
        Sprite d = new Sprite(disposedImage);
        d.setLocation(0, 0);
        disposedImage.dispose();
        Sprite invisible = sprite(0, 0);
        invisible.setVisible(false);

        SpriteSet set = new SpriteSet(new Sprite[]{a, b, c, d, invisible});
        set.setCollisionOf(0);
        check(set.getCollisionFlag(0) == (1 << 1), "setCollisionOf should update only the requested sprite flag");
        check(set.getCollisionFlag(1) == 0, "setCollisionOf should not update unrelated sprite flags");
        check(set.getCollisionFlag(2) == 0, "non-colliding sprite flag should stay clear");
        check(set.getCollisionFlag(3) == 0, "disposed-image sprite should stay ignored");
        check(set.getCollisionFlag(4) == 0, "invisible sprite should stay ignored");

        set.setCollisionAll();
        check(set.getCollisionFlag(0) == (1 << 1), "sprite 0 should collide only with sprite 1");
        check(set.getCollisionFlag(1) == (1 << 0), "sprite 1 should collide only with sprite 0");
        check(set.getCollisionFlag(2) == 0, "sprite 2 should have no collisions");
        check(set.getCollisionFlag(3) == 0, "disposed-image sprite should not participate in collisions");
        check(set.getCollisionFlag(4) == 0, "invisible sprite should not participate in collisions");

        check(set.isCollision(0, 1), "overlapping visible sprites should collide");
        check(!set.isCollision(0, 0), "same sprite should never collide with itself");
        check(!set.isCollision(0, 2), "separated sprites should not collide");
        check(!set.isCollision(0, 3), "disposed-image sprites should be ignored");
        check(!set.isCollision(0, 4), "invisible sprites should be ignored");

        assertThrows("setCollisionAll null sprite", NullPointerException.class, () -> new SpriteSet(new Sprite[]{a, null}).setCollisionAll());
        assertThrows("setCollisionOf null sprite", NullPointerException.class, () -> new SpriteSet(new Sprite[]{a, null}).setCollisionOf(0));
        assertThrows("setCollisionOf low index", ArrayIndexOutOfBoundsException.class, () -> set.setCollisionOf(-1));
        assertThrows("setCollisionOf high index", ArrayIndexOutOfBoundsException.class, () -> set.setCollisionOf(5));
        assertThrows("isCollision low index", ArrayIndexOutOfBoundsException.class, () -> set.isCollision(-1, 0));
        assertThrows("isCollision high index", ArrayIndexOutOfBoundsException.class, () -> set.isCollision(0, 5));
        assertThrows("getCollisionFlag low index", ArrayIndexOutOfBoundsException.class, () -> set.getCollisionFlag(-1));
        assertThrows("getCollisionFlag high index", ArrayIndexOutOfBoundsException.class, () -> set.getCollisionFlag(5));
    }

    private static void verifySpriteRenderModeDrawContract() {
        Image spriteImage = Image.createImage(1, 1);
        Graphics spriteGraphics = spriteImage.getGraphics();
        spriteGraphics.setColor(0xFFFFFFFF);
        spriteGraphics.fillRect(0, 0, 1, 1);

        Sprite sprite = new Sprite(spriteImage);
        sprite.setRenderMode(Graphics2.OP_REPL, 0, 255);

        Image target = Image.createImage(2, 1);
        Graphics2 graphics = (Graphics2) target.getGraphics();
        graphics.setColor(0xFF000000);
        graphics.fillRect(0, 0, 2, 1);
        graphics.drawSpriteSet(new SpriteSet(new Sprite[]{sprite}));

        check(graphics.getRGBPixel(0, 0) == 0xFF000000, "sprite render mode should affect drawSpriteSet output");
    }

    private static Sprite sprite(int x, int y) {
        Image image = Image.createImage(8, 8);
        Sprite sprite = new Sprite(image);
        sprite.setLocation(x, y);
        return sprite;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static void assertThrows(String label, Class<? extends Throwable> expected, ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Throwable throwable) {
            if (expected.isInstance(throwable)) {
                return;
            }
            throw new IllegalStateException(label + " expected=" + expected.getName()
                    + " actual=" + throwable.getClass().getName(), throwable);
        }
        throw new IllegalStateException(label + " expected exception " + expected.getName());
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Throwable;
    }
}
