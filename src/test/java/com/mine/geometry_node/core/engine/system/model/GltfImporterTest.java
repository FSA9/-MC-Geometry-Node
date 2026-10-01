package com.mine.geometry_node.core.engine.system.model;

import com.google.gson.*;
import com.mine.geometry_node.core.engine.system.model.asset.*;
import com.mine.geometry_node.core.engine.system.model.io.*;
import com.mine.geometry_node.core.engine.system.model.diagnostic.ModelProblem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class GltfImporterTest {
    @TempDir Path directory;
    private static final byte[] PNG = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLttAAAAABJRU5ErkJggg==");
    private static JsonObject triangle(boolean normal) {
        JsonObject d=JsonParser.parseString("{\"asset\":{\"version\":\"2.0\"},\"buffers\":[{\"byteLength\":36,\"uri\":\"mesh.bin\"}],\"bufferViews\":[{\"buffer\":0,\"byteLength\":36}],\"accessors\":[{\"bufferView\":0,\"componentType\":5126,\"count\":3,\"type\":\"VEC3\"}],\"meshes\":[{\"primitives\":[{\"attributes\":{\"POSITION\":0}}]}],\"nodes\":[{\"mesh\":0}],\"scenes\":[{\"nodes\":[0]}],\"scene\":0}").getAsJsonObject();
        return d;
    }
    private static byte[] positions(float... values) { ByteBuffer b=ByteBuffer.allocate(values.length*4).order(ByteOrder.LITTLE_ENDIAN);for(float v:values)b.putFloat(v);return b.array(); }
    private GltfAsset load(JsonObject doc,byte[] bin) throws Exception {
        Files.writeString(directory.resolve("model.gltf"),doc.toString());Files.write(directory.resolve("mesh.bin"),bin);
        return new GltfImporter().load(new FileModelResourceProvider(directory),"model.gltf",new ModelCancellation());
    }
    @Test void importsDefaultMaterialAndFlatNormalsWithoutMutatingSharedMesh() throws Exception {
        JsonObject doc=triangle(false);doc.getAsJsonArray("nodes").add(JsonParser.parseString("{\"name\":\"same\",\"mesh\":0,\"translation\":[3,0,0]}"));doc.getAsJsonArray("scenes").get(0).getAsJsonObject().getAsJsonArray("nodes").add(1);
        GltfAsset a=load(doc,positions(0,0,0,1,0,0,0,1,0));assertEquals(1,a.meshes().size());assertEquals(2,a.nodes().size());
        GltfAsset.Primitive p=a.meshes().get(0).primitives().get(0);assertEquals("default",p.material().name());assertArrayEquals(new float[]{0,0,1,0,0,1,0,0,1},p.normals());
        float[] change=p.positions();change[0]=99;assertEquals(0,p.positions()[0]);assertEquals(3,a.nodes().get(1).local().m30());assertEquals(0,a.nodes().get(0).local().m30());
    }
    @Test void stripAndFanUseDistinctTriangulation() throws Exception {
        for(int mode:new int[]{5,6}) {JsonObject doc=triangle(false);doc.getAsJsonArray("buffers").get(0).getAsJsonObject().addProperty("byteLength",48);doc.getAsJsonArray("bufferViews").get(0).getAsJsonObject().addProperty("byteLength",48);doc.getAsJsonArray("accessors").get(0).getAsJsonObject().addProperty("count",4);doc.getAsJsonArray("meshes").get(0).getAsJsonObject().getAsJsonArray("primitives").get(0).getAsJsonObject().addProperty("mode",mode);
            GltfAsset.Primitive p=load(doc,positions(0,0,0,1,0,0,0,1,0,1,1,0)).meshes().get(0).primitives().get(0);assertEquals(6,p.indexCount());float[] n=p.normals();assertEquals(1,n[2]);assertEquals(mode==5?1:-1,n[11]);}
    }
    @Test void enforcesDeclaredBufferLengthRatherThanPhysicalTail() throws Exception {
        JsonObject d=triangle(false);d.getAsJsonArray("buffers").get(0).getAsJsonObject().addProperty("byteLength",12);
        assertEquals(ModelProblem.Code.INVALID_ACCESSOR,assertThrows(ModelProblem.class,()->load(d,positions(0,0,0,1,0,0,0,1,0))).code());
    }
    @Test void keepsTextureIndexSamplerAndSharedImageIdentity() throws Exception {
        JsonObject d=triangle(false);d.add("images",JsonParser.parseString("[{\"uri\":\"color.png\"}]"));d.add("textures",JsonParser.parseString("[{\"source\":0,\"sampler\":0},{\"source\":0,\"sampler\":1}]"));d.add("samplers",JsonParser.parseString("[{\"magFilter\":9728,\"wrapS\":33071},{\"magFilter\":9729,\"wrapS\":33648}]"));Files.write(directory.resolve("color.png"),PNG);
        GltfAsset a=load(d,positions(0,0,0,1,0,0,0,1,0));assertEquals(2,a.textures().size());assertSame(a.textures().get(0).image(),a.textures().get(1).image());assertEquals(33071,a.textures().get(0).sampler().wrapS());assertEquals(33648,a.textures().get(1).sampler().wrapS());
        assertEquals(Files.size(directory.resolve("model.gltf"))+36+PNG.length,a.report().encodedBytes());
    }
    @Test void dataUriIsAlreadyPartOfEncodedJsonClosure() throws Exception {
        JsonObject d=triangle(false);byte[] b=positions(0,0,0,1,0,0,0,1,0);d.getAsJsonArray("buffers").get(0).getAsJsonObject().addProperty("uri","data:application/octet-stream;base64,"+Base64.getEncoder().encodeToString(b));GltfAsset a=load(d,b);assertEquals(Files.size(directory.resolve("model.gltf")),a.report().encodedBytes());
    }
    @Test void rejectsCyclesFractionalIndicesAndMissingUvWithPointers() throws Exception {
        JsonObject cycle=triangle(false);cycle.add("nodes",JsonParser.parseString("[{\"children\":[1]},{\"children\":[0]}]"));assertTrue(assertThrows(ModelProblem.class,()->load(cycle,positions(0,0,0,1,0,0,0,1,0))).location().startsWith("/nodes"));
        JsonObject bad=triangle(false);bad.getAsJsonArray("nodes").get(0).getAsJsonObject().addProperty("mesh",.5);assertTrue(assertThrows(ModelProblem.class,()->load(bad,positions(0,0,0,1,0,0,0,1,0))).location().contains("mesh"));
        JsonObject uv=triangle(false);uv.add("images",JsonParser.parseString("[{\"uri\":\"color.png\"}]"));uv.add("textures",JsonParser.parseString("[{\"source\":0}]"));uv.add("materials",JsonParser.parseString("[{\"pbrMetallicRoughness\":{\"baseColorTexture\":{\"index\":0}}}]"));uv.getAsJsonArray("meshes").get(0).getAsJsonObject().getAsJsonArray("primitives").get(0).getAsJsonObject().addProperty("material",0);Files.write(directory.resolve("color.png"),PNG);assertTrue(assertThrows(ModelProblem.class,()->load(uv,positions(0,0,0,1,0,0,0,1,0))).getMessage().contains("TEXCOORD_0"));
    }
    @Test void requiredExtensionsRejectButOptionalAndAnimationAreReported() throws Exception {
        JsonObject d=triangle(false);d.add("extensionsUsed",JsonParser.parseString("[\"KHR_draco_mesh_compression\"]"));d.add("animations",new JsonArray());GltfAsset a=load(d,positions(0,0,0,1,0,0,0,1,0));assertTrue(a.report().unsupportedOptional().contains("KHR_draco_mesh_compression"));assertTrue(a.report().gaps().stream().anyMatch(x->x.contains("animation")));d.add("extensionsRequired",d.get("extensionsUsed").deepCopy());assertEquals(ModelProblem.Code.UNSUPPORTED_REQUIRED_EXTENSION,assertThrows(ModelProblem.class,()->load(d,positions(0,0,0,1,0,0,0,1,0))).code());
    }
    @Test void closesEverySourceOnFailureAndDoesNotOpenAfterCancellation() throws Exception {
        AtomicInteger opens=new AtomicInteger(),closes=new AtomicInteger();ModelResourceProvider provider=id->{opens.incrementAndGet();byte[] bytes="{}".getBytes(StandardCharsets.UTF_8);return new ModelResource(id,new ModelByteSource(){public long size(){return bytes.length;}public ByteBuffer read(long off,int size,ModelCancellation c){return ByteBuffer.wrap(bytes,(int)off,size).slice().order(ByteOrder.LITTLE_ENDIAN);}public void close(){closes.incrementAndGet();}});};assertThrows(ModelProblem.class,()->new GltfImporter().load(provider,"bad.gltf",new ModelCancellation()));assertEquals(1,closes.get());ModelCancellation canceled=new ModelCancellation();canceled.cancel();assertThrows(ModelProblem.class,()->new GltfImporter().load(provider,"bad.gltf",canceled));assertEquals(1,opens.get());
    }
    @Test void safeLogicalResolutionRejectsEncodedTraversalAndSchemes() throws Exception {
        assertEquals("textures/a.png",ModelResourcePaths.resolve("models/a.gltf","../textures/a.png"));assertThrows(ModelProblem.class,()->ModelResourcePaths.resolve("a.gltf","%2e%2e/secret"));assertThrows(ModelProblem.class,()->ModelResourcePaths.resolve("a.gltf","https://example.org/a"));assertThrows(ModelProblem.class,()->ModelResourcePaths.resolve("a.gltf","%5csecret"));
    }
    @Test void optionalManualFixturesImportWithoutStartingGame() throws Exception {
        Path fixtures=Path.of("docs/model/fixtures");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isDirectory(fixtures));
        for(String folder:List.of("P2","m8","P5")) {
            try(var files=Files.list(fixtures.resolve(folder))) {
                for(Path file:files.filter(f->f.toString().endsWith(".glb")||f.toString().endsWith(".gltf")).toList()) {
                    GltfAsset asset=new GltfImporter().load(new FileModelResourceProvider(file.getParent()),file.getFileName().toString(),new ModelCancellation());
                    assertFalse(asset.meshes().isEmpty(),file.toString());assertFalse(asset.report().dependencies().isEmpty(),file.toString());
                    assertTrue(asset.report().dependencies().stream().allMatch(d->d.sha256().length()==64),file.toString());
                }
            }
        }
    }
}
