package com.mine.geometry_node.core.engine.system.model.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

/** Small bounded source used for data URI and embedded chunks. */
public final class InMemoryModelSource implements ModelByteSource {
    private final byte[] bytes;
    public InMemoryModelSource(byte[] bytes) { this.bytes = Objects.requireNonNull(bytes).clone(); }
    @Override public long size() { return bytes.length; }
    @Override public ByteBuffer read(long offset, int length, ModelCancellation cancellation) throws IOException {
        cancellation.check();
        if (offset < 0 || length < 0 || offset > bytes.length || length > bytes.length - offset)
            throw new IOException("source range outside memory source");
        return ByteBuffer.wrap(bytes, (int)offset, length).slice().asReadOnlyBuffer().order(java.nio.ByteOrder.LITTLE_ENDIAN);
    }
    @Override public void close() { }
}
