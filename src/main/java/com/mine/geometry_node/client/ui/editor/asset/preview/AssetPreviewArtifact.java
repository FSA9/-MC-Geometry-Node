package com.mine.geometry_node.client.ui.editor.asset.preview;

import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewKind;

import java.io.File;
import java.util.Objects;

/** A resolved preview artifact, independent of the provider that produced it. */
public record AssetPreviewArtifact(File file, AssetPreviewKind kind, String mediaType) {
    public AssetPreviewArtifact {
        file = Objects.requireNonNull(file, "file");
        kind = kind == null ? AssetPreviewKind.NONE : kind;
        mediaType = mediaType == null ? "" : mediaType.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public static AssetPreviewArtifact file(File file) {
        return new AssetPreviewArtifact(file, AssetPreviewKind.NONE, "");
    }
}
