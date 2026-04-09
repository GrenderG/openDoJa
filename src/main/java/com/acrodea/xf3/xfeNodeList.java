package com.acrodea.xf3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class xfeNodeList {
    private final List<xfeNode> nodes = new ArrayList<>();

    public void add(xfeNode node) {
        if (node != null) {
            nodes.add(node);
        }
    }

    List<xfeNode> snapshot() {
        return Collections.unmodifiableList(nodes);
    }
}
