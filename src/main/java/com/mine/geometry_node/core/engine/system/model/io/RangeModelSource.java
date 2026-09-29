package com.mine.geometry_node.core.engine.system.model.io;

import java.io.IOException;
import java.nio.ByteBuffer;

/** Read-only bounded view over a parent source (used by a GLB BIN chunk). */
public final class RangeModelSource implements ModelByteSource {
    private final ModelByteSource parent;
    private final long base, length;
    public RangeModelSource(ModelByteSource parent, long base, long length) {
        if (base < 0 || length < 0 || base > parent.size() || length > parent.size() - base)
            throw new IllegalArgumentException("invalid source range");
        this.parent = parent; this.base = base; this.length = length;
    }
    @Override public long size() { return length; }
    @Override public ByteBuffer read(long offset, int count, ModelCancellation token) throws IOException {
        if (offset < 0 || count < 0 || offset > length || count > length - offset)
            throw new IOException("source range outside view");
        return parent.read(base + offset, count, token);
    }
    @Override public void close() { }
}
