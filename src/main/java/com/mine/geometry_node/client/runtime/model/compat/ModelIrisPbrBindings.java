package com.mine.geometry_node.client.runtime.model.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.renderer.texture.AbstractTexture;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
import com.mojang.blaze3d.opengl.GlSampler;
import com.mojang.blaze3d.opengl.GlProgram;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.util.IdentityHashMap;
import java.util.Map;

/** Asset-owned PBR attachments. Iris borrows these textures; its global holder cache never owns them. */
public final class ModelIrisPbrBindings {
    public static final class Binding {
        private final AbstractTexture normal, specular;
        // Borrowed wrappers carry existing sampler IDs; OwnedTexture alone releases those IDs.
        private Class<?> samplerType;
        private Object normalSampler, specularSampler;
        private Binding(AbstractTexture normal, AbstractTexture specular) { this.normal=normal; this.specular=specular; }
        public AbstractTexture normal() { return normal; }
        public AbstractTexture specular() { return specular; }
    }
    private static final Map<GpuTexture, Binding> BINDINGS = new IdentityHashMap<>();
    private static long binds;
    private static String lastFailure = "";
    private static Method notifier;
    private static boolean notifierResolved;

    /** Keep the lease until the asset's GPU retirement fence, then close before disposing textures. */
    public static AutoCloseable register(GpuTexture albedo, AbstractTexture normal, AbstractTexture specular) {
        RenderSystem.assertOnRenderThread();
        var binding = new Binding(java.util.Objects.requireNonNull(normal), java.util.Objects.requireNonNull(specular));
        if (BINDINGS.putIfAbsent(java.util.Objects.requireNonNull(albedo), binding) != null)
            throw new IllegalStateException("PBR albedo must have a unique material owner");
        return () -> { RenderSystem.assertOnRenderThread(); BINDINGS.remove(albedo, binding); };
    }
    /** Atomically replace borrowed attachments; closing the previous lease cannot remove this generation. */
    public static AutoCloseable replace(GpuTexture albedo, AbstractTexture normal, AbstractTexture specular) {
        RenderSystem.assertOnRenderThread();
        var binding=new Binding(java.util.Objects.requireNonNull(normal),java.util.Objects.requireNonNull(specular));
        if(!BINDINGS.containsKey(albedo))throw new IllegalStateException("material PBR binding has retired");
        BINDINGS.put(albedo,binding);
        return () -> { RenderSystem.assertOnRenderThread(); BINDINGS.remove(albedo,binding); };
    }
    public static boolean requiresDrawTracking() { return !BINDINGS.isEmpty() || previousModelDraw; }
    public static Binding find(GpuTexture texture) { return BINDINGS.get(texture); }
    public static void changed() {
        try {
            if (!notifierResolved) {
                notifierResolved = true;
                notifier = Class.forName("net.irisshaders.iris.pbr.texture.PBRTextureManager").getMethod("notifyPBRTexturesChanged");
            }
            if (notifier != null) { notifier.invoke(null); binds++; }
        } catch (ReflectiveOperationException | LinkageError failure) { lastFailure = failure.toString(); }
    }
    private record SamplerAccess(Field normal, Field specular, Constructor<?> borrowed) {}
    private static final ClassValue<SamplerAccess> SAMPLERS = new ClassValue<>() {
        @Override protected SamplerAccess computeValue(Class<?> type) {
            try {
                Field normal=type.getDeclaredField("normalSampler"), specular=type.getDeclaredField("specularSampler");
                if(normal.getType()!=specular.getType() || !normal.trySetAccessible() || !specular.trySetAccessible())
                    throw new IllegalStateException("Iris PBR sampler fields are incompatible");
                Class<?> samplerType=normal.getType();
                Constructor<?> borrowed;
                if(samplerType==GlSampler.class) borrowed=null;
                else if(samplerType.getName().equals("net.irisshaders.iris.gl.sampler.GlSampler")) borrowed=samplerType.getConstructor(int.class);
                else throw new IllegalStateException("Unsupported Iris sampler ABI: "+samplerType.getName());
                return new SamplerAccess(normal,specular,borrowed);
            } catch(ReflectiveOperationException | RuntimeException failure) { lastFailure=failure.toString(); return null; }
        }
    };
    /** Resolve the installed Iris ABI, without a mandatory Iris dependency or mismatched @Shadow descriptors. */
    public static boolean applySamplers(Object pipeline, Binding binding) {
        var access=SAMPLERS.get(pipeline.getClass());
        if(access==null)return false;
        if(!(binding.normal.getSampler() instanceof GlSampler normal) || !(binding.specular.getSampler() instanceof GlSampler specular)) {
            lastFailure="PBR requires OpenGL-backed model samplers"; return false;
        }
        try {
            if(binding.samplerType!=access.normal.getType()) {
                Object borrowedNormal=access.borrowed==null?normal:access.borrowed.newInstance(normal.getId());
                Object borrowedSpecular=access.borrowed==null?specular:access.borrowed.newInstance(specular.getId());
                binding.normalSampler=borrowedNormal; binding.specularSampler=borrowedSpecular; binding.samplerType=access.normal.getType();
            }
            access.normal.set(pipeline,binding.normalSampler);
            access.specular.set(pipeline,binding.specularSampler);
            return true;
        } catch(ReflectiveOperationException | RuntimeException failure) { lastFailure=failure.toString(); return false; }
    }
    private record PipelineAccess(Field pipeline, Method albedo) {}
    private static final ClassValue<PipelineAccess> PIPELINES = new ClassValue<>() {
        @Override protected PipelineAccess computeValue(Class<?> type) {
            for(Class<?> c=type;c!=null;c=c.getSuperclass()) {
                if(!c.getName().equals("net.irisshaders.iris.pipeline.programs.ExtendedShader"))continue;
                try {
                    Field f=c.getDeclaredField("pipeline");
                    if(!f.trySetAccessible())return null;
                    return new PipelineAccess(f,f.getType().getMethod("onSetAlbedoTex",GpuTextureView.class));
                }catch(ReflectiveOperationException | RuntimeException failure){lastFailure=failure.toString();return null;}
            }
            return null;
        }
    };
    private static final class MaterialSamplerUniform {
        int location = -2;
        int value = -1;
    }
    private static final Map<Integer, MaterialSamplerUniform> MATERIAL_SAMPLER_UNIFORMS = new java.util.concurrent.ConcurrentHashMap<>();
    public static void linked(int program, String fragment) {
        if(com.mine.geometry_node.client.runtime.model.render.pipeline.ModelSundialMaterialSampling.patched(fragment)) MATERIAL_SAMPLER_UNIFORMS.put(program,new MaterialSamplerUniform());
        else MATERIAL_SAMPLER_UNIFORMS.remove(program);
    }
    public static void deleted(int program) {
        MATERIAL_SAMPLER_UNIFORMS.remove(program);
        // A shader reload can delete the program while it is still the cached
        // material in the current Iris pass.  Forget it immediately so the next
        // host draw cannot be mistaken for a continuation of the model draw.
        if (lastProgram != null && lastProgram.getProgramId() == program) {
            previousModelDraw = false;
            lastProgram = null;
            lastAlbedo = null;
            lastBinding = null;
        }
    }
    private static void materialSampler(GlProgram program, boolean model) {
        int id=program.getProgramId();
        var uniform=MATERIAL_SAMPLER_UNIFORMS.get(id);
        if(uniform==null)return;
        if(uniform.location==-2) {
            uniform.location=org.lwjgl.opengl.GL20C.glGetUniformLocation(id,com.mine.geometry_node.client.runtime.model.render.pipeline.ModelSundialMaterialSampling.UNIFORM);
        }
        int value=model?1:0;
        // This private uniform has no other writer; only model/host transitions need a GL call.
        if(uniform.location>=0 && uniform.value!=value) {
            org.lwjgl.opengl.GL20C.glUniform1i(uniform.location,value);
            uniform.value=value;
        }
    }
    private static boolean previousModelDraw;
    private static GlProgram lastProgram;
    private static GpuTexture lastAlbedo;
    private static Binding lastBinding;
    /** Iris initializes a program once per host pass. Rebind per material, including restoration on the next vanilla draw. */
    public static void beforeDraw(GlProgram program,GpuTextureView albedo) {
        var binding=albedo==null?null:BINDINGS.get(albedo.texture());
        boolean model=binding!=null;
        materialSampler(program,model);
        if(model && program==lastProgram && albedo.texture()==lastAlbedo && binding==lastBinding)return;
        if(!model && !previousModelDraw)return;
        var access=PIPELINES.get(program.getClass());
        // A host pass can switch to a program that does not expose Iris's private
        // pipeline ABI.  Drop our bookkeeping before returning so a failed restore
        // cannot make a later vanilla draw inherit model PBR attachments.
        if(access==null) {
            previousModelDraw=false;
            lastProgram=null; lastAlbedo=null; lastBinding=null;
            return;
        }
        try { access.albedo.invoke(access.pipeline.get(program),albedo);
            previousModelDraw=model;
            lastProgram=model?program:null; lastAlbedo=model?albedo.texture():null; lastBinding=binding;
        }
        catch(ReflectiveOperationException | RuntimeException failure){
            previousModelDraw=false;
            lastProgram=null; lastAlbedo=null; lastBinding=null;
            lastFailure=failure.toString();
        }
    }
    public static Map<String,Object> snapshot() {
        return Map.of("registeredMaterials", BINDINGS.size(), "samplerRebindings", binds,
                "protocol", "LabPBR 1.3", "visualVerification", "UNTESTED", "lastFailure", lastFailure);
    }
    private ModelIrisPbrBindings() {}
}
