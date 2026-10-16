package com.mine.geometry_node.client.runtime.light.render;

import com.mine.geometry_node.client.runtime.light.diagnostic.LightReports;
import com.mine.geometry_node.client.runtime.light.diagnostic.NodeLightPixelDiagnostics;

import com.mine.geometry_node.client.model.render.diagnostic.ModelShaderGpuTimings;
import com.mine.geometry_node.client.model.render.geometry.ModelRenderAsset;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Map;
import org.lwjgl.opengl.*;

/** Surface atlas and froxel receiver bindings; volume generation owns independent programs. */
final class NodeLightTargets {
    private static int surface, volume, framebuffer, width, height;
    private static long bytes, draws;
    private static final float[] surfaceRects = new float[32];
    static int surfaceTexture() { return surface; }
    static int volumeTexture() { return volume; }
    static void retiredVolume(int texture) {if(volume==texture)volume=0;}

    static void draw(int program, NodeLightRenderer.Interface u, Runnable draw) {
        int[] viewport=new int[4];GL11C.glGetIntegerv(GL11C.GL_VIEWPORT,viewport);
        GL20C.glUniform2f(u.originalSize,viewport[2],viewport[3]);
        volume=NodeLightFroxels.draw(program,viewport[2],viewport[3]);
        int active=GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
        int[] units={unit(program,u.surface),unit(program,u.volume),unit(program,u.cookies)};
        int[] textures=new int[3],samplers=new int[3];
        for(int i=0;i<3;i++)if(units[i]>=0){GL13C.glActiveTexture(GL13C.GL_TEXTURE0+units[i]);textures[i]=GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);samplers[i]=GL30C.glGetIntegeri(GL33C.GL_SAMPLER_BINDING,units[i]);}
        try {
            int w=1,h=0;
            if(units[0]>=0)for(var light:NodeLightRenderer.lights()) {
                float scale=light.definition().config().quality().shadowResolution();if(scale>=1)continue;
                w=Math.max(w,(int)Math.ceil(viewport[2]*scale));h+=Math.max(1,(int)Math.ceil(viewport[3]*scale));
            }
            for(int i=0;i<3;i++)if(units[i]>=0){GL13C.glActiveTexture(GL13C.GL_TEXTURE0+units[i]);GL33C.glBindSampler(units[i],0);GL11C.glBindTexture(GL11C.GL_TEXTURE_2D,i==2?NodeLightRenderer.cookies():i==1?volume:0);}
            java.util.Arrays.fill(surfaceRects,0);
            if(h>0) {
                ensure(w,h);
                int fbo=GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
                boolean blend=GL30C.glIsEnabledi(GL11C.GL_BLEND,0),depth=GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST),
                        stencil=GL11C.glIsEnabled(GL11C.GL_STENCIL_TEST),scissor=GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST);
                byte[] mask=new byte[4];
                try(var stack=org.lwjgl.system.MemoryStack.stackPush()){var b=stack.malloc(4);GL30C.glGetBooleani_v(GL11C.GL_COLOR_WRITEMASK,0,b);b.get(mask);}
                try {
                    GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER,framebuffer);
                    GL20C.glDrawBuffers(GL30C.GL_COLOR_ATTACHMENT0);
                    GL30C.glDisablei(GL11C.GL_BLEND,0);GL30C.glColorMaski(0,true,true,true,true);
                    GL11C.glDisable(GL11C.GL_DEPTH_TEST);GL11C.glDisable(GL11C.GL_STENCIL_TEST);GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
                    int y=0;
                    for(int i=0;i<NodeLightRenderer.lights().size();i++) {
                        float scale=NodeLightRenderer.lights().get(i).definition().config().quality().shadowResolution();if(scale>=1)continue;
                        int pw=Math.max(1,(int)Math.ceil(viewport[2]*scale)),ph=Math.max(1,(int)Math.ceil(viewport[3]*scale));
                        surfaceRects[i*4]=(float)pw/width;surfaceRects[i*4+1]=(float)ph/height;surfaceRects[i*4+3]=(float)y/height;
                        GL11C.glViewport(0,y,pw,ph);GL20C.glUniform4f(u.viewport,0,y,pw,ph);
                        GL20C.glUniform1i(u.index,i);GL20C.glUniform1i(u.pass,1);
                        int timer=ModelShaderGpuTimings.begin("node_light_surface");
                        try{draw.run();draws++;}finally{ModelShaderGpuTimings.end(timer);}
                        LightReports.submitted(program,1,i,pw,ph);y+=ph;
                    }
                } finally {
                    GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER,fbo);GL11C.glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);
                    toggle(GL11C.GL_DEPTH_TEST,depth);toggle(GL11C.GL_STENCIL_TEST,stencil);toggle(GL11C.GL_SCISSOR_TEST,scissor);
                    if(blend)GL30C.glEnablei(GL11C.GL_BLEND,0);else GL30C.glDisablei(GL11C.GL_BLEND,0);
                    GL30C.glColorMaski(0,mask[0]!=0,mask[1]!=0,mask[2]!=0,mask[3]!=0);GL20C.glUniform1i(u.pass,0);
                }
            }
            if(units[0]>=0){GL13C.glActiveTexture(GL13C.GL_TEXTURE0+units[0]);GL11C.glBindTexture(GL11C.GL_TEXTURE_2D,surface);}
            GL20C.glUniform4fv(u.surfaceRects,surfaceRects);GL20C.glUniform1i(u.resolved,1);GL13C.glActiveTexture(active);
            if(LightReports.probePixels(program,0,-1))NodeLightPixelDiagnostics.draw(program,0,-1,viewport[2],viewport[3],draw);
            draw.run();LightReports.submitted(program,0,-1,viewport[2],viewport[3]);
        } finally {
            GL20C.glUniform1i(u.resolved,0);
            for(int i=0;i<3;i++)if(units[i]>=0){GL13C.glActiveTexture(GL13C.GL_TEXTURE0+units[i]);GL11C.glBindTexture(GL11C.GL_TEXTURE_2D,textures[i]);GL33C.glBindSampler(units[i],samplers[i]);}
            GL13C.glActiveTexture(active);
        }
    }
    private static int unit(int p,int location){return location<0?-1:GL20C.glGetUniformi(p,location);}
    private static void toggle(int cap,boolean enabled){if(enabled)GL11C.glEnable(cap);else GL11C.glDisable(cap);}
    private static void ensure(int w,int h) {
        if(surface!=0&&w==width&&h==height)return;
        long required=(long)w*h*8;int limit=GL11C.glGetInteger(GL11C.GL_MAX_TEXTURE_SIZE);
        if(w>limit||h>limit||required>128L*1024*1024)throw new IllegalStateException("Surface atlas exceeds budget");
        if(!ModelRenderAsset.residency().reserveTransientGpu(required))throw new IllegalStateException("Surface atlas GPU budget exhausted");
        releaseSurface();bytes=required;width=w;height=h;
        int old=GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D),unpack=GL11C.glGetInteger(GL21C.GL_PIXEL_UNPACK_BUFFER_BINDING);
        int fbo=GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER,0);surface=GL11C.glGenTextures();framebuffer=GL30C.glGenFramebuffers();
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D,surface);
            GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D,0,GL30C.GL_RGBA16F,w,h,0,GL11C.GL_RGBA,GL11C.GL_FLOAT,0L);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D,GL11C.GL_TEXTURE_MIN_FILTER,GL11C.GL_NEAREST);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D,GL11C.GL_TEXTURE_MAG_FILTER,GL11C.GL_NEAREST);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D,GL12C.GL_TEXTURE_MAX_LEVEL,0);
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER,framebuffer);
            GL30C.glFramebufferTexture2D(GL30C.GL_DRAW_FRAMEBUFFER,GL30C.GL_COLOR_ATTACHMENT0,GL11C.GL_TEXTURE_2D,surface,0);
            GL20C.glDrawBuffers(GL30C.GL_COLOR_ATTACHMENT0);
            if(GL30C.glCheckFramebufferStatus(GL30C.GL_DRAW_FRAMEBUFFER)!=GL30C.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Incomplete surface atlas");
        } catch(RuntimeException error){releaseSurface();throw error;}
        finally{GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER,fbo);GL11C.glBindTexture(GL11C.GL_TEXTURE_2D,old);GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER,unpack);}
    }
    private static void releaseSurface() {
        int texture=surface,fbo=framebuffer;long retired=bytes;surface=framebuffer=width=height=0;bytes=0;
        if(retired!=0)RenderSystem.queueFencedTask(()->{GL11C.glDeleteTextures(texture);GL30C.glDeleteFramebuffers(fbo);ModelRenderAsset.residency().releaseTransientGpu(retired);});
    }
    static void clear(){releaseSurface();volume=0;NodeLightFroxels.clear();}
    static Map<String,Object> report(){return Map.of("gpuBytes",bytes,"width",width,"height",height,"draws",draws,"volumeArchitecture","per_light_froxel_prefix");}
}
