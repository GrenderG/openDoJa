package com.acrodea.xf3;

import java.util.ArrayList;
import java.util.List;

public class xfeActor extends xfeSubTree {
    private xfeMatrixTransformation localTransformation = new xfeMatrixTransformation();
    private final xfeControllerSet controllerSet = new xfeControllerSet();
    private final List<xfeZone> zones = new ArrayList<>();
    private boolean active = true;

    public xfeActor() {
    }

    public xfeActor(String name) {
        super(name);
    }

    public void setLocalTransformation(xfeTransformation transformation) {
        if (transformation instanceof xfeMatrixTransformation matrixTransformation) {
            localTransformation = new xfeMatrixTransformation();
            localTransformation.setTransformation(matrixTransformation.getMatrix(), matrixTransformation.getMode());
        }
    }

    public xfeMatrixTransformation getTransformation() {
        return localTransformation;
    }

    public xfeNodeTreeIterator getNodeHierarchy() {
        List<xfeNode> nodes = new ArrayList<>();
        collect(this, nodes);
        xfeNodeTreeIterator iterator = new xfeNodeTreeIterator();
        iterator.reset(nodes);
        return iterator;
    }

    public void getZones(xfeNodeListIterator iterator) {
        if (iterator != null) {
            iterator.reset(zones);
        }
    }

    public void activate(xfeSubTree subTree) {
        active = true;
    }

    public void deactivate() {
        active = false;
    }

    public xfeControllerSet getControllerSet() {
        return controllerSet;
    }

    public boolean isActive() {
        return active;
    }

    public void addZone(xfeZone zone) {
        if (zone != null) {
            zones.add(zone);
        }
    }

    private static void collect(xfeNode node, List<xfeNode> nodes) {
        nodes.add(node);
        if (node instanceof xfeGroup group) {
            for (xfeNode child : group.getChildren()) {
                collect(child, nodes);
            }
        }
    }
}
