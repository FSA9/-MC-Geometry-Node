package com.mine.geometry_node.client.runtime.light.render;

import com.mine.geometry_node.client.runtime.light.diagnostic.NodeLightPixelDiagnostics;
import com.mine.geometry_node.client.runtime.light.shader.NodeLightProgramCache;
import com.mine.geometry_node.client.runtime.light.shader.NodeLightPrograms;
import com.mine.geometry_node.client.runtime.light.shader.NodeLightShader;

import com.mine.geometry_node.client.model.render.bvh.ModelSundialHeldLight;
import com.mine.geometry_node.client.model.render.geometry.ModelRenderAsset;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.*;

import java.util.Map;

/** Native post-lighting pass, sharing pack volume integration and per-light quality. */
public final class NativeVolumeLights {
    private static int program, vao, sourceFbo, copyFbo, outputFbo, color, depth, width, height, depthFormat;
    private static long bytes, draws, frameDraws, retryAfter;
    private static long programBuilds, programBuildNanos, allocationNanos;
    private static String status="IDLE", failure="";
    private static NodeLightRenderer.Interface uniforms;
    private static final Matrix4f projectionMatrix=new Matrix4f();
    private static final Matrix4f inverseMatrix=new Matrix4f();
    private static final float[] matrixData=new float[16];
    private static int colorUniform,depthUniform,projectionUniform,viewUniform;
    private NativeVolumeLights() {}

    public static void setProjection(Matrix4fc projection) {projectionMatrix.set(projection);}

    static String fragmentSource() {
        return NodeLightShader.withVolumeDiagnostics("#version 330 core\n" + ModelSundialHeldLight.declarations() + NodeLightShader.declarations() + """
                uniform sampler2D nativeColor;
                uniform sampler2D nativeDepth;
                uniform mat4 nativeProjectionInverse;
                uniform mat4 gbufferModelViewInverse;
                uniform int nodeTerrainReceiver;
                out vec4 fragColor;
                void main() {
                    vec2 uv=gl_FragCoord.xy/nodeOriginalSize;
                    float depth=texture(nativeDepth,uv).r;
                    vec4 view=nativeProjectionInverse*vec4(uv*2.0-1.0,depth*2.0-1.0,1.0);
                    vec3 viewPosition=abs(view.w)>1e-7?view.xyz/view.w:normalize(view.xyz)*1024.0;
                    vec3 p=(gbufferModelViewInverse*vec4(viewPosition,1.0)).xyz;
                    vec4 scene=texture(nativeColor,uv);
                    vec3 volume=vec3(0.0);
                    if(nodeTerrainReceiver!=0)for(int i=0;i<min(nodeLightCount,8);i++) {
                        if(nodeLights[i*12+6].x<0.5 || nodeLights[i*12+6].y<=0.0 || nodeLights[i*12+6].z<=0.0)continue;
                        volume+=nodeVolumeTransmitted(i,p);
                    }
                    fragColor=vec4(pow(pow(max(scene.rgb,vec3(0.0)),vec3(2.2))+volume,vec3(1.0/2.2)),scene.a);
                }
                """);
    }

    public static void render(Matrix4fc modelView) {
        frameDraws=0;
        if(!NativeTerrainLights.enabled() || !NodeLightRenderer.active()
                || NodeLightRenderer.lights().stream().noneMatch(l -> !l.definition().config().volume().mode().equals("off")
                && l.definition().config().volume().density()>0 && l.definition().config().volume().strength()>0)) {
            status="IDLE";return;
        }
        if(System.nanoTime()<retryAfter)return;
        var target=Minecraft.getInstance().getMainRenderTarget();
        if(!(target.getColorTexture() instanceof GlTexture targetColor) || !(target.getDepthTexture() instanceof GlTexture targetDepth))return;
        try(var state=new State()) {
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D,targetDepth.glId());
            int format=GL11C.glGetTexLevelParameteri(GL11C.GL_TEXTURE_2D,0,GL11C.GL_TEXTURE_INTERNAL_FORMAT);
            ensure(targetColor.getWidth(0),targetColor.getHeight(0),format);
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER,sourceFbo);
            GL30C.glFramebufferTexture2D(GL30C.GL_READ_FRAMEBUFFER,GL30C.GL_COLOR_ATTACHMENT0,GL11C.GL_TEXTURE_2D,targetColor.glId(),0);
            GL30C.glFramebufferTexture2D(GL30C.GL_READ_FRAMEBUFFER,GL30C.GL_DEPTH_ATTACHMENT,GL11C.GL_TEXTURE_2D,targetDepth.glId(),0);
            GL11C.glReadBuffer(GL30C.GL_COLOR_ATTACHMENT0);
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER,copyFbo);
            GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
            int copyTimer=com.mine.geometry_node.client.model.render.diagnostic.ModelShaderGpuTimings.begin("node_light_native_snapshot");
            try {GL30C.glBlitFramebuffer(0,0,width,height,0,0,width,height,GL11C.GL_COLOR_BUFFER_BIT|GL11C.GL_DEPTH_BUFFER_BIT,GL11C.GL_NEAREST);}
            finally {com.mine.geometry_node.client.model.render.diagnostic.ModelShaderGpuTimings.end(copyTimer);}
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER,outputFbo);
            GL30C.glFramebufferTexture2D(GL30C.GL_DRAW_FRAMEBUFFER,GL30C.GL_COLOR_ATTACHMENT0,GL11C.GL_TEXTURE_2D,targetColor.glId(),0);
            GL20C.glDrawBuffers(GL30C.GL_COLOR_ATTACHMENT0);
            GL11C.glViewport(0,0,width,height);
            GL20C.glUseProgram(program);GL30C.glBindVertexArray(vao);
            GL11C.glDisable(GL11C.GL_DEPTH_TEST);GL11C.glDisable(GL11C.GL_STENCIL_TEST);
            GL11C.glDisable(GL11C.GL_CULL_FACE);GL11C.glDisable(GL30C.GL_FRAMEBUFFER_SRGB);
            GL30C.glDisablei(GL11C.GL_BLEND,0);GL30C.glColorMaski(0,true,true,true,true);
            bind(colorUniform,0,color);bind(depthUniform,1,depth);
            GL20C.glUniform1i(uniforms.volume,10);
            GL20C.glUniformMatrix4fv(projectionUniform,false,inverseMatrix.set(projectionMatrix).invert().get(matrixData));
            GL20C.glUniformMatrix4fv(viewUniform,false,inverseMatrix.set(modelView).invert().get(matrixData));
            NativeTerrainLights.beginVolume();
            try {
                NodeLightTargets.draw(program,uniforms,()->{
                    if(uniforms.pass>=0 && GL20C.glGetUniformi(program,uniforms.pass)!=0 || NodeLightPixelDiagnostics.isDrawing()) {GL11C.glDrawArrays(GL11C.GL_TRIANGLES,0,3);return;}
                    int timer=com.mine.geometry_node.client.model.render.diagnostic.ModelShaderGpuTimings.begin("node_light_native_composite");
                    try {GL11C.glDrawArrays(GL11C.GL_TRIANGLES,0,3);}
                    finally {com.mine.geometry_node.client.model.render.diagnostic.ModelShaderGpuTimings.end(timer);}
                });
                draws++;frameDraws++;status="ACTIVE";failure="";
            } finally {NativeTerrainLights.end();}
        } catch(RuntimeException error) {
            failure=error.toString();status="FAILED";retryAfter=System.nanoTime()+1_000_000_000L;
        }
    }

    private static void bind(int location,int unit,int texture) {
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0+unit);GL11C.glBindTexture(GL11C.GL_TEXTURE_2D,texture);GL33C.glBindSampler(unit,0);
        GL20C.glUniform1i(location,unit);
    }

    private static void ensure(int w,int h,int format) {
        ensureProgram();
        if(color!=0&&width==w&&height==h&&depthFormat==format)return;
        clear();
        long allocationStarted=System.nanoTime();
        long required=(long)w*h*8;
        if(!ModelRenderAsset.residency().reserveTransientGpu(required))throw new IllegalStateException("Native volume snapshot GPU budget exhausted");
        bytes=required;width=w;height=h;depthFormat=format;
        try {
            sourceFbo=GL30C.glGenFramebuffers();copyFbo=GL30C.glGenFramebuffers();outputFbo=GL30C.glGenFramebuffers();
            color=texture(GL11C.GL_RGBA8,GL11C.GL_RGBA,GL11C.GL_UNSIGNED_BYTE);
            depth=texture(format,GL11C.GL_DEPTH_COMPONENT,GL11C.GL_FLOAT);
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER,copyFbo);
            GL30C.glFramebufferTexture2D(GL30C.GL_DRAW_FRAMEBUFFER,GL30C.GL_COLOR_ATTACHMENT0,GL11C.GL_TEXTURE_2D,color,0);
            GL30C.glFramebufferTexture2D(GL30C.GL_DRAW_FRAMEBUFFER,GL30C.GL_DEPTH_ATTACHMENT,GL11C.GL_TEXTURE_2D,depth,0);
            GL20C.glDrawBuffers(GL30C.GL_COLOR_ATTACHMENT0);
            if(GL30C.glCheckFramebufferStatus(GL30C.GL_DRAW_FRAMEBUFFER)!=GL30C.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Native volume snapshot framebuffer incomplete");
        } catch(RuntimeException error) {clear();throw error;}
        finally {allocationNanos=System.nanoTime()-allocationStarted;}
    }

    private static void ensureProgram() {
        if(program!=0)return;
        long started=System.nanoTime();
        try {
            String vertexSource="#version 330 core\nvoid main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2.0-1.0,0,1);}";
            String fragmentSource=fragmentSource();
            var linked=NodeLightPrograms.link(vertexSource,fragmentSource);
            program=linked.id();
            if(!linked.cached())programBuilds++;
            uniforms=new NodeLightRenderer.Interface(program);
            colorUniform=GL20C.glGetUniformLocation(program,"nativeColor");
            depthUniform=GL20C.glGetUniformLocation(program,"nativeDepth");
            projectionUniform=GL20C.glGetUniformLocation(program,"nativeProjectionInverse");
            viewUniform=GL20C.glGetUniformLocation(program,"gbufferModelViewInverse");
            vao=GL30C.glGenVertexArrays();
        } catch(RuntimeException error) {close();throw error;}
        finally {programBuildNanos=System.nanoTime()-started;}
    }

    private static int texture(int format,int external,int type) {
        int id=GL11C.glGenTextures();
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D,id);
        GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER,0);
        GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D,0,format,width,height,0,external,type,0L);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D,GL11C.GL_TEXTURE_MIN_FILTER,GL11C.GL_NEAREST);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D,GL11C.GL_TEXTURE_MAG_FILTER,GL11C.GL_NEAREST);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D,GL12C.GL_TEXTURE_MAX_LEVEL,0);
        return id;
    }

    public static void clear() {
        int s=sourceFbo,c=copyFbo,o=outputFbo,a=color,b=depth;long freed=bytes;
        sourceFbo=copyFbo=outputFbo=color=depth=width=height=0;bytes=0;retryAfter=0;
        status="IDLE";frameDraws=0;
        if(s!=0||c!=0||o!=0||a!=0||b!=0||freed!=0)RenderSystem.queueFencedTask(()->{
            GL30C.glDeleteFramebuffers(s);GL30C.glDeleteFramebuffers(c);GL30C.glDeleteFramebuffers(o);
            GL11C.glDeleteTextures(a);GL11C.glDeleteTextures(b);ModelRenderAsset.residency().releaseTransientGpu(freed);
        });
    }

    /** The shader is independent of world, shaderpack, and framebuffer dimensions. */
    public static void close() {
        NodeLightFroxels.close();
        clear();
        int p=program,v=vao;program=vao=0;uniforms=null;
        if(p!=0)com.mine.geometry_node.client.model.render.bvh.ModelIrisHeldLight.deleted(p);
        if(p!=0||v!=0)RenderSystem.queueFencedTask(()->{GL20C.glDeleteProgram(p);GL30C.glDeleteVertexArrays(v);});
    }

    public static Map<String,Object> report() {
        var report=new java.util.LinkedHashMap<String,Object>();
        report.put("status",status);report.put("failure",failure);report.put("draws",draws);report.put("frameDraws",frameDraws);
        report.put("gpuBytes",bytes);report.put("width",width);report.put("height",height);
        report.put("programBuilds",programBuilds);report.put("programBuildMs",programBuildNanos/1_000_000.0);report.put("allocationMs",allocationNanos/1_000_000.0);
        report.put("programCache",NodeLightProgramCache.report());
        return report;
    }

    static final class State implements AutoCloseable {
        private final int active=GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE),program=GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM),vao=GL11C.glGetInteger(GL30C.GL_VERTEX_ARRAY_BINDING);
        private final int read=GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING),draw=GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING),unpack=GL11C.glGetInteger(GL21C.GL_PIXEL_UNPACK_BUFFER_BINDING);
        private final int[] viewport=new int[4],units={0,1,10},textures=new int[3],samplers=new int[3];
        private final int currentTexture=GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        private final int[] caps={GL11C.GL_DEPTH_TEST,GL11C.GL_STENCIL_TEST,GL11C.GL_CULL_FACE,GL11C.GL_SCISSOR_TEST,GL30C.GL_FRAMEBUFFER_SRGB};
        private final boolean[] enabled=new boolean[caps.length];
        private final boolean blend=GL30C.glIsEnabledi(GL11C.GL_BLEND,0);
        private final byte[] mask=new byte[4];
        State() {
            GL11C.glGetIntegerv(GL11C.GL_VIEWPORT,viewport);
            for(int i=0;i<caps.length;i++)enabled[i]=GL11C.glIsEnabled(caps[i]);
            try(var stack=org.lwjgl.system.MemoryStack.stackPush()){var buffer=stack.malloc(4);GL30C.glGetBooleani_v(GL11C.GL_COLOR_WRITEMASK,0,buffer);buffer.get(mask);}
            for(int i=0;i<units.length;i++){GL13C.glActiveTexture(GL13C.GL_TEXTURE0+units[i]);textures[i]=GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);samplers[i]=GL30C.glGetIntegeri(GL33C.GL_SAMPLER_BINDING,units[i]);}
            GL13C.glActiveTexture(active);
        }
        public void close() {
            GL20C.glUseProgram(program);GL30C.glBindVertexArray(vao);
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER,read);GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER,draw);
            GL11C.glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);
            for(int i=0;i<caps.length;i++){if(enabled[i])GL11C.glEnable(caps[i]);else GL11C.glDisable(caps[i]);}
            if(blend)GL30C.glEnablei(GL11C.GL_BLEND,0);else GL30C.glDisablei(GL11C.GL_BLEND,0);
            GL30C.glColorMaski(0,mask[0]!=0,mask[1]!=0,mask[2]!=0,mask[3]!=0);
            GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER,unpack);
            GL13C.glActiveTexture(active);GL11C.glBindTexture(GL11C.GL_TEXTURE_2D,currentTexture);
            for(int i=0;i<units.length;i++){GL13C.glActiveTexture(GL13C.GL_TEXTURE0+units[i]);GL11C.glBindTexture(GL11C.GL_TEXTURE_2D,textures[i]);GL33C.glBindSampler(units[i],samplers[i]);}
            GL13C.glActiveTexture(active);
        }
    }
}
