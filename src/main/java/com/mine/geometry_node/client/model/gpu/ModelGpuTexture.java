package com.mine.geometry_node.client.model.gpu;

public interface ModelGpuTexture extends AutoCloseable {
    int width();

    int height();

    default int mipLevels() { return 1; }

    default long byteSize() {
        long total = 0;
        for (int level = 0; level < mipLevels(); level++) {
            total += (long) Math.max(1, width() >> level) * Math.max(1, height() >> level) * 4L;
        }
        return total;
    }

    boolean isClosed();

    @Override
    void close();
}
