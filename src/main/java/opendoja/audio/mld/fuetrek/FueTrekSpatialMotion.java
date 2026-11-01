package opendoja.audio.mld.fuetrek;

/** The SDK's DSModule position/velocity state, before DirectSound3D processing. */
final class FueTrekSpatialMotion {
    int remainingBlocks;
    float x, y, z;
    float targetX, targetY, targetZ;
    float velocityX, velocityY, velocityZ;
    float deltaX, deltaY, deltaZ;

    void set(int mode, double xCenti, double yCenti, double zCenti,
             int durationMillis, int sampleRate, int blockFrames) {
        if (mode == 0) {
            x = y = z = targetX = targetY = targetZ = 0;
        } else if (mode == 1) {
            x = targetX = (float) (xCenti * 0.01);
            y = targetY = (float) (yCenti * 0.01);
            z = targetZ = (float) (zCenti * 0.01);
        } else if (mode == 2) {
            targetX = (float) (xCenti * 0.01);
            targetY = (float) (yCenti * 0.01);
            targetZ = (float) (zCenti * 0.01);
            if (durationMillis != 0) {
                // 10001170 keeps these arithmetic intermediates in x87
                // double precision and rounds only the stored float words.
                double seconds = Integer.toUnsignedLong(durationMillis) * 0.001;
                velocityX = (float) (((double) targetX - x) / seconds);
                velocityY = (float) (((double) targetY - y) / seconds);
                velocityZ = (float) (((double) targetZ - z) / seconds);
                remainingBlocks = Integer.divideUnsigned(sampleRate * durationMillis, blockFrames * 1000) + 1;
                deltaX = (float) (((double) targetX - x) / remainingBlocks);
                deltaY = (float) (((double) targetY - y) / remainingBlocks);
                deltaZ = (float) (((double) targetZ - z) / remainingBlocks);
                return;
            }
            x = targetX;
            y = targetY;
            z = targetZ;
        } else {
            return;
        }
        velocityX = velocityY = velocityZ = 0;
        remainingBlocks = 1;
    }

    void advanceBlock() {
        if (remainingBlocks == 0) return;
        if (remainingBlocks == 1) {
            x = targetX;
            y = targetY;
            z = targetZ;
            velocityX = velocityY = velocityZ = 0;
        } else {
            x += deltaX;
            y += deltaY;
            z += deltaZ;
        }
        remainingBlocks--;
    }
}
