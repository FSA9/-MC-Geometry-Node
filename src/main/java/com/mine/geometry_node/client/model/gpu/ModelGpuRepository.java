package com.mine.geometry_node.client.model.gpu;

import com.mine.geometry_node.core.engine.system.model.identity.ModelAssetReference;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class ModelGpuRepository implements AutoCloseable {
    private final ModelGpuDevice device;
    private final RenderThreadDispatcher renderThread;
    private final Map<ModelAssetReference, Entry> entries = new HashMap<>();
    private boolean closed;
    private long uploadAttempts;
    private long completedUploads;
    private long failedUploads;
    private long cancelledUploads;
    private long releasedResources;
    private long createdBuffers;
    private long createdTextures;
    private long liveBufferBytes;
    private long liveTextureBytes;
    private int pendingUploads;
    private int liveResources;

    public ModelGpuRepository(ModelGpuDevice device, RenderThreadDispatcher renderThread) {
        this.device = Objects.requireNonNull(device, "device");
        this.renderThread = Objects.requireNonNull(renderThread, "renderThread");
    }

    public CompletableFuture<ModelGpuLease> acquire(ModelGpuUploadPlan plan) {
        Objects.requireNonNull(plan, "plan");
        Entry entry;
        synchronized (this) {
            if (closed) return CompletableFuture.failedFuture(new IllegalStateException("GPU model repository is closed"));
            entry = entries.get(plan.source());
            if (entry == null) {
                entry = new Entry(plan.source());
                entries.put(plan.source(), entry);
                scheduleUpload(entry, plan);
            }
            entry.references++;
        }
        Entry acquired = entry;
        CompletableFuture<ModelGpuLease> result = new CompletableFuture<>();
        entry.resource.whenComplete((resource, failure) -> {
            if (failure != null) {
                result.completeExceptionally(failure);
            } else if (!result.complete(new ModelGpuLease(this, acquired, resource))) {
                release(acquired);
            }
        });
        return result;
    }

    public synchronized int cachedResourceCount() { return entries.size(); }

    public synchronized ModelGpuRepositoryDiagnostics diagnostics() {
        return new ModelGpuRepositoryDiagnostics(uploadAttempts, completedUploads, failedUploads, cancelledUploads,
                releasedResources, entries.size(), pendingUploads, liveResources, createdBuffers, createdTextures,
                liveBufferBytes, liveTextureBytes);
    }

    private void scheduleUpload(Entry entry, ModelGpuUploadPlan plan) {
        synchronized (this) { uploadAttempts++; pendingUploads++; }
        renderThread.execute(() -> {
            synchronized (this) {
                if (closed || entries.get(entry.source) != entry) {
                    pendingUploads--;
                    cancelledUploads++;
                    entry.resource.completeExceptionally(new IllegalStateException("GPU upload was cancelled"));
                    return;
                }
            }
            try {
                ModelGpuResource resource = upload(plan);
                synchronized (this) {
                    completedUploads++;
                    pendingUploads--;
                    liveResources++;
                    createdBuffers += resource.bufferCount();
                    createdTextures += resource.textures().size();
                    liveBufferBytes += resource.bufferBytes();
                    liveTextureBytes += resource.textureBytes();
                }
                entry.resource.complete(resource);
            } catch (Throwable throwable) {
                synchronized (this) {
                    entries.remove(entry.source, entry);
                    failedUploads++;
                    pendingUploads--;
                }
                entry.resource.completeExceptionally(throwable);
            }
        });
    }

    private ModelGpuResource upload(ModelGpuUploadPlan plan) {
        renderThread.assertRenderThread();
        List<ModelGpuLayoutGroup> groups = new ArrayList<>(plan.layoutGroups().size());
        List<ModelGpuTexture> textures = new ArrayList<>(plan.images().size());
        try {
            for (int index = 0; index < plan.layoutGroups().size(); index++) {
                ModelGpuLayoutGroupPlan group = plan.layoutGroups().get(index);
                ModelGpuBuffer vertices = device.createBuffer(label(plan, "vertices", index),
                        ModelGpuBufferKind.VERTEX, group.vertexData());
                ModelGpuBuffer indices = null;
                try {
                    indices = device.createBuffer(label(plan, "indices", index),
                            ModelGpuBufferKind.INDEX, group.indexData());
                    groups.add(new ModelGpuLayoutGroup(group.layout(), group.vertexStride(), group.vertexCount(),
                            group.indexCount(), vertices, indices));
                    vertices = null;
                    indices = null;
                } finally {
                    close(vertices);
                    close(indices);
                }
            }
            for (int index = 0; index < plan.images().size(); index++) {
                textures.add(device.createTexture(label(plan, "texture", index), plan.images().get(index)));
            }
            return new ModelGpuResource(plan.source(), groups, plan.drawRanges(), textures, plan.images());
        } catch (Throwable throwable) {
            close(groups, textures);
            throw throwable;
        }
    }

    private static String label(ModelGpuUploadPlan plan, String kind, int index) {
        return "GeometryNode model " + plan.source().normalizedPath() + " " + kind + " " + index;
    }

    void release(Entry entry) {
        ModelGpuResource resource = null;
        synchronized (this) {
            if (entry.references <= 0) throw new IllegalStateException("GPU model reference count underflow");
            entry.references--;
            if (entry.references == 0 && entries.remove(entry.source, entry)) {
                resource = entry.resource.getNow(null);
            }
        }
        if (resource != null) scheduleClose(resource);
    }

    private void scheduleClose(ModelGpuResource resource) {
        renderThread.execute(() -> {
            renderThread.assertRenderThread();
            long bufferBytes = resource.bufferBytes();
            long textureBytes = resource.textureBytes();
            resource.close();
            synchronized (this) {
                releasedResources++;
                liveResources = Math.max(0, liveResources - 1);
                liveBufferBytes = Math.max(0L, liveBufferBytes - bufferBytes);
                liveTextureBytes = Math.max(0L, liveTextureBytes - textureBytes);
            }
        });
    }

    @Override
    public void close() {
        List<Entry> removed;
        synchronized (this) {
            if (closed) return;
            closed = true;
            removed = List.copyOf(entries.values());
            entries.clear();
        }
        for (Entry entry : removed) {
            entry.resource.thenAccept(this::scheduleClose);
        }
    }

    private static void close(ModelGpuBuffer buffer) {
        if (buffer != null && !buffer.isClosed()) buffer.close();
    }

    private static void close(List<ModelGpuLayoutGroup> groups, List<ModelGpuTexture> textures) {
        for (ModelGpuLayoutGroup group : groups) {
            close(group.vertexBuffer());
            close(group.indexBuffer());
        }
        for (ModelGpuTexture texture : textures) {
            if (texture != null && !texture.isClosed()) texture.close();
        }
    }

    static final class Entry {
        private final ModelAssetReference source;
        private final CompletableFuture<ModelGpuResource> resource = new CompletableFuture<>();
        private int references;

        private Entry(ModelAssetReference source) { this.source = source; }
    }
}
