package com.acrodea.xf3.loader;

import com.acrodea.xf3.xfeController;
import com.acrodea.xf3.xfeNode;
import com.acrodea.xf3.xfeParameterDataSet;
import com.acrodea.xf3.xfeRoot;

public interface xfeXF2ParameterizedControllerFactory {
    xfeController createController(xfeParameterDataSet dataSet, xfeRoot root, String name, xfeNode node);
}
