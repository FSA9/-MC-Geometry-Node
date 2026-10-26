package com.mine.geometry_node.client.runtime.model.render.lod.cluster;

import com.mine.geometry_node.core.engine.system.model.asset.GltfAsset;
import com.mine.geometry_node.core.engine.system.model.io.ModelCancellation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Worker-side builder for the render-only CPU cluster hierarchy.
 *
 * <p>The builder intentionally keeps the source geometry untouched. Triangles
 * are Morton ordered into spatial leaves, then four neighbouring nodes are
 * merged repeatedly. Internal nodes carry a tiny representative-triangle
 * proxy and conservative bounds/error; later traversal can choose either the
 * proxy or descendants without rebuilding geometry.</p>
 */
public final class ModelCpuClusterBuilder {
    public static final int MIN_LEAF_TRIANGLES = 64;
    public static final int TARGET_LEAF_TRIANGLES = 96;
    public static final int MAX_LEAF_TRIANGLES = 128;
    public static final int MAX_CHILDREN = 4;
    public static final int MAX_DEPTH = 16;

    public record Input(float[] positions, float[] normals, float[] tangents,
                        float[] baseUv, float[] emissionUv, float[] uv0, float[] uv1,
                        int[] colors, int[] indices, GltfAsset.Material material) {
        public Input {
            Objects.requireNonNull(positions);
            Objects.requireNonNull(normals);
            Objects.requireNonNull(tangents);
            Objects.requireNonNull(baseUv);
            Objects.requireNonNull(emissionUv);
            Objects.requireNonNull(uv0);
            Objects.requireNonNull(uv1);
            Objects.requireNonNull(colors);
            Objects.requireNonNull(indices);
            Objects.requireNonNull(material);
        }
    }

    private static final class MutableNode {
        int parent = -1;
        int depth;
        int[] children;
        GltfAsset.Bounds bounds;
        float error;
        int sourceTriangles;
        ModelCpuClusterGeometry geometry;
        /** Small planar patches carried through proxies so two-triangle surfaces do not tear. */
        List<ModelCpuClusterGeometry> protectedPatches = List.of();

        MutableNode(GltfAsset.Bounds bounds, float error, int sourceTriangles,
                    ModelCpuClusterGeometry geometry) {
            this.bounds = bounds;
            this.error = error;
            this.sourceTriangles = sourceTriangles;
            this.geometry = geometry;
        }
    }

    private record TriangleOrder(int triangle, long mortonKey) {}

    private ModelCpuClusterBuilder() {}

    public static ModelCpuClusterHierarchy build(int primitiveId, Input input,
                                                 ModelCancellation token) throws IOException {
        return build(primitiveId, input, TARGET_LEAF_TRIANGLES, MAX_CHILDREN, token);
    }

    public static ModelCpuClusterHierarchy build(int primitiveId, Input input,
                                                 int targetLeafTriangles, int maxChildren,
                                                 ModelCancellation token) throws IOException {
        Objects.requireNonNull(input);
        Objects.requireNonNull(token);
        validateInput(input);
        if (primitiveId < -1) throw new IllegalArgumentException("primitiveId must be >= -1");
        if (input.material().alphaMode() == GltfAsset.AlphaMode.BLEND)
            return ModelCpuClusterHierarchy.disabled(primitiveId, input.material(), input.indices().length / 3,
                    "BLEND_TRANSPARENT_BSP");
        if (targetLeafTriangles < MIN_LEAF_TRIANGLES || targetLeafTriangles > MAX_LEAF_TRIANGLES)
            throw new IllegalArgumentException("targetLeafTriangles must be between 64 and 128");
        if (maxChildren < 2 || maxChildren > 8)
            throw new IllegalArgumentException("maxChildren must be between 2 and 8");

        final float[] positions = input.positions();
        final int[] indices = input.indices();
        final int vertexCount = positions.length / 3;
        final int triangleCount = indices.length / 3;
        GltfAsset.Bounds sourceBounds = bounds(positions);
        long[] ordered = mortonOrder(positions, indices, sourceBounds, token);

        int leafCount = triangleCount <= MAX_LEAF_TRIANGLES
                ? 1 : (triangleCount + targetLeafTriangles - 1) / targetLeafTriangles;
        // This partition keeps ordinary leaves in [64,128] while avoiding a
        // small tail. A primitive below 64 triangles remains one leaf.
        int[] leafSizes = partition(triangleCount, leafCount);
        int[] remap = new int[vertexCount];
        Arrays.fill(remap, -1);
        List<MutableNode> all = new ArrayList<>(leafCount * 2);
        List<Integer> current = new ArrayList<>(leafCount);
        int cursor = 0;
        for (int leaf = 0; leaf < leafCount; leaf++) {
            token.check();
            int count = leafSizes[leaf];
            GeometryAccumulator accumulator = new GeometryAccumulator(count);
            GltfAsset.Bounds leafBounds = GltfAsset.Bounds.empty();
            int touched = 0;
            for (int i = 0; i < count; i++) {
                int sourceTriangle = (int) ordered[cursor + i];
                int sourceOffset = sourceTriangle * 3;
                for (int corner = 0; corner < 3; corner++) {
                    int sourceVertex = indices[sourceOffset + corner];
                    int local = remap[sourceVertex];
                    if (local < 0) {
                        local = accumulator.addVertex(sourceVertex, input);
                        remap[sourceVertex] = local;
                        touched++;
                    }
                    accumulator.addIndex(local);
                    int p = sourceVertex * 3;
                    leafBounds = leafBounds.include(positions[p], positions[p + 1], positions[p + 2]);
                }
                if ((i & 255) == 0) token.check();
            }
            for (int i = 0; i < touched; i++) {
                int sourceVertex = accumulator.sourceVertex(i);
                remap[sourceVertex] = -1;
            }
            ModelCpuClusterGeometry geometry = accumulator.finish(leafBounds, false, count);
            MutableNode node = new MutableNode(leafBounds, 0, count, geometry);
            node.protectedPatches = detectProtectedPlanarPatches(geometry);
            all.add(node);
            current.add(all.size() - 1);
            cursor += count;
        }

        int depth = 0;
        while (current.size() > 1) {
            token.check();
            if (++depth > MAX_DEPTH) {
                // A four-way hierarchy over 64-128 triangle leaves should not
                // reach this in normal assets. Continue with wider groups only
                // when necessary, while preserving a bounded explicit stack.
                maxChildren = Math.max(maxChildren, 8);
            }
            List<Integer> next = new ArrayList<>((current.size() + maxChildren - 1) / maxChildren);
            for (int offset = 0; offset < current.size(); offset += maxChildren) {
                int childCount = Math.min(maxChildren, current.size() - offset);
                int[] children = new int[childCount];
                GltfAsset.Bounds parentBounds = GltfAsset.Bounds.empty();
                int sourceTriangles = 0;
                float childError = 0;
                int protectedTriangles = 0;
                for (int i = 0; i < childCount; i++) {
                    MutableNode child = all.get(current.get(offset + i));
                    for (ModelCpuClusterGeometry patch : child.protectedPatches)
                        protectedTriangles = Math.addExact(protectedTriangles, patch.triangleCount());
                }
                // Protected patch triangles are carried verbatim. The remaining
                // proxy budget is filled with one representative
                // per child. This
                // keeps the hierarchy cut triangle-bounded while preserving small
                // coplanar surfaces (windows, cards, thin panels) through every
                // ancestor instead of reducing them to a single diagonal triangle.
                int proxyCapacity = Math.max(1, Math.addExact(protectedTriangles, childCount));
                GeometryAccumulator proxy = new GeometryAccumulator(proxyCapacity);
                List<ModelCpuClusterGeometry> patches = new ArrayList<>();
                for (int i = 0; i < childCount; i++) {
                    int childId = current.get(offset + i);
                    children[i] = childId;
                    MutableNode child = all.get(childId);
                    child.parent = all.size();
                    parentBounds = include(parentBounds, child.bounds);
                    sourceTriangles = Math.addExact(sourceTriangles, child.sourceTriangles);
                    childError = Math.max(childError, child.error);
                    patches.addAll(child.protectedPatches);
                }
                // sourceTriangles is known only after the child scan above; patch
                // payload is disjoint source coverage, so append it first.
                for (ModelCpuClusterGeometry patch : patches) appendGeometry(proxy, patch);
                int representativeBudget = Math.max(0, sourceTriangles - proxy.triangleCount());
                for (int i = 0; i < childCount && representativeBudget > 0; i++) {
                    MutableNode child = all.get(children[i]);
                    representativeBudget -= appendRepresentative(proxy, child.geometry, representativeBudget, child.children == null || child.children.length == 0);
                }
                // Use the complete hierarchy-node bounds for culling. The
                // sampled/protected payload may occupy only part of that box;
                // using its tight bounds here can incorrectly frustum-cull a
                // valid proxy before it reaches the draw path.
                ModelCpuClusterGeometry proxyGeometry = proxy.finish(parentBounds, true, sourceTriangles);
                // Compare every child AABB extreme against the actual representative
                // triangles.  The old center-to-proxy-AABB estimate could be smaller
                // than the real Hausdorff error and make a proxy eligible too early.
                float error = Math.max(childError, proxyError(parentBounds, proxyGeometry, children, all));
                MutableNode parent = new MutableNode(parentBounds, error, sourceTriangles, proxyGeometry);
                parent.children = children;
                parent.depth = depth;
                parent.protectedPatches = List.copyOf(patches);
                all.add(parent);
                next.add(all.size() - 1);
            }
            current = next;
        }

        List<ModelCpuClusterNode> result = new ArrayList<>(all.size());
        for (int nodeId = 0; nodeId < all.size(); nodeId++) {
            MutableNode node = all.get(nodeId);
            result.add(new ModelCpuClusterNode(nodeId, node.parent,
                    node.children == null ? new int[0] : node.children,
                    node.depth, node.bounds, node.error, node.sourceTriangles, node.geometry));
        }
        int leafTriangles = leafCount == 0 ? 0 : leafSizes[0];
        return ModelCpuClusterHierarchy.ready(primitiveId, input.material(), result, current.get(0),
                triangleCount, leafTriangles);
    }

    private static void validateInput(Input input) {
        float[] positions = input.positions();
        int[] indices = input.indices();
        if (positions.length == 0 || positions.length % 3 != 0)
            throw new IllegalArgumentException("positions must contain xyz triples");
        if (indices.length == 0 || indices.length % 3 != 0)
            throw new IllegalArgumentException("indices must contain triangles");
        int vertices = positions.length / 3;
        validateOptionalLength(input.normals(), positions.length, "normals");
        validateOptionalLength(input.tangents(), vertices * 4, "tangents");
        validateOptionalLength(input.baseUv(), vertices * 2, "baseUv");
        validateOptionalLength(input.emissionUv(), vertices * 2, "emissionUv");
        validateOptionalLength(input.uv0(), vertices * 2, "uv0");
        validateOptionalLength(input.uv1(), vertices * 2, "uv1");
        validateOptionalLength(input.colors(), vertices, "colors");
        for (int index : indices) if (index < 0 || index >= vertices)
            throw new IllegalArgumentException("index out of range");
        for (float value : positions) if (!Float.isFinite(value))
            throw new IllegalArgumentException("positions must be finite");
    }

    private static void validateOptionalLength(float[] values, int expected, String name) {
        if (values.length != 0 && values.length != expected)
            throw new IllegalArgumentException(name + " has invalid length");
    }

    private static void validateOptionalLength(int[] values, int expected, String name) {
        if (values.length != 0 && values.length != expected)
            throw new IllegalArgumentException(name + " has invalid length");
    }

    private static int[] partition(int triangles, int leaves) {
        int[] sizes = new int[leaves];
        int base = triangles / leaves;
        int remainder = triangles % leaves;
        for (int i = 0; i < leaves; i++) sizes[i] = base + (i < remainder ? 1 : 0);
        return sizes;
    }

    private static long[] mortonOrder(float[] positions, int[] indices, GltfAsset.Bounds bounds,
                                      ModelCancellation token) throws IOException {
        int triangles = indices.length / 3;
        long[] keys = new long[triangles];
        float dx = bounds.maxX() - bounds.minX();
        float dy = bounds.maxY() - bounds.minY();
        float dz = bounds.maxZ() - bounds.minZ();
        for (int triangle = 0; triangle < triangles; triangle++) {
            if ((triangle & 1023) == 0) token.check();
            int offset = triangle * 3;
            float cx = (positions[indices[offset] * 3] + positions[indices[offset + 1] * 3] + positions[indices[offset + 2] * 3]) / 3f;
            float cy = (positions[indices[offset] * 3 + 1] + positions[indices[offset + 1] * 3 + 1] + positions[indices[offset + 2] * 3 + 1]) / 3f;
            float cz = (positions[indices[offset] * 3 + 2] + positions[indices[offset + 1] * 3 + 2] + positions[indices[offset + 2] * 3 + 2]) / 3f;
            int x = quantize(cx, bounds.minX(), dx);
            int y = quantize(cy, bounds.minY(), dy);
            int z = quantize(cz, bounds.minZ(), dz);
            long morton = spread10(x) | (spread10(y) << 1) | (spread10(z) << 2);
            keys[triangle] = (morton << 32) | (triangle & 0xffffffffL);
        }
        Arrays.sort(keys);
        long[] ordered = new long[triangles];
        for (int i = 0; i < keys.length; i++) ordered[i] = keys[i] & 0xffffffffL;
        return ordered;
    }

    private static int quantize(float value, float min, float extent) {
        if (!(extent > 0) || !Float.isFinite(extent)) return 0;
        return Math.clamp(Math.round((value - min) / extent * 1023f), 0, 1023);
    }

    private static long spread10(int value) {
        long x = value & 0x3ffL;
        x = (x | x << 16) & 0x30000ffL;
        x = (x | x << 8) & 0x300f00fL;
        x = (x | x << 4) & 0x30c30c3L;
        return (x | x << 2) & 0x9249249L;
    }

    private static GltfAsset.Bounds bounds(float[] positions) {
        GltfAsset.Bounds result = GltfAsset.Bounds.empty();
        for (int i = 0; i < positions.length; i += 3)
            result = result.include(positions[i], positions[i + 1], positions[i + 2]);
        return result;
    }

    private static GltfAsset.Bounds include(GltfAsset.Bounds a, GltfAsset.Bounds b) {
        return a.include(b.minX(), b.minY(), b.minZ()).include(b.maxX(), b.maxY(), b.maxZ());
    }

    /**
     * Finds small connected coplanar patches in a full leaf. A proxy made from
     * one arbitrary triangle is allowed for ordinary geometry, but it is unsafe
     * for a two-triangle plane: the diagonal triangle can disappear at distance
     * and leave a visible hole. Such patches are carried verbatim by ancestors.
     */
    private static List<ModelCpuClusterGeometry> detectProtectedPlanarPatches(ModelCpuClusterGeometry geometry) {
        int triangles = geometry.triangleCount();
        if (triangles < 2) return List.of();
        float[] positions = geometry.rawPositions();
        int[] indices = geometry.rawIndices();
        TriangleInfo[] info = new TriangleInfo[triangles];
        double extent = 0.0;
        GltfAsset.Bounds bounds = geometry.bounds();
        extent = Math.max(extent, bounds.maxX() - bounds.minX());
        extent = Math.max(extent, bounds.maxY() - bounds.minY());
        extent = Math.max(extent, bounds.maxZ() - bounds.minZ());
        double tolerance = Math.max(1.0e-6, extent * 1.0e-5);
        for (int t = 0; t < triangles; t++) info[t] = triangleInfo(positions, indices, t);

        // Group by quantized position rather than index identity. Exporters
        // frequently duplicate vertices at UV seams; the two triangles of a
        // window then have no shared index even though their edge positions are
        // identical.
        Map<PositionKey, ArrayList<Integer>> byPosition = new HashMap<>();
        for (int t = 0; t < triangles; t++) {
            int offset = t * 3;
            for (int corner = 0; corner < 3; corner++) {
                int vertex = indices[offset + corner] * 3;
                PositionKey key = new PositionKey(Math.round(positions[vertex] / tolerance),
                        Math.round(positions[vertex + 1] / tolerance),
                        Math.round(positions[vertex + 2] / tolerance));
                byPosition.computeIfAbsent(key, ignored -> new ArrayList<>()).add(t);
            }
        }
        int[] parent = new int[triangles];
        Arrays.setAll(parent, i -> i);
        int[] seen = new int[triangles];
        int stamp = 1;
        for (int t = 0; t < triangles; t++) {
            TriangleInfo a = info[t];
            if (!a.valid) continue;
            stamp++;
            int offset = t * 3;
            for (int corner = 0; corner < 3; corner++) {
                int vertex = indices[offset + corner] * 3;
                PositionKey key = new PositionKey(Math.round(positions[vertex] / tolerance),
                        Math.round(positions[vertex + 1] / tolerance),
                        Math.round(positions[vertex + 2] / tolerance));
                ArrayList<Integer> adjacent = byPosition.get(key);
                if (adjacent == null) continue;
                for (int other : adjacent) {
                    if (other <= t || seen[other] == stamp) continue;
                    seen[other] = stamp;
                    TriangleInfo b = info[other];
                    if (b.valid && coplanar(a, b, tolerance)) union(parent, t, other);
                }
            }
        }
        Map<Integer, ArrayList<Integer>> groups = new HashMap<>();
        for (int t = 0; t < triangles; t++) {
            if (!info[t].valid) continue;
            groups.computeIfAbsent(find(parent, t), ignored -> new ArrayList<>()).add(t);
        }
        List<ModelCpuClusterGeometry> result = new ArrayList<>();
        for (ArrayList<Integer> group : groups.values()) {
            // Large coplanar walls are ordinary LOD material; preserving every
            // triangle would erase the CPUC benefit. The guard targets small
            // surface patches such as the common two-triangle window/card.
            if (group.size() < 2 || group.size() > 16) continue;
            GeometryAccumulator patch = new GeometryAccumulator(group.size());
            for (int triangle : group) appendTriangle(patch, geometry, triangle);
            if (patch.triangleCount() == group.size())
                result.add(patch.finish(patch.bounds(), true, group.size()));
        }
        return result.isEmpty() ? List.of() : List.copyOf(result);
    }

    private static TriangleInfo triangleInfo(float[] positions, int[] indices, int triangle) {
        int offset = triangle * 3;
        int ia = indices[offset] * 3, ib = indices[offset + 1] * 3, ic = indices[offset + 2] * 3;
        double abx = positions[ib] - positions[ia], aby = positions[ib + 1] - positions[ia + 1], abz = positions[ib + 2] - positions[ia + 2];
        double acx = positions[ic] - positions[ia], acy = positions[ic + 1] - positions[ia + 1], acz = positions[ic + 2] - positions[ia + 2];
        double nx = aby * acz - abz * acy, ny = abz * acx - abx * acz, nz = abx * acy - aby * acx;
        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (!(length > 1.0e-12) || !Double.isFinite(length)) return TriangleInfo.INVALID;
        nx /= length; ny /= length; nz /= length;
        double d = nx * positions[ia] + ny * positions[ia + 1] + nz * positions[ia + 2];
        return new TriangleInfo(nx, ny, nz, d, true);
    }

    private static boolean coplanar(TriangleInfo a, TriangleInfo b, double tolerance) {
        double dot = a.nx * b.nx + a.ny * b.ny + a.nz * b.nz;
        if (Math.abs(dot) < 0.9999) return false;
        double signedDistance = dot < 0.0 ? -b.d : b.d;
        return Math.abs(a.d - signedDistance) <= tolerance;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a), rb = find(parent, b);
        if (ra != rb) parent[rb] = ra;
    }

    private static int find(int[] parent, int value) {
        int root = value;
        while (parent[root] != root) root = parent[root];
        while (parent[value] != value) {
            int next = parent[value]; parent[value] = root; value = next;
        }
        return root;
    }

    private record PositionKey(long x, long y, long z) {}

    private record TriangleInfo(double nx, double ny, double nz, double d, boolean valid) {
        private static final TriangleInfo INVALID = new TriangleInfo(0, 0, 0, 0, false);
    }

    private static float proxyError(GltfAsset.Bounds parentBounds, ModelCpuClusterGeometry proxy,
                                    int[] children, List<MutableNode> nodes) {
        float error = 0.0f;
        float[] proxyPositions = proxy.rawPositions();
        int[] proxyIndices = proxy.rawIndices();
        for (int childId : children) {
            MutableNode child = nodes.get(childId);
            GltfAsset.Bounds bounds = child.bounds;
            double cx = (bounds.minX() + bounds.maxX()) * 0.5;
            double cy = (bounds.minY() + bounds.maxY()) * 0.5;
            double cz = (bounds.minZ() + bounds.maxZ()) * 0.5;
            double nearest = Double.POSITIVE_INFINITY;
            for (int offset = 0; offset < proxyIndices.length; offset += 3) {
                int a = proxyIndices[offset] * 3, b = proxyIndices[offset + 1] * 3, c = proxyIndices[offset + 2] * 3;
                nearest = Math.min(nearest, pointTriangleDistanceSquared(cx, cy, cz,
                        proxyPositions[a], proxyPositions[a + 1], proxyPositions[a + 2],
                        proxyPositions[b], proxyPositions[b + 1], proxyPositions[b + 2],
                        proxyPositions[c], proxyPositions[c + 1], proxyPositions[c + 2]));
            }
            double dx = bounds.maxX() - bounds.minX(), dy = bounds.maxY() - bounds.minY(), dz = bounds.maxZ() - bounds.minZ();
            // Any point in the child AABB is at most half its diagonal from the
            // center; adding that radius to the center-to-proxy distance is a
            // conservative bound for the entire child volume.
            double bound = Math.sqrt(Math.max(0.0, nearest)) + 0.5 * Math.sqrt(dx * dx + dy * dy + dz * dz);
            error = Math.max(error, (float) (child.error + bound));
        }
        float px = parentBounds.maxX() - parentBounds.minX();
        float py = parentBounds.maxY() - parentBounds.minY();
        float pz = parentBounds.maxZ() - parentBounds.minZ();
        return Math.max(error, (float) (Math.sqrt(px * px + py * py + pz * pz) * 1e-5));
    }

    /** Squared distance from a point to a triangle, using the Ericson closest-point regions. */
    private static double pointTriangleDistanceSquared(double px, double py, double pz,
                                                       double ax, double ay, double az,
                                                       double bx, double by, double bz,
                                                       double cx, double cy, double cz) {
        double abx = bx - ax, aby = by - ay, abz = bz - az;
        double acx = cx - ax, acy = cy - ay, acz = cz - az;
        double nx = aby * acz - abz * acy, ny = abz * acx - abx * acz, nz = abx * acy - aby * acx;
        double nn = nx * nx + ny * ny + nz * nz;
        if (nn <= 1e-24) {
            // Degenerate triangle: the closest point is on one of its edges.
            return Math.min(pointSegmentDistanceSquared(px, py, pz, ax, ay, az, bx, by, bz),
                    Math.min(pointSegmentDistanceSquared(px, py, pz, ax, ay, az, cx, cy, cz),
                            pointSegmentDistanceSquared(px, py, pz, bx, by, bz, cx, cy, cz)));
        }
        double apx = px - ax, apy = py - ay, apz = pz - az;
        double d1 = abx * apx + aby * apy + abz * apz;
        double d2 = acx * apx + acy * apy + acz * apz;
        if (d1 <= 0.0 && d2 <= 0.0) return apx * apx + apy * apy + apz * apz;
        double bpx = px - bx, bpy = py - by, bpz = pz - bz;
        double d3 = abx * bpx + aby * bpy + abz * bpz;
        double d4 = acx * bpx + acy * bpy + acz * bpz;
        if (d3 >= 0.0 && d4 <= d3) return bpx * bpx + bpy * bpy + bpz * bpz;
        double vc = d1 * d4 - d3 * d2;
        if (vc <= 0.0 && d1 >= 0.0 && d3 <= 0.0) {
            double v = d1 / (d1 - d3);
            double qx = apx - v * abx, qy = apy - v * aby, qz = apz - v * abz;
            return qx * qx + qy * qy + qz * qz;
        }
        double cpx = px - cx, cpy = py - cy, cpz = pz - cz;
        double d5 = abx * cpx + aby * cpy + abz * cpz;
        double d6 = acx * cpx + acy * cpy + acz * cpz;
        if (d6 >= 0.0 && d5 <= d6) return cpx * cpx + cpy * cpy + cpz * cpz;
        double vb = d5 * d2 - d1 * d6;
        if (vb <= 0.0 && d2 >= 0.0 && d6 <= 0.0) {
            double w = d2 / (d2 - d6);
            double qx = apx - w * acx, qy = apy - w * acy, qz = apz - w * acz;
            return qx * qx + qy * qy + qz * qz;
        }
        double va = d3 * d6 - d5 * d4;
        if (va <= 0.0 && (d4 - d3) >= 0.0 && (d5 - d6) >= 0.0) {
            double w = (d4 - d3) / ((d4 - d3) + (d5 - d6));
            double qx = bpx - w * (cx - bx), qy = bpy - w * (cy - by), qz = bpz - w * (cz - bz);
            return qx * qx + qy * qy + qz * qz;
        }
        double distance = (px - ax) * nx + (py - ay) * ny + (pz - az) * nz;
        return distance * distance / nn;
    }

    private static double pointSegmentDistanceSquared(double px, double py, double pz,
                                                      double ax, double ay, double az,
                                                      double bx, double by, double bz) {
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double lengthSquared = dx * dx + dy * dy + dz * dz;
        double t = lengthSquared <= 1e-24 ? 0.0 : Math.clamp(((px - ax) * dx + (py - ay) * dy + (pz - az) * dz) / lengthSquared, 0.0, 1.0);
        double qx = px - (ax + t * dx), qy = py - (ay + t * dy), qz = pz - (az + t * dz);
        return qx * qx + qy * qy + qz * qz;
    }

    private static void appendGeometry(GeometryAccumulator target, ModelCpuClusterGeometry source) {
        for (int triangle = 0; triangle < source.triangleCount(); triangle++)
            appendTriangle(target, source, triangle);
    }

    private static void appendTriangle(GeometryAccumulator target, ModelCpuClusterGeometry source, int triangle) {
        float[] positions = source.rawPositions();
        float[] normals = source.rawNormals();
        float[] tangents = source.rawTangents();
        float[] baseUv = source.rawBaseUv();
        float[] emissionUv = source.rawEmissionUv();
        float[] uv0 = source.rawUv0();
        float[] uv1 = source.rawUv1();
        int[] colors = source.rawColors();
        int[] indices = source.rawIndices();
        int offset = triangle * 3;
        for (int corner = 0; corner < 3; corner++) {
            int vertex = indices[offset + corner];
            target.addCopiedVertex(vertex, positions, normals, tangents, baseUv, emissionUv, uv0, uv1, colors);
        }
        target.addIndex(target.vertexCount() - 3);
        target.addIndex(target.vertexCount() - 2);
        target.addIndex(target.vertexCount() - 1);
    }

    private static int appendRepresentative(GeometryAccumulator target, ModelCpuClusterGeometry source,
                                             int budget, boolean leaf) {
        if (budget <= 0 || source.triangleCount() <= 0) return 0;
        int samples = leaf ? Math.min(4, Math.min(source.triangleCount(), budget)) : 1;
        for (int sample = 0; sample < samples; sample++) {
            int triangle = samples == 1 ? 0
                    : Math.round(sample * (source.triangleCount() - 1f) / (samples - 1f));
            appendTriangle(target, source, triangle);
        }
        return samples;
    }

    private static final class GeometryAccumulator {
        private float[] positions, normals, tangents, baseUv, emissionUv, uv0, uv1;
        private int[] colors, indices, sourceVertices;
        private int vertices, indicesUsed;
        private GltfAsset.Bounds bounds = GltfAsset.Bounds.empty();

        GeometryAccumulator(int capacityTriangles) {
            int capacityVertices = Math.max(3, capacityTriangles * 3);
            positions = new float[capacityVertices * 3];
            normals = new float[capacityVertices * 3];
            tangents = new float[capacityVertices * 4];
            baseUv = new float[capacityVertices * 2];
            emissionUv = new float[capacityVertices * 2];
            uv0 = new float[capacityVertices * 2];
            uv1 = new float[capacityVertices * 2];
            colors = new int[capacityVertices];
            indices = new int[capacityTriangles * 3];
            sourceVertices = new int[capacityVertices];
        }

        int addVertex(int sourceVertex, Input input) {
            ensureVertexCapacity(vertices + 1);
            int p = vertices * 3;
            float[] sourcePositions = input.positions();
            System.arraycopy(sourcePositions, sourceVertex * 3, positions, p, 3);
            copyOrZero(input.normals(), sourceVertex * 3, normals, p, 3);
            copyOrZero(input.tangents(), sourceVertex * 4, tangents, vertices * 4, 4);
            copyOrZero(input.baseUv(), sourceVertex * 2, baseUv, vertices * 2, 2);
            copyOrZero(input.emissionUv(), sourceVertex * 2, emissionUv, vertices * 2, 2);
            copyOrZero(input.uv0(), sourceVertex * 2, uv0, vertices * 2, 2);
            copyOrZero(input.uv1(), sourceVertex * 2, uv1, vertices * 2, 2);
            colors[vertices] = input.colors().length == 0 ? 0xffffffff : input.colors()[sourceVertex];
            sourceVertices[vertices] = sourceVertex;
            vertices++;
            return vertices - 1;
        }

        void addCopiedVertex(int sourceVertex, float[] sourcePositions, float[] sourceNormals,
                             float[] sourceTangents, float[] sourceBaseUv, float[] sourceEmissionUv,
                             float[] sourceUv0, float[] sourceUv1, int[] sourceColors) {
            ensureVertexCapacity(vertices + 1);
            int p = vertices * 3;
            System.arraycopy(sourcePositions, sourceVertex * 3, positions, p, 3);
            copyOrZero(sourceNormals, sourceVertex * 3, normals, p, 3);
            copyOrZero(sourceTangents, sourceVertex * 4, tangents, vertices * 4, 4);
            copyOrZero(sourceBaseUv, sourceVertex * 2, baseUv, vertices * 2, 2);
            copyOrZero(sourceEmissionUv, sourceVertex * 2, emissionUv, vertices * 2, 2);
            copyOrZero(sourceUv0, sourceVertex * 2, uv0, vertices * 2, 2);
            copyOrZero(sourceUv1, sourceVertex * 2, uv1, vertices * 2, 2);
            colors[vertices] = sourceColors.length == 0 ? 0xffffffff : sourceColors[sourceVertex];
            bounds = bounds.include(positions[p], positions[p + 1], positions[p + 2]);
            sourceVertices[vertices] = sourceVertex;
            vertices++;
        }

        void addIndex(int index) {
            if (indicesUsed >= indices.length) indices = Arrays.copyOf(indices, Math.max(indicesUsed + 3, indices.length * 2));
            indices[indicesUsed++] = index;
        }

        int sourceVertex(int local) { return sourceVertices[local]; }
        int vertexCount() { return vertices; }
        int triangleCount() { return indicesUsed / 3; }
        GltfAsset.Bounds bounds() { return bounds; }

        ModelCpuClusterGeometry finish(GltfAsset.Bounds geometryBounds, boolean proxy, int sourceTriangles) {
            return ModelCpuClusterGeometry.owned(Arrays.copyOf(positions, vertices * 3),
                    Arrays.copyOf(normals, vertices * 3), Arrays.copyOf(tangents, vertices * 4),
                    Arrays.copyOf(baseUv, vertices * 2), Arrays.copyOf(emissionUv, vertices * 2),
                    Arrays.copyOf(uv0, vertices * 2), Arrays.copyOf(uv1, vertices * 2),
                    Arrays.copyOf(colors, vertices), Arrays.copyOf(indices, indicesUsed),
                    geometryBounds, proxy, sourceTriangles);
        }

        private void ensureVertexCapacity(int required) {
            if (required <= sourceVertices.length) return;
            int next = Math.max(required, sourceVertices.length * 2);
            positions = Arrays.copyOf(positions, next * 3);
            normals = Arrays.copyOf(normals, next * 3);
            tangents = Arrays.copyOf(tangents, next * 4);
            baseUv = Arrays.copyOf(baseUv, next * 2);
            emissionUv = Arrays.copyOf(emissionUv, next * 2);
            uv0 = Arrays.copyOf(uv0, next * 2);
            uv1 = Arrays.copyOf(uv1, next * 2);
            colors = Arrays.copyOf(colors, next);
            sourceVertices = Arrays.copyOf(sourceVertices, next);
        }

        private static void copyOrZero(float[] source, int sourceOffset, float[] target, int targetOffset, int count) {
            if (source.length == 0) Arrays.fill(target, targetOffset, targetOffset + count, 0);
            else System.arraycopy(source, sourceOffset, target, targetOffset, count);
        }
    }
}
