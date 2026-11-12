package com.nttdocomo.ui.graphics3d;

import opendoja.g3d.SoftwareTexture;

/**
 * Internal bridge that exposes package-private {@link Primitive} render state
 * to the parent graphics package without widening {@link Primitive}'s public
 * API.
 */
public final class _PrimitiveRenderStateAccess {
    private _PrimitiveRenderStateAccess() {
    }

    public static PrimitiveRenderState snapshot(Primitive primitive) {
        SoftwareTexture texture = primitive.textureHandle();
        int param = primitive.getPrimitiveParam();
        int blendMode = primitive.blendModeValue();
        // Some STAR builds use magenta palette-zero cutouts in blended sprites without TEXTURE_COLORKEY
        if (primitive.starBlendColorKey() && texture != null && texture.hasPaletteZeroColor(0xff00ff)
                && (blendMode == DrawableObject3D.BLEND_ALPHA || blendMode == DrawableObject3D.BLEND_ADD)) {
            param |= Primitive.TEXTURE_COLORKEY;
        }
        return new PrimitiveRenderState(
                texture,
                primitive.textureWrapEnabled(),
                primitive.textureCoordinateTranslateU(),
                primitive.textureCoordinateTranslateV(),
                primitive.depthTestEnabled(),
                primitive.depthWriteEnabled(),
                primitive.doubleSided(),
                param
        );
    }

    public static void configureStarPrimitive(Primitive primitive) {
        primitive.setStarBlendColorKey(true);
    }

    public record PrimitiveRenderState(SoftwareTexture textureHandle, boolean textureWrapEnabled,
                                       float textureCoordinateTranslateU, float textureCoordinateTranslateV,
                                       boolean depthTestEnabled, boolean depthWriteEnabled,
                                       boolean doubleSided, int primitiveParam) {
    }
}
