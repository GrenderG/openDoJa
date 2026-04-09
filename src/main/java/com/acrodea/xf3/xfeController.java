package com.acrodea.xf3;

public class xfeController extends xfeNode {
    protected final xfeRoot mRoot;

    public xfeController(xfeRoot root, String name) {
        super(name);
        this.mRoot = root;
    }

    public xfeRoot getRoot() {
        return mRoot;
    }

    public boolean onTick(int tickDelta) {
        return true;
    }
}
