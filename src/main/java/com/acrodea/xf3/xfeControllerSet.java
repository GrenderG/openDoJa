package com.acrodea.xf3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class xfeControllerSet {
    private final List<xfeController> controllers = new ArrayList<>();

    public void addController(xfeController controller) {
        if (controller != null && !controllers.contains(controller)) {
            controllers.add(controller);
        }
    }

    public void getControllers(xfeNodeListIterator iterator) {
        if (iterator != null) {
            iterator.reset(controllers);
        }
    }

    List<xfeController> snapshot() {
        if (controllers.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(controllers);
    }
}
