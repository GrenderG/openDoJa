package com.acrodea.xf3.loader;

public final class xfeXF2ParameterizedControllerLoader implements xfeXF2ChunkLoader {
    private final xfeXF2ParameterizedControllerFactory factory;

    public xfeXF2ParameterizedControllerLoader(xfeXF2ParameterizedControllerFactory factory) {
        this.factory = factory;
    }

    public xfeXF2ParameterizedControllerFactory getFactory() {
        return factory;
    }
}
