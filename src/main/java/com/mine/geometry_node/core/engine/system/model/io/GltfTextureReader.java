package com.mine.geometry_node.core.engine.system.model.io;

import com.google.gson.*;
import com.mine.geometry_node.core.engine.system.model.asset.*;
import com.mine.geometry_node.core.engine.system.model.diagnostic.ModelProblem;
import java.io.IOException;
import java.util.*;

/** Encoded image ownership and texture/sampler identity are independent. */
final class GltfTextureReader {
    static List<GltfAsset.Texture> read(JsonObject doc, String entry, GltfResourceSession session,
                                                          List<ModelByteSource> buffers, GltfImportBudget budget, ModelCancellation token, ModelLimits limits) throws IOException {
        JsonArray images = GltfJson.array(doc, "images", ""), textures = GltfJson.array(doc, "textures", ""), samplers = GltfJson.array(doc, "samplers", "");
        
        ArrayList<GltfAsset.Image> decodedImages = new ArrayList<>(); Map<String,GltfAsset.Image> sharedImages = new HashMap<>();
        for (int i = 0; i < images.size(); i++) {
            token.check(); String path = "/images/" + i; JsonObject image = GltfJson.at(images, i, "/images");
            String uri = GltfJson.string(image, "uri", null, path);
            String mime = GltfJson.string(image, "mimeType", "application/octet-stream", path);
            if (uri != null && image.has("bufferView")) throw GltfJson.invalid(path, "uri and bufferView are mutually exclusive");
            String id;
            if (uri != null) id = uri.startsWith("data:") ? "data:sha256:" + digest(uri) : ModelResourcePaths.resolve(entry, uri);
            else if (image.has("bufferView")) id = "bufferView:" + GltfJson.index(image, "bufferView", -1, path);
            else throw GltfJson.invalid(path, "image requires uri or bufferView");
            GltfAsset.Image shared = sharedImages.get(id);
            if (shared == null) {
                byte[] bytes;
                if (uri != null) {
                    GltfResourceSession.DataUri data = GltfResourceSession.dataUri(uri, limits, token);
                    if (data != null) {
                        budget.reserve(data.bytes().length, path); bytes = data.bytes();
                        if (mime.equals("application/octet-stream") && !data.mimeType().isBlank()) mime = data.mimeType();
                    } else {
                        ModelByteSource source = session.open(id); bytes = session.readBytes(source, 0, source.size(), path, budget);
                    }
                } else {
                    if (!image.has("mimeType")) throw GltfJson.invalid(path, "bufferView image requires mimeType");
                    int viewIndex = GltfJson.index(image, "bufferView", -1, path);
                    JsonObject view = GltfJson.at(GltfJson.array(doc, "bufferViews", ""), viewIndex, "/bufferViews");
                    int buffer = GltfJson.index(view, "buffer", -1, "/bufferViews/" + viewIndex);
                    if (buffer < 0 || buffer >= buffers.size() || view.has("byteStride")) throw GltfJson.invalid(path, "image bufferView must reference a noninterleaved valid buffer");
                    long offset = GltfJson.number(view, "byteOffset", 0, "/bufferViews/" + viewIndex);
                    long length = GltfJson.number(view, "byteLength", -1, "/bufferViews/" + viewIndex);
                    bytes = session.readBytes(buffers.get(buffer), offset, length, path, budget);
                }
                mime = validateMime(bytes, mime, path); shared = new GltfAsset.Image(mime, id, bytes); sharedImages.put(id, shared);
            } else if (!mime.equals("application/octet-stream") && !mime.equals(shared.mimeType())) {
                throw GltfJson.invalid(path + "/mimeType", "shared image MIME declarations disagree");
            }
            decodedImages.add(shared);
        }
        ArrayList<GltfAsset.Texture> out = new ArrayList<>();
        for (int i = 0; i < textures.size(); i++) {
            String path = "/textures/" + i; JsonObject texture = GltfJson.at(textures, i, "/textures"); int image = GltfJson.index(texture, "source", -1, path);
            if (image < 0 || image >= decodedImages.size()) throw GltfJson.invalid(path + "/source", "image index out of range");
            int samplerIndex = GltfJson.index(texture, "sampler", -1, path); GltfAsset.Sampler sampler = new GltfAsset.Sampler(9729, 9987, 10497, 10497);
            if (samplerIndex >= 0) { JsonObject s = GltfJson.at(samplers, samplerIndex, "/samplers"); sampler = new GltfAsset.Sampler(GltfJson.index(s, "magFilter", 9729, "/samplers/" + samplerIndex), GltfJson.index(s, "minFilter", 9987, "/samplers/" + samplerIndex), GltfJson.index(s, "wrapS", 10497, "/samplers/" + samplerIndex), GltfJson.index(s, "wrapT", 10497, "/samplers/" + samplerIndex)); }
            validateSampler(sampler, path);
            out.add(new GltfAsset.Texture(GltfJson.string(texture, "name", "texture_" + i, path), decodedImages.get(image), sampler));
        }
        return List.copyOf(out);
    }
    private static void validateSampler(GltfAsset.Sampler s, String path) throws ModelProblem {
        if (!Set.of(9728,9729).contains(s.magFilter()) || !Set.of(9728,9729,9984,9985,9986,9987).contains(s.minFilter())
                || !Set.of(33071,33648,10497).contains(s.wrapS()) || !Set.of(33071,33648,10497).contains(s.wrapT()))
            throw GltfJson.invalid(path + "/sampler", "invalid glTF filter or wrap enum");
    }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static String validateMime(byte[] bytes, String declared, String path) throws ModelProblem {
        boolean png = bytes.length >= 8 && bytes[0] == (byte)137 && bytes[1] == 80 && bytes[2] == 78 && bytes[3] == 71
                && bytes[4] == 13 && bytes[5] == 10 && bytes[6] == 26 && bytes[7] == 10;
        boolean jpeg = bytes.length >= 3 && bytes[0] == (byte)255 && bytes[1] == (byte)216 && bytes[2] == (byte)255;
        String actual = png ? "image/png" : jpeg ? "image/jpeg" : null;
        if (actual == null || !declared.equals("application/octet-stream") && !declared.equals(actual))
            throw GltfJson.invalid(path + "/mimeType", "expected PNG/JPEG payload consistent with declared MIME");
        return actual;
    }
}
