package com.mine.geometry_node.client.model.render.backend.host.light.asset;

import com.mine.geometry_node.client.model.render.backend.host.geometry.HostCanonicalPrimitive;
import com.mine.geometry_node.client.model.render.backend.host.light.occlusion.HostConservativeVoxelGrid;
import com.mine.geometry_node.client.model.render.backend.host.light.occlusion.HostTriangleBvh;
import com.mine.geometry_node.client.model.runtime.StaticModelMaterial;
import com.mine.geometry_node.core.engine.system.model.domain.ModelAttributeSemantic;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntFunction;

/** Immutable, asset-shared F2 receiver/caster sidecar. */
public final class HostPreparedLightingAsset implements AutoCloseable {
    private final Status status;
    private final String detail;
    private final List<HostLightingSurface> surfaces;
    private final HostTriangleBvh bvh;
    private final HostConservativeVoxelGrid voxelGrid;
    private final Diagnostics diagnostics;
    private final HostLightingAssetBudget.Reservation reservation;

    private HostPreparedLightingAsset(Status status, String detail, List<HostLightingSurface> surfaces,
                                      HostTriangleBvh bvh, HostConservativeVoxelGrid voxelGrid,
                                      Diagnostics diagnostics, HostLightingAssetBudget.Reservation reservation) {
        this.status = status;
        this.detail = detail;
        this.surfaces = List.copyOf(surfaces);
        this.bvh = bvh;
        this.voxelGrid = voxelGrid;
        this.diagnostics = diagnostics;
        this.reservation = reservation;
    }

    public static HostPreparedLightingAsset prepare(List<HostCanonicalPrimitive> primitives,
                                                    IntFunction<StaticModelMaterial> materials) {
        return prepare(primitives, materials, HostLightingGeometryParameters.DEFAULT,
                HostLightingAssetBudget.INSTANCE);
    }

    public static HostPreparedLightingAsset prepare(List<HostCanonicalPrimitive> primitives,
                                                    IntFunction<StaticModelMaterial> materials,
                                                    HostLightingGeometryParameters parameters,
                                                    HostLightingAssetBudget budget) {
        Objects.requireNonNull(primitives, "primitives");
        Objects.requireNonNull(materials, "materials");
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(budget, "budget");
        for (HostCanonicalPrimitive primitive : primitives) {
            for (HostCanonicalPrimitive.NodeOccurrence occurrence : primitive.nodeOccurrences()) {
                if (occurrence.skinned()) return unavailable(Status.SKINNED_UNSUPPORTED,
                        "skinned canonical occurrence has no deformation snapshot");
            }
        }
        long estimated;
        try {
            estimated = estimatedBytes(primitives, materials, parameters);
        } catch (ArithmeticException overflow) {
            return unavailable(Status.BUDGET_REJECTED, "lighting asset size estimate overflow");
        } catch (RuntimeException failure) {
            return unavailable(Status.BUILD_FAILED, failure.getClass().getSimpleName() + ": "
                    + Objects.toString(failure.getMessage(), "lighting material estimate failed"));
        }
        HostLightingAssetBudget.Reservation reservation = budget.tryReserve(estimated);
        if (reservation == null) return unavailable(Status.BUDGET_REJECTED,
                "lighting asset requires " + estimated + " reserved bytes");
        try {
            List<HostLightingSurface> surfaces = new ArrayList<>();
            int degenerate = 0;
            for (HostCanonicalPrimitive primitive : primitives) {
                HostLightingMaterial material = HostLightingMaterial.from(primitive.identity().materialIndex(),
                        materials.apply(primitive.identity().materialIndex()));
                for (int occurrenceIndex = 0; occurrenceIndex < primitive.nodeOccurrences().size(); occurrenceIndex++) {
                    HostCanonicalPrimitive.NodeOccurrence occurrence = primitive.nodeOccurrences().get(occurrenceIndex);
                    BuildSurface result = buildSurface(primitive, occurrenceIndex, occurrence, material, parameters);
                    surfaces.add(result.surface());
                    degenerate = Math.addExact(degenerate, result.degenerateTriangles());
                }
            }
            HostTriangleBvh bvh = HostTriangleBvh.build(surfaces, parameters);
            HostConservativeVoxelGrid voxel = HostConservativeVoxelGrid.build(surfaces, parameters);
            long retained = surfaces.stream().mapToLong(HostLightingSurface::retainedBytes).sum();
            retained = Math.addExact(retained, bvh.retainedBytes());
            retained = Math.addExact(retained, voxel.retainedBytes());
            int vertices = surfaces.stream().mapToInt(HostLightingSurface::vertexCount).sum();
            int triangles = surfaces.stream().mapToInt(HostLightingSurface::triangleCount).sum();
            Diagnostics diagnostics = new Diagnostics(surfaces.size(), vertices, triangles, degenerate,
                    bvh.triangleCount(), bvh.nodeCount(), voxel.occupiedCells(), voxel.triangleBoxTests(),
                    retained, reservation.bytes());
            return new HostPreparedLightingAsset(Status.READY, "", surfaces, bvh, voxel,
                    diagnostics, reservation);
        } catch (RuntimeException failure) {
            reservation.close();
            return unavailable(Status.BUILD_FAILED, failure.getClass().getSimpleName() + ": "
                    + Objects.toString(failure.getMessage(), "lighting geometry build failed"));
        } catch (Error failure) {
            reservation.close();
            throw failure;
        }
    }

    private static BuildSurface buildSurface(HostCanonicalPrimitive primitive, int occurrenceIndex,
                                             HostCanonicalPrimitive.NodeOccurrence occurrence,
                                             HostLightingMaterial material,
                                             HostLightingGeometryParameters parameters) {
        Matrix4f transform = occurrence.modelTransform();
        Matrix3f normalTransform = new Matrix3f(transform);
        if (Math.abs(normalTransform.determinant()) < 1.0e-12F) {
            throw new IllegalArgumentException("singular node transform for lighting geometry");
        }
        normalTransform.invert().transpose();
        int vertices = primitive.vertexCount();
        float[] positions = new float[vertices * 3];
        float[] shading = new float[vertices * 3];
        HostCanonicalPrimitive.Attribute authoredNormals = primitive.attribute(ModelAttributeSemantic.NORMAL);
        for (int vertex = 0; vertex < vertices; vertex++) {
            Vector3f position = transform.transformPosition(new Vector3f(primitive.positionComponent(vertex, 0),
                    primitive.positionComponent(vertex, 1), primitive.positionComponent(vertex, 2)));
            positions[vertex * 3] = position.x; positions[vertex * 3 + 1] = position.y;
            positions[vertex * 3 + 2] = position.z;
            if (authoredNormals != null) {
                Vector3f normal = normalTransform.transform(new Vector3f(authoredNormals.component(vertex, 0),
                        authoredNormals.component(vertex, 1), authoredNormals.component(vertex, 2)));
                if (normal.lengthSquared() > 0F) normal.normalize();
                shading[vertex * 3] = normal.x; shading[vertex * 3 + 1] = normal.y;
                shading[vertex * 3 + 2] = normal.z;
            }
        }
        int[] indices = primitive.indices();
        float[] geometric = new float[(indices.length / 3) * 3];
        int degenerate = 0;
        for (int triangle = 0; triangle < indices.length / 3; triangle++) {
            int a = indices[triangle * 3], b = indices[triangle * 3 + 1], c = indices[triangle * 3 + 2];
            Vector3f ab = vector(positions, a, b), ac = vector(positions, a, c);
            Vector3f normal = ab.cross(ac);
            if (normal.lengthSquared() <= parameters.degenerateAreaSquared()) {
                degenerate++;
                continue;
            }
            normal.normalize();
            int offset = triangle * 3;
            geometric[offset] = normal.x; geometric[offset + 1] = normal.y; geometric[offset + 2] = normal.z;
            if (authoredNormals == null) {
                accumulate(shading, a, normal); accumulate(shading, b, normal); accumulate(shading, c, normal);
            }
        }
        if (authoredNormals == null) normalize(shading);
        HostLightingSurface.Identity identity = new HostLightingSurface.Identity(primitive.identity(),
                occurrenceIndex, occurrence.nodeIndex(), occurrence.skinIndex());
        return new BuildSurface(new HostLightingSurface(identity, material, positions, shading, geometric, indices),
                degenerate);
    }

    private static Vector3f vector(float[] positions, int from, int to) {
        return new Vector3f(positions[to * 3] - positions[from * 3],
                positions[to * 3 + 1] - positions[from * 3 + 1],
                positions[to * 3 + 2] - positions[from * 3 + 2]);
    }

    private static void accumulate(float[] values, int vertex, Vector3f normal) {
        values[vertex * 3] += normal.x; values[vertex * 3 + 1] += normal.y; values[vertex * 3 + 2] += normal.z;
    }

    private static void normalize(float[] values) {
        for (int offset = 0; offset < values.length; offset += 3) {
            Vector3f value = new Vector3f(values[offset], values[offset + 1], values[offset + 2]);
            if (value.lengthSquared() > 0F) value.normalize();
            values[offset] = value.x; values[offset + 1] = value.y; values[offset + 2] = value.z;
        }
    }

    private static long estimatedBytes(List<HostCanonicalPrimitive> primitives,
                                       IntFunction<StaticModelMaterial> materials,
                                       HostLightingGeometryParameters parameters) {
        long surfaceBytes = 0, opaqueTriangles = 0;
        for (HostCanonicalPrimitive primitive : primitives) {
            long occurrences = primitive.nodeOccurrences().size();
            long perOccurrence = Math.addExact(Math.multiplyExact((long) primitive.vertexCount(), 6L * Float.BYTES),
                    Math.multiplyExact((long) primitive.occurrenceCount(),
                            Integer.BYTES + Float.BYTES));
            surfaceBytes = Math.addExact(surfaceBytes, Math.multiplyExact(occurrences, perOccurrence));
            HostLightingMaterial material = HostLightingMaterial.from(primitive.identity().materialIndex(),
                    materials.apply(primitive.identity().materialIndex()));
            if (material.blocksLight()) {
                opaqueTriangles = Math.addExact(opaqueTriangles,
                        Math.multiplyExact(occurrences, primitive.triangleCount()));
            }
        }
        long bvhUpperBound = Math.multiplyExact(opaqueTriangles, 32L);
        long voxelUpperBound = Math.addExact((parameters.maximumVoxelCells() + 7L) / 8L, 1024L);
        return Math.addExact(Math.addExact(surfaceBytes, bvhUpperBound), voxelUpperBound);
    }

    private static HostPreparedLightingAsset unavailable(Status status, String detail) {
        return new HostPreparedLightingAsset(status, detail, List.of(), null, null,
                new Diagnostics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0), null);
    }

    public Status status() { return status; }
    public boolean ready() { return status == Status.READY; }
    public String detail() { return detail; }
    public List<HostLightingSurface> surfaces() { return surfaces; }
    public HostTriangleBvh bvh() { return Objects.requireNonNull(bvh, "lighting BVH is unavailable"); }
    public HostConservativeVoxelGrid voxelGrid() {
        return Objects.requireNonNull(voxelGrid, "lighting voxel grid is unavailable");
    }
    public Diagnostics diagnostics() { return diagnostics; }

    @Override public void close() {
        if (reservation != null) reservation.close();
    }

    public enum Status { READY, SKINNED_UNSUPPORTED, BUDGET_REJECTED, BUILD_FAILED }

    public record Diagnostics(int surfaces, int vertices, int triangles, int degenerateTriangles,
                              int opaqueTriangles, int bvhNodes, int occupiedVoxels,
                              long triangleBoxTests, long retainedBytes, long reservedBytes) {}

    private record BuildSurface(HostLightingSurface surface, int degenerateTriangles) {}
}
