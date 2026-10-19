package com.mine.geometry_node.core.metadata;

import java.util.function.UnaryOperator;

/**
 * Owner-controlled storage boundary. update runs read/transform/validate/commit atomically on the
 * owner's thread or lock, returns false for equal content, and notifies only after a successful
 * commit. The owner checks authority and target lifetime on every access. Do not retain a stale
 * read to implement update. A thrown transformation must leave the owner unchanged.
 */
public interface MetadataStore<V> {
    V read();
    boolean update(UnaryOperator<V> transformation);
}
