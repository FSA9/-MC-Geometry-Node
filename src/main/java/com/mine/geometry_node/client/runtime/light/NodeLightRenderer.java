package com.mine.geometry_node.client.runtime.light;

import com.mine.geometry_node.client.model.render.bvh.ModelIrisHeldLight;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.IntSupplier;

/** Render-thread light snapshots and pack-independent GPU resources. */
public final class NodeLightRenderer {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    public static final int MAX_VISIBLE_LIGHTS = 8;
    private static final Map<Integer, Interface> PROGRAMS = new HashMap<>();
    private static final Map<Integer, Interface> NATIVE = new HashMap<>();
    private static List<ClientLightStore.ResolvedLight> lights = List.of();
    private static final float[] data = new float[8*12*4];
    private static long generation, preparedNanos;
    private static long cookieGeneration = Long.MIN_VALUE;
    private static long nativeDraws, frameNativeDraws, packDraws, framePackDraws;
    private static long forwardActivations, frameForwardActivations;
    private static int rejected;
    private static String failure = "", status = "IDLE";
    private static String registrationFailure = "";
    private static Vec3 camera = Vec3.ZERO;
    private static boolean drawing;
    private static Object world;

    private NodeLightRenderer() {}
    public static void prepare(Vec3 eye) {
        com.mine.geometry_node.client.model.render.diagnostic.ModelShaderGpuTimings.poll();
        long started=System.nanoTime();
        frameNativeDraws=0;
        framePackDraws=0;
        frameForwardActivations=0;
        var mc=Minecraft.getInstance();
        if(world!=mc.level) {clear();world=mc.level;}
        camera=eye;
        var resolved=ClientLightStore.resolved(mc.getDeltaTracker().getGameTimeDeltaPartialTick(true));
        lights=nearest(resolved,eye);
        rejected=resolved.size()-lights.size();
        Arrays.fill(data,0);
        for(int i=0;i<lights.size();i++) {
            var light=lights.get(i);var d=light.definition();var c=d.config();
            var q=c.quality();var v=c.volume();var p=c.projection();
            float half=(float)Math.toRadians(d.coneAngle()*0.5);
            boolean area="area".equals(d.type());
            put(i,0,light.position().subtract(eye),d.range());
            // -2 is omnidirectional; area emitters use a front hemisphere and dimensions in spare lanes.
            put(i,1,light.direction(),"point".equals(d.type())?-2f:area?0f:(float)Math.cos(half));
            put(i,2,d.color(),d.intensity());
            put(i,3,light.right(),area?0f:(float)Math.cos(half*(1-d.edgeSoftness())));
            put(i,4,light.up(),area?0f:(float)Math.tan(half));
            put(i,5,new Vec3(c.shadow().enabled()?1:0,d.emitterRadius(),q.shadowSamples()),q.shadowResolution());
            put(i,6,new Vec3(v.mode().equals("off")?0:v.mode().equals("uniform")?1:2,v.strength(),v.density()),0.4f);
            put(i,7,new Vec3(v.noiseStrength(),v.noiseScale(),area?c.area().width()*0.5f:0),v.seed()%4096);
            put(i,8,new Vec3(0,Math.toRadians(p.rotation()),p.scale()),p.colored()?1:0);
            put(i,9,new Vec3(q.volumeSamples(),q.volumeResolution(),c.sourceVisible()?1:0),q.volumeEmitterSamples());
            put(i,10,v.velocity(),area?c.area().height()*0.5f:0);
            put(i,11,new Vec3((eye.x/v.noiseScale())%4096,(eye.y/v.noiseScale())%4096,(eye.z/v.noiseScale())%4096),1);
        }
        generation++;
        if (NativeTerrainLights.enabled() || !PROGRAMS.isEmpty()) {
            NodeLightEntityShadows.prepare(eye, lights, mc.getDeltaTracker().getGameTimeDeltaPartialTick(true));
        } else {
            NodeLightEntityShadows.clear();
        }
        if(lights.isEmpty()) { NodeLightTargets.clear();NodeLightCookies.clear();status="IDLE"; }
        else {
            status=PROGRAMS.isEmpty()?(registrationFailure.isEmpty()?"WAITING_FOR_RECEIVER":"REGISTRATION_FAILED"):"PREPARED";
            // Forward transparent receivers run before composite passes bind the projection atlas.
            try {updateCookies();}
            catch(RuntimeException error){failure=error.toString();status="PREPARE_FAILED";}
        }
        preparedNanos=System.nanoTime()-started;
        LightReports.frame(preparedNanos);
    }
    private static void put(int index,int row,Vec3 value,float w) {
        int b=(index*12+row)*4;data[b]=(float)value.x;data[b+1]=(float)value.y;data[b+2]=(float)value.z;data[b+3]=w;
    }

    static List<ClientLightStore.ResolvedLight> nearest(List<ClientLightStore.ResolvedLight> resolved, Vec3 eye) {
        if(resolved.isEmpty())return List.of();
        var selected=new ArrayList<ClientLightStore.ResolvedLight>(MAX_VISIBLE_LIGHTS);
        double[] distances=new double[MAX_VISIBLE_LIGHTS];
        for(var light:resolved) {
            double distance=light.position().distanceToSqr(eye);
            double reach=light.definition().range()+light.definition().emitterRadius()+128;
            if(!(distance<reach*reach))continue;
            int index=selected.size();
            // Keep only the nearest eight, with the same deterministic ID tie-break as a full sort.
            while(index>0) {
                int order=Double.compare(distance,distances[index-1]);
                if(order>0 || order==0 && light.id().compareTo(selected.get(index-1).id())>=0)break;
                index--;
            }
            if(index==MAX_VISIBLE_LIGHTS)continue;
            if(selected.size()==MAX_VISIBLE_LIGHTS)selected.remove(MAX_VISIBLE_LIGHTS-1);
            for(int i=selected.size();i>index;i--)distances[i]=distances[i-1];
            distances[index]=distance;
            selected.add(index,light);
        }
        return List.copyOf(selected);
    }
    public static boolean active() {return !lights.isEmpty();}
    public static List<ClientLightStore.ResolvedLight> lights() {return lights;}
    public static Vec3 camera() {return camera;}
    public static long generation() {return generation;}
    public static boolean inPrepass() {
        // Diagnostic replays must bypass nested model-light FBO passes as well.
        if(NodeLightPixelDiagnostics.isDrawing() || NodeLightFroxels.rendering())return true;
        if(!drawing)return false;
        int program=GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        var u=PROGRAMS.get(program);
        return u!=null&&u.pass>=0&&GL20C.glGetUniformi(program,u.pass)!=0;
    }

    public static void registerProgram(Object builder) {
        try {
            var type=builder.getClass();Field field=type.getDeclaredField("program");field.setAccessible(true);
            int program=field.getInt(builder);
            if(PROGRAMS.containsKey(program)||GL20C.glGetUniformLocation(program,"nodeLightCount")<0)return;
            var add=Arrays.stream(type.getMethods()).filter(m->m.getName().equals("addDynamicSampler")&&m.getParameterCount()==3&&m.getParameterTypes()[0]==IntSupplier.class).findFirst().orElseThrow();
            for(var entry:Map.<String,IntSupplier>of("nodeLightSurfaceAtlas",NodeLightTargets::surfaceTexture,
                    "nodeLightVolumeAtlas",NodeLightTargets::volumeTexture,"nodeLightCookies",NodeLightCookies::texture).entrySet()) {
                if(GL20C.glGetUniformLocation(program,entry.getKey())<0)continue;
                if(!(boolean)add.invoke(builder,entry.getValue(),null,new String[]{entry.getKey()}))
                    throw new IllegalStateException("Cannot allocate light sampler: "+entry.getKey());
            }
            if(GL20C.glGetUniformLocation(program,"nodeEntityBvh")>=0
                    && !(boolean)add.invoke(builder,(IntSupplier)NodeLightEntityShadows::texture,null,new String[]{"nodeEntityBvh"}))
                throw new IllegalStateException("Cannot allocate light sampler: nodeEntityBvh");
            PROGRAMS.put(program,new Interface(program));
            registrationFailure="";
            LOGGER.info("Node light pack receiver registered: program={}",program);
        }catch(ReflectiveOperationException|RuntimeException e){
            String reason="Light sampler registration: "+e;
            if(!reason.equals(registrationFailure))LOGGER.warn(reason);
            registrationFailure=reason;
        }
    }

    public static void upload(int program) {
        Interface u=PROGRAMS.get(program);if(u==null)return;
        upload(program,u);
        if(u.pass<0 && u.froxelReady<0 && active()){forwardActivations++;frameForwardActivations++;}
    }
    public static void uploadForward(int program) {
        Interface u=PROGRAMS.get(program);
        if(u==null || u.pass>=0 || u.froxelReady>=0)return;
        ModelIrisHeldLight.uploadUniforms(program);
        upload(program,u);
        if(active()){forwardActivations++;frameForwardActivations++;}
    }
    static void upload(int program,Interface u) {
        if (!active()) {
            if (!u.idleUploaded) {
                GL20C.glUniform1i(u.count,0);
                GL20C.glUniform1i(u.pass,0);
                GL20C.glUniform1i(u.resolved,0);
                GL20C.glUniform1i(u.froxelReady,0);
                u.idleUploaded=true;
            }
            return;
        }
        u.idleUploaded=false;
        GL20C.glUniform1i(u.cameraAbsorption,1);
        GL20C.glUniform1i(u.packJitter,u.jitterEnabled?1:0);
        GL20C.glUniform1i(u.count,lights.size());GL20C.glUniform1i(u.pass,0);GL20C.glUniform1i(u.resolved,0);
        GL20C.glUniform1i(u.occluders,ModelIrisHeldLight.nodeOccluderCount());
        // Supported pack receivers reconstruct the same camera-relative world coordinates.
        GL20C.glUniform3i(u.entityRange,NodeLightEntityShadows.nodeCount(),
                NodeLightEntityShadows.triangleTexelOffset(),NodeLightEntityShadows.textureWidth());
        GL20C.glUniform1i(u.entityBoundsCount,NodeLightEntityShadows.boundsCount());
        // Activation state resets between draws; the frame snapshot itself stays unchanged.
        if(u.uploadedGeneration!=generation) {
            GL20C.glUniform4fv(u.entityBounds,NodeLightEntityShadows.bounds());
            var level=Minecraft.getInstance().level;
            float time=level==null?0:(level.getGameTime()%240000)+Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
            GL20C.glUniform1f(u.time,time/20f);
            GL20C.glUniform3f(u.origin,(float)(camera.x%4096),(float)(camera.y%4096),(float)(camera.z%4096));
            if(active())GL20C.glUniform4fv(u.data,data);
            u.uploadedGeneration=generation;
        }
        if(u.eye>=0) {
            if(u.inverseView>=0) {GL20C.glGetUniformfv(program,u.inverseView,u.inverseScratch);GL20C.glUniform3f(u.eye,u.inverseScratch[12],u.inverseScratch[13],u.inverseScratch[14]);}
            else GL20C.glUniform3f(u.eye,0,0,0);
        }
    }

    public static void draw(Runnable draw) {
        if (!active()) {draw.run();return;}
        int program=GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        Interface u=PROGRAMS.get(program);
        if(u==null) {draw.run();return;}
        try {
            updateCookies();
            // Program.use occurs before its sampler layout is finalized. Upload at the draw as well.
            upload(program,u);
            drawing=true;
            try {if(u.pass<0 && u.froxelReady<0)draw.run();else NodeLightTargets.draw(program,u,draw);} finally {drawing=false;}
            packDraws++;framePackDraws++;
            if(u.draws++==0)LOGGER.info("Node light pack surface draw reached: program={}, lights={}",program,lights.size());
            status="ACTIVE";failure="";
        }catch(RuntimeException e) {
            failure=e.toString();status="RENDER_FAILED";
            GL20C.glUniform1i(u.count,0);GL20C.glUniform1i(u.pass,0);GL20C.glUniform1i(u.resolved,0);
            draw.run();
        }
    }

    public static void drawNative(Runnable draw) {
        try (var scope = bindNative()) {
            draw.run();
            if (scope != null) recordNativeDraw();
        }
    }

    static NativeBinding bindNative() {
        if(!active()) return null;
        int program=GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        Interface u=NATIVE.computeIfAbsent(program,Interface::new);
        if(u.count<0) return null;
        try {updateCookies();upload(program,u);}
        catch(RuntimeException exception) {
            failure=exception.toString();status="NATIVE_RENDER_FAILED";
            GL20C.glUniform1i(u.count,0);return null;
        }
        return new NativeBinding(program,u);
    }

    static void recordNativeDraw() {
        nativeDraws++;frameNativeDraws++;status="NATIVE_SURFACE";failure="";
        if(nativeDraws==1)com.mine.geometry_node.GeometryNode.LOGGER.info("Node light native surface draw reached: lights={}",lights.size());
    }

    static final class NativeBinding implements AutoCloseable {
        private final Interface u;
        private final int activeUnit,oldTexture,oldSampler,oldCookie;
        private final int oldEntityTexture,oldEntitySampler,oldEntityUniform;
        NativeBinding(int program,Interface u) {
        this.u=u;
        activeUnit=GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0+12);
        oldEntityTexture=GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        oldEntitySampler=GL30C.glGetIntegeri(GL33C.GL_SAMPLER_BINDING,12);
        oldEntityUniform=u.entityBvh<0?0:GL20C.glGetUniformi(program,u.entityBvh);
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D,NodeLightEntityShadows.texture());
        GL33C.glBindSampler(12,0);
        GL20C.glUniform1i(u.entityBvh,12);
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0+13);
        oldTexture=GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);oldSampler=GL30C.glGetIntegeri(GL33C.GL_SAMPLER_BINDING,13);
        oldCookie=u.cookies<0?0:GL20C.glGetUniformi(program,u.cookies);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D,NodeLightCookies.texture());GL33C.glBindSampler(13,0);
            GL20C.glUniform1i(u.cookies,13);GL13C.glActiveTexture(activeUnit);
        }
        @Override public void close() {
            GL20C.glUniform1i(u.count,0);GL20C.glUniform1i(u.cookies,oldCookie);
            GL20C.glUniform3i(u.entityRange,0,0,1);
            GL20C.glUniform1i(u.entityBoundsCount,0);
            GL20C.glUniform1i(u.entityBvh,oldEntityUniform);
            GL13C.glActiveTexture(GL13C.GL_TEXTURE0+12);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D,oldEntityTexture);
            GL33C.glBindSampler(12,oldEntitySampler);
            GL13C.glActiveTexture(GL13C.GL_TEXTURE0+13);GL11C.glBindTexture(GL11C.GL_TEXTURE_2D,oldTexture);GL33C.glBindSampler(13,oldSampler);GL13C.glActiveTexture(activeUnit);
        }
    }

    private static void updateCookies() {
        if(cookieGeneration==generation)return;
        NodeLightCookies.prepare(lights);
        for(int i=0;i<lights.size();i++) {
            if(lights.get(i).projectionTexture()==null)continue;
            data[(i*12+8)*4]=1;
            data[(i*12+11)*4+3]=NodeLightCookies.aspect(i);
        }
        cookieGeneration=generation;
    }
    static int cookies() {return NodeLightCookies.texture();}
    public static void deleted(int program) {PROGRAMS.remove(program);NATIVE.remove(program);NativeTerrainLights.deleted(program);NodeLightFroxels.deleted(program);}
    public static void clear() {
        cookieGeneration=Long.MIN_VALUE;
        lights=List.of();NodeLightTargets.clear();NodeLightCookies.clear();
        NodeLightEntityShadows.clear();
        NativeVolumeLights.clear();
    }
    public static Map<String,Object> report() {
        var out=new LinkedHashMap<String,Object>();out.put("status",status);out.put("failure",failure);
        out.put("registrationFailure",registrationFailure);
        out.put("shaderAdapters",NodeLightShader.report());
        out.put("clientSync",ClientLightStore.diagnostics());
        out.put("serverSync",com.mine.geometry_node.core.engine.graph.light.GraphLightService.INSTANCE.diagnostics());
        out.put("active",lights.size());out.put("excluded",rejected);out.put("programs",PROGRAMS.size());
        out.put("nativeDraws",nativeDraws);out.put("frameNativeDraws",frameNativeDraws);
        out.put("packDraws",packDraws);out.put("framePackDraws",framePackDraws);
        out.put("forwardActivations",forwardActivations);out.put("frameForwardActivations",frameForwardActivations);
        out.put("packReceivers",PROGRAMS.entrySet().stream().map(e->Map.of("program",e.getKey(),"draws",e.getValue().draws,
                "path",e.getValue().froxelReady>=0?"froxel":e.getValue().pass<0?"direct":"surface_atlas", "surfaceAtlas",e.getValue().surface>=0,"volumeAtlas",e.getValue().volume>=0)).toList());
        out.put("terrain",NativeTerrainLights.report());
        out.put("nativeVolume",NativeVolumeLights.report());
        out.put("entityShadows",NodeLightEntityShadows.report());
        out.put("prepareNanos",preparedNanos);out.put("prepass",NodeLightTargets.report());
        out.put("projectionGpuBytes",NodeLightCookies.bytes());out.put("failedProjections",NodeLightCookies.failures());
        out.put("limits","8 nearest lights; range <=32; shared 8-model BVH and 8 world tiles; uncovered world segments are shadowed");
        out.put("nativeScope","vanilla/Sodium terrain and resident model surfaces; native depth-limited volumetrics with per-light resolution; no entity or model collector fallback surface receivers; reduced surface evaluation requires supported Iris pack");
        out.put("lights",lights.stream().map(l->Map.of("id",l.id(),"config",l.definition().config().toString(),"position",l.position().toString())).toList());
        out.put("emitterSettings",lights.stream().map(l->{
            var config=l.definition().config();
            int samples=l.definition().emitterRadius()<=0?1:Math.clamp(config.quality().shadowSamples(),4,16);
            return Map.of("id",l.id(),"type",l.definition().type(),"radius",config.shadow().sourceSize(),
                    "areaWidth",config.area().width(),"areaHeight",config.area().height(),"emitterBoundRadius",l.definition().emitterRadius(),
                    "samples",samples,"shadows",config.shadow().enabled(),"range",l.definition().range());
        }).toList());
        out.put("volumeSettings",lights.stream().map(l->{
            var config=l.definition().config();var volume=config.volume();
            boolean enabled=!volume.mode().equals("off") && volume.strength()>0 && volume.density()>0;
            int emitters=l.definition().emitterRadius()<=0?1:config.quality().volumeEmitterSamples();
            var grid=NodeLightFroxelQuality.report(Minecraft.getInstance().getWindow().getWidth(),
                    Minecraft.getInstance().getWindow().getHeight(),config.quality());
            return Map.of("id",l.id(),"mode",volume.mode(),"enabled",enabled,"strength",volume.strength(),
                    "density",volume.density(),"requestedGrid",grid,"emitterSamplesPerFroxel",emitters,
                    "configuredEmitterEvaluationUpperBound",enabled?((Number)grid.get("froxelCount")).longValue()*emitters:0L);
        }).toList());
        out.put("workEstimateScope","Configured per-light froxel-grid bounds, not measured ray counts. Runtime allocation limits and active-light state are reported separately. Lighting is evaluated once per froxel and shared by depth interval lookups. Off/zero-density volumes do not integrate.");
        return out;
    }
    static final class Interface {
        long draws;
        boolean idleUploaded;
        long uploadedGeneration=Long.MIN_VALUE;
        final float[] inverseScratch=new float[16];
        final int packJitter;
        final int cameraAbsorption;
        final int froxelReady;
        final int entityBoundsCount,entityBounds;
        final int[] occupiedMin=new int[8],occupiedMax=new int[8];
        final boolean jitterEnabled;
        final int count,pass,index,resolved,data,occluders,time,origin,eye,inverseView,viewport,originalSize,surface,volume,cookies,surfaceRects,entityBvh,entityRange;
        Interface(int p) {
            cameraAbsorption=location(p,"nodeFroxelCameraAbsorption");
            froxelReady=location(p,"nodeFroxelReady");
            packJitter=location(p,"nodePackJitter");
            jitterEnabled=packJitter>=0 && com.mine.geometry_node.client.runtime.light.shader.adapter.LightShaderOptions.booleanValue("taaEnabled",true);
            count=location(p,"nodeLightCount");pass=location(p,"nodeLightPass");index=location(p,"nodeLightIndex");resolved=location(p,"nodeLightResolved");
            data=location(p,"nodeLights[0]");occluders=location(p,"nodeOccluderCount");time=location(p,"nodeLightTime");origin=location(p,"nodeNoiseOrigin");
            eye=location(p,"nodeEyeOffset");inverseView=location(p,"gbufferModelViewInverse");
            viewport=location(p,"nodePassViewport");originalSize=location(p,"nodeOriginalSize");
            surface=location(p,"nodeLightSurfaceAtlas");volume=location(p,"nodeLightVolumeAtlas");cookies=location(p,"nodeLightCookies");
            surfaceRects=location(p,"nodeSurfaceRects[0]");
            entityBvh=location(p,"nodeEntityBvh");entityRange=location(p,"nodeEntityRange");
            entityBoundsCount=location(p,"nodeEntityBoundsCount");entityBounds=location(p,"nodeEntityBounds[0]");
            for(int i=0;i<8;i++) {
                occupiedMin[i]=location(p,"geometry_node_blockOccupiedMin["+i+"]");
                occupiedMax[i]=location(p,"geometry_node_blockOccupiedMax["+i+"]");
            }
        }
        private static int location(int p,String name){return GL20C.glGetUniformLocation(p,name);}
    }
}
