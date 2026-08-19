package com.mine.geometry_node.client.model.render.backend.host.light.asset;

import java.util.List;
import java.util.Objects;

/** Bounded deterministic model-space receiver samples; one probe never crosses a triangle side. */
public final class HostReceiverProbeSet {
    private final float[] positions;
    private final float[] normals;
    private final int sourceTriangles;

    private HostReceiverProbeSet(float[] positions, float[] normals, int sourceTriangles) {
        this.positions = positions;
        this.normals = normals;
        this.sourceTriangles = sourceTriangles;
    }

    public static HostReceiverProbeSet build(List<HostLightingSurface> surfaces, int maximumProbes) {
        Objects.requireNonNull(surfaces, "surfaces");
        if (maximumProbes < 1) throw new IllegalArgumentException("maximumProbes must be positive");
        int sourceTriangles = 0;
        for (HostLightingSurface surface : surfaces) {
            for (int triangle = 0; triangle < surface.triangleCount(); triangle++) {
                if (!surface.triangleDegenerate(triangle)) sourceTriangles = Math.addExact(sourceTriangles, 1);
            }
        }
        int probes = Math.min(sourceTriangles, maximumProbes);
        float[] positions = new float[probes * 3];
        float[] normals = new float[probes * 3];
        if (probes == 0) return new HostReceiverProbeSet(positions, normals, sourceTriangles);
        int validTriangle = 0, probe = 0;
        long nextSelection = 0;
        for (HostLightingSurface surface : surfaces) {
            for (int triangle = 0; triangle < surface.triangleCount(); triangle++) {
                if (surface.triangleDegenerate(triangle)) continue;
                if (validTriangle == nextSelection) {
                    writeProbe(surface, triangle, positions, normals, probe++);
                    if (probe == probes) return new HostReceiverProbeSet(positions, normals, sourceTriangles);
                    nextSelection = (long) probe * sourceTriangles / probes;
                }
                validTriangle++;
            }
        }
        throw new IllegalStateException("receiver probe selection did not reach its deterministic target");
    }

    public int size() { return positions.length / 3; }
    public int sourceTriangles() { return sourceTriangles; }
    public boolean sampled() { return size() < sourceTriangles; }
    public float position(int probe, int axis) { return positions[index(probe, axis)]; }
    public float normal(int probe, int axis) { return normals[index(probe, axis)]; }
    public long retainedBytes() { return (long) (positions.length + normals.length) * Float.BYTES; }

    private int index(int probe, int axis) {
        if (probe < 0 || probe >= size() || axis < 0 || axis > 2) throw new IndexOutOfBoundsException();
        return probe * 3 + axis;
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
}
