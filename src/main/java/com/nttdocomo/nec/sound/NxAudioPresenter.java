package com.nttdocomo.nec.sound;

import com.nttdocomo.ui.AudioPresenter;

/**
 * NEC handset wrapper around the base DoJa {@link AudioPresenter}.
 *
 * Binary-backed evidence for this subtype boundary:
 * - recovered title linkage references this exact type name and uses it only
 *   through inherited {@link AudioPresenter} members;
 * - the device dump contains the class name
 *   {@code com/nttdocomo/nec/sound/NxAudioPresenter} but no distinct
 *   presenter-specific member names or descriptors;
 * - title jars and the phone dump were both used as reference inputs, but the
 *   reconstructed surface is limited to the symbols they jointly prove.
 *
 * The public surface remains a subtype shell over the standard presenter. The
 * distinct MLD cadence is observed from the recovered NEC presenter path and
 * inferred for this compatibility surface, but it is not proven to apply to
 * every NEC handset or NEC audio implementation. Keeping it on this presenter
 * avoids broadening the rule to DoJa profile or device identity.
 */
public class NxAudioPresenter extends AudioPresenter {
    /*
     * Observed from the recovered title/audio behavior for this NEC presenter
     * path. This is intentionally not a device- or profile-wide default.
     */
    private static final float NEC_MLD_PLAYBACK_RATE = 0.9787f;

    protected NxAudioPresenter() {
        super();
    }

    @Override
    protected float mldPlaybackRate() {
        return NEC_MLD_PLAYBACK_RATE * super.mldPlaybackRate();
    }
}
