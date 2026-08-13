package com.mine.geometry_node.core.engine.system.model.domain;

import java.util.Arrays;
import java.nio.ByteBuffer;

public final class ModelVertexAttribute {
    private final ModelAttributeSemantic semantic;
    private final ModelComponentType componentType;
    private final int componentCount;
    private final boolean normalized;
    private final int elementCount;
    private final byte[] data;

    public ModelVertexAttribute(ModelAttributeSemantic semantic, ModelComponentType componentType,
                                int componentCount, boolean normalized, int elementCount, byte[] data) {
        if (semantic == null || componentType == null) throw new IllegalArgumentException("attribute metadata must not be null");
        if (componentCount < 1 || componentCount > 4 || elementCount < 0) throw new IllegalArgumentException("invalid attribute dimensions");
        long expected = Math.multiplyExact(Math.multiplyExact((long) componentType.byteSize(), componentCount), elementCount);
        if (data == null || data.length != expected) throw new IllegalArgumentException("attribute byte length does not match its layout");
        this.semantic = semantic;
        this.componentType = componentType;
        this.componentCount = componentCount;
        this.normalized = normalized;
        this.elementCount = elementCount;
        this.data = Arrays.copyOf(data, data.length);
    }

    public ModelAttributeSemantic semantic() { return semantic; }
    public ModelComponentType componentType() { return componentType; }
    public int componentCount() { return componentCount; }
    public boolean normalized() { return normalized; }
    public int elementCount() { return elementCount; }
    public int byteSize() { return data.length; }
    public byte[] data() { return Arrays.copyOf(data, data.length); }
    public ByteBuffer readOnlyData() { return ByteBuffer.wrap(data).asReadOnlyBuffer(); }
}
