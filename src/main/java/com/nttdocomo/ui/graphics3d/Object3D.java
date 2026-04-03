package com.nttdocomo.ui.graphics3d;

import opendoja.g3d.MascotActionTableData;
import opendoja.g3d.MascotFigure;
import opendoja.g3d.MascotLoader;
import opendoja.g3d.SoftwareTexture;

import java.io.IOException;
import java.io.InputStream;

/**
 * Base class for all 3D objects handled by the DoJa graphics3d API.
 */
public abstract class Object3D {
    /**
     * Type value indicating that the object has been disposed or has no type.
     */
    public static final int TYPE_NONE = 0;
    /**
     * Type value for an action-table object.
     */
    public static final int TYPE_ACTION_TABLE = 1;
    /**
     * Type value for a figure object.
     */
    public static final int TYPE_FIGURE = 2;
    /**
     * Type value for a texture object.
     */
    public static final int TYPE_TEXTURE = 3;
    /**
     * Type value for a fog object.
     */
    public static final int TYPE_FOG = 4;
    /**
     * Type value for a light object.
     */
    public static final int TYPE_LIGHT = 5;
    /**
     * Type value for a primitive object.
     */
    public static final int TYPE_PRIMITIVE = 6;
    /**
     * Type value for a group object.
     */
    public static final int TYPE_GROUP = 7;
    /**
     * Type value for a group-mesh object.
     */
    public static final int TYPE_GROUP_MESH = 8;

    private int type;
    private int time;

    /**
     * Initializes a 3D object with the specified type.
     *
     * @param type the object type
     */
    protected Object3D(int type) {
        this.type = type;
    }

    /**
     * Creates a 3D object from encoded object data read from a stream.
     *
     * @param inputStream the stream containing encoded 3D object data
     * @return the created 3D object
     * @throws IOException if the data cannot be read or does not describe a supported object
     */
    public static Object3D createInstance(InputStream inputStream) throws IOException {
        Object3D object = createInstance(inputStream.readAllBytes());
        if (object == null) {
            throw new IOException("Unsupported Object3D data");
        }
        return object;
    }

    /**
     * Creates a 3D object from encoded object data in a byte array.
     *
     * @param data the encoded 3D object data
     * @return the created object, or {@code null} if the data is unsupported
     */
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

    /**
     * Releases any data held by this 3D object. After disposal the type value
     * becomes {@link #TYPE_NONE}.
     */
    public void dispose() {
        type = TYPE_NONE;
    }

    /**
     * Gets the type number of this 3D object.
     *
     * @return the object type
     */
    public int getType() {
        return type;
    }

    /**
     * Sets the current time associated with this 3D object.
     *
     * @param time the current time
     */
    public void setTime(int time) {
        this.time = time;
    }

    /**
     * Gets the current time associated with this 3D object.
     *
     * @return the current time
     */
    public int getTime() {
        return time;
    }
}
