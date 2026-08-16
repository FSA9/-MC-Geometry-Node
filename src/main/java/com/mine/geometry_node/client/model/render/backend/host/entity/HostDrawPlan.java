package com.mine.geometry_node.client.model.render.backend.host.entity;

import com.mine.geometry_node.client.model.render.backend.host.geometry.HostEntityGeometry;
import com.mine.geometry_node.client.model.render.backend.host.geometry.HostGeometryProjector;
import com.mine.geometry_node.client.model.render.backend.host.material.HostMaterialAnalyzer;
import com.mine.geometry_node.client.model.render.backend.host.material.HostMaterialProfile;
import com.mine.geometry_node.client.model.render.backend.host.material.HostMaterialProjection;
import com.mine.geometry_node.client.model.runtime.StaticModelMaterial;
import com.mine.geometry_node.client.model.runtime.StaticModelRenderMetadata;
import com.mine.geometry_node.client.model.runtime.StaticModelTexture;
import com.mine.geometry_node.core.engine.system.model.domain.*;
import org.joml.Vector3f;

import java.util.*;

/** Immutable asset-level HOST draw skeleton. Instance and frame state are intentionally excluded. */
public final class HostDrawPlan {
    private static final long HOST_VERTICES_PER_TRIANGLE = 4L;

    private final List<Draw> draws;
    private final long requiredVertices;

    private HostDrawPlan(List<Draw> draws, long requiredVertices) {
        this.draws = List.copyOf(draws);
        this.requiredVertices = requiredVertices;
    }

    public static HostDrawPlan compile(ModelDefinition definition, StaticModelRenderMetadata metadata) {
        Map<GeometryKey, HostEntityGeometry> geometry = new HashMap<>();
        List<Draw> draws = new ArrayList<>();
        long requiredVertices = 0;
        for (int nodeIndex = 0; nodeIndex < definition.nodes().size(); nodeIndex++) {
            ModelNode node = definition.nodes().get(nodeIndex);
            if (node.meshIndex() < 0 || !metadata.nodeVisible(nodeIndex)) continue;
            ModelMesh mesh = definition.meshes().get(node.meshIndex());
            for (int primitiveIndex = 0; primitiveIndex < mesh.primitives().size(); primitiveIndex++) {
                ModelPrimitive primitive = mesh.primitives().get(primitiveIndex);
                StaticModelMaterial material = metadata.material(primitive.materialIndex());
                StaticModelTexture coordinates = coordinateSource(material);
                GeometryKey key = new GeometryKey(node.meshIndex(), primitiveIndex, coordinates.texCoord(),
                        coordinates.transform());
                HostEntityGeometry projected = null;
                String geometryFailure = "";
                boolean skinned = node.skinIndex() >= 0;
                if (!skinned) {
                    try {
                        projected = geometry.computeIfAbsent(key,
                                ignored -> HostGeometryProjector.project(primitive, coordinates));
                    } catch (RuntimeException failure) {
                        geometryFailure = failure.getClass().getSimpleName() + ": "
                                + Objects.toString(failure.getMessage(), "geometry projection failed");
                    }
                }
                draws.add(new Draw(nodeIndex, node.meshIndex(), primitiveIndex, material, coordinates, projected,
                        geometryFailure, primitive.bounds(), HostGeometryProjector.boundsCenter(primitive.bounds()),
                        primitive.triangleCount(),
                        HostMaterialAnalyzer.analyze(HostMaterialProfile.HOST_NATIVE_ENTITY, material, skinned),
                        HostMaterialAnalyzer.analyze(HostMaterialProfile.HOST_NATIVE_LABPBR, material, skinned)));
                requiredVertices = saturatedAdd(requiredVertices,
                        saturatedMultiply(primitive.triangleCount(), HOST_VERTICES_PER_TRIANGLE));
            }
        }
        return new HostDrawPlan(draws, requiredVertices);
    }

    public List<Draw> draws() { return draws; }
    public long requiredVertices() { return requiredVertices; }

    /** Cheap preflight used before the first plan is allowed to expand canonical geometry. */
    public static long requiredVertices(ModelDefinition definition, StaticModelRenderMetadata metadata) {
        long required = 0;
        for (int nodeIndex = 0; nodeIndex < definition.nodes().size(); nodeIndex++) {
            ModelNode node = definition.nodes().get(nodeIndex);
            if (node.meshIndex() < 0 || !metadata.nodeVisible(nodeIndex)) continue;
            for (ModelPrimitive primitive : definition.meshes().get(node.meshIndex()).primitives()) {
                required = saturatedAdd(required,
                        saturatedMultiply(primitive.triangleCount(), HOST_VERTICES_PER_TRIANGLE));
            }
        }
        return required;
    }

    private static StaticModelTexture coordinateSource(StaticModelMaterial material) {
        StaticModelTexture[] textures = {material.baseColorTexture(), material.metallicRoughnessTexture(),
                material.normalTexture(), material.occlusionTexture(), material.emissiveTexture()};
        for (StaticModelTexture texture : textures) if (texture.present()) return texture;
        return StaticModelTexture.absent();
    }

    private static long saturatedAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static long saturatedMultiply(long left, long right) {
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }

    private record GeometryKey(int mesh, int primitive, int uvSet, ModelTextureTransform transform) {}

    public record Draw(int nodeIndex, int meshIndex, int primitiveIndex, StaticModelMaterial material,
                       StaticModelTexture coordinateSource, HostEntityGeometry geometry, String geometryFailure,
                       ModelBounds localBounds, Vector3f localCenter,
                       long triangleCount, HostMaterialProjection entityProjection,
                       HostMaterialProjection labPbrProjection) {
        public Draw {
            localCenter = new Vector3f(localCenter);
        }

        @Override public Vector3f localCenter() { return new Vector3f(localCenter); }

        public HostMaterialProjection projection(HostMaterialProfile profile) {
            return profile == HostMaterialProfile.HOST_NATIVE_LABPBR ? labPbrProjection : entityProjection;
        }
    }
}
