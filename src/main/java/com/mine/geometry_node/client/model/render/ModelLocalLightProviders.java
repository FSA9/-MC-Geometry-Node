package com.mine.geometry_node.client.model.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import java.util.*;

/** Explicit visual point lights. Material emissive color alone does not create a light source. */
public final class ModelLocalLightProviders {
    public static final int MAX_LIGHTS=32, MAX_PROVIDERS=16;
    /** Called on render thread; implementations must only read already available state, never load chunks. */
    @FunctionalInterface public interface Provider {void collect(ClientLevel world, Sink sink);}
    @FunctionalInterface public interface Sink {void add(double x,double y,double z,float emission);}
    private record Attached(String instance,float x,float y,float z,float emission) {}
    private static final Map<String,Provider> providers=new LinkedHashMap<>();
    private static final Map<String,Attached> attached=new LinkedHashMap<>();
    private static final float[] data=new float[MAX_LIGHTS*4];
    private static ClientLevel world;
    private static int count,rejected;
    private static long updateNanos;
    private static final long PROVIDER_BUDGET_NANOS=500_000;
    private static String failure="";
    public static synchronized void register(String id,Provider provider) {
        Objects.requireNonNull(id);Objects.requireNonNull(provider);
        if(!providers.containsKey(id)&&providers.size()>=MAX_PROVIDERS)throw new IllegalStateException("local light provider limit: "+MAX_PROVIDERS);
        providers.put(id,provider);
    }
    public static synchronized void unregister(String id) {providers.remove(id);}
    /** One source anchored in model-local space; moves/scales with the instance, removed when the instance disappears. */
    public static synchronized void attach(String id,String instance,float x,float y,float z,float emission) {
        if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z)||!Float.isFinite(emission)||emission<=0||emission>15)
            throw new IllegalArgumentException("finite local position and emission in (0,15] required");
        Objects.requireNonNull(id);Objects.requireNonNull(instance);
        ClientLevel current=Minecraft.getInstance().level;
        if(current==null)throw new IllegalStateException("a client world is required for attached lights");
        if(current!=world) {attached.clear();world=current;}
        if(!attached.containsKey(id)&&attached.size()>=MAX_LIGHTS)throw new IllegalStateException("attached light limit: "+MAX_LIGHTS);
        attached.put(Objects.requireNonNull(id),new Attached(Objects.requireNonNull(instance),x,y,z,emission));
    }
    public static synchronized void detach(String id) {attached.remove(id);}
    public static synchronized void detachInstance(String instance) {attached.values().removeIf(a->a.instance.equals(instance));}
    public static synchronized void clearAttached() {attached.clear();}
    static synchronized void update(List<ModelRenderInstance> instances,Vec3 camera,boolean enabled) {
        long started=System.nanoTime();count=rejected=0;failure="";
        ClientLevel current=Minecraft.getInstance().level;
        if(current!=world) {attached.clear();world=current;}
        if(!enabled||current==null||(attached.isEmpty()&&providers.isEmpty())) {updateNanos=System.nanoTime()-started;return;}
        Map<String,ModelRenderInstance> active=new HashMap<>();for(var i:instances)active.put(i.id(),i);
        // Missing render snapshots can mean a model is temporarily loading; explicit instance removal
        // owns detachment, so a reload does not silently lose its light attachments.
        Sink sink=(x,y,z,emission)->{
            if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||!Float.isFinite(emission)||emission<=0||emission>15) {rejected++;return;}
            if(count>=MAX_LIGHTS) {rejected++;return;}
            float rx=(float)(x-camera.x),ry=(float)(y-camera.y),rz=(float)(z-camera.z);
            if(!Float.isFinite(rx)||!Float.isFinite(ry)||!Float.isFinite(rz)) {rejected++;return;}
            int p=count++*4;data[p]=rx;data[p+1]=ry;data[p+2]=rz;data[p+3]=emission;
        };
        for(Attached light:attached.values()) {
            var instance=active.get(light.instance);if(instance==null||!instance.visible()||instance.asset().isClosed())continue;var t=instance.transform();
            Vector3f p=t.relativeTo(t.x(),t.y(),t.z()).transformPosition(new Vector3f(light.x,light.y,light.z));
            sink.add(t.x()+p.x,t.y()+p.y,t.z()+p.z,light.emission);
        }
        // Snapshot allows a callback to register/unregister a provider without invalidating iteration.
        // One aggregate soft budget bounds all providers; a running third-party callback cannot be preempted.
        long providerStarted=System.nanoTime();
        for(var entry:new LinkedHashMap<>(providers).entrySet()) {
            if(count>=MAX_LIGHTS)break;
            if(System.nanoTime()-providerStarted>=PROVIDER_BUDGET_NANOS) {
                failure="providers exceeded aggregate 0.5ms soft budget";break;
            }
            try {entry.getValue().collect(current,sink);}catch(RuntimeException e) {failure=entry.getKey()+": "+e;}
            if(System.nanoTime()-providerStarted>=PROVIDER_BUDGET_NANOS) {
                failure="providers exceeded aggregate 0.5ms soft budget at "+entry.getKey();break;
            }
        }
        updateNanos=System.nanoTime()-started;
    }
    static synchronized void resetFrame() {
        count=rejected=0;updateNanos=0;failure="";
        ClientLevel current=Minecraft.getInstance().level;
        if(current!=world) {attached.clear();world=current;}
    }
    static int count() {return count;}
    static float[] uniforms() {return data;}
    static synchronized Map<String,Object> report() {
        return Map.of("registeredProviders",List.copyOf(providers.keySet()),"attachedSources",attached.size(),"activeSources",count,
                "rejectedSources",rejected,"updateNanos",updateNanos,"lastFailure",failure,
                "scope","explicit neutral direct point lights, max contribution, model-local attachments and client provider API; no automatic emissive-material conversion or arbitrary third-party mod discovery");
    }
    private ModelLocalLightProviders() {}
}
