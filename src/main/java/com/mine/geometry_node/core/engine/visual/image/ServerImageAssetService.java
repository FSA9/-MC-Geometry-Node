package com.mine.geometry_node.core.engine.visual.image;

import com.mine.geometry_node.core.engine.graph.storage.DynamicGraphManager;
import com.mine.geometry_node.core.engine.service.GraphEngineServices;
import com.mine.geometry_node.core.utils.ServerAssetPaths;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ServerImageAssetService {
    private static final int MAX_CACHE_ENTRIES = 128;
    private static final Map<Path, CachedAsset> CACHE = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Path, CachedAsset> eldest) {
            return size() > MAX_CACHE_ENTRIES;
        }
    };

    private ServerImageAssetService() {
    }

    public static synchronized GraphEngineServices.VisualAsset load(MinecraftServer server, String relativePath) throws IOException {
        if (server == null) {
            throw new IOException("Missing server");
        }

        Path root = server.getWorldPath(DynamicGraphManager.GRAPH_DIR).toAbsolutePath().normalize();
        Path path = ServerAssetPaths.resolveUnderRoot(root, relativePath, false);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw new IOException("Server image does not exist: " + relativePath);
        }

        Path realRoot = root.toRealPath();
        Path realPath = path.toRealPath();
        if (!realPath.startsWith(realRoot)) {
            throw new IOException("Server image escapes geometry_nodes: " + relativePath);
        }

        long size = Files.size(realPath);
        if (size <= 0 || size > ImageAssetValidator.MAX_ENCODED_BYTES) {
            throw new IOException("Server image has an unsupported file size: " + size);
        }
        FileTime modified = Files.getLastModifiedTime(realPath);
        CachedAsset cached = CACHE.get(realPath);
        if (cached != null && cached.size == size && cached.modified.equals(modified)) {
            return cached.asset;
        }

        byte[] data = Files.readAllBytes(realPath);
        ImageAssetValidator.validateImage(data);
        GraphEngineServices.VisualAsset asset = new GraphEngineServices.VisualAsset(
                ImageAssetValidator.contentId(data),
                data
        );
        CACHE.put(realPath, new CachedAsset(size, modified, asset));
        return asset;
    }

    private record CachedAsset(long size, FileTime modified, GraphEngineServices.VisualAsset asset) {
    }
}
