package com.mine.geometry_node.client.runtime.light;

import com.mine.geometry_node.client.model.render.geometry.ModelRenderAsset;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.*;
import java.util.Map;

/** Per-light viewports in two atlases. All raw GL state is restored before the pack draw. */
final class NodeLightTargets {
    private static int surface,volume,framebuffer,width,height;
    private static long bytes,draws;
    private static int failedWidth,failedHeight;
    private static long retryAfter;
    private static long segmentRetryAfter;
    private static boolean segmentAtlasEnabled;
    private static String segmentFallback="";
    private static String allocationFailure="";
    private static final float[] surfaceRects=new float[32],volumeRects=new float[32];
    private static final float[] volumeSegmentRects=new float[32],volumeSegmentDepthRects=new float[32];
    static int surfaceTexture(){return surface;}
    static int volumeTexture(){return volume;}
    static void draw(int program,NodeLightRenderer.Interface u,Runnable draw) {
        int[] vp=new int[4];GL11C.glGetIntegerv(GL11C.GL_VIEWPORT,vp);
        int requiredWidth=1,requiredHeight=0;
        boolean needsPrepass=false;
        for(var l:NodeLightRenderer.lights()) {
            var q=l.definition().config().quality();
            var v=l.definition().config().volume();
            boolean volumeEnabled=!v.mode().equals("off")&&v.strength()>0&&v.density()>0;
            needsPrepass|=(u.surface>=0&&q.shadowResolution()<1)||(u.volume>=0&&volumeEnabled);
            float scale=Math.max(q.shadowResolution()<1?q.shadowResolution():0,volumeEnabled?q.volumeResolution():0);
            if(scale==0)continue;
            requiredWidth=Math.max(requiredWidth,(int)Math.ceil(vp[2]*scale));
            requiredHeight+=Math.max(1,(int)Math.ceil(vp[3]*scale));
        }
        int layerHeight=requiredHeight;
        // All registered receivers use the same allocation to avoid resizing between pack passes.
        boolean requestedSegments=NodeLightRenderer.hasVolumeSegments();
        boolean segmentAtlas=requestedSegments
                && System.nanoTime()>=segmentRetryAfter
                && requiredHeight*3<=GL11C.glGetInteger(GL11C.GL_MAX_TEXTURE_SIZE)
                && (long)requiredWidth*requiredHeight*3*16<=256L*1024*1024;
        if(segmentAtlas)segmentFallback="";
        else if(requestedSegments&&System.nanoTime()>=segmentRetryAfter)segmentFallback="texture_extent_or_256MiB_budget";
        if(segmentAtlas)requiredHeight*=3;
        java.util.Arrays.fill(volumeSegmentRects,0);
        java.util.Arrays.fill(volumeSegmentDepthRects,0);
        // Keep a common atlas layout across receivers, but allocate only for a pass that uses it.
        if(needsPrepass) {
            try {ensure(requiredWidth,requiredHeight);}
            catch(RuntimeException exception) {
                if(!segmentAtlas)throw exception;
                // Optional background caching must not disable lights when residency is tight.
                segmentAtlas=false;segmentRetryAfter=System.nanoTime()+5_000_000_000L;retryAfter=0;
                segmentFallback=exception.toString();
                ensure(requiredWidth,layerHeight);
            }
        } else if(requiredHeight==0&&bytes>0)clear();
        segmentAtlasEnabled=needsPrepass&&segmentAtlas;
        GL20C.glUniform2f(u.originalSize,vp[2],vp[3]);
        if(!needsPrepass) {
            drawDirect(program,u,()->{
                if (LightReports.probePixels(program,0,-1))
                    NodeLightPixelDiagnostics.draw(program,0,-1,vp[2],vp[3],draw);
                draw.run();
            });
            LightReports.submitted(program,0,-1,vp[2],vp[3]);
            return;
        }
        int active=GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE),fbo=GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] units={unit(program,u.surface),unit(program,u.volume),unit(program,u.cookies)};
        int[] textures=new int[3],samplers=new int[3];
        boolean blend=GL30C.glIsEnabledi(GL11C.GL_BLEND,0),depth=GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST),stencil=GL11C.glIsEnabled(GL11C.GL_STENCIL_TEST),scissor=GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST);
        byte[] mask=new byte[4];
        try(var stack=org.lwjgl.system.MemoryStack.stackPush()) {var buffer=stack.malloc(4);GL30C.glGetBooleani_v(GL11C.GL_COLOR_WRITEMASK,0,buffer);buffer.get(mask);}
        for(int i=0;i<3;i++) {if(units[i]<0)continue;GL13C.glActiveTexture(GL13C.GL_TEXTURE0+units[i]);textures[i]=GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);samplers[i]=GL30C.glGetIntegeri(GL33C.GL_SAMPLER_BINDING,units[i]);}
        try {
        for(int i=0;i<3;i++) {if(units[i]<0)continue;GL13C.glActiveTexture(GL13C.GL_TEXTURE0+units[i]);GL33C.glBindSampler(units[i],0);GL11C.glBindTexture(GL11C.GL_TEXTURE_2D,i==2?NodeLightRenderer.cookies():0);}
        try {
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER,framebuffer);
            GL20C.glDrawBuffers(GL30C.GL_COLOR_ATTACHMENT0);
            GL30C.glDisablei(GL11C.GL_BLEND,0);GL30C.glColorMaski(0,true,true,true,true);
            GL11C.glDisable(GL11C.GL_DEPTH_TEST);GL11C.glDisable(GL11C.GL_STENCIL_TEST);GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
            int y=0,attachedTexture=0;
            for(int i=0;i<NodeLightRenderer.lights().size();i++) {
                var c=NodeLightRenderer.lights().get(i).definition().config();var q=c.quality();
                GL20C.glUniform1i(u.index,i);
                for(int pass=1;pass<=(segmentAtlas&&u.volumeSegmentRects>=0?4:2);pass++) {
                    if(units[pass==1?0:1]<0)continue;
                    if(pass==1&&q.shadowResolution()==1)continue;
                    float scale=pass==1?q.shadowResolution():q.volumeResolution();
                    int w=Math.max(1,(int)Math.ceil(vp[2]*scale)),h=Math.max(1,(int)Math.ceil(vp[3]*scale));
                    float[] rect=pass==1?surfaceRects:pass==2?volumeRects:pass==3?volumeSegmentRects:volumeSegmentDepthRects;
                    int passY=y+(pass>=3?(pass-2)*layerHeight:0);
                    rect[i*4]=(float)w/width;rect[i*4+1]=(float)h/height;rect[i*4+2]=0;rect[i*4+3]=(float)passY/height;
                    if(pass>=2&&(c.volume().mode().equals("off")||c.volume().strength()<=0||c.volume().density()<=0))continue;
                    int targetTexture=pass==1?surface:volume;
                    if(attachedTexture!=targetTexture) {
                        GL30C.glFramebufferTexture2D(GL30C.GL_DRAW_FRAMEBUFFER,GL30C.GL_COLOR_ATTACHMENT0,GL11C.GL_TEXTURE_2D,targetTexture,0);
                        attachedTexture=targetTexture;
                    }
                    GL11C.glViewport(0,passY,w,h);GL20C.glUniform4f(u.viewport,0,passY,w,h);GL20C.glUniform1i(u.pass,pass);
                    if(LightReports.probeVolume(program,pass,i))probeVolume(program,u,pass,draw);
                    int timer=com.mine.geometry_node.client.model.render.diagnostic.ModelShaderGpuTimings.begin(pass==1?"node_light_surface":pass==2?"node_light_volume":pass==3?"node_light_volume_segment":"node_light_volume_segment_depth");
                    try {draw.run();draws++;} finally {com.mine.geometry_node.client.model.render.diagnostic.ModelShaderGpuTimings.end(timer);}
                    LightReports.submitted(program,pass,i,w,h);
                    if ((pass==2 || pass==3) && LightReports.probePixels(program,pass,i))
                        NodeLightPixelDiagnostics.draw(program,pass,i,w,h,draw);
                }
                boolean volumeEnabled=!c.volume().mode().equals("off")&&c.volume().strength()>0&&c.volume().density()>0;
                float scale=Math.max(q.shadowResolution()<1?q.shadowResolution():0,volumeEnabled?q.volumeResolution():0);
                if(scale>0)y+=Math.max(1,(int)Math.ceil(vp[3]*scale));
            }
        } finally {
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER,fbo);GL11C.glViewport(vp[0],vp[1],vp[2],vp[3]);
            toggle(GL11C.GL_DEPTH_TEST,depth);toggle(GL11C.GL_STENCIL_TEST,stencil);toggle(GL11C.GL_SCISSOR_TEST,scissor);
            if(blend)GL30C.glEnablei(GL11C.GL_BLEND,0);else GL30C.glDisablei(GL11C.GL_BLEND,0);
            GL30C.glColorMaski(0,mask[0]!=0,mask[1]!=0,mask[2]!=0,mask[3]!=0);
            GL20C.glUniform1i(u.pass,0);
        }
            if(units[0]>=0){GL13C.glActiveTexture(GL13C.GL_TEXTURE0+units[0]);GL11C.glBindTexture(GL11C.GL_TEXTURE_2D,surface);}
            if(units[1]>=0){GL13C.glActiveTexture(GL13C.GL_TEXTURE0+units[1]);GL11C.glBindTexture(GL11C.GL_TEXTURE_2D,volume);}
            GL20C.glUniform4fv(u.surfaceRects,surfaceRects);GL20C.glUniform4fv(u.volumeRects,volumeRects);GL20C.glUniform1i(u.resolved,1);
            GL20C.glUniform4fv(u.volumeSegmentRects,volumeSegmentRects);GL20C.glUniform4fv(u.volumeSegmentDepthRects,volumeSegmentDepthRects);
            GL13C.glActiveTexture(active);
            if (LightReports.probePixels(program,0,-1))
                NodeLightPixelDiagnostics.draw(program,0,-1,vp[2],vp[3],draw);
            draw.run();
            LightReports.submitted(program,0,-1,vp[2],vp[3]);
        } finally {
            GL20C.glUniform1i(u.resolved,0);
            for(int i=0;i<3;i++){if(units[i]<0)continue;GL13C.glActiveTexture(GL13C.GL_TEXTURE0+units[i]);GL11C.glBindTexture(GL11C.GL_TEXTURE_2D,textures[i]);GL33C.glBindSampler(units[i],samplers[i]);}
            GL13C.glActiveTexture(active);
        }
    }
    private static void probeVolume(int program,NodeLightRenderer.Interface u,int pass,Runnable draw) {
        int[] masks={0,1,2,4,7};
        String[] names={"baseline","no_world","no_entity","no_model","no_occlusion"};
        int[] entityRange={0,0,1};
        if(u.entityRange>=0)GL20C.glGetUniformiv(program,u.entityRange,entityRange);
        int occluders=u.occluders<0?0:GL20C.glGetUniformi(program,u.occluders);
        float[][] occupiedMin=new float[8][4],occupiedMax=new float[8][4];
        for(int field=0;field<8;field++) {
            if(u.occupiedMin[field]>=0)GL20C.glGetUniformfv(program,u.occupiedMin[field],occupiedMin[field]);
            if(u.occupiedMax[field]>=0)GL20C.glGetUniformfv(program,u.occupiedMax[field],occupiedMax[field]);
        }
        try {
            // Warm the unmodified path; compare each removal against an adjacent reference.
            draw.run();
            for(int i=0;i<masks.length;i++) {
                if(masks[i]==2 && entityRange[0]<=0 || masks[i]==4 && occluders<=0)continue;
                String name="node_light_probe_"+(pass==2?"front_":"back_")+names[i];
                if(i>0) {
                    probeUniforms(u,masks[i],entityRange,occluders,occupiedMin,occupiedMax);
                    draw.run();
                    probeUniforms(u,0,entityRange,occluders,occupiedMin,occupiedMax);
                    int reference=com.mine.geometry_node.client.model.render.diagnostic.ModelShaderGpuTimings.begin(name+"_reference");
                    try {draw.run();} finally {com.mine.geometry_node.client.model.render.diagnostic.ModelShaderGpuTimings.end(reference);}
                }
                probeUniforms(u,masks[i],entityRange,occluders,occupiedMin,occupiedMax);
                int timer=com.mine.geometry_node.client.model.render.diagnostic.ModelShaderGpuTimings.begin(name);
                try {draw.run();} finally {com.mine.geometry_node.client.model.render.diagnostic.ModelShaderGpuTimings.end(timer);}
            }
        } finally {
            GL20C.glUniform3i(u.entityRange,entityRange[0],entityRange[1],entityRange[2]);
            GL20C.glUniform1i(u.occluders,occluders);
            for(int field=0;field<8;field++)GL20C.glUniform4fv(u.occupiedMax[field],occupiedMax[field]);
        }
    }
    private static void probeUniforms(NodeLightRenderer.Interface u,int mask,int[] entityRange,int occluders,
                                      float[][] occupiedMin,float[][] occupiedMax) {
        GL20C.glUniform3i(u.entityRange,(mask&2)==0?entityRange[0]:0,entityRange[1],entityRange[2]);
        GL20C.glUniform1i(u.occluders,(mask&4)==0?occluders:0);
        for(int field=0;field<8;field++) {
            float[] min=occupiedMin[field],max=occupiedMax[field];
            // Empty bounds retain tile coverage and camera-path glass/water absorption.
            if((mask&1)!=0 && u.occupiedMin[field]>=0)
                GL20C.glUniform4f(u.occupiedMax[field],min[0],min[1],min[2],1);
            else GL20C.glUniform4fv(u.occupiedMax[field],max);
        }
    }
    private static void drawDirect(int program,NodeLightRenderer.Interface u,Runnable draw) {
        int cookieUnit=unit(program,u.cookies);
        if(cookieUnit<0) {draw.run();return;}
        int active=GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0+cookieUnit);
        int texture=GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        int sampler=GL30C.glGetIntegeri(GL33C.GL_SAMPLER_BINDING,cookieUnit);
        try {
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D,NodeLightRenderer.cookies());
            GL33C.glBindSampler(cookieUnit,0);
            GL13C.glActiveTexture(active);
            draw.run();
        } finally {
            GL13C.glActiveTexture(GL13C.GL_TEXTURE0+cookieUnit);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D,texture);
            GL33C.glBindSampler(cookieUnit,sampler);
            GL13C.glActiveTexture(active);
        }
    }
    private static int unit(int program,int location){return location<0?-1:GL20C.glGetUniformi(program,location);}
    private static void toggle(int cap,boolean enabled){if(enabled)GL11C.glEnable(cap);else GL11C.glDisable(cap);}
    private static void ensure(int w,int h) {
        if(surface!=0&&w==width&&h==height)return;
        if(System.nanoTime()<retryAfter)throw new IllegalStateException(allocationFailure);
        long nextBytes=(long)w*h*16;
        int max=GL11C.glGetInteger(GL11C.GL_MAX_TEXTURE_SIZE);
        if(w>max||h>max||nextBytes>256L*1024*1024) {
            failed(w,h,"Requested per-light resolution exceeds 256 MiB/texture extent budget");
            throw new IllegalStateException(allocationFailure);
        }
        if(!ModelRenderAsset.residency().reserveTransientGpu(nextBytes)) {
            failed(w,h,"Node light GPU budget exhausted");
            throw new IllegalStateException(allocationFailure);
        }
        clear();
        bytes=nextBytes;width=w;height=h;
        int previous=GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D),unpack=GL11C.glGetInteger(GL21C.GL_PIXEL_UNPACK_BUFFER_BINDING);
        int previousFramebuffer=GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER,0);surface=allocate(w,h);volume=allocate(w,h);framebuffer=GL30C.glGenFramebuffers();
            if(framebuffer==0)throw new IllegalStateException("Unable to allocate node light framebuffer");
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER,framebuffer);
            GL20C.glDrawBuffers(GL30C.GL_COLOR_ATTACHMENT0);
            // Both immutable texture attachments are validated once per allocation.
            for(int texture:new int[]{surface,volume}) {
                GL30C.glFramebufferTexture2D(GL30C.GL_DRAW_FRAMEBUFFER,GL30C.GL_COLOR_ATTACHMENT0,GL11C.GL_TEXTURE_2D,texture,0);
                if(GL30C.glCheckFramebufferStatus(GL30C.GL_DRAW_FRAMEBUFFER)!=GL30C.GL_FRAMEBUFFER_COMPLETE)
                    throw new IllegalStateException("Incomplete node light framebuffer");
            }
            allocationFailure="";failedWidth=failedHeight=0;retryAfter=0;
        } catch(RuntimeException e){clear();failed(w,h,e.toString());throw e;}
        finally {GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER,previousFramebuffer);GL11C.glBindTexture(GL11C.GL_TEXTURE_2D,previous);GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER,unpack);}
    }
    private static int allocate(int w,int h) {
        int id=GL11C.glGenTextures();
        if(id==0)throw new IllegalStateException("Unable to allocate node light texture");
        try {
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D,id);
        GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D,0,GL30C.GL_RGBA16F,w,h,0,GL11C.GL_RGBA,GL11C.GL_FLOAT,0L);
        if(GL11C.glGetTexLevelParameteri(GL11C.GL_TEXTURE_2D,0,GL11C.GL_TEXTURE_WIDTH)!=w
                ||GL11C.glGetTexLevelParameteri(GL11C.GL_TEXTURE_2D,0,GL11C.GL_TEXTURE_HEIGHT)!=h)
            throw new IllegalStateException("Unable to allocate requested node light texture storage");
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D,GL11C.GL_TEXTURE_MIN_FILTER,GL11C.GL_NEAREST);GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D,GL11C.GL_TEXTURE_MAG_FILTER,GL11C.GL_NEAREST);GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D,GL12C.GL_TEXTURE_MAX_LEVEL,0);
        return id;
        } catch(RuntimeException exception) {GL11C.glDeleteTextures(id);throw exception;}
    }
    private static void failed(int w,int h,String reason) {
        failedWidth=w;failedHeight=h;allocationFailure=reason;retryAfter=System.nanoTime()+1_000_000_000L;
    }
    static void clear() {
        int a=surface,b=volume,c=framebuffer;long freed=bytes;surface=volume=framebuffer=width=height=0;bytes=0;
        if(a!=0||b!=0||c!=0||freed!=0)RenderSystem.queueFencedTask(()->{if(a!=0)GL11C.glDeleteTextures(a);if(b!=0)GL11C.glDeleteTextures(b);if(c!=0)GL30C.glDeleteFramebuffers(c);ModelRenderAsset.residency().releaseTransientGpu(freed);});
    }
    static Map<String,Object> report(){return Map.of("gpuBytes",bytes,"width",width,"height",height,"draws",draws,
            "allocationFailure",allocationFailure,"failedWidth",failedWidth,"failedHeight",failedHeight,
            "segmentAtlasEnabled",segmentAtlasEnabled,"segmentFallback",segmentFallback);}
}
