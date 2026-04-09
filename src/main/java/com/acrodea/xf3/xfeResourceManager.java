package com.acrodea.xf3;

public interface xfeResourceManager {
    int getResourceId(String name);

    xfeResource getResource(int id);

    void addResource(int id, xfeResource resource);
}
