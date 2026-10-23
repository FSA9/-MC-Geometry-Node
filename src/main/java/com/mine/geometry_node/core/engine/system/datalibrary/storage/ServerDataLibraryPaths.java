package com.mine.geometry_node.core.engine.system.datalibrary.storage;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;

/** World-owned database at {@code <world>/data/geometry_node/data_library.json}. */
public final class ServerDataLibraryPaths {
    private static final LevelResource ROOT = new LevelResource("data/geometry_node");

    private ServerDataLibraryPaths() {
    }

    public static Path file(MinecraftServer server) {
        if (server == null) throw new IllegalArgumentException("server must not be null");
        return server.getWorldPath(ROOT).resolve(DataLibraryFileStore.DEFAULT_FILE_NAME)
                .toAbsolutePath().normalize();
    }
}
