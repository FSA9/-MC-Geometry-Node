package com.mine.geometry_node.client.model.render;
import com.mine.geometry_node.client.model.compat.IrisModelBridge;
import com.mine.geometry_node.core.engine.system.model.asset.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import java.util.*;
/** Client-only P0 harness, opt-in via command. Not a persistent production instance store. */
public final class ModelProbeController {
    public enum Mode { COMPATIBILITY, GPU, BOTH }
    public static final ModelProbeController INSTANCE=new ModelProbeController();
    private record Item(ModelMesh mesh,ModelProbeGpuMesh gpu,Vec3 position,boolean resident,int light) {}
    private final IrisModelBridge iris=new IrisModelBridge();
    private List<Item> items=List.of();
    private long generation, mainDraws,shadowDraws,submitted,uploadBytes;
    private String failure="";
    private net.minecraft.client.multiplayer.ClientLevel level;
    private ModelProbeController() {}
    public void show(Mode mode) {
        RenderSystem.assertOnRenderThread(); clear();
        Minecraft mc=Minecraft.getInstance(); if(mc.player==null||mc.level==null)throw new IllegalStateException("enter a world first");
        iris.initialize(ModelProbePipelines.PIPELINE,this::shadow);
        Vec3 anchor=mc.player.getEyePosition().add(mc.player.getLookAngle().scale(6));
        List<Item> created=new ArrayList<>();
        try {
            for(int shape=0;shape<3;shape++) {
                Matrix4f m=new Matrix4f().rotateY(.35f);
                if(shape==1)m.scale(-1,1,1); if(shape==2)m.rotateZ(.4f).scale(.6f,1.6f,1.1f);
                ModelMesh mesh=ModelProbeFixtures.cube(m);
                for(boolean gpu:new boolean[]{false,true}) {
                    if(mode==Mode.GPU&&!gpu || mode==Mode.COMPATIBILITY&&gpu)continue;
                    Vec3 position=anchor.add((shape-1)*2.3,gpu&&mode==Mode.BOTH?2:0,0);
                    int light=net.minecraft.util.LightCoordsUtil.pack(mc.level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,BlockPos.containing(position)),
                            mc.level.getBrightness(net.minecraft.world.level.LightLayer.SKY,BlockPos.containing(position)));
                    ModelProbeGpuMesh resident=new ModelProbeGpuMesh(mesh,light);
                    // The compatibility row also has a resident opaque shadow caster, separate from its main submit.
                    created.add(new Item(mesh,resident,position,gpu,light));
                }
            }
            items=List.copyOf(created); level=mc.level;
        } catch(RuntimeException|Error e) { created.forEach(i->i.gpu.close()); throw e; }
    }
    public void submit(PoseStack stack,SubmitNodeCollector collector,Vec3 camera) {
        if(!validWorld()||iris.shadow())return;
        for(Item item:items) if(!item.resident) {
            stack.pushPose();
            try {
                Vec3 p=item.position.subtract(camera);stack.translate(p.x,p.y,p.z);
                collector.submitCustomGeometry(stack,ModelProbePipelines.TYPE,(pose,out)->{
                    for(int i=0;i<item.mesh.indexCount();i++) ModelProbeVertices.emit(item.mesh,item.mesh.index(i),out,pose,item.light);
                }); submitted++;
            } finally {stack.popPose();}
        }
    }
    public void draw(org.joml.Matrix4fc view,Vec3 camera) {
        if(!validWorld()||iris.shadow())return;
        for(Item item:items) if(item.resident) { drawItem(item,view,camera); mainDraws++; }
    }
    private void shadow(Matrix4f view,Matrix4f projection,double x,double y,double z,float tick) {
        if(!validWorld())return;
        // Iris already owns projection/target routing here. Never advance pose or use main-camera culling.
        for(Item item:items) {drawItem(item,view,new Vec3(x,y,z));shadowDraws++;}
    }
    private void drawItem(Item item,org.joml.Matrix4fc view,Vec3 camera) {
        try {
            Vec3 p=item.position.subtract(camera);
            long before=item.gpu.uploadedBytes();
            item.gpu.draw(new Matrix4f(view).translate((float)p.x,(float)p.y,(float)p.z));
            uploadBytes+=item.gpu.uploadedBytes()-before;
        } catch(RuntimeException e) {
            failure=e.toString(); clear();
            com.mine.geometry_node.GeometryNode.LOGGER.error("Model P0 probe stopped after draw failure",e);
        }
    }
    private boolean validWorld() {
        Minecraft minecraft=Minecraft.getInstance();
        if (level != null && level != minecraft.level) { clear(); return false; }
        return !items.isEmpty() && level==minecraft.level;
    }
    public void clear() {
        RenderSystem.assertOnRenderThread();
        List<Item> old=items;items=List.of();level=null;generation++;
        // A host frame may still contain submitted callbacks; only GPU buffers retire after its fence.
        old.forEach(i->i.gpu.close());
    }
    public Map<String,Object> report() {
        var m=new LinkedHashMap<String,Object>();
        m.put("stage","P0 probe, not production model loading");m.put("generation",generation);m.put("instances",items.size());
        m.put("residentBytes",items.stream().mapToLong(i->i.gpu.bytes()).sum());m.put("uploadedBytesTotal",uploadBytes);
        m.put("mainResidentDrawCalls",mainDraws);m.put("hostSubmissions",submitted);m.put("opaqueShadowDrawCalls",shadowDraws);
        m.put("iris",iris.status());m.put("shaderPackActive",iris.active());m.put("lastFailure",failure);
        m.put("visualVerification","UNTESTED: draw execution alone does not establish correct pixels");
        m.put("limitations",List.of("opaque only", "light sampled once on show", "transform baked once per probe shape", "PBR/BLEND/local-light occlusion not implemented by P0"));
        return m;
    }
}
