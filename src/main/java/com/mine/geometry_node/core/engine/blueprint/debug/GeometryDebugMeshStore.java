package com.mine.geometry_node.core.engine.blueprint.debug;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class GeometryDebugMeshStore {
    private final Map<String, Source> sources = new HashMap<>();

    boolean isEmpty() {
        return sources.isEmpty();
    }

    Collection<Source> sources() {
        return sources.values();
    }

    boolean replace(String sourceKey, List<GeometryDebugMesh> meshes) {
        String safeKey = normalizeKey(sourceKey);
        if (safeKey.isEmpty()) {
            return false;
        }

        long signature = sourceSignature(meshes);
        Source source = sources.get(safeKey);
        if (source != null && source.signature == signature) {
            return false;
        }

        sources.put(safeKey, new Source(List.copyOf(meshes), signature));
        return true;
    }

    boolean remove(String sourceKey) {
        String safeKey = normalizeKey(sourceKey);
        return !safeKey.isEmpty() && sources.remove(safeKey) != null;
    }

    private static String normalizeKey(String sourceKey) {
        return sourceKey != null ? sourceKey.trim() : "";
    }

    private static long sourceSignature(List<GeometryDebugMesh> meshes) {
        long signature = 1469598103934665603L;
        for (GeometryDebugMesh mesh : meshes) {
            signature = mix(signature, mesh.id().hashCode());
            signature = mix(signature, mesh.graphId().hashCode());
            signature = mix(signature, Double.doubleToLongBits(mesh.center().x));
            signature = mix(signature, Double.doubleToLongBits(mesh.center().y));
            signature = mix(signature, Double.doubleToLongBits(mesh.center().z));
            signature = mix(signature, mesh.vertexCount());
            signature = mix(signature, mesh.edgeCount());
            signature = mix(signature, mesh.faceCount());
            for (float value : mesh.vertices()) {
                signature = mix(signature, Float.floatToIntBits(value));
            }
            for (int value : mesh.edges()) {
                signature = mix(signature, value);
            }
            for (int value : mesh.faces()) {
                signature = mix(signature, value);
            }
        }
        return signature * 31L + meshes.size();
    }

    private static long mix(long signature, long value) {
        return (signature ^ value) * 1099511628211L;
    }

    static final class Source {
        private final List<GeometryDebugMesh> meshes;
        private final long signature;

        private Source(List<GeometryDebugMesh> meshes, long signature) {
            this.meshes = meshes;
            this.signature = signature;
        }

        List<GeometryDebugMesh> meshes() {
            return meshes;
        }
    }
}
