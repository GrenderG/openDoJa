package com.nttdocomo.opt.ui.j3d;

import opendoja.g3d.MascotFigure;
import opendoja.g3d.MascotLoader;
import opendoja.g3d.SoftwareTexture;

import java.io.IOException;
import java.io.InputStream;

public class Figure {
    private final MascotFigure handle;

    public Figure(byte[] data) {
        try {
            this.handle = new MascotFigure(MascotLoader.loadFigure(data));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Figure(InputStream inputStream) throws IOException {
        this.handle = new MascotFigure(MascotLoader.loadFigure(inputStream));
    }

    public void setTexture(Texture texture) {
        handle.setTexture(texture == null ? null : texture.handle());
    }

    public void setTexture(Texture[] textures) {
        if (textures == null) {
            throw new NullPointerException("textures");
        }
        SoftwareTexture[] converted = new SoftwareTexture[textures.length];
        for (int i = 0; i < textures.length; i++) {
            converted[i] = textures[i].handle();
        }
        handle.setTextures(converted);
    }

    public int getNumTextures() {
        return handle.numTextures();
    }

    public void changeTexture(int index) {
        handle.selectTexture(index);
    }

    public void setPosture(ActionTable actionTable, int action, int frame) {
        handle.setAction(actionTable.handle(), action);
        handle.setTime(frame);
    }

    public int getNumPattern() {
        return handle.numPatterns();
    }

    public void setPattern(int pattern) {
        handle.setPattern(pattern);
    }

    MascotFigure handle() {
        return handle;
    }
}
