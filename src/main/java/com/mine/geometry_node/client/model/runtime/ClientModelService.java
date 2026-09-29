package com.mine.geometry_node.client.model.runtime;

import com.mine.geometry_node.client.model.render.*;
import com.mine.geometry_node.client.model.resource.ResourcePackModelProvider;
import com.mine.geometry_node.core.engine.system.model.asset.GltfAsset;
import com.mine.geometry_node.core.engine.system.model.io.*;
import com.mine.geometry_node.core.engine.system.model.math.ModelTransform;
import com.mine.geometry_node.core.engine.system.model.runtime.*;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Client-local P1/P2 instance API. Commands and future node adapters share this service. */
public final class ClientModelService {
    public static final ClientModelService INSTANCE=new ClientModelService();
    public static final String IMPORT_PROFILE="STATIC_BASIC";
    public static final int IMPORTER_REVISION=1;
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
    private final Map<String,Pending> pending=new LinkedHashMap<>();
    private final Map<String,String> failures=new LinkedHashMap<>();
    private long epoch,requestSequence,assetsDisposed;
    private ClientLevel level;
    private ClientModelService(){workers.allowCoreThreadTimeOut(true);}

    /** Replaces an instance only after its complete CPU import and renderer preparation succeed. Main thread only. */
    public CompletionStage<ModelInstanceStore.Snapshot<LoadedAsset>> create(String id,Source source,ModelTransform transform,int scene,boolean visible) {
        return create(id,source,transform,scene,visible,ModelInstanceLifetime.TEMPORARY);
    }
    /** Creates a client-local instance; persistent lifetime is reserved for P3-N. */
    public CompletionStage<ModelInstanceStore.Snapshot<LoadedAsset>> create(String id,Source source,ModelTransform transform,int scene,
                                                                            boolean visible,ModelInstanceLifetime lifetime) {
        RenderSystem.assertOnRenderThread();ModelInstanceStore.validateId(id);
        Objects.requireNonNull(source);Objects.requireNonNull(transform);Objects.requireNonNull(lifetime);
        if(lifetime==ModelInstanceLifetime.PERSISTENT)
            throw new UnsupportedOperationException("persistent model instances are provided by P3-N");
        Minecraft minecraft=Minecraft.getInstance();
        if(minecraft.level==null)throw new IllegalStateException("enter a world before creating a model");
        ensureWorld();level=minecraft.level;
        Pending previous=pending.remove(id);if(previous!=null)previous.close();
        RepositoryKey repositoryKey=repositoryKey(source);
        ModelLease<LoadedAsset> lease=repository.acquire(repositoryKey,(key,cancel)->importAsset(key.source(),cancel));
        Pending request=new Pending(++requestSequence,epoch,lease,source,transform,scene,visible,lifetime);
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
        try {cancellation.check();return new LoadedAsset(source,asset,prepared,contentHash(asset.report()));}
        catch(Throwable failure){prepared.close();throw failure;}
    }
    private void publish(String id,Pending request,LoadedAsset asset,Throwable error) {
        RenderSystem.assertOnRenderThread();
        if(pending.get(id)!=request||request.epoch!=epoch||level!=Minecraft.getInstance().level) {request.close();return;}
        pending.remove(id);
        if(error!=null) {rememberFailure(id,error);request.fail(error);return;}
        try {
            int scene=request.scene<0?asset.cpu.defaultScene():request.scene;
            validateScene(asset.cpu,scene);asset.render();
            instances.put(id,asset,scene,request.transform,request.visible,request.lifetime,request.lease::close);
            request.completion.complete(instances.get(id).orElseThrow());
        } catch(Throwable failure) {rememberFailure(id,failure);request.fail(failure);}
    }
    private static void validateScene(GltfAsset asset,int scene) {
        if(scene<0||scene>=asset.scenes().size())throw new IllegalArgumentException("scene index outside asset: "+scene);
    }
    public void transform(String id,ModelTransform transform) {RenderSystem.assertOnRenderThread();instances.transform(id,transform);}
    public void visible(String id,boolean visible) {RenderSystem.assertOnRenderThread();instances.visible(id,visible);}
    public void scene(String id,int scene) {
        RenderSystem.assertOnRenderThread();var instance=instances.get(id).orElseThrow(()->new IllegalArgumentException("unknown model instance: "+id));
        validateScene(instance.asset().cpu,scene);instances.scene(id,scene);
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
    private void rememberFailure(String id,Throwable failure){failures.remove(id);failures.put(id,rootMessage(failure));while(failures.size()>64)failures.remove(failures.keySet().iterator().next());}
    public void shutdown(){RenderSystem.assertOnRenderThread();clear();repository.close();workers.shutdownNow();}
    public boolean remove(String id) {
        RenderSystem.assertOnRenderThread();Pending request=pending.remove(id);if(request!=null)request.close();failures.remove(id);
        return instances.remove(id)||request!=null;
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
        List<Pending> requests=pending.values().stream()
                .filter(request->lifetime==null||request.lifetime==lifetime).toList();
        requests.forEach(request->pending.values().remove(request));
        requests.forEach(Pending::close);
        instances.clear(lifetime);
        if(lifetime==null) {
            repository.clear();failures.clear();level=null;
        } else if(lifetime==ModelInstanceLifetime.TEMPORARY) {
            // P3-L exposes only temporary client instances, so their failures share this session scope.
            failures.clear();
        }
    }
    public List<ModelRenderInstance> renderSnapshots() {
        RenderSystem.assertOnRenderThread();ensureWorld();
        return instances.snapshots().stream().map(instance->new ModelRenderInstance(instance.id(),instance.asset().render(),instance.scene(),instance.transform(),instance.visible())).toList();
    }
    private void ensureWorld(){if(level!=null&&level!=Minecraft.getInstance().level)clear();}
    private void dispose(LoadedAsset asset) {Minecraft.getInstance().execute(()->{asset.close();assetsDisposed++;});}
    public Map<String,Object> report() {
        RenderSystem.assertOnRenderThread();ensureWorld();
        Map<String,Object> report=new LinkedHashMap<>();
        report.put("stage","P3-L LOCAL_RUNTIME; client-local instances");report.put("generation",epoch);
        report.put("repositoryEntries",repository.size());report.put("pending",List.copyOf(pending.keySet()));
        report.put("assetsDisposed",assetsDisposed);report.put("workerQueue",workers.getQueue().size());report.put("failures",Map.copyOf(failures));
        report.put("instances",instances.snapshots().stream().map(this::describe).toList());
        report.put("renderer",ModelStaticRenderer.report());
        Set<LoadedAsset> unique=Collections.newSetFromMap(new IdentityHashMap<>());instances.snapshots().forEach(instance->unique.add(instance.asset()));
        report.put("decodedTextureBytes",unique.stream().mapToLong(asset->asset.prepared.decodedTextureBytes()).sum());
        report.put("residentTextureBytes",unique.stream().mapToLong(asset->asset.render==null?0:asset.render.textureBytes()).sum());
        report.put("visualVerification","UNTESTED until manual in-game verification");
        report.put("limitations",List.of("client-local; persistence and networking P3-N", "PBR values retained; full PBR rendering P6", "animation P7/P8", "source cache is session-scoped; call reload/invalidate after dependency changes"));
        return report;
    }
    public Map<String,Object> describe(ModelInstanceStore.Snapshot<LoadedAsset> instance) {
        Map<String,Object> result=new LinkedHashMap<>();result.put("id",instance.id());result.put("source",instance.asset().source);
        result.put("sourceIdentity",instance.asset().source.sessionIdentity());
        result.put("assetKey",instance.asset().key());result.put("contentHash",instance.asset().contentHash());
        result.put("lifetime",instance.lifetime());
        result.put("scene",instance.scene());result.put("visible",instance.visible());result.put("transform",instance.transform());result.put("revision",instance.revision());
        result.put("state","READY");result.put("rendererTextureBytes",instance.asset().render==null?0:instance.asset().render.textureBytes());
        result.put("import",instance.asset().cpu.report());result.put("scenes",instance.asset().cpu.scenes().stream().map(GltfAsset.Scene::name).toList());
        return result;
    }
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
        private final Source source;private final GltfAsset cpu;private final ModelRenderAsset.Prepared prepared;private final String contentHash;
        private ModelRenderAsset render;private boolean closed;
        private LoadedAsset(Source source,GltfAsset cpu,ModelRenderAsset.Prepared prepared,String contentHash){this.source=source;this.cpu=cpu;this.prepared=prepared;this.contentHash=contentHash;}
        public GltfAsset cpu(){return cpu;}
        public Source source(){return source;}
        public String contentHash(){return contentHash;}
        public com.mine.geometry_node.core.engine.system.model.asset.ModelAssetKey key(){
            return new com.mine.geometry_node.core.engine.system.model.asset.ModelAssetKey(source.sessionIdentity(),cpu.entry(),contentHash,IMPORT_PROFILE,IMPORTER_REVISION);
        }
        private ModelRenderAsset render(){RenderSystem.assertOnRenderThread();if(closed)throw new IllegalStateException("model asset retired");if(render==null)render=ModelRenderAsset.create(prepared);return render;}
        @Override public void close(){RenderSystem.assertOnRenderThread();if(closed)return;closed=true;if(render!=null)render.close();prepared.close();}
    }
    private static final class Pending {
        final long request,epoch;final ModelLease<LoadedAsset> lease;final Source source;final ModelTransform transform;final int scene;final boolean visible;final ModelInstanceLifetime lifetime;
        final CompletableFuture<ModelInstanceStore.Snapshot<LoadedAsset>> completion=new CompletableFuture<>();
        Pending(long request,long epoch,ModelLease<LoadedAsset> lease,Source source,ModelTransform transform,int scene,boolean visible,ModelInstanceLifetime lifetime){this.request=request;this.epoch=epoch;this.lease=lease;this.source=source;this.transform=transform;this.scene=scene;this.visible=visible;this.lifetime=lifetime;}
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
