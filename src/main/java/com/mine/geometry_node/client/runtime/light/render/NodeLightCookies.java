package com.mine.geometry_node.client.runtime.light.render;

import com.mine.geometry_node.client.runtime.light.store.ClientLightStore;

import com.mine.geometry_node.client.model.render.geometry.ModelRenderAsset;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.opengl.*;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Render-thread projection atlas ownership, upload retries and fenced retirement. */
final class NodeLightCookies {
    private static final int COOKIE_SIZE = 256;
    private static final GlTexture[] uploadedSources = new GlTexture[NodeLightRenderer.MAX_VISIBLE_LIGHTS];
    private static final GlTexture[] cookieSources = new GlTexture[NodeLightRenderer.MAX_VISIBLE_LIGHTS];
    private static final Identifier[] imageKeys = new Identifier[NodeLightRenderer.MAX_VISIBLE_LIGHTS];
    private static final Map<Identifier, Long> failedImages = new LinkedHashMap<>();
    private static long cookieBytes, cookieRetryAfter;
    private static int cookieTexture, cookieFramebuffer, copyFramebuffer;

    private NodeLightCookies() {}

    static int texture() { return cookieTexture; }
    static long bytes() { return cookieBytes; }
    static int failures() { return failedImages.size(); }
    static float aspect(int index) {
        var source = cookieSources[index];
        return source == null ? 1 : (float) source.getHeight(0) / source.getWidth(0);
    }

    static void clear() {
        clearCookies();
        failedImages.clear();
        cookieRetryAfter = 0;
    }

    static void prepare(List<ClientLightStore.ResolvedLight> lights) {
        if(lights.stream().noneMatch(light -> light.projectionTexture()!=null)) {
            if(cookieTexture!=0)clearCookies();
            cookieRetryAfter=0;
            return;
        }
        if(System.nanoTime()<cookieRetryAfter)throw new IllegalStateException("Node light image allocation is awaiting retry");
        boolean changed=cookieTexture==0;
        for(int i=0;i<lights.size();i++) {
            var key=lights.get(i).projectionTexture();
            if(key==null) {uploadedSources[i]=null;imageKeys[i]=null;cookieSources[i]=null;continue;}
            var texture=Minecraft.getInstance().getTextureManager().getTexture(key).getTexture();
            if(!(texture instanceof GlTexture gl))throw new IllegalStateException("Node light projection requires an OpenGL texture");
            cookieSources[i]=gl;
            // GL may recycle a deleted texture ID after a resource reload.
            changed|=uploadedSources[i]!=gl||!key.equals(imageKeys[i]);
        }
        if(!changed)return;
        int activeTexture=GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
        int binding=GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        int read=GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        int unpack=GL11C.glGetInteger(GL21C.GL_PIXEL_UNPACK_BUFFER_BINDING);
        try {
            if(cookieTexture==0) {
                long required=(long)COOKIE_SIZE*COOKIE_SIZE*NodeLightRenderer.MAX_VISIBLE_LIGHTS*4;
                if(!ModelRenderAsset.residency().reserveTransientGpu(required))throw new IllegalStateException("Node light image GPU budget exhausted");
                cookieBytes=required;
                try {
                cookieTexture=GL11C.glGenTextures();GL11C.glBindTexture(GL11C.GL_TEXTURE_2D,cookieTexture);
                GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER,0);
                GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D,0,GL11C.GL_RGBA8,COOKIE_SIZE,COOKIE_SIZE*NodeLightRenderer.MAX_VISIBLE_LIGHTS,0,GL11C.GL_RGBA,GL11C.GL_UNSIGNED_BYTE,0L);
                GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D,GL11C.GL_TEXTURE_MIN_FILTER,GL11C.GL_LINEAR);
                GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D,GL11C.GL_TEXTURE_MAG_FILTER,GL11C.GL_LINEAR);
                GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D,GL12C.GL_TEXTURE_MAX_LEVEL,0);
                if(cookieTexture==0||GL11C.glGetTexLevelParameteri(GL11C.GL_TEXTURE_2D,0,GL11C.GL_TEXTURE_WIDTH)!=COOKIE_SIZE
                        ||GL11C.glGetTexLevelParameteri(GL11C.GL_TEXTURE_2D,0,GL11C.GL_TEXTURE_HEIGHT)!=COOKIE_SIZE*NodeLightRenderer.MAX_VISIBLE_LIGHTS)
                    throw new IllegalStateException("Node light image texture allocation failed");
                cookieFramebuffer=GL30C.glGenFramebuffers();copyFramebuffer=GL30C.glGenFramebuffers();Arrays.fill(uploadedSources,null);
                Arrays.fill(imageKeys,null);
                if(cookieFramebuffer==0||copyFramebuffer==0)throw new IllegalStateException("Node light image framebuffer allocation failed");
                } catch(RuntimeException exception) {clearCookies();throw exception;}
            }
            for(int i=0;i<lights.size();i++) {
                var light=lights.get(i);if(light.projectionTexture()==null) {uploadedSources[i]=null;imageKeys[i]=null;continue;}
                var key=light.projectionTexture();
                if(System.nanoTime()<failedImages.getOrDefault(key,0L))throw new IllegalStateException("Node light projection awaiting retry: "+key);
                var texture=cookieSources[i];
                int id=texture.glId();
                if(uploadedSources[i]==texture&&key.equals(imageKeys[i]))continue;
                // Blit resamples once when a projection resource changes, never read pixels back.
                int draw=GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
                try {
                    GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER,cookieFramebuffer);
                    GL30C.glFramebufferTexture2D(GL30C.GL_READ_FRAMEBUFFER,GL30C.GL_COLOR_ATTACHMENT0,GL11C.GL_TEXTURE_2D,id,0);
                    GL11C.glReadBuffer(GL30C.GL_COLOR_ATTACHMENT0);
                    GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER,copyFramebuffer);
                    GL30C.glFramebufferTexture2D(GL30C.GL_DRAW_FRAMEBUFFER,GL30C.GL_COLOR_ATTACHMENT0,GL11C.GL_TEXTURE_2D,cookieTexture,0);
                    GL20C.glDrawBuffers(GL30C.GL_COLOR_ATTACHMENT0);
                    if(GL30C.glCheckFramebufferStatus(GL30C.GL_READ_FRAMEBUFFER)!=GL30C.GL_FRAMEBUFFER_COMPLETE
                            ||GL30C.glCheckFramebufferStatus(GL30C.GL_DRAW_FRAMEBUFFER)!=GL30C.GL_FRAMEBUFFER_COMPLETE)
                        throw new IllegalStateException("Node light projection framebuffer is incomplete");
                    boolean scissor=GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST);GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
                    try {GL30C.glBlitFramebuffer(0,0,texture.getWidth(0),texture.getHeight(0),0,i*COOKIE_SIZE,COOKIE_SIZE,(i+1)*COOKIE_SIZE,GL11C.GL_COLOR_BUFFER_BIT,GL11C.GL_LINEAR);}
                    finally {if(scissor)GL11C.glEnable(GL11C.GL_SCISSOR_TEST);}
                    uploadedSources[i]=texture;imageKeys[i]=key;failedImages.remove(key);
                }catch(RuntimeException exception) {
                    failedImages.put(key,System.nanoTime()+1_000_000_000L);
                    while(failedImages.size()>32)failedImages.remove(failedImages.keySet().iterator().next());
                    throw exception;
                }finally {GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER,draw);}
            }
        }catch(RuntimeException exception) {
            cookieRetryAfter=System.nanoTime()+1_000_000_000L;throw exception;
        }finally {
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER,read);
            GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER,unpack);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D,binding);GL13C.glActiveTexture(activeTexture);
        }
    }
    private static void clearCookies() {
        Arrays.fill(uploadedSources,null);Arrays.fill(imageKeys,null);
        Arrays.fill(cookieSources,null);
        int tex=cookieTexture,fbo=cookieFramebuffer,copy=copyFramebuffer;cookieTexture=cookieFramebuffer=copyFramebuffer=0;
        long released=cookieBytes;cookieBytes=0;
        if(tex!=0||fbo!=0||copy!=0||released!=0)RenderSystem.queueFencedTask(()->{
            if(tex!=0)GL11C.glDeleteTextures(tex);if(fbo!=0)GL30C.glDeleteFramebuffers(fbo);
            if(copy!=0)GL30C.glDeleteFramebuffers(copy);
            ModelRenderAsset.residency().releaseTransientGpu(released);
        });
    }
}
