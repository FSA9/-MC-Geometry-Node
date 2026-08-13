package com.mine.geometry_node.core.engine.system.model.importer;

import com.mine.geometry_node.core.engine.system.model.api.ModelAssetReference;

import java.util.Arrays;
import java.nio.ByteBuffer;

public final class ModelImportSource {
    private final ModelAssetReference asset;
    private final byte[] content;

    public ModelImportSource(ModelAssetReference asset, byte[] content) {
        if (asset == null || content == null) throw new IllegalArgumentException("import source fields must not be null");
        if (content.length > ModelImportBudget.DEFAULT.maxSourceBytes()) {
            throw new IllegalArgumentException("import source exceeds the hard byte limit");
        }
        if (asset.revision().sourceSize() != content.length) {
            throw new IllegalArgumentException("asset revision size does not match source content");
        }
        this.asset = asset;
        this.content = Arrays.copyOf(content, content.length);
    }

    public ModelAssetReference asset() { return asset; }
    public int byteSize() { return content.length; }
    public byte[] content() { return Arrays.copyOf(content, content.length); }
    public ByteBuffer readOnlyContent() { return ByteBuffer.wrap(content).asReadOnlyBuffer(); }
}
