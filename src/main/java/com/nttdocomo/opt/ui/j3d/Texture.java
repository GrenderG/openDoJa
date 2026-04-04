package com.nttdocomo.opt.ui.j3d;

import opendoja.g3d.SoftwareTexture;

import java.io.IOException;
import java.io.InputStream;

/**
 * Defines a class that holds texture data.
 * Textures are used for model mapping and for environment mapping.
 * Which purpose a texture is for is specified in the constructor.
 * A model-mapping texture is set on a {@link Figure} object.
 * An environment-mapping texture is set on a {@link Graphics3D} object.
 *
 * <p>Only uncompressed 8-bit BMP-format texture data can be used as source
 * data.
 * Depending on the handset, this class may not be supported; in that case an
 * {@link UnsupportedOperationException} occurs when a method is called.</p>
 *
 * <p>Introduced in DoJa-2.0.</p>
 */
public class Texture {
    private final SoftwareTexture handle;

    /**
     * Creates a texture object from texture data.
     * For a model-mapping texture, the shading setting is initially the normal
     * mode.
     *
     * @param data the byte sequence representing the data
     * @param forEnv specify {@code true} when the texture is used for
     *               environment mapping
     * @throws NullPointerException if {@code data} is {@code null}
     * @throws com.nttdocomo.ui.UIException if the data is invalid
     *         ({@link com.nttdocomo.ui.UIException#UNSUPPORTED_FORMAT})
     */
    public Texture(byte[] data, boolean forEnv) {
        try {
            this.handle = new SoftwareTexture(data, !forEnv);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates a texture object from texture data.
     * For a model-mapping texture, the shading setting is initially the normal
     * mode.
     *
     * @param inputStream the input stream that supplies the data
     * @param forEnv specify {@code true} when the texture is used for
     *               environment mapping
     * @throws NullPointerException if {@code inputStream} is {@code null}
     * @throws IOException if an I/O error occurs while reading the data
     * @throws com.nttdocomo.ui.UIException if the data is invalid
     *         ({@link com.nttdocomo.ui.UIException#UNSUPPORTED_FORMAT})
     */
    public Texture(InputStream inputStream, boolean forEnv) throws IOException {
        this.handle = new SoftwareTexture(inputStream, !forEnv);
    }

    /**
     * Returns the shading setting of an object with this texture attached to
     * the normal mode.
     * This cancels the call to {@link #setToonShader(int, int, int)}.
     *
     * @throws com.nttdocomo.ui.UIException if called on a texture for
     *         environment mapping
     */
    public void setNormalShader() {
        requireModelTexture("setNormalShader");
        handle.setNormalShader();
    }

    /**
     * Sets information for drawing the shading of an object with this texture
     * attached by toon shading.
     *
     * @param highlight the threshold value; a value in {@code [0, 255]} can be
     *                  specified and {@code 255} represents {@code 100%}
     * @param mid the bright-side value; a value in {@code [0, 255]} can be
     *            specified and {@code 255} represents {@code 100%}
     * @param shadow the dark-side value; a value in {@code [0, 255]} can be
     *               specified and {@code 255} represents {@code 100%}
     * @throws com.nttdocomo.ui.UIException if called on a texture for
     *         environment mapping
     * @throws IllegalArgumentException if any argument is outside
     *         {@code [0, 255]}
     */
    public void setToonShader(int highlight, int mid, int shadow) {
        requireModelTexture("setToonShader");
        validateToonParameter(highlight, "highlight");
        validateToonParameter(mid, "mid");
        validateToonParameter(shadow, "shadow");
        handle.setToonShader(highlight, mid, shadow);
    }

    SoftwareTexture handle() {
        return handle;
    }

    private void requireModelTexture(String operation) {
        if (handle.sphereMap()) {
            throw new com.nttdocomo.ui.UIException(
                    com.nttdocomo.ui.UIException.ILLEGAL_STATE,
                    operation + " is only valid for model-mapping textures"
            );
        }
    }

    private static void validateToonParameter(int value, String name) {
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException(name + " must be in [0,255]: " + value);
        }
    }
}
