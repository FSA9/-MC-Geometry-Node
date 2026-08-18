package com.mine.geometry_node.client.model.render.backend.host.geometry;

/** Maps an output triangle corner back to its canonical source/proxy vertex occurrence. */
public final class HostVertexOccurrence {
    private HostVertexOccurrence() {}

    public static int source(int sourceTriangle, int outputCorner, boolean mirrored) {
        return occurrence(0, sourceTriangle, outputCorner, mirrored);
    }

    public static int proxy(int sourceTriangleCount, int proxyTriangle,
                            int outputCorner, boolean mirrored) {
        if (sourceTriangleCount < 0) throw new IllegalArgumentException("sourceTriangleCount must not be negative");
        return occurrence(Math.multiplyExact(sourceTriangleCount, 3),
                proxyTriangle, outputCorner, mirrored);
    }

    private static int occurrence(int base, int triangle, int outputCorner, boolean mirrored) {
        if (triangle < 0) throw new IllegalArgumentException("triangle must not be negative");
        if (outputCorner < 0 || outputCorner > 2) {
            throw new IllegalArgumentException("outputCorner must be in [0, 2]");
        }
        int sourceCorner = mirrored && outputCorner != 0 ? 3 - outputCorner : outputCorner;
        return Math.addExact(base, Math.addExact(Math.multiplyExact(triangle, 3), sourceCorner));
    }
}
