package com.mine.geometry_node.client.asset.remote;

import com.mine.geometry_node.core.cache.CacheClearRequest;
import com.mine.geometry_node.core.cache.CacheDescriptor;
import com.mine.geometry_node.core.cache.CacheExecutors;
import com.mine.geometry_node.core.cache.CacheRegistry;
import com.mine.geometry_node.core.cache.CacheStats;
import com.mine.geometry_node.core.cache.ManagedCache;
import java.util.concurrent.CompletionStage;

import com.mine.geometry_node.core.engine.system.asset.AssetDescriptor;
import com.mine.geometry_node.core.engine.system.asset.AssetRelativePath;
import com.mine.geometry_node.core.engine.system.asset.AssetTypeCatalog;
import com.mine.geometry_node.core.network.packet.asset.repository.PacketRemoteAssetFileOperationRequest;
import com.mine.geometry_node.core.network.packet.asset.repository.PacketRemoteAssetListResponse;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Graph paths observed in authoritative remote-directory snapshots.
 *
 * <p>This is a partial client-side index used for command suggestions, not a complete repository listing.
 * Mutations invalidate affected directory trees so an observed path is never retained as if it were current.</p>
 */
public final class RemoteGraphPathIndex {
    public static final RemoteGraphPathIndex INSTANCE = new RemoteGraphPathIndex();

    private final Map<String, DirectorySnapshot> directories = new HashMap<>();

    private RemoteGraphPathIndex() {
        CacheRegistry.GLOBAL.register(new CacheDescriptor(
                "geometry_node:asset.remote.graph_paths", "geometry_node.cache.asset.graph_paths",
                CacheDescriptor.Domain.CLIENT, CacheDescriptor.Storage.MEMORY,
                CacheDescriptor.Lifetime.APPLICATION), new ManagedCache() {
            @Override public CompletionStage<CacheStats> stats() {
                return CacheExecutors.submit(() -> {
                    synchronized (RemoteGraphPathIndex.this) {
                        return CacheStats.memory(directories.size(), -1);
                    }
                });
            }
            @Override public CompletionStage<Void> clear(CacheClearRequest request) {
                return CacheExecutors.run(RemoteGraphPathIndex.this::clear);
            }
        });
    }

    public void registerCache() {
        // Registration belongs to this process-wide index, contents reset with the connection.
    }

    public synchronized List<String> paths() {
        return directories.values().stream()
                .flatMap(snapshot -> snapshot.graphPaths().stream())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public synchronized void accept(PacketRemoteAssetListResponse response) {
        if (response == null || !response.success()) return;
        String directory;
        try {
            directory = normalizeDirectory(response.directory());
        } catch (IllegalArgumentException ignored) {
            return;
        }

        Set<String> childDirectories = new HashSet<>();
        Set<String> graphPaths = new HashSet<>();
        for (AssetDescriptor entry : response.entries()) {
            String path;
            try {
                path = AssetRelativePath.normalize(entry.path(), false);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            if (entry.directory()) {
                if (directory.equals(parent(path))) childDirectories.add(path);
            } else if (directory.equals(parent(path))
                    && AssetTypeCatalog.GRAPH_TYPE_ID.equals(entry.metadata().typeId())) {
                graphPaths.add(path);
            }
        }

        directories.keySet().removeIf(cachedDirectory -> isMissingChildTree(
                cachedDirectory, directory, childDirectories));
        directories.put(directory, new DirectorySnapshot(Set.copyOf(graphPaths)));
    }

    public synchronized void invalidateMutation(PacketRemoteAssetFileOperationRequest.Operation operation,
                                                Collection<String> sourcePaths,
                                                String destinationPath) {
        if (operation == null) return;
        Set<String> roots = new HashSet<>();
        if (sourcePaths != null) {
            for (String source : sourcePaths) addAffectedRoots(roots, source);
        }
        if (destinationPath != null && !destinationPath.isBlank()) {
            addAffectedRoots(roots, destinationPath);
        }
        for (String root : roots) invalidateTree(root);
    }

    public synchronized void clear() {
        directories.clear();
    }

    private void invalidateTree(String root) {
        directories.keySet().removeIf(directory -> isSameOrDescendant(directory, root));
    }

    private static void addAffectedRoots(Set<String> roots, String path) {
        try {
            String normalized = AssetRelativePath.normalize(path, false);
            roots.add(normalized);
            roots.add(parent(normalized));
        } catch (IllegalArgumentException ignored) {
        }
    }

    private static boolean isMissingChildTree(String candidate, String refreshedDirectory,
                                              Set<String> existingChildren) {
        if (!isDescendant(candidate, refreshedDirectory)) return false;
        String relative = refreshedDirectory.isEmpty()
                ? candidate
                : candidate.substring(refreshedDirectory.length() + 1);
        int separator = relative.indexOf('/');
        String childName = separator < 0 ? relative : relative.substring(0, separator);
        String childPath = refreshedDirectory.isEmpty()
                ? childName
                : refreshedDirectory + "/" + childName;
        return !existingChildren.contains(childPath);
    }

    private static boolean isSameOrDescendant(String candidate, String root) {
        if (root.isEmpty()) return true;
        return candidate.equals(root) || candidate.startsWith(root + "/");
    }

    private static boolean isDescendant(String candidate, String parent) {
        return !candidate.equals(parent) && (parent.isEmpty() || candidate.startsWith(parent + "/"));
    }

    private static String normalizeDirectory(String path) {
        return path == null || path.isBlank() ? "" : AssetRelativePath.normalize(path, false);
    }

    private static String parent(String path) {
        int separator = path.lastIndexOf('/');
        return separator < 0 ? "" : path.substring(0, separator);
    }

    private record DirectorySnapshot(Set<String> graphPaths) {
    }
}
