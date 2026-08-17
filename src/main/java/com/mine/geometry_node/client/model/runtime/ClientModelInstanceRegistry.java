package com.mine.geometry_node.client.model.runtime;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.client.model.gpu.RenderThreadDispatcher;
import com.mine.geometry_node.client.model.debug.ModelLoadProgressTracker;
import com.mine.geometry_node.core.engine.system.model.domain.ModelDefinition;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class ClientModelInstanceRegistry implements AutoCloseable {
    private final ModelResourceCoordinator resources;
    private final RenderThreadDispatcher renderThread;
    private final BackendPreparation backendPreparation;
    private final Map<ModelInstanceId, Entry> instances = new HashMap<>();
    private long generation;
    private List<ReadyInstance> cachedReadySnapshot = List.of();
    private boolean readySnapshotDirty = true;

    public ClientModelInstanceRegistry(ModelResourceCoordinator resources, RenderThreadDispatcher renderThread) {
        this(resources, renderThread, (resource, path) -> CompletableFuture.completedFuture(null));
    }

    public ClientModelInstanceRegistry(ModelResourceCoordinator resources, RenderThreadDispatcher renderThread,
                                       BackendPreparation backendPreparation) {
        this.resources = Objects.requireNonNull(resources, "resources");
        this.renderThread = Objects.requireNonNull(renderThread, "renderThread");
        this.backendPreparation = Objects.requireNonNull(backendPreparation, "backendPreparation");
    }

    public void upsertLocal(ModelInstanceId id, Path path, ModelInstanceState state) {
        renderThread.assertRenderThread();
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(state, "state");
        ModelLoadProgressTracker.begin(path);
        final LocalModelAssetRequest request;
        try {
            request = LocalModelAssetRequest.inspect(path);
        } catch (Exception exception) {
            publishInspectionFailure(id, path, state, exception);
            return;
        }
        ClientModelResourceLease lease;
        long token;
        removeLocked(id);
        token = ++generation;
        lease = resources.acquire(request);
        instances.put(id, new Entry(token, request.path(), state, lease));
        invalidateReadySnapshot();
        lease.resource().whenComplete((resource, failure) ->
                renderThread.execute(() -> finish(id, token, resource, failure)));
    }

    public boolean updateState(ModelInstanceId id, ModelInstanceState state) {
        renderThread.assertRenderThread();
        Entry entry = instances.get(id);
        if (entry == null) return false;
        Objects.requireNonNull(state, "state");
        if (entry.resource != null && !renderable(entry.resource.metadata(), state.placement())) return false;
        entry.state = state;
        invalidateReadySnapshot();
        return true;
    }

    public boolean selectAnimation(ModelInstanceId id, int animationIndex) {
        return withPose(id, pose -> pose.select(animationIndex));
    }
    public boolean playAnimation(ModelInstanceId id) { return withPose(id, ModelInstancePose::play); }
    public boolean pauseAnimation(ModelInstanceId id) { return withPose(id, ModelInstancePose::pause); }
    public boolean stopAnimation(ModelInstanceId id) { return withPose(id, ModelInstancePose::stop); }
    public boolean resetAnimation(ModelInstanceId id) { return withPose(id, ModelInstancePose::reset); }
    public boolean seekAnimation(ModelInstanceId id, float seconds) { return withPose(id, pose -> pose.seek(seconds)); }
    public boolean setAnimationSpeed(ModelInstanceId id, float speed) { return withPose(id, pose -> pose.setSpeed(speed)); }
    public boolean setAnimationLooping(ModelInstanceId id, boolean looping) { return withPose(id, pose -> pose.setLooping(looping)); }
    public boolean setAnimationReverse(ModelInstanceId id, boolean reverse) { return withPose(id, pose -> pose.setReverse(reverse)); }

    public void tickAnimations(long nowNanos) {
        renderThread.assertRenderThread();
        for (Entry entry : instances.values()) if (entry.pose != null) entry.pose.tick(nowNanos);
    }

    public void remove(ModelInstanceId id) { renderThread.assertRenderThread(); removeLocked(id); }

    public void removeExpired(long nowNanos) {
        renderThread.assertRenderThread();
        List<ModelInstanceId> expired = instances.entrySet().stream()
                .filter(item -> item.getValue().state.expired(nowNanos)).map(Map.Entry::getKey).toList();
        expired.forEach(this::removeLocked);
    }

    public List<ReadyInstance> readySnapshot() {
        renderThread.assertRenderThread();
        if (readySnapshotDirty) {
            cachedReadySnapshot = instances.entrySet().stream()
                    .filter(item -> item.getValue().resource != null)
                    .map(item -> new ReadyInstance(item.getKey(), item.getValue().state,
                            item.getValue().resource, item.getValue().pose))
                    .sorted(Comparator.comparing(ReadyInstance::id)).toList();
            readySnapshotDirty = false;
        }
        return cachedReadySnapshot;
    }

    public InstanceStatus status(ModelInstanceId id) {
        renderThread.assertRenderThread();
        Entry entry = instances.get(id);
        if (entry == null) return new InstanceStatus(ModelLoadState.CLOSED, null, "", null);
        return new InstanceStatus(entry.loadState, entry.path, entry.failure, entry.resource);
    }

    public ModelInstanceState instanceState(ModelInstanceId id) {
        renderThread.assertRenderThread();
        Entry entry = instances.get(id);
        return entry == null ? null : entry.state;
    }

    public ModelInstancePose instancePose(ModelInstanceId id) {
        renderThread.assertRenderThread();
        Entry entry = instances.get(id);
        return entry == null ? null : entry.pose;
    }

    public int size() { renderThread.assertRenderThread(); return instances.size(); }

    public Diagnostics diagnostics() {
        renderThread.assertRenderThread();
        int loading = 0, ready = 0, failed = 0, visible = 0;
        Set<LoadedModelResource> resources = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Entry entry : instances.values()) {
            switch (entry.loadState) {
                case LOADING -> loading++;
                case READY -> {
                    ready++;
                    if (entry.state.visible()) visible++;
                    if (entry.resource != null) resources.add(entry.resource);
                }
                case FAILED -> failed++;
                case CLOSED -> { }
            }
        }
        long sourceBytes = 0, vertices = 0, triangles = 0;
        for (LoadedModelResource resource : resources) {
            sourceBytes = saturatedAdd(sourceBytes, resource.sourceBytes());
            triangles = saturatedAdd(triangles, resource.triangles());
            for (var mesh : resource.definition().meshes()) {
                for (var primitive : mesh.primitives()) {
                    vertices = saturatedAdd(vertices, primitive.vertexCount());
                }
            }
        }
        return new Diagnostics(instances.size(), loading, ready, failed, visible,
                resources.size(), sourceBytes, vertices, triangles);
    }

    /** Geometry eligible for the current dimension before distance/frustum culling. */
    public SceneGeometry sceneGeometry(ModelDimensionId dimension) {
        renderThread.assertRenderThread();
        long vertices = 0, triangles = 0;
        for (Entry entry : instances.values()) {
            if (entry.resource == null || entry.loadState != ModelLoadState.READY
                    || !entry.state.visible() || !entry.state.dimension().equals(dimension)) continue;
            ModelDefinition definition = entry.resource.definition();
            StaticModelRenderMetadata metadata = entry.resource.metadata();
            for (int nodeIndex = 0; nodeIndex < definition.nodes().size(); nodeIndex++) {
                var node = definition.nodes().get(nodeIndex);
                if (node.meshIndex() < 0 || !metadata.nodeVisible(nodeIndex)
                        || !entry.state.nodeState().visible(nodeIndex)) continue;
                for (var primitive : definition.meshes().get(node.meshIndex()).primitives()) {
                    vertices = saturatedAdd(vertices, primitive.vertexCount());
                    triangles = saturatedAdd(triangles, primitive.triangleCount());
                }
            }
        }
        return new SceneGeometry(vertices, triangles);
    }

    public void clear() {
        renderThread.assertRenderThread();
        List<Entry> removed = List.copyOf(instances.values());
        instances.clear();
        generation++;
        invalidateReadySnapshot();
        removed.forEach(entry -> { if (entry.lease != null) entry.lease.close(); });
    }

    @Override public void close() { clear(); }

    private static long saturatedAdd(long left, long right) {
        return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
    }

    public record Diagnostics(int instances, int loading, int ready, int failed, int visible,
                              int resources, long sourceBytes, long vertices, long triangles) {}
    public record SceneGeometry(long vertices, long triangles) {}

    private void publishInspectionFailure(ModelInstanceId id, Path path, ModelInstanceState state, Exception failure) {
        ModelLoadProgressTracker.finish(path);
        removeLocked(id);
        Entry entry = new Entry(++generation, path.toAbsolutePath().normalize(), state, null);
        entry.loadState = ModelLoadState.FAILED;
        entry.failure = rootMessage(failure);
        instances.put(id, entry);
        invalidateReadySnapshot();
    }

    private void finish(ModelInstanceId id, long token, LoadedModelResource resource, Throwable failure) {
        renderThread.assertRenderThread();
        Entry entry = instances.get(id);
        if (entry == null || entry.token != token) return;
        if (failure != null) {
            ModelLoadProgressTracker.finish(entry.path);
            entry.loadState = ModelLoadState.FAILED;
            entry.failure = rootMessage(failure);
            GeometryNode.LOGGER.error("Model instance {} failed to load {}: {}", id.value(), entry.path, entry.failure);
        } else {
            if (!renderable(resource.metadata(), entry.state.placement())) {
                ModelLoadProgressTracker.finish(entry.path);
                entry.loadState = ModelLoadState.FAILED;
                entry.failure = "instance and node transforms compose to a singular render transform";
                entry.lease.close();
                GeometryNode.LOGGER.error("Model instance {} rejected {}: {}", id.value(), entry.path, entry.failure);
            } else {
                final CompletableFuture<Void> preparation;
                try {
                    preparation = Objects.requireNonNull(backendPreparation.prepare(resource, entry.path),
                            "backend preparation future");
                } catch (RuntimeException preparationFailure) {
                    finishBackend(id, token, resource, preparationFailure);
                    return;
                }
                preparation.whenComplete((ignored, preparationFailure) ->
                        renderThread.execute(() -> finishBackend(id, token, resource, preparationFailure)));
            }
        }
    }

    private void finishBackend(ModelInstanceId id, long token, LoadedModelResource resource, Throwable failure) {
        renderThread.assertRenderThread();
        Entry entry = instances.get(id);
        if (entry == null || entry.token != token) return;
        ModelLoadProgressTracker.finish(entry.path);
        if (failure != null) {
            entry.loadState = ModelLoadState.FAILED;
            entry.failure = rootMessage(failure);
            entry.lease.close();
            GeometryNode.LOGGER.error("Model instance {} failed to prepare HOST artifact for {}: {}",
                    id.value(), entry.path, entry.failure);
            return;
        }
        entry.resource = resource;
        entry.pose = new ModelInstancePose(resource.definition());
        entry.loadState = ModelLoadState.READY;
        invalidateReadySnapshot();
    }

    private static boolean renderable(StaticModelRenderMetadata metadata, ModelInstancePlacement placement) {
        org.joml.Matrix4f instance = new org.joml.Matrix4f().rotate(placement.rotation()).scale(placement.scale());
        for (int index = 0; index < metadata.nodeCount(); index++) {
            if (metadata.nodeDrawable(index)
                    && !ModelTransformMath.isRenderable(new org.joml.Matrix4f(instance)
                    .mul(metadata.nodeWorldTransform(index)))) return false;
        }
        return true;
    }

    private void removeLocked(ModelInstanceId id) {
        Entry removed = instances.remove(id);
        if (removed != null) {
            ModelLoadProgressTracker.finish(removed.path);
            invalidateReadySnapshot();
            if (removed.lease != null) removed.lease.close();
        }
    }

    private void invalidateReadySnapshot() { readySnapshotDirty = true; }

    private static String rootMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) root = root.getCause();
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }

    private boolean withPose(ModelInstanceId id, java.util.function.Consumer<ModelInstancePose> action) {
        renderThread.assertRenderThread();
        Entry entry = instances.get(id);
        if (entry == null || entry.pose == null) return false;
        action.accept(entry.pose);
        return true;
    }

    public record ReadyInstance(ModelInstanceId id, ModelInstanceState state, LoadedModelResource resource,
                                ModelInstancePose pose) {}
    public record InstanceStatus(ModelLoadState state, Path path, String failure, LoadedModelResource resource) {}

    @FunctionalInterface
    public interface BackendPreparation {
        CompletableFuture<Void> prepare(LoadedModelResource resource, Path path);
    }

    private static final class Entry {
        private final long token;
        private final Path path;
        private final ClientModelResourceLease lease;
        private ModelInstanceState state;
        private ModelLoadState loadState = ModelLoadState.LOADING;
        private String failure = "";
        private LoadedModelResource resource;
        private ModelInstancePose pose;

        private Entry(long token, Path path, ModelInstanceState state, ClientModelResourceLease lease) {
            this.token = token;
            this.path = path;
            this.state = state;
            this.lease = lease;
        }
    }
}
