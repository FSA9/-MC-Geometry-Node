package com.mine.geometry_node.core.schematic;

import com.mine.geometry_node.core.engine.system.asset.ServerAssetPaths;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class SchematicPaths {
    private SchematicPaths() {
    }

    public static Path resolveServerPath(MinecraftServer server, String rawPath) throws IOException {
        if (server == null) {
            throw new IOException("Missing server");
        }

        String relative = normalizeSchematicPath(rawPath);
        Path root = ServerAssetPaths.root(server);
        Path path = ServerAssetPaths.resolveUnderRoot(root, relative, false);
        if (!Files.isRegularFile(path)) {
            throw new IOException("Schematic file does not exist in geometry_nodes: " + relative);
        }
        return path;
    }

    public static String normalizeSchematicPath(String rawPath) throws IOException {
        String relative;
        try {
            relative = ServerAssetPaths.normalizeRelativePath(rawPath, false);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid schematic path: " + e.getMessage(), e);
        }
        String lower = relative.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".schem") && !lower.endsWith(".schematic")) {
            throw new IOException("Schematic path must end with .schem or .schematic: " + relative);
        }
        return relative;
    }
}
