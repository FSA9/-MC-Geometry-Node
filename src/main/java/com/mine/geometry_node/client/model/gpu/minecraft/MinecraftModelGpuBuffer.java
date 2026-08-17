package com.mine.geometry_node.client.model.gpu.minecraft;

import com.mine.geometry_node.client.model.gpu.ModelGpuBuffer;
import com.mojang.blaze3d.buffers.GpuBuffer;

public final class MinecraftModelGpuBuffer implements ModelGpuBuffer {
    private final GpuBuffer buffer;

    public MinecraftModelGpuBuffer(GpuBuffer buffer) {
        this.buffer = buffer;
    }

    public GpuBuffer buffer() { return buffer; }

    @Override public int byteSize() { return Math.toIntExact(buffer.size()); }
    @Override public boolean isClosed() { return buffer.isClosed(); }
    @Override public void close() { buffer.close(); }
}
