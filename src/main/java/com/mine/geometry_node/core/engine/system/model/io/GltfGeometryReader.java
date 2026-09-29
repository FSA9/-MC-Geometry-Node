package com.mine.geometry_node.core.engine.system.model.io;

import com.google.gson.*;
import com.mine.geometry_node.core.engine.system.model.asset.*;
import com.mine.geometry_node.core.engine.system.model.asset.ModelLimits;
import com.mine.geometry_node.core.engine.system.model.diagnostic.ModelProblem;
import java.io.IOException;
import java.util.*;

/** Validates vertex semantics, resolves accessors, and compiles all supported topology to triangles. */
final class GltfGeometryReader {
    private final JsonObject document;
    private final GltfAccessorReader accessorReader;
    private final JsonArray accessors;
    private final ModelCancellation token;
    private final GltfImportBudget budget;
    private final List<String> notes;
    GltfGeometryReader(JsonObject document, List<ModelByteSource> buffers, ModelLimits limits, ModelCancellation token,
                       GltfImportBudget budget, List<String> notes) throws ModelProblem {
        this.document = document; this.token = token; this.budget = budget; this.notes = notes;
        accessors = GltfJson.array(document, "accessors", "");
        accessorReader = new GltfAccessorReader(document, buffers, limits, token);
    }
    List<GltfAsset.Mesh> read(List<GltfAsset.Material> materials, GltfAsset.Material defaultMaterial) throws IOException {
        JsonArray meshes = GltfJson.array(document, "meshes", ""); ArrayList<GltfAsset.Mesh> result = new ArrayList<>();
        for (int mi = 0; mi < meshes.size(); mi++) {
            token.check(); String path = "/meshes/" + mi; JsonObject mesh = GltfJson.at(meshes, mi, "/meshes");
            JsonArray primitives = GltfJson.array(mesh, "primitives", path);
            if (primitives.isEmpty()) throw GltfJson.invalid(path, "mesh must contain primitives");
            ArrayList<GltfAsset.Primitive> compiled = new ArrayList<>();
            for (int pi = 0; pi < primitives.size(); pi++)
                compiled.add(primitive(GltfJson.at(primitives, pi, path + "/primitives"), path + "/primitives/" + pi, materials, defaultMaterial));
            result.add(new GltfAsset.Mesh(GltfJson.string(mesh, "name", "", path), compiled));
        }
        return List.copyOf(result);
    }
    private GltfAsset.Primitive primitive(JsonObject primitive, String path, List<GltfAsset.Material> materials,
                                          GltfAsset.Material defaultMaterial) throws IOException {
        token.check(); JsonObject attributes = GltfJson.optionalObject(primitive, "attributes", path);
        if (!attributes.has("POSITION")) throw GltfJson.invalid(path + "/attributes", "POSITION is required by STATIC_BASIC");
        Attribute position = attribute(attributes, "POSITION", -1, path);
        int count = position.count;
        Attribute normal = attribute(attributes, "NORMAL", count, path), tangent = attribute(attributes, "TANGENT", count, path);
        Attribute uv0 = attribute(attributes, "TEXCOORD_0", count, path), uv1 = attribute(attributes, "TEXCOORD_1", count, path);
        Attribute color = attribute(attributes, "COLOR_0", count, path);
        for (String semantic : attributes.keySet()) if (!Set.of("POSITION", "NORMAL", "TANGENT", "TEXCOORD_0", "TEXCOORD_1", "COLOR_0").contains(semantic))
            notes.add(path + ": attribute " + semantic + " retained only in source; STATIC_BASIC ignores it");
        int mode = GltfJson.index(primitive, "mode", 4, path);
        GltfAsset.PrimitiveMode sourceMode = switch (mode) {
            case 4 -> GltfAsset.PrimitiveMode.TRIANGLES;
            case 5 -> GltfAsset.PrimitiveMode.TRIANGLE_STRIP;
            case 6 -> GltfAsset.PrimitiveMode.TRIANGLE_FAN;
            default -> throw GltfJson.invalid(path + "/mode", "STATIC_BASIC supports only triangles, triangle strip and triangle fan");
        };
        int indexAccessor = GltfJson.index(primitive, "indices", -1, path), indexCount = count;
        if (indexAccessor >= 0) {
            JsonObject metadata = GltfJson.at(accessors, indexAccessor, "/accessors");
            int component = GltfJson.index(metadata, "componentType", -1, "/accessors/" + indexAccessor);
            if (!"SCALAR".equals(GltfJson.string(metadata, "type", "", path)) || !Set.of(5121,5123,5125).contains(component)
                    || GltfJson.bool(metadata, "normalized", false, path)) throw GltfJson.invalid(path + "/indices", "indices require non-normalized unsigned integer SCALAR");
            indexCount = positiveCount(metadata, "/accessors/" + indexAccessor);
        }
        if (indexCount < 3 || mode == 4 && indexCount % 3 != 0) throw GltfJson.invalid(path, "invalid vertex/index count for triangle topology");
        long triangleIndices = mode == 4 ? indexCount : (long)(indexCount - 2) * 3;
        if (triangleIndices > Integer.MAX_VALUE) throw GltfJson.invalid(path, "triangulated index count exceeds supported array size");
        int outputVertices = normal == null ? (int)triangleIndices : count;
        long floatsPerVertex = 6 + (tangent == null ? 0 : 4) + (uv0 == null ? 0 : 2) + (uv1 == null ? 0 : 2) + (color == null ? 0 : 4);
        budget.reserve((long)outputVertices * floatsPerVertex * 4 + triangleIndices * 4, path);
        int[] indices = indexAccessor < 0 ? sequential(count) : indices(accessorReader.read(indexAccessor), count, path);
        indices = triangulate(indices, mode);
        float[] positions = read(position), normals = read(normal), tangents = read(tangent), tex0 = read(uv0), tex1 = read(uv1), colors = read(color);
        if (color != null && color.components == 3) colors = rgba(colors, count);
        if (normal == null) {
            // glTF specifies flat face normals when NORMAL is absent. Shared indices must split here.
            positions = expand(positions, 3, indices); tangents = expand(tangents, 4, indices);
            tex0 = expand(tex0, 2, indices); tex1 = expand(tex1, 2, indices); colors = expand(colors, 4, indices);
            normals = flatNormals(positions); indices = sequential(outputVertices);
            notes.add(path + ": missing NORMAL generated as flat face normals");
        }
        GltfAsset.Material material = defaultMaterial;
        if (primitive.has("material")) {
            int index = GltfJson.index(primitive, "material", -1, path);
            if (index >= materials.size()) throw GltfJson.invalid(path + "/material", "material index out of range");
            material = materials.get(index);
        }
        validateTextureCoordinates(material, uv0, uv1, path);
        GltfAsset.Bounds bounds = GltfAsset.Bounds.empty();
        for (int i = 0; i < positions.length; i += 3) bounds = bounds.include(positions[i], positions[i+1], positions[i+2]);
        return new GltfAsset.Primitive(positions, normals, tangents, tex0, tex1, colors, indices, material, sourceMode, bounds);
    }
    private void validateTextureCoordinates(GltfAsset.Material material, Attribute uv0, Attribute uv1, String path) throws ModelProblem {
        GltfAsset.TextureInfo[] infos = {material.baseColorTexture(), material.metallicRoughnessTexture(), material.normalTexture(), material.occlusionTexture(), material.emissiveTexture()};
        for (GltfAsset.TextureInfo info : infos) if (info != null) {
            if (info.texCoord() == 0 && uv0 == null || info.texCoord() == 1 && uv1 == null)
                throw GltfJson.invalid(path + "/attributes", "material references missing TEXCOORD_" + info.texCoord());
            if (info.texCoord() > 1) throw GltfJson.invalid(path + "/attributes", "STATIC_BASIC supports TEXCOORD_0 and TEXCOORD_1; requested TEXCOORD_" + info.texCoord());
        }
    }
    private record Attribute(int accessor, int count, int components) { }
    private Attribute attribute(JsonObject attrs, String semantic, int expectedCount, String path) throws ModelProblem {
        if (!attrs.has(semantic)) return null;
        int index = GltfJson.index(attrs.get(semantic), path + "/attributes/" + semantic);
        JsonObject a = GltfJson.at(accessors, index, "/accessors"); String loc = "/accessors/" + index;
        int count = positiveCount(a, loc), component = GltfJson.index(a, "componentType", -1, loc);
        String type = GltfJson.string(a, "type", "", loc); int width;
        boolean normalized = GltfJson.bool(a, "normalized", false, loc);
        switch (semantic) {
            case "POSITION", "NORMAL" -> { width = 3; if (!type.equals("VEC3") || component != 5126 || normalized) throw GltfJson.invalid(loc, semantic + " requires FLOAT VEC3"); }
            case "TANGENT" -> { width = 4; if (!type.equals("VEC4") || component != 5126 || normalized) throw GltfJson.invalid(loc, "TANGENT requires FLOAT VEC4"); }
            case "TEXCOORD_0", "TEXCOORD_1" -> { width = 2; if (!type.equals("VEC2") || !validColorComponent(component, normalized)) throw GltfJson.invalid(loc, semantic + " requires FLOAT or normalized unsigned byte/short VEC2"); }
            case "COLOR_0" -> { width = type.equals("VEC3") ? 3 : type.equals("VEC4") ? 4 : 0; if (width == 0 || !validColorComponent(component, normalized)) throw GltfJson.invalid(loc, "COLOR_0 requires FLOAT or normalized unsigned byte/short VEC3/VEC4"); }
            default -> throw new IllegalArgumentException(semantic);
        }
        if (expectedCount >= 0 && count != expectedCount) throw GltfJson.invalid(loc, "attribute count differs from POSITION");
        return new Attribute(index, count, width);
    }
    private static boolean validColorComponent(int type, boolean normalized) { return type == 5126 && !normalized || (type == 5121 || type == 5123) && normalized; }
    private static int positiveCount(JsonObject a, String path) throws ModelProblem {
        int count = GltfJson.index(a, "count", 0, path); if (count < 1) throw GltfJson.invalid(path + "/count", "positive count required"); return count;
    }
    private float[] read(Attribute attribute) throws IOException {
        if (attribute == null) return new float[0]; double[] values = accessorReader.read(attribute.accessor); float[] output = new float[values.length];
        for (int i = 0; i < values.length; i++) {
            if ((i & 16383) == 0) token.check(); float value = (float)values[i];
            if (!Float.isFinite(value)) throw GltfJson.invalid("/accessors/" + attribute.accessor, "component exceeds float range"); output[i] = value;
        }
        return output;
    }
    private int[] indices(double[] source, int vertexCount, String path) throws IOException {
        int[] output = new int[source.length]; for (int i = 0; i < source.length; i++) {
            if ((i & 16383) == 0) token.check(); double v = source[i];
            if (v < 0 || v >= vertexCount || v != Math.rint(v)) throw GltfJson.invalid(path + "/indices", "index outside POSITION count"); output[i] = (int)v;
        } return output;
    }
    private static int[] sequential(int count) { int[] r = new int[count]; for (int i = 0; i < count; i++) r[i] = i; return r; }
    private int[] triangulate(int[] source, int mode) throws IOException {
        if (mode == 4) return source; int[] out = new int[(source.length - 2) * 3];
        for (int triangle = 0; triangle < source.length - 2; triangle++) {
            if ((triangle & 16383) == 0) token.check(); int at = triangle * 3;
            int a = mode == 6 ? source[0] : source[triangle], b = source[triangle + 1], c = source[triangle + 2];
            if (mode == 5 && (triangle & 1) != 0) { int swap = a; a = b; b = swap; }
            out[at] = a; out[at + 1] = b; out[at + 2] = c;
        } return out;
    }
    private static float[] rgba(float[] rgb, int count) { float[] r = new float[count * 4]; for (int i = 0; i < count; i++) { System.arraycopy(rgb, i * 3, r, i * 4, 3); r[i * 4 + 3] = 1; } return r; }
    private float[] expand(float[] values, int width, int[] indices) throws IOException {
        if (values.length == 0) return values; float[] out = new float[indices.length * width];
        for (int i = 0; i < indices.length; i++) { if ((i & 16383) == 0) token.check(); System.arraycopy(values, indices[i] * width, out, i * width, width); } return out;
    }
    private float[] flatNormals(float[] p) throws IOException {
        float[] normals = new float[p.length];
        for (int a = 0; a < p.length; a += 9) {
            if ((a & 16383) == 0) token.check(); double ax = (double)p[a+3]-p[a], ay=(double)p[a+4]-p[a+1], az=(double)p[a+5]-p[a+2];
            double bx=(double)p[a+6]-p[a], by=(double)p[a+7]-p[a+1], bz=(double)p[a+8]-p[a+2];
            double nx=ay*bz-az*by, ny=az*bx-ax*bz, nz=ax*by-ay*bx, length=Math.sqrt(nx*nx+ny*ny+nz*nz);
            if (length > 0) { nx/=length; ny/=length; nz/=length; } else { nx=0; ny=1; nz=0; }
            for (int vertex=0; vertex<3; vertex++) { int at=a+vertex*3; normals[at]=(float)nx; normals[at+1]=(float)ny; normals[at+2]=(float)nz; }
        } return normals;
    }
}
