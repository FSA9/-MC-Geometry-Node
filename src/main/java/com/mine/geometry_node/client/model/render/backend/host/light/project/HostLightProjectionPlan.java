package com.mine.geometry_node.client.model.render.backend.host.light.project;

import com.mine.geometry_node.client.model.render.backend.host.entity.HostDrawPlan;
import com.mine.geometry_node.client.model.render.backend.host.geometry.HostEntityGeometry;
import com.mine.geometry_node.client.model.render.backend.host.light.asset.HostLightingSurface;
import com.mine.geometry_node.client.model.render.backend.host.light.asset.HostPreparedLightingAsset;
import com.mine.geometry_node.client.model.render.backend.host.light.asset.HostReceiverProbeSet;
import com.mine.geometry_node.client.model.render.backend.host.light.contract.HostLightBinding;
import com.mine.geometry_node.client.model.render.backend.host.light.contract.HostScalarLightField;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Asset-shared mapping from source/proxy triangle occurrences to bounded F3 receiver probes. */
public final class HostLightProjectionPlan {
    private static final int BLOCK_LIGHT_MASK = 15 << 4;
    private static final float MINIMUM_NORMAL_DOT = 0.25F;
    private static final int COMPONENTS_PER_VERTEX = 12;
    private static final int CORNERS_PER_TRIANGLE = 3;
    private static final int NO_PROBE = -1;

    private final Status status;
    private final String detail;
    private final int probeCount;
    private final Map<DrawKey, DrawProjection> draws;
    private final long retainedBytes;

    private HostLightProjectionPlan(Status status, String detail, int probeCount,
                                    Map<DrawKey, DrawProjection> draws, long retainedBytes) {
        this.status = status;
        this.detail = detail;
        this.probeCount = probeCount;
        this.draws = Map.copyOf(draws);
        this.retainedBytes = retainedBytes;
    }

    public static HostLightProjectionPlan build(HostDrawPlan drawPlan, HostPreparedLightingAsset lighting) {
        Objects.requireNonNull(drawPlan, "drawPlan");
        Objects.requireNonNull(lighting, "lighting");
        if (!lighting.ready()) return unavailable("lighting asset is unavailable");
        HostReceiverProbeSet probes = lighting.receiverProbes();
        if (probes.size() == 0) return unavailable("lighting asset has no receiver probes");
        try {
            Map<SurfaceKey, Integer> surfaceIndices = surfaceIndices(lighting.surfaces());
            ProbeTree[] trees = probeTrees(lighting.surfaces().size(), probes);
            Map<DrawKey, DrawProjection> projected = new HashMap<>();
            long retained = 0;
            for (HostDrawPlan.Draw draw : drawPlan.draws()) {
                HostEntityGeometry geometry = draw.geometry();
                if (geometry == null) continue;
                Integer surfaceIndex = surfaceIndices.get(new SurfaceKey(
                        draw.canonicalPrimitive().identity(), draw.nodeIndex()));
                if (surfaceIndex == null) continue;
                DrawProjection projection = project(draw, geometry,
                        lighting.surfaces().get(surfaceIndex), trees[surfaceIndex]);
                projected.put(DrawKey.from(draw), projection);
                retained = Math.addExact(retained, projection.retainedBytes());
            }
            if (projected.isEmpty()) return unavailable("no render draw has a receiver surface");
            return new HostLightProjectionPlan(Status.READY, "", probes.size(), projected, retained);
        } catch (RuntimeException failure) {
            return unavailable(failure.getClass().getSimpleName() + ": "
                    + Objects.toString(failure.getMessage(), "projection build failed"));
        }
    }

    public boolean ready() { return status == Status.READY; }
    public Status status() { return status; }
    public String detail() { return detail; }
    public int probeCount() { return probeCount; }
    public int projectedDraws() { return draws.size(); }
    public long retainedBytes() { return retainedBytes; }

    public HostLightBinding binding(HostDrawPlan.Draw draw, HostScalarLightField field, int fallbackPackedLight) {
        Objects.requireNonNull(draw, "draw");
        Objects.requireNonNull(field, "field");
        if (!ready() || field.size() != probeCount) return HostLightBinding.constant(fallbackPackedLight);
        DrawProjection projection = draws.get(DrawKey.from(draw));
        if (projection == null) return HostLightBinding.constant(fallbackPackedLight);
        int preservedNativeLight = fallbackPackedLight & ~BLOCK_LIGHT_MASK;
        return HostLightBinding.field(field.identity().fieldId(), preservedNativeLight, occurrence -> {
            int probe = projection.probe(occurrence);
            return probe == NO_PROBE ? fallbackPackedLight
                    : preservedNativeLight | (field.packedLight(probe) & BLOCK_LIGHT_MASK);
        });
    }

    private static DrawProjection project(HostDrawPlan.Draw draw, HostEntityGeometry geometry,
                                          HostLightingSurface surface, ProbeTree tree) {
        int occurrences = Math.multiplyExact(geometry.staticTriangleCount(), CORNERS_PER_TRIANGLE);
        int[] mapping = new int[occurrences];
        Arrays.fill(mapping, NO_PROBE);
        int sourceTriangles = geometry.sourceTriangleCount();
        for (int triangle = 0; triangle < sourceTriangles; triangle++) {
            Vector3f center = surfaceCenter(surface, triangle);
            Vector3f normal = surfaceNormal(surface, triangle);
            int probe = tree.nearest(center, normal);
            Arrays.fill(mapping, triangle * 3, triangle * 3 + 3, probe);
        }
        Matrix4f transform = draw.modelTransform();
        Matrix3f normalTransform = new Matrix3f(transform);
        if (Math.abs(normalTransform.determinant()) < 1.0e-12F) {
            throw new IllegalArgumentException("singular draw transform");
        }
        normalTransform.invert().transpose();
        int proxyTriangles = geometry.lod().proxyTriangleCount();
        for (int triangle = 0; triangle < proxyTriangles; triangle++) {
            Vector3f a = proxyPosition(geometry, triangle, 0, transform);
            Vector3f b = proxyPosition(geometry, triangle, 1, transform);
            Vector3f c = proxyPosition(geometry, triangle, 2, transform);
            Vector3f normal = b.sub(a, new Vector3f()).cross(c.sub(a, new Vector3f()));
            if (normal.lengthSquared() <= 1.0e-12F) {
                int base = triangle * 3 * COMPONENTS_PER_VERTEX;
                normal.set(geometry.lod().proxyComponent(base + 3),
                        geometry.lod().proxyComponent(base + 4), geometry.lod().proxyComponent(base + 5));
                normalTransform.transform(normal);
            }
            if (normal.lengthSquared() <= 1.0e-12F) continue;
            normal.normalize();
            Vector3f center = a.add(b).add(c).mul(1F / 3F);
            int probe = tree.nearest(center, normal);
            int output = Math.multiplyExact(sourceTriangles + triangle, 3);
            Arrays.fill(mapping, output, output + 3, probe);
        }
        return new DrawProjection(mapping);
    }

    private static Vector3f surfaceCenter(HostLightingSurface surface, int triangle) {
        Vector3f result = new Vector3f();
        for (int corner = 0; corner < 3; corner++) {
            int vertex = surface.vertexIndex(triangle, corner);
            result.add(surface.position(vertex, 0), surface.position(vertex, 1), surface.position(vertex, 2));
        }
        return result.mul(1F / 3F);
    }

    private static Vector3f surfaceNormal(HostLightingSurface surface, int triangle) {
        return new Vector3f(surface.geometricNormal(triangle, 0),
                surface.geometricNormal(triangle, 1), surface.geometricNormal(triangle, 2));
    }

    private static Vector3f proxyPosition(HostEntityGeometry geometry, int triangle, int corner,
                                          Matrix4f transform) {
        int base = (triangle * 3 + corner) * COMPONENTS_PER_VERTEX;
        return transform.transformPosition(new Vector3f(geometry.lod().proxyComponent(base),
                geometry.lod().proxyComponent(base + 1), geometry.lod().proxyComponent(base + 2)));
    }

    private static Map<SurfaceKey, Integer> surfaceIndices(List<HostLightingSurface> surfaces) {
        Map<SurfaceKey, Integer> result = new HashMap<>();
        for (int index = 0; index < surfaces.size(); index++) {
            HostLightingSurface surface = surfaces.get(index);
            SurfaceKey key = new SurfaceKey(surface.identity().primitive(), surface.identity().nodeIndex());
            if (result.put(key, index) != null) throw new IllegalArgumentException("duplicate receiver surface");
        }
        return result;
    }

    private static ProbeTree[] probeTrees(int surfaces, HostReceiverProbeSet probes) {
        List<List<Integer>> grouped = new ArrayList<>(surfaces);
        for (int index = 0; index < surfaces; index++) grouped.add(new ArrayList<>());
        for (int probe = 0; probe < probes.size(); probe++) grouped.get(probes.surfaceIndex(probe)).add(probe);
        ProbeTree[] result = new ProbeTree[surfaces];
        for (int surface = 0; surface < surfaces; surface++) {
            result[surface] = new ProbeTree(probes, grouped.get(surface));
        }
        return result;
    }

    public enum Status { READY, UNAVAILABLE }

    private record DrawKey(int nodeIndex, int meshIndex, int primitiveIndex) {
        private static DrawKey from(HostDrawPlan.Draw draw) {
            return new DrawKey(draw.nodeIndex(), draw.meshIndex(), draw.primitiveIndex());
        }
    }

    private record SurfaceKey(com.mine.geometry_node.client.model.render.backend.host.geometry.HostCanonicalPrimitive.Identity primitive,
                              int nodeIndex) {}

    private static final class DrawProjection {
        private final int[] probes;
        private DrawProjection(int[] probes) { this.probes = probes; }
        private int probe(int occurrence) {
            if (occurrence < 0 || occurrence >= probes.length) throw new IndexOutOfBoundsException(occurrence);
            return probes[occurrence];
        }
        private long retainedBytes() { return (long) probes.length * Integer.BYTES; }
    }

    private static final class ProbeTree {
        private final HostReceiverProbeSet probes;
        private final Node root;

        private ProbeTree(HostReceiverProbeSet probes, List<Integer> indices) {
            this.probes = probes;
            Integer[] order = indices.toArray(Integer[]::new);
            this.root = build(order, 0, order.length, 0, probes);
        }

        private int nearest(Vector3f position, Vector3f normal) {
            if (root == null || normal.lengthSquared() <= 1.0e-12F) return NO_PROBE;
            normal.normalize();
            Best best = new Best();
            search(root, position, normal, best);
            return best.probe;
        }

        private void search(Node node, Vector3f position, Vector3f normal, Best best) {
            if (node == null) return;
            int probe = node.probe;
            float nx = probes.normal(probe, 0), ny = probes.normal(probe, 1), nz = probes.normal(probe, 2);
            if (nx * normal.x + ny * normal.y + nz * normal.z >= MINIMUM_NORMAL_DOT) {
                float dx = probes.position(probe, 0) - position.x;
                float dy = probes.position(probe, 1) - position.y;
                float dz = probes.position(probe, 2) - position.z;
                float distance = dx * dx + dy * dy + dz * dz;
                if (distance < best.distance || distance == best.distance && probe < best.probe) {
                    best.distance = distance;
                    best.probe = probe;
                }
            }
            float delta = component(position, node.axis) - probes.position(probe, node.axis);
            Node near = delta <= 0 ? node.left : node.right;
            Node far = delta <= 0 ? node.right : node.left;
            search(near, position, normal, best);
            if (delta * delta <= best.distance) search(far, position, normal, best);
        }

        private static Node build(Integer[] order, int from, int to, int depth, HostReceiverProbeSet probes) {
            if (from >= to) return null;
            int axis = depth % 3;
            Arrays.sort(order, from, to, (left, right) -> {
                int coordinate = Float.compare(probes.position(left, axis), probes.position(right, axis));
                return coordinate != 0 ? coordinate : Integer.compare(left, right);
            });
            int middle = (from + to) >>> 1;
            return new Node(order[middle], axis,
                    build(order, from, middle, depth + 1, probes),
                    build(order, middle + 1, to, depth + 1, probes));
        }

        private static float component(Vector3f value, int axis) {
            return axis == 0 ? value.x : axis == 1 ? value.y : value.z;
        }

        private record Node(int probe, int axis, Node left, Node right) {}
        private static final class Best {
            private float distance = Float.POSITIVE_INFINITY;
            private int probe = NO_PROBE;
        }
    }

    private static HostLightProjectionPlan unavailable(String detail) {
        return new HostLightProjectionPlan(Status.UNAVAILABLE, detail, 0, Map.of(), 0);
    }
}
