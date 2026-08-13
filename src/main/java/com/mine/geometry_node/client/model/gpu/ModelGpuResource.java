package com.mine.geometry_node.client.model.gpu;

import com.mine.geometry_node.core.engine.system.model.api.ModelAssetReference;

import java.util.List;

public final class ModelGpuResource implements AutoCloseable {
    private final ModelAssetReference source;
    private final List<ModelGpuLayoutGroup> layoutGroups;
    private final List<ModelGpuDrawRange> drawRanges;
    private final List<ModelGpuTexture> textures;
    private boolean closed;

    ModelGpuResource(ModelAssetReference source, List<ModelGpuLayoutGroup> layoutGroups,
                     List<ModelGpuDrawRange> drawRanges, List<ModelGpuTexture> textures,
                     List<ModelGpuImagePlan> imagePlans) {
        this.source = source;
        this.layoutGroups = List.copyOf(layoutGroups);
        this.drawRanges = List.copyOf(drawRanges);
        this.textures = List.copyOf(textures);
        if (textures.size() != imagePlans.size()) throw new IllegalArgumentException("texture plan count mismatch");
        for (int index = 0; index < imagePlans.size(); index++) {
            if (imagePlans.get(index).imageIndex() != index) {
                throw new IllegalArgumentException("texture plans must follow image index order");
            }
        }
    }

    public ModelAssetReference source() { return source; }
    public List<ModelGpuLayoutGroup> layoutGroups() { return layoutGroups; }
    public List<ModelGpuDrawRange> drawRanges() { return drawRanges; }
    public List<ModelGpuTexture> textures() { return textures; }
    public ModelGpuTexture texture(int imageIndex) {
        if (imageIndex < 0 || imageIndex >= textures.size()) {
            throw new IllegalArgumentException("GPU texture image index is out of range: " + imageIndex);
        }
        return textures.get(imageIndex);
    }
    public long bufferBytes() {
        return layoutGroups.stream().mapToLong(group ->
                (long) group.vertexBuffer().byteSize() + group.indexBuffer().byteSize()).sum();
    }
    public long textureBytes() {
        return textures.stream().mapToLong(ModelGpuTexture::byteSize).sum();
    }
    public int bufferCount() { return Math.multiplyExact(layoutGroups.size(), 2); }
    public synchronized boolean isClosed() { return closed; }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        for (ModelGpuLayoutGroup group : layoutGroups) {
            group.vertexBuffer().close();
            group.indexBuffer().close();
        }
        for (ModelGpuTexture texture : textures) texture.close();
    }
}
