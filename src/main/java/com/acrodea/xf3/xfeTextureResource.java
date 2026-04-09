package com.acrodea.xf3;

import java.io.InputStream;

public class xfeTextureResource implements xfeResource {
    private String resourceName;

    public boolean load(String resourceName) {
        this.resourceName = resourceName;
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = xfeTextureResource.class.getClassLoader();
        }
        try (InputStream stream = loader == null ? null : loader.getResourceAsStream(resourceName)) {
            return stream != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    public String getResourceName() {
        return resourceName;
    }
}
