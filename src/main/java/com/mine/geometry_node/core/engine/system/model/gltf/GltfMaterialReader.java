package com.mine.geometry_node.core.engine.system.model.gltf;

import com.google.gson.*;
import com.mine.geometry_node.core.engine.system.model.asset.*;
import com.mine.geometry_node.core.engine.system.model.diagnostic.ModelProblem;
import java.io.IOException;
import java.util.*;

/** Pure material descriptions remain independent of render-pipeline bindings. */
final class GltfMaterialReader {
    static List<GltfAsset.Material> read(JsonObject doc) throws ModelProblem {
        JsonArray array = GltfJson.array(doc, "materials", ""); if (array.isEmpty()) return List.of(); ArrayList<GltfAsset.Material> out = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) { String path = "/materials/" + i; JsonObject m = GltfJson.at(array, i, "/materials"); JsonObject pbr = GltfJson.optionalObject(m, "pbrMetallicRoughness", path);
            String alpha = GltfJson.string(m, "alphaMode", "OPAQUE", path); GltfAsset.AlphaMode mode; try { mode = GltfAsset.AlphaMode.valueOf(alpha); } catch (IllegalArgumentException e) { throw GltfJson.invalid(path + "/alphaMode", "expected OPAQUE, MASK or BLEND"); }
            float cutoff = GltfJson.scalar(m, "alphaCutoff", .5f, path); if (cutoff < 0) throw GltfJson.invalid(path + "/alphaCutoff", "must be nonnegative");
            float[] base = GltfJson.vector(pbr, "baseColorFactor", new float[]{1,1,1,1}, path + "/pbrMetallicRoughness"), emissive = GltfJson.vector(m, "emissiveFactor", new float[]{0,0,0}, path);
            boolean unlit = m.has("extensions") && GltfJson.optionalObject(m, "extensions", path).has("KHR_materials_unlit");
            JsonObject normal = GltfJson.optionalObject(m, "normalTexture", path), ao = GltfJson.optionalObject(m, "occlusionTexture", path);
            out.add(new GltfAsset.Material(GltfJson.string(m, "name", "material_" + i, path), mode, cutoff, GltfJson.bool(m, "doubleSided", false, path), unlit, base,
                    GltfJson.scalar(pbr, "metallicFactor", 1, path + "/pbrMetallicRoughness"), GltfJson.scalar(pbr, "roughnessFactor", 1, path + "/pbrMetallicRoughness"), emissive,
                    GltfJson.scalar(normal, "scale", 1, path + "/normalTexture"), GltfJson.scalar(ao, "strength", 1, path + "/occlusionTexture"),
                    textureInfo(pbr, "baseColorTexture", path), textureInfo(pbr, "metallicRoughnessTexture", path), textureInfo(m, "normalTexture", path), textureInfo(m, "occlusionTexture", path), textureInfo(m, "emissiveTexture", path), extensions(m,path), emissiveStrength(m,path)));
        }
        return out;
    }
    private static float emissiveStrength(JsonObject material,String path) throws ModelProblem {
        JsonObject extensions=GltfJson.optionalObject(material,"extensions",path);
        JsonObject strength=GltfJson.optionalObject(extensions,"KHR_materials_emissive_strength",path+"/extensions");
        float value=GltfJson.scalar(strength,"emissiveStrength",1,path+"/extensions/KHR_materials_emissive_strength");
        if(value<0) throw GltfJson.invalid(path+"/extensions/KHR_materials_emissive_strength/emissiveStrength","must be nonnegative");
        return value;
    }
    private static Map<String,String> extensions(JsonObject material,String path) throws ModelProblem {
        LinkedHashMap<String,String> result=new LinkedHashMap<>();
        for(var entry:GltfJson.optionalObject(material,"extensions",path).entrySet()) result.put(entry.getKey(),entry.getValue().toString());
        return Map.copyOf(result);
    }
    private static GltfAsset.TextureInfo textureInfo(JsonObject owner, String key, String path) throws ModelProblem {
        if (!owner.has(key)) return null; JsonObject info = GltfJson.object(owner.get(key), path + "/" + key); int index = GltfJson.index(info, "index", -1, path + "/" + key);
        int coord = GltfJson.index(info, "texCoord", 0, path + "/" + key); float[] offset = new float[]{0,0}, scale = new float[]{1,1}; float rotation = 0;
        if (info.has("extensions")) { JsonObject extensions = GltfJson.optionalObject(info, "extensions", path + "/" + key); if (extensions.has("KHR_texture_transform")) { JsonObject transform = GltfJson.optionalObject(extensions, "KHR_texture_transform", path + "/" + key + "/extensions"); offset = GltfJson.vector(transform, "offset", offset, path); scale = GltfJson.vector(transform, "scale", scale, path); rotation = GltfJson.scalar(transform, "rotation", 0, path); coord = GltfJson.index(transform, "texCoord", coord, path); } }
        return new GltfAsset.TextureInfo(index, coord, offset, scale, rotation);
    }
    static void validateTextures(List<GltfAsset.Material> materials, int textureCount) throws ModelProblem {
        for (int i=0;i<materials.size();i++) {
            GltfAsset.Material m=materials.get(i);
            for (float v:m.baseColorFactor()) if(v<0||v>1) throw GltfJson.invalid("/materials/"+i+"/baseColorFactor", "factor must be within [0,1]");
            for (float v:m.emissiveFactor()) if(v<0||v>1) throw GltfJson.invalid("/materials/"+i+"/emissiveFactor", "factor must be within [0,1]");
            if(m.metallicFactor()<0||m.metallicFactor()>1||m.roughnessFactor()<0||m.roughnessFactor()>1||m.occlusionStrength()<0||m.occlusionStrength()>1)
                throw GltfJson.invalid("/materials/"+i, "metallic/roughness/occlusion factors must be within [0,1]");
            GltfAsset.TextureInfo[] infos={m.baseColorTexture(),m.metallicRoughnessTexture(),m.normalTexture(),m.occlusionTexture(),m.emissiveTexture()};
            for(GltfAsset.TextureInfo info:infos) if(info!=null&&(info.textureIndex()<0||info.textureIndex()>=textureCount))
                throw GltfJson.invalid("/materials/"+i, "texture index out of range");
        }
    }
    static GltfAsset.Material defaultMaterial() { return new GltfAsset.Material("default", GltfAsset.AlphaMode.OPAQUE, .5f, false, false, new float[]{1,1,1,1}, 1, 1, new float[]{0,0,0}, 1, 1, null, null, null, null, null); }
}
