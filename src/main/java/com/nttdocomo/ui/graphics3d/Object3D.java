package com.nttdocomo.ui.graphics3d;

import opendoja.g3d.MascotActionTableData;
import opendoja.g3d.MascotFigure;
import opendoja.g3d.MascotLoader;
import opendoja.g3d.SoftwareTexture;

import java.io.IOException;
import java.io.InputStream;

public abstract class Object3D {
    public static final int TYPE_NONE = 0;
    public static final int TYPE_ACTION_TABLE = 1;
    public static final int TYPE_FIGURE = 2;
    public static final int TYPE_TEXTURE = 3;
    public static final int TYPE_FOG = 4;
    public static final int TYPE_LIGHT = 5;
    public static final int TYPE_PRIMITIVE = 6;
    public static final int TYPE_GROUP = 7;
    public static final int TYPE_GROUP_MESH = 8;

    private final int type;
    private int time;

    protected Object3D(int type) {
        this.type = type;
    }

    public static Object3D createInstance(InputStream inputStream) throws IOException {
        Object3D object = createInstance(inputStream.readAllBytes());
        if (object == null) {
            throw new IOException("Unsupported Object3D data");
        }
        return object;
    }

    public static Object3D createInstance(byte[] data) {
        if (data == null || data.length < 2) {
            return null;
        }
        try {
            if (data[0] == 'M' && data[1] == 'B') {
                return new Figure(new MascotFigure(MascotLoader.loadFigure(data)));
            }
            if (data[0] == 'M' && data[1] == 'T') {
                return new ActionTable(MascotLoader.loadActionTable(data));
            }
            if (data[0] == 'B' && data[1] == 'M') {
                return new Texture(new SoftwareTexture(data, true));
            }
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    public void dispose() {
    }

    public int getType() {
        return type;
    }

    public void setTime(int time) {
        this.time = time;
    }

    public int getTime() {
        return time;
    }
}
