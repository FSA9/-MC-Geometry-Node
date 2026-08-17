package com.mine.geometry_node.client.model.render.backend.host.lod;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import static org.lwjgl.util.meshoptimizer.MeshOptimizer.meshopt_SimplifyErrorAbsolute;
import static org.lwjgl.util.meshoptimizer.MeshOptimizer.meshopt_SimplifyPermissive;
import static org.lwjgl.util.meshoptimizer.MeshOptimizer.meshopt_SimplifySparse;
import static org.lwjgl.util.meshoptimizer.MeshOptimizer.meshopt_simplifyWithAttributes;

/** Immutable whole-primitive proxy levels used by one model-wide requested LOD. */
public final class HostModelLodPlan {
    public static final int MIN_SOURCE_TRIANGLES = 256;
    public static final int MIN_SIMPLIFIABLE_COMPONENT_TRIANGLES = 64;
    public static final int MAX_SIMPLIFIED_COMPONENTS = 256;
    public static final int MAX_COMPONENT_ANALYSIS_TRIANGLES = 500_000;
    public static final float[] TARGET_RATIOS = {0.70F, 0.40F, 0.20F};
    private static final float MAX_PROXY_RATIO = 1.30F;
    private static final int COMPONENTS_PER_VERTEX = 12;
    private static final int ATTRIBUTE_COMPONENTS = 9;

    private final List<Level> levels;
    private final float[] proxyVertices;
    private final Statistics statistics;

    private HostModelLodPlan(List<Level> levels, float[] proxyVertices, Statistics statistics) {
        this.levels = List.copyOf(levels);
        this.proxyVertices = Arrays.copyOf(proxyVertices, proxyVertices.length);
        this.statistics = statistics;
    }

    public static HostModelLodPlan build(float[] source) {
        return build(source, null, 0);
    }

    public static HostModelLodPlan build(float[] source, int[] canonicalIndices, int canonicalVertexCount) {
        long started = System.nanoTime();
        int sourceTriangles = source.length / (3 * COMPONENTS_PER_VERTEX);
        Level sourceLevel = new Level(0, sourceTriangles, 0.0F, 0);
        List<Level> levels = new ArrayList<>(List.of(sourceLevel));
        if (sourceTriangles < MIN_SOURCE_TRIANGLES) {
            while (levels.size() < 4) levels.add(sourceLevel);
            return new HostModelLodPlan(levels, new float[0],
                    new Statistics(sourceTriangles, 0, 0, 0, StopReason.BELOW_MINIMUM,
                            0, 0, System.nanoTime() - started, 0, 0));
        }

        try {
            Mesh mesh = canonicalIndices == null
                    ? Mesh.fromExpanded(source)
                    : Mesh.fromCanonical(source, canonicalIndices, canonicalVertexCount);
            ComponentPlan components = ComponentPlan.build(mesh);
            if (components == null) {
                while (levels.size() < 4) levels.add(sourceLevel);
                return new HostModelLodPlan(levels, new float[0],
                        new Statistics(sourceTriangles, sourceTriangles, sourceTriangles, sourceTriangles,
                                StopReason.COMPONENT_LIMIT, mesh.vertexCount(), mesh.lockedVertexCount(),
                                System.nanoTime() - started, 0, 0));
            }
            FloatArray proxies = new FloatArray(Math.max(36, source.length));
            Level previous = sourceLevel;
            int generated = 0;
            long proxyTriangles = 0;
            long proxyTriangleBudget = (long) Math.floor(sourceTriangles * MAX_PROXY_RATIO);
            for (int index = 0; index < TARGET_RATIOS.length; index++) {
                Simplified simplified = simplify(mesh, components, TARGET_RATIOS[index]);
                if (simplified.indices.length >= previous.triangleCount * 3 || simplified.indices.length < 3) {
                    break;
                }
                int triangles = simplified.indices.length / 3;
                if (triangles > proxyTriangleBudget - proxyTriangles) break;
                int firstTriangle = sourceTriangles + Math.toIntExact(proxyTriangles);
                proxies.addAll(mesh.expand(simplified.indices));
                Level level = new Level(firstTriangle, triangles, simplified.error, index + 1);
                levels.add(level);
                previous = level;
                generated++;
                proxyTriangles += triangles;
            }
            while (levels.size() < 4) levels.add(previous);
            StopReason reason = generated == 3 ? StopReason.COMPLETE : StopReason.NO_REDUCTION;
            return new HostModelLodPlan(levels, proxies.toArray(),
                    new Statistics(sourceTriangles, levels.get(1).triangleCount,
                            levels.get(2).triangleCount, levels.get(3).triangleCount,
                            reason, mesh.vertexCount(), mesh.lockedVertexCount(),
                            System.nanoTime() - started, components.componentCount,
                            components.protectedComponentCount));
        } catch (CanonicalOccurrenceMismatch failure) {
            while (levels.size() < 4) levels.add(sourceLevel);
            return new HostModelLodPlan(levels, new float[0],
                    new Statistics(sourceTriangles, sourceTriangles, sourceTriangles, sourceTriangles,
                            StopReason.ATTRIBUTE_OCCURRENCE_MISMATCH, 0, 0,
                            System.nanoTime() - started, 0, 0));
        } catch (RuntimeException | LinkageError failure) {
            while (levels.size() < 4) levels.add(sourceLevel);
            return new HostModelLodPlan(levels, new float[0],
                    new Statistics(sourceTriangles, sourceTriangles, sourceTriangles, sourceTriangles,
                            StopReason.BUILD_FAILURE, 0, 0, System.nanoTime() - started, 0, 0));
        }
    }

    private static Simplified simplify(Mesh mesh, ComponentPlan components, float ratio) {
        IntArray selected = new IntArray(mesh.indices.length);
        selected.addAll(components.protectedIndices);
        float error = 0.0F;
        for (int[] component : components.simplifiableIndices) {
            Simplified simplified = simplify(mesh, component, ratio);
            selected.addAll(simplified.indices);
            error = Math.max(error, simplified.error);
        }
        return new Simplified(selected.toArray(), error);
    }

    private static Simplified simplify(Mesh mesh, int[] sourceIndices, float ratio) {
        int target = Math.max(3, ((int) Math.floor(sourceIndices.length * ratio) / 3) * 3);
        if (target >= sourceIndices.length) return new Simplified(sourceIndices, 0.0F);
        IntBuffer input = MemoryUtil.memAllocInt(sourceIndices.length).put(sourceIndices).flip();
        IntBuffer output = MemoryUtil.memAllocInt(sourceIndices.length);
        FloatBuffer positions = MemoryUtil.memAllocFloat(mesh.positions.length).put(mesh.positions).flip();
        FloatBuffer attributes = MemoryUtil.memAllocFloat(mesh.attributes.length).put(mesh.attributes).flip();
        FloatBuffer weights = MemoryUtil.memAllocFloat(ATTRIBUTE_COMPONENTS)
                .put(new float[]{0.35F, 0.35F, 0.35F, 0.5F, 0.5F, 0.08F, 0.08F, 0.08F, 0.08F}).flip();
        ByteBuffer locks = MemoryUtil.memAlloc(mesh.locks.length).put(mesh.locks).flip();
        FloatBuffer error = MemoryUtil.memAllocFloat(1);
        try {
            long count = meshopt_simplifyWithAttributes(output, input, positions, mesh.vertexCount(),
                    3L * Float.BYTES, attributes, (long) ATTRIBUTE_COMPONENTS * Float.BYTES,
                    weights, locks, target, Float.MAX_VALUE,
                    meshopt_SimplifySparse | meshopt_SimplifyErrorAbsolute | meshopt_SimplifyPermissive, error);
            if (count < 3 || count > target || count >= sourceIndices.length || count % 3 != 0) {
                return new Simplified(sourceIndices, 0.0F);
            }
            int[] result = new int[(int) count];
            output.get(0, result);
            return new Simplified(result, Math.max(0.0F, error.get(0)));
        } finally {
            MemoryUtil.memFree(input);
            MemoryUtil.memFree(output);
            MemoryUtil.memFree(positions);
            MemoryUtil.memFree(attributes);
            MemoryUtil.memFree(weights);
            MemoryUtil.memFree(locks);
            MemoryUtil.memFree(error);
        }
    }

    public List<Level> levels() { return levels; }
    public Level level(int requested) { return levels.get(Math.clamp(requested, 0, 3)); }
    public int proxyTriangleCount() { return proxyVertices.length / (3 * COMPONENTS_PER_VERTEX); }
    public int staticTriangleCount() { return statistics.sourceTriangles + proxyTriangleCount(); }
    public float[] proxyVertexData() { return Arrays.copyOf(proxyVertices, proxyVertices.length); }
    public float proxyComponent(int index) { return proxyVertices[index]; }
    public Statistics statistics() { return statistics; }

    public static long estimatedProxyBytes(long sourceTriangles) {
        if (sourceTriangles < 0) throw new IllegalArgumentException("negative source triangle count");
        if (sourceTriangles < MIN_SOURCE_TRIANGLES) return 0;
        long sourceBytes = Math.multiplyExact(sourceTriangles, 3L * COMPONENTS_PER_VERTEX * Float.BYTES);
        return Math.addExact(Math.multiplyExact(sourceBytes, 130), 99) / 100;
    }

    public record Level(int firstTriangle, int triangleCount, float objectError, int generatedLevel) {
        public Level {
            if (firstTriangle < 0 || triangleCount < 1 || !Float.isFinite(objectError)
                    || objectError < 0 || generatedLevel < 0 || generatedLevel > 3) {
                throw new IllegalArgumentException("invalid model LOD level");
            }
        }
    }

    public record Statistics(int sourceTriangles, int level1Triangles, int level2Triangles, int level3Triangles,
                             StopReason stopReason, int eligibleVertices, int lockedVertices, long buildNanos,
                             int componentCount, int protectedComponentCount) {
        public double lockedRatio() {
            return eligibleVertices == 0 ? 0.0 : (double) lockedVertices / eligibleVertices;
        }
    }

    public enum StopReason {
        BELOW_MINIMUM, NO_REDUCTION, COMPLETE, COMPONENT_LIMIT, ATTRIBUTE_OCCURRENCE_MISMATCH, BUILD_FAILURE
    }

    private record Simplified(int[] indices, float error) {}

    private static final class ComponentPlan {
        private final int[] protectedIndices;
        private final List<int[]> simplifiableIndices;
        private final int componentCount;
        private final int protectedComponentCount;

        private ComponentPlan(int[] protectedIndices, List<int[]> simplifiableIndices,
                              int componentCount, int protectedComponentCount) {
            this.protectedIndices = protectedIndices;
            this.simplifiableIndices = List.copyOf(simplifiableIndices);
            this.componentCount = componentCount;
            this.protectedComponentCount = protectedComponentCount;
        }

        private static ComponentPlan build(Mesh mesh) {
            int triangleCount = mesh.indices.length / 3;
            if (triangleCount > MAX_COMPONENT_ANALYSIS_TRIANGLES) return null;
            int[] roots = triangleComponents(mesh.indices, mesh.positions);
            int[] counts = new int[triangleCount];
            int[] first = new int[triangleCount];
            Arrays.fill(first, Integer.MAX_VALUE);
            for (int triangle = 0; triangle < triangleCount; triangle++) {
                int root = roots[triangle];
                counts[root]++;
                first[root] = Math.min(first[root], triangle);
            }
            int componentCount = 0;
            PriorityQueue<ComponentCandidate> largest = new PriorityQueue<>((left, right) -> {
                int bySize = Integer.compare(left.triangleCount, right.triangleCount);
                return bySize != 0 ? bySize : Integer.compare(right.firstTriangle, left.firstTriangle);
            });
            for (int root = 0; root < triangleCount; root++) {
                if (counts[root] == 0) continue;
                componentCount++;
                if (counts[root] < MIN_SIMPLIFIABLE_COMPONENT_TRIANGLES) continue;
                ComponentCandidate candidate = new ComponentCandidate(root, counts[root], first[root]);
                if (largest.size() < MAX_SIMPLIFIED_COMPONENTS) largest.add(candidate);
                else if (largest.comparator().compare(candidate, largest.peek()) > 0) {
                    largest.poll();
                    largest.add(candidate);
                }
            }
            List<ComponentCandidate> selected = new ArrayList<>(largest);
            selected.sort((left, right) -> Integer.compare(left.firstTriangle, right.firstTriangle));
            int[] selectedIndex = new int[triangleCount];
            Arrays.fill(selectedIndex, -1);
            List<IntArray> componentIndices = new ArrayList<>(selected.size());
            for (int index = 0; index < selected.size(); index++) {
                ComponentCandidate candidate = selected.get(index);
                selectedIndex[candidate.root] = index;
                componentIndices.add(new IntArray(candidate.triangleCount * 3));
            }
            IntArray protectedIndices = new IntArray(mesh.indices.length);
            for (int triangle = 0; triangle < triangleCount; triangle++) {
                int offset = triangle * 3;
                int selectedComponent = selectedIndex[roots[triangle]];
                IntArray target = selectedComponent < 0 ? protectedIndices : componentIndices.get(selectedComponent);
                target.add(mesh.indices[offset]);
                target.add(mesh.indices[offset + 1]);
                target.add(mesh.indices[offset + 2]);
            }
            List<int[]> simplifiable = new ArrayList<>(componentIndices.size());
            for (IntArray value : componentIndices) simplifiable.add(value.toArray());
            return new ComponentPlan(protectedIndices.toArray(), simplifiable, componentCount,
                    componentCount - simplifiable.size());
        }

        private static int[] triangleComponents(int[] indices, float[] positions) {
            int vertexCount = positions.length / 3;
            Map<PositionKey, Integer> ids = new HashMap<>();
            int[] positionIds = new int[vertexCount];
            for (int vertex = 0; vertex < vertexCount; vertex++) {
                PositionKey key = new PositionKey(positions, vertex * 3);
                positionIds[vertex] = ids.computeIfAbsent(key, ignored -> ids.size());
            }
            int triangleCount = indices.length / 3;
            DisjointSet components = new DisjointSet(triangleCount);
            GeometricEdgeTable edges = new GeometricEdgeTable(Math.multiplyExact(triangleCount, 3));
            for (int triangle = 0; triangle < triangleCount; triangle++) {
                int offset = triangle * 3;
                edges.add(positionIds[indices[offset]], positionIds[indices[offset + 1]], triangle);
                edges.add(positionIds[indices[offset + 1]], positionIds[indices[offset + 2]], triangle);
                edges.add(positionIds[indices[offset + 2]], positionIds[indices[offset]], triangle);
            }
            edges.connectManifoldPairs(components);
            int[] roots = new int[triangleCount];
            for (int triangle = 0; triangle < triangleCount; triangle++) roots[triangle] = components.find(triangle);
            return roots;
        }
    }

    private record ComponentCandidate(int root, int triangleCount, int firstTriangle) {}

    private static final class DisjointSet {
        private final int[] parent;
        private final byte[] rank;
        private DisjointSet(int size) {
            parent = new int[size];
            rank = new byte[size];
            for (int index = 0; index < size; index++) parent[index] = index;
        }
        private int find(int value) {
            int root = value;
            while (parent[root] != root) root = parent[root];
            while (parent[value] != value) {
                int next = parent[value];
                parent[value] = root;
                value = next;
            }
            return root;
        }
        private void union(int left, int right) {
            int leftRoot = find(left), rightRoot = find(right);
            if (leftRoot == rightRoot) return;
            if (rank[leftRoot] < rank[rightRoot]) parent[leftRoot] = rightRoot;
            else {
                parent[rightRoot] = leftRoot;
                if (rank[leftRoot] == rank[rightRoot]) rank[leftRoot]++;
            }
        }
    }

    /** Primitive long-to-int table used to keep component analysis free of per-edge objects. */
    private static final class GeometricEdgeTable {
        private final long[] keys;
        private final int[] firstTriangles;
        private final int[] secondTriangles;
        private final byte[] state;
        private final int mask;
        private GeometricEdgeTable(int expected) {
            int capacity = 1;
            long required = Math.max(2L, (long) Math.ceil(expected / 0.65));
            while (capacity < required && capacity < (1 << 30)) capacity <<= 1;
            if (capacity < required) throw new IllegalArgumentException("component edge table exceeds addressable size");
            keys = new long[capacity];
            firstTriangles = new int[capacity];
            secondTriangles = new int[capacity];
            state = new byte[capacity];
            mask = capacity - 1;
        }
        private void add(int left, int right, int triangle) {
            if (left == right) return;
            int first = Math.min(left, right), second = Math.max(left, right);
            long key = (long) first << 32 | Integer.toUnsignedLong(second);
            int slot = mix(key) & mask;
            while (state[slot] != 0) {
                if (keys[slot] == key) {
                    int count = state[slot] & 0x3;
                    if (count == 1) {
                        secondTriangles[slot] = triangle;
                        state[slot] = (byte) (2 | (state[slot] & 0x4)
                                | (left < right ? 0x8 : 0));
                    } else {
                        state[slot] = (byte) (3 | (state[slot] & 0xC));
                    }
                    return;
                }
                slot = slot + 1 & mask;
            }
            state[slot] = (byte) (1 | (left < right ? 0x4 : 0));
            keys[slot] = key;
            firstTriangles[slot] = triangle;
        }
        private void connectManifoldPairs(DisjointSet components) {
            for (int slot = 0; slot < state.length; slot++) {
                if ((state[slot] & 0x3) != 2) continue;
                boolean firstForward = (state[slot] & 0x4) != 0;
                boolean secondForward = (state[slot] & 0x8) != 0;
                if (firstForward != secondForward) {
                    components.union(firstTriangles[slot], secondTriangles[slot]);
                }
            }
        }
        private static int mix(long value) {
            value ^= value >>> 33;
            value *= 0xff51afd7ed558ccdl;
            value ^= value >>> 33;
            value *= 0xc4ceb9fe1a85ec53l;
            return (int) (value ^ value >>> 33);
        }
    }

    private static final class Mesh {
        private final float[] vertices;
        private final float[] positions;
        private final float[] attributes;
        private final int[] indices;
        private final byte[] locks;

        private Mesh(float[] vertices, float[] positions, float[] attributes, int[] indices, byte[] locks) {
            this.vertices = vertices;
            this.positions = positions;
            this.attributes = attributes;
            this.indices = indices;
            this.locks = locks;
        }

        private static Mesh fromExpanded(float[] source) {
            Map<VertexKey, Integer> unique = new HashMap<>();
            List<float[]> vertices = new ArrayList<>();
            int sourceVertices = source.length / COMPONENTS_PER_VERTEX;
            int[] indices = new int[sourceVertices];
            for (int sourceVertex = 0; sourceVertex < sourceVertices; sourceVertex++) {
                int offset = sourceVertex * COMPONENTS_PER_VERTEX;
                float[] vertex = Arrays.copyOfRange(source, offset, offset + COMPONENTS_PER_VERTEX);
                VertexKey key = new VertexKey(vertex);
                Integer index = unique.get(key);
                if (index == null) {
                    index = vertices.size();
                    unique.put(key, index);
                    vertices.add(vertex);
                }
                indices[sourceVertex] = index;
            }
            float[] flat = new float[vertices.size() * COMPONENTS_PER_VERTEX];
            float[] positions = new float[vertices.size() * 3];
            float[] attributes = new float[vertices.size() * ATTRIBUTE_COMPONENTS];
            for (int vertex = 0; vertex < vertices.size(); vertex++) {
                float[] value = vertices.get(vertex);
                System.arraycopy(value, 0, flat, vertex * COMPONENTS_PER_VERTEX, COMPONENTS_PER_VERTEX);
                System.arraycopy(value, 0, positions, vertex * 3, 3);
                System.arraycopy(value, 3, attributes, vertex * ATTRIBUTE_COMPONENTS, ATTRIBUTE_COMPONENTS);
            }
            return new Mesh(flat, positions, attributes, indices, boundaryLocks(indices, vertices.size()));
        }

        private static Mesh fromCanonical(float[] source, int[] indices, int vertexCount) {
            if (indices.length * COMPONENTS_PER_VERTEX != source.length || indices.length % 3 != 0
                    || vertexCount < 1) {
                throw new IllegalArgumentException("canonical topology does not match HOST source");
            }
            float[] vertices = new float[Math.multiplyExact(vertexCount, COMPONENTS_PER_VERTEX)];
            boolean[] initialized = new boolean[vertexCount];
            for (int occurrence = 0; occurrence < indices.length; occurrence++) {
                int vertex = indices[occurrence];
                if (vertex < 0 || vertex >= vertexCount) {
                    throw new IllegalArgumentException("canonical index outside vertex data");
                }
                if (!initialized[vertex]) {
                    System.arraycopy(source, occurrence * COMPONENTS_PER_VERTEX,
                            vertices, vertex * COMPONENTS_PER_VERTEX, COMPONENTS_PER_VERTEX);
                    initialized[vertex] = true;
                } else if (!sameVertex(source, occurrence * COMPONENTS_PER_VERTEX,
                        vertices, vertex * COMPONENTS_PER_VERTEX)) {
                    throw new CanonicalOccurrenceMismatch();
                }
            }
            float[] positions = new float[vertexCount * 3];
            float[] attributes = new float[vertexCount * ATTRIBUTE_COMPONENTS];
            for (int vertex = 0; vertex < vertexCount; vertex++) {
                System.arraycopy(vertices, vertex * COMPONENTS_PER_VERTEX, positions, vertex * 3, 3);
                System.arraycopy(vertices, vertex * COMPONENTS_PER_VERTEX + 3,
                        attributes, vertex * ATTRIBUTE_COMPONENTS, ATTRIBUTE_COMPONENTS);
            }
            return new Mesh(vertices, positions, attributes, Arrays.copyOf(indices, indices.length),
                    geometricBoundaryLocks(indices, positions));
        }

        private static boolean sameVertex(float[] left, int leftOffset, float[] right, int rightOffset) {
            for (int component = 0; component < COMPONENTS_PER_VERTEX; component++) {
                if (Float.floatToIntBits(left[leftOffset + component])
                        != Float.floatToIntBits(right[rightOffset + component])) return false;
            }
            return true;
        }

        private static byte[] geometricBoundaryLocks(int[] indices, float[] positions) {
            Map<PositionKey, Integer> positionIds = new HashMap<>();
            int vertexCount = positions.length / 3;
            int[] positionId = new int[vertexCount];
            for (int vertex = 0; vertex < vertexCount; vertex++) {
                PositionKey key = new PositionKey(positions, vertex * 3);
                positionId[vertex] = positionIds.computeIfAbsent(key, ignored -> positionIds.size());
            }
            Map<Long, Integer> uses = new HashMap<>();
            for (int index = 0; index < indices.length; index += 3) {
                edge(uses, positionId[indices[index]], positionId[indices[index + 1]]);
                edge(uses, positionId[indices[index + 1]], positionId[indices[index + 2]]);
                edge(uses, positionId[indices[index + 2]], positionId[indices[index]]);
            }
            boolean[] boundaryPosition = new boolean[positionIds.size()];
            for (Map.Entry<Long, Integer> entry : uses.entrySet()) if (entry.getValue() != 2) {
                boundaryPosition[(int) (entry.getKey() >>> 32)] = true;
                boundaryPosition[(int) (long) entry.getKey()] = true;
            }
            byte[] result = new byte[vertexCount];
            for (int vertex = 0; vertex < vertexCount; vertex++) {
                if (boundaryPosition[positionId[vertex]]) result[vertex] = 1;
            }
            return result;
        }

        private int vertexCount() { return positions.length / 3; }
        private int lockedVertexCount() {
            int result = 0;
            for (byte value : locks) if (value != 0) result++;
            return result;
        }

        private float[] expand(int[] selected) {
            float[] result = new float[selected.length * COMPONENTS_PER_VERTEX];
            for (int index = 0; index < selected.length; index++) {
                System.arraycopy(vertices, selected[index] * COMPONENTS_PER_VERTEX,
                        result, index * COMPONENTS_PER_VERTEX, COMPONENTS_PER_VERTEX);
            }
            return result;
        }

        private static byte[] boundaryLocks(int[] indices, int vertexCount) {
            Map<Long, Integer> uses = new HashMap<>();
            for (int index = 0; index < indices.length; index += 3) {
                edge(uses, indices[index], indices[index + 1]);
                edge(uses, indices[index + 1], indices[index + 2]);
                edge(uses, indices[index + 2], indices[index]);
            }
            byte[] result = new byte[vertexCount];
            for (Map.Entry<Long, Integer> entry : uses.entrySet()) if (entry.getValue() != 2) {
                result[(int) (entry.getKey() >>> 32)] = 1;
                result[(int) (long) entry.getKey()] = 1;
            }
            return result;
        }

        private static void edge(Map<Long, Integer> uses, int left, int right) {
            int first = Math.min(left, right), second = Math.max(left, right);
            long key = (long) first << 32 | Integer.toUnsignedLong(second);
            uses.merge(key, 1, Integer::sum);
        }
    }

    private static final class CanonicalOccurrenceMismatch extends RuntimeException {}

    private static final class VertexKey {
        private final int[] bits;
        private VertexKey(float[] vertex) {
            bits = new int[vertex.length];
            for (int index = 0; index < vertex.length; index++) bits[index] = Float.floatToIntBits(vertex[index]);
        }
        @Override public boolean equals(Object other) {
            return other instanceof VertexKey key && Arrays.equals(bits, key.bits);
        }
        @Override public int hashCode() { return Arrays.hashCode(bits); }
    }

    private record PositionKey(int x, int y, int z) {
        private PositionKey(float[] positions, int offset) {
            this(Float.floatToIntBits(positions[offset]), Float.floatToIntBits(positions[offset + 1]),
                    Float.floatToIntBits(positions[offset + 2]));
        }
    }

    private static final class FloatArray {
        private float[] values;
        private int size;
        private FloatArray(int capacity) { values = new float[Math.max(1, capacity)]; }
        private void addAll(float[] source) {
            int required = Math.addExact(size, source.length);
            if (required > values.length) values = Arrays.copyOf(values, Math.max(required, values.length * 2));
            System.arraycopy(source, 0, values, size, source.length);
            size = required;
        }
        private float[] toArray() { return Arrays.copyOf(values, size); }
    }

    private static final class IntArray {
        private int[] values;
        private int size;
        private IntArray(int capacity) { values = new int[Math.max(1, capacity)]; }
        private void add(int value) {
            if (size == values.length) values = Arrays.copyOf(values, Math.multiplyExact(values.length, 2));
            values[size++] = value;
        }
        private void addAll(int[] source) {
            int required = Math.addExact(size, source.length);
            if (required > values.length) values = Arrays.copyOf(values, Math.max(required,
                    Math.multiplyExact(values.length, 2)));
            System.arraycopy(source, 0, values, size, source.length);
            size = required;
        }
        private int[] toArray() { return Arrays.copyOf(values, size); }
    }
}
