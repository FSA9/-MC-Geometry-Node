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
import java.util.*;
import java.util.concurrent.*;

/** Client-local P1/P2 instance API. Commands and future node adapters share this service. */
public final class ClientModelService {
    public static final ClientModelService INSTANCE=new ClientModelService();
    public enum SourceKind { LOCAL, RESOURCE_PACK }
    /** Session snapshot key; P3 content-addressed network identity is intentionally a separate contract. */
    public record Source(SourceKind kind,String location,String entry) {
        public Source {Objects.requireNonNull(kind);Objects.requireNonNull(location);Objects.requireNonNull(entry);}
        public Source(SourceKind kind,String location){this(kind,location,"");}
        public static Source local(Path path){return new Source(SourceKind.LOCAL,path.toAbsolutePath().normalize().toString());}
        public static Source resource(Identifier identifier){return new Source(SourceKind.RESOURCE_PACK,identifier.toString());}
        public static Source localRoot(Path root,String entry){return new Source(SourceKind.LOCAL,root.toAbsolutePath().normalize().toString(),entry);}
    }
    private final ThreadPoolExecutor workers=new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16),task->{
        Thread thread=new Thread(task,"GeometryNode-ModelImport");thread.setDaemon(true);return thread;});
    private final ModelRepository<Source,LoadedAsset> repository=new ModelRepository<>(workers,this::dispose);
    private final ModelInstanceStore<LoadedAsset> instances=new ModelInstanceStore<>();
    private final Map<String,Pending> pending=new LinkedHashMap<>();
    private final Map<String,String> failures=new LinkedHashMap<>();
    private long epoch,requestSequence,assetsDisposed;
    private ClientLevel level;
    private ClientModelService(){workers.allowCoreThreadTimeOut(true);}

    /** Replaces an instance only after its complete CPU import and renderer preparation succeed. Main thread only. */
    public CompletionStage<ModelInstanceStore.Snapshot<LoadedAsset>> create(String id,Source source,ModelTransform transform,int scene,boolean visible) {
        RenderSystem.assertOnRenderThread();ModelInstanceStore.validateId(id);
        Minecraft minecraft=Minecraft.getInstance();
        if(minecraft.level==null)throw new IllegalStateException("enter a world before creating a model");
        ensureWorld();level=minecraft.level;
        Pending previous=pending.remove(id);if(previous!=null)previous.close();
        ModelLease<LoadedAsset> lease=repository.acquire(source,(key,cancel)->importAsset(key,cancel));
        Pending request=new Pending(++requestSequence,epoch,lease,transform,scene,visible);
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
        try {cancellation.check();return new LoadedAsset(source,asset,prepared);}
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
            instances.put(id,asset,scene,request.transform,request.visible,request.lease::close);
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
        RenderSystem.assertOnRenderThread();epoch++;
        List<Pending> requests=List.copyOf(pending.values());pending.clear();requests.forEach(Pending::close);
        instances.clear();repository.clear();failures.clear();level=null;
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
        report.put("stage","P1/P2 STATIC_BASIC; client-local instances");report.put("generation",epoch);
        report.put("repositoryEntries",repository.size());report.put("pending",List.copyOf(pending.keySet()));
        report.put("assetsDisposed",assetsDisposed);report.put("workerQueue",workers.getQueue().size());report.put("failures",Map.copyOf(failures));
        report.put("instances",instances.snapshots().stream().map(this::describe).toList());
        report.put("renderer",ModelStaticRenderer.report());
        Set<LoadedAsset> unique=Collections.newSetFromMap(new IdentityHashMap<>());instances.snapshots().forEach(instance->unique.add(instance.asset()));
        report.put("decodedTextureBytes",unique.stream().mapToLong(asset->asset.prepared.decodedTextureBytes()).sum());
        report.put("residentTextureBytes",unique.stream().mapToLong(asset->asset.render==null?0:asset.render.textureBytes()).sum());
        report.put("visualVerification","UNTESTED until manual in-game verification");
        report.put("limitations",List.of("client-local; persistence and networking P3", "PBR values retained; full PBR rendering P6", "animation P7/P8", "source cached as a session snapshot; remove all users or clear to reimport changed dependencies"));
        return report;
    }
    public Map<String,Object> describe(ModelInstanceStore.Snapshot<LoadedAsset> instance) {
        Map<String,Object> result=new LinkedHashMap<>();result.put("id",instance.id());result.put("source",instance.asset().source);
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
    public static final class LoadedAsset implements AutoCloseable {
        private final Source source;private final GltfAsset cpu;private final ModelRenderAsset.Prepared prepared;
        private ModelRenderAsset render;private boolean closed;
        private LoadedAsset(Source source,GltfAsset cpu,ModelRenderAsset.Prepared prepared){this.source=source;this.cpu=cpu;this.prepared=prepared;}
        public GltfAsset cpu(){return cpu;}
        public Source source(){return source;}
        private ModelRenderAsset render(){RenderSystem.assertOnRenderThread();if(closed)throw new IllegalStateException("model asset retired");if(render==null)render=ModelRenderAsset.create(prepared);return render;}
        @Override public void close(){RenderSystem.assertOnRenderThread();if(closed)return;closed=true;if(render!=null)render.close();prepared.close();}
    }
    private static final class Pending {
        final long request,epoch;final ModelLease<LoadedAsset> lease;final ModelTransform transform;final int scene;final boolean visible;
        final CompletableFuture<ModelInstanceStore.Snapshot<LoadedAsset>> completion=new CompletableFuture<>();
        Pending(long request,long epoch,ModelLease<LoadedAsset> lease,ModelTransform transform,int scene,boolean visible){this.request=request;this.epoch=epoch;this.lease=lease;this.transform=transform;this.scene=scene;this.visible=visible;}
        void close(){lease.close();completion.completeExceptionally(new CancellationException("model request cancelled"));}
        void fail(Throwable error){lease.close();completion.completeExceptionally(error);}
    }
}
