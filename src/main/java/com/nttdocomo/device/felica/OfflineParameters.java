package com.nttdocomo.device.felica;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class of offline FeliCa parameter collections.
 */
public abstract class OfflineParameters {
    private static final int MAX_SIZE = 16;
    private final List<Object> parameters = new ArrayList<>();

    /**
     * Creates an empty offline-parameter list.
     */
    protected OfflineParameters() {
    }

    /**
     * Gets the number of currently registered parameters.
     *
     * @return the number of currently registered parameters
     */
    public final int getSize() {
        return parameters.size();
    }

    /**
     * Gets the maximum number of parameters that can be registered.
     *
     * @return the maximum number of parameters that can be registered
     */
    public int getMaxSize() {
        return MAX_SIZE;
    }

    /**
     * Deletes the parameter at the specified position.
     *
     * @param position the position of the parameter to delete
     * @throws IllegalArgumentException if {@code position} is negative or is
     *         greater than or equal to the current registered-parameter count
     */
    public void remove(int position) {
        if (position < 0 || position >= parameters.size()) {
            throw new IllegalArgumentException("position");
        }
        parameters.remove(position);
    }

    /**
     * Deletes all registered parameters.
     */
    public final void removeAll() {
        parameters.clear();
    }

    final int addInternal(Object parameter) {
        if (parameters.size() >= getMaxSize()) {
            throw new IllegalStateException("maximum parameter count exceeded");
        }
        parameters.add(parameter);
        return parameters.size() - 1;
    }

    final List<Object> parameters() {
        return parameters;
    }
}
