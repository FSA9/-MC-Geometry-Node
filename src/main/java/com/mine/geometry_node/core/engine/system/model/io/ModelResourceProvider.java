package com.mine.geometry_node.core.engine.system.model.io;

import java.io.IOException;

/** Provider for one explicitly controlled model namespace (resource pack, world asset, or local root). */
public interface ModelResourceProvider extends AutoCloseable {
    ModelResource open(String logicalId) throws IOException;
    @Override default void close() throws IOException { }
}
