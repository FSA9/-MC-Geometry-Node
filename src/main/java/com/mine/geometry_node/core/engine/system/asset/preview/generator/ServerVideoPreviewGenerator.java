package com.mine.geometry_node.core.engine.system.asset.preview.generator;

import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewRevision;
import com.mine.geometry_node.core.engine.system.asset.preview.store.ServerAssetPreviewStore;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Video preview seam. A media backend can implement {@link FrameExtractor} and pass it to
 * this generator to publish a PNG first frame through the normal preview store.
 */
public final class ServerVideoPreviewGenerator implements ServerAssetPreviewGenerator {
    private static volatile FrameExtractor installedExtractor;
    @FunctionalInterface
    public interface FrameExtractor {
        ServerAssetPreviewStore.StoredPreview extract(MinecraftServer server, Path source,
                                                      AssetPreviewRevision revision,
                                                      ServerAssetPreviewStore store) throws IOException;
    }

    private final ServerAssetPreviewStore store;
    private final FrameExtractor extractor;

    /** Installs the optional media backend before the preview service resolves video generators. */
    public static void installBackend(FrameExtractor extractor) {
        installedExtractor = extractor;
    }

    public static void clearBackend() {
        installedExtractor = null;
    }

    public static FrameExtractor installedBackend() {
        return installedExtractor;
    }

    public ServerVideoPreviewGenerator(ServerAssetPreviewStore store) {
        this(store, installedExtractor);
    }

    public ServerVideoPreviewGenerator(ServerAssetPreviewStore store, FrameExtractor extractor) {
        this.store = store;
        this.extractor = extractor;
    }

    @Override
    public ServerAssetPreviewStore.StoredPreview generate(MinecraftServer server, Path source,
                                                          AssetPreviewRevision revision) throws IOException {
        if (extractor == null) {
            throw new PreviewUnavailableException("Video preview backend is not installed");
        }
        return extractor.extract(server, source, revision, store);
    }
}
