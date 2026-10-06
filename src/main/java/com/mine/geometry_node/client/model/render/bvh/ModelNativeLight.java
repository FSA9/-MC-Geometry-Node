package com.mine.geometry_node.client.model.render.bvh;

import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL33C;
import java.util.HashMap;
import java.util.Map;

/** Native forward receivers share the same bounded light scene without writing shaderpack G-buffers. */
public final class ModelNativeLight {
    private static final int BVH_UNIT=14, WORLD_UNIT=15;
    private static final Map<Integer,int[]> locations=new HashMap<>();
    private static Scope active;
    private static long draws;
    private static String failure="";
    public static String patch(String source) {
        String marker="// GEOMETRY_NODE_NATIVE_LIGHT_DECLARATIONS";
        if(source==null||!source.contains(marker)||source.contains("uniform sampler2D geometry_node_lightBvh;"))return source;
        String declarations=ModelSundialHeldLight.declarations().replace(
                "int modelLightingReceiverSlotAt(vec3 receiver) { return -1; }",
                "uniform int modelLightingNativeReceiver;\nint modelLightingReceiverSlotAt(vec3 receiver) { return modelLightingNativeReceiver - 1; }");
        return source.replace(marker,declarations);
    }
    public static Scope scope() {
        if(active!=null)throw new IllegalStateException("nested native light draw scope");
        return active=new Scope();
    }
    public static final class Scope implements AutoCloseable {
        private int packed, receiver;
        public void light(int packedLight){packed=packedLight;}
        public void receiver(String instanceId){receiver=ModelIrisHeldLight.receiverSlot(instanceId)+1;}
        @Override public void close(){if(active==this)active=null;}
    }
    public static boolean isActive(){return active!=null;}
    public static void deleted(int program){locations.remove(program);}
    public static Map<String,Object> report(){return Map.of("draws",draws,"lastFailure",failure,
            "scope","native forward BLEND receivers; shared model alpha transmission, placed/provider/held sources ; sky lightmap unchanged; native lightmap, no pack refraction/GI");}
    public static void draw(Runnable draw) {
        Scope scope=active;
        if(scope==null){draw.run();return;}
        int program=GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        int[] uniforms=locations.computeIfAbsent(program,p->new int[]{
                GL20C.glGetUniformLocation(p,"geometry_node_nativeActive"),
                GL20C.glGetUniformLocation(p,"geometry_node_nativeLight"),
                GL20C.glGetUniformLocation(p,"geometry_node_lightBvh"),
                GL20C.glGetUniformLocation(p,"geometry_node_blockWorld"),
                GL20C.glGetUniformLocation(p,"modelLightingNativeReceiver")});
        if(uniforms[0]<0){failure="native receiver shader interface unavailable";draw.run();return;}
        int previousActive=GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
        int previousBvh=0,previousWorld=0,bvhSampler=0,worldSampler=0;
        try {
            GL13C.glActiveTexture(GL13C.GL_TEXTURE0+BVH_UNIT);
            previousBvh=GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);bvhSampler=GL11C.glGetInteger(GL33C.GL_SAMPLER_BINDING);
            GL13C.glActiveTexture(GL13C.GL_TEXTURE0+WORLD_UNIT);
            previousWorld=GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);worldSampler=GL11C.glGetInteger(GL33C.GL_SAMPLER_BINDING);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D,ModelPlacedLightField.texture());GL33C.glBindSampler(WORLD_UNIT,0);
            GL13C.glActiveTexture(GL13C.GL_TEXTURE0+BVH_UNIT);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D,ModelIrisHeldLight.nativeTexture());GL33C.glBindSampler(BVH_UNIT,0);
            GL20C.glUniform1i(uniforms[2],BVH_UNIT);GL20C.glUniform1i(uniforms[3],WORLD_UNIT);
            ModelIrisHeldLight.uploadNativeUniforms(program);
            GL20C.glUniform1i(uniforms[4],scope.receiver);
            GL20C.glUniform2f(uniforms[1],(scope.packed&65535)/240f,((scope.packed>>>16)&65535)/240f);
            GL20C.glUniform1i(uniforms[0],1);
            GL13C.glActiveTexture(previousActive);
            draw.run();draws++;failure="";
        } finally {
            GL20C.glUniform1i(uniforms[0],0);
            GL20C.glUniform1i(uniforms[4],0);
            GL13C.glActiveTexture(GL13C.GL_TEXTURE0+WORLD_UNIT);GL11C.glBindTexture(GL11C.GL_TEXTURE_2D,previousWorld);GL33C.glBindSampler(WORLD_UNIT,worldSampler);
            GL13C.glActiveTexture(GL13C.GL_TEXTURE0+BVH_UNIT);GL11C.glBindTexture(GL11C.GL_TEXTURE_2D,previousBvh);GL33C.glBindSampler(BVH_UNIT,bvhSampler);
            GL13C.glActiveTexture(previousActive);
        }
    }
    private ModelNativeLight(){}
}
