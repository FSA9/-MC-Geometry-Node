package com.mine.geometry_node.core.engine.graph.storage;

public record RemoteGraphEntry(String path, String name, boolean directory, long size,
                               long lastModified, String graphTypeId) {
}
