package com.mine.geometry_node.client.model.runtime;

import com.mine.geometry_node.core.engine.system.model.asset.GltfAsset;
import com.mine.geometry_node.core.engine.system.model.collision.ModelCollisionCache;
import com.mine.geometry_node.core.engine.system.model.collision.ModelCollisionSettings;
import com.mine.geometry_node.core.engine.system.model.collision.ModelCollisionTransforms;
import com.mine.geometry_node.core.engine.system.model.collision.runtime.ModelCollisionScene;
import com.mine.geometry_node.core.engine.system.model.io.ModelCancellation;
import com.mine.geometry_node.core.engine.system.model.math.ModelTransform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Client-local instances publish collision to both sides of the integrated server. */
public final class ClientModelCollisions {
    public static final ClientModelCollisions INSTANCE = new ClientModelCollisions();
    private final Map<String, Active> active = new LinkedHashMap<>();
    private final Map<String, Request> requests = new LinkedHashMap<>();
    private final Map<String, String> failures = new LinkedHashMap<>();
    private final Map<String, Long> commandCompletionNanos = new LinkedHashMap<>();
    // Worker-owned weak entries share immutable query data without extending its resident lifetime.
    private final Map<PreparedKey, java.lang.ref.WeakReference<ModelCollisionScene.Prepared>> preparedCache = new LinkedHashMap<>();
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(8), task -> {
                Thread thread = new Thread(task, "ModelCollisionGeneration");
                thread.setDaemon(true);
                return thread;
            });

    private ClientModelCollisions() { worker.allowCoreThreadTimeOut(true); }

    public static void requireAuthority() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.getSingleplayerServer() == null)
            throw new IllegalStateException(Component.translatable("geometry_node.model.collision.integrated_only").getString());
    }

    public ModelCollisionSettings settings(String id) {
        Request request = requests.get(id);
        if (request != null) return request.settings;
        Active current = active.get(id);
        return current == null ? null : current.settings;
    }

    public Status status(String id) {
        Active value = active.get(id);
        return new Status(requests.containsKey(id), value != null,
                value == null ? 0 : value.geometry.mesh().parts().size(),
                value != null && value.result.cacheHit(), failures.getOrDefault(id, ""),
                value == null ? 0 : value.geometry.mesh().skippedTriangles(),
                value == null ? 0 : value.geometry.mesh().incompleteObjects());
    }

    public record Status(boolean pending, boolean active, int parts, boolean cacheHit, String failure,
                         int skippedTriangles, int incompleteObjects) {}

    /** Keeps the old instance and collider until preparation succeeds. Called on the client thread. */
    public CompletionStage<Void> replace(String id, GltfAsset asset, int scene, ModelTransform transform,
                                         ModelCollisionSettings settings, Runnable commit) {
        requireAuthority();
        cancel(id);
        Minecraft mc = Minecraft.getInstance();
        ClientLevel clientLevel = mc.level;
        var server = mc.getSingleplayerServer();
        var serverLevel = server.getLevel(clientLevel.dimension());
        if (serverLevel == null) throw new IllegalStateException("model collision dimension unavailable");
        Request request = new Request(settings, clientLevel, serverLevel);
        requests.put(id, request);
        failures.remove(id);
        var directory = mc.gameDirectory.toPath().resolve("geometry_nodes/caches/model/collision");
        Active previous = active.get(id);
        // Translations reuse the baked local-space collision geometry and spatial index.
        if (previous != null && previous.asset == asset && previous.scene == scene
                && previous.settings.equals(settings) && sameLinear(previous.transform, transform)) {
            publish(id, request, new Active(asset, scene, transform, settings, previous.geometry,
                    previous.result, 0, 0, clientLevel, serverLevel), commit);
        } else try {
            worker.execute(() -> {
                try {
                    var result = ModelCollisionCache.prepare(directory, asset, scene,
                            new org.joml.Matrix4f().scaling(transform.sx(), transform.sy(), transform.sz()), settings, request.cancellation);
                    request.cancellation.check();
                    var preparedKey = new PreparedKey(result.key(), transform.qx(), transform.qy(), transform.qz(), transform.qw());
                    var reference = preparedCache.get(preparedKey);
                    var geometry = reference == null ? null : reference.get();
                    long rotationNanos = 0, indexNanos = 0;
                    if (geometry == null) {
                        long start = System.nanoTime();
                        var matrix = new org.joml.Matrix4d().rotation(new org.joml.Quaterniond(
                                transform.qx(), transform.qy(), transform.qz(), transform.qw()).normalize())
                                .scale(transform.sx(), transform.sy(), transform.sz());
                        var rotated = ModelCollisionTransforms.transform(result.mesh(), matrix, request.cancellation);
                        rotationNanos = System.nanoTime() - start;
                        start = System.nanoTime();
                        geometry = ModelCollisionScene.prepare(rotated, request.cancellation);
                        indexNanos = System.nanoTime() - start;
                        preparedCache.put(preparedKey, new java.lang.ref.WeakReference<>(geometry));
                        while (preparedCache.size() > 32) preparedCache.remove(preparedCache.keySet().iterator().next());
                    }
                    request.cancellation.check();
                    var sharedResult = new ModelCollisionCache.Result(geometry.mesh(), result.key(), result.cacheHit(),
                            result.buildNanos(), result.cacheFailure(), result.stageNanos());
                    Active replacement = new Active(asset, scene, transform, settings, geometry, sharedResult,
                            rotationNanos, indexNanos, clientLevel, serverLevel);
                    mc.execute(() -> publish(id, request, replacement, commit));
                } catch (Exception error) {
                    mc.execute(() -> fail(id, request, error));
                }
            });
        } catch (RuntimeException error) { fail(id, request, error); }
        return request.completion.minimalCompletionStage();
    }

    private void publish(String id, Request request, Active replacement, Runnable commit) {
        Minecraft mc = Minecraft.getInstance();
        if (!current(id, request)) return;
        var server = mc.getSingleplayerServer();
        if (server == null || server.getLevel(request.clientLevel.dimension()) != request.serverLevel) {
            fail(id, request, new CancellationException("model collision world changed"));
            return;
        }
        // Publish server authority first; a cancelled queued update must never resurrect a collider.
        server.execute(() -> {
            if (request.cancelled) return;
            ModelCollisionScene.publish(request.serverLevel, id, replacement.geometry,
                    replacement.transform.x(), replacement.transform.y(), replacement.transform.z());
            mc.execute(() -> {
                if (!current(id, request)) return;
                try {
                    commit.run();
                    ModelCollisionScene.publish(request.clientLevel, id, replacement.geometry,
                            replacement.transform.x(), replacement.transform.y(), replacement.transform.z());
                    active.put(id, replacement);
                    requests.remove(id);
                    request.completion.complete(null);
                } catch (RuntimeException error) {
                    restoreServer(id, replacement, active.get(id));
                    fail(id, request, error);
                }
            });
        });
    }

    private boolean current(String id, Request request) {
        if (requests.get(id) != request || request.cancelled) return false;
        if (Minecraft.getInstance().level != request.clientLevel) {
            remove(id);
            return false;
        }
        return true;
    }

    private void fail(String id, Request request, Throwable error) {
        if (requests.get(id) != request) return;
        requests.remove(id);
        request.cancelled = true;
        request.cancellation.cancel();
        if (!(error instanceof CancellationException)) {
            failures.put(id, ClientModelService.rootMessage(error));
            while (failures.size() > 64) failures.remove(failures.keySet().iterator().next());
        }
        request.completion.completeExceptionally(error);
    }

    public void cancel(String id) {
        Request old = requests.remove(id);
        if (old == null) return;
        old.cancelled = true;
        old.cancellation.cancel();
        old.completion.completeExceptionally(new CancellationException("model collision request superseded"));
        restoreServer(id, old.clientLevel, old.serverLevel, active.get(id));
    }

    private void restoreServer(String id, Active replacement, Active old) {
        restoreServer(id, replacement.clientLevel, replacement.serverLevel, old);
    }

    private void restoreServer(String id, ClientLevel clientLevel, ServerLevel serverLevel, Active old) {
        serverLevel.getServer().execute(() -> {
            if (old != null && old.clientLevel == clientLevel)
                ModelCollisionScene.publish(serverLevel, id, old.geometry, old.transform.x(), old.transform.y(), old.transform.z());
            else ModelCollisionScene.remove(serverLevel, id);
        });
    }

    public void remove(String id) {
        commandCompletionNanos.remove(id);
        cancel(id);
        failures.remove(id);
        Active old = active.remove(id);
        if (old != null) {
            ModelCollisionScene.remove(old.clientLevel, id);
            old.serverLevel.getServer().execute(() -> ModelCollisionScene.remove(old.serverLevel, id));
        }
    }

    public void clear() {
        commandCompletionNanos.clear();
        for (String id : java.util.List.copyOf(requests.keySet())) remove(id);
        for (String id : java.util.List.copyOf(active.keySet())) remove(id);
        failures.clear();
        worker.purge();
    }

    public void shutdown() { clear(); worker.shutdownNow(); }

    public void commandCompleted(String id, long nanos) {
        commandCompletionNanos.put(id, nanos);
        while (commandCompletionNanos.size() > 64) commandCompletionNanos.remove(commandCompletionNanos.keySet().iterator().next());
    }

    public Map<String, Object> report() {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("activeInstances", active.size());
        output.put("pendingInstances", java.util.List.copyOf(requests.keySet()));
        output.put("failures", Map.copyOf(failures));
        output.put("workerQueue", worker.getQueue().size());
        output.put("instances", active.entrySet().stream().map(entry -> {
            Active value = entry.getValue();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", entry.getKey());
            item.put("mergeScale", value.settings.mergeScale());
            item.put("fitError", value.settings.fitError());
            item.put("parts", value.geometry.mesh().parts().size());
            item.put("representation", "closed_convex_volumes");
            item.put("convexFaces", value.geometry.mesh().parts().stream().mapToLong(part -> part.faceCount()).sum());
            item.put("boundaryTriangles", value.geometry.mesh().parts().stream().mapToLong(part -> part.triangleCount()).sum());
            item.put("vertices", value.geometry.mesh().parts().stream().mapToLong(part -> part.vertexCount()).sum());
            item.put("sourceTriangles", value.geometry.mesh().sourceTriangles());
            item.put("skippedTriangles", value.geometry.mesh().skippedTriangles());
            item.put("incompleteObjects", value.geometry.mesh().incompleteObjects());
            item.put("generationIssues", value.geometry.mesh().generationIssues());
            item.put("complete", value.geometry.mesh().skippedTriangles() == 0);
            item.put("cacheCoordinateSpace", "gltf_scene_local");
            item.put("instanceScale", java.util.List.of(value.transform.sx(), value.transform.sy(), value.transform.sz()));
            item.put("instanceRotation", java.util.List.of(value.transform.qx(), value.transform.qy(), value.transform.qz(), value.transform.qw()));
            item.put("estimatedGeometryBytes", value.geometry.estimatedGeometryBytes());
            item.put("estimatedIndexBytes", value.geometry.estimatedIndexBytes());
            item.put("cacheKey", value.result.key());
            item.put("cacheHit", value.result.cacheHit());
            item.put("buildNanos", value.result.buildNanos());
            item.put("cachePreparationNanos", value.result.buildNanos());
            item.put("preparationStagesNanos", value.result.stageNanos());
            item.put("instanceRotationNanos", value.rotationNanos);
            item.put("indexPreparationNanos", value.indexNanos);
            if (commandCompletionNanos.containsKey(entry.getKey()))
                item.put("lastCommandCompletionNanos", commandCompletionNanos.get(entry.getKey()));
            item.put("cacheFailure", value.result.cacheFailure());
            return item;
        }).toList());
        return output;
    }

    private static boolean sameLinear(ModelTransform a, ModelTransform b) {
        return a.qx() == b.qx() && a.qy() == b.qy() && a.qz() == b.qz() && a.qw() == b.qw()
                && a.sx() == b.sx() && a.sy() == b.sy() && a.sz() == b.sz();
    }

    private record Active(GltfAsset asset, int scene, ModelTransform transform, ModelCollisionSettings settings,
                          ModelCollisionScene.Prepared geometry, ModelCollisionCache.Result result,
                          long rotationNanos, long indexNanos,
                          ClientLevel clientLevel, ServerLevel serverLevel) {}

    private record PreparedKey(String cacheKey, float qx, float qy, float qz, float qw) {}

    private static final class Request {
        final ModelCollisionSettings settings;
        final ClientLevel clientLevel;
        final ServerLevel serverLevel;
        final ModelCancellation cancellation = new ModelCancellation();
        final CompletableFuture<Void> completion = new CompletableFuture<>();
        volatile boolean cancelled;
        Request(ModelCollisionSettings settings, ClientLevel clientLevel, ServerLevel serverLevel) {
            this.settings = settings; this.clientLevel = clientLevel; this.serverLevel = serverLevel;
        }
    }
}
