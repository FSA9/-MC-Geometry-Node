package com.mine.geometry_node.client.model.render.backend.host.light.asset;

import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;

/** Bounded deterministic model-space receiver samples; one probe never crosses a triangle side. */
public final class HostReceiverProbeSet {
    private final float[] positions;
    private final float[] normals;
    private final int[] surfaceIndices;
    private final int[] triangleIndices;
    private final int sourceTriangles;

    private HostReceiverProbeSet(float[] positions, float[] normals,
                                 int[] surfaceIndices, int[] triangleIndices,
                                 int sourceTriangles) {
        this.positions = positions;
        this.normals = normals;
        this.surfaceIndices = surfaceIndices;
        this.triangleIndices = triangleIndices;
        this.sourceTriangles = sourceTriangles;
    }

    public static HostReceiverProbeSet build(List<HostLightingSurface> surfaces, int maximumProbes) {
        Objects.requireNonNull(surfaces, "surfaces");
        if (maximumProbes < 1) throw new IllegalArgumentException("maximumProbes must be positive");
        int sourceTriangles = 0;
        int[] validTriangles = new int[surfaces.size()];
        for (int surfaceIndex = 0; surfaceIndex < surfaces.size(); surfaceIndex++) {
            HostLightingSurface surface = surfaces.get(surfaceIndex);
            for (int triangle = 0; triangle < surface.triangleCount(); triangle++) {
                if (!surface.triangleDegenerate(triangle)) {
                    validTriangles[surfaceIndex]++;
                    sourceTriangles = Math.addExact(sourceTriangles, 1);
                }
            }
        }
        int probes = Math.min(sourceTriangles, maximumProbes);
        float[] positions = new float[probes * 3];
        float[] normals = new float[probes * 3];
        int[] surfaceIndices = new int[probes];
        int[] triangleIndices = new int[probes];
        if (probes == 0) {
            return new HostReceiverProbeSet(positions, normals, surfaceIndices, triangleIndices, sourceTriangles);
        }
        int[] quotas = allocate(validTriangles, probes);
        int probe = 0;
        for (int surfaceIndex = 0; surfaceIndex < surfaces.size(); surfaceIndex++) {
            HostLightingSurface surface = surfaces.get(surfaceIndex);
            int quota = quotas[surfaceIndex];
            if (quota == 0) continue;
            int validTriangle = 0, selected = 0;
            long nextSelection = 0;
            for (int triangle = 0; triangle < surface.triangleCount(); triangle++) {
                if (surface.triangleDegenerate(triangle)) continue;
                if (validTriangle == nextSelection) {
                    writeProbe(surface, triangle, positions, normals, probe);
                    surfaceIndices[probe] = surfaceIndex;
                    triangleIndices[probe] = triangle;
                    probe++;
                    selected++;
                    if (selected == quota) break;
                    nextSelection = (long) selected * validTriangles[surfaceIndex] / quota;
                }
                validTriangle++;
            }
        }
        if (probe != probes) throw new IllegalStateException("receiver probe allocation did not reach its target");
        return new HostReceiverProbeSet(positions, normals, surfaceIndices, triangleIndices, sourceTriangles);
    }

    public int size() { return positions.length / 3; }
    public int sourceTriangles() { return sourceTriangles; }
    public boolean sampled() { return size() < sourceTriangles; }
    public float position(int probe, int axis) { return positions[index(probe, axis)]; }
    public float normal(int probe, int axis) { return normals[index(probe, axis)]; }
    public int surfaceIndex(int probe) { return surfaceIndices[probeIndex(probe)]; }
    public int triangleIndex(int probe) { return triangleIndices[probeIndex(probe)]; }
    public long retainedBytes() {
        return (long) (positions.length + normals.length) * Float.BYTES
                + (long) (surfaceIndices.length + triangleIndices.length) * Integer.BYTES;
    }

    private int index(int probe, int axis) {
        probeIndex(probe);
        if (axis < 0 || axis > 2) throw new IndexOutOfBoundsException();
        return probe * 3 + axis;
    }

    private int probeIndex(int probe) {
        if (probe < 0 || probe >= size()) throw new IndexOutOfBoundsException(probe);
        return probe;
    }

    private static int[] allocate(int[] triangles, int probes) {
        int[] result = new int[triangles.length];
        int nonEmpty = 0;
        for (int count : triangles) if (count > 0) nonEmpty++;
        if (probes >= nonEmpty) {
            for (int surface = 0; surface < triangles.length; surface++) {
                if (triangles[surface] > 0) result[surface] = 1;
            }
        }
        PriorityQueue<SurfaceQuota> queue = new PriorityQueue<>((left, right) -> {
            int weight = Double.compare(right.weight(), left.weight());
            return weight != 0 ? weight : Integer.compare(left.surface(), right.surface());
        });
        for (int surface = 0; surface < triangles.length; surface++) {
            if (triangles[surface] > result[surface]) {
                queue.add(new SurfaceQuota(surface, triangles[surface], result[surface]));
            }
        }
        int assigned = probes >= nonEmpty ? nonEmpty : 0;
        for (; assigned < probes; assigned++) {
            SurfaceQuota next = Objects.requireNonNull(queue.poll(), "receiver probe quota");
            result[next.surface()]++;
            if (result[next.surface()] < next.triangles()) {
                queue.add(new SurfaceQuota(next.surface(), next.triangles(), result[next.surface()]));
            }
        }
        return result;
    }

    private static void writeProbe(HostLightingSurface surface, int triangle,
                                   float[] positions, float[] normals, int probe) {
        int output = probe * 3;
        for (int axis = 0; axis < 3; axis++) {
            float sum = 0;
            for (int corner = 0; corner < 3; corner++) {
                sum += surface.position(surface.vertexIndex(triangle, corner), axis);
            }
            positions[output + axis] = sum / 3F;
            normals[output + axis] = surface.geometricNormal(triangle, axis);
        }
    }

    private record SurfaceQuota(int surface, int triangles, int assigned) {
        private double weight() { return (double) triangles / (assigned + 1); }
    }
}
