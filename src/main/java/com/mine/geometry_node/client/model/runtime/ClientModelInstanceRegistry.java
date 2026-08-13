package com.mine.geometry_node.client.model.runtime;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.client.model.gpu.RenderThreadDispatcher;

import java.nio.file.Path;
import java.util.*;

public final class ClientModelInstanceRegistry implements AutoCloseable {
    private final ModelResourceCoordinator resources;
    private final RenderThreadDispatcher renderThread;
    private final Map<ModelInstanceId, Entry> instances = new HashMap<>();
    private long generation;

    public ClientModelInstanceRegistry(ModelResourceCoordinator resources, RenderThreadDispatcher renderThread) {
        this.resources = Objects.requireNonNull(resources, "resources");
        this.renderThread = Objects.requireNonNull(renderThread, "renderThread");
    }

    public void upsertLocal(ModelInstanceId id, Path path, ModelInstanceState state) {
        renderThread.assertRenderThread();
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(state, "state");
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
        return instances.entrySet().stream()
                .filter(item -> item.getValue().resource != null)
                .map(item -> new ReadyInstance(item.getKey(), item.getValue().state, item.getValue().resource, item.getValue().pose))
                .sorted(Comparator.comparing(ReadyInstance::id)).toList();
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

    public void clear() {
        renderThread.assertRenderThread();
        List<Entry> removed = List.copyOf(instances.values());
        instances.clear();
        generation++;
        removed.forEach(entry -> { if (entry.lease != null) entry.lease.close(); });
    }

    @Override public void close() { clear(); }

    private void publishInspectionFailure(ModelInstanceId id, Path path, ModelInstanceState state, Exception failure) {
        removeLocked(id);
        Entry entry = new Entry(++generation, path.toAbsolutePath().normalize(), state, null);
        entry.loadState = ModelLoadState.FAILED;
        entry.failure = rootMessage(failure);
        instances.put(id, entry);
    }

    private void finish(ModelInstanceId id, long token, LoadedModelResource resource, Throwable failure) {
        renderThread.assertRenderThread();
        Entry entry = instances.get(id);
        if (entry == null || entry.token != token) return;
        if (failure != null) {
            entry.loadState = ModelLoadState.FAILED;
            entry.failure = rootMessage(failure);
            GeometryNode.LOGGER.error("Model instance {} failed to load {}: {}", id.value(), entry.path, entry.failure);
        } else {
            if (!renderable(resource.metadata(), entry.state.placement())) {
                entry.loadState = ModelLoadState.FAILED;
                entry.failure = "instance and node transforms compose to a singular render transform";
                entry.lease.close();
                GeometryNode.LOGGER.error("Model instance {} rejected {}: {}", id.value(), entry.path, entry.failure);
            } else {
                entry.resource = resource;
                entry.pose = new ModelInstancePose(resource.definition());
                entry.loadState = ModelLoadState.READY;
            }
        }
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
        if (removed != null && removed.lease != null) removed.lease.close();
    }

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
