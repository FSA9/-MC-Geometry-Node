package com.mine.geometry_node.core.engine.system.model.gltf;

import com.mine.geometry_node.core.engine.system.model.io.ModelByteSource;
import com.mine.geometry_node.core.engine.system.model.io.ModelCancellation;
import com.mine.geometry_node.core.engine.system.model.io.ModelResource;
import com.mine.geometry_node.core.engine.system.model.io.ModelResourcePaths;
import com.mine.geometry_node.core.engine.system.model.io.ModelResourceProvider;

import com.mine.geometry_node.core.engine.system.model.asset.ModelLimits;
import com.mine.geometry_node.core.engine.system.model.asset.GltfAsset;
import com.mine.geometry_node.core.engine.system.model.diagnostic.ModelProblem;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;

/** Owns deduplicated source handles and the complete encoded dependency budget for one import. */
final class GltfResourceSession implements AutoCloseable {
    record DataUri(String mimeType, byte[] bytes) { }
    private final ModelResourceProvider provider;
    private final ModelLimits limits;
    private final ModelCancellation token;
    private final LinkedHashMap<String,ModelResource> resources = new LinkedHashMap<>();
    private long encodedBytes;
    GltfResourceSession(ModelResourceProvider provider, ModelLimits limits, ModelCancellation token) {
        this.provider = provider; this.limits = limits; this.token = token;
    }
    ModelByteSource open(String logicalId) throws IOException {
        token.check(); String id = ModelResourcePaths.normalize(logicalId);
        ModelResource existing = resources.get(id); if (existing != null) return existing.source();
        if (resources.size() >= limits.dependencies()) throw budget(id, "dependency count exceeded");
        ModelResource resource;
        try { resource = provider.open(id); }
        catch (ModelProblem e) { throw e; }
        catch (IOException e) { throw new ModelProblem(ModelProblem.Code.INVALID_SOURCE, id, e.getMessage()); }
        boolean adopted = false;
        try {
            token.check();
            if (resource.encodedBytes() < 0 || resource.encodedBytes() > limits.packageBytes() - encodedBytes)
                throw budget(id, "encoded dependency closure exceeds " + limits.packageBytes() + " bytes");
            encodedBytes += resource.encodedBytes(); resources.put(id, resource); adopted = true;
            return resource.source();
        } finally { if (!adopted) resource.close(); }
    }
    long encodedBytes() { return encodedBytes; }
    List<GltfAsset.Dependency> dependencies() throws IOException {
        ArrayList<GltfAsset.Dependency> result = new ArrayList<>();
        for (var entry : resources.entrySet()) {
            token.check(); java.security.MessageDigest digest;
            try { digest = java.security.MessageDigest.getInstance("SHA-256"); }
            catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
            ModelByteSource source = entry.getValue().source();
            for (long at = 0; at < source.size();) {
                token.check(); int count = (int)Math.min(Math.min(64 * 1024, limits.readSliceBytes()), source.size() - at);
                digest.update(source.read(at, count, token)); at += count;
            }
            result.add(new GltfAsset.Dependency(entry.getKey(), source.size(), HexFormat.of().formatHex(digest.digest())));
        }
        return List.copyOf(result);
    }
    byte[] readBytes(ModelByteSource source, long offset, long size, String path, GltfImportBudget budget) throws IOException {
        if (offset < 0 || size < 0 || offset > source.size() || size > source.size() - offset || size > Integer.MAX_VALUE)
            throw GltfJson.invalid(path, "source range invalid");
        budget.reserve(size, path);
        byte[] bytes = new byte[(int)size];
        int slice = Math.min(64 * 1024, limits.readSliceBytes());
        for (int at = 0; at < bytes.length;) {
            token.check(); int count = Math.min(slice, bytes.length - at);
            source.read(offset + at, count, token).get(bytes, at, count); at += count;
        }
        return bytes;
    }
    static DataUri dataUri(String uri, ModelLimits limits, ModelCancellation token) throws IOException {
        if (!uri.startsWith("data:")) return null;
        int comma = uri.indexOf(','); if (comma < 0) throw GltfJson.invalid("data URI", "missing comma");
        String meta = uri.substring(5, comma), body = uri.substring(comma + 1);
        String mime = meta.split(";", 2)[0];
        long upperBound = meta.endsWith(";base64") ? (long)body.length() * 3 / 4 + 3 : body.length();
        ModelLimits.requireWithin(upperBound, Math.min(limits.jsonBytes(), limits.decodedAccessorBytes()), "data URI");
        token.check(); byte[] bytes;
        try {
            if (meta.endsWith(";base64")) bytes = Base64.getDecoder().decode(body);
            else {
                ByteArrayOutputStream output = new ByteArrayOutputStream(body.length());
                for (int i = 0; i < body.length(); i++) {
                    if ((i & 4095) == 0) token.check(); char c = body.charAt(i);
                    if (c == '%') {
                        if (i + 2 >= body.length()) throw new IllegalArgumentException();
                        int a = Character.digit(body.charAt(++i), 16), b = Character.digit(body.charAt(++i), 16);
                        if (a < 0 || b < 0) throw new IllegalArgumentException(); output.write((a << 4) | b);
                    } else { if (c > 127) throw new IllegalArgumentException(); output.write(c); }
                }
                bytes = output.toByteArray();
            }
        } catch (IllegalArgumentException e) { throw GltfJson.invalid("data URI", "invalid data URI encoding"); }
        token.check(); return new DataUri(mime, bytes);
    }
    @Override public void close() throws IOException {
        IOException first = null; ArrayList<ModelResource> reverse = new ArrayList<>(resources.values());
        for (int i = reverse.size() - 1; i >= 0; i--) try { reverse.get(i).close(); }
        catch (IOException e) { if (first == null) first = e; else first.addSuppressed(e); }
        resources.clear(); if (first != null) throw first;
    }
    private static ModelProblem budget(String path, String message) { return new ModelProblem(ModelProblem.Code.BUDGET_EXCEEDED, path, message); }
}
