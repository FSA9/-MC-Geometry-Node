package com.mine.geometry_node.core.engine.system.asset.preview;

import com.mine.geometry_node.core.engine.system.asset.transfer.io.AssetTransferHashing;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public record AssetPreviewRevision(
        AssetPreviewIdentity identity,
        long sourceSize,
        long sourceLastModified,
        int formatVersion
) {
    public AssetPreviewRevision {
        identity = Objects.requireNonNull(identity, "identity");
        if (sourceSize < 0L) throw new IllegalArgumentException("sourceSize must not be negative");
        if (sourceLastModified < 0L) throw new IllegalArgumentException("sourceLastModified must not be negative");
        if (formatVersion <= 0) throw new IllegalArgumentException("formatVersion must be positive");
    }

    public static AssetPreviewRevision current(AssetPreviewIdentity identity, long sourceSize,
                                               long sourceLastModified) {
        return new AssetPreviewRevision(identity, sourceSize, sourceLastModified, AssetPreviewLimits.FORMAT_VERSION);
    }

    public String cacheKey() {
        String canonical = identity.remotePath() + '\0' + identity.kind().id() + '\0'
                + sourceSize + '\0' + sourceLastModified + '\0' + formatVersion;
        var digest = AssetTransferHashing.newSha256();
        return AssetTransferHashing.toHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
    }
}
