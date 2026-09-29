package com.mine.geometry_node.core.engine.system.model.io;

import java.io.IOException;
import java.util.Objects;

/** A bounded logical resource returned by a model provider. */
public record ModelResource(String logicalId, ModelByteSource source) implements AutoCloseable {
    public ModelResource {
        Objects.requireNonNull(logicalId);
        Objects.requireNonNull(source);
        if (logicalId.isBlank()) throw new IllegalArgumentException("blank logical resource id");
    }
    public long encodedBytes() { return source.size(); }
    @Override public void close() throws IOException { source.close(); }
}
