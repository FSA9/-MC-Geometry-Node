package com.mine.geometry_node.core.engine.system.data.library;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;

/** World-owned location of the single server Data Library database. */
public final class ServerDataLibraryPaths {
    private static final LevelResource ROOT = new LevelResource("geometry_node");

    private ServerDataLibraryPaths() {
    }

    public static Path file(MinecraftServer server) {
        if (server == null) throw new IllegalArgumentException("server must not be null");
        return server.getWorldPath(ROOT).resolve(DataLibraryFileStore.DEFAULT_FILE_NAME)
                .toAbsolutePath().normalize();
    }
}
