package com.mine.geometry_node.client.model.gpu;

import com.mine.geometry_node.core.engine.system.model.identity.ModelAssetReference;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class ModelGpuRepository implements AutoCloseable {
    private final ModelGpuDevice device;
    private final RenderThreadDispatcher renderThread;
    private final ModelUploadScheduler uploadScheduler;
    private final boolean autoPump;
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
        this(device, renderThread, new ModelUploadScheduler(renderThread,
                Long.MAX_VALUE, Integer.MAX_VALUE, Long.MAX_VALUE), true);
    }

    public ModelGpuRepository(ModelGpuDevice device, RenderThreadDispatcher renderThread,
                              ModelUploadScheduler uploadScheduler) {
        this(device, renderThread, uploadScheduler, false);
    }

    private ModelGpuRepository(ModelGpuDevice device, RenderThreadDispatcher renderThread,
                               ModelUploadScheduler uploadScheduler, boolean autoPump) {
        this.device = Objects.requireNonNull(device, "device");
        this.renderThread = Objects.requireNonNull(renderThread, "renderThread");
        this.uploadScheduler = Objects.requireNonNull(uploadScheduler, "uploadScheduler");
        this.autoPump = autoPump;
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
        UploadWork work = new UploadWork(entry, plan);
        if (!uploadScheduler.enqueue(work)) {
            work.cancelledByScheduler();
        } else if (autoPump) {
            renderThread.execute(uploadScheduler::pump);
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

    private final class UploadWork implements ModelUploadScheduler.WorkItem {
        private final Entry entry;
        private final ModelGpuUploadPlan plan;
        private final List<ModelGpuLayoutGroup> groups;
        private final List<ModelGpuTexture> textures;
        private int groupIndex;
        private int textureIndex;
        private ModelGpuResource completed;

        private UploadWork(Entry entry, ModelGpuUploadPlan plan) {
            this.entry = entry;
            this.plan = plan;
            groups = new ArrayList<>(plan.layoutGroups().size());
            textures = new ArrayList<>(plan.images().size());
        }

        @Override public long nextBytes() {
            if (groupIndex < plan.layoutGroups().size()) {
                ModelGpuLayoutGroupPlan group = plan.layoutGroups().get(groupIndex);
                return Math.addExact(Math.multiplyExact((long) group.vertexStride(), group.vertexCount()),
                        Math.multiplyExact((long) group.indexCount(), Integer.BYTES));
            }
            if (textureIndex < plan.images().size()) {
                long bytes = 0;
                for (DecodedModelImage level : plan.images().get(textureIndex).levels()) {
                    bytes = Math.addExact(bytes, level.byteSize());
                }
                return bytes;
            }
            return 0;
        }

        @Override public int nextObjects() {
            if (groupIndex < plan.layoutGroups().size()) return 2;
            return textureIndex < plan.images().size() ? 1 : 0;
        }

        @Override public long remainingBytes() {
            long bytes = 0;
            for (int index = groupIndex; index < plan.layoutGroups().size(); index++) {
                ModelGpuLayoutGroupPlan group = plan.layoutGroups().get(index);
                bytes = Math.addExact(bytes, Math.addExact(
                        Math.multiplyExact((long) group.vertexStride(), group.vertexCount()),
                        Math.multiplyExact((long) group.indexCount(), Integer.BYTES)));
            }
            for (int index = textureIndex; index < plan.images().size(); index++) {
                for (DecodedModelImage level : plan.images().get(index).levels()) {
                    bytes = Math.addExact(bytes, level.byteSize());
                }
            }
            return bytes;
        }

        @Override public int remainingObjects() {
            return (plan.layoutGroups().size() - groupIndex) * 2 + plan.images().size() - textureIndex;
        }

        @Override public long stagingBytes() {
            long bytes = 0;
            for (ModelGpuLayoutGroupPlan group : plan.layoutGroups()) {
                bytes = Math.addExact(bytes, Math.addExact(
                        Math.multiplyExact((long) group.vertexStride(), group.vertexCount()),
                        Math.multiplyExact((long) group.indexCount(), Integer.BYTES)));
            }
            for (ModelGpuImagePlan image : plan.images()) {
                for (DecodedModelImage level : image.levels()) bytes = Math.addExact(bytes, level.byteSize());
            }
            return bytes;
        }

        @Override public boolean cancelled() {
            synchronized (ModelGpuRepository.this) {
                return closed || entries.get(entry.source) != entry;
            }
        }

        @Override public boolean runStep() {
            renderThread.assertRenderThread();
            if (groupIndex < plan.layoutGroups().size()) {
                ModelGpuLayoutGroupPlan group = plan.layoutGroups().get(groupIndex);
                ModelGpuBuffer vertices = device.createBuffer(label(plan, "vertices", groupIndex),
                        ModelGpuBufferKind.VERTEX, group.vertexData());
                ModelGpuBuffer indices = null;
                try {
                    indices = device.createBuffer(label(plan, "indices", groupIndex),
                            ModelGpuBufferKind.INDEX, group.indexData());
                    groups.add(new ModelGpuLayoutGroup(group.layout(), group.vertexStride(), group.vertexCount(),
                            group.indexCount(), vertices, indices));
                    vertices = null;
                    indices = null;
                } finally {
                    close(vertices);
                    close(indices);
                }
                groupIndex++;
            } else if (textureIndex < plan.images().size()) {
                textures.add(device.createTexture(label(plan, "texture", textureIndex),
                        plan.images().get(textureIndex)));
                textureIndex++;
            }
            if (groupIndex == plan.layoutGroups().size() && textureIndex == plan.images().size()) {
                completed = new ModelGpuResource(plan.source(), groups, plan.drawRanges(), textures, plan.images());
                return true;
            }
            return false;
        }

        @Override public void completed() {
            ModelGpuResource resource = Objects.requireNonNull(completed, "completed GPU resource");
            synchronized (ModelGpuRepository.this) {
                completedUploads++;
                pendingUploads--;
                liveResources++;
                createdBuffers += resource.bufferCount();
                createdTextures += resource.textures().size();
                liveBufferBytes += resource.bufferBytes();
                liveTextureBytes += resource.textureBytes();
            }
            entry.resource.complete(resource);
        }

        @Override public void cancelledByScheduler() {
            close(groups, textures);
            synchronized (ModelGpuRepository.this) {
                entries.remove(entry.source, entry);
                pendingUploads--;
                cancelledUploads++;
            }
            entry.resource.completeExceptionally(new IllegalStateException("GPU upload was cancelled"));
        }

        @Override public void failed(Throwable failure) {
            close(groups, textures);
            synchronized (ModelGpuRepository.this) {
                entries.remove(entry.source, entry);
                failedUploads++;
                pendingUploads--;
            }
            entry.resource.completeExceptionally(failure);
        }
    }

    static final class Entry {
        private final ModelAssetReference source;
        private final CompletableFuture<ModelGpuResource> resource = new CompletableFuture<>();
        private int references;

        private Entry(ModelAssetReference source) { this.source = source; }
    }
}
