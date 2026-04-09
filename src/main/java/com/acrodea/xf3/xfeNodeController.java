package com.acrodea.xf3;

public class xfeNodeController extends xfeController {
    protected xfeNode mNode;

    public xfeNodeController(xfeRoot root, String name, xfeNode node) {
        super(root, name);
        this.mNode = node;
    }

    public xfeNode getNode() {
        return mNode;
    }

    public void setNode(xfeNode node) {
        this.mNode = node;
    }
}
