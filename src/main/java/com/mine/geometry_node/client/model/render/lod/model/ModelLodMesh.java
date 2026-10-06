package com.mine.geometry_node.client.model.render.lod.model;

import com.mine.geometry_node.core.engine.system.model.asset.GltfAsset;

import java.util.List;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;

/** An index-only LOD. Arrays are borrowed read-only from the prepared asset or owned cache payload. */
public record ModelLodMesh(int[] indices, float error, boolean sourceReuse) {
    public ModelLodMesh {
        Objects.requireNonNull(indices);
        if (indices.length == 0 || indices.length % 3 != 0 || !Float.isFinite(error) || error < 0)
            throw new IllegalArgumentException("invalid model LOD mesh");
    }

    public int triangleCount() { return indices.length / 3; }
    public long cpuBytes() { return sourceReuse ? 0 : (long) indices.length * Integer.BYTES; }

    public record Levels(List<ModelLodMesh> meshes) {
        public Levels {
            meshes = List.copyOf(meshes);
            if (meshes.size() != 3) throw new IllegalArgumentException("three reduced LOD levels required");
        }

        public ModelLodMesh level(int level) {
            if (level < 1 || level > 3) throw new IllegalArgumentException("LOD level must be 1..3");
            return meshes.get(level - 1);
        }

        public long cpuBytes() {
            var seen = Collections.newSetFromMap(new IdentityHashMap<int[], Boolean>());
            long bytes = 0;
            for (ModelLodMesh mesh : meshes) if (seen.add(mesh.indices())) bytes += mesh.cpuBytes();
            return bytes;
        }

        public static Levels source(Input input) {
            ModelLodMesh source = new ModelLodMesh(input.indices(), 0, true);
            return new Levels(List.of(source, source, source));
        }
    }

    public record Input(float[] positions, float[] normals, float[] tangents,
                        float[] baseUv, float[] emissionUv, float[] uv0, float[] uv1,
                        int[] colors, int[] indices, GltfAsset.Material material) {
        public Input {
            Objects.requireNonNull(material);
            Objects.requireNonNull(positions);
            Objects.requireNonNull(indices);
            if (positions.length == 0 || positions.length % 3 != 0 || indices.length == 0 || indices.length % 3 != 0)
                throw new IllegalArgumentException("model LOD requires indexed triangles");
            int vertices = positions.length / 3;
            checkLength(normals, vertices * 3);
            checkLength(tangents, vertices * 4);
            checkLength(baseUv, vertices * 2);
            checkLength(emissionUv, vertices * 2);
            checkLength(uv0, vertices * 2);
            checkLength(uv1, vertices * 2);
            Objects.requireNonNull(colors);
            if (colors.length != 0 && colors.length != vertices) throw new IllegalArgumentException("invalid colors");
            for (float value : positions) if (!Float.isFinite(value)) throw new IllegalArgumentException("nonfinite position");
            for (int index : indices) if (index < 0 || index >= vertices) throw new IllegalArgumentException("invalid index");
        }

        public int vertexCount() { return positions.length / 3; }
        public int triangleCount() { return indices.length / 3; }

        private static void checkLength(float[] values, int expected) {
            Objects.requireNonNull(values);
            if (values.length != 0 && values.length != expected) throw new IllegalArgumentException("invalid vertex attributes");
            for (float value : values) if (!Float.isFinite(value)) throw new IllegalArgumentException("nonfinite vertex attribute");
        }
    }
}
