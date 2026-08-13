package com.mine.geometry_node.client.model.gpu;

public interface ModelGpuBuffer extends AutoCloseable {
    int byteSize();

    boolean isClosed();

    @Override
    void close();
}
