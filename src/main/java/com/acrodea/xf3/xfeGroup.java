package com.acrodea.xf3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class xfeGroup extends xfeNode {
    private final xfeMatrixTransformation transformation = new xfeMatrixTransformation();
    private final List<xfeNode> children = new ArrayList<>();

    public xfeGroup() {
    }

    public xfeGroup(String name) {
        super(name);
    }

    public xfeMatrixTransformation getTransformation() {
        return transformation;
    }

    public List<xfeNode> getChildren() {
        return Collections.unmodifiableList(children);
    }

    public void addChild(xfeNode child) {
        if (child == null) {
            return;
        }
        child.setParent(this);
        children.add(child);
    }
}
