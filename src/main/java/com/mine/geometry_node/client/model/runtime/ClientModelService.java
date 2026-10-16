package com.mine.geometry_node.client.model.runtime;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.client.model.render.geometry.ModelRenderAsset;
import com.mine.geometry_node.client.model.render.geometry.ModelRenderInstance;
import com.mine.geometry_node.client.model.render.pipeline.ModelStaticRenderer;
import com.mine.geometry_node.client.model.render.lod.model.ModelLodMetrics;
import com.mine.geometry_node.client.model.render.resource.ModelGpuBudgetException;
import com.mine.geometry_node.client.model.render.resource.ModelUploadQueue;

import com.mine.geometry_node.client.model.resource.ResourcePackModelProvider;
import com.mine.geometry_node.core.engine.system.model.asset.GltfAsset;
import com.mine.geometry_node.core.engine.system.model.collision.ModelCollisionSettings;
import com.mine.geometry_node.core.engine.system.model.io.*;
import com.mine.geometry_node.core.engine.system.model.math.ModelTransform;
import com.mine.geometry_node.core.engine.system.model.runtime.*;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Client-local P1/P2 instance API. Commands and future node adapters share this service. */
public final class ClientModelService {
    public static final ClientModelService INSTANCE=new ClientModelService();
    public static final String IMPORT_PROFILE="STATIC_BASIC";
    public static final int IMPORTER_REVISION=2;
    public enum SourceKind { LOCAL, RESOURCE_PACK }
    /** Session source reference. Absolute paths are diagnostic only, never asset identities. */
    public record Source(SourceKind kind,String location,String entry) {
        public Source {
            Objects.requireNonNull(kind);Objects.requireNonNull(location);Objects.requireNonNull(entry);
            if(location.isBlank())throw new IllegalArgumentException("blank model source location");
        }
        public Source(SourceKind kind,String location){this(kind,location,"");}
        public static Source local(Path path){return new Source(SourceKind.LOCAL,path.toAbsolutePath().normalize().toString());}
        public static Source resource(Identifier identifier){return new Source(SourceKind.RESOURCE_PACK,identifier.toString());}
        public static Source localRoot(Path root,String entry){return new Source(SourceKind.LOCAL,root.toAbsolutePath().normalize().toString(),entry);}
        /** Stable local-session identity that does not expose an absolute path. */
        public String sessionIdentity(){
            if(kind==SourceKind.RESOURCE_PACK)return "resource-pack:"+location+(entry.isEmpty()?"":"#"+entry);
            return "local-session:"+sha256(location+"\u0000"+entry);
        }
        public String diagnosticLocation(){return location+(entry.isEmpty()?"":"#"+entry);}
    }
    private static String sha256(String text) {
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            StringBuilder hex=new StringBuilder(64);
            for(byte value:digest.digest(text.getBytes(StandardCharsets.UTF_8)))hex.append(String.format(Locale.ROOT,"%02x",value));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable",impossible);
        }
    }
    private final ThreadPoolExecutor workers=new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16),task->{
        Thread thread=new Thread(task,"GeometryNode-ModelImport");thread.setDaemon(true);return thread;});
    private final ModelRepository<RepositoryKey,LoadedAsset> repository=new ModelRepository<>(workers,this::dispose);
    private final ModelInstanceStore<LoadedAsset> instances=new ModelInstanceStore<>();
    private long loadedCountsRevision = -1;
    private ModelLodMetrics.LoadedCounts loadedCounts = new ModelLodMetrics.LoadedCounts(0, 0);
    private final Map<String,Pending> pending=new LinkedHashMap<>();
    private final Map<String,String> failures=new LinkedHashMap<>();
    private final Map<String,String> renderDeferrals=new LinkedHashMap<>();
    private final ModelUploadQueue<PendingUpload> uploads=new ModelUploadQueue<>();
    private long epoch,requestSequence,assetsDisposed;
    private long renderResourceGeneration;
    private String lastRenderInvalidation = "initial";
    private final AtomicLong peakEncodedBytes=new AtomicLong();
    private final AtomicLong peakCpuGeometryBytes=new AtomicLong();
    private final AtomicLong peakDecodedTextureBytes=new AtomicLong();
    private final AtomicLong peakPreparedBytes=new AtomicLong();
    private final AtomicLong livePreparedBytes=new AtomicLong();
    private ClientLevel level;
    private ClientModelService(){workers.allowCoreThreadTimeOut(true);}

    /** Replaces an instance only after its complete CPU import and renderer preparation succeed. Main thread only. */
    public CompletionStage<ModelInstanceStore.Snapshot<LoadedAsset>> create(String id,Source source,ModelTransform transform,int scene,boolean visible) {
        return create(id,source,transform,scene,visible,ModelInstanceLifetime.TEMPORARY);
    }
    /** Creates a client-local instance; persistent lifetime is reserved for P3-N. */
    public CompletionStage<ModelInstanceStore.Snapshot<LoadedAsset>> create(String id,Source source,ModelTransform transform,int scene,
                                                                            boolean visible,ModelInstanceLifetime lifetime) {
        return create(id, source, transform, scene, visible, lifetime, ClientModelCollisions.INSTANCE.settings(id));
    }
    public CompletionStage<ModelInstanceStore.Snapshot<LoadedAsset>> create(String id,Source source,ModelTransform transform,int scene,
            boolean visible,ModelInstanceLifetime lifetime,ModelCollisionSettings collision) {
        RenderSystem.assertOnRenderThread();ModelInstanceStore.validateId(id);
        Objects.requireNonNull(source);Objects.requireNonNull(transform);Objects.requireNonNull(lifetime);
        if(lifetime==ModelInstanceLifetime.PERSISTENT)
            throw new UnsupportedOperationException("persistent model instances are provided by P3-N");
        Minecraft minecraft=Minecraft.getInstance();
        if(minecraft.level==null)throw new IllegalStateException("enter a world before creating a model");
        if(collision!=null)ClientModelCollisions.requireAuthority();
        ensureWorld();level=minecraft.level;
        ClientModelCollisions.INSTANCE.cancel(id);
        Pending previous=pending.remove(id);if(previous!=null)previous.close();
        RepositoryKey repositoryKey=repositoryKey(source);
        ModelLease<LoadedAsset> lease=repository.acquire(repositoryKey,(key,cancel)->importAsset(key.source(),cancel));
        Pending request=new Pending(++requestSequence,epoch,lease,transform,scene,visible,lifetime,collision);
        pending.put(id,request);failures.remove(id);
        lease.completion().whenComplete((asset,error)->minecraft.execute(()->publish(id,request,asset,error)));
        return request.completion.minimalCompletionStage();
    }
    public CompletionStage<ModelInstanceStore.Snapshot<LoadedAsset>> load(Path path) {
        return create("preview",Source.local(path),previewTransform(),-1,true);
    }
    public CompletionStage<ModelInstanceStore.Snapshot<LoadedAsset>> loadResource(Identifier identifier) {
        return create("preview",Source.resource(identifier),previewTransform(),-1,true);
    }
    /** Reimports an existing instance while retaining its old asset until the new revision is ready. */
    public CompletionStage<ModelInstanceStore.Snapshot<LoadedAsset>> reload(String id) {
        RenderSystem.assertOnRenderThread();
        var current=instances.get(id).orElseThrow(()->new IllegalArgumentException("unknown model instance: "+id));
        LoadedAsset asset=current.asset();
        repository.invalidate(repositoryKey(asset.source));
        return create(id,asset.source,current.transform(),current.scene(),current.visible(),current.lifetime());
    }
    /** Retires the cached source revision without removing current instance leases. */
    public void invalidate(Source source) {
        RenderSystem.assertOnRenderThread();repository.invalidate(repositoryKey(Objects.requireNonNull(source)));
    }
    private LoadedAsset importAsset(Source source,ModelCancellation cancellation)throws Exception {
        GltfAsset asset;
        if(source.kind==SourceKind.LOCAL) {
            Path path=Path.of(source.location).toRealPath();
            Path root=source.entry.isEmpty()?path.getParent():path;
            String entry=source.entry.isEmpty()?path.getFileName().toString():source.entry;
            try(ModelResourceProvider provider=new FileModelResourceProvider(root)) {
                asset=new GltfImporter().load(provider,entry,cancellation);
            }
        } else {
            Identifier identifier=Identifier.parse(source.location);
            String full=identifier.getPath();int split=full.lastIndexOf('/');
            String prefix=split<0?"":full.substring(0,split),entry=split<0?full:full.substring(split+1);
            try(ModelResourceProvider provider=new ResourcePackModelProvider(Minecraft.getInstance().getResourceManager(),identifier.getNamespace(),prefix,cancellation,Minecraft.getInstance().gameDirectory.toPath().resolve("geometry_node/model-spool"))) {
                asset=new GltfImporter().load(provider,entry,cancellation);
            }
        }
        cancellation.check();
        ModelRenderAsset.Prepared prepared=ModelRenderAsset.prepare(asset,cancellation);
        long preparedBytes=Math.addExact(prepared.cpuGeometryBytes(),prepared.decodedTextureBytes());
        long liveBytes=livePreparedBytes.addAndGet(preparedBytes);
        peakEncodedBytes.accumulateAndGet(asset.report().encodedBytes(),Math::max);
        peakCpuGeometryBytes.accumulateAndGet(prepared.cpuGeometryBytes(),Math::max);
        peakDecodedTextureBytes.accumulateAndGet(prepared.decodedTextureBytes(),Math::max);
        peakPreparedBytes.accumulateAndGet(liveBytes,Math::max);
        try {cancellation.check();return new LoadedAsset(source,asset,prepared,contentHash(asset.report()),preparedBytes,cancellation);}
        catch(Throwable failure){prepared.close();livePreparedBytes.addAndGet(-preparedBytes);throw failure;}
    }
    private void publish(String id,Pending request,LoadedAsset asset,Throwable error) {
        RenderSystem.assertOnRenderThread();
        if(pending.get(id)!=request||request.epoch!=epoch||level!=Minecraft.getInstance().level) {request.close();return;}
        if(error!=null) {pending.remove(id);rememberFailure(id,error);request.fail(error);return;}
        try {
            int scene=request.scene<0?asset.cpu.defaultScene():request.scene;
            validateScene(asset.cpu,scene);
            long uploadBytes=asset.prepared.initialTextureUploadBytes();
            uploads.enqueue(new PendingUpload(id,request,asset,scene),uploadBytes,request.visible?1:0);
        } catch(Throwable failure) {pending.remove(id);rememberFailure(id,failure);request.fail(failure);}
    }
    /** Runs the bounded render-thread upload queue once per frame before model submission. */
    public void processRenderUploads() {
        RenderSystem.assertOnRenderThread();
        ModelUploadQueue.FrameResult result=uploads.process(ModelStaticRenderer.budget().maxUploadBytesPerFrame(),ModelStaticRenderer.budget().maxUploadNanosPerFrame(),this::completeUpload);
        ModelStaticRenderer.metrics().upload(result.bytes(),result.nanos());
    }
    private void completeUpload(PendingUpload upload) {
        String id=upload.id(); Pending request=upload.request();
        if(pending.get(id)!=request||request.epoch!=epoch||level!=Minecraft.getInstance().level) {request.close();return;}
        try {
            upload.asset().render();
            Runnable commit=()->{
                if(pending.get(id)!=request||request.epoch!=epoch)throw new CancellationException("model request superseded");
                instances.put(id,upload.asset(),upload.scene(),request.transform,request.visible,request.lifetime,request.lease::close);
            };
            if(request.collision==null) {
                ClientModelCollisions.INSTANCE.remove(id);commit.run();
                pending.remove(id);request.completion.complete(instances.get(id).orElseThrow());
            }
            else ClientModelCollisions.INSTANCE.replace(id,upload.asset().cpu,upload.scene(),request.transform,request.collision,commit)
                    .whenComplete((ignored,error)->{
                        if(pending.get(id)!=request)return;
                        pending.remove(id);
                        if(error!=null){rememberFailure(id,error);request.fail(error);}
                        else request.completion.complete(instances.get(id).orElseThrow());
                    });
        } catch(Throwable failure) {pending.remove(id);rememberFailure(id,failure);request.fail(failure);}
    }
    private static void validateScene(GltfAsset asset,int scene) {
        if(scene<0||scene>=asset.scenes().size())throw new IllegalArgumentException("scene index outside asset: "+scene);
    }
    public void transform(String id,ModelTransform transform) {
        transformAsync(id,transform).whenComplete((ignored,error)->{if(error!=null)collisionFailure(id,error);});
    }
    public CompletionStage<Void> transformAsync(String id,ModelTransform transform) {
        RenderSystem.assertOnRenderThread();requireNotLoading(id);
        var current=instances.get(id).orElseThrow(()->new IllegalArgumentException("unknown model instance: "+id));
        var settings=ClientModelCollisions.INSTANCE.settings(id);
        if(settings==null){instances.transform(id,transform);return CompletableFuture.completedFuture(null);}
        return ClientModelCollisions.INSTANCE.replace(id,current.asset().cpu,current.scene(),transform,settings,
                ()->instances.transform(id,transform));
    }
    public void visible(String id,boolean visible) {RenderSystem.assertOnRenderThread();instances.visible(id,visible);}
    public void scene(String id,int scene) {
        RenderSystem.assertOnRenderThread();requireNotLoading(id);
        var instance=instances.get(id).orElseThrow(()->new IllegalArgumentException("unknown model instance: "+id));
        validateScene(instance.asset().cpu,scene);
        var settings=ClientModelCollisions.INSTANCE.settings(id);
        if(settings==null)instances.scene(id,scene);
        else ClientModelCollisions.INSTANCE.replace(id,instance.asset().cpu,scene,instance.transform(),settings,
                ()->instances.scene(id,scene)).whenComplete((ignored,error)->{if(error!=null)collisionFailure(id,error);});
    }
    public CompletionStage<Void> collision(String id,ModelCollisionSettings settings) {
        RenderSystem.assertOnRenderThread();ensureWorld();requireNotLoading(id);
        var instance=instances.get(id).orElseThrow(()->new IllegalArgumentException("unknown model instance: "+id));
        if(settings==null){ClientModelCollisions.INSTANCE.remove(id);return CompletableFuture.completedFuture(null);}
        return ClientModelCollisions.INSTANCE.replace(id,instance.asset().cpu,instance.scene(),instance.transform(),settings,()->{});
    }
    private void requireNotLoading(String id) {
        if(pending.containsKey(id))throw new IllegalStateException("model import pending: "+id);
    }
    private static void collisionFailure(String id,Throwable error) {
        if(Minecraft.getInstance().player!=null)Minecraft.getInstance().player.sendSystemMessage(
                net.minecraft.network.chat.Component.translatable("geometry_node.model.collision.failed",id,rootMessage(error)));
    }
    /** Queues a shared asset material override; returned count is affected instances, not completion. */
    public int overrideMaterial(String id,int materialIndex,String property,float value) {
        RenderSystem.assertOnRenderThread();ensureWorld();
        if(materialIndex<0)throw new IllegalArgumentException("material index must be nonnegative");
        if(!Float.isFinite(value))throw new IllegalArgumentException("material value must be finite");
        switch(property) {
            case "metallic","roughness","occlusionStrength" -> {if(value<0||value>1)throw new IllegalArgumentException(property+" must be in [0,1]");}
            case "emissiveStrength" -> {if(value<0)throw new IllegalArgumentException("emissiveStrength must be nonnegative");}
            case "normalScale" -> {}
            default -> throw new IllegalArgumentException("unknown material property: "+property);
        }
        LoadedAsset asset=instances.get(id).orElseThrow(()->new IllegalArgumentException("unknown model instance: "+id)).asset();
        asset.render().overrideMaterial(materialIndex,property,value);
        asset.materialOverrides.computeIfAbsent(materialIndex,key->new LinkedHashMap<>()).put(property,value);
        return (int)instances.snapshots().stream().filter(instance->instance.asset()==asset).count();
    }
    /** Queues restoration of imported values; inspect material pending/failure diagnostics for completion. */
    public int resetMaterial(String id,int materialIndex) {
        RenderSystem.assertOnRenderThread();ensureWorld();
        LoadedAsset asset=instances.get(id).orElseThrow(()->new IllegalArgumentException("unknown model instance: "+id)).asset();
        asset.render().resetMaterial(materialIndex);
        asset.materialOverrides.remove(materialIndex);
        return (int)instances.snapshots().stream().filter(instance->instance.asset()==asset).count();
    }
    public Optional<ModelInstanceStore.Snapshot<LoadedAsset>> query(String id) {RenderSystem.assertOnRenderThread();return instances.get(id);}
    public Map<String,Object> queryReport(String id) {
        RenderSystem.assertOnRenderThread();ensureWorld();Map<String,Object> result=new LinkedHashMap<>();result.put("id",id);
        Pending request=pending.get(id);var ready=instances.get(id);
        if(request!=null){result.put("state","LOADING");result.put("request",request.request);result.put("generation",request.epoch);}
        else if(failures.containsKey(id)){result.put("state","FAILED");result.put("failure",failures.get(id));}
        else if(ready.isPresent())result.put("state","READY");else result.put("state","ABSENT");
        ready.ifPresent(snapshot->result.put("instance",describe(snapshot)));return result;
    }
    private void rememberFailure(String id,Throwable failure){
        String message=rootMessage(failure);
        failures.remove(id);failures.put(id,message);while(failures.size()>64)failures.remove(failures.keySet().iterator().next());
        GeometryNode.LOGGER.error("Model request failed: id={}, detail={}",id,message,failure);
    }
    public void shutdown(){RenderSystem.assertOnRenderThread();clear();ClientModelCollisions.INSTANCE.shutdown();repository.close();workers.shutdownNow();}
    public boolean remove(String id) {
        ClientModelCollisions.INSTANCE.remove(id);
        RenderSystem.assertOnRenderThread();Pending request=pending.remove(id);if(request!=null)request.close();failures.remove(id);
        boolean removed = instances.remove(id);
        com.mine.geometry_node.client.model.render.bvh.ModelLightingSettings.removeInstance(id);
        com.mine.geometry_node.client.model.render.bvh.ModelLocalLightProviders.detachInstance(id);
        if (instances.size() == 0) ModelStaticRenderer.clearSceneResources();
        return removed||request!=null;
    }
    public void clear() {
        clear(null);
    }

    private static RepositoryKey repositoryKey(Source source) {
        return new RepositoryKey(source, IMPORT_PROFILE, IMPORTER_REVISION);
    }
    public void clearTemporary() {
        clear(ModelInstanceLifetime.TEMPORARY);
    }
    /** Clears only instances of the requested lifetime; null clears the whole client session. */
    public void clear(ModelInstanceLifetime lifetime) {
        RenderSystem.assertOnRenderThread();epoch++;
        if(lifetime==null)ClientModelCollisions.INSTANCE.clear();
        else {
            for(var instance:instances.snapshots(lifetime))ClientModelCollisions.INSTANCE.remove(instance.id());
            for(var entry:List.copyOf(pending.entrySet()))if(entry.getValue().lifetime==lifetime)ClientModelCollisions.INSTANCE.remove(entry.getKey());
        }
        uploads.clear();
        List<Pending> requests=pending.values().stream()
                .filter(request->lifetime==null||request.lifetime==lifetime).toList();
        requests.forEach(request->pending.values().remove(request));
        requests.forEach(Pending::close);
        if (lifetime == null) com.mine.geometry_node.client.model.render.bvh.ModelLightingSettings.clearInstances();
        else for (var instance : instances.snapshots()) {
            if (instance.lifetime() == lifetime)
                com.mine.geometry_node.client.model.render.bvh.ModelLightingSettings.removeInstance(instance.id());
        }
        instances.clear(lifetime);
        if (instances.size() == 0) com.mine.geometry_node.client.model.render.bvh.ModelLocalLightProviders.clearAttached();
        renderDeferrals.clear();
        if (instances.size() == 0) ModelStaticRenderer.clearSceneResources();
        if(lifetime==null) {
            repository.clear();failures.clear();level=null;
        } else if(lifetime==ModelInstanceLifetime.TEMPORARY) {
            // P3-L exposes only temporary client instances, so their failures share this session scope.
            failures.clear();
        }
    }
    /** Keep instance IDs, transforms, leases and imported data; only GPU resources need rebuilding. */
    public void invalidateRenderResources(String reason) {
        RenderSystem.assertOnRenderThread();
        renderResourceGeneration++;
        lastRenderInvalidation = Objects.requireNonNull(reason);
        Set<LoadedAsset> unique = Collections.newSetFromMap(new IdentityHashMap<>());
        instances.snapshots().forEach(instance -> unique.add(instance.asset()));
        unique.forEach(LoadedAsset::invalidateRender);
        renderDeferrals.clear();
    }

    /** Cheap idle gate; pending imports still progress through processRenderUploads. */
    public boolean hasVisibleInstances() {
        RenderSystem.assertOnRenderThread(); ensureWorld();
        return instances.hasVisibleInstances();
    }

    /** Counts prepared instances, including hidden and GPU-deferred ones, without creating render assets. */
    public ModelLodMetrics.LoadedCounts loadedCounts() {
        RenderSystem.assertOnRenderThread();
        long revision = instances.revision();
        if (revision == loadedCountsRevision) return loadedCounts;
        long models = 0, objects = 0;
        for (var instance : instances.snapshots()) {
            if (instance.asset().closed) continue;
            models++;
            objects += instance.asset().prepared.lodObjectCount(instance.scene());
        }
        loadedCounts = new ModelLodMetrics.LoadedCounts(models, objects);
        loadedCountsRevision = revision;
        return loadedCounts;
    }

    public List<ModelRenderInstance> renderSnapshots() {
        if (com.mine.geometry_node.client.runtime.game.scene.ViewportVisualization.inEditorPass()
                && !com.mine.geometry_node.client.runtime.game.scene.ViewportVisualization.current().visible(
                com.mine.geometry_node.client.runtime.game.scene.ViewportVisualization.Layer.MODEL)) return List.of();
        RenderSystem.assertOnRenderThread();ensureWorld();
        if (!instances.hasVisibleInstances()) { renderDeferrals.clear(); return List.of(); }
        List<ModelRenderInstance> ready = new ArrayList<>();
        renderDeferrals.clear();
        for (var instance : instances.snapshots()) {
            if (!instance.visible()) continue;
            try {
                ready.add(new ModelRenderInstance(instance.id(), instance.asset().render(), instance.scene(), instance.transform(), true));
            } catch (ModelGpuBudgetException deferred) {
                // Fenced evictions still occupy VRAM. Retry next frame without aborting the host render event.
                renderDeferrals.put(instance.id(), deferred.getMessage());
            }
        }
        return List.copyOf(ready);
    }
    private void ensureWorld(){if(level!=null&&level!=Minecraft.getInstance().level)clear();}
    private void dispose(LoadedAsset asset) {Minecraft.getInstance().execute(()->{asset.close();assetsDisposed++;});}
    public Map<String,Object> report() {
        RenderSystem.assertOnRenderThread();ensureWorld();
        Map<String,Object> report=new LinkedHashMap<>();
        report.put("stage",ModelStaticRenderer.stage());report.put("generation",epoch);
        report.put("repositoryEntries",repository.size());report.put("pending",List.copyOf(pending.keySet()));
        report.put("uploadQueue",Map.of("items",uploads.size(),"enqueuedBytes",uploads.enqueuedBytes(),"processedItems",uploads.processedItems(),"processedBytes",uploads.processedBytes(),"deferredItems",uploads.deferredItems(),"oversizeItems",uploads.oversizeItems(),"peakEnqueuedBytes",uploads.peakEnqueuedBytes(),"peakQueueItems",uploads.peakQueueItems()));
        report.put("assetsDisposed",assetsDisposed);report.put("workerQueue",workers.getQueue().size());report.put("failures",Map.copyOf(failures));report.put("renderDeferrals",Map.copyOf(renderDeferrals));
        report.put("schemaVersion",3);
        var snapshots=instances.snapshots();
        Map<LoadedAsset,String> assetIds=new IdentityHashMap<>();
        List<LoadedAsset> unique=new ArrayList<>();
        List<Map<String,Object>> instanceReports=new ArrayList<>();
        ModelGeometryStatistics.Counts instanced=ModelGeometryStatistics.ZERO;
        for(var instance:snapshots) {
            LoadedAsset asset=instance.asset();
            if(!assetIds.containsKey(asset)){assetIds.put(asset,"asset-"+unique.size());unique.add(asset);}
            instanceReports.add(describeInstance(instance,assetIds.get(asset)));
            instanced=instanced.plus(asset.statistics.scene(instance.scene()));
        }
        report.put("instances",instanceReports);
        report.put("assets",unique.stream().map(asset->describePerformanceAsset(asset,assetIds.get(asset))).toList());
        report.put("renderer",ModelStaticRenderer.report());
        var collisionReport = new LinkedHashMap<String,Object>(ClientModelCollisions.INSTANCE.report());
        collisionReport.put("runtime",com.mine.geometry_node.core.engine.system.model.collision.runtime.ModelCollisionMetrics.report());
        report.put("collision",collisionReport);
        report.put("renderResourceLifecycle",Map.of("generation",renderResourceGeneration,"lastInvalidation",lastRenderInvalidation,"reloadPreservesInstances",true));
        ModelGeometryStatistics.Counts geometry=ModelGeometryStatistics.ZERO;
        for(LoadedAsset asset:unique)geometry=geometry.plus(asset.statistics.uniqueMeshes());
        report.put("geometry",Map.of("uniqueAssets",geometry.report(),"instancedScenes",instanced.report(),
                "scope","source indexed geometry; uniqueAssets counts shared assets once, instancedScenes counts selected node occurrences including hidden instances; before BSP splits/GPU expansion"));
        report.put("memory",memoryReport(unique));
        report.put("runtime",runtimeReport());
        report.put("visualVerification","UNTESTED until manual in-game verification");
        report.put("limitations",List.of("client-local; persistence and networking P3-N", "P7 native PBR approximation / Iris LabPBR; per-material translation losses and visual verification reported separately", "animation P9/P10", "P4 uses bounded upload queue and resident opaque buffers; P5 Iris routing is capability-reported and visual compatibility remains per-pack", "source cache is session-scoped; call reload/invalidate after dependency changes"));
        return report;
    }
    private Map<String,Object> memoryReport(List<LoadedAsset> unique) {
        long encoded=unique.stream().mapToLong(asset->asset.cpu.report().encodedBytes()).sum();
        long cpuGeometry=unique.stream().mapToLong(asset->asset.prepared.cpuGeometryBytes()).sum();
        long gpuGeometry=unique.stream().mapToLong(asset->asset.prepared.gpuGeometryBytes()).sum();
        long decodedTextures=unique.stream().mapToLong(asset->asset.prepared.decodedTextureBytes()).sum();
        return Map.ofEntries(
                Map.entry("scope","model-owned accounting/estimates, not whole-machine VRAM or total JVM heap; active unique asset CPU/planned geometry estimates only; actual model-owned GPU/texture ledger appears once at renderer.residency and includes cache/retired/transient allocations"),
                Map.entry("sourceEncodedBytes",encoded),
                Map.entry("sourceCpuGeometryBytes",unique.stream().mapToLong(asset->asset.statistics.sourceCpuGeometryBytes()).sum()),
                Map.entry("cpuGeometryBytes",cpuGeometry),
                Map.entry("gpuGeometryBytesEstimated",gpuGeometry),
                Map.entry("decodedTextureBytes",decodedTextures),
                Map.entry("preparedBytes",Math.addExact(cpuGeometry,decodedTextures)),
                Map.entry("peakSourceEncodedBytes",peakEncodedBytes.get()),
                Map.entry("peakCpuGeometryBytes",peakCpuGeometryBytes.get()),
                Map.entry("peakDecodedTextureBytes",peakDecodedTextureBytes.get()),
                Map.entry("peakPreparedBytes",peakPreparedBytes.get())
        );
    }
    private static Map<String,Object> runtimeReport() {
        Runtime runtime=Runtime.getRuntime();
        var heap=ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        var nonHeap=ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
        List<Map<String,Object>> gc=new ArrayList<>();
        for(GarbageCollectorMXBean bean:ManagementFactory.getGarbageCollectorMXBeans()) {
            gc.add(Map.of("name",bean.getName(),"collectionCount",bean.getCollectionCount(),"collectionTimeMillis",bean.getCollectionTime()));
        }
        return Map.ofEntries(
                Map.entry("jvm",System.getProperty("java.vm.name","unknown")),
                Map.entry("javaVersion",System.getProperty("java.version","unknown")),
                Map.entry("availableProcessors",runtime.availableProcessors()),
                Map.entry("heapUsedBytes",heap.getUsed()),Map.entry("heapCommittedBytes",heap.getCommitted()),Map.entry("heapMaxBytes",heap.getMax()),
                Map.entry("nonHeapUsedBytes",nonHeap.getUsed()),Map.entry("nonHeapCommittedBytes",nonHeap.getCommitted()),
                Map.entry("gc",gc)
        );
    }
    public Map<String,Object> describe(ModelInstanceStore.Snapshot<LoadedAsset> instance) {
        Map<String,Object> result=describeInstance(instance,"asset-0");
        result.put("asset",describeAsset(instance.asset(),"asset-0"));
        return result;
    }
    private Map<String,Object> describeInstance(ModelInstanceStore.Snapshot<LoadedAsset> instance,String assetId) {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("id",instance.id());result.put("assetId",assetId);result.put("lifetime",instance.lifetime());
        result.put("scene",instance.scene());result.put("visible",instance.visible());result.put("transform",instance.transform());result.put("revision",instance.revision());
        result.put("state",renderDeferrals.containsKey(instance.id())?"GPU_DEFERRED":"READY");
        if(renderDeferrals.containsKey(instance.id()))result.put("renderDeferredReason",renderDeferrals.get(instance.id()));
        return result;
    }
    private Map<String,Object> describePerformanceAsset(LoadedAsset asset,String assetId) {
        Map<String,Object> result=new LinkedHashMap<>();
        String name=asset.source.entry();
        if(name.isEmpty()) {
            String location=asset.source.kind()==SourceKind.RESOURCE_PACK
                    ?Identifier.parse(asset.source.location()).getPath():asset.source.location().replace('\\','/');
            name=location.substring(location.lastIndexOf('/')+1);
        }
        result.put("id",assetId);result.put("name",name);
        result.put("geometry",asset.statistics.uniqueMeshes().report());
        List<Map<String,Object>> primitiveClusters = asset.render == null ? asset.prepared.clusterReport() : asset.render.clusterReport();
        result.put("clusters",Map.of("count",primitiveClusters.stream().mapToLong(item -> number(item.get("clusterCount"))).sum()));
        result.put("modelLod",asset.render == null ? asset.prepared.lodReport() : asset.render.lodReport());
        result.put("gpuResident",asset.render!=null&&asset.render.gpuResident());
        result.put("irisResidency",asset.render==null?Map.of():asset.render.irisResidencyReport());
        result.put("materialUpdates",asset.render==null?Map.of():asset.render.materialUpdateStatistics());
        result.put("opaqueBatching",asset.prepared.opaqueBatchReport());
        result.put("lightPartitioning",asset.prepared.lightPartitionReport());
        result.put("lightOcclusion",asset.prepared.lightOcclusionReport());
        result.put("transparentPreparation",asset.render==null?List.of():asset.render.transparentPreparationReport());
        result.put("memory",Map.of("sourceCpuGeometryBytes",asset.statistics.sourceCpuGeometryBytes(),
                "preparedGeometryBytes",asset.prepared.cpuGeometryBytes(),
                "decodedTextureBytes",asset.prepared.decodedTextureBytes(),
                "gpuGeometryBytesEstimated",asset.prepared.gpuGeometryBytes()));
        return result;
    }
    private Map<String,Object> describeAsset(LoadedAsset asset,String assetId) {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("id",assetId);result.put("source",asset.source);result.put("assetKey",asset.key());
        result.put("opaqueBatching",asset.prepared.opaqueBatchReport());
        result.put("lightPartitioning",asset.prepared.lightPartitionReport());
        result.put("lightOcclusion",asset.prepared.lightOcclusionReport());
        // P7-02 source-indexed cluster metadata is diagnostic-only: it does not duplicate or
        // simplify geometry, and remains available before GPU residency is established.
        List<Map<String,Object>> primitiveClusters = asset.render == null ? asset.prepared.clusterReport() : asset.render.clusterReport();
        result.put("clusters", Map.of("count", primitiveClusters.stream().mapToLong(item -> number(item.get("clusterCount"))).sum(),
                "primitives", primitiveClusters));
        result.put("modelLod",asset.render == null ? asset.prepared.lodReport() : asset.render.lodReport());
        result.put("geometry",asset.statistics.report());result.put("gpuResident",asset.render!=null&&asset.render.gpuResident());
        result.put("irisResidency",asset.render==null?Map.of():asset.render.irisResidencyReport());
        result.put("irisMaterials",asset.render==null?Map.of():asset.render.irisMaterialReport());
        if(!asset.materialOverrides.isEmpty()) {
            Map<Integer,Map<String,Float>> overrides=new LinkedHashMap<>();
            asset.materialOverrides.forEach((index,values)->overrides.put(index,Map.copyOf(values)));
            result.put("materialOverrides",Map.of("scope","shared asset; preserved across GPU/resource rebuilds; source reload creates a fresh asset","values",overrides));
        }
        result.put("transparentPreparation",asset.render==null?List.of():asset.render.transparentPreparationReport());
        result.put("import",asset.cpu.report());result.put("scenes",asset.cpu.scenes().stream().map(GltfAsset.Scene::name).toList());
        return result;
    }
    private static long number(Object value) { return value instanceof Number number ? number.longValue() : 0L; }

    public static ModelTransform previewTransform() {
        Minecraft minecraft=Minecraft.getInstance();if(minecraft.player==null)throw new IllegalStateException("enter a world first");
        var player=minecraft.player;var anchor=player.getEyePosition().add(player.getLookAngle().scale(8));
        Quaternionf rotation=new Quaternionf().rotationY((float)(Math.PI-Math.toRadians(player.getYRot())));
        return new ModelTransform(anchor.x,anchor.y,anchor.z,rotation.x,rotation.y,rotation.z,rotation.w,1,1,1);
    }
    public static String rootMessage(Throwable error) {
        while((error instanceof CompletionException||error instanceof ExecutionException)&&error.getCause()!=null)error=error.getCause();
        return error.getMessage()==null?error.toString():error.getMessage();
    }
    private static String contentHash(GltfAsset.Report report) {
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            digest.update((IMPORT_PROFILE+"/"+IMPORTER_REVISION+"\n").getBytes(StandardCharsets.UTF_8));
            report.dependencies().stream().sorted(Comparator.comparing(GltfAsset.Dependency::logicalId)).forEach(dependency -> {
                digest.update(dependency.logicalId().getBytes(StandardCharsets.UTF_8));
                digest.update((byte)0);
                digest.update(Long.toString(dependency.encodedBytes()).getBytes(StandardCharsets.UTF_8));
                digest.update((byte)0);
                digest.update(dependency.sha256().getBytes(StandardCharsets.UTF_8));
                digest.update((byte)'\n');
            });
            StringBuilder hex=new StringBuilder(64);
            for(byte value:digest.digest())hex.append(String.format(Locale.ROOT,"%02x",value));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable",impossible);
        }
    }
    public static final class LoadedAsset implements AutoCloseable {
        private final Source source;private final GltfAsset cpu;private final ModelRenderAsset.Prepared prepared;private final String contentHash;private final long preparedBytes;
        private final ModelGeometryStatistics statistics;
        private ModelRenderAsset render;private boolean closed;
        private final Map<Integer,Map<String,Float>> materialOverrides=new LinkedHashMap<>();
        private LoadedAsset(Source source,GltfAsset cpu,ModelRenderAsset.Prepared prepared,String contentHash,long preparedBytes,ModelCancellation cancellation) throws IOException {this.source=source;this.cpu=cpu;this.prepared=prepared;this.contentHash=contentHash;this.preparedBytes=preparedBytes;this.statistics=ModelGeometryStatistics.prepare(cpu,cancellation);}
        public GltfAsset cpu(){return cpu;}
        public Source source(){return source;}
        public String contentHash(){return contentHash;}
        public com.mine.geometry_node.core.engine.system.model.asset.ModelAssetKey key(){
            return new com.mine.geometry_node.core.engine.system.model.asset.ModelAssetKey(source.sessionIdentity(),cpu.entry(),contentHash,IMPORT_PROFILE,IMPORTER_REVISION);
        }
        private long renderGeneration = -1;
        private void invalidateRender() { if (render != null) { render.close(); render = null; } }
        private ModelRenderAsset render() {
            RenderSystem.assertOnRenderThread();
            if (closed) throw new IllegalStateException("model asset retired");
            long generation = ClientModelService.INSTANCE.renderResourceGeneration;
            if (renderGeneration != generation) invalidateRender();
            if (render == null || !render.gpuResident()) {
                invalidateRender();
                ModelRenderAsset replacement = ModelRenderAsset.create(prepared);
                try {
                    for(var material:materialOverrides.entrySet())for(var property:material.getValue().entrySet())
                        replacement.overrideMaterial(material.getKey(),property.getKey(),property.getValue());
                } catch(RuntimeException error) {replacement.close();throw error;}
                render = replacement;
                renderGeneration = generation;
            }
            return render;
        }
        @Override public void close(){RenderSystem.assertOnRenderThread();if(closed)return;closed=true;if(render!=null)render.close();prepared.close();ClientModelService.INSTANCE.livePreparedBytes.addAndGet(-preparedBytes);}
    }
    private record PendingUpload(String id, Pending request, LoadedAsset asset, int scene) {}

    private static final class Pending {
        final long request,epoch;final ModelLease<LoadedAsset> lease;final ModelTransform transform;final int scene;final boolean visible;final ModelInstanceLifetime lifetime;
        final ModelCollisionSettings collision;
        final CompletableFuture<ModelInstanceStore.Snapshot<LoadedAsset>> completion=new CompletableFuture<>();
        Pending(long request,long epoch,ModelLease<LoadedAsset> lease,ModelTransform transform,int scene,boolean visible,ModelInstanceLifetime lifetime,ModelCollisionSettings collision){this.request=request;this.epoch=epoch;this.lease=lease;this.transform=transform;this.scene=scene;this.visible=visible;this.lifetime=lifetime;this.collision=collision;}
        void close(){lease.close();completion.completeExceptionally(new CancellationException("model request cancelled"));}
        void fail(Throwable error){lease.close();completion.completeExceptionally(error);}
    }

    /** Local cache identity includes the importer contract so a profile upgrade cannot reuse stale data. */
    private record RepositoryKey(Source source, String profile, int importerRevision) {
        private RepositoryKey {
            Objects.requireNonNull(source);
            Objects.requireNonNull(profile);
            if (profile.isBlank() || importerRevision < 1) {
                throw new IllegalArgumentException("invalid local model repository key");
            }
        }
    }
}
