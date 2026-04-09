package com.acrodea.xf3;

public class xfeNode {
    private String name;
    private xfeGroup parent;

    public xfeNode() {
    }

    public xfeNode(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public xfeGroup getParent() {
        return parent;
    }

    public void setName(String name) {
        this.name = name;
    }

    void setParent(xfeGroup parent) {
        this.parent = parent;
    }
}
