package com.mine.geometry_node.core.engine.system.model.domain;

import java.util.Arrays;

public final class ModelIndexBuffer {
    private final ModelComponentType componentType;
    private final int indexCount;
    private final byte[] data;

    public ModelIndexBuffer(ModelComponentType componentType, int indexCount, byte[] data) {
        if (componentType != ModelComponentType.UINT8 && componentType != ModelComponentType.UINT16
                && componentType != ModelComponentType.UINT32) {
            throw new IllegalArgumentException("indices require an unsigned integer component type");
        }
        if (indexCount < 0) throw new IllegalArgumentException("indexCount must not be negative");
        long expected = Math.multiplyExact((long) componentType.byteSize(), indexCount);
        if (data == null || data.length != expected) throw new IllegalArgumentException("index byte length does not match its layout");
        this.componentType = componentType;
        this.indexCount = indexCount;
        this.data = Arrays.copyOf(data, data.length);
    }

    public ModelComponentType componentType() { return componentType; }
    public int indexCount() { return indexCount; }
    public int byteSize() { return data.length; }
    public byte[] data() { return Arrays.copyOf(data, data.length); }

    public long indexAt(int index) {
        if (index < 0 || index >= indexCount) throw new IndexOutOfBoundsException(index);
        int offset = Math.multiplyExact(index, componentType.byteSize());
        return switch (componentType) {
            case UINT8 -> Byte.toUnsignedInt(data[offset]);
            case UINT16 -> (data[offset] & 0xFFL) | (data[offset + 1] & 0xFFL) << 8;
            case UINT32 -> (data[offset] & 0xFFL) | (data[offset + 1] & 0xFFL) << 8
                    | (data[offset + 2] & 0xFFL) << 16 | (data[offset + 3] & 0xFFL) << 24;
            default -> throw new IllegalStateException("unsupported index type: " + componentType);
        };
    }
}
