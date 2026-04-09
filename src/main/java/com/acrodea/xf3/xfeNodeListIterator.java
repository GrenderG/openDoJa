package com.acrodea.xf3;

import java.util.Collections;
import java.util.List;

public class xfeNodeListIterator {
    private List<? extends xfeNode> nodes = Collections.emptyList();
    private int index;

    public xfeNode getNext() {
        if (index >= nodes.size()) {
            return null;
        }
        return nodes.get(index++);
    }

    void reset(List<? extends xfeNode> nodes) {
        this.nodes = nodes == null ? Collections.emptyList() : nodes;
        this.index = 0;
    }
}
