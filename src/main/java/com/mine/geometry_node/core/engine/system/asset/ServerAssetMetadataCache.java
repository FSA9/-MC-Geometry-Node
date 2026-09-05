package com.mine.geometry_node.core.engine.system.asset;

import com.mine.geometry_node.core.engine.runtime.ServerEngine;
import com.mine.geometry_node.core.network.packet.asset.AssetPacketLimits;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Server-scoped, self-validating metadata cache for files visible in the asset repository. */
public final class ServerAssetMetadataCache implements ServerEngine {
    public static final ServerAssetMetadataCache INSTANCE = new ServerAssetMetadataCache();
    private static final int MAX_ENTRIES_PER_SERVER = Math.max(
            AssetPacketLimits.MAX_REPOSITORY_ENTRIES,
            AssetPacketLimits.MAX_TRANSFER_MANIFEST_ENTRIES);

    private final Map<MinecraftServer, ServerCache> caches = new WeakHashMap<>();
    private final Set<MinecraftServer> stoppedServers = Collections.newSetFromMap(new WeakHashMap<>());

    private ServerAssetMetadataCache() {
    }

    @Override
    public String id() {
        return "geometry_node:asset_metadata_cache";
    }

    public AssetMetadata inspect(MinecraftServer server, Path file, String logicalPath) {
        return describe(server, file, logicalPath).metadata();
    }

    public Inspection describe(MinecraftServer server, Path file, String logicalPath) {
        if (server == null || file == null) return Inspection.UNKNOWN;
        ServerCache cache = cacheFor(server);
        if (cache == null) return Inspection.UNKNOWN;
        Path normalized = file.toAbsolutePath().normalize();
        String normalizedLogicalPath = logicalPath == null ? "" : logicalPath.trim().replace('\\', '/');
        try {
            BasicFileAttributes attributes = Files.readAttributes(
                    normalized, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile() || attributes.isSymbolicLink()) return Inspection.UNKNOWN;

            FileRevision revision = new FileRevision(
                    attributes.fileKey(), attributes.size(), attributes.lastModifiedTime());
            CacheKey key = new CacheKey(normalized, normalizedLogicalPath);
            AssetMetadata cached = cache.get(key, revision);
            if (cached != null) return new Inspection(cached, revision.size(), revision.lastModified().toMillis());

            AssetMetadata inspected = AssetTypeCatalog.inspect(normalized, normalizedLogicalPath);
            if (!isStopped(server)) cache.put(key, revision, inspected);
            return new Inspection(inspected, revision.size(), revision.lastModified().toMillis());
        } catch (IOException | RuntimeException ignored) {
            return Inspection.UNKNOWN;
        }
    }

    public synchronized void invalidate(MinecraftServer server, Path file) {
        ServerCache cache = caches.get(server);
        if (cache != null && file != null) cache.removePath(file.toAbsolutePath().normalize());
    }

    @Override
    public synchronized void shutdown(MinecraftServer server) {
        stoppedServers.add(server);
        caches.remove(server);
    }

    private synchronized ServerCache cacheFor(MinecraftServer server) {
        if (stoppedServers.contains(server)) return null;
        return caches.computeIfAbsent(server, ignored -> new ServerCache());
    }

    private synchronized boolean isStopped(MinecraftServer server) {
        return stoppedServers.contains(server);
    }

    public record Inspection(AssetMetadata metadata, long size, long lastModifiedMillis) {
        private static final Inspection UNKNOWN = new Inspection(AssetMetadata.UNKNOWN, 0L, 0L);

        public Inspection {
            metadata = metadata == null ? AssetMetadata.UNKNOWN : metadata;
        }
    }

    private static final class ServerCache {
        private final LinkedHashMap<CacheKey, CachedMetadata> entries = new LinkedHashMap<>(16, 0.75f, true);

        private synchronized AssetMetadata get(CacheKey key, FileRevision revision) {
            CachedMetadata cached = entries.get(key);
            if (cached == null) return null;
            if (cached.revision.equals(revision)) return cached.metadata;
            entries.remove(key);
            return null;
        }

        private synchronized void put(CacheKey key, FileRevision revision, AssetMetadata metadata) {
            entries.put(key, new CachedMetadata(revision, metadata));
            while (entries.size() > MAX_ENTRIES_PER_SERVER) {
                entries.remove(entries.keySet().iterator().next());
            }
        }

        private synchronized void removePath(Path path) {
            entries.keySet().removeIf(key -> key.path().equals(path));
        }
    }

    private record CacheKey(Path path, String logicalPath) {
    }

    private record FileRevision(Object fileKey, long size, FileTime lastModified) {
    }

    private record CachedMetadata(FileRevision revision, AssetMetadata metadata) {
    }
}
