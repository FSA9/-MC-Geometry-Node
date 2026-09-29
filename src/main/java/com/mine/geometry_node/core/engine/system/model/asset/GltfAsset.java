package com.mine.geometry_node.core.engine.system.model.asset;

import java.util.*;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/** Immutable CPU representation of the STATIC_BASIC glTF profile. All arrays are defensive copies. */
public final class GltfAsset {
    public enum AlphaMode { OPAQUE, MASK, BLEND }
    public enum PrimitiveMode { TRIANGLES, TRIANGLE_STRIP, TRIANGLE_FAN }
    public record Bounds(float minX,float minY,float minZ,float maxX,float maxY,float maxZ) {
        public static Bounds empty() { return new Bounds(Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY); }
        public boolean isEmpty() { return minX>maxX || minY>maxY || minZ>maxZ; }
        public Bounds include(float x,float y,float z) { return new Bounds(Math.min(minX,x),Math.min(minY,y),Math.min(minZ,z),Math.max(maxX,x),Math.max(maxY,y),Math.max(maxZ,z)); }
    }
    public static final class Primitive {
        private final float[] positions,normals,tangents,uv0,uv1,colors;
        private final int[] indices;
        private final Material material;
        private final Bounds bounds;
        private final PrimitiveMode mode;
        public Primitive(float[] positions,float[] normals,float[] tangents,float[] uv0,float[] uv1,float[] colors,int[] indices,
                         Material material,PrimitiveMode mode,Bounds bounds) {
            this.positions=copy(positions); this.normals=copy(normals); this.tangents=copy(tangents); this.uv0=copy(uv0); this.uv1=copy(uv1); this.colors=copy(colors); this.indices=indices.clone();
            this.material=material; this.mode=Objects.requireNonNull(mode); this.bounds=Objects.requireNonNull(bounds);
            if (this.positions.length%3!=0 || this.normals.length!=0 && this.normals.length!=this.positions.length || this.uv0.length!=0 && this.uv0.length/2!=vertexCount()
                    || this.uv1.length!=0 && this.uv1.length/2!=vertexCount() || this.colors.length!=0 && this.colors.length/4!=vertexCount()
                    || this.tangents.length!=0 && this.tangents.length/4!=vertexCount() || indices.length%3!=0)
                throw new IllegalArgumentException("inconsistent primitive arrays");
            for (int i : this.indices) if (i<0 || i>=vertexCount()) throw new IllegalArgumentException("primitive index out of range");
            finite(this.positions); finite(this.normals); finite(this.tangents); finite(this.uv0); finite(this.uv1); finite(this.colors);
        }
        public int vertexCount(){ return positions.length/3; }
        public int indexCount(){ return indices.length; }
        public float[] positions(){ return positions.clone(); }
        public float[] normals(){ return normals.clone(); }
        public float[] tangents(){ return tangents.clone(); }
        public float[] uv0(){ return uv0.clone(); }
        public float[] uv1(){ return uv1.clone(); }
        public float[] colors(){ return colors.clone(); }
        public int[] indices(){ return indices.clone(); }
        public Material material(){ return material; }
        public PrimitiveMode mode(){ return mode; }
        public Bounds bounds(){ return bounds; }
        public long cpuBytes(){ return (long)(positions.length+normals.length+tangents.length+uv0.length+uv1.length+colors.length)*4L+(long)indices.length*4; }
    }
    public static final class Mesh {
        private final String name; private final List<Primitive> primitives;
        public Mesh(String name,List<Primitive> primitives){this.name=name==null?"":name;this.primitives=List.copyOf(primitives);}
        public String name(){return name;} public List<Primitive> primitives(){return primitives;}
    }
    public static final class Node {
        private final int index,parent,mesh; private final String name; private final Matrix4f local; private final List<Integer> children;
        public Node(int index,int parent,String name,Matrix4fc local,int mesh,List<Integer> children){
            this.index=index;this.parent=parent;this.mesh=mesh;this.name=name==null?"":name;this.local=new Matrix4f(local);this.children=List.copyOf(children);
        }
        public int index(){return index;} public int parent(){return parent;} public int mesh(){return mesh;} public String name(){return name;} public Matrix4f local(){return new Matrix4f(local);} public List<Integer> children(){return children;}
    }
    public static final class Scene { private final String name; private final List<Integer> roots;
        public Scene(String name,List<Integer> roots){this.name=name==null?"":name;this.roots=List.copyOf(roots);} public String name(){return name;} public List<Integer> roots(){return roots;}}
    public static final class TextureInfo {
        private final int textureIndex,texCoord; private final float[] offset,scale; private final float rotation;
        public TextureInfo(int textureIndex,int texCoord,float[] offset,float[] scale,float rotation){this.textureIndex=textureIndex;this.texCoord=texCoord;this.offset=offset.clone();this.scale=scale.clone();this.rotation=rotation;}
        public int textureIndex(){return textureIndex;} public int texCoord(){return texCoord;} public float[] offset(){return offset.clone();} public float[] scale(){return scale.clone();} public float rotation(){return rotation;}
    }
    public static final class Material {
        private final String name; private final Map<String,String> extensions; private final AlphaMode alphaMode; private final float alphaCutoff; private final boolean doubleSided,unlit;
        private final float[] baseColorFactor,emissiveFactor; private final float metallicFactor,roughnessFactor,normalScale,occlusionStrength;
        private final TextureInfo baseColorTexture,metallicRoughnessTexture,normalTexture,occlusionTexture,emissiveTexture;
        public Material(String name,AlphaMode mode,float cutoff,boolean doubleSided,boolean unlit,float[] base,float metallic,float rough,float[] emissive,float normalScale,float ao,
                        TextureInfo baseTex,TextureInfo mrTex,TextureInfo normalTex,TextureInfo aoTex,TextureInfo emissiveTex){
            this(name,mode,cutoff,doubleSided,unlit,base,metallic,rough,emissive,normalScale,ao,baseTex,mrTex,normalTex,aoTex,emissiveTex,Map.of());
        }
        public Material(String name,AlphaMode mode,float cutoff,boolean doubleSided,boolean unlit,float[] base,float metallic,float rough,float[] emissive,float normalScale,float ao,
                        TextureInfo baseTex,TextureInfo mrTex,TextureInfo normalTex,TextureInfo aoTex,TextureInfo emissiveTex,Map<String,String> extensions){
            this.name=name==null?"":name;this.extensions=Map.copyOf(extensions);this.alphaMode=Objects.requireNonNull(mode);this.alphaCutoff=cutoff;this.doubleSided=doubleSided;this.unlit=unlit;this.baseColorFactor=base.clone();this.metallicFactor=metallic;this.roughnessFactor=rough;this.emissiveFactor=emissive.clone();this.normalScale=normalScale;this.occlusionStrength=ao;this.baseColorTexture=baseTex;this.metallicRoughnessTexture=mrTex;this.normalTexture=normalTex;this.occlusionTexture=aoTex;this.emissiveTexture=emissiveTex;
        }
        public String name(){return name;} public Map<String,String> extensions(){return extensions;} public AlphaMode alphaMode(){return alphaMode;} public float alphaCutoff(){return alphaCutoff;} public boolean doubleSided(){return doubleSided;} public boolean unlit(){return unlit;}
        public float[] baseColorFactor(){return baseColorFactor.clone();} public float metallicFactor(){return metallicFactor;} public float roughnessFactor(){return roughnessFactor;} public float[] emissiveFactor(){return emissiveFactor.clone();} public float normalScale(){return normalScale;} public float occlusionStrength(){return occlusionStrength;}
        public TextureInfo baseColorTexture(){return baseColorTexture;} public TextureInfo metallicRoughnessTexture(){return metallicRoughnessTexture;} public TextureInfo normalTexture(){return normalTexture;} public TextureInfo occlusionTexture(){return occlusionTexture;} public TextureInfo emissiveTexture(){return emissiveTexture;}
    }
    public record Sampler(int magFilter,int minFilter,int wrapS,int wrapT) {}
    /** Shared immutable encoded image; several texture/sampler descriptions may reference it. */
    public static final class Image {
        private final String mimeType,logicalId;
        private final byte[] bytes;
        public Image(String mimeType,String logicalId,byte[] bytes) {
            this.mimeType=Objects.requireNonNull(mimeType);this.logicalId=Objects.requireNonNull(logicalId);this.bytes=bytes.clone();
        }
        public String mimeType(){return mimeType;} public String logicalId(){return logicalId;}
        public byte[] bytes(){return bytes.clone();} public int byteLength(){return bytes.length;}
    }
    public static final class Texture {
        private final String name; private final Image image; private final Sampler sampler;
        public Texture(String name,String mime,String id,byte[] bytes,Sampler sampler){this(name,new Image(mime,id,bytes),sampler);}
        public Texture(String name,Image image,Sampler sampler){this.name=name==null?"":name;this.image=Objects.requireNonNull(image);this.sampler=Objects.requireNonNull(sampler);}
        public String name(){return name;} public String mimeType(){return image.mimeType();} public String logicalId(){return image.logicalId();}
        public byte[] bytes(){return image.bytes();} public int byteLength(){return image.byteLength();} public Image image(){return image;} public Sampler sampler(){return sampler;}
    }
    public record Dependency(String logicalId,long encodedBytes,String sha256) {}
    public record Report(String assetVersion,String minVersion,List<String> usedExtensions,List<String> requiredExtensions,List<String> unsupportedOptional,List<String> degraded,List<String> gaps,long encodedBytes,long decodedBytes,List<Dependency> dependencies) {
        public Report {usedExtensions=List.copyOf(usedExtensions);requiredExtensions=List.copyOf(requiredExtensions);unsupportedOptional=List.copyOf(unsupportedOptional);degraded=List.copyOf(degraded);gaps=List.copyOf(gaps);dependencies=List.copyOf(dependencies);}
    }
    private final String entry; private final List<Scene> scenes; private final int defaultScene; private final List<Node> nodes; private final List<Mesh> meshes; private final List<Material> materials; private final List<Texture> textures; private final Report report;
    public GltfAsset(String entry,List<Scene> scenes,int defaultScene,List<Node> nodes,List<Mesh> meshes,List<Material> materials,List<Texture> textures,Report report){
        this.entry=entry;this.scenes=List.copyOf(scenes);this.defaultScene=defaultScene;this.nodes=List.copyOf(nodes);this.meshes=List.copyOf(meshes);this.materials=List.copyOf(materials);this.textures=List.copyOf(textures);this.report=Objects.requireNonNull(report);
    }
    public String entry(){return entry;} public List<Scene> scenes(){return scenes;} public int defaultScene(){return defaultScene;} public Scene defaultSceneObject(){return defaultScene>=0&&defaultScene<scenes.size()?scenes.get(defaultScene):scenes.isEmpty()?null:scenes.get(0);} public List<Node> nodes(){return nodes;} public List<Mesh> meshes(){return meshes;} public List<Material> materials(){return materials;} public List<Texture> textures(){return textures;} public Report report(){return report;}
    private static float[] copy(float[] a){return a==null?new float[0]:a.clone();} private static void finite(float[] a){for(float v:a)if(!Float.isFinite(v))throw new IllegalArgumentException("non-finite primitive value");}
}
