package com.mine.geometry_node.core.engine.system.image;

import com.mine.geometry_node.core.engine.system.asset.reference.AssetReference;
import com.mine.geometry_node.core.engine.system.asset.reference.AssetSource;
import com.mine.geometry_node.core.engine.system.asset.reference.AssetSourceRegistry;

/** Validated image reference used by the image visual node. */
public record ImagePathReference(Source source, String path) {
    public static ImagePathReference parse(String rawPath) {
        return parse(rawPath, AssetSourceRegistry.PACK);
    }

    public static ImagePathReference parse(String rawPath, AssetSource defaultSource) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new IllegalArgumentException("image path must not be empty");
        }

        AssetReference reference = AssetReference.parse(rawPath, defaultSource);
        requireSupportedImage(reference.location());
        if (reference.isRemote()) return new ImagePathReference(Source.SERVER, reference.location());
        if (reference.isPack()) return new ImagePathReference(Source.PACK, reference.location());
        if (reference.isWorldPack()) return new ImagePathReference(Source.WORLD_PACK, reference.location());
        return new ImagePathReference(Source.LOCAL, reference.location());
    }

    private static void requireSupportedImage(String path) {
        if (!ImageAssetFormats.isSupportedPath(path)) {
            throw new IllegalArgumentException("unsupported image format: " + path);
        }
    }

    public enum Source {
        SERVER("server"),
        LOCAL("local"),
        PACK("pack"),
        WORLD_PACK("worldpack");

        private final String id;

        Source(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }
}
