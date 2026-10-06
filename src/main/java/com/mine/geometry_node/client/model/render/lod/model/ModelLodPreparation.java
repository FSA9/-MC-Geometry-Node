package com.mine.geometry_node.client.model.render.lod.model;

import com.mine.geometry_node.client.model.render.geometry.ModelRenderAsset.Occurrence;
import com.mine.geometry_node.client.model.render.geometry.ModelRenderAsset.Primitive;
import com.mine.geometry_node.core.engine.system.model.io.ModelCancellation;
import com.mine.geometry_node.core.engine.system.model.asset.GltfAsset;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Worker-side LOD cache and scene assembly; vertex storage and GPU ownership stay with the asset. */
public final class ModelLodPreparation {
    public record Result(List<List<List<Occurrence>>> scenes, List<List<ModelLodObject>> objects,
                         long cpuBytes, Map<String, Object> report) {}

    public static Result prepare(List<List<Occurrence>> scenes, Path directory,
                                 ModelCancellation token, long budget) throws IOException {
        return prepare(scenes, List.of(), directory, token, budget);
    }

    public static Result prepare(List<List<Occurrence>> scenes, List<GltfAsset.Node> nodes, Path directory,
                                 ModelCancellation token, long budget) throws IOException {
        List<Primitive> sources = new ArrayList<>();
        IdentityHashMap<Primitive, Integer> ids = new IdentityHashMap<>();
        for (var scene : scenes) for (var occurrence : scene) for (var primitive : occurrence.primitives()) {
            token.check();
            if (!ids.containsKey(primitive)) { ids.put(primitive, sources.size()); sources.add(primitive); }
        }
        var inputs = sources.stream().map(p -> new ModelLodMesh.Input(p.positions, p.normals, p.tangents,
                p.baseUv, p.emissionUv, p.uv0, p.uv1, p.colors, p.indices, p.material)).toList();
        // Reserve metadata for at most three reduced variants; vertex attributes remain shared.
        long objectBytes = objectMetadataBytes(scenes);
        var cached = ModelLodCache.prepare(directory, inputs, token, Math.max(0L, budget - sources.size() * 1024L - objectBytes));
        List<List<List<Occurrence>>> levels = new ArrayList<>(); levels.add(List.copyOf(scenes));
        long bytes = Math.addExact(cached.cpuBytes(), objectBytes);
        IdentityHashMap<int[], Primitive> sharedVariants = new IdentityHashMap<>();
        for (int level = 1; level < 4; level++) {
            token.check();
            List<Primitive> variants = new ArrayList<>(sources.size());
            for (int i = 0; i < sources.size(); i++) {
                token.check();
                var mesh = cached.meshes().get(i).level(level);
                Primitive original = sources.get(i);
                if (mesh.sourceReuse()) variants.add(original);
                else {
                    Primitive variant = sharedVariants.get(mesh.indices());
                    if (variant == null) {
                        variant = Primitive.lodVariant(original, mesh.indices());
                        sharedVariants.put(mesh.indices(), variant);
                        bytes = Math.addExact(bytes, 256L);
                    }
                    variants.add(variant);
                }
            }
            List<List<Occurrence>> levelScenes = new ArrayList<>();
            for (var scene : scenes) {
                List<Occurrence> occurrences = new ArrayList<>();
                for (var occurrence : scene) {
                    token.check();
                    occurrences.add(new Occurrence(occurrence.transform(), occurrence.primitives().stream()
                            .map(p -> variants.get(ids.get(p))).toList(), occurrence.nodeIndex()));
                }
                levelScenes.add(List.copyOf(occurrences));
            }
            levels.add(List.copyOf(levelScenes));
        }
        var report = new LinkedHashMap<String, Object>(cached.report());
        report.put("cpuBytes", bytes);
        report.put("objectMetadataBytes", objectBytes);
        report.put("cacheDirectory", directory.toAbsolutePath().toString());
        var objects = objects(levels, nodes, token);
        report.put("scope", "gltf_node_object");
        report.put("objects", objects.stream().mapToInt(List::size).sum());
        report.put("bvhFullGeometry", true);
        return new Result(List.copyOf(levels), objects, bytes, Map.copyOf(report));
    }

    public static long objectMetadataBytes(List<List<Occurrence>> scenes) {
        return scenes.stream().mapToLong(scene -> scene.size() * 512L).sum();
    }

    static List<List<ModelLodObject>> objects(List<List<List<Occurrence>>> levels,
                                             List<GltfAsset.Node> nodes, ModelCancellation token) throws IOException {
        List<List<ModelLodObject>> result = new ArrayList<>();
        for (int scene = 0; scene < levels.getFirst().size(); scene++) {
            var source = levels.getFirst().get(scene);
            Map<Integer, List<Integer>> groups = new LinkedHashMap<>();
            for (int occurrence = 0; occurrence < source.size(); occurrence++) {
                token.check();
                int node = source.get(occurrence).nodeIndex();
                groups.computeIfAbsent(node >= 0 ? node : -1 - occurrence, unused -> new ArrayList<>()).add(occurrence);
            }
            List<ModelLodObject> sceneObjects = new ArrayList<>();
            for (var group : groups.entrySet()) {
                token.check();
                List<List<Occurrence>> objectLevels = new ArrayList<>(ModelLodPolicy.LEVEL_COUNT);
                for (var level : levels) {
                    List<Occurrence> objectOccurrences = new ArrayList<>(group.getValue().size());
                    for (int occurrence : group.getValue()) objectOccurrences.add(level.get(scene).get(occurrence));
                    objectLevels.add(List.copyOf(objectOccurrences));
                }
                int node = group.getKey();
                String name = node >= 0 && node < nodes.size() ? nodes.get(node).name() : "";
                sceneObjects.add(new ModelLodObject(sceneObjects.size(), node, name, objectLevels));
            }
            result.add(List.copyOf(sceneObjects));
        }
        return List.copyOf(result);
    }

    private ModelLodPreparation() {}
}
