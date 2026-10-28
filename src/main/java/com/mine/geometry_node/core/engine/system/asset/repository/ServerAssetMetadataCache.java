package com.mine.geometry_node.core.engine.system.asset.repository;

import com.mine.geometry_node.core.engine.system.asset.AssetMetadata;
import com.mine.geometry_node.core.engine.system.asset.AssetDocumentInfo;
import com.mine.geometry_node.core.engine.system.asset.type.AssetTypeCatalog;

import com.mine.geometry_node.core.engine.lifecycle.ServerEngine;
import com.mine.geometry_node.core.network.packet.asset.AssetPacketLimits;
import net.minecraft.server.MinecraftServer;

import com.mine.geometry_node.core.cache.*;
import java.util.concurrent.CompletionStage;
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

    private static final long MAX_ESTIMATED_BYTES_PER_SERVER = 32L * 1024 * 1024;

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
            Inspection cached = cache.get(key, revision);
            if (cached != null) return cached;

            long generation = cache.generation();
            AssetMetadata inspected = AssetTypeCatalog.inspect(normalized, normalizedLogicalPath);
            Inspection result = new Inspection(inspected, revision.size(), revision.lastModified().toMillis(),
                    AssetDocumentInfo.inspect(normalized, inspected));
            if (!isStopped(server)) cache.put(key, revision, result, generation);
            return result;
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
        ServerCache cache = caches.remove(server);
        if (cache != null) cache.close();
    }

    private synchronized ServerCache cacheFor(MinecraftServer server) {
        if (stoppedServers.contains(server)) return null;
        return caches.computeIfAbsent(server, ignored -> new ServerCache());
    }

    private synchronized boolean isStopped(MinecraftServer server) {
        return stoppedServers.contains(server);
    }

    public record Inspection(AssetMetadata metadata, long size, long lastModifiedMillis,
                             AssetDocumentInfo documentInfo) {
        public Inspection(AssetMetadata metadata, long size, long lastModifiedMillis) {
            this(metadata, size, lastModifiedMillis, AssetDocumentInfo.EMPTY);
        }
        private static final Inspection UNKNOWN = new Inspection(AssetMetadata.UNKNOWN, 0L, 0L);

        public Inspection {
            metadata = metadata == null ? AssetMetadata.UNKNOWN : metadata;
            documentInfo = documentInfo == null ? AssetDocumentInfo.EMPTY : documentInfo;
        }
    }

    private static final class ServerCache {
        private final LinkedHashMap<CacheKey, CachedMetadata> entries = new LinkedHashMap<>(16, 0.75f, true);
        private long generation;
        private long estimatedBytes;
        private final CacheRegistry.Registration registration = CacheRegistry.GLOBAL.register(new CacheDescriptor(
                "geometry_node:asset.server.metadata", "geometry_node.cache.asset.server_metadata",
                CacheDescriptor.Domain.SERVER, CacheDescriptor.Storage.MEMORY,
                CacheDescriptor.Lifetime.WORLD), new ManagedCache() {
            @Override public CompletionStage<CacheStats> stats() {
                return CacheExecutors.submit(() -> {
                    synchronized (ServerCache.this) { return CacheStats.memory(entries.size(), estimatedBytes); }
                });
            }
            @Override public CompletionStage<Void> clear(CacheClearRequest request) {
                return CacheExecutors.run(ServerCache.this::clear);
            }
        });

        private synchronized long generation() { return generation; }
        private synchronized void clear() { generation++; entries.clear(); estimatedBytes = 0; }
        private void close() { registration.close(); clear(); }

        private synchronized Inspection get(CacheKey key, FileRevision revision) {
            CachedMetadata cached = entries.get(key);
            if (cached == null) return null;
            if (cached.revision.equals(revision)) return cached.inspection;
            entries.remove(key);
            estimatedBytes -= cached.estimatedBytes();
            return null;
        }

        private synchronized void put(CacheKey key, FileRevision revision, Inspection inspection, long expectedGeneration) {
            if (generation != expectedGeneration) return;
            long weight = 160L + 2L * (key.path().toString().length() + key.logicalPath().length()
                    + inspection.metadata().typeId().length() + inspection.metadata().variantId().length()
                    + inspection.documentInfo().description().length());
            for (String tag : inspection.documentInfo().tags()) weight += 40L + 2L * tag.length();
            CachedMetadata previous = entries.put(key, new CachedMetadata(revision, inspection, weight));
            if (previous != null) estimatedBytes -= previous.estimatedBytes();
            estimatedBytes += weight;
            while (entries.size() > MAX_ENTRIES_PER_SERVER || estimatedBytes > MAX_ESTIMATED_BYTES_PER_SERVER) {
                var oldest = entries.entrySet().iterator();
                estimatedBytes -= oldest.next().getValue().estimatedBytes();
                oldest.remove();
            }
        }

        private synchronized void removePath(Path path) {
            generation++;
            var iterator = entries.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                if (entry.getKey().path().equals(path)) {
                    estimatedBytes -= entry.getValue().estimatedBytes();
                    iterator.remove();
                }
            }
        }
    }

    private record CacheKey(Path path, String logicalPath) {
    }

    private record FileRevision(Object fileKey, long size, FileTime lastModified) {
    }

    private record CachedMetadata(FileRevision revision, Inspection inspection, long estimatedBytes) {
    }
}
