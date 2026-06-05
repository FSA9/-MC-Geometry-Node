package com.mine.geometry_node.core.engine.graph.storage;

public record RemoteGraphConflict(String sourcePath, String targetPath, boolean directory) {
}
