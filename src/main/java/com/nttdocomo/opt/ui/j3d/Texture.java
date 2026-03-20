package com.nttdocomo.opt.ui.j3d;

import opendoja.g3d.SoftwareTexture;

import java.io.IOException;
import java.io.InputStream;

public class Texture {
    private final SoftwareTexture handle;

    public Texture(byte[] data, boolean forModel) {
        try {
            this.handle = new SoftwareTexture(data, forModel);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Texture(InputStream inputStream, boolean forModel) throws IOException {
        this.handle = new SoftwareTexture(inputStream, forModel);
    }

    public void setNormalShader() {
    }

    public void setToonShader(int highlight, int mid, int shadow) {
    }

    SoftwareTexture handle() {
        return handle;
    }
}
