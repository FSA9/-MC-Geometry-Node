package com.mine.geometry_node.client.model.render;

import com.mine.geometry_node.core.engine.system.model.asset.GltfAsset;
import java.io.IOException;

/** Worker-only alpha coverage approximation. No texture reads or tessellation on the render thread. */
final class ModelOcclusionAlpha {
    static final int MAX_SUBDIVISIONS = 64;
    interface Texture {
        int width(); int height();
        /** Alpha at integer coordinates, in the image's original top-to-bottom glTF UV orientation. */
        float alpha(int x, int y);
    }
    record Vertex(float x, float y, float z, float u, float v, float alpha) {
        static Vertex interpolate(Vertex a, Vertex b, Vertex c, float u, float v) {
            float w = 1-u-v;
            return new Vertex(a.x*w+b.x*u+c.x*v,a.y*w+b.y*u+c.y*v,a.z*w+b.z*u+c.z*v,
                    a.u*w+b.u*u+c.u*v,a.v*w+b.v*u+c.v*v,a.alpha*w+b.alpha*u+c.alpha*v);
        }
    }
    interface Consumer { void triangle(Vertex a, Vertex b, Vertex c, float opacity) throws IOException; }
    private ModelOcclusionAlpha() {}

    static int subdivisions(Vertex a, Vertex b, Vertex c, Texture texture) {
        if (texture == null) return a.alpha == b.alpha && a.alpha == c.alpha ? 1 : 16;
        float du = Math.max(Math.max(Math.abs(a.u-b.u),Math.abs(a.u-c.u)),Math.abs(b.u-c.u));
        float dv = Math.max(Math.max(Math.abs(a.v-b.v),Math.abs(a.v-c.v)),Math.abs(b.v-c.v));
        return Math.max(1, Math.min(MAX_SUBDIVISIONS, (int)Math.ceil(Math.max(du*texture.width(),dv*texture.height())*2)));
    }
    static void emit(Vertex a, Vertex b, Vertex c, GltfAsset.AlphaMode mode, float factor, float cutoff,
                     Texture texture, GltfAsset.Sampler sampler, Consumer out) throws IOException {
        if (mode == GltfAsset.AlphaMode.OPAQUE) { out.triangle(a,b,c,1); return; }
        int n = subdivisions(a,b,c,texture);
        for(int y=0;y<n;y++) for(int x=0;x<n-y;x++) {
            Vertex p=Vertex.interpolate(a,b,c,(float)x/n,(float)y/n);
            Vertex q=Vertex.interpolate(a,b,c,(float)(x+1)/n,(float)y/n);
            Vertex r=Vertex.interpolate(a,b,c,(float)x/n,(float)(y+1)/n);
            emitCell(p,q,r,mode,factor,cutoff,texture,sampler,out);
            if(x+y<n-1) emitCell(q,Vertex.interpolate(a,b,c,(float)(x+1)/n,(float)(y+1)/n),r,
                    mode,factor,cutoff,texture,sampler,out);
        }
    }
    private static void emitCell(Vertex a, Vertex b, Vertex c, GltfAsset.AlphaMode mode, float factor,
                                 float cutoff, Texture texture, GltfAsset.Sampler sampler, Consumer out) throws IOException {
        float alpha = factor*(a.alpha+b.alpha+c.alpha)/3;
        if(texture!=null) alpha *= sample(texture,sampler,(a.u+b.u+c.u)/3,(a.v+b.v+c.v)/3);
        alpha=Math.max(0,Math.min(1,alpha));
        if(mode==GltfAsset.AlphaMode.MASK) alpha=alpha>=cutoff?1:0;
        if(alpha>0) out.triangle(a,b,c,alpha);
    }
    static float sample(Texture texture, GltfAsset.Sampler sampler, float u, float v) {
        // Match base-level sampling; mip footprints depend on each receiver and cannot be baked.
        if(sampler.magFilter()==9728) return texture.alpha(wrap((int)Math.floor(u*texture.width()),texture.width(),sampler.wrapS()),
                wrap((int)Math.floor(v*texture.height()),texture.height(),sampler.wrapT()));
        double px=u*texture.width()-.5,py=v*texture.height()-.5;
        int x=(int)Math.floor(px),y=(int)Math.floor(py);float fx=(float)(px-x),fy=(float)(py-y);
        float a=texture.alpha(wrap(x,texture.width(),sampler.wrapS()),wrap(y,texture.height(),sampler.wrapT()));
        float b=texture.alpha(wrap(x+1,texture.width(),sampler.wrapS()),wrap(y,texture.height(),sampler.wrapT()));
        float c=texture.alpha(wrap(x,texture.width(),sampler.wrapS()),wrap(y+1,texture.height(),sampler.wrapT()));
        float d=texture.alpha(wrap(x+1,texture.width(),sampler.wrapS()),wrap(y+1,texture.height(),sampler.wrapT()));
        return (a*(1-fx)+b*fx)*(1-fy)+(c*(1-fx)+d*fx)*fy;
    }
    private static int wrap(int value,int size,int mode) {
        if(mode==33071)return Math.max(0,Math.min(size-1,value));
        if(mode==33648){int p=Math.floorMod(value,size*2);return p<size?p:size*2-1-p;}
        return Math.floorMod(value,size);
    }
}
