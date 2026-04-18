package com.nttdocomo.nec.sound;

/**
 * NEC audio factory used by early handset titles.
 *
 * Binary-backed evidence:
 * - recovered title linkage references {@code SoundManager.getAudioPresenter()} and
 *   stores the result as an {@link NxAudioPresenter};
 * - the device dump contains the `SoundManager`, `getAudioPresenter`, and
 *   `createAudioPresenter` symbols;
 * - the recovered title and dump metadata together only prove a small
 *   presenter-factory boundary, so the implementation stays limited to that.
 */
public final class SoundManager {
    private SoundManager() {
    }

    public static NxAudioPresenter getAudioPresenter() {
        return createAudioPresenter();
    }

    static NxAudioPresenter createAudioPresenter() {
        return new NxAudioPresenter();
    }
}
