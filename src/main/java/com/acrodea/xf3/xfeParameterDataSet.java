package com.acrodea.xf3;

import java.util.HashMap;
import java.util.Map;

public class xfeParameterDataSet {
    private final Map<String, xfeParameterDataValue> values = new HashMap<>();

    public xfeParameterDataValue getValue(String name) {
        return values.get(name);
    }

    public void putString(String name, String value) {
        values.put(name, new xfeParameterDataStringValue(value));
    }
}
