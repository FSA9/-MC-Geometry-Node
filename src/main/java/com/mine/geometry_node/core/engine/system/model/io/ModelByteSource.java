package com.mine.geometry_node.core.engine.system.model.io;
import java.io.IOException;
import java.nio.ByteBuffer;
/** Caller owns lifetime. Bounded positional reads; no implicit dependency/network access. */
public interface ModelByteSource extends AutoCloseable {
    long size();
    ByteBuffer read(long offset, int length, ModelCancellation cancellation) throws IOException;
    @Override void close() throws IOException;
}
