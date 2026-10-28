package com.mine.geometry_node.core.engine.graph.storage;

import com.mine.geometry_node.core.utils.io.DirectoryMigration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/** Physical graph directories; graph IDs remain relative to these roots. */
public final class GraphStorageLayout {
    public static final String DIRECTORY = "geometry_nodes/graphs";
    private static final String LEGACY_LOCAL_DIRECTORY = "geometry_nodes/local_drafts";

    private GraphStorageLayout() {}

    public static Path prepareLocal(Path gameDirectory) throws IOException {
        Path target = gameDirectory.resolve(DIRECTORY);
        DirectoryMigration.move(gameDirectory.resolve(LEGACY_LOCAL_DIRECTORY), target);
        Files.createDirectories(target);
        return target;
    }

    public static void migrateServer(Path worldDirectory) throws IOException {
        Path legacy = worldDirectory.resolve("geometry_nodes");
        DirectoryMigration.moveContents(legacy, worldDirectory.resolve(DIRECTORY),
                Set.of("graphs", "packs", "caches", "data_library.json"));
    }

    /** Handles relative paths and persisted Windows/POSIX paths without changing their prefix. */
    public static String migrateLocalReference(String value) {
        if (value == null || value.isBlank()) return value;
        String normalized = value.replace('\\', '/');
        int start = normalized.indexOf(LEGACY_LOCAL_DIRECTORY);
        if (start < 0 || start > 0 && normalized.charAt(start - 1) != '/') return value;
        int end = start + LEGACY_LOCAL_DIRECTORY.length();
        if (end < normalized.length() && normalized.charAt(end) != '/') return value;
        String replacement = value.indexOf('\\') >= 0 && value.indexOf('/') < 0
                ? DIRECTORY.replace('/', '\\') : DIRECTORY;
        return value.substring(0, start) + replacement + value.substring(end);
    }
}
