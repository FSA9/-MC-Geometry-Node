package com.mine.geometry_node.client.model.gpu;

import com.mine.geometry_node.core.engine.system.model.identity.ModelAssetReference;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

public final class ModelGpuResource implements AutoCloseable {
    private final ModelAssetReference source;
    private final List<ModelGpuLayoutGroup> layoutGroups;
    private final List<ModelGpuDrawRange> drawRanges;
    private final Map<ModelGpuTextureKey, ModelGpuTexture> textures;
    private boolean closed;

    ModelGpuResource(ModelAssetReference source, List<ModelGpuLayoutGroup> layoutGroups,
                     List<ModelGpuDrawRange> drawRanges, List<ModelGpuTexture> textures,
                     List<ModelGpuImagePlan> imagePlans) {
        this.source = source;
        this.layoutGroups = List.copyOf(layoutGroups);
        this.drawRanges = List.copyOf(drawRanges);
        if (textures.size() != imagePlans.size()) throw new IllegalArgumentException("texture plan count mismatch");
        Map<ModelGpuTextureKey, ModelGpuTexture> keyed = new LinkedHashMap<>();
        for (int index = 0; index < imagePlans.size(); index++) {
            ModelGpuTexture previous = keyed.put(imagePlans.get(index).key(), textures.get(index));
            if (previous != null) throw new IllegalArgumentException("duplicate GPU texture projection " + imagePlans.get(index).key());
        }
        this.textures = Map.copyOf(keyed);
    }

    public ModelAssetReference source() { return source; }
    public List<ModelGpuLayoutGroup> layoutGroups() { return layoutGroups; }
    public List<ModelGpuDrawRange> drawRanges() { return drawRanges; }
    public List<ModelGpuTexture> textures() { return List.copyOf(textures.values()); }
    public ModelGpuTexture texture(int imageIndex) {
        return texture(new ModelGpuTextureKey(imageIndex, ModelTextureColorSpace.SRGB_COLOR));
    }
    public ModelGpuTexture texture(ModelGpuTextureKey key) {
        ModelGpuTexture texture = textures.get(key);
        if (texture == null) throw new IllegalArgumentException("GPU texture projection is unavailable: " + key);
        return texture;
    }
    public long bufferBytes() {
        return layoutGroups.stream().mapToLong(group ->
                (long) group.vertexBuffer().byteSize() + group.indexBuffer().byteSize()).sum();
    }
    public long textureBytes() {
        return textures.values().stream().mapToLong(ModelGpuTexture::byteSize).sum();
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
        for (ModelGpuTexture texture : textures.values()) texture.close();
    }
}
