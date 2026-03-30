package com.nttdocomo.ui.graphics3d;

import opendoja.g3d.MascotFigure;
import opendoja.g3d.SoftwareTexture;
import opendoja.host.OpenDoJaLog;

public class Figure extends DrawableObject3D {
    private static final boolean TRACE_FAILURES = opendoja.host.OpenDoJaLaunchArgs.getBoolean(opendoja.host.OpenDoJaLaunchArgs.TRACE_FAILURES);
    private static final boolean TRACE_3D_CALLS = opendoja.host.OpenDoJaLaunchArgs.getBoolean(opendoja.host.OpenDoJaLaunchArgs.DEBUG3D_CALLS);
    private final MascotFigure handle;

    Figure(MascotFigure handle) {
        super(TYPE_FIGURE);
        this.handle = handle;
    }

    public void setTexture(Texture texture) {
        try {
            handle.setTexture(texture == null ? null : texture.handle());
        } catch (RuntimeException e) {
            throw traceFailure("setTexture", e);
        }
    }

    public void setTextures(Texture[] textures) {
        if (textures == null) {
            throw new NullPointerException("textures");
        }
        try {
            SoftwareTexture[] converted = new SoftwareTexture[textures.length];
            for (int i = 0; i < textures.length; i++) {
                if (textures[i] == null) {
                    throw new NullPointerException("texture[" + i + "]");
                }
                converted[i] = textures[i].handle();
            }
            handle.setTextures(converted);
        } catch (RuntimeException e) {
            throw traceFailure("setTextures", e);
        }
    }

    public void setAction(ActionTable actionTable, int action) {
        try {
            handle.setAction(actionTable.handle(), action);
            if (TRACE_3D_CALLS) {
                OpenDoJaLog.debug(Figure.class, () -> "3D call Figure.setAction action=" + action
                        + " polygons=" + (handle.model() == null ? -1 : handle.model().polygons().length));
            }
        } catch (RuntimeException e) {
            throw traceFailure("setAction", e);
        }
    }

    public ActionTable getActionTable() {
        return handle.actionTable() == null ? null : new ActionTable(handle.actionTable());
    }

    @Override
    public void setTime(int time) {
        super.setTime(time);
        try {
            handle.setTime(time);
            if (TRACE_3D_CALLS) {
                OpenDoJaLog.debug(Figure.class, () -> "3D call Figure.setTime time=" + time
                        + " polygons=" + (handle.model() == null ? -1 : handle.model().polygons().length));
            }
        } catch (RuntimeException e) {
            throw traceFailure("setTime", e);
        }
    }

    public int getNumPatterns() {
        return handle.numPatterns();
    }

    public void setPattern(int pattern) {
        try {
            handle.setPattern(pattern);
        } catch (RuntimeException e) {
            throw traceFailure("setPattern", e);
        }
    }

    @Override
    public void setPerspectiveCorrectionEnabled(boolean enabled) {
        setPerspectiveCorrectionEnabledInternal(enabled);
    }

    @Override
    public void setBlendMode(int blendMode) {
        setBlendModeInternal(blendMode);
    }

    @Override
    public void setTransparency(float transparency) {
        setTransparencyInternal(transparency);
    }

    MascotFigure handle() {
        return handle;
    }

    private RuntimeException traceFailure(String operation, RuntimeException failure) {
        if (TRACE_FAILURES) {
            OpenDoJaLog.error(Figure.class, "openDoJa figure failure in " + operation, failure);
        }
        return failure;
    }
}
