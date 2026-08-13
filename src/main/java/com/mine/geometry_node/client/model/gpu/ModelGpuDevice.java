package com.mine.geometry_node.client.model.gpu;

public interface ModelGpuDevice {
    ModelGpuBuffer createBuffer(String label, ModelGpuBufferKind kind, byte[] data);

    ModelGpuTexture createTexture(String label, ModelGpuImagePlan image);
}
