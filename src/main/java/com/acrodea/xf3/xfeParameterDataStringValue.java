package com.acrodea.xf3;

public class xfeParameterDataStringValue extends xfeParameterDataValue {
    private final String value;

    public xfeParameterDataStringValue(String value) {
        this.value = value;
    }

    @Override
    public int getType() {
        return TYPE_STRING;
    }

    public String getString(int index) {
        return value;
    }
}
