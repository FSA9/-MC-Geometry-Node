package com.mine.geometry_node.client.model.render;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.system.model.asset.GltfAsset;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import java.util.*;

/** Native static baseline. BLEND triangles sort across all model instances before any material batching. */
public final class ModelStaticRenderer {
    private static final int MAX_BLEND_TRIANGLES=500_000;
    private static final int MAX_RUN_TRIANGLES=16_384;
    private static long opaqueSubmissions,transparentTriangles,transparentDraws;
    private static String lastFailure="";
    private record Draw(ModelRenderAsset.Primitive primitive,RenderType type,PoseStack.Pose pose,boolean mirrored,int light){}
    private record Triangle(Draw draw,int offset,float depth){}
    private ModelStaticRenderer(){}
    public static void submit(PoseStack ignoredHostStack,SubmitNodeCollector collector,Vec3 camera,List<ModelRenderInstance> instances) {
        RenderSystem.assertOnRenderThread();
        try {
            for(ModelRenderInstance instance:instances) {
                if(!instance.visible()||instance.asset().isClosed())continue;
                for(ModelRenderAsset.Occurrence occurrence:instance.asset().occurrences(instance.scene())) {
                    PoseStack pose=new PoseStack();pose.mulPose(instance.transform().relativeTo(camera.x,camera.y,camera.z).mul(occurrence.transform()));
                    boolean mirrored=pose.last().pose().determinant3x3()<0;
                    for(ModelRenderAsset.Primitive primitive:occurrence.primitives()) {
                        if(primitive.material.alphaMode()==GltfAsset.AlphaMode.BLEND)continue;
                        int light=sampleLight(primitive,pose.last().pose(),camera);
                        RenderType type=instance.asset().renderType(primitive.material);
                        collector.submitCustomGeometry(pose,type,(captured,out)->{
                            for(int offset=0;offset<primitive.indices.length;offset+=3)emitTriangle(primitive,captured,mirrored,light,offset,out);
                        });opaqueSubmissions++;
                    }
                }
            }
        } catch(RuntimeException e){failed(e);}
    }
    /** Called after vanilla translucent particles; P2 cross-sorts models, not vanilla water/glass geometry. */
    public static void drawTransparent(Matrix4fc view,Vec3 camera,List<ModelRenderInstance> instances) {
        RenderSystem.assertOnRenderThread();List<Triangle> triangles=new ArrayList<>();
        try {
            Vector3f center=new Vector3f();
            for(ModelRenderInstance instance:instances) {
                if(!instance.visible()||instance.asset().isClosed())continue;
                for(ModelRenderAsset.Occurrence occurrence:instance.asset().occurrences(instance.scene())) {
                    PoseStack stack=new PoseStack();stack.mulPose(instance.transform().relativeTo(camera.x,camera.y,camera.z).mul(occurrence.transform()));
                    PoseStack.Pose pose=stack.last();boolean mirrored=pose.pose().determinant3x3()<0;
                    Matrix4f eye=new Matrix4f(view).mul(pose.pose());
                    for(ModelRenderAsset.Primitive primitive:occurrence.primitives()) {
                        if(primitive.material.alphaMode()!=GltfAsset.AlphaMode.BLEND)continue;
                        if((long)triangles.size()+primitive.indices.length/3>MAX_BLEND_TRIANGLES)throw new IllegalStateException("P2 transparent sort budget exceeded: "+MAX_BLEND_TRIANGLES+" triangles; draw not submitted");
                        Draw draw=new Draw(primitive,instance.asset().renderType(primitive.material),pose,mirrored,sampleLight(primitive,pose.pose(),camera));
                        for(int offset=0;offset<primitive.indices.length;offset+=3) {
                            int a=primitive.indices[offset]*3,b=primitive.indices[offset+1]*3,c=primitive.indices[offset+2]*3;float[] p=primitive.positions;
                            center.set((p[a]+p[b]+p[c])/3,(p[a+1]+p[b+1]+p[c+1])/3,(p[a+2]+p[b+2]+p[c+2])/3);eye.transformPosition(center);
                            triangles.add(new Triangle(draw,offset,center.z));
                        }
                    }
                }
            }
            // Eye looks down -Z. Stable sort resolves ties in deterministic instance/primitive/triangle order.
            triangles.sort(Comparator.comparingDouble(Triangle::depth));
            var global=RenderSystem.getModelViewStack();global.pushMatrix();
            try {
                global.set(view);
                try(ByteBufferBuilder arena=new ByteBufferBuilder(65536)) {
                    int begin=0;
                    while(begin<triangles.size()) {
                        RenderType type=triangles.get(begin).draw().type();int end=begin+1;
                        while(end<triangles.size()&&end-begin<MAX_RUN_TRIANGLES&&triangles.get(end).draw().type()==type)end++;
                        BufferBuilder builder=new BufferBuilder(arena,VertexFormat.Mode.TRIANGLES,ModelStaticPipelines.FORMAT);
                        for(int i=begin;i<end;i++){Triangle triangle=triangles.get(i);Draw draw=triangle.draw();emitTriangle(draw.primitive(),draw.pose(),draw.mirrored(),draw.light(),triangle.offset(),builder);}
                        MeshData mesh=builder.buildOrThrow();type.draw(mesh);transparentDraws++;transparentTriangles+=end-begin;begin=end;
                    }
                }
            } finally {global.popMatrix();}
        } catch(RuntimeException e){failed(e);}
    }
    private static int sampleLight(ModelRenderAsset.Primitive primitive,Matrix4fc relative,Vec3 camera) {
        var level=Minecraft.getInstance().level;if(level==null)return 0;
        GltfAsset.Bounds b=primitive.bounds;
        Vector3f center=relative.transformPosition((b.minX()+b.maxX())*.5f,(b.minY()+b.maxY())*.5f,(b.minZ()+b.maxZ())*.5f,new Vector3f());
        BlockPos pos=BlockPos.containing(camera.x+center.x,camera.y+center.y,camera.z+center.z);
        return LightCoordsUtil.pack(level.getBrightness(LightLayer.BLOCK,pos),level.getBrightness(LightLayer.SKY,pos));
    }
    private static void emitTriangle(ModelRenderAsset.Primitive primitive,PoseStack.Pose pose,boolean mirrored,int light,int offset,VertexConsumer out) {
        for(int corner=0;corner<3;corner++) {
            int index=primitive.indices[offset+(mirrored&&corner!=0?3-corner:corner)],at=index*3,uv=index*2;
            int u=Float.floatToRawIntBits(primitive.emissionUv[uv]),v=Float.floatToRawIntBits(primitive.emissionUv[uv+1]);
            out.addVertex(pose,primitive.positions[at],primitive.positions[at+1],primitive.positions[at+2])
                    .setColor(primitive.colors[index]).setUv(primitive.baseUv[uv],primitive.baseUv[uv+1])
                    .setUv1(u&65535,u>>>16).setUv2(v&65535,v>>>16).setLineWidth((float)light)
                    .setNormal(pose,primitive.normals[at],primitive.normals[at+1],primitive.normals[at+2]);
        }
    }
    private static void failed(RuntimeException e){String message=e.toString();if(!message.equals(lastFailure))GeometryNode.LOGGER.error("Static glTF renderer stopped affected pass",e);lastFailure=message;}
    public static Map<String,Object> report() {
        return Map.of("backend","P1/P2 native immediate", "visualVerification","UNTESTED", "opaqueSubmissions",opaqueSubmissions,
                "transparentTrianglesTotal",transparentTriangles,"transparentDrawCalls",transparentDraws,"lastFailure",lastFailure,
                "limitations",List.of("No Iris/shaderpack compatibility or shadow claim before P5", "Basic material fallback; metallic/roughness/normal/AO retained for P6", "Light sampled per primitive occurrence; spatial lighting is P4", "BLEND globally triangle-sorted for model geometry; intersecting triangles and vanilla translucent cross-sort unresolved", "Vertex COLOR_0 quantized to native RGBA8", "Native GL samplers required; mip generation decodes sRGB"));
    }
}
