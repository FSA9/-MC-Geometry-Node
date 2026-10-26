package com.mine.geometry_node.client.runtime.model.render.resource;

import com.mine.geometry_node.core.engine.system.model.asset.GltfAsset;
import com.mine.geometry_node.core.engine.system.model.io.ModelCancellation;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.util.*;

/** Worker-side glTF -> LabPBR 1.3. All data channels remain linear; emissive inputs are sRGB. */
public final class ModelLabPbrBaker {
    public record Baked(ModelTextureBackend.Decoded albedo, ModelTextureBackend.Decoded normal, ModelTextureBackend.Decoded specular,
                 GltfAsset.Sampler sampler, List<String> losses) implements AutoCloseable {
        public long bytes() { return albedo.bytes + normal.bytes + specular.bytes; }
        public GltfAsset.Sampler specularSampler() { return new GltfAsset.Sampler(9728, sampler.minFilter()>=9984?9984:9728,sampler.wrapS(),sampler.wrapT()); }
        @Override public void close() { albedo.close(); normal.close(); specular.close(); }
    }
    public static Baked bake(GltfAsset.Material material, List<GltfAsset.Texture> textures,
                      Map<Integer, ModelTextureBackend.Decoded> images, ModelCancellation token, long remaining) throws IOException {
        return bake(material,textures,images,token,remaining,true);
    }
    /** Factor-only refresh never regenerates the unchanged albedo image. */
    public static Baked bakeParameters(GltfAsset.Material material,List<GltfAsset.Texture> textures,
                               Map<Integer,ModelTextureBackend.Decoded> images,ModelCancellation token,long remaining) throws IOException {
        return bake(material,textures,images,token,remaining,false);
    }
    private static Baked bake(GltfAsset.Material material,List<GltfAsset.Texture> textures,
                              Map<Integer,ModelTextureBackend.Decoded> images,ModelCancellation token,long remaining,boolean includeAlbedo) throws IOException {
        List<String> losses = new ArrayList<>();
        var base = material.baseColorTexture();
        var sampler = base == null ? new GltfAsset.Sampler(9729,9729,10497,10497) : textures.get(base.textureIndex()).sampler();
        Source normal = compatible(material.unlit()?null:material.normalTexture(), base, textures, sampler, images, losses, "normal");
        Source mr = compatible(material.unlit()?null:material.metallicRoughnessTexture(), base, textures, sampler, images, losses, "metallicRoughness");
        Source ao = compatible(material.unlit()?null:material.occlusionTexture(), base, textures, sampler, images, losses, "occlusion");
        if (ao != null && material.occlusionStrength() > 0)
            losses.add("LabPBR AO is carried in normal.B; the shaderpack must enable texture AO (Sundial: LabPBR texture AO / LABPBR_TEXTURE_AO, disabled by default); option state is not detected");
        Source emission = compatible(material.unlit()?null:material.emissiveTexture(), base, textures, sampler, images, losses, "emissive");
        NativeImage baseImage=!includeAlbedo||base==null?null:images.get(base.textureIndex()).levels.getFirst();
        int aw=baseImage==null?1:baseImage.getWidth(),ah=baseImage==null?1:baseImage.getHeight();
        int width=1,height=1;
        for (Source source : new Source[]{normal,mr,ao,emission}) if(source!=null) { width=Math.max(width,source.image.getWidth()); height=Math.max(height,source.image.getHeight()); }
        boolean mips=sampler.minFilter()>=9984;
        long bytes=Math.addExact(chainBytes(aw,ah,mips,4),chainBytes(width,height,mips,8));
        if(bytes>remaining)throw new IOException("LabPBR translated texture budget exceeded: "+bytes+" > "+remaining);
        List<NativeImage> as=new ArrayList<>(),ns=new ArrayList<>(),ss=new ArrayList<>();
        try {
            if(includeAlbedo)buildAlbedo(baseImage,material,mips,as,token);
            else { var placeholder=new NativeImage(1,1,false); placeholder.setPixel(0,0,0xffffffff); as.add(placeholder); }
            var n=new NativeImage(width,height,false);ns.add(n);var s=new NativeImage(width,height,false);ss.add(s);
            float[] ef=material.emissiveFactor();
            boolean partialMetal=false, negativeNormalZ=false;
            for(int y=0;y<height;y++) {if((y&31)==0)token.check();for(int x=0;x<width;x++) {
                int nc=sample(normal,x,y,width,height,0xff8080ff),mc=sample(mr,x,y,width,height,0xffffffff);
                float nx=normal==null?0:((nc>>>16&255)/255f*2-1)*material.normalScale();
                float ny=normal==null?0:((nc>>>8&255)/255f*2-1)*material.normalScale();
                float nz=normal==null?1:(nc&255)/255f*2-1;
                negativeNormalZ |= nz < 0;
                float inv=(float)(1/Math.sqrt(Math.max(1e-12,nx*nx+ny*ny+nz*nz))); nx*=inv;ny*=inv;
                float occ=1-material.occlusionStrength()*(1-(sample(ao,x,y,width,height,0xffffffff)>>>16&255)/255f);
                // Our Iris tangent adapter already yields the glTF bitangent. Sundial also
                // derives that frame directly from UV gradients. Do not flip Y a second time.
                n.setPixel(x,y,normalPixel(nx,ny,channel(occ)));
                float rough=clamp(material.roughnessFactor()*(mc>>>8&255)/255f),metal=clamp(material.metallicFactor()*(mc&255)/255f);
                partialMetal|=metal>0&&metal<1;
                int ec=sample(emission,x,y,width,height,0xffffffff);
                float e=material.unlit()?1:Math.max(ef[0]*linear(ec>>>16&255),Math.max(ef[1]*linear(ec>>>8&255),ef[2]*linear(ec&255)))*material.emissiveStrength();
                // glTF roughness is perceptual; LabPBR smoothness is its complement (no extra sqrt).
                int reflectance=metal>=.5f?255:10;
                s.setPixel(x,y,Math.round(clamp(e)*254)<<24 | channel(1-rough)<<16 | reflectance<<8);
            }}
            if(sampler.magFilter()!=9728 || sampler.minFilter()!=9728 && sampler.minFilter()!=9984)losses.add("LabPBR discrete specular channel uses nearest filtering to avoid interpolating metal IDs");
            if(negativeNormalZ)losses.add("LabPBR reconstructs positive normal Z; negative-Z glTF normals cannot be represented");
            if(partialMetal)losses.add("LabPBR binary metal encoding quantizes intermediate glTF metallic at 0.5");
            if(material.emissiveStrength()>1)losses.add("LabPBR emissive intensity is bounded to 1; HDR emission is clipped");
            if(material.unlit() || ef[0]>0 || ef[1]>0 || ef[2]>0)losses.add("LabPBR emissive uses albedo color; independent glTF emissive color/unlit exactness requires pack support");
            while(mips&&(n.getWidth()>1||n.getHeight()>1)) {
                token.check();int nw=Math.max(1,n.getWidth()/2),nh=Math.max(1,n.getHeight()/2);
                var nn=new NativeImage(nw,nh,false);ns.add(nn);var sn=new NativeImage(nw,nh,false);ss.add(sn);
                for(int y=0;y<nh;y++){if((y&31)==0)token.check();for(int x=0;x<nw;x++) {
                    int x0=x*n.getWidth()/nw,x1=(x+1)*n.getWidth()/nw,y0=y*n.getHeight()/nh,y1=(y+1)*n.getHeight()/nh;
                    double nx=0,ny=0,nz=0,occ=0,rough=0,em=0;int count=0,metals=0;
                    for(int sy=y0;sy<y1;sy++)for(int sx=x0;sx<x1;sx++) {
                        int nc=n.getPixel(sx,sy),sc=s.getPixel(sx,sy);double tx=(nc>>>16&255)/255d*2-1,ty=(nc>>>8&255)/255d*2-1;
                        nx+=tx;ny+=ty;nz+=Math.sqrt(Math.max(0,1-tx*tx-ty*ty));occ+=nc&255;rough+=sc>>>16&255;em+=sc>>>24&255;metals+=(sc>>>8&255)>=230?1:0;count++;
                    }
                    double inv=1/Math.sqrt(Math.max(1e-12,nx*nx+ny*ny+nz*nz));
                    nn.setPixel(x,y,normalPixel((float)(nx*inv),(float)(ny*inv),(int)Math.round(occ/count)));
                    sn.setPixel(x,y,(int)Math.round(em/count)<<24|(int)Math.round(rough/count)<<16|(metals*2>=count?255:10)<<8);
                }}
                n=nn;s=sn;
            }
            return new Baked(new ModelTextureBackend.Decoded(as),new ModelTextureBackend.Decoded(ns),new ModelTextureBackend.Decoded(ss),sampler,List.copyOf(losses));
        }catch(IOException|RuntimeException|Error failure){as.forEach(NativeImage::close);ns.forEach(NativeImage::close);ss.forEach(NativeImage::close);throw failure;}
    }
    private static long chainBytes(int width,int height,boolean mipmaps,int bytesPerTexel) {
        long bytes=0;
        do { bytes=Math.addExact(bytes,(long)width*height*bytesPerTexel);if(!mipmaps || width==1&&height==1)return bytes; width=Math.max(1,width/2);height=Math.max(1,height/2); }while(true);
    }
    private static void buildAlbedo(NativeImage source,GltfAsset.Material material,boolean mipmaps,
                                    List<NativeImage> output,ModelCancellation token) throws IOException {
        int width=source==null?1:source.getWidth(),height=source==null?1:source.getHeight();
        var image=new NativeImage(width,height,false);output.add(image);
        float[] factor=material.baseColorFactor();
        int[] r=factorTable(factor[0]),g=factorTable(factor[1]),b=factorTable(factor[2]);
        for(int y=0;y<height;y++){if((y&31)==0)token.check();for(int x=0;x<width;x++){
            int c=source==null?0xffffffff:source.getPixel(x,y);
            image.setPixel(x,y,(material.alphaMode()==GltfAsset.AlphaMode.OPAQUE?255:c>>>24&255)<<24|r[c>>>16&255]<<16|g[c>>>8&255]<<8|b[c&255]);
        }}
        while(mipmaps&&(image.getWidth()>1||image.getHeight()>1)) {
            token.check();int nw=Math.max(1,image.getWidth()/2),nh=Math.max(1,image.getHeight()/2);
            var next=new NativeImage(nw,nh,false);output.add(next);
            for(int y=0;y<nh;y++){if((y&31)==0)token.check();for(int x=0;x<nw;x++){
                int x0=x*image.getWidth()/nw,x1=(x+1)*image.getWidth()/nw,y0=y*image.getHeight()/nh,y1=(y+1)*image.getHeight()/nh;
                double ar=0,ag=0,ab=0,aa=0;int count=0;
                for(int sy=y0;sy<y1;sy++)for(int sx=x0;sx<x1;sx++){
                    int c=image.getPixel(sx,sy);ar+=linear(c>>>16&255);ag+=linear(c>>>8&255);ab+=linear(c&255);aa+=c>>>24&255;count++;
                }
                next.setPixel(x,y,(int)Math.round(aa/count)<<24|display((float)(ar/count))<<16|display((float)(ag/count))<<8|display((float)(ab/count)));
            }}
            image=next;
        }
    }
    private record Source(NativeImage image,GltfAsset.Sampler sampler,double a,double b,double c,double d,double tx,double ty) {}
    private static Source compatible(GltfAsset.TextureInfo info,GltfAsset.TextureInfo base,List<GltfAsset.Texture> textures,
                                    GltfAsset.Sampler sampler,Map<Integer,ModelTextureBackend.Decoded> images,List<String> losses,String name) {
        if(info==null)return null;
        if(info.texCoord()!=(base==null?0:base.texCoord())) {
            losses.add("Iris LabPBR "+name+" map omitted: distinct UV sets cannot share the pack albedo lookup; scalar fallback");return null;
        }
        var decoded=images.get(info.textureIndex());
        if(decoded==null)throw new IllegalArgumentException("PBR image was not decoded: "+info.textureIndex());
        double[] bm=transform(base),sm=transform(info);
        double det=bm[0]*bm[3]-bm[1]*bm[2];
        if(Math.abs(det)<1e-12) {losses.add("Iris LabPBR "+name+" map omitted: singular base UV transform");return null;}
        double ia=bm[3]/det,ib=-bm[1]/det,ic=-bm[2]/det,id=bm[0]/det;
        double itx=-ia*bm[4]-ib*bm[5],ity=-ic*bm[4]-id*bm[5];
        double a=sm[0]*ia+sm[1]*ic,b=sm[0]*ib+sm[1]*id,c=sm[2]*ia+sm[3]*ic,d=sm[2]*ib+sm[3]*id;
        double tx=sm[0]*itx+sm[1]*ity+sm[4],ty=sm[2]*itx+sm[3]*ity+sm[5];
        if(Math.abs(a-1)+Math.abs(b)+Math.abs(c)+Math.abs(d-1)+Math.abs(tx)+Math.abs(ty)>1e-6)
            losses.add("Iris LabPBR "+name+" UV transform baked over one albedo tile; resampling and out-of-tile periodicity may differ");
        var sourceSampler=textures.get(info.textureIndex()).sampler();
        if(!sourceSampler.equals(sampler))losses.add("Iris LabPBR "+name+" independent sampler baked into the albedo domain; runtime filtering follows albedo");
        return new Source(decoded.levels.getFirst(),sourceSampler,a,b,c,d,tx,ty);
    }
    private static double[] transform(GltfAsset.TextureInfo info) {
        if(info==null)return new double[]{1,0,0,1,0,0};
        float[] scale=info.scale(),offset=info.offset();double c=Math.cos(info.rotation()),s=Math.sin(info.rotation());
        return new double[]{c*scale[0],-s*scale[1],s*scale[0],c*scale[1],offset[0],offset[1]};
    }
    private static int sample(Source source,int x,int y,int width,int height,int fallback) {
        if(source==null)return fallback;
        double u=(x+.5)/width,v=(y+.5)/height;
        double su=source.a*u+source.b*v+source.tx,sv=source.c*u+source.d*v+source.ty;
        NativeImage image=source.image;
        // Exact source addressing is applied during baking; the translated texture has its own sampler.
        int ix=address((int)Math.floor(su*image.getWidth()),image.getWidth(),source.sampler.wrapS());
        int iy=address((int)Math.floor(sv*image.getHeight()),image.getHeight(),source.sampler.wrapT());
        return image.getPixel(ix,iy);
    }
    private static int address(int i,int size,int wrap) {
        if(wrap==33071)return Math.clamp(i,0,size-1);
        if(wrap==33648){int p=Math.floorMod(i,2*size);return p<size?p:2*size-1-p;}
        return Math.floorMod(i,size);
    }
    private static int normalPixel(float nx,float ny,int ao) {
        int x=channel(nx*.5f+.5f),y=channel(ny*.5f+.5f);
        // UNORM8 quantization can push an exactly grazing normal outside the unit circle.
        // Keep sqrt(1-dot(xy,xy)) finite even for packs which do not clamp before reconstruction.
        while((2*x-255)*(2*x-255)+(2*y-255)*(2*y-255)>255*255) {
            if(Math.abs(x-127.5)>=Math.abs(y-127.5))x+=x>=128?-1:1;else y+=y>=128?-1:1;
        }
        return 0xff000000|x<<16|y<<8|ao;
    }
    private static int display(float v){return channel(v<=.0031308f?v*12.92f:1.055f*(float)Math.pow(v,1/2.4)-.055f);}
    private static final float[] LINEAR = new float[256];
    static { for(int i=0;i<256;i++){float s=i/255f;LINEAR[i]=s<=.04045f?s/12.92f:(float)Math.pow((s+.055f)/1.055f,2.4);} }
    private static float linear(int v){return LINEAR[v];}
    private static int[] factorTable(float factor){int[] out=new int[256];for(int i=0;i<256;i++)out[i]=display(LINEAR[i]*factor);return out;}
    private static float clamp(float v){return Math.clamp(v,0,1);}
    private static int channel(float v){return Math.round(clamp(v)*255);}
    private ModelLabPbrBaker() {}
}
