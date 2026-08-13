package com.mine.geometry_node.core.engine.system.model.domain;

public enum ModelComponentType {
    INT8(1, true, false), UINT8(1, false, false), INT16(2, true, false),
    UINT16(2, false, false), UINT32(4, false, false), FLOAT32(4, true, true);

    private final int byteSize;
    private final boolean signed;
    private final boolean floatingPoint;

    ModelComponentType(int byteSize, boolean signed, boolean floatingPoint) {
        this.byteSize = byteSize;
        this.signed = signed;
        this.floatingPoint = floatingPoint;
    }

    public int byteSize() { return byteSize; }
    public boolean isSigned() { return signed; }
    public boolean isFloatingPoint() { return floatingPoint; }
}
