package com.mine.geometry_node.core.engine.system.asset;

import java.nio.file.Path;

/** Reserved artifacts are excluded even though ordinary files need no registered asset type. */
public final class AssetRepositoryFiles {
    private AssetRepositoryFiles() {}

    public static boolean isInternal(Path path) {
        if (path == null || path.getFileName() == null) return false;
        String name = path.getFileName().toString();
        return name.startsWith(".geometrynode-")
                || (name.startsWith(".") && name.contains(".geometrynode-"));
    }
}
