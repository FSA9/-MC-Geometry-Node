package com.mine.geometry_node.client.ui.editor.asset.schematic;

import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewFormat;

import java.util.Arrays;

public record SchematicUploadPreview(AssetPreviewFormat format, int width, int height, byte[] content) {
    public SchematicUploadPreview {
        content = Arrays.copyOf(content, content.length);
    }

    @Override
    public byte[] content() {
        return Arrays.copyOf(content, content.length);
    }
}
