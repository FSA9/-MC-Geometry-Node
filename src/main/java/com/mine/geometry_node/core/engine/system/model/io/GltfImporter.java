package com.mine.geometry_node.core.engine.system.model.io;

import com.google.gson.*;
import com.mine.geometry_node.core.engine.system.model.asset.*;
import com.mine.geometry_node.core.engine.system.model.diagnostic.ModelProblem;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;

/** CPU glTF 2.0 importer for the STATIC_BASIC profile. It owns no renderer state. */
public final class GltfImporter {
    private static final Set<String> SUPPORTED_REQUIRED = Set.of("KHR_texture_transform", "KHR_materials_unlit");
    private final ModelLimits limits;
    public GltfImporter() { this(ModelLimits.DEFAULT); }
    public GltfImporter(ModelLimits limits) { this.limits = Objects.requireNonNull(limits); }
    public GltfAsset load(ModelResourceProvider provider, String entry, ModelCancellation token) throws IOException {
        Objects.requireNonNull(provider); Objects.requireNonNull(entry); Objects.requireNonNull(token);
        try (GltfResourceSession session = new GltfResourceSession(provider, limits, token)) {
            String rootId = ModelResourcePaths.normalize(entry);
            ModelByteSource root = session.open(rootId); token.check();
            JsonObject document; ModelByteSource glbBin = null;
            if (rootId.toLowerCase(Locale.ROOT).endsWith(".glb")) {
                GlbContainerReader.Container container = new GlbContainerReader().read(root, limits, token);
                document = container.json();
                if (container.binary().isPresent()) {
                    GlbContainerReader.Chunk chunk = container.binary().get();
                    glbBin = new RangeModelSource(root, chunk.offset(), chunk.length());
                }
            } else {
                if (root.size() > limits.jsonBytes()) throw budget("JSON", "JSON exceeds configured limit");
                String text = readUtf8(root, token); document = GlbContainerReader.parseJson(text, token);
                validateVersion(document);
            }
            GlbContainerReader.requireExtensions(document, SUPPORTED_REQUIRED);
            List<String> used = GltfJson.strings(document, "extensionsUsed"), required = GltfJson.strings(document, "extensionsRequired");
            ArrayList<String> optional = new ArrayList<>(), degraded = new ArrayList<>(), gaps = new ArrayList<>();
            for (String extension : used) if (!SUPPORTED_REQUIRED.contains(extension)) optional.add(extension);
            if (document.has("skins")) gaps.add("skin: deferred to P10");
            if (document.has("animations")) gaps.add("animation: deferred to P9");
            JsonArray meshArray = GltfJson.array(document, "meshes", "");
            for (int i = 0; i < meshArray.size(); i++) {
                JsonObject mesh = GltfJson.at(meshArray, i, "/meshes");
                JsonArray ps = GltfJson.array(mesh, "primitives", "/meshes/" + i);
                for (int j = 0; j < ps.size(); j++) if (GltfJson.at(ps, j, "/meshes/" + i + "/primitives").has("targets")) gaps.add("morph: deferred to P10");
            }
            GltfImportBudget budget = new GltfImportBudget(limits);
            ArrayList<ModelByteSource> buffers = readBuffers(document, rootId, glbBin, session, token, budget, limits);
            validateBufferViews(document, buffers);
            List<GltfAsset.Material> materials = GltfMaterialReader.read(document);
            GltfAsset.Material defaultMaterial = GltfMaterialReader.defaultMaterial();
            List<GltfAsset.Texture> textures = GltfTextureReader.read(document, rootId, session, buffers, budget, token, limits);
            GltfMaterialReader.validateTextures(materials, textures.size());
            if (!materials.isEmpty()) degraded.add("STATIC_BASIC preserves PBR factors and textures; metallic/roughness, normal mapping and AO lighting are deferred to P7");
            List<GltfAsset.Mesh> meshes = new GltfGeometryReader(document, buffers, limits, token, budget, degraded).read(materials, defaultMaterial);
            GltfSceneReader.Result scenes = GltfSceneReader.read(document, meshes.size(), budget, token, gaps);
            if (materials.isEmpty()) materials = List.of(defaultMaterial);
            long decoded = budget.retained();
            String minVersion = ""; JsonObject asset = GltfJson.optionalObject(document, "asset", "");
            minVersion = GltfJson.string(asset, "minVersion", "", "/asset");
            GltfAsset.Report report = new GltfAsset.Report("2.0", minVersion, used, required, optional, degraded, gaps, session.encodedBytes(), decoded, session.dependencies());
            return new GltfAsset(rootId, scenes.scenes(), scenes.defaultScene(), scenes.nodes(), meshes, materials, textures, report);
        }
    }
    private static ArrayList<ModelByteSource> readBuffers(JsonObject doc, String entry, ModelByteSource glbBin,
                                                           GltfResourceSession session, ModelCancellation token, GltfImportBudget budget, ModelLimits limits) throws IOException {
        ArrayList<ModelByteSource> result = new ArrayList<>(); JsonArray array = GltfJson.array(doc, "buffers", "");
        Map<String,ModelByteSource> embedded = new HashMap<>();
        for (int i = 0; i < array.size(); i++) {
            token.check(); String path = "/buffers/" + i; JsonObject b = GltfJson.at(array, i, "/buffers");
            long declared = GltfJson.number(b, "byteLength", -1, path); if (declared < 1) throw GltfJson.invalid(path, "byteLength is required");
            String uri = GltfJson.string(b, "uri", null, path); ModelByteSource source;
            if (uri == null) {
                if (i != 0 || glbBin == null) throw GltfJson.invalid(path, "URI is required outside the GLB BIN chunk"); source = glbBin;
            } else {
                GltfResourceSession.DataUri data = GltfResourceSession.dataUri(uri, limits, token);
                if (data != null) { if (!Set.of("application/octet-stream", "application/gltf-buffer").contains(data.mimeType())) throw GltfJson.invalid(path, "data URI buffer MIME must be application/octet-stream or application/gltf-buffer"); if (data.bytes().length != declared) throw GltfJson.invalid(path, "data URI length differs from byteLength"); source = embedded.get(uri); if (source == null) { budget.reserve(data.bytes().length, path); source = new InMemoryModelSource(data.bytes()); embedded.put(uri, source); } }
                else source = session.open(ModelResourcePaths.resolve(entry, uri));
            }
            if (source.size() < declared) throw GltfJson.invalid(path, "source shorter than declared byteLength");
            if (uri == null && source.size() - declared > 3) throw GltfJson.invalid(path, "GLB BIN padding exceeds three bytes");
            result.add(new RangeModelSource(source, 0, declared));
        }
        return result;
    }
    private static void validateBufferViews(JsonObject document, List<ModelByteSource> buffers) throws ModelProblem {
        JsonArray views = GltfJson.array(document, "bufferViews", "");
        for (int i=0;i<views.size();i++) {
            String path="/bufferViews/"+i; JsonObject view=GltfJson.at(views,i,"/bufferViews");
            int buffer=GltfJson.index(view,"buffer",-1,path); long offset=GltfJson.number(view,"byteOffset",0,path), length=GltfJson.number(view,"byteLength",-1,path);
            if(buffer<0||buffer>=buffers.size()||offset<0||length<1||offset>buffers.get(buffer).size()||length>buffers.get(buffer).size()-offset)
                throw new ModelProblem(ModelProblem.Code.INVALID_ACCESSOR,path,"bufferView exceeds declared buffer byteLength");
            if(view.has("byteStride")) {int stride=GltfJson.index(view,"byteStride",0,path);if(stride<4||stride>252||stride%4!=0)throw new ModelProblem(ModelProblem.Code.INVALID_ACCESSOR,path+"/byteStride","stride must be a multiple of four in [4,252]");}
            if(view.has("target")) {int target=GltfJson.index(view,"target",0,path);if(target!=34962&&target!=34963)throw new ModelProblem(ModelProblem.Code.INVALID_ACCESSOR,path+"/target","invalid GPU buffer target");}
        }
    }
    private static String readUtf8(ModelByteSource source, ModelCancellation token) throws IOException { ByteBuffer buffer = source.read(0, (int)source.size(), token); return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(buffer).toString(); }
    private static void validateVersion(JsonObject document) throws ModelProblem { JsonObject asset = GltfJson.optionalObject(document, "asset", ""); if (!"2.0".equals(GltfJson.string(asset, "version", "", "/asset"))) throw GltfJson.invalid("/asset/version", "only glTF 2.0 is supported"); if (asset.has("minVersion") && !"2.0".equals(GltfJson.string(asset, "minVersion", "", "/asset"))) throw GltfJson.invalid("/asset/minVersion", "unsupported minimum version"); }
    private static ModelProblem budget(String location, String detail) { return new ModelProblem(ModelProblem.Code.BUDGET_EXCEEDED, location, detail); }
}
