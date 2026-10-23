package com.mine.geometry_node.core.engine.system.model.collision.generation;

import com.mine.geometry_node.core.engine.system.model.collision.ModelCollisionMesh;
import com.mine.geometry_node.core.engine.system.model.collision.ModelCollisionSettings;
import com.mine.geometry_node.core.engine.system.model.collision.ModelCollisionTransforms;

import com.mine.geometry_node.core.engine.system.model.asset.GltfAsset;
import com.mine.geometry_node.core.engine.system.model.io.ModelCancellation;

import org.joml.Matrix4fc;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** World-independent content cache. The caller supplies the game-directory collision subfolder. */
public final class ModelCollisionCache {
    private static final int MAGIC = 0x4D434F4C, VERSION = 7;
    private static final long MAX_BYTES = 256L * 1024 * 1024;

    private static final com.mine.geometry_node.core.engine.system.model.runtime.ModelDiskCacheAccess DISK =
            new com.mine.geometry_node.core.engine.system.model.runtime.ModelDiskCacheAccess();
    private static boolean ownedFile(String name) { return name.matches("[0-9a-f]{64}\\.(mcol|json)"); }
    public static void clearDisk(Path directory) throws IOException { DISK.clear(directory, ModelCollisionCache::ownedFile); }
    public static com.mine.geometry_node.core.cache.CacheStats diskStats(Path directory) throws IOException {
        return DISK.stats(directory, ModelCollisionCache::ownedFile);
    }
    private ModelCollisionCache() {}

    public record Result(
            ModelCollisionMesh mesh,
            String key,
            boolean cacheHit,
            long buildNanos,
            String cacheFailure,
            Map<String, Long> stageNanos) {
        public Result {
            stageNanos = Map.copyOf(stageNanos);
        }

        public Result(
                ModelCollisionMesh mesh,
                String key,
                boolean cacheHit,
                long buildNanos,
                String cacheFailure) {
            this(mesh, key, cacheHit, buildNanos, cacheFailure, Map.of());
        }
    }

    public static Result prepare(
            Path directory,
            GltfAsset asset,
            int sceneIndex,
            Matrix4fc bakedTransform,
            ModelCollisionSettings settings,
            ModelCancellation token)
            throws IOException {
        long diskGeneration = DISK.generation();
        long started = System.nanoTime();
        token.check();
        String key = key(asset, sceneIndex, bakedTransform, settings, token);
        long keyed = System.nanoTime();
        Path path = directory.resolve(key + ".mcol");
        String failure = "";
        try {
            if (Files.exists(path)) {
                ModelCollisionMesh cached = read(path, key, token);
                String metadataFailure = "";
                try {
                    if (!Files.isRegularFile(directory.resolve(key + ".json")))
                        DISK.publish(diskGeneration, () -> writeMetadata(path, key, cached, settings, sceneIndex, bakedTransform));
                } catch (IOException unavailable) {
                    metadataFailure = "cache metadata not saved: " + unavailable.getMessage();
                }
                long completed = System.nanoTime();
                return new Result(
                        cached,
                        key,
                        true,
                        completed - started,
                        metadataFailure,
                        Map.of("contentHash", keyed - started, "cacheRead", completed - keyed));
            }
        } catch (IOException | RuntimeException invalid) {
            token.check();
            failure = "cache rejected: " + invalid.getMessage();
        }
        long cacheChecked = System.nanoTime();
        var built =
                ModelCollisionBuilder.buildMeasured(
                        asset, sceneIndex, bakedTransform, settings, token);
        ModelCollisionMesh mesh =
                ModelCollisionTransforms.transform(
                        built.mesh(), new org.joml.Matrix4d(bakedTransform).invert(), token);
        long generated = System.nanoTime();
        try {
            DISK.publish(diskGeneration, () -> {
                write(path, key, mesh, token);
                writeMetadata(path, key, mesh, settings, sceneIndex, bakedTransform);
            });
        } catch (IOException | RuntimeException unavailable) {
            token.check();
            failure = "cache or metadata not saved: " + unavailable.getMessage();
        }
        long completed = System.nanoTime();
        return new Result(
                mesh,
                key,
                false,
                completed - started,
                failure,
                Map.of(
                        "contentHash",
                        keyed - started,
                        "cacheLookup",
                        cacheChecked - keyed,
                        "sceneBake",
                        built.sceneBakeNanos(),
                        "volumeGeneration",
                        built.volumeGenerationNanos(),
                        "convexMerge",
                        built.mergeNanos(),
                        "cacheWrite",
                        completed - generated));
    }

    private static void writeMetadata(
            Path path,
            String key,
            ModelCollisionMesh mesh,
            ModelCollisionSettings settings,
            int scene,
            Matrix4fc generationTransform)
            throws IOException {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("version", VERSION);
        info.put("cacheKey", key);
        info.put("coordinateSpace", "gltf_scene_local");
        info.put("generationBoundary", "scene_node_mesh_then_connected_component");
        info.put("scene", scene);
        info.put("mergeScaleBlocks", settings.mergeScale());
        info.put("fitErrorBlocks", settings.fitError());
        info.put("generationTransform", generationTransform.get(new float[16]));
        info.put("complete", mesh.skippedTriangles() == 0);
        info.put("sourceTriangles", mesh.sourceTriangles());
        info.put("skippedTriangles", mesh.skippedTriangles());
        info.put("incompleteObjects", mesh.incompleteObjects());
        info.put("generationIssues", mesh.generationIssues());
        info.put("parts", mesh.parts().size());
        info.put(
                "convexFaces",
                mesh.parts().stream().mapToInt(ModelCollisionMesh.Part::faceCount).sum());
        info.put(
                "vertices",
                mesh.parts().stream().mapToInt(ModelCollisionMesh.Part::vertexCount).sum());
        info.put(
                "partBounds",
                mesh.parts().stream()
                        .map(
                                p ->
                                        new double[] {
                                            p.minX(), p.minY(), p.minZ(), p.maxX(), p.maxY(),
                                            p.maxZ()
                                        })
                        .toList());
        Path metadata = path.resolveSibling(key + ".json");
        Path temporary = Files.createTempFile(path.getParent(), "collision-info-", ".tmp");
        try {
            Files.writeString(
                    temporary,
                    new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(info));
            Files.move(temporary, metadata, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static String key(
            GltfAsset asset,
            int sceneIndex,
            Matrix4fc transform,
            ModelCollisionSettings settings,
            ModelCancellation token)
            throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var out =
                    new DataOutputStream(
                            new BufferedOutputStream(
                                    new DigestOutputStream(
                                            OutputStream.nullOutputStream(), digest)))) {
                out.writeInt(VERSION);
                out.writeInt(sceneIndex);
                out.writeDouble(settings.mergeScale());
                out.writeDouble(settings.fitError());
                for (float f : transform.get(new float[16])) out.writeFloat(f);
                out.writeInt(asset.scenes().size());
                for (var scene : asset.scenes()) {
                    out.writeInt(scene.roots().size());
                    for (int root : scene.roots()) out.writeInt(root);
                }
                out.writeInt(asset.nodes().size());
                for (var node : asset.nodes()) {
                    token.check();
                    out.writeInt(node.mesh());
                    out.writeInt(node.children().size());
                    for (int child : node.children()) out.writeInt(child);
                    for (float f : node.local().get(new float[16])) out.writeFloat(f);
                }
                out.writeInt(asset.meshes().size());
                for (var mesh : asset.meshes()) {
                    out.writeInt(mesh.primitives().size());
                    for (var primitive : mesh.primitives()) {
                        token.check();
                        float[] positions = primitive.positions();
                        int[] indices = primitive.indices();
                        out.writeInt(positions.length);
                        for (int i = 0; i < positions.length; i++) {
                            if ((i & 4095) == 0) token.check();
                            out.writeFloat(positions[i]);
                        }
                        out.writeInt(indices.length);
                        for (int i = 0; i < indices.length; i++) {
                            if ((i & 4095) == 0) token.check();
                            out.writeInt(indices[i]);
                        }
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static ModelCollisionMesh read(Path path, String key, ModelCancellation token)
            throws IOException {
        long fileBytes = Files.size(path);
        if (fileBytes > MAX_BYTES) throw new IOException("collision cache byte budget exceeded");
        CRC32C crc = new CRC32C();
        try (var in =
                new DataInputStream(
                        new CheckedInputStream(
                                new BufferedInputStream(Files.newInputStream(path)), crc))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION || !in.readUTF().equals(key))
                throw new IOException("collision cache header mismatch");
            int source = in.readInt(), count = in.readInt();
            int skipped = in.readInt(), incomplete = in.readInt();
            int issueCount = in.readInt();
            if (issueCount < 0 || issueCount > 32)
                throw new IOException("invalid collision issue count");
            List<String> issues = new ArrayList<>();
            for (int i = 0; i < issueCount; i++) issues.add(in.readUTF());
            if (source < 0
                    || source > ModelCollisionBuilder.MAX_TRIANGLES
                    || count < 0
                    || count > ModelCollisionBuilder.MAX_PARTS)
                throw new IOException("collision cache count invalid");
            List<ModelCollisionMesh.Part> parts = new ArrayList<>(count);
            long bytes = 0;
            for (int i = 0; i < count; i++) {
                token.check();
                int nv = in.readInt(), nt = in.readInt(), np = in.readInt();
                if (nv < 12 || nv > 384 || nt < 12 || nt > 768 || np < 16 || np > 512)
                    throw new IOException("collision cache part dimensions invalid");
                bytes += (long) (nv + np) * 8 + (long) nt * 4;
                if (bytes > fileBytes) throw new IOException("collision cache truncated");
                double[] vertices = new double[nv], planes = new double[np];
                int[] triangles = new int[nt];
                for (int j = 0; j < nv; j++) vertices[j] = in.readDouble();
                for (int j = 0; j < nt; j++) triangles[j] = in.readInt();
                for (int j = 0; j < np; j++) planes[j] = in.readDouble();
                parts.add(new ModelCollisionMesh.Part(vertices, triangles, planes));
            }
            long expected = crc.getValue();
            if (in.readLong() != expected || in.read() != -1)
                throw new IOException("collision cache checksum mismatch");
            return new ModelCollisionMesh(parts, source, skipped, incomplete, issues);
        }
    }

    private static void write(
            Path path, String key, ModelCollisionMesh mesh, ModelCancellation token)
            throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), "collision-", ".tmp");
        try {
            CRC32C crc = new CRC32C();
            try (var out =
                    new DataOutputStream(
                            new CheckedOutputStream(
                                    new BufferedOutputStream(Files.newOutputStream(temporary)),
                                    crc))) {
                out.writeInt(MAGIC);
                out.writeInt(VERSION);
                out.writeUTF(key);
                out.writeInt(mesh.sourceTriangles());
                out.writeInt(mesh.parts().size());
                out.writeInt(mesh.skippedTriangles());
                out.writeInt(mesh.incompleteObjects());
                out.writeInt(mesh.generationIssues().size());
                for (String issue : mesh.generationIssues())
                    out.writeUTF(issue.substring(0, Math.min(2048, issue.length())));
                long bytes = 0;
                for (var part : mesh.parts()) {
                    token.check();
                    double[] vertices = part.vertices(), planes = part.planes();
                    int[] triangles = part.triangles();
                    bytes +=
                            (long) (vertices.length + planes.length) * 8
                                    + (long) triangles.length * 4
                                    + 12;
                    if (bytes > MAX_BYTES - 1024)
                        throw new IOException("collision cache byte budget exceeded");
                    out.writeInt(vertices.length);
                    out.writeInt(triangles.length);
                    out.writeInt(planes.length);
                    for (double d : vertices) out.writeDouble(d);
                    for (int i : triangles) out.writeInt(i);
                    for (double d : planes) out.writeDouble(d);
                }
                out.writeLong(crc.getValue());
            }
            token.check();
            try {
                Files.move(
                        temporary,
                        path,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
