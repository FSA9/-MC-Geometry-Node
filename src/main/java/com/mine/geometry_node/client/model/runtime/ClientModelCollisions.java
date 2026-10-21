package com.mine.geometry_node.client.model.runtime;

import com.mine.geometry_node.core.engine.system.model.asset.GltfAsset;
import com.mine.geometry_node.core.engine.resource.residency.ResourceResidency;
import com.mine.geometry_node.core.engine.system.model.runtime.ModelLease;
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
import net.minecraft.world.phys.AABB;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Client-local recipes drive prediction and integrated-server collision independently. Server
 * residency retains only prepared collision geometry; dormant recipes retain no CPU asset lease.
 */
public final class ClientModelCollisions {
    public static final ClientModelCollisions INSTANCE = new ClientModelCollisions();
    private final Map<String, Active> active = new LinkedHashMap<>();
    private final Map<String, ServerOwner> definitions = new LinkedHashMap<>();
    // Each key is mutated only on its owning server thread. Different integrated-server sessions
    // can finish queued teardown concurrently; neither may remove the other's runtime.
    private final Map<ServerKey,ServerOwner> serverOwners = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<ServerLevel> closedServerLevels=java.util.Collections.synchronizedSet(
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>()));
    private final ModelPublicationQueue<ServerKey> serverPublications = new ModelPublicationQueue<>();
    private record ServerKey(ServerLevel level, String id) {}
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
        return ClientModelService.INSTANCE.query(id).map(ClientModelService.DefinitionSnapshot::collision).orElse(null);
    }

    /** Client recipe updates are mirrored, but each side decides its own spatial residency. */
    void define(ModelDefinition model) {
        requireAuthority();
        var mc=Minecraft.getInstance();
        var serverLevel=mc.getSingleplayerServer().getLevel(mc.level.dimension());
        if(serverLevel==null)throw new IllegalStateException("model collision dimension unavailable");
        ServerOwner previous=definitions.get(model.id);
        if(previous!=null&&previous.clientLevel==mc.level&&previous.incarnation==model
                &&previous.revision==model.revision&&previous.source.equals(model.source)
                &&previous.scene==model.scene&&previous.settings.equals(model.collision)
                &&previous.transform.equals(model.transform))return;
        ServerOwner owner=new ServerOwner(model,mc.level,serverLevel);
        definitions.put(model.id,owner);
        if(previous!=null)previous.retired=true;
        retain(owner.clientLevel,model.id,owner);
        serverPublications.submit(owner.key(),serverLevel.getServer(),()->install(owner,null));
    }

    private static void retain(net.minecraft.world.level.Level level,String id,ServerOwner definition) {
        var t=definition.transform;
        ModelCollisionScene.retainDefinition(level,id,definition.bounds,t.x(),t.y(),t.z(),null);
    }

    /** All watch operations, world decisions, and server publications execute on the server owner. */
    private void install(ServerOwner owner,ModelCollisionScene.Prepared offered) {
        if(owner.retired||closedServerLevels.contains(owner.serverLevel)){owner.retired=true;return;}
        ServerOwner old=serverOwners.put(owner.key(),owner);
        if(old!=owner) {
            if(old!=null)old.closeRuntime();
            owner.start();
        }
        if(offered!=null)owner.runtime.offer(offered);
        else owner.runtime.reconcile();
    }

    private void deleteServer(ServerKey key) {
        ServerOwner old=serverOwners.remove(key);
        if(old!=null)old.closeRuntime();
        ModelCollisionScene.remove(key.level(),key.id());
    }

    /** Server-thread teardown also releases owners when a stopping server cannot drain client work. */
    public void unloadServer(ServerLevel level) {
        closedServerLevels.add(level);
        for(ServerKey key:java.util.List.copyOf(serverOwners.keySet()))
            if(key.level()==level)deleteServer(key);
    }

    /** Called on the server level thread after the shared residency index has reconciled. */
    public void tickServer(ServerLevel level) {
        for(ServerOwner owner:serverOwners.values())if(owner.serverLevel==level&&!owner.retired) {
            owner.runtime.tick();
            owner.retryStatus=owner.runtime.retryStatus();
        }
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
                                         ModelCollisionSettings settings, ModelActivation.Ticket ticket,
                                         BooleanSupplier ownerCurrent, Runnable commit) {
        requireAuthority();
        cancel(id);
        Minecraft mc = Minecraft.getInstance();
        ClientLevel clientLevel = mc.level;
        var server = mc.getSingleplayerServer();
        var serverLevel = server.getLevel(clientLevel.dimension());
        if (serverLevel == null) throw new IllegalStateException("model collision dimension unavailable");
        Request request = new Request(settings, clientLevel, serverLevel, ticket, ownerCurrent);
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
                    Active replacement=prepare(asset,scene,transform,settings,request.cancellation,clientLevel,serverLevel,directory);
                    mc.execute(() -> publish(id, request, replacement, commit));
                } catch (Exception error) {
                    mc.execute(() -> fail(id, request, error));
                }
            });
        } catch (RuntimeException error) { fail(id, request, error); }
        return request.completion.minimalCompletionStage();
    }

    private void publish(String id,Request request,Active replacement,Runnable commit) {
        if(!current(id,request))return;
        try {
            commit.run();
            ModelCollisionScene.publish(request.clientLevel,id,replacement.geometry,
                    replacement.transform.x(),replacement.transform.y(),replacement.transform.z());
            active.put(id,replacement);
            ServerOwner owner=definitions.get(id);
            if(owner!=null)serverPublications.submit(owner.key(),owner.serverLevel.getServer(),
                    ()->install(owner,replacement.geometry));
            requests.remove(id);
            request.completion.complete(null);
        } catch(RuntimeException failure){fail(id,request,failure);}
    }

    /** Worker-only; does not create GPU resources or retain the imported asset in the server runtime. */
    private Active prepare(GltfAsset asset,int scene,ModelTransform transform,ModelCollisionSettings settings,
            ModelCancellation cancellation,ClientLevel clientLevel,ServerLevel serverLevel,java.nio.file.Path directory) throws Exception {
        var result=ModelCollisionCache.prepare(directory,asset,scene,
                new org.joml.Matrix4f().scaling(transform.sx(),transform.sy(),transform.sz()),settings,cancellation);
        cancellation.check();
        var preparedKey=new PreparedKey(result.key(),transform.qx(),transform.qy(),transform.qz(),transform.qw());
        var reference=preparedCache.get(preparedKey);
        var geometry=reference==null?null:reference.get();
        long rotationNanos=0,indexNanos=0;
        if(geometry==null) {
            long start=System.nanoTime();
            var matrix=new org.joml.Matrix4d().rotation(new org.joml.Quaterniond(
                    transform.qx(),transform.qy(),transform.qz(),transform.qw()).normalize())
                    .scale(transform.sx(),transform.sy(),transform.sz());
            var rotated=ModelCollisionTransforms.transform(result.mesh(),matrix,cancellation);
            rotationNanos=System.nanoTime()-start;start=System.nanoTime();
            geometry=ModelCollisionScene.prepare(rotated,cancellation);indexNanos=System.nanoTime()-start;
            preparedCache.put(preparedKey,new java.lang.ref.WeakReference<>(geometry));
            while(preparedCache.size()>32)preparedCache.remove(preparedCache.keySet().iterator().next());
        }
        cancellation.check();
        var sharedResult=new ModelCollisionCache.Result(geometry.mesh(),result.key(),result.cacheHit(),
                result.buildNanos(),result.cacheFailure(),result.stageNanos());
        return new Active(asset,scene,transform,settings,geometry,sharedResult,rotationNanos,indexNanos,clientLevel,serverLevel);
    }

    private boolean current(String id, Request request) {
        if (requests.get(id) != request || request.cancelled) return false;
        if (Minecraft.getInstance().level != request.clientLevel) {
            remove(id);
            return false;
        }
        if (!request.ticket.valid() || !request.ownerCurrent.getAsBoolean()) {
            suspend(id);
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
        Request old=requests.remove(id);
        if(old==null)return;
        old.cancelled=true;old.cancellation.cancel();
        old.completion.completeExceptionally(new CancellationException("model collision request superseded"));
    }

    /** Client tracking loss releases prediction geometry, never independently resident server collision. */
    public void suspend(String id) { retire(id,false); }
    public void remove(String id) { retire(id,true); }

    private void retire(String id,boolean delete) {
        commandCompletionNanos.remove(id);failures.remove(id);
        Request request=requests.remove(id);
        if(request!=null){request.cancelled=true;request.cancellation.cancel();}
        Active old=active.remove(id);
        ServerOwner definition=delete?definitions.remove(id):definitions.get(id);
        if(delete&&definition!=null)definition.retired=true;
        ClientLevel clientLevel=definition!=null?definition.clientLevel:old!=null?old.clientLevel:request!=null?request.clientLevel:null;
        ServerLevel serverLevel=definition!=null?definition.serverLevel:old!=null?old.serverLevel:request!=null?request.serverLevel:null;
        if(clientLevel!=null) {
            if(delete)ModelCollisionScene.remove(clientLevel,id);
            else {
                if(definition!=null)retain(clientLevel,id,definition);
                ModelCollisionScene.suspend(clientLevel,id);
            }
            if(delete) {
                ServerKey key=new ServerKey(serverLevel,id);
                serverPublications.remove(key,serverLevel.getServer(),()->deleteServer(key));
            }
        }
        if(request!=null)request.completion.completeExceptionally(new CancellationException("model collision inactive"));
    }

    public void clear() {
        commandCompletionNanos.clear();
        for (String id : java.util.List.copyOf(requests.keySet())) remove(id);
        for (String id : java.util.List.copyOf(active.keySet())) remove(id);
        for (String id : java.util.List.copyOf(definitions.keySet())) remove(id);
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
        output.put("definedInstances", definitions.size());
        output.put("serverActiveInstances",definitions.values().stream().filter(owner->owner.serverActive).count());
        output.put("serverFailures",definitions.entrySet().stream().filter(entry->!entry.getValue().failure.isEmpty())
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,entry->entry.getValue().failure)));
        output.put("serverRetries",definitions.entrySet().stream().filter(entry->entry.getValue().retryStatus.failedAttempts()>0)
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,entry->entry.getValue().retryStatus)));
        output.put("serverRetryPolicy",Map.of("maxAttempts",4,"delayTicks",java.util.List.of(20,40,80),
                "reset","next residency transition or explicit model reload"));
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

    /** One immutable recipe incarnation. No client activation ticket controls this server owner. */
    private final class ServerOwner {
        final String id;
        final Object incarnation;
        final long revision;
        final ClientModelService.Source source;
        final int scene;
        final ModelCollisionSettings settings;
        final ClientLevel clientLevel;
        final ServerLevel serverLevel;
        final AABB bounds;
        final ModelTransform transform;
        volatile boolean retired,serverActive;
        volatile String failure="";
        volatile ModelServerResidency.RetryStatus retryStatus=new ModelServerResidency.RetryStatus(0,-1,false);
        ResourceResidency.Registration watch;
        ModelServerResidency<ModelCollisionScene.Prepared> runtime;

        ServerOwner(ModelDefinition model,ClientLevel clientLevel,ServerLevel serverLevel) {
            id=model.id;incarnation=model;revision=model.revision;source=model.source;scene=model.scene;
            settings=model.collision;bounds=model.worldBounds();transform=model.transform;
            this.clientLevel=clientLevel;this.serverLevel=serverLevel;
        }
        ServerKey key(){return new ServerKey(serverLevel,id);}
        void start() {
            retain(serverLevel,id,this);
            runtime=new ModelServerResidency<>(serverLevel.getServer(),
                    ()->!retired&&ResourceResidency.isResident(serverLevel,bounds,null),this::build,
                    geometry->{
                        // Recheck on this side at the actual commit; watch callbacks can lag an unload.
                        if(retired||!ResourceResidency.isResident(serverLevel,bounds,null))return false;
                        ModelCollisionScene.publish(serverLevel,id,geometry,transform.x(),transform.y(),transform.z());
                        serverActive=true;failure="";
                        return true;
                    },()->{
                        ModelCollisionScene.suspend(serverLevel,id);serverActive=false;
                        // Record an unload observed before callback drain, so a quick reload will
                        // still produce the next transition even if it occurs before level tick.
                        if(watch!=null)watch.update(bounds,null);
                    },
                    error->{failure=ClientModelService.rootMessage(error);retryStatus=runtime.retryStatus();});
            watch=ResourceResidency.watch(serverLevel,bounds,null,ignored->runtime.reconcile());
            // watch deliberately sends no initial callback. install explicitly reconciles or offers.
        }
        void closeRuntime() {
            retired=true;
            if(watch!=null){watch.close();watch=null;}
            if(runtime!=null){runtime.close();runtime=null;}
        }
        ModelServerResidency.Build<ModelCollisionScene.Prepared> build() {
            ServerBuild build=new ServerBuild(this);
            Minecraft.getInstance().execute(build::start);
            return new ModelServerResidency.Build<>(build.completion,build::cancel);
        }
    }

    /** Temporary CPU lease for a server-demanded rebuild, independent of client render residency. */
    private final class ServerBuild {
        final ServerOwner owner;
        final ModelCancellation cancellation=new ModelCancellation();
        final CompletableFuture<ModelCollisionScene.Prepared> completion=new CompletableFuture<>();
        final java.util.concurrent.atomic.AtomicReference<ModelLease<ClientModelService.LoadedAsset>> lease=new java.util.concurrent.atomic.AtomicReference<>();
        volatile boolean cancelled;
        ServerBuild(ServerOwner owner){this.owner=owner;}
        void start() {
            if(cancelled||owner.retired||Minecraft.getInstance().level!=owner.clientLevel){cancel();return;}
            try {
                var acquired=ClientModelService.INSTANCE.acquireCollisionAsset(owner.source);
                lease.set(acquired);
                if(cancelled||owner.retired){cancel();return;}
                acquired.completion().whenComplete((asset,error)->{
                    if(error!=null){finish(null,error);return;}
                    if(cancelled||owner.retired){cancel();return;}
                    try {
                        worker.execute(()->{
                            try {
                                cancellation.check();
                                var directory=Minecraft.getInstance().gameDirectory.toPath().resolve("geometry_nodes/caches/model/collision");
                                var prepared=prepare(asset.cpu(),owner.scene,owner.transform,owner.settings,cancellation,
                                        owner.clientLevel,owner.serverLevel,directory).geometry;
                                cancellation.check();finish(prepared,null);
                            } catch(Exception failure){finish(null,failure);}
                        });
                    } catch(RuntimeException failure){finish(null,failure);}
                });
            } catch(RuntimeException failure){finish(null,failure);}
        }
        void finish(ModelCollisionScene.Prepared geometry,Throwable error) {
            release();
            if(cancelled||owner.retired)completion.completeExceptionally(new CancellationException("server model collision superseded"));
            else if(error!=null)completion.completeExceptionally(error);
            else completion.complete(geometry);
        }
        void cancel() {
            cancelled=true;cancellation.cancel();release();
            completion.completeExceptionally(new CancellationException("server model collision dormant"));
        }
        void release(){var owned=lease.getAndSet(null);if(owned!=null)owned.close();}
    }

    private record PreparedKey(String cacheKey, float qx, float qy, float qz, float qw) {}

    private static final class Request {
        final ModelCollisionSettings settings;
        final ModelActivation.Ticket ticket;
        final BooleanSupplier ownerCurrent;
        final ClientLevel clientLevel;
        final ServerLevel serverLevel;
        final ModelCancellation cancellation = new ModelCancellation();
        final CompletableFuture<Void> completion = new CompletableFuture<>();
        volatile boolean cancelled;
        Request(ModelCollisionSettings settings, ClientLevel clientLevel, ServerLevel serverLevel,
                ModelActivation.Ticket ticket, BooleanSupplier ownerCurrent) {
            this.settings = settings; this.clientLevel = clientLevel; this.serverLevel = serverLevel;
            this.ticket = ticket; this.ownerCurrent = ownerCurrent;
        }
    }
}
