package com.nttdocomo.ui;

public class AudioTrackPresenter extends AudioPresenter {
    AudioTrackPresenter() {
    }

    @Override
    public void setSyncEvent(int type, int time) {
    }

    public void setSound(MediaImage image) {
        setData(null);
    }

    @Override
    public void setSound(MediaSound sound) {
        super.setSound(sound);
    }

    @Override
    public Audio3D getAudio3D() {
        return new Audio3D();
    }
}
