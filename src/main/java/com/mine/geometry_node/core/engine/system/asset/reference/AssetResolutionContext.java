package com.mine.geometry_node.core.engine.system.asset.reference;

import java.nio.file.Path;
import java.util.Objects;

public record AssetResolutionContext(Path gameDirectory, Path worldDirectory) {
    public AssetResolutionContext {
        gameDirectory = Objects.requireNonNull(gameDirectory, "gameDirectory").toAbsolutePath().normalize();
        worldDirectory = worldDirectory == null ? null : worldDirectory.toAbsolutePath().normalize();
    }

    public Path requireWorldDirectory() {
        if (worldDirectory == null) throw new IllegalArgumentException("current world directory is unavailable");
        return worldDirectory;
    }
}
