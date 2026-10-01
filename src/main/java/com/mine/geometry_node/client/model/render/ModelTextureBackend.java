package com.mine.geometry_node.client.model.render;

import com.mine.geometry_node.core.engine.system.model.asset.GltfAsset;
import com.mine.geometry_node.core.engine.system.model.io.ModelCancellation;
import com.mojang.blaze3d.opengl.GlSampler;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.*;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.lwjgl.opengl.GL33C;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.*;
import java.util.*;

/** Owned textures/samplers. Worker decode, render-thread upload, no shared sampler mutation. */
final class ModelTextureBackend {
    static final int MAX_DIMENSION=16384;
    static final long MAX_TEXTURE_BYTES=512L*1024*1024;
    static final GltfAsset.Sampler WHITE_SAMPLER=new GltfAsset.Sampler(9728,9728,33071,33071);
    static final class Decoded implements AutoCloseable {
        final List<NativeImage> levels;
        final long bytes;
        Decoded(List<NativeImage> levels){this.levels=List.copyOf(levels);this.bytes=levels.stream().mapToLong(i->(long)i.getWidth()*i.getHeight()*4).sum();}
        @Override public void close(){levels.forEach(NativeImage::close);}
    }
    static Decoded solid(int argb){NativeImage image=new NativeImage(1,1,false);image.setPixel(0,0,argb);return new Decoded(List.of(image));}
    /** glTF OPAQUE and emissive RGB ignore image alpha, including all mip levels. */
    static Decoded opaqueCopy(Decoded source, ModelCancellation token, long remaining) throws IOException {
        if (source.bytes > remaining) throw new IOException("opaque texture variant exceeds decoded texture budget");
        List<NativeImage> levels = new ArrayList<>();
        try {
            for (NativeImage image : source.levels) {
                NativeImage copy = new NativeImage(image.getWidth(), image.getHeight(), false); levels.add(copy);
                for (int y = 0; y < image.getHeight(); y++) {
                    if ((y & 31) == 0) token.check();
                    for (int x = 0; x < image.getWidth(); x++) copy.setPixel(x, y, image.getPixel(x, y) | 0xff000000);
                }
            }
            return new Decoded(levels);
        } catch (IOException | RuntimeException | Error failure) { levels.forEach(NativeImage::close); throw failure; }
    }
    static Decoded decode(GltfAsset.Texture source,ModelCancellation token,long remaining) throws IOException {
        token.check();byte[] encoded=source.bytes();
        int width,height;
        try(var input=new MemoryCacheImageInputStream(new ByteArrayInputStream(encoded))) {
            var readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext())throw new IOException("unsupported image header: "+source.logicalId());
            var reader=readers.next();
            try {
                String format=reader.getFormatName();
                if(!format.equalsIgnoreCase("png")&&!format.equalsIgnoreCase("jpeg")&&!format.equalsIgnoreCase("jpg"))
                    throw new IOException("only PNG/JPEG images are supported: "+source.logicalId());
                reader.setInput(input,true,true);width=reader.getWidth(0);height=reader.getHeight(0);
            } finally {reader.dispose();}
        }
        if(width<1||height<1||width>MAX_DIMENSION||height>MAX_DIMENSION)
            throw new IOException("image dimensions exceed 1.."+MAX_DIMENSION+": "+source.logicalId()+" "+width+"x"+height);
        boolean mips=source.sampler().minFilter()>=9984;
        long predicted=0;int w=width,h=height;
        do {predicted=Math.addExact(predicted,(long)w*h*4);if(!mips||w==1||h==1)break;w=Math.max(1,w/2);h=Math.max(1,h/2);}while(true);
        if(predicted>remaining)throw new IOException("decoded texture/mipmap budget exceeded: "+source.logicalId()+" requires "+predicted+", remaining "+remaining);
        List<NativeImage> levels=new ArrayList<>();
        try {
            // Minecraft NativeImage.read validates a PNG signature, so JPEG uses the bounded ImageIO decoder.
            var buffered=ImageIO.read(new ByteArrayInputStream(encoded));
            if(buffered==null)throw new IOException("image decode failed: "+source.logicalId());
            NativeImage image=new NativeImage(width,height,false);levels.add(image);
            try {for(int y=0;y<height;y++){if((y&31)==0)token.check();for(int x=0;x<width;x++)image.setPixel(x,y,buffered.getRGB(x,y));}}
            finally {buffered.flush();}
            if(image.getWidth()!=width||image.getHeight()!=height)throw new IOException("image header/decode dimension mismatch");
            while(mips&&image.getWidth()>1&&image.getHeight()>1) {
                token.check();NativeImage next=new NativeImage(Math.max(1,image.getWidth()/2),Math.max(1,image.getHeight()/2),false);levels.add(next);
                for(int y=0;y<next.getHeight();y++) {
                    if((y&31)==0)token.check();
                    for(int x=0;x<next.getWidth();x++) {
                        // Gamma-correct RGB mip generation; alpha remains linear. Include odd edge texels.
                        int x0=x*image.getWidth()/next.getWidth(),x1=(x+1)*image.getWidth()/next.getWidth();
                        int y0=y*image.getHeight()/next.getHeight(),y1=(y+1)*image.getHeight()/next.getHeight();
                        double r=0,g=0,b=0,a=0;int count=0;
                        for(int sy=y0;sy<y1;sy++)for(int sx=x0;sx<x1;sx++) {
                            int c=image.getPixel(sx,sy);a+=(c>>>24)&255;r+=linear((c>>>16)&255);g+=linear((c>>>8)&255);b+=linear(c&255);count++;
                        }
                        next.setPixel(x,y,((int)Math.round(a/count)<<24)|(display(r/count)<<16)|(display(g/count)<<8)|display(b/count));
                    }
                }
                image=next;
            }
            token.check();return new Decoded(levels);
        } catch(IOException|RuntimeException|Error e){levels.forEach(NativeImage::close);throw e;}
    }
    private static double linear(int value){double v=value/255.0;return v<=.04045?v/12.92:Math.pow((v+.055)/1.055,2.4);}
    private static int display(double value){double v=value<=.0031308?value*12.92:1.055*Math.pow(value,1/2.4)-.055;return Math.clamp((int)Math.round(v*255),0,255);}
    static OwnedTexture upload(String label,Decoded decoded,GltfAsset.Sampler requested) {
        RenderSystem.assertOnRenderThread();return new OwnedTexture(label,decoded,requested);
    }
    static OwnedTexture upload(String label,Decoded decoded,GltfAsset.Sampler requested,boolean ignoredSrgb) { return upload(label,decoded,requested); }
    static final class OwnedTexture extends AbstractTexture {
        private boolean closed;
        private boolean ownsSampler;
        OwnedTexture(String label,Decoded data,GltfAsset.Sampler requested) {
            try {
                NativeImage first=data.levels.getFirst();var device=RenderSystem.getDevice();
                if(first.getWidth()>device.getMaxTextureSize()||first.getHeight()>device.getMaxTextureSize())throw new IllegalArgumentException("texture exceeds GPU maximum "+device.getMaxTextureSize());
                texture=device.createTexture(label,GpuTexture.USAGE_COPY_DST|GpuTexture.USAGE_TEXTURE_BINDING,TextureFormat.RGBA8,first.getWidth(),first.getHeight(),1,data.levels.size());
                textureView=device.createTextureView(texture);
                sampler=device.createSampler(address(requested.wrapS()),address(requested.wrapT()),filter(requested.minFilter()),filter(requested.magFilter()),1,OptionalDouble.empty());
                ownsSampler=true;
                // Current 26.1.2 host API has no mirrored repeat or nearest mip-level selector.
                // This private GL sampler is owned here; every glTF min/mag/wrap enum is represented exactly.
                if(sampler instanceof GlSampler gl) {
                    GL33C.glSamplerParameteri(gl.getId(),GL33C.GL_TEXTURE_WRAP_S,requested.wrapS());
                    GL33C.glSamplerParameteri(gl.getId(),GL33C.GL_TEXTURE_WRAP_T,requested.wrapT());
                    GL33C.glSamplerParameteri(gl.getId(),GL33C.GL_TEXTURE_MIN_FILTER,requested.minFilter());
                    GL33C.glSamplerParameteri(gl.getId(),GL33C.GL_TEXTURE_MAG_FILTER,requested.magFilter());
                } else throw new IllegalStateException("exact glTF sampler backend unavailable on "+sampler.getClass().getName());
                var encoder=device.createCommandEncoder();
                for(int mip=0;mip<data.levels.size();mip++) {
                    NativeImage image=data.levels.get(mip);
                    encoder.writeToTexture(texture,image,mip,0,0,0,image.getWidth(),image.getHeight(),0,0);
                }
            } catch(RuntimeException|Error e){close();throw e;}
        }
        @Override public void close(){if(closed)return;closed=true;if(ownsSampler)sampler.close();super.close();}
    }
    private static AddressMode address(int value){return value==33071?AddressMode.CLAMP_TO_EDGE:AddressMode.REPEAT;}
    private static FilterMode filter(int value){return value==9728||value==9984||value==9986?FilterMode.NEAREST:FilterMode.LINEAR;}
    private ModelTextureBackend(){}
}

