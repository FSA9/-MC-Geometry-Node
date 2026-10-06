package com.mine.geometry_node.client.model.render.lod.model;

import com.mine.geometry_node.core.engine.system.model.asset.GltfAsset;
import com.mine.geometry_node.core.engine.system.model.io.ModelCancellation;

import java.io.*;
import java.nio.file.*;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.zip.CRC32C;
import java.util.zip.CheckedInputStream;
import java.util.zip.CheckedOutputStream;

/** Content-addressed model LOD cache shared across worlds in one game directory. */
public final class ModelLodCache {
    private static final int MAGIC = 0x4D4C4F44;
    private static final int VERSION = 2;
    private static final long MAX_CACHE_BYTES = 512L * 1024 * 1024;

    private ModelLodCache() {}

    public record Result(List<ModelLodMesh.Levels> meshes, long cpuBytes, Map<String, Object> report) {
        public Result { meshes = List.copyOf(meshes); report = Map.copyOf(report); }
    }

    public static synchronized Result prepare(Path cacheDirectory, List<ModelLodMesh.Input> meshes,
                                               ModelCancellation token, long additionalCpuBudget) throws IOException {
        Objects.requireNonNull(cacheDirectory);
        meshes = List.copyOf(meshes);
        if (additionalCpuBudget < 0) throw new IllegalArgumentException("negative LOD budget");
        token.check();
        long started = System.nanoTime();
        String key = key(meshes, token);
        Path target = cacheDirectory.resolve(key + ".mlod");
        String cacheFailure = "", status = "MISS_BUILT";
        try {
            if (Files.exists(target)) {
                try {
                    var loaded = read(target, key, meshes, token, additionalCpuBudget);
                    return result(loaded, meshes, key, "HIT", "", Map.of(), started);
                } catch (ModelLodSimplifier.BudgetException exhausted) {
                    token.check();
                    return result(source(meshes), meshes, key, "BUDGET_FALLBACK", "",
                            Map.of(exhausted.getMessage(), meshes.size()), started);
                } catch (IOException | RuntimeException invalid) {
                    token.check();
                    status = "REBUILT";
                    cacheFailure = invalid.getClass().getSimpleName() + ": " + invalid.getMessage();
                }
            }
        } catch (SecurityException unavailable) {
            cacheFailure = unavailable.getClass().getSimpleName() + ": " + unavailable.getMessage();
        }
        List<ModelLodMesh.Levels> built = new ArrayList<>(meshes.size());
        Map<String, Integer> fallback = new TreeMap<>();
        long retained = 0;
        for (ModelLodMesh.Input input : meshes) {
            token.check();
            ModelLodMesh.Levels levels;
            try {
                levels = ModelLodSimplifier.simplify(input, token, additionalCpuBudget - retained);
            } catch (IOException | RuntimeException | LinkageError failure) {
                token.check();
                String reason = failure instanceof ModelLodSimplifier.BudgetException
                        ? failure.getMessage() : "simplifier_" + failure.getClass().getSimpleName();
                fallback.merge(reason, 1, Integer::sum);
                levels = ModelLodMesh.Levels.source(input);
            }
            retained = Math.addExact(retained, levels.cpuBytes());
            built.add(levels);
        }
        token.check();
        if (fallback.isEmpty()) {
            try {
                write(target, key, built, token);
            } catch (IOException | RuntimeException failure) {
                token.check();
                cacheFailure = failure.getClass().getSimpleName() + ": " + failure.getMessage();
                status = "BUILT_NOT_SAVED";
            }
        } else status = "PARTIAL_FALLBACK";
        return result(built, meshes, key, status, cacheFailure, fallback, started);
    }

    static String key(List<ModelLodMesh.Input> meshes, ModelCancellation token) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var out = new DataOutputStream(new BufferedOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest)))) {
                out.writeUTF(ModelLodSimplifier.ALGORITHM);
                out.writeInt(VERSION);
                floats(out, ModelLodSimplifier.RATIOS, token);
                floats(out, ModelLodSimplifier.ERRORS, token);
                floats(out, ModelLodSimplifier.WEIGHTS, token);
                out.writeInt(ModelLodSimplifier.OPTIONS);
                out.writeInt(meshes.size());
                for (var mesh : meshes) {
                    token.check();
                    floats(out, mesh.positions(), token);
                    floats(out, mesh.normals(), token);
                    floats(out, mesh.tangents(), token);
                    floats(out, mesh.baseUv(), token);
                    floats(out, mesh.emissionUv(), token);
                    floats(out, mesh.uv0(), token);
                    floats(out, mesh.uv1(), token);
                    ints(out, mesh.colors(), token);
                    ints(out, mesh.indices(), token);
                    material(out, mesh.material(), token);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static List<ModelLodMesh.Levels> read(Path path, String key, List<ModelLodMesh.Input> inputs,
                                                   ModelCancellation token, long budget) throws IOException {
        if (Files.size(path) > MAX_CACHE_BYTES) throw new IOException("oversize LOD cache");
        CRC32C checksum = new CRC32C();
        try (var in = new DataInputStream(new CheckedInputStream(new BufferedInputStream(Files.newInputStream(path)), checksum))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION || !in.readUTF().equals(key) || in.readInt() != inputs.size())
                throw new IOException("LOD cache header mismatch");
            List<ModelLodMesh.Levels> meshes = new ArrayList<>(inputs.size());
            long bytes = 0;
            for (var input : inputs) {
                List<ModelLodMesh> levels = new ArrayList<>(3);
                for (int level = 0; level < 3; level++) {
                    token.check();
                    int count = in.readInt();
                    float error = in.readFloat();
                    if (!Float.isFinite(error) || error < 0 || error > ModelLodSimplifier.ERRORS[level] + 1e-5f)
                        throw new IOException("invalid LOD cache error");
                    if (count == -1) {
                        if (error != 0) throw new IOException("invalid source LOD error");
                        levels.add(new ModelLodMesh(input.indices(), 0, true));
                        continue;
                    }
                    if (count == -2) {
                        if (level == 0 || levels.get(level - 1).error() != error)
                            throw new IOException("invalid shared LOD cache level");
                        levels.add(levels.get(level - 1));
                        continue;
                    }
                    if (count < 3 || count % 3 != 0 || count >= input.indices().length
                            || input.material().alphaMode() != GltfAsset.AlphaMode.OPAQUE)
                        throw new IOException("invalid LOD cache index count");
                    long payload = (long) count * Integer.BYTES;
                    if (payload > budget - bytes) throw new ModelLodSimplifier.BudgetException("retained_index_budget");
                    if (payload > Files.size(path)) throw new IOException("truncated LOD cache");
                    bytes += payload;
                    int[] indices = new int[count];
                    for (int i = 0; i < count; i++) {
                        if ((i & 4095) == 0) token.check();
                        int index = in.readInt();
                        if (index < 0 || index >= input.vertexCount()) throw new IOException("invalid LOD cache vertex index");
                        indices[i] = index;
                    }
                    levels.add(new ModelLodMesh(indices, error, false));
                }
                meshes.add(new ModelLodMesh.Levels(levels));
            }
            long expected = checksum.getValue();
            if (in.readLong() != expected || in.read() != -1) throw new IOException("LOD cache checksum mismatch");
            return meshes;
        }
    }

    private static void write(Path target, String key, List<ModelLodMesh.Levels> meshes,
                              ModelCancellation token) throws IOException {
        long bytes = meshes.stream().mapToLong(ModelLodMesh.Levels::cpuBytes).sum();
        if (bytes + meshes.size() * 24L + 128 > MAX_CACHE_BYTES) throw new IOException("oversize LOD cache output");
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".model-lod-", ".tmp");
        try {
            CRC32C checksum = new CRC32C();
            try (var out = new DataOutputStream(new CheckedOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)), checksum))) {
                out.writeInt(MAGIC);
                out.writeInt(VERSION);
                out.writeUTF(key);
                out.writeInt(meshes.size());
                for (var mesh : meshes) {
                    ModelLodMesh previous = null;
                    for (var level : mesh.meshes()) {
                        token.check();
                        boolean shared = previous != null && previous.indices() == level.indices();
                        out.writeInt(level.sourceReuse() ? -1 : shared ? -2 : level.indices().length);
                        out.writeFloat(level.error());
                        if (!level.sourceReuse() && !shared) for (int i = 0; i < level.indices().length; i++) {
                            if ((i & 4095) == 0) token.check();
                            out.writeInt(level.indices()[i]);
                        }
                        previous = level;
                    }
                }
                out.writeLong(checksum.getValue());
            }
            token.check();
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Result result(List<ModelLodMesh.Levels> meshes, List<ModelLodMesh.Input> inputs,
                                  String key, String status, String cacheFailure, Map<String, Integer> fallback, long started) {
        List<Map<String, Object>> levels = new ArrayList<>();
        long sourceTriangles = inputs.stream().mapToLong(ModelLodMesh.Input::triangleCount).sum();
        levels.add(Map.of("level", 0, "targetRatio", 1f, "triangles", sourceTriangles,
                "reducedMeshes", 0, "sourceMeshes", inputs.size(), "maxError", 0f));
        for (int level = 1; level <= 3; level++) {
            long triangles = 0;
            int reduced = 0;
            float error = 0;
            for (var mesh : meshes) {
                var geometry = mesh.level(level);
                triangles += geometry.triangleCount();
                if (!geometry.sourceReuse()) reduced++;
                error = Math.max(error, geometry.error());
            }
            levels.add(Map.of("level", level, "targetRatio", ModelLodSimplifier.RATIOS[level - 1],
                    "triangles", triangles, "reducedMeshes", reduced, "sourceMeshes", meshes.size() - reduced, "maxError", error,
                    "targetReached", triangles <= Math.ceil(sourceTriangles * ModelLodSimplifier.RATIOS[level - 1])));
        }
        long bytes = meshes.stream().mapToLong(ModelLodMesh.Levels::cpuBytes).sum();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("cacheStatus", status);
        report.put("key", key);
        report.put("cpuBytes", bytes);
        report.put("sourceTriangles", sourceTriangles);
        report.put("levels", List.copyOf(levels));
        report.put("preservedAlphaMeshes", inputs.stream().filter(i -> i.material().alphaMode() != GltfAsset.AlphaMode.OPAQUE).count());
        report.put("fallbackReasons", Map.copyOf(fallback));
        report.put("buildNanos", System.nanoTime() - started);
        report.put("cacheFailure", cacheFailure);
        return new Result(meshes, bytes, report);
    }

    private static List<ModelLodMesh.Levels> source(List<ModelLodMesh.Input> inputs) {
        return inputs.stream().map(ModelLodMesh.Levels::source).toList();
    }

    private static void floats(DataOutputStream out, float[] values, ModelCancellation token) throws IOException {
        out.writeInt(values.length);
        for (int i = 0; i < values.length; i++) {
            if ((i & 4095) == 0) token.check();
            out.writeInt(Float.floatToIntBits(values[i]));
        }
    }

    private static void ints(DataOutputStream out, int[] values, ModelCancellation token) throws IOException {
        out.writeInt(values.length);
        for (int i = 0; i < values.length; i++) {
            if ((i & 4095) == 0) token.check();
            out.writeInt(values[i]);
        }
    }

    private static void material(DataOutputStream out, GltfAsset.Material material, ModelCancellation token) throws IOException {
        out.writeUTF(material.alphaMode().name());
        out.writeFloat(material.alphaCutoff());
        out.writeBoolean(material.doubleSided());
        out.writeBoolean(material.unlit());
        floats(out, material.baseColorFactor(), token);
        floats(out, material.emissiveFactor(), token);
        out.writeFloat(material.metallicFactor());
        out.writeFloat(material.roughnessFactor());
        out.writeFloat(material.normalScale());
        out.writeFloat(material.occlusionStrength());
        out.writeFloat(material.emissiveStrength());
        texture(out, material.baseColorTexture(), token);
        texture(out, material.emissiveTexture(), token);
        texture(out, material.normalTexture(), token);
        texture(out, material.metallicRoughnessTexture(), token);
        texture(out, material.occlusionTexture(), token);
        out.writeInt(material.extensions().size());
        for (var entry : new TreeMap<>(material.extensions()).entrySet()) {
            byte[] name = entry.getKey().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] value = entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            out.writeInt(name.length); out.write(name);
            out.writeInt(value.length); out.write(value);
        }
    }

    private static void texture(DataOutputStream out, GltfAsset.TextureInfo texture, ModelCancellation token) throws IOException {
        out.writeBoolean(texture != null);
        if (texture == null) return;
        out.writeInt(texture.textureIndex());
        out.writeInt(texture.texCoord());
        floats(out, texture.offset(), token);
        floats(out, texture.scale(), token);
        out.writeFloat(texture.rotation());
    }
}
