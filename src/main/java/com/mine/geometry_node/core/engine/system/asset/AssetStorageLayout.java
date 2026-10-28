package com.mine.geometry_node.core.engine.system.asset;

import com.mine.geometry_node.core.utils.io.DirectoryMigration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/** Physical asset roots shared by local workspaces and worlds, independent of graph IDs. */
public final class AssetStorageLayout {
    public static final String COMMON_DIRECTORY = "geometry_nodes/common";
    public static final String PACKS_DIRECTORY = "geometry_nodes/packs";
    private static final List<String> LEGACY_LOCAL_DIRECTORIES = List.of(
            "geometry_nodes/graphs", "geometry_nodes/local_drafts");
    private static final Set<String> WORLD_STORES = Set.of(
            "common", "graphs", "packs", "caches", "data_library.json");

    private AssetStorageLayout() {}

    public static Path commonRoot(Path ownerDirectory) {
        return ownerDirectory.resolve(COMMON_DIRECTORY);
    }

    public static Path packsRoot(Path ownerDirectory) {
        return ownerDirectory.resolve(PACKS_DIRECTORY);
    }

    /** Migrates local graph/draft files without changing their relative paths or contents. */
    public static synchronized Path prepareLocal(Path gameDirectory) throws IOException {
        Path target = commonRoot(gameDirectory);
        for (String directory : LEGACY_LOCAL_DIRECTORIES) {
            DirectoryMigration.move(gameDirectory.resolve(directory), target);
        }
        Files.createDirectories(target);
        return target;
    }

    /** Also accepts old worlds whose graph files lived directly under geometry_nodes. */
    public static synchronized Path prepareWorld(Path worldDirectory) throws IOException {
        Path target = commonRoot(worldDirectory);
        DirectoryMigration.move(worldDirectory.resolve("geometry_nodes/graphs"), target);
        DirectoryMigration.moveContents(worldDirectory.resolve("geometry_nodes"), target, WORLD_STORES);
        Files.createDirectories(target);
        return target;
    }

    /** Handles persisted relative and Windows/POSIX paths without changing their prefix or suffix. */
    public static String migrateLocalReference(String value) {
        if (value == null || value.isBlank()) return value;
        String normalized = value.replace('\\', '/');
        for (String legacy : LEGACY_LOCAL_DIRECTORIES) {
            int start = normalized.indexOf(legacy);
            if (start < 0 || start > 0 && normalized.charAt(start - 1) != '/') continue;
            int end = start + legacy.length();
            if (end < normalized.length() && normalized.charAt(end) != '/') continue;
            String replacement = value.indexOf('\\') >= 0 && value.indexOf('/') < 0
                    ? COMMON_DIRECTORY.replace('/', '\\') : COMMON_DIRECTORY;
            return value.substring(0, start) + replacement + value.substring(end);
        }
        return value;
    }
}
